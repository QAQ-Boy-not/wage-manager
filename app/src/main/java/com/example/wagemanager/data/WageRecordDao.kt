// WageRecordDao.kt - 工资记录表 DAO
//
// 设计要点：
// 1. observeByWorkDate 返回 Flow：Room 写入后自动发射新列表，Compose 自动重组
// 2. JOIN workers 取工人姓名和收款码路径，UI 一次性拿到所有展示信息
// 3. 不使用 DISTINCT / GROUP BY：同一工人同一天允许多条记录，每条独立显示
// 4. 排序：先按 create_time 倒序，再按 id 倒序（确保同一时刻插入的多条有稳定顺序）

package com.example.wagemanager.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 首页列表所需的复合数据：工资记录 + 工人姓名 + 收款码路径。
 * 通过 Room JOIN 一次性查出，避免 UI 层多次查询。
 */
data class WageRecordWithWorker(
    @Embedded
    val record: WageRecord,

    @ColumnInfo(name = "worker_name")
    val workerName: String,

    @ColumnInfo(name = "worker_qrcode_path")
    val workerQrcodePath: String?
)

@Dao
internal interface WageRecordDao {

    /**
     * 插入工资记录，返回自增 id。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: WageRecord): Long

    /**
     * 观察指定日期的所有工资记录（带工人信息）。
     * Room 写入后 Flow 自动发射新值。
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        WHERE wage_records.work_date = :workDate
        ORDER BY wage_records.create_time DESC, wage_records.id DESC
        """
    )
    fun observeByWorkDate(workDate: LocalDate): Flow<List<WageRecordWithWorker>>

    /**
     * 按 id 查单条记录（带工人信息）。用于编辑模式预填、删除/撤销前的状态读取。
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        WHERE wage_records.id = :recordId
        LIMIT 1
        """
    )
    suspend fun findById(recordId: Long): WageRecordWithWorker?

    /**
     * 标记已付：is_paid=1, paid_time=now。
     * WHERE 包含 id=:recordId 是必须的（防止误改其他记录）。
     * 返回受影响行数，0 表示记录不存在。
     */
    @Query(
        """
        UPDATE wage_records
        SET is_paid = 1, paid_time = :paidTime
        WHERE id = :recordId AND is_paid = 0
        """
    )
    suspend fun markPaid(recordId: Long, paidTime: LocalDateTime): Int

    /**
     * 撤销付款：is_paid=0, paid_time=NULL。
     * WHERE 包含 id=:recordId AND is_paid=1：
     * - 防止对未付记录重复撤销
     * - 让"撤销"操作幂等（重复调没副作用）
     */
    @Query(
        """
        UPDATE wage_records
        SET is_paid = 0, paid_time = NULL
        WHERE id = :recordId AND is_paid = 1
        """
    )
    suspend fun revokePayment(recordId: Long): Int

    /**
     * 编辑未付记录：同时改 worker_id、work_date、wage_cent。
     * V1.1 强制：已付款记录不可改，仅可"撤销付款" → WHERE 含 is_paid=0。
     */
    @Query(
        """
        UPDATE wage_records
        SET worker_id = :workerId, wage_cent = :wageCent
        WHERE id = :recordId AND is_paid = 0
        """
    )
    suspend fun updateWage(recordId: Long, workerId: String, wageCent: Long): Int

    /**
     * 删除单条工资记录。返回受影响行数。
     * 注：删除工资记录不级联删除工人（V1.1 强制：工人档案保留）。
     */
    @Query(
        """
        DELETE FROM wage_records
        WHERE id = :recordId
        """
    )
    suspend fun deleteById(recordId: Long): Int
}
