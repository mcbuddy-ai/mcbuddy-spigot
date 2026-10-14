# MCBuddy Spigot: notes for AI agents

The Minecraft plugin of MCBuddy. `/ask` asks the AI buddy a question, `/askall` asks in front of everyone, `/askx` turns a request into commands, and chat mode (`/buddy chat`) sends whatever a player writes in chat to the buddy. The plugin streams the answer from mcbuddy-server over protocol v2, runs the game tools the agent asks for, and shows a live Claude Code style status while it works. README.md explains it for players and server owners, README.RU.md is its Russian copy, MODRINTH.md the Modrinth page; read README.md first.

## Talking to the owner

- Reply in Russian, address the owner as «вы». Quotes «ёлочки», an em dash with spaces « — ».
- Code, comments, commit messages, and `config.yml` are in English. Player-facing texts live in `src/main/resources/lang/<code>.yml` (en, ru, es, pt, de); a new text goes into every one of them, and `LangTest` checks that each has every English key. README.md is English; README.RU.md follows Russian typography. Change both READMEs and MODRINTH.md together.

## How it fits together

| Path | What it does |
|---|---|
| `Zygote.kt` | Plugin entry: `/ask`, `/askall`, `/askx`, and `/buddy` with its subcommands and tab completion |
| `ChatMode.kt`, `PlayerSettings.kt` | Chat mode: routes chat lines by their first character (`!`, `?`, `~`), the join reminder, the tip under a second `/ask`; `Permissions`. Each player's settings in `players.json`; the random `server-id` that names the server's shared thread of public questions |
| `Lang.kt`, `Locales.kt`, `GeoIp.kt` | Each reader's language: `lang/<code>.yml` with `{name}` placeholders and `{n|one|other}` plurals, picked by the country of the IP (DB-IP, downloaded into `geoip/`), then the game's language, then `locales.default`. Every text a player reads goes through `Lang`; tools say their chat summary through `context.lang` |
| `MemoryCommands.kt` | `/buddy new`, `/buddy memory`, `/buddy forget`: the backend's session and memory endpoints |
| `Handler.kt`, `Service.kt`, `Sse.kt`, `Protocol.kt` | Sends a question to `POST /api/v2/chat`, reads the SSE stream, posts tool results back; falls back to v1 when the backend has no v2. A public question (`visibility: "public"`) and its answer go to everyone online; status, tool lines, buttons, and errors stay with the asker |
| `tools/` | Client tools. They run on the server thread and see only the asking player and the world around them; other players appear only as a count. Each can be switched off in `config.yml`. `ActionTools` change nothing that matters (compass, sorting, a ping, reminders with short ids that live in memory, capped by `tools.reminders.max_pending`); `NotebookTools` keep the player's notes with coordinates in `notes/<uuid>.json` (`Notebook.kt`, atomic writes, capped by `tools.notes.max_per_player`); `LookTools` (`look_at`) reads whatever is under the crosshair, `InspectTools` searches and inspects items and containers, `CareTools` gear, XP, food; `Mechanics.kt` holds the vanilla formulas and tables they use, with tests; `OperatorTools` change the game and are operator-only |
| `Run.kt`, `Status.kt`, `Animator.kt` | One active run per player and the live status: spinner, rotating verbs, elapsed time, tokens read (↑) and written (↓) in the action bar, `⏺`/`⎿` tool lines in chat |
| `Executor.kt` | Runs `/askx` commands as the player, minus `blocked_commands` |
| `Clicks.kt`, `tools/Buttons.kt` | Buttons under answers (`add_buttons`, books, the chat mode tip) and what each does when pressed |
| `tools/BuddyTools.kt` | `get_buddy_guide` (the commands, prefixes, settings, and features this player has) and `buddy_settings`. A new command or permission goes into its lists too |
| `menus/` | The recipe, enchanting, and villager windows behind `/buddy recipe`, `/buddy enchants`, `/buddy villagers` and their buttons |
| `tools/Goals.kt`, `GoalTools` | Goals with a boss bar in `goals/<uuid>.json`: statistics count from a baseline, pickups from events minus the player's own death drops |
| `tools/TimeZones.kt` | Each player's time zone in `timezones.json`, sent as `timezone` so the backend can put their real time in front of each question |

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
- Kotlin, kotlinx, OkHttp, and Okio are shaded and relocated under `ru.mcbuddy.spigot.libs`.

## Building and releasing

- `./gradlew build` runs the tests and builds `build/libs/mcbuddy-spigot-<version>.jar`, shaded; Java 25. Nothing in the repo holds the version: local builds are `dev`, CI builds on `master` and pull requests are `dev-<sha>`.
- Releases are made by hand, only when the owner asks. Add a “## X.Y.Z” section to CHANGELOG.md, push, then run `gh workflow run release.yml -R mcbuddy-ai/mcbuddy-spigot -f version=X.Y.Z` (its `changelog` field overrides the section; one of the two must be there; `-f modrinth=true -f hangar=true` also publish there once those projects exist). It builds with `-PreleaseVersion`, tags `vX.Y.Z`, and attaches `mcbuddy-spigot-X.Y.Z.jar` to a GitHub release. Watch it with `gh run watch`. Never delete or re-tag a published release.

## Installing on Moonlands

The owner's server, Moonlands, runs this plugin. When the owner asks to put a change “on the server”, release it here (the “Release” workflow above), then follow “Shipping a plugin or backend update” in `~/c/personal/minecraft/CLAUDE.md` (MairwunNx/minecraft). That repo's rules about restarts and the live world apply.

## Commits

Short one-line imperative English messages, one logical change per commit: “Show tool results as ⎿ lines”.

## Modrinth and Hangar

`build.gradle.kts` is ready for both, as in Mo’Joins: `./gradlew modrinth` uploads to the Modrinth project `mcbuddy` and syncs MODRINTH.md as its description; `./gradlew publishPluginPublicationToHangar` uploads to `MairwunNx/MCBuddy`. Both read the version from `-PreleaseVersion` and the notes from `-PchangelogFile`, and need `--no-configuration-cache`. The projects don't exist yet: create them only when the owner asks (see “Publishing the owner's projects” in `~/c/personal/minecraft/CLAUDE.md`). Tokens are the `MODRINTH_TOKEN` and `HANGAR_TOKEN` secrets; never put them in the repo. MODRINTH.md is for server owners: no build, stack, or related projects, and no Moonlands banner.

