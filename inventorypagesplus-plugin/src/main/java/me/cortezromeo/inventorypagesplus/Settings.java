package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.ProtectionManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class Settings {

    public static String DATABASE_TYPE;
    public static String DATABASE_SETTINGS_H2_FILE_NAME;
    public static String DATABASE_SETTINGS_H2_TABLE;
    public static String DATABASE_SETTINGS_MYSQL_HOST;
    public static String DATABASE_SETTINGS_MYSQL_PORT;
    public static String DATABASE_SETTINGS_MYSQL_NAME;
    public static String DATABASE_SETTINGS_MYSQL_TABLE;
    public static String DATABASE_SETTINGS_MYSQL_USER;
    public static String DATABASE_SETTINGS_MYSQL_PASSWORD;
    public static String LOCALE;
    public static boolean AUTO_SAVE_ENABLED;
    public static int AUTO_SAVE_SECONDS;
    public static boolean DEBUG_ENABLED;
    public static String DEBUG_PREFIX;
    public static String BACKUP_FILE_NAME_DATE_FORMAT;
    public static int INVENTORY_SETTINGS_MAX_PAGE_DEFAULT;
    public static int INVENTORY_SETTINGS_PREV_ITEM_POS_DEFAULT;
    public static int INVENTORY_SETTINGS_NEXT_ITEM_POS_DEFAULT;
    public static boolean INVENTORY_SETTINGS_FOCUS_USING_DEFAULT_ITEM_POS;
    public static boolean INVENTORY_SETTINGS_USE_SAVED_CURRENT_PAGE;
    public static boolean INVENTORY_SETTINGS_KEEP_INVENTORY;
    public static boolean INVENTORY_SETTINGS_KEEP_LEVEL;
    public static boolean INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY;
    public static int PAGE_PERMISSIONS_DEFAULT_MAX_PAGE;
    public static String PAGE_PERMISSIONS_UNLIMITED_LABEL;
    public static boolean ADVANCED_PICK_UP_SETTINGS_ENABLED;
    public static boolean ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_ENABLED;
    public static String ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_TEXT;
    public static boolean ADVANCED_PICK_UP_SETTINGS_SOUND_ENABLED;
    public static String ADVANCED_PICK_UP_SETTINGS_SOUND_NAME;
    public static double ADVANCED_PICK_UP_SETTINGS_SOUND_VOLUME;
    public static double ADVANCED_PICK_UP_SETTINGS_SOUND_PITCH;
    public static List<Integer> SKIP_SLOTS = new ArrayList<>();
    public static boolean PROTECTION_ENABLED;
    public static int PROTECTION_PIN_MIN_LENGTH;
    public static int PROTECTION_PIN_MAX_LENGTH;
    public static int PROTECTION_MAX_ATTEMPTS;
    public static long PROTECTION_BAN_SECONDS;
    public static List<String> PROTECTION_COMMAND_BLACKLIST = new ArrayList<>();

    public static void setupValue() {
        DebugManager.debug("SETTINGS", "Loading settings from config yaml...");
        FileConfiguration configuration = InventoryPagesPlus.plugin.getConfig();

        DATABASE_TYPE = configuration.getString("database.type");
        DATABASE_SETTINGS_H2_FILE_NAME = configuration.getString("database.settings.h2.file-name");
        DATABASE_SETTINGS_H2_TABLE = configuration.getString("database.settings.h2.table");
        DATABASE_SETTINGS_H2_FILE_NAME = configuration.getString("database.settings.h2.file-name");
        DATABASE_SETTINGS_MYSQL_HOST = configuration.getString("database.settings.mysql.database.host");
        DATABASE_SETTINGS_MYSQL_PORT = configuration.getString("database.settings.mysql.database.port");
        DATABASE_SETTINGS_MYSQL_NAME = configuration.getString("database.settings.mysql.database.name");
        DATABASE_SETTINGS_MYSQL_TABLE = configuration.getString("database.settings.mysql.database.table");
        DATABASE_SETTINGS_MYSQL_USER = configuration.getString("database.settings.mysql.database.user");
        DATABASE_SETTINGS_MYSQL_PASSWORD = configuration.getString("database.settings.mysql.database.password");
        LOCALE = configuration.getString("locale");
        DEBUG_ENABLED = configuration.getBoolean("debug.enabled");
        DEBUG_PREFIX = configuration.getString("debug.prefix");
        AUTO_SAVE_ENABLED = configuration.getBoolean("auto-save.enabled");
        AUTO_SAVE_SECONDS = configuration.getInt("auto-save.seconds");
        BACKUP_FILE_NAME_DATE_FORMAT = configuration.getString("backup-settings.file-name-date-format");
        INVENTORY_SETTINGS_MAX_PAGE_DEFAULT = configuration.getInt("inventory-settings.max-page-default");
        INVENTORY_SETTINGS_PREV_ITEM_POS_DEFAULT = configuration.getInt("inventory-settings.prev-item-position-default");
        INVENTORY_SETTINGS_NEXT_ITEM_POS_DEFAULT = configuration.getInt("inventory-settings.next-item-position-default");
        INVENTORY_SETTINGS_FOCUS_USING_DEFAULT_ITEM_POS = configuration.getBoolean("inventory-settings.focus-using-default-item-position");
        INVENTORY_SETTINGS_USE_SAVED_CURRENT_PAGE = configuration.getBoolean("inventory-settings.use-saved-current-page");
        INVENTORY_SETTINGS_KEEP_INVENTORY = configuration.getBoolean("inventory-settings.keep-inventory");
        INVENTORY_SETTINGS_KEEP_LEVEL = configuration.getBoolean("inventory-settings.keep-level");
        INVENTORY_SETTINGS_USE_CREATIVE_INVENTORY = configuration.getBoolean("inventory-settings.use-creative-inventory");
        PAGE_PERMISSIONS_DEFAULT_MAX_PAGE = configuration.getInt("page-permissions.default-max-page");
        PAGE_PERMISSIONS_UNLIMITED_LABEL = configuration.getString("page-permissions.unlimited-label");
        ADVANCED_PICK_UP_SETTINGS_ENABLED = configuration.getBoolean("advanced-pick-up-settings.enabled");
        ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_ENABLED = configuration.getBoolean("advanced-pick-up-settings.action-bar.enabled");
        ADVANCED_PICK_UP_SETTINGS_ACTIONBAR_TEXT = configuration.getString("advanced-pick-up-settings.action-bar.text");
        ADVANCED_PICK_UP_SETTINGS_SOUND_ENABLED = configuration.getBoolean("advanced-pick-up-settings.sound.enabled");
        ADVANCED_PICK_UP_SETTINGS_SOUND_NAME = configuration.getString("advanced-pick-up-settings.sound.name");
        ADVANCED_PICK_UP_SETTINGS_SOUND_VOLUME = configuration.getDouble("advanced-pick-up-settings.sound.volume");
        ADVANCED_PICK_UP_SETTINGS_SOUND_PITCH = configuration.getDouble("advanced-pick-up-settings.sound.pitch");
        for (String skipSlotNumber : configuration.getStringList("inventory-settings.skip-slots")) {
            try {
                SKIP_SLOTS.add(Integer.parseInt(skipSlotNumber));
            } catch (Exception exception) {
                DebugManager.debug("SETTINGS", "(inventory-settings.skip-slots) Skipped " + skipSlotNumber + " because it is not an integer!");
            }
        }
        PROTECTION_ENABLED = configuration.getBoolean("protection.enabled", true);
        PROTECTION_PIN_MIN_LENGTH = configuration.getInt("protection.pin.min-length", 4);
        PROTECTION_PIN_MAX_LENGTH = configuration.getInt("protection.pin.max-length", 6);
        PROTECTION_MAX_ATTEMPTS = configuration.getInt("protection.max-attempts", 5);
        PROTECTION_BAN_SECONDS = configuration.getLong("protection.ban-seconds", 300L);
        // Reassign so a reload does not accumulate stale entries; normalize to lowercase root command names.
        PROTECTION_COMMAND_BLACKLIST = new ArrayList<>();
        for (String entry : configuration.getStringList("protection.command-blacklist")) {
            String command = ProtectionManager.normalizeCommand(entry);
            if (!command.isEmpty())
                PROTECTION_COMMAND_BLACKLIST.add(command);
        }
        DebugManager.debug("SETTINGS", "Loaded settings from config yaml.");
    }

}
