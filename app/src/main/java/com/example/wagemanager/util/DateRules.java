// DateRules.java - 出工日期规则与中文格式化
//
// 设计要点：
// 1. 出工日期不允许晚于"今天"（未来日期拒绝）
// 2. 格式化统一用 DateTimeFormatter + Locale.CHINA，不硬编码"年月日星期"
// 3. 纯 Java，可 javac 测试

package com.example.wagemanager.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateRules {

    /** 中文格式：2026年8月7日 星期五 */
    private static final DateTimeFormatter CHINESE_DATE = DateTimeFormatter
            .ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);

    private DateRules() {
        // 工具类不允许实例化
    }

    /**
     * 判断出工日期是否允许登记。
     * 规则：work_date ≤ today（不允许未来日期）。
     */
    public static boolean isWorkDateAllowed(LocalDate workDate, LocalDate today) {
        if (workDate == null || today == null) {
            return false;
        }
        return !workDate.isAfter(today);
    }

    /**
     * 把 LocalDate 格式化为中文长格式（带星期）。
     * 例如 2026-08-07 → "2026年8月7日 星期五"
     */
    public static String formatChineseDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return CHINESE_DATE.format(date);
    }
}
