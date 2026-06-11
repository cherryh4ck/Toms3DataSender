package io.github.Cherryh4ck.toms3DataSender.Commands

import io.github.Cherryh4ck.toms3DataSender.Toms3DataSender
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import java.util.UUID

class ChangeGradient(private val plugin : Toms3DataSender) : TabExecutor {
    val availableGradients = listOf("default", "dark_green", "dark_red", "gold", "dark_gray", "green", "red", "yellow", "dark_blue", "dark_aqua", "dark_purple", "gray", "blue", "aqua", "light_purple", "black")
    val mm = MiniMessage.miniMessage()
    val cooldowns: MutableSet<UUID> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(mm.deserialize("${plugin.prefix} You can't use this command as console."))
            return true
        }

        if (!sender.hasPermission("tmdonors.donors")) {
            sender.sendMessage(mm.deserialize("${plugin.prefix} This command is donor-only."))
            return true
        }

        if (cooldowns.contains(sender.uniqueId)) {
            sender.sendMessage(mm.deserialize("${plugin.prefix} Please wait a few seconds before using this command again."))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(mm.deserialize("${plugin.prefix} This command changes your website's gradient color. Example: <b>/changegradient red</b>."))
            return true
        }

        if (!availableGradients.contains(args[0])) {
            sender.sendMessage(mm.deserialize("${plugin.prefix} Please use a valid color."))
            return true
        }

        val gradientIndex = availableGradients.indexOf(args[0]) + 1
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.updateGradient(sender.uniqueId.toString(), gradientIndex)
        })
        sender.sendMessage(mm.deserialize("${plugin.prefix} Website gradient changed to <b>${args[0]}</b>."))
        cooldowns.add(sender.uniqueId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            cooldowns.remove(sender.uniqueId)
        }, 100L)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        return if (args.size == 1 && sender.hasPermission("tmdonors.donors")){
            availableGradients
        }
        else{
            emptyList()
        }
    }
}