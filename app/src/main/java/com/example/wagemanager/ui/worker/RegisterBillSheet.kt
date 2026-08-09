// RegisterBillSheet.kt - 添加/编辑账单 BottomSheet（V1.3 + M2.1 Bug14/15 修复）
//
// 设计要点（M2.1）：
// - 永远 disable 工人姓名输入（RegisterBillSheet 只在详情页用，工人已确定）
// - 加 workerId 参数：调 addBillToWorker 而不是 registerBill（不走同名检查）
// - 加工区下拉 + 备注文本框（之前偷懒没加，Bug15 修复）
// - 编辑成功后关闭 BottomSheet（Bug11 修复）

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.wagemanager.util.MoneyUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 单笔添加/编辑表单状态
 */
private data class BillFormState(
    val workDate: LocalDate = LocalDate.now(),
    val nameInput: String = "",
    val wageInput: String = "",
    val notes: String = "",
    val worksiteId: String? = null,
    val wageError: MoneyUtils.WageError? = null,
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val showWorksiteMenu: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterBillSheet(
    repository: WageRepository,
    /**
     * 已存在工人的 ID（M2.1 Bug14：详情页"+ 添加账单"专用）
     * - null：调 repository.registerBill（待实现，新工人 + 强制改名场景）
     * - 非 null：调 repository.addBillToWorker（详情页专用）
     */
    workerId: String? = null,
    initialName: String? = null,
    editingBill: BillItem? = null,
    @Suppress("UNUSED_PARAMETER") editingBillId: Long? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val wageFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val isEditMode = editingBill != null
    val initialWageInput = editingBill?.let {
        val yuan = it.wageCent.toDouble() / 100.0
        if (yuan == yuan.toLong().toDouble()) yuan.toLong().toString()
        else "%.2f".format(yuan)
    } ?: ""

    var form by remember {
        mutableStateOf(
            BillFormState(
                workDate = editingBill?.workDate ?: LocalDate.now(),
                nameInput = initialName.orEmpty(),
                wageInput = initialWageInput,
                notes = editingBill?.notes.orEmpty()
            )
        )
    }

    // 工区列表（Bug15：加载已有工区）
    var worksites by remember { mutableStateOf<List<Worksite>>(emptyList()) }
    LaunchedEffect(Unit) {
        repository.observeWorksites().collect { worksites = it }
    }

    fun submit() {
        val parse = MoneyUtils.parseWageCent(form.wageInput)
        if (!parse.isValid) {
            form = form.copy(wageError = parse.error)
            return
        }

        form = form.copy(isSaving = true)
        scope.launch {
            try {
                if (isEditMode && editingBill != null) {
                    // 编辑模式
                    repository.updateWage(
                        recordId = editingBill.recordId,
                        name = form.nameInput.ifBlank { initialName.orEmpty() },
                        wageCent = parse.wageCent,
                        worksiteId = form.worksiteId,
                        notes = form.notes.takeIf { it.isNotBlank() }
                    )
                    val msg = "✅ 已修改：${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = form.copy(successMessage = msg)
                    scope.launch {
                        delay(1_000)
                        onDismiss()
                    }
                } else if (workerId != null) {
                    // 详情页新增模式：直接给已存在工人加账单（Bug14）
                    repository.addBillToWorker(
                        workerId = workerId,
                        wageCent = parse.wageCent,
                        workDate = form.workDate,
                        worksiteId = form.worksiteId,
                        notes = form.notes.takeIf { it.isNotBlank() }
                    )
                    val msg = "✅ 已添加：${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = form.copy(successMessage = msg)
                    scope.launch {
                        delay(1_000)
                        onDismiss()
                    }
                } else {
                    // 新工人 + 强制改名（保留 registerBill 路径）
                    val name = form.nameInput.trim()
                    if (name.isEmpty()) {
                        form = form.copy(isSaving = false)
                        return@launch
                    }
                    repository.registerBill(
                        name = name,
                        wageCent = parse.wageCent,
                        workDate = form.workDate,
                        worksiteId = form.worksiteId,
                        notes = form.notes.takeIf { it.isNotBlank() }
                    )
                    val msg = "✅ 已添加：$name ${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = form.copy(successMessage = msg)
                    scope.launch {
                        delay(1_000)
                        onDismiss()
                    }
                }
            } catch (e: Exception) {
                form = form.copy(
                    isSaving = false,
                    successMessage = "❌ 失败：${e.message ?: "未知错误"}"
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 标题
            Text(
                text = stringResource(
                    if (isEditMode) R.string.bill_edit_title
                    else R.string.bill_register_title
                ),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 反馈条
            val successMsg = form.successMessage
            if (successMsg != null) {
                Text(
                    text = successMsg,
                    fontSize = 16.sp,
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
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 日期（只读，M4 加日期选择器）
            Text(
                text = stringResource(R.string.bill_field_work_date, form.workDate.toString()),
                fontSize = 14.sp,
                color = colorResource(R.color.wage_text_primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorResource(R.color.wage_card_background),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 工人姓名（永远 disable，详情页上下文已确定）
            OutlinedTextField(
                value = form.nameInput,
                onValueChange = { },
                label = { Text(stringResource(R.string.bill_field_worker_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(FocusRequester()),
                singleLine = true,
                enabled = false,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

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
            Spacer(modifier = Modifier.height(8.dp))

            // Bug15：工区下拉
            WorksiteSelectorSimple(
                worksites = worksites,
                selectedId = form.worksiteId,
                onSelected = { id -> form = form.copy(worksiteId = id) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Bug15：备注输入框
            OutlinedTextField(
                value = form.notes,
                onValueChange = { form = form.copy(notes = it) },
                label = { Text(stringResource(R.string.bill_field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 提交 / 取消
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
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    // 默认焦点在金额框
    LaunchedEffect(Unit) {
        if (!isEditMode) {
            try {
                wageFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
}

/**
 * 简化工区选择器（V1.3 M2.1：详情页用，下拉 + 新建快捷入口）
 */
@Composable
private fun WorksiteSelectorSimple(
    worksites: List<Worksite>,
    selectedId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = worksites.firstOrNull { it.id == selectedId }?.name ?: ""

    Box {
        Text(
            text = if (selectedName.isEmpty()) "选择工区（可选）▼" else "📍 $selectedName ▼",
            fontSize = 16.sp,
            color = if (selectedName.isEmpty()) Color.Gray else colorResource(R.color.wage_text_primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(R.color.wage_card_background),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "无" 选项（清除选择）
            DropdownMenuItem(
                text = { Text("不指定", fontSize = 16.sp) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            if (worksites.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无工区，请去管理页添加", fontSize = 14.sp) },
                    onClick = { expanded = false }
                )
            } else {
                worksites.forEach { ws ->
                    DropdownMenuItem(
                        text = { Text("${ws.name}（${ws.address}）", fontSize = 16.sp) },
                        onClick = {
                            onSelected(ws.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}