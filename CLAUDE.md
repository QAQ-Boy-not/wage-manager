# CLAUDE.md - 给 AI 看的项目说明

> 这是给 Claude AI 看的"项目说明书"。新会话开始时，AI 读这份文档就能立刻理解项目状态、用户背景、关键决策。

---

## 📋 项目概述

**项目名**：零活工人工资管理 App  
**当前阶段**：✅ Hello CI 跑通 → 📍 **准备开始正式工资管理功能**

**一句话定位**：帮助带班妈妈（保洁零活）通过扫码快速登记当日出工人员及浮动工资，并标记支付状态，杜绝漏付、错付。

**目标用户**：50 岁左右带班母亲，华为鸿蒙系统手机，视力一般，偏好大字体大按钮。

---

## 👤 用户背景（重要！影响沟通方式）

- **Java 后端程序员**，熟练使用 Spring Boot、Maven 等
- **不熟悉 Android 开发**（Kotlin、Compose、Gradle 都是新接触）
- **学习意愿强**，希望"看懂代码"而不只是"用代码"
- **想顺便学点新技术**（Kotlin、Compose、CI/CD）
- **机器**：WSL Ubuntu（无 GUI）、只有 mvn/java/git、刚装了 JDK 17 + Gradle 8.4
- **沟通偏好**：中文、类比 Java 后端概念、避免过度技术化术语

### 用户已掌握的概念（不需要再讲）

```
✅ Android 项目结构（Manifest、Gradle、build.gradle）
✅ @Composable 注解（声明式 UI）
✅ remember + mutableStateOf 状态管理
✅ Compose 智能重组（不是全量重画，只重组用到 state 的部分）
✅ Snapshot 系统（读取时自动注册订阅，写入时查订阅表）
✅ 状态变化批处理（同帧多次变化合并，跳帧只渲染最终态）
✅ ViewModel + StateFlow + collectAsState 数据流
✅ suspend 函数（协程不阻塞，在 IO 线程执行）
✅ Dispatchers.IO / Main 线程切换
✅ ViewModelScope 生命周期绑定
✅ Kotlin 没有 new 关键字
✅ data class + val/var + copy + 命名参数（替代 Java POJO）
✅ 值传递规则跟 Java 一样（对象是引用值传递）
✅ MVVM 分层（业务逻辑可单独 javac 测试）
✅ CI/CD = GitHub Actions（云端自动构建）
```

### 用户可能还需要解释的（如果忘记）

```
- Room 数据库（Entity/Dao/Database）
- LazyColumn 列表渲染
- Modifier 链式配置
- Flow 操作符（map/filter/sample 等）
```

---

## 🛠️ 技术栈

| 类别 | 选型 | 备注 |
|------|------|------|
| **语言** | Kotlin 1.9.10 | 基于 JVM，Java 程序员友好 |
| **UI 框架** | Jetpack Compose | 用代码写 UI，告别 XML |
| **构建工具** | Gradle 8.4 | 跟 Maven 类似但更现代 |
| **Android 插件** | AGP (Android Gradle Plugin) 8.1.4 | **只能配 Gradle 8.0-8.4** |
| **JDK** | 17（本地 + CI）| AGP 8.1.4 要求 JDK 17+ |
| **SDK 版本** | minSdk 24, targetSdk 34 | 覆盖 Android 7.0+ |
| **数据库** | Room（即将加）| ORM + Flow 响应式 |
| **扫码库** | ML Kit（计划）| 学东西多、识别率高 |
| **CI/CD** | GitHub Actions | 云端自动构建 APK |

---

## 📁 项目结构（当前）

```
wage-manager/
├── .github/
│   └── workflows/
│       └── build.yml              # CI 配置（固定 Gradle 8.4）
├── .gitignore
├── CLAUDE.md                       # ← 你正在读的
├── DEVELOPMENT_GUIDELINES.md       # 命名规范、包结构、UI/业务分离
├── build.gradle                    # 项目级
├── settings.gradle                 # 仓库配置（用官方仓库，不用阿里云镜像）
├── gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/wagemanager/
        │   └── MainActivity.kt     # 当前的 Hello CI
        └── res/values/
            ├── strings.xml         # app_name = "零活工人"
            ├── colors.xml
            └── themes.xml          # Theme.WageManager
```

