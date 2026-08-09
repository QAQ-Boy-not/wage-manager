// WageRepository.kt - 工资业务仓库
//
// 设计要点：
// 1. 业务校验放在 Repository，不让 ViewModel / UI 直接接触 Room
// 2. 写工人和写工资用同一个事务（database.withTransaction）：
//    工资写入失败时不会遗留"无工资记录的孤儿工人"
// 3. Clock 通过构造注入，方便测试固定"今天"
//
// 同名处理（M2.1 简化）：
// - UI 弹"切换到该工人 / 改名新建"对话框
// - ViewModel 解析为 existingWorkerId: String?（null = 新建，非 null = 用已存在）
// - Repository 不再做 sealed ManualWorkerChoice 复杂分支，直接传 existingWorkerId
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
     * 同名处理：
     * - existingWorkerId == null：新建一个 manual_xxx 工人（首次登记 / 改名后无同名）
     * - existingWorkerId != null：用已存在工人（同名字符串选了"切换到该工人"）
     *
     * 业务规则：
     * - name trim 后不能为空
     * - wageCent 必须 > 0
     * - workDate 不能晚于今天
     *
     * 事务保证：写工人和写工资是原子的。
     *
     * @return 新工资记录的自增 id
     */
    suspend fun registerManualWage(
        name: String,
        wageCent: Long,
        workDate: LocalDate,
        existingWorkerId: String? = null
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }
        require(DateRules.isWorkDateAllowed(workDate, LocalDate.now(clock))) {
            "出工日期不能晚于今天"
        }

        return database.withTransaction {
            val worker = if (existingWorkerId != null) {
                // 切换到已存在工人
                workerDao.findById(existingWorkerId)
                    ?: error("指定的工人不存在：$existingWorkerId")
            } else {
                // 新建工人
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
     * 编辑未付记录。
     *
     * 同名处理跟 registerManualWage 一致：existingWorkerId 决定是新建工人还是切换到已存在。
     *
     * @return true 表示成功（记录存在且原状态是未付）
     */
    suspend fun updateWage(
        recordId: Long,
        name: String,
        wageCent: Long,
        existingWorkerId: String? = null
    ): Boolean {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }

        return database.withTransaction {
            val worker = if (existingWorkerId != null) {
                workerDao.findById(existingWorkerId)
                    ?: error("指定的工人不存在：$existingWorkerId")
            } else {
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

            val rows = wageRecordDao.updateWage(recordId, worker.id, wageCent)
            rows > 0
        }
    }

    /**
     * 删除单条工资记录。删除不级联删工人（V1.1 强制）。
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
