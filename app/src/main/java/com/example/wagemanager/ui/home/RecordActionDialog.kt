// RecordActionDialog.kt - 长按已付项弹出的操作菜单 + 二次确认对话框
//
// 设计要点：
// 1. 长按已付项 → 弹操作菜单（撤销付款 / 删除记录），用 AlertDialog 简化实现
//    （Material3 标准做法是用 DropdownMenu，但 AlertDialog 更直观且点击区域大）
// 2. 选完操作 → 关闭菜单 + 触发二次确认（PendingConfirmAction）
// 3. 二次确认对话框：标题 + 说明 + 取消 / 确认
//
// 设计选择：为啥用 AlertDialog 而不是 DropdownMenu？
// - 目标用户 50 岁，DropdownMenu 太小容易点错
// - AlertDialog 按钮大、文字大，符合"大字体大按钮"原则

package com.example.wagemanager.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R

/**
 * 长按已付项弹出的操作菜单（撤销付款 / 删除记录）。
 *
 * @param recordId 当前选中的记录 id（用于回调）
 * @param workerName 显示给用户看，让用户确认是哪条记录
 * @param onSelected 用户选了某项操作
 * @param onDismiss 用户关闭菜单
 */
@Composable
fun RecordActionMenuDialog(
    recordId: Long,
    workerName: String,
    onSelected: (Long, ActionOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_action_menu_title, workerName),
                fontSize = 22.sp
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_action_menu_message),
                fontSize = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = { onSelected(recordId, ActionOption.REVOKE_PAYMENT) }) {
                Text(
                    text = stringResource(R.string.action_revoke_payment),
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onSelected(recordId, ActionOption.DELETE_RECORD) }) {
                Text(
                    text = stringResource(R.string.action_delete_record),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    )
}

/**
 * 通用二次确认对话框（标记已付 / 撤销付款 / 删除记录 共用）。
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_confirm),
    cancelLabel: String = stringResource(R.string.action_cancel),
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontSize = 22.sp)
        },
        text = {
            Text(text = message, fontSize = 18.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = cancelLabel, fontSize = 20.sp)
            }
        }
    )
}