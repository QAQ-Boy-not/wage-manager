// ManagementScreen.kt - 管理页（V1.3 三实体 CRUD 入口）
//
// 3 Tab：[👷 工人] [📍 工区] [📋 订单]
// 每个 Tab 显示列表 + "+ 新增"按钮 + 点击进详情
//
// V1.3 简化：列表为主，不做完整 CRUD UI
// 新增：弹简单 BottomSheet（工人姓名 / 工区名称 / 订单金额）
// 删除：列表项长按弹确认对话框

package com.example.wagemanager.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wagemanager.R
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.data.Worker
import com.example.wagemanager.data.Worksite
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils
import kotlinx.coroutines.launch

/**
 * 管理页 Tab
 */
enum class ManagementTab(val label: String, val emoji: String) {
    WORKER("工人", "👷"),
    WORKSITE("工区", "📍"),
    // 订单 Tab 在 M4 完善，本次先列骨架
    ORDER("订单", "📋")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    repository: WageRepository,
    onBack: () -> Unit,
    onWorkerClick: (String) -> Unit
) {
    var currentTab by remember { mutableStateOf(ManagementTab.WORKER) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部导航 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "← 返回",
                        fontSize = 18.sp,
                        color = colorResource(R.color.wage_action_blue)
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "⚙️ 管理",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ===== Tab 栏 =====
            TabRow(selectedTabIndex = currentTab.ordinal) {
                ManagementTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        text = {
                            Text(
                                text = "${tab.emoji} ${tab.label}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // ===== Tab 内容 =====
            when (currentTab) {
                ManagementTab.WORKER -> WorkerListTab(
                    repository = repository,
                    onWorkerClick = onWorkerClick
                )
                ManagementTab.WORKSITE -> WorksiteListTab(
                    repository = repository
                )
                ManagementTab.ORDER -> OrderListTab(
                    repository = repository,
                    onOrderClick = onWorkerClick
                )
            }
        }
    }
}

// ============== 工人 Tab ==============

@Composable
private fun WorkerListTab(
    repository: WageRepository,
    onWorkerClick: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var liveWorkers by remember { mutableStateOf<List<Worker>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        liveWorkers = repository.listAllWorkers()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (liveWorkers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "还没有工人\n点右下角 ➕ 新增",
                        fontSize = 18.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(liveWorkers, key = { it.id }) { worker ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWorkerClick(worker.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = colorResource(R.color.wage_card_background)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 ${worker.name}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (worker.firstWorkDate != null) {
                                    Text(
                                        text = DateRules.formatChineseDate(worker.firstWorkDate),
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.material3.FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = colorResource(R.color.wage_action_blue)
        ) {
            Text("➕", fontSize = 28.sp, color = Color.White)
        }
    }

    if (showAdd) {
        AddWorkerSheet(
            repository = repository,
            onDismiss = { showAdd = false },
            onSaved = {
                showAdd = false
                scope.launch { liveWorkers = repository.listAllWorkers() }
            }
        )
    }
}

// ============== 工区 Tab ==============

@Composable
private fun WorksiteListTab(
    repository: WageRepository
) {
    var showAdd by remember { mutableStateOf(false) }
    var liveWorksites by remember { mutableStateOf<List<Worksite>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repository.observeWorksites().collect {
            liveWorksites = it
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (liveWorksites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "还没有工区\n点右下角 ➕ 新增",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(liveWorksites, key = { it.id }) { ws ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = colorResource(R.color.wage_card_background)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "📍 ${ws.name}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ws.address,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.material3.FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = colorResource(R.color.wage_action_blue)
        ) {
            Text("➕", fontSize = 28.sp, color = Color.White)
        }
    }

    if (showAdd) {
        AddWorksiteSheet(
            repository = repository,
            onDismiss = { showAdd = false }
        )
    }
}

// ============== 订单 Tab（M4 完善：带筛选的账单列表） ==============

@Composable
private fun OrderListTab(
    repository: WageRepository,
    onOrderClick: (String) -> Unit
) {
    var isPaidTab by remember { mutableStateOf<Boolean?>(null) } // null = 全部
    var allBills by remember { mutableStateOf<List<com.example.wagemanager.data.WageRecordWithWorker>>(emptyList()) }

    LaunchedEffect(Unit) {
        repository.observeAllBills().collect { allBills = it }
    }

    val visibleBills = allBills.filter { bill ->
        when (isPaidTab) {
            null -> true      // 全部
            true -> bill.record.isPaid   // 已付
            false -> !bill.record.isPaid  // 未付
        }
    }

    val unpaidCount = allBills.count { !it.record.isPaid }
    val paidCount = allBills.count { it.record.isPaid }

    Column(modifier = Modifier.fillMaxSize()) {
        // 筛选 Tab：[未付] [已付] [全部]
        TabRow(
            selectedTabIndex = when (isPaidTab) {
                false -> 0
                true -> 1
                null -> 2
            }
        ) {
            Tab(
                selected = isPaidTab == false,
                onClick = { isPaidTab = false },
                text = { Text("🔴 未付 ($unpaidCount)", fontSize = 14.sp) }
            )
            Tab(
                selected = isPaidTab == true,
                onClick = { isPaidTab = true },
                text = { Text("🟢 已付 ($paidCount)", fontSize = 14.sp) }
            )
            Tab(
                selected = isPaidTab == null,
                onClick = { isPaidTab = null },
                text = { Text("全部 (${allBills.size})", fontSize = 14.sp) }
            )
        }

        // 账单列表
        if (visibleBills.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = when (isPaidTab) {
                        false -> "没有未付账单 ✅"
                        true -> "没有已付账单"
                        null -> "还没有账单\n回到首页添加"
                    },
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleBills, key = { it.record.id }) { billWithWorker ->
                    OrderBillCard(
                        bill = billWithWorker,
                        onClick = { onOrderClick(billWithWorker.record.workerId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderBillCard(
    bill: com.example.wagemanager.data.WageRecordWithWorker,
    onClick: () -> Unit
) {
    val amountColor = if (bill.record.isPaid) R.color.wage_paid_green else R.color.wage_unpaid_red

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：日期 + 工人
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateRules.formatChineseDate(bill.record.workDate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "👤 ${bill.workerName}",
                    fontSize = 14.sp,
                    color = colorResource(R.color.wage_text_primary)
                )
                if (!bill.worksiteName.isNullOrBlank() || !bill.record.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            bill.worksiteName?.let { "📍 $it" },
                            bill.record.notes?.takeIf { it.isNotBlank() }?.let { "📝 $it" }
                        ).joinToString(" "),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            // 右侧：金额
            Text(
                text = "${if (bill.record.isPaid) "🟢" else "🔴"} ${MoneyUtils.formatCent(bill.record.wageCent)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(amountColor)
            )
        }
    }
}

// ============== 新增工人 BottomSheet ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorkerSheet(
    repository: WageRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("新增工人", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("工人姓名") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "姓名不能为空"
                        return@Button
                    }
                    scope.launch {
                        try {
                            repository.insertWorker(trimmed, java.time.LocalDate.now())
                            onSaved()
                        } catch (e: Exception) {
                            error = e.message ?: "添加失败"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("✅ 保存", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ============== 新增工区 BottomSheet ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorksiteSheet(
    repository: WageRepository,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("新增工区", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("工区名称") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = address,
                onValueChange = { address = it; error = null },
                label = { Text("详细地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = {
                    if (name.isBlank() || address.isBlank()) {
                        error = "名称和地址都不能为空"
                        return@Button
                    }
                    scope.launch {
                        try {
                            repository.createWorksite(name, address)
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "添加失败"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("✅ 保存", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}