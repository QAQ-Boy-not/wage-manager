// RegisterBillSheet.kt - 单笔添加/编辑账单 BottomSheet（V1.3）
//
// 职责（V1.3）：
// - 单笔添加 / 编辑账单（工人详情页 [+ 订单] / 操作菜单 ✏️ 编辑）
// - 输入框：姓名 + 金额
// - V1.3 同名强制改名：UI 层只做基础校验（名字非空、金额合法）；
//   真正的同名校验在 repository.registerBill 里抛 DuplicateNameException。
//   本组件暂不展示 DuplicateNameException 错误（TODO：M2.x 加输入框标红）
// - 成功反馈条 + 自动清空

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.data.WageRepository
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
    val wageError: MoneyUtils.WageError? = null,
    val isSaving: Boolean = false,
    val successMessage: String? = null
)

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
                wageInput = initialWageInput
            )
        )
    }

    fun submit() {
        val name = form.nameInput.trim()
        if (name.isEmpty()) {
            // 同名校验在 Repository 里抛 DuplicateNameException；
            // 这里只做基础非空校验
            // TODO: 捕获 DuplicateNameException 显示 error_duplicate_name
            return
        }
        val parse = MoneyUtils.parseWageCent(form.wageInput)
        if (!parse.isValid) {
            form = form.copy(wageError = parse.error)
            return
        }

        form = form.copy(isSaving = true)
        scope.launch {
            try {
                if (isEditMode && editingBill != null) {
                    repository.updateWage(
                        recordId = editingBill.recordId,
                        name = name,
                        wageCent = parse.wageCent,
                        worksiteId = null,
                        notes = null
                    )
                    val msg = "✅ 已修改：$name ${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = form.copy(successMessage = msg)
                    // Bug 11 修复：编辑成功后关闭 BottomSheet（跟批量一致）
                    scope.launch {
                        delay(1_000)  // 短一点让用户看到反馈条
                        onDismiss()
                    }
                } else {
                    repository.registerBill(
                        name = name,
                        wageCent = parse.wageCent,
                        workDate = form.workDate,
                        worksiteId = null,
                        notes = null
                    )
                    val msg = "✅ 已添加：$name ${MoneyUtils.formatCent(parse.wageCent)}元"
                    form = form.copy(successMessage = msg)
                    // 新增模式也关闭 BottomSheet（妈妈点 + 加完一笔就回到列表）
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

            // 日期
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
            Spacer(modifier = Modifier.height(8.dp))

            // 工人姓名（编辑模式不可改；新建模式可改 + 同名强制改名）
            OutlinedTextField(
                value = form.nameInput,
                onValueChange = { form = form.copy(nameInput = it) },
                label = { Text(stringResource(R.string.bill_field_worker_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { /* 同名校验移到 Repository */ },
                singleLine = true,
                // Bug 13 修复：RegisterBillSheet 只在详情页用，工人已确定
                // 永远 disable 名字输入（避免妈妈误改成别人的名字触发"重复名称"）
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
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // 默认焦点在金额框（新建模式）
    LaunchedEffect(Unit) {
        if (!isEditMode && form.successMessage == null) {
            try {
                wageFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
}