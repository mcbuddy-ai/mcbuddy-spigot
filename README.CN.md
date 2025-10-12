# 💬 McBuddy Spigot

[![AI Capable](https://img.shields.io/badge/AI-Capable-brightgreen?style=flat&logo=openai&logoColor=white)](https://github.com/mcbuddy-ai/mcbuddy-spigot)
[![GitHub Release](https://img.shields.io/github/v/release/mcbuddy-ai/mcbuddy-spigot?style=flat&logo=github&color=blue)](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases)
[![Spigot](https://img.shields.io/badge/Spigot-1.21.10-ED8106?style=flat&logo=minecraft&logoColor=white)](https://www.spigotmc.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?style=flat&logo=gradle&logoColor=white)](https://gradle.org/)

**语言**: [🇷🇺 Русский](README.md) | [🇺🇸 English](README.EN.md) | 🇨🇳 中文

**McBuddy Spigot** — 💬 MCBuddy 集成的 Spigot 插件 — 添加 `/ask` 命令，直接在 Minecraft 服务器聊天中向 AI 助手提问！🎮

> **注意**：插件需要运行中的 MCBuddy Server 才能工作。

## 功能特性

### 主要命令

- **`/ask <问题>`** — 向 MCBuddy AI 助手询问有关 Minecraft 的问题
- **`/askx <动作描述>`** — 通过 AI 助手执行 Minecraft 命令
- **`/ask reload`** — 重新加载插件配置

### 视觉效果

- **Action Bar 动画** — 在快捷栏上方显示带有动画点的"思考中"状态指示器
- **粒子效果** — 请求处理期间玩家头部周围的魔法效果
- **声音效果** — 处理开始、成功和错误的通知

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
- `mcbuddy.reload` — 重新加载配置的权限

### 工作原理

1. 玩家在聊天中输入 `/ask` 或 `/askx` 命令
2. 插件显示视觉效果（粒子、Action Bar）
3. 请求通过 HTTP API 发送到 MCBuddy Server
4. AI 处理问题并返回答案
5. 玩家在 Minecraft 聊天中收到格式化的答案

## 兼容性

- **Spigot/Paper**: 1.21+
- **Java**: 21+
- **Kotlin**: 2.2.0
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

- **Minecraft 服务器**: Spigot/Paper 1.21+ 或更高
- **Java**: 21 或更高
- **MCBuddy Server**: 可用的 API 服务器（默认 `https://mcbuddy.ru`）

## 从源代码构建

### 构建要求：
- Java 21 或更高
- Gradle 8.13 或更高

### 构建过程：

1. **克隆仓库：**
   ```bash
   git clone https://github.com/mcbuddy-ai/mcbuddy-spigot
   cd mcbuddy-spigot
   ```

2. **构建 JAR 文件：**
   - 执行 `./gradlew shadowJar`

3. **结果：**
   - `build/libs/mcbuddy-spigot-X.X.X.jar` — 基础版本（不含依赖项）
   - `build/libs/mcbuddy-spigot-X.X.X-all.jar` — 完整版本，包含所有依赖项

4. **安装：**
   - 将 `mcbuddy-spigot-X.X.X-all.jar` 复制到您服务器的 `plugins/` 文件夹
   - 重启服务器
   - 配置 `plugins/mcbuddy-spigot/config.yml`

### 配置

编辑 `plugins/mcbuddy-spigot/config.yml`：

```yaml
# MCBuddy Server URL
server-url: "https://mcbuddy.ru"

# HTTP 请求超时（以秒为单位）
request-timeout: 30

# 可选的 OpenRouter 令牌
openrouter-token: ""

# 视觉效果
effects:
  action-bar: true
  particles: true
  sounds: true

# /askx 的阻止命令
blocked-commands:
  - "stop"
  - "restart"
  - "op"
  - "deop"
```

## 技术栈

- **Kotlin 2.2.0** — 主要开发语言
- **Spigot API 1.21.4** — Minecraft 插件开发平台
- **kotlinx-coroutines** — 异步编程和非阻塞操作
- **kotlinx-serialization** — 用于 API 交互的 JSON 序列化
- **OkHttp 4.12.0** — 用于 MCBuddy Server 请求的 HTTP 客户端
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
