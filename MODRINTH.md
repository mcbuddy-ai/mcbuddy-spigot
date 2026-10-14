# MCBuddy

💬 **MCBuddy** puts an AI buddy into your server's chat. A player types `/ask` and gets an answer grounded in their real game: their inventory, what they're looking at, the chests around them, your server's recipes and rules. Each player reads the plugin in their own language.

## What you get

- 💬 **`/ask` about anything in the game.** “What should I do next?”, “which sword is better?”, “where did I put my redstone?”. The assistant looks things up in the game while it answers instead of guessing.
- 👀 **It sees what the player sees:** the block under the crosshair with its drops, a chest's contents, an enchanting table's bookshelves, a mob and the hits it takes, a villager's trades.
- 🎒 **It knows the player's things:** inventory, armor, the ender chest, bundles and shulker boxes, and any item in full, down to a weapon's damage and speed as the tooltip shows them.
- 🔍 **It finds things** in the inventory, the ender chest, and the chests nearby, inside shulker boxes too.
- 🧭 **It shows the way** with the compass and a particle trail: to a death point, a village, a biome, or a place from the player's notebook.
- 📒 **A notebook, goals with a boss bar, and reminders.**
- 🪟 **Buttons under answers** and **recipe, enchanting, and villager windows** that only show pictures, so nothing can be taken from them.
- 🌍 **Every player reads their own language.** English, Russian, Spanish, Portuguese, and German come bundled, picked by the player's country, then by their game's language.
- ✻ **A live status like Claude Code** in the action bar, and a chat line for each tool the assistant uses.
- 🔒 **Private and safe.** Tools know only the player who asked; other players show up only as a count. Tools that give items or change the world go to operators only. Every tool can be switched off.

## Install

1. Put the jar in your server's `plugins/` folder and restart the server.
2. Edit `plugins/mcbuddy-spigot/config.yml` and the texts in `plugins/mcbuddy-spigot/lang/` if you like.
3. Run `/ask reload`, then try `/ask how do I craft a beacon?`.

MCBuddy runs on Spigot, Paper 26.3, and their forks, on Java 25. It talks to the MCBuddy backend, `https://mcbuddy.ru` by default (`server.url`).

## How a player gets their language

1. **By IP.** The plugin looks up the player's country and checks `locales.countries`: Russia gets Russian, Brazil gets Portuguese, Germany gets German, and so on. You can change the lists.
2. **By game language.** When the country isn't listed, the plugin takes the language set in the player's game, if a file in `lang/` matches it.
3. **Default.** Everyone else gets `locales.default`, English out of the box.

The country lookup needs no account and sends no address anywhere. On the first start, the plugin downloads a free country database (4 MB) and refreshes it every month. Players who connect through a proxy show up with the proxy's address, so they get the proxy's country.

**Add a language:** copy `lang/en.yml` to, say, `lang/it.yml`, translate it, add `it: [ IT, SM, VA ]` to `locales.countries`, and run `/ask reload`.

## Commands and permissions

| Command | What it does |
|---|---|
| `/ask <question>` | Asks the assistant |
| `/ask cancel`, `/ask new` | Stops the current question; starts the conversation over |
| `/ask memory`, `/ask forget <number\|all>` | Shows and erases what the assistant remembers about you |
| `/ask recipe <item>`, `/ask enchants`, `/ask villagers` | Opens a window |
| `/askx <what to do>` | Turns a request into commands, minus `blocked_commands` |
| `/ask reload` | Reloads the settings and the languages |

`mcbuddy.ask` is everyone's by default; `mcbuddy.askx` and `mcbuddy.reload` belong to operators. `scan_nearby_containers` and `find_item` see through walls: switch them off under `tools.enabled` if your players have claims.

## Links

- Source code, issues, and the full config: [GitHub](https://github.com/mcbuddy-ai/mcbuddy-spigot)
- From the same author: [Mo’Joins](https://modrinth.com/plugin/mojoins), join and quit lines in each player's language, and [Mo’Afks](https://modrinth.com/plugin/moafks), a safe AFK mode

[IP Geolocation by DB-IP](https://db-ip.com), licensed under CC BY 4.0.
