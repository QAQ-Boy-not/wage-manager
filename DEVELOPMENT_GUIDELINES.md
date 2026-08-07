# 开发规范与命名约定

> 本项目的代码规范，所有新代码必须遵守。

---

## 1️⃣ 命名规范（Kotlin 通用）

| 类型 | 规则 | 例子 |
|------|------|------|
| **类** | PascalCase（首字母大写） | `HomeScreen`, `WorkerDao` |
| **函数/方法** | camelCase（首字母小写） | `onScanned`, `loadWorkers` |
| **变量/属性** | camelCase | `workerName`, `wageAmount` |
| **常量** | UPPER_SNAKE_CASE | `MAX_WAGE`, `DB_NAME` |
| **包名** | 全小写 | `com.example.wagemanager.data` |

---

## 2️⃣ Composable 函数命名

### 屏幕级（顶级界面）

```kotlin
@Composable
fun HomeScreen() { ... }           // 首页
@Composable
fun HistoryScreen() { ... }        // 查账页
@Composable
fun RegisterBottomSheet() { ... }  // 录入弹窗
```

**规则**：
- 必须以 `Screen` 或 `BottomSheet` 结尾
- 文件名 = 函数名（去掉 `fun`）

### 组件级（可复用片段）

```kotlin
@Composable
fun WageCard(worker: Worker, wage: Double, onPaidClick: () -> Unit) { ... }
@Composable
fun StatsBar(totalWage: Double, unpaidCount: Int, paidCount: Int) { ... }
@Composable
fun BigBlueButton(text: String, onClick: () -> Unit) { ... }
```

**规则**：
- 用业务含义命名（`WageCard` 而不是 `MyCard`）
- 参数放在最前面，回调放最后

### 私有 helper（屏幕内部用，不导出）

```kotlin
@Composable
private fun HomeScreenContent(state: HomeState, ...) { ... }
```

**规则**：
- 加 `private` 修饰
- 用 `Content` 或 `Layout` 结尾

---

## 3️⃣ 状态变量命名

```kotlin
// ✅ 推荐：业务化命名
var workerName by remember { mutableStateOf("") }
var wageAmount by remember { mutableStateOf("") }
var isPaid by remember { mutableStateOf(false) }

// ❌ 不推荐：无意义命名
var text by remember { mutableStateOf("") }
var x by remember { mutableStateOf(0) }
var flag by remember { mutableStateOf(false) }
```

**规则**：
- 业务化：`workerName` 而不是 `text`
- 布尔用 `is/has/can` 前缀：`isPaid`, `hasError`, `canSubmit`

---

## 4️⃣ 回调函数命名

```kotlin
// ✅ on + 动作
onClick = { ... }
onPaidClick = { ... }
onScanned = { content -> ... }
onDelete = { ... }
onDateChange = { newDate -> ... }

// ❌ 不规范
callback = { ... }
handler = { ... }
doSomething = { ... }
```

**规则**：
- 一律 `on + 动词` 开头
- Component 参数顺序：数据 → 回调

---

## 5️⃣ 文件组织

### 一个屏幕 = 一个文件

```
ui/home/
├── HomeScreen.kt          // 主屏幕 + 内部子组件
```

复杂的话可以拆：
```
ui/home/
├── HomeScreen.kt          // 入口
├── HomeStats.kt           // 统计栏子组件
├── HomeWageList.kt        // 列表子组件
├── RegisterBottomSheet.kt // 录入弹窗
```

---

## 6️⃣ 包结构

```
com.example.wagemanager/
│
├── MainActivity.kt              # 入口
├── HistoryActivity.kt           # 另一个 Activity
│
├── ui/                          # 所有 UI 代码
│   ├── theme/                   # 主题、颜色、字体
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   │
│   ├── home/                    # 首页相关
│   │   ├── HomeScreen.kt
│   │   ├── HomeStats.kt
│   │   ├── HomeWageList.kt
│   │   └── RegisterBottomSheet.kt
│   │
│   ├── history/                 # 查账页相关
│   │   ├── HistoryScreen.kt
│   │   └── HistoryFilters.kt
│   │
│   └── components/              # 跨页面共享组件
│       ├── BigButton.kt
│       └── WageCard.kt
│
├── data/                        # 数据层（Room）
│   ├── AppDatabase.kt
│   ├── Worker.kt                # @Entity
│   ├── WorkerDao.kt
│   ├── WageRecord.kt            # @Entity
│   └── WageRecordDao.kt
│
├── scanner/                     # 扫码相关
│   └── ScannerActivity.kt
│
└── util/                        # 工具类
    ├── QRCodeStorage.kt
    └── CsvExporter.kt
```

