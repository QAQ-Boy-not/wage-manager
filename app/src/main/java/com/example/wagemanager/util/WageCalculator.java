// WageCalculator.java - 工资记录汇总计算
//
// 设计要点：
// 1. 输入只关心"一条记录的金额和支付状态"，不关心工人信息（统计口径是"记录"而非"工人"）
// 2. 用 long 累加 + Math.addExact 防溢出
// 3. 纯 Java、无 Android 依赖，可 javac 直接测试

package com.example.wagemanager.util;

import java.util.Collection;

public final class WageCalculator {

    /** 单条记录的最小输入单元（用于汇总计算） */
    public static final class Entry {
        private final long wageCent;
        private final boolean isPaid;

        public Entry(long wageCent, boolean isPaid) {
            this.wageCent = wageCent;
            this.isPaid = isPaid;
        }

        public long getWageCent() {
            return wageCent;
        }

        public boolean isPaid() {
            return isPaid;
        }
    }

    /** 汇总结果 */
    public static final class Summary {
        private final long totalCent;
        private final int recordCount;
        private final int unpaidCount;
        private final int paidCount;

        public Summary(long totalCent, int recordCount, int unpaidCount, int paidCount) {
            this.totalCent = totalCent;
            this.recordCount = recordCount;
            this.unpaidCount = unpaidCount;
            this.paidCount = paidCount;
        }

        public long getTotalCent() {
            return totalCent;
        }

        public int getRecordCount() {
            return recordCount;
        }

        public int getUnpaidCount() {
            return unpaidCount;
        }

        public int getPaidCount() {
            return paidCount;
        }
    }

    private WageCalculator() {
        // 工具类不允许实例化
    }

    /**
     * 汇总一组工资记录。
     * 注意：按"记录数"统计，不按"工人数"去重（V1.1 强制结论：同工人同日允许多条）。
     */
    public static Summary summarize(Collection<Entry> records) {
        long totalCent = 0L;
        int recordCount = 0;
        int unpaidCount = 0;
        int paidCount = 0;

        for (Entry entry : records) {
            totalCent = Math.addExact(totalCent, entry.getWageCent());
            recordCount++;
            if (entry.isPaid()) {
                paidCount++;
            } else {
                unpaidCount++;
            }
        }

        return new Summary(totalCent, recordCount, unpaidCount, paidCount);
    }
}
