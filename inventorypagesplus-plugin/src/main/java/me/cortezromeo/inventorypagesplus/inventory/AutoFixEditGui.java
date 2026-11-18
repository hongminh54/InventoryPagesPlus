package me.cortezromeo.inventorypagesplus.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixEditInventoryFile;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.AutoFixManager;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AutoFixEditGui extends CustomInventoryBase implements Listener {

    private final FileConfiguration config;
    private final String title;
    private final int size;
    private final int oldSlot;
    private final int newSlot;
    private final int previewSlot;
    private final int confirmSlot;
    private final int deleteSlot;
    private final List<Integer> decorSlots;
    private final List<Integer> separatorSlots;
    private final String key;

    public AutoFixEditGui(Player owner, String key) {
        super(owner);
        this.key = key;
        config = AutoFixEditInventoryFile.get();
        title = InventoryPagesPlus.nms.addColor(config.getString("title", "Item Fix"));
        size = config.getInt("size", 27);
        oldSlot = config.getInt("old-slot", 10);
        newSlot = config.getInt("new-slot", 16);
        previewSlot = config.getInt("preview-slot", 13);
        confirmSlot = config.getInt("confirm-slot", 17);
        deleteSlot = config.getInt("delete-slot", 26);
        decorSlots = config.getIntegerList("decor-slots");
        separatorSlots = config.getIntegerList("separator-slots");
    }

    @Override
    public void open() {
        super.open();
        inventory.setItem(oldSlot, AutoFixManager.getOldItem(key));
        inventory.setItem(newSlot, AutoFixManager.getNewItem(key));
        updatePreview();
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
    }

    @Override
    public String getMenuTitle() {
        return title;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public void setMenuItems() {
        for (int slot : decorSlots)
            inventory.setItem(slot, guiItem("decor"));
        for (int slot : separatorSlots)
            inventory.setItem(slot, guiItem("separator"));
        inventory.setItem(confirmSlot, guiItem("confirm"));
        inventory.setItem(deleteSlot, guiItem("delete"));
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory()))
            return;
        int slot = event.getSlot();
        if (slot == oldSlot || slot == newSlot) {
            // The click mutates the slot after the event, refresh the preview one tick later.
            Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, this::updatePreview);
            return;
        }
        event.setCancelled(true);
        if (slot == confirmSlot)
            confirm();
        else if (slot == deleteSlot)
            delete();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTopInventory().equals(inventory))
            return;
        // Double-click collect-to-cursor could suck the GUI buttons out of their slots.
        if (event.getClick() == ClickType.DOUBLE_CLICK)
            event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!event.getView().getTopInventory().equals(inventory))
            return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < inventory.getSize() && rawSlot != oldSlot && rawSlot != newSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory))
            return;
        HandlerList.unregisterAll(this);
        // Closing without confirming keeps the stored rule unchanged, hand the editor items back.
        ItemUtil.giveOrDrop(owner, inventory.getItem(oldSlot));
        ItemUtil.giveOrDrop(owner, inventory.getItem(newSlot));
        inventory.setItem(oldSlot, null);
        inventory.setItem(newSlot, null);
    }

    private void updatePreview() {
        ItemStack oldItem = inventory.getItem(oldSlot);
        ItemStack newItem = inventory.getItem(newSlot);
        if (oldItem == null || oldItem.getType() == Material.AIR || newItem == null || newItem.getType() == Material.AIR) {
            inventory.setItem(previewSlot, null);
            return;
        }
        ItemStack preview = AutoFixManager.buildFixedItem(oldItem, newItem);
        if (preview == null) {
            inventory.setItem(previewSlot, null);
            return;
        }
        ItemMeta itemMeta = preview.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.hasLore() ? new ArrayList<>(itemMeta.getLore()) : new ArrayList<>();
            for (String line : config.getStringList("preview-lore"))
                lore.add(InventoryPagesPlus.nms.addColor(line));
            itemMeta.setLore(lore);
            preview.setItemMeta(itemMeta);
        }
        inventory.setItem(previewSlot, preview);
    }

    private void confirm() {
        ItemStack oldItem = inventory.getItem(oldSlot);
        ItemStack newItem = inventory.getItem(newSlot);
        if (oldItem == null || oldItem.getType() == Material.AIR || !oldItem.hasItemMeta()
                || oldItem.getItemMeta() == null || !oldItem.getItemMeta().hasDisplayName()) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_INVALID_OLD);
            return;
        }
        if (newItem == null || newItem.getType() == Material.AIR) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_INVALID_NEW);
            return;
        }
        String newKey = AutoFixManager.addFix(oldItem, newItem);
        // Renaming or swapping the old item changes the rule key, drop the stale rule.
        if (!newKey.equals(key))
            AutoFixManager.removeFix(key);
        inventory.setItem(oldSlot, null);
        inventory.setItem(newSlot, null);
        MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_ADDED.replace("%key%", newKey));
        AutoFixManager.tryFixAllOnline();
        backToList();
    }

    private void delete() {
        AutoFixManager.removeFix(key);
        inventory.setItem(oldSlot, null);
        inventory.setItem(newSlot, null);
        MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_DELETED.replace("%key%", key));
        backToList();
    }

    private void backToList() {
        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> new AutoFixGui(owner).open());
    }

    private ItemStack guiItem(String key) {
        String path = "items." + key;
        return ItemUtil.getItem(config.getString(path + ".type"),
                config.getString(path + ".value"),
                (short) config.getInt(path + ".data"),
                config.getInt(path + ".customModelData"),
                config.getString(path + ".name"),
                config.getStringList(path + ".lore"));
    }
}