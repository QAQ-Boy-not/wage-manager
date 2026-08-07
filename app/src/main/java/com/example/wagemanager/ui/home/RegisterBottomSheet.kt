// RegisterBottomSheet.kt - 手动登记 BottomSheet
//
// 设计要点：
// 1. ModalBottomSheet：Compose 标准的"半屏弹窗"
// 2. 工资输入框默认焦点 + Decimal 键盘
// 3. 姓名输入框失焦触发同名查重（onWorkerNameFocusLost）
// 4. 同名候选弹 AlertDialog 让用户选"复用/新建"
// 5. 选"新建同名工人"再弹一次确认对话框（防误重名）
// 6. 表单 isSaving 时禁用提交按钮

package com.example.wagemanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.data.Worker
import com.example.wagemanager.ui.components.BigButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBottomSheet(
    state: RegisterFormState,
    onWorkerNameChange: (String) -> Unit,
    onWorkerNameFocusLost: () -> Unit,
    onWageInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onReuseWorker: (String) -> Unit,
    onCreateNewWorker: () -> Unit,
    onConfirmCreateNewWorker: () -> Unit,
    onDuplicateDialogDismiss: () -> Unit,
    onConfirmNewWorkerDialogDismiss: () -> Unit
) {
    if (!state.isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val wageFocusRequester = androidx.compose.runtime.remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

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

            // 登记 / 取消
            BigButton(
                text = stringResource(R.string.action_confirm_register),
                backgroundColor = MaterialTheme.colorScheme.primary,
                enabled = !state.isSaving,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
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

    // ===== 同名候选对话框 =====
    if (state.isDuplicateDialogVisible && state.duplicateWorkers.isNotEmpty()) {
        DuplicateWorkerDialog(
            workerName = state.workerName.trim(),
            duplicates = state.duplicateWorkers,
            onReuse = onReuseWorker,
            onCreateNew = onCreateNewWorker,
            onDismiss = onDuplicateDialogDismiss
        )
    }

    // ===== 新建同名工人二次确认 =====
    if (state.isConfirmNewWorkerDialogVisible) {
        ConfirmNewWorkerDialog(
            workerName = state.workerName.trim(),
            onConfirm = onConfirmCreateNewWorker,
            onDismiss = onConfirmNewWorkerDialogDismiss
        )
    }

    // 打开 BottomSheet 时把焦点放到工资框
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            wageFocusRequester.requestFocus()
        } catch (_: Exception) {
            // focus requester 在 IME 未就绪时偶尔失败，忽略
        }
    }
}

/**
 * 同名工人候选列表：让用户选"复用 / 新建"
 */
@Composable
private fun DuplicateWorkerDialog(
    workerName: String,
    duplicates: List<Worker>,
    onReuse: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_duplicate_title, workerName)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_duplicate_message))
                Spacer(modifier = Modifier.height(8.dp))
                duplicates.forEach { worker ->
                    DuplicateCandidateRow(
                        worker = worker,
                        onClick = { onReuse(worker.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Text(
                    text = stringResource(R.string.action_create_new_worker),
                    fontSize = 18.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontSize = 18.sp
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

/**
 * 确认新建同名工人（防误重名）
 */
@Composable
private fun ConfirmNewWorkerDialog(
    workerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_confirm_new_worker_title)) },
        text = {
            Text(stringResource(R.string.dialog_confirm_new_worker_message, workerName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_confirm),
                    fontSize = 18.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    fontSize = 18.sp
                )
            }
        }
    )
}

// 扩展函数：检测输入框失焦已用 onFocusChanged 直接实现，无需额外扩展
