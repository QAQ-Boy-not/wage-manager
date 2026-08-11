// DatePickerSheet.kt - 通用日期选择器（V1.3 M3 + M3.1 修复）
//
// 用途：在 WorkerList / WorkerDetail / AddBillSheet / BatchAddBillSheet 顶部使用
// 用户点击当前日期 → 弹这个 BottomSheet → 选日期 → 回调
//
// 设计要点：
// - ModalBottomSheet（含 Material DatePicker 日历）
// - 顶部：当前选中日期大字 + 快捷按钮（今天 / 昨天 / 前天）
// - 中部：自定义单字星期表头（一二三四五六日，通过 weekdays slot 注入 DatePicker，天然对齐）
// - 底部：[✅ 选这个日期] [取消]
//
// M3.1 Bug 修复：
// - A：时区偏移 → atStartOfDay / atZone 一律用 ZoneOffset.UTC（双向 LocalDate ↔ millis）
// - B：DatePicker 自带 weekday 表头被裁切到"星" → BOM 升级到 1.2.0，用 weekdays slot 整体替换
// - C：周六周日列错位 → BOM 1.2.0 slot 内部天然对齐
// - 删除 key(forceRecreateKey) hack → BOM 1.2.0 公开 selectDate() 方法，直接调即可

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
import java.time.ZoneOffset

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

    // BOM 2024.02.00 / material3 1.2.0 公开了 selectDate() 方法
    // state 是唯一真相源，selectedDateMillis 变化后 UI 自动更新
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli()
    )

    // 从 state 推导"当前选中日期"（避免冗余 var）
    val tempDate: LocalDate = datePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    } ?: initialDate

    // 选日期的统一入口（用 material3 1.2.0 新增的公开方法 selectDate）
    fun selectDate(date: LocalDate) {
        datePickerState.selectDate(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
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

            // DatePicker 日历（weekdays slot 注入自定义表头，天然对齐，根治 Bug B/C）
            DatePicker(
                state = datePickerState,
                showModeToggle = false,  // 不显示日历/输入切换按钮
                weekdays = { WeekdayHeader() }
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
 * BOM 1.2.0 起，DatePicker 暴露 weekdays slot，传入后整体替换默认 weekday 表头。
 * slot 内部和 day cell 共享同一布局容器，天然对齐，不再需要外部 padding 调整。
 */
@Composable
private fun WeekdayHeader() {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier.fillMaxWidth(),
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