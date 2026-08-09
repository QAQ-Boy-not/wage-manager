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

    /**
     * 观察所有工人及其账单汇总（首页工人列表用）。
     *
     * LEFT JOIN wage_records：没账单的工人也会显示（已付 0 / 未付 0）。
     * 用 SUM(CASE WHEN is_paid=0/1 THEN 1 ELSE 0 END) 区分未付/已付条数。
     * 排序：按最近出工日期倒序，没出工的工人排在最后（按 first_work_date 升序）。
     *
     * 注意：NULLS LAST 在 Room 的 SQLite 版本中可能不支持，
     * 如 CI 报错可改用 ORDER BY MAX(...) IS NULL, MAX(...) DESC 兼容写法。
     */
    @Query(
        """
        SELECT
            w.id AS worker_id,
            w.name AS worker_name,
            w.is_manual AS is_manual,
            w.first_work_date AS first_work_date,
            COUNT(r.id) AS total_count,
            COALESCE(SUM(CASE WHEN r.is_paid = 0 THEN 1 ELSE 0 END), 0) AS unpaid_count,
            COALESCE(SUM(CASE WHEN r.is_paid = 1 THEN 1 ELSE 0 END), 0) AS paid_count,
            COALESCE(SUM(CASE WHEN r.is_paid = 0 THEN r.wage_cent ELSE 0 END), 0) AS unpaid_cent,
            COALESCE(SUM(CASE WHEN r.is_paid = 1 THEN r.wage_cent ELSE 0 END), 0) AS paid_cent,
            MAX(r.work_date) AS latest_work_date
        FROM workers w
        LEFT JOIN wage_records r ON r.worker_id = w.id
        GROUP BY w.id, w.name, w.is_manual, w.first_work_date
        ORDER BY MAX(r.work_date) IS NULL ASC, MAX(r.work_date) DESC, w.first_work_date ASC
        """
    )
    fun observeWorkerSummaries(): Flow<List<WorkerSummary>>

    /**
     * 观察某工人的所有账单（详情页用）。
     * 按日期倒序，同日按 create_time 倒序、id 倒序。
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
        WHERE wage_records.worker_id = :workerId
        ORDER BY wage_records.work_date DESC, wage_records.create_time DESC, wage_records.id DESC
        """
    )
    fun observeByWorkerId(workerId: String): Flow<List<WageRecordWithWorker>>
}

/**
 * 工人汇总（首页用）。
 * 通过 Room 的 LEFT JOIN + GROUP BY 一次性算出每个工人的账单统计。
 */
data class WorkerSummary(
    @ColumnInfo(name = "worker_id")
    val workerId: String,

    @ColumnInfo(name = "worker_name")
    val workerName: String,

    @ColumnInfo(name = "is_manual")
    val isManual: Boolean,

    @ColumnInfo(name = "first_work_date")
    val firstWorkDate: LocalDate?,

    @ColumnInfo(name = "total_count")
    val totalCount: Int,

    @ColumnInfo(name = "unpaid_count")
    val unpaidCount: Int,

    @ColumnInfo(name = "paid_count")
    val paidCount: Int,

    @ColumnInfo(name = "unpaid_cent")
    val unpaidCent: Long,

    @ColumnInfo(name = "paid_cent")
    val paidCent: Long,

    @ColumnInfo(name = "latest_work_date")
    val latestWorkDate: LocalDate?
)
