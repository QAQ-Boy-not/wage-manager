// WageRecordDao.kt - 工资记录表 DAO（V1.3 含 worksite_id 关联）
//
// 设计要点：
// 1. observeByWorkDate 返回 Flow：Room 写入后自动发射新列表，Compose 自动重组
// 2. JOIN workers + worksites：UI 一次性拿到工人姓名 + 工区名称
// 3. 不使用 DISTINCT / GROUP BY：同一工人同一天允许多条记录，每条独立显示

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
 * 首页列表所需的复合数据：工资记录 + 工人姓名 + 收款码路径 + 工区名称
 * 通过 Room JOIN 一次性查出，避免 UI 层多次查询。
 */
data class WageRecordWithWorker(
    @Embedded
    val record: WageRecord,

    @ColumnInfo(name = "worker_name")
    val workerName: String,

    @ColumnInfo(name = "worker_qrcode_path")
    val workerQrcodePath: String?,

    @ColumnInfo(name = "worksite_name")
    val worksiteName: String?
)

@Dao
internal interface WageRecordDao {

    /**
     * 插入单条工资记录，返回自增 id。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: WageRecord): Long

    /**
     * 批量插入多笔工资记录（V1.3 批量添加用）。
     * 用 @Insert 一次性插入，Room 会优化为单事务。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<WageRecord>): List<Long>

    /**
     * 观察指定日期的所有工资记录（带工人 + 工区信息）。
     * Room 写入后 Flow 自动发射新值。
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path,
            worksites.name AS worksite_name
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        LEFT JOIN worksites
            ON worksites.id = wage_records.worksite_id
        WHERE wage_records.work_date = :workDate
        ORDER BY wage_records.create_time DESC, wage_records.id DESC
        """
    )
    fun observeByWorkDate(workDate: LocalDate): Flow<List<WageRecordWithWorker>>

    /**
     * 按 id 查单条记录（带工人信息）。
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path,
            worksites.name AS worksite_name
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        LEFT JOIN worksites
            ON worksites.id = wage_records.worksite_id
        WHERE wage_records.id = :recordId
        LIMIT 1
        """
    )
    suspend fun findById(recordId: Long): WageRecordWithWorker?

    /**
     * 观察某工人的所有账单（详情页用）。
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path,
            worksites.name AS worksite_name
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        LEFT JOIN worksites
            ON worksites.id = wage_records.worksite_id
        WHERE wage_records.worker_id = :workerId
        ORDER BY wage_records.work_date DESC, wage_records.create_time DESC, wage_records.id DESC
        """
    )
    fun observeByWorkerId(workerId: String): Flow<List<WageRecordWithWorker>>

    /**
     * 观察某工人在某日期的账单（M3 时间维度）
     * WorkerDetailScreen 选定日期后用
     */
    @Query(
        """
        SELECT
            wage_records.*,
            workers.name AS worker_name,
            workers.qrcode_path AS worker_qrcode_path,
            worksites.name AS worksite_name
        FROM wage_records
        INNER JOIN workers
            ON workers.id = wage_records.worker_id
        LEFT JOIN worksites
            ON worksites.id = wage_records.worksite_id
        WHERE wage_records.worker_id = :workerId
          AND wage_records.work_date = :workDate
        ORDER BY wage_records.create_time DESC, wage_records.id DESC
        """
    )
    fun observeByWorkerIdAndDate(workerId: String, workDate: LocalDate): Flow<List<WageRecordWithWorker>>

    /**
     * 观察所有工人 + 各自账单汇总（首页用）。
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
     * 标记已付。
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
     * 批量标记已付（M2.1 Bug3）：把指定日期所有未付账单一次性标记为已付。
     * 返回受影响行数。
     */
    @Query(
        """
        UPDATE wage_records
        SET is_paid = 1, paid_time = :paidTime
        WHERE work_date = :workDate AND is_paid = 0
        """
    )
    suspend fun markAllPaidByDate(workDate: LocalDate, paidTime: LocalDateTime): Int

    /**
     * 撤销付款。
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
     * 编辑未付记录。
     */
    @Query(
        """
        UPDATE wage_records
        SET worker_id = :workerId,
            worksite_id = :worksiteId,
            wage_cent = :wageCent,
            notes = :notes
        WHERE id = :recordId AND is_paid = 0
        """
    )
    suspend fun updateWage(
        recordId: Long,
        workerId: String,
        worksiteId: String?,
        wageCent: Long,
        notes: String?
    ): Int

    /**
     * 删除单条工资记录。
     */
    @Query(
        """
        DELETE FROM wage_records
        WHERE id = :recordId
        """
    )
    suspend fun deleteById(recordId: Long): Int
}

/**
 * 工人汇总（首页用）。
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