package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Statistic
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import ru.mcbuddy.spigot.Plurals

/** Goals with a boss bar: “collect 16 diamonds”, “reach level 30”, “find a cherry grove”. */
object GoalTools {
  private fun tool(name: String, body: (ToolContext, Player, JsonObject) -> ToolOutput) = object : ClientTool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context, context.requirePlayer(), args)
  }

  private val DIMENSIONS = setOf("normal", "nether", "the_end")

  private val set = tool("set_goal") { context, player, args ->
    val kind = args.string("kind")?.lowercase() ?: throw ToolError("kind is required: ${Goal.KINDS.joinToString()}")
    if (kind !in Goal.KINDS) throw ToolError("unknown kind $kind; use ${Goal.KINDS.joinToString()}")
    val title = args.string("title")?.take(40) ?: throw ToolError("title is required: what the bar says, in the player's language")
    val color = args.string("color")?.lowercase() ?: "green"
    if (BarColor.entries.none { it.name.equals(color, ignoreCase = true) }) throw ToolError("color must be one of ${BarColor.entries.joinToString { it.name.lowercase() }}")
    val style = args.string("style")?.lowercase() ?: "solid"
    if (BarStyle.entries.none { it.name.equals(style, ignoreCase = true) }) throw ToolError("style must be one of ${BarStyle.entries.joinToString { it.name.lowercase() }}")
    val amount = args.int("target")
    fun target() = (amount ?: throw ToolError("target is required for $kind")).also { if (it !in 1..1_000_000) throw ToolError("target must be 1–1000000") }.toLong()
    val base = Goal(id = "", kind = kind, title = title, target = 1, color = color, style = style, createdAt = 0)
    val spec = when (kind) {
      "stat" -> {
        val name = args.string("statistic")?.lowercase() ?: throw ToolError("statistic is required, such as mine_block, kill_entity, craft_item, fish_caught, walk_one_cm")
        val statistic = runCatching { Statistic.valueOf(name.uppercase()) }.getOrNull() ?: throw ToolError("no statistic called $name")
        var material: String? = null
        var entity: String? = null
        when (statistic.type) {
          Statistic.Type.BLOCK -> material = InteractTools.material(args.string("item") ?: throw ToolError("$name needs item: which block")).also {
            if (!it.isBlock) throw ToolError("${it.key.key} is not a block")
          }.key.key
          Statistic.Type.ITEM -> material = InteractTools.material(args.string("item") ?: throw ToolError("$name needs item")).key.key
          Statistic.Type.ENTITY -> entity = (Goals.entityType(args.string("entity") ?: throw ToolError("$name needs entity, such as zombie"))
            ?: throw ToolError("no mob called ${args.string("entity")}")).key.key
          else -> {}
        }
        // Distances are counted in centimeters; the player thinks in blocks.
        val scale = if (name.endsWith("_one_cm")) 100 else 1
        base.copy(statistic = name, material = material, entity = entity, target = target() * scale, scale = scale)
      }
      "pickup" -> base.copy(material = InteractTools.material(args.string("item") ?: throw ToolError("pickup needs item")).key.key, target = target())
      "level" -> base.copy(target = target().also { if (it > 1000) throw ToolError("level must be 1–1000") })
      "advancement" -> {
        val key = args.string("advancement")?.lowercase() ?: throw ToolError("advancement is required, such as story/mine_diamond")
        val advancement = NamespacedKey.fromString(if (':' in key) key else "minecraft:$key")?.let(Bukkit::getAdvancement)
          ?: throw ToolError("no advancement called $key")
        base.copy(advancement = advancement.key.toString())
      }
      "reach" -> {
        val x = args.int("x") ?: throw ToolError("reach needs x and z")
        val z = args.int("z") ?: throw ToolError("reach needs x and z")
        val dimension = args.string("dimension")?.lowercase() ?: player.world.environment.name.lowercase()
        if (dimension !in DIMENSIONS) throw ToolError("dimension must be normal, nether, or the_end")
        base.copy(x = x, z = z, radius = (args.int("radius") ?: 10).coerceIn(2, 200), dimension = dimension)
      }
      "biome" -> {
        val name = args.string("biome")?.lowercase()?.removePrefix("minecraft:") ?: throw ToolError("biome is required, such as cherry_grove")
        Registry.BIOME.get(NamespacedKey.minecraft(name)) ?: throw ToolError("no biome called $name")
        base.copy(biome = name)
      }
      else -> {
        val dimension = args.string("dimension")?.lowercase() ?: throw ToolError("dimension is required: nether or the_end")
        if (dimension !in DIMENSIONS) throw ToolError("dimension must be normal, nether, or the_end")
        base.copy(dimension = dimension)
      }
    }
    val goal = context.features.goals.add(player, spec)
    ToolOutput(describe(goal), "${goal.title}: ${goal.progress / goal.scale}/${goal.target / goal.scale}")
  }

  private fun describe(goal: Goal) = buildJsonObject {
    put("id", goal.id)
    put("kind", goal.kind)
    put("title", goal.title)
    put("progress", goal.progress / goal.scale)
    put("target", goal.target / goal.scale)
    if (goal.done) put("done", true)
  }

  private val list = tool("get_goals") { context, player, _ ->
    val goals = context.features.goals.of(player.uniqueId).all()
    val count = goals.size.toLong()
    ToolOutput(buildJsonArray { goals.forEach { add(describe(it)) } }, if (count == 0L) "целей нет" else "$count ${Plurals.ru(count, "цель", "цели", "целей")}")
  }

  private val cancel = tool("cancel_goal") { context, player, args ->
    val all = args.bool("all") == true
    val id = if (all) null else args.string("id") ?: throw ToolError("id is required, or all: true")
    val removed = context.features.goals.remove(player, id)
    if (removed == 0 && !all) throw ToolError("there is no goal with this id")
    val count = removed.toLong()
    ToolOutput(buildJsonObject { put("cancelled", removed) }, "снял $count ${Plurals.ru(count, "цель", "цели", "целей")}")
  }

  val all = listOf(set, list, cancel)
}
