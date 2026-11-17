package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.history.SnapshotReason;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ClearCommand implements CommandExecutor, TabExecutor {
    public ClearCommand() {
        InventoryPagesPlus.plugin.getCommand("clear").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded ClearCommand.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player) {
                if (!sender.hasPermission("inventorypagesplus.clear.others") && !sender.hasPermission("inventorypagesplus.admin")) {
                    MessageUtil.sendMessage(((Player) sender), Messages.NO_PERMISSION);
                    return false;
                }
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().containsKey(target.getUniqueId().toString())) {
                    PlayerInventoryDatabase playerInventoryData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getUniqueId());
                    if (Settings.HISTORY_ENABLED && Settings.HISTORY_SNAPSHOT_BEFORE_CLEAR)
                        InventoryPagesPlus.getHistoryManager().capture(target, SnapshotReason.BEFORE_CLEAR, true);
                    playerInventoryData.clearPage(target.getGameMode());
                    clearHotbar(target);
                    playerInventoryData.showPage(target.getGameMode());
                    MessageUtil.sendMessage(sender, Messages.COMMAND_CLEAR_CLEAR_TARGET.replace("%player%", targetName));
                    MessageUtil.sendMessage(target, Messages.COMMAND_CLEAR_CLEAR_TARGETS_MESSAGE.replace("%player%", sender.getName()));
                    return false;
                }
            } else {
                MessageUtil.sendMessage(sender, Messages.GET_PLAYER_DATA.replace("%player%", targetName));
                if (InventoryPagesPlus.getDatabaseManager().getTempPlayerUUID().containsKey(targetName)) {
                    InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(targetName);
                    PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(targetName);
                    if (data != null) {
                        if (Settings.HISTORY_ENABLED && Settings.HISTORY_SNAPSHOT_BEFORE_CLEAR)
                            InventoryPagesPlus.getHistoryManager().capture(data, SnapshotReason.BEFORE_CLEAR, true);
                        data.clearPage(GameMode.SURVIVAL);
                        if (!PlayerInventoryDataStorage.savePlayerInventoryDataChecked(data))
                            InventoryPagesPlus.plugin.getLogger().warning("Could not persist the offline inventory clear for " + targetName + ".");
                    }
                    MessageUtil.sendMessage(sender, Messages.COMMAND_CLEAR_CLEAR_TARGET.replace("%player%", targetName));
                } else {
                    Bukkit.getScheduler().runTaskAsynchronously(InventoryPagesPlus.plugin, () -> {
                        String targetUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(targetName, false);
                        PlayerInventoryDatabase detached = targetUUID == null ? null
                                : PlayerInventoryDataStorage.loadOfflinePlayerInventory(targetName, targetUUID);
                        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
                            if (targetUUID == null) {
                                MessageUtil.sendMessage(sender, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", targetName));
                                return;
                            }
                            UUID uuid = UUID.fromString(targetUUID);
                            Player online = Bukkit.getPlayer(uuid);
                            PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(uuid);
                            if (data == null) {
                                if (online != null) {
                                    InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(targetName);
                                    data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(uuid);
                                } else if (detached != null) {
                                    InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().putIfAbsent(targetUUID, detached);
                                    data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(uuid);
                                }
                            }
                            if (data == null)
                                return;
                            if (Settings.HISTORY_ENABLED && Settings.HISTORY_SNAPSHOT_BEFORE_CLEAR) {
                                if (online != null)
                                    InventoryPagesPlus.getHistoryManager().capture(online, SnapshotReason.BEFORE_CLEAR, true);
                                else
                                    InventoryPagesPlus.getHistoryManager().capture(data, SnapshotReason.BEFORE_CLEAR, true);
                            }
                            data.clearPage(online == null ? GameMode.SURVIVAL : online.getGameMode());
                            if (online != null)
                                data.showPage(online.getGameMode());
                            else if (!PlayerInventoryDataStorage.savePlayerInventoryDataChecked(data))
                                InventoryPagesPlus.plugin.getLogger().warning("Could not persist the offline inventory clear for " + targetName + ".");
                            MessageUtil.sendMessage(sender, Messages.COMMAND_CLEAR_CLEAR_TARGET.replace("%player%", targetName));
                        });
                    });
                }
            }
            return false;
        }

        if (sender instanceof Player player) {

            if (!player.hasPermission("inventorypagesplus.clear") && !sender.hasPermission("inventorypagesplus.admin")) {
                MessageUtil.sendMessage(player, Messages.NO_PERMISSION);
                return false;
            }
            String playerUUID = player.getUniqueId().toString();
            GameMode playerGameMode = player.getGameMode();
            PlayerInventoryDatabase playerInventoryData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(UUID.fromString(playerUUID));

            if (playerInventoryData.isProtectionLocked()) {
                MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_LOCKED_REMINDER);
                return false;
            }

            if (Settings.HISTORY_ENABLED && Settings.HISTORY_SNAPSHOT_BEFORE_CLEAR)
                InventoryPagesPlus.getHistoryManager().capture(player, SnapshotReason.BEFORE_CLEAR, true);
            playerInventoryData.clearPage(playerGameMode);
            clearHotbar(player);
            playerInventoryData.showPage(player.getGameMode());
            MessageUtil.sendMessage(player, Messages.COMMAND_CLEAR_CLEAR);
        } else {
            MessageUtil.log(InventoryPagesPlus.plugin.getDescription().getName() + " - Clear commands for console");
            MessageUtil.log("/clear <player>");
            MessageUtil.log("/clearall <player>");
        }
        return false;
    }

    public void clearHotbar(Player player) {
        if (player == null)
            return;
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, null);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("inventorypagesplus.clear.others") || sender.hasPermission("inventorypagesplus.admin"))
                for (Player player : Bukkit.getOnlinePlayers())
                    commands.add(player.getName());
            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}
