# 💬 McBuddy Spigot

[![AI Capable](https://img.shields.io/badge/AI-Capable-brightgreen?style=flat&logo=openai&logoColor=white)](https://github.com/mcbuddy-ai/mcbuddy-spigot)
[![GitHub Release](https://img.shields.io/github/v/release/mcbuddy-ai/mcbuddy-spigot?style=flat&logo=github&color=blue)](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases)
[![Spigot](https://img.shields.io/badge/Spigot-26.3-ED8106?style=flat&logo=minecraft&logoColor=white)](https://www.spigotmc.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-25-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.5-02303A?style=flat&logo=gradle&logoColor=white)](https://gradle.org/)

**语言**: [🇷🇺 Русский](README.md) | [🇺🇸 English](README.EN.md) | 🇨🇳 中文

**McBuddy Spigot** — 💬 MCBuddy 集成的 Spigot 插件 — 添加 `/ask` 命令，直接在 Minecraft 服务器聊天中向 AI 助手提问！🎮

> **注意**：插件需要运行中的 MCBuddy Server 才能工作。

## 功能特性

### 主要命令

- **`/ask <问题>`** — 向 MCBuddy AI 助手询问有关 Minecraft 的问题
- **`/askx <动作描述>`** — 通过 AI 助手执行 Minecraft 命令
- **`/ask cancel`** — 停止助手仍在处理的问题
- **`/ask new`** — 重新开始对话；助手记住的关于你的内容会保留
- **`/ask memory`** — 查看助手记住了关于你的什么
- **`/ask forget <编号|all>`** — 忘记一条或全部记录
- **`/ask reload`** — 重新加载插件配置（权限 `mcbuddy.reload`）

### 助手了解游戏

助手会自己决定需要知道什么，并在回答过程中向插件询问。问它“我接下来该做什么？”，它会查看你的物品栏、进度和统计数据，而不是去猜。

- **玩家状态** — 生命值、饥饿值、经验、坐标、生物群系、效果、手持物品
- **物品栏** — 盔甲、副手，按需包括末影箱
- **周围环境** — 附近的方块和生物：矿石、箱子、刷怪笼、怪物
- **准星** — 你正在看的方块或生物
- **世界** — 时间、天数、月相、天气、难度以及影响建议的游戏规则
- **统计与进度** — 游戏时长、死亡次数、已获得和接下来的进度
- **配方** — 你的服务器上的确切配方
- **结构** — 最近的村庄、要塞等
- **重要地点** — 你的重生点、世界出生点和上次死亡的位置
- **交易** — 你正在看的村民或流浪商人的交易
- **容器** — 面前箱子里有什么，以及附近哪个箱子放着你要的物品
- **方块** — 硬度、该用什么工具、用手中物品会掉落什么、作物生长阶段
- **装备** — 工具和盔甲还剩多少耐久，哪些该修了
- **经验与食物** — 距离目标等级还差多少经验，现在该吃物品栏里的什么
- **地图、附魔台、生物** — 手中地图的比例、有几个书架生效、打死一个生物要几下
- **黑暗处** — 周围哪些地方会刷出敌对生物
- **药水与农场** — 酿造材料、作物生长情况和附近的动物数量
- **网络搜索** — 需要最新信息时，助手会自己搜索
- **精确计算** — 组数、下界坐标、经验和时间都会精确计算，而不是估算

### 助手还能动手帮忙

对任何玩家，助手都可以指路（让你的指南针指向目标，并画出只有你能看到的粒子轨迹）、整理物品栏（快捷栏不动），以及在 1–120 分钟后提醒你某件事。它也能按要求列出和取消提醒，每位玩家最多同时有 10 条。让它“发个信号”，你头顶会响起声音并冒出粒子，方便朋友找到你。这些都不会发放物品，也不会改变世界。

### 笔记本

说一句“记住，这里有个卖箭的村民”，助手会连同你的坐标一起记下来。之后用自己的话问，比如“那个卖箭的在哪？”，它会找到这条笔记并给你指路。笔记保存在服务器的 `plugins/mcbuddy-spigot/notes` 中，每位玩家最多 200 条。

对管理员（OP），它还能发放物品、传送、切换游戏模式、治疗、施加效果、修改时间和天气。只有管理员提问时，插件才会把这些工具提供给助手，并在执行前再次检查：普通玩家无法说服助手给自己一把剑。可以在 `tools.operator` 中关闭它们。

### 记忆

助手会记住最近 8 次问答，更早的内容会压缩成简短摘要。对话在最后一个问题后保留一天。关于你的重要信息，比如基地在哪里、你的目标是什么，在你提到或要求它记住时，它会长期记下来。用 `/ask memory` 和 `/ask forget` 查看和删除这些记录。

### 隐私

工具只知道提问的玩家及其周围的世界。其他玩家只以附近人数的形式出现：没有昵称、坐标或物品栏。每个工具都可以在 `config.yml` 中关闭。

### 实时状态（类似 Claude Code）

- **Action Bar** — 旋转图标（`·✢✳✶✻✽`）、不断变化的动词、已用时间和双向 token 数：`✻ Сверяюсь с вики… (12с · ↑ 2.4k · ↓ 340 токенов)`。`↑` 是模型读取的量，`↓` 是它写出的量。写回答时动词会变成“Пишу ответ…”
- **聊天中的工具行** — 助手在做什么、发现了什么：
  ```
  ⏺ Заглядываю в инвентарь
    ⎿ 12 стаков
  ⏺ Ищу в интернете: «фантомы minecraft»
    ⎿ 5 источников
  ```
- **粒子和声音** — 与以前一样，均可配置

### 安全性

- **阻止的命令** — 防止通过 `/askx` 执行关键服务器命令
- **可配置的权限** — 灵活的命令权限系统

### 集成

- **MCBuddy Server API** — 与后端交互以接收 AI 答案
- **OpenRouter 支持** — 可以使用自定义令牌
- **异步处理** — 不阻塞主服务器线程

## 使用方法

### 开始使用

1. 在您的 Spigot/Paper 服务器上安装插件
2. 使用 MCBuddy Server 地址配置 `config.yml`
3. 在游戏中使用 `/ask` 和 `/askx` 命令

### 可用命令

#### 适用于玩家
- `/ask <问题>` — 询问有关 Minecraft 的问题
  - 示例：`/ask 如何制作钻石镐？`
  - 示例：`/ask 在哪里找到钻石？`

#### 适用于管理员
- `/askx <描述>` — 通过 AI 执行操作
  - 示例：`/askx 给玩家 Steve 一把钻石剑`
  - 示例：`/askx 将所有玩家传送到出生点`
- `/ask reload` — 重新加载插件配置

### 权限

- `mcbuddy.ask` — 使用 `/ask` 命令的权限
- `mcbuddy.askx` — 使用 `/askx` 命令的权限
- `mcbuddy.reload` — 重新加载配置的权限（默认仅限管理员）

### 工作原理

1. 玩家输入 `/ask` 或 `/askx`
2. 插件向 MCBuddy Server 打开一个流（`POST /api/v2/chat`）并显示实时状态
3. 当助手需要游戏中的信息时，服务器发送工具调用；插件在服务器主线程上执行并回传结果
4. 答案出现在聊天中；对于 `/askx`，命令照常执行，被阻止的命令除外
5. 如果后端较旧、没有 v2，插件会回退到 `/api/ask`，只给出回答

## 兼容性

- **Spigot/Paper**: 26.3
- **Java**: 25+
- **Kotlin**: 2.4.21
- **MCBuddy Server**: 需要运行中的服务器

## 安装

### 从发布版本安装（推荐）

1. **下载插件：**
   - 访问 [Releases](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases) 页面
   - 下载最新版本的文件 `mcbuddy-spigot-X.X.X-all.jar`
   
   > **重要**：使用带有 `-all.jar` 后缀的版本，它包含所有必需的依赖项！

2. **在服务器上安装：**
   - 将 `mcbuddy-spigot-X.X.X-all.jar` 复制到您服务器的 `plugins/` 文件夹

3. **重启服务器：**

4. **配置：**（可选）
   - 如需要，编辑配置文件 `plugins/mcbuddy-spigot/config.yml`

5. **应用设置：**
   - 执行 `/ask reload` 以应用设置

6. **测试：**
   - 执行 `/ask 如何合成钻石镐？`

### 系统要求

- **Minecraft 服务器**: Spigot 或 Paper 26.3
- **Java**: 25 或更高
- **MCBuddy Server**: 可用的 API 服务器（默认 `https://mcbuddy.ru`）

## 从源代码构建

### 构建要求：
- Java 25 或更高
- 无需安装 Gradle：仓库自带 `./gradlew` 9.5

### 构建过程：

1. **克隆仓库：**
   ```bash
   git clone https://github.com/mcbuddy-ai/mcbuddy-spigot
   cd mcbuddy-spigot
   ```

2. **构建 JAR 文件：**
   - 执行 `./gradlew build`：构建插件并运行测试

3. **结果：**
   - `build/libs/mcbuddy-spigot-X.X.X.jar` — 基础版本（不含依赖项）
   - `build/libs/mcbuddy-spigot-X.X.X-all.jar` — 完整版本，包含所有依赖项

4. **安装：**
   - 将 `mcbuddy-spigot-X.X.X-all.jar` 复制到您服务器的 `plugins/` 文件夹
   - 重启服务器
   - 配置 `plugins/mcbuddy-spigot/config.yml`

### 配置

编辑 `plugins/mcbuddy-spigot/config.yml`。新增的配置项：

| 键 | 作用 |
|---|---|
| `ui.action_bar.format` | 状态行：`{spinner}`、`{verb}`、`{elapsed}`、`{input}`、`{output}` |
| `ui.action_bar.spinner`、`frame_millis` | 旋转图标的帧和速度 |
| `ui.action_bar.verbs`、`askx_verbs`、`verb_seconds` | 动词及其切换频率 |
| `ui.tool_lines.*` | 聊天中的 `⏺`/`⎿` 行和工具名称 |
| `tools.enabled.<工具>` | 开启或关闭某个工具 |
| `tools.operator.enabled`、`tools.operator.<工具>` | 管理员工具：整体或逐个开关 |
| `messages.reminder`、`messages.memory_*`、`messages.forget*` | 提醒和记忆的文本 |
| `tools.reminders.max_pending` | 每位玩家最多等待的提醒数（10） |
| `tools.notes.max_per_player` | 每位玩家笔记本最多的笔记数（200） |
| `tools.locate_structure_radius` | 结构搜索半径（区块） |
| `http.tool_timeout` | 工具最长运行秒数 |

旧的 `config.yml` 仍可使用：缺少的键会使用默认值。

## 技术栈

- **Kotlin 2.4.21** — 主要开发语言
- **Spigot API 26.3** — 同时支持 Spigot 和 Paper
- **kotlinx-coroutines** — 异步编程和非阻塞操作
- **kotlinx-serialization** — 用于 API 交互的 JSON 序列化
- **OkHttp 5.5** — HTTP 客户端以及来自 MCBuddy Server 的 Server-Sent Events；依赖被重定位到 `ru.mcbuddy.spigot.libs`，不会与其他插件冲突
- **Gradle + Shadow** — 构建系统和 fat JAR 创建

## AI 参与

AI 工具被用于生成部分文档和创建角色效果。主要架构、Spigot API 集成和业务逻辑是手动开发的。提交完全由 AI 代理编写，在可能的情况下尽量减少 AI 参与。

## 相关项目链接

[McBuddy Server](https://github.com/mcbuddy-ai/mcbuddy-server) — 🛠️⚡ MCBuddy AI 助手的后端 — 基于 Bun 的快速服务器，集成 OpenRouter 和网络搜索，处理来自 Minecraft 和 Telegram 的请求。🌟

[McBuddy Bot](https://github.com/mcbuddy-ai/mcbuddy-bot) — 🤖 用于与 MCBuddy 通信的 Telegram 机器人 — 询问有关 Minecraft 的问题并立即获得清晰、准确的答案！📱

## 来自"同一作者"系列

[Xi Manager](https://github.com/mairwunnx/xi) — 🀄️ 基于 AI 的 Telegram 机器人，风格化为 Xi 的私人助理。伟大领袖的私人助理，随时准备回答人民群众的问题。

[Dickobrazz](https://github.com/mairwunnx/dickobrazz) — 🌶️ Dickobrazz 机器人，又名 dicobot，能够精确到厘米测量你的单位大小。现代化的技术型测量器，带有赛季系统和游戏化。

[Louisepizdon](https://github.com/MairwunNx/louisepizdon) — 🥀 Louisepizdon，一个比你奶奶还诚实的 AI Telegram 机器人。会正确评估你，根据照片分析你衣服的定价！

[Mo'Bosses](https://github.com/mairwunnx/mobosses) — 🏆 **Mo'Bosses** 是最好的 RPG 插件，将普通的怪物转变为史诗级的 Boss，拥有**高级玩家进阶系统**。与其他插件不同，这里每场战斗都很重要，每个等级都会开启新的可能性！⚔

[Mo'Joins](https://github.com/mairwunnx/mojoins) — 🎉 自定义加入/退出：消息、声音、粒子、烟花和加入后的保护。全部用于 PaperMC。

[Mo'Afks](https://github.com/mairwunnx/moafks) — 🛡️ 在线时间暂停 — 现在可能了。PaperMC 插件，为玩家提供安全的 AFK 模式：伤害免疫、无碰撞、被怪物忽略、自动检测不活动和整洁的视觉效果。

---

![image](./media.png)

🇷🇺 **在俄罗斯用爱制作。** ❤️

**McBuddy** — 是对 Minecraft 游戏和现代技术的热爱的结晶。该项目是为俄语游戏社区创建的，注重代码质量和用户体验。

> 🫡 Made by Pavel Erokhin (Павел Ерохин), aka mairwunnx.
