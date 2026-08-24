// DatePickerSheet.kt - 通用日期选择器（V1.3 M3 + M3.1 + M4 用户反馈调整）
//
// 设计要点：
// - ModalBottomSheet（含 Material3 DatePicker 日历）
// - 顶部：3 个快捷按钮（今天 / 昨天 / 前天）
// - 中部：DatePicker（material3 1.4.0+，显示中文 weekday）
// - 点击快捷按钮或日历某天 → 自动 onConfirm + 关闭弹窗
//
// M4.1 调整：
// - 恢复 DatePicker（之前 e8dc76d 删掉是过度简化）
// - 快捷按钮 + 日历双重入口：今天等用快捷，其他用日历
//
// Bug A：时区偏移 → atStartOfDay / atZone 一律用 ZoneOffset.UTC（双向 LocalDate ↔ millis）
// Bug B/C：DatePicker 自带 weekday 表头 → material3 1.4.0 渲染中文（一二三四五六日）

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
import androidx.compose.foundation.layout.widthIn
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
 * 通用日期选择 BottomSheet（M3 + M3.1 + M4.1）
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

    // tempDate：用户当前选中的日期
    var tempDate by remember { mutableStateOf(initialDate) }

    // 用 key 强制重建 DatePickerState（material3 1.4.0 DatePickerState.selectedDateMillis 是 private set）
    var forceRecreateKey by remember { mutableStateOf(0) }

    // 选日期的统一入口：更新 tempDate + 触发 DatePicker 重建 + 自动确认关闭
    fun selectDate(date: LocalDate) {
        if (date != tempDate) {
            tempDate = date
            forceRecreateKey++
        }
        onConfirm(date)
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
            // ===== 顶部：3 个快捷按钮（今天 / 昨天 / 前天）=====
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

            // ===== 中部：DatePicker 日历（material3 1.4.0）=====
            // key 包住，快捷按钮触发重建以更新选中日期
            key(forceRecreateKey) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = tempDate.atStartOfDay(ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
                )

                // 用户手动点日历某天 → 同步回 tempDate + 自动 onConfirm 关闭弹窗
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    val millis = datePickerState.selectedDateMillis ?: return@LaunchedEffect
                    val newDate = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    if (newDate != tempDate) {
                        tempDate = newDate
                        onConfirm(newDate)
                    }
                }

                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .align(Alignment.CenterHorizontally),
                    headline = { }
                )
            }
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
            .height(56.dp)
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_action_blue)
        )
    }
}