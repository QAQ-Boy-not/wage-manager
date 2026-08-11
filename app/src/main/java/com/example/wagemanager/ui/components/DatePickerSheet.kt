// DatePickerSheet.kt - 通用日期选择器（V1.3 M3 新增 + M3.1 修复）
//
// 用途：在 WorkerList / WorkerDetail / AddBillSheet / BatchAddBillSheet 顶部使用
// 用户点击当前日期 → 弹这个 BottomSheet → 选日期 → 回调
//
// 设计要点：
// - ModalBottomSheet（含 Material DatePicker 日历）
// - 顶部：当前选中日期大字 + 快捷按钮（今天 / 昨天 / 前天）
// - 中部：自定义单字星期表头（一二三四五六日）+ DatePicker（material3 自带日历）
// - 底部：[✅ 选这个日期] [取消]
//
// M3.1 Bug 修复：
// - 快捷按钮之前只更新 tempDate 临时变量，DatePicker UI 不响应
// - 改用单向数据流：datePickerState 是唯一真相源，tempDate 从 state 推导
// - Material3 1.1.2 的 DatePicker 表头 cell 太窄，"星期X" 被裁切到"星"
// - 上方加一行"一二三四五六日"补全视觉对齐

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.util.DateRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 通用日期选择 BottomSheet（M3 新增 + M3.1 修复）
 *
 * @param initialDate 初始显示的日期
 * @param onConfirm 用户选完日期点确认（返回选中的日期）
 * @param onDismiss 关闭选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 单向数据流：datePickerState 是唯一真相源
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    )

    // 从 state 推导"当前选中日期"（避免冗余 var）
    val tempDate: LocalDate = datePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    } ?: initialDate

    // 选日期的统一入口（同时更新 DatePicker UI 选中态 + state）
    fun selectDate(date: LocalDate) {
        datePickerState.selectedDateMillis = date.atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
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
            // M3.1 Bug 修复：之前只更新 tempDate，DatePicker UI 不变
            // → 改为更新 datePickerState.selectedDateMillis
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

            // 自定义单字星期表头（补全下方 DatePicker 表头被裁切到"星"的问题）
            WeekdayHeader()

            // DatePicker 日历
            DatePicker(
                state = datePickerState,
                showModeToggle = false  // 不显示日历/输入切换按钮
            )

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

/**
 * 自定义单字星期表头（一二三四五六日）
 *
 * Material3 1.1.2 的 DatePicker 自带 weekday 表头 cell 太窄，
 * 显示"星期X"被裁切到只剩"星"。用这行单字表头作视觉对齐参考。
 */
@Composable
private fun WeekdayHeader() {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),  // 24dp 水平 padding 对齐 DatePicker 的 cell
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekdays.forEach { day ->
            Text(
                text = day,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}