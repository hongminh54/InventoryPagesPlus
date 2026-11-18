package me.cortezromeo.inventorypagesplus.listener;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.inventory.CustomInventoryBase;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.AutoFixManager;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class AutoFixListener implements Listener {

    public AutoFixListener() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded AutoFix PlayerJoinEvent.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!AutoFixManager.isEnable())
            return;
        Player player = event.getPlayer();
        // Wait a few ticks so the page database is fully loaded before scanning.
        Bukkit.getScheduler().runTaskLater(InventoryPagesPlus.plugin, () -> {
            if (player.isOnline())
                AutoFixManager.tryFix(player);
        }, 2L);
    }

    // Fix old items the moment they are clicked, like ItemProtecter did.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!AutoFixManager.isEnable())
            return;
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (AutoFixManager.isBypassed(player))
            return;
        // Plugin GUIs manage their own items, never touch them here.
        if (event.getView().getTopInventory().getHolder() instanceof CustomInventoryBase)
            return;
        ItemStack current = event.getCurrentItem();
        if (!AutoFixManager.isItemFix(current))
            return;
        ItemStack fixed = AutoFixManager.fix(current);
        if (fixed == null)
            return;
        String itemName = current.getItemMeta() != null && current.getItemMeta().hasDisplayName()
                ? current.getItemMeta().getDisplayName() : current.getType().name();
        event.setCurrentItem(fixed);
        MessageUtil.sendMessage(player, Messages.COMMAND_AUTOFIX_ITEM_UPDATED.replace("%item%", itemName));
    }
}