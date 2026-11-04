package com.baide.shangban;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户设置（保存在 SharedPreferences）。 */
public class Config {
    public String company = "江苏百得外包服务有限公司上海分公司";
    public double monthlySalary = 2740.0;
    /** 月计薪天数：劳动法标准 21.75 天 */
    public double payDaysPerMonth = 21.75;
    public int startMin = 8 * 60 + 30;   // 08:30
    public int endMin = 17 * 60 + 30;    // 17:30
    public boolean lunchEnabled = true;
    public int lunchStartMin = 12 * 60;  // 12:00
    public int lunchEndMin = 13 * 60;    // 13:00
    /** 周一..周日 是否上班（双休：周一至周五） */
    public boolean[] workdays = {true, true, true, true, true, false, false};
    public boolean islandOverlay = true;

    private static final String P = "cfg";

    public static Config load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(P, Context.MODE_PRIVATE);
        Config k = new Config();
        k.company = sp.getString("company", k.company);
        k.monthlySalary = Double.longBitsToDouble(sp.getLong("salary", Double.doubleToLongBits(k.monthlySalary)));
        k.payDaysPerMonth = Double.longBitsToDouble(sp.getLong("payDays", Double.doubleToLongBits(k.payDaysPerMonth)));
        k.startMin = sp.getInt("start", k.startMin);
        k.endMin = sp.getInt("end", k.endMin);
        k.lunchEnabled = sp.getBoolean("lunch", k.lunchEnabled);
        k.lunchStartMin = sp.getInt("lunchStart", k.lunchStartMin);
        k.lunchEndMin = sp.getInt("lunchEnd", k.lunchEndMin);
        String wd = sp.getString("workdays", "1111100");
        for (int i = 0; i < 7 && i < wd.length(); i++) k.workdays[i] = wd.charAt(i) == '1';
        k.islandOverlay = sp.getBoolean("overlay", k.islandOverlay);
        return k;
    }

    public void save(Context c) {
        StringBuilder wd = new StringBuilder();
        for (boolean b : workdays) wd.append(b ? '1' : '0');
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
                .putString("company", company)
                .putLong("salary", Double.doubleToLongBits(monthlySalary))
                .putLong("payDays", Double.doubleToLongBits(payDaysPerMonth))
                .putInt("start", startMin)
                .putInt("end", endMin)
                .putBoolean("lunch", lunchEnabled)
                .putInt("lunchStart", lunchStartMin)
                .putInt("lunchEnd", lunchEndMin)
                .putString("workdays", wd.toString())
                .putBoolean("overlay", islandOverlay)
                .apply();
    }

    public static boolean isRunning(Context c) {
        return c.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean("running", false);
    }

    public static void setRunning(Context c, boolean r) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putBoolean("running", r).apply();
    }

    public static String hm(int min) {
        return String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    /** 解析 "8:30" / "08:30" → 分钟数，失败返回 -1 */
    public static int parseHm(String s) {
        try {
            String[] p = s.trim().replace('：', ':').split(":");
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }
}
