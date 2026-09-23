package com.codex.cc10manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChildModeActivity extends Activity {
    private static final int BG = Color.rgb(15, 20, 27);
    private static final int CARD = Color.rgb(25, 33, 43);
    private static final int GREEN = Color.rgb(54, 201, 143);
    private static final int TEXT = Color.rgb(238, 244, 247);
    private static final int SUBTEXT = Color.rgb(155, 169, 180);
    private SharedPreferences prefs;
    private OriginalChildModeBridge original;
    private PackageManager pm;
    private TextView statusText;
    private TextView configText;
    private TextView listText;
    private Switch overlaySwitch;
    private Switch timedOnlyLockSwitch;
    private boolean refreshing;
    private final Handler handler = new Handler();
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private static class AppItem {
        String packageName;
        String label;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = ChildModePrefs.get(this);
        original = new OriginalChildModeBridge(this);
        pm = getPackageManager();
        setContentView(buildUi());
        startService(new Intent(this, ChildModeService.class));
    }

    @Override protected void onResume() {
        super.onResume();
        original.invalidateConfig();
        handler.post(refresher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(26), dp(18), dp(26), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹ 返回", Color.rgb(43, 55, 68), TEXT);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(110), dp(44)));
        TextView title = text("儿童模式", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(20), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView followSystem = text("跟随系统儿童模式", 16, SUBTEXT);
        followSystem.setGravity(Gravity.RIGHT);
        header.addView(followSystem);
        root.addView(header);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        columnsLp.setMargins(0, dp(16), 0, 0);
        root.addView(columns, columnsLp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(20), dp(18), dp(20), dp(18));
        left.setBackground(roundRect(CARD, 15));
        TextView statusTitle = text("当前状态", 20, GREEN);
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        left.addView(statusTitle);
        statusText = text("", 17, TEXT);
        statusText.setPadding(0, dp(12), 0, dp(12));
        left.addView(statusText);
        configText = text("", 15, SUBTEXT);
        left.addView(configText);
        overlaySwitch = new Switch(this);
        overlaySwitch.setText("当贝桌面显示计时卡片");
        overlaySwitch.setTextColor(TEXT);
        overlaySwitch.setTextSize(15);
        overlaySwitch.setShowText(true);
        overlaySwitch.setTextOn("开启");
        overlaySwitch.setTextOff("关闭");
        overlaySwitch.setPadding(0, dp(12), 0, 0);
        overlaySwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton buttonView,
                                                           boolean isChecked) {
                        if (!refreshing) {
                            prefs.edit().putBoolean(ChildModePrefs.OVERLAY_ENABLED,
                                    isChecked).apply();
                            startService(new Intent(ChildModeActivity.this,
                                    ChildModeService.class));
                        }
                    }
                });
        left.addView(overlaySwitch);
        timedOnlyLockSwitch = new Switch(this);
        timedOnlyLockSwitch.setText("仅计时应用触发休息页");
        timedOnlyLockSwitch.setTextColor(TEXT);
        timedOnlyLockSwitch.setTextSize(15);
        timedOnlyLockSwitch.setShowText(true);
        timedOnlyLockSwitch.setTextOn("开启");
        timedOnlyLockSwitch.setTextOff("关闭");
        timedOnlyLockSwitch.setPadding(0, dp(10), 0, 0);
        timedOnlyLockSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton buttonView,
                                                           boolean isChecked) {
                        if (!refreshing) {
                            prefs.edit().putBoolean(ChildModePrefs.LOCK_TIMED_ONLY,
                                    isChecked).apply();
                            startService(new Intent(ChildModeActivity.this,
                                    ChildModeService.class));
                        }
                    }
                });
        left.addView(timedOnlyLockSwitch);
        TextView timedOnlyHint = text("开启后，时长用完或处于禁看时段时，打开其他应用不会弹出休息页。",
                13, SUBTEXT);
        timedOnlyHint.setPadding(0, dp(3), 0, 0);
        left.addView(timedOnlyHint);
        Button reset = button("清零今天计时", Color.rgb(64, 57, 48), TEXT);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        resetLp.setMargins(0, dp(16), 0, 0);
        left.addView(reset, resetLp);
        columns.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 4));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(20), dp(18), dp(20), dp(18));
        right.setBackground(roundRect(CARD, 15));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 6);
        rightLp.setMargins(dp(14), 0, 0, 0);
        columns.addView(right, rightLp);
        TextView appsTitle = text("计时应用白名单", 20, GREEN);
        appsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        right.addView(appsTitle);
        right.addView(text("只有列表中的应用在前台、屏幕亮起且检测到媒体播放时才累计时长；暂停或停留在界面时不计时。",
                14, SUBTEXT));
        listText = text("", 16, TEXT);
        ScrollView listScroll = new ScrollView(this);
        listScroll.addView(listText);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        listLp.setMargins(0, dp(12), 0, dp(10));
        right.addView(listScroll, listLp);
        LinearLayout buttons = new LinearLayout(this);
        Button select = button("选择计时应用", GREEN, Color.rgb(8, 30, 23));
        select.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { selectTimedApps(); }
        });
        buttons.addView(select, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        right.addView(buttons);
        return root;
    }

    private void selectTimedApps() {
        final List<AppItem> apps = launcherApps();
        final Set<String> selected = ChildModePrefs.getSet(prefs, ChildModePrefs.TIMED_PACKAGES);
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checked[i] = selected.contains(apps.get(i).packageName);
        }
        new AlertDialog.Builder(this).setTitle("选择需要计时的应用")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        String pkg = apps.get(which).packageName;
                        if (isChecked) selected.add(pkg); else selected.remove(pkg);
                    }
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putStringSet(ChildModePrefs.TIMED_PACKAGES,
                                new HashSet<String>(selected)).apply();
                        refresh();
                    }
                }).show();
    }

    private List<AppItem> launcherApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        List<AppItem> result = new ArrayList<AppItem>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || !seen.add(info.activityInfo.packageName)) continue;
            AppItem item = new AppItem();
            item.packageName = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            item.label = label == null ? item.packageName : label.toString();
            result.add(item);
        }
        sortApps(result);
        return result;
    }

    private List<AppItem> appsForPackages(Set<String> packages) {
        List<AppItem> result = new ArrayList<AppItem>();
        for (String pkg : packages) {
            AppItem item = new AppItem();
            item.packageName = pkg;
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                item.label = pm.getApplicationLabel(info).toString();
            } catch (Exception e) { item.label = pkg; }
            result.add(item);
        }
        sortApps(result);
        return result;
    }

    private void sortApps(List<AppItem> apps) {
        Collections.sort(apps, new Comparator<AppItem>() {
            @Override public int compare(AppItem a, AppItem b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("清零今天计时？")
                .setMessage("每日累计和当前单次累计都将归零。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清零", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putLong(ChildModePrefs.DAILY_MS, 0)
                                .putLong(ChildModePrefs.SESSION_MS, 0)
                                .putLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0)
                                .putString(ChildModePrefs.LOCK_REASON, "")
                                .putLong(ChildModePrefs.UNLOCK_UNTIL, 0).apply();
                        refresh();
                    }
                }).show();
    }

    private void refresh() {
        ChildModePrefs.resetDayIfNeeded(prefs);
        refreshing = true;
        boolean enabled = original.isChildMode();
        overlaySwitch.setChecked(prefs.getBoolean(
                ChildModePrefs.OVERLAY_ENABLED, false));
        timedOnlyLockSwitch.setChecked(prefs.getBoolean(
                ChildModePrefs.LOCK_TIMED_ONLY, true));
        refreshing = false;
        long daily = prefs.getLong(ChildModePrefs.DAILY_MS, 0);
        long session = prefs.getLong(ChildModePrefs.SESSION_MS, 0);
        String pkg = prefs.getString(ChildModePrefs.ACTIVE_PACKAGE, "");
        boolean counting = prefs.getBoolean(ChildModePrefs.COUNTING, false);
        statusText.setText((enabled ? "系统儿童模式已开启" : "系统儿童模式已关闭")
                + "\n当前应用：" + appLabel(pkg)
                + "\n计时状态：" + (counting ? "正在累计" : "未计时")
                + "\n今日已用：" + formatDuration(daily)
                + "\n本次已用：" + formatDuration(session));
        configText.setText("原厂视频防沉迷配置\n每日总时长："
                + original.dailySeconds() / 60 + " 分钟\n单次时长："
                + original.sessionSeconds() / 60 + " 分钟\n可观看时间："
                + formatClock(original.allowedStartSeconds()) + "–"
                + formatClock(original.allowedEndSeconds()));
        Set<String> timed = ChildModePrefs.getSet(prefs, ChildModePrefs.TIMED_PACKAGES);
        List<AppItem> apps = appsForPackages(timed);
        if (apps.isEmpty()) {
            listText.setText("尚未选择。儿童模式开启后不会累计任何应用的时长。 ");
        } else {
            StringBuilder builder = new StringBuilder();
            for (AppItem app : apps) {
                builder.append("• ").append(app.label).append('\n');
            }
            listText.setText(builder.toString());
        }
    }

    private String appLabel(String pkg) {
        if (pkg == null || pkg.length() == 0) return "未识别";
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    private String formatDuration(long ms) {
        long total = ms / 1000;
        return String.format(Locale.CHINA, "%d 分 %02d 秒", total / 60, total % 60);
    }

    private String formatClock(int seconds) {
        return String.format(Locale.CHINA, "%02d:%02d", seconds / 3600,
                (seconds % 3600) / 60);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private Button button(String value, int bg, int fg) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(fg);
        button.setAllCaps(false);
        button.setBackground(roundRect(bg, 11));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
