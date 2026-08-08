// BigButton.kt - 大按钮组件（贯穿全 App 的样式基线）
//
// 设计要点：
// 1. 高度 ≥ 64dp、文字 ≥ 20sp，符合老年用户"大按钮"偏好
// 2. 白字 + 深色背景，对比度足够
// 3. disabled 态明显：灰底 + 灰字，肉眼可辨
// 4. 回调参数放最后（DEVELOPMENT_GUIDELINES.md 规范）

package com.example.wagemanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BigButton(
    text: String,
    backgroundColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF9E9E9E),
            disabledContentColor = Color(0xFFE0E0E0)
        )
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
