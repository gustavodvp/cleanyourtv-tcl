package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Settings screen: pick a remote button and the app it opens. Needs the KeyMapService accessibility service. */
public class KeyMapActivity extends Activity {
    private LinearLayout list;
    private AlertDialog waiting;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (48 * getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad / 2, pad, pad / 2);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#101114"));
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        KeyMap.capture = null;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private boolean serviceOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private void render() {
        list.removeAllViews();
        list.addView(text(getString(R.string.keymap_title), 28));
        if (!serviceOn()) {
            list.addView(text(getString(R.string.keymap_service_off), 16));
            list.addView(button(getString(R.string.keymap_open_accessibility), v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        }
        for (Map.Entry<Integer, String> e : KeyMap.all(this).entrySet()) {
            final int code = e.getKey();
            list.addView(button(KeyEvent.keyCodeToString(code) + "  →  " + appLabel(e.getValue()), v ->
                    new AlertDialog.Builder(this).setTitle(R.string.keymap_remove_q)
                            .setPositiveButton(R.string.keymap_remove, (d, w) -> {
                                KeyMap.remove(this, code);
                                render();
                            }).setNegativeButton(android.R.string.cancel, null).show()));
        }
        Button add = button(getString(R.string.keymap_add), v -> startCapture());
        list.addView(add);
        add.requestFocus();
    }

    private void startCapture() {
        waiting = new AlertDialog.Builder(this).setMessage(R.string.keymap_press)
                .setNegativeButton(android.R.string.cancel, (d, w) -> KeyMap.capture = null)
                .setOnCancelListener(d -> KeyMap.capture = null).create();
        KeyMap.capture = code -> main.post(() -> gotKey(code));
        waiting.setOnKeyListener((d, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0
                    && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_DPAD_CENTER) {
                KeyMap.capture = null;
                gotKey(code);
                return true;
            }
            return false;
        });
        waiting.show();
    }

    private void gotKey(int code) {
        KeyMap.capture = null;
        if (waiting != null && waiting.isShowing()) waiting.dismiss();
        final List<String> pkgs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(q, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            pkgs.add(ri.activityInfo.packageName);
            labels.add(ri.loadLabel(getPackageManager()).toString());
        }
        final Integer[] order = new Integer[labels.size()];
        for (int k = 0; k < order.length; k++) order[k] = k;
        java.util.Arrays.sort(order, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        final List<String> sortedPkgs = new ArrayList<>();
        final List<String> sortedLabels = new ArrayList<>();
        for (int k : order) {
            sortedPkgs.add(pkgs.get(k));
            sortedLabels.add(labels.get(k));
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.keymap_pick, KeyEvent.keyCodeToString(code)))
                .setItems(sortedLabels.toArray(new String[0]), (d, i) -> {
                    KeyMap.put(this, code, sortedPkgs.get(i));
                    render();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
