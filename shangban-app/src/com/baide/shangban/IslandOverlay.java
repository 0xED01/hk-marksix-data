package com.baide.shangban;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * 顶部悬浮“灵动岛”：自动对准屏幕中间的挖孔摄像头，
 * 摄像头包在黑色胶囊正中间，左边显示图标/状态，右边显示金额。
 * 点击展开（向下长出倒计时和进度条），长按打开 App。
 */
public class IslandOverlay {
    private final Context ctx;
    private final WindowManager wm;
    private LinearLayout root, row;
    private TextView left, right, line2;
    private ProgressBar bar;
    private GradientDrawable bg;
    private WindowManager.LayoutParams lp;
    private boolean shown = false, expanded = false;

    // 摄像头位置（屏幕像素）
    private int camCx, camTop, camH, camW;
    private int sidePad, rowH, gap, leftWCollapsed, leftWExpanded;

    IslandOverlay(Context c) {
        ctx = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 读取挖孔位置；读不到时按“屏幕正中、状态栏内”的挖孔估算。 */
    private void detectCamera() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        camCx = screenW / 2;
        camW = dp(26);
        camH = dp(26);
        camTop = dp(10);
        try {
            Display d = wm.getDefaultDisplay();
            DisplayCutout cut = Build.VERSION.SDK_INT >= 29 ? d.getCutout() : null;
            if (cut != null) {
                List<Rect> rects = cut.getBoundingRects();
                for (Rect r : rects) {
                    if (r.top < dp(60)) {
                        if (r.width() > 0 && r.width() < screenW / 2) {
                            camCx = r.centerX();
                            camW = r.width();
                            camH = r.height();
                            camTop = r.top;
                            // 有些机型 boundingRect 从 0 一直到状态栏底部，摄像头本体是靠下的圆
                            if (camH > camW * 1.3f) {
                                camTop = r.bottom - camW - dp(2);
                                camH = camW;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    private void create() {
        detectCamera();
        sidePad = dp(10);
        rowH = Math.max(camH + dp(10), dp(30));
        gap = camW + dp(12);

        Paint p = new Paint();
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setFontFeatureSettings("tnum");
        p.setTextSize(13 * ctx.getResources().getDisplayMetrics().scaledDensity);
        int rightW = (int) p.measureText("¥000.0000") + dp(2);
        p.setTextSize(12 * ctx.getResources().getDisplayMetrics().scaledDensity);
        leftWExpanded = (int) p.measureText("💰 上班中") + dp(4);
        leftWCollapsed = dp(22);

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(rowH / 2f);
        root.setBackground(bg);
        root.setPadding(sidePad, 0, sidePad, 0);

        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        left = new TextView(ctx);
        left.setTextColor(Color.WHITE);
        left.setTextSize(12);
        left.setTypeface(Typeface.DEFAULT_BOLD);
        left.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        left.setSingleLine(true);
        row.addView(left, new LinearLayout.LayoutParams(leftWCollapsed, rowH));

        View hole = new View(ctx); // 给摄像头留的空位
        row.addView(hole, new LinearLayout.LayoutParams(gap, rowH));

        right = new TextView(ctx);
        right.setTextColor(0xFFFFB74D);
        right.setTextSize(13);
        right.setTypeface(Typeface.DEFAULT_BOLD);
        right.setFontFeatureSettings("tnum");
        right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        right.setSingleLine(true);
        row.addView(right, new LinearLayout.LayoutParams(rightW, rowH));
        root.addView(row);

        line2 = new TextView(ctx);
        line2.setTextColor(Color.WHITE);
        line2.setTextSize(13);
        line2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.topMargin = dp(2);
        root.addView(line2, l2);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        blp.topMargin = dp(6);
        blp.bottomMargin = dp(12);
        root.addView(bar, blp);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.y = Math.max(0, camTop + camH / 2 - rowH / 2);    // 垂直对准摄像头
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);

        root.setOnTouchListener(new View.OnTouchListener() {
            long downAt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downAt = System.currentTimeMillis();
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - downAt > 500) {
                        ctx.startActivity(new Intent(ctx, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    } else {
                        expanded = !expanded;
                        applyExpanded();
                    }
                    return true;
                }
                return true;
            }
        });
        applyExpanded();
    }

    private void applyExpanded() {
        line2.setVisibility(expanded ? View.VISIBLE : View.GONE);
        bar.setVisibility(expanded ? View.VISIBLE : View.GONE);
        bg.setCornerRadius(expanded ? dp(22) : rowH / 2f);
        int leftW = expanded ? leftWExpanded : leftWCollapsed;
        left.getLayoutParams().width = leftW;
        left.requestLayout();
        // 让留空位的中心正好落在摄像头中心
        lp.x = Math.max(0, camCx - (sidePad + leftW + gap / 2));
        if (shown) {
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        }
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
        String icon;
        switch (s.state) {
            case WORKING: icon = "💰"; break;
            case LUNCH: icon = "🍱"; break;
            case BEFORE_WORK: icon = "⏰"; break;
            default: icon = "😴";
        }
        left.setText(expanded ? icon + " " + s.statusText : icon);
        right.setText(String.format(Locale.US, "¥%.4f", s.earnedToday));
        if (expanded) {
            String cd = s.countdownSec >= 0
                    ? s.countdownLabel.replace("还有", "") + " " + SalaryCalc.hmsShort(s.countdownSec) : "";
            line2.setText(cd + String.format(Locale.US, "  ·  %.1f%%", s.progress * 100));
            bar.setProgress((int) (s.progress * 1000));
        }
    }
}
