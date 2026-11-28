package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.manager.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Base class for all MockBukkit-based tests in InventoryPagesPlus.
 * Provides a mocked Bukkit server and loaded plugin instance for each test method.
 */
public abstract class MockBukkitTestBase {

    protected ServerMock server;
    protected InventoryPagesPlus plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // Load the real plugin class so static fields (plugin, nms, etc.) are initialised.
        // Note: onEnable() performs file I/O and DB setup; if your test environment lacks
        // config files, consider mocking individual components instead (see PlayerPageInventoryTest).
        plugin = MockBukkit.load(InventoryPagesPlus.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
        server = null;
        plugin = null;
    }

    /** Wall-clock milliseconds of one action; used by the performance tests to report timings. */
    protected static long timeMillis(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000L;
    }

    // crashedFile/crashedData are JVM-wide statics initialised with the first test's temp
    // data folder, so re-point and clear them before every timing run.
    protected void resetCrashedState() {
        DatabaseManager.crashedFile = new File(plugin.getDataFolder(), "database/crashed.yml");
        DatabaseManager.crashedData = YamlConfiguration.loadConfiguration(DatabaseManager.crashedFile);
    }

    /** A page map of fully filled pages; item amounts vary per slot so round-trips can be verified. */
    protected static HashMap<Integer, ArrayList<ItemStack>> filledPageMap(int pageCount) {
        HashMap<Integer, ArrayList<ItemStack>> pages = new HashMap<>();
        for (int page = 0; page < pageCount; page++) {
            ArrayList<ItemStack> items = new ArrayList<>(25);
            for (int slot = 0; slot < 25; slot++)
                items.add(new ItemStack(Material.STONE, slot + 1));
            pages.put(page, items);
        }
        return pages;
    }
}
