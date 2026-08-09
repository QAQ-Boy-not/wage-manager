// RegisterBillSheet.kt - 添加账单 BottomSheet（V1.2 工人模型）
//
// 设计要点：
// 1. ModalBottomSheet：Compose 标准的"半屏弹窗"
// 2. 金额输入框默认焦点 + Decimal 键盘
// 3. 姓名输入框失焦触发同名查重
// 4. 同名查重（V1.2 简化版）：弹"切换到该工人 / 改名新建"对话框，无二次确认
// 5. 成功反馈条"✅ 已添加 张姐 280元"，2 秒自动消失，支持连续登记多笔
// 6. 备注 / 地点字段 M3 才有 schema 字段，先不显示

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import com.example.wagemanager.ui.components.BigButton
import com.example.wagemanager.util.MoneyUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 添加账单表单状态
 */
private data class BillFormState(
    val workDate: LocalDate = LocalDate.now(),
    val nameInput: String = "",
    val wageInput: String = "",
    val wageError: MoneyUtils.WageError? = null,
    val nameError: NameError? = null,
    val duplicateWorkers: List<Worker> = emptyList(),
    val isDuplicateDialogVisible: Boolean = false,
    val selectedExistingWorkerId: String? = null,
    val isSaving: Boolean = false,
    val successMessage: String? = null
) {
    enum class NameError { REQUIRED }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBillSheet(
    repository: WageRepository,
    initialName: String? = null,
    editingBill: BillItem? = null,
    @Suppress("UNUSED_PARAMETER") editingBillId: Long? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val wageFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val isEditMode = editingBill != null

    // 初始金额格式（cents → "280.50"）
    val initialWageInput = editingBill?.let {
        val yuan = it.wageCent.toDouble() / 100.0
        if (yuan == yuan.toLong().toDouble()) yuan.toLong().toString()
        else "%.2f".format(yuan)
    } ?: ""

    var form by remember {
        mutableStateOf(
            BillFormState(
                workDate = editingBill?.workDate ?: LocalDate.now(),
                nameInput = editingBill?.let { initialName.orEmpty() } ?: initialName.orEmpty(),
                wageInput = initialWageInput,
                // 如果传了 initialName，自动跳过同名查重（detail 页加账单场景）
                selectedExistingWorkerId = if (initialName != null) "" else null
            )
        )
    }

    // 提交逻辑：根据 isEditMode 决定 registerBill 或 updateWage
    fun submit() {
        val name = form.nameInput.trim()
        if (name.isEmpty()) {
            form = form.copy(nameError = BillFormState.NameError.REQUIRED)
            return
        }
        val parse = MoneyUtils.parseWageCent(form.wageInput)
        if (!parse.isValid) {
            form = form.copy(wageError = parse.error)
            return
        }
        // 同名但用户还没决定
        if (!isEditMode && form.duplicateWorkers.isNotEmpty() && form.selectedExistingWorkerId == null) {
            form = form.copy(isDuplicateDialogVisible = true)
            return
        }

        form = form.copy(isSaving = true)
        scope.launch {
            try {
                if (isEditMode && editingBill != null) {
                    // 编辑模式：调 updateWage
                    repository.updateWage(
                        recordId = editingBill.recordId,
                        name = name,
                        wageCent = parse.wageCent,
                        existingWorkerId = form.selectedExistingWorkerId.takeIf { it.isNotEmpty() }
                    )
                    val msg = "✅ 已修改：$name ${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = BillFormState(successMessage = msg)
                    scope.launch {
                        delay(2_000)
                        form = form.copy(successMessage = null)
                    }
                } else {
                    // 新建模式：调 registerBill
                    repository.registerBill(
                        name = name,
                        wageCent = parse.wageCent,
                        workDate = form.workDate,
                        existingWorkerId = form.selectedExistingWorkerId.takeIf { it.isNotEmpty() }
                    )
                    val msg = "✅ 已添加：$name ${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = BillFormState(
                        workDate = form.workDate,
                        successMessage = msg
                    )
                    scope.launch {
                        delay(2_000)
                        form = form.copy(successMessage = null)
                    }
                }
            } catch (e: Exception) {
                val op = if (isEditMode) "修改" else "添加"
                form = form.copy(
                    isSaving = false,
                    successMessage = "❌ ${op}失败：${e.message ?: "未知错误"}"
                )
                scope.launch {
                    delay(2_000)
                    form = form.copy(successMessage = null)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 标题
            Text(
                text = stringResource(
                    if (isEditMode) R.string.bill_edit_title
                    else R.string.bill_register_title
                ),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 反馈条
            val successMsg = form.successMessage
            if (successMsg != null) {
                Text(
                    text = successMsg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (successMsg.startsWith("✅"))
                        colorResource(R.color.wage_paid_green)
                    else
                        colorResource(R.color.wage_unpaid_red),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = colorResource(R.color.wage_card_background),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 日期（M1 写死今天，M4 加日期选择器）
            Text(
                text = stringResource(R.string.bill_field_work_date, form.workDate.toString()),
                fontSize = 16.sp,
                color = colorResource(R.color.wage_text_primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorResource(R.color.wage_card_background),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 工人姓名
            OutlinedTextField(
                value = form.nameInput,
                onValueChange = { value ->
                    form = form.copy(
                        nameInput = value,
                        nameError = null,
                        selectedExistingWorkerId = null,
                        duplicateWorkers = emptyList(),
                        isDuplicateDialogVisible = false
                    )
                },
                label = { Text(stringResource(R.string.bill_field_worker_name)) },
                isError = form.nameError != null,
                supportingText = {
                    if (form.nameError == BillFormState.NameError.REQUIRED) {
                        Text(stringResource(R.string.error_name_required))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            // 失焦查同名
                            val name = form.nameInput.trim()
                            if (name.isNotEmpty()) {
                                scope.launch {
                                    val duplicates = repository.findWorkersByExactName(name)
                                    form = form.copy(
                                        duplicateWorkers = duplicates,
                                        isDuplicateDialogVisible = duplicates.isNotEmpty()
                                    )
                                }
                            }
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 金额
            OutlinedTextField(
                value = form.wageInput,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() || it == '.' }
                        .let { raw ->
                            val firstDot = raw.indexOf('.')
                            if (firstDot < 0) raw
                            else raw.substring(0, firstDot + 1) +
                                    raw.substring(firstDot + 1).replace(".", "").take(2)
                        }
                    form = form.copy(wageInput = filtered, wageError = null)
                },
                label = { Text(stringResource(R.string.bill_field_wage)) },
                isError = form.wageError != null,
                supportingText = {
                    val err = form.wageError
                    if (err != null) {
                        Text(
                            text = stringResource(
                                when (err) {
                                    MoneyUtils.WageError.REQUIRED -> R.string.error_wage_required
                                    MoneyUtils.WageError.INVALID_FORMAT -> R.string.error_wage_invalid_format
                                    MoneyUtils.WageError.MUST_BE_POSITIVE -> R.string.error_wage_must_be_positive
                                    MoneyUtils.WageError.OUT_OF_RANGE -> R.string.error_wage_out_of_range
                                    else -> R.string.error_wage_invalid_format
                                }
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(wageFocusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 添加 / 保存 / 取消
            BigButton(
                text = stringResource(
                    if (isEditMode) R.string.bill_action_save
                    else R.string.bill_action_add
                ),
                backgroundColor = MaterialTheme.colorScheme.primary,
                enabled = !form.isSaving,
                onClick = {
                    focusManager.clearFocus()
                    submit()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ===== 同名对话框（V1.2 简化） =====
    if (form.isDuplicateDialogVisible && form.duplicateWorkers.isNotEmpty()) {
        DuplicateWorkerDialog(
            workerName = form.nameInput.trim(),
            duplicates = form.duplicateWorkers,
            onUseExisting = { workerId ->
                form = form.copy(
                    selectedExistingWorkerId = workerId,
                    isDuplicateDialogVisible = false
                )
            },
            onRenameAndCreate = {
                form = form.copy(
                    isDuplicateDialogVisible = false,
                    selectedExistingWorkerId = null
                )
            }
        )
    }

    // 焦点管理
    LaunchedEffect(form.successMessage) {
        val msg = form.successMessage
        if (msg != null && msg.startsWith("✅")) {
            // 成功 → 焦点回到姓名框
            try {
                delay(50)
                nameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (form.successMessage == null) {
            try {
                wageFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
}

/**
 * 同名工人对话框（V1.2 简化版）
 *
 * 两个按钮：
 * - "切换到该工人"（绿色）→ 用已存在 worker_id
 * - "改名新建"（红色）→ 关闭弹窗，用户继续改输入框
 */
@Composable
private fun DuplicateWorkerDialog(
    workerName: String,
    duplicates: List<Worker>,
    onUseExisting: (String) -> Unit,
    onRenameAndCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onRenameAndCreate,
        title = {
            Text(
                text = stringResource(R.string.dialog_duplicate_title, workerName),
                fontSize = 22.sp
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_duplicate_message),
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                duplicates.forEach { worker ->
                    DuplicateCandidateRow(
                        worker = worker,
                        onClick = { onUseExisting(worker.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUseExisting(duplicates.first().id) }) {
                Text(
                    text = stringResource(R.string.action_use_existing),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_paid_green)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onRenameAndCreate) {
                Text(
                    text = stringResource(R.string.action_rename_and_create),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
private fun DuplicateCandidateRow(
    worker: Worker,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = worker.name,
            fontSize = 20.sp,
            modifier = Modifier.size(width = 200.dp, height = 28.dp)
        )
        val tail = worker.id.takeLast(4)
        Text(
            text = "...$tail",
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}