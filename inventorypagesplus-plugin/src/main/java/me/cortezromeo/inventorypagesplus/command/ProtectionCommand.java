package me.cortezromeo.inventorypagesplus.command;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.ProtectionManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.support.DialogSupport;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProtectionCommand implements CommandExecutor, TabExecutor {

    public ProtectionCommand() {
        InventoryPagesPlus.plugin.getCommand("pageprotect").setExecutor(this);
        DebugManager.debug("LOADING COMMAND", "Loaded ProtectionCommand.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, Messages.NON_CONSOLE_COMMAND);
            return false;
        }

        if (!Settings.PROTECTION_ENABLED) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_DISABLED);
            return false;
        }

        PlayerInventoryDatabase data = getData(player);
        if (data == null) {
            MessageUtil.sendMessage(player, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", player.getName()));
            return false;
        }

        // The dialog is only a richer entry point: every button runs the matching sub-command, so
        // the plain command paths below stay the single source of truth and the fallback.
        if (args.length == 0) {
            if (!openMainDialog(player, data, label))
                sendHelp(player);
            return false;
        }
        // A locked page only accepts a PIN attempt, help, status, and a permitted admin reset.
        if (data.isProtectionLocked() && !isAllowedWhileLocked(player, args[0])) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_LOCKED_REMINDER);
            return false;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return false;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length == 1 && openSetDialog(player, data, label))
                return false;
            handleSet(player, data, args);
            return false;
        }
        if (args[0].equalsIgnoreCase("change")) {
            if (args.length == 1 && openChangeDialog(player, data, label))
                return false;
            handleChange(player, data, args);
            return false;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length == 1 && openRemoveDialog(player, data, label))
                return false;
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

        // Only digit arguments count as PIN guesses, so a typo never burns an attempt.
        if (isPinArgument(args[0]))
            handleUnlock(player, data, args[0]);
        else
            sendHelp(player);
        return false;
    }

    private boolean isAllowedWhileLocked(Player player, String arg) {
        return isPinArgument(arg)
                || arg.equalsIgnoreCase("help")
                || arg.equalsIgnoreCase("status")
                || (arg.equalsIgnoreCase("admin") && player.hasPermission("inventorypagesplus.protect.admin"));
    }

    private boolean isPinArgument(String arg) {
        if (arg.isEmpty())
            return false;
        for (int i = 0; i < arg.length(); i++) {
            if (arg.charAt(i) < '0' || arg.charAt(i) > '9')
                return false;
        }
        return true;
    }

    private boolean openMainDialog(Player player, PlayerInventoryDatabase data, String label) {
        // A locked page only has one useful action, so ask for the PIN instead of showing the menu.
        if (data.hasProtectionPin() && data.isProtectionLocked()) {
            if (sendAdminBanned(player, data))
                return true;
            if (ProtectionManager.isBanned(data)) {
                sendBanned(player, data);
                return true;
            }
            return openUnlockDialog(player, label);
        }
        return openMenu(player, data, label);
    }

    private boolean openMenu(Player player, PlayerInventoryDatabase data, String label) {
        Map<String, String> actions = new LinkedHashMap<>();
        if (data.hasProtectionPin()) {
            actions.put(Messages.COMMAND_PROTECTION_DIALOG_BUTTON_LOCK, label + " lock");
            actions.put(Messages.COMMAND_PROTECTION_DIALOG_BUTTON_CHANGE, label + " change");
            actions.put(Messages.COMMAND_PROTECTION_DIALOG_BUTTON_REMOVE, label + " remove");
        } else {
            actions.put(Messages.COMMAND_PROTECTION_DIALOG_BUTTON_SET, label + " set");
        }
        actions.put(Messages.COMMAND_PROTECTION_DIALOG_BUTTON_STATUS, label + " status");

        return DialogSupport.showMenu(player, Messages.COMMAND_PROTECTION_DIALOG_TITLE,
                Messages.COMMAND_PROTECTION_DIALOG_MENU_BODY, actions);
    }

    private boolean openSetDialog(Player player, PlayerInventoryDatabase data, String label) {
        // Falling through lets handleSet report the already-set error instead of a doomed dialog.
        if (data.hasProtectionPin())
            return false;
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("pin", Messages.COMMAND_PROTECTION_DIALOG_INPUT_PIN);
        inputs.put("confirm", Messages.COMMAND_PROTECTION_DIALOG_INPUT_CONFIRM_PIN);
        return openPrompt(player, Messages.COMMAND_PROTECTION_DIALOG_SET_TITLE,
                Messages.COMMAND_PROTECTION_DIALOG_SET_BODY, inputs, label + " set $(pin) $(confirm)");
    }

    private boolean openUnlockDialog(Player player, String label) {
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("pin", Messages.COMMAND_PROTECTION_DIALOG_INPUT_PIN);
        return openPrompt(player, Messages.COMMAND_PROTECTION_DIALOG_UNLOCK_TITLE,
                Messages.COMMAND_PROTECTION_DIALOG_UNLOCK_BODY, inputs, label + " $(pin)");
    }

    private boolean openChangeDialog(Player player, PlayerInventoryDatabase data, String label) {
        // Falling through lets handleChange report the not-set error instead of a doomed dialog.
        if (!data.hasProtectionPin())
            return false;
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("old", Messages.COMMAND_PROTECTION_DIALOG_INPUT_PIN);
        inputs.put("new", Messages.COMMAND_PROTECTION_DIALOG_INPUT_NEW_PIN);
        inputs.put("confirm", Messages.COMMAND_PROTECTION_DIALOG_INPUT_CONFIRM_PIN);
        return openPrompt(player, Messages.COMMAND_PROTECTION_DIALOG_CHANGE_TITLE,
                Messages.COMMAND_PROTECTION_DIALOG_CHANGE_BODY, inputs, label + " change $(old) $(new) $(confirm)");
    }

    private boolean openRemoveDialog(Player player, PlayerInventoryDatabase data, String label) {
        // Falling through lets handleRemove report the not-set error instead of a doomed dialog.
        if (!data.hasProtectionPin())
            return false;
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("pin", Messages.COMMAND_PROTECTION_DIALOG_INPUT_PIN);
        return openPrompt(player, Messages.COMMAND_PROTECTION_DIALOG_REMOVE_TITLE,
                Messages.COMMAND_PROTECTION_DIALOG_REMOVE_BODY, inputs, label + " remove $(pin)");
    }

    private boolean openPrompt(Player player, String title, String body, Map<String, String> inputs, String commandTemplate) {
        return DialogSupport.showPrompt(player, title, body, inputs, commandTemplate,
                Messages.COMMAND_PROTECTION_DIALOG_CONFIRM, Messages.COMMAND_PROTECTION_DIALOG_CANCEL);
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
        // Malformed PINs are rejected before checkPin so only real guesses cost an attempt.
        if (!ProtectionManager.isValidPin(pin)) {
            sendInvalidPin(player);
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

        // The locked-state gate above guarantees an unlocked page, so no unlock handling is needed here.
        data.setProtectionPinHash(ProtectionManager.hashPin(newPin));
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);
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

        // The locked-state gate above guarantees an unlocked page, so no unlock handling is needed here.
        data.setProtectionPinHash(null);
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_REMOVE_SUCCESS);
    }

    private void handleLock(Player player, PlayerInventoryDatabase data) {
        if (!data.hasProtectionPin()) {
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_NOT_SET);
            return;
        }
        // The locked-state gate above guarantees an unlocked page here, so re-locking can never
        // hit the case where real items sit in the display slots (ProtectionManager.lock saves first).
        ProtectionManager.lock(data);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(player.getName());
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
        if (args.length < 3 || !isAdminAction(args[1])) {
            sendHelp(player);
            return;
        }

        // Offline targets are resolved through the name-based database loader.
        Player target = Bukkit.getPlayerExact(args[2]);
        PlayerInventoryDatabase targetData = target != null
                ? getData(target)
                : InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(args[2], true);
        if (targetData == null) {
            MessageUtil.sendMessage(player, Messages.TARGETS_DATABASE_DOESNT_EXIST.replace("%player%", args[2]));
            return;
        }
        String targetName = targetData.getPlayerName();

        if (args[1].equalsIgnoreCase("set")) {
            handleAdminSet(player, target, targetData, targetName, args);
            return;
        }
        String reason = adminReason(args);
        boolean wasLocked = targetData.isProtectionLocked();

        if (args[1].equalsIgnoreCase("ban")) {
            ProtectionManager.adminBan(targetData, reason);
            InventoryPagesPlus.getDatabaseManager().savePlayerInventory(targetName);
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_BAN
                    .replace("%player%", targetName).replace("%reason%", reason));
            if (target != null)
                MessageUtil.sendMessage(target, Messages.COMMAND_PROTECTION_ADMIN_BAN_TARGET.replace("%reason%", reason));
            return;
        }

        if (args[1].equalsIgnoreCase("reset")) {
            // reset wipes everything, including the player's own PIN.
            ProtectionManager.clearAdminBan(targetData);
            targetData.setProtectionPinHash(null);
            targetData.setProtectionAttempts(0);
            targetData.setProtectionBanUntil(0);
            if (wasLocked)
                ProtectionManager.unlock(targetData);
            else
                targetData.setProtectionUnlocked(true);
            InventoryPagesPlus.getDatabaseManager().savePlayerInventory(targetName);
            MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_RESET.replace("%player%", targetName));
            if (target != null)
                MessageUtil.sendMessage(target, Messages.COMMAND_PROTECTION_ADMIN_RESET_TARGET.replace("%player%", player.getName()));
            return;
        }

        // unban is the exact inverse of ban: the assigned PIN disappears, the player's own PIN survives.
        ProtectionManager.adminUnban(targetData);
        if (wasLocked)
            ProtectionManager.unlock(targetData);
        else
            targetData.setProtectionUnlocked(true);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(targetName);
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_UNBAN
                .replace("%player%", targetName).replace("%reason%", reason));
        if (target != null)
            MessageUtil.sendMessage(target, Messages.COMMAND_PROTECTION_ADMIN_UNBAN_TARGET);
    }

    private void handleAdminSet(Player player, Player target, PlayerInventoryDatabase targetData,
                                String targetName, String[] args) {
        if (args.length < 4) {
            sendHelp(player);
            return;
        }
        if (!ProtectionManager.isValidPin(args[3])) {
            sendInvalidPin(player);
            return;
        }
        boolean hadPin = targetData.hasProtectionPin();
        // An explicit PIN replaces any administrator ban; setting one on an unprotected player locks their pages too.
        ProtectionManager.clearAdminBan(targetData);
        targetData.setProtectionPinHash(ProtectionManager.hashPin(args[3]));
        targetData.setProtectionAttempts(0);
        targetData.setProtectionBanUntil(0);
        if (!hadPin)
            ProtectionManager.lock(targetData);
        InventoryPagesPlus.getDatabaseManager().savePlayerInventory(targetName);
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_SET.replace("%player%", targetName));
        if (target != null)
            MessageUtil.sendMessage(target, Messages.COMMAND_PROTECTION_ADMIN_SET_TARGET);
    }

    private static boolean isAdminAction(String arg) {
        return arg.equalsIgnoreCase("reset") || arg.equalsIgnoreCase("ban")
                || arg.equalsIgnoreCase("unban") || arg.equalsIgnoreCase("set");
    }

    private static String adminReason(String[] args) {
        return args.length <= 3 ? "none" : String.join(" ", Arrays.copyOfRange(args, 3, args.length));
    }

    // Shared verify path for unlock/change/remove: admin ban gate, ban gate, then attempt counting on a wrong PIN.
    private boolean checkPin(Player player, PlayerInventoryDatabase data, String pin) {
        if (sendAdminBanned(player, data))
            return false;
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

    // Returns true and shows the reason when the pages sit under an administrator ban.
    private boolean sendAdminBanned(Player player, PlayerInventoryDatabase data) {
        String reason = ProtectionManager.getAdminBanReason(data);
        if (reason == null)
            return false;
        MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_ADMIN_BANNED.replace("%reason%", reason));
        return true;
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
        // Admin usage lines only appear for players allowed to run them.
        if (sender.hasPermission("inventorypagesplus.protect.admin"))
            for (String message : Messages.COMMAND_PROTECTION_ADMIN_MESSAGES)
                MessageUtil.sendMessage(sender, message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("inventorypagesplus.protect.admin");

        // Never complete PIN arguments, so a PIN is never leaked through tab completion.
        if (args.length == 1) {
            commands.add("set");
            commands.add("change");
            commands.add("remove");
            commands.add("lock");
            commands.add("status");
            commands.add("help");
            if (isAdmin)
                commands.add("admin");
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && isAdmin) {
            commands.add("ban");
            commands.add("reset");
            commands.add("set");
            commands.add("unban");
            StringUtil.copyPartialMatches(args[1], commands, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && isAdmin && isAdminAction(args[1])) {
            for (Player player : Bukkit.getOnlinePlayers())
                commands.add(player.getName());
            StringUtil.copyPartialMatches(args[2], commands, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}