package me.cortezromeo.inventorypagesplus.file.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class HistoryInventoryFile {
    private static File file;
    private static FileConfiguration fileConfiguration;

    public static void setup() {
        file = new File(InventoryPagesPlus.plugin.getDataFolder() + "/inventories/historyinventory.yml");
        File directory = file.getParentFile();
        if (!directory.exists() && !directory.mkdirs())
            InventoryPagesPlus.plugin.getLogger().warning("Could not create inventory configuration directory.");
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
        if (file.exists())
            migrateLayout();
    }

    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void saveDefault() {
        if (file.exists())
            return;
        try {
            // saveResource extracts at the data-folder root, not under inventories.
            File resource = new File(InventoryPagesPlus.plugin.getDataFolder(), "historyinventory.yml");
            if (!resource.exists())
                InventoryPagesPlus.plugin.saveResource("historyinventory.yml", false);
            Files.move(resource.toPath(), file.toPath());
            reload();
        } catch (IOException | IllegalArgumentException exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Could not save historyinventory.yml: " + exception.getMessage());
        }
    }

    public static void reload() {
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
        migrateLayout();
    }

    private static void migrateLayout() {
        if (!fileConfiguration.contains("items.prevSnapshot") && !fileConfiguration.contains("items.nextSnapshot"))
            return;
        try (InputStreamReader reader = new InputStreamReader(
                InventoryPagesPlus.plugin.getResource("historyinventory.yml"), StandardCharsets.UTF_8)) {
            File backup = new File(file.getParentFile(), "historyinventory.pre-daily.yml.bak");
            if (!backup.exists())
                Files.copy(file.toPath(), backup.toPath());
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean oldLayout = fileConfiguration.getInt("items.close.slot", -1) == 49;
            for (String key : List.of("prevDay", "nextDay", "prevChange", "nextChange", "info", "restore", "noHistory", "disabled"))
                fileConfiguration.set("items." + key, defaults.getConfigurationSection("items." + key).getValues(false));
            if (oldLayout) {
                for (String key : List.of("close", "prevPage", "nextPage"))
                    fileConfiguration.set("items." + key + ".slot", defaults.getInt("items." + key + ".slot"));
            }
            fileConfiguration.set("title", defaults.getString("title"));
            fileConfiguration.set("items.border.slots", defaults.getIntegerList("items.border.slots"));
            fileConfiguration.set("items.prevSnapshot", null);
            fileConfiguration.set("items.nextSnapshot", null);
            fileConfiguration.set("items.noSnapshot", null);
            fileConfiguration.save(file);
            InventoryPagesPlus.plugin.getLogger().info("Migrated history viewer to daily navigation; previous configuration backed up.");
        } catch (IOException exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Could not migrate historyinventory.yml: " + exception.getMessage());
        }
    }
}
