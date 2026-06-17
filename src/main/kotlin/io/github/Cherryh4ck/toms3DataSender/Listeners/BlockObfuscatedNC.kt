package io.github.Cherryh4ck.toms3DataSender.Listeners

import io.github.Cherryh4ck.toms3DataSender.Toms3DataSender
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class BlockObfuscatedNC(private val plugin: Toms3DataSender) : Listener {
    val mm = MiniMessage.miniMessage()

    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val command = event.message

        if (command.startsWith("/nc") || command.startsWith("/namecolor")) {
            if (command.contains("magic")) {
                event.isCancelled = true
                player.sendMessage(mm.deserialize("${plugin.prefix} This style cannot be used."))
            }
        }
    }
}