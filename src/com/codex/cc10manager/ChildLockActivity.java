package com.codex.cc10manager;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class ChildLockActivity extends Activity {
    private static final int REQUEST_SYSTEM_PASSWORD = 201;
    private static final int BG = Color.rgb(11, 18, 25);
    private static final int GREEN = Color.rgb(54, 201, 143);
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private TextView dailyRemainingText;
    private TextView sessionRemainingText;
    private TextView restRemainingText;

    private final Runnable guard = new Runnable() {
        @Override public void run() {
            if (!new OriginalChildModeBridge(ChildLockActivity.this).isChildMode()
                    || prefs.getString(ChildModePrefs.LOCK_REASON, "").length() == 0
                    || System.currentTimeMillis() < prefs.getLong(
                    ChildModePrefs.UNLOCK_UNTIL, 0)) {
                finish();
                return;
            }
            refreshQuota();
            immersive();
            handler.postDelayed(this, 800);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = ChildModePrefs.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(buildUi());
        refreshQuota();
        immersive();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(guard);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(guard);
        super.onPause();
    }

    @Override public void onBackPressed() { }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(70), dp(48), dp(70), dp(48));
        root.setBackgroundColor(BG);

        LinearLayout message = new LinearLayout(this);
        message.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("休息一下吧", 42, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        message.addView(title);
        TextView reason = text(prefs.getString(ChildModePrefs.LOCK_REASON,
                "当前观看时间已到"), 23, GREEN);
        reason.setPadding(0, dp(18), 0, dp(16));
        message.addView(reason);
        message.addView(text("系统儿童模式仍保持开启。输入四位家长密码后可临时解锁；\n关闭儿童模式请使用系统原厂设置。",
                18, Color.rgb(174, 186, 195)));
        LinearLayout quota = new LinearLayout(this);
        quota.setOrientation(LinearLayout.HORIZONTAL);
        quota.setPadding(0, dp(24), 0, 0);
        dailyRemainingText = quotaText();
        sessionRemainingText = quotaText();
        restRemainingText = quotaText();
        quota.addView(dailyRemainingText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        quota.addView(sessionRemainingText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        quota.addView(restRemainingText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        message.addView(quota);
        root.addView(message, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 3));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(26), dp(30), dp(26));
        panel.setBackground(roundRect(Color.rgb(27, 37, 48), 18));
        panel.addView(text("家长验证", 25, Color.WHITE));
        TextView passwordHint = text("使用系统儿童模式设置中的四位密码验证，密码修改后无需在管理器中重新设置。",
                16, Color.rgb(174, 186, 195));
        passwordHint.setPadding(0, dp(16), 0, dp(18));
        panel.addView(passwordHint);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button temporary = button("输入系统密码并临时解锁", GREEN, Color.rgb(8, 30, 23));
        temporary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSystemPasswordVerifier(); }
        });
        actions.addView(temporary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        panel.addView(actions);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2);
        panelLp.setMargins(dp(65), 0, 0, 0);
        root.addView(panel, panelLp);
        return root;
    }

    private void openSystemPasswordVerifier() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("genie://com.alibaba.ailabs.genie.datacenter/password?type=1"));
            startActivityForResult(intent, REQUEST_SYSTEM_PASSWORD);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统密码验证页面",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SYSTEM_PASSWORD && resultCode == RESULT_OK
                && data != null && data.getBooleanExtra("ret", false)) {
            prefs.edit().putLong(ChildModePrefs.UNLOCK_UNTIL,
                    System.currentTimeMillis() + 30 * 60 * 1000L)
                    .putString(ChildModePrefs.LOCK_REASON, "").apply();
            finish();
        }
    }

    private void refreshQuota() {
        if (dailyRemainingText == null) return;
        OriginalChildModeBridge original = new OriginalChildModeBridge(this);
        long daily = prefs.getLong(ChildModePrefs.DAILY_MS, 0);
        long session = prefs.getLong(ChildModePrefs.SESSION_MS, 0);
        int dailyLimit = original.dailySeconds();
        int sessionLimit = original.sessionSeconds();
        long now = System.currentTimeMillis();
        long exhaustedAt = prefs.getLong(ChildModePrefs.SESSION_EXHAUSTED_AT, 0);
        dailyRemainingText.setText("今日剩余\n" + remaining(dailyLimit, daily));
        sessionRemainingText.setText("单次剩余\n" + remaining(sessionLimit, session));
        String rest;
        if (dailyLimit > 0 && daily >= dailyLimit * 1000L) {
            rest = "今日额度已用完";
        } else if (exhaustedAt > 0) {
            rest = formatDuration(Math.max(0,
                    ChildModeService.SESSION_REST_RESET_MS - (now - exhaustedAt)));
        } else {
            rest = "尚未触发";
        }
        restRemainingText.setText("休息重置\n" + rest);
    }

    private TextView quotaText() {
        TextView view = text("", 17, Color.WHITE);
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    private String remaining(int limitSeconds, long usedMs) {
        return limitSeconds <= 0 ? "不限"
                : formatDuration(Math.max(0, limitSeconds * 1000L - usedMs));
    }

    private String formatDuration(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.CHINA, "%02d:%02d",
                seconds / 60, seconds % 60);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value, int bg, int fg) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(fg);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackground(roundRect(bg, 12));
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
}
