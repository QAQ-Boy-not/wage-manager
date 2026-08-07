// HomeViewModel.kt - 首页 ViewModel + 状态机
//
// 状态分层：
// - HomeUiState：首页整体 UI 状态（日期、记录列表、汇总、表单）
// - RegisterFormState：登记 BottomSheet 表单内部状态
// - HomeEvent：一次性事件（Toast / 关闭），用 Channel 发出避免旋转重显
//
// 同名查重状态机：
//   姓名失焦
//     → trim 为空：不查重，由提交按钮触发姓名错误
//     → 查同名（= 精确匹配）：
//         0 条 → 允许 CreateNew 提交
//         ≥1 条 → 弹"复用/新建"对话框
//             [复用] → selectedReuseWorkerId = 选中 id，可提交（走 Reuse）
//             [新建] → 再弹一次"确认新建同名"对话框，确认后 isCreateDuplicateConfirmed = true
//
// 汇总计算：records 变化 → WageCalculator.summarize，不在 Composable 里算。

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

/**
 * 首页单条工资记录的 UI 数据
 */
data class HomeWageItem(
    val recordId: Long,
    val workerId: String,
    val workerName: String,
    val wageCent: Long,
    val isPaid: Boolean,
    val createdAt: LocalDateTime
)

/**
 * 登记 BottomSheet 表单状态
 */
data class RegisterFormState(
    val isVisible: Boolean = false,
    val workerName: String = "",
    val wageInput: String = "",
    val wageError: MoneyUtils.WageError? = null,
    val nameError: NameError? = null,
    /** 同名候选工人（仅在弹候选对话框时有值） */
    val duplicateWorkers: List<Worker> = emptyList(),
    /** 用户选中的复用工人 id；null 表示当前选 CreateNew 或还没决定 */
    val selectedReuseWorkerId: String? = null,
    /** 用户是否已确认"新建同名工人"二次确认 */
    val isCreateDuplicateConfirmed: Boolean = false,
    val isDuplicateDialogVisible: Boolean = false,
    val isConfirmNewWorkerDialogVisible: Boolean = false,
    val isSaving: Boolean = false
) {
    enum class NameError { REQUIRED }

    /**
     * 根据当前 UI 状态推断要提交的工人选择。
     * null 表示同名但用户还没决定复用还是新建。
     */
    fun resolvedWorkerChoice(): ManualWorkerChoice? {
        return when {
            // 0 同名：默认走 CreateNew
            duplicateWorkers.isEmpty() && !isDuplicateDialogVisible -> ManualWorkerChoice.CreateNew
            // 用户已选复用
            selectedReuseWorkerId != null -> ManualWorkerChoice.Reuse(selectedReuseWorkerId!!)
            // 用户已确认新建同名
            isCreateDuplicateConfirmed -> ManualWorkerChoice.CreateNew
            // 同名但还没决定
            else -> null
        }
    }
}

/**
 * 首页整体状态
 */
data class HomeUiState(
    val workDate: LocalDate,
    val records: List<HomeWageItem> = emptyList(),
    val totalCent: Long = 0,
    val unpaidCount: Int = 0,
    val paidCount: Int = 0,
    val recordCount: Int = 0,
    val registerForm: RegisterFormState = RegisterFormState()
)

/**
 * 一次性事件（UI 用 LaunchedEffect 收集，消费完即丢弃）
 */
sealed interface HomeEvent {
    data class RegisterSuccess(val workerName: String, val wageCent: Long) : HomeEvent
    data class RegisterFailed(val message: String) : HomeEvent
    data class WageInputError(val error: MoneyUtils.WageError) : HomeEvent
    data object NameRequired : HomeEvent
}

class HomeViewModel(
    private val repository: WageRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _workDate = MutableStateFlow(LocalDate.now(clock))
    private val _registerForm = MutableStateFlow(RegisterFormState())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /**
     * 当前选中日期的所有工资记录（Flow）。
     * workDate 变化时自动切换到对应日期的 Flow。
     */
    private val recordsForDate = _workDate
        .flatMapLatest { date -> repository.observeRecords(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 首页 UI 状态（合并 workDate、Room 数据流、表单状态）
     */
    val uiState: StateFlow<HomeUiState> = combine(
        _workDate,
        recordsForDate,
        _registerForm
    ) { workDate, records, form ->
        val items = records.map { it.toHomeItem() }
        val summary = WageCalculator.summarize(
            items.map { WageCalculator.Entry(it.wageCent, it.isPaid) }
        )
        HomeUiState(
            workDate = workDate,
            records = items,
            totalCent = summary.totalCent,
            unpaidCount = summary.unpaidCount,
            paidCount = summary.paidCount,
            recordCount = summary.recordCount,
            registerForm = form
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(workDate = LocalDate.now(clock))
    )

    // ===== 生命周期 =====

    /**
     * 屏幕恢复（onResume / 跨 Activity 回来）时调用。
     * 重要：跨午夜后重新进入 App，不能继续把工资登记到昨天。
     */
    fun onScreenResume() {
        val today = LocalDate.now(clock)
        if (_workDate.value != today) {
            _workDate.value = today
        }
    }

    // ===== 登记流程 =====

    fun onManualRegisterClick() {
        _registerForm.value = RegisterFormState(isVisible = true)
    }

    fun onRegisterDismiss() {
        _registerForm.value = RegisterFormState()
    }

    fun onWorkerNameChange(value: String) {
        _registerForm.update {
            it.copy(
                workerName = value,
                nameError = null,
                // 用户改了名字，重置复用决策
                selectedReuseWorkerId = null,
                isCreateDuplicateConfirmed = false,
                duplicateWorkers = emptyList(),
                isDuplicateDialogVisible = false,
                isConfirmNewWorkerDialogVisible = false
            )
        }
    }

    fun onWageInputChange(value: String) {
        // 只允许数字和一个小数点（不超两位小数）输入实时过滤
        val filtered = value.filter { it.isDigit() || it == '.' }
            .let { raw ->
                val firstDot = raw.indexOf('.')
                if (firstDot < 0) raw
                else raw.substring(0, firstDot + 1) +
                        raw.substring(firstDot + 1).replace(".", "").take(2)
            }
        _registerForm.update { it.copy(wageInput = filtered, wageError = null) }
    }

    /**
     * 姓名输入框失焦时调用，触发同名查重。
     */
    fun onWorkerNameFocusLost() {
        val form = _registerForm.value
        val name = form.workerName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val duplicates = repository.findWorkersByExactName(name)
            _registerForm.update {
                it.copy(
                    duplicateWorkers = duplicates,
                    // 0 条同名：直接允许 CreateNew（不弹对话框）
                    // ≥1 条同名：弹"复用/新建"选择对话框
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
                // 取消新建确认，回到"复用/新建"选择对话框
                isDuplicateDialogVisible = true
            )
        }
    }

    fun onRegisterSubmit() {
        val form = _registerForm.value
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
            // 同名但用户还没决定复用还是新建，强制弹同名选择
            _registerForm.update {
                it.copy(isDuplicateDialogVisible = form.duplicateWorkers.isNotEmpty())
            }
            return
        }

        // 提交中，禁用重复点击
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
                events.send(HomeEvent.RegisterFailed(e.message ?: "登记失败"))
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

/**
 * 把 Room 的复合数据转成 UI 显示用的轻量数据
 */
private fun WageRecordWithWorker.toHomeItem(): HomeWageItem {
    return HomeWageItem(
        recordId = record.id,
        workerId = record.workerId,
        workerName = workerName,
        wageCent = record.wageCent,
        isPaid = record.isPaid,
        createdAt = record.createTime
    )
}
