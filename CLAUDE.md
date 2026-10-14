# MCBuddy Spigot: notes for AI agents

The Minecraft plugin of MCBuddy. `/ask` asks the AI buddy a question, `/askall` asks in front of everyone, `/askx` turns a request into commands, and chat mode (`/buddy chat`) sends whatever a player writes in chat to the buddy. The plugin streams the answer from mcbuddy-server over protocol v2, runs the game tools the agent asks for, and shows a live Claude Code style status while it works. README.md explains it for players and server owners, README.RU.md is its Russian copy, MODRINTH.md the Modrinth page; read README.md first.

## Talking to the owner

- Reply in Russian, address the owner as «вы». Quotes «ёлочки», an em dash with spaces « — ».
- Code, comments, commit messages, and `config.yml` are in English. Player-facing texts live in `core/src/main/resources/lang/<code>.yml` (en, ru, es, pt, de); a new text goes into every one of them, and `LangTest` checks that each has every English key. README.md is English; README.RU.md follows Russian typography. Change both READMEs and MODRINTH.md together.

## How it fits together

Two Gradle modules. `core` holds everything that doesn't touch a game platform and never imports `org.bukkit` or `net.minecraft`; `spigot` is the plugin for Spigot, Paper, and their forks, an adapter around `core`. A future Fabric or NeoForge port is another adapter next to `spigot`, not a second copy of the plugin.

| Path | What it does |
|---|---|
| `core/…/Service.kt`, `Sse.kt`, `Protocol.kt` | Sends a question to `POST /api/v2/chat`, reads the SSE stream, runs the client tools through `Host.onMain`, posts their results back; falls back to v1 when the backend has no v2. `Host` is what a platform gives the backend client: its log, versions, the tools' context, the main thread |
| `core/…/Asker.kt`, `Run.kt`, `Status.kt` | Who asked, one question in flight, and the live status line: spinner, rotating verbs, elapsed time, tokens read (↑) and written (↓) |
| `core/…/Lang.kt`, `GeoIp.kt`, `Yaml.kt` | Each reader's language: `lang/<code>.yml` (in `core/src/main/resources`) with `{name}` placeholders and `{n|one|other}` plurals, picked by the country of the IP (DB-IP, downloaded into `geoip/`), then the game's language, then `locales.default`. `YamlConfig` reads YAML with SnakeYAML exactly as Bukkit's `FileConfiguration` did; `YamlTest` in `spigot` holds it to that |
| `core/…/Config.kt` | `config.yml` as settings. Sounds and particles stay names; the platform resolves them |
| `core/…/Chat.kt`, `PlayerSettings.kt` | `Permissions`; chat mode's routing by the first character (`!`, `?`, `~`). Each player's settings in `players.json`; the random `server-id` that names the server's shared thread of public questions |
| `core/…/tools/` | What tools share on every platform: `ClientTool` and `ToolRegistry`, `ItemData` with `compact()` and `Items.detail()` (an item as the model reads it), `Facts`, `Dimensions`, `Mechanics` (vanilla formulas and tables, with tests), the notebook (`notes/<uuid>.json`), goals (`goals/<uuid>.json`), reminders, books, buttons and their store, time zones (`timezones.json`), and `Guide` (the commands and prefixes `get_buddy_guide` lists; a new command or permission goes there too) |
| `spigot/…/Zygote.kt` | Plugin entry: `/ask`, `/askall`, `/askx`, and `/buddy` with its subcommands and tab completion; builds the core's pieces and the `Host` |
| `spigot/…/Handler.kt`, `Animator.kt`, `Executor.kt` | Runs questions for Bukkit senders. A public question (`visibility: "public"`) and its answer go to everyone online; status, tool lines, buttons, and errors stay with the asker. The action bar, sounds, and particles; `/askx` commands as the player, minus `blocked_commands` |
| `spigot/…/ChatMode.kt`, `Locales.kt`, `MemoryCommands.kt` | Chat mode's events, the join reminder, the tip under a second `/ask`; each player's language from their login; `/buddy new`, `/buddy memory`, `/buddy forget` |
| `spigot/…/tools/` | The client tools on Bukkit. They run on the server thread and see only the asking player and the world around them; other players appear only as a count. Each can be switched off in `config.yml`. `ItemStacks.kt` turns Bukkit items into `ItemData`. `ActionTools` change nothing that matters (compass, sorting, a ping, reminders); `NotebookTools` keep notes with coordinates; `LookTools` (`look_at`) reads whatever is under the crosshair, `InspectTools` searches and inspects items and containers, `CareTools` gear, XP, food; `GoalTools` and `Goals.kt` the goals' boss bars and events; `OperatorTools` change the game and are operator-only |
| `spigot/…/Clicks.kt`, `menus/` | Buttons under answers (`add_buttons`, books, the chat mode tip) and what each does when pressed; the recipe, enchanting, villager, and settings windows |

