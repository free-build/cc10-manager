package com.codex.cc10manager;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class ChildModeService extends Service {
    private static final String DANGBEI_HOME = "com.dangbei1.tvlauncher";
    private static final String SYSTEM_PASSWORD_ACTIVITY =
            "com.alibaba.ailabs.childmode.ui.page.ValidateLockActivity";
    private static final String CHILD_LOCK_ACTIVITY =
            "com.codex.cc10manager.ChildLockActivity";
    static final long SESSION_REST_RESET_MS = 60 * 60 * 1000L;
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private OriginalChildModeBridge original;
    private ChildTimeOverlay overlay;
    private long lastTick;
    private long lastPersist;
    private long lastLockLaunch;
    private int originalRestoreAttempts;

    private final Runnable originalStateRestorer = new Runnable() {
        @Override public void run() {
            if (!original.isPersistedChildModeEnabled()
                    || original.isProviderChildMode()) return;
            if (originalRestoreAttempts >= 3) return;
            originalRestoreAttempts++;
            if (original.restoreOriginalChildMode()) {
                handler.postDelayed(new Runnable() {
                    @Override public void run() {
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        startActivity(home);
                    }
                }, 4000);
            }
            handler.postDelayed(this, 12000);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = ChildModePrefs.get(this);
        original = new OriginalChildModeBridge(this);
        overlay = new ChildTimeOverlay(this, prefs);
        lastTick = System.currentTimeMillis();
        handler.post(ticker);
        // The original provider initializes after boot and can forget its runtime
        // flag even though the persistent property still says child mode was on.
        // Re-enter through AliGenie's own public route after its services settle.
        handler.postDelayed(originalStateRestorer, 15000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(originalStateRestorer);
        if (overlay != null) overlay.hide();
        persistStatus(false, "");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void tick() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, Math.min(3000, now - lastTick));
        lastTick = now;
        ChildModePrefs.resetDayIfNeeded(prefs);

        if (!original.isChildMode()) {
            prefs.edit().putString(ChildModePrefs.LOCK_REASON, "")
                    .putLong(ChildModePrefs.UNLOCK_UNTIL, 0).apply();
            persistStatus(false, topPackage());
            overlay.hide();
            return;
        }
        String pkg = topPackage();
        String topClass = topActivityClass();
        Set<String> timed = ChildModePrefs.getSet(prefs, ChildModePrefs.TIMED_PACKAGES);
        boolean timedOnlyLock = prefs.getBoolean(ChildModePrefs.LOCK_TIMED_ONLY, true);
        boolean lockPageVisible = CHILD_LOCK_ACTIVITY.equals(topClass)
                || SYSTEM_PASSWORD_ACTIVITY.equals(topClass);
        boolean shouldApplyRestriction = !timedOnlyLock
                || timed.contains(pkg) || lockPageVisible;
        int dailyLimit = original.dailySeconds();
        int sessionLimit = original.sessionSeconds();
        long daily = prefs.getLong(ChildModePrefs.DAILY_MS, 0);
        long session = prefs.getLong(ChildModePrefs.SESSION_MS, 0);
        long exhaustedAt = prefs.getLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0);
        if (sessionLimit > 0 && session >= sessionLimit * 1000L) {
            if (exhaustedAt <= 0) {
                exhaustedAt = now;
                prefs.edit().putLong(ChildModePrefs.SESSION_EXHAUSTED_AT,
                        exhaustedAt).apply();
            } else if (now - exhaustedAt >= SESSION_REST_RESET_MS
                    && (dailyLimit <= 0 || daily < dailyLimit * 1000L)) {
                session = 0;
                exhaustedAt = 0;
                prefs.edit().putLong(ChildModePrefs.SESSION_MS, 0)
                        .putLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0)
                        .putString(ChildModePrefs.LOCK_REASON, "").apply();
            }
        } else if (exhaustedAt > 0) {
            exhaustedAt = 0;
            prefs.edit().putLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0).apply();
        }
        if (now < prefs.getLong(ChildModePrefs.UNLOCK_UNTIL, 0)) {
            persistStatus(false, pkg);
            updateOverlay(pkg, now, false);
            return;
        }
        String reason = shouldApplyRestriction
                ? restrictionReason(daily, session, dailyLimit, sessionLimit) : "";
        if (reason.length() > 0) {
            persistStatus(false, pkg);
            updateOverlay(pkg, now, false);
            showLock(reason, now);
            return;
        }
        if (!shouldApplyRestriction
                && prefs.getString(ChildModePrefs.LOCK_REASON, "").length() > 0) {
            prefs.edit().putString(ChildModePrefs.LOCK_REASON, "").apply();
        }

        boolean candidate = pkg.length() > 0 && timed.contains(pkg) && screenInteractive();
        boolean counting = candidate && isMediaPlaying();

        SharedPreferences.Editor editor = prefs.edit();
        if (counting) {
            daily += elapsed;
            session += elapsed;
            editor.putLong(ChildModePrefs.DAILY_MS, daily)
                    .putLong(ChildModePrefs.SESSION_MS, session)
                    .putLong(ChildModePrefs.LAST_ACTIVE_AT, now);
        }
        if (sessionLimit > 0 && session >= sessionLimit * 1000L
                && exhaustedAt <= 0) {
            exhaustedAt = now;
            editor.putLong(ChildModePrefs.SESSION_EXHAUSTED_AT, exhaustedAt);
        }
        editor.putBoolean(ChildModePrefs.COUNTING, counting)
                .putString(ChildModePrefs.ACTIVE_PACKAGE, pkg)
                .apply();
        updateOverlay(pkg, now, counting);

        reason = shouldApplyRestriction
                ? restrictionReason(daily, session, dailyLimit, sessionLimit) : "";
        if (reason.length() > 0) showLock(reason, now);
        if (now - lastPersist > 10000) {
            lastPersist = now;
            prefs.edit().putLong(ChildModePrefs.DAILY_MS, daily)
                    .putLong(ChildModePrefs.SESSION_MS, session).commit();
        }
    }

    private void updateOverlay(String pkg, long now, boolean counting) {
        boolean show = prefs.getBoolean(ChildModePrefs.OVERLAY_ENABLED, false)
                && DANGBEI_HOME.equals(pkg);
        long daily = prefs.getLong(ChildModePrefs.DAILY_MS, 0);
        long session = prefs.getLong(ChildModePrefs.SESSION_MS, 0);
        int dailyLimit = original.dailySeconds();
        int sessionLimit = original.sessionSeconds();
        long exhaustedAt = prefs.getLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0);
        long restRemaining = exhaustedAt <= 0 ? -1
                : Math.max(0, SESSION_REST_RESET_MS - (now - exhaustedAt));
        overlay.update(show,
                dailyLimit <= 0 ? 0 : dailyLimit * 1000L - daily,
                sessionLimit <= 0 ? 0 : sessionLimit * 1000L - session,
                restRemaining, counting, dailyLimit <= 0, sessionLimit <= 0);
    }

    private String restrictionReason(long daily, long session,
                                     int dailyLimit, int sessionLimit) {
        if (!insideAllowedTime()) return "当前不在允许观看视频的时间段";
        if (dailyLimit > 0 && daily >= dailyLimit * 1000L) return "今天的视频时长已用完";
        if (sessionLimit > 0 && session >= sessionLimit * 1000L) return "本次连续观看时间已到";
        return "";
    }

    private boolean insideAllowedTime() {
        int start = original.allowedStartSeconds();
        int end = original.allowedEndSeconds();
        if (start == end) return true;
        Calendar calendar = Calendar.getInstance();
        int now = calendar.get(Calendar.HOUR_OF_DAY) * 3600
                + calendar.get(Calendar.MINUTE) * 60 + calendar.get(Calendar.SECOND);
        return start < end ? now >= start && now < end : now >= start || now < end;
    }

    private String topPackage() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ComponentName top = tasks.get(0).topActivity;
                return top == null ? "" : top.getPackageName();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private boolean screenInteractive() {
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return power == null || power.isInteractive();
    }

    private boolean isMediaPlaying() {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audio != null && audio.isMusicActive();
    }

    private void persistStatus(boolean counting, String pkg) {
        prefs.edit().putBoolean(ChildModePrefs.COUNTING, counting)
                .putString(ChildModePrefs.ACTIVE_PACKAGE, pkg == null ? "" : pkg)
                .apply();
    }

    private void showLock(String reason, long now) {
        prefs.edit().putString(ChildModePrefs.LOCK_REASON, reason).apply();
        if (SYSTEM_PASSWORD_ACTIVITY.equals(topActivityClass())) return;
        if (now - lastLockLaunch < 2500) return;
        lastLockLaunch = now;
        Intent intent = new Intent(this, ChildLockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private String topActivityClass() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ComponentName top = tasks.get(0).topActivity;
                return top == null ? "" : top.getClassName();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
