package ru.mcbuddy.core

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.Reader

/**
 * A YAML file read the way Bukkit's FileConfiguration reads it, so `config.yml` and `lang/` mean the same on every
 * platform: dotted paths, keys as strings, and [defaults] (the bundled file) behind a few of the getters, as in Bukkit.
 */
class YamlConfig(private val root: Map<String, Any?>, private val defaults: Map<String, Any?> = emptyMap()) {
  /** The top-level keys of this file or section, without the defaults'. */
  val keys: Set<String> get() = root.keys

  fun get(path: String): Any? = lookup(root, path)

  /** A string, or a number or boolean as text; [default] when the key is missing. */
  fun string(path: String, default: String): String = get(path)?.toString() ?: default

  /** Like Bukkit's getString without a default: the bundled file's value when this one has none. */
  fun string(path: String): String? = (get(path) ?: lookup(defaults, path))?.toString()

  fun int(path: String, default: Int): Int = (get(path) as? Number)?.toInt() ?: default

  fun double(path: String, default: Double): Double = (get(path) as? Number)?.toDouble() ?: default

  fun boolean(path: String, default: Boolean): Boolean = get(path) as? Boolean ?: default

  /** A list of strings; the bundled file's list when this one has none, an empty list when neither has. */
  fun strings(path: String): List<String> {
    val list = get(path) as? List<*> ?: lookup(defaults, path) as? List<*> ?: return emptyList()
    return list.filter { it is String || it is Number || it is Boolean || it is Char }.map { it.toString() }
  }

  /**
   * A section, or null when there is none. A section only the bundled file has comes back empty, as Bukkit's
   * getConfigurationSection does: its keys don't count, but the getters inside still see the defaults.
   */
  fun section(path: String): YamlConfig? {
    @Suppress("UNCHECKED_CAST")
    val inside = lookup(defaults, path) as? Map<String, Any?> ?: emptyMap()
    return when (val value = get(path)) {
      is Map<*, *> -> @Suppress("UNCHECKED_CAST") YamlConfig(value as Map<String, Any?>, inside)
      null -> if (lookup(defaults, path) is Map<*, *>) YamlConfig(emptyMap(), inside) else null
      else -> null
    }
  }

  /** Every leaf as `section.key` → a string or a list of strings, the way lang files are read. */
  fun flatten(): Map<String, Any> = buildMap { flatten("", root, this) }

  private fun flatten(prefix: String, map: Map<String, Any?>, into: MutableMap<String, Any>) {
    for ((key, value) in map) {
      val path = prefix + key
      when (value) {
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") flatten("$path.", value as Map<String, Any?>, into)
        is List<*> -> into[path] = value.map { it.toString() }
        null -> {}
        else -> into[path] = value.toString()
      }
    }
  }

  companion object {
    private fun yaml() = Yaml(LoaderOptions().apply {
      maxAliasesForCollections = Int.MAX_VALUE
      codePointLimit = Int.MAX_VALUE
      nestingDepthLimit = 100
    })

    /** Parses a file's text; an empty file is an empty map. Throws on YAML that doesn't parse. */
    fun parse(reader: Reader): Map<String, Any?> = normalize(yaml().load<Any?>(reader))

    fun parse(text: String): Map<String, Any?> = parse(text.reader())

    fun read(file: File): YamlConfig = file.reader(Charsets.UTF_8).use { YamlConfig(parse(it)) }

    /** Keys become strings and dotted keys become sections, as Bukkit stores them. */
    private fun normalize(value: Any?): Map<String, Any?> {
      val map = value as? Map<*, *> ?: return emptyMap()
      val result = LinkedHashMap<String, Any?>()
      for ((key, inner) in map) put(result, key.toString().split('.'), if (inner is Map<*, *>) normalize(inner) else inner)
      return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(map: LinkedHashMap<String, Any?>, path: List<String>, value: Any?) {
      if (path.size == 1) {
        map[path[0]] = value
        return
      }
      val next = map[path[0]] as? LinkedHashMap<String, Any?> ?: LinkedHashMap<String, Any?>().also { map[path[0]] = it }
      put(next, path.drop(1), value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun lookup(map: Map<String, Any?>, path: String): Any? {
      var current: Any? = map
      for (part in path.split('.')) current = (current as? Map<String, Any?> ?: return null)[part]
      return current
    }
  }
}
