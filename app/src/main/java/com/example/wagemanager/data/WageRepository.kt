// WageRepository.kt - 工资业务仓库
//
// 设计要点：
// 1. 业务校验放在 Repository，不让 ViewModel / UI 直接接触 Room
// 2. 写工人和写工资用同一个事务（database.withTransaction）：
//    工资写入失败时不会遗留"无工资记录的孤儿工人"
// 3. Clock 通过构造注入，方便测试固定"今天"
//
// 注意：WageRepository.kt 不允许被 javac 测试（依赖 AndroidX），Room 部分由 CI 验证。

package com.example.wagemanager.data

import androidx.room.withTransaction
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.ManualWorkerId
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 手动录入时如何处理同名工人。
 * - CreateNew：始终新建一个 manual_xxx 工人
 * - Reuse：复用指定 id 的已有工人
 */
sealed interface ManualWorkerChoice {
    data object CreateNew : ManualWorkerChoice
    data class Reuse(val workerId: String) : ManualWorkerChoice
}

class WageRepository(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val workerDao: WorkerDao get() = database.workerDao()
    private val wageRecordDao: WageRecordDao get() = database.wageRecordDao()

    /**
     * 观察指定日期的所有工资记录（带工人信息）。
     * Room 写入后 Flow 自动发射新值，UI 自动刷新。
     */
    fun observeRecords(workDate: LocalDate): Flow<List<WageRecordWithWorker>> {
        return wageRecordDao.observeByWorkDate(workDate)
    }

    /**
     * 按姓名精确查工人（同名校验用）。
     */
    suspend fun findWorkersByExactName(name: String): List<Worker> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        return workerDao.findByExactName(trimmed)
    }

    /**
     * 手动登记工资。
     *
     * 业务规则：
     * - name trim 后不能为空
     * - wageCent 必须 > 0
     * - workDate 不能晚于今天
     * - workerChoice=Reuse 时 workerId 必须存在
     *
     * 事务保证：写工人和写工资是原子的，工资失败不会留孤儿工人。
     *
     * @return 新工资记录的自增 id
     */
    suspend fun registerManualWage(
        name: String,
        wageCent: Long,
        workDate: LocalDate,
        workerChoice: ManualWorkerChoice
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }
        require(DateRules.isWorkDateAllowed(workDate, LocalDate.now(clock))) {
            "出工日期不能晚于今天"
        }

        return database.withTransaction {
            // 1. 决定 Worker：复用 or 新建
            val worker = when (workerChoice) {
                ManualWorkerChoice.CreateNew -> {
                    val newWorker = Worker(
                        id = ManualWorkerId.create(),
                        name = normalizedName,
                        qrRaw = null,
                        qrcodePath = null,
                        isManual = true,
                        firstWorkDate = workDate
                    )
                    workerDao.insert(newWorker)
                    newWorker
                }
                is ManualWorkerChoice.Reuse -> {
                    workerDao.findById(workerChoice.workerId)
                        ?: error("复用的工人不存在：${workerChoice.workerId}")
                }
            }

            // 2. 写工资记录
            wageRecordDao.insert(
                WageRecord(
                    workerId = worker.id,
                    workDate = workDate,
                    wageCent = wageCent,
                    isPaid = false,
                    paidTime = null,
                    createTime = LocalDateTime.now(clock).withNano(0)
                )
            )
        }
    }

    /**
     * 标记已付。
     *
     * @return true 表示成功（记录存在且原状态是未付）；false 表示记录不存在或已是已付
     */
    suspend fun markPaid(recordId: Long): Boolean {
        val paidTime = LocalDateTime.now(clock).withNano(0)
        val rows = wageRecordDao.markPaid(recordId, paidTime)
        return rows > 0
    }

    /**
     * 撤销付款（已付 → 未付，paid_time 置空）。
     *
     * @return true 表示成功（记录存在且原状态是已付）；false 表示记录不存在或已是未付
     */
    suspend fun revokePayment(recordId: Long): Boolean {
        val rows = wageRecordDao.revokePayment(recordId)
        return rows > 0
    }

    /**
     * 编辑未付记录的姓名和工资。
     * 业务规则：已付款记录不可改，仅可"撤销付款"。
     *
     * 命名复用：传 name 让调用方可以同名复用 / 新建同名工人，逻辑跟 registerManualWage 一致。
     *
     * @return true 表示成功（记录存在且原状态是未付）
     */
    suspend fun updateWage(
        recordId: Long,
        name: String,
        wageCent: Long,
        workerChoice: ManualWorkerChoice
    ): Boolean {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }

        return database.withTransaction {
            // 1. 决定 Worker：复用 or 新建
            val worker = when (workerChoice) {
                ManualWorkerChoice.CreateNew -> {
                    val existing = wageRecordDao.findById(recordId)
                    val workDate = existing?.record?.workDate ?: LocalDate.now(clock)
                    val newWorker = Worker(
                        id = ManualWorkerId.create(),
                        name = normalizedName,
                        qrRaw = null,
                        qrcodePath = null,
                        isManual = true,
                        firstWorkDate = workDate
                    )
                    workerDao.insert(newWorker)
                    newWorker
                }
                is ManualWorkerChoice.Reuse -> {
                    workerDao.findById(workerChoice.workerId)
                        ?: error("复用的工人不存在：${workerChoice.workerId}")
                }
            }

            // 2. 改工资记录（DAO 的 WHERE 含 is_paid=0 保护，已付记录改不动）
            val rows = wageRecordDao.updateWage(recordId, worker.id, wageCent)
            rows > 0
        }
    }

    /**
     * 删除单条工资记录。删除不级联删工人（V1.1 强制）。
     *
     * @return true 表示成功
     */
    suspend fun deleteRecord(recordId: Long): Boolean {
        val rows = wageRecordDao.deleteById(recordId)
        return rows > 0
    }

    /**
     * 按 id 查单条记录（编辑模式预填用）。
     */
    suspend fun findRecordById(recordId: Long): WageRecordWithWorker? {
        return wageRecordDao.findById(recordId)
    }
}
