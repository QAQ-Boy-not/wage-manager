// WageRecord.kt - 工资记录表 Entity
//
// 字段对应需求文档第六章 wage_records 表：
//   id INT PK AUTO（自增主键）
//   worker_id TEXT NN（关联 workers.id；不强制 FK CASCADE）
//   work_date TEXT NN（出工日期 yyyy-MM-dd）
//   wage_cent INTEGER NN（工资金额，单位"分"，避免浮点误差）
//   is_paid INTEGER NN=0（0=未付，1=已付）
//   paid_time TEXT（支付时间 yyyy-MM-dd HH:mm:ss，NULL 表示未付）
//   create_time TEXT NN（记录创建时间）
//
// 约束（业务层维护）：
//   wage_cent > 0
//   work_date ≤ 今天
//   V1 不开 ON DELETE CASCADE（应用层维护一致性）

package com.example.wagemanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "wage_records",
    foreignKeys = [
        ForeignKey(
            entity = Worker::class,
            parentColumns = ["id"],
            childColumns = ["worker_id"],
            // 需求 V1 强制：不开启级联删除，由应用层维护一致性
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["worker_id"], name = "index_wage_records_worker_id"),
        Index(value = ["work_date"], name = "index_wage_records_work_date"),
        Index(value = ["is_paid"], name = "index_wage_records_is_paid")
    ]
)
data class WageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "worker_id")
    val workerId: String,

    @ColumnInfo(name = "work_date")
    val workDate: LocalDate,

    /** 工资金额，单位"分"。必须 > 0。 */
    @ColumnInfo(name = "wage_cent")
    val wageCent: Long,

    @ColumnInfo(name = "is_paid", defaultValue = "0")
    val isPaid: Boolean = false,

    @ColumnInfo(name = "paid_time")
    val paidTime: LocalDateTime? = null,

    @ColumnInfo(name = "create_time")
    val createTime: LocalDateTime
)
