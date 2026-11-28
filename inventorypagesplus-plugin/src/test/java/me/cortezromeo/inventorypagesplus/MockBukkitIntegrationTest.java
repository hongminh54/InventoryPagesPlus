package me.cortezromeo.inventorypagesplus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests verifying MockBukkit can mock a Bukkit server
 * and that InventoryPagesPlus plugin class loads correctly.
 */
class MockBukkitIntegrationTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testMockBukkitServer_IsCreated() {
        assertNotNull(server);
        assertNotNull(org.bukkit.Bukkit.getServer());
        assertSame(server, org.bukkit.Bukkit.getServer());
    }

    @Test
    void testAddPlayer_CreatesOnlinePlayer() {
        PlayerMock player = server.addPlayer();
        assertNotNull(player);
        assertTrue(player.isOnline());
        assertEquals(1, server.getOnlinePlayers().size());
    }

    @Test
    void testCreateWorld_CreatesMockWorld() {
        org.bukkit.World world = server.createWorld(new org.bukkit.WorldCreator("test-world"));
        assertNotNull(world);
        assertEquals("test-world", world.getName());
    }

    @Test
    void testLoadPlugin_SetsStaticPluginField() throws Exception {
        // Load the real plugin class via MockBukkit.
        // This calls onLoad() which sets InventoryPagesPlus.plugin = this.
        InventoryPagesPlus plugin = MockBukkit.load(InventoryPagesPlus.class);
        assertNotNull(plugin);
        assertSame(plugin, InventoryPagesPlus.plugin);
    }

    @Test
    void testPlugin_GetName_ReturnsInventoryPagesPlus() throws Exception {
        InventoryPagesPlus plugin = MockBukkit.load(InventoryPagesPlus.class);
        assertEquals("InventoryPagesPlus", plugin.getName());
    }
}
