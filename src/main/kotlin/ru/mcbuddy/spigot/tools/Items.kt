package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

/** An item in as few tokens as the model needs: id, count, and only the extras it has. */
fun ItemStack?.compact(slot: Int? = null): JsonObject? {
  if (this == null || type.isAir || amount <= 0) return null
  val meta = itemMeta
  return buildJsonObject {
    slot?.let { put("slot", it) }
    put("id", type.key.key)
    if (amount > 1) put("count", amount)
    if (meta?.hasDisplayName() == true) put("name", meta.displayName)
    if (enchantments.isNotEmpty()) putJsonObject("enchantments") { enchantments.forEach { (enchantment, level) -> put(enchantment.key.key, level) } }
    val max = type.maxDurability.toInt()
    if (max > 0) {
      put("damage", (meta as? Damageable)?.damage ?: 0)
      put("max_damage", max)
    }
  }
}