**规则**：
- 按**功能模块**分包，不是按"类型"
- 屏幕在 `ui/xxx/` 下
- 跨屏幕共享的放 `ui/components/`
- 数据库相关放 `data/`
- 工具类放 `util/`

---

## 7️⃣ Compose 函数位置约定

```
@ Composable 函数必须放在 .kt 文件里
@ 一个 .kt 文件可以放多个 Composable（同一个屏幕相关的）
@ 跨页面复用的组件放 ui/components/ 下
@ 屏幕专属的组件放 ui/xxx/ 下
```

**反例**：所有 Composable 都堆在 MainActivity.kt（乱）

**正例**：
```kotlin
// HomeScreen.kt
@Composable
fun HomeScreen() {
    // 这里只写组装逻辑
    Column {
        HomeStats(...)
        HomeWageList(...)
    }
}

@Composable
private fun HomeStats(...) { ... }  // 同文件，子组件

@Composable
private fun HomeWageList(...) { ... }  // 同文件，子组件
```

---

## 8️⃣ Modifier 规范

```kotlin
// ✅ 标准写法：链式调用，每个一行
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
)

// ❌ 不要挤在一行
Column(modifier = Modifier.fillMaxSize().padding(24.dp), ...)
```

---

## 9️⃣ 注释规范

```kotlin
// ✅ 文件顶部：一句话说明这个文件干啥
// HomeScreen.kt - 首页 UI：扫码按钮 + 统计栏 + 双标签列表

// ✅ 复杂函数：说明意图
/**
 * 计算今日未付工资总额
 * 不包括已支付的记录
 */
fun calculateUnpaidTotal(): Double { ... }

// ❌ 废话注释
// 增加一个变量
var count = 0
```

---

## 🔟 业务逻辑 vs UI 分离原则

```
业务逻辑（不依赖 Android）→ 放普通 Kotlin 类或函数
UI（Compose 函数）        → 只负责显示和接收输入
```

**示例**：
```kotlin
// util/WageCalculator.kt - 纯业务逻辑，可单独测试
object WageCalculator {
    fun total(wages: List<Double>): Double = wages.sum()
    fun unpaidTotal(records: List<WageRecord>): Double = ...
}

// ui/home/HomeScreen.kt - 只调用业务逻辑，自己不计算
@Composable
fun HomeScreen(records: List<WageRecord>) {
    val total = remember(records) { WageCalculator.total(...) }  // 调用业务层
    Text("合计：$total 元")
}
```

**好处**：
- 业务逻辑可以纯 `javac` 测试（不用 Gradle）
- UI 文件只关心"显示什么"，不关心"怎么算"

---

## 📋 规范速查表

```
类名              → PascalCase
函数/变量         → camelCase
常量              → UPPER_SNAKE_CASE
@Composable 屏幕  → XxxScreen / XxxBottomSheet
@Composable 组件  → 业务名（如 WageCard）
回调参数          → on + 动词
布尔变量          → is/has/can + 名词
包结构            → 按功能模块分（ui/home/、data/、util/）
```

---

## ✅ 自我检查清单（写完一个文件后过一遍）

- [ ] 命名符合 camelCase / PascalCase
- [ ] Composable 函数以 `Screen` / `BottomSheet` / 业务名结尾
- [ ] 回调用 `onXxx` 命名
- [ ] 没有把 UI 和业务逻辑混在一起
- [ ] 复杂 Composable 拆成多个 private 子组件
- [ ] 文件顶部有简短注释说明用途
- [ ] 没有废话注释（`// 增加一个变量` 这种）

---

**文档版本**：v1.0  
**最后更新**：2026-08-01