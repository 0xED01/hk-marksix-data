package com.baide.shangban;

import java.util.Calendar;
import java.util.Locale;

/** 根据设置和当前时间计算各种数值。 */
public class SalaryCalc {

    public enum State { BEFORE_WORK, WORKING, LUNCH, OFF_WORK, REST_DAY }

    public static class Snapshot {
        public State state;
        public String statusText;      // 上班中 / 午休中 ...
        public String countdownLabel;  // 距离下班还有
        public long countdownSec;      // 倒计时秒数（-1 表示无）
        public double earnedToday;
        public double ratePerSec;
        public double dailyWage;
        public double hourlyWage;
        public double workedHours;
        public double totalHours;
        public double progress;        // 0..1
        public double monthTotal;
        public String funText;
        public boolean isWorkday;
    }

    private static final String[] ITEM_NAMES = {
            "一瓶可乐", "一个煎饼果子", "一杯蜜雪冰城", "一份早餐", "一杯瑞幸",
            "一杯奶茶", "一份外卖", "一张电影票", "一顿火锅", "一件 T 恤",
            "一双袜子礼盒", "一次剧本杀", "一条牛仔裤", "一双运动鞋"};
    private static final double[] ITEM_PRICES = {
            3, 6, 8, 12, 15,
            18, 25, 45, 68, 79,
            88, 98, 129, 299};

    public static Snapshot compute(Config c, Calendar now) {
        Snapshot s = new Snapshot();
        int lunchLen = lunchLenMin(c);
        int workMinPerDay = Math.max(1, (c.endMin - c.startMin) - lunchLen);
        s.totalHours = workMinPerDay / 60.0;
        s.dailyWage = c.monthlySalary / c.payDaysPerMonth;
        s.hourlyWage = s.dailyWage / s.totalHours;
        s.ratePerSec = s.dailyWage / (workMinPerDay * 60.0);

        double secOfDay = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60
                + now.get(Calendar.SECOND) + now.get(Calendar.MILLISECOND) / 1000.0;
        s.isWorkday = isWorkday(c, now);

        double worked = workedSeconds(c, secOfDay);
        double start = c.startMin * 60.0, end = c.endMin * 60.0;
        double ls = c.lunchStartMin * 60.0, le = c.lunchEndMin * 60.0;
        boolean lunch = lunchLen > 0;

        if (!s.isWorkday) {
            s.state = State.REST_DAY;
            s.statusText = "休息日";
            worked = 0;
            Calendar next = nextWorkStart(c, now);
            s.countdownLabel = "距离下次上班还有";
            s.countdownSec = next == null ? -1 : (next.getTimeInMillis() - now.getTimeInMillis()) / 1000;
        } else if (secOfDay < start) {
            s.state = State.BEFORE_WORK;
            s.statusText = "还没上班";
            s.countdownLabel = "距离上班还有";
            s.countdownSec = (long) (start - secOfDay);
        } else if (lunch && secOfDay >= ls && secOfDay < le) {
            s.state = State.LUNCH;
            s.statusText = "午休中";
            s.countdownLabel = "距离午休结束还有";
            s.countdownSec = (long) (le - secOfDay);
        } else if (secOfDay < end) {
            s.state = State.WORKING;
            s.statusText = "上班中";
            s.countdownLabel = "距离下班还有";
            s.countdownSec = (long) Math.ceil(end - secOfDay);
        } else {
            s.state = State.OFF_WORK;
            s.statusText = "已下班";
            Calendar next = nextWorkStart(c, now);
            s.countdownLabel = "距离下次上班还有";
            s.countdownSec = next == null ? -1 : (next.getTimeInMillis() - now.getTimeInMillis()) / 1000;
        }

        s.earnedToday = worked * s.ratePerSec;
        s.workedHours = worked / 3600.0;
        s.progress = Math.min(1.0, worked / (workMinPerDay * 60.0));

        // 本月累计：已过去的工作日按满勤算 + 今天已赚
        Calendar d = (Calendar) now.clone();
        int today = now.get(Calendar.DAY_OF_MONTH);
        int pastDays = 0;
        for (int i = 1; i < today; i++) {
            d.set(Calendar.DAY_OF_MONTH, i);
            if (isWorkday(c, d)) pastDays++;
        }
        s.monthTotal = pastDays * s.dailyWage + s.earnedToday;

        s.funText = funText(s, worked, workMinPerDay * 60.0);
        return s;
    }

    private static int lunchLenMin(Config c) {
        if (!c.lunchEnabled) return 0;
        int a = Math.max(c.startMin, c.lunchStartMin);
        int b = Math.min(c.endMin, c.lunchEndMin);
        return Math.max(0, b - a);
    }

    /** 今天截至 secOfDay 已有效工作的秒数（扣除午休）。 */
    static double workedSeconds(Config c, double secOfDay) {
        double start = c.startMin * 60.0, end = c.endMin * 60.0;
        double w = overlap(start, end, 0, secOfDay);
        if (lunchLenMin(c) > 0) {
            w -= overlap(Math.max(start, c.lunchStartMin * 60.0), Math.min(end, c.lunchEndMin * 60.0), 0, secOfDay);
        }
        return Math.max(0, w);
    }

    private static double overlap(double a1, double a2, double b1, double b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    public static boolean isWorkday(Config c, Calendar cal) {
        int dow = cal.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 .. SATURDAY=7
        int idx = (dow + 5) % 7;                 // 周一=0 .. 周日=6
        return c.workdays[idx];
    }

    private static Calendar nextWorkStart(Config c, Calendar now) {
        Calendar d = (Calendar) now.clone();
        for (int i = 0; i < 14; i++) {
            if (i > 0) d.add(Calendar.DAY_OF_MONTH, 1);
            Calendar t = (Calendar) d.clone();
            t.set(Calendar.HOUR_OF_DAY, c.startMin / 60);
            t.set(Calendar.MINUTE, c.startMin % 60);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (isWorkday(c, t) && t.after(now)) return t;
        }
        return null;
    }

    private static String funText(Snapshot s, double worked, double total) {
        if (s.state == State.REST_DAY) return "今天休息，好好放松，工资照样在路上～";
        if (s.state == State.OFF_WORK) {
            return "今天收工！稳稳赚到 ¥" + String.format(Locale.US, "%.2f", s.earnedToday) + "，回家吧～";
        }
        if (s.state == State.BEFORE_WORK) return "还没开工，先吃个早餐，今天能赚 ¥" + String.format(Locale.US, "%.2f", s.dailyWage);
        double remainWorkSec = total - worked;
        for (int i = 0; i < ITEM_PRICES.length; i++) {
            if (ITEM_PRICES[i] > s.earnedToday) {
                double need = (ITEM_PRICES[i] - s.earnedToday) / s.ratePerSec;
                if (need > remainWorkSec) break;
                String prefix = s.state == State.LUNCH ? "午休回来后再过 " : "再过 ";
                return prefix + dur((long) need) + "，" + ITEM_NAMES[i] + "就赚回来了。";
            }
        }
        return "坚持住，再过 " + dur((long) remainWorkSec) + " 今天的 ¥" + String.format(Locale.US, "%.2f", s.dailyWage) + " 就全到手了！";
    }

    public static String dur(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60;
        if (h > 0) return h + " 小时 " + m + " 分";
        if (m > 0) return m + " 分钟";
        return sec + " 秒";
    }

    public static String hms(long sec) {
        if (sec < 0) return "--";
        long d = sec / 86400;
        long h = (sec % 86400) / 3600, m = (sec % 3600) / 60, ss = sec % 60;
        if (d > 0) return d + "天" + h + "小时" + m + "分" + ss + "秒";
        if (h > 0) return h + "小时" + m + "分" + ss + "秒";
        return m + "分" + ss + "秒";
    }

    public static String hmsShort(long sec) {
        if (sec < 0) return "--:--";
        long h = sec / 3600, m = (sec % 3600) / 60, ss = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, ss);
        return String.format(Locale.US, "%02d:%02d", m, ss);
    }
}
