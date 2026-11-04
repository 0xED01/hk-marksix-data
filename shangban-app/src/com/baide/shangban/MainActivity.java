package com.baide.shangban;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUE = 0xFF27408B;
    private static final int ORANGE = 0xFFB4501E;
    private static final int GRAY = 0xFF666666;
    private static final int BG = 0xFFF7F6F2;
    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Config cfg;
    private TextView tvCompany, tvDate, tvStatus, tvCountdown, tvEarned, tvRate, tvFun,
            tvProgress, tvWorked, tvMonth, tvHourly, tvDaily, tvPromoState;
    private ProgressBar pb;
    private Button btnTimer;
    private Switch swOverlay;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 100);
        }
    };

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        cfg = Config.load(this);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        TimerService.activityVisible = true;
        TimerService.refreshConfig();
        cfg = Config.load(this);
        swOverlay.setChecked(cfg.islandOverlay && canOverlay());
        updatePromoState();
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        TimerService.activityVisible = false;
        TimerService.refreshConfig();
        handler.removeCallbacks(ticker);
    }

    // ---------------- UI ----------------

    private TextView tv(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams mt(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFE2E0DA);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFitsSystemWindows(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(14), dp(22), dp(28));
        sv.addView(col);

        // 标题栏
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView title = tv("上班鼓励器", 19, 0xFF222222, true);
        title.setPadding(dp(10), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView set = tv("⚙ 设置", 15, GRAY, false);
        set.setPadding(dp(10), dp(8), dp(4), dp(8));
        set.setOnClickListener(v -> showSettings());
        head.addView(set);
        col.addView(head);

        tvCompany = tv(cfg.company, 13, GRAY, false);
        col.addView(tvCompany, mt(4));
        col.addView(divider(), mt(12));

        tvDate = tv("", 15, 0xFF444444, false);
        tvDate.setTypeface(Typeface.MONOSPACE);
        col.addView(tvDate, mt(16));

        tvStatus = tv("", 30, BLUE, true);
        col.addView(tvStatus, mt(14));
        tvCountdown = tv("", 17, 0xFF333333, false);
        col.addView(tvCountdown, mt(4));

        col.addView(tv("今日已赚", 14, GRAY, false), mt(22));
        tvEarned = tv("", 42, ORANGE, true);
        tvEarned.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        col.addView(tvEarned, mt(2));
        tvRate = tv("", 14, 0xFF444444, false);
        col.addView(tvRate, mt(2));
        tvFun = tv("", 15, 0xFF333333, false);
        col.addView(tvFun, mt(10));

        btnTimer = new Button(this);
        btnTimer.setTextColor(Color.WHITE);
        btnTimer.setTextSize(16);
        btnTimer.setTypeface(Typeface.DEFAULT_BOLD);
        btnTimer.setBackground(round(0xFF1C2230, 10));
        btnTimer.setStateListAnimator(null);
        btnTimer.setOnClickListener(v -> toggleTimer());
        LinearLayout.LayoutParams bp = mt(18);
        bp.height = dp(52);
        col.addView(btnTimer, bp);

        tvProgress = tv("", 14, GRAY, false);
        col.addView(tvProgress, mt(22));
        tvWorked = tv("", 14, GRAY, false);
        col.addView(tvWorked, mt(2));
        pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        GradientDrawable track = round(0xFFE6E4DE, 3);
        ClipDrawable fill = new ClipDrawable(round(BLUE, 3), Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable ld = new LayerDrawable(new android.graphics.drawable.Drawable[]{track, fill});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        pb.setProgressDrawable(ld);
        LinearLayout.LayoutParams pp = mt(8);
        pp.height = dp(6);
        col.addView(pb, pp);

        col.addView(divider(), mt(20));

        LinearLayout month = new LinearLayout(this);
        month.setGravity(Gravity.CENTER_VERTICAL);
        month.addView(tv("本月累计", 15, 0xFF444444, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvMonth = tv("", 20, BLUE, true);
        tvMonth.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        month.addView(tvMonth);
        col.addView(month, mt(16));
        col.addView(tv("已过的日子按满勤补算。本月只计已赚，不含亏损。", 13, GRAY, false), mt(4));

        col.addView(divider(), mt(16));
        tvHourly = tv("", 14, GRAY, false);
        col.addView(tvHourly, mt(14));
        tvDaily = tv("", 14, GRAY, false);
        col.addView(tvDaily, mt(4));

        // 灵动岛设置
        LinearLayout island = new LinearLayout(this);
        island.setOrientation(LinearLayout.VERTICAL);
        island.setBackground(round(Color.WHITE, 12));
        island.setPadding(dp(16), dp(14), dp(16), dp(14));
        col.addView(island, mt(22));
        island.addView(tv("灵动岛 / 原子岛显示", 16, 0xFF222222, true));

        swOverlay = new Switch(this);
        swOverlay.setText("顶部悬浮灵动岛（自动包住摄像头，点击展开，长按打开）");
        swOverlay.setTextSize(14);
        swOverlay.setOnCheckedChangeListener((v, on) -> {
            if (on && !canOverlay()) {
                swOverlay.setChecked(false);
                Toast.makeText(this, "请允许“上班鼓励器”显示在其他应用上层", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                cfg.islandOverlay = true;
                cfg.save(this);
                return;
            }
            cfg.islandOverlay = on;
            cfg.save(this);
            TimerService.refreshConfig();
        });
        island.addView(swOverlay, mt(8));

        tvPromoState = tv("", 13, GRAY, false);
        island.addView(tvPromoState, mt(10));

        Button promo = smallBtn("开启实时活动通知（状态栏胶囊 / 原子岛）");
        promo.setOnClickListener(v -> openPromotionSettings());
        island.addView(promo, mt(8));

        Button battery = smallBtn("允许后台运行（防止被系统清理）");
        battery.setOnClickListener(v -> requestBattery());
        island.addView(battery, mt(8));

        island.addView(tv("iQOO / OriginOS 小贴士：\n"
                + "1. 先点“开启计时器”，再回到桌面，原子岛/顶部胶囊就会实时跳钱。\n"
                + "2. 设置 → 电池 → 后台耗电管理 → 上班鼓励器 → 允许后台高耗电。\n"
                + "3. 设置 → 应用与权限 → 权限管理 → 上班鼓励器 → 打开“自启动”和“悬浮窗”。\n"
                + "4. 在最近任务里下拉锁定本 App，避免一键清理。", 12, GRAY, false), mt(12));

        return sv;
    }

    private Button smallBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(BLUE);
        b.setBackground(round(0xFFEDF0FA, 8));
        b.setStateListAnimator(null);
        return b;
    }

    // ---------------- 逻辑 ----------------

    private void refresh() {
        Calendar now = Calendar.getInstance();
        SalaryCalc.Snapshot s = SalaryCalc.compute(cfg, now);
        tvCompany.setText(cfg.company);
        tvDate.setText(String.format(Locale.US, "%04d-%02d-%02d %s %02d:%02d:%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
                WEEK[now.get(Calendar.DAY_OF_WEEK) - 1],
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)));
        tvStatus.setText(s.statusText);
        tvStatus.setTextColor(s.state == SalaryCalc.State.WORKING ? BLUE : 0xFF4A6E3A);
        tvCountdown.setText(s.countdownSec >= 0 ? s.countdownLabel + "  " + SalaryCalc.hms(s.countdownSec) : "");
        tvEarned.setText(String.format(Locale.US, "¥%.4f", s.earnedToday));
        tvRate.setText(String.format(Locale.US, "当前赚钱速度  ¥%.4f / 秒",
                s.state == SalaryCalc.State.WORKING ? s.ratePerSec : 0.0));
        tvFun.setText(s.funText);
        tvProgress.setText(String.format(Locale.US, "今日工时进度 %.1f%%", s.progress * 100));
        tvWorked.setText(String.format(Locale.US, "已工作 %.2f / %.2f 小时", s.workedHours, s.totalHours));
        pb.setProgress((int) (s.progress * 1000));
        tvMonth.setText(String.format(Locale.US, "¥%.4f", s.monthTotal));
        tvHourly.setText(String.format(Locale.US, "时薪 ¥%.2f   ·   月薪 ¥%.2f", s.hourlyWage, cfg.monthlySalary));
        tvDaily.setText(String.format(Locale.US, "今日满勤可得 ¥%.2f", s.dailyWage));
        btnTimer.setText(Config.isRunning(this) ? "关闭计时器" : "开启计时器（灵动岛）");
    }

    private void toggleTimer() {
        if (Config.isRunning(this)) {
            TimerService.stop(this);
            Config.setRunning(this, false);
        } else {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            TimerService.start(this);
            Config.setRunning(this, true);
            Toast.makeText(this, "计时器已开启，回到桌面看看灵动岛吧", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void updatePromoState() {
        String s;
        if (Build.VERSION.SDK_INT >= 36) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            boolean ok = false;
            try { ok = nm.canPostPromotedNotifications(); } catch (Throwable ignored) { }
            s = ok ? "✅ 实时活动通知：已允许（Android 16 Live Update）"
                    : "⚠️ 实时活动通知：未允许，点下方按钮打开";
        } else {
            s = "当前系统低于 Android 16，使用普通常驻通知 + 悬浮胶囊";
        }
        s += "\n" + (canOverlay() ? "✅ 悬浮窗权限：已允许" : "⚠️ 悬浮窗权限：未允许");
        tvPromoState.setText(s);
    }

    private void openPromotionSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 36) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS);
        } else {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        }
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (Exception e) {
            Intent f = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try { startActivity(f); } catch (Exception ignored) { }
        }
    }

    private void requestBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "已允许后台运行 ✅", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------------- 设置对话框 ----------------

    private EditText field(LinearLayout parent, String label, String value, int type) {
        parent.addView(tv(label, 13, GRAY, false), mt(10));
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private void showSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(6), dp(22), dp(6));
        sv.addView(c);

        int text = InputType.TYPE_CLASS_TEXT;
        int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText eCompany = field(c, "公司名称", cfg.company, text);
        EditText eSalary = field(c, "月工资（元）", trim(cfg.monthlySalary), num);
        EditText eDays = field(c, "月计薪天数（法定标准 21.75）", trim(cfg.payDaysPerMonth), num);
        EditText eStart = field(c, "上班时间（如 8:30）", Config.hm(cfg.startMin), text);
        EditText eEnd = field(c, "下班时间（如 17:30）", Config.hm(cfg.endMin), text);
        CheckBox cbLunch = new CheckBox(this);
        cbLunch.setText("午休不计工资");
        cbLunch.setChecked(cfg.lunchEnabled);
        c.addView(cbLunch, mt(10));
        EditText eLs = field(c, "午休开始", Config.hm(cfg.lunchStartMin), text);
        EditText eLe = field(c, "午休结束", Config.hm(cfg.lunchEndMin), text);

        c.addView(tv("上班的日子", 13, GRAY, false), mt(10));
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        LinearLayout row = new LinearLayout(this);
        CheckBox[] cbs = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            cbs[i] = new CheckBox(this);
            cbs[i].setText(names[i]);
            cbs[i].setChecked(cfg.workdays[i]);
            row.addView(cbs[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        c.addView(row);

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(sv)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        Config n = Config.load(this);
                        n.company = eCompany.getText().toString().trim();
                        n.monthlySalary = Double.parseDouble(eSalary.getText().toString());
                        n.payDaysPerMonth = Double.parseDouble(eDays.getText().toString());
                        n.startMin = Config.parseHm(eStart.getText().toString());
                        n.endMin = Config.parseHm(eEnd.getText().toString());
                        n.lunchEnabled = cbLunch.isChecked();
                        n.lunchStartMin = Config.parseHm(eLs.getText().toString());
                        n.lunchEndMin = Config.parseHm(eLe.getText().toString());
                        for (int i = 0; i < 7; i++) n.workdays[i] = cbs[i].isChecked();
                        if (n.startMin < 0 || n.endMin <= n.startMin || n.monthlySalary <= 0 || n.payDaysPerMonth <= 0
                                || (n.lunchEnabled && (n.lunchStartMin < 0 || n.lunchEndMin <= n.lunchStartMin))) {
                            throw new IllegalArgumentException();
                        }
                        n.save(this);
                        cfg = n;
                        TimerService.refreshConfig();
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "格式不对，请检查（时间如 8:30）", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
