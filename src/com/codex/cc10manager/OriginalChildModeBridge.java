package com.codex.cc10manager;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

final class OriginalChildModeBridge {
    private static final String TAG = "CC10ChildConfig";
    private static final String ORIGINAL_CONFIG_DB =
            "/data/data/com.alibaba.ailabs.genie.smartapp/databases/config_info.db";
    static final int DEFAULT_DAILY_SECONDS = 3600;
    static final int DEFAULT_SESSION_SECONDS = 1200;
    static final int DEFAULT_ALLOWED_START_SECONDS = 21600;
    static final int DEFAULT_ALLOWED_END_SECONDS = 72000;
    private static final Uri URI = Uri.parse(
            "content://com.alibaba.ailabs.childmode.provider/childMode");
    private static final Uri ORIGINAL_OPEN_URI = Uri.parse(
            "genie://com.alibaba.ailabs.genie.datacenter/childmode"
                    + "?type=open&from=genieApp");

    private final Context context;
    private static final Object DB_LOCK = new Object();
    private static final Map<String, String> DB_VALUES = new HashMap<String, String>();
    private static final long DB_REFRESH_INTERVAL_MS = 2000L;
    private static long lastDbRead;
    private static long lastDbAttempt;
    private static long lastModeRead;
    private static boolean lastMode;

    OriginalChildModeBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    String read(String key, String fallback) {
        refreshDatabaseSnapshot();
        synchronized (DB_LOCK) {
            String stored = DB_VALUES.get(key);
            if (stored != null && stored.length() > 0) return stored;
        }
        Cursor cursor = null;
        String[] selections = new String[] {key, "key='" + key + "'"};
        for (int i = 0; i < selections.length; i++) {
            try {
                cursor = context.getContentResolver().query(
                        URI, null, selections[i], null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int valueIndex = cursor.getColumnIndex("value");
                    if (valueIndex >= 0) {
                        String value = cursor.getString(valueIndex);
                        if (value != null && value.length() > 0) return value;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
                cursor = null;
            }
        }
        return fallback;
    }

    private void refreshDatabaseSnapshot() {
        synchronized (DB_LOCK) {
            long now = System.currentTimeMillis();
            if (now - lastDbAttempt < DB_REFRESH_INTERVAL_MS) return;
            lastDbAttempt = now;
            SQLiteDatabase database = null;
            Cursor cursor = null;
            try {
                database = SQLiteDatabase.openDatabase(ORIGINAL_CONFIG_DB, null,
                        SQLiteDatabase.OPEN_READONLY);
                cursor = database.query("childMode", new String[] {"key", "value"},
                        null, null, null, null, null);
                Map<String, String> values = new HashMap<String, String>();
                while (cursor.moveToNext()) {
                    values.put(cursor.getString(0), cursor.getString(1));
                }
                if (!values.isEmpty()) {
                    DB_VALUES.clear();
                    DB_VALUES.putAll(values);
                    // Only throttle after a successful snapshot. A transient copy/read
                    // failure must be retried on the next service tick instead of keeping
                    // stale parental-control limits for another full cache interval.
                    lastDbRead = now;
                }
            } catch (Exception error) {
                Log.w(TAG, "Original child configuration refresh failed: "
                        + error.getClass().getSimpleName() + ": " + error.getMessage());
            } finally {
                if (cursor != null) cursor.close();
                if (database != null) database.close();
            }
        }
    }

    void invalidateConfig() {
        synchronized (DB_LOCK) {
            lastDbRead = 0;
            lastDbAttempt = 0;
        }
    }

    int readInt(String key, int fallback) {
        try {
            return Integer.parseInt(read(key, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    int dailySeconds() { return readInt("time_per_every_day", DEFAULT_DAILY_SECONDS); }
    int sessionSeconds() { return readInt("time_per_once_time", DEFAULT_SESSION_SECONDS); }
    int allowedStartSeconds() { return readInt("forbidden_start_time", DEFAULT_ALLOWED_START_SECONDS); }
    int allowedEndSeconds() { return readInt("forbidden_end_time", DEFAULT_ALLOWED_END_SECONDS); }
    String password() { return read("pwd", ""); }

    boolean isChildMode() {
        synchronized (DB_LOCK) {
            long now = System.currentTimeMillis();
            if (now - lastModeRead < 800) return lastMode;
            lastModeRead = now;
            boolean providerEnabled = isProviderChildMode();
            if (providerEnabled) {
                lastMode = true;
                return true;
            }
            BufferedReader reader = null;
            try {
                Process process = Runtime.getRuntime().exec(new String[] {
                        "/system/bin/getprop", "persist.sys.is.child.mode"});
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // The original provider can report false after reboot even though
                // the persisted child-mode property still records the enabled state.
                // Keep child mode active when either source says it is enabled.
                lastMode = providerEnabled || "1".equals(reader.readLine());
            } catch (Exception ignored) {
            } finally {
                if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            }
            return lastMode;
        }
    }

    boolean isProviderChildMode() {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    URI, null, "{\"method\":1}", null, null);
            if (cursor == null) return false;
            Bundle extras = cursor.getExtras();
            return extras != null && extras.getBoolean("ret", false)
                    && extras.getBoolean("data", false);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    boolean isPersistedChildModeEnabled() {
        BufferedReader reader = null;
        try {
            Process process = Runtime.getRuntime().exec(new String[] {
                    "/system/bin/getprop", "persist.sys.is.child.mode"});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return "1".equals(reader.readLine());
        } catch (Exception ignored) {
            return false;
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
    }

    boolean restoreOriginalChildMode() {
        if (!isPersistedChildModeEnabled() || isProviderChildMode()) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, ORIGINAL_OPEN_URI);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}
