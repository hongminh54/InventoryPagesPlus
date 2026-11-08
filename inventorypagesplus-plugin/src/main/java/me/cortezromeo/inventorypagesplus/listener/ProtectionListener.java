package me.cortezromeo.inventorypagesplus.listener;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.ProtectionManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Freezes a locked player's interactions and blocks blacklisted commands.
 * Runs at LOWEST so the existing HIGHEST listeners (pickup/click) skip via ignoreCancelled.
 * Only affects LOCKED players; NO_PIN and bypass players are untouched.
 */
public class ProtectionListener implements Listener {

    public ProtectionListener() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded ProtectionListener.");
    }

    private boolean isLocked(Player player) {
        PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getUniqueId());
        return data != null && data.isProtectionLocked();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isLocked(event.getPlayer()))
            return;

        String message = event.getMessage();
        if (message.length() < 2)
            return;

        String command = ProtectionManager.normalizeCommand(message.substring(1).trim().split("\\s+")[0]);

        // The unlock command must always pass, otherwise a locked player could never unlock.
        if (command.equals("baove") || command.equals("pageprotect"))
            return;

        if (Settings.PROTECTION_COMMAND_BLACKLIST.contains(command)) {
            event.setCancelled(true);
            MessageUtil.sendMessage(event.getPlayer(), Messages.COMMAND_PROTECTION_COMMAND_BLOCKED.replace("%command%", command));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && isLocked((Player) event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isLocked(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isLocked(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && isLocked((Player) event.getWhoClicked()))
            event.setCancelled(true);
    }

    // Creative clicks use a separate HandlerList, so they need their own handler to stay frozen.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player && isLocked((Player) event.getWhoClicked()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isLocked(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Allow the player's own inventory (view only, clicks are frozen); block external containers.
        if (event.getInventory().getType() == InventoryType.CRAFTING)
            return;
        if (event.getPlayer() instanceof Player && isLocked((Player) event.getPlayer()))
            event.setCancelled(true);
    }
}