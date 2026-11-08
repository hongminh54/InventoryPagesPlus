package me.cortezromeo.inventorypagesplus.listener;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class EntityPickupListener implements Listener {
    public EntityPickupListener() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded EntityPickupItemEvent.");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();

            if (InventoryPagesPlus.nms.getCustomData(item).equals(PlayerPageInventory.itemCustomData)) {
                event.setCancelled(true);
                event.getItem().remove();
            }

            if (Settings.ADVANCED_PICK_UP_SETTINGS_ENABLED) {
                UUID playerUUID = player.getUniqueId();
                if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().containsKey(playerUUID.toString())) {
                    PlayerInventoryDatabase playerInventoryData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(playerUUID);
                    ItemStack itemStack = event.getItem().getItemStack();
                    if (!playerInventoryData.storeOrDropItem(itemStack, player.getGameMode())) {
                        event.setCancelled(true);
                        event.getItem().remove();
                        if (Settings.ADVANCED_PICK_UP_SETTINGS_SOUND_ENABLED)
                            player.playSound(player.getLocation(), InventoryPagesPlus.nms.createSound(Settings.ADVANCED_PICK_UP_SETTINGS_SOUND_NAME), (float) Settings.ADVANCED_PICK_UP_SETTINGS_SOUND_VOLUME, (float) Settings.ADVANCED_PICK_UP_SETTINGS_SOUND_PITCH);
                    }
                }
            }
        }
    }
}
