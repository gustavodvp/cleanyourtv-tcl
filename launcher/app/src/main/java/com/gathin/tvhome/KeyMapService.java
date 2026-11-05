package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens the app mapped to a remote button (see KeyMapActivity). Some buttons, like the TCL YouTube key (4065),
 * are also handled by the system, which opens the Play Store page of a removed app; when the Play Store shows up
 * within a few seconds of a mapped key, the mapped app is opened again on top of it.
 */
public class KeyMapService extends AccessibilityService {

    private static final long WINDOW_MS = 4000;

    private String lastPkg;
    private long lastKeyAt = -WINDOW_MS;
    private long lastLaunchAt = -WINDOW_MS;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // Swallow the matching key-up of a mapped or just-captured key.
            return KeyMap.get(this, code) != null;
        }
        if (event.getRepeatCount() != 0) return KeyMap.get(this, code) != null;
        KeyMap.Capture cap = KeyMap.capture;
        if (cap != null && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_DPAD_CENTER) {
            cap.onKey(code);
            return true;
        }
        String pkg = KeyMap.get(this, code);
        if (pkg == null) return false;
        lastPkg = pkg;
        lastKeyAt = SystemClock.elapsedRealtime();
        launch(pkg);
        return true;
    }

    private void launch(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        lastLaunchAt = SystemClock.elapsedRealtime();
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || lastPkg == null || !"com.android.vending".contentEquals(pkg)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastKeyAt > WINDOW_MS || now - lastLaunchAt < 800) return;
        launch(lastPkg);
    }

    @Override
    public void onInterrupt() {
    }
}
