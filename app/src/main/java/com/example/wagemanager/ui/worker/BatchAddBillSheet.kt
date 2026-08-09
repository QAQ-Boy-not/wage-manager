// BatchAddBillSheet.kt - 批量添加账单 BottomSheet（V1.3）
//
// 设计要点：
// 1. 一次为多个工人创建账单，共享工区/金额/备注/日期
// 2. 工区下拉：选择已有工区 + "[+ 新建工区]" 快捷入口
// 3. 工人选择：点击 → 弹 WorkerPickerDialog（全屏 Dialog）
// 4. 同名强制改名：批量添加路径下不会触发（picker 列表来自数据库，无同名）

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.wagemanager.data.Worksite
import com.example.wagemanager.ui.components.BigButton
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

/**
 * 批量添加账单表单状态
 */
private data class BatchFormState(
    val worksiteId: String? = null,
    val worksiteName: String = "",
    val wageInput: String = "",
    val notes: String = "",
    val wageError: MoneyUtils.WageError? = null,
    val selectedWorkerIds: Set<String> = emptySet(),
    val selectedWorkerNames: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val showWorksitePicker: Boolean = false,
    val showWorkerPicker: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchAddBillSheet(
    repository: WageRepository,
    workDate: LocalDate,
    /**
     * Bug 4：详情页 FAB 用 preselectedWorkerIds 跳过选工人步骤
     * （默认空 = 首页批量入口，需要手动选工人）
     */
    preselectedWorkerIds: Set<String> = emptySet(),
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var form by remember {
        mutableStateOf(
            BatchFormState(
                selectedWorkerIds = preselectedWorkerIds
            )
        )
    }
    var allWorkers by remember { mutableStateOf<List<WorkerPickItem>>(emptyList()) }
    var worksites by remember { mutableStateOf<List<Worksite>>(emptyList()) }

    // 加载所有工人 + 工区
    LaunchedEffect(Unit) {
        // V1.3 简化：一次性加载所有工人 + 工区（< 100 个）
        allWorkers = repository.listAllWorkers().map {
            WorkerPickItem(it.id, it.name, it.firstWorkDate)
        }
    }
    LaunchedEffect(Unit) {
        repository.observeWorksites().collect { wsList ->
            worksites = wsList
        }
    }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.batch_add_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 反馈条
            val successMsg = form.successMessage
            if (successMsg != null) {
                Text(
                    text = successMsg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (successMsg.startsWith("✅")) colorResource(R.color.wage_paid_green)
                    else colorResource(R.color.wage_unpaid_red),
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

            // 工区下拉
            Text(
                text = stringResource(R.string.batch_field_worksite),
                fontSize = 16.sp,
                color = colorResource(R.color.wage_text_primary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            WorksiteSelector(
                worksites = worksites,
                selectedId = form.worksiteId,
                onSelected = { id, name -> form = form.copy(worksiteId = id, worksiteName = name) },
                onCreateNew = { form = form.copy(showWorksitePicker = true) },
                onLoaded = { worksites = it }
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
                label = { Text(stringResource(R.string.batch_field_wage)) },
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
                                    else -> R.string.error_wage_invalid_format
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 备注
            OutlinedTextField(
                value = form.notes,
                onValueChange = { form = form.copy(notes = it) },
                label = { Text(stringResource(R.string.batch_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 选择工人按钮（Bug 4：详情页预选时不弹 picker，只显示固定文本）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorResource(R.color.wage_card_background),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        enabled = preselectedWorkerIds.isEmpty()
                    ) {
                        if (preselectedWorkerIds.isEmpty()) {
                            form = form.copy(showWorkerPicker = true)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "👷",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (preselectedWorkerIds.isNotEmpty())
                                stringResource(R.string.batch_field_preselected_workers)
                            else
                                stringResource(R.string.batch_field_select_workers),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.wage_text_primary)
                        )
                        Text(
                            text = if (form.selectedWorkerIds.isEmpty())
                                stringResource(R.string.batch_field_no_worker)
                            else
                                "${form.selectedWorkerIds.size} 人：${form.selectedWorkerNames.take(3).joinToString("、")}${if (form.selectedWorkerNames.size > 3) "..." else ""}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = "选择 ›",
                        fontSize = 16.sp,
                        color = colorResource(R.color.wage_action_blue)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 提交
            BigButton(
                text = stringResource(
                    R.string.batch_action_submit,
                    form.selectedWorkerIds.size
                ),
                backgroundColor = MaterialTheme.colorScheme.primary,
                enabled = !form.isSaving && form.selectedWorkerIds.isNotEmpty() && form.worksiteId != null,
                onClick = {
                    focusManager.clearFocus()
                    submitBatch(
                        repository = repository,
                        workDate = workDate,
                        currentForm = form,
                        onStateChange = { form = it },
                        onSuccess = { count ->
                            // Bug 8 修复：批量成功直接关闭 BottomSheet
                            // （不再连续添加，避免误操作）
                            onDismiss()
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ===== 工人选择器 =====
    if (form.showWorkerPicker) {
        WorkerPickerDialog(
            allWorkers = allWorkers,
            initiallySelected = form.selectedWorkerIds,
            onConfirm = { selectedIds ->
                val names = allWorkers.filter { it.workerId in selectedIds }.map { it.workerName }
                form = form.copy(
                    selectedWorkerIds = selectedIds.toSet(),
                    selectedWorkerNames = names,
                    showWorkerPicker = false
                )
            },
            onDismiss = { form = form.copy(showWorkerPicker = false) }
        )
    }

    // ===== 加载数据 =====
}

/**
 * 工区选择器（简化：下拉）
 */
@Composable
private fun WorksiteSelector(
    worksites: List<Worksite>,
    selectedId: String?,
    onSelected: (String, String) -> Unit,
    onCreateNew: () -> Unit,
    onLoaded: (List<Worksite>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = worksites.firstOrNull { it.id == selectedId }?.name ?: ""

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(R.color.wage_card_background),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedName.isEmpty()) "选择工区 ▼" else "$selectedName ▼",
                fontSize = 18.sp,
                color = if (selectedName.isEmpty()) Color.Gray else colorResource(R.color.wage_text_primary),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCreateNew) {
                Text(text = "+ 新建工区", fontSize = 16.sp, color = colorResource(R.color.wage_action_blue))
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (worksites.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无工区，请点击'+ 新建工区'") },
                    onClick = { expanded = false }
                )
            } else {
                worksites.forEach { ws ->
                    DropdownMenuItem(
                        text = { Text("${ws.name}（${ws.address}）", fontSize = 16.sp) },
                        onClick = {
                            onSelected(ws.id, ws.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun submitBatch(
    repository: WageRepository,
    workDate: LocalDate,
    currentForm: BatchFormState,
    onStateChange: (BatchFormState) -> Unit,
    onSuccess: (Int) -> Unit
) {
    val wage = MoneyUtils.parseWageCent(currentForm.wageInput)
    if (!wage.isValid) {
        onStateChange(currentForm.copy(wageError = wage.error))
        return
    }
    if (currentForm.worksiteId == null) return
    if (currentForm.selectedWorkerIds.isEmpty()) return

    onStateChange(currentForm.copy(isSaving = true))

    kotlinx.coroutines.GlobalScope.launch {
        try {
            val count = repository.registerBills(
                workerIds = currentForm.selectedWorkerIds.toList(),
                worksiteId = currentForm.worksiteId,
                wageCent = wage.wageCent,
                notes = currentForm.notes.takeIf { it.isNotBlank() },
                workDate = workDate
            )
            withContext(Dispatchers.Main) {
                onSuccess(count)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onStateChange(
                    currentForm.copy(
                        isSaving = false,
                        successMessage = "❌ 添加失败：${e.message ?: "未知错误"}"
                    )
                )
                kotlinx.coroutines.delay(2_000)
                onStateChange(currentForm.copy(successMessage = null))
            }
        }
    }
}