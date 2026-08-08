// WageBusinessRulesJavacTest.java - 纯 Java 业务规则单测
//
// 这不是 JUnit，是手动 main() + 自写断言。
// 设计目的：在不上 Gradle、不依赖 Android SDK 的情况下验证业务规则的正确性。
// 执行：见 plan 文件"纯 Java 单测"章节。

package com.example.wagemanager.util;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public final class WageBusinessRulesJavacTest {

    public static void main(String[] args) {
        testWageParsingValid();
        testWageParsingInvalid();
        testMoneyFormatting();
        testRecordSummarySameWorker();
        testRecordSummaryMixedPayment();
        testRecordSummaryEmpty();
        testDateRules();
        testManualWorkerId();
        System.out.println("All business rule tests passed.");
    }

    /** 自写断言：失败抛 AssertionError 终止测试 */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }

    // ===== 金额解析：合法输入 =====
    private static void testWageParsingValid() {
        MoneyUtils.ParseResult r;

        r = MoneyUtils.parseWageCent("280");
        check(r.isValid() && r.getWageCent() == 28000 && r.getError() == MoneyUtils.WageError.NONE,
                "整数 280 应解析为 28000 分");

        r = MoneyUtils.parseWageCent("280.5");
        check(r.isValid() && r.getWageCent() == 28050, "280.5 应解析为 28050 分");

        r = MoneyUtils.parseWageCent("280.50");
        check(r.isValid() && r.getWageCent() == 28050, "280.50 应解析为 28050 分");

        r = MoneyUtils.parseWageCent("0.01");
        check(r.isValid() && r.getWageCent() == 1, "0.01 应解析为 1 分");

        r = MoneyUtils.parseWageCent(" 100 ");
        check(r.isValid() && r.getWageCent() == 10000, "前后空白应被 trim");

        r = MoneyUtils.parseWageCent("99999999.99");
        check(r.isValid() && r.getWageCent() == 9999999999L, "大额应正常解析");
    }

    // ===== 金额解析：非法输入 =====
    private static void testWageParsingInvalid() {
        MoneyUtils.ParseResult r;

        r = MoneyUtils.parseWageCent(null);
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.REQUIRED, "null 应报 REQUIRED");

        r = MoneyUtils.parseWageCent("");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.REQUIRED, "空字符串应报 REQUIRED");

        r = MoneyUtils.parseWageCent("   ");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.REQUIRED, "纯空白应报 REQUIRED");

        r = MoneyUtils.parseWageCent("0");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.MUST_BE_POSITIVE, "0 应报 MUST_BE_POSITIVE");

        r = MoneyUtils.parseWageCent("0.00");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.MUST_BE_POSITIVE, "0.00 应报 MUST_BE_POSITIVE");

        r = MoneyUtils.parseWageCent("-1");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.INVALID_FORMAT, "-1 应报 INVALID_FORMAT");

        r = MoneyUtils.parseWageCent("1.234");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.INVALID_FORMAT, "三位小数应报 INVALID_FORMAT");

        r = MoneyUtils.parseWageCent("1,000");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.INVALID_FORMAT, "千分位逗号应报 INVALID_FORMAT");

        r = MoneyUtils.parseWageCent("abc");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.INVALID_FORMAT, "字母应报 INVALID_FORMAT");

        r = MoneyUtils.parseWageCent("1e5");
        check(!r.isValid() && r.getError() == MoneyUtils.WageError.INVALID_FORMAT, "科学计数法应报 INVALID_FORMAT");
    }

    // ===== 金额格式化 =====
    private static void testMoneyFormatting() {
        check("0.01".equals(MoneyUtils.formatCent(1)), "1 分 → 0.01 元");
        check("1.00".equals(MoneyUtils.formatCent(100)), "100 分 → 1.00 元");
        check("280.50".equals(MoneyUtils.formatCent(28050)), "28050 分 → 280.50 元");
        check("0.00".equals(MoneyUtils.formatCent(0)), "0 分 → 0.00 元");
    }

    // ===== 汇总：同一工人同一天两条未付（防 worker 去重回归） =====
    private static void testRecordSummarySameWorker() {
        WageCalculator.Summary s = WageCalculator.summarize(Arrays.asList(
                new WageCalculator.Entry(28000, false),
                new WageCalculator.Entry(28000, false)
        ));
        check(s.getTotalCent() == 56000, "合计应为 56000 分");
        check(s.getRecordCount() == 2, "记录数应为 2");
        check(s.getUnpaidCount() == 2, "未付应为 2 条");
        check(s.getPaidCount() == 0, "已付应为 0 条");
    }

    // ===== 汇总：混合支付状态 =====
    private static void testRecordSummaryMixedPayment() {
        WageCalculator.Summary s = WageCalculator.summarize(Arrays.asList(
                new WageCalculator.Entry(28000, false),
                new WageCalculator.Entry(10000, true)
        ));
        check(s.getTotalCent() == 38000, "合计应为 38000 分");
        check(s.getRecordCount() == 2, "记录数应为 2");
        check(s.getUnpaidCount() == 1, "未付应为 1 条");
        check(s.getPaidCount() == 1, "已付应为 1 条");
    }

    // ===== 汇总：空列表 =====
    private static void testRecordSummaryEmpty() {
        WageCalculator.Summary s = WageCalculator.summarize(Collections.<WageCalculator.Entry>emptyList());
        check(s.getTotalCent() == 0, "空列表合计应为 0");
        check(s.getRecordCount() == 0, "空列表记录数应为 0");
        check(s.getUnpaidCount() == 0, "空列表未付应为 0");
        check(s.getPaidCount() == 0, "空列表已付应为 0");
    }

    // ===== 日期规则 =====
    private static void testDateRules() {
        LocalDate today = LocalDate.of(2026, 8, 7);

        check(DateRules.isWorkDateAllowed(LocalDate.of(2026, 8, 7), today), "今天允许");
        check(DateRules.isWorkDateAllowed(LocalDate.of(2026, 8, 6), today), "昨天允许");
        check(!DateRules.isWorkDateAllowed(LocalDate.of(2026, 8, 8), today), "明天拒绝");
        check(!DateRules.isWorkDateAllowed(LocalDate.of(2027, 1, 1), today), "明年拒绝");

        check(!DateRules.isWorkDateAllowed(null, today), "null workDate 拒绝");
        check(!DateRules.isWorkDateAllowed(today, null), "null today 拒绝");

        String formatted = DateRules.formatChineseDate(LocalDate.of(2026, 8, 7));
        check(formatted.startsWith("2026年8月7日"), "中文日期应以'2026年8月7日'开头，实际：" + formatted);
        check(formatted.contains("星期"), "中文日期应包含'星期'，实际：" + formatted);
    }

    // ===== 手动 worker_id 生成 =====
    private static void testManualWorkerId() {
        String id1 = ManualWorkerId.create();
        String id2 = ManualWorkerId.create();

        check(id1.startsWith("manual_"), "id 应以 manual_ 开头：" + id1);
        check(id1.length() == "manual_".length() + 32, "id 长度应为前缀 + 32 位十六进制：" + id1);
        check(!id1.equals(id2), "两次 create 应产生不同 id");

        // 固定 UUID 输出可重复
        UUID fixed = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String expected = "manual_550e8400e29b41d4a716446655440000";
        String actual = ManualWorkerId.fromUuid(fixed);
        check(expected.equals(actual), "固定 UUID 输出错误，期望：" + expected + " 实际：" + actual);
    }
}
