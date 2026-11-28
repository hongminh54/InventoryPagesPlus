package me.cortezromeo.inventorypagesplus.storage;

import me.cortezromeo.inventorypagesplus.MockBukkitTestBase;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Times the heavy storage paths (bulk save/load, name lookups) so regressions that could
 * block the main thread fail the build. Thresholds are deliberately generous: they are
 * canaries for pathological behaviour, not targets.
 */
class StoragePerformanceTest extends MockBukkitTestBase {

    private static final int PLAYERS = 12;
    private static final int PAGES = 4;
    private static final int LOOKUPS = 50;
    // Same length as the saved names so YAML lookups must scan the whole length bucket.
    private static final String ABSENT_NAME = "PerfAbsent00";

    private PlayerInventoryDatabase offlineData(String playerName) {
        return new PlayerInventory(null, playerName, UUID.randomUUID().toString(),
                Settings.INVENTORY_SETTINGS_MAX_PAGE_DEFAULT, filledPageMap(PAGES), null,
                PlayerPageInventory.prevItem, PlayerPageInventory.prevPos,
                PlayerPageInventory.nextItem, PlayerPageInventory.nextPos, PlayerPageInventory.noPageItem);
    }

    private PlayerInventoryDatabase[] saveDataset(String backend) {
        PlayerInventoryDatabase[] dataset = new PlayerInventoryDatabase[PLAYERS];
        long saveTotal = 0;
        for (int i = 0; i < PLAYERS; i++) {
            final int index = i;
            dataset[index] = offlineData(String.format("PerfPlayer%02d", i));
            saveTotal += timeMillis(() -> assertTrue(PlayerInventoryDataStorage.savePlayerInventoryDataChecked(dataset[index])));
        }
        long loadTotal = 0;
        for (PlayerInventoryDatabase data : dataset) {
            PlayerInventoryDatabase[] loaded = new PlayerInventoryDatabase[1];
            loadTotal += timeMillis(() -> loaded[0] = PlayerInventoryDataStorage.loadOfflinePlayerInventory(data.getPlayerName(), data.getPlayerUUID()));
            assertNotNull(loaded[0]);
            assertEquals(PAGES, loaded[0].getItems().size(), backend + ": every filled page must survive a save/load round-trip.");
        }
        System.out.printf("[PERF][%s] save avg %,d ms/player | load avg %,d ms/player (%d players x %d pages)%n",
                backend, saveTotal / PLAYERS, loadTotal / PLAYERS, PLAYERS, PAGES);
        assertTrue(saveTotal / PLAYERS < 250, backend + ": per-player save exceeded the main-thread budget.");
        assertTrue(loadTotal / PLAYERS < 250, backend + ": per-player load exceeded the main-thread budget.");
        return dataset;
    }

    @Test
    void yamlBulkSaveLoadAndNameScanTimings() {
        PlayerInventoryDataStorage.init(DatabaseType.YAML);
        saveDataset("YAML");

        // The only way to resolve an offline unknown name is scanning and parsing every file
        // in the length bucket: cost grows linearly with the number of stored players.
        long scanTotal = timeMillis(() -> {
            for (int i = 0; i < LOOKUPS; i++)
                assertNull(PlayerInventoryDataStorage.getPlayerUUIDFromData(ABSENT_NAME, false));
        });
        long perLookup = Math.max(1, scanTotal / LOOKUPS);
        System.out.printf("[PERF][YAML] unknown-name lookup avg %,d ms (full %,d-file directory scan)%n", perLookup, PLAYERS);
        assertTrue(perLookup < 250, "YAML name-scan lookup exceeded the main-thread budget.");
    }

    @Test
    void h2BulkSaveLoadAndNameLookupTimings() {
        PlayerInventoryDataStorage.init(DatabaseType.H2);
        saveDataset("H2");

        // PLAYERNAME has no index, so each offline-name resolution is a table scan (cheap per-row
        // compared to YAML parsing, but still O(rows) per call).
        long lookupTotal = timeMillis(() -> {
            for (int i = 0; i < LOOKUPS; i++)
                assertNull(PlayerInventoryDataStorage.getPlayerUUIDFromData(ABSENT_NAME, false));
        });
        long perLookup = Math.max(1, lookupTotal / LOOKUPS);
        System.out.printf("[PERF][H2] unknown-name lookup avg %,d ms (unindexed PLAYERNAME scan over %,d rows)%n", perLookup, (long) PLAYERS);
        assertTrue(perLookup < 250, "H2 name lookup exceeded the main-thread budget.");

        // hasData(name) is called on the join path and repeats the same unindexed scan.
        long hasDataTotal = timeMillis(() -> {
            for (int i = 0; i < LOOKUPS; i++)
                assertTrue(PlayerInventoryDataStorage.hasData("PerfPlayer00"));
        });
        System.out.printf("[PERF][H2] hasData avg %,d ms%n", Math.max(1, hasDataTotal / LOOKUPS));
    }

}