---

## 🚀 开发流程（已确认可行）

### 用户工作环境

```
本机：WSL Ubuntu（无 GUI）
编辑器：VSCode + Remote WSL 扩展 + Kotlin Language 扩展
Git：本地已配 SSH key（github_rsa，密码 123456）
JDK：本地已装 17.0.20-tem（清华源）
Gradle：本地已装 8.4（SDKMAN）
Android SDK：本地不装（云端 CI 有）
```

### 标准开发循环

```bash
# 1. 在 VSCode 里改代码
# 2. 终端：
cd /home/wangjing/code/app/wage-manager
git add .
git commit -m "描述改了什么"
git push

# 3. 浏览器打开 CI（首次 5-8 分钟，后续 1-2 分钟）
https://github.com/QAQ-Boy-not/wage-manager/actions

# 4. 跑成功后下载 app-debug.apk，adb install 到手机
```

### SSH Agent 注意事项

每次新 Bash 工具调用需要重新激活 SSH agent：

```bash
eval "$(ssh-agent -s)" > /dev/null 2>&1
SSH_ASKPASS=~/.ssh/askpass.sh SSH_ASKPASS_REQUIRE=force ssh-add ~/.ssh/id_rsa
```

`~/.ssh/askpass.sh` 内容：
```bash
#!/bin/sh
echo "123456"
```

### 本地 Gradle 用法（语法检查）

```bash
source ~/.bashrc  # 已配好 JAVA 17 + Gradle 8.4 PATH
cd /home/wangjing/code/app/wage-manager
gradle help                              # 不需要 SDK
gradle :app:compileDebugKotlin           # 需要 Android SDK（本地失败，靠 CI）
```

---

## 🎯 数据流架构（核心）

```
SQLite (Room)
   ↓ Query 返回 Flow<List<T>>
Dao
   ↓ 暴露 Flow
Repository（可选）
   ↓ 转换/合并
ViewModel
   ↓ 转 StateFlow
val state by viewModel.state.collectAsState()   ← Compose 订阅
   ↓ 状态变化触发重组
UI (Composable)
```

**关键点**：
- Room 的 `Flow` 是响应式的，数据变 → 自动发射新值
- ViewModel 用 `stateIn()` 把 Flow 转 StateFlow（带初始值）
- Compose 用 `collectAsState()` 把 StateFlow 转 Compose State
- **状态变 → 自动重组用到的 Composable**（智能差分）

---

## ⚠️ 关键决策记录

### 为什么不用模拟器？

WSL 无图形界面 + 模拟器需要 GUI + 用户就是真机用户（带班妈妈）→ 直接用真机调试。

### 为什么用云构建（GitHub Actions）？

本机装 Android SDK 需要 5GB+ 下载，WSL 路径权限坑多 → 改用 GitHub Actions 云端自动构建。

### 为什么固定 Gradle 版本为 8.4？

AGP 8.1.4 只兼容 Gradle 8.0-8.4。CI 默认会下最新版（Gradle 9.x），build.yml 必须显式指定 `gradle-version: '8.4'`。

### 为什么改名 wage-manager（不叫 hello-ci）？

工程目录、内部包名一开始就正式，避免后续改名麻烦。当前内容仍是 Hello CI 功能，跑通 CI 后逐步加正式功能。

### 为什么不用公司 SSH key？

公司 key（id_rsa，公司邮箱 `wangj4@geovis.com.cn`）已备份到 `~/.ssh/backup/`，新建的 `github_rsa`（密码 `123456`）专用于个人 GitHub。`~/.ssh/config` 不需要配置，因为 GitHub key 已重命名为默认的 `id_rsa`。

### 为什么不用阿里云 Maven 镜像？

阿里云的 `gradle-plugin` 镜像对 AGP 返回的是 metadata 而非 jar，会导致 "documentation and the consumer needed a library" 错误。**只用官方仓库**（google() + mavenCentral() + gradlePluginPortal()）即可，国内访问速度可接受。

### 为什么不用 Gradle Wrapper？

仓库里没生成 wrapper 文件。统一用 `gradle` 命令（依赖 SDKMAN 安装的 8.4），`./gradlew` 不工作。

