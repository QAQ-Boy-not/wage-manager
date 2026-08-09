// HomeTabs.kt - 未付 / 已付 双标签切换
//
// 设计要点：
// 1. 两个按钮并排，宽度相等
// 2. 选中态：未付红 / 已付绿
// 3. 未选态：灰色边框 + 灰色文字
// 4. 高度 ≥ 60dp，文字 ≥ 22sp（大字体大按钮）
// 5. 回调 onTabChange(toPaid: Boolean)

package com.example.wagemanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R

@Composable
fun HomeTabs(
    isPaidTab: Boolean,
    unpaidCount: Int,
    paidCount: Int,
    onTabChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.tab_unpaid, unpaidCount),
            isSelected = !isPaidTab,
            selectedColor = colorResource(R.color.wage_unpaid_red),
            onClick = { onTabChange(false) }
        )
        TabButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.tab_paid, paidCount),
            isSelected = isPaidTab,
            selectedColor = colorResource(R.color.wage_paid_green),
            onClick = { onTabChange(true) }
        )
    }
}

@Composable
private fun TabButton(
    modifier: Modifier,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val background = if (isSelected) selectedColor else Color.White
    val textColor = if (isSelected) Color.White else colorResource(R.color.wage_disabled_gray)
    val borderModifier = if (!isSelected) {
        Modifier.border(
            width = 2.dp,
            color = colorResource(R.color.wage_disabled_gray),
            shape = RoundedCornerShape(8.dp)
        )
    } else Modifier

    Row(
        modifier = modifier
            .height(60.dp)
            .background(color = background, shape = RoundedCornerShape(8.dp))
            .then(borderModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}