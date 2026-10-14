# Changelog

The “Release” workflow takes the notes for a version from its section here, unless you type them into its `changelog` field.

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
