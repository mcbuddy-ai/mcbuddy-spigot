# MCBuddy

💬 **MCBuddy** puts an AI buddy into your server's chat. A player types `/ask`, or just writes in chat mode, and gets an answer grounded in their real game: their inventory, what they're looking at, the chests around them, this server's recipes and rules. The assistant talks like a friend who knows Minecraft inside out, and each player reads the plugin in their own language.

[Русская версия](https://github.com/mcbuddy-ai/mcbuddy-spigot/blob/master/README.RU.md)

## What you get

- 💬 **`/ask` about anything in the game.** “What should I do next?”, “which sword is better?”, “where did I put my redstone?”, “how many hits for this creeper?”. The assistant looks things up in the game while it answers instead of guessing.
- 🗨️ **Chat mode.** When friends talk on Discord and the game chat is free, `/buddy chat` sends everything a player writes to the assistant. `!` in front runs commands, `?` asks in front of everyone, `~` writes to the ordinary chat. After a second `/ask`, a quiet tip offers it, with a button to switch it on and one to hide the tip.
- 📣 **Questions everyone sees.** `/askall` shows the question and the answer to every player online. Public questions share one thread per server, so a friend can follow up on someone else's question.
- 📖 **It explains itself.** Ask “what can you do?” or “how do I turn off chat mode?”: the assistant reads the commands and settings this player really has, and switches chat mode when asked in words.
- 🧩 **It knows the server's plugins.** Questions about graves, claims, or AFK get answers for the plugin and version that really run.
- 👀 **It sees what the player sees.** One look covers the block under the crosshair with its drops, a chest's contents, an enchanting table's bookshelves, a mob and the hits it takes, a villager's trades.
- 🎒 **It knows the player's things.** Inventory, armor, the ender chest, what's inside bundles and shulker boxes, and any item in full: enchantments, potion durations, trims, a weapon's damage and speed as the tooltip shows them.
- 🔍 **It finds things.** Where an item is and how many there are: in the inventory, the ender chest, and the chests nearby, inside shulker boxes too.
- 🧭 **It shows the way.** A compass and a particle trail to a death point, a village, a cherry grove, or a place from the player's notebook.
- 📒 **A notebook, goals, and reminders.** “Remember there's an arrow trader here”, “set a goal to collect 16 diamonds” (a boss bar at the top), “remind me in 5 minutes”.
- 🪟 **Buttons and windows.** Answers end with buttons: a follow-up question, the way to a spot, a recipe, enchantments, the villagers around. The recipe, enchanting, and villager windows show pictures only, so nothing can be taken from them.
- 🌍 **Every player reads their own language.** English, Russian, Spanish, Portuguese, and German come bundled; the plugin picks one by the player's country, then by their game's language. The assistant answers in the language of the question.
- ✻ **A live status like Claude Code:** a spinner and a changing verb in the action bar, and a chat line for each tool the assistant uses.
- 🔒 **Private and safe.** Tools know only the player who asked; other players show up only as a count. Tools that give items or change the world are offered to operators only, and the plugin checks again before running one. Every tool can be switched off.

## Try it live

MCBuddy runs on Moonlands, my hard survival server with friends. Come in and ask it something.

[![Play on Moonlands: pure vanilla, hard mobs, AI, claims. minecraft.mairwunnx.com](https://raw.githubusercontent.com/mcbuddy-ai/mcbuddy-spigot/master/media/og-adv-image.png)](https://minecraft.mairwunnx.com)

Join at `minecraft.mairwunnx.com`. The site has the live map, player stats, and the hall of fame.

## Requirements

- Spigot or Paper 26.3, or a fork of them, like Purpur
- Java 25
- An MCBuddy backend: `https://mcbuddy.ru` by default, or your own [MCBuddy Server](https://github.com/mcbuddy-ai/mcbuddy-server)

## Install

1. Download `mcbuddy-spigot-X.Y.Z.jar` from [Releases](https://github.com/mcbuddy-ai/mcbuddy-spigot/releases).
2. Put it in your server's `plugins/` folder and restart the server.
3. Edit `plugins/mcbuddy-spigot/config.yml` and the texts in `plugins/mcbuddy-spigot/lang/` if you like.
4. Run `/buddy reload`, then try `/ask how do I craft a beacon?`.

## How a player gets their language

The plugin tries the sources in `locales.sources`, in order:

1. **`ip`:** the country of the player's IP address. `locales.countries` maps languages to country codes: Russia, Belarus, and Kazakhstan get Russian; Spain and Latin America get Spanish; Brazil and Portugal get Portuguese; Germany, Austria, and Switzerland get German.
2. **`client`:** the language set in the player's game, when a file in `lang/` matches it.

When nothing matches, the player gets `locales.default`, English out of the box. The console reads the default too.

The country lookup needs no account and sends no address anywhere. On the first start, the plugin downloads DB-IP's free country database (4 MB) into `plugins/mcbuddy-spigot/geoip/` and refreshes it every 30 days. Players who connect through a proxy or a relay show up with its address, so they get the relay's country.

Everything a player reads lives in `lang/<language>.yml`: status verbs, tool lines, buttons, windows, errors. A key missing from a file comes from the default language, then from English; an empty string turns a message off. To add a language, copy `lang/en.yml` to, say, `lang/it.yml`, translate it, add `it: [ IT, SM, VA ]` to `locales.countries`, and run `/buddy reload`.

## Commands and permissions

| Command | What it does |
|---|---|
| `/ask <question>` | Asks the assistant; only you see the answer |
| `/askall <question>` | Asks in front of everyone: every player online sees the question and the answer |
| `/askx <what to do>` | Turns a request into commands and runs them as you, minus `blocked_commands` |
| `/buddy chat [on\|off]` | Chat mode: what you write in chat goes to the assistant |
| `/buddy hints [on\|off]` | The tip about chat mode under answers |
| `/buddy cancel` | Stops the question it's working on |
| `/buddy new` | Starts the conversation over; what it remembers about you stays |
| `/buddy memory`, `/buddy forget <number\|all>` | Shows and erases what it remembers about you |
| `/buddy recipe <item>`, `/buddy enchants`, `/buddy villagers` | Opens the recipe, enchanting, or villager window |
| `/buddy reload` | Reloads the settings and the languages |

In chat mode, a line goes by its first character, with or without a space after it:

| You write | It goes |
|---|---|
| `how do I find netherite?` | To the assistant, privately, like `/ask` |
| `! give me a stack of torches` | To the assistant as commands, like `/askx` |
| `? where's the nearest village?` | To everyone, like `/askall` |
| `~ brb` | To the ordinary chat |

Chat mode stays on between visits; the plugin reminds you when you join. `players.json` keeps each player's settings.

| Permission | Who has it | What it allows |
|---|---|---|
| `mcbuddy.ask` | everyone | `/ask` and `/buddy`'s memory, windows, and cancel |
| `mcbuddy.askall` | everyone | `/askall` and `?` in chat mode |
| `mcbuddy.chat` | everyone | Chat mode and its tip |
| `mcbuddy.askx` | operators | `/askx` and `!` in chat mode |
| `mcbuddy.askall.nocooldown` | operators | Asking everyone without the cooldown |
| `mcbuddy.reload` | operators | `/buddy reload` |

## Settings

`config.yml` holds settings only; every key has a default, so you can delete the ones you don't change.

| Key | What it sets |
|---|---|
| `server.url` | The MCBuddy backend |
| `locales.*`, `geoip.*` | Where a player's language comes from, and the country database |
| `tools.enabled.<tool>` | Switches a tool off. `scan_nearby_containers` and `find_item` see through walls: turn them off if players have claims |
| `tools.operator.*` | Tools that give items, teleport, heal, or change time and weather; operators only |
| `tools.reminders.max_pending`, `tools.notes.max_per_player`, `tools.goals.max_per_player` | How many reminders (10), notes (200), and goals (5) one player can have |
| `tools.locate_structure_radius`, `tools.locate_biome_radius`, `tools.villager_radius` | Search radii |
| `tools.server.hidden_plugins` | Plugins `get_server` leaves out of its list, such as an anti-cheat |
| `askall.cooldown_seconds` | How long a player waits between two questions everyone sees (30) |
| `time.zone` | The players' default time zone; a player can tell the assistant theirs |
| `ui.*` | The action bar, tool lines, particles, sounds, and the author of the assistant's books |
| `blocked_commands` | Commands `/askx` never runs |

A `config.yml` from 2.3 or 2.4 still loads; a 2.3 config's texts are skipped, since they now live in `lang/`.

## How it works

The plugin opens a stream to the MCBuddy backend and shows the live status. When the assistant needs something from the game, the backend sends a tool call; the plugin runs it on the server thread and posts the result back. The protocol is in [docs/protocol-v2.md](https://github.com/mcbuddy-ai/mcbuddy-server/blob/master/docs/protocol-v2.md) of MCBuddy Server.

## Build

You need JDK 25; Gradle downloads it when it's missing.

```shell
git clone https://github.com/mcbuddy-ai/mcbuddy-spigot
cd mcbuddy-spigot
./gradlew build
```

The jar lands in `build/libs/`, with every dependency inside. Releases come from the “Release” workflow on GitHub, which takes the version as input.

## Stack

- Kotlin and Gradle Kotlin DSL
- Spigot API 26.3, so the jar runs on Spigot and Paper
- kotlinx-coroutines and kotlinx-serialization
- OkHttp for the backend's Server-Sent Events
- MaxMind DB reader for the country database

## AI involvement

AI wrote parts of the documentation and some of the code at first. Since 2.0 the plugin is written by an AI agent, Claude, from my specs: the protocol, the tools, the windows, the languages. AI also writes the commit messages from the changes.

## Related projects

[MCBuddy Server](https://github.com/mcbuddy-ai/mcbuddy-server)—🛠️⚡ the backend for MCBuddy: an agent on Bun and Mastra that answers through OpenRouter, calls the plugin's tools, searches the web, and remembers each player.

## From the same author

[Mo’Joins](https://github.com/MairwunNx/mojoins)—🎉 greets players in their own language: join and quit lines, a welcome for newcomers, sounds, and particles.

[Mo’Afks](https://github.com/MairwunNx/moafks)—🛡️ a safe AFK mode: no damage, no pushing, mobs ignore you, and AFK players don't block the night.

[Mo’Bosses](https://github.com/mairwunnx/mobosses)—🏆 an RPG plugin that turns ordinary mobs into epic bosses, with a deep player progression system.

## License

MIT, see [LICENSE](https://github.com/mcbuddy-ai/mcbuddy-spigot/blob/master/LICENSE).

## Credits

[IP Geolocation by DB-IP](https://db-ip.com), licensed under CC BY 4.0. Profession and item names in the windows come from Minecraft's own translations.

> 🫡 Made by Pavel Erokhin (Павел Ерохин), aka mairwunnx.
