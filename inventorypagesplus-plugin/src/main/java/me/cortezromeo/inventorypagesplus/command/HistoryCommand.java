package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.history.HistoryDates;
import me.cortezromeo.inventorypagesplus.history.InventoryHistoryManager;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshotSummary;
import me.cortezromeo.inventorypagesplus.history.SnapshotReason;
import me.cortezromeo.inventorypagesplus.inventory.history.InventoryHistoryGui;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.util.StringUtil;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HistoryCommand implements CommandExecutor, TabExecutor {
    private final Map<UUID, UUID> requests = new HashMap<>();
    private boolean savingAll;

    public HistoryCommand() {
        InventoryPagesPlus.plugin.getCommand("invhistory").setExecutor(this);
        InventoryPagesPlus.plugin.getCommand("invhistory").setTabCompleter(this);
        DebugManager.debug("LOADING COMMAND", "Loaded HistoryCommand.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player)
            requests.remove(player.getUniqueId());
        if (!Settings.HISTORY_ENABLED) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_DISABLED);
            return true;
        }
        boolean save = args.length > 0 && args[0].equalsIgnoreCase("save");
        if (!(sender instanceof Player) && !save) {
            MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
            return true;
        }
        if (!sender.hasPermission(Settings.HISTORY_PERMISSION)) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_NO_PERMISSION);
            return true;
        }
        if (args.length == 0 || args.length > (save ? 2 : 3)) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_USAGE.replace("%date_format%", Settings.HISTORY_DATE_FORMAT));
            return true;
        }
        if (save && args.length == 1) {
            if (checkAccess(sender, null, null, true))
                saveAllSnapshots(sender);
            return true;
        }
        String targetName = save ? args[1] : args[0];
        if (!checkAccess(sender, null, targetName, save))
            return true;
        if (save) {
            saveSnapshot(sender, targetName);
            return true;
        }

        LocalDate requestedDate = null;
        int requestedPage = -1;
        try {
            if (args.length == 2 && args[1].matches("[+-]?\\d+"))
                requestedPage = HistoryDates.page(args[1]);
            else {
                if (args.length >= 2)
                    requestedDate = HistoryDates.parse(args[1]);
                if (args.length == 3)
                    requestedPage = HistoryDates.page(args[2]);
            }
        } catch (DateTimeParseException exception) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_INVALID_DATE.replace("%date_format%", Settings.HISTORY_DATE_FORMAT));
            return true;
        } catch (NumberFormatException exception) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_INVALID_PAGE);
            return true;
        }
        final LocalDate date = requestedDate;
        final int initialPage = requestedPage;
        Player viewer = (Player) sender;
        UUID viewerId = viewer.getUniqueId();
        UUID request = UUID.randomUUID();
        requests.put(viewerId, request);
        MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_LOADING);
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            loadHistory(viewerId, request, onlineTarget.getUniqueId().toString(), onlineTarget.getName(), date, initialPage);
            return true;
        }

        // Natural lookup fabricates UUIDs in offline mode. Require stored data instead.
        Bukkit.getScheduler().runTaskAsynchronously(InventoryPagesPlus.plugin, () -> {
            String resolvedUUID = null;
            boolean failed = false;
            try {
                if (PlayerInventoryDataStorage.hasData(targetName)) {
                    String storedUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(targetName, false);
                    if (storedUUID != null)
                        resolvedUUID = UUID.fromString(storedUUID).toString();
                }
            } catch (RuntimeException exception) {
                failed = true;
                InventoryPagesPlus.plugin.getLogger().warning("Could not resolve history player: " + exception.getMessage());
            }
            String result = resolvedUUID;
            boolean lookupFailed = failed;
            if (InventoryPagesPlus.plugin.isEnabled()) {
                try {
                    Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
                        Player currentViewer = currentViewer(viewerId, request);
                        if (currentViewer == null)
                            return;
                        if (!checkAccess(currentViewer, result, targetName, false)) {
                            requests.remove(viewerId, request);
                            return;
                        }
                        if (lookupFailed || result == null) {
                            requests.remove(viewerId, request);
                            MessageUtil.sendMessage(currentViewer, (lookupFailed ? Messages.COMMAND_HISTORY_LOAD_FAILED
                                    : Messages.COMMAND_HISTORY_PLAYER_NOT_FOUND).replace("%player%", targetName));
                            return;
                        }
                        loadHistory(viewerId, request, result, targetName, date, initialPage);
                    });
                } catch (IllegalPluginAccessException ignored) {
                    // The plugin disabled while the lookup was running.
                }
            }
        });
        return true;
    }

    private void saveAllSnapshots(CommandSender sender) {
        if (savingAll) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_SAVE_ALL_RUNNING);
            return;
        }
        List<UUID> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers())
            players.add(player.getUniqueId());
        if (players.isEmpty()) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_SAVE_ALL_EMPTY);
            return;
        }
        savingAll = true;
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        CommandSender console = senderId == null ? sender : null;
        InventoryHistoryManager manager = InventoryPagesPlus.getHistoryManager();
        MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_SAVE_ALL_STARTED.replace("%total%", String.valueOf(players.size())));
        new Runnable() {
            private int index;
            private int savedCount;
            private int failedCount;

            @Override
            public void run() {
                if (index == players.size() || !manager.isAvailable()) {
                    savingAll = false;
                    failedCount += players.size() - index;
                    CommandSender currentSender = senderId == null ? console : Bukkit.getPlayer(senderId);
                    if (currentSender != null && currentSender.hasPermission(Settings.HISTORY_PERMISSION)
                            && currentSender.hasPermission(Settings.HISTORY_PERMISSION_SAVE)
                            && currentSender.hasPermission(Settings.HISTORY_PERMISSION_OTHERS)) {
                        MessageUtil.sendMessage(currentSender, Messages.COMMAND_HISTORY_SAVE_ALL_COMPLETE
                                .replace("%saved%", String.valueOf(savedCount))
                                .replace("%failed%", String.valueOf(failedCount))
                                .replace("%total%", String.valueOf(players.size())));
                    }
                    return;
                }
                Player target = Bukkit.getPlayer(players.get(index++));
                manager.capture(target, SnapshotReason.MANUAL, true).whenComplete((saved, failure) -> {
                    if (!InventoryPagesPlus.plugin.isEnabled())
                        return;
                    try {
                        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
                            if (failure == null && Boolean.TRUE.equals(saved))
                                savedCount++;
                            else
                                failedCount++;
                            run();
                        });
                    } catch (IllegalPluginAccessException ignored) {
                        // The scheduler is unavailable during plugin shutdown.
                    }
                });
            }
        }.run();
    }

    private void saveSnapshot(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            MessageUtil.sendMessage(sender, Messages.COMMAND_HISTORY_PLAYER_NOT_FOUND.replace("%player%", targetName));
            return;
        }
        String targetUUID = target.getUniqueId().toString();
        String resolvedName = target.getName();
        if (!checkAccess(sender, targetUUID, resolvedName, true))
            return;
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        CommandSender console = senderId == null ? sender : null;
        InventoryPagesPlus.getHistoryManager().capture(target, SnapshotReason.MANUAL, true).whenComplete((saved, failure) -> {
            if (!InventoryPagesPlus.plugin.isEnabled())
                return;
            try {
                Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> {
                    CommandSender currentSender = senderId == null ? console : Bukkit.getPlayer(senderId);
                    if (currentSender == null || !checkAccess(currentSender, targetUUID, resolvedName, true))
                        return;
                    String message = failure == null && Boolean.TRUE.equals(saved)
                            ? Messages.COMMAND_HISTORY_SNAPSHOT_SAVED : Messages.COMMAND_HISTORY_SNAPSHOT_SAVE_FAILED;
                    MessageUtil.sendMessage(currentSender, message.replace("%player%", resolvedName));
                });
            } catch (IllegalPluginAccessException ignored) {
                // The plugin disabled while the snapshot was being saved.
            }
        });
    }

    private void loadHistory(UUID viewerId, UUID request, String targetUUID, String targetName, LocalDate date, int initialPage) {
        Player viewer = currentViewer(viewerId, request);
        if (viewer == null)
            return;
        if (!checkAccess(viewer, targetUUID, targetName, false)) {
            requests.remove(viewerId, request);
            return;
        }
        long from = date == null ? HistoryDates.cutoff() : HistoryDates.start(date);
        long to = date == null ? Long.MAX_VALUE : HistoryDates.start(date.plusDays(1));
        InventoryPagesPlus.getHistoryManager().loadSummaries(targetUUID, from, to,
                summaries -> completeHistory(viewerId, request, targetUUID, targetName, initialPage, summaries));
    }

    private void completeHistory(UUID viewerId, UUID request, String targetUUID, String targetName,
                                 int initialPage, List<InventorySnapshotSummary> summaries) {
        Player viewer = currentViewer(viewerId, request);
        if (viewer == null)
            return;
        requests.remove(viewerId, request);
        if (!checkAccess(viewer, targetUUID, targetName, false))
            return;
        if (summaries == null) {
            MessageUtil.sendMessage(viewer, Messages.COMMAND_HISTORY_LOAD_FAILED);
            return;
        }
        if (summaries.isEmpty()) {
            MessageUtil.sendMessage(viewer, Messages.COMMAND_HISTORY_NO_SNAPSHOTS.replace("%player%", targetName));
            return;
        }
        for (InventorySnapshotSummary summary : summaries) {
            if (summary == null || !targetUUID.equalsIgnoreCase(summary.getPlayerUUID())) {
                MessageUtil.sendMessage(viewer, Messages.COMMAND_HISTORY_LOAD_FAILED);
                return;
            }
        }
        if (initialPage > summaries.get(0).getMaxPage()) {
            MessageUtil.sendMessage(viewer, Messages.COMMAND_HISTORY_INVALID_PAGE);
            return;
        }
        new InventoryHistoryGui(viewer, targetUUID, targetName, summaries.get(0), initialPage).open();
    }

    private Player currentViewer(UUID viewerId, UUID request) {
        if (!request.equals(requests.get(viewerId)))
            return null;
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline()) {
            requests.remove(viewerId, request);
            return null;
        }
        return viewer;
    }

    private boolean checkAccess(CommandSender sender, String targetUUID, String targetName, boolean save) {
        String error = null;
        InventoryHistoryManager manager = InventoryPagesPlus.getHistoryManager();
        if (!Settings.HISTORY_ENABLED)
            error = Messages.COMMAND_HISTORY_DISABLED;
        else if (!sender.hasPermission(Settings.HISTORY_PERMISSION)
                || (save && !sender.hasPermission(Settings.HISTORY_PERMISSION_SAVE)))
            error = Messages.COMMAND_HISTORY_NO_PERMISSION;
        else {
            boolean self = sender instanceof Player player && (targetUUID == null
                    ? player.getName().equalsIgnoreCase(targetName)
                    : player.getUniqueId().toString().equalsIgnoreCase(targetUUID));
            if (!self && !sender.hasPermission(Settings.HISTORY_PERMISSION_OTHERS))
                error = Messages.COMMAND_HISTORY_NO_PERMISSION_OTHERS;
            else if (manager == null || !manager.isAvailable())
                error = Messages.COMMAND_HISTORY_LOAD_FAILED;
        }
        if (error == null)
            return true;
        MessageUtil.sendMessage(sender, error);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Settings.HISTORY_ENABLED || !sender.hasPermission(Settings.HISTORY_PERMISSION))
            return Collections.emptyList();
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(Settings.HISTORY_PERMISSION_SAVE))
                candidates.add("save");
            if (sender instanceof Player)
                addOnlineNames(sender, candidates);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("save")
                && sender.hasPermission(Settings.HISTORY_PERMISSION_SAVE)) {
            addOnlineNames(sender, candidates);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("save")) {
            LocalDate today = LocalDate.now(Settings.HISTORY_ZONE);
            for (int offset = 0; offset < 7; offset++)
                candidates.add(HistoryDates.format(today.minusDays(offset)));
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("save")) {
            for (int page = 1; page <= 9; page++)
                candidates.add(String.valueOf(page));
        } else {
            return Collections.emptyList();
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], candidates, matches);
        if (matches.isEmpty())
            return Collections.emptyList();
        Collections.sort(matches);
        return matches;
    }

    private void addOnlineNames(CommandSender sender, List<String> candidates) {
        boolean others = sender.hasPermission(Settings.HISTORY_PERMISSION_OTHERS);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (others || (sender instanceof Player player && player.getUniqueId().equals(online.getUniqueId())))
                candidates.add(online.getName());
        }
    }
}
