package com.baide.shangban;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import java.util.Calendar;
import java.util.Locale;

/**
 * 前台服务：每秒刷新一条“实时活动”通知。
 * Android 16（OriginOS 6）上会请求提升为 Live Update，由系统显示在状态栏胶囊 / 原子岛（灵动岛）中；
 * 同时可选显示一个顶部悬浮“灵动岛”胶囊，兼容所有系统。
 */
public class TimerService extends Service {
    public static final String ACTION_START = "com.baide.shangban.START";
    public static final String ACTION_STOP = "com.baide.shangban.STOP";
    public static final String ACTION_REFRESH = "com.baide.shangban.REFRESH";
    private static final String CHANNEL = "live_salary";
    private static final int NID = 1001;

    /** MainActivity 在前台时为 true，此时隐藏悬浮岛 */
    public static volatile boolean activityVisible = false;
    private static TimerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Config cfg;
    private IslandOverlay overlay;
    private NotificationManager nm;
    private int tick = 0;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 200);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, TimerService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, TimerService.class).setAction(ACTION_STOP));
    }

    public static void refreshConfig() {
        TimerService s = instance;
        if (s != null) s.handler.post(() -> {
            s.cfg = Config.load(s);
            s.syncOverlay();
        });
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "实时工资（灵动岛）", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("在状态栏 / 原子岛实时显示今日已赚");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        cfg = Config.load(this);
        overlay = new IslandOverlay(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(a)) {
            Config.setRunning(this, false);
            handler.removeCallbacks(loop);
            overlay.hide();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        Config.setRunning(this, true);
        cfg = Config.load(this);
        Notification n = build(SalaryCalc.compute(cfg, Calendar.getInstance()));
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NID, n);
        }
        handler.removeCallbacks(loop);
        handler.post(loop);
        return START_STICKY;
    }

    private void update() {
        SalaryCalc.Snapshot s = SalaryCalc.compute(cfg, Calendar.getInstance());
        syncOverlay();
        overlay.update(s);
        // 通知每秒刷新一次，避免被系统限流
        if (tick++ % 5 == 0) {
            try { nm.notify(NID, build(s)); } catch (Exception ignored) { }
        }
    }

    void syncOverlay() {
        boolean want = cfg.islandOverlay && !activityVisible
                && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this));
        if (want) overlay.show(); else overlay.hide();
    }

    private Notification build(SalaryCalc.Snapshot s) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pStop = PendingIntent.getService(this, 1,
                new Intent(this, TimerService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String money = String.format(Locale.US, "¥%.4f", s.earnedToday);
        String shortMoney = String.format(Locale.US, "¥%.2f", s.earnedToday);
        String title = s.statusText + " · 今日已赚 " + money;
        String text = s.countdownSec >= 0
                ? s.countdownLabel + " " + SalaryCalc.hmsShort(s.countdownSec)
                : s.statusText;
        if (s.state == SalaryCalc.State.WORKING) {
            text += String.format(Locale.US, " · ¥%.4f/秒", s.ratePerSec);
        }

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(String.format(Locale.US, "工时进度 %.1f%%", s.progress * 100))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColorized(false)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pOpen)
                .setProgress(1000, (int) (s.progress * 1000), false)
                .addAction(new Notification.Action.Builder(null, "关闭计时器", pStop).build());
        b.setStyle(new Notification.BigTextStyle().bigText(text + "\n" + s.funText));

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Update：状态栏胶囊 / 原子岛显示的短文字
            b.setShortCriticalText(s.state == SalaryCalc.State.WORKING ? shortMoney : s.statusText);
            Bundle ex = new Bundle();
            ex.putBoolean("android.requestPromotedOngoing", true);
            b.addExtras(ex);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(loop);
        if (overlay != null) overlay.hide();
        instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
