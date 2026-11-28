package me.cortezromeo.inventorypagesplus.history;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.manager.DatabaseManager;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.storage.*;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import me.cortezromeo.inventorypagesplus.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.h2.jdbc.JdbcConnection;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public class InventoryHistoryManager {
    private static final String EXECUTOR_THREAD_NAME = "InventoryPagesPlus-History";

    private final ConcurrentHashMap<String, String> lastHashCache = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Boolean>> pendingSaves = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingPeriodic = ConcurrentHashMap.newKeySet();
    private final Set<UUID> restoring = new HashSet<>();
    private final AtomicBoolean maintenancePending = new AtomicBoolean();
    private ExecutorService executor;
    private InventoryHistoryStorage storage;
    private BukkitTask periodicTask;
    private BukkitTask sweepTask;
    private BukkitTask maintenanceTask;
    private HistoryRetention retention;
    private volatile boolean available;
    private volatile boolean initializing;
    private volatile boolean stopping;
    private boolean sweepRunning;

    private static String[] serialize(InventorySnapshot snapshot) {
        String[] json = {
                StringUtil.pagesToJson(snapshot.getPages()),
                StringUtil.itemsToJson(snapshot.getCreativeItems()),
                StringUtil.itemsToJson(snapshot.getArmor(), 4),
                StringUtil.itemToJson(snapshot.getOffhand()),
                StringUtil.itemsToJson(snapshot.getHotbar(), 9)
        };
        for (String value : json)
            if (value == null)
                throw new IllegalStateException("Snapshot serialization failed; refusing a partial snapshot.");
        return json;
    }

    private static String contentHash(InventorySnapshot snapshot, String[] json) throws Exception {
        String content = json[0] + "|" + json[1] + "|" + json[2] + "|" + json[3] + "|" + json[4]
                + "|" + snapshot.getCurrentPage() + "|" + snapshot.getMaxPage() + "|" + snapshot.isUsedCreative()
                + "|" + HistoryDates.date(snapshot.getCreatedAt());
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest) {
            hash.append(Character.forDigit((value & 255) >>> 4, 16));
            hash.append(Character.forDigit(value & 15, 16));
        }
        return hash.toString();
    }

    private static ItemStack itemAt(InventorySnapshot snapshot, SnapshotSlotType type, int page, int slot) {
        ItemStack item;
        switch (type) {
            case PAGE:
                List<ItemStack> items = snapshot.getPageItems(page);
                item = slot >= 0 && slot < items.size() ? items.get(slot) : null;
                break;
            case ARMOR:
                List<ItemStack> armor = snapshot.getArmor();
                item = slot >= 0 && slot < armor.size() ? armor.get(slot) : null;
                break;
            case OFFHAND:
                item = snapshot.getOffhand();
                break;
            case HOTBAR:
                List<ItemStack> hotbar = snapshot.getHotbar();
                item = slot >= 0 && slot < hotbar.size() ? hotbar.get(slot) : null;
                break;
            default:
                item = null;
                break;
        }
        return item == null || item.getType().isAir() ? null : item;
    }

    public boolean isAvailable() {
        return available && Settings.HISTORY_ENABLED && !stopping;
    }

    public void init() {
        if (!Settings.HISTORY_ENABLED || available || initializing || stopping)
            return;
        initializing = true;
        DatabaseType type = InventoryPagesPlus.databaseType;
        String configuredType = Settings.DATABASE_TYPE;
        String table = type == DatabaseType.MYSQL ? Settings.DATABASE_SETTINGS_MYSQL_HISTORY_TABLE
                : Settings.DATABASE_SETTINGS_H2_HISTORY_TABLE;
        // Absolute path: "./" + an already-absolute dataFolder is an invalid H2 path on Windows.
        File databaseFolder = new File(InventoryPagesPlus.plugin.getDataFolder(), "database");
        databaseFolder.mkdirs();
        String h2Url = "jdbc:h2:" + databaseFolder.getAbsolutePath().replace('\\', '/')
                + "/" + Settings.DATABASE_SETTINGS_H2_FILE_NAME + ";mode=MySQL";
        String mysqlUrl = "jdbc:mysql://" + Settings.DATABASE_SETTINGS_MYSQL_HOST + ":"
                + Settings.DATABASE_SETTINGS_MYSQL_PORT + "/" + Settings.DATABASE_SETTINGS_MYSQL_NAME;
        String user = Settings.DATABASE_SETTINGS_MYSQL_USER;
        String password = Settings.DATABASE_SETTINGS_MYSQL_PASSWORD;
        InventoryHistoryStorage[] ownedStorage = new InventoryHistoryStorage[1];
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Settings.HISTORY_MAX_PENDING), task -> {
            Thread thread = new Thread(() -> {
                try {
                    task.run();
                } finally {
                    // The connection is closed by its owning worker, including interrupted shutdowns.
                    try {
                        if (ownedStorage[0] != null)
                            ownedStorage[0].close();
                    } finally {
                        if (retention != null) {
                            try {
                                retention.close();
                            } catch (Exception exception) {
                                failure("close archive scan", exception);
                            }
                        }
                    }
                }
            }, EXECUTOR_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(() -> {
            Connection connection = null;
            try {
                if (type == null || !type.name().equalsIgnoreCase(configuredType))
                    throw new IllegalStateException("Primary storage fell back; history will not change backend silently.");
                if (type == DatabaseType.YAML) {
                    storage = new InventoryHistoryYAMLStorage();
                } else {
                    connection = type == DatabaseType.H2
                            ? new JdbcConnection(h2Url, new Properties(), null, null, false)
                            : DriverManager.getConnection(mysqlUrl, user, password);
                    connection.setAutoCommit(true);
                    storage = new InventoryHistorySQLStorage(connection, type, table);
                }
                ownedStorage[0] = storage;
                retention = new HistoryRetention(InventoryPagesPlus.plugin.getDataFolder().toPath());
                available = !stopping;
                onMain(() -> {
                    schedulePeriodic();
                    DebugManager.debug("HISTORY", "Initialized inventory history.");
                });
            } catch (Exception exception) {
                available = false;
                if (storage == null && connection != null) {
                    try {
                        connection.close();
                    } catch (Exception closeException) {
                        exception.addSuppressed(closeException);
                    }
                }
                failure("initialize", exception);
                executor.shutdown();
            } finally {
                initializing = false;
            }
        });
    }

    public void reload() {
        cancelTasks();
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.execute(lastHashCache::clear);
            } catch (RejectedExecutionException ignored) {
                lastHashCache.clear();
            }
        }
        if (!Settings.HISTORY_ENABLED)
            return;
        if (available)
            schedulePeriodic();
        else if (!initializing)
            init();
    }

    public void shutdown() {
        cancelTasks();
        if (isAvailable() && Settings.HISTORY_SNAPSHOT_ON_DISABLE) {
            for (Player player : Bukkit.getOnlinePlayers())
                capture(player, SnapshotReason.DISABLE, true);
        }
        stopping = true;
        available = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    InventoryPagesPlus.plugin.getLogger().warning("History shutdown timed out; queued snapshots may be unsaved.");
                }
            } catch (InterruptedException exception) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        pendingSaves.forEach(future -> future.complete(false));
        pendingSaves.clear();
        pendingPeriodic.clear();
        lastHashCache.clear();
        restoring.clear();
    }

    public CompletableFuture<Boolean> capture(Player player, SnapshotReason reason, boolean force) {
        if (player == null || !isAvailable())
            return CompletableFuture.completedFuture(false);
        if (!Bukkit.isPrimaryThread()) {
            UUID uuid = player.getUniqueId();
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (!onMain(() -> capture(Bukkit.getPlayer(uuid), reason, force)
                    .whenComplete((saved, error) -> result.complete(error == null && Boolean.TRUE.equals(saved)))))
                result.complete(false);
            return result;
        }
        if (!player.isOnline())
            return CompletableFuture.completedFuture(false);
        PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getUniqueId());
        if (data == null)
            return CompletableFuture.completedFuture(false);
        if (!data.isProtectionLocked())
            data.saveCurrentPage();
        return capture(data, reason, force, player);
    }

    public CompletableFuture<Boolean> capture(PlayerInventoryDatabase data, SnapshotReason reason, boolean force) {
        return capture(data, reason, force, null);
    }

    public CompletableFuture<Boolean> capture(PlayerInventoryDatabase data, SnapshotReason reason, boolean force,
                                              Player equipmentSource) {
        if (data == null || !isAvailable() || pendingSaves.size() >= Settings.HISTORY_MAX_PENDING)
            return CompletableFuture.completedFuture(false);
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (!onMain(() -> capture(data, reason, force, equipmentSource)
                    .whenComplete((saved, error) -> result.complete(error == null && Boolean.TRUE.equals(saved)))))
                result.complete(false);
            return result;
        }
        try {
            return saveAsync(snapshot(data, reason, equipmentSource), force);
        } catch (Exception exception) {
            failure("capture", exception);
            return CompletableFuture.completedFuture(false);
        }
    }

    private InventorySnapshot snapshot(PlayerInventoryDatabase data, SnapshotReason reason, Player player) {
        Map<Integer, List<ItemStack>> pages = new TreeMap<>();
        pages.putAll(data.getItems());
        boolean usedCreative = data.hasUsedCreative();
        List<ItemStack> armor = new ArrayList<>(4);
        ItemStack offhand = null;
        List<ItemStack> hotbar = new ArrayList<>(9);
        // Equipment lives in the vanilla inventory, so it is only readable while the player is online.
        if (player != null && player.isOnline()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack[] worn = inventory.getArmorContents();
            for (int slot = worn.length - 1; slot >= 0; slot--)
                armor.add(InventoryPagesPlus.nms.getItemStack(worn[slot]));
            offhand = InventoryPagesPlus.nms.getItemStack(inventory.getItemInOffHand());
            for (int slot = 0; slot < 9; slot++)
                hotbar.add(InventoryPagesPlus.nms.getItemStack(inventory.getItem(slot)));
        }
        return new InventorySnapshot(UUID.randomUUID().toString(), data.getPlayerUUID(), data.getPlayerName(),
                System.currentTimeMillis(), reason, Settings.HISTORY_SERVER_ID, data.getCurrentPage(),
                usedMaxPage(data), usedCreative, pages,
                data.getCreativeItems(), armor, offhand, hotbar);
    }

    // Pages above the stored max page exist as soon as a permission grants them, so the snapshot
    // must also cover every page that actually holds items or the GUI cannot navigate to them.
    private static int usedMaxPage(PlayerInventoryDatabase data) {
        int maxPage = data.getMaxPage();
        for (Map.Entry<Integer, ArrayList<ItemStack>> page : data.getItems().entrySet())
            if (page.getKey() > maxPage && page.getValue().stream()
                    .anyMatch(item -> item != null && !item.getType().isAir()))
                maxPage = page.getKey();
        return maxPage;
    }

    private CompletableFuture<Boolean> saveAsync(InventorySnapshot snapshot, boolean force) {
        if (pendingSaves.size() >= Settings.HISTORY_MAX_PENDING)
            return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        pendingSaves.add(result);
        try {
            executor.execute(() -> {
                try {
                    String[] json = serialize(snapshot);
                    long size = 0;
                    for (String value : json)
                        size += value.getBytes(StandardCharsets.UTF_8).length;
                    // Reserve room for escaping and metadata when exporting the stored row.
                    if (size > Settings.HISTORY_MAX_RECORD_BYTES / 2)
                        throw new IllegalStateException("History record exceeds configured size limit.");
                    String contentHash = contentHash(snapshot, json);
                    if (lastHashCache.size() >= 10000)
                        lastHashCache.clear();
                    String key = snapshot.getPlayerUUID() + "|" + snapshot.getServerId();
                    boolean important = snapshot.getReason() == SnapshotReason.MANUAL
                            || snapshot.getReason() == SnapshotReason.BEFORE_CLEAR
                            || snapshot.getReason() == SnapshotReason.BEFORE_CLEAR_ALL
                            || snapshot.getReason() == SnapshotReason.BEFORE_RESTORE
                            || snapshot.getReason() == SnapshotReason.DISABLE;
                    String previous = lastHashCache.get(key);
                    if (!force && !important) {
                        if (previous == null)
                            previous = storage.getLastContentHash(snapshot.getPlayerUUID(), snapshot.getServerId());
                        if (contentHash.equals(previous)) {
                            lastHashCache.put(key, contentHash);
                            result.complete(true);
                            return;
                        }
                    }
                    storage.save(snapshot, json[0], json[1], json[2], json[3], json[4], contentHash);
                    lastHashCache.put(key, contentHash);
                    result.complete(true);
                } catch (Exception exception) {
                    failure("save", exception);
                    result.complete(false);
                } finally {
                    pendingSaves.remove(result);
                }
            });
        } catch (RejectedExecutionException exception) {
            pendingSaves.remove(result);
            result.complete(false);
        }
        return result;
    }

    public void extractItem(Player admin, String playerUUID, String snapshotId, long createdAt,
                            SnapshotSlotType type, int page, int slot, Consumer<ItemStack> callback) {
        if (!Bukkit.isPrimaryThread()) {
            UUID adminId = admin == null ? null : admin.getUniqueId();
            onMain(() -> extractItem(adminId == null ? null : Bukkit.getPlayer(adminId), playerUUID,
                    snapshotId, createdAt, type, page, slot, callback));
            return;
        }
        if (!isAvailable() || !Settings.HISTORY_EXTRACTION_ENABLED || admin == null || !admin.isOnline()
                || !admin.hasPermission(Settings.HISTORY_PERMISSION_RESTORE) || type == null) {
            callback.accept(null);
            return;
        }
        UUID adminId = admin.getUniqueId();
        String serverId = Settings.HISTORY_SERVER_ID;
        try {
            executor.execute(() -> {
                ItemStack taken = null;
                try {
                    // Reload from storage so the copy always comes from the persisted snapshot.
                    InventorySnapshot original = storage.get(playerUUID, snapshotId, createdAt);
                    if (original == null || (!serverId.isEmpty() && !serverId.equals(original.getServerId())))
                        throw new IllegalStateException("Snapshot is no longer available.");
                    // Snapshot getters already return defensive clones; the snapshot stays intact.
                    taken = itemAt(original, type, page, slot);
                } catch (Exception exception) {
                    failure("extract item", exception);
                }
                ItemStack result = taken;
                onMain(() -> {
                    if (result == null) {
                        callback.accept(null);
                        return;
                    }
                    Player viewer = Bukkit.getPlayer(adminId);
                    if (viewer == null || !viewer.isOnline() || !isAvailable() || !Settings.HISTORY_EXTRACTION_ENABLED
                            || !viewer.hasPermission(Settings.HISTORY_PERMISSION_RESTORE)) {
                        callback.accept(null);
                        return;
                    }
                    Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(InventoryPagesPlus.nms.getItemStack(result));
                    for (ItemStack drop : overflow.values())
                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
                    callback.accept(result);
                });
            });
        } catch (RejectedExecutionException exception) {
            callback.accept(null);
        }
    }

    private void schedulePeriodic() {
        cancelTasks();
        if (!isAvailable())
            return;
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(InventoryPagesPlus.plugin, this::scheduleMaintenance,
                20L, Settings.HISTORY_RETENTION_INTERVAL_SECONDS * 20L);
        if (Settings.HISTORY_SNAPSHOT_INTERVAL_SECONDS == 0)
            return;
        long interval = Settings.HISTORY_SNAPSHOT_INTERVAL_SECONDS * 20L;
        periodicTask = Bukkit.getScheduler().runTaskTimer(InventoryPagesPlus.plugin, () -> {
            if (!isAvailable() || sweepRunning || Bukkit.getOnlinePlayers().isEmpty())
                return;
            List<UUID> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers())
                players.add(player.getUniqueId());
            sweepRunning = true;
            sweepTask = new BukkitRunnable() {
                private int index;

                @Override
                public void run() {
                    int batch = Settings.HISTORY_SNAPSHOT_PLAYERS_PER_TICK;
                    while (isAvailable() && batch-- > 0 && index < players.size()) {
                        Player player = Bukkit.getPlayer(players.get(index++));
                        if (player == null)
                            continue;
                        // One pending periodic save per player bounds the queue when storage is slower than the sweep.
                        String key = player.getUniqueId().toString();
                        if (pendingPeriodic.add(key))
                            capture(player, SnapshotReason.PERIODIC, false)
                                    .whenComplete((saved, error) -> pendingPeriodic.remove(key));
                        else
                            DebugManager.debug("HISTORY", "Skipped periodic snapshot for " + player.getName()
                                    + "; the previous save is still pending.");
                    }
                    if (!isAvailable() || index >= players.size()) {
                        sweepRunning = false;
                        sweepTask = null;
                        cancel();
                    }
                }
            }.runTaskTimer(InventoryPagesPlus.plugin, 1L, 1L);
        }, interval, interval);
    }

    private void scheduleMaintenance() {
        if (!isAvailable() || !maintenancePending.compareAndSet(false, true))
            return;
        long cutoff = HistoryDates.cutoff();
        String serverId = Settings.HISTORY_SERVER_ID;
        int batch = Settings.HISTORY_RETENTION_BATCH_SIZE;
        boolean archive = Settings.HISTORY_RETENTION_ARCHIVE;
        long archiveCutoff = Settings.HISTORY_ARCHIVE_KEEP_MONTHS == 0 ? 0 : HistoryDates.cutoff(
                LocalDate.now(Settings.HISTORY_ZONE), Settings.HISTORY_ARCHIVE_KEEP_MONTHS, Settings.HISTORY_ZONE);
        try {
            executor.execute(() -> {
                try {
                    if (isAvailable()) {
                        retention.process(storage, cutoff, serverId, batch, archive);
                        retention.cleanArchives(archiveCutoff, batch);
                    }
                } catch (Exception exception) {
                    failure("maintain history (unarchived source retained)", exception);
                } finally {
                    maintenancePending.set(false);
                }
            });
        } catch (RejectedExecutionException exception) {
            maintenancePending.set(false);
        }
    }

    private void cancelTasks() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        sweepRunning = false;
    }

    public void loadSummaries(String playerUUID, long fromMillis, long toMillis,
                              Consumer<List<InventorySnapshotSummary>> callback) {
        if (!isAvailable()) {
            onMain(() -> callback.accept(null));
            return;
        }
        String serverId = Settings.HISTORY_SERVER_ID;
        long from = Math.max(fromMillis, HistoryDates.cutoff());
        try {
            executor.execute(() -> {
                try {
                    List<InventorySnapshotSummary> summaries = storage.list(playerUUID, serverId, from, toMillis, 1);
                    onMain(() -> callback.accept(summaries));
                } catch (Exception exception) {
                    failure("list", exception);
                    onMain(() -> callback.accept(null));
                }
            });
        } catch (RejectedExecutionException exception) {
            onMain(() -> callback.accept(null));
        }
    }

    public void loadAdjacent(InventorySnapshotSummary reference, long fromMillis, long toMillis, boolean older,
                             Consumer<InventorySnapshotSummary> callback) {
        if (!isAvailable()) {
            onMain(() -> callback.accept(null));
            return;
        }
        String serverId = Settings.HISTORY_SERVER_ID;
        long from = Math.max(fromMillis, HistoryDates.cutoff());
        try {
            executor.execute(() -> {
                try {
                    InventorySnapshotSummary result = storage.adjacent(reference, serverId, from, toMillis, older);
                    onMain(() -> callback.accept(result));
                } catch (Exception exception) {
                    failure("browse history", exception);
                    onMain(() -> callback.accept(null));
                }
            });
        } catch (RejectedExecutionException exception) {
            onMain(() -> callback.accept(null));
        }
    }

    public void loadSnapshot(InventorySnapshotSummary summary, Consumer<InventorySnapshot> callback) {
        if (!isAvailable() || summary == null) {
            onMain(() -> callback.accept(null));
            return;
        }
        String serverId = Settings.HISTORY_SERVER_ID;
        try {
            executor.execute(() -> {
                try {
                    InventorySnapshot result = storage.get(summary.playerUUID(), summary.id(), summary.createdAt());
                    if (result != null && (!serverId.isEmpty() && !serverId.equals(result.getServerId())))
                        result = null;
                    InventorySnapshot snapshot = result;
                    onMain(() -> callback.accept(snapshot));
                } catch (Exception exception) {
                    failure("load", exception);
                    onMain(() -> callback.accept(null));
                }
            });
        } catch (RejectedExecutionException exception) {
            onMain(() -> callback.accept(null));
        }
    }

    public void restore(Player target, InventorySnapshot snapshot, Consumer<Boolean> callback) {
        if (!Bukkit.isPrimaryThread()) {
            UUID uuid = target == null ? null : target.getUniqueId();
            onMain(() -> restore(uuid == null ? null : Bukkit.getPlayer(uuid), snapshot, callback));
            return;
        }
        if (!isAvailable() || !Settings.HISTORY_RESTORE_ENABLED || target == null || !target.isOnline()
                || snapshot == null || !target.getUniqueId().toString().equals(snapshot.getPlayerUUID())
                || (!Settings.HISTORY_SERVER_ID.isEmpty() && !Settings.HISTORY_SERVER_ID.equals(snapshot.getServerId()))) {
            callback.accept(false);
            return;
        }
        UUID uuid = target.getUniqueId();
        PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(uuid);
        if (data == null || data.isProtectionLocked() || !restoring.add(uuid)) {
            callback.accept(false);
            return;
        }
        data.saveCurrentPage();
        InventorySnapshot before;
        try {
            before = snapshot(data, SnapshotReason.BEFORE_RESTORE, target);
        } catch (Exception exception) {
            restoring.remove(uuid);
            failure("capture restore safety snapshot", exception);
            callback.accept(false);
            return;
        }
        saveAsync(before, true).thenAccept(saved -> onMain(() -> {
            Player player = Bukkit.getPlayer(uuid);
            PlayerInventoryDatabase current = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(uuid);
            if (!saved || !isAvailable() || !Settings.HISTORY_RESTORE_ENABLED || player == null || !player.isOnline()
                    || current == null || current.isProtectionLocked()) {
                restoring.remove(uuid);
                callback.accept(false);
                return;
            }
            boolean[] restored = {false};
            // Exclusive against autosave and invsee flushes; never block the main thread behind their I/O.
            boolean ran = DatabaseManager.runIfStorageIdle(() -> {
                try {
                    current.saveCurrentPage();
                    // Do not overwrite edits made while the durable safety snapshot was being written.
                    if (!unchangedSinceSafety(current, before))
                        return;
                    applySnapshot(current, snapshot, player);
                    if (PlayerInventoryDataStorage.savePlayerInventoryDataChecked(current)) {
                        restored[0] = true;
                        return;
                    }
                    failure("persist restore", new IllegalStateException("Primary storage rejected the restored inventory."));
                } catch (Exception exception) {
                    failure("restore", exception);
                }
                try {
                    applySnapshot(current, before, player);
                    if (!PlayerInventoryDataStorage.savePlayerInventoryDataChecked(current))
                        InventoryPagesPlus.plugin.getLogger().severe("History restore rollback for " + player.getName()
                                + " could not be persisted; the BEFORE_RESTORE snapshot remains available.");
                } catch (Exception rollbackException) {
                    failure("roll back restore", rollbackException);
                }
            });
            restoring.remove(uuid);
            callback.accept(ran && restored[0]);
        }));
    }

    private boolean unchangedSinceSafety(PlayerInventoryDatabase current, InventorySnapshot before) {
        return current.getItems().equals(before.getPages())
                && current.getCurrentPage() == before.getCurrentPage()
                && usedMaxPage(current) == before.getMaxPage()
                && current.hasUsedCreative() == before.isUsedCreative()
                && (!current.hasUsedCreative() || current.getCreativeItems().equals(before.getCreativeItems()));
    }

    private void applySnapshot(PlayerInventoryDatabase data, InventorySnapshot snapshot, Player player) {
        data.setMaxPage(Math.max(data.getMaxPage(), snapshot.getMaxPage()));
        HashMap<Integer, ArrayList<ItemStack>> pages = new HashMap<>();
        for (Map.Entry<Integer, List<ItemStack>> entry : snapshot.getPages().entrySet())
            pages.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        data.setItems(pages);
        // Snapshots taken before the flag was recovered carry no creative payload; keep live
        // creative data instead of wiping it.
        if (snapshot.isUsedCreative()) {
            ArrayList<ItemStack> creative = new ArrayList<>(snapshot.getCreativeItems());
            while (creative.size() < 27)
                creative.add(null);
            data.setCreativeItems(creative);
            data.setUsedCreative(true);
        }
        data.setCurrentPage(Math.max(0, snapshot.getCurrentPage()));
        data.showPage(player.getGameMode());
    }

    private boolean onMain(Runnable task) {
        if (stopping || !InventoryPagesPlus.plugin.isEnabled())
            return false;
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return true;
        }
        try {
            Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
                if (!stopping && InventoryPagesPlus.plugin.isEnabled())
                    task.run();
            });
            return true;
        } catch (IllegalPluginAccessException exception) {
            return false;
        }
    }

    private void failure(String operation, Exception exception) {
        InventoryPagesPlus.plugin.getLogger().log(Level.WARNING, "History failed to " + operation + ".", exception);
        onMain(() -> {
            MessageUtil.log("&c[HISTORY] Failed to " + operation + "; see the server log.");
            DebugManager.debug("HISTORY", operation + ": " + exception.getClass().getSimpleName());
        });
    }
}
