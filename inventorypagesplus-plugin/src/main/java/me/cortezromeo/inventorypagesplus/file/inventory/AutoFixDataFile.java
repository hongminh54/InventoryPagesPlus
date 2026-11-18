package me.cortezromeo.inventorypagesplus.file.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class AutoFixDataFile {

    private static File file;
    private static FileConfiguration fileConfiguration;

    public static void setup() {
        file = new File(InventoryPagesPlus.plugin.getDataFolder(), Settings.AUTOFIX_DATA_FILE);
        if (!file.exists()) {
            try {
                File directory = file.getParentFile();
                if (directory != null && !directory.exists() && !directory.mkdirs())
                    InventoryPagesPlus.plugin.getLogger().warning("Could not create the autofix data directory.");
                if (!file.createNewFile())
                    InventoryPagesPlus.plugin.getLogger().warning("Could not create " + Settings.AUTOFIX_DATA_FILE);
            } catch (IOException exception) {
                InventoryPagesPlus.plugin.getLogger().warning("Could not create " + Settings.AUTOFIX_DATA_FILE + ": " + exception.getMessage());
            }
        }
        reload();
        boolean changed = false;
        if (!fileConfiguration.contains("NameList")) {
            fileConfiguration.set("NameList", new ArrayList<String>());
            changed = true;
        }
        if (changed)
            save();
    }

    public static FileConfiguration get() {
        return fileConfiguration;
    }

    public static void reload() {
        fileConfiguration = YamlConfiguration.loadConfiguration(file);
        // Rule keys are "stripped name-MATERIAL" and may contain dots, so every path is read literally.
        fileConfiguration.options().pathSeparator((char) 0);
    }

    public static void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Could not save " + Settings.AUTOFIX_DATA_FILE + ": " + exception.getMessage());
        }
    }
}