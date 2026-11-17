package me.cortezromeo.inventorypagesplus.listener;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.inventory.CustomInventoryBase;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class InventoryClickListener implements Listener {
    public InventoryClickListener() {
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded InventoryClickEvent.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CustomInventoryBase custom && custom.isReadOnly()) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            if (!(event instanceof InventoryCreativeEvent))
                custom.handleMenu(event);
            return;
        }

        // Preserve the legacy path, including its handling of cancelled events.
        if (event.isCancelled() || event.getClickedInventory() == null)
            return;

        if (holder instanceof CustomInventoryBase) {
            ((CustomInventoryBase) holder).handleMenu(event);
            return;
        }

        PlayerPageInventory.handleEvent(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CustomInventoryBase custom && custom.isReadOnly()) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof CustomInventoryBase custom && custom.isReadOnly()) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

}
