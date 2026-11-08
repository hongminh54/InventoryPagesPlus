package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataH2Storage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataMySQLStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private final String backupPath = InventoryPagesPlus.plugin.getDataFolder() + "\\backup\\";
    private String backupFileName;

    public BackupManager() {
    }

    public String getBackupFileName() {
        return backupFileName;
    }

    public void backupAll() {
        DatabaseType databaseType = InventoryPagesPlus.databaseType;
        DebugManager.debug("BACKUP (DATABASE: " + databaseType + ")", "Start creating backup files (for all database).");

        // Save database before backing up
        InventoryPagesPlus.getDatabaseManager().updateAndSaveAllInventoriesToDatabase();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(Settings.BACKUP_FILE_NAME_DATE_FORMAT);
        Date date = new Date();
        backupFileName = simpleDateFormat.format(date) + " (" + databaseType.toString().toLowerCase() + ")";
        String zipFileName = backupPath + backupFileName + ".zip";

        if (InventoryPagesPlus.databaseType == DatabaseType.YAML) {
            Path databaseFolder = Paths.get(InventoryPagesPlus.plugin.getDataFolder() + "\\database");
            Path zipPath = Paths.get(zipFileName);
            try {
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
                Files.walkFileTree(databaseFolder, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        zos.putNextEntry(new ZipEntry(databaseFolder.relativize(file).toString()));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
                zos.close();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        } else if (InventoryPagesPlus.databaseType == DatabaseType.H2) {
            if (PlayerInventoryDataH2Storage.getConnection() == null) {
                DebugManager.debug("BACKUP (DATABASE: " + databaseType + ")", "Cannot backup because H2 Database connection is not valid!");
                return;
            }
            try {
                Statement statement = PlayerInventoryDataH2Storage.getConnection().createStatement();
                String sql = "BACKUP TO '" + zipFileName + "'";
                statement.executeUpdate(sql);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        } else if (InventoryPagesPlus.databaseType == DatabaseType.MYSQL) {
            if (PlayerInventoryDataMySQLStorage.getConnection() == null) {
                DebugManager.debug("BACKUP (DATABASE: " + databaseType + ")", "Cannot backup because MySQL Database connection is not valid!");
                return;
            }
            try {
                Statement statement = PlayerInventoryDataMySQLStorage.getConnection().createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
                        ResultSet.CONCUR_UPDATABLE);

                String databaseName = PlayerInventoryDataStorage.mySQLTableName;
                String tableName = PlayerInventoryDataStorage.mySQLTableName;

                String executionPath = System.getProperty("user.dir").replace("\\", "/");
                executionPath = executionPath + "/plugins/" + InventoryPagesPlus.plugin.getDescription().getName() + "/backup/" + databaseName + ".csv";
                File file = new File(backupPath + databaseName + ".csv");
                if (file.exists())
                    if (file.delete())
                        DebugManager.debug("BACKUP (DATABASE: " + databaseType + ")", "Deleted old backup csv file.");
                String query = "SELECT * FROM " + tableName + " INTO OUTFILE '" + executionPath + "' FIELDS TERMINATED BY ','";
                statement.executeQuery(query);

                try {
                    FileOutputStream fos = new FileOutputStream(zipFileName);
                    ZipOutputStream zos = new ZipOutputStream(fos);

                    zos.putNextEntry(new ZipEntry(file.getName()));

                    byte[] bytes = Files.readAllBytes(Paths.get(backupPath + databaseName + ".csv"));
                    zos.write(bytes, 0, bytes.length);
                    zos.closeEntry();
                    zos.close();
                    file.delete();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        DebugManager.debug("BACKUP (DATABASE: " + databaseType + ")", "Created backup files (for all database).");
    }

}
