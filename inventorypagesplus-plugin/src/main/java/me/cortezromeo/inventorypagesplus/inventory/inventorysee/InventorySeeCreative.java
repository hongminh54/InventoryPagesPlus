package me.cortezromeo.inventorypagesplus.inventory.inventorysee;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeCreativeInventoryFile;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.UUID;

public class InventorySeeCreative extends InventorySee {

    public static ItemStack borderItem, closeItem, otherItemsInventoryItem, infoItem;
    public static int closeItemSlot, otherItemsInventoryItemSlot, infoItemSlot;
    private BukkitTask bukkitRunnable;

    public InventorySeeCreative(Player owner, String targetName, String targetUUID, int page) {
        super(owner);
        super.targetName = targetName;
        super.targetUUID = targetUUID;
        super.page = page;
    }

    public static void setupItems() {
        FileConfiguration invseeCreativeFile = InvseeCreativeInventoryFile.get();

        borderItem = ItemUtil.getItem(invseeCreativeFile.getString("items.border.type"),
                invseeCreativeFile.getString("items.border.value"),
                (short) invseeCreativeFile.getInt("items.border.data"),
                invseeCreativeFile.getInt("items.border.customModelData"),
                invseeCreativeFile.getString("items.border.name"),
                invseeCreativeFile.getStringList("items.border.lore"));

        closeItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeCreativeFile.getString("items.close.type"),
                invseeCreativeFile.getString("items.close.value"),
                (short) invseeCreativeFile.getInt("items.close.data"),
                invseeCreativeFile.getInt("items.close.customModelData"),
                invseeCreativeFile.getString("items.close.name"),
                invseeCreativeFile.getStringList("items.close.lore")), invseeCreativeFile.getString("items.close.direct"));
        closeItemSlot = invseeCreativeFile.getInt("items.close.slot");

        otherItemsInventoryItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeCreativeFile.getString("items.otherItemsInventory.type"),
                invseeCreativeFile.getString("items.otherItemsInventory.value"),
                (short) invseeCreativeFile.getInt("items.otherItemsInventory.data"),
                invseeCreativeFile.getInt("items.otherItemsInventory.customModelData"),
                invseeCreativeFile.getString("items.otherItemsInventory.name"),
                invseeCreativeFile.getStringList("items.otherItemsInventory.lore")), invseeCreativeFile.getString("items.otherItemsInventory.direct"));
        otherItemsInventoryItemSlot = invseeCreativeFile.getInt("items.otherItemsInventory.slot");

        infoItem = ItemUtil.getItem(invseeCreativeFile.getString("items.info.type"),
                invseeCreativeFile.getString("items.info.value"),
                (short) invseeCreativeFile.getInt("items.info.data"),
                invseeCreativeFile.getInt("items.info.customModelData"),
                invseeCreativeFile.getString("items.info.name"),
                invseeCreativeFile.getStringList("items.info.lore"));
        infoItemSlot = invseeCreativeFile.getInt("items.info.slot");

        DebugManager.debug("LOADING INVENTORIES (InventorySeeCreative)", "Completed with no issues.");
    }

    @Override
    public void open() {
        if (super.inventory == null || !getInventory().getViewers().contains(getOwner())) {
            super.open();
            addTargetItems();
        } else {
            addTargetItems();
            //setMenuItems();
            getOwner().updateInventory();
        }

        if (bukkitRunnable == null) {
            bukkitRunnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (getInventory().getViewers().isEmpty() || !getOwner().getOpenInventory().getTopInventory().equals(getInventory())) {
                        cancel();
                        return;
                    }
                    open();
                }
            }.runTaskTimerAsynchronously(InventoryPagesPlus.nms.getPlugin(), 20, 20);
        }
    }

    @Override
    public String getMenuTitle() {
        return InventoryPagesPlus.nms.addColor(InvseeCreativeInventoryFile.get().getString("title")
                .replace("%player%", getTargetInventoryDatabase().getPlayerName()));
    }

    @Override
    public int getSlots() {
        return 5 * 9;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        if (event.getClickedInventory() == null)
            return;
        if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
            PlayerPageInventory.handleEvent(event);
            return;
        }

        // cancel click event
        event.setCancelled(true);
        super.handleMenu(event);
    }

    @Override
    public void setMenuItems() {
        for (int border = 27; border < 36; border++)
            inventory.setItem(border, borderItem);
        inventory.setItem(otherItemsInventoryItemSlot, addPlaceholders(otherItemsInventoryItem));
        inventory.setItem(infoItemSlot, addPlaceholders(infoItem));
        inventory.setItem(closeItemSlot, addPlaceholders(closeItem));
    }

    private void addTargetItems() {
        InventoryPagesPlus.getDatabaseManager().saveCurrentPage(UUID.fromString(getTargetInventoryDatabase().getPlayerUUID()));
        ArrayList<ItemStack> creativeItems = getTargetInventoryDatabase().getCreativeItems();
        for (int itemSlot = 0; itemSlot < creativeItems.size(); itemSlot++) {
            ItemStack itemStack = creativeItems.get(itemSlot);
            inventory.setItem(itemSlot, itemStack);
        }
/*        Player target = Bukkit.getPlayer(getTargetName());
        if (target != null) {
            for (int itemSlot = 0; itemSlot < target.getEnderChest().getSize(); itemSlot++) {
                ItemStack itemStack = target.getEnderChest().getItem(itemSlot);
                inventory.setItem(itemSlot, itemStack);
            }
        }*/
    }
}