### 为什么 AGP 8.1.4 需要 JDK 17？

Gradle 启动时找到 SDKMAN 里的 Java 8（最低版本），会失败。必须在 .bashrc 顶部 export PATH 指向 JDK 17，或者用 `sdk default java 17.0.20-tem`。

---

## 📌 当前进度

```
✅ 已完成：
- [x] Hello CI 工程（验证 CI 流程）
- [x] GitHub Actions 配置跑通，APK 装到手机
- [x] SSH key 配置（与公司隔离）
- [x] Gradle 8.4 + JDK 17 本地安装
- [x] 工程改名 wage-manager
- [x] 写 CLAUDE.md + DEVELOPMENT_GUIDELINES.md

📍 当前：准备开始正式工资管理功能

🚧 接下来要做（按优先级）：
- [ ] 1. 加 Room 数据库（Worker, WageRecord 两张表 + Entity/Dao/Database）
- [ ] 2. 业务逻辑层（util/WageCalculator.kt 等，可用 javac 测试）
- [ ] 3. 首页 UI（扫码按钮 + 统计栏 + 双标签列表 + 录入 BottomSheet）
- [ ] 4. 扫码 Activity（ML Kit 集成）
- [ ] 5. 查账页 UI（日期切换 + 工人筛选 + 汇总）
- [ ] 6. CSV 导出功能（FileProvider + 系统分享）
- [ ] 7. 长按删除、二次确认等交互细节
```

---

## 💡 重要约定

1. **业务逻辑可以单独测试**：用 `javac` 直接编译纯 Java/Kotlin 代码，不用 Gradle。
   - 例：`cd /tmp && javac WageCalculator.java && java WageCalculator`

2. **UI 跟业务逻辑分离**：
   - `data/` Room 实体和 Dao
   - `util/` 纯业务逻辑（wage 计算、CSV 格式化）
   - `ui/` Compose 屏幕
   - 业务逻辑不依赖 Android，可以在纯 JVM 里测试

3. **CI 缓存机制**：Android SDK（5GB）首次下载后会缓存，后续构建秒级。

4. **AVD 模拟器不可用**：WSL 无界面，真机调试用 `adb install`。

5. **Git 配置全局化**：
   - `git config --global user.name "QAQ-Boy-not"`
   - `git config --global user.email "a373846800@gmail.com"`
   - 与公司 Git 隔离（个人 GitHub 仓库用个人邮箱）

---

## 🎯 下次会话开始新工作时的建议

AI 看到这份文档后，请：
1. **不要重复讲基础概念**（@Composable、remember、suspend 等用户已经懂）
2. **直接进入开发**：从 Room 数据库开始，按"接下来要做"清单推进
3. **每个模块写完 push，让 CI 验证**（不要尝试本地编译 Android 代码）
4. **业务逻辑文件**优先写纯 Kotlin/Java（不依赖 Android），用 javac 测试
5. **遇到网络问题**（GitHub、AGP 下载）参考"为什么不用阿里云 Maven 镜像"决策
6. **遇到 Gradle 问题**先确认用 JDK 17（不是 8），参考 ~/.bashrc 顶部的 PATH 配置

---

## 🔗 相关链接

- 项目仓库：https://github.com/QAQ-Boy-not/wage-manager
- CI 状态：https://github.com/QAQ-Boy-not/wage-manager/actions
- 需求文档：`/home/wangjing/code/app/零活工人工资管理App.txt`
- 开发规范：`/home/wangjing/code/app/wage-manager/DEVELOPMENT_GUIDELINES.md`
- Hello CI Demo（已废弃）：`/home/wangjing/code/app/demo/`

---

**文档版本**：v2.0  
**最后更新**：2026-08-02  
**作者**：QAQ-Boy-not

---

## 📝 版本历史

### v2.0 (2026-08-02)

- 加入"用户背景"和"已掌握概念"清单
- 加入"数据流架构"图
- 补充关键决策（阿里云镜像坑、JDK 17 必须、Gradle Wrapper 不用）
- 更新工具链状态（JDK 17 已装、Android SDK 本地不装）
- 加入"下次会话开始新工作时的建议"

### v1.0 (2026-08-01)

- 初版：Hello CI 工程完成时