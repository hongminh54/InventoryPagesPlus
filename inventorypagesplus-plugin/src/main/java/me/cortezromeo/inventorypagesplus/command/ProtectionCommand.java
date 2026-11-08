package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.ProtectionManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtectionCommand implements CommandExecutor, TabExecutor {

    public ProtectionCommand() {
        InventoryPagesPlus.plugin.getCommand("baove").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded ProtectionCommand.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
            return false;
        }
        Player player = (Player) sender;

        if (!Settings.PROTECTION_ENABLED) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_DISABLED);
            return false;
        }

        PlayerInventoryDatabase data = getData(player);
        if (data == null) {
            MessageUtil.sendMessage(player, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", player.getName()));
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return false;
        }
        if (args[0].equalsIgnoreCase("set")) {
            handleSet(player, data, args);
            return false;
        }
        if (args[0].equalsIgnoreCase("change")) {
            handleChange(player, data, args);
            return false;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            handleRemove(player, data, args);
            return false;
        }
        if (args[0].equalsIgnoreCase("lock")) {
            handleLock(player, data);
            return false;
        }
        if (args[0].equalsIgnoreCase("status")) {
            handleStatus(player, data);
            return false;
        }
        if (args[0].equalsIgnoreCase("admin")) {
            handleAdmin(player, args);
            return false;
        }

        handleUnlock(player, data, args[0]);
        return false;
    }

    private void handleSet(Player player, PlayerInventoryDatabase data, String[] args) {
        if (data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ALREADY_SET);
            return;
        }
        if (args.length < 3) {
            sendHelp(player);
            return;
        }
        String pin = args[1];
        if (!pin.equals(args[2])) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_PIN_MISMATCH);
            return;
        }
        if (!ProtectionManager.isValidPin(pin)) {
            sendInvalidPin(player);
            return;
        }

        data.setProtectionPinHash(ProtectionManager.hashPin(pin));
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);
        // The page is already displayed (there was no PIN), so absorbing items again would duplicate them.
        data.setProtectionUnlocked(true);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_SET_SUCCESS);
    }

    private void handleUnlock(Player player, PlayerInventoryDatabase data, String pin) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_NOT_SET);
            return;
        }
        if (!data.isProtectionLocked()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ALREADY_UNLOCKED);
            return;
        }
        if (!checkPin(player, data, pin))
            return;

        boolean dropped = ProtectionManager.unlock(data);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_UNLOCKED);
        if (dropped)
            MessageUtil.sendMessage(player, Messages.ITEMS_DROPPED);
    }

    private void handleChange(Player player, PlayerInventoryDatabase data, String[] args) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_NOT_SET);
            return;
        }
        if (args.length < 4) {
            sendHelp(player);
            return;
        }
        if (!checkPin(player, data, args[1]))
            return;

        String newPin = args[2];
        if (!ProtectionManager.isValidPin(newPin)) {
            sendInvalidPin(player);
            return;
        }
        if (!newPin.equals(args[3])) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_PIN_MISMATCH);
            return;
        }

        boolean wasLocked = data.isProtectionLocked();
        data.setProtectionPinHash(ProtectionManager.hashPin(newPin));
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);
        if (wasLocked)
            ProtectionManager.unlock(data);
        else
            data.setProtectionUnlocked(true);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_CHANGE_SUCCESS);
    }

    private void handleRemove(Player player, PlayerInventoryDatabase data, String[] args) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_NOT_SET);
            return;
        }
        if (args.length < 2) {
            sendHelp(player);
            return;
        }
        if (!checkPin(player, data, args[1]))
            return;

        boolean wasLocked = data.isProtectionLocked();
        data.setProtectionPinHash(null);
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);
        if (wasLocked)
            ProtectionManager.unlock(data);
        else
            data.setProtectionUnlocked(true);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_REMOVE_SUCCESS);
    }

    private void handleLock(Player player, PlayerInventoryDatabase data) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_NOT_SET);
            return;
        }
        // Re-locking an already hidden page would wipe the real items sitting in slots 9..35.
        if (!data.isProtectionLocked()) {
            ProtectionManager.lock(data);
            InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        }
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_LOCKED);
    }

    private void handleStatus(Player player, PlayerInventoryDatabase data) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_STATUS_NONE);
            return;
        }
        if (ProtectionManager.isBanned(data)) {
            sendBanned(player, data);
            return;
        }
        if (data.isProtectionLocked())
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_STATUS_LOCKED);
        else
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_STATUS_UNLOCKED);
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("inventorypagesplus.protect.admin")) {
            MessageUtil.sendMessage(player, Messages.NO_PERMISSION);
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("reset")) {
            sendHelp(player);
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            MessageUtil.sendMessage(player, Messages.INVALID_NAME.replace("%player%", args[2]));
            return;
        }
        PlayerInventoryDatabase targetData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getUniqueId());
        if (targetData == null)
            targetData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(target.getName(), true);
        if (targetData == null) {
            MessageUtil.sendMessage(player, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", target.getName()));
            return;
        }

        boolean wasLocked = targetData.isProtectionLocked();
        targetData.setProtectionPinHash(null);
        targetData.setProtectionAttempts(0);
        targetData.setProtectionBanUntil(0);
        if (wasLocked)
            ProtectionManager.unlock(targetData);
        else
            targetData.setProtectionUnlocked(true);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(target.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_RESET.replace("%player%", target.getName()));
        MessageUtil.sendMessage(target, Messages.COMMAND_PROTECTION_ADMIN_RESET_TARGET.replace("%player%", player.getName()));
    }

    // Shared verify path for unlock/change/remove: ban gate, then attempt counting on a wrong PIN.
    private boolean checkPin(Player player, PlayerInventoryDatabase data, String pin) {
        if (ProtectionManager.isBanned(data)) {
            sendBanned(player, data);
            return false;
        }
        if (ProtectionManager.verifyPin(pin, data.getProtectionPinHash()))
            return true;

        boolean justBanned = ProtectionManager.registerFailedAttempt(data);
        if (justBanned) {
            InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
            sendBanned(player, data);
        } else {
            int attemptsLeft = Math.max(1, Settings.PROTECTION_MAX_ATTEMPTS) - data.getProtectionAttempts();
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_WRONG_PIN.replace("%attempts%", String.valueOf(Math.max(0, attemptsLeft))));
        }
        return false;
    }

    private PlayerInventoryDatabase getData(Player player) {
        PlayerInventoryDatabase data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getUniqueId());
        if (data == null)
            data = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(player.getName(), true);
        return data;
    }

    private void sendBanned(CommandSender sender, PlayerInventoryDatabase data) {
        long remaining = ProtectionManager.getRemainingBanSeconds(data);
        MessageUtil.sendMessage(sender, Messages.COMMAND_PROTECTION_BANNED
                .replace("%minutes%", String.valueOf(remaining / 60))
                .replace("%seconds%", String.valueOf(remaining % 60)));
    }

    private void sendInvalidPin(Player player) {
        int min = Math.max(1, Settings.PROTECTION_PIN_MIN_LENGTH);
        int max = Math.max(min, Settings.PROTECTION_PIN_MAX_LENGTH);
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_INVALID_PIN
                .replace("%min%", String.valueOf(min))
                .replace("%max%", String.valueOf(max)));
    }

    private void sendHelp(CommandSender sender) {
        for (String message : Messages.COMMAND_PROTECTION_MESSAGES)
            MessageUtil.sendMessage(sender, message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        // Never complete PIN arguments, so a PIN is never leaked through tab completion.
        if (args.length == 1) {
            commands.add("set");
            commands.add("change");
            commands.add("remove");
            commands.add("lock");
            commands.add("status");
            commands.add("help");
            if (sender.hasPermission("inventorypagesplus.protect.admin"))
                commands.add("admin");
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("inventorypagesplus.protect.admin")) {
            commands.add("reset");
            StringUtil.copyPartialMatches(args[1], commands, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("reset") && sender.hasPermission("inventorypagesplus.protect.admin")) {
            for (Player player : Bukkit.getOnlinePlayers())
                commands.add(player.getName());
            StringUtil.copyPartialMatches(args[2], commands, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}