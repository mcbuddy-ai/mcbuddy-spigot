# Changelog

The “Release” workflow takes the notes for a version from its section here, unless you type them into its `changelog` field.

## 2.4.0

- 🔍 **The assistant looks into items.** The new `inspect_item` tool reads one item from the hand, an armor slot, the inventory, the ender chest, or the container under the crosshair: attack damage and speed as the tooltip shows them, potion effects with their seconds, trims, dye color, the anvil's repair cost, a signed book's title and author (never its pages), fireworks, and what a shulker box or bundle holds. It only reads; switch it off with `tools.inspect_item`.
- 🎒 **Bundles and shulker boxes count.** The inventory lists what the bundles and shulker boxes in it hold, so “how many arrows do I have?” counts the ones in a bundle too. Containers do the same.
- 📚 Every item the assistant sees names a book’s stored enchantments, a potion’s type, an armor trim, and how full a box or bundle is. `get_container` lists every stack by slot when asked.
- 📦 **One jar per release**, `mcbuddy-spigot-X.Y.Z.jar`, with everything inside; there is no `-all` jar any more. Releases are made by hand from the “Release” workflow.

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
