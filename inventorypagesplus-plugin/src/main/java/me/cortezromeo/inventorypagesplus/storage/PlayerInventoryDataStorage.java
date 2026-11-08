package me.cortezromeo.inventorypagesplus.storage;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;

import java.io.File;

public class PlayerInventoryDataStorage {

    public static String mySQLDatabaseName;
    public static String mySQLTableName;
    public static String mySQLUserName;
    public static String mySQLUserPassword;
    private static PlayerInventoryStorage STORAGE;

    public static void init(DatabaseType databaseType) {
        File file = new File(InventoryPagesPlus.plugin.getDataFolder() + "/database/");
        if (!file.exists()) {
            file.mkdirs();
        }

        if (databaseType == DatabaseType.YAML) {
            STORAGE = new PlayerInventoryDataYAMLStorage();
        } else if (databaseType == DatabaseType.H2) {
            STORAGE = new PlayerInventoryDataH2Storage(Settings.DATABASE_SETTINGS_H2_FILE_NAME, Settings.DATABASE_SETTINGS_H2_TABLE);
        } else if (databaseType == DatabaseType.MYSQL) {
            String host = Settings.DATABASE_SETTINGS_MYSQL_HOST;
            String port = Settings.DATABASE_SETTINGS_MYSQL_PORT;
            mySQLDatabaseName = Settings.DATABASE_SETTINGS_MYSQL_NAME;
            mySQLTableName = Settings.DATABASE_SETTINGS_MYSQL_TABLE;
            mySQLUserName = Settings.DATABASE_SETTINGS_MYSQL_USER;
            mySQLUserPassword = Settings.DATABASE_SETTINGS_MYSQL_PASSWORD;
            try {
                STORAGE = new PlayerInventoryDataMySQLStorage(host, port, mySQLDatabaseName, mySQLTableName, mySQLUserName, mySQLUserPassword);
            } catch (Exception exception) {
                exception.printStackTrace();
                STORAGE = new PlayerInventoryDataYAMLStorage();
                InventoryPagesPlus.databaseType = DatabaseType.H2;
                MessageUtil.log("&c--------------------------------------");
                MessageUtil.log("    &4ERROR");
                MessageUtil.log("&eCannot connect to MySQL database!");
                MessageUtil.log("&ePlease check the error messages provided above for the information.");
                MessageUtil.log("&eDatabase will automatically use &b&lH2 &eto load.");
                MessageUtil.log("&c--------------------------------------");
            }
        }
        DebugManager.debug("LOADING DATABASE", "Loaded " + InventoryPagesPlus.databaseType + " database.");
    }

    public static boolean hasData(String playerName) {
        return PlayerInventoryDataStorage.STORAGE.hasDataPlayerName(playerName);
    }

    public static String getPlayerUUIDFromData(String playerName, boolean naturalCheck) {
        return PlayerInventoryDataStorage.STORAGE.getUUIDFromData(playerName, naturalCheck);
    }

    public static PlayerInventoryDatabase getPlayerInventoryData(String playerName) {
        return PlayerInventoryDataStorage.STORAGE.getData(playerName);
    }

    public static void savePlayerInventoryData(PlayerInventoryDatabase data) {
        PlayerInventoryDataStorage.STORAGE.saveData(data);
    }

    public static void disable() {
        PlayerInventoryDataStorage.STORAGE.disable();
    }

}
