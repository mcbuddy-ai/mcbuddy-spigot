package ru.mcbuddy.spigot

import org.bukkit.command.CommandSender
import java.lang.reflect.Proxy

/** A sender for code that only holds on to it; any call on it fails the test. */
val FakeSender: CommandSender = Proxy.newProxyInstance(CommandSender::class.java.classLoader, arrayOf(CommandSender::class.java)) { _, method, _ ->
  error("unexpected call: ${method.name}")
} as CommandSender
