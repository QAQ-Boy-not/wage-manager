// HomeViewModel.kt - 首页 ViewModel + 状态机（M2 完整版）
//
// 状态分层：
// - HomeUiState：首页整体（日期、tab 选中、记录列表、汇总、表单、操作菜单、二次确认）
// - RegisterFormState：登记 / 编辑 BottomSheet 共用表单
// - HomeEvent：一次性事件（Toast / 关闭），用 Channel 发出避免旋转重显
//
// 关键状态机：
// 1. 同名查重（M1 已有）
// 2. 标签切换：isPaidTab = false 未付 / true 已付
// 3. 操作菜单（长按已付项）：actionMenuRecordId 决定显示哪个记录的操作菜单
// 4. 二次确认：pendingConfirmAction 决定弹哪个操作的确认对话框
// 5. 编辑模式：RegisterFormState.mode = Edit + editingRecordId
//
// 汇总：WageCalculator.summarize(全部记录)，UI 按 tab 过滤显示。

package com.example.wagemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.ManualWorkerChoice
import com.example.wagemanager.data.WageRecordWithWorker
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import com.example.wagemanager.util.MoneyUtils
import com.example.wagemanager.util.PaymentRules
import com.example.wagemanager.util.WageCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** 表单模式：Create 新建 / Edit 编辑未付记录 */
enum class FormMode { Create, Edit }

/**
 * 首页单条工资记录的 UI 数据
 */
data class HomeWageItem(
    val recordId: Long,
    val workerId: String,
    val workerName: String,
    val wageCent: Long,
    val isPaid: Boolean,
    val paidTime: LocalDateTime?,
    val createdAt: LocalDateTime
)

/**
 * 登记 / 编辑 BottomSheet 共用表单状态
 */
data class RegisterFormState(
    val isVisible: Boolean = false,
    val mode: FormMode = FormMode.Create,
    val editingRecordId: Long? = null,
    val workerName: String = "",
    val wageInput: String = "",
    val wageError: MoneyUtils.WageError? = null,
    val nameError: NameError? = null,
    val duplicateWorkers: List<Worker> = emptyList(),
    val selectedReuseWorkerId: String? = null,
    val isCreateDuplicateConfirmed: Boolean = false,
    val isDuplicateDialogVisible: Boolean = false,
    val isConfirmNewWorkerDialogVisible: Boolean = false,
    val isSaving: Boolean = false
) {
    enum class NameError { REQUIRED }

    fun resolvedWorkerChoice(): ManualWorkerChoice? {
        return when {
            duplicateWorkers.isEmpty() && !isDuplicateDialogVisible -> ManualWorkerChoice.CreateNew
            selectedReuseWorkerId != null -> ManualWorkerChoice.Reuse(selectedReuseWorkerId!!)
            isCreateDuplicateConfirmed -> ManualWorkerChoice.CreateNew
            else -> null
        }
    }
}

/**
 * 长按操作菜单选项（仅已付记录显示）
 */
enum class ActionOption {
    REVOKE_PAYMENT,   // 撤销付款
    DELETE_RECORD     // 删除记录
}

/**
 * 待二次确认的操作（点击操作按钮后弹 AlertDialog 确认）
 */
sealed interface PendingConfirmAction {
    data class MarkPaid(val recordId: Long) : PendingConfirmAction
    data class RevokePayment(val recordId: Long) : PendingConfirmAction
    data class DeleteRecord(val recordId: Long) : PendingConfirmAction
}

/**
 * 首页整体状态
 */
data class HomeUiState(
    val workDate: LocalDate,
    val isPaidTab: Boolean = false,
    val records: List<HomeWageItem> = emptyList(),
    val totalCent: Long = 0,
    val unpaidCount: Int = 0,
    val paidCount: Int = 0,
    val recordCount: Int = 0,
    val registerForm: RegisterFormState = RegisterFormState(),
    /** 长按哪个记录弹出操作菜单（仅已付项支持） */
    val actionMenuRecordId: Long? = null,
    /** 待二次确认的操作（AlertDialog 用） */
    val pendingConfirmAction: PendingConfirmAction? = null
)

/**
 * 一次性事件
 */
sealed interface HomeEvent {
    // 登记 / 编辑
    data class RegisterSuccess(val workerName: String, val wageCent: Long) : HomeEvent
    data class EditSuccess(val recordId: Long, val workerName: String, val wageCent: Long) : HomeEvent
    data class OperationFailed(val message: String) : HomeEvent
    data class WageInputError(val error: MoneyUtils.WageError) : HomeEvent
    data object NameRequired : HomeEvent
    // 标记 / 撤销 / 删除
    data class MarkPaidSuccess(val recordId: Long) : HomeEvent
    data class RevokeSuccess(val recordId: Long) : HomeEvent
    data class DeleteSuccess(val recordId: Long) : HomeEvent
}

class HomeViewModel(
    private val repository: WageRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _workDate = MutableStateFlow(LocalDate.now(clock))
    private val _registerForm = MutableStateFlow(RegisterFormState())
    private val _isPaidTab = MutableStateFlow(false)
    private val _actionMenuRecordId = MutableStateFlow<Long?>(null)
    private val _pendingConfirmAction = MutableStateFlow<PendingConfirmAction?>(null)

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val recordsForDate = _workDate
        .flatMapLatest { date -> repository.observeRecords(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 首页 UI 状态（合并 workDate、Room 数据流、表单状态、tab、菜单、确认）
     */
    val uiState: StateFlow<HomeUiState> = combine(
        combine(_workDate, _isPaidTab, _actionMenuRecordId, _pendingConfirmAction) { wd, tab, menu, pending ->
            Tuple4(wd, tab, menu, pending)
        },
        combine(recordsForDate, _registerForm) { recs, form ->
            Pair(recs, form)
        }
    ) { tuple, pair ->
        val (workDate, isPaidTab, actionMenuRecordId, pendingConfirmAction) = tuple
        val (records, form) = pair
        val items = records.map { it.toHomeItem() }
        val summary = WageCalculator.summarize(
            items.map { WageCalculator.Entry(it.wageCent, it.isPaid) }
        )
        HomeUiState(
            workDate = workDate,
            isPaidTab = isPaidTab,
            records = items,
            totalCent = summary.totalCent,
            unpaidCount = summary.unpaidCount,
            paidCount = summary.paidCount,
            recordCount = summary.recordCount,
            registerForm = form,
            actionMenuRecordId = actionMenuRecordId,
            pendingConfirmAction = pendingConfirmAction
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(workDate = LocalDate.now(clock))
    )

    // ===== 生命周期 =====

    fun onScreenResume() {
        val today = LocalDate.now(clock)
        if (_workDate.value != today) {
            _workDate.value = today
        }
    }

    // ===== Tab 切换 =====

    fun onTabChange(toPaidTab: Boolean) {
        _isPaidTab.value = toPaidTab
    }

    // ===== 登记流程（Create 模式） =====

    fun onManualRegisterClick() {
        _registerForm.value = RegisterFormState(isVisible = true, mode = FormMode.Create)
    }

    fun onRegisterDismiss() {
        _registerForm.value = RegisterFormState()
    }

    fun onWorkerNameChange(value: String) {
        _registerForm.update {
            it.copy(
                workerName = value,
                nameError = null,
                selectedReuseWorkerId = null,
                isCreateDuplicateConfirmed = false,
                duplicateWorkers = emptyList(),
                isDuplicateDialogVisible = false,
                isConfirmNewWorkerDialogVisible = false
            )
        }
    }

    fun onWageInputChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
            .let { raw ->
                val firstDot = raw.indexOf('.')
                if (firstDot < 0) raw
                else raw.substring(0, firstDot + 1) +
                        raw.substring(firstDot + 1).replace(".", "").take(2)
            }
        _registerForm.update { it.copy(wageInput = filtered, wageError = null) }
    }

    fun onWorkerNameFocusLost() {
        val form = _registerForm.value
        val name = form.workerName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val duplicates = repository.findWorkersByExactName(name)
            _registerForm.update {
                it.copy(
                    duplicateWorkers = duplicates,
                    isDuplicateDialogVisible = duplicates.isNotEmpty()
                )
            }
        }
    }

    fun onReuseWorker(workerId: String) {
        _registerForm.update {
            it.copy(
                selectedReuseWorkerId = workerId,
                isDuplicateDialogVisible = false
            )
        }
    }

    fun onCreateDuplicateWorkerClick() {
        _registerForm.update {
            it.copy(
                isDuplicateDialogVisible = false,
                isConfirmNewWorkerDialogVisible = true
            )
        }
    }

    fun onCreateDuplicateWorkerConfirm() {
        _registerForm.update {
            it.copy(
                isCreateDuplicateConfirmed = true,
                isConfirmNewWorkerDialogVisible = false
            )
        }
    }

    fun onDuplicateDialogDismiss() {
        _registerForm.update {
            it.copy(
                isDuplicateDialogVisible = false,
                isConfirmNewWorkerDialogVisible = false,
                selectedReuseWorkerId = null,
                isCreateDuplicateConfirmed = false
            )
        }
    }

    fun onConfirmNewWorkerDialogDismiss() {
        _registerForm.update {
            it.copy(
                isConfirmNewWorkerDialogVisible = false,
                isCreateDuplicateConfirmed = false,
                isDuplicateDialogVisible = true
            )
        }
    }

    fun onRegisterSubmit() {
        val form = _registerForm.value
        if (form.mode == FormMode.Edit) {
            onEditSubmitInternal()
            return
        }
        val name = form.workerName.trim()
        if (name.isEmpty()) {
            viewModelScope.launch { events.send(HomeEvent.NameRequired) }
            _registerForm.update { it.copy(nameError = RegisterFormState.NameError.REQUIRED) }
            return
        }

        val parse = MoneyUtils.parseWageCent(form.wageInput)
        if (!parse.isValid) {
            viewModelScope.launch { events.send(HomeEvent.WageInputError(parse.error)) }
            _registerForm.update { it.copy(wageError = parse.error) }
            return
        }

        val choice = form.resolvedWorkerChoice()
        if (choice == null) {
            _registerForm.update {
                it.copy(isDuplicateDialogVisible = form.duplicateWorkers.isNotEmpty())
            }
            return
        }

        _registerForm.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                repository.registerManualWage(
                    name = name,
                    wageCent = parse.wageCent,
                    workDate = _workDate.value,
                    workerChoice = choice
                )
                events.send(HomeEvent.RegisterSuccess(name, parse.wageCent))
                _registerForm.value = RegisterFormState()
            } catch (e: Exception) {
                events.send(HomeEvent.OperationFailed(e.message ?: "登记失败"))
                _registerForm.update { it.copy(isSaving = false) }
            }
        }
    }

    // ===== 标记已付 =====

    fun onMarkPaidClick(recordId: Long) {
        _pendingConfirmAction.value = PendingConfirmAction.MarkPaid(recordId)
    }

    // ===== 操作菜单（仅已付项长按） =====

    fun onActionMenuShow(recordId: Long) {
        _actionMenuRecordId.value = recordId
    }

    fun onActionMenuDismiss() {
        _actionMenuRecordId.value = null
    }

    fun onActionSelected(recordId: Long, option: ActionOption) {
        _actionMenuRecordId.value = null
        when (option) {
            ActionOption.REVOKE_PAYMENT -> {
                _pendingConfirmAction.value = PendingConfirmAction.RevokePayment(recordId)
            }
            ActionOption.DELETE_RECORD -> {
                _pendingConfirmAction.value = PendingConfirmAction.DeleteRecord(recordId)
            }
        }
    }

    // ===== 二次确认 =====

    fun onPendingConfirmDismiss() {
        _pendingConfirmAction.value = null
    }

    fun onPendingConfirmAccept() {
        val action = _pendingConfirmAction.value ?: return
        _pendingConfirmAction.value = null
        viewModelScope.launch {
            try {
                when (action) {
                    is PendingConfirmAction.MarkPaid -> {
                        val ok = repository.markPaid(action.recordId)
                        if (ok) events.send(HomeEvent.MarkPaidSuccess(action.recordId))
                        else events.send(HomeEvent.OperationFailed("记录已被处理"))
                    }
                    is PendingConfirmAction.RevokePayment -> {
                        val ok = repository.revokePayment(action.recordId)
                        if (ok) events.send(HomeEvent.RevokeSuccess(action.recordId))
                        else events.send(HomeEvent.OperationFailed("记录已被处理"))
                    }
                    is PendingConfirmAction.DeleteRecord -> {
                        val ok = repository.deleteRecord(action.recordId)
                        if (ok) events.send(HomeEvent.DeleteSuccess(action.recordId))
                        else events.send(HomeEvent.OperationFailed("记录已被处理"))
                    }
                }
            } catch (e: Exception) {
                events.send(HomeEvent.OperationFailed(e.message ?: "操作失败"))
            }
        }
    }

    // ===== 编辑未付记录 =====

    fun onEditClick(recordId: Long) {
        viewModelScope.launch {
            val record = repository.findRecordById(recordId)
            if (record == null) {
                events.send(HomeEvent.OperationFailed("记录不存在"))
                return@launch
            }
            val err = PaymentRules.validate(PaymentRules.PaymentAction.EDIT, record.record.isPaid)
            if (err != PaymentRules.PaymentError.NONE) {
                events.send(HomeEvent.OperationFailed("已付款记录不可编辑，请先撤销付款"))
                return@launch
            }
            // 元 → 分的转换：用 BigDecimal 反向
            val yuan = record.record.wageCent.toDouble() / 100.0
            val wageInput = if (yuan == yuan.toLong().toDouble()) {
                yuan.toLong().toString()
            } else {
                "%.2f".format(yuan)
            }
            _registerForm.value = RegisterFormState(
                isVisible = true,
                mode = FormMode.Edit,
                editingRecordId = recordId,
                workerName = record.workerName,
                wageInput = wageInput
            )
        }
    }

    private fun onEditSubmitInternal() {
        val form = _registerForm.value
        val recordId = form.editingRecordId ?: return
        val name = form.workerName.trim()
        if (name.isEmpty()) {
            viewModelScope.launch { events.send(HomeEvent.NameRequired) }
            _registerForm.update { it.copy(nameError = RegisterFormState.NameError.REQUIRED) }
            return
        }
        val parse = MoneyUtils.parseWageCent(form.wageInput)
        if (!parse.isValid) {
            viewModelScope.launch { events.send(HomeEvent.WageInputError(parse.error)) }
            _registerForm.update { it.copy(wageError = parse.error) }
            return
        }
        val choice = form.resolvedWorkerChoice()
        if (choice == null) {
            _registerForm.update {
                it.copy(isDuplicateDialogVisible = form.duplicateWorkers.isNotEmpty())
            }
            return
        }
        _registerForm.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val ok = repository.updateWage(recordId, name, parse.wageCent, choice)
                if (ok) {
                    events.send(HomeEvent.EditSuccess(recordId, name, parse.wageCent))
                    _registerForm.value = RegisterFormState()
                } else {
                    events.send(HomeEvent.OperationFailed("记录已被处理（可能已标记已付）"))
                    _registerForm.update { it.copy(isSaving = false) }
                }
            } catch (e: Exception) {
                events.send(HomeEvent.OperationFailed(e.message ?: "编辑失败"))
                _registerForm.update { it.copy(isSaving = false) }
            }
        }
    }

    companion object {
        fun factory(repository: WageRepository): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    HomeViewModel(repository)
                }
            }
        }
    }
}

/** 内部用：4 元组（kotlin 没有内建 Tuple4） */
private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun WageRecordWithWorker.toHomeItem(): HomeWageItem {
    return HomeWageItem(
        recordId = record.id,
        workerId = record.workerId,
        workerName = workerName,
        wageCent = record.wageCent,
        isPaid = record.isPaid,
        paidTime = record.paidTime,
        createdAt = record.createTime
    )
}