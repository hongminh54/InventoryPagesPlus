package me.cortezromeo.inventorypagesplus.history;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.command.HistoryCommand;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.server.VersionSupport;
import me.cortezromeo.inventorypagesplus.storage.InventoryHistorySQLStorage;
import me.cortezromeo.inventorypagesplus.storage.InventoryHistoryStorage;
import me.cortezromeo.inventorypagesplus.storage.InventoryHistoryYAMLStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.UnsafeValues;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.zip.ZipFile;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standalone regression suite; run with the module's run-history-regression.sh.
 * H2/JDBC, YAML/files, Gson and Bukkit object streams are real. Server/ItemFactory/
 * UnsafeValues and VersionSupport are narrowly faked for plain type/amount stacks.
 * Extraction tests run the real pipeline with an inline scheduler and a fake admin.
 * These tests do NOT establish NBT/PDC/custom metadata preservation, a live Bukkit
 * scheduler, plugin lifecycle correctness, or MySQL compatibility.
 */
public final class HistoryStorageRegression {
    private static final String A = uuid(101);
    private static final String B = uuid(102);
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final List<LogRecord> LOGS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Player> FIXTURE_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Runnable> SCHEDULED_TASKS = new ConcurrentLinkedQueue<>();
    private static volatile boolean queueTasks;
    private static PluginCommand fixtureHistoryCommand;
    private static final AtomicInteger COPIES = new AtomicInteger();
    private static final Thread MAIN_THREAD = Thread.currentThread();
    private static Path output;
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        if (!HistoryStorageRegression.class.desiredAssertionStatus())
            throw new IllegalStateException("Run with -ea; assertions must not be disabled");
        Path allowed = Path.of("D:/backup file/PirateV2-Project/InventoryPagesPlus/inventorypagesplus-plugin/target").toRealPath();
        Path parent = Path.of(args[0]).toRealPath();
        if (!parent.startsWith(allowed))
            throw new IllegalArgumentException("All generated files must stay inside module target");
        output = Files.createTempDirectory(parent, "run-");
        bootstrap();
        Class.forName("org.h2.Driver");
        System.out.println("Real H2 2.3.232 + YAML; test-only Bukkit fixture; plain stacks only.");
        System.out.println("Artifacts: " + output);
        modelTests();
        serializationTests();
        for (Backend backend : Backend.values())
            sharedStorageTests(backend);
        sqlTests();
        yamlTests();
        managerTests();
        creativeRestoreTests();
        extractionTests();
        dailyHistoryTests();
        for (Backend backend : Backend.values())
            retentionTests(backend);
        historyCommandTests();
        permissionConfigurationTests();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed (named test cases, not assertion count)");
        if (failed != 0)
            throw new AssertionError(failed + " regression test(s) failed; see FAIL lines above");
    }

    private static void historyCommandTests() {
        test("COMMAND save without a player captures all online players and bounds submissions", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                for (int index = 0; index < 80; index++)
                    context.player("Player" + index);
                context.execute(context.console, "save");
                eq(1, context.captured.size(), "did not start immediately or captured everybody on one tick");
                eq(1, SCHEDULED_TASKS.size(), "unbounded scheduler submissions");
                check(!context.messages.contains("saved 80/80 failed 0"), "reported success before completion");
                context.drain();
                eq(FIXTURE_PLAYERS.keySet(), new HashSet<>(context.captured), "missing online players");
                eq(80, context.captured.size(), "duplicate capture");
                check(context.messages.contains("saved 80/80 failed 0"), "missing aggregate completion");
                context.execute(context.console, "save");
                context.drain();
                eq(160, context.captured.size(), "completed job still blocks the next save");
            }
        });
        test("COMMAND player sender saves everyone by default but an explicit name stays targeted", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                Player alice = context.player("Alice");
                Player bob = context.player("Bob");
                context.execute(alice, "save");
                context.drain();
                eq(Set.of(alice.getUniqueId(), bob.getUniqueId()), new HashSet<>(context.captured), "saved only sender");
                context.captured.clear();
                context.execute(alice, "save", "Bob");
                context.drain();
                eq(List.of(bob.getUniqueId()), context.captured, "explicit save affected other players");
                check(context.messages.contains("saved Bob"), "targeted completion changed");
            }
        });
        test("COMMAND bulk save requires view/save/others and explicit self save does not require others", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                Player alice = context.player("Alice");
                for (String permission : List.of(Settings.HISTORY_PERMISSION, Settings.HISTORY_PERMISSION_SAVE, Settings.HISTORY_PERMISSION_OTHERS)) {
                    context.permissions.remove(permission);
                    context.execute(alice, "save");
                    eq(0, context.captured.size(), "bulk save bypassed " + permission);
                    context.permissions.add(permission);
                }
                context.permissions.remove(Settings.HISTORY_PERMISSION_OTHERS);
                context.execute(alice, "save", "Alice");
                context.drain();
                eq(List.of(alice.getUniqueId()), context.captured, "explicit self save denied");
            }
        });
        test("COMMAND bulk save rejects overlap and reports rejected/exceptional writes accurately", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                Player alice = context.player("Alice");
                Player bob = context.player("Bob");
                context.player("Charlie");
                context.outcomes.put(alice.getUniqueId(), CompletableFuture.completedFuture(false));
                context.outcomes.put(bob.getUniqueId(), CompletableFuture.failedFuture(new IOException("test write failure")));
                context.execute(context.console, "save");
                context.execute(context.console, "save");
                eq(1, context.captured.size(), "overlapping save was accepted");
                check(context.messages.contains("running"), "overlap not reported");
                context.drain();
                eq(3, context.captured.size(), "failed write stopped remaining players");
                check(context.messages.contains("saved 1/3 failed 2"), "incorrect failure count");
            }
        });
        test("COMMAND bulk save handles zero players, departures and disabled history", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                context.execute(context.console, "save");
                check(context.messages.contains("empty"), "zero-player feedback");
                context.player("Alice");
                context.player("Bob");
                context.player("Charlie");
                context.execute(context.console, "save");
                UUID captured = context.captured.get(0);
                FIXTURE_PLAYERS.keySet().removeIf(id -> !id.equals(captured));
                context.drain();
                check(context.messages.contains("saved 1/3 failed 2"), "departed players reported saved");
                context.captured.clear();
                Settings.HISTORY_ENABLED = false;
                context.execute(context.console, "save");
                eq(0, context.captured.size(), "disabled history saved");
                check(context.messages.contains("disabled"), "disabled feedback");
            }
        });
        test("COMMAND bulk save waits for asynchronous completion before moving to the next player", () -> {
            try (HistoryCommandFixture context = new HistoryCommandFixture()) {
                Player alice = context.player("Alice");
                Player bob = context.player("Bob");
                CompletableFuture<Boolean> pending = new CompletableFuture<>();
                context.outcomes.put(alice.getUniqueId(), pending);
                context.outcomes.put(bob.getUniqueId(), pending);
                context.execute(context.console, "save");
                eq(1, context.captured.size(), "more than one pending capture");
                check(SCHEDULED_TASKS.isEmpty(), "scheduled next capture before write completion");
                CompletableFuture.runAsync(() -> pending.complete(true)).get(10, TimeUnit.SECONDS);
                eq(1, context.captured.size(), "capture ran on completion worker");
                context.drain();
                check(context.messages.contains("saved 2/2 failed 0"), "async save never completed");
            }
        });
    }

    private static final class HistoryCommandFixture implements AutoCloseable {
        final List<UUID> captured = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final Map<UUID, CompletableFuture<Boolean>> outcomes = new HashMap<>();
        final Set<String> permissions = new HashSet<>(List.of(Settings.HISTORY_PERMISSION,
                Settings.HISTORY_PERMISSION_SAVE, Settings.HISTORY_PERMISSION_OTHERS));
        final Map<Field, Object> previousMessages = new HashMap<>();
        final InventoryHistoryManager previousManager = InventoryPagesPlus.getHistoryManager();
        final boolean previousEnabled = Settings.HISTORY_ENABLED;
        final boolean previousPluginEnabled = InventoryPagesPlus.plugin.isEnabled();
        final CommandSender console;
        final HistoryCommand command;

        HistoryCommandFixture() throws Exception {
            for (Field field : Messages.class.getFields())
                if (field.getType() == String.class)
                    previousMessages.put(field, field.get(null));
            Messages.PREFIX = "";
            Messages.COMMAND_HISTORY_USAGE = "usage";
            Messages.COMMAND_HISTORY_DISABLED = "disabled";
            Messages.COMMAND_HISTORY_NO_PERMISSION = "no permission";
            Messages.COMMAND_HISTORY_NO_PERMISSION_OTHERS = "no others permission";
            Messages.COMMAND_HISTORY_LOAD_FAILED = "unavailable";
            Messages.COMMAND_HISTORY_PLAYER_NOT_FOUND = "missing %player%";
            Messages.COMMAND_HISTORY_SNAPSHOT_SAVED = "saved %player%";
            Messages.COMMAND_HISTORY_SNAPSHOT_SAVE_FAILED = "failed %player%";
            Messages.COMMAND_HISTORY_SAVE_ALL_STARTED = "start %total%";
            Messages.COMMAND_HISTORY_SAVE_ALL_COMPLETE = "saved %saved%/%total% failed %failed%";
            Messages.COMMAND_HISTORY_SAVE_ALL_RUNNING = "running";
            Messages.COMMAND_HISTORY_SAVE_ALL_EMPTY = "empty";
            Settings.HISTORY_ENABLED = true;
            setField(JavaPlugin.class, InventoryPagesPlus.plugin, "isEnabled", true);
            setField(InventoryPagesPlus.class, null, "historyManager", new InventoryHistoryManager() {
                @Override public boolean isAvailable() { return Settings.HISTORY_ENABLED; }

                @Override public CompletableFuture<Boolean> capture(Player player, SnapshotReason reason, boolean force) {
                    check(Thread.currentThread() == MAIN_THREAD, "capture outside main thread");
                    eq(SnapshotReason.MANUAL, reason, "wrong save reason");
                    check(force, "manual save was not forced");
                    if (player == null || !player.isOnline())
                        return CompletableFuture.completedFuture(false);
                    captured.add(player.getUniqueId());
                    return outcomes.getOrDefault(player.getUniqueId(), CompletableFuture.completedFuture(true));
                }
            });
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            fixtureHistoryCommand = constructor.newInstance("invhistory", InventoryPagesPlus.plugin);
            command = new HistoryCommand();
            console = proxy(ConsoleCommandSender.class, (proxy, method, args) -> senderCall(method, args, "CONSOLE"));
            queueTasks = true;
        }

        Player player(String name) {
            UUID id = UUID.randomUUID();
            Player player = proxy(Player.class, (proxy, method, args) -> {
                if (method.getName().equals("getUniqueId"))
                    return id;
                if (method.getName().equals("isOnline"))
                    return FIXTURE_PLAYERS.containsKey(id);
                return senderCall(method, args, name);
            });
            FIXTURE_PLAYERS.put(id, player);
            return player;
        }

        private Object senderCall(Method method, Object[] args, String name) {
            switch (method.getName()) {
                case "hasPermission": return permissions.contains(args[0]);
                case "getName": return name;
                case "sendMessage":
                    check(Thread.currentThread() == MAIN_THREAD, "message outside main thread");
                    messages.add((String) args[0]);
                    return null;
                default: throw new UnsupportedOperationException("Fixture command sender: " + method);
            }
        }

        void execute(CommandSender sender, String... args) {
            check(command.onCommand(sender, fixtureHistoryCommand, "invhistory", args), "command not handled");
        }

        void drain() {
            int count = 0;
            Runnable task;
            while ((task = SCHEDULED_TASKS.poll()) != null) {
                check(++count < 10000, "scheduler loop did not finish");
                task.run();
            }
        }

        @Override public void close() throws Exception {
            queueTasks = false;
            SCHEDULED_TASKS.clear();
            FIXTURE_PLAYERS.clear();
            fixtureHistoryCommand = null;
            setField(InventoryPagesPlus.class, null, "historyManager", previousManager);
            setField(JavaPlugin.class, InventoryPagesPlus.plugin, "isEnabled", previousPluginEnabled);
            Settings.HISTORY_ENABLED = previousEnabled;
            for (Map.Entry<Field, Object> entry : previousMessages.entrySet())
                entry.getKey().set(null, entry.getValue());
        }
    }

    private static void permissionConfigurationTests() {
        test("PERMISSIONS fixed history nodes match plugin.yml and ignore legacy config on reload", () -> {
            Map<String, String> expected = Map.of(
                    "HISTORY_PERMISSION", "inventorypagesplus.history",
                    "HISTORY_PERMISSION_OTHERS", "inventorypagesplus.history.others",
                    "HISTORY_PERMISSION_SAVE", "inventorypagesplus.history.save",
                    "HISTORY_PERMISSION_RESTORE", "inventorypagesplus.history.restore");
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
            check(!defaults.contains("history.permissions"), "customizable permissions remain in defaults");
            PluginDescriptionFile description = new PluginDescriptionFile(new StringReader(Files.readString(Path.of("src/main/resources/plugin.yml"))));
            Set<String> declared = description.getPermissions().stream()
                    .map(permission -> permission.getName()).collect(Collectors.toSet());
            check(declared.containsAll(expected.values()), "fixed history permission nodes missing from plugin.yml");
            // Only the history nodes are pinned to OP here; other feature nodes own their own defaults.
            check(description.getPermissions().stream()
                            .filter(permission -> expected.values().contains(permission.getName()))
                            .allMatch(permission -> permission.getDefault().name().equals("OP")),
                    "history permission defaults changed");
            Field configField = JavaPlugin.class.getDeclaredField("newConfig");
            configField.setAccessible(true);
            Object previous = configField.get(InventoryPagesPlus.plugin);
            try {
                configField.set(InventoryPagesPlus.plugin, defaults);
                for (String custom : List.of("legacy.custom", "")) {
                    for (String key : List.of("view", "others", "save", "restore"))
                        defaults.set("history.permissions." + key, custom);
                    Settings.setupValue();
                    for (Map.Entry<String, String> entry : expected.entrySet()) {
                        Field field = Settings.class.getField(entry.getKey());
                        check(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), "permission is mutable");
                        eq(entry.getValue(), field.get(null), "config changed permission " + entry.getKey());
                    }
                }
            } finally {
                configField.set(InventoryPagesPlus.plugin, previous);
            }
        });
    }

    private static void modelTests() {
        test("MODEL constructor defensively copies containers and mutable stacks", () -> {
            ItemStack pageItem = item(Material.STONE, 3);
            ItemStack creativeItem = item(Material.EMERALD, 4);
            List<ItemStack> page = slots(pageItem, null);
            List<ItemStack> creative = slots(null, creativeItem);
            Map<Integer, List<ItemStack>> pages = new HashMap<>();
            pages.put(2, page);
            InventorySnapshot snapshot = snapshot(1, A, 9, ALPHA, SnapshotReason.JOIN, pages, creative, true);
            pageItem.setAmount(63);
            creativeItem.setAmount(62);
            page.clear();
            creative.clear();
            pages.clear();
            eq(1, snapshot.getPageCount(), "source map mutation");
            stack(snapshot.getPageItems(2).get(0), Material.STONE, 3);
            stack(snapshot.getCreativeItems().get(1), Material.EMERALD, 4);
            eq(null, snapshot.getPageItems(2).get(1), "null slot retained");
        });
        test("MODEL getters return fresh mutable stack copies", () -> {
            InventorySnapshot snapshot = sample(1, A, 9, ALPHA);
            snapshot.getPages().get(2).get(0).setAmount(61);
            snapshot.getPageItems(2).get(0).setAmount(60);
            snapshot.getCreativeItems().get(1).setAmount(59);
            stack(snapshot.getPageItems(2).get(0), Material.STONE, 2);
            stack(snapshot.getCreativeItems().get(1), Material.EMERALD, 4);
            check(snapshot.getPageItems(2).get(0) != snapshot.getPageItems(2).get(0), "getters alias stacks");
        });
        test("MODEL map and nested lists are unmodifiable", () -> {
            InventorySnapshot snapshot = sample(1, A, 9, ALPHA);
            expect(UnsupportedOperationException.class, () -> snapshot.getPages().clear());
            expect(UnsupportedOperationException.class, () -> snapshot.getPages().get(2).clear());
            expect(UnsupportedOperationException.class, () -> snapshot.getPageItems(2).add(null));
            expect(UnsupportedOperationException.class, () -> snapshot.getCreativeItems().clear());
        });
        test("MODEL equipment pads, defensively copies and returns fresh stacks", () -> {
            ItemStack helmet = item(Material.DIAMOND_HELMET, 1);
            List<ItemStack> armor = slots(helmet, item(Material.DIAMOND_CHESTPLATE, 1));
            ItemStack offhand = item(Material.SHIELD, 1);
            List<ItemStack> hotbar = slots(item(Material.DIAMOND_SWORD, 1), null, item(Material.BREAD, 5));
            InventorySnapshot snapshot = new InventorySnapshot(uuid(1), A, "Alice", 9, SnapshotReason.JOIN, ALPHA,
                    2, 10, false, null, null, armor, offhand, hotbar);
            eq(4, snapshot.getArmor().size(), "armor padded");
            stack(snapshot.getArmor().get(0), Material.DIAMOND_HELMET, 1);
            stack(snapshot.getArmor().get(1), Material.DIAMOND_CHESTPLATE, 1);
            eq(null, snapshot.getArmor().get(2), "armor padding null");
            eq(null, snapshot.getArmor().get(3), "armor padding null");
            stack(snapshot.getOffhand(), Material.SHIELD, 1);
            eq(9, snapshot.getHotbar().size(), "hotbar padded");
            stack(snapshot.getHotbar().get(2), Material.BREAD, 5);
            helmet.setAmount(63);
            offhand.setAmount(62);
            armor.clear();
            hotbar.clear();
            stack(snapshot.getArmor().get(0), Material.DIAMOND_HELMET, 1);
            stack(snapshot.getOffhand(), Material.SHIELD, 1);
            stack(snapshot.getHotbar().get(0), Material.DIAMOND_SWORD, 1);
            snapshot.getArmor().get(0).setAmount(60);
            snapshot.getOffhand().setAmount(59);
            snapshot.getHotbar().get(2).setAmount(58);
            stack(snapshot.getArmor().get(0), Material.DIAMOND_HELMET, 1);
            stack(snapshot.getOffhand(), Material.SHIELD, 1);
            stack(snapshot.getHotbar().get(2), Material.BREAD, 5);
            expect(UnsupportedOperationException.class, () -> snapshot.getArmor().clear());
            expect(UnsupportedOperationException.class, () -> snapshot.getHotbar().add(null));
        });
        test("MODEL equipment truncation, legacy defaults and air normalization", () -> {
            List<ItemStack> armor = new ArrayList<>(Collections.nCopies(6, item(Material.STONE, 1)));
            List<ItemStack> hotbar = new ArrayList<>(Collections.nCopies(12, item(Material.DIRT, 1)));
            InventorySnapshot snapshot = new InventorySnapshot(uuid(1), A, "Alice", 9, SnapshotReason.JOIN, ALPHA,
                    0, 0, false, null, null, armor, item(Material.AIR, 1), hotbar);
            eq(4, snapshot.getArmor().size(), "armor truncated");
            eq(9, snapshot.getHotbar().size(), "hotbar truncated");
            eq(null, snapshot.getOffhand(), "air offhand normalized");
            InventorySnapshot legacy = sample(1, A, 9, ALPHA);
            eq(new ArrayList<>(Collections.nCopies(4, null)), legacy.getArmor(), "legacy armor empty");
            eq(null, legacy.getOffhand(), "legacy offhand empty");
            eq(new ArrayList<>(Collections.nCopies(9, null)), legacy.getHotbar(), "legacy hotbar empty");
            InventorySnapshot nulls = new InventorySnapshot(uuid(2), A, "Alice", 9, SnapshotReason.JOIN, ALPHA,
                    0, 0, false, null, null, null, null, null);
            eq(4, nulls.getArmor().size(), "null armor padded");
            eq(9, nulls.getHotbar().size(), "null hotbar padded");
        });
        test("MODEL empty pages, missing page and numeric key order", () -> {
            Map<Integer, List<ItemStack>> pages = new LinkedHashMap<>();
            pages.put(10, null);
            pages.put(2, Collections.emptyList());
            InventorySnapshot snapshot = snapshot(1, A, 9, ALPHA, SnapshotReason.JOIN, pages, null, true);
            eq(List.of(2, 10), new ArrayList<>(snapshot.getPages().keySet()), "numeric page order");
            eq(2, snapshot.getPageCount(), "model counts supplied empty pages");
            eq(List.of(), snapshot.getPageItems(10), "null page normalized");
            eq(List.of(), snapshot.getPageItems(99), "missing page");
            eq(List.of(), snapshot.getCreativeItems(), "null creative list");
        });
        test("MODEL negative page fields and null server normalize", () -> {
            InventorySnapshot snapshot = new InventorySnapshot(uuid(1), A, "Alice", 7, SnapshotReason.JOIN,
                    null, -3, -2, false, null, slots(item(Material.STONE, 1)));
            eq(0, snapshot.getCurrentPage(), "current page");
            eq(0, snapshot.getMaxPage(), "max page");
            eq(0, snapshot.getPageCount(), "null pages");
            eq("", snapshot.getServerId(), "server");
            eq(List.of(), snapshot.getCreativeItems(), "unused creative discarded");
        });
        test("MODEL summary carries metadata and clamps counts", () -> {
            InventorySnapshotSummary summary = new InventorySnapshotSummary(uuid(1), A, "Alice", 9,
                    SnapshotReason.BEFORE_RESTORE, null, -1, -2, -3, true);
            eq(uuid(1), summary.id(), "id");
            eq(A, summary.playerUUID(), "owner");
            eq("Alice", summary.playerName(), "name");
            eq(9L, summary.createdAt(), "time");
            eq(SnapshotReason.BEFORE_RESTORE, summary.reason(), "reason");
            eq("", summary.serverId(), "server");
            eq(0, summary.currentPage(), "current");
            eq(0, summary.maxPage(), "max");
            eq(0, summary.pageCount(), "page count");
            check(summary.usedCreative(), "creative flag");
        });
        test("MODEL snapshot reasons retain all eight supported reasons", () ->
                eq(Set.of("PERIODIC", "JOIN", "QUIT", "MANUAL", "BEFORE_CLEAR", "BEFORE_CLEAR_ALL", "BEFORE_RESTORE", "DISABLE"),
                        Arrays.stream(SnapshotReason.values()).map(Enum::name).collect(Collectors.toSet()), "reason set"));
    }

    private static void serializationTests() {
        test("SERIAL pages roundtrip: 25 slots, interior nulls and basic item values", () -> {
            Map<Integer, List<ItemStack>> decoded = StringUtil.pagesFromJson(StringUtil.pagesToJson(sample(1, A, 9, ALPHA).getPages()));
            eq(List.of(2, 10), new ArrayList<>(decoded.keySet()), "page keys");
            eq(25, decoded.get(2).size(), "page size");
            stack(decoded.get(2).get(0), Material.STONE, 2);
            eq(null, decoded.get(2).get(1), "interior null");
            stack(decoded.get(2).get(2), Material.DIAMOND, 7);
            check(decoded.get(2).subList(3, 25).stream().allMatch(Objects::isNull), "padding");
        });
        test("SERIAL creative roundtrip: 27 slots and null padding", () -> {
            List<ItemStack> decoded = StringUtil.itemsFromJson(StringUtil.itemsToJson(slots(null, item(Material.EMERALD, 4))));
            eq(27, decoded.size(), "creative size");
            eq(null, decoded.get(0), "leading null");
            stack(decoded.get(1), Material.EMERALD, 4);
            check(decoded.subList(2, 27).stream().allMatch(Objects::isNull), "padding");
        });
        test("SERIAL equipment roundtrip: armor 4, offhand 1, hotbar 9", () -> {
            List<ItemStack> armor = slots(item(Material.DIAMOND_HELMET, 1), null, item(Material.DIAMOND_BOOTS, 1));
            List<ItemStack> decodedArmor = StringUtil.itemsFromJson(StringUtil.itemsToJson(armor, 4), 4);
            eq(4, decodedArmor.size(), "armor size");
            stack(decodedArmor.get(0), Material.DIAMOND_HELMET, 1);
            eq(null, decodedArmor.get(1), "armor interior null");
            stack(decodedArmor.get(2), Material.DIAMOND_BOOTS, 1);
            eq(null, decodedArmor.get(3), "armor padding");
            stack(StringUtil.itemFromJson(StringUtil.itemToJson(item(Material.SHIELD, 1))), Material.SHIELD, 1);
            eq(null, StringUtil.itemFromJson(StringUtil.itemToJson(null)), "null offhand roundtrip");
            eq(null, StringUtil.itemFromJson(null), "absent offhand");
            List<ItemStack> hotbar = slots(null, item(Material.BREAD, 5));
            List<ItemStack> decodedHotbar = StringUtil.itemsFromJson(StringUtil.itemsToJson(hotbar, 9), 9);
            eq(9, decodedHotbar.size(), "hotbar size");
            stack(decodedHotbar.get(1), Material.BREAD, 5);
            check(decodedHotbar.subList(2, 9).stream().allMatch(Objects::isNull), "hotbar padding");
        });
        test("SERIAL equipment sizes enforced on encode and decode", () -> {
            List<ItemStack> oversized = new ArrayList<>(Collections.nCopies(12, null));
            oversized.set(3, item(Material.STONE, 3));
            oversized.set(8, item(Material.DIAMOND, 8));
            oversized.set(11, item(Material.EMERALD, 11));
            eq(4, JsonParser.parseString(StringUtil.itemsToJson(oversized, 4)).getAsJsonArray().size(), "armor encoding length");
            eq(9, JsonParser.parseString(StringUtil.itemsToJson(oversized, 9)).getAsJsonArray().size(), "hotbar encoding length");
            List<String> encoded = new ArrayList<>(Collections.nCopies(12, null));
            encoded.set(3, StringUtil.toBase64(item(Material.STONE, 3)));
            encoded.set(8, StringUtil.toBase64(item(Material.DIAMOND, 8)));
            encoded.set(11, "!invalid-outside-supported-slots!");
            String json = new Gson().toJson(encoded);
            LOGS.clear();
            List<ItemStack> decodedArmor = StringUtil.itemsFromJson(json, 4);
            eq(4, decodedArmor.size(), "armor decoding length");
            stack(decodedArmor.get(3), Material.STONE, 3);
            List<ItemStack> decodedHotbar = StringUtil.itemsFromJson(json, 9);
            eq(9, decodedHotbar.size(), "hotbar decoding length");
            stack(decodedHotbar.get(8), Material.DIAMOND, 8);
            eq(0, LOGS.size(), "out-of-range equipment corruption should not be decoded");
        });
        test("SERIAL encoding truncates pages at 25 and creative at 27", () -> {
            List<ItemStack> oversized = new ArrayList<>(Collections.nCopies(30, null));
            oversized.set(24, item(Material.STONE, 24));
            oversized.set(26, item(Material.DIAMOND, 26));
            oversized.set(29, item(Material.EMERALD, 29));
            JsonArray page = JsonParser.parseString(StringUtil.pagesToJson(Map.of(2, oversized))).getAsJsonObject().getAsJsonArray("2");
            eq(25, page.size(), "page encoding length");
            stack(StringUtil.pagesFromJson("{\"2\":" + page + "}").get(2).get(24), Material.STONE, 24);
            JsonArray creative = JsonParser.parseString(StringUtil.itemsToJson(oversized)).getAsJsonArray();
            eq(27, creative.size(), "creative encoding length");
            stack(StringUtil.itemsFromJson(creative.toString()).get(26), Material.DIAMOND, 26);
        });
        test("SERIAL decoding truncates oversized serialized arrays", () -> {
            List<String> oversized = new ArrayList<>(Collections.nCopies(30, null));
            oversized.set(24, StringUtil.toBase64(item(Material.STONE, 24)));
            oversized.set(26, StringUtil.toBase64(item(Material.DIAMOND, 26)));
            oversized.set(29, "!invalid-outside-supported-slots!");
            String json = new Gson().toJson(oversized);
            LOGS.clear();
            List<ItemStack> page = StringUtil.pagesFromJson("{\"2\":" + json + "}").get(2);
            eq(25, page.size(), "page decoding length");
            stack(page.get(24), Material.STONE, 24);
            List<ItemStack> creative = StringUtil.itemsFromJson(json);
            eq(27, creative.size(), "creative decoding length");
            stack(creative.get(26), Material.DIAMOND, 26);
            eq(0, LOGS.size(), "out-of-range corruption should not be decoded");
        });
        test("SERIAL canonical numeric ordering independent of insertion order", () -> {
            Map<Integer, List<ItemStack>> first = new LinkedHashMap<>();
            first.put(10, slots(item(Material.STONE, 1)));
            first.put(2, slots(item(Material.DIAMOND, 2)));
            Map<Integer, List<ItemStack>> second = new LinkedHashMap<>();
            second.put(2, first.get(2));
            second.put(10, first.get(10));
            String json = StringUtil.pagesToJson(first);
            eq(json, StringUtil.pagesToJson(second), "canonical bytes");
            check(json.indexOf("\"2\"") < json.indexOf("\"10\""), "lexicographic rather than numeric ordering");
        });
        test("SERIAL empty pages omitted; null/empty creative encoded as 27 nulls", () -> {
            Map<Integer, List<ItemStack>> pages = new HashMap<>();
            pages.put(0, null);
            pages.put(1, Collections.emptyList());
            pages.put(2, new ArrayList<>(Collections.nCopies(25, null)));
            eq("{}", StringUtil.pagesToJson(pages), "empty page omission");
            eq("{}", StringUtil.pagesToJson(null), "null pages");
            eq(Map.of(), StringUtil.pagesFromJson("{\"0\":null,\"1\":[],\"2\":[null]}"), "decode empty pages");
            eq(new ArrayList<>(Collections.nCopies(27, null)), StringUtil.itemsFromJson(StringUtil.itemsToJson(null)), "null creative padding");
        });
        test("SERIAL malformed JSON warns and returns safe empty results", () -> {
            LOGS.clear();
            eq(Map.of(), StringUtil.pagesFromJson("["), "malformed pages");
            warn("Unable to decode history pages");
            LOGS.clear();
            eq(new ArrayList<>(Collections.nCopies(27, null)), StringUtil.itemsFromJson("{"), "malformed creative");
            warn("Unable to decode history items");
        });
        test("SERIAL absent payload differs from encoded JSON null", () -> {
            eq(Map.of(), StringUtil.pagesFromJson(null), "absent pages");
            eq(Map.of(), StringUtil.pagesFromJson(""), "empty pages string");
            eq(List.of(), StringUtil.itemsFromJson(null), "absent creative");
            eq(List.of(), StringUtil.itemsFromJson(""), "empty creative string");
            LOGS.clear();
            eq(Map.of(), StringUtil.pagesFromJson("null"), "JSON null pages");
            warn("Unable to decode history pages");
            LOGS.clear();
            eq(27, StringUtil.itemsFromJson("null").size(), "JSON null creative");
            warn("Unable to decode history items");
        });
        test("SERIAL corrupt item logs its slot without shifting valid neighbors", () -> {
            List<String> encoded = slotsOfStrings(StringUtil.toBase64(item(Material.STONE, 2)), "!bad-base64!", null,
                    StringUtil.toBase64(item(Material.DIAMOND, 7)));
            String json = new Gson().toJson(encoded);
            LOGS.clear();
            List<ItemStack> page = StringUtil.pagesFromJson("{\"2\":" + json + "}").get(2);
            stack(page.get(0), Material.STONE, 2);
            eq(null, page.get(1), "corrupt slot");
            eq(null, page.get(2), "null slot");
            stack(page.get(3), Material.DIAMOND, 7);
            warn("slot 1");
            LOGS.clear();
            List<ItemStack> creative = StringUtil.itemsFromJson(json);
            eq(27, creative.size(), "creative corrupt recovery size");
            stack(creative.get(3), Material.DIAMOND, 7);
            warn("slot 1");
        });
        test("SERIAL decoded-null item is logged and all-null page omitted", () -> {
            String encodedNull = StringUtil.toBase64(new ItemStack[]{null});
            LOGS.clear();
            String json = new Gson().toJson(List.of(encodedNull));
            eq(Map.of(), StringUtil.pagesFromJson("{\"2\":" + json + "}"), "decoded-null page");
            warn("decoded to null at slot 0");
        });
    }

    private static void sharedStorageTests(Backend backend) {
        storageTest(backend, "save/get roundtrip and summary metadata", fixture -> {
            InventorySnapshot original = sample(1, A, 9, ALPHA);
            fixture.save(original, "hash-1");
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            check(result != null, "saved snapshot missing");
            eq(uuid(1), result.getId(), "id");
            eq(A, result.getPlayerUUID(), "owner");
            eq("Alice", result.getPlayerName(), "name");
            eq(9L, result.getCreatedAt(), "time");
            eq(SnapshotReason.JOIN, result.getReason(), "reason");
            eq(ALPHA, result.getServerId(), "server");
            eq(2, result.getCurrentPage(), "current");
            eq(10, result.getMaxPage(), "max");
            eq(2, result.getPageCount(), "page count");
            check(result.isUsedCreative(), "creative flag");
            eq(25, result.getPageItems(2).size(), "page padded");
            eq(27, result.getCreativeItems().size(), "creative padded");
            stack(result.getPageItems(2).get(2), Material.DIAMOND, 7);
            stack(result.getCreativeItems().get(1), Material.EMERALD, 4);
            InventorySnapshotSummary summary = fixture.storage.list(A, ALPHA, 0, 10, 10).get(0);
            eq(original.getId(), summary.id(), "summary id");
            eq(original.getPlayerName(), summary.playerName(), "summary name");
            eq(original.getPageCount(), summary.pageCount(), "summary count");
            eq(original.getCurrentPage(), summary.currentPage(), "summary current");
            eq(original.getMaxPage(), summary.maxPage(), "summary maximum");
            check(summary.usedCreative(), "summary creative");
        });
        storageTest(backend, "get enforces owner and missing id isolation", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            eq(null, fixture.storage.get(B, uuid(1), 9), "foreign owner get");
            eq(null, fixture.storage.get(A, uuid(99), 9), "unknown snapshot");
            eq(List.of(), fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), "foreign owner list");
        });
        storageTest(backend, "equipment roundtrip and legacy empty equipment", fixture -> {
            fixture.save(equipped(1, A, 9), "hash-1");
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            check(result != null, "equipment snapshot missing");
            stack(result.getArmor().get(0), Material.DIAMOND_HELMET, 1);
            stack(result.getArmor().get(1), Material.DIAMOND_CHESTPLATE, 1);
            stack(result.getArmor().get(2), Material.DIAMOND_LEGGINGS, 1);
            stack(result.getArmor().get(3), Material.DIAMOND_BOOTS, 1);
            stack(result.getOffhand(), Material.SHIELD, 1);
            eq(9, result.getHotbar().size(), "hotbar size");
            stack(result.getHotbar().get(0), Material.DIAMOND_SWORD, 1);
            eq(null, result.getHotbar().get(1), "hotbar interior null");
            stack(result.getHotbar().get(2), Material.BREAD, 5);
            stack(result.getPageItems(2).get(2), Material.DIAMOND, 7);
            fixture.save(sample(2, A, 99, ALPHA), "hash-2");
            InventorySnapshot legacy = fixture.storage.get(A, uuid(2), 99);
            eq(new ArrayList<>(Collections.nCopies(4, null)), legacy.getArmor(), "legacy armor");
            eq(null, legacy.getOffhand(), "legacy offhand");
            eq(new ArrayList<>(Collections.nCopies(9, null)), legacy.getHotbar(), "legacy hotbar");
        });
        storageTest(backend, "get logs corrupt equipment slot and preserves valid neighbors", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            String json = new Gson().toJson(slotsOfStrings("!bad-base64!", StringUtil.toBase64(item(Material.DIAMOND, 7))));
            fixture.mutate(1, 9, "armor-json", json);
            LOGS.clear();
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            check(result != null, "corrupt equipment slot lost snapshot");
            eq(null, result.getArmor().get(0), "bad armor placeholder");
            stack(result.getArmor().get(1), Material.DIAMOND, 7);
            stack(result.getOffhand(), Material.SHIELD, 1);
            warn("slot 0");
        });
        storageTest(backend, "list orders numeric time then descending id", fixture -> {
            fixture.seed();
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3, 2, 1);
            ids(fixture.storage.list(B, null, 0, Long.MAX_VALUE, 99), 6);
        });
        storageTest(backend, "server filters, wildcard and inclusive/exclusive time boundaries", fixture -> {
            fixture.seed();
            ids(fixture.storage.list(A, ALPHA, 99, 2000, 99), 4, 3, 2);
            ids(fixture.storage.list(A, BETA, 0, Long.MAX_VALUE, 99), 5);
            ids(fixture.storage.list(A, null, 2000, 2001, 99), 5);
            ids(fixture.storage.list(A, "", 1000, 1000, 99));
            ids(fixture.storage.list(A, "absent", 0, Long.MAX_VALUE, 99));
            ids(fixture.storage.list(A, "alpha' OR 1=1 --", 0, Long.MAX_VALUE, 99));
        });
        storageTest(backend, "positive and nonpositive list limits", fixture -> {
            fixture.seed();
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 1), 5);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 0), 5, 4, 3, 2, 1);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, -1), 5, 4, 3, 2, 1);
        });
        storageTest(backend, "lastHash honors owner/server/time/id", fixture -> {
            fixture.seed();
            eq("hash-4", fixture.storage.getLastContentHash(A, ALPHA), "tied timestamp hash");
            eq("hash-5", fixture.storage.getLastContentHash(A, BETA), "server hash");
            eq("hash-5", fixture.storage.getLastContentHash(A, ""), "wildcard hash");
            eq("hash-5", fixture.storage.getLastContentHash(A, null), "null wildcard hash");
            eq("hash-6", fixture.storage.getLastContentHash(B, ALPHA), "owner hash");
            eq(null, fixture.storage.getLastContentHash(uuid(999), ""), "absent owner");
            eq(null, fixture.storage.getLastContentHash(A, "absent"), "absent server");
        });
        storageTest(backend, "list does not decode item payloads", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "hash-1");
            fixture.mutate(1, 9, "pages-json", "{malformed");
            fixture.mutate(1, 9, "creative-items-json", "{malformed");
            COPIES.set(0);
            LOGS.clear();
            fixture.queries.clear();
            ids(fixture.storage.list(A, ALPHA, 0, 10, 99), 1);
            eq(0, COPIES.get(), "list must not clone/decode stacks");
            eq(0, LOGS.size(), "list must not parse malformed JSON");
            if (backend == Backend.H2) {
                eq(1, fixture.queries.size(), "list query count");
                String projection = fixture.queries.get(0).toUpperCase().split(" FROM ")[0];
                check(!projection.contains("PAGES") && !projection.contains("CREATIVEITEMS") && !projection.contains("*"),
                        "SQL list selected payload columns: " + projection);
            }
        });
        storageTest(backend, "duplicate snapshot rejected without overwriting", fixture -> {
            InventorySnapshot snapshot = sample(1, A, 9, ALPHA);
            fixture.save(snapshot, "original");
            expect(Exception.class, () -> fixture.save(snapshot, "replacement"));
            eq("original", fixture.storage.getLastContentHash(A, ALPHA), "duplicate changed original");
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
        });
        storageTest(backend, "null JSON rejected without writing snapshot", fixture -> {
            InventorySnapshot snapshot = sample(1, A, 9, ALPHA);
            String[] valid = {"{}", "[]", "[]", "[null]", "[]"};
            for (int position = 0; position < valid.length; position++) {
                String[] json = valid.clone();
                json[position] = null;
                expect(IllegalArgumentException.class, () -> fixture.storage.save(snapshot, json[0], json[1], json[2], json[3], json[4], "h"));
            }
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99));
        });
        storageTest(backend, "zero/negative retention disables both limits", fixture -> {
            fixture.seed();
            fixture.storage.prune(A, "", 0, 0);
            fixture.storage.prune(A, "", -1, -1);
            fixture.storage.pruneAll(0);
            fixture.storage.pruneAll(-1);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3, 2, 1);
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 6);
        });
        storageTest(backend, "age-only retention preserves exact cutoff and other scopes", fixture -> {
            fixture.seed();
            fixture.storage.prune(A, ALPHA, 99, 0);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3, 2);
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 6);
            eq(null, fixture.storage.get(A, uuid(1), 9), "pruned direct get");
        });
        storageTest(backend, "count-only retention preserves newest ties and other scopes", fixture -> {
            fixture.seed();
            fixture.storage.prune(A, ALPHA, 0, 2);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3);
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 6);
        });
        storageTest(backend, "combined retention and wildcard server cap", fixture -> {
            fixture.seed();
            fixture.storage.prune(A, ALPHA, 1000, 3);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3);
            fixture.storage.prune(A, null, 0, 2);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4);
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 6);
        });
        storageTest(backend, "pruneAll crosses owners and servers but preserves boundary", fixture -> {
            fixture.seed();
            fixture.storage.pruneAll(2000);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5);
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 6);
        });
        storageTest(backend, "unknown and missing reason fallback logs and keeps data", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "hash-1");
            for (String reason : new String[]{"FUTURE_REASON", null}) {
                fixture.mutate(1, 9, "reason", reason);
                LOGS.clear();
                InventorySnapshotSummary summary = fixture.storage.list(A, "", 0, 10, 99).get(0);
                eq(SnapshotReason.PERIODIC, summary.reason(), "summary fallback");
                warn("Invalid history reason");
                LOGS.clear();
                InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
                check(result != null, "fallback lost snapshot");
                eq(SnapshotReason.PERIODIC, result.getReason(), "get fallback");
                stack(result.getPageItems(2).get(0), Material.STONE, 2);
                warn("Invalid history reason");
            }
        });
    }

    private static void sqlTests() {
        storageTest(Backend.H2, "real schema/index creation is repeatable", fixture -> {
            new InventoryHistorySQLStorage(fixture.connection, DatabaseType.H2, "history");
            String actualTableName = null;
            try (ResultSet tables = fixture.connection.getMetaData().getTables(null, null, "%", null)) {
                while (tables.next()) {
                    if ("history".equalsIgnoreCase(tables.getString("TABLE_NAME")))
                        actualTableName = tables.getString("TABLE_NAME");
                }
            }
            check(actualTableName != null, "history table missing from real database metadata");
            Map<Integer, String> columns = new TreeMap<>();
            try (ResultSet result = fixture.connection.getMetaData().getIndexInfo(null, null, actualTableName, false, false)) {
                while (result.next()) {
                    if ("IDX_HISTORY_PLAYER_TIME".equalsIgnoreCase(result.getString("INDEX_NAME")))
                        columns.put(result.getInt("ORDINAL_POSITION"), result.getString("COLUMN_NAME").toUpperCase());
                }
            }
            eq(List.of("PLAYERUUID", "CREATEDAT"), new ArrayList<>(columns.values()), "composite history index");
            fixture.save(sample(1, A, 9, ALPHA), "one");
            check(fixture.storage.get(A, uuid(1), 9) != null, "repeat constructor broke table");
        });
        storageTest(Backend.H2, "unsafe table identifiers and unsupported backend rejected", fixture -> {
            for (String table : new String[]{null, "", "9history", "bad-name", "history; DROP TABLE history", "x".repeat(65)})
                expect(SQLException.class, () -> new InventoryHistorySQLStorage(fixture.connection, DatabaseType.H2, table));
            expect(SQLException.class, () -> new InventoryHistorySQLStorage(null, DatabaseType.H2, "safe"));
            expect(SQLException.class, () -> new InventoryHistorySQLStorage(fixture.connection, DatabaseType.YAML, "safe"));
            new InventoryHistorySQLStorage(fixture.connection, DatabaseType.H2, "_safe_1");
        });
        storageTest(Backend.H2, "get uses unique id and owner, not timestamp path hint", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            check(fixture.storage.get(A, uuid(1), 999) != null, "SQL timestamp hint unexpectedly required");
            eq(null, fixture.storage.get("' OR 1=1 --", uuid(1), 9), "SQL owner parameter injection");
            eq(null, fixture.storage.get(A, "' OR 1=1 --", 9), "SQL id parameter injection");
        });
        storageTest(Backend.H2, "get logs corrupt items and preserves valid neighboring slots", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            String json = new Gson().toJson(slotsOfStrings("!bad-base64!", StringUtil.toBase64(item(Material.DIAMOND, 7))));
            fixture.mutate(1, 9, "pages-json", "{\"2\":" + json + "}");
            LOGS.clear();
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            eq(null, result.getPageItems(2).get(0), "bad item placeholder");
            stack(result.getPageItems(2).get(1), Material.DIAMOND, 7);
            warn("slot 0");
        });
        storageTest(Backend.H2, "get tolerates malformed equipment structure with safe empty fallback", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.mutate(1, 9, "armor-json", "{malformed");
            fixture.mutate(1, 9, "hotbar-json", "{malformed");
            LOGS.clear();
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            check(result != null, "malformed equipment structure lost snapshot");
            eq(new ArrayList<>(Collections.nCopies(4, null)), result.getArmor(), "malformed armor fallback");
            eq(new ArrayList<>(Collections.nCopies(9, null)), result.getHotbar(), "malformed hotbar fallback");
            stack(result.getOffhand(), Material.SHIELD, 1);
            warn("Unable to decode history items");
        });
        test("H2 legacy 13-column table migrates and keeps old rows", () -> {
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:h2:mem:history_legacy_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE `history` (ID VARCHAR(36) NOT NULL PRIMARY KEY, PLAYERUUID VARCHAR(50) NOT NULL, "
                                + "PLAYERNAME VARCHAR(50), CREATEDAT BIGINT NOT NULL, REASON VARCHAR(32), SERVERID VARCHAR(64), "
                                + "PAGECOUNT INT DEFAULT 0, CURRENTPAGE INT DEFAULT 0, MAXPAGE INT DEFAULT 0, "
                                + "USEDCREATIVE BOOLEAN DEFAULT FALSE, CONTENTHASH VARCHAR(64), PAGES TEXT, CREATIVEITEMS TEXT)")) {
                    statement.executeUpdate();
                }
                InventorySnapshot legacy = sample(1, A, 9, ALPHA);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO `history` (ID, PLAYERUUID, PLAYERNAME, CREATEDAT, REASON, SERVERID, PAGECOUNT, "
                                + "CURRENTPAGE, MAXPAGE, USEDCREATIVE, CONTENTHASH, PAGES, CREATIVEITEMS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    statement.setString(1, legacy.getId());
                    statement.setString(2, legacy.getPlayerUUID());
                    statement.setString(3, legacy.getPlayerName());
                    statement.setLong(4, legacy.getCreatedAt());
                    statement.setString(5, legacy.getReason().name());
                    statement.setString(6, legacy.getServerId());
                    statement.setInt(7, legacy.getPageCount());
                    statement.setInt(8, legacy.getCurrentPage());
                    statement.setInt(9, legacy.getMaxPage());
                    statement.setBoolean(10, legacy.isUsedCreative());
                    statement.setString(11, "legacy-hash");
                    statement.setString(12, StringUtil.pagesToJson(legacy.getPages()));
                    statement.setString(13, StringUtil.itemsToJson(legacy.getCreativeItems()));
                    statement.executeUpdate();
                }
                InventoryHistoryStorage storage = new InventoryHistorySQLStorage(connection, DatabaseType.H2, "history");
                InventorySnapshot result = storage.get(A, uuid(1), 9);
                check(result != null, "legacy row lost during migration");
                stack(result.getPageItems(2).get(2), Material.DIAMOND, 7);
                eq(new ArrayList<>(Collections.nCopies(4, null)), result.getArmor(), "migrated armor empty");
                eq(null, result.getOffhand(), "migrated offhand empty");
                eq(new ArrayList<>(Collections.nCopies(9, null)), result.getHotbar(), "migrated hotbar empty");
                ids(storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
                InventorySnapshot fresh = equipped(2, A, 99);
                storage.save(fresh, StringUtil.pagesToJson(fresh.getPages()), StringUtil.itemsToJson(fresh.getCreativeItems()),
                        StringUtil.itemsToJson(fresh.getArmor(), 4), StringUtil.itemToJson(fresh.getOffhand()),
                        StringUtil.itemsToJson(fresh.getHotbar(), 9), "hash-2");
                stack(storage.get(A, uuid(2), 99).getArmor().get(0), Material.DIAMOND_HELMET, 1);
                new InventoryHistorySQLStorage(connection, DatabaseType.H2, "history");
                check(storage.get(A, uuid(1), 9) != null, "repeat migration broke legacy row");
            }
        });
        test("H2 close closes the real owned connection", () -> {
            try (Fixture fixture = new Fixture(Backend.H2)) {
                fixture.storage.close();
                check(fixture.connection.isClosed(), "connection not closed");
            }
        });
    }

    private static void yamlTests() {
        storageTest(Backend.YAML, "one file per snapshot with string JSON payloads", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            List<Path> files;
            try (Stream<Path> stream = Files.walk(fixture.folder)) {
                files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            }
            eq(List.of(fixture.file(A, 1, 9)), files, "no index or extra snapshot files");
            YamlConfiguration config = fixture.config(1, 9);
            eq(A, config.getString("player-uuid"), "file owner");
            eq(9L, config.getLong("created-at"), "file timestamp");
            eq(2, config.getInt("page-count"), "file metadata");
            check(config.isString("pages-json") && config.isString("creative-items-json"), "payloads not opaque strings");
            check(config.isString("armor-json") && config.isString("offhand-json") && config.isString("hotbar-json"),
                    "equipment payloads not opaque strings");
            check(JsonParser.parseString(config.getString("pages-json")).isJsonObject(), "page object");
            check(JsonParser.parseString(config.getString("creative-items-json")).isJsonArray(), "creative array");
        });
        storageTest(Backend.YAML, "legacy file without equipment keys loads with empty equipment", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            YamlConfiguration config = fixture.config(1, 9);
            for (String key : new String[]{"armor-json", "offhand-json", "hotbar-json"})
                config.set(key, null);
            config.save(fixture.file(A, 1, 9).toFile());
            LOGS.clear();
            InventorySnapshot result = fixture.storage.get(A, uuid(1), 9);
            check(result != null, "legacy file rejected");
            eq(new ArrayList<>(Collections.nCopies(4, null)), result.getArmor(), "legacy armor");
            eq(null, result.getOffhand(), "legacy offhand");
            eq(new ArrayList<>(Collections.nCopies(9, null)), result.getHotbar(), "legacy hotbar");
            stack(result.getPageItems(2).get(2), Material.DIAMOND, 7);
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
            eq(0, LOGS.size(), "legacy file logged warnings");
        });
        storageTest(Backend.YAML, "malformed equipment JSON retained during pruning and get rejected", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.mutate(1, 9, "armor-json", "{malformed");
            Path corrupt = fixture.file(A, 1, 9);
            byte[] original = Files.readAllBytes(corrupt);
            LOGS.clear();
            eq(null, fixture.storage.get(A, uuid(1), 9), "malformed equipment accepted");
            warn("Skipping corrupt history snapshot");
            fixture.storage.prune(A, ALPHA, 100, 0);
            fixture.storage.pruneAll(100);
            check(Arrays.equals(original, Files.readAllBytes(corrupt)), "corrupt equipment file changed or deleted");
        });
        storageTest(Backend.YAML, "non-string equipment keys skipped and retained", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.save(equipped(2, A, 99), "two");
            fixture.mutate(1, 9, "hotbar-json", 42);
            LOGS.clear();
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 2);
            fixture.storage.pruneAll(Long.MAX_VALUE);
            check(Files.exists(fixture.file(A, 1, 9)), "non-string equipment key deleted");
            warn("Skipping corrupt history snapshot");
        });
        storageTest(Backend.YAML, "direct get does not scan corrupt siblings; requires exact timestamp", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            Files.writeString(fixture.file(A, 2, 99), "invalid: [", StandardCharsets.UTF_8);
            LOGS.clear();
            check(fixture.storage.get(A, uuid(1), 9) != null, "direct lookup missing");
            eq(0, LOGS.size(), "direct get scanned unrelated corrupt sibling");
            eq(null, fixture.storage.get(A, uuid(1), 99), "wrong timestamp should miss direct filename");
        });
        storageTest(Backend.YAML, "rejects traversal, noncanonical UUIDs and negative timestamps", fixture -> {
            for (String owner : new String[]{"../escape", "1-1-1-1-1", "", null})
                expect(IllegalArgumentException.class, () -> fixture.storage.list(owner, "", 0, 100, 10));
            expect(IllegalArgumentException.class, () -> fixture.storage.get(A, "../escape", 9));
            expect(IllegalArgumentException.class, () -> fixture.storage.get(A, uuid(1), -1));
        });
        storageTest(Backend.YAML, "malformed YAML retained during age/count/global pruning", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            fixture.save(sample(2, A, 99, ALPHA), "two");
            Path corrupt = fixture.file(A, 1, 9);
            byte[] bytes = "invalid: [".getBytes(StandardCharsets.UTF_8);
            Files.write(corrupt, bytes);
            LOGS.clear();
            ids(fixture.storage.list(A, "", 0, 1000, 99), 2);
            eq(null, fixture.storage.get(A, uuid(1), 9), "malformed YAML get");
            fixture.storage.prune(A, "", 1000, 1);
            fixture.storage.pruneAll(1000);
            check(Arrays.equals(bytes, Files.readAllBytes(corrupt)), "corrupt YAML changed or deleted");
            check(!Files.exists(fixture.file(A, 2, 99)), "valid expired file not pruned");
            warn("Skipping corrupt history snapshot");
        });
        storageTest(Backend.YAML, "identity mismatch and missing fields are skipped and retained", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            fixture.save(sample(2, A, 99, ALPHA), "two");
            fixture.save(sample(3, A, 1000, ALPHA), "three");
            fixture.mutate(1, 9, "player-uuid", B);
            fixture.mutate(2, 99, "created-at", 98L);
            fixture.mutate(3, 1000, "page-count", null);
            LOGS.clear();
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99));
            fixture.storage.pruneAll(Long.MAX_VALUE);
            check(Files.exists(fixture.file(A, 1, 9)), "foreign identity deleted");
            check(Files.exists(fixture.file(A, 2, 99)), "wrong timestamp deleted");
            check(Files.exists(fixture.file(A, 3, 1000)), "missing metadata deleted");
            warn("Skipping corrupt history snapshot");
        });
        storageTest(Backend.YAML, "malformed/wrong-top-level JSON retained and get rejected", fixture -> {
            String[][] corruptions = {{"pages-json", "{"}, {"pages-json", "[]"}, {"creative-items-json", "{}"}};
            int id = 1;
            for (String[] corruption : corruptions) {
                fixture.save(sample(id, A, id, ALPHA), "hash-" + id);
                fixture.mutate(id, id, corruption[0], corruption[1]);
                LOGS.clear();
                eq(null, fixture.storage.get(A, uuid(id), id), "invalid JSON accepted");
                warn("Skipping corrupt history snapshot");
                id++;
            }
            fixture.storage.prune(A, ALPHA, 999, 1);
            fixture.storage.pruneAll(999);
            for (int i = 1; i < id; i++)
                check(Files.exists(fixture.file(A, i, i)), "corrupt JSON file deleted: " + i);
        });
        storageTest(Backend.YAML, "invalid filenames retained and excluded from results", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            Path invalid = fixture.file(A, 1, 9).getParent().resolve("not-a-snapshot.yml");
            Files.writeString(invalid, "invalid: true\n", StandardCharsets.UTF_8);
            LOGS.clear();
            ids(fixture.storage.list(A, "", 0, 100, 99), 1);
            fixture.storage.pruneAll(100);
            check(Files.exists(invalid), "invalid filename was deleted");
            warn("Skipping invalid history snapshot path");
        });
        // Intentionally strict regression: syntactically valid JSON can still have an
        // invalid slot-list shape. Do not bless deletion of such recovery artifacts.
        storageTest(Backend.YAML, "retention preserves malformed nested page payload", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            fixture.mutate(1, 9, "pages-json", "{\"2\":{\"not\":\"a-slot-array\"}}");
            Path corrupt = fixture.file(A, 1, 9);
            byte[] original = Files.readAllBytes(corrupt);
            fixture.storage.prune(A, ALPHA, 100, 0);
            check(Files.exists(corrupt), "production deleted corrupt nested page JSON instead of retaining it");
            check(Arrays.equals(original, Files.readAllBytes(corrupt)), "corrupt nested JSON changed");
        });
        storageTest(Backend.YAML, "retention preserves malformed nested creative payload", fixture -> {
            fixture.save(sample(1, A, 9, ALPHA), "one");
            fixture.mutate(1, 9, "creative-items-json", "[{\"not\":\"a-base64-string\"}]");
            Path corrupt = fixture.file(A, 1, 9);
            fixture.storage.pruneAll(100);
            check(Files.exists(corrupt), "production deleted corrupt creative slot JSON instead of retaining it");
        });
    }

    private static void managerTests() {
        test("MANAGER fixture: periodic dedupe and persisted hash after cache restart", () -> {
            try (Fixture fixture = new Fixture(Backend.H2)) {
                try (ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                    check(manager.save(withReason(sample(1, A, 9, ALPHA), SnapshotReason.PERIODIC), false), "first save");
                    check(manager.save(withReason(sample(2, A, 99, ALPHA), SnapshotReason.PERIODIC), false), "dedupe completion");
                    ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
                }
                try (ManagerFixture restarted = new ManagerFixture(fixture.storage)) {
                    check(restarted.save(sample(3, A, 1000, ALPHA), false), "persisted dedupe completion");
                    ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
                }
            }
        });
        test("MANAGER fixture: important reasons and force bypass dedupe", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                check(manager.save(sample(1, A, 1, ALPHA), false), "seed save");
                int id = 2;
                for (SnapshotReason reason : new SnapshotReason[]{SnapshotReason.MANUAL, SnapshotReason.BEFORE_CLEAR,
                        SnapshotReason.BEFORE_CLEAR_ALL, SnapshotReason.BEFORE_RESTORE, SnapshotReason.DISABLE}) {
                    InventorySnapshot base = sample(id, A, id, ALPHA);
                    check(manager.save(withReason(base, reason), false), "important reason " + reason);
                    id++;
                }
                check(manager.save(sample(7, A, 7, ALPHA), true), "forced JOIN");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 7, 6, 5, 4, 3, 2, 1);
            }
        });
        test("MANAGER fixture: hash includes item/current/max/creative state", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                InventorySnapshot base = sample(1, A, 1, ALPHA);
                check(manager.save(base, false), "seed save");
                check(manager.save(new InventorySnapshot(uuid(2), A, "Alice", 2, SnapshotReason.PERIODIC, ALPHA,
                        3, 10, true, base.getPages(), base.getCreativeItems()), false), "current-page save");
                check(manager.save(new InventorySnapshot(uuid(3), A, "Alice", 3, SnapshotReason.PERIODIC, ALPHA,
                        3, 11, true, base.getPages(), base.getCreativeItems()), false), "max-page save");
                check(manager.save(new InventorySnapshot(uuid(4), A, "Alice", 4, SnapshotReason.PERIODIC, ALPHA,
                        3, 11, false, base.getPages(), base.getCreativeItems()), false), "creative flag save");
                Map<Integer, List<ItemStack>> changed = new HashMap<>(base.getPages());
                changed.put(2, slots(item(Material.STONE, 33)));
                check(manager.save(new InventorySnapshot(uuid(5), A, "Alice", 5, SnapshotReason.PERIODIC, ALPHA,
                        3, 11, false, changed, List.of()), false), "changed item save");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 5, 4, 3, 2, 1);
            }
        });
        test("MANAGER fixture: hash includes armor/offhand/hotbar state", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                InventorySnapshot base = sample(1, A, 1, ALPHA);
                check(manager.save(base, false), "seed save");
                check(manager.save(new InventorySnapshot(uuid(2), A, "Alice", 2, SnapshotReason.PERIODIC, ALPHA,
                        2, 10, true, base.getPages(), base.getCreativeItems(),
                        slots(item(Material.DIAMOND_HELMET, 1)), null, base.getHotbar()), false), "armor save");
                check(manager.save(new InventorySnapshot(uuid(3), A, "Alice", 3, SnapshotReason.PERIODIC, ALPHA,
                        2, 10, true, base.getPages(), base.getCreativeItems(),
                        base.getArmor(), item(Material.SHIELD, 1), base.getHotbar()), false), "offhand save");
                check(manager.save(new InventorySnapshot(uuid(4), A, "Alice", 4, SnapshotReason.PERIODIC, ALPHA,
                        2, 10, true, base.getPages(), base.getCreativeItems(),
                        base.getArmor(), null, slots(item(Material.BREAD, 5))), false), "hotbar save");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 4, 3, 2, 1);
            }
        });
        test("MANAGER fixture: owner/server scoped dedupe and real worker I/O", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                check(manager.save(sample(1, A, 1, ALPHA), false), "alpha");
                check(manager.save(sample(2, A, 2, BETA), false), "beta");
                check(manager.save(sample(3, B, 3, ALPHA), false), "other owner");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 2, 1);
                ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99), 3);
                check(!manager.ioThreads.isEmpty(), "no worker calls observed");
                check(manager.ioThreads.stream().allMatch(thread -> thread != MAIN_THREAD), "I/O happened on caller thread");
                eq(1L, manager.ioThreads.stream().distinct().count(), "I/O used more than one worker");
            }
        });
        test("MANAGER fixture: failed save returns false, retry does not dedupe it", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                manager.failNextSave = true;
                LOGS.clear();
                check(!manager.save(sample(1, A, 1, ALPHA), false), "failed storage save reported success");
                warn("History failed to save");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99));
                check(manager.save(sample(2, A, 2, ALPHA), false), "retry failed");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 2);
            }
        });
        test("MANAGER fixture: rejected worker submission completes false", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                manager.worker.shutdown();
                check(!manager.save(sample(1, A, 1, ALPHA), false), "rejected task succeeded");
                check(manager.ioThreads.isEmpty(), "rejected task performed I/O");
            }
        });
    }

    private static void creativeRestoreTests() {
        boolean previousConfig = Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY;
        try {
            Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY = false;
            test("CREATIVE storage load recovers the used-creative flag from persisted items", () -> {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field singleton = unsafeClass.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);
                Object unsafe = singleton.get(null);
                me.cortezromeo.inventorypagesplus.storage.PlayerInventory loaded =
                        (me.cortezromeo.inventorypagesplus.storage.PlayerInventory) unsafeClass
                                .getMethod("allocateInstance", Class.class).invoke(unsafe,
                                        me.cortezromeo.inventorypagesplus.storage.PlayerInventory.class);
                loaded.setUsedCreative(false);
                loaded.setCreativeItems(new ArrayList<>(Arrays.asList(null, item(Material.EMERALD, 4))));
                check(loaded.hasUsedCreative(), "flag not recovered from loaded creative items");
                me.cortezromeo.inventorypagesplus.storage.PlayerInventory empty =
                        (me.cortezromeo.inventorypagesplus.storage.PlayerInventory) unsafeClass
                                .getMethod("allocateInstance", Class.class).invoke(unsafe,
                                        me.cortezromeo.inventorypagesplus.storage.PlayerInventory.class);
                empty.setUsedCreative(false);
                empty.setCreativeItems(new ArrayList<>(Arrays.asList(null, null)));
                check(!empty.hasUsedCreative(), "all-null creative list raised the flag");
            });
            test("CREATIVE snapshot keeps the creative payload while the config is disabled", () -> {
                StubInventory live = new StubInventory();
                live.items.put(2, new ArrayList<>(Arrays.asList(item(Material.DIRT, 3))));
                live.creative.add(item(Material.EMERALD, 4));
                live.usedCreative = true;
                InventorySnapshot snapshot = live.snapshot(SnapshotReason.BEFORE_RESTORE);
                check(snapshot.isUsedCreative(), "config gate dropped creative from the snapshot");
                stack(snapshot.getCreativeItems().get(0), Material.EMERALD, 4);
                live.usedCreative = false;
                InventorySnapshot unused = live.snapshot(SnapshotReason.PERIODIC);
                check(!unused.isUsedCreative(), "unused flag recorded as used");
                eq(0, unused.getCreativeItems().size(), "unused creative payload kept");
            });
            test("CREATIVE legacy no-payload restore keeps live creative and stays rollable", () -> {
                StubInventory live = new StubInventory();
                live.creative.add(item(Material.DIAMOND, 2));
                live.usedCreative = true;
                InventorySnapshot before = live.snapshot(SnapshotReason.BEFORE_RESTORE);
                InventorySnapshot legacy = new InventorySnapshot(uuid(9), A, "Alice", 9, SnapshotReason.JOIN, ALPHA,
                        2, 10, false, new HashMap<>(live.items), List.of());
                live.apply(legacy);
                eq(1, live.creative.size(), "legacy snapshot wiped live creative items");
                stack(live.creative.get(0), Material.DIAMOND, 2);
                check(live.usedCreative, "legacy snapshot cleared the used flag");
                live.creative.set(0, item(Material.DIAMOND, 99));
                live.apply(before);
                eq(27, live.creative.size(), "rollback payload not padded");
                stack(live.creative.get(0), Material.DIAMOND, 2);
            });
            test("CREATIVE payload restore replaces live creative and pads to 27 slots", () -> {
                StubInventory live = new StubInventory();
                live.creative.add(item(Material.DIRT, 1));
                live.usedCreative = true;
                InventorySnapshot source = new InventorySnapshot(uuid(8), A, "Alice", 8, SnapshotReason.MANUAL, ALPHA,
                        3, 12, true, new HashMap<>(live.items), Arrays.asList(null, item(Material.EMERALD, 7)));
                live.apply(source);
                eq(27, live.creative.size(), "creative not padded to 27");
                check(live.creative.get(0) == null, "slot 0 not cleared");
                stack(live.creative.get(1), Material.EMERALD, 7);
                check(live.usedCreative, "flag not raised by payload apply");
                eq(3, live.currentPage, "current page not restored");
                eq(12, live.maxPage, "max page not raised");
            });
            test("CREATIVE safety comparison detects creative edits while the config is disabled", () -> {
                StubInventory live = new StubInventory();
                live.items.put(2, new ArrayList<>(Arrays.asList(item(Material.DIRT, 3))));
                live.creative.add(item(Material.EMERALD, 4));
                live.usedCreative = true;
                InventorySnapshot before = live.snapshot(SnapshotReason.BEFORE_RESTORE);
                check(live.unchangedSinceSafety(before), "unchanged data reported as changed");
                live.creative.set(0, item(Material.EMERALD, 5));
                check(!live.unchangedSinceSafety(before), "creative edit missed");
            });
            test("HISTORY snapshot raises max page to the highest page holding items", () -> {
                StubInventory live = new StubInventory();
                live.maxPage = 1;
                live.currentPage = 1;
                live.items.put(0, new ArrayList<>(Arrays.asList(item(Material.STONE, 1))));
                live.items.put(3, new ArrayList<>(Arrays.asList(item(Material.DIRT, 3))));
                live.items.put(6, new ArrayList<>(Arrays.asList((ItemStack) null, null)));
                InventorySnapshot snapshot = live.snapshot(SnapshotReason.MANUAL);
                eq(3, snapshot.getMaxPage(), "max page not raised to the highest page with items");
                check(live.unchangedSinceSafety(snapshot), "safety comparison misses the raised max page");
            });
        } finally {
            Settings.INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY = previousConfig;
        }
    }

    private static void extractionTests() {
        test("EXTRACTION copies armor/offhand items, snapshot stays intact and copies repeat", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                ItemStack taken = context.extract(SnapshotSlotType.ARMOR, 0, 0);
                stack(taken, Material.DIAMOND_HELMET, 1);
                taken.setAmount(64);
                InventorySnapshot result = context.fixture.storage.get(A, uuid(1), 9);
                stack(result.getArmor().get(0), Material.DIAMOND_HELMET, 1);
                stack(result.getArmor().get(1), Material.DIAMOND_CHESTPLATE, 1);
                stack(result.getOffhand(), Material.SHIELD, 1);
                stack(result.getHotbar().get(0), Material.DIAMOND_SWORD, 1);
                stack(result.getPageItems(2).get(2), Material.DIAMOND, 7);
                eq("hash-1", context.fixture.storage.getLastContentHash(A, ALPHA), "copy changed hash");
                stack(context.extract(SnapshotSlotType.OFFHAND, 0, 0), Material.SHIELD, 1);
                stack(context.extract(SnapshotSlotType.ARMOR, 0, 0), Material.DIAMOND_HELMET, 1);
                stack(context.fixture.storage.get(A, uuid(1), 9).getOffhand(), Material.SHIELD, 1);
            }
        });
        test("EXTRACTION page copy keeps the page and its items", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                Map<Integer, List<ItemStack>> pages = new HashMap<>();
                pages.put(3, slots(item(Material.EMERALD, 2)));
                InventorySnapshot snapshot = new InventorySnapshot(uuid(1), A, "Alice", 9, SnapshotReason.JOIN, ALPHA,
                        0, 10, false, pages, null, null, null, null);
                context.fixture.save(snapshot, "hash-1");
                stack(context.extract(SnapshotSlotType.PAGE, 3, 0), Material.EMERALD, 2);
                InventorySnapshot result = context.fixture.storage.get(A, uuid(1), 9);
                stack(result.getPageItems(3).get(0), Material.EMERALD, 2);
                eq(1, result.getPageCount(), "page count changed");
                eq("hash-1", context.fixture.storage.getLastContentHash(A, ALPHA), "page copy changed hash");
            }
        });
        test("EXTRACTION hotbar copy works; empty/missing targets leave storage untouched", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                stack(context.extract(SnapshotSlotType.HOTBAR, 0, 2), Material.BREAD, 5);
                stack(context.fixture.storage.get(A, uuid(1), 9).getHotbar().get(2), Material.BREAD, 5);
                LOGS.clear();
                eq(null, context.extract(SnapshotSlotType.HOTBAR, 0, 1), "empty hotbar slot yielded item");
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 99), "out-of-range slot yielded item");
                eq(null, context.extract(SnapshotSlotType.PAGE, 77, 0), "missing page yielded item");
                eq(0, LOGS.size(), "no-op extraction logged warnings");
                LOGS.clear();
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0, uuid(99), 9), "missing snapshot yielded item");
                warn("History failed to extract item");
                eq("hash-1", context.fixture.storage.getLastContentHash(A, ALPHA), "extraction changed hash");
            }
        });
        test("EXTRACTION denied without permission or when disabled", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                context.permission = false;
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "permission bypass");
                context.permission = true;
                Settings.HISTORY_EXTRACTION_ENABLED = false;
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "disabled bypass");
                Settings.HISTORY_EXTRACTION_ENABLED = true;
                eq(null, context.extract(null, 0, 0), "null slot type bypass");
                eq(0, context.manager.ioThreads.size(), "denied extraction performed I/O");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
                stack(context.extract(SnapshotSlotType.ARMOR, 0, 0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION refuses snapshots from another server", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                Settings.HISTORY_SERVER_ID = BETA;
                LOGS.clear();
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "foreign server snapshot extracted");
                warn("History failed to extract item");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION failed load keeps snapshot and retry succeeds", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                context.failNextGet = true;
                LOGS.clear();
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "failed load yielded item");
                warn("History failed to extract item");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
                eq("hash-1", context.fixture.storage.getLastContentHash(A, ALPHA), "failed load changed hash");
                stack(context.extract(SnapshotSlotType.ARMOR, 0, 0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION permission revoked while loading prevents delivery", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                context.revokeAfterGet = true;
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "revoked permission still received item");
                eq(0, context.delivered, "inventory was modified");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION mutable addItem input does not change result or stored record", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                context.mutateDeliveredItem = true;
                stack(context.extract(SnapshotSlotType.ARMOR, 0, 0), Material.DIAMOND_HELMET, 1);
                eq(1, context.delivered, "item not delivered");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION shutdown discards GUI callback instead of running it on the worker", () -> {
            CompletableFuture<Void> release = new CompletableFuture<>();
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                CompletableFuture<ItemStack> result = new CompletableFuture<>();
                context.manager.worker.submit(release::join);
                try {
                    context.manager.manager.extractItem(context.admin, A, uuid(1), 9, SnapshotSlotType.ARMOR, 0, 0, result::complete);
                    setField(JavaPlugin.class, InventoryPagesPlus.plugin, "isEnabled", false);
                } finally {
                    release.complete(null);
                }
                context.manager.worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
                check(!result.isDone(), "GUI callback ran after scheduler became unavailable");
                eq(0, context.delivered, "item delivered during shutdown");
                stack(context.fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
            }
        });
        test("EXTRACTION offline admin at delivery gets nothing and snapshot stays intact", () -> {
            try (ExtractionFixture context = new ExtractionFixture()) {
                context.fixture.save(equipped(1, A, 9), "hash-1");
                context.goOfflineAfterGet = true;
                eq(null, context.extract(SnapshotSlotType.ARMOR, 0, 0), "offline admin received item");
                InventorySnapshot result = context.fixture.storage.get(A, uuid(1), 9);
                stack(result.getArmor().get(0), Material.DIAMOND_HELMET, 1);
                eq("hash-1", context.fixture.storage.getLastContentHash(A, ALPHA), "offline delivery changed hash");
                FIXTURE_PLAYERS.put(context.adminId, context.admin);
                stack(context.extract(SnapshotSlotType.ARMOR, 0, 0), Material.DIAMOND_HELMET, 1);
            }
        });
    }

    /** Drives the real extractItem pipeline with an inline scheduler and a fake admin player. */
    private static final class ExtractionFixture implements AutoCloseable {
        final Fixture fixture;
        final ManagerFixture manager;
        final UUID adminId = UUID.randomUUID();
        final Player admin;
        final boolean oldEnabled = Settings.HISTORY_ENABLED;
        final boolean oldExtraction = Settings.HISTORY_EXTRACTION_ENABLED;
        final String oldServerId = Settings.HISTORY_SERVER_ID;
        volatile boolean permission = true;
        volatile boolean failNextGet;
        volatile boolean goOfflineAfterGet;
        volatile boolean revokeAfterGet;
        volatile boolean mutateDeliveredItem;
        volatile int delivered;

        ExtractionFixture() throws Exception {
            fixture = new Fixture(Backend.H2);
            InventoryHistoryStorage observed = proxy(InventoryHistoryStorage.class, (proxy, method, args) -> {
                if (method.getName().equals("get") && failNextGet) {
                    failNextGet = false;
                    throw new IOException("Deliberately injected test load failure");
                }
                Object result = invoke(method, fixture.storage, args);
                if (method.getName().equals("get") && revokeAfterGet) {
                    revokeAfterGet = false;
                    permission = false;
                }
                if (method.getName().equals("get") && goOfflineAfterGet) {
                    goOfflineAfterGet = false;
                    FIXTURE_PLAYERS.remove(adminId);
                }
                return result;
            });
            manager = new ManagerFixture(observed);
            Settings.HISTORY_ENABLED = true;
            Settings.HISTORY_EXTRACTION_ENABLED = true;
            Settings.HISTORY_SERVER_ID = "";
            setField(InventoryHistoryManager.class, manager.manager, "available", true);
            setField(JavaPlugin.class, InventoryPagesPlus.plugin, "isEnabled", true);
            admin = proxy(Player.class, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getUniqueId": return adminId;
                    case "isOnline": return FIXTURE_PLAYERS.containsKey(adminId);
                    case "hasPermission": return permission;
                    case "getName": return "Admin";
                    case "getInventory": return proxy(PlayerInventory.class, (invProxy, invMethod, invArgs) -> {
                        if (invMethod.getName().equals("addItem")) {
                            delivered++;
                            if (mutateDeliveredItem)
                                ((ItemStack[]) invArgs[0])[0].setAmount(0);
                            return new HashMap<Integer, ItemStack>();
                        }
                        throw new UnsupportedOperationException("Fixture PlayerInventory: " + invMethod);
                    });
                    default: throw new UnsupportedOperationException("Fixture Player: " + method);
                }
            });
            FIXTURE_PLAYERS.put(adminId, admin);
        }

        ItemStack extract(SnapshotSlotType type, int page, int slot) throws Exception {
            return extract(type, page, slot, uuid(1), 9);
        }

        ItemStack extract(SnapshotSlotType type, int page, int slot, String id, long time) throws Exception {
            CompletableFuture<ItemStack> result = new CompletableFuture<>();
            manager.manager.extractItem(admin, A, id, time, type, page, slot, result::complete);
            ItemStack taken = result.get(10, TimeUnit.SECONDS);
            manager.worker.submit(() -> { }).get(10, TimeUnit.SECONDS); // Drain the worker queue, no polling/sleep.
            return taken;
        }

        @Override
        public void close() throws Exception {
            FIXTURE_PLAYERS.remove(adminId);
            manager.close();
            fixture.close();
            Settings.HISTORY_ENABLED = oldEnabled;
            Settings.HISTORY_EXTRACTION_ENABLED = oldExtraction;
            Settings.HISTORY_SERVER_ID = oldServerId;
            setField(JavaPlugin.class, InventoryPagesPlus.plugin, "isEnabled", false);
        }
    }

    private static void dailyHistoryTests() {
        test("DATE strict calendar parsing and configurable separators", () -> {
            DateTimeFormatter formatter = HistoryDates.formatter("dd-MM-yyyy");
            eq(LocalDate.of(2024, 2, 29), LocalDate.parse("29-02-2024", formatter), "leap day");
            for (String invalid : List.of("29-02-2023", "31-04-2026", "17-11-2026 12:30", "17/11/2026"))
                expect(DateTimeParseException.class, () -> LocalDate.parse(invalid, formatter));
            eq(LocalDate.of(2026, 11, 17), LocalDate.parse("2026/11/17", HistoryDates.formatter("yyyy/MM/dd")), "custom format");
            for (String invalid : List.of("yyyy-MM-dd HH:mm:ss", "MM-dd", "yyyy-DDD", "yyyy MM dd", "yyyyMMdd", "yyy-MM-dd"))
                expect(RuntimeException.class, () -> HistoryDates.formatter(invalid));
            DateTimeFormatter previous = Settings.HISTORY_DATE_FORMATTER;
            try {
                Settings.HISTORY_DATE_FORMATTER = formatter;
                for (String invalid : List.of("31-12-+999999999", "01-01-0000", "01-01--0001"))
                    expect(DateTimeParseException.class, () -> HistoryDates.parse(invalid));
                eq(LocalDate.of(9999, 12, 31), HistoryDates.parse("31-12-9999"), "four-digit maximum year");
            } finally {
                Settings.HISTORY_DATE_FORMATTER = previous;
            }
            eq(0, HistoryDates.page("1"), "one-based page");
            eq(2147483646, HistoryDates.page("2147483647"), "largest supported page");
            for (String invalid : List.of("0", "-1", "abc", "2147483648"))
                expect(NumberFormatException.class, () -> HistoryDates.page(invalid));
        });
        test("DATE three calendar months and DST day boundaries", () -> {
            ZoneId old = Settings.HISTORY_ZONE;
            try {
                Settings.HISTORY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
                long cutoff = HistoryDates.cutoff(LocalDate.of(2026, 5, 31), 3, Settings.HISTORY_ZONE);
                eq(LocalDate.of(2026, 2, 28), HistoryDates.date(cutoff), "month-end cutoff");
                eq(LocalDate.of(2026, 2, 27), HistoryDates.date(cutoff - 1), "cutoff boundary");
                Settings.HISTORY_ZONE = ZoneId.of("America/New_York");
                LocalDate date = LocalDate.of(2026, 3, 8);
                eq(23 * 3600000L, HistoryDates.start(date.plusDays(1)) - HistoryDates.start(date), "DST is not 24 hours");
            } finally {
                Settings.HISTORY_ZONE = old;
            }
        });
        test("MANAGER unchanged inventory still gets history on a new date", () -> {
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                long day = HistoryDates.start(LocalDate.of(2026, 11, 17));
                check(manager.save(sample(1, A, day, ALPHA), false), "first day");
                check(manager.save(sample(2, A, day + 1000, ALPHA), false), "same day dedupe");
                check(manager.save(sample(3, A, HistoryDates.start(LocalDate.of(2026, 11, 18)), ALPHA), false), "next day");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 3, 1);
            }
        });
        test("MANAGER pending save limit fails without losing accepted work", () -> {
            int old = Settings.HISTORY_MAX_PENDING;
            CompletableFuture<Void> release = new CompletableFuture<>();
            try (Fixture fixture = new Fixture(Backend.H2); ManagerFixture manager = new ManagerFixture(fixture.storage)) {
                Settings.HISTORY_MAX_PENDING = 1;
                manager.worker.submit(release::join);
                Object accepted = manager.save.invoke(manager.manager, sample(1, A, 1, ALPHA), true);
                Object rejected = manager.save.invoke(manager.manager, sample(2, A, 2, ALPHA), true);
                eq(false, ((CompletableFuture<?>) rejected).get(10, TimeUnit.SECONDS), "overflow accepted");
                release.complete(null);
                eq(true, ((CompletableFuture<?>) accepted).get(10, TimeUnit.SECONDS), "accepted save lost");
                ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99), 1);
            } finally {
                release.complete(null);
                Settings.HISTORY_MAX_PENDING = old;
            }
        });
        test("GUI configuration separates item slots, day/change/page controls and contains no clock", () -> {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/historyinventory.yml").toFile());
            Set<Integer> controls = new HashSet<>();
            for (String key : List.of("close", "prevDay", "nextDay", "prevChange", "nextChange", "prevPage", "nextPage", "info", "restore")) {
                int slot = config.getInt("items." + key + ".slot", -1);
                check(slot >= 0 && slot < 45 && slot % 9 >= 6 && slot != 6 || slot == 15 || slot == 24, "control overlaps items: " + key);
                check(controls.add(slot), "duplicate control slot");
            }
            for (int slot : config.getIntegerList("items.border.slots"))
                check(!controls.contains(slot), "border overlaps a control");
            String content = Files.readString(Path.of("src/main/resources/historyinventory.yml"));
            check(!content.toLowerCase().contains("snapshot") && !content.contains("HH:mm"), "old viewer text remains");
        });
    }

    private static void retentionTests(Backend backend) {
        storageTest(backend, "daily adjacent navigation is scoped, stable at timestamp ties and unbounded by list size", fixture -> {
            fixture.seed();
            InventorySnapshotSummary reference = fixture.storage.list(A, ALPHA, 0, Long.MAX_VALUE, 1).get(0);
            InventorySnapshotSummary previous = fixture.storage.adjacent(reference, ALPHA, 0, Long.MAX_VALUE, true);
            eq(uuid(3), previous.id(), "timestamp tie skipped");
            eq(uuid(4), fixture.storage.adjacent(previous, ALPHA, 0, Long.MAX_VALUE, false).id(), "reverse tie navigation");
            eq(null, fixture.storage.adjacent(reference, ALPHA, 0, Long.MAX_VALUE, false), "cross-server or owner leak");
            eq(null, fixture.storage.adjacent(previous, ALPHA, 1000, 2000, true), "escaped selected date");
            COPIES.set(0);
            fixture.storage.adjacent(reference, ALPHA, 0, Long.MAX_VALUE, true);
            eq(0, COPIES.get(), "adjacent decoded items");
        });
        storageTest(backend, "ZIP stores byte-exact raw records before removing expired sources", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.save(equipped(2, A, 99), "two");
            fixture.save(sample(3, A, 9, BETA), "other-server");
            HistoryStoredRecord original = fixture.storage.exportRecord(fixture.storage.list(A, ALPHA, 0, 10, 1).get(0));
            HistoryRetention retention = new HistoryRetention(fixture.folder);
            COPIES.set(0);
            eq(1, retention.process(fixture.storage, 99, ALPHA, 50, true), "expired count");
            eq(0, COPIES.get(), "archival decoded item payload");
            eq(null, fixture.storage.get(A, uuid(1), 9), "expired source remains");
            check(fixture.storage.get(A, uuid(2), 99) != null, "cutoff boundary removed");
            check(fixture.storage.get(A, uuid(3), 9) != null, "other server removed");
            List<Path> archives = archives(fixture.folder);
            eq(1, archives.size(), "archive count");
            try (ZipFile zip = new ZipFile(archives.get(0).toFile())) {
                check(Arrays.equals(original.getPayload(), zip.getInputStream(zip.getEntry(original.entryName())).readAllBytes()), "archive payload changed");
            }
        });
        storageTest(backend, "DELETE expires offline owners in bounded batches without creating archives", fixture -> {
            fixture.seed();
            HistoryRetention retention = new HistoryRetention(fixture.folder);
            int total = 0;
            for (int iteration = 0; iteration < 12; iteration++) {
                int removed = retention.process(fixture.storage, Long.MAX_VALUE, "", 1, false);
                check(removed <= 1, "batch exceeded");
                total += removed;
            }
            eq(6, total, "offline players not fully processed");
            ids(fixture.storage.list(A, "", 0, Long.MAX_VALUE, 99));
            ids(fixture.storage.list(B, "", 0, Long.MAX_VALUE, 99));
            check(!Files.exists(fixture.folder.resolve("history-archives")), "DELETE wrote archive");
        });
        storageTest(backend, "archive I/O failure leaves source intact", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            Files.writeString(fixture.folder.resolve("history-archives"), "blocked directory");
            HistoryRetention retention = new HistoryRetention(fixture.folder);
            expect(IOException.class, () -> retention.process(fixture.storage, 100, ALPHA, 50, true));
            stack(fixture.storage.get(A, uuid(1), 9).getArmor().get(0), Material.DIAMOND_HELMET, 1);
            eq("one", fixture.storage.getLastContentHash(A, ALPHA), "archive failure changed hash");
        });
        storageTest(backend, "existing verified ZIP is reusable and corruption prevents source deletion", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            HistoryStoredRecord record = fixture.storage.exportRecord(fixture.storage.list(A, ALPHA, 0, 100, 1).get(0));
            HistoryRetention retention = new HistoryRetention(fixture.folder);
            Path archive = retention.writeArchive(List.of(record));
            eq(archive, retention.writeArchive(List.of(record)), "retry created another archive");
            Files.writeString(archive, "truncated archive");
            expect(IOException.class, () -> retention.process(fixture.storage, 100, ALPHA, 50, true));
            check(fixture.storage.get(A, uuid(1), 9) != null, "corrupt archive deleted source");
        });
        storageTest(backend, "raw corrupt item data is archived unchanged, not silently normalized", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.mutate(1, 9, "armor-json", "[\"!invalid-base64!\"]");
            InventorySnapshotSummary summary = fixture.storage.list(A, ALPHA, 0, 100, 1).get(0);
            HistoryStoredRecord record = fixture.storage.exportRecord(summary);
            COPIES.set(0);
            HistoryRetention retention = new HistoryRetention(fixture.folder);
            eq(1, retention.process(fixture.storage, 100, ALPHA, 50, true), "corrupt item archive failed");
            eq(0, COPIES.get(), "corrupt item decoded");
            try (ZipFile zip = new ZipFile(archives(fixture.folder).get(0).toFile())) {
                check(Arrays.equals(record.getPayload(), zip.getInputStream(zip.getEntry(record.entryName())).readAllBytes()), "raw corruption lost");
            }
        });
        storageTest(backend, "changed source hash cannot be deleted using an older exported record", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            HistoryStoredRecord record = fixture.storage.exportRecord(fixture.storage.list(A, ALPHA, 0, 100, 1).get(0));
            fixture.mutate(1, 9, "content-hash", "changed");
            check(!fixture.storage.deleteRecord(record), "changed source deleted");
            check(fixture.storage.get(A, uuid(1), 9) != null, "source lost");
        });
        storageTest(backend, "changed payload with unchanged hash is retained and the connection remains usable", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            HistoryStoredRecord original = fixture.storage.exportRecord(fixture.storage.list(A, ALPHA, 0, 100, 1).get(0));
            fixture.mutate(1, 9, "armor-json", "[]");
            check(!fixture.storage.deleteRecord(original), "modified payload deleted");
            eq("one", fixture.storage.getLastContentHash(A, ALPHA), "hash changed");
            if (fixture.connection != null)
                check(fixture.connection.getAutoCommit(), "autocommit not restored after refusal");
            HistoryStoredRecord current = fixture.storage.exportRecord(original.getSummary());
            check(fixture.storage.deleteRecord(current), "matching source not deleted");
            if (fixture.connection != null)
                check(fixture.connection.getAutoCommit(), "autocommit not restored after delete");
            check(!fixture.storage.deleteRecord(current), "missing source reported deleted");
        });
        storageTest(backend, "archive expiry honors newest entry and leaves unrelated files", fixture -> {
            fixture.save(equipped(1, A, 9), "one");
            fixture.save(equipped(2, A, 99), "two");
            try (HistoryRetention retention = new HistoryRetention(fixture.folder)) {
                retention.process(fixture.storage, 100, ALPHA, 50, true);
                Path unrelated = fixture.folder.resolve("history-archives/manual-backup.zip");
                Files.writeString(unrelated, "keep");
                eq(0, retention.cleanArchives(99, 1), "boundary archive deleted");
                eq(1, retention.cleanArchives(100, 1), "old archive retained");
                check(Files.exists(unrelated), "unrelated archive deleted");
            }
        });
        storageTest(backend, "archive scan is bounded and resumes without retaining open handles after close", fixture -> {
            Path directory = Files.createDirectory(fixture.folder.resolve("history-archives"));
            for (int index = 0; index < 1005; index++)
                Files.writeString(directory.resolve("archive-" + index + "-" + "0".repeat(64) + ".zip"), "expired");
            try (HistoryRetention retention = new HistoryRetention(fixture.folder)) {
                eq(1000, retention.cleanArchives(2000, 2000), "scan exceeded 1000 paths");
                eq(5, retention.cleanArchives(2000, 2000), "scan did not resume");
                eq(0, retention.cleanArchives(2000, 2000), "empty scan");
                eq(0, retention.cleanArchives(0, 2000), "disabled archive expiry");
            }
            Files.delete(directory);
        });
    }

    private static List<Path> archives(Path folder) throws IOException {
        try (Stream<Path> paths = Files.list(folder.resolve("history-archives"))) {
            return paths.filter(path -> path.toString().endsWith(".zip")).collect(Collectors.toList());
        }
    }

    private enum Backend { H2, YAML }

    private static final class Fixture implements AutoCloseable {
        final Backend backend;
        final Path folder;
        final Connection connection;
        final InventoryHistoryStorage storage;
        final List<String> queries = new CopyOnWriteArrayList<>();

        Fixture(Backend backend) throws Exception {
            this.backend = backend;
            folder = Files.createTempDirectory(output, backend.name().toLowerCase() + "-");
            if (backend == Backend.H2) {
                connection = DriverManager.getConnection("jdbc:h2:mem:history_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL");
                Connection observed = proxy(Connection.class, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement"))
                        queries.add((String) args[0]);
                    return invoke(method, connection, args);
                });
                storage = new InventoryHistorySQLStorage(observed, DatabaseType.H2, "history");
            } else {
                connection = null;
                setField(JavaPlugin.class, InventoryPagesPlus.plugin, "dataFolder", folder.toFile());
                storage = new InventoryHistoryYAMLStorage();
            }
        }

        void save(InventorySnapshot snapshot, String hash) throws Exception {
            storage.save(snapshot, StringUtil.pagesToJson(snapshot.getPages()), StringUtil.itemsToJson(snapshot.getCreativeItems()),
                    StringUtil.itemsToJson(snapshot.getArmor(), 4), StringUtil.itemToJson(snapshot.getOffhand()),
                    StringUtil.itemsToJson(snapshot.getHotbar(), 9), hash);
        }

        void seed() throws Exception {
            save(sample(2, A, 99, ALPHA), "hash-2");
            save(sample(1, A, 9, ALPHA), "hash-1");
            save(sample(5, A, 2000, BETA), "hash-5");
            save(sample(4, A, 1000, ALPHA), "hash-4");
            save(sample(3, A, 1000, ALPHA), "hash-3");
            save(sample(6, B, 3000, ALPHA), "hash-6");
        }

        Path file(String owner, int id, long time) {
            return folder.resolve("database/history").resolve(owner).resolve(time + "-" + uuid(id) + ".yml");
        }

        YamlConfiguration config(int id, long time) throws Exception {
            YamlConfiguration config = new YamlConfiguration();
            config.load(file(A, id, time).toFile());
            return config;
        }

        void mutate(int id, long time, String key, Object value) throws Exception {
            if (backend == Backend.YAML) {
                YamlConfiguration config = config(id, time);
                config.set(key, value);
                config.save(file(A, id, time).toFile());
            } else {
                String column = Map.of("pages-json", "PAGES", "creative-items-json", "CREATIVEITEMS", "reason", "REASON",
                        "armor-json", "ARMOR", "offhand-json", "OFFHAND", "hotbar-json", "HOTBAR", "content-hash", "CONTENTHASH").get(key);
                if (column == null)
                    throw new IllegalArgumentException("Unsupported test SQL field: " + key);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE `history` SET " + column + " = ? WHERE ID = ?")) {
                    statement.setObject(1, value);
                    statement.setString(2, uuid(id));
                    eq(1, statement.executeUpdate(), "corrupt fixture row update count");
                }
            }
        }

        @Override
        public void close() {
            storage.close();
        }
    }

    /** Runs the real manager's private save pipeline, not init/capture/Bukkit scheduling. */
    private static final class ManagerFixture implements AutoCloseable {
        final InventoryHistoryManager manager = new InventoryHistoryManager();
        final ExecutorService worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "HistoryRegression-Worker"));
        final List<Thread> ioThreads = new CopyOnWriteArrayList<>();
        final Method save;
        volatile boolean failNextSave;

        ManagerFixture(InventoryHistoryStorage delegate) throws Exception {
            InventoryHistoryStorage observed = proxy(InventoryHistoryStorage.class, (proxy, method, args) -> {
                ioThreads.add(Thread.currentThread());
                if (method.getName().equals("save") && failNextSave) {
                    failNextSave = false;
                    throw new IOException("Deliberately injected test storage failure");
                }
                return invoke(method, delegate, args);
            });
            setField(InventoryHistoryManager.class, manager, "executor", worker);
            setField(InventoryHistoryManager.class, manager, "storage", observed);
            save = InventoryHistoryManager.class.getDeclaredMethod("saveAsync", InventorySnapshot.class, boolean.class);
            save.setAccessible(true);
        }

        @SuppressWarnings("unchecked")
        boolean save(InventorySnapshot snapshot, boolean force) throws Exception {
            CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) save.invoke(manager, snapshot, force);
            boolean result = future.get(10, TimeUnit.SECONDS);
            if (!worker.isShutdown())
                worker.submit(() -> { }).get(10, TimeUnit.SECONDS); // Drain the save's finally block, no polling/sleep.
            return result;
        }

        @Override
        public void close() throws Exception {
            worker.shutdown();
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                worker.shutdownNow();
                throw new AssertionError("Test worker failed to terminate");
            }
        }
    }

    /** Stateful PlayerInventoryDatabase for the manager's private snapshot/apply pipeline. */
    private static final class StubInventory {
        final InventoryHistoryManager manager = new InventoryHistoryManager();
        HashMap<Integer, ArrayList<ItemStack>> items = new HashMap<>();
        ArrayList<ItemStack> creative = new ArrayList<>();
        boolean usedCreative;
        int currentPage = 2;
        int maxPage = 10;

        @SuppressWarnings("unchecked")
        PlayerInventoryDatabase data() {
            return proxy(PlayerInventoryDatabase.class, (instance, method, args) -> {
                switch (method.getName()) {
                    case "getPlayerUUID": return A;
                    case "getPlayerName": return "Alice";
                    case "getItems": return items;
                    case "setItems": items = (HashMap<Integer, ArrayList<ItemStack>>) args[0]; return null;
                    case "getCreativeItems": return creative;
                    case "setCreativeItems": creative = (ArrayList<ItemStack>) args[0]; return null;
                    case "hasUsedCreative": return usedCreative;
                    case "setUsedCreative": usedCreative = (Boolean) args[0]; return null;
                    case "getCurrentPage": return currentPage;
                    case "setCurrentPage": currentPage = (Integer) args[0]; return null;
                    case "getMaxPage": return maxPage;
                    case "setMaxPage": maxPage = (int) args[0]; return null;
                    case "showPage": return null;
                    default: throw new UnsupportedOperationException("Fixture PlayerInventoryDatabase: " + method);
                }
            });
        }

        InventorySnapshot snapshot(SnapshotReason reason) throws Exception {
            Method method = InventoryHistoryManager.class.getDeclaredMethod("snapshot",
                    PlayerInventoryDatabase.class, SnapshotReason.class, Player.class);
            method.setAccessible(true);
            return (InventorySnapshot) checked(method, manager, new Object[]{data(), reason, null});
        }

        void apply(InventorySnapshot snapshot) throws Exception {
            Method method = InventoryHistoryManager.class.getDeclaredMethod("applySnapshot",
                    PlayerInventoryDatabase.class, InventorySnapshot.class, Player.class);
            method.setAccessible(true);
            Player player = proxy(Player.class, (instance, playerMethod, args) -> {
                if (playerMethod.getName().equals("getGameMode"))
                    return GameMode.SURVIVAL;
                throw new UnsupportedOperationException("Fixture Player: " + playerMethod);
            });
            checked(method, manager, new Object[]{data(), snapshot, player});
        }

        boolean unchangedSinceSafety(InventorySnapshot before) throws Exception {
            Method method = InventoryHistoryManager.class.getDeclaredMethod("unchangedSinceSafety",
                    PlayerInventoryDatabase.class, InventorySnapshot.class);
            method.setAccessible(true);
            return (boolean) checked(method, manager, new Object[]{data(), before});
        }

        private static Object checked(Method method, Object target, Object[] args) throws Exception {
            try {
                return invoke(method, target, args);
            } catch (Exception exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new AssertionError(throwable);
            }
        }
    }

    private static void bootstrap() throws Exception {
        Logger logger = Logger.getLogger("HistoryStorageRegression-fixture");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        ItemFactory factory = proxy(ItemFactory.class, (proxy, method, args) -> {
            if (method.getName().equals("getItemMeta"))
                return null; // Deliberately no metadata emulation.
            if (method.getName().equals("equals") && args.length == 2)
                return args[0] == args[1];
            throw new UnsupportedOperationException("Fixture ItemFactory: " + method);
        });
        UnsafeValues unsafeValues = proxy(UnsafeValues.class, (proxy, method, args) -> {
            if (method.getName().equals("getDataVersion"))
                return 2586;
            if (method.getName().equals("getMaterial"))
                return Material.getMaterial((String) args[0]);
            throw new UnsupportedOperationException("Fixture UnsafeValues: " + method);
        });
        // Extraction callbacks hop through the scheduler; run them inline on the calling thread.
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (proxy, method, args) -> {
            if (method.getName().equals("runTask") || method.getName().equals("runTaskAsynchronously")) {
                if (queueTasks)
                    SCHEDULED_TASKS.add((Runnable) args[1]);
                else
                    ((Runnable) args[1]).run();
                return proxy(BukkitTask.class, (taskProxy, taskMethod, taskArgs) -> {
                    switch (taskMethod.getName()) {
                        case "isCancelled": return false;
                        case "getTaskId": return 0;
                        default: return null;
                    }
                });
            }
            throw new UnsupportedOperationException("Fixture Scheduler: " + method);
        });
        CommandSender console = proxy(ConsoleCommandSender.class, (proxy, method, args) -> {
            if (method.getName().startsWith("sendMessage"))
                return null;
            if (method.getName().equals("getName"))
                return "CONSOLE";
            throw new UnsupportedOperationException("Fixture Console: " + method);
        });
        PluginManager pluginManager = proxy(PluginManager.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getPermissionSubscriptions": return Collections.emptySet();
                case "recalculatePermissionDefaults": return null;
                default: throw new UnsupportedOperationException("Fixture PluginManager: " + method);
            }
        });
        Server server = proxy(Server.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLogger": return logger;
                case "getPluginManager": return pluginManager;
                case "getItemFactory": return factory;
                case "getUnsafe": return unsafeValues;
                case "isPrimaryThread": return Thread.currentThread() == MAIN_THREAD;
                case "getName": return "HistoryRegressionFixture";
                case "getVersion": return "test-only";
                case "getBukkitVersion": return "1.16.5";
                case "getPlayer": return FIXTURE_PLAYERS.get(args[0]);
                case "getPlayerExact": return FIXTURE_PLAYERS.values().stream()
                        .filter(player -> player.getName().equals(args[0])).findFirst().orElse(null);
                case "getOnlinePlayers": return new ArrayList<>(FIXTURE_PLAYERS.values());
                case "getPluginCommand": return "invhistory".equals(args[0]) ? fixtureHistoryCommand : null;
                case "getScheduler": return scheduler;
                case "getConsoleSender": return console;
                default: throw new UnsupportedOperationException("Fixture Server: " + method);
            }
        });
        setField(Bukkit.class, null, "server", server);
        // JavaPlugin's real constructor requires PluginClassLoader. Allocate only this
        // test instance without running onLoad/onEnable; reflection never enters production.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        InventoryPagesPlus plugin = (InventoryPagesPlus) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), InventoryPagesPlus.class);
        setField(JavaPlugin.class, plugin, "server", server);
        setField(JavaPlugin.class, plugin, "description", new PluginDescriptionFile("HistoryRegression", "test", InventoryPagesPlus.class.getName()));
        setField(JavaPlugin.class, plugin, "dataFolder", output.toFile());
        PluginLogger pluginLogger = new PluginLogger(plugin);
        pluginLogger.setUseParentHandlers(false);
        pluginLogger.setLevel(Level.ALL);
        pluginLogger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { LOGS.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        setField(JavaPlugin.class, plugin, "logger", pluginLogger);
        InventoryPagesPlus.plugin = plugin;
        InventoryPagesPlus.nms = new VersionSupport(plugin) {
            @Override public ItemStack getItemStack(ItemStack stack) {
                COPIES.incrementAndGet();
                return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0 ? null : stack.clone();
            }
            @Override public ItemStack createItemStack(String material, int amount, short data, int model) { throw unsupported(); }
            @Override public Sound createSound(String name) { throw unsupported(); }
            @Override public ItemStack getHeadItemFromPlayerName(String name) { throw unsupported(); }
            @Override public ItemStack getHeadItemFromBase64(String value) { throw unsupported(); }
            @Override public ItemStack addCustomData(ItemStack stack, String data) { throw unsupported(); }
            @Override public String getCustomData(ItemStack stack) { throw unsupported(); }
            @Override public String addColor(String text) { return text; }
            @Override public String stripColor(String text) { throw unsupported(); }
            private UnsupportedOperationException unsupported() { return new UnsupportedOperationException("Outside plain-stack test fixture"); }
        };
    }

    private static InventorySnapshot sample(int id, String owner, long time, String server) {
        Map<Integer, List<ItemStack>> pages = new LinkedHashMap<>();
        pages.put(10, slots(item(Material.DIRT, 3)));
        pages.put(2, slots(item(Material.STONE, 2), null, item(Material.DIAMOND, 7)));
        return snapshot(id, owner, time, server, SnapshotReason.JOIN, pages, slots(null, item(Material.EMERALD, 4)), true);
    }

    private static InventorySnapshot snapshot(int id, String owner, long time, String server, SnapshotReason reason,
                                               Map<Integer, List<ItemStack>> pages, List<ItemStack> creative, boolean used) {
        return new InventorySnapshot(uuid(id), owner, owner.equals(A) ? "Alice" : "Bob", time, reason, server, 2, 10, used, pages, creative);
    }

    private static InventorySnapshot withReason(InventorySnapshot snapshot, SnapshotReason reason) {
        return new InventorySnapshot(snapshot.getId(), snapshot.getPlayerUUID(), snapshot.getPlayerName(), snapshot.getCreatedAt(),
                reason, snapshot.getServerId(), snapshot.getCurrentPage(), snapshot.getMaxPage(), snapshot.isUsedCreative(),
                snapshot.getPages(), snapshot.getCreativeItems());
    }

    private static InventorySnapshot equipped(int id, String owner, long time) {
        return new InventorySnapshot(uuid(id), owner, owner.equals(A) ? "Alice" : "Bob", time, SnapshotReason.JOIN, ALPHA,
                2, 10, false, sample(id, owner, time, ALPHA).getPages(), null,
                slots(item(Material.DIAMOND_HELMET, 1), item(Material.DIAMOND_CHESTPLATE, 1),
                        item(Material.DIAMOND_LEGGINGS, 1), item(Material.DIAMOND_BOOTS, 1)),
                item(Material.SHIELD, 1),
                slots(item(Material.DIAMOND_SWORD, 1), null, item(Material.BREAD, 5)));
    }

    private static ItemStack item(Material material, int amount) { return new ItemStack(material, amount); }
    private static String uuid(int value) { return new UUID(0, value).toString(); }
    private static List<ItemStack> slots(ItemStack... items) { return new ArrayList<>(Arrays.asList(items)); }
    private static List<String> slotsOfStrings(String... items) { return new ArrayList<>(Arrays.asList(items)); }

    private static void stack(ItemStack actual, Material type, int amount) {
        check(actual != null, "missing stack " + type);
        eq(type, actual.getType(), "item type");
        eq(amount, actual.getAmount(), "item amount");
    }

    private static void ids(List<InventorySnapshotSummary> actual, int... expected) {
        eq(Arrays.stream(expected).mapToObj(HistoryStorageRegression::uuid).collect(Collectors.toList()),
                actual.stream().map(InventorySnapshotSummary::id).collect(Collectors.toList()), "snapshot ids/order");
    }

    private static void warn(String fragment) {
        check(LOGS.stream().anyMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getMessage().contains(fragment)),
                "missing warning '" + fragment + "'; actual=" + LOGS.stream().map(LogRecord::getMessage).collect(Collectors.toList()));
    }

    private static void setField(Class<?> owner, Object instance, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "HistoryRegressionProxy(" + type.getSimpleName() + ")";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: throw new UnsupportedOperationException(method.toString());
                }
            }
            return handler.invoke(proxy, method, args);
        });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static void storageTest(Backend backend, String name, StorageCheck action) {
        test(backend + " " + name, () -> {
            try (Fixture fixture = new Fixture(backend)) {
                action.run(fixture);
            }
        });
    }

    private static void test(String name, Check action) {
        LOGS.clear();
        try {
            action.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable exception) {
            failed++;
            System.out.println("FAIL " + name + ": " + exception);
            exception.printStackTrace(System.out);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        assert Objects.equals(expected, actual) : message + ": expected=" + expected + ", actual=" + actual;
    }

    private static void check(boolean condition, String message) { assert condition : message; }

    private static void expect(Class<? extends Throwable> type, Check action) throws Exception {
        try {
            action.run();
        } catch (Throwable exception) {
            if (type.isInstance(exception))
                return;
            throw new AssertionError("Expected " + type.getName() + " but got " + exception, exception);
        }
        throw new AssertionError("Expected " + type.getName() + " but no exception was thrown");
    }

    @FunctionalInterface private interface Check { void run() throws Exception; }
    @FunctionalInterface private interface StorageCheck { void run(Fixture fixture) throws Exception; }
}
