package me.cortezromeo.inventorypagesplus.history;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class InventorySnapshot {

    private final String id;
    private final String playerUUID;
    private final String playerName;
    private final long createdAt;
    private final SnapshotReason reason;
    private final String serverId;
    private final int currentPage;
    private final int maxPage;
    private final int pageCount;
    private final boolean usedCreative;
    private final Map<Integer, List<ItemStack>> pages;
    private final List<ItemStack> creativeItems;
    private final List<ItemStack> armor;
    private final ItemStack offhand;
    private final List<ItemStack> hotbar;

    public InventorySnapshot(String id, String playerUUID, String playerName, long createdAt,
                             SnapshotReason reason, String serverId, int currentPage, int maxPage,
                             boolean usedCreative, Map<Integer, List<ItemStack>> pages,
                             List<ItemStack> creativeItems) {
        this(id, playerUUID, playerName, createdAt, reason, serverId, currentPage, maxPage,
                usedCreative, pages, creativeItems, Collections.emptyList(), null, Collections.emptyList());
    }

    public InventorySnapshot(String id, String playerUUID, String playerName, long createdAt,
                             SnapshotReason reason, String serverId, int currentPage, int maxPage,
                             boolean usedCreative, Map<Integer, List<ItemStack>> pages,
                             List<ItemStack> creativeItems, List<ItemStack> armor, ItemStack offhand,
                             List<ItemStack> hotbar) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.createdAt = createdAt;
        this.reason = reason;
        this.serverId = serverId == null ? "" : serverId;
        this.currentPage = Math.max(0, currentPage);
        this.maxPage = Math.max(0, maxPage);
        this.usedCreative = usedCreative;
        this.pages = copyPages(pages);
        this.pageCount = this.pages.size();
        this.creativeItems = usedCreative ? copyItems(creativeItems) : Collections.emptyList();
        this.armor = copyItems(padded(armor, 4));
        this.offhand = offhand == null ? null : InventoryPagesPlus.nms.getItemStack(offhand);
        this.hotbar = copyItems(padded(hotbar, 9));
    }

    public String getId() {
        return id;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public SnapshotReason getReason() {
        return reason;
    }

    public String getServerId() {
        return serverId;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getMaxPage() {
        return maxPage;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isUsedCreative() {
        return usedCreative;
    }

    public Map<Integer, List<ItemStack>> getPages() {
        return copyPages(pages);
    }

    public NavigableSet<Integer> getPageNumbers() {
        return Collections.unmodifiableNavigableSet(new TreeSet<>(pages.keySet()));
    }

    public List<ItemStack> getPageItems(int page) {
        return copyItems(pages.get(page));
    }

    public List<ItemStack> getCreativeItems() {
        return copyItems(creativeItems);
    }

    public List<ItemStack> getArmor() {
        return copyItems(armor);
    }

    public ItemStack getOffhand() {
        return offhand == null ? null : InventoryPagesPlus.nms.getItemStack(offhand);
    }

    public List<ItemStack> getHotbar() {
        return copyItems(hotbar);
    }

    private static List<ItemStack> padded(List<ItemStack> source, int size) {
        List<ItemStack> padded = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++)
            padded.add(source != null && slot < source.size() ? source.get(slot) : null);
        return padded;
    }

    private static Map<Integer, List<ItemStack>> copyPages(Map<Integer, List<ItemStack>> source) {
        Map<Integer, List<ItemStack>> copy = new TreeMap<>();
        if (source != null) {
            for (Map.Entry<Integer, List<ItemStack>> entry : source.entrySet())
                copy.put(entry.getKey(), copyItems(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        if (source == null || source.isEmpty())
            return Collections.emptyList();
        List<ItemStack> copy = new ArrayList<>(source.size());
        // Unmodifiable containers alone do not protect mutable ItemStacks.
        for (ItemStack item : source)
            copy.add(item == null ? null : InventoryPagesPlus.nms.getItemStack(item));
        return Collections.unmodifiableList(copy);
    }
}
