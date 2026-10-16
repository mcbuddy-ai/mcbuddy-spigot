package chat.allay.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Brewing as the game keeps it since 26.3: the files in `data/minecraft/recipe/brewing/`, each a bottle of one potion plus a
 * reagent giving another bottle. Spigot's `BrewingRecipe` shows only the result, so the platform reads the files and
 * this turns them into the steps from a water bottle to any potion.
 */
object Brewing {
  /** A bottle: its item (`potion`, `splash_potion`, `lingering_potion`) and the potion inside, as the game's ids. */
  data class Bottle(val item: String, val potion: String)

  /** One use of the brewing stand. */
  data class Brew(val from: Bottle, val reagent: String, val to: Bottle)

  val WATER = Bottle("potion", "water")
  val FORMS = listOf("potion", "splash_potion", "lingering_potion")

  /** The brews in one recipe file: one per input potion. Tags and shapes the game doesn't ship give none. */
  fun parse(json: String): List<Brew> = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    if (id(root["type"]) != "brewing") return emptyList()
    val input = root["input"]?.jsonObject ?: return emptyList()
    val from = id(input["item"]) ?: return emptyList()
    val potions = ids(input["potion_contents"]?.jsonObject?.get("potions"))
    val reagents = ids(root["reagent"]?.jsonObject?.get("item"))
    val output = root["output"]?.jsonObject ?: return emptyList()
    val to = id(output["id"]) ?: return emptyList()
    val contents = output["components"]?.jsonObject?.get("minecraft:potion_contents")
    val made = id((contents as? JsonObject)?.get("potion") ?: contents) ?: return emptyList()
    potions.flatMap { potion -> reagents.map { reagent -> Brew(Bottle(from, potion), reagent, Bottle(to, made)) } }
  }.getOrDefault(emptyList())

  /**
   * The brews from a water bottle to [target], or null when the stand can't make it. The usual way first: brew the
   * drinkable potion, then gunpowder and dragon's breath turn the bottle; otherwise the fewest brews any way.
   */
  fun chain(brews: List<Brew>, target: Bottle): List<Brew>? {
    if (target.item != "potion") usual(brews, target)?.let { return it }
    return shortest(brews, target)
  }

  private fun usual(brews: List<Brew>, target: Bottle): List<Brew>? {
    val path = shortest(brews, Bottle("potion", target.potion))?.toMutableList() ?: return null
    var bottle = Bottle("potion", target.potion)
    while (bottle != target) {
      val next = FORMS.getOrNull(FORMS.indexOf(bottle.item) + 1) ?: return null
      val brew = brews.firstOrNull { it.from == bottle && it.to == Bottle(next, target.potion) } ?: return null
      path += brew
      bottle = brew.to
    }
    return path
  }

  private fun shortest(brews: List<Brew>, target: Bottle): List<Brew>? {
    if (target == WATER) return emptyList()
    val came = HashMap<Bottle, Brew>()
    val queue = ArrayDeque(listOf(WATER))
    while (queue.isNotEmpty()) {
      val bottle = queue.removeFirst()
      for (brew in brews) {
        if (brew.from != bottle || brew.to == WATER || brew.to in came) continue
        came[brew.to] = brew
        if (brew.to == target) return generateSequence(brew) { came[it.from] }.toList().reversed()
        queue += brew.to
      }
    }
    return null
  }

  /** Every potion the stand makes, in the game's ids. */
  fun potions(brews: List<Brew>): Set<String> = brews.map { it.to.potion }.toSortedSet()

  /**
   * The potion the model or a player named: an exact id, or a part of one (“night vision” finds `night_vision`
   * before `long_night_vision`). Null when nothing matches.
   */
  fun find(brews: List<Brew>, name: String): String? {
    val wanted = name.trim().lowercase().removePrefix("minecraft:").replace(' ', '_').removePrefix("potion_of_")
    val known = potions(brews)
    return known.firstOrNull { it == wanted } ?: known.filter { wanted in it }.minByOrNull { it.length }
  }

  private fun strip(id: String) = id.removePrefix("minecraft:")

  private fun id(element: JsonElement?): String? = (element as? JsonPrimitive)?.content?.takeUnless { it.startsWith("#") }?.let(::strip)

  private fun ids(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull(::id)
    else -> listOfNotNull(id(element))
  }
}
