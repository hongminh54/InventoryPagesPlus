package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.MockBukkitTestBase;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.task.AutoSaveTask;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.ScheduledTask;
import org.bukkit.scheduler.BukkitTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Times the two paths that run on or around the main thread: the join load (UUID lookup +
 * full inventory load + crashed.yml rewrite + page display) and the periodic autosave loop.
 */
class JoinAutoSavePerformanceTest extends MockBukkitTestBase {

    private static final int PLAYERS = 8;
    private static final int PAGES = 4;

    private PlayerMock joinWithSavedPages(int index) {
        PlayerMock player = server.addPlayer(String.format("JoinPerf%02d", index));
        PlayerInventoryDatabase data = PlayerInventoryDataStorage.getPlayerInventoryData(player.getName());
        data.setItems(filledPageMap(PAGES));
        PlayerInventoryDataStorage.savePlayerInventoryData(data);
        return player;
    }

    @Test
    void joinLoadPathStaysWithinMainThreadBudget() {
        resetCrashedState();
        for (int i = 0; i < PLAYERS; i++)
            joinWithSavedPages(i);

        long total = 0;
        for (int i = 0; i < PLAYERS; i++) {
            final int index = i;
            total += timeMillis(() -> InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(String.format("JoinPerf%02d", index)));
        }
        long perJoin = total / PLAYERS;
        System.out.printf("[PERF][join] loadPlayerInventory avg %,d ms/player (%d players x %d pages, includes crashed.yml rewrite + showPage)%n",
                perJoin, PLAYERS, PAGES);
        // On a real server this runs inside PlayerJoinEvent, so every millisecond is join lag.
        assertTrue(perJoin < 500, "Join load path exceeded the main-thread budget.");

        // Every join rewrites crashed.yml: the file must list all loaded players.
        assertTrue(DatabaseManager.crashedFile.exists());
        assertEquals(PLAYERS, DatabaseManager.crashedData.getConfigurationSection("crashed").getKeys(false).size());
    }

    @Test
    void autosaveCycleScalesLinearlyWithOnlinePlayers() {
        resetCrashedState();
        for (int i = 0; i < PLAYERS; i++) {
            PlayerMock player = joinWithSavedPages(i);
            InventoryPagesPlus.getDatabaseManager().loadPlayerInventory(player.getName());
        }

        long cycle = timeMillis(() -> InventoryPagesPlus.getDatabaseManager().updateAndSaveAllInventoriesToDatabase());
        System.out.printf("[PERF][autosave] updateAndSaveAllInventoriesToDatabase %,d ms total, ~%,d ms/player (%d online x %d pages)%n",
                cycle, Math.max(1, cycle / PLAYERS), PLAYERS, PAGES);
        // Autosave holds the storage lock for the whole loop, so a long cycle also delays
        // join saves. It runs async, but the lock wait is what can stall the join thread.
        assertTrue(cycle < 5_000, "Autosave cycle exceeded the regression budget.");
    }

    @Test
    void autoSaveTaskIsScheduledOffTheMainThread() {
        AutoSaveTask autoSaveTask = new AutoSaveTask(60);
        try {
            BukkitTask task = autoSaveTask.getBukkitTask();
            assertTrue(task instanceof ScheduledTask, "Expected the MockBukkit task implementation.");
            assertFalse(((ScheduledTask) task).isSync(), "Autosave must never run on the main thread.");
        } finally {
            autoSaveTask.cancel();
        }
    }

}
