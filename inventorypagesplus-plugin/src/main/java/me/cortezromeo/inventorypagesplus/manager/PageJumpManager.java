package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.support.DialogSupport;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

public class PageJumpManager implements CommandExecutor, Listener {

    // Bounds the dialog input so an "unlimited pages" player cannot request an absurd page index.
    private static final int MAX_REACHABLE_PAGE = 9999;
    private static final long EXPIRY_MILLIS = 60_000L;

    private static final class Pending {
        final int max;
        final IntConsumer jump;
        final long expiresAt = System.currentTimeMillis() + EXPIRY_MILLIS;

        Pending(int max, IntConsumer jump) {
            this.max = max;
            this.jump = jump;
        }
    }

    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PageJumpManager() {
        PluginCommand command = InventoryPagesPlus.plugin.getCommand("pagejump");
        command.setExecutor(this);
        // The command is a dialog/internal helper, so it offers no tab suggestions.
        command.setTabCompleter((sender, cmd, alias, args) -> new ArrayList<>());
        Bukkit.getPluginManager().registerEvents(this, InventoryPagesPlus.plugin);
        DebugManager.debug("LOADING EVENT", "Loaded PageJumpManager.");
    }

    public static boolean isShiftClick(InventoryClickEvent event) {
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)
            return true;
        // Creative-mode clicks on the player's own inventory report ClickType.CREATIVE; shift is the
        // collect-to-cursor action there, while plain clicks are the PICKUP_* actions.
        return event.getClick() == ClickType.CREATIVE && event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
    }

    public static boolean isDisabled() {
        return "disabled".equalsIgnoreCase(Settings.INVENTORY_SETTINGS_PAGE_JUMP);
    }

    /**
     * Starts a jump prompt for a 0-based inclusive page range. Returns false when the feature
     * is off, so the caller keeps its plain single-step navigation for this click.
     */
    public static boolean begin(Player player, int maxPage, IntConsumer jump) {
        if (isDisabled() || maxPage < 1)
            return false;

        int max = Math.min(maxPage, MAX_REACHABLE_PAGE);
        pending.put(player.getUniqueId(), new Pending(max, jump));
        if (!"chat".equalsIgnoreCase(Settings.INVENTORY_SETTINGS_PAGE_JUMP) && DialogSupport.isAvailable()) {
            Map<String, String> inputs = Map.of("page", Messages.COMMAND_PAGE_JUMP_DIALOG_INPUT_PAGE);
            if (DialogSupport.showPrompt(player, Messages.COMMAND_PAGE_JUMP_DIALOG_TITLE,
                    Messages.COMMAND_PAGE_JUMP_DIALOG_BODY, inputs, "pagejump $(page)",
                    Messages.COMMAND_PAGE_JUMP_DIALOG_CONFIRM, Messages.COMMAND_PAGE_JUMP_DIALOG_CANCEL,
                    String.valueOf(max + 1).length()))
                return true;
            // A dialog failure (or dialog unsupported) keeps the player in the chat flow.
        }
        MessageUtil.sendMessage(player, Messages.COMMAND_PAGE_JUMP_PROMPT.replace("%max%", String.valueOf(max + 1)));
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
            return false;
        }
        Pending taken = active(player.getUniqueId());
        if (taken == null) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PAGE_JUMP_NONE);
            return false;
        }
        // A valid number consumes the prompt; an invalid one keeps it so the player can retry.
        if (accept(player, taken, args.length == 1 ? args[0] : ""))
            pending.remove(player.getUniqueId());
        return false;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Pending current = active(event.getPlayer().getUniqueId());
        if (current == null)
            return;

        String text = InventoryPagesPlus.nms.stripColor(event.getMessage()).trim();
        if (text.isEmpty())
            return;
        // Any non-number message (including "cancel") abandons the prompt and reaches chat normally.
        if (!text.matches("\\d+")) {
            pending.remove(event.getPlayer().getUniqueId());
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String number = text;
        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
            if (active(player.getUniqueId()) == null)
                return;
            if (accept(player, current, number))
                pending.remove(player.getUniqueId());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // Validates the typed 1-based page and runs the GUI callback with the 0-based index.
    private boolean accept(Player player, Pending target, String raw) {
        int page;
        try {
            page = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            page = -1;
        }
        if (page < 1 || page > target.max + 1) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PAGE_JUMP_INVALID
                    .replace("%min%", "1")
                    .replace("%max%", String.valueOf(target.max + 1)));
            return false;
        }
        target.jump.accept(page - 1);
        MessageUtil.sendMessage(player, Messages.COMMAND_PAGE_JUMP_SUCCESS.replace("%page%", String.valueOf(page)));
        return true;
    }

    private static Pending active(UUID playerId) {
        Pending entry = pending.get(playerId);
        if (entry == null)
            return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            pending.remove(playerId);
            return null;
        }
        return entry;
    }
}
