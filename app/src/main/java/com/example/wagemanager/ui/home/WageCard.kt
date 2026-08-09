// WageCard.kt - 工资记录列表卡片
//
// 设计要点：
// 1. 未付项：左侧姓名 + 金额，右下角"✅ 标记已付"按钮
// 2. 已付项：左侧姓名 + 金额 + 右侧支付时间 HH:mm（绿色）
// 3. 长按已付项 → 弹操作菜单（撤销 / 删除）
// 4. 点击未付卡片 → 进编辑模式（不是打开菜单，避免误触）
// 5. M2 没有图片（M3 才接），所以"点击看收款码"留给 M3

package com.example.wagemanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.ui.components.BigButton
import com.example.wagemanager.util.MoneyUtils
import com.example.wagemanager.util.PaymentRules

@Composable
fun WageCard(
    item: HomeWageItem,
    onMarkPaidClick: () -> Unit,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.recordId) {
                detectTapGestures(
                    onTap = {
                        if (!item.isPaid) onEditClick()
                    },
                    onLongPress = {
                        if (item.isPaid) onLongClick()
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：姓名 + 金额
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "👤 ${item.workerName}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_text_primary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${MoneyUtils.formatCent(item.wageCent)} 元",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(
                        if (item.isPaid) R.color.wage_paid_green else R.color.wage_unpaid_red
                    )
                )
            }

            // 右侧：未付显示"标记已付"按钮；已付显示支付时间
            if (!item.isPaid) {
                Box(
                    modifier = Modifier
                        .background(
                            color = colorResource(R.color.wage_paid_green),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .pointerInput(item.recordId) {
                            // 让 Card 的 onTap 不吞掉按钮点击
                            detectTapGestures(onTap = { onMarkPaidClick() })
                        }
                ) {
                    Text(
                        text = stringResource(R.string.action_mark_paid),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.label_paid_time),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = PaymentRules.formatPaidTime(item.paidTime),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.wage_paid_green)
                    )
                }
            }
        }
    }
}