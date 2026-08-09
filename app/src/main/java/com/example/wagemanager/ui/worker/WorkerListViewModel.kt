// WorkerListViewModel.kt - 首页 ViewModel（V1.3 按工人聚合）
//
// 职责：
// - 观察 work_date 的所有订单 Flow
// - 按 worker_id 分组聚合成 WorkerGroup
// - 区分未付 / 已付 tab
// - 计算全局汇总（今日未付总额 / 已付总额）
// - 控制批量添加 BottomSheet 的显示

package com.example.wagemanager.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.WageRecordWithWorker
import com.example.wagemanager.data.WageRepository
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
    val isMarkAllPaidConfirmVisible: Boolean = false
) {
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
    private val _isMarkAllPaidConfirmVisible = MutableStateFlow(false)

    val uiState: StateFlow<WorkerListUiState> = combine(
        _workDate.flatMapLatest { date -> repository.observeBillsByWorkDate(date) },
        _workDate,
        _isPaidTab,
        _isBatchSheetVisible,
        _isMarkAllPaidConfirmVisible
    ) { bills, workDate, isPaidTab, isSheetVisible, isMarkAllConfirm ->
        val items = bills.map { record ->
            BillItem(
                recordId = record.record.id,
                wageCent = record.record.wageCent,
                workDate = record.record.workDate,
                isPaid = record.record.isPaid,
                paidTime = record.record.paidTime,
                createdAt = record.record.createTime,
                worksiteName = record.worksiteName,
                notes = record.record.notes
            )
        }

        val groups = bills.groupBy { it.record.workerId }
            .map { (workerId, groupBills) ->
                val groupItems = groupBills.map { it.toBillItem() }
                val unpaid = groupItems.filter { !it.isPaid }
                val paid = groupItems.filter { it.isPaid }
                WorkerGroup(
                    workerId = workerId,
                    workerName = groupBills.first().workerName,
                    unpaidCent = unpaid.sumOf { it.wageCent },
                    unpaidCount = unpaid.size,
                    paidCent = paid.sumOf { it.wageCent },
                    paidCount = paid.size,
                    unpaidBills = unpaid.sortedByDescending { it.recordId },
                    paidBills = paid.sortedByDescending { it.recordId }
                )
        }

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
            isMarkAllPaidConfirmVisible = isMarkAllConfirm
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

    // ===== Bug 3：全部标记已付 =====

    fun onMarkAllPaidClick() {
        _isMarkAllPaidConfirmVisible.value = true
    }

    fun onMarkAllPaidDismiss() {
        _isMarkAllPaidConfirmVisible.value = false
    }

    fun onMarkAllPaidConfirm() {
        _isMarkAllPaidConfirmVisible.value = false
        viewModelScope.launch {
            try {
                repository.markAllPaidByDate(_workDate.value)
            } catch (e: Exception) {
                // 失败由 WorkerListScreen 的 LaunchedEffect eventFlow 处理
            }
        }
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

/** WageRecordWithWorker → BillItem 转换 */
private fun WageRecordWithWorker.toBillItem(): BillItem = BillItem(
    recordId = record.id,
    wageCent = record.wageCent,
    workDate = record.workDate,
    isPaid = record.isPaid,
    paidTime = record.paidTime,
    createdAt = record.createTime,
    worksiteName = worksiteName,
    notes = record.notes
)