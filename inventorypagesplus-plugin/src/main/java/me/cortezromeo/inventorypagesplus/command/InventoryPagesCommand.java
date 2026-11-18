package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixAddInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixDataFile;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixEditInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.HistoryInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeOtherItemsInventoryFile;
import me.cortezromeo.inventorypagesplus.file.inventory.PlayerInventoryFile;
import me.cortezromeo.inventorypagesplus.inventory.AutoFixGui;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeCreative;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeEnderChest;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeMain;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeOtherItems;
import me.cortezromeo.inventorypagesplus.language.English;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.language.Vietnamese;
import me.cortezromeo.inventorypagesplus.manager.AutoFixManager;
import me.cortezromeo.inventorypagesplus.manager.AutoSaveManager;
import me.cortezromeo.inventorypagesplus.manager.BackupManager;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.InventoryPagesPlusDataManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
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
                AutoFixDataFile.reload();
                AutoFixManager.reloadRules();
                AutoFixInventoryFile.reload();
                AutoFixAddInventoryFile.reload();
                AutoFixEditInventoryFile.reload();
                HistoryInventoryFile.reload();
                if (InventoryPagesPlus.getHistoryManager() != null)
                    InventoryPagesPlus.getHistoryManager().reload();
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
                    InventoryPagesPlusDataManager databaseManager = InventoryPagesPlus.getDatabaseManager();
                    if (databaseManager.getPlayerInventoryDatabase().containsKey(player.getUniqueId().toString())) {
                        databaseManager.savePlayerInventory(player.getName());
                        PlayerInventoryDatabase data = databaseManager.getPlayerInventoryDatabase(player.getUniqueId());
                        // loadPlayerInventory re-absorbs slots 9..35 as crash leftovers. On reload those slots
                        // still hold the page just saved, so clear them first (like quit does) or it duplicates.
                        if (!data.isProtectionLocked())
                            data.clearDisplayedPage();
                        databaseManager.loadPlayerInventory(player.getName());
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

        if (args.length >= 1 && args[0].equalsIgnoreCase("autofix")) {
            if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
                    return false;
                }
                new AutoFixGui((Player) sender).open();
                return false;
            }
            if (args[1].equalsIgnoreCase("reload")) {
                InventoryPagesPlus.plugin.reloadConfig();
                Settings.setupValue();
                Messages.setupValue(Settings.LOCALE);
                AutoFixDataFile.reload();
                AutoFixManager.reloadRules();
                AutoFixInventoryFile.reload();
                AutoFixAddInventoryFile.reload();
                AutoFixEditInventoryFile.reload();
                MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_RELOADED);
                return false;
            }
            if (args[1].equalsIgnoreCase("toggle")) {
                boolean enabled = !Settings.AUTOFIX_ENABLED;
                Settings.AUTOFIX_ENABLED = enabled;
                InventoryPagesPlus.plugin.getConfig().set("autofix.enabled", enabled);
                InventoryPagesPlus.plugin.saveConfig();
                MessageUtil.sendMessage(sender, enabled ? Messages.COMMAND_AUTOFIX_TOGGLED_ON : Messages.COMMAND_AUTOFIX_TOGGLED_OFF);
                return false;
            }
            if (args[1].equalsIgnoreCase("force")) {
                if (!Settings.AUTOFIX_ENABLED) {
                    MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_DISABLED);
                    return false;
                }
                if (args.length == 2) {
                    AutoFixManager.forceFixAll(sender);
                    return false;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    MessageUtil.sendMessage(sender, Messages.INVALID_NAME.replace("%player%", args[2]));
                    return false;
                }
                int count = AutoFixManager.forceFix(target);
                MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_FORCE_DONE
                        .replace("%player%", target.getName())
                        .replace("%amount%", String.valueOf(count)));
                if (count > 0)
                    MessageUtil.sendMessage(target, Messages.COMMAND_AUTOFIX_COMPLETED.replace("%amount%", String.valueOf(count)));
                return false;
            }
            if (args[1].equalsIgnoreCase("bypass")) {
                Player target;
                if (args.length == 2) {
                    if (!(sender instanceof Player)) {
                        MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
                        return false;
                    }
                    target = (Player) sender;
                } else {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        MessageUtil.sendMessage(sender, Messages.INVALID_NAME.replace("%player%", args[2]));
                        return false;
                    }
                }
                boolean bypassed = AutoFixManager.toggleBypass(target);
                MessageUtil.sendMessage(target, bypassed ? Messages.COMMAND_AUTOFIX_BYPASS_ON : Messages.COMMAND_AUTOFIX_BYPASS_OFF);
                if (!target.equals(sender))
                    MessageUtil.sendMessage(sender, (bypassed ? Messages.COMMAND_AUTOFIX_BYPASS_ON_OTHER : Messages.COMMAND_AUTOFIX_BYPASS_OFF_OTHER)
                            .replace("%player%", target.getName()));
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
                commands.add(5, "autofix");
                StringUtil.copyPartialMatches(args[0], commands, completions);
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("setmaxpage") || args[0].equalsIgnoreCase("addmaxpage") || args[0].equalsIgnoreCase("removemaxpage")) {
                    if (!Bukkit.getOnlinePlayers().isEmpty())
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            commands.add(player.getName());
                        }
                } else if (args[0].equalsIgnoreCase("autofix")) {
                    commands.add("reload");
                    commands.add("toggle");
                    commands.add("force");
                    commands.add("bypass");
                }
                StringUtil.copyPartialMatches(args[1], commands, completions);
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("autofix") && (args[1].equalsIgnoreCase("force") || args[1].equalsIgnoreCase("bypass"))) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        commands.add(player.getName());
                    }
                }
                StringUtil.copyPartialMatches(args[2], commands, completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
