package chat.allay.core

import java.io.File
import java.io.Reader
import java.util.Locale

/**
 * The texts of every language, by code (`en`, `ru`, `pt_br`), flattened to dotted keys. A key missing
 * from one language comes from the default language, then from English.
 */
class Texts(private val languages: Map<String, Map<String, Any>>, default: String) {
  val codes: Set<String> get() = languages.keys

  val default: String = default.takeIf { it in languages } ?: FALLBACK

  fun lang(code: String?): Lang = Lang(code?.takeIf { it in languages } ?: default, this)

  /** A string or a list of strings; null when no language has the key. */
  fun raw(code: String, key: String): Any? = languages[code]?.get(key) ?: languages[default]?.get(key) ?: languages[FALLBACK]?.get(key)

  companion object {
    const val FALLBACK = "en"
    val BUNDLED = listOf("en", "ru", "es", "pt", "de")

    /** Every leaf of a language file as `section.key` → string or list of strings. */
    fun read(reader: Reader): Map<String, Any> = YamlConfig(YamlConfig.parse(reader)).flatten()

    fun read(file: File): Map<String, Any> = YamlConfig.read(file).flatten()
  }
}

/**
 * One reader's language. Texts use `&` colors, `{name}` placeholders, and plurals as `{n|stack|stacks}`,
 * with as many forms as the language has (Russian takes three: `{n|стак|стака|стаков}`).
 */
class Lang(val code: String, private val texts: Texts) {
  /** The text with its placeholders filled; an empty string means the message is off. Unknown keys come back as the key. */
  fun text(key: String, vararg values: Pair<String, Any?>): String = fill(texts.raw(code, key)?.let { it as? String ?: (it as List<*>).joinToString("\n") } ?: key, values.toMap())

  fun list(key: String): List<String> = when (val raw = texts.raw(code, key)) {
    is List<*> -> raw.map { it.toString() }
    is String -> listOf(raw)
    else -> emptyList()
  }

  fun has(key: String) = texts.raw(code, key) != null

  fun plural(n: Long, forms: List<String>): String = Plurals.pick(code, n, forms)

  /** One pass, so a value that contains braces (a player's note) is never read as a placeholder. */
  private fun fill(template: String, values: Map<String, Any?>): String = PLACEHOLDER.replace(template) { match ->
    val name = match.groupValues[1]
    val value = values[name] ?: return@replace match.value
    val forms = match.groupValues[2]
    if (forms.isEmpty()) value.toString() else plural((value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: 0, forms.removePrefix("|").split('|'))
  }

  private companion object {
    val PLACEHOLDER = Regex("\\{(\\w+)((?:\\|[^|{}]*)*)}")
  }
}

/** Plural forms as CLDR counts them, for the bundled languages; others take English's rule. */
object Plurals {
  fun pick(code: String, n: Long, forms: List<String>): String {
    if (forms.isEmpty()) return ""
    val index = when (code.substringBefore('_')) {
      "ru", "uk", "be" -> {
        val mod10 = n % 10
        val mod100 = n % 100
        when {
          mod10 == 1L && mod100 != 11L -> 0
          mod10 in 2..4 && mod100 !in 12..14 -> 1
          else -> 2
        }
      }
      "pt", "fr" -> if (n == 0L || n == 1L) 0 else 1
      else -> if (n == 1L) 0 else 1
    }
    return forms[index.coerceAtMost(forms.lastIndex)]
  }
}

/** Picks a player's language: the country of their IP address, then the language their game is set to. */
class LocaleResolver(private val settings: Config.Locales, private val available: Set<String>) {
  private val byCountry: Map<String, String> = buildMap {
    settings.countries.forEach { (code, countries) -> countries.forEach { putIfAbsent(it.uppercase(), normalize(code)) } }
  }

  fun resolve(country: String?, client: String?): String? = explain(country, client)?.first

  /** The language and the source that picked it, `ip` or `client`; null when neither did and the default applies. */
  fun explain(country: String?, client: String?): Pair<String, String>? {
    for (source in settings.sources) {
      val code = when (source) {
        "ip" -> country?.let { byCountry[it.uppercase()] }
        "client" -> client?.let(::fromClient)
        else -> null
      }
      if (code != null && code in available) return code to source
    }
    return null
  }

  /** `pt_BR` matches a `pt_br` file first, then `pt`. */
  private fun fromClient(locale: String): String? {
    val full = normalize(locale)
    val language = full.substringBefore('_')
    return when {
      full in available -> full
      language in available -> language
      else -> null
    }
  }

  companion object {
    fun normalize(code: String) = code.trim().lowercase(Locale.ROOT).replace('-', '_')
  }
}
