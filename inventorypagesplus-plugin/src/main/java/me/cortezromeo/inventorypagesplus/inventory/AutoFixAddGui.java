package me.cortezromeo.inventorypagesplus.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixAddInventoryFile;
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

import java.util.List;

public class AutoFixAddGui extends CustomInventoryBase implements Listener {

    private final FileConfiguration config;
    private final String title;
    private final int size;
    private final List<Integer> oldSlots;
    private final List<Integer> newSlots;
    private final int confirmSlot;
    private final List<Integer> decorSlots;

    public AutoFixAddGui(Player owner) {
        super(owner);
        config = AutoFixAddInventoryFile.get();
        title = InventoryPagesPlus.nms.addColor(config.getString("title", "Fix GUI"));
        size = config.getInt("size", 27);
        oldSlots = config.getIntegerList("old-slots");
        newSlots = config.getIntegerList("new-slots");
        confirmSlot = config.getInt("confirm-slot", 17);
        decorSlots = config.getIntegerList("decor-slots");
    }

    @Override
    public void open() {
        super.open();
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
        inventory.setItem(confirmSlot, guiItem("confirm"));
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        if (!inventory.equals(event.getClickedInventory()))
            return;
        int slot = event.getSlot();
        if (oldSlots.contains(slot) || newSlots.contains(slot))
            return;
        event.setCancelled(true);
        if (slot == confirmSlot)
            confirm();
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
            if (rawSlot < inventory.getSize() && !oldSlots.contains(rawSlot) && !newSlots.contains(rawSlot)) {
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
        // Unconfirmed template items belong to the admin, give them back.
        for (int slot : oldSlots) {
            ItemUtil.giveOrDrop(owner, inventory.getItem(slot));
            inventory.setItem(slot, null);
        }
        for (int slot : newSlots) {
            ItemUtil.giveOrDrop(owner, inventory.getItem(slot));
            inventory.setItem(slot, null);
        }
    }

    private void confirm() {
        int pairs = Math.min(oldSlots.size(), newSlots.size());
        for (int i = 0; i < pairs; i++) {
            ItemStack oldItem = inventory.getItem(oldSlots.get(i));
            ItemStack newItem = inventory.getItem(newSlots.get(i));
            boolean hasOld = oldItem != null && oldItem.getType() != Material.AIR;
            boolean hasNew = newItem != null && newItem.getType() != Material.AIR;
            if (!hasOld && !hasNew)
                continue;
            if (!hasOld || !hasValidName(oldItem)) {
                MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_INVALID_OLD_PAIR.replace("%pair%", String.valueOf(i + 1)));
                return;
            }
            if (!hasNew) {
                MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_INVALID_NEW_PAIR.replace("%pair%", String.valueOf(i + 1)));
                return;
            }
        }

        int added = 0;
        for (int i = 0; i < pairs; i++) {
            ItemStack oldItem = inventory.getItem(oldSlots.get(i));
            ItemStack newItem = inventory.getItem(newSlots.get(i));
            if (oldItem == null || oldItem.getType() == Material.AIR || newItem == null || newItem.getType() == Material.AIR)
                continue;
            AutoFixManager.addFix(oldItem, newItem);
            // The rule is stored as a serialized blob, hand the physical items back to the admin.
            ItemUtil.giveOrDrop(owner, oldItem);
            ItemUtil.giveOrDrop(owner, newItem);
            inventory.setItem(oldSlots.get(i), null);
            inventory.setItem(newSlots.get(i), null);
            added++;
        }
        if (added == 0) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULE_INVALID_OLD);
            return;
        }
        MessageUtil.sendMessage(owner, Messages.COMMAND_AUTOFIX_RULES_ADDED.replace("%amount%", String.valueOf(added)));
        AutoFixManager.tryFixAllOnline();
        owner.closeInventory();
    }

    private boolean hasValidName(ItemStack item) {
        if (!item.hasItemMeta())
            return false;
        ItemMeta itemMeta = item.getItemMeta();
        return itemMeta != null && itemMeta.hasDisplayName();
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