package me.cortezromeo.inventorypagesplus.language;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Messages {

    public static String PREFIX;
    public static String NO_PERMISSION;
    public static String NON_CONSOLE_COMMAND;
    public static String ITEMS_DROPPED;
    public static String INVALID_NUMBER;
    public static String INVALID_NAME;
    public static String GET_PLAYER_DATA;
    public static String TARGETS_DATABASE_DOESNT_EXIST;
    public static String NO_PAGE_MESSAGES;
    public static List<String> COMMAND_INVENTORYPAGESPLUS_MESSAGES = new ArrayList<>();
    public static String COMMAND_INVENTORYPAGESPLUS_RELOAD;
    public static String COMMAND_INVENTORYPAGESPLUS_BACKUP_START;
    public static String COMMAND_INVENTORYPAGESPLUS_BACKUP_COMPLETE;
    public static String COMMAND_INVENTORYPAGESPLUS_SET_MAX_PAGE;
    public static String COMMAND_INVENTORYPAGESPLUS_ADD_MAX_PAGE;
    public static String COMMAND_INVENTORYPAGESPLUS_REMOVE_MAX_PAGE;
    public static List<String> COMMAND_SETPAGESLOT_MESSAGES = new ArrayList<>();
    public static String COMMAND_SETPAGESLOT_MISMATCH_NEXT_PAGE;
    public static String COMMAND_SETPAGESLOT_MISMATCH_PREV_PAGE;
    public static String COMMAND_SETPAGESLOT_SLOT_RANGE_ERROR;
    public static String COMMAND_SETPAGESLOT_EMPTY_SLOT_REQUEST;
    public static String COMMAND_SETPAGESLOT_NO_CHANGE_RESET;
    public static String COMMAND_SETPAGESLOT_SET_NEXT_PAGE;
    public static String COMMAND_SETPAGESLOT_SET_PREV_PAGE;
    public static String COMMAND_SETPAGESLOT_RESET_PAGE_SLOT;
    public static List<String> COMMAND_INVSEE_MESSAGES = new ArrayList<>();
    public static String COMMAND_INVSEE_NO_OFFLINE_INVSEE_PERMISSION;
    public static String COMMAND_CLEAR_CLEAR;
    public static String COMMAND_CLEAR_CLEAR_ALL;
    public static String COMMAND_CLEAR_CLEAR_TARGET;
    public static String COMMAND_CLEAR_CLEAR_TARGETS_MESSAGE;
    public static String COMMAND_CLEAR_CLEAR_ALL_TARGET;
    public static String COMMAND_CLEAR_CLEAR_ALL_TARGETS_MESSAGE;
    public static List<String> COMMAND_PROTECTION_MESSAGES = new ArrayList<>();
    public static String COMMAND_PROTECTION_DISABLED;
    public static String COMMAND_PROTECTION_NOT_SET;
    public static String COMMAND_PROTECTION_ALREADY_SET;
    public static String COMMAND_PROTECTION_INVALID_PIN;
    public static String COMMAND_PROTECTION_PIN_MISMATCH;
    public static String COMMAND_PROTECTION_WRONG_PIN;
    public static String COMMAND_PROTECTION_BANNED;
    public static String COMMAND_PROTECTION_UNLOCKED;
    public static String COMMAND_PROTECTION_LOCKED;
    public static String COMMAND_PROTECTION_LOCKED_REMINDER;
    public static String COMMAND_PROTECTION_ALREADY_UNLOCKED;
    public static String COMMAND_PROTECTION_SET_SUCCESS;
    public static String COMMAND_PROTECTION_CHANGE_SUCCESS;
    public static String COMMAND_PROTECTION_REMOVE_SUCCESS;
    public static String COMMAND_PROTECTION_STATUS_NONE;
    public static String COMMAND_PROTECTION_STATUS_LOCKED;
    public static String COMMAND_PROTECTION_STATUS_UNLOCKED;
    public static String COMMAND_PROTECTION_ADMIN_RESET;
    public static String COMMAND_PROTECTION_ADMIN_RESET_TARGET;
    public static String COMMAND_PROTECTION_COMMAND_BLOCKED;

    public static void setupValue(String locale) {
        locale = locale.toLowerCase();
        File messageFile = new File(InventoryPagesPlus.plugin.getDataFolder() + "/languages/messages_" + locale + ".yml");
        FileConfiguration fileConfiguration;
        if (!messageFile.exists()) {
            fileConfiguration = English.get();
            MessageUtil.log("&c--------------------------------------");
            MessageUtil.log("    &4ERROR");
            MessageUtil.log("&eLocale &c&l" + locale + "&e does not exist!");
            MessageUtil.log("&ePlease check it again in config.yml.");
            MessageUtil.log("&eMessages will automatically be loaded using &b&lEnglish&e.");
            MessageUtil.log("&c--------------------------------------");
        } else {
            fileConfiguration = YamlConfiguration.loadConfiguration(messageFile);
        }

        PREFIX = fileConfiguration.getString("messages.prefix");
        NO_PERMISSION = fileConfiguration.getString("messages.no-permission");
        NON_CONSOLE_COMMAND = fileConfiguration.getString("messages.non-console-command");
        ITEMS_DROPPED = fileConfiguration.getString("messages.items-dropped");
        INVALID_NUMBER = fileConfiguration.getString("messages.invalid-number");
        INVALID_NAME = fileConfiguration.getString("messages.invalid-name");
        GET_PLAYER_DATA = fileConfiguration.getString("messages.get-player-data");
        TARGETS_DATABASE_DOESNT_EXIST = fileConfiguration.getString("messages.targets-database-doesnt-exist");
        NO_PAGE_MESSAGES = fileConfiguration.getString("messages.no-page-message");
        COMMAND_INVENTORYPAGESPLUS_MESSAGES = fileConfiguration.getStringList("messages.commands.inventorypagesplus.messages");
        COMMAND_INVENTORYPAGESPLUS_RELOAD = fileConfiguration.getString("messages.commands.inventorypagesplus.reload");
        COMMAND_INVENTORYPAGESPLUS_BACKUP_START = fileConfiguration.getString("messages.commands.inventorypagesplus.backup.start");
        COMMAND_INVENTORYPAGESPLUS_BACKUP_COMPLETE = fileConfiguration.getString("messages.commands.inventorypagesplus.backup.complete");
        COMMAND_INVENTORYPAGESPLUS_SET_MAX_PAGE = fileConfiguration.getString("messages.commands.inventorypagesplus.set-max-page");
        COMMAND_INVENTORYPAGESPLUS_ADD_MAX_PAGE = fileConfiguration.getString("messages.commands.inventorypagesplus.add-max-page");
        COMMAND_INVENTORYPAGESPLUS_REMOVE_MAX_PAGE = fileConfiguration.getString("messages.commands.inventorypagesplus.remove-max-page");
        COMMAND_SETPAGESLOT_MESSAGES = fileConfiguration.getStringList("messages.commands.setpageslot.messages");
        COMMAND_SETPAGESLOT_MISMATCH_NEXT_PAGE = fileConfiguration.getString("messages.commands.setpageslot.page-slot-mismatch.next-page");
        COMMAND_SETPAGESLOT_MISMATCH_PREV_PAGE = fileConfiguration.getString("messages.commands.setpageslot.page-slot-mismatch.prev-page");
        COMMAND_SETPAGESLOT_SLOT_RANGE_ERROR = fileConfiguration.getString("messages.commands.setpageslot.slot-range-error");
        COMMAND_SETPAGESLOT_EMPTY_SLOT_REQUEST = fileConfiguration.getString("messages.commands.setpageslot.empty-slot-request");
        COMMAND_SETPAGESLOT_NO_CHANGE_RESET = fileConfiguration.getString("messages.commands.setpageslot.no-change-reset");
        COMMAND_SETPAGESLOT_SET_NEXT_PAGE = fileConfiguration.getString("messages.commands.setpageslot.next-page-slot-set");
        COMMAND_SETPAGESLOT_SET_PREV_PAGE = fileConfiguration.getString("messages.commands.setpageslot.prev-page-slot-set");
        COMMAND_SETPAGESLOT_RESET_PAGE_SLOT = fileConfiguration.getString("messages.commands.setpageslot.reset-page-slot-number");
        COMMAND_INVSEE_MESSAGES = fileConfiguration.getStringList("messages.commands.invsee.messages");
        COMMAND_INVSEE_NO_OFFLINE_INVSEE_PERMISSION = fileConfiguration.getString("messages.commands.invsee.no-offline-invsee-permission");
        COMMAND_CLEAR_CLEAR = fileConfiguration.getString("messages.commands.clear.clear");
        COMMAND_CLEAR_CLEAR_ALL = fileConfiguration.getString("messages.commands.clear.clear-all");
        COMMAND_CLEAR_CLEAR_TARGET = fileConfiguration.getString("messages.commands.clear.clear-target");
        COMMAND_CLEAR_CLEAR_TARGETS_MESSAGE = fileConfiguration.getString("messages.commands.clear.clear-targets-message");
        COMMAND_CLEAR_CLEAR_ALL_TARGET = fileConfiguration.getString("messages.commands.clear.clear-all-target");
        COMMAND_CLEAR_CLEAR_ALL_TARGETS_MESSAGE = fileConfiguration.getString("messages.commands.clear.clear-all-targets-message");
        COMMAND_PROTECTION_MESSAGES = fileConfiguration.getStringList("messages.commands.protection.messages");
        COMMAND_PROTECTION_DISABLED = fileConfiguration.getString("messages.commands.protection.disabled");
        COMMAND_PROTECTION_NOT_SET = fileConfiguration.getString("messages.commands.protection.not-set");
        COMMAND_PROTECTION_ALREADY_SET = fileConfiguration.getString("messages.commands.protection.already-set");
        COMMAND_PROTECTION_INVALID_PIN = fileConfiguration.getString("messages.commands.protection.invalid-pin");
        COMMAND_PROTECTION_PIN_MISMATCH = fileConfiguration.getString("messages.commands.protection.pin-mismatch");
        COMMAND_PROTECTION_WRONG_PIN = fileConfiguration.getString("messages.commands.protection.wrong-pin");
        COMMAND_PROTECTION_BANNED = fileConfiguration.getString("messages.commands.protection.banned");
        COMMAND_PROTECTION_UNLOCKED = fileConfiguration.getString("messages.commands.protection.unlocked");
        COMMAND_PROTECTION_LOCKED = fileConfiguration.getString("messages.commands.protection.locked");
        COMMAND_PROTECTION_LOCKED_REMINDER = fileConfiguration.getString("messages.commands.protection.locked-reminder");
        COMMAND_PROTECTION_ALREADY_UNLOCKED = fileConfiguration.getString("messages.commands.protection.already-unlocked");
        COMMAND_PROTECTION_SET_SUCCESS = fileConfiguration.getString("messages.commands.protection.set-success");
        COMMAND_PROTECTION_CHANGE_SUCCESS = fileConfiguration.getString("messages.commands.protection.change-success");
        COMMAND_PROTECTION_REMOVE_SUCCESS = fileConfiguration.getString("messages.commands.protection.remove-success");
        COMMAND_PROTECTION_STATUS_NONE = fileConfiguration.getString("messages.commands.protection.status-none");
        COMMAND_PROTECTION_STATUS_LOCKED = fileConfiguration.getString("messages.commands.protection.status-locked");
        COMMAND_PROTECTION_STATUS_UNLOCKED = fileConfiguration.getString("messages.commands.protection.status-unlocked");
        COMMAND_PROTECTION_ADMIN_RESET = fileConfiguration.getString("messages.commands.protection.admin-reset");
        COMMAND_PROTECTION_ADMIN_RESET_TARGET = fileConfiguration.getString("messages.commands.protection.admin-reset-target");
        COMMAND_PROTECTION_COMMAND_BLOCKED = fileConfiguration.getString("messages.commands.protection.command-blocked");

        DebugManager.debug("LOADING MESSAGES", "Loaded message file name: " + locale + ".");
    }

}
