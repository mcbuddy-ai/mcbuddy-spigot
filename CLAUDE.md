# MCBuddy Spigot: notes for AI agents

The Minecraft plugin of MCBuddy. `/ask` asks the AI buddy a question, `/askx` turns a request into commands. The plugin streams the answer from mcbuddy-server over protocol v2, runs the game tools the agent asks for, and shows a live Claude Code style status while it works. README.MD explains it for humans; read it first.

## Talking to the owner

- Reply in Russian, address the owner as «вы». Quotes «ёлочки», an em dash with spaces « — ».
- Code, comments, and commit messages are in English. Player-facing texts in `config.yml` are Russian by default and stay configurable. README.MD is Russian, README.EN.md and README.CN.md follow it.

## How it fits together

| Path | What it does |
|---|---|
| `Zygote.kt` | Plugin entry: commands, tab completion, `/ask reload`, `/ask cancel` |
| `Handler.kt`, `Service.kt`, `Sse.kt`, `Protocol.kt` | Sends a question to `POST /api/v2/chat`, reads the SSE stream, posts tool results back; falls back to v1 when the backend has no v2 |
| `tools/` | Client tools. They run on the server thread and see only the asking player and the world around them; other players appear only as a count. Each can be switched off in `config.yml` |
| `Run.kt`, `Status.kt`, `Animator.kt` | One active run per player and the live status: spinner, rotating verbs, elapsed time and tokens in the action bar, `⏺`/`⎿` tool lines in chat |
| `Executor.kt` | Runs `/askx` commands as the player, minus `blocked_commands` |

The contract with the backend is `docs/protocol-v2.md` in mcbuddy-ai/mcbuddy-server. Change both sides together.

## Rules that keep it working

- Spigot API only (`org.spigotmc:spigot-api`), so the jar runs on Spigot and Paper. Watch for types that differ between them: `GameRule` is an interface in Spigot and a class in Paper, so read rules through `Registry.GAME_RULE`.
- Bukkit calls happen on the server thread; network calls never do.
- Keep tool results compact: they cost tokens on every call.
- Kotlin, kotlinx, OkHttp, and Okio are shaded and relocated under `ru.mcbuddy.spigot.libs`.

## Building and releasing

- `./gradlew build` runs the tests and builds the shaded jar; Java 25.
- The version lives in `.version` only. A push to `master` builds the plugin; a changed `.version` also creates a GitHub release with the jars. Bump it only when the owner asks.

## Commits

Short one-line imperative English messages, one logical change per commit: “Show tool results as ⎿ lines”.
