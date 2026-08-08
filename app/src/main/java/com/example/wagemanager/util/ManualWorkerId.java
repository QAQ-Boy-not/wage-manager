// ManualWorkerId.java - 手动录入工人的 worker_id 生成器
//
// 设计要点：
// 1. 必须以 "manual_" 前缀开头（与扫码白名单 id 区分，需求第七章强制）
// 2. 用 UUID 去连字符作为后缀，保证全局唯一
// 3. 纯 Java，可 javac 测试

package com.example.wagemanager.util;

import java.util.UUID;

public final class ManualWorkerId {

    /** 前缀常量：扫描识别的工人不会用此前缀，方便区分 */
    public static final String PREFIX = "manual_";

    private ManualWorkerId() {
        // 工具类不允许实例化
    }

    /**
     * 生成一个新的手动 worker_id，格式："manual_" + UUID 去连字符（32 位十六进制）。
     * 例如：manual_550e8400e29b41d4a716446655440000
     */
    public static String create() {
        return fromUuid(UUID.randomUUID());
    }

    /**
     * 从指定 UUID 构造 worker_id（用于单测断言输出可重复）。
     */
    static String fromUuid(UUID uuid) {
        return PREFIX + uuid.toString().replace("-", "");
    }
}
