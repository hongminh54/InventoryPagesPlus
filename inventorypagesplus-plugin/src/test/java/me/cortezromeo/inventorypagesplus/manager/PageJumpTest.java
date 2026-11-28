package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.MockBukkitTestBase;
import me.cortezromeo.inventorypagesplus.Settings;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the chat fallback flow of {@link PageJumpManager}. The dialog API is not
 * present on the mocked server, so every begin() lands in the chat path.
 */
class PageJumpTest extends MockBukkitTestBase {

    @Test
    void testChatNumber_JumpsToZeroBasedPage() {
        PlayerMock player = server.addPlayer();
        AtomicInteger jumped = new AtomicInteger(-1);

        assertTrue(PageJumpManager.begin(player, 4, jumped::set));
        player.chat("3");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(1);

        assertEquals(2, jumped.get());
    }

    @Test
    void testOutOfRange_KeepsPromptAndRetrySucceeds() {
        PlayerMock player = server.addPlayer();
        AtomicInteger jumped = new AtomicInteger(-1);

        assertTrue(PageJumpManager.begin(player, 2, jumped::set));
        player.chat("99");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(1);
        assertEquals(-1, jumped.get());

        player.chat("2");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(1);
        assertEquals(1, jumped.get());
    }

    @Test
    void testNonNumericMessage_CancelsPromptWithoutJumping() {
        PlayerMock player = server.addPlayer();
        AtomicInteger jumped = new AtomicInteger(-1);

        assertTrue(PageJumpManager.begin(player, 4, jumped::set));
        player.chat("hello");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(1);
        assertEquals(-1, jumped.get());

        // The prompt is abandoned, so a late number must not jump anymore.
        player.chat("2");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(1);
        assertEquals(-1, jumped.get());
    }

    @Test
    void testDisabledModeAndSinglePage_BeginRefused() {
        PlayerMock player = server.addPlayer();
        String previous = Settings.INVENTORY_SETTINGS_PAGE_JUMP;
        try {
            Settings.INVENTORY_SETTINGS_PAGE_JUMP = "disabled";
            assertFalse(PageJumpManager.begin(player, 4, page -> fail("disabled mode must not jump")));

            Settings.INVENTORY_SETTINGS_PAGE_JUMP = "auto";
            assertFalse(PageJumpManager.begin(player, 0, page -> fail("a single page has nothing to jump to")));
        } finally {
            Settings.INVENTORY_SETTINGS_PAGE_JUMP = previous;
        }
    }
}
