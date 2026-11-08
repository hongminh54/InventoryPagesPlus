package me.cortezromeo.inventorypagesplus.listener;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    public PlayerQuitListener() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded PlayerQuitEvent.");
    }

    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerUUID = player.getUniqueId().toString();

        if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase().containsKey(playerUUID)) {
            PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getUniqueId());
            boolean wasLocked = data.isProtectionLocked();

            InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());

            // While locked, slots 9..35 hold the player's real items; clearing them would delete real items.
            if (!wasLocked)
                data.clearDisplayedPage();

            // An unlock must never survive a relog, even if this object stays cached in the map.
            data.setProtectionUnlocked(false);
        }
    }
}
