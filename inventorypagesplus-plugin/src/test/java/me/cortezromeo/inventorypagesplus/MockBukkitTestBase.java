package me.cortezromeo.inventorypagesplus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

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
}
