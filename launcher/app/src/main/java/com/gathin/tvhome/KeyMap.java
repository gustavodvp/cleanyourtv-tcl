package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.TreeMap;

/** Remote button -> app package, kept in SharedPreferences ("k<keycode>" -> package). */
final class KeyMap {
    interface Capture {
        void onKey(int keyCode);
    }

    /** While set, the next key seen by the activity or by KeyMapService goes here instead of being handled. */
    static volatile Capture capture;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("keymap", Context.MODE_PRIVATE);
    }

    static String get(Context c, int keyCode) {
        return prefs(c).getString("k" + keyCode, null);
    }

    static void put(Context c, int keyCode, String pkg) {
        prefs(c).edit().putString("k" + keyCode, pkg).apply();
    }

    static void remove(Context c, int keyCode) {
        prefs(c).edit().remove("k" + keyCode).apply();
    }

    static Map<Integer, String> all(Context c) {
        Map<Integer, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey().startsWith("k") && e.getValue() instanceof String) {
                try {
                    out.put(Integer.parseInt(e.getKey().substring(1)), (String) e.getValue());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}
