// WorkerDetailViewModel.kt - 工人详情页 ViewModel（V1.2）
//
// 职责：
// - 观察某工人的所有账单（Room Flow）
// - 按支付状态分组（未付 / 已付）
// - 计算汇总（总数 / 总额）
// - 控制添加账单 BottomSheet 的显示（M1 复用）

package com.example.wagemanager.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import com.example.wagemanager.data.WorkerDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val createdAt: LocalDateTime
)

/**
 * 详情页 UI 状态
 */
data class WorkerDetailUiState(
    val workerId: String,
    val workerName: String = "",
    val firstWorkDate: LocalDate? = null,
    val unpaidBills: List<BillItem> = emptyList(),
    val paidBills: List<BillItem> = emptyList(),
    val totalCount: Int = 0,
    val totalCent: Long = 0L,
    val unpaidCent: Long = 0L,
    val paidCent: Long = 0L,
    val isRegisterSheetVisible: Boolean = false,
    val isWorkerNotFound: Boolean = false
)

class WorkerDetailViewModel(
    private val repository: WageRepository,
    private val workerId: String,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _isRegisterSheetVisible = MutableStateFlow(false)
    private val _workerInfo = MutableStateFlow<Worker?>(null)

    init {
        // 加载工人基本信息（姓名 + 首次登记日期）
        viewModelScope.launch {
            _workerInfo.value = repository.findWorkerById(workerId)
        }
    }

    val uiState: StateFlow<WorkerDetailUiState> = combine(
        repository.observeWorkerDetail(workerId),
        _workerInfo,
        _isRegisterSheetVisible
    ) { records, worker, isSheetVisible ->
        val items = records.map { record ->
            BillItem(
                recordId = record.record.id,
                wageCent = record.record.wageCent,
                workDate = record.record.workDate,
                isPaid = record.record.isPaid,
                paidTime = record.record.paidTime,
                createdAt = record.record.createTime
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
            isRegisterSheetVisible = isSheetVisible,
            isWorkerNotFound = worker == null && records.isEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkerDetailUiState(workerId = workerId)
    )

    fun onAddBillClick() {
        _isRegisterSheetVisible.value = true
    }

    fun onAddBillDismiss() {
        _isRegisterSheetVisible.value = false
    }

    fun onAddBillSubmitted() {
        _isRegisterSheetVisible.value = false
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