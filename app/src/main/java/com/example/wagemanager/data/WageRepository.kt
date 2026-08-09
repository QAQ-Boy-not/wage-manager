// WageRepository.kt - 工资业务仓库（V1.2 工人模型）
//
// 设计要点：
// 1. 业务校验放在 Repository，不让 ViewModel / UI 直接接触 Room
// 2. 写工人和写工资用同一个事务（database.withTransaction）：
//    工资写入失败时不会遗留"无工资记录的孤儿工人"
// 3. Clock 通过构造注入，方便测试固定"今天"
//
// V1.2 模型变化：
// - registerBill 替代 registerManualWage（语义贴合新模型）
// - 新增 observeWorkerSummaries / observeWorkerDetail（首页 + 详情页用）
// - markPaid / revokePayment / updateWage / deleteRecord 保留（M2 用）
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

    // ============== V1.2 新增：首页 + 详情页查询 ==============

    /**
     * 观察所有工人 + 各自账单汇总（首页用）。
     */
    fun observeWorkerSummaries(): Flow<List<WorkerSummary>> {
        return wageRecordDao.observeWorkerSummaries()
    }

    /**
     * 观察某工人的所有账单（详情页用，按日期倒序）。
     */
    fun observeWorkerDetail(workerId: String): Flow<List<WageRecordWithWorker>> {
        return wageRecordDao.observeByWorkerId(workerId)
    }

    /**
     * 按 id 查工人（详情页基本信息用）。
     */
    suspend fun findWorkerById(workerId: String): Worker? {
        return workerDao.findById(workerId)
    }

    // ============== 同名查重（V1.2 简化版） ==============

    /**
     * 按姓名精确查工人（同名校验用）。
     */
    suspend fun findWorkersByExactName(name: String): List<Worker> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        return workerDao.findByExactName(trimmed)
    }

    // ============== 添加账单（V1.2 改名为 registerBill） ==============

    /**
     * 添加一张账单（V1.2 主入口）。
     *
     * 同名处理（V1.2 简化）：
     * - existingWorkerId == null：新建一个 manual_xxx 工人（首次添加 / 改名后无同名）
     * - existingWorkerId != null：用已存在工人（同名字符串选了"切换到该工人"）
     *
     * 业务规则：
     * - name trim 后不能为空
     * - wageCent 必须 > 0
     * - workDate 不能晚于今天
     *
     * 事务保证：写工人和写账单是原子的。
     *
     * @return 新账单的自增 id
     */
    suspend fun registerBill(
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
                workerDao.findById(existingWorkerId)
                    ?: error("指定的工人不存在：$existingWorkerId")
            } else {
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

    // ============== 标记 / 撤销 / 编辑 / 删除（M2 准备，V1.2 先保留） ==============

    /**
     * 标记已付。
     * @return true 表示成功（记录存在且原状态是未付）
     */
    suspend fun markPaid(recordId: Long): Boolean {
        val paidTime = LocalDateTime.now(clock).withNano(0)
        val rows = wageRecordDao.markPaid(recordId, paidTime)
        return rows > 0
    }

    /**
     * 撤销付款（已付 → 未付，paid_time 置空）。
     */
    suspend fun revokePayment(recordId: Long): Boolean {
        val rows = wageRecordDao.revokePayment(recordId)
        return rows > 0
    }

    /**
     * 编辑未付账单。
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
     * 删除单条账单。删除不级联删工人（V1.1 强制）。
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
