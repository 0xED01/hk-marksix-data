package com.baide.shangban;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
    private int sidePad, rowH, gap;

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

    private TextView big;

    private TextView side(int color, float sp, int gravity) {
        TextView t = new TextView(ctx);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setFontFeatureSettings("tnum");
        t.setGravity(gravity | Gravity.CENTER_VERTICAL);
        t.setMaxLines(1);
        // 文字太长时自动缩小，绝不超出胶囊
        t.setAutoSizeTextTypeUniformWithConfiguration(8, (int) sp, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    private void create() {
        detectCamera();
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        sidePad = dp(10);
        rowH = Math.max(camH + dp(10), dp(32));
        // 挖孔区域有时比摄像头本身大，留空宽度做个上限
        int cam = Math.max(dp(16), Math.min(camW, dp(30)));
        gap = cam + dp(10);
        int sideW = dp(50);

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
        left = side(0xFFFFB74D, 13, Gravity.END);          // 左：今日已赚
        row.addView(left, new LinearLayout.LayoutParams(sideW, rowH));
        row.addView(new View(ctx), new LinearLayout.LayoutParams(gap, rowH)); // 摄像头
        right = side(Color.WHITE, 13, Gravity.START);      // 右：倒计时
        row.addView(right, new LinearLayout.LayoutParams(sideW, rowH));
        root.addView(row);

        big = side(0xFFFFB74D, 22, Gravity.CENTER_HORIZONTAL);
        root.addView(big, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        line2 = side(Color.WHITE, 12, Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        l2.topMargin = dp(2);
        root.addView(line2, l2);

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        blp.topMargin = dp(6);
        blp.bottomMargin = dp(12);
        root.addView(bar, blp);

        int totalW = sidePad * 2 + sideW * 2 + gap;
        lp = new WindowManager.LayoutParams(
                totalW,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // 左右对称，正中心对准摄像头
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = Math.max(0, camCx - totalW / 2);
        lp.y = Math.max(0, camTop + camH / 2 - rowH / 2);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) lp.setFitInsetsTypes(0);

        root.setOnTouchListener(new View.OnTouchListener() {
            long downAt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downAt = System.currentTimeMillis();
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - downAt > 500) {
                        ctx.startActivity(new Intent(ctx, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    } else {
                        expanded = !expanded;
                        applyExpanded();
                    }
                }
                return true;
            }
        });
        applyExpanded();
    }

    private void applyExpanded() {
        int vis = expanded ? View.VISIBLE : View.GONE;
        big.setVisibility(vis);
        line2.setVisibility(vis);
        bar.setVisibility(vis);
        bg.setCornerRadius(expanded ? dp(20) : rowH / 2f);
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
        left.setText(String.format(Locale.US, "¥%.2f", s.earnedToday));
        String r;
        switch (s.state) {
            case WORKING: r = SalaryCalc.hmsShort(s.countdownSec); break;
            case LUNCH: r = "午休"; break;
            case BEFORE_WORK: r = "未上班"; break;
            case OFF_WORK: r = "已下班"; break;
            default: r = "休息";
        }
        right.setText(r);
        if (expanded) {
            big.setText(String.format(Locale.US, "¥%.4f", s.earnedToday));
            String cd = s.countdownSec >= 0
                    ? s.countdownLabel.replace("距离", "距").replace("还有", "") + " " + SalaryCalc.hmsShort(s.countdownSec) : s.statusText;
            line2.setText(cd);
            bar.setProgress((int) (s.progress * 1000));
        }
    }
}
