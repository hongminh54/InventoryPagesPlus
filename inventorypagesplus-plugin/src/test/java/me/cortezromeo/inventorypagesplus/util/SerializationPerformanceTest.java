package me.cortezromeo.inventorypagesplus.util;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.MockBukkitTestBase;
import me.cortezromeo.inventorypagesplus.history.InventoryHistoryManager;
import me.cortezromeo.inventorypagesplus.history.SnapshotReason;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Times the item/page serialization every storage backend and the history snapshot path run
 * through, and verifies history capture hands its file/database work to a worker thread
 * instead of blocking the caller.
 */
class SerializationPerformanceTest extends MockBukkitTestBase {

    private static final int PAGES = 20;
    private static final int ITEMS = PAGES * 25;
    private static final int ITERATIONS = 200;

    @Test
    void pagesJsonRoundTripTimings() {
        Map<Integer, List<ItemStack>> pages = new TreeMap<>();
        filledPageMap(PAGES).forEach(pages::put);

        String[] json = new String[1];
        long encodeMs = timeMillis(() -> json[0] = StringUtil.pagesToJson(pages));
        assertNotNull(json[0]);
        Map<Integer, List<ItemStack>> restored = new TreeMap<>();
        long decodeMs = timeMillis(() -> restored.putAll(StringUtil.pagesFromJson(json[0])));

        int restoredItems = 0;
        for (List<ItemStack> page : restored.values())
            restoredItems += (int) page.stream().filter(item -> item != null).count();
        assertEquals(PAGES, restored.size());
        assertEquals(ITEMS, restoredItems, "Every item must survive the pages JSON round-trip.");

        System.out.printf("[PERF][json] pagesToJson %,d ms | pagesFromJson %,d ms (%d pages, %d items, payload ~%,d KB)%n",
                encodeMs, decodeMs, PAGES, ITEMS, json[0].length() / 1024);
        assertTrue(encodeMs < 1_000 && decodeMs < 1_000,
                "A full-player page (de)serialization already exceeds the main-thread budget.");
    }

    @Test
    void itemBase64WithMetaTimings() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Perf Stick");
        meta.setLore(List.of("line one", "line two"));
        item.setItemMeta(meta);

        long total = timeMillis(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                String encoded = StringUtil.toBase64(item);
                assertNotNull(encoded);
                ItemStack decoded = StringUtil.itemStackFromBase64(encoded);
                assertNotNull(decoded);
                assertEquals(Material.STICK, decoded.getType());
            }
        });
        double perItem = (double) total / ITERATIONS;
        System.out.printf("[PERF][base64] item with meta round-trip: %.3f ms/item (%,d iterations)%n", perItem, ITERATIONS);
        // One page save encodes 25 items, so per-item cost multiplies across every player in a cycle.
        assertTrue(perItem < 5, "Single-item base64 round-trip is too slow to be part of a main-thread save.");
    }

    @Test
    void historyCaptureOffloadsWorkToItsWorkerThread() throws Exception {
        InventoryHistoryManager history = InventoryPagesPlus.getHistoryManager();
        assertNotNull(history);
        // init() opens the history H2 connection on the executor thread; wait for it to settle.
        long deadline = System.currentTimeMillis() + 5_000;
        while (!history.isAvailable() && System.currentTimeMillis() < deadline)
            Thread.sleep(50);
        assertTrue(history.isAvailable(), "History storage did not initialise; capture timing is meaningless.");

        resetCrashedState();
        PlayerMock player = server.addPlayer("HistoryPerf");
        PlayerInventoryDatabase data = PlayerInventoryDataStorage.getPlayerInventoryData(player.getName());
        data.setItems(filledPageMap(PAGES));
        PlayerInventoryDataStorage.savePlayerInventoryData(data);
        InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(player.getName());

        long start = System.nanoTime();
        CompletableFuture<Boolean> future = history.capture(player, SnapshotReason.JOIN, true);
        long callMs = (System.nanoTime() - start) / 1_000_000L;
        long waitStart = System.nanoTime();
        assertTrue(future.get(10, TimeUnit.SECONDS), "Snapshot was not saved.");
        long completionMs = Math.max(1, (System.nanoTime() - waitStart) / 1_000_000L);
        System.out.printf("[PERF][history] capture() returned in %,d ms; snapshot finished on the worker thread in ~%,d ms%n",
                callMs, completionMs);
        assertTrue(callMs < 100, "capture() must not run serialization or I/O on the calling thread.");
    }

}
