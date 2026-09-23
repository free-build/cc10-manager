package com.codex.cc10manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class ChildModePrefs {
    static final String NAME = "child_mode_control";
    static final String ENABLED = "enabled";
    static final String TIMED_PACKAGES = "timed_packages";
    static final String DAILY_MS = "daily_ms";
    static final String SESSION_MS = "session_ms";
    static final String DAY_KEY = "day_key";
    static final String ACTIVE_PACKAGE = "active_package";
    static final String COUNTING = "counting";
    static final String LOCK_REASON = "lock_reason";
    static final String UNLOCK_UNTIL = "unlock_until";
    static final String LAST_ACTIVE_AT = "last_active_at";
    static final String SESSION_EXHAUSTED_AT = "session_exhausted_at";
    static final String FALLBACK_PIN = "fallback_pin";
    static final String OVERLAY_ENABLED = "overlay_enabled";
    static final String LOCK_TIMED_ONLY = "lock_timed_only";
    static final String OVERLAY_X = "overlay_x";
    static final String OVERLAY_Y = "overlay_y";

    private ChildModePrefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    static void resetDayIfNeeded(SharedPreferences prefs) {
        String today = today();
        if (!today.equals(prefs.getString(DAY_KEY, ""))) {
            prefs.edit().putString(DAY_KEY, today)
                    .putLong(DAILY_MS, 0)
                    .putLong(SESSION_MS, 0)
                    .putLong(SESSION_EXHAUSTED_AT, 0)
                    .putString(LOCK_REASON, "")
                    .apply();
        }
    }

    static Set<String> getSet(SharedPreferences prefs, String key) {
        Set<String> stored = prefs.getStringSet(key, Collections.<String>emptySet());
        return new HashSet<String>(stored);
    }
}
