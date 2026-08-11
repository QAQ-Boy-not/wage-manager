// WorkerPickerDialog.kt - 工人选择器（全屏 Material Dialog）
//
// 设计要点：
// 1. 用 Dialog（不是 BottomSheet）：避免 ModalBottomSheet 嵌套限制
// 2. 全屏覆盖：Surface 满屏
// 3. 搜索框：按姓名 contains 模糊匹配（不含 UUID）
// 4. 已选置顶：选过的工人放最上面
// 5. 全选 / 全不选：一键操作（影响当前可见列表）
//
// V1.3 强制结论：
// - 内部 UUID 不暴露给用户
// - 只显示 👤 + 姓名（按 first_work_date 升序区分同名）

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wagemanager.util.DateRules
import java.time.LocalDate

/**
 * 工人选择项（UI 层 dto）
 */
data class WorkerPickItem(
    val workerId: String,
    val workerName: String,
    val firstWorkDate: LocalDate?
)

/**
 * 工人选择器（全屏 Dialog）
 *
 * @param allWorkers 所有工人列表（V1.3 按 first_work_date 升序）
 * @param initiallySelected 初始已选（编辑场景用，新建场景传空）
 * @param onConfirm 确认按钮回调（返回 workerId 列表）
 * @param onCreateNewWorker 新建工人回调（BatchAddBillSheet 调 Repository）
 * @param onDismiss 关闭回调
 */
@Composable
fun WorkerPickerDialog(
    allWorkers: List<WorkerPickItem>,
    initiallySelected: Set<String> = emptySet(),
    onConfirm: (List<String>) -> Unit,
    onCreateNewWorker: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateWorkerDialog by remember { mutableStateOf(false) }

    // 搜索过滤（按姓名 contains）
    val filtered = remember(allWorkers, searchQuery) {
        if (searchQuery.isBlank()) allWorkers
        else allWorkers.filter { it.workerName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // 已选 + 未选 分两组（已选置顶）
    val (selectedItems, unselectedItems) = remember(filtered, selected) {
        val sel = filtered.filter { it.workerId in selected }
        val unsel = filtered.filter { it.workerId !in selected }
        Pair(sel, unsel)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ===== 顶部：标题 + 关闭 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择工人",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text = "×", fontSize = 24.sp)
                    }
                }

                // ===== 搜索框 + + 新建工人 按钮 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索姓名...", fontSize = 18.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    if (onCreateNewWorker != null) {
                        Spacer(modifier = Modifier.size(8.dp))
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showCreateWorkerDialog = true }
                        ) {
                            Text("➕ 新建", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 已选 N 人 + 全选/全不选 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${selected.size} 人",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = if (selected.isEmpty()) Color.Gray else MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        selected = if (filtered.isNotEmpty() && filtered.all { it.workerId in selected }) {
                            selected - filtered.map { it.workerId }.toSet()
                        } else {
                            selected + filtered.map { it.workerId }.toSet()
                        }
                    }) {
                        Text(
                            text = if (filtered.isNotEmpty() && filtered.all { it.workerId in selected }) "全不选" else "全选",
                            fontSize = 16.sp
                        )
                    }
                }

                // ===== 工人列表 =====
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp, vertical = 8.dp
                    )
                ) {
                    if (selectedItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "已选",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(selectedItems, key = { "sel_${it.workerId}" }) { worker ->
                            WorkerPickerRow(
                                worker = worker,
                                isSelected = true,
                                onToggle = {
                                    selected = selected - worker.workerId
                                }
                            )
                        }
                    }
                    if (unselectedItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "其他工人",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(unselectedItems, key = { "unsel_${it.workerId}" }) { worker ->
                            WorkerPickerRow(
                                worker = worker,
                                isSelected = false,
                                onToggle = {
                                    selected = selected + worker.workerId
                                }
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "没有匹配的工人",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // ===== 底部：完成按钮 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onConfirm(selected.toList()) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "完成 (${selected.size})",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // ===== Bug17：新建工人对话框 =====
    if (showCreateWorkerDialog && onCreateNewWorker != null) {
        CreateWorkerDialog(
            onConfirm = { name ->
                onCreateNewWorker(name)
                showCreateWorkerDialog = false
            },
            onDismiss = { showCreateWorkerDialog = false }
        )
    }
}

/**
 * 工人列表项
 */
@Composable
private fun WorkerPickerRow(
    worker: WorkerPickItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "👤 ${worker.workerName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (worker.firstWorkDate != null) {
                Text(
                    text = "${DateRules.formatChineseDate(worker.firstWorkDate)} 首次",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * 新建工人对话框（Bug17）
 */
@Composable
private fun CreateWorkerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建工人", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("工人姓名") },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
                if (error != null) {
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                    Text(error!!, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (name.isBlank()) {
                    error = "姓名不能为空"
                    return@TextButton
                }
                onConfirm(name.trim())
            }) {
                Text("✅ 保存", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", fontSize = 18.sp)
            }
        }
    )
}