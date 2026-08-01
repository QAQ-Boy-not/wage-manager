package com.example.wagemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MainActivity - App 入口
 *
 * 这是 Android 应用的第一个界面。
 * ComponentActivity 是支持 Compose 的 Activity 基类。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContent 是 Compose 的入口：把 Composable 函数渲染到屏幕上
        setContent {
            HomeScreen()
        }
    }
}

/**
 * HomeScreen - 整个屏幕的 UI
 *
 * @Composable 注解：告诉编译器"这是一个 UI 组件"
 * 这个函数描述了整个屏幕长什么样
 */
@Composable
fun HomeScreen() {
    // 状态变量：count 改变时，UI 自动重新渲染
    // remember { mutableStateOf(0) }：记住这个值，旋转屏幕也不会丢
    var count by remember { mutableStateOf(0) }

    // Surface：最外层容器，设置背景色
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Column：垂直排列的容器
        Column(
            modifier = Modifier
                .fillMaxSize()        // 占满整个屏幕
                .padding(24.dp),       // 四周留 24dp 边距
            verticalArrangement = Arrangement.Center,         // 垂直居中
            horizontalAlignment = Alignment.CenterHorizontally  // 水平居中
        ) {
            // 大标题
            Text(
                text = "Hello CI!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 间距
            Spacer(modifier = Modifier.height(24.dp))

            // 计数显示
            Text(
                text = "构建次数：$count",
                fontSize = 24.sp
            )

            // 间距
            Spacer(modifier = Modifier.height(32.dp))

            // 大按钮：点击 +1
            Button(
                onClick = { count++ },       // 点击时 count 加 1
                modifier = Modifier
                    .fillMaxWidth()           // 宽度撑满
                    .height(80.dp)            // 高度 80dp（大按钮）
            ) {
                Text(
                    text = "点击 +1",
                    fontSize = 24.sp
                )
            }

            // 间距
            Spacer(modifier = Modifier.height(48.dp))

            // 底部提示：证明 CI 构建成功
            Text(
                text = "✅ 来自 GitHub Actions 构建",
                fontSize = 18.sp,
                color = Color(0xFF4CAF50)   // 绿色
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "如果看到这行字，说明整个 CI 流程跑通了 🎉",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}