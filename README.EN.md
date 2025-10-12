# 💬 McBuddy Spigot

[![AI Capable](https://img.shields.io/badge/AI-Capable-brightgreen?style=flat&logo=openai&logoColor=white)](https://github.com/mcbuddy-ai/mcbuddy-spigot)
[![GitHub Release](https://img.shields.io/github/v/release/mcbuddy-ai/mcbuddy-spigot?style=flat&logo=github&color=blue)](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases)
[![Spigot](https://img.shields.io/badge/Spigot-1.21.10-ED8106?style=flat&logo=minecraft&logoColor=white)](https://www.spigotmc.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.13-02303A?style=flat&logo=gradle&logoColor=white)](https://gradle.org/)

**Language**: [🇷🇺 Русский](README.md) | 🇺🇸 English | [🇨🇳 中文](README.CN.md)

**McBuddy Spigot** — 💬 Spigot plugin for MCBuddy integration — adds `/ask` command for AI assistant questions directly in Minecraft server chat! 🎮

> **Note**: The plugin requires a running MCBuddy Server to function.

## Features

### Main Commands

- **`/ask <question>`** — ask a question to MCBuddy AI assistant about Minecraft
- **`/askx <action description>`** — execute Minecraft commands via AI assistant
- **`/ask reload`** — reload plugin configuration

### Visual Effects

- **Action Bar animations** — "thinking" status indicators with animated dots above the hotbar
- **Particles** — magical effects around the player's head during request processing
- **Sound effects** — notifications for processing start, success, and errors

### Security

- **Blocked commands** — protection against executing critical server commands via `/askx`
- **Configurable permissions** — flexible permission system for commands

### Integration

- **MCBuddy Server API** — interaction with backend to receive AI answers
- **OpenRouter support** — ability to use custom token
- **Asynchronous processing** — does not block the main server thread

## Usage

### Getting Started

1. Install the plugin on your Spigot/Paper server
2. Configure `config.yml` with MCBuddy Server address
3. Use `/ask` and `/askx` commands in-game

### Available Commands

#### For Players
- `/ask <question>` — ask a question about Minecraft
  - Example: `/ask How to craft a diamond pickaxe?`
  - Example: `/ask Where to find diamonds?`

#### For Administrators
- `/askx <description>` — perform action via AI
  - Example: `/askx give player Steve a diamond sword`
  - Example: `/askx teleport all players to spawn`
- `/ask reload` — reload plugin configuration

### Permissions

- `mcbuddy.ask` — permission to use `/ask` command
- `mcbuddy.askx` — permission to use `/askx` command
- `mcbuddy.reload` — permission to reload configuration

### How It Works

1. Player enters `/ask` or `/askx` command in chat
2. Plugin displays visual effects (particles, Action Bar)
3. Request is sent to MCBuddy Server via HTTP API
4. AI processes the question and returns answer
5. Player receives formatted answer in Minecraft chat

## Compatibility

- **Spigot/Paper**: 1.21+
- **Java**: 21+
- **Kotlin**: 2.2.0
- **MCBuddy Server**: requires running server

## Installation

### Install from Release (recommended)

1. **Download the plugin:**
   - Go to the [Releases](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases) page
   - Download the latest version of the file `mcbuddy-spigot-X.X.X-all.jar`
   
   > **Important**: Use the version with the `-all.jar` suffix, it contains all necessary dependencies!

2. **Install on server:**
   - Copy `mcbuddy-spigot-X.X.X-all.jar` to the `plugins/` folder of your server

3. **Restart the server:**

4. **Configure:** (optional)
   - If needed, edit the configuration file `plugins/mcbuddy-spigot/config.yml`

5. **Apply settings:**
   - Execute `/ask reload` to apply settings

6. **Test:**
   - Execute `/ask How to craft a diamond pickaxe?`

### System Requirements

- **Minecraft server**: Spigot/Paper 1.21+ or higher
- **Java**: 21 or higher
- **MCBuddy Server**: Available API server (default `https://mcbuddy.ru`)

## Build from Source

### Build Requirements:
- Java 21 or higher
- Gradle 8.13 or higher

### Build Process:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mcbuddy-ai/mcbuddy-spigot
   cd mcbuddy-spigot
   ```

2. **Build JAR file:**
   - Execute `./gradlew shadowJar`

3. **Result:**
   - `build/libs/mcbuddy-spigot-X.X.X.jar` — Base version (without dependencies)
   - `build/libs/mcbuddy-spigot-X.X.X-all.jar` — Full version with all dependencies

4. **Installation:**
   - Copy `mcbuddy-spigot-X.X.X-all.jar` to the `plugins/` folder of your server
   - Restart the server
   - Configure `plugins/mcbuddy-spigot/config.yml`

### Configuration

Edit `plugins/mcbuddy-spigot/config.yml`:

```yaml
# MCBuddy Server URL
server-url: "https://mcbuddy.ru"

# HTTP request timeout (in seconds)
request-timeout: 30

# Optional OpenRouter token
openrouter-token: ""

# Visual effects
effects:
  action-bar: true
  particles: true
  sounds: true

# Blocked commands for /askx
blocked-commands:
  - "stop"
  - "restart"
  - "op"
  - "deop"
```

## Tech Stack

- **Kotlin 2.2.0** — main development language
- **Spigot API 1.21.4** — platform for Minecraft plugin development
- **kotlinx-coroutines** — asynchronous programming and non-blocking operations
- **kotlinx-serialization** — JSON serialization for API interaction
- **OkHttp 4.12.0** — HTTP client for MCBuddy Server requests
- **Gradle + Shadow** — build system and fat JAR creation

## AI Participation

AI tools were used for generating part of the documentation and creating character effects. The main architecture, Spigot API integration, and business logic were developed manually. Commits were fully written by an AI agent, with AI participation minimized where possible.

## Links to Related Projects

[McBuddy Server](https://github.com/mcbuddy-ai/mcbuddy-server) — 🛠️⚡ Backend for MCBuddy AI assistant — fast server on Bun with OpenRouter integration and web search, processing requests from Minecraft and Telegram. 🌟

[McBuddy Bot](https://github.com/mcbuddy-ai/mcbuddy-bot) — 🤖 Telegram bot for communicating with MCBuddy — ask about Minecraft and instantly get clear, accurate answers! 📱

## From the Series "By the Same Author"

[Xi Manager](https://github.com/mairwunnx/xi) — 🀄️ AI-powered Telegram bot styled as Xi's personal assistant. A personal assistant to the great leader, ready to answer questions from the common people.

[Dickobrazz](https://github.com/mairwunnx/dickobrazz) — 🌶️ Dickobrazz bot, aka dicobot, capable of measuring your unit size to the nearest centimeter. A modern and technological cockometer with seasons system and gamification.

[Louisepizdon](https://github.com/MairwunNx/louisepizdon) — 🥀 Louisepizdon, an AI Telegram bot that's more honest than your grandmother. Will evaluate you properly, breaking down the pricing of your clothes from a photo!

[Mo'Bosses](https://github.com/mairwunnx/mobosses) — 🏆 **Mo'Bosses** is the best RPG plugin that transforms ordinary mobs into epic bosses with an **advanced player progression system**. Unlike other plugins, here every fight matters, and each level opens new possibilities! ⚔

[Mo'Joins](https://github.com/mairwunnx/mojoins) — 🎉 Custom joins/quits: messages, sounds, particles, fireworks, and protection after joining. All for PaperMC.

[Mo'Afks](https://github.com/mairwunnx/moafks) — 🛡️ Pause in online time — now possible. A plugin for PaperMC that gives players a safe AFK mode: damage immunity, no collisions, ignored by mobs, auto-detect inactivity, and neat visual effects.

---

![image](./media.png)

🇷🇺 **Made in Russia with love.** ❤️

**McBuddy** — is the result of love for Minecraft and modern technologies. The project is created for the Russian-speaking gaming community, with care for code quality and user experience.

> 🫡 Made by Pavel Erokhin (Павел Ерохин), aka mairwunnx.
