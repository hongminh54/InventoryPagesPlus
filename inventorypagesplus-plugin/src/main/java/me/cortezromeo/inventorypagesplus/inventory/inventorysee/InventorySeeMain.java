package me.cortezromeo.inventorypagesplus.inventory.inventorysee;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.InvseeInventoryFile;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.PageLimitManager;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class InventorySeeMain extends InventorySee {

    public static ItemStack borderItem, offlinePlayerItem, nextItem, prevItem, noPageItem, otherItemsInventoryItem, infoItem, closeItem;
    public static int nextItemSlot, prevItemSlot, otherItemsInventoryItemSlot, infoItemSlot, closeItemSlot;
    private BukkitTask bukkitRunnable;

    public InventorySeeMain(Player owner, String targetName, String targetUUID, int page) {
        super(owner);
        super.targetName = targetName;
        super.targetUUID = targetUUID;
        super.page = page;
    }

    public static void setupItems() {
        {
            FileConfiguration invseeInvFile = InvseeInventoryFile.get();

            borderItem = ItemUtil.getItem(invseeInvFile.getString("items.border.type"),
                    invseeInvFile.getString("items.border.value"),
                    (short) invseeInvFile.getInt("items.border.data"),
                    invseeInvFile.getInt("items.border.customModelData"),
                    invseeInvFile.getString("items.border.name"),
                    invseeInvFile.getStringList("items.border.lore"));

            prevItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeInvFile.getString("items.prev.type"),
                    invseeInvFile.getString("items.prev.value"),
                    (short) invseeInvFile.getInt("items.prev.data"),
                    invseeInvFile.getInt("items.prev.customModelData"),
                    invseeInvFile.getString("items.prev.name"),
                    invseeInvFile.getStringList("items.prev.lore")), invseeInvFile.getString("items.prev.direct"));
            prevItemSlot = invseeInvFile.getInt("items.prev.slot");

            nextItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeInvFile.getString("items.next.type"),
                    invseeInvFile.getString("items.next.value"),
                    (short) invseeInvFile.getInt("items.next.data"),
                    invseeInvFile.getInt("items.next.customModelData"),
                    invseeInvFile.getString("items.next.name"),
                    invseeInvFile.getStringList("items.next.lore")), invseeInvFile.getString("items.next.direct"));
            nextItemSlot = invseeInvFile.getInt("items.next.slot");

            noPageItem = ItemUtil.getItem(invseeInvFile.getString("items.noPage.type"),
                    invseeInvFile.getString("items.noPage.value"),
                    (short) invseeInvFile.getInt("items.noPage.data"),
                    invseeInvFile.getInt("items.noPage.customModelData"),
                    invseeInvFile.getString("items.noPage.name"),
                    invseeInvFile.getStringList("items.noPage.lore"));

            offlinePlayerItem = ItemUtil.getItem(invseeInvFile.getString("items.offlinePlayer.type"),
                    invseeInvFile.getString("items.offlinePlayer.value"),
                    (short) invseeInvFile.getInt("items.offlinePlayer.data"),
                    invseeInvFile.getInt("items.offlinePlayer.customModelData"),
                    invseeInvFile.getString("items.offlinePlayer.name"),
                    invseeInvFile.getStringList("items.offlinePlayer.lore"));

            otherItemsInventoryItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeInvFile.getString("items.otherItemsInventory.type"),
                    invseeInvFile.getString("items.otherItemsInventory.value"),
                    (short) invseeInvFile.getInt("items.otherItemsInventory.data"),
                    invseeInvFile.getInt("items.otherItemsInventory.customModelData"),
                    invseeInvFile.getString("items.otherItemsInventory.name"),
                    invseeInvFile.getStringList("items.otherItemsInventory.lore")), invseeInvFile.getString("items.otherItemsInventory.direct"));
            otherItemsInventoryItemSlot = invseeInvFile.getInt("items.otherItemsInventory.slot");

            infoItem = ItemUtil.getItem(invseeInvFile.getString("items.info.type"),
                    invseeInvFile.getString("items.info.value"),
                    (short) invseeInvFile.getInt("items.info.data"),
                    invseeInvFile.getInt("items.info.customModelData"),
                    invseeInvFile.getString("items.info.name"),
                    invseeInvFile.getStringList("items.info.lore"));
            infoItemSlot = invseeInvFile.getInt("items.info.slot");

            closeItem = InventoryPagesPlus.nms.addCustomData(ItemUtil.getItem(invseeInvFile.getString("items.close.type"),
                    invseeInvFile.getString("items.close.value"),
                    (short) invseeInvFile.getInt("items.close.data"),
                    invseeInvFile.getInt("items.close.customModelData"),
                    invseeInvFile.getString("items.close.name"),
                    invseeInvFile.getStringList("items.close.lore")), invseeInvFile.getString("items.close.direct"));
            closeItemSlot = invseeInvFile.getInt("items.close.slot");

            DebugManager.debug("LOADING INVENTORIES (InventorySeeMain)", "Completed with no issues.");
        }
    }

    @Override
    public void open() {
        if (super.inventory == null || !getInventory().getViewers().contains(getOwner())) {
            super.open();
            addTargetItems();
        } else {
            addTargetItems();
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
        return InventoryPagesPlus.nms.addColor(InvseeInventoryFile.get().getString("title").replace("%player%", getTargetInventoryDatabase().getPlayerName()));
    }

    @Override
    public int getSlots() {
        return 6 * 9;
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

        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            String customData = InventoryPagesPlus.nms.getCustomData(event.getCurrentItem());
            if (customData.equals("next") || customData.equals("prev")) {
                if (customData.equals("next"))
                    addPage(1);
                else
                    removePage(1);
                setMenuItems();
                open();
                return;
            }
        }

        super.handleMenu(event);
    }

    @Override
    public void setMenuItems() {
        for (int border = 36; border < 45; border++)
            inventory.setItem(border, borderItem);

        if (getPage() == 0) {
            inventory.setItem(prevItemSlot, getPageItemStack(noPageItem, false));
        } else {
            inventory.setItem(prevItemSlot, getPageItemStack(prevItem, false));
        }

        if (getPage() == getTargetInventoryDatabase().getEffectiveMaxPage()) {
            inventory.setItem(nextItemSlot, getPageItemStack(noPageItem, false));
        } else {
            inventory.setItem(nextItemSlot, getPageItemStack(nextItem, true));
        }

        inventory.setItem(otherItemsInventoryItemSlot, addPlaceholders(otherItemsInventoryItem));
        inventory.setItem(infoItemSlot, addPlaceholders(infoItem));
        inventory.setItem(closeItemSlot, addPlaceholders(closeItem));
    }

    private @NotNull ItemStack getPageItemStack(ItemStack itemStack, boolean nextPage) {
        ItemStack modItem = new ItemStack(itemStack);
        ItemMeta itemMeta = modItem.getItemMeta();
        int currentPageUser = getPage() + 1;

        String displayName = itemMeta.getDisplayName();
        displayName = displayName.replace("%previousPageNumber%", String.valueOf(currentPageUser - 1))
                .replace("%nextPageNumber%", String.valueOf(currentPageUser + 1));
        itemMeta.setDisplayName(displayName);

        List<String> itemLore = itemMeta.getLore();
        itemLore.replaceAll(string -> string
                .replace("%usedSlots%", (nextPage ? String.valueOf(getUsedSlot(getPage() + 1)) : String.valueOf(getUsedSlot(getPage() - 1))))
                .replace("%currentPage%", String.valueOf(currentPageUser))
                .replace("%maxPage%", PageLimitManager.formatMaxPage(getTargetInventoryDatabase().getEffectiveMaxPage())));
        itemMeta.setLore(itemLore);
        modItem.setItemMeta(itemMeta);
        return modItem;
    }

    int getUsedSlot(int page) {
        if (!getTargetInventoryDatabase().getItems().containsKey(page))
            return 0;

        int usedSlot = 0;
        for (ItemStack itemStack : getTargetInventoryDatabase().getItems(page)) {
            if (itemStack != null)
                usedSlot = usedSlot + 1;
        }

        return usedSlot;
    }

    private void addTargetItems() {
        InventoryPagesPlus.getDatabaseManager().saveCurrentPage(UUID.fromString(getTargetInventoryDatabase().getPlayerUUID()));
        // get items from page
        boolean foundPrevItem = false;
        boolean foundNextItem = false;
        for (int slot = 0; slot < 27; slot++) {
            int slotClone = slot;
            if (slot == getTargetInventoryDatabase().getPrevItemPos()) {
                foundPrevItem = true;
            } else if (slot == getTargetInventoryDatabase().getNextItemPos()) {
                foundNextItem = true;
            } else {
                if (foundPrevItem)
                    slotClone--;
                if (foundNextItem)
                    slotClone--;
                inventory.setItem(slot, getTargetInventoryDatabase().getItems(getPage()).get(slotClone));
            }
        }

        // hotbar
        if (Bukkit.getPlayer(UUID.fromString(getTargetUUID())) != null) {
            for (int slot = 0; slot < 9; slot++) {
                inventory.setItem(27 + slot, Bukkit.getPlayer(UUID.fromString(getTargetUUID())).getInventory().getItem(slot));
            }
            inventory.setItem(otherItemsInventoryItemSlot, addPlaceholders(otherItemsInventoryItem));
        } else {
            for (int slot = 0; slot < 9; slot++) {
                inventory.setItem(27 + slot, offlinePlayerItem);
            }
        }
    }
}
