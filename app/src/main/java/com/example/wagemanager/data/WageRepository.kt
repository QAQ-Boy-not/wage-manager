// WageRepository.kt - 工资业务仓库（V1.3 三实体 + 批量 + 同名强制改名）
//
// 设计要点：
// 1. 业务校验放在 Repository，不让 ViewModel / UI 直接接触 Room
// 2. 写工人 / 工区 / 工资用同一个事务（database.withTransaction）
// 3. Clock 通过构造注入，方便测试固定"今天"
//
// V1.3 新增：
// - Worksite CRUD
// - 批量添加订单 registerBills（多选工人 + 共享工区/金额/备注）
// - 同名强制改名：findWorkersByExactName(name) 非空 → 返回错误让 UI 强制改名
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
    private val worksiteDao: WorksiteDao get() = database.worksiteDao()

    // ============== V1.3：Worksite CRUD ==============

    /** 观察所有工区（管理页 + 批量添加下拉用） */
    fun observeWorksites(): Flow<List<Worksite>> = worksiteDao.observeAll()

    /** 创建工区 */
    suspend fun createWorksite(name: String, address: String): Worksite {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "工区名称不能为空" }
        require(address.isNotBlank()) { "工区地址不能为空" }
        val worksite = Worksite(
            id = ManualWorkerId.create().replace("manual_", "ws_"),  // 用 ws_<uuid>
            name = trimmedName,
            address = address.trim(),
            createdAt = LocalDateTime.now(clock).withNano(0)
        )
        worksiteDao.insert(worksite)
        return worksite
    }

    /** 按 id 查工区 */
    suspend fun findWorksiteById(worksiteId: String): Worksite? =
        worksiteDao.findById(worksiteId)

    // ============== V1.3：首页 + 详情页查询 ==============

    /** 观察所有工人 + 各自账单汇总（首页用） */
    fun observeWorkerSummaries(): Flow<List<WorkerSummary>> =
        wageRecordDao.observeWorkerSummaries()

    /** 观察某工人的所有账单（详情页用） */
    fun observeWorkerDetail(workerId: String): Flow<List<WageRecordWithWorker>> =
        wageRecordDao.observeByWorkerId(workerId)

    /** 观察某日所有账单（首页按工人聚合用，V1.3 新增） */
    fun observeBillsByWorkDate(workDate: LocalDate): Flow<List<WageRecordWithWorker>> =
        wageRecordDao.observeByWorkDate(workDate)

    /** 按 id 查工人 */
    suspend fun findWorkerById(workerId: String): Worker? =
        workerDao.findById(workerId)

    /** 一次性查所有工人（V1.3 批量添加 + 工人选择器用） */
    suspend fun listAllWorkers(): List<Worker> = workerDao.findAll()

    /** 新增工人（V1.3 管理页用，V1.3 强制改名：≥1 同名抛异常） */
    suspend fun insertWorker(name: String, workDate: LocalDate): Worker {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "工人姓名不能为空" }
        val existing = workerDao.findByExactName(trimmed)
        require(existing.isEmpty()) { "已存在同名工人「$trimmed」" }

        val newWorker = Worker(
            id = ManualWorkerId.create(),
            name = trimmed,
            qrRaw = null,
            qrcodePath = null,
            isManual = true,
            firstWorkDate = workDate
        )
        workerDao.insert(newWorker)
        return newWorker
    }

    // ============== V1.3：同名处理（强制改名） ==============

    /**
     * 按姓名精确查工人（同名校验用，V1.3 决策强制改名）。
     * 返回按首次登记日期升序的列表（最早创建的排前面）。
     *
     * UI 行为：
     * - 0 条 → 正常，让用户提交（自动新建）
     * - ≥1 条 → 输入框标红 + 禁用提交按钮（强制改名）
     */
    suspend fun findWorkersByExactName(name: String): List<Worker> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        return workerDao.findByExactName(trimmed)
    }

    // ============== V1.3：单笔添加账单 ==============

    /**
     * 添加一张账单（单笔入口）。
     *
     * V1.3 强制改名逻辑：
     * - name 必填
     * - 查同名：findWorkersByExactName
     *   - 0 同名 → 新建工人 + 写账单
     *   - ≥1 同名 → 抛 DuplicateNameException，UI 必须改名
     *
     * @param worksiteId 关联工区（M1/M2 可空，M3 强制）
     * @param notes 劳动内容（M1/M2 可空，M3 鼓励填）
     */
    suspend fun registerBill(
        name: String,
        wageCent: Long,
        workDate: LocalDate,
        worksiteId: String? = null,
        notes: String? = null
    ): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }
        require(DateRules.isWorkDateAllowed(workDate, LocalDate.now(clock))) {
            "出工日期不能晚于今天"
        }

        // V1.3 强制改名：≥1 同名抛异常
        val existing = workerDao.findByExactName(normalizedName)
        if (existing.isNotEmpty()) {
            throw DuplicateNameException(normalizedName)
        }

        return database.withTransaction {
            // 1. 新建工人（强制改名流程已通过校验，这里只可能 0 同名）
            val newWorker = Worker(
                id = ManualWorkerId.create(),
                name = normalizedName,
                qrRaw = null,
                qrcodePath = null,
                isManual = true,
                firstWorkDate = workDate
            )
            workerDao.insert(newWorker)

            // 2. 写账单
            wageRecordDao.insert(
                WageRecord(
                    workerId = newWorker.id,
                    worksiteId = worksiteId,
                    workDate = workDate,
                    wageCent = wageCent,
                    notes = notes,
                    isPaid = false,
                    paidTime = null,
                    createTime = LocalDateTime.now(clock).withNano(0)
                )
            )
        }
    }

    // ============== V1.3：批量添加账单 ==============

    /**
     * 批量添加多笔账单（首页 FAB 入口）。
     *
     * 所有订单共享同一工区 + 金额 + 备注 + 日期，工人不同。
     *
     * V1.3 强制改名逻辑：选中的工人里如果有任何名字有重名（理论上不会，
     * 因为 picker 列表就是从数据库查的），整个事务回滚。
     *
     * @param workerIds 工人 id 列表（必须全部存在于 workers 表）
     * @return 创建的订单数
     */
    suspend fun registerBills(
        workerIds: List<String>,
        worksiteId: String?,
        wageCent: Long,
        notes: String?,
        workDate: LocalDate
    ): Int {
        require(workerIds.isNotEmpty()) { "至少选一个工人" }
        require(wageCent > 0) { "工资金额必须 > 0" }
        require(DateRules.isWorkDateAllowed(workDate, LocalDate.now(clock))) {
            "出工日期不能晚于今天"
        }

        return database.withTransaction {
            val now = LocalDateTime.now(clock).withNano(0)
            val records = workerIds.map { workerId ->
                WageRecord(
                    workerId = workerId,
                    worksiteId = worksiteId,
                    workDate = workDate,
                    wageCent = wageCent,
                    notes = notes,
                    isPaid = false,
                    paidTime = null,
                    createTime = now
                )
            }
            wageRecordDao.insertAll(records)
            records.size
        }
    }

    // ============== 状态流转（M1.1 已实现，本里程碑复用） ==============

    suspend fun markPaid(recordId: Long): Boolean {
        val paidTime = LocalDateTime.now(clock).withNano(0)
        val rows = wageRecordDao.markPaid(recordId, paidTime)
        return rows > 0
    }

    /**
     * 批量标记指定日期所有未付账单（M2.1 Bug3）
     * @return 标记成功的笔数
     */
    suspend fun markAllPaidByDate(workDate: LocalDate): Int {
        val paidTime = LocalDateTime.now(clock).withNano(0)
        return wageRecordDao.markAllPaidByDate(workDate, paidTime)
    }

    suspend fun revokePayment(recordId: Long): Boolean {
        val rows = wageRecordDao.revokePayment(recordId)
        return rows > 0
    }

    suspend fun updateWage(
        recordId: Long,
        name: String,
        wageCent: Long,
        worksiteId: String? = null,
        notes: String? = null
    ): Boolean {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "工人姓名不能为空" }
        require(wageCent > 0) { "工资金额必须 > 0" }

        // V1.3 强制改名：编辑后名字不能跟别人重复
        val current = wageRecordDao.findById(recordId)
        val originalWorkerId = current?.record?.workerId
        val duplicates = workerDao.findByExactName(normalizedName)
            .filter { it.id != originalWorkerId }
        if (duplicates.isNotEmpty()) {
            throw DuplicateNameException(normalizedName)
        }

        return database.withTransaction {
            // 编辑模式：复用原 worker_id（不改工人）
            val workerId = originalWorkerId ?: return@withTransaction false
            val rows = wageRecordDao.updateWage(
                recordId, workerId, worksiteId, wageCent, notes
            )
            rows > 0
        }
    }

    suspend fun deleteRecord(recordId: Long): Boolean {
        val rows = wageRecordDao.deleteById(recordId)
        return rows > 0
    }

    suspend fun findRecordById(recordId: Long): WageRecordWithWorker? {
        return wageRecordDao.findById(recordId)
    }
}

/**
 * 同名冲突异常（V1.3 强制改名专用）。
 *
 * UI 捕获后应该：
 * 1. 在姓名输入框下显示"已存在「X」，请改名"
 * 2. 禁用提交按钮
 *
 * 不弹对话框，不自动切换 / 自动改名。
 */
class DuplicateNameException(val name: String) :
    Exception("已存在同名工人「$name」，请改名")