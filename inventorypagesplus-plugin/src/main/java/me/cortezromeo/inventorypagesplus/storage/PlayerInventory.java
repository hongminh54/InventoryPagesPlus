package me.cortezromeo.inventorypagesplus.storage;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.PageLimitManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PlayerInventory implements PlayerInventoryDatabase {
    private final Player player;
    private String playerName;
    private String playerUUID;
    private final ItemStack prevItem;
    private final ItemStack nextItem;
    private final ItemStack noPageItem;
    private Integer currentPage = 0, maxPage = 1, prevItemPos, nextItemPos;
    private int permissionMaxPage;
    private Boolean hasUsedCreative = false;
    private HashMap<Integer, ArrayList<ItemStack>> items = new HashMap<>();
    private ArrayList<ItemStack> creativeItems = new ArrayList<>(27);
    private String protectionPinHash;
    private int protectionAttempts;
    private long protectionBanUntil;
    private boolean protectionUnlocked;

    PlayerInventory(Player player, String playerName, String playerUUID, int maxPage, HashMap<Integer, ArrayList<ItemStack>> items, ArrayList<ItemStack> creativeItems, ItemStack prevItem, Integer prevPos, ItemStack nextItem, Integer nextPos, ItemStack noPageItem) {
        this.player = player;
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.maxPage = maxPage;
        // Resolved before the pick up loop below, which already needs the effective limit.
        this.permissionMaxPage = PageLimitManager.getPermissionMaxPage(player);
        this.prevItem = prevItem;
        this.prevItemPos = prevPos;
        this.nextItem = nextItem;
        this.nextItemPos = nextPos;
        this.noPageItem = noPageItem;

        // initialize creative inventory
        for (int i = 0; i < 27; i++) {
            this.creativeItems.add(null);
        }

        if (items != null)
            this.setItems(items);

        if (creativeItems != null)
            this.setCreativeItems(creativeItems);

        // Pages are created on demand, only the first one is guaranteed to exist.
        // This runs after setItems because that call replaces the whole page map.
        if (!pageExists(0))
            createPage(0);
    }

    @Override
    public void recoverDisplayItems() {
        if (player == null)
            return;

        boolean droppedItem = false;
        for (int i = 0; i < 27; i++) {
            // This slot can not be loaded because it is considered as a skip slot
            if (!Settings.SKIP_SLOTS.isEmpty())
                if (Settings.SKIP_SLOTS.contains(i))
                    continue;

            ItemStack item = player.getInventory().getItem(i + 9);
            if (item == null)
                continue;
            // Never absorb the fake navigation buttons back into storage.
            if (InventoryPagesPlus.nms.getCustomData(item).equals(PlayerPageInventory.itemCustomData))
                continue;
            if (this.storeOrDropItem(item, player.getGameMode()))
                droppedItem = true;
        }
        if (droppedItem)
            MessageUtil.sendMessage(player, Messages.ITEMS_DROPPED);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public String getPlayerUUID() {
        return playerUUID;
    }

    @Override
    public void setPlayerUUID(String playerUUID) {
        this.playerUUID = playerUUID;
    }

    @Override
    public int getMaxPage() {
        return this.maxPage;
    }

    @Override
    public void setMaxPage(int number) {
        if (number < 0)
            number = 0;
        this.maxPage = number;

        saveCurrentPage();
        if (player != null)
            showPage(player.getGameMode());
    }

    @Override
    public int getEffectiveMaxPage() {
        return Math.max(this.maxPage, this.permissionMaxPage);
    }

    @Override
    public void addMaxPage(int number) {
        if (number < 0)
            number = 0;
        this.maxPage = this.maxPage + number;

        saveCurrentPage();
        if (player != null)
            showPage(player.getGameMode());
    }

    @Override
    public void removeMaxPage(int number) {
        if (this.maxPage - number < 0) {
            number = this.maxPage;
        }

        this.maxPage = this.maxPage - number;
        saveCurrentPage();
        if (player != null)
            showPage(player.getGameMode());
    }

    @Override
    public int getNextItemPos() {
        return this.nextItemPos;
    }

    @Override
    public void setNextItemPos(int number) {
        if (number < 0)
            number = 0;

        if (number > 26)
            number = 26;

        if (number == prevItemPos) {
            if (number == 0)
                number = 1;
            else if (number == 26)
                number = 25;
        }
        this.nextItemPos = number;
    }

    @Override
    public int getPrevItemPos() {
        return this.prevItemPos;
    }

    @Override
    public void setPrevItemPos(int number) {
        if (number < 0)
            number = 0;

        if (number > 26)
            number = 26;

        if (number == nextItemPos) {
            if (number == 0)
                number = 1;
            else if (number == 26)
                number = 25;
        }

        this.prevItemPos = number;
    }

    @Override
    public void saveCurrentPage() {
        if (isProtectionLocked())
            return;

        if (player == null)
            return;

        if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY || player.getGameMode() != GameMode.CREATIVE) {
            ArrayList<ItemStack> pageItems = new ArrayList<>(25);
            for (int slotNumber = 0; slotNumber < 27; slotNumber++) {
                if (slotNumber != prevItemPos && slotNumber != nextItemPos) {
                    pageItems.add(InventoryPagesPlus.nms.getItemStack(player.getInventory().getItem(slotNumber + 9)));
                }
            }
            this.items.put(this.currentPage, pageItems);
        } else {
            for (int slotNumber = 0; slotNumber < 27; slotNumber++) {
                creativeItems.set(slotNumber, InventoryPagesPlus.nms.getItemStack(player.getInventory().getItem(slotNumber + 9)));
                //DebugManager.debug("SAVING CURRENT PAGE", "Saved current page (creative items) of " + playerName);
            }
        }
    }

    @Override
    public void clearPage(GameMode gm) {
        if (player != null) {
            player.getInventory().setItemInOffHand(null);
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            player.updateInventory();
        }
        clearPage(this.currentPage, gm);
    }

    @Override
    public void clearPage(int page, GameMode gm) {
        if (gm != GameMode.CREATIVE) {
            ArrayList<ItemStack> pageItems = new ArrayList<>(25);
            for (int i = 0; i < 25; i++) {
                pageItems.add(null);
            }
            this.items.put(page, pageItems);
        } else {
            if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY) {
                clearPage(page, GameMode.SURVIVAL);
                return;
            }
            for (int i = 0; i < 27; i++) {
                creativeItems.set(i, null);
            }
        }
    }

    @Override
    public void clearAllPages(GameMode gm) {
        if (gm != GameMode.CREATIVE) {
            if (player != null) {
                player.getInventory().setItemInOffHand(null);
                player.getInventory().setHelmet(null);
                player.getInventory().setChestplate(null);
                player.getInventory().setLeggings(null);
                player.getInventory().setBoots(null);
                player.updateInventory();
            }
            for (Integer page : this.items.keySet()) {
                clearPage(page, gm);
            }
        } else {
            if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY) {
                clearAllPages(GameMode.SURVIVAL);
                return;
            }
            clearPage(gm);
        }
    }

    @Override
    public void dropPage(GameMode gm) {
        dropPage(this.currentPage, gm);
    }

    @Override
    public void dropPage(int page, GameMode gm) {
        if (isProtectionLocked())
            return;

        if (gm != GameMode.CREATIVE) {
            for (int slot = 0; slot < 25; slot++) {
                ItemStack item = InventoryPagesPlus.nms.getItemStack(this.getItems(page).get(slot));
                if (item != null && !InventoryPagesPlus.nms.getCustomData(item).equalsIgnoreCase(PlayerPageInventory.itemCustomData)) {
                    this.player.getWorld().dropItemNaturally(this.player.getLocation(), item);
                    this.getItems(page).set(slot, null);
                    player.getInventory().setItem(slot + 9, null);
                }
            }
        } else {
            if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY) {
                dropPage(page, GameMode.SURVIVAL);
                return;
            }
            for (int i = 0; i < 27; i++) {
                ItemStack item = InventoryPagesPlus.nms.getItemStack(this.creativeItems.get(i));
                if (item != null && InventoryPagesPlus.nms.getCustomData(item).equalsIgnoreCase(PlayerPageInventory.itemCustomData)) {
                    this.player.getWorld().dropItemNaturally(this.player.getLocation(), item);
                    this.creativeItems.set(i, null);
                    player.getInventory().setItem(i + 9, null);
                }
            }
        }
    }

    @Override
    public void dropAllPages(GameMode gm) {
        if (isProtectionLocked())
            return;

        if (gm != GameMode.CREATIVE) {
            for (Integer page : this.items.keySet()) {
                dropPage(page, gm);
            }
        } else {
            dropPage(gm);
        }
    }

    @Override
    public void showPage() {
        this.showPage(this.currentPage);
    }

    @Override
    public void showPage(Integer page) {
        showPage(page, GameMode.SURVIVAL);
    }

    @Override
    public void showPage(GameMode gm) {
        showPage(this.currentPage, gm);
    }

    @Override
    public void showPage(Integer page, GameMode gm) {
        if (isProtectionLocked())
            return;

        if (this.player == null)
            return;

        int effectiveMaxPage = getEffectiveMaxPage();
        // Clamp before creating, otherwise a page above the limit would be created for nothing.
        this.currentPage = Math.min(page, effectiveMaxPage);
        if (!pageExists(this.currentPage))
            createPage(this.currentPage);
        //player.sendMessage("GameMode: " + gm);
        if (gm != GameMode.CREATIVE) {
            boolean foundPrev = false;
            boolean foundNext = false;
            for (int slotNumber = 0; slotNumber < 27; slotNumber++) {

                // This slot can not be loaded because it is considered as a skip slot
                if (!Settings.SKIP_SLOTS.isEmpty())
                    if (Settings.SKIP_SLOTS.contains(slotNumber))
                        continue;

                int slotNumberClone = slotNumber;
                if (slotNumber == prevItemPos) {
                    if (this.currentPage == 0) {
                        this.player.getInventory().setItem(slotNumber + 9, addPageNums(noPageItem, false));
                    } else {
                        this.player.getInventory().setItem(slotNumber + 9, addPageNums(prevItem, false));
                    }
                    foundPrev = true;
                } else if (slotNumber == nextItemPos) {
                    if (this.currentPage == effectiveMaxPage) {
                        this.player.getInventory().setItem(slotNumber + 9, addPageNums(noPageItem, false));
                    } else {
                        this.player.getInventory().setItem(slotNumber + 9, addPageNums(nextItem, true));
                    }
                    foundNext = true;
                } else {
                    if (foundPrev) {
                        slotNumberClone--;
                    }
                    if (foundNext) {
                        slotNumberClone--;
                    }
                    ItemStack itemStack = InventoryPagesPlus.nms.getItemStack(this.getItems(this.currentPage).get(slotNumberClone));
                    if (itemStack != null) {
                        if (InventoryPagesPlus.nms.getCustomData(itemStack).equals(PlayerPageInventory.itemCustomData)) {
                            itemStack = null;
                        }
                    }
                    this.player.getInventory().setItem(slotNumber + 9, itemStack);
                }
            }
            //player.sendMessage("Showing Page: " + this.page);
        } else {
            if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY) {
                showPage(this.currentPage, GameMode.SURVIVAL);
                return;
            }
            this.hasUsedCreative = true;
            for (int slotNumber = 0; slotNumber < 27; slotNumber++) {

                // This slot can not be loaded because it is considered as a skip slot
                if (!Settings.SKIP_SLOTS.isEmpty())
                    if (Settings.SKIP_SLOTS.contains(slotNumber))
                        continue;

                this.player.getInventory().setItem(slotNumber + 9, InventoryPagesPlus.nms.getItemStack(this.creativeItems.get(slotNumber)));
            }
        }

        // Refresh once per page instead of once per slot, every call sends a full inventory packet.
        this.player.updateInventory();
    }

    ItemStack addPageNums(ItemStack item, boolean nextPage) {
        ItemStack modItem = new ItemStack(item);
        ItemMeta itemMeta = modItem.getItemMeta();
        int currentPageUser = currentPage + 1;

        if (itemMeta == null)
            return item;

        String displayName = itemMeta.getDisplayName();
        displayName = displayName.replace("%previousPageNumber%", String.valueOf(currentPageUser - 1))
                .replace("%nextPageNumber%", String.valueOf(currentPageUser + 1));
        itemMeta.setDisplayName(displayName);

        if (itemMeta.getLore() != null) {
            List<String> itemLore = itemMeta.getLore();
            itemLore.replaceAll(string -> string
                    .replace("%usedSlots%", (nextPage ? String.valueOf(getUsedSlot(currentPage + 1)) : String.valueOf(getUsedSlot(currentPage - 1))))
                    .replace("%currentPage%", String.valueOf(currentPageUser))
                    .replace("%maxPage%", PageLimitManager.formatMaxPage(getEffectiveMaxPage())));
            itemMeta.setLore(itemLore);
        }

        modItem.setItemMeta(itemMeta);
        return modItem;
    }

    @Override
    public int getUsedSlot(int page) {
        if (!getItems().containsKey(page))
            return 0;

        int usedSlot = 0;
        for (ItemStack itemStack : getItems(page)) {
            if (itemStack != null)
                usedSlot = usedSlot + 1;
        }

        return usedSlot;
    }

    @Override
    public int getUsedSlotCreative() {
        int usedSlot = 0;
        for (ItemStack itemStack : getCreativeItems()) {
            if (itemStack != null)
                usedSlot = usedSlot + 1;
        }

        return usedSlot;
    }

    @Override
    public void prevPage() {
        if (isProtectionLocked())
            return;

        // Re-resolve so a rank granted or lost while online applies without relogging.
        this.permissionMaxPage = PageLimitManager.getPermissionMaxPage(this.player);
        if (this.currentPage > 0) {
            this.saveCurrentPage();
            this.currentPage = this.currentPage - 1;
            this.showPage();
            this.saveCurrentPage();
        }
    }

    @Override
    public void nextPage() {
        if (isProtectionLocked())
            return;

        this.permissionMaxPage = PageLimitManager.getPermissionMaxPage(this.player);
        int effectiveMaxPage = getEffectiveMaxPage();
        if (this.currentPage > effectiveMaxPage) {
            // Keep the items of the page the player just lost access to before clamping back.
            this.saveCurrentPage();
            this.showPage();
            return;
        }
        if (this.currentPage < effectiveMaxPage) {
            this.saveCurrentPage();
            this.currentPage = this.currentPage + 1;
            // showPage creates the page on demand, which is what makes unlimited pages possible.
            this.showPage();
            this.saveCurrentPage();
        }
    }

    Boolean pageExists(Integer page) {
        return items.containsKey(page);
    }

    @Override
    public void createPage(Integer page) {
        ArrayList<ItemStack> pageItems = new ArrayList<ItemStack>(25);
        for (int i = 0; i < 25; i++) {
            pageItems.add(null);
        }
        this.items.put(page, pageItems);
    }

    @Override
    public HashMap<Integer, ArrayList<ItemStack>> getItems() {
        return this.items;
    }

    @Override
    public void setItems(HashMap<Integer, ArrayList<ItemStack>> items) {
        this.items = items;
    }

    @Override
    public ArrayList<ItemStack> getItems(int page) {
        if (!pageExists(page)) {
            createPage(page);
        }
        return items.get(page);
    }

    @Override
    public ArrayList<ItemStack> getCreativeItems() {
        return this.creativeItems;
    }

    @Override
    public void setCreativeItems(ArrayList<ItemStack> creativeItems) {
        this.creativeItems = creativeItems;
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public void setCurrentPage(Integer page) {
        this.currentPage = page;
    }

    @Override
    public boolean hasUsedCreative() {
        return this.hasUsedCreative;
    }

    @Override
    public void setUsedCreative(Boolean hasUsedCreative) {
        this.hasUsedCreative = hasUsedCreative;
    }

    private void sendPickUpActionBar(ItemStack itemStack, int page, int slotNumber) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        String itemName = itemMeta != null && itemMeta.hasDisplayName() ? itemMeta.getDisplayName() : itemStack.getType().name();

        String actionBar = Settings.ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_TEXT
                .replace("%amount%", String.valueOf(itemStack.getAmount()))
                .replace("%itemName%", itemName)
                .replace("%page%", String.valueOf(page + 1))
                .replace("%slotNumber%", String.valueOf(slotNumber));

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(InventoryPagesPlus.nms.addColor(actionBar)));
    }

    int nextCreativeFreeSpace() {
        for (Integer i = 0; i < 27; i++) {
            if (creativeItems.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    // returns true if dropped
    @Override
    public boolean storeOrDropItem(ItemStack itemStack, GameMode gameMode) {
        // Locked: let vanilla pickup keep the item in the real inventory, never absorb it into the hidden page.
        if (isProtectionLocked())
            return true;

        if (this.player == null)
            return true;

        itemStack = InventoryPagesPlus.nms.getItemStack(itemStack);

        for (int hotBarSlot = 0; hotBarSlot <= 8; hotBarSlot++) {
            ItemStack itemFromSlot = InventoryPagesPlus.nms.getItemStack(player.getInventory().getItem(hotBarSlot));
            if (itemFromSlot == null) {
                player.getInventory().setItem(hotBarSlot, itemStack);
                return false;
            } else {
                if (itemFromSlot.isSimilar(itemStack)) {
                    int amountCombined = itemStack.getAmount() + itemFromSlot.getAmount();
                    if (amountCombined <= itemStack.getMaxStackSize()) {
                        itemFromSlot.setAmount(amountCombined);
                        player.getInventory().setItem(hotBarSlot, itemFromSlot);
                        return false;
                    }
                }
            }
        }
        if (gameMode != GameMode.CREATIVE) {
            int effectiveMaxPage = getEffectiveMaxPage();
            // The page map is unordered, so the highest existing page bounds the search instead of the limit.
            // One page past it may be opened lazily when every existing page is full.
            int highestPage = this.items.isEmpty() ? -1 : Collections.max(this.items.keySet());
            for (int page = 0; page <= highestPage + 1 && page <= effectiveMaxPage; page++) {
                if (page == this.currentPage)
                    saveCurrentPage();
                ArrayList<ItemStack> pageItems = getItems(page);
                for (int slotNumber = 0; slotNumber < 25; slotNumber++) {
                    if (pageItems.get(slotNumber) == null) {
                        pageItems.set(slotNumber, itemStack);
                        this.items.put(page, pageItems);
                        if (page == this.currentPage)
                            showPage();
                        if (Settings.ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_ENABLED)
                            sendPickUpActionBar(itemStack, page, slotNumber);
                        return false;
                    } else {
                        ItemStack itemFromSlot = InventoryPagesPlus.nms.getItemStack(pageItems.get(slotNumber));
                        if (itemFromSlot.isSimilar(itemStack)) {
                            int amountCombined = itemStack.getAmount() + itemFromSlot.getAmount();
                            if (amountCombined <= itemStack.getMaxStackSize()) {
                                itemFromSlot.setAmount(amountCombined);
                                pageItems.set(slotNumber, itemFromSlot);
                                this.items.put(page, pageItems);
                                if (page == this.currentPage)
                                    showPage();
                                if (Settings.ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_ENABLED)
                                    sendPickUpActionBar(itemStack, page, slotNumber);
                                return false;
                            }
                        }
                    }
                }
            }
            this.player.getWorld().dropItem(player.getLocation(), itemStack);
            return true;
        } else {
            if (!Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY)
                return storeOrDropItem(itemStack, GameMode.SURVIVAL);
            int nextFreeSpace = nextCreativeFreeSpace();
            if (nextFreeSpace != -1) {
                this.creativeItems.set(nextFreeSpace, itemStack);
                return false;
            } else {
                this.player.getWorld().dropItem(player.getLocation(), itemStack);
                return true;
            }
        }

    }

    @Override
    public String getProtectionPinHash() {
        return protectionPinHash;
    }

    @Override
    public void setProtectionPinHash(String protectionPinHash) {
        this.protectionPinHash = protectionPinHash;
    }

    @Override
    public int getProtectionAttempts() {
        return protectionAttempts;
    }

    @Override
    public void setProtectionAttempts(int protectionAttempts) {
        this.protectionAttempts = protectionAttempts;
    }

    @Override
    public long getProtectionBanUntil() {
        return protectionBanUntil;
    }

    @Override
    public void setProtectionBanUntil(long protectionBanUntil) {
        this.protectionBanUntil = protectionBanUntil;
    }

    @Override
    public boolean isProtectionUnlocked() {
        return protectionUnlocked;
    }

    @Override
    public void setProtectionUnlocked(boolean protectionUnlocked) {
        this.protectionUnlocked = protectionUnlocked;
    }

    @Override
    public boolean isProtectionLocked() {
        if (!Settings.PROTECTION_ENABLED || !hasProtectionPin() || protectionUnlocked)
            return false;
        return player == null || !player.hasPermission("inventorypagesplus.protect.bypass");
    }

    @Override
    public void clearDisplayedPage() {
        if (player == null)
            return;

        for (int slot = 9; slot < 36; slot++) {
            if (!Settings.SKIP_SLOTS.isEmpty() && Settings.SKIP_SLOTS.contains(slot - 9))
                continue;
            player.getInventory().setItem(slot, null);
        }
        player.updateInventory();
    }
}
