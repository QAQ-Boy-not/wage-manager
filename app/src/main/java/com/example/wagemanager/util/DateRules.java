// DateRules.java - 出工日期规则与中文格式化
//
// 设计要点：
// 1. 格式化统一用 DateTimeFormatter + Locale.CHINA，不硬编码"年月日星期"
// 2. 纯 Java，可 javac 测试
//
// 历史：之前有 isWorkDateAllowed(workDate, today) 限制"出工日期不能晚于今天"，
// 但跟 M3 决策矛盾（M3 允许预登任意远的活），已在 M3.2 删除。
// WageRepository 也不再 require 这个检查。

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
