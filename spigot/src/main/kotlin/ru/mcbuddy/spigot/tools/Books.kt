package ru.mcbuddy.spigot.tools

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import ru.mcbuddy.core.tools.WrittenBook
import ru.mcbuddy.spigot.Minecraft.formatMessage

/** A book the assistant wrote, as the game's written book. */
object BookItems {
  fun item(book: WrittenBook, author: String): ItemStack = ItemStack(Material.WRITTEN_BOOK).apply {
    val meta = itemMeta as BookMeta
    meta.title = book.title.take(32)
    meta.author = author
    meta.generation = BookMeta.Generation.ORIGINAL
    meta.pages = book.pages.map(::formatMessage)
    itemMeta = meta
  }
}
