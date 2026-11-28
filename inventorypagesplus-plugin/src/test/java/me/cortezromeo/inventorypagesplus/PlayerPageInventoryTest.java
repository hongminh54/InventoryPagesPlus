package me.cortezromeo.inventorypagesplus.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlayerPageInventory} page-switching logic.
 * A mocked server is required because modern Paper Material/ItemType
 * are backed by registries that only resolve through a Bukkit server.
 */
class PlayerPageInventoryTest {

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
    void testIsSwitcherItem_SameTypeAndDisplayName_ReturnsTrue() {
        ItemStack a = new ItemStack(Material.STONE);
        ItemMeta metaA = a.getItemMeta();
        metaA.setDisplayName("§6Next Page");
        a.setItemMeta(metaA);

        ItemStack b = new ItemStack(Material.STONE);
        ItemMeta metaB = b.getItemMeta();
        metaB.setDisplayName("§6Next Page");
        b.setItemMeta(metaB);

        assertTrue(PlayerPageInventory.isSwitcherItem(a, b));
    }

    @Test
    void testIsSwitcherItem_DifferentType_ReturnsFalse() {
        ItemStack a = new ItemStack(Material.STONE);
        ItemStack b = new ItemStack(Material.DIRT);
        assertFalse(PlayerPageInventory.isSwitcherItem(a, b));
    }

    @Test
    void testIsSwitcherItem_NullItem_ReturnsFalse() {
        ItemStack b = new ItemStack(Material.STONE);
        assertFalse(PlayerPageInventory.isSwitcherItem(null, b));
    }

    @Test
    void testIsSwitcherItem_NullMetaOnSwitcherItem_ReturnsFalse() {
        ItemStack a = new ItemStack(Material.STONE);
        ItemStack b = new ItemStack(Material.STONE);
        // b has no item meta (null)
        assertFalse(PlayerPageInventory.isSwitcherItem(a, b));
    }

    @Test
    void testIsSwitcherItem_NullDisplayNameOnSwitcherItem_ReturnsFalse() {
        ItemStack a = new ItemStack(Material.STONE);
        ItemMeta metaA = a.getItemMeta();
        metaA.setDisplayName("Test");
        a.setItemMeta(metaA);

        ItemStack b = new ItemStack(Material.STONE);
        // b has item meta but no display name
        assertFalse(PlayerPageInventory.isSwitcherItem(a, b));
    }

    @Test
    void testIsSwitcherItem_DifferentDisplayName_ReturnsFalse() {
        ItemStack a = new ItemStack(Material.STONE);
        ItemMeta metaA = a.getItemMeta();
        metaA.setDisplayName("§6Next Page");
        a.setItemMeta(metaA);

        ItemStack b = new ItemStack(Material.STONE);
        ItemMeta metaB = b.getItemMeta();
        metaB.setDisplayName("§cPrevious Page");
        b.setItemMeta(metaB);

        assertFalse(PlayerPageInventory.isSwitcherItem(a, b));
    }

    @Test
    void testStaticFields_InitiallyNull() {
        assertNull(PlayerPageInventory.nextItem);
        assertNull(PlayerPageInventory.prevItem);
        assertNull(PlayerPageInventory.noPageItem);
        assertNull(PlayerPageInventory.prevPos);
        assertNull(PlayerPageInventory.nextPos);
        assertEquals("inventorypagespluspageitems", PlayerPageInventory.itemCustomData);
    }
}
