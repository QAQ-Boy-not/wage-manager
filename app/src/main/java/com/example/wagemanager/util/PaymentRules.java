// PaymentRules.java - 支付/编辑/删除的业务规则校验
//
// 设计要点：
// 1. 业务规则的"能不能做"判断集中在这里，UI 和 ViewModel 调一个方法就够
// 2. 纯 JDK，无 Android 依赖，可 javac 直接测试
// 3. 规则：
//    - 标记已付：只能对未付记录
//    - 撤销付款：只能对已付记录
//    - 编辑：只能对未付记录（V1.1 强制结论：已付款不可改，仅可"撤销付款"）
//    - 删除：任何状态都可删

package com.example.wagemanager.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class PaymentRules {

    /** 支付相关操作类型 */
    public enum PaymentAction {
        MARK_PAID,        // 标记已付
        REVOKE_PAYMENT,   // 撤销付款
        EDIT,             // 编辑
        DELETE            // 删除
    }

    /** 操作校验错误（用于 UI 给出明确提示） */
    public enum PaymentError {
        NONE,
        ALREADY_PAID,         // 标记已付时记录已是已付
        NOT_PAID,             // 撤销付款时记录是未付
        CANNOT_EDIT_PAID      // 编辑已付记录
    }

    /** 时间显示格式（已付项显示 HH:mm） */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private PaymentRules() {
        // 工具类不允许实例化
    }

    /**
     * 校验指定操作是否允许在当前支付状态下执行。
     *
     * @param action 操作类型
     * @param isPaid 记录当前是否已付
     * @return 错误码，NONE 表示允许
     */
    public static PaymentError validate(PaymentAction action, boolean isPaid) {
        switch (action) {
            case MARK_PAID:
                return isPaid ? PaymentError.ALREADY_PAID : PaymentError.NONE;
            case REVOKE_PAYMENT:
                return isPaid ? PaymentError.NONE : PaymentError.NOT_PAID;
            case EDIT:
                return isPaid ? PaymentError.CANNOT_EDIT_PAID : PaymentError.NONE;
            case DELETE:
                return PaymentError.NONE;
            default:
                return PaymentError.NONE;
        }
    }

    /**
     * 格式化支付时间显示（已付项右上角显示 HH:mm）。
     * 入参 null 返回空串。
     */
    public static String formatPaidTime(LocalDateTime paidTime) {
        if (paidTime == null) return "";
        return HHMM.format(paidTime);
    }

    /**
     * 完整格式（带秒）：用于详情展示。
     */
    public static String formatPaidTimeFull(LocalDateTime paidTime) {
        if (paidTime == null) return "";
        return paidTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}