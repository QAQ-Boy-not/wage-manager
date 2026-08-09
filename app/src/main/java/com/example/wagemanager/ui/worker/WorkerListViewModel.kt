// WorkerListViewModel.kt - 首页 ViewModel（V1.3 按工人聚合）
//
// 职责：
// - 观察 work_date 的所有订单 Flow
// - 按 worker_id 分组聚合成 WorkerGroup
// - 区分未付 / 已付 tab
// - 计算全局汇总（今日未付总额 / 已付总额）
// - 控制批量添加 BottomSheet 的显示
//
// V1.3 关键变更：
// - 首页改为按工人聚合（不再是工人卡片列表）
// - 工人卡片显示：👤 姓名 + 今日应付金额 / 笔数 + 订单缩略
// - 状态分两组：未付 / 已付
// - FAB ➕ 打开 BatchAddBillSheet（批量添加）

package com.example.wagemanager.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.WageRecordWithWorker
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * UI 用的订单条目
 */
data class BillItem(
    val recordId: Long,
    val workerId: String,
    val wageCent: Long,
    val workDate: LocalDate,
    val worksiteName: String?,
    val notes: String?,
    val isPaid: Boolean,
    val paidTime: java.time.LocalDateTime?
)

/**
 * 工人聚合卡（首页显示用）
 */
data class WorkerGroup(
    val workerId: String,
    val workerName: String,
    val unpaidCent: Long,
    val unpaidCount: Int,
    val paidCent: Long,
    val paidCount: Int,
    val unpaidBills: List<BillItem>,
    val paidBills: List<BillItem>
) {
    val totalCent: Long get() = unpaidCent + paidCent
    val totalCount: Int get() = unpaidCount + paidCount
}

/**
 * 首页 UI 状态
 */
data class WorkerListUiState(
    val workDate: LocalDate = LocalDate.now(),
    val isPaidTab: Boolean = false,
    val workerGroups: List<WorkerGroup> = emptyList(),
    val totalUnpaidCent: Long = 0L,
    val totalUnpaidCount: Int = 0,
    val totalPaidCent: Long = 0L,
    val totalPaidCount: Int = 0,
    val isBatchSheetVisible: Boolean = false,
    val isLoading: Boolean = true
) {
    /** 当前 tab 应显示的工人组 */
    fun visibleGroups(): List<WorkerGroup> =
        workerGroups.filter { group ->
            if (isPaidTab) group.paidBills.isNotEmpty()
            else group.unpaidBills.isNotEmpty()
        }.sortedByDescending {
            if (isPaidTab) it.paidCent else it.unpaidCent
        }
}

class WorkerListViewModel(
    private val repository: WageRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _workDate = MutableStateFlow(LocalDate.now(clock))
    private val _isPaidTab = MutableStateFlow(false)
    private val _isBatchSheetVisible = MutableStateFlow(false)
    private val _workers = MutableStateFlow<List<Worker>>(emptyList())

    init {
        // 加载所有工人（用于按工人聚合时关联名字）
        viewModelScope.launch {
            // 简化：直接查所有工人（工人数量预期 < 50）
            _workers.value = emptyList() // 这里不需要了，由 wageRecord JOIN 出来
        }
    }

    val uiState: StateFlow<WorkerListUiState> = combine(
        _workDate.flatMapLatest { date -> repository.observeBillsByWorkDate(date) },
        _workDate,
        _isPaidTab,
        _isBatchSheetVisible
    ) { bills, workDate, isPaidTab, isSheetVisible ->
        val items = bills.map { record ->
            BillItem(
                recordId = record.record.id,
                workerId = record.record.workerId,
                wageCent = record.record.wageCent,
                workDate = record.record.workDate,
                worksiteName = record.worksiteName,
                notes = record.record.notes,
                isPaid = record.record.isPaid,
                paidTime = record.record.paidTime
            )
        }

        // 按 workerId 聚合
        val groups = items.groupBy { it.workerId }
            .map { (workerId, groupItems) ->
                val unpaid = groupItems.filter { !it.isPaid }
                val paid = groupItems.filter { it.isPaid }
                WorkerGroup(
                    workerId = workerId,
                    workerName = groupItems.first().workerName() ?: workerId.takeLast(4),
                    unpaidCent = unpaid.sumOf { it.wageCent },
                    unpaidCount = unpaid.size,
                    paidCent = paid.sumOf { it.wageCent },
                    paidCount = paid.size,
                    unpaidBills = unpaid.sortedByDescending { it.recordId },
                    paidBills = paid.sortedByDescending { it.recordId }
                )
            }

        // 全局汇总
        val totalUnpaid = items.filter { !it.isPaid }
        val totalPaid = items.filter { it.isPaid }

        WorkerListUiState(
            workDate = workDate,
            isPaidTab = isPaidTab,
            workerGroups = groups,
            totalUnpaidCent = totalUnpaid.sumOf { it.wageCent },
            totalUnpaidCount = totalUnpaid.size,
            totalPaidCent = totalPaid.sumOf { it.wageCent },
            totalPaidCount = totalPaid.size,
            isBatchSheetVisible = isSheetVisible,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkerListUiState()
    )

    fun onScreenResume() {
        val today = LocalDate.now(clock)
        if (_workDate.value != today) {
            _workDate.value = today
        }
    }

    fun onTabChange(toPaidTab: Boolean) {
        _isPaidTab.value = toPaidTab
    }

    fun onAddBillClick() {
        _isBatchSheetVisible.value = true
    }

    fun onAddBillDismiss() {
        _isBatchSheetVisible.value = false
    }

    companion object {
        fun factory(repository: WageRepository): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    WorkerListViewModel(repository)
                }
            }
        }
    }
}

/** WageRecordWithWorker → 工人姓名 */
private fun WageRecordWithWorker.workerName(): String = workerName