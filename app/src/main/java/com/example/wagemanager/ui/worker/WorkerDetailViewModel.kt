// WorkerDetailViewModel.kt - 工人详情页 ViewModel（V1.2 + M1.1 状态流转）
//
// 职责：
// - 观察某工人的所有账单（Room Flow）
// - 按支付状态分组（未付 / 已付）
// - 计算汇总（总数 / 总额）
// - 控制添加账单 BottomSheet 的显示
// - M1.1：状态流转（标记已付 / 撤销付款 / 删除 / 编辑）

package com.example.wagemanager.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.WageRecordWithWorker
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * UI 用的账单条目
 */
data class BillItem(
    val recordId: Long,
    val wageCent: Long,
    val workDate: LocalDate,
    val isPaid: Boolean,
    val paidTime: LocalDateTime?,
    val createdAt: LocalDateTime,
    val worksiteName: String? = null,   // V1.3 新增
    val notes: String? = null           // V1.3 新增
)

/**
 * 待二次确认的操作
 */
sealed interface PendingConfirmAction {
    data class MarkPaid(val recordId: Long, val workerName: String, val wageCent: Long) : PendingConfirmAction
    data class RevokePayment(val recordId: Long, val workerName: String) : PendingConfirmAction
    data class DeleteRecord(val recordId: Long, val workerName: String, val wageCent: Long) : PendingConfirmAction
}

/**
 * 详情页 UI 控制状态（跟数据流分开）
 */
data class DetailControlState(
    val isRegisterSheetVisible: Boolean = false,
    val actionMenuRecordId: Long? = null,
    val pendingConfirmAction: PendingConfirmAction? = null,
    val editingBillId: Long? = null
)

/**
 * 详情页 UI 状态
 */
data class WorkerDetailUiState(
    val workerId: String,
    val workerName: String = "",
    val firstWorkDate: LocalDate? = null,
    val selectedDate: LocalDate = LocalDate.now(),  // M3：选定日期
    val unpaidBills: List<BillItem> = emptyList(),
    val paidBills: List<BillItem> = emptyList(),
    val totalCount: Int = 0,
    val totalCent: Long = 0L,
    val unpaidCent: Long = 0L,
    val paidCent: Long = 0L,
    val isPaidTab: Boolean = false,
    val control: DetailControlState = DetailControlState(),
    val isWorkerNotFound: Boolean = false
) {
    /** 当前 tab 应显示的账单 */
    fun visibleBills(): List<BillItem> =
        if (isPaidTab) paidBills else unpaidBills

    fun findBillById(recordId: Long): BillItem? =
        (unpaidBills + paidBills).firstOrNull { it.recordId == recordId }
}

/**
 * 一次性事件（Toast）
 */
sealed interface WorkerDetailEvent {
    data class OperationFailed(val message: String) : WorkerDetailEvent
}

class WorkerDetailViewModel(
    private val repository: WageRepository,
    private val workerId: String,
    @Suppress("unused") private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _workerInfo = MutableStateFlow<Worker?>(null)
    private val _control = MutableStateFlow(DetailControlState())
    private val _isPaidTab = MutableStateFlow(false)
    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))  // M3：选定日期

    private val events = Channel<WorkerDetailEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _workerInfo.value = repository.findWorkerById(workerId)
        }
    }

    fun onDateChange(date: LocalDate) {
        _selectedDate.value = date
    }

    // 5 个 Flow combine（5 参数版本支持）
    val uiState: StateFlow<WorkerDetailUiState> = combine(
        _selectedDate,
        _selectedDate.flatMapLatest { date -> repository.observeWorkerBillsByDate(workerId, date) },
        _workerInfo,
        _control,
        _isPaidTab
    ) { date, records, worker, control, isPaidTab ->
        val items = records.map { record ->
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
        val unpaid = items.filter { !it.isPaid }
        val paid = items.filter { it.isPaid }
        WorkerDetailUiState(
            workerId = workerId,
            workerName = worker?.name ?: records.firstOrNull()?.workerName ?: "",
            firstWorkDate = worker?.firstWorkDate,
            unpaidBills = unpaid,
            paidBills = paid,
            totalCount = items.size,
            totalCent = items.sumOf { it.wageCent },
            unpaidCent = unpaid.sumOf { it.wageCent },
            paidCent = paid.sumOf { it.wageCent },
            isPaidTab = isPaidTab,
            selectedDate = date,
            control = control,
            isWorkerNotFound = worker == null && records.isEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkerDetailUiState(workerId = workerId)
    )

    // ===== 添加 / 编辑账单 =====

    fun onTabChange(toPaidTab: Boolean) {
        _isPaidTab.value = toPaidTab
    }

    fun onAddBillClick() {
        _control.value = _control.value.copy(
            editingBillId = null,
            isRegisterSheetVisible = true
        )
    }

    fun onAddBillDismiss() {
        _control.value = _control.value.copy(isRegisterSheetVisible = false)
    }

    // ===== 操作菜单（长按账单） =====

    fun onActionMenuShow(recordId: Long) {
        _control.value = _control.value.copy(actionMenuRecordId = recordId)
    }

    fun onActionMenuDismiss() {
        _control.value = _control.value.copy(actionMenuRecordId = null)
    }

    fun onActionMenuEdit(recordId: Long) {
        _control.value = _control.value.copy(
            actionMenuRecordId = null,
            editingBillId = recordId,
            isRegisterSheetVisible = true
        )
    }

    // ===== 二次确认 =====

    fun onPendingConfirmDismiss() {
        _control.value = _control.value.copy(pendingConfirmAction = null)
    }

    fun onPendingConfirmAccept() {
        val action = _control.value.pendingConfirmAction ?: return
        _control.value = _control.value.copy(pendingConfirmAction = null)
        viewModelScope.launch {
            try {
                when (action) {
                    is PendingConfirmAction.MarkPaid -> {
                        val ok = repository.markPaid(action.recordId)
                        if (!ok) events.send(WorkerDetailEvent.OperationFailed("记录已被处理"))
                    }
                    is PendingConfirmAction.RevokePayment -> {
                        val ok = repository.revokePayment(action.recordId)
                        if (!ok) events.send(WorkerDetailEvent.OperationFailed("记录已被处理"))
                    }
                    is PendingConfirmAction.DeleteRecord -> {
                        val ok = repository.deleteRecord(action.recordId)
                        if (!ok) events.send(WorkerDetailEvent.OperationFailed("记录已被处理"))
                    }
                }
            } catch (e: Exception) {
                events.send(WorkerDetailEvent.OperationFailed(e.message ?: "操作失败"))
            }
        }
    }

    /** UI 层调用：把待确认操作 push 到 control */
    fun setPendingAction(action: PendingConfirmAction) {
        _control.value = _control.value.copy(
            actionMenuRecordId = null,
            pendingConfirmAction = action
        )
    }

    companion object {
        fun factory(repository: WageRepository, workerId: String): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    WorkerDetailViewModel(repository, workerId)
                }
            }
        }
    }
}