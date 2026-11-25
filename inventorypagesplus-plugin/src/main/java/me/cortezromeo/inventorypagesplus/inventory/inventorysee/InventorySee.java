package me.cortezromeo.inventorypagesplus.inventory.inventorysee;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.inventory.CustomInventoryBase;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.PageLimitManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class InventorySee extends CustomInventoryBase {

    protected String targetName;
    protected String targetUUID;
    protected int page;

    public InventorySee(Player owner) {
        super(owner);
    }

    /**
     * Edit intervention for viewers with the take/give permissions: clicking an editable
     * slot pulls the whole stack into the viewer's inventory, a click while holding a
     * cursor item places or swaps it into the target, and shift-clicking one's own item
     * pushes that stack into the target's storage. Returns true when the click was used.
     */
    protected boolean handleIntervention(InventoryClickEvent event, Player viewer) {
        Player target = Bukkit.getPlayer(UUID.fromString(targetUUID));
        boolean editable = target != null && !getTargetInventoryDatabase().isProtectionLocked();

        if (event.getClickedInventory() == viewer.getInventory()) {
            // A vanilla shift-click would drop the stack into the mirror, which the next
            // refresh wipes, so this branch always consumes the click itself.
            if (!event.isShiftClick())
                return false;
            event.setCancelled(true);
            ItemStack moving = InventoryPagesPlus.nms.getItemStack(event.getCurrentItem());
            if (!editable || moving == null || !canIntervene(viewer, false, true)
                    || InventoryPagesPlus.nms.getCustomData(moving).equals(PlayerPageInventory.itemCustomData))
                return true;
            Inventory storage = interventionStorage(target);
            if (storage != null)
                event.setCurrentItem(mergeLeftover(storage.addItem(moving).values()));
            return true;
        }

        if (!editable || event.getClickedInventory() != getInventory())
            return false;

        int viewSlot = event.getSlot();
        if (!isEditableSlot(target, viewSlot))
            return false;

        ItemStack slotItem = InventoryPagesPlus.nms.getItemStack(getTargetSlotItem(target, viewSlot));
        InventoryAction action = event.getAction();

        if (event.isShiftClick() || action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE) {
            if (slotItem == null || !canIntervene(viewer, true, false))
                return false;
            Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(slotItem);
            ItemStack remaining = mergeLeftover(leftover.values());
            setTargetSlotItem(target, viewSlot, remaining);
            inventory.setItem(viewSlot, InventoryPagesPlus.nms.getItemStack(remaining));
            return true;
        }

        // Only a filled cursor on a plain click edits, so number-key and drop actions stay inert.
        ItemStack cursor = event.getCursor();
        boolean placeClick = cursor != null && cursor.getType() != Material.AIR
                && (action == InventoryAction.SWAP_WITH_CURSOR || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_SOME || action == InventoryAction.PLACE_ONE);
        if (!placeClick || !canIntervene(viewer, slotItem != null, true))
            return false;

        setTargetSlotItem(target, viewSlot, InventoryPagesPlus.nms.getItemStack(cursor));
        inventory.setItem(viewSlot, cursor);
        event.setCursor(slotItem);
        viewer.updateInventory();
        return true;
    }

    protected Inventory interventionStorage(Player target) {
        return null;
    }

    private boolean canIntervene(Player viewer, boolean takesOut, boolean putsIn) {
        if (viewer.hasPermission(Settings.INVSEE_PERMISSION_ADMIN))
            return true;
        if (takesOut && !viewer.hasPermission(Settings.INVSEE_PERMISSION_TAKE))
            return false;
        return !putsIn || viewer.hasPermission(Settings.INVSEE_PERMISSION_GIVE);
    }

    private ItemStack mergeLeftover(Iterable<ItemStack> leftover) {
        ItemStack merged = null;
        for (ItemStack stack : leftover) {
            if (merged == null)
                merged = stack;
            else
                merged.setAmount(merged.getAmount() + stack.getAmount());
        }
        return merged;
    }

    protected boolean isEditableSlot(Player target, int viewSlot) {
        return false;
    }

    protected ItemStack getTargetSlotItem(Player target, int viewSlot) {
        return null;
    }

    protected void setTargetSlotItem(Player target, int viewSlot, ItemStack item) {
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
        if (handleIntervention(event, (Player) event.getWhoClicked()))
            return;

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
