package io.github.Cherryh4ck.toms3DataSender

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class ConnectionListener(private val plugin : Toms3DataSender) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val playerCount = Bukkit.getOnlinePlayers().size
            plugin.currentPlayerPeak.getAndUpdate { current ->
                maxOf(current, playerCount)
            }
        })
    }
}