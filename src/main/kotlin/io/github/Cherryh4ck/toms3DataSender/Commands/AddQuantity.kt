package io.github.Cherryh4ck.toms3DataSender.Commands

import io.github.Cherryh4ck.toms3DataSender.Toms3DataSender
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AddQuantity (private val plugin : Toms3DataSender) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            return true
        }
        if (args.size < 2) {
            return true
        }

        val player = args[0]
        val quantity = args[1].toBigDecimalOrNull() ?: return true
        plugin.addQuantity(player, quantity)
        plugin.logger.info("Added $quantity to $player !!!")

        return true
    }
}