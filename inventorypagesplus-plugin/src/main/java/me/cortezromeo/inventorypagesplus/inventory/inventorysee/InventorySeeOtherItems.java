package me.cortezromeo.inventorypagesplus.inventory.inventorysee;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeOtherItemsInventoryFile;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class InventorySeeOtherItems extends InventorySee {

    public static ItemStack borderItem, closeItem, offlinePlayerItem, invseeMainItem, creativeInventoryItem, enderChestItem;
    public static int closeItemSlot, helmetItemSlot, chestItemSlot, legsItemSlot, bootsItemSlot, mainHandItemSlot, secondHandItemSlot, invseeMainItemSlot, creativeInventoryItemSlot, enderChestItemSlot;
    private BukkitTask bukkitRunnable;

    public InventorySeeOtherItems(Player owner, String targetName, String targetUUID, int page) {
        super(owner);
        super.targetName = targetName;
        super.targetUUID = targetUUID;
        super.page = page;
    }

    public static void setupItems() {
        FileConfiguration invseeOtherItemsFile = InvseeOtherItemsInventoryFile.get();

        borderItem = ItemUtil.getItem(invseeOtherItemsFile.getString("items.border.type"),
                invseeOtherItemsFile.getString("items.border.value"),
                (short) invseeOtherItemsFile.getInt("items.border.data"),
                invseeOtherItemsFile.getInt("items.border.customModelData"),
                invseeOtherItemsFile.getString("items.border.name"),
                invseeOtherItemsFile.getStringList("items.border.lore"));

        offlinePlayerItem = ItemUtil.getItem(invseeOtherItemsFile.getString("items.offlinePlayer.type"),
                invseeOtherItemsFile.getString("items.offlinePlayer.value"),
                (short) invseeOtherItemsFile.getInt("items.offlinePlayer.data"),
                invseeOtherItemsFile.getInt("items.offlinePlayer.customModelData"),
                invseeOtherItemsFile.getString("items.offlinePlayer.name"),
                invseeOtherItemsFile.getStringList("items.offlinePlayer.lore"));

        closeItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeOtherItemsFile.getString("items.close.type"),
                invseeOtherItemsFile.getString("items.close.value"),
                (short) invseeOtherItemsFile.getInt("items.close.data"),
                invseeOtherItemsFile.getInt("items.close.customModelData"),
                invseeOtherItemsFile.getString("items.close.name"),
                invseeOtherItemsFile.getStringList("items.close.lore")), invseeOtherItemsFile.getString("items.close.direct"));
        closeItemSlot = invseeOtherItemsFile.getInt("items.close.slot");

        invseeMainItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeOtherItemsFile.getString("items.invseeMainInventory.type"),
                invseeOtherItemsFile.getString("items.invseeMainInventory.value"),
                (short) invseeOtherItemsFile.getInt("items.invseeMainInventory.data"),
                invseeOtherItemsFile.getInt("items.invseeMainInventory.customModelData"),
                invseeOtherItemsFile.getString("items.invseeMainInventory.name"),
                invseeOtherItemsFile.getStringList("items.invseeMainInventory.lore")), invseeOtherItemsFile.getString("items.invseeMainInventory.direct"));
        invseeMainItemSlot = invseeOtherItemsFile.getInt("items.invseeMainInventory.slot");

        creativeInventoryItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeOtherItemsFile.getString("items.creativeInventory.type"),
                invseeOtherItemsFile.getString("items.creativeInventory.value"),
                (short) invseeOtherItemsFile.getInt("items.creativeInventory.data"),
                invseeOtherItemsFile.getInt("items.creativeInventory.customModelData"),
                invseeOtherItemsFile.getString("items.creativeInventory.name"),
                invseeOtherItemsFile.getStringList("items.creativeInventory.lore")), invseeOtherItemsFile.getString("items.creativeInventory.direct"));
        creativeInventoryItemSlot = invseeOtherItemsFile.getInt("items.creativeInventory.slot");

        enderChestItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeOtherItemsFile.getString("items.enderChestInventory.type"),
                invseeOtherItemsFile.getString("items.enderChestInventory.value"),
                (short) invseeOtherItemsFile.getInt("items.enderChestInventory.data"),
                invseeOtherItemsFile.getInt("items.enderChestInventory.customModelData"),
                invseeOtherItemsFile.getString("items.enderChestInventory.name"),
                invseeOtherItemsFile.getStringList("items.enderChestInventory.lore")), invseeOtherItemsFile.getString("items.enderChestInventory.direct"));
        enderChestItemSlot = invseeOtherItemsFile.getInt("items.enderChestInventory.slot");

        helmetItemSlot = invseeOtherItemsFile.getInt("items.headItem.slot");
        chestItemSlot = invseeOtherItemsFile.getInt("items.playerChestItem.slot");
        legsItemSlot = invseeOtherItemsFile.getInt("items.playerPantsItem.slot");
        bootsItemSlot = invseeOtherItemsFile.getInt("items.playerBootsItem.slot");
        mainHandItemSlot = invseeOtherItemsFile.getInt("items.playerItemInMainHand.slot");
        secondHandItemSlot = invseeOtherItemsFile.getInt("items.playerItemInSecondHand.slot");

        DebugManager.debug("LOADING INVENTORIES (InventorySeeOtherItems)", "Completed with no issues.");
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
        return InventoryPagesPlus.nms.addColor(InvseeOtherItemsInventoryFile.get().getString("title")
                .replace("%player%", getTargetInventoryDatabase().getPlayerName()));
    }

    @Override
    public int getSlots() {
        return InvseeOtherItemsInventoryFile.get().getInt("rows") * 9;
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
        for (int border = 0; border < getSlots(); border++)
            inventory.setItem(border, borderItem);
        inventory.setItem(closeItemSlot, closeItem);
        inventory.setItem(invseeMainItemSlot, addPlaceholders(invseeMainItem));
        inventory.setItem(creativeInventoryItemSlot, addPlaceholders(creativeInventoryItem));
        if (Bukkit.getPlayer(UUID.fromString(getTargetUUID())) != null) {
            inventory.setItem(enderChestItemSlot, addPlaceholders(enderChestItem));
        } else
            inventory.setItem(enderChestItemSlot, addPlaceholders(offlinePlayerItem));
    }

    private void addTargetItems() {
        Player target = Bukkit.getPlayer(getTargetInventoryDatabase().getPlayerName());
        if (target != null) {
            PlayerInventory targetInventory = target.getInventory();
            inventory.setItem(helmetItemSlot, targetInventory.getHelmet());
            inventory.setItem(chestItemSlot, targetInventory.getChestplate());
            inventory.setItem(legsItemSlot, targetInventory.getLeggings());
            inventory.setItem(bootsItemSlot, targetInventory.getBoots());
            inventory.setItem(mainHandItemSlot, targetInventory.getItemInMainHand());
            inventory.setItem(secondHandItemSlot, targetInventory.getItemInOffHand());
        } else {
            inventory.setItem(helmetItemSlot, offlinePlayerItem);
            inventory.setItem(chestItemSlot, offlinePlayerItem);
            inventory.setItem(legsItemSlot, offlinePlayerItem);
            inventory.setItem(bootsItemSlot, offlinePlayerItem);
            inventory.setItem(mainHandItemSlot, offlinePlayerItem);
            inventory.setItem(secondHandItemSlot, offlinePlayerItem);
        }
    }
}
