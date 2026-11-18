package me.cortezromeo.inventorypagesplus.file.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class AutoFixAddInventoryFile {

    private static File file;
    private static FileConfiguration fileConfiguration;

    public static void setup() {
        file = new File(InventoryPagesPlus.plugin.getDataFolder() + "/inventories/autofixaddinventory.yml");
        File directory = file.getParentFile();
        if (!directory.exists() && !directory.mkdirs())
            InventoryPagesPlus.plugin.getLogger().warning("Could not create inventory configuration directory.");
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void saveDefault() {
        if (file.exists())
            return;
        try {
            // saveResource extracts at the data-folder root, not under inventories.
            File resource = new File(InventoryPagesPlus.plugin.getDataFolder(), "autofixaddinventory.yml");
            if (!resource.exists())
                InventoryPagesPlus.plugin.saveResource("autofixaddinventory.yml", false);
            Files.move(resource.toPath(), file.toPath());
            reload();
        } catch (IOException | IllegalArgumentException exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Could not save autofixaddinventory.yml: " + exception.getMessage());
        }
    }

    public static void reload() {
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }
}