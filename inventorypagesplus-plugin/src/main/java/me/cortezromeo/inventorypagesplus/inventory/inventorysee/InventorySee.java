package me.cortezromeo.inventorypagesplus.inventory.inventorysee;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.inventory.CustomInventoryBase;
import me.cortezromeo.inventorypagesplus.manager.PageLimitManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public abstract class InventorySee extends CustomInventoryBase {

    protected String targetName;
    protected String targetUUID;
    protected int page;

    public InventorySee(Player owner) {
        super(owner);
    }

    public PlayerInventoryDatabase getTargetInventoryDatabase() {
        return InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(UUID.fromString(targetUUID));
    }

    public String getTargetName() {
        return targetName;
    }

    public String getTargetUUID() {
        return targetUUID;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void addPage(int page) {
        this.page = this.page + 1;
    }

    public void removePage(int page) {
        this.page = this.page - 1;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        String customData = InventoryPagesPlus.nms.getCustomData(clickedItem);
        if (customData.equals("close")) {
            getOwner().closeInventory();
        } else if (customData.equals("otherItemsInventory")) {
            new InventorySeeOtherItems(getOwner(), getTargetName(), getTargetUUID(), getPage()).open();
        } else if (customData.equals("invseeMainInventory")) {
            new InventorySeeMain(getOwner(), getTargetName(), getTargetUUID(), getPage()).open();
        } else if (customData.equals("creativeInventory")) {
            new InventorySeeCreative(getOwner(), getTargetName(), getTargetUUID(), getPage()).open();
        } else if (customData.equals("enderChestInventory")) {
            new InventorySeeEnderChest(getOwner(), getTargetName(), getTargetUUID(), getPage()).open();
        }
    }

    public @NotNull ItemStack addPlaceholders(ItemStack itemStack) {
        ItemStack modItem = new ItemStack(itemStack);
        ItemMeta itemMeta = modItem.getItemMeta();
        itemMeta.setDisplayName(modItem.getItemMeta().getDisplayName().replace("%player%", getTargetInventoryDatabase().getPlayerName()));

        List<String> itemLores = modItem.getItemMeta().getLore();
        for (int itemLore = 0; itemLore < itemLores.size(); itemLore++) {
            String lore = itemLores.get(itemLore).replace("%player%", getTargetInventoryDatabase().getPlayerName());
            lore = lore.replace("%totalpages%", PageLimitManager.formatMaxPage(getTargetInventoryDatabase().getEffectiveMaxPage()))
                    .replace("%currentviewingpage%", String.valueOf(getTargetInventoryDatabase().getCurrentPage()))
                    .replace("%creativeusedslots%", String.valueOf(getTargetInventoryDatabase().getUsedSlotCreative()))
                    .replace("%enderchestusedslots%", String.valueOf(getUsedSlotEnderChest()))
                    .replace("%nextpageslotnumber%", String.valueOf(getTargetInventoryDatabase().getNextItemPos()))
                    .replace("%previouspageslotnumber%", String.valueOf(getTargetInventoryDatabase().getPrevItemPos()));
            itemLores.set(itemLore, lore);
        }
        itemMeta.setLore(itemLores);
        modItem.setItemMeta(itemMeta);
        return modItem;
    }

    private int getUsedSlotEnderChest() {
        Player target = Bukkit.getPlayer(getTargetName());
        int usedSlot = 0;
        if (target != null) {
            for (ItemStack itemStack : target.getEnderChest().getContents()) {
                if (itemStack != null)
                    usedSlot++;
            }
        }
        return usedSlot;
    }

}
