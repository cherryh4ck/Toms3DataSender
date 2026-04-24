package io.github.Cherryh4ck.toms3DataSender

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChatListener(private val plugin: Toms3DataSender) : Listener {
    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val playerName = event.player.name
        val messageComponent = event.message()
        val messageText = PlainTextComponentSerializer.plainText().serialize(messageComponent)

        if (messageText.startsWith(">")){
            plugin.insertChatData(playerName, messageText, "CHAT_GREENTEXT");
        }
        else if (messageText.startsWith("!")){
            plugin.insertChatData(playerName, messageText, "CHAT_REDTEXT");
        }
        else{
            plugin.insertChatData(playerName, messageText, "CHAT");
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.insertChatData(playerName, "has joined the server.", "EVENT");
        })
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        val playerName = event.player.name

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.insertChatData(playerName, "has left the server.", "EVENT");
        })
    }
}