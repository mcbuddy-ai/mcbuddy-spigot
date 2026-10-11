# 💬 McBuddy Spigot

[![AI Capable](https://img.shields.io/badge/AI-Capable-brightgreen?style=flat&logo=openai&logoColor=white)](https://github.com/mcbuddy-ai/mcbuddy-spigot)
[![GitHub Release](https://img.shields.io/github/v/release/mcbuddy-ai/mcbuddy-spigot?style=flat&logo=github&color=blue)](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases)
[![Spigot](https://img.shields.io/badge/Spigot-26.3-ED8106?style=flat&logo=minecraft&logoColor=white)](https://www.spigotmc.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-25-007396?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.5-02303A?style=flat&logo=gradle&logoColor=white)](https://gradle.org/)

**Language**: [🇷🇺 Русский](README.md) | 🇺🇸 English | [🇨🇳 中文](README.CN.md)

**McBuddy Spigot** — 💬 Spigot plugin for MCBuddy integration — adds `/ask` command for AI assistant questions directly in Minecraft server chat! 🎮

> **Note**: The plugin requires a running MCBuddy Server to function.

## Features

### Main Commands

- **`/ask <question>`** — ask a question to MCBuddy AI assistant about Minecraft
- **`/askx <action description>`** — execute Minecraft commands via AI assistant
- **`/ask cancel`** — stop a question the assistant is still working on
- **`/ask new`** — start the conversation over; what the assistant remembers about you stays
- **`/ask memory`** — show what the assistant remembers about you
- **`/ask forget <number|all>`** — forget one entry or all of them
- **`/ask reload`** — reload plugin configuration (`mcbuddy.reload`)

### The Assistant Knows the Game

The assistant decides what it needs to know and asks the plugin while it answers. Ask “what should I do next?” and it looks at your inventory, advancements, and statistics instead of guessing.

- **Player state** — health, food, XP, coordinates, biome, effects, the item in hand
- **Inventory** — armor, offhand, and the ender chest on request
- **Surroundings** — blocks and creatures around you: ores, chests, spawners, mobs
- **Crosshair** — the block or creature you’re looking at
- **World** — time, day, moon phase, weather, difficulty, and the rules that change advice
- **Statistics and advancements** — playtime, deaths, what you have and what comes next
- **Recipes** — the exact recipes of your server
- **Structures** — the nearest village, stronghold, and so on
- **Key places** — your respawn point, the world spawn, and where you last died
- **Trades** — the deals of the villager or wandering trader you’re looking at
- **Web search** — the assistant looks up fresh facts by itself when it needs them
- **Exact math** — stacks, Nether coordinates, XP, and time get calculated, not estimated

### The Assistant Lends a Hand

For any player, the assistant can show the way (it points your compass and draws a particle trail only you see), tidy your inventory (the hotbar stays put), and remind you of something in 1–120 minutes. None of this hands out items or changes the world.

For operators, it also gives items, teleports, switches the game mode, heals, applies effects, and changes the time and weather. The plugin offers these tools to the assistant only when an operator asks, and checks again before running one: a regular player can’t talk the assistant into a sword. Switch them off under `tools.operator`.

### Memory

The assistant remembers the last 8 questions and answers and folds older ones into a short summary. A conversation lasts a day after the last question. What matters about you, like where your base is or what you’re working toward, it writes down for good when you mention it or ask it to remember. See and erase the entries with `/ask memory` and `/ask forget`.

### Privacy

Tools only know about the player who asked and the world around them. Other players show up only as a count nearby: no names, coordinates, or inventories. Every tool can be switched off in `config.yml`.

### Live Status, Like Claude Code

- **Action bar** — a spinner (`·✢✳✶✻✽`), a changing verb, elapsed time, and tokens both ways: `✻ Сверяюсь с вики… (12с · ↑ 2.4k · ↓ 340 токенов)`. `↑` is what the model has read, `↓` what it has written. While it writes the answer, the verb turns into “Пишу ответ…”
- **Tool lines in chat** — what the assistant does and what it found:
  ```
  ⏺ Заглядываю в инвентарь
    ⎿ 12 стаков
  ⏺ Ищу в интернете: «фантомы minecraft»
    ⎿ 5 источников
  ```
- **Particles and sounds** — as before, all configurable

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
- `mcbuddy.reload` — permission to reload configuration (operators by default)

### How It Works

1. The player enters `/ask` or `/askx`
2. The plugin opens a stream to MCBuddy Server (`POST /api/v2/chat`) and shows the live status
3. When the assistant needs something from the game, the server sends a tool call; the plugin runs it on the server thread and posts the result back
4. The answer arrives in chat; for `/askx`, the commands run as before, minus the blocked ones
5. With an older backend that has no v2, the plugin falls back to `/api/ask` and just answers

## Compatibility

- **Spigot/Paper**: 26.3
- **Java**: 25+
- **Kotlin**: 2.4.21
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

- **Minecraft server**: Spigot or Paper 26.3
- **Java**: 25 or higher
- **MCBuddy Server**: Available API server (default `https://mcbuddy.ru`)

## Build from Source

### Build Requirements:
- Java 25 or higher
- No Gradle install needed: the repository ships `./gradlew` 9.5

### Build Process:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mcbuddy-ai/mcbuddy-spigot
   cd mcbuddy-spigot
   ```

2. **Build JAR file:**
   - Execute `./gradlew build`: it builds the plugin and runs the tests

3. **Result:**
   - `build/libs/mcbuddy-spigot-X.X.X.jar` — Base version (without dependencies)
   - `build/libs/mcbuddy-spigot-X.X.X-all.jar` — Full version with all dependencies

4. **Installation:**
   - Copy `mcbuddy-spigot-X.X.X-all.jar` to the `plugins/` folder of your server
   - Restart the server
   - Configure `plugins/mcbuddy-spigot/config.yml`

### Configuration

Edit `plugins/mcbuddy-spigot/config.yml`. The new keys:

| Key | What it sets |
|---|---|
| `ui.action_bar.format` | The status line: `{spinner}`, `{verb}`, `{elapsed}`, `{input}`, `{output}` |
| `ui.action_bar.spinner`, `frame_millis` | Spinner frames and speed |
| `ui.action_bar.verbs`, `askx_verbs`, `verb_seconds` | Verbs and how often they change |
| `ui.tool_lines.*` | The `⏺`/`⎿` chat lines and tool labels |
| `tools.enabled.<tool>` | Switch a tool on or off |
| `tools.operator.enabled`, `tools.operator.<tool>` | Operator tools: all at once or one by one |
| `messages.reminder`, `messages.memory_*`, `messages.forget*` | Texts for reminders and memory |
| `tools.locate_structure_radius` | Structure search radius in chunks |
| `http.tool_timeout` | Seconds a tool may run |

An old `config.yml` keeps working: missing keys take their defaults.

## Tech Stack

- **Kotlin 2.4.21** — main development language
- **Spigot API 26.3** — runs on both Spigot and Paper
- **kotlinx-coroutines** — asynchronous programming and non-blocking operations
- **kotlinx-serialization** — JSON serialization for API interaction
- **OkHttp 5.5** — HTTP client and Server-Sent Events from MCBuddy Server; shaded under `ru.mcbuddy.spigot.libs` so it can’t clash with other plugins
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
