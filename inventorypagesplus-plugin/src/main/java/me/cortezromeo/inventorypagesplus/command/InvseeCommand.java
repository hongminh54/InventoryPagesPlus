package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.InvseeManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InvseeCommand implements CommandExecutor {
    public InvseeCommand() {
        InventoryPagesPlus.plugin.getCommand("invsee").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded InvseeCommand.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.log(Messages.NON_CONSOLE_COMMAND);
            return false;
        }

        if (!player.hasPermission("inventorypagesplus.invsee") || !sender.hasPermission("inventorypagesplus.admin")) {
            MessageUtil.sendMessage(player, Messages.NO_PERMISSION);
            return false;
        }

        if (args.length == 1) {
            InvseeManager.openInventorySee(player, args[0], true);
            return false;
        }

        for (String message : Messages.COMMAND_INVSEE_MESSAGES) {
            message = message.replace("%version%", InventoryPagesPlus.plugin.getDescription().getVersion());
            MessageUtil.sendMessage(sender, message);
        }
        return false;
    }
}
