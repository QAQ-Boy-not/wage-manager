// DatePickerSheet.kt - 极简版（M4 用户反馈：去掉 DatePicker）
//
// 设计要点：
// - ModalBottomSheet 弹窗 + 3 个快捷按钮 Row（今天/昨天/前天）
// - 不再有 DatePicker 日历（用户反馈：快捷按钮下空白不必要）
// - 点任意按钮 → 自动 onConfirm + 关闭弹窗
// - 点弹窗外面 → onDismiss 取消
//
// 权衡：失去选任意日期的能力。妈妈只能选今天/昨天/前天。
// 这是 M4 用户明确决策：极简优先，不需要任意日期选择。

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.LocalDate

/**
 * 通用日期选择 BottomSheet（M3 新增 + M3.1 修复 + M4 极简化）
 *
 * @param initialDate 初始显示的日期（保留用于将来扩展）
 * @param onConfirm 用户选完日期点确认（返回选中的日期）
 * @param onDismiss 关闭选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    @Suppress("UNUSED_PARAMETER") initialDate: LocalDate,  // 保留参数兼容调用方
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // 仅 3 个快捷按钮：今天 / 昨天 / 前天
            // M4 极简：去掉 DatePicker（用户反馈空白不必要）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickDateButton(
                    label = stringResource(R.string.date_picker_today),
                    onClick = { onConfirm(LocalDate.now()) },
                    modifier = Modifier.weight(1f)
                )
                QuickDateButton(
                    label = stringResource(R.string.date_picker_yesterday),
                    onClick = { onConfirm(LocalDate.now().minusDays(1)) },
                    modifier = Modifier.weight(1f)
                )
                QuickDateButton(
                    label = stringResource(R.string.date_picker_day_before),
                    onClick = { onConfirm(LocalDate.now().minusDays(2)) },
                    modifier = Modifier.weight(1f)
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