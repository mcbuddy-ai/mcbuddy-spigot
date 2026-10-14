package chat.allay.spigot.tools

import chat.allay.core.*
import chat.allay.core.tools.*

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.entity.Player
import java.time.Instant

/** The player's notebook: “remember there's an arrow trader here”, and later “where was that arrow guy?”. */
object NotebookTools {
  private fun tool(name: String, body: (ToolContext, Player, JsonObject) -> ToolOutput) = object : Tool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context, context.requirePlayer(), args)
  }

  private val add = tool("add_note") { context, player, args ->
    val text = args.string("text")?.take(200) ?: throw ToolError("text is required")
    val location = player.location
    val current = Dimensions.id(player.world)
    // By default a note marks where the player stands; with here: false it takes the given spot, or none.
    val here = args.bool("here") ?: (args.int("x") == null && args.int("z") == null)
    val x = if (here) location.blockX else args.int("x")
    val z = if (here) location.blockZ else args.int("z")
    if ((x == null) != (z == null)) throw ToolError("give both x and z, or neither")
    val y = if (x == null) null else if (here) location.blockY else args.int("y") ?: location.blockY
    val dimension = if (x == null) null else if (here) current else args.string("dimension")?.let(Dimensions::parse) ?: current
    // Distances need the world; a note from another dimension keeps only the dimension's name.
    val world = if (dimension == current) player.world.name else null
    val book = context.notes.of(player.uniqueId)
    val note = book.add { id -> Note(id, text, x, y, z, world, dimension, System.currentTimeMillis()) }
    context.notes.save(player.uniqueId)
    val result = buildJsonObject {
      put("id", note.id)
      put("text", note.text)
      note.x?.let { put("x", it); put("y", note.y); put("z", note.z); put("dimension", Dimensions.normalize(note.dimension)) }
    }
    ToolOutput(result, if (note.x != null) context.lang.text("summary.note_saved_at", "x" to note.x, "y" to note.y, "z" to note.z) else context.lang.text("summary.note_saved"))
  }

  private val find = tool("find_notes") { context, player, args ->
    val limit = (args.int("limit") ?: 20).coerceIn(1, 50)
    val query = args.string("query")?.take(100)
    val here = player.location
    val distance = { note: Note ->
      if (note.x == null || note.world != player.world.name) null else Math.hypot(note.x!! - here.x, note.z!! - here.z)
    }
    val found = context.notes.of(player.uniqueId).find(query, distance, limit)
    val result = buildJsonObject {
      put("matched", found.matched)
      put("total", found.total)
      put("notes", buildJsonArray {
        found.notes.forEach { note ->
          add(buildJsonObject {
            put("id", note.id)
            put("text", note.text)
            if (note.x != null) {
              put("x", note.x)
              put("y", note.y)
              put("z", note.z)
              Dimensions.normalize(note.dimension)?.let { put("dimension", it) }
            }
            distance(note)?.let { d ->
              put("distance", Math.round(d))
              put("direction", Facts.direction(note.x!! - here.x, note.z!! - here.z))
            }
            put("created_at", Instant.ofEpochMilli(note.createdAt).toString())
          })
        }
      })
    }
    val count = found.notes.size
    val summary = when {
      found.total == 0 -> context.lang.text("summary.notes_empty")
      !found.matched -> context.lang.text("summary.notes_all", "count" to count)
      else -> context.lang.text("summary.notes", "count" to count)
    }
    ToolOutput(result, summary)
  }

  private val delete = tool("delete_note") { context, player, args ->
    val id = args.string("id") ?: throw ToolError("id is required")
    if (!context.notes.of(player.uniqueId).delete(id)) throw ToolError("there is no note with this id")
    context.notes.save(player.uniqueId)
    ToolOutput(buildJsonObject { put("deleted", id) }, context.lang.text("summary.note_deleted"))
  }

  val all = listOf(add, find, delete)
}