The contract with the backend is `docs/protocol-v2.md` in mcbuddy-ai/mcbuddy-server. Change both sides together.

## Rules that keep it working

- Spigot API only (`org.spigotmc:spigot-api`), so the jar runs on Spigot and Paper. Watch for types that differ between them: `GameRule` is an interface in Spigot and a class in Paper, so read rules through `Registry.GAME_RULE`.
- Bukkit calls happen on the server thread; network calls never do. Chat arrives off it: `ChatMode` only decides and cancels there, then hands the line to the server thread.
- A player's own words go into a template through `Minecraft.withText`, so an `&` they typed never becomes a color.
- A tool that creates items or changes the world is an operator tool (`operator = true`). The registry offers it only to operators, `Service` refuses it for anyone else, and the tool checks `isOp()` once more. Keep all three checks; non-operators must not be able to get gear through the assistant.
- One deliberate exception: `[Взять книгу]` gives anyone the written book with the assistant's own long answer, once per book. The owner allowed it: a book of text is no gear. Don't extend it to other items.
- Windows (`menus/`) only show pictures. `MenuListener` cancels every click, drag, and hopper move on a `Menu`; keep it that way, or the windows become a dupe.
- Buttons run `/buddy do <id>`. Ids belong to the player who got them (`ButtonStore`) and expire after an hour, so a typed command can't trigger an action nobody offered.
- Keep tool results compact: they cost tokens on every call. Spots are absolute `x`, `y`, `z` with `distance` and `direction` (`Facts.place`); dimensions are the game's ids through `Dimensions`.
- Kotlin, kotlinx, OkHttp, Okio, the MaxMind reader, and SnakeYAML are shaded and relocated under `ru.mcbuddy.spigot.libs`.
- `core` never imports `org.bukkit` or `net.minecraft`. Where it needs the game, it asks through `Asker`, `Host`, or `ItemData`; don't add a seam only a future platform would use.

## Building and releasing

- `./gradlew build` runs both modules' tests and builds `spigot/build/libs/mcbuddy-spigot-<version>.jar`, shaded; Java 25. Nothing in the repo holds the version: local builds are `dev`, CI builds on `master` and pull requests are `dev-<sha>`.
- Releases are made by hand, only when the owner asks. Add a “## X.Y.Z” section to CHANGELOG.md, push, then run `gh workflow run release.yml -R mcbuddy-ai/mcbuddy-spigot -f version=X.Y.Z` (its `changelog` field overrides the section; one of the two must be there; `-f modrinth=true -f hangar=true` also publish there once those projects exist). It builds with `-PreleaseVersion`, tags `vX.Y.Z`, and attaches `mcbuddy-spigot-X.Y.Z.jar` to a GitHub release. Watch it with `gh run watch`. Never delete or re-tag a published release.

## Installing on Moonlands

The owner's server, Moonlands, runs this plugin. When the owner asks to put a change “on the server”, release it here (the “Release” workflow above), then follow “Shipping a plugin or backend update” in `~/c/personal/minecraft/CLAUDE.md` (MairwunNx/minecraft). That repo's rules about restarts and the live world apply.

## Commits

Short one-line imperative English messages, one logical change per commit: “Show tool results as ⎿ lines”.

## Modrinth and Hangar

`build.gradle.kts` is ready for both, as in Mo’Joins: `./gradlew modrinth` uploads to the Modrinth project `mcbuddy` and syncs MODRINTH.md as its description; `./gradlew publishPluginPublicationToHangar` uploads to `MairwunNx/MCBuddy`. Both read the version from `-PreleaseVersion` and the notes from `-PchangelogFile`, and need `--no-configuration-cache`. The projects don't exist yet: create them only when the owner asks (see “Publishing the owner's projects” in `~/c/personal/minecraft/CLAUDE.md`). Tokens are the `MODRINTH_TOKEN` and `HANGAR_TOKEN` secrets; never put them in the repo. MODRINTH.md is for server owners: no build, stack, or related projects, and no Moonlands banner.

