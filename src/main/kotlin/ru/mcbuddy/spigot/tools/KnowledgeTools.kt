package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.StonecuttingRecipe
import ru.mcbuddy.spigot.Plurals

/** What this server knows that the model might not: its exact recipes, and the asker's advancements. */
object KnowledgeTools {
  private val recipes = object : ClientTool {
    override val name = "get_recipes"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val item = (args["item"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.removePrefix("minecraft:")
        ?.replace(' ', '_') ?: throw ToolError("say which item")
      val material = Material.matchMaterial(item)?.takeIf { it.isItem } ?: throw ToolError("no item called $item")
      val found = Bukkit.getRecipesFor(ItemStack(material)).take(5).mapNotNull(::describe)
      val result = buildJsonObject {
        put("item", material.key.key)
        put("recipes", JsonArray(found))
      }
      val count = found.size.toLong()
      return ToolOutput(result, if (count == 0L) "рецептов нет" else "$count ${Plurals.ru(count, "рецепт", "рецепта", "рецептов")}")
    }

    private fun choice(choice: RecipeChoice?): String = when (choice) {
      is RecipeChoice.MaterialChoice -> choice.choices.take(3).joinToString(" | ") { it.key.key }
      is RecipeChoice.ExactChoice -> choice.choices.take(3).joinToString(" | ") { it.type.key.key }
      null -> ""
      else -> choice.itemStack.type.key.key
    }

    private fun describe(recipe: Recipe): JsonObject? = buildJsonObject {
      if (recipe.result.amount > 1) put("makes", recipe.result.amount)
      when (recipe) {
        is ShapedRecipe -> {
          put("type", "crafting_shaped")
          put("shape", buildJsonArray { recipe.shape.forEach { add(JsonPrimitive(it)) } })
          putJsonObject("key") { recipe.choiceMap.forEach { (symbol, value) -> if (value != null) put(symbol.toString(), choice(value)) } }
        }
        is ShapelessRecipe -> {
          put("type", "crafting_shapeless")
          put("ingredients", buildJsonArray { recipe.choiceList.forEach { add(JsonPrimitive(choice(it))) } })
        }
        is CookingRecipe<*> -> {
          put("type", recipe.javaClass.simpleName.removePrefix("Craft").removeSuffix("Recipe").lowercase())
          put("input", choice(recipe.inputChoice))
          put("seconds", recipe.cookingTime / 20.0)
          put("experience", recipe.experience)
        }
        is StonecuttingRecipe -> {
          put("type", "stonecutting")
          put("input", choice(recipe.inputChoice))
        }
        is SmithingTransformRecipe -> {
          put("type", "smithing")
          put("template", choice(recipe.template))
          put("base", choice(recipe.base))
          put("addition", choice(recipe.addition))
        }
        else -> return null
      }
    }
  }

  private val advancements = object : ClientTool {
    override val name = "get_advancements"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val player = context.requirePlayer()
      val filter = (args["filter"] as? JsonPrimitive)?.contentOrNull ?: "todo"
      val done = mutableListOf<JsonObject>()
      val todo = mutableListOf<JsonObject>()
      Bukkit.advancementIterator().forEach { advancement ->
        val key = advancement.key.key
        val display = advancement.display ?: return@forEach
        if (key.startsWith("recipes/") || display.isHidden) return@forEach
        val entry = buildJsonObject {
          put("title", display.title)
          put("description", display.description)
        }
        val progress = player.getAdvancementProgress(advancement)
        when {
          progress.isDone -> done += entry
          // Started ones first: they are the nearest next steps. Spigot has no parent links to go by.
          progress.awardedCriteria.isNotEmpty() -> todo.add(0, buildJsonObject { entry.forEach { (k, v) -> put(k, v) }; put("started", true) })
          else -> todo += entry
        }
      }
      val result = buildJsonObject {
        put("done_count", done.size)
        if (filter == "done") put("done", JsonArray(done.take(40))) else put("next", JsonArray(todo.take(40)))
      }
      val count = done.size.toLong()
      return ToolOutput(result, "получено $count, впереди ${todo.size}")
    }
  }

  val all = listOf(recipes, advancements)
}
