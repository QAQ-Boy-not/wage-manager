// RegisterBottomSheet.kt - 手动登记 BottomSheet（M2.1 简化 + 连续登记）
//
// 设计要点（M2.1）：
// 1. 简化同名查重：弹"切换到该工人 / 改名新建"两个按钮（去掉二次确认）
// 2. 连续登记：登记成功后 BottomSheet 不关闭，姓名/工资清空，光标回到姓名
// 3. 反馈条：登记成功后顶部显示 2 秒"✅ 已登记：XXX"，明确反馈
// 4. 按钮文案：[✅ 登记] [完成]（完成 = 关闭 BottomSheet，不再记了）

package com.example.wagemanager.ui.home

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
import androidx.compose.runtime.remember
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
import com.example.wagemanager.data.Worker
import com.example.wagemanager.ui.components.BigButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBottomSheet(
    state: RegisterFormState,
    onWorkerNameChange: (String) -> Unit,
    onWorkerNameFocusLost: () -> Unit,
    onWageInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onUseExistingWorker: (String) -> Unit,
    onRenameAndCreate: () -> Unit,
    onDuplicateDialogDismiss: () -> Unit
) {
    if (!state.isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val wageFocusRequester = remember { FocusRequester() }

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
                    if (state.mode == FormMode.Edit) R.string.edit_title
                    else R.string.register_title
                ),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 反馈条：登记成功后 2 秒显示
            if (state.lastSuccessMessage != null) {
                Text(
                    text = state.lastSuccessMessage,
                    color = colorResource(R.color.wage_paid_green),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
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

            // 工人姓名
            OutlinedTextField(
                value = state.workerName,
                onValueChange = onWorkerNameChange,
                label = { Text(stringResource(R.string.field_worker_name)) },
                isError = state.nameError != null,
                supportingText = {
                    if (state.nameError == RegisterFormState.NameError.REQUIRED) {
                        Text(stringResource(R.string.error_name_required))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) onWorkerNameFocusLost()
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 今日工资
            OutlinedTextField(
                value = state.wageInput,
                onValueChange = onWageInputChange,
                label = { Text(stringResource(R.string.field_wage_today)) },
                isError = state.wageError != null,
                supportingText = {
                    val err = state.wageError
                    if (err != null) {
                        Text(
                            text = stringResource(
                                when (err) {
                                    com.example.wagemanager.util.MoneyUtils.WageError.REQUIRED -> R.string.error_wage_required
                                    com.example.wagemanager.util.MoneyUtils.WageError.INVALID_FORMAT -> R.string.error_wage_invalid_format
                                    com.example.wagemanager.util.MoneyUtils.WageError.MUST_BE_POSITIVE -> R.string.error_wage_must_be_positive
                                    com.example.wagemanager.util.MoneyUtils.WageError.OUT_OF_RANGE -> R.string.error_wage_out_of_range
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

            // 登记 / 保存
            BigButton(
                text = stringResource(
                    if (state.mode == FormMode.Edit) R.string.action_save_edit
                    else R.string.action_confirm_register
                ),
                backgroundColor = MaterialTheme.colorScheme.primary,
                enabled = !state.isSaving,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 完成（关闭 BottomSheet）
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.action_finish),
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ===== 同名对话框（M2.1 简化版） =====
    if (state.isDuplicateDialogVisible && state.duplicateWorkers.isNotEmpty()) {
        DuplicateWorkerDialog(
            workerName = state.workerName.trim(),
            duplicates = state.duplicateWorkers,
            onUseExisting = onUseExistingWorker,
            onRenameAndCreate = onRenameAndCreate,
            onDismiss = onDuplicateDialogDismiss
        )
    }

    // 焦点管理：
    // - Create 模式首次打开 → 焦点在工资框
    // - 登记成功后 → 焦点回到姓名框（方便连续登记下一个工人）
    LaunchedEffect(state.lastSuccessMessage) {
        if (state.lastSuccessMessage != null) {
            // 登记成功，焦点回到姓名框
            try {
                kotlinx.coroutines.delay(50)
                nameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (state.mode == FormMode.Create && state.lastSuccessMessage == null) {
        LaunchedEffect(Unit) {
            try {
                wageFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
}

/**
 * 同名工人对话框（M2.1 简化）
 *
 * 两个按钮：
 * - "切换到该工人"：onUseExisting(workerId) → 用已存在 worker 登记
 * - "改名新建"：onRenameAndCreate() → 关闭弹窗，用户继续改输入框
 */
@Composable
private fun DuplicateWorkerDialog(
    workerName: String,
    duplicates: List<Worker>,
    onUseExisting: (String) -> Unit,
    onRenameAndCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onUseExisting.let { { it(duplicates.first().id) } }) {
                Text(
                    text = stringResource(R.string.action_use_existing),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
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