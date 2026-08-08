// WageManagerTheme.kt - Compose 主题
//
// 设计要点：
// 1. 颜色从 XML 资源取，便于统一管理 / 后续支持多主题
// 2. 字体统一：大标题 28sp、统计数字 24sp、按钮文字 20sp、正文 ≥ 20sp
//    目标用户（50 岁带班妈妈）视力一般，全 App 强制大字号
// 3. M1 暂不区分明暗：夜间模式也用相同高对比度配色，避免老年用户看不清

package com.example.wagemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R

@Composable
fun WageManagerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = colorResource(R.color.wage_action_blue),
        onPrimary = Color.White,
        secondary = colorResource(R.color.wage_paid_green),
        onSecondary = Color.White,
        error = colorResource(R.color.wage_unpaid_red),
        onError = Color.White,
        background = colorResource(R.color.wage_background),
        onBackground = colorResource(R.color.wage_text_primary),
        surface = colorResource(R.color.wage_background),
        onSurface = colorResource(R.color.wage_text_primary),
        surfaceVariant = colorResource(R.color.wage_card_background),
        onSurfaceVariant = colorResource(R.color.wage_text_primary)
    )

    val typography = Typography(
        // 大标题：首页日期
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        // 统计数字：合计 / 未付条数 / 已付条数
        titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        // 按钮文字
        titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        // 正文
        bodyLarge = TextStyle(fontSize = 20.sp),
        bodyMedium = TextStyle(fontSize = 18.sp),
        labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
