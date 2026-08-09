// WorkerListViewModel.kt - 首页 ViewModel（V1.2 工人模型）
//
// 职责：
// - 观察所有工人 + 账单汇总（Room Flow）
// - 计算今日汇总（所有工人的未付/已付总条数 + 总额）
// - 控制添加账单 BottomSheet 的显示

package com.example.wagemanager.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.WorkerSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/**
 * UI 用的工人汇总（脱掉 Room DTO）
 */
data class WorkerSummaryItem(
    val workerId: String,
    val workerName: String,
    val isManual: Boolean,
    val firstWorkDate: LocalDate?,
    val unpaidCount: Int,
    val unpaidCent: Long,
    val paidCount: Int,
    val paidCent: Long,
    val latestWorkDate: LocalDate?
)

/**
 * 首页 UI 状态
 */
data class WorkerListUiState(
    val workDate: LocalDate = LocalDate.now(),
    val workers: List<WorkerSummaryItem> = emptyList(),
    val totalUnpaidCount: Int = 0,
    val totalUnpaidCent: Long = 0L,
    val totalPaidCount: Int = 0,
    val totalPaidCent: Long = 0L,
    val isRegisterSheetVisible: Boolean = false
)

class WorkerListViewModel(
    private val repository: WageRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _workDate = MutableStateFlow(LocalDate.now(clock))
    private val _isRegisterSheetVisible = MutableStateFlow(false)

    val uiState: StateFlow<WorkerListUiState> = combine(
        repository.observeWorkerSummaries(),
        _workDate,
        _isRegisterSheetVisible
    ) { summaries, workDate, isSheetVisible ->
        val items = summaries.map { it.toItem() }
        WorkerListUiState(
            workDate = workDate,
            workers = items,
            totalUnpaidCount = items.sumOf { it.unpaidCount },
            totalUnpaidCent = items.sumOf { it.unpaidCent },
            totalPaidCount = items.sumOf { it.paidCount },
            totalPaidCent = items.sumOf { it.paidCent },
            isRegisterSheetVisible = isSheetVisible
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

    fun onAddBillClick() {
        _isRegisterSheetVisible.value = true
    }

    fun onAddBillDismiss() {
        _isRegisterSheetVisible.value = false
    }

    fun onAddBillSubmitted() {
        // 登记成功后只关闭 BottomSheet，UI 自动通过 Flow 更新
        _isRegisterSheetVisible.value = false
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

private fun WorkerSummary.toItem(): WorkerSummaryItem = WorkerSummaryItem(
    workerId = workerId,
    workerName = workerName,
    isManual = isManual,
    firstWorkDate = firstWorkDate,
    unpaidCount = unpaidCount,
    unpaidCent = unpaidCent,
    paidCount = paidCount,
    paidCent = paidCent,
    latestWorkDate = latestWorkDate
)