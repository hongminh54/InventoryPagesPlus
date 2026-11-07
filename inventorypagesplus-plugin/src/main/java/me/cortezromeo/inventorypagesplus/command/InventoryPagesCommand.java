package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeOtherItemsInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.PlayerInventoryFile;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeCreative;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeEnderChest;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeMain;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeOtherItems;
import me.cortezromeo.inventorypagesplus.language.English;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.language.Vietnamese;
import me.cortezromeo.inventorypagesplus.manager.AutoSaveManager;
import me.cortezromeo.inventorypagesplus.manager.BackupManager;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryPagesCommand implements CommandExecutor, TabExecutor {
    public InventoryPagesCommand() {
        InventoryPagesPlus.plugin.getCommand("inventorypagesplus").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded InventoryPageRecoded.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            if (!sender.hasPermission("inventorypagesplus.admin")) {
                MessageUtil.sendMessage(sender, Messages.NO_PERMISSION);
                return false;
            }
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                long time = System.currentTimeMillis();
                InventoryPagesPlus.plugin.reloadConfig();
                Settings.setupValue();
                English.reload();
                Vietnamese.reload();
                Messages.setupValue(Settings.LOCALE);
                PlayerInventoryFile.reload();
                InvseeInventoryFile.reload();
                InvseeOtherItemsInventoryFile.reload();
                PlayerPageInventory.setupItems();
                InventorySeeMain.setupItems();
                InventorySeeOtherItems.setupItems();
                InventorySeeCreative.setupItems();
                InventorySeeEnderChest.setupItems();
                // Rebuild the cached inventories so online players get the new page limits and switcher items.
                // Saving first keeps every page, the reload then behaves exactly like a rejoin.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().containsKey(player.getUniqueId().toString())) {
                        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
                        InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(player.getName());
                    }
                }
                if (AutoSaveManager.isAutoSaveEnabled() && !Settings.AUTO_SAVE_ENABLED)
                    AutoSaveManager.stopAutoSave();
                else
                    AutoSaveManager.startAutoSave(Settings.AUTO_SAVE_SECONDS);
                AutoSaveManager.reloadTimeAutoSave();
                DebugManager.debug("RELOADING PLUGIN", "Reloaded plugin. &b&l(" + (System.currentTimeMillis() - time) + "ms)");
                MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_RELOAD);
                return false;
            }
            if (args[0].equalsIgnoreCase("backup")) {
                MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_BACKUP_START);
                Bukkit.getScheduler().runTaskAsynchronously(InventoryPagesPlus.plugin, () -> {
                    BackupManager backupManager = new BackupManager();
                    backupManager.backupAll();
                    MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_BACKUP_COMPLETE
                            .replace("%databaseType%", InventoryPagesPlus.databaseType.toString().toUpperCase())
                            .replace("%fileName%", backupManager.getBackupFileName()));
                });
                return false;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setmaxpage") || args[0].equalsIgnoreCase("addmaxpage") || args[0].equalsIgnoreCase("removemaxpage")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    MessageUtil.sendMessage(sender, Messages.INVALID_NAME.replace("%player%", args[1]));
                    return false;
                }

                int maxPage;
                try {
                    maxPage = Integer.parseInt(args[2]);
                } catch (Exception exception) {
                    MessageUtil.sendMessage(sender, Messages.INVALID_NUMBER);
                    return false;
                }

                if (args[0].equalsIgnoreCase("setmaxpage")) {
                    InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getUniqueId()).setMaxPage(maxPage);
                    MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_SET_MAX_PAGE
                            .replace("%player%", args[1])
                            .replace("%number%", args[2]));
                    return false;
                }

                if (args[0].equalsIgnoreCase("addmaxpage")) {
                    InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getUniqueId()).addMaxPage(maxPage);
                    MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_ADD_MAX_PAGE
                            .replace("%player%", args[1])
                            .replace("%number%", args[2]));
                    return false;
                }

                if (args[0].equalsIgnoreCase("removemaxpage")) {
                    InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getUniqueId()).removeMaxPage(maxPage);
                    MessageUtil.sendMessage(sender, Messages.COMMAND_INVENTORYPAGESPLUS_REMOVE_MAX_PAGE
                            .replace("%player%", args[1])
                            .replace("%number%", args[2]));
                    return false;
                }
            }
        }
        for (String message : Messages.COMMAND_INVENTORYPAGESPLUS_MESSAGES) {
            message = message.replace("%version%", InventoryPagesPlus.plugin.getDescription().getVersion());
            MessageUtil.sendMessage(sender, message);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (sender.hasPermission("inventorypagesplus.admin")) {
            if (args.length == 1) {
                commands.add(0, "reload");
                commands.add(1, "setmaxpage");
                commands.add(2, "addmaxpage");
                commands.add(3, "removemaxpage");
                commands.add(4, "backup");
                StringUtil.copyPartialMatches(args[0], commands, completions);
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("setmaxpage") || args[0].equalsIgnoreCase("addmaxpage") || args[0].equalsIgnoreCase("removemaxpage")) {
                    if (!Bukkit.getOnlinePlayers().isEmpty())
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            commands.add(player.getName());
                        }
                }
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
