package com.codex.cc10manager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String SMARTAPP = "com.alibaba.ailabs.genie.smartapp";
    private static final String DANGBEI = "com.dangbei1.tvlauncher";
    private static final String DANGBEI_ACTIVITY = "com.dangbei.launcher.ui.main.MainActivity";
    private static final String SCREENSAVER = "com.alibaba.ailabs.genie.screensaver";
    private static final String SCREENSAVER_COMPONENT =
            "com.alibaba.ailabs.genie.screensaver/"
            + "com.alibaba.ailabs.genie.screensaver.FlipperDream";
    private static final String SETTING_SCREENSAVER_ENABLED = "screensaver_enabled";
    private static final String SETTING_SCREENSAVER_ON_SLEEP =
            "screensaver_activate_on_sleep";
    private static final String SETTING_SCREENSAVER_COMPONENTS =
            "screensaver_components";
    private static final int[] SCREEN_TIMEOUT_MINUTES = {5, 10, 30, 60};
    private static final int GREEN = Color.rgb(54, 201, 143);
    private static final int BG = Color.rgb(15, 20, 27);
    private static final int CARD = Color.rgb(25, 33, 43);
    private static final int CARD_2 = Color.rgb(31, 41, 52);
    private static final int TEXT = Color.rgb(238, 244, 247);
    private static final int SUBTEXT = Color.rgb(155, 169, 180);
    private static final int WARNING = Color.rgb(255, 184, 77);

    private PackageManager pm;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final List<ManagedItem> items = new ArrayList<ManagedItem>();
    private final Map<String, Switch> switches = new LinkedHashMap<String, Switch>();
    private TextView memoryText;
    private TextView permissionText;
    private TextView homeText;
    private TextView eventText;
    private TextView screenModeText;
    private Button undoButton;
    private Switch alwaysOnSwitch;
    private Switch standbyWallpaperSwitch;
    private final List<Button> timeoutButtons = new ArrayList<Button>();
    private boolean refreshing;
    private boolean privileged;

    private static class ManagedItem {
        final String id;
        final String group;
        final String title;
        final String description;
        final String packageName;
        final String className;

        ManagedItem(String id, String group, String title, String description,
                    String packageName, String className) {
            this.id = id;
            this.group = group;
            this.title = title;
            this.description = description;
            this.packageName = packageName;
            this.className = className;
        }

        boolean isComponent() {
            return className != null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        pm = getPackageManager();
        prefs = getSharedPreferences("manager_state", MODE_PRIVATE);
        privileged = checkSelfPermission(
                Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission("android.permission.WRITE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
        createManagedItems();
        setContentView(buildUi());
        startService(new Intent(this, ChildModeService.class));
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (memoryText != null) {
            refreshAll();
        }
    }

    private void createManagedItems() {
        items.add(new ManagedItem(
                "original_home", "桌面与后台",
                "天猫精灵原桌面",
                "关闭后由当贝成为唯一桌面；语音助手和下拉栏仍保留",
                SMARTAPP,
                "com.alibaba.ailabs.genie.launcher.activity.HomeActivity"));
        items.add(new ManagedItem(
                "analytics", "桌面与后台",
                "原桌面统计服务",
                "关闭使用情况统计，减少后台常驻",
                SMARTAPP,
                "com.alibaba.analytics.AnalyticsService"));
        items.add(new ManagedItem(
                "log_service", "桌面与后台",
                "原桌面日志服务",
                "关闭非必要日志上报，不影响本地语音日志",
                SMARTAPP,
                "com.alibaba.ailabs.genie.logger.service.LogService"));
        items.add(new ManagedItem(
                "slide_accs", "桌面与后台",
                "原桌面推送服务",
                "关闭 Slide/ACCS 内容推送",
                SMARTAPP,
                "com.taobao.slide.accs.SlideAccsService"));

        items.add(new ManagedItem(
                "market", "内容与推荐",
                "天猫精灵应用市场",
                "关闭官方应用市场与推荐入口",
                "com.alibaba.ailabs.genie.market", null));
        items.add(new ManagedItem(
                "cook", "内容与推荐",
                "菜谱",
                "关闭菜谱内容应用",
                "com.alibaba.ailabs.genie.cook", null));
        items.add(new ManagedItem(
                "albums", "内容与推荐",
                "相册",
                "关闭原厂云相册应用",
                "com.alibaba.ailabs.genie.albums", null));

        items.add(new ManagedItem(
                "rtc", "摄像头与儿童功能",
                "视频通话 RTC",
                "不使用视频通话时关闭，摄像头通话将不可用",
                "com.alibaba.ailabs.genie.rtc", null));
        items.add(new ManagedItem(
                "aicare", "摄像头与儿童功能",
                "儿童与看护",
                "关闭 AI 看护内容应用",
                "com.alibaba.ailabs.genie.aicare", null));
        items.add(new ManagedItem(
                "ar_face", "摄像头与儿童功能",
                "人脸识别",
                "关闭人脸相关功能",
                "com.alibaba.ailabs.ar.face", null));
        items.add(new ManagedItem(
                "ar_monitor", "摄像头与儿童功能",
                "视觉监控",
                "关闭摄像头监控模块",
                "com.alibaba.ailabs.ar.monitor", null));
        items.add(new ManagedItem(
                "ar_fireeye", "摄像头与儿童功能",
                "FireEye 视觉服务",
                "关闭儿童/视觉识别辅助模块",
                "com.alibaba.ailabs.ar.fireeye2", null));
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(18), dp(24), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("CC10 优化管理器", 27, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(title);
        homeText = text("正在读取当前桌面…", 14, SUBTEXT);
        titleBox.addView(homeText);
        header.addView(titleBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        memoryText = text("正在读取内存…", 15, TEXT);
        memoryText.setGravity(Gravity.RIGHT);
        header.addView(memoryText);
        root.addView(header);

        permissionText = text("", 14, TEXT);
        permissionText.setPadding(dp(14), dp(9), dp(14), dp(9));
        LinearLayout.LayoutParams permissionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        permissionLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(permissionText, permissionLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button optimize = actionButton("一键推荐优化", GREEN, Color.rgb(10, 31, 25));
        optimize.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirmBatch(false);
            }
        });
        actions.addView(optimize, weightedButtonParams());

        Button restore = actionButton("一键恢复原厂", Color.rgb(66, 89, 108), TEXT);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirmBatch(true);
            }
        });
        actions.addView(restore, weightedButtonParams());

        undoButton = actionButton("撤销上次批量操作", Color.rgb(45, 57, 70), TEXT);
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                undoLastBatch();
            }
        });
        actions.addView(undoButton, weightedButtonParams());

        Button refresh = actionButton("刷新状态", Color.rgb(45, 57, 70), TEXT);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                refreshAll();
            }
        });
        actions.addView(refresh, weightedButtonParams());
        root.addView(actions);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        bodyLp.setMargins(0, dp(14), 0, 0);
        root.addView(body, bodyLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 7));

        addScreenStandbySection(list);

        String lastGroup = "";
        for (final ManagedItem item : items) {
            if (!lastGroup.equals(item.group)) {
                TextView group = text(item.group, 17, GREEN);
                group.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                group.setPadding(dp(6), dp(13), 0, dp(6));
                list.addView(group);
                lastGroup = item.group;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setBackground(roundRect(CARD, 12));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(item.title, 17, TEXT);
            labels.addView(name);
            TextView description = text(item.description, 13, SUBTEXT);
            labels.addView(description);
            row.addView(labels, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Switch toggle = new Switch(this);
            toggle.setShowText(true);
            toggle.setTextOn("已开启");
            toggle.setTextOff("已停用");
            toggle.setTextColor(TEXT);
            toggle.setTextSize(13);
            toggle.setPadding(dp(10), 0, 0, 0);
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton buttonView,
                                                       boolean isChecked) {
                    if (!refreshing) {
                        setItemFromUi(item, isChecked);
                    }
                }
            });
            switches.put(item.id, toggle);
            row.addView(toggle);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, dp(12), dp(8));
            list.addView(row, rowLp);
        }

        LinearLayout safety = new LinearLayout(this);
        safety.setOrientation(LinearLayout.VERTICAL);
        safety.setPadding(dp(17), dp(15), dp(17), dp(15));
        safety.setBackground(roundRect(CARD_2, 14));
        LinearLayout.LayoutParams safetyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 3);
        safetyLp.setMargins(dp(9), 0, 0, 0);
        body.addView(safety, safetyLp);

        TextView protectedTitle = text("安全保护（不可关闭）", 18, GREEN);
        protectedTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        safety.addView(protectedTitle);
        safety.addView(text(
                "\n✓ 天猫精灵语音助手\n" +
                "✓ 音乐播放服务\n" +
                "✓ 智能家居 IoT\n" +
                "✓ 语音卡片 WebApps\n" +
                "✓ 系统下拉栏\n" +
                "✓ 系统设置\n\n" +
                "管理器不会修改 boot、system 内容，也不会卸载应用；所有开关均可恢复。",
                14, TEXT));

        Button childMode = actionButton("儿童模式与视频计时", GREEN,
                Color.rgb(10, 31, 25));
        childMode.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ChildModeActivity.class));
            }
        });
        LinearLayout.LayoutParams childModeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(49));
        childModeLp.setMargins(0, dp(14), 0, 0);
        safety.addView(childMode, childModeLp);

        eventText = text("等待操作", 13, SUBTEXT);
        eventText.setPadding(0, dp(15), 0, 0);
        safety.addView(eventText);
        return root;
    }

    private void addScreenStandbySection(LinearLayout list) {
        TextView group = text("屏幕待机", 17, GREEN);
        group.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        group.setPadding(dp(6), dp(2), 0, dp(6));
        list.addView(group);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.setBackground(roundRect(CARD, 12));

        alwaysOnSwitch = screenSwitch(
                "常亮模式",
                "开启后，CC10 接通电源时不会自动黑屏或进入屏保");
        alwaysOnSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton buttonView, boolean isChecked) {
                        if (!refreshing) {
                            setAlwaysOn(isChecked);
                        }
                    }
                });
        card.addView(alwaysOnSwitch);

        standbyWallpaperSwitch = screenSwitch(
                "待机显示壁纸",
                "关闭常亮后：开启则到时进入原厂壁纸，关闭则到时黑屏");
        standbyWallpaperSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton buttonView, boolean isChecked) {
                        if (!refreshing) {
                            setStandbyWallpaper(isChecked);
                        }
                    }
                });
        LinearLayout.LayoutParams wallpaperLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wallpaperLp.setMargins(0, dp(5), 0, 0);
        card.addView(standbyWallpaperSwitch, wallpaperLp);

        LinearLayout timeoutRow = new LinearLayout(this);
        timeoutRow.setOrientation(LinearLayout.HORIZONTAL);
        timeoutRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView timeoutTitle = text("无操作等待", 14, SUBTEXT);
        timeoutRow.addView(timeoutTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (final int minutes : SCREEN_TIMEOUT_MINUTES) {
            Button button = actionButton(minutes + " 分钟",
                    Color.rgb(45, 57, 70), TEXT);
            button.setTextSize(12);
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    setScreenTimeout(minutes);
                }
            });
            timeoutButtons.add(button);
            LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                    dp(82), dp(39));
            buttonLp.setMargins(dp(7), 0, 0, 0);
            timeoutRow.addView(button, buttonLp);
        }
        LinearLayout.LayoutParams timeoutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        timeoutLp.setMargins(0, dp(8), 0, 0);
        card.addView(timeoutRow, timeoutLp);

        screenModeText = text("", 13, GREEN);
        screenModeText.setPadding(0, dp(8), 0, 0);
        card.addView(screenModeText);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, dp(12), dp(8));
        list.addView(card, cardLp);
    }

    private Switch screenSwitch(String title, String description) {
        Switch toggle = new Switch(this);
        toggle.setText(title + "\n" + description);
        toggle.setTextColor(TEXT);
        toggle.setTextSize(14);
        toggle.setShowText(true);
        toggle.setTextOn("开启");
        toggle.setTextOff("关闭");
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(2), 0, dp(2));
        return toggle;
    }

    private void setAlwaysOn(boolean enabled) {
        if (!privileged) {
            showToast("缺少系统设置权限");
            refreshAll();
            return;
        }
        try {
            boolean success = Settings.Global.putInt(getContentResolver(),
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN, enabled ? 7 : 0);
            if (!success) {
                throw new SecurityException("系统拒绝修改常亮设置");
            }
            logEvent("常亮模式 → " + (enabled ? "已开启" : "已关闭"));
            showToast(enabled
                    ? "已开启接电常亮"
                    : "已恢复按设定时间待机");
        } catch (Exception e) {
            logEvent("常亮设置失败：" + friendlyError(e));
            showToast("设置失败：" + friendlyError(e));
        }
        refreshAll();
    }

    private void setStandbyWallpaper(boolean enabled) {
        if (!privileged) {
            showToast("缺少系统设置权限");
            refreshAll();
            return;
        }
        try {
            if (enabled) {
                pm.setApplicationEnabledSetting(
                        SCREENSAVER,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0);
                Settings.Secure.putString(getContentResolver(),
                        SETTING_SCREENSAVER_COMPONENTS, SCREENSAVER_COMPONENT);
                Settings.Secure.putInt(getContentResolver(),
                        SETTING_SCREENSAVER_ON_SLEEP, 1);
                Settings.Secure.putInt(getContentResolver(),
                        SETTING_SCREENSAVER_ENABLED, 1);
            } else {
                Settings.Secure.putInt(getContentResolver(),
                        SETTING_SCREENSAVER_ENABLED, 0);
                pm.setApplicationEnabledSetting(
                        SCREENSAVER,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
            }
            logEvent("待机方式 → " + (enabled ? "原厂壁纸" : "黑屏"));
            showToast(enabled
                    ? "到达等待时间后将显示原厂壁纸"
                    : "到达等待时间后将关闭屏幕");
        } catch (Exception e) {
            logEvent("待机方式设置失败：" + friendlyError(e));
            showToast("设置失败：" + friendlyError(e));
        }
        refreshAll();
    }

    private void setScreenTimeout(int minutes) {
        if (!privileged) {
            showToast("缺少系统设置权限");
            return;
        }
        try {
            boolean success = Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60 * 1000);
            if (!success) {
                throw new SecurityException("系统拒绝修改待机时间");
            }
            logEvent("无操作等待 → " + minutes + " 分钟");
            showToast("待机等待时间已设为 " + minutes + " 分钟");
        } catch (Exception e) {
            logEvent("待机时间设置失败：" + friendlyError(e));
            showToast("设置失败：" + friendlyError(e));
        }
        refreshAll();
    }

    private void setItemFromUi(final ManagedItem item, final boolean enabled) {
        if (!privileged) {
            showToast("当前是只读模式，请先安装为系统特权应用");
            refreshAll();
            return;
        }
        if ("original_home".equals(item.id) && !enabled && !isDangbeiHomeAvailable()) {
            showToast("未检测到可用的当贝桌面，已阻止关闭原桌面");
            refreshAll();
            return;
        }
        try {
            setEnabled(item, enabled);
            logEvent(item.title + " → " + (enabled ? "已开启" : "已停用"));
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    refreshAll();
                }
            }, 350);
        } catch (Exception e) {
            logEvent("操作失败：" + friendlyError(e));
            showToast("操作失败：" + friendlyError(e));
            refreshAll();
        }
    }

    private void confirmBatch(final boolean restore) {
        if (!privileged) {
            showToast("当前是只读模式，请先完成系统特权安装");
            return;
        }
        if (!restore && !isDangbeiHomeAvailable()) {
            showToast("未检测到当贝桌面，不能执行推荐优化");
            return;
        }
        String title = restore ? "恢复原厂功能？" : "应用推荐优化？";
        String message = restore
                ? "将重新开启列表中的原桌面、内容应用、视频通话和摄像头功能。核心数据不会被清除。"
                : "将停用列表中的非必要项目，保留语音、音乐、智能家居、语音卡片和下拉设置。";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(restore ? "恢复" : "开始优化",
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                applyBatch(restore);
                            }
                        })
                .show();
    }

    private void applyBatch(boolean enabled) {
        saveSnapshot();
        int changed = 0;
        int failed = 0;
        for (ManagedItem item : items) {
            try {
                if (isInstalled(item.packageName) && isEnabled(item) != enabled) {
                    setEnabled(item, enabled);
                    changed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        prefs.edit().putBoolean("has_snapshot", true).apply();
        logEvent((enabled ? "恢复完成" : "优化完成") + "：修改 " + changed
                + " 项" + (failed > 0 ? "，失败 " + failed + " 项" : ""));
        showToast(enabled ? "原厂项目已恢复" : "推荐优化已应用");
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                refreshAll();
            }
        }, 600);
    }

    private void saveSnapshot() {
        SharedPreferences.Editor editor = prefs.edit();
        for (ManagedItem item : items) {
            if (isInstalled(item.packageName)) {
                editor.putBoolean("snapshot_" + item.id, isEnabled(item));
                editor.putBoolean("snapshot_present_" + item.id, true);
            } else {
                editor.remove("snapshot_" + item.id);
                editor.remove("snapshot_present_" + item.id);
            }
        }
        editor.apply();
    }

    private void undoLastBatch() {
        if (!privileged) {
            showToast("当前是只读模式");
            return;
        }
        if (!prefs.getBoolean("has_snapshot", false)) {
            showToast("没有可撤销的批量操作");
            return;
        }
        int restored = 0;
        for (ManagedItem item : items) {
            if (prefs.getBoolean("snapshot_present_" + item.id, false)
                    && isInstalled(item.packageName)) {
                boolean enabled = prefs.getBoolean("snapshot_" + item.id, true);
                if ("original_home".equals(item.id) && !enabled
                        && !isDangbeiHomeAvailable()) {
                    continue;
                }
                try {
                    setEnabled(item, enabled);
                    restored++;
                } catch (Exception ignored) {
                }
            }
        }
        prefs.edit().putBoolean("has_snapshot", false).apply();
        logEvent("已撤销上次批量操作，恢复 " + restored + " 项");
        refreshAll();
    }

    private void setEnabled(ManagedItem item, boolean enabled) {
        if (item.isComponent()) {
            pm.setComponentEnabledSetting(
                    new ComponentName(item.packageName, item.className),
                    enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } else {
            pm.setApplicationEnabledSetting(
                    item.packageName,
                    enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    0);
        }
    }

    private boolean isEnabled(ManagedItem item) {
        if (!isInstalled(item.packageName)) {
            return false;
        }
        if (item.isComponent()) {
            int state = pm.getComponentEnabledSetting(
                    new ComponentName(item.packageName, item.className));
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return true;
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return false;
            }
            try {
                ComponentInfo info;
                if (item.className.endsWith("Activity")) {
                    info = pm.getActivityInfo(
                            new ComponentName(item.packageName, item.className), 0);
                } else {
                    info = pm.getServiceInfo(
                            new ComponentName(item.packageName, item.className), 0);
                }
                return info.enabled && info.applicationInfo.enabled;
            } catch (Exception e) {
                return true;
            }
        }
        int state = pm.getApplicationEnabledSetting(item.packageName);
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    private void refreshAll() {
        refreshing = true;
        boolean alwaysOn = readAlwaysOn();
        boolean wallpaper = readStandbyWallpaper();
        int timeoutMinutes = readScreenTimeoutMinutes();
        alwaysOnSwitch.setChecked(alwaysOn);
        standbyWallpaperSwitch.setChecked(wallpaper);
        alwaysOnSwitch.setEnabled(privileged);
        standbyWallpaperSwitch.setEnabled(privileged && !alwaysOn);
        standbyWallpaperSwitch.setAlpha(alwaysOn ? 0.55f : 1.0f);
        for (int i = 0; i < timeoutButtons.size(); i++) {
            Button button = timeoutButtons.get(i);
            boolean selected = timeoutMinutes == SCREEN_TIMEOUT_MINUTES[i];
            button.setBackground(roundRect(
                    selected ? GREEN : Color.rgb(45, 57, 70), 10));
            button.setTextColor(selected ? Color.rgb(10, 31, 25) : TEXT);
            button.setEnabled(privileged && !alwaysOn);
            button.setAlpha(alwaysOn ? 0.55f : 1.0f);
        }
        screenModeText.setText(alwaysOn
                ? "当前：接电常亮"
                : "当前：" + timeoutMinutes + " 分钟后"
                + (wallpaper ? "显示原厂壁纸" : "黑屏，触摸后唤醒"));
        for (ManagedItem item : items) {
            Switch toggle = switches.get(item.id);
            boolean installed = isInstalled(item.packageName);
            toggle.setEnabled(privileged && installed);
            toggle.setChecked(installed && isEnabled(item));
            toggle.setAlpha(installed ? 1.0f : 0.45f);
            toggle.setText(installed
                    ? (toggle.isChecked() ? "已开启" : "已停用")
                    : "未安装");
        }
        refreshing = false;
        permissionText.setText(privileged
                ? "系统管理权限已就绪 · 开关可直接生效"
                : "只读模式 · 需要安装到 /system/priv-app 后才能修改");
        permissionText.setTextColor(privileged ? GREEN : WARNING);
        permissionText.setBackground(roundRect(
                privileged ? Color.rgb(20, 55, 44) : Color.rgb(63, 48, 26), 10));
        undoButton.setEnabled(privileged && prefs.getBoolean("has_snapshot", false));
        undoButton.setAlpha(undoButton.isEnabled() ? 1.0f : 0.5f);
        homeText.setText("当前桌面：" + resolveCurrentHome());
        memoryText.setText(readMemorySummary());
    }

    private boolean readAlwaysOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0;
    }

    private boolean readStandbyWallpaper() {
        boolean settingEnabled = Settings.Secure.getInt(getContentResolver(),
                SETTING_SCREENSAVER_ENABLED, 0) != 0;
        return settingEnabled && isPackageEnabled(SCREENSAVER);
    }

    private int readScreenTimeoutMinutes() {
        int milliseconds = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 10 * 60 * 1000);
        return Math.max(1, Math.round(milliseconds / 60000.0f));
    }

    private boolean isPackageEnabled(String packageName) {
        if (!isInstalled(packageName)) {
            return false;
        }
        int state = pm.getApplicationEnabledSetting(packageName);
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    private boolean isDangbeiHomeAvailable() {
        if (!isInstalled(DANGBEI)) {
            return false;
        }
        try {
            ComponentName component = new ComponentName(DANGBEI, DANGBEI_ACTIVITY);
            int appState = pm.getApplicationEnabledSetting(DANGBEI);
            int componentState = pm.getComponentEnabledSetting(component);
            boolean appOkay = appState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && appState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
            boolean componentOkay =
                    componentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            && componentState
                            != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
            pm.getActivityInfo(component, 0);
            return appOkay && componentOkay;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return info != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String resolveCurrentHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) {
            return "未识别";
        }
        String pkg = info.activityInfo.packageName;
        if (DANGBEI.equals(pkg)) {
            return "当贝桌面";
        }
        if (SMARTAPP.equals(pkg)) {
            return "天猫精灵原桌面";
        }
        return pkg;
    }

    private String readMemorySummary() {
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    values.put(parts[0].replace(":", ""), Long.parseLong(parts[1]));
                }
            }
        } catch (Exception e) {
            return "内存信息不可用";
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        long available = value(values, "MemAvailable");
        long total = value(values, "MemTotal");
        long swapFree = value(values, "SwapFree");
        long swapTotal = value(values, "SwapTotal");
        return String.format(Locale.CHINA,
                "可用内存 %.0f MB / %.0f MB\n可用交换区 %.0f MB / %.0f MB",
                available / 1024.0, total / 1024.0,
                swapFree / 1024.0, swapTotal / 1024.0);
    }

    private long value(Map<String, Long> map, String key) {
        Long value = map.get(key);
        return value == null ? 0 : value.longValue();
    }

    private void logEvent(String message) {
        eventText.setText("最近操作\n" + message);
    }

    private String friendlyError(Exception e) {
        if (e instanceof SecurityException) {
            return "缺少系统管理权限";
        }
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private Button actionButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setBackground(roundRect(background, 10));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(45), 1);
        params.setMargins(0, 0, dp(10), 0);
        return params;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
