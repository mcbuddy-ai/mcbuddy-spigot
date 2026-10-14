# Changelog

The “Release” workflow takes the notes for a version from its section here, unless you type them into its `changelog` field.

## 5.0.0

Your server now chooses who pays for the AI model: its own key, or Allay's subscription paid in Telegram Stars. Needs Allay Server 5.0 for both; an older backend answers as before.

- 💳 **A subscription in Telegram Stars** (`provider.mode: hosted`, the default). `/allay link` gets a license, keeps it in `plugins/Allay/license.key`, and shows a **[Pay in Telegram]** button and a **[Copy link]** one; the link works once and for 24 hours. Plans: a week for 150 ⭐, a month for 500 ⭐ that renews by itself, a year for 10 000 ⭐. The bot sees your server's MOTD, so you know which server you pay for.
- 🔑 **Your own key** (`provider.mode: byok`): `provider.byok` takes the `base_url` of any OpenAI-compatible API, the `api_key`, the `model`, and the `search_model` (empty: no web search). An `X-OpenRouter-Token` in `http.custom_headers` keeps working.
- 📊 **`/allay status`** shows who pays. For your key: the API, the models, and the key with all but its ends hidden. For the subscription: linked or not, active or not, the plan, the date it's paid until in your language, a bar of this period's limit (“62% of this period's limit used, refills 14 November”), and whether it renews.
- 🔔 **Operators hear about the subscription.** Every 5 minutes Allay checks it and tells operators online when a payment comes in, when the limit passes 80% and 100% (with a **[Renew]** button), and three days before a subscription that doesn't renew ends. `subscription.json` remembers what they heard, so a restart doesn't repeat it.
- ☕ **Friendly notes when Allay can't answer.** A lapsed subscription reads “Allay is taking a break on this server: the subscription isn't paid. Your admins know what to do.” A spent limit says when it refills. Operators also get a **[/allay link]** button, or what to fix in `config.yml` when the provider refuses the key, doesn't know the model, the model can't call tools, or the API address isn't allowed. Too many questions at once says how many seconds to wait.
- 🌍 **`/allay locale [player]`** shows which language a player reads Allay in and why: their IP's country, their game's language, or the default.
- 🔒 The key and the license never go into the log; anywhere they show, only their ends do. `license.key` is readable by the server's user only.
- New permission `allay.admin` (operators) for `/allay status`, `/allay link`, `/allay locale`, and the notes about the subscription.

## 4.0.1

- The move from MCBuddy keeps only the language lines you wrote yourself. A server that has run since MCBuddy 2.4 kept that release's texts in `lang/`, and 4.0.0 took them for your own; now texts any MCBuddy release shipped give way to Allay's.

## 4.0.0

MCBuddy is now **Allay**, after the friendly mob that brings players what they need. Needs Allay Server 4.0 for the renamed tools; MCBuddy Server 3.0 still answers, but the assistant then knows the old names.

What an admin does: swap `mcbuddy-spigot-*.jar` for `allay-spigot-*.jar` (don't keep both) and update the permissions in your permission plugin.

- 📦 **The data folder moves by itself.** On the first start Allay moves `plugins/mcbuddy-spigot/` into `plugins/Allay/`: `config.yml`, `lang/`, `notes/`, `goals/`, `timezones.json`, `players.json`, `server-id`, and `geoip/`, logging each file. The server's id stays, so the shared thread of public questions carries on. Settings already in `plugins/Allay/` (a `config.yml` your setup copies in at start) win over MCBuddy's. If a move fails, everything goes back and the old folder stays as it was; if both folders already hold players' data, nothing moves and the log says so.
- ✏️ **Texts take the new name where you didn't change them.** The default book author becomes “Allay”, default sounds become the new ones, and lines in `lang/` you never touched give way to Allay's texts. Lines you changed stay, with MCBuddy and `/buddy` renamed.
- ⚠️ **`/buddy` is `/allay`**, with the same subcommands and settings window; there's no `/buddy` alias. **Permissions are `allay.*`** instead of `mcbuddy.*`: `allay.ask`, `allay.askall`, `allay.askall.nocooldown`, `allay.chat`, `allay.askx`, `allay.reload`, with the same defaults.
- 🪽 **Allay sounds like an allay.** A curious hum while it works (`ENTITY_ALLAY_AMBIENT_WITH_ITEM`), a happy chortle with the answer (`ENTITY_ALLAY_ITEM_GIVEN`), and a soft sigh on errors (`ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM`). Each pitch moves a little (`ui.sounds.pitch_variation`), and players who read a public answer hear a quiet chortle too (`ui.sounds.public_answer`).
- 🌐 **The backend lives at `https://allay.chat`**; mcbuddy.ru is gone. A moved `config.yml` that still points at mcbuddy.ru points at allay.chat.
- 💬 Texts in all five languages say Allay; public lines read “✦ Allay »” in the allay's blue, and some status verbs are the mob's own: humming a tune, circling the note block, looking for something shiny.
- 🧰 The tools `get_buddy_guide` and `buddy_settings` are `get_allay_guide` and `allay_settings`; their switches in `config.yml` move along with the data.
- 🧱 **Two modules.** `core` holds everything that doesn't touch a game platform; `spigot` is the plugin for Spigot, Paper, and their forks. The jar is `allay-spigot-X.Y.Z.jar`, and the libraries inside are relocated under `chat.allay.libs`.
- The User-Agent is `Allay-Spigot/<version>`.

## 3.1.0

- ⚙️ **`/buddy` opens a settings window**, built like the recipe window: switches for chat mode and its tip with a green or red pane under each, your time zone and the time there, what MCBuddy remembers, starting over (shift-click, so a stray click can't wipe the conversation), and the recipe, enchanting, and villager windows. Like the other windows, it only shows pictures.
- `get_buddy_guide` tells the assistant about the window, so it can point players there.

## 3.0.0

Needs MCBuddy Server 3.0. An older backend still answers, but it doesn't know a question is public, keeps no shared thread, and lacks the new tools.

- 🗨️ **Chat mode.** `/buddy chat` sends everything a player writes in chat to the assistant instead of the other players. `!` in front runs commands like `/askx`, `?` asks in front of everyone, `~` writes to the ordinary chat; a space after the prefix doesn't matter. It stays on between visits, and the plugin reminds the player when they join.
- 💡 **A tip under the second `/ask`** of a visit, quiet and gray like Claude Code's: one button turns chat mode on, the other hides the tip for good (`/buddy hints` brings it back).
- 📣 **`/askall`**: every player online sees the question and the answer. Public questions share one thread per server on the backend, so a friend can follow up on someone else's question. `askall.cooldown_seconds` (30) keeps the chat from flooding; `mcbuddy.askall.nocooldown` skips it.
- 📖 **The assistant explains MCBuddy.** `get_buddy_guide` tells it the commands, chat prefixes, settings, and features this player has; `buddy_settings` switches chat mode and the tip when the player asks in words.
- 🧩 **`get_server` lists the server's plugins** with versions and short descriptions; `tools.server.hidden_plugins` leaves some out.
- ⚠️ **New command layout.** `/ask` only asks now. `cancel`, `new`, `memory`, `forget`, `recipe`, `enchants`, `villagers`, and `reload` moved to `/buddy`. New permissions: `mcbuddy.askall`, `mcbuddy.chat`, `mcbuddy.askall.nocooldown`.
- The server tool line under `get_server` shows its text again instead of a key.

## 2.4.0

- 🌍 **Each player reads their own language.** Every text moved from `config.yml` to `lang/<language>.yml`: English, Russian, Spanish, Portuguese, and German. The plugin picks a player's language by the country of their IP, with DB-IP's free database it downloads and refreshes itself, then by their game's language, then `locales.default`. Tool lines, buttons, windows, boss bars, and errors all follow it.
- ⚙️ **`config.yml` holds settings only, in English.** A 2.3 config still loads; its texts (`messages`, `menus`, `error_messages`, and the text keys under `ui`) are ignored, so move your changes to `lang/`. `/ask reload` reloads the languages too.
- 👀 **`look_at` replaces seven tools**: `get_target`, `get_block_info`, `get_container`, `get_enchant_options`, `get_mob_info`, `get_villager_trades`, and, for maps, `get_map_info` (now part of `inspect_item`). One call tells the assistant whatever is under the crosshair.
- 🔍 **`find_item`** finds where an item is and how many there are: inventory, armor, ender chest, and containers around, inside shulker boxes and bundles too. `scan_nearby_containers` now only lists what's around.
- 🔎 **`inspect_item`** reads one item in full: attack damage and speed as the tooltip shows them, potion effects with their seconds, trims, dye color, the anvil's repair cost, a signed book's title and author (never its pages), fireworks, maps, and what a shulker box or bundle holds; `inner_slot` reads an item inside one.
- 🎒 **Bundles and shulker boxes count.** The inventory and containers say what the bundles and shulker boxes in them hold, so “how many arrows do I have?” counts the ones in a bundle too.
- 🧭 **Absolute coordinates everywhere.** Notable blocks, containers, dark spots, and brewing stands come with x, y, z, distance, and direction instead of offsets. Dimensions are `overworld`, `the_nether`, and `the_end`; notes and goals saved with the old names still match. `point_to` takes a dimension and refuses a spot in another one.
- ⏰ **`reminders` and `goals`** each replace three tools with one `action`. Advancements now carry the key a goal needs. `get_item_durability` covers the whole inventory, not just the hotbar.
- Tool switches in `config.yml` follow the new names; switches for the old ones are ignored.
- 📦 **One jar per release**, `mcbuddy-spigot-X.Y.Z.jar`, with everything inside; there is no `-all` jar any more. Releases are made by hand from the “Release” workflow.
- Needs MCBuddy Server 2.4: the backend serves only the new tool names.
- README in English with a Russian copy, and the MIT license.

## 2.3.0

- Buttons under answers, the recipe, enchanting, and villager windows (`/ask recipe`, `/ask enchants`, `/ask villagers`), goals with a boss bar, long answers as books, and each player's time zone.

## 2.2.0

- A notebook of places, tools that read containers, blocks, maps, enchanting tables, mobs, dark spots, brewing, farms, gear wear, experience, and food, and `ping_me`.

## 2.1.0

- Places, villager trades, `point_to`, `sort_inventory`, reminders with ids, operator-only tools, `/ask new`, `/ask memory`, and `/ask forget`.

## 2.0.0

A rewrite for Spigot 26.3 and Java 25.

- Answers stream over protocol v2, and the assistant calls game tools about the asking player.
- A live status in the action bar and chat while it works; `/ask cancel` and tab completion.

## 1.4.2

- Build fixes and newer dependencies.

## 1.3.0

- Clear messages for backend, network, and plugin errors; configurable timeouts.

## 1.2.0

- `/ask reload` and `/askx reload` reload the config; sounds load reliably.

## 1.0.0

- `/ask` and `/askx` with the MCBuddy backend.
