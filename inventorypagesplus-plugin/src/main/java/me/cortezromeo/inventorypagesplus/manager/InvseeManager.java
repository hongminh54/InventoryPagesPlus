package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.inventory.inventorysee.InventorySeeMain;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;

public class InvseeManager {

    private static final HashMap<Player, String> invseeOfflineTargetUUIDQueue = new HashMap<>();

    public static void openInventorySee(Player player, String targetName, boolean editMode) {
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            new InventorySeeMain(player, targetName, target.getUniqueId().toString(), 0).open();
        } else {
            if (player.hasPermission("inventorypagesplus.invsee.offline") && player.hasPermission("inventorypagesplus.admin")) {
                MessageUtil.sendMessage(player, Messages.GET_PLAYER_DATA.replace("%player%", targetName));

                if (InventoryPagesPlus.getDatabaseManager().getTempPlayerUUID().containsKey(targetName)) {
                    InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(targetName);
                    new InventorySeeMain(player, targetName, InventoryPagesPlus.getDatabaseManager().getTempPlayerUUID().get(targetName), 0).open();
                } else {
                    Bukkit.getScheduler().runTaskAsynchronously(InventoryPagesPlus.plugin, () -> {
                        invseeOfflineTargetUUIDQueue.put(player, "/.");
                        String UUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(targetName, false);
                        if (UUID == null) {
                            MessageUtil.sendMessage(player, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", targetName));
                            invseeOfflineTargetUUIDQueue.remove(player);
                            return;
                        }
                        if (!InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().containsKey(UUID)) {
                            InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(targetName);
                            invseeOfflineTargetUUIDQueue.put(player, UUID);
                        }
                    });
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!invseeOfflineTargetUUIDQueue.containsKey(player)) {
                                cancel();
                            } else {
                                if (!invseeOfflineTargetUUIDQueue.get(player).equals("/.")) {
                                    new InventorySeeMain(player, targetName, invseeOfflineTargetUUIDQueue.get(player), 0).open();
                                    invseeOfflineTargetUUIDQueue.remove(player);
                                    cancel();
                                }
                            }
                        }
                    }.runTaskTimer(InventoryPagesPlus.plugin, 10, 20);
                }
            } else {
                MessageUtil.sendMessage(player, Messages.COMMAND_INVSEE_NO_OFFLINE_INVSEE_PERMISSION);
            }
        }
    }
}
