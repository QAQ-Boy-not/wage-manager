// DatePickerSheet.kt - 通用日期选择器（V1.3 M3 + M3.1 修复）
//
// 用途：在 WorkerList / WorkerDetail / AddBillSheet / BatchAddBillSheet 顶部使用
// 用户点击当前日期 → 弹这个 BottomSheet → 选日期 → 回调
//
// 设计要点：
// - ModalBottomSheet（含 Material3 DatePicker 日历）
// - 顶部：当前选中日期大字 + 快捷按钮（今天 / 昨天 / 前天）
// - 中部：DatePicker（material3 1.4.0+，预期 weekday 表头显示'一二三四五六日'而非'星'）
// - 底部：[✅ 选这个日期] [取消]
//
// M3.1 修复：
// - A：时区偏移 → atStartOfDay / atZone 一律用 ZoneOffset.UTC（双向 LocalDate ↔ millis）
// - B："星"字问题：升 BOM 2026.01.01 / material3 1.4.0 修复（不再用外置 WeekdayHeader）
// - 不使用 selectDate() method 或 weekdays slot（material3 1.4.0 是否有未确认）
// - 快捷按钮用 key(forceRecreateKey) 重建 DatePickerState（兼容所有 BOM 版本）

package com.example.wagemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.util.DateRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 通用日期选择 BottomSheet（M3 新增 + M3.1 修复）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // tempDate：用户当前选中的日期
    var tempDate by remember { mutableStateOf(initialDate) }

    // 用 key 强制重建 DatePickerState（material3 DatePickerState.selectedDateMillis 是 private set）
    var forceRecreateKey by remember { mutableStateOf(0) }

    // 选日期的统一入口
    fun selectDate(date: LocalDate) {
        if (date != tempDate) {
            tempDate = date
            forceRecreateKey++
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 标题
            Text(
                text = stringResource(R.string.date_picker_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 当前选中日期（大字）
            Text(
                text = DateRules.formatChineseDate(tempDate),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 快捷按钮（今天 / 昨天 / 前天）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickDateButton(
                    label = stringResource(R.string.date_picker_today),
                    onClick = { selectDate(LocalDate.now()) },
                    modifier = Modifier.weight(1f)
                )
                QuickDateButton(
                    label = stringResource(R.string.date_picker_yesterday),
                    onClick = { selectDate(LocalDate.now().minusDays(1)) },
                    modifier = Modifier.weight(1f)
                )
                QuickDateButton(
                    label = stringResource(R.string.date_picker_day_before),
                    onClick = { selectDate(LocalDate.now().minusDays(2)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // DatePicker 日历（material3 1.4.0 basic API，让 1.4.0 自己渲染 weekday 表头）
            // key 包住，快捷按钮触发重建以更新选中日期
            key(forceRecreateKey) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = tempDate.atStartOfDay(ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
                )

                // 用户手动点日历某天 → 同步回 tempDate
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
                    val newDate = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    if (newDate != tempDate) {
                        tempDate = newDate
                    }
                }

                DatePicker(
                    state = datePickerState,
                    showModeToggle = false
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = { onConfirm(tempDate) }) {
                    Text(
                        text = stringResource(R.string.date_picker_confirm),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.wage_action_blue)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickDateButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = colorResource(R.color.wage_card_background),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_action_blue)
        )
    }
}