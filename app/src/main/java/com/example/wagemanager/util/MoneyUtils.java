// MoneyUtils.java - 工资金额（元 ↔ 分）解析与格式化
//
// 设计要点：
// 1. 永远不经过 double —— 用 BigDecimal 精确转换，避免 0.1 + 0.2 那种浮点误差
// 2. 输入只接受普通十进制：整数 + 可选 1~2 位小数
// 3. 单位统一为"分"（Long），所有 Room/UI 都按分计算

package com.example.wagemanager.util;

import java.math.BigDecimal;

public final class MoneyUtils {

    /** 工资解析错误类型（UI 据此显示对应文案） */
    public enum WageError {
        NONE,
        REQUIRED,
        INVALID_FORMAT,
        MUST_BE_POSITIVE,
        OUT_OF_RANGE
    }

    /** 解析结果：成功时 wageCent 是有效金额；失败时 error 给出原因 */
    public static final class ParseResult {
        private final boolean valid;
        private final long wageCent;
        private final WageError error;

        private ParseResult(boolean valid, long wageCent, WageError error) {
            this.valid = valid;
            this.wageCent = wageCent;
            this.error = error;
        }

        public static ParseResult ok(long wageCent) {
            return new ParseResult(true, wageCent, WageError.NONE);
        }

        public static ParseResult fail(WageError error) {
            return new ParseResult(false, 0L, error);
        }

        public boolean isValid() {
            return valid;
        }

        public long getWageCent() {
            return wageCent;
        }

        public WageError getError() {
            return error;
        }
    }

    /** 合法输入：整数或带 1~2 位小数（如 280 / 280.5 / 280.50），自动 trim 前后空白 */
    private static final String WAGE_PATTERN = "\\d+(\\.\\d{1,2})?";

    private MoneyUtils() {
        // 工具类不允许实例化
    }

    /**
     * 解析用户输入的"元"字符串为"分"。
     *
     * @param input 来自 UI 输入框的原始字符串
     * @return ParseResult：成功时 wageCent 是金额（单位分）；失败时 error 说明原因
     */
    public static ParseResult parseWageCent(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return ParseResult.fail(WageError.REQUIRED);
        }
        if (!trimmed.matches(WAGE_PATTERN)) {
            return ParseResult.fail(WageError.INVALID_FORMAT);
        }

        try {
            BigDecimal yuan = new BigDecimal(trimmed);
            // movePointRight(2) 把元变成分；longValueExact 在超出 long 时抛异常
            long cent = yuan.movePointRight(2).longValueExact();
            if (cent <= 0) {
                return ParseResult.fail(WageError.MUST_BE_POSITIVE);
            }
            return ParseResult.ok(cent);
        } catch (ArithmeticException ex) {
            // longValueExact 超出范围 / 小数位不精确（虽然正则已限制，这里兜底）
            return ParseResult.fail(WageError.OUT_OF_RANGE);
        } catch (NumberFormatException ex) {
            return ParseResult.fail(WageError.INVALID_FORMAT);
        }
    }

    /**
     * 把"分"格式化为"元"显示字符串（始终保留两位小数）。
     * 例如 1 → "0.01"、100 → "1.00"、28050 → "280.50"
     */
    public static String formatCent(long wageCent) {
        BigDecimal yuan = BigDecimal.valueOf(wageCent).movePointLeft(2);
        return yuan.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
