package me.cortezromeo.inventorypagesplus.inventory.history;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.file.inventory.HistoryInventoryFile;
import me.cortezromeo.inventorypagesplus.history.*;
import me.cortezromeo.inventorypagesplus.inventory.CustomInventoryBase;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.*;

public class InventoryHistoryGui extends CustomInventoryBase {
    // Left 5x5 grid is the virtual page; column 5 is armor, slot 6 offhand, bottom row the hotbar.
    private static final int[] PAGE_SLOTS = {0, 1, 2, 3, 4, 9, 10, 11, 12, 13, 18, 19, 20, 21, 22,
            27, 28, 29, 30, 31, 36, 37, 38, 39, 40};
    private static final int[] ARMOR_SLOTS = {5, 14, 23, 32};
    private static final int OFFHAND_SLOT = 6;
    private static final int HOTBAR_START = 45;
    private static final Map<String, Integer> DEFAULT_SLOTS = Map.ofEntries(
            Map.entry("close", 43), Map.entry("prevPage", 25), Map.entry("nextPage", 26),
            Map.entry("prevChange", 16), Map.entry("nextChange", 17),
            Map.entry("prevDay", 15), Map.entry("nextDay", 24), Map.entry("info", 7),
            Map.entry("restore", 34), Map.entry("emptyPage", 22), Map.entry("noHistory", 22));

    private final String targetUUID;
    private final String targetName;
    private final Map<Integer, String> controls = new HashMap<>();
    private final Set<String> warnings = new HashSet<>();
    private final int initialPage;
    private InventorySnapshotSummary summary;
    private String renderedTitle;
    private InventorySnapshot snapshot;
    private int page;
    private boolean loading = true;
    private boolean restoring;
    private boolean actionQueued;
    private int requestNumber;
    private int renderNumber;
    private int usedSlots;
    private UUID session;

    public InventoryHistoryGui(Player owner, String targetUUID, String targetName,
                               InventorySnapshotSummary summary, int initialPage) {
        super(owner);
        this.targetUUID = UUID.fromString(targetUUID).toString();
        if (summary == null || !this.targetUUID.equalsIgnoreCase(summary.playerUUID()))
            throw new IllegalArgumentException("History summary belongs to a different player.");
        this.targetName = targetName;
        this.summary = summary;
        this.initialPage = initialPage;
    }

    private static void completeSnapshot(UUID viewerId, UUID session, int request,
                                         int desiredPage, InventorySnapshot result) {
        InventoryHistoryGui gui = activeGui(viewerId, session);
        if (gui == null || gui.requestNumber != request)
            return;
        if (!gui.canView()) {
            gui.owner.closeInventory();
            return;
        }
        gui.loading = false;
        InventorySnapshotSummary summary = gui.summary;
        if (result == null || !gui.targetUUID.equalsIgnoreCase(result.getPlayerUUID())
                || !summary.id().equals(result.getId()) || summary.createdAt() != result.getCreatedAt()) {
            gui.snapshot = null;
            MessageUtil.sendMessage(gui.owner, Messages.COMMAND_HISTORY_LOAD_FAILED);
        } else {
            gui.snapshot = result;
            int desired = desiredPage >= 0 ? desiredPage : result.getCurrentPage();
            gui.page = Math.min(desired, result.getMaxPage());
        }
        gui.refresh();
    }

    private static InventoryHistoryGui activeGui(UUID viewerId, UUID session) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline())
            return null;
        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof InventoryHistoryGui gui && session.equals(gui.session))
            return gui;
        return null;
    }

    private static boolean isReal(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static boolean isItemSlot(int slot) {
        for (int pageSlot : PAGE_SLOTS)
            if (pageSlot == slot)
                return true;
        for (int armorSlot : ARMOR_SLOTS)
            if (armorSlot == slot)
                return true;
        return slot == OFFHAND_SLOT || (slot >= HOTBAR_START && slot < HOTBAR_START + 9);
    }

    @Override
    public void open() {
        if (!canView())
            return;
        session = UUID.randomUUID();
        requestNumber++;
        loading = snapshot == null;
        renderedTitle = getMenuTitle();
        super.open();
        owner.updateInventory();
        if (snapshot == null)
            requestSnapshot(initialPage);
    }

    @Override
    public String getMenuTitle() {
        return InventoryPagesPlus.nms.addColor(applyPlaceholders(HistoryInventoryFile.get().getString("title", "")));
    }

    @Override
    public int getSlots() {
        return 6 * 9;
    }

    @Override
    public boolean isReadOnly() {
        // Vanilla interaction stays cancelled; permitted admins receive copies through handleMenu instead.
        return true;
    }

    @Override
    public void setMenuItems() {
        inventory.clear();
        controls.clear();
        actionQueued = false;
        renderNumber++;
        usedSlots = 0;
        int pageUsed = 0;
        if (!loading && snapshot != null) {
            List<ItemStack> items = snapshot.getPageItems(page);
            for (int index = 0; index < PAGE_SLOTS.length; index++) {
                ItemStack item = index < items.size() ? items.get(index) : null;
                if (isReal(item)) {
                    inventory.setItem(PAGE_SLOTS[index], InventoryPagesPlus.nms.getItemStack(item));
                    usedSlots++;
                    pageUsed++;
                }
            }
            ItemStack emptySlot = applyItem("items.emptySlot");
            List<ItemStack> armor = snapshot.getArmor();
            for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                ItemStack item = index < armor.size() ? armor.get(index) : null;
                if (isReal(item)) {
                    inventory.setItem(ARMOR_SLOTS[index], InventoryPagesPlus.nms.getItemStack(item));
                    usedSlots++;
                } else if (emptySlot != null)
                    inventory.setItem(ARMOR_SLOTS[index], emptySlot);
            }
            ItemStack offhand = snapshot.getOffhand();
            if (isReal(offhand)) {
                inventory.setItem(OFFHAND_SLOT, InventoryPagesPlus.nms.getItemStack(offhand));
                usedSlots++;
            } else if (emptySlot != null)
                inventory.setItem(OFFHAND_SLOT, emptySlot);
            List<ItemStack> hotbar = snapshot.getHotbar();
            for (int index = 0; index < 9; index++) {
                ItemStack item = index < hotbar.size() ? hotbar.get(index) : null;
                if (isReal(item)) {
                    inventory.setItem(HOTBAR_START + index, InventoryPagesPlus.nms.getItemStack(item));
                    usedSlots++;
                } else if (emptySlot != null)
                    inventory.setItem(HOTBAR_START + index, emptySlot);
            }
        }

        ItemStack border = applyItem("items.border");
        for (int slot : HistoryInventoryFile.get().getIntegerList("items.border.slots")) {
            if (validSlot(slot, false))
                inventory.setItem(slot, border);
            else
                warn("Ignoring invalid history border slot: " + slot);
        }

        if (loading || snapshot == null) {
            putItem("noHistory", true);
            putControl("prevPage", false);
            putControl("nextPage", false);
            putControl("prevChange", false);
            putControl("nextChange", false);
            putControl("close", true);
            return;
        }

        if (pageUsed == 0)
            putItem("emptyPage", true);
        putItem("info", false);
        putControl("prevPage", !restoring && page > 0);
        putControl("nextPage", !restoring && page < snapshot.getMaxPage());
        putControl("prevDay", !restoring);
        putControl("nextDay", !restoring);
        putControl("prevChange", !restoring);
        putControl("nextChange", !restoring);
        if (Settings.HISTORY_RESTORE_ENABLED)
            putControl("restore", !restoring && Bukkit.getPlayer(UUID.fromString(targetUUID)) != null);
        putControl("close", true);
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (event instanceof InventoryCreativeEvent || event.getAction() == InventoryAction.CLONE_STACK
                || event.getClick() == ClickType.MIDDLE || event.getClickedInventory() != inventory
                || !event.getWhoClicked().getUniqueId().equals(owner.getUniqueId()))
            return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || actionQueued)
            return;
        int rawSlot = event.getRawSlot();
        String action = controls.get(rawSlot);
        // Snapshot items can carry arbitrary PDC; only rendered control slots have authority.
        if (action != null) {
            if (action.equals(InventoryPagesPlus.nms.getCustomData(item)))
                queueAction(action);
            return;
        }
        if (isItemSlot(rawSlot) && hasItemAt(rawSlot))
            queueExtraction(rawSlot);
    }

    private void queueAction(String action) {
        actionQueued = true;
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        int expectedRender = renderNumber;
        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
            InventoryHistoryGui gui = activeGui(viewerId, expectedSession);
            if (gui == null)
                return;
            gui.actionQueued = false;
            if (gui.renderNumber != expectedRender)
                return;
            gui.performAction(action);
        });
    }

    private void queueExtraction(int rawSlot) {
        if (!canExtract())
            return;
        int[] address = itemAddress(rawSlot);
        if (address == null)
            return;
        actionQueued = true;
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        int expectedRender = renderNumber;
        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
            InventoryHistoryGui gui = activeGui(viewerId, expectedSession);
            if (gui == null)
                return;
            gui.actionQueued = false;
            if (gui.renderNumber != expectedRender)
                return;
            gui.performExtraction(address);
        });
    }

    private void performAction(String action) {
        if ("close".equals(action)) {
            owner.closeInventory();
            return;
        }
        if (!canView()) {
            owner.closeInventory();
            return;
        }
        if (loading || restoring || snapshot == null)
            return;
        switch (action) {
            case "prevPage":
            case "nextPage":
                page = "prevPage".equals(action) ? Math.max(0, page - 1) : Math.min(snapshot.getMaxPage(), page + 1);
                refresh();
                break;
            case "prevChange":
            case "nextChange":
            case "prevDay":
            case "nextDay":
                navigate(action.startsWith("prev"), action.endsWith("Day"));
                break;
            case "restore":
                restore();
                break;
            default:
                break;
        }
    }

    private void performExtraction(int[] address) {
        if (!canView()) {
            owner.closeInventory();
            return;
        }
        if (loading || restoring || snapshot == null || !canExtract())
            return;
        actionQueued = true;
        SnapshotSlotType type = SnapshotSlotType.values()[address[0]];
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        InventoryPagesPlus.getHistoryManager().extractItem(owner, targetUUID, snapshot.getId(),
                snapshot.getCreatedAt(), type, address[1], address[2], taken -> {
                    InventoryHistoryGui gui = activeGui(viewerId, expectedSession);
                    if (gui == null)
                        return;
                    gui.actionQueued = false;
                    MessageUtil.sendMessage(gui.owner, taken != null
                            ? Messages.COMMAND_HISTORY_EXTRACT_SUCCESS
                            .replace("%player%", gui.targetName)
                            .replace("%item%", taken.getType().name())
                            .replace("%amount%", String.valueOf(taken.getAmount()))
                            : Messages.COMMAND_HISTORY_EXTRACT_FAILED);
                });
    }

    private void navigate(boolean older, boolean otherDay) {
        actionQueued = true;
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        int request = ++requestNumber;
        LocalDate date = HistoryDates.date(summary.createdAt());
        long from = otherDay ? (older ? HistoryDates.cutoff() : HistoryDates.start(date.plusDays(1))) : HistoryDates.start(date);
        long to = otherDay ? (older ? HistoryDates.start(date) : Long.MAX_VALUE) : HistoryDates.start(date.plusDays(1));
        InventoryPagesPlus.getHistoryManager().loadAdjacent(summary, from, to, older, result -> {
            InventoryHistoryGui gui = activeGui(viewerId, expectedSession);
            if (gui == null || gui.requestNumber != request)
                return;
            gui.actionQueued = false;
            if (!gui.canView()) {
                gui.owner.closeInventory();
                return;
            }
            if (result == null) {
                MessageUtil.sendMessage(gui.owner, Messages.COMMAND_HISTORY_NO_MORE);
                return;
            }
            gui.summary = result;
            gui.snapshot = null;
            gui.loading = true;
            gui.refresh();
            gui.requestSnapshot(gui.page);
        });
    }

    private void requestSnapshot(int desiredPage) {
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        int request = ++requestNumber;
        InventoryPagesPlus.getHistoryManager().loadSnapshot(summary,
                result -> completeSnapshot(viewerId, expectedSession, request, desiredPage, result));
    }

    private void refresh() {
        String title = getMenuTitle();
        if (!title.equals(renderedTitle) && owner.isOnline() && owner.getOpenInventory().getTopInventory() == inventory) {
            renderedTitle = title;
            inventory = Bukkit.createInventory(this, getSlots(), title);
            setMenuItems();
            owner.openInventory(inventory);
        } else {
            setMenuItems();
        }
        if (owner.isOnline() && owner.getOpenInventory().getTopInventory() == inventory)
            owner.updateInventory();
    }

    private boolean canView() {
        InventoryHistoryManager manager = InventoryPagesPlus.getHistoryManager();
        String error = null;
        if (!Settings.HISTORY_ENABLED)
            error = Messages.COMMAND_HISTORY_DISABLED;
        else if (manager == null || !manager.isAvailable())
            error = Messages.COMMAND_HISTORY_LOAD_FAILED;
        else if (!owner.hasPermission(Settings.HISTORY_PERMISSION))
            error = Messages.COMMAND_HISTORY_NO_PERMISSION;
        else if (!owner.getUniqueId().toString().equalsIgnoreCase(targetUUID)
                && !owner.hasPermission(Settings.HISTORY_PERMISSION_OTHERS))
            error = Messages.COMMAND_HISTORY_NO_PERMISSION_OTHERS;
        if (error == null)
            return true;
        MessageUtil.sendMessage(owner, error);
        return false;
    }

    private boolean canRestore() {
        if (!Settings.HISTORY_RESTORE_ENABLED) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_HISTORY_RESTORE_DISABLED);
            return false;
        }
        if (!owner.hasPermission(Settings.HISTORY_PERMISSION_RESTORE)) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_HISTORY_RESTORE_NO_PERMISSION);
            return false;
        }
        return true;
    }

    private boolean canExtract() {
        if (!Settings.HISTORY_EXTRACTION_ENABLED) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_HISTORY_EXTRACT_DISABLED);
            return false;
        }
        if (!owner.hasPermission(Settings.HISTORY_PERMISSION_RESTORE)) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_HISTORY_EXTRACT_NO_PERMISSION);
            return false;
        }
        return true;
    }

    private void restore() {
        if (!canRestore())
            return;
        Player target = Bukkit.getPlayer(UUID.fromString(targetUUID));
        if (target == null || !target.isOnline() || !targetUUID.equalsIgnoreCase(snapshot.getPlayerUUID())) {
            MessageUtil.sendMessage(owner, Messages.COMMAND_HISTORY_RESTORE_FAILED);
            return;
        }
        restoring = true;
        refresh();
        UUID viewerId = owner.getUniqueId();
        UUID expectedSession = session;
        InventoryPagesPlus.getHistoryManager().restore(target, snapshot, success -> {
            InventoryHistoryGui gui = activeGui(viewerId, expectedSession);
            if (gui == null)
                return;
            gui.restoring = false;
            if (!gui.canView()) {
                gui.owner.closeInventory();
                return;
            }
            if (gui.canRestore())
                MessageUtil.sendMessage(gui.owner, (Boolean.TRUE.equals(success)
                        ? Messages.COMMAND_HISTORY_RESTORE_SUCCESS : Messages.COMMAND_HISTORY_RESTORE_FAILED)
                        .replace("%player%", gui.targetName));
            gui.refresh();
        });
    }

    private int[] itemAddress(int rawSlot) {
        for (int index = 0; index < PAGE_SLOTS.length; index++)
            if (PAGE_SLOTS[index] == rawSlot)
                return new int[]{SnapshotSlotType.PAGE.ordinal(), page, index};
        for (int index = 0; index < ARMOR_SLOTS.length; index++)
            if (ARMOR_SLOTS[index] == rawSlot)
                return new int[]{SnapshotSlotType.ARMOR.ordinal(), 0, index};
        if (rawSlot == OFFHAND_SLOT)
            return new int[]{SnapshotSlotType.OFFHAND.ordinal(), 0, 0};
        if (rawSlot >= HOTBAR_START && rawSlot < HOTBAR_START + 9)
            return new int[]{SnapshotSlotType.HOTBAR.ordinal(), 0, rawSlot - HOTBAR_START};
        return null;
    }

    private boolean hasItemAt(int rawSlot) {
        if (loading || snapshot == null)
            return false;
        int[] address = itemAddress(rawSlot);
        if (address == null)
            return false;
        switch (SnapshotSlotType.values()[address[0]]) {
            case PAGE:
                List<ItemStack> items = snapshot.getPageItems(page);
                return address[2] < items.size() && isReal(items.get(address[2]));
            case ARMOR:
                List<ItemStack> armor = snapshot.getArmor();
                return address[2] < armor.size() && isReal(armor.get(address[2]));
            case OFFHAND:
                return isReal(snapshot.getOffhand());
            case HOTBAR:
                List<ItemStack> hotbar = snapshot.getHotbar();
                return address[2] < hotbar.size() && isReal(hotbar.get(address[2]));
            default:
                return false;
        }
    }

    private boolean validSlot(int slot, boolean contentAllowed) {
        return slot >= 0 && slot < getSlots() && (contentAllowed || !isItemSlot(slot));
    }

    private int configuredSlot(String key, boolean contentAllowed) {
        String path = "items." + key + ".slot";
        FileConfiguration config = HistoryInventoryFile.get();
        if (config.isInt(path) && validSlot(config.getInt(path), contentAllowed))
            return config.getInt(path);
        Integer fallback = DEFAULT_SLOTS.get(key);
        if (fallback != null && validSlot(fallback, contentAllowed)) {
            warn("Ignoring invalid history item slot: " + path + "; using default " + fallback + ".");
            return fallback;
        }
        warn("Ignoring invalid history item slot: " + path);
        return -1;
    }

    private void putItem(String key, boolean contentAllowed) {
        int slot = configuredSlot(key, contentAllowed);
        if (slot < 0)
            return;
        controls.remove(slot);
        inventory.setItem(slot, applyItem("items." + key));
    }

    private void putControl(String action, boolean enabled) {
        int slot = configuredSlot(action, false);
        if (slot < 0)
            return;
        controls.remove(slot);
        ItemStack item = applyItem("items." + (enabled ? action : "disabled"));
        inventory.setItem(slot, item);
        if (enabled && item != null && !item.getType().isAir()
                && action.equals(InventoryPagesPlus.nms.getCustomData(item)))
            controls.put(slot, action);
    }

    private ItemStack applyItem(String path) {
        FileConfiguration config = HistoryInventoryFile.get();
        String type = config.getString(path + ".type");
        String value = config.getString(path + ".value");
        if (type == null || value == null) {
            warn("Missing history item configuration: " + path);
            return null;
        }
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(path + ".lore"))
            lore.add(applyPlaceholders(line));
        try {
            ItemStack item = ItemUtil.getItem(type, value, (short) config.getInt(path + ".data"),
                    config.getInt(path + ".customModelData"),
                    applyPlaceholders(config.getString(path + ".name", "")), lore);
            String direct = config.getString(path + ".direct", "");
            if (path.equals("items." + direct) && (direct.equals("close") || direct.equals("prevPage")
                    || direct.equals("nextPage") || direct.equals("prevChange")
                    || direct.equals("nextChange") || direct.equals("prevDay") || direct.equals("nextDay")
                    || direct.equals("restore")))
                return InventoryPagesPlus.nms.addCustomData(item, direct);
            return item;
        } catch (RuntimeException exception) {
            warn("Invalid history item configuration: " + path + " (" + exception.getMessage() + ")");
            return null;
        }
    }

    private String applyPlaceholders(String text) {
        String date = HistoryDates.format(HistoryDates.date(summary.createdAt()));
        long maxPage = snapshot == null ? 1L : Math.max(1L, (long) snapshot.getMaxPage() + 1L);
        String extractStatus = Settings.HISTORY_EXTRACTION_ENABLED && owner.hasPermission(Settings.HISTORY_PERMISSION_RESTORE)
                ? Messages.COMMAND_HISTORY_EXTRACT_HINT : Messages.COMMAND_HISTORY_EXTRACT_VIEW_ONLY;
        return text.replace("%player%", targetName)
                .replace("%date%", date)
                .replace("%page%", String.valueOf((long) page + 1L))
                .replace("%max_page%", String.valueOf(maxPage))
                .replace("%used_slots%", String.valueOf(usedSlots))
                .replace("%extract_status%", extractStatus == null ? "" : extractStatus);
    }

    private void warn(String message) {
        if (warnings.add(message))
            InventoryPagesPlus.plugin.getLogger().warning(message);
    }
}
