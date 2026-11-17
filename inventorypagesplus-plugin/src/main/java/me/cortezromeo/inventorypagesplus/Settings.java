package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.history.HistoryDates;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.manager.ProtectionManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Settings {

    public static final String HISTORY_PERMISSION = "inventorypagesplus.history";
    public static final String HISTORY_PERMISSION_OTHERS = "inventorypagesplus.history.others";
    public static final String HISTORY_PERMISSION_SAVE = "inventorypagesplus.history.save";
    public static final String HISTORY_PERMISSION_RESTORE = "inventorypagesplus.history.restore";
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
    public static boolean HISTORY_ENABLED;
    public static String HISTORY_SERVER_ID;
    public static int HISTORY_SNAPSHOT_INTERVAL_SECONDS;
    public static int HISTORY_SNAPSHOT_PLAYERS_PER_TICK;
    public static boolean HISTORY_SNAPSHOT_ON_JOIN;
    public static boolean HISTORY_SNAPSHOT_ON_QUIT;
    public static boolean HISTORY_SNAPSHOT_ON_DISABLE;
    public static boolean HISTORY_SNAPSHOT_BEFORE_CLEAR;
    public static int HISTORY_RETENTION_MONTHS = 3;
    public static boolean HISTORY_RETENTION_ARCHIVE = true;
    public static int HISTORY_RETENTION_BATCH_SIZE = 50;
    public static int HISTORY_RETENTION_INTERVAL_SECONDS = 60;
    public static int HISTORY_ARCHIVE_KEEP_MONTHS = 12;
    public static int HISTORY_MAX_PENDING = 64;
    public static int HISTORY_MAX_RECORD_BYTES = 8 * 1024 * 1024;
    public static String HISTORY_DATE_FORMAT = "dd-MM-yyyy";
    public static DateTimeFormatter HISTORY_DATE_FORMATTER = HistoryDates.formatter(HISTORY_DATE_FORMAT);
    public static ZoneId HISTORY_ZONE = ZoneId.systemDefault();
    public static boolean HISTORY_RESTORE_ENABLED;
    public static boolean HISTORY_EXTRACTION_ENABLED;
    public static String DATABASE_SETTINGS_H2_HISTORY_TABLE;
    public static String DATABASE_SETTINGS_MYSQL_HISTORY_TABLE;

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
        HISTORY_ENABLED = configuration.getBoolean("history.enabled", true);
        String serverId = configuration.getString("history.server-id", "");
        HISTORY_SERVER_ID = serverId == null ? "" : serverId.trim();
        HISTORY_SNAPSHOT_INTERVAL_SECONDS = Math.max(0, configuration.getInt("history.snapshot.interval-seconds", 3600));
        HISTORY_SNAPSHOT_PLAYERS_PER_TICK = Math.max(1, configuration.getInt("history.snapshot.players-per-tick", 1));
        HISTORY_SNAPSHOT_ON_JOIN = configuration.getBoolean("history.snapshot.on-join", true);
        HISTORY_SNAPSHOT_ON_QUIT = configuration.getBoolean("history.snapshot.on-quit", true);
        HISTORY_SNAPSHOT_ON_DISABLE = configuration.getBoolean("history.snapshot.on-disable", true);
        HISTORY_SNAPSHOT_BEFORE_CLEAR = configuration.getBoolean("history.snapshot.before-clear", true);
        HISTORY_RETENTION_MONTHS = Math.max(1, Math.min(3, configuration.getInt("history.retention.months", 3)));
        String retentionAction = configuration.getString("history.retention.action", "ZIP");
        HISTORY_RETENTION_ARCHIVE = !"DELETE".equalsIgnoreCase(retentionAction);
        if (!"ZIP".equalsIgnoreCase(retentionAction) && !"DELETE".equalsIgnoreCase(retentionAction))
            InventoryPagesPlus.plugin.getLogger().warning("Invalid history.retention.action; using ZIP.");
        HISTORY_RETENTION_BATCH_SIZE = Math.max(1, Math.min(200, configuration.getInt("history.retention.batch-size", 50)));
        HISTORY_RETENTION_INTERVAL_SECONDS = Math.max(10, configuration.getInt("history.retention.interval-seconds", 60));
        HISTORY_ARCHIVE_KEEP_MONTHS = Math.max(0, configuration.getInt("history.retention.archive-keep-months", 12));
        HISTORY_MAX_PENDING = Math.max(16, Math.min(1024, configuration.getInt("history.performance.max-pending-tasks", 64)));
        HISTORY_MAX_RECORD_BYTES = Math.max(1, Math.min(32, configuration.getInt("history.performance.max-record-megabytes", 8))) * 1024 * 1024;
        try {
            HISTORY_DATE_FORMAT = configuration.getString("history.date-format", "dd-MM-yyyy");
            HISTORY_DATE_FORMATTER = HistoryDates.formatter(HISTORY_DATE_FORMAT);
        } catch (RuntimeException exception) {
            HISTORY_DATE_FORMAT = "dd-MM-yyyy";
            HISTORY_DATE_FORMATTER = HistoryDates.formatter(HISTORY_DATE_FORMAT);
            InventoryPagesPlus.plugin.getLogger().warning("Invalid history.date-format; using dd-MM-yyyy.");
        }
        try {
            HISTORY_ZONE = ZoneId.of(configuration.getString("history.time-zone", ZoneId.systemDefault().getId()));
        } catch (RuntimeException exception) {
            HISTORY_ZONE = ZoneId.systemDefault();
            InventoryPagesPlus.plugin.getLogger().warning("Invalid history.time-zone; using " + HISTORY_ZONE);
        }
        HISTORY_RESTORE_ENABLED = configuration.getBoolean("history.restore.enabled", false);
        HISTORY_EXTRACTION_ENABLED = configuration.getBoolean("history.extraction.enabled", true);
        DATABASE_SETTINGS_H2_HISTORY_TABLE = configuration.getString("database.settings.h2.history-table", "inventoryhistory");
        DATABASE_SETTINGS_MYSQL_HISTORY_TABLE = configuration.getString("database.settings.mysql.database.history-table", "inventoryhistory");
        DebugManager.debug("SETTINGS", "Loaded settings from config yaml.");
    }

}
