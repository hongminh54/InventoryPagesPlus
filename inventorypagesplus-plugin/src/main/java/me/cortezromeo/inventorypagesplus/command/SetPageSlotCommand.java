package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.PageSlotManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SetPageSlotCommand implements CommandExecutor, TabExecutor {
    public SetPageSlotCommand() {
        InventoryPagesPlus.plugin.getCommand("setpageslot").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded SetPageSlotCommand.");
    }

    // slot is from 0 - 27
    // to get the slot from player's inventory, add 9 (doing this for not including the hotbar)
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {

            if (!player.hasPermission("inventorypagesplus.setpageslot") && !sender.hasPermission("inventorypagesplus.admin")) {
                MessageUtil.sendMessage(player, Messages.NO_PERMISSION);
                return false;
            }

            PlayerInventoryDatabase playerInventoryData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getUniqueId());

            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reset")) {
                    if (PageSlotManager.resetPageSlot(player))
                        MessageUtil.sendMessage(player, Messages.COMMAND_SETPAGESLOT_RESET_PAGE_SLOT);
                    return false;
                }
            }
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("nextpage") || args[0].equalsIgnoreCase("prevpage")) {
                    int slot = 0;
                    try {
                        slot = Integer.parseInt(args[1]);
                    } catch (Exception exception) {
                        MessageUtil.sendMessage(player, Messages.INVALID_NUMBER);
                        return false;
                    }
                    if (args[0].equalsIgnoreCase("nextpage")) {
                        if (PageSlotManager.setNextPageSlot(player, slot))
                            MessageUtil.sendMessage(player, Messages.COMMAND_SETPAGESLOT_SET_NEXT_PAGE.replace("%slotNumber%", String.valueOf(slot)));
                        return false;
                    }
                    if (args[0].equalsIgnoreCase("prevpage")) {
                        if (PageSlotManager.setPrevPageSlot(player, slot))
                            MessageUtil.sendMessage(player, Messages.COMMAND_SETPAGESLOT_SET_PREV_PAGE.replace("%slotNumber%", String.valueOf(slot)));
                        return false;
                    }
                }
            }

            for (String message : Messages.COMMAND_SETPAGESLOT_MESSAGES) {
                message = message.replace("%version%", InventoryPagesPlus.plugin.getDescription().getVersion());
                MessageUtil.sendMessage(sender, message);
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("inventorypagesplus.setpageslot") || sender.hasPermission("inventorypagesplus.admin")) {
                commands.add("reset");
                commands.add("nextpage");
                commands.add("prevpage");
            }
            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}
