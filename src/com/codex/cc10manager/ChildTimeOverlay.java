package com.codex.cc10manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class ChildTimeOverlay {
    private final Context context;
    private final SharedPreferences prefs;
    private final WindowManager windows;
    private LinearLayout card;
    private TextView dailyText;
    private TextView sessionText;
    private TextView restText;
    private WindowManager.LayoutParams params;

    ChildTimeOverlay(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    void update(boolean visible, long dailyRemaining, long sessionRemaining,
                long restRemaining, boolean counting, boolean dailyUnlimited,
                boolean sessionUnlimited) {
        if (!visible || !canDraw()) {
            hide();
            return;
        }
        ensureView();
        dailyText.setText("今日剩余  " + (dailyUnlimited
                ? "不限" : formatDuration(dailyRemaining)));
        sessionText.setText("单次剩余  " + (sessionUnlimited
                ? "不限" : formatDuration(sessionRemaining)));
        restText.setText("休息重置  " + (counting ? "播放中"
                : restRemaining < 0 ? "已重置" : formatDuration(restRemaining)));
    }

    void hide() {
        if (card == null) return;
        try { windows.removeView(card); } catch (Exception ignored) {}
        card = null;
        dailyText = null;
        sessionText = null;
        restText = null;
        params = null;
    }

    private boolean canDraw() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context);
    }

    private void ensureView() {
        if (card != null) return;
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(13), dp(18), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(236, 25, 33, 43));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(54, 201, 143));
        card.setBackground(background);

        TextView title = text("儿童模式 · 视频时长", 15, Color.rgb(54, 201, 143));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        dailyText = text("", 16, Color.WHITE);
        sessionText = text("", 16, Color.WHITE);
        restText = text("", 14, Color.rgb(180, 192, 201));
        card.addView(dailyText);
        card.addView(sessionText);
        card.addView(restText);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(310),
                WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        int defaultX = Math.max(0,
                context.getResources().getDisplayMetrics().widthPixels - dp(334));
        params.x = prefs.getInt(ChildModePrefs.OVERLAY_X, defaultX);
        params.y = prefs.getInt(ChildModePrefs.OVERLAY_Y, dp(38));
        attachDrag();
        try {
            windows.addView(card, params);
            card.post(new Runnable() {
                @Override public void run() {
                    clampToScreen();
                    try { windows.updateViewLayout(card, params); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {
            card = null;
        }
    }

    private void attachDrag() {
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + Math.round(event.getRawX() - downX);
                    params.y = startY + Math.round(event.getRawY() - downY);
                    clampToScreen();
                    try { windows.updateViewLayout(card, params); } catch (Exception ignored) {}
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    clampToScreen();
                    prefs.edit().putInt(ChildModePrefs.OVERLAY_X, params.x)
                            .putInt(ChildModePrefs.OVERLAY_Y, params.y).apply();
                    return true;
                }
                return false;
            }
        });
    }

    private void clampToScreen() {
        if (params == null || card == null) return;
        Point size = new Point();
        windows.getDefaultDisplay().getSize(size);
        int cardWidth = card.getWidth() > 0 ? card.getWidth() : dp(310);
        int cardHeight = card.getHeight() > 0 ? card.getHeight() : dp(150);
        params.x = Math.max(0, Math.min(params.x, Math.max(0, size.x - cardWidth)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, size.y - cardHeight)));
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.05f);
        return view;
    }

    private String formatDuration(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.CHINA, "%02d:%02d",
                seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
