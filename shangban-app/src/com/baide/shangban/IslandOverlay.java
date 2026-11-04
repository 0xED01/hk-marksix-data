package com.baide.shangban;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/** 顶部悬浮“灵动岛”胶囊：点击展开/收起，长按打开 App，可拖动。 */
public class IslandOverlay {
    private final Context ctx;
    private final WindowManager wm;
    private LinearLayout root;
    private TextView line1, line2;
    private ProgressBar bar;
    private WindowManager.LayoutParams lp;
    private boolean shown = false, expanded = false;

    IslandOverlay(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void create() {
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(dp(22));
        root.setBackground(bg);
        root.setPadding(dp(16), dp(7), dp(16), dp(7));

        line1 = new TextView(ctx);
        line1.setTextColor(0xFFFFB74D);
        line1.setTextSize(14);
        line1.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        line1.setGravity(Gravity.CENTER);
        root.addView(line1);

        line2 = new TextView(ctx);
        line2.setTextColor(Color.WHITE);
        line2.setTextSize(12);
        line2.setGravity(Gravity.CENTER);
        root.addView(line2);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(200), dp(4));
        blp.topMargin = dp(4);
        root.addView(bar, blp);
        applyExpanded();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.x = ctx.getSharedPreferences("cfg", 0).getInt("islandX", 0);
        lp.y = ctx.getSharedPreferences("cfg", 0).getInt("islandY", dp(6));
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        root.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; boolean moved; long downAt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false;
                        downAt = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            lp.x = ox + (int) dx; lp.y = Math.max(0, oy + (int) dy);
                            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            ctx.getSharedPreferences("cfg", 0).edit()
                                    .putInt("islandX", lp.x).putInt("islandY", lp.y).apply();
                        } else if (System.currentTimeMillis() - downAt > 500) {
                            Intent i = new Intent(ctx, MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            ctx.startActivity(i);
                        } else {
                            expanded = !expanded;
                            applyExpanded();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void applyExpanded() {
        line2.setVisibility(expanded ? View.VISIBLE : View.GONE);
        bar.setVisibility(expanded ? View.VISIBLE : View.GONE);
        line1.setTextSize(expanded ? 18 : 14);
        root.setPadding(dp(16), dp(expanded ? 10 : 6), dp(16), dp(expanded ? 10 : 6));
    }

    void show() {
        if (shown) return;
        if (root == null) create();
        try { wm.addView(root, lp); shown = true; } catch (Exception ignored) { }
    }

    void hide() {
        if (!shown) return;
        try { wm.removeView(root); } catch (Exception ignored) { }
        shown = false;
    }

    void update(SalaryCalc.Snapshot s) {
        if (!shown) return;
        if (expanded) {
            line1.setText(String.format(Locale.US, "¥%.4f", s.earnedToday));
            String cd = s.countdownSec >= 0 ? s.countdownLabel.replace("还有", "") + " " + SalaryCalc.hmsShort(s.countdownSec) : "";
            line2.setText(s.statusText + "  ·  " + cd);
            bar.setProgress((int) (s.progress * 1000));
        } else {
            String icon = s.state == SalaryCalc.State.WORKING ? "💰 " : (s.state == SalaryCalc.State.LUNCH ? "🍱 " : "😴 ");
            line1.setText(icon + String.format(Locale.US, "¥%.4f", s.earnedToday));
        }
    }
}
