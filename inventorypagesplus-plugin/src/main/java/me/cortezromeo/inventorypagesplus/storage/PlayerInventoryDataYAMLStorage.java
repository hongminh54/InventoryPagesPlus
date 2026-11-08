package me.cortezromeo.inventorypagesplus.storage;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.FileUtil;
import me.cortezromeo.inventorypagesplus.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInventoryDataYAMLStorage implements PlayerInventoryStorage {

    private File getFile(String playerName, String playerUUID) {
        String path = InventoryPagesPlus.plugin.getDataFolder() + "/database/" + playerName.length() + "/";
        if (!new File(path).exists())
            new File(path).mkdir();

        File file = new File(path + playerUUID + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    public PlayerInventoryDatabase fromFile(File file, String playerName, String playerUUID) {
        YamlConfiguration storage = YamlConfiguration.loadConfiguration(file);

        HashMap<Integer, ArrayList<ItemStack>> pageItemHashMap = new HashMap<>();
        int maxPageDefault = Settings.INVENTORY_SETTINGS_MAX_PAGE_DEFAULT;
        if (maxPageDefault < 0)
            maxPageDefault = 0;

        PlayerInventoryDatabase data = new PlayerInventory(Bukkit.getPlayer(playerName), playerName, playerUUID, maxPageDefault, null, null, PlayerPageInventory.prevItem, PlayerPageInventory.prevPos, PlayerPageInventory.nextItem, PlayerPageInventory.nextPos, PlayerPageInventory.noPageItem);

        if (storage.getString("name") == null) {
            return data;
        } else {
            // Load protection first so setMaxPage's save/show side effects honor the locked state.
            data.setProtectionPinHash(storage.getString("protection.pin"));
            data.setProtectionAttempts(storage.getInt("protection.attempts"));
            data.setProtectionBanUntil(storage.getLong("protection.ban-until"));

            int maxPage = storage.getInt("maxPage");
            data.setMaxPage(maxPage);
            data.setPlayerName(playerName);
            data.setPlayerUUID(playerUUID);
            // load survival items
            ConfigurationSection mainSection = storage.getConfigurationSection("items.main");
            if (mainSection != null) {
                // Iterate the stored pages, not 0..maxPage, so pages above the stored limit survive a restart.
                for (String pageKey : mainSection.getKeys(false)) {
                    int page;
                    try {
                        page = Integer.parseInt(pageKey);
                    } catch (NumberFormatException exception) {
                        continue;
                    }
                    ArrayList<ItemStack> pageItems = new ArrayList<>(25);
                    boolean emptyPage = true;
                    for (int slotNumber = 0; slotNumber < 25; slotNumber++) {
                        ItemStack itemStack = StringUtil.itemStackFromBase64(storage.getString("items.main." + page + "." + slotNumber));
                        if (itemStack != null)
                            emptyPage = false;
                        pageItems.add(itemStack);
                    }
                    if (!emptyPage)
                        pageItemHashMap.put(page, pageItems);
                }
            }
            data.setItems(pageItemHashMap);

            // load creative items
            if (storage.contains("items.creative.0")) {
                ArrayList<ItemStack> creativeItems = new ArrayList<>();
                for (int i = 0; i < 27; i++)
                    creativeItems.add(StringUtil.itemStackFromBase64(storage.getString("items.creative.0." + i)));
                data.setCreativeItems(creativeItems);
            }

            if (Settings.INVENTORY_SETTINGS_USE_SAVED_CURRENT_PAGE)
                data.setCurrentPage(storage.getInt("currentPage"));

            if (!Settings.INVENTORY_SETTINGS_FOCUS_USING_DEFAULT_ITEM_POS) {
                data.setPrevItemPos(storage.getInt("prevItemPos"));
                data.setNextItemPos(storage.getInt("nextItemPos"));
            }
        }
        return data;
    }

    @Override
    public String getUUIDFromData(String playerName, boolean naturalCheck) {
        if (naturalCheck) {
            if (Bukkit.getPlayer(playerName) != null) {
                return Bukkit.getPlayer(playerName).getUniqueId().toString();
            }
            // If server is in offline mode then can use this way to get player's UUID
            if (!Bukkit.getServer().getOnlineMode()) {
                String offlinePlayerString = "OfflinePlayer:" + playerName;
                return UUID.nameUUIDFromBytes(offlinePlayerString.getBytes(StandardCharsets.UTF_8)).toString();
            }
        }
        if (InventoryPagesPlus.getDatabaseManager().getTempPlayerUUID().containsKey(playerName))
            return InventoryPagesPlus.getDatabaseManager().getTempPlayerUUID().get(playerName);

        String path = InventoryPagesPlus.plugin.getDataFolder() + "/database/" + playerName.length() + "/";
        File playerDatabaseFolder = new File(path);
        if (playerDatabaseFolder.exists()) {
            File[] listUUIDFile = playerDatabaseFolder.listFiles();
            if (listUUIDFile != null) {
                for (File UUIDFile : listUUIDFile) {
                    YamlConfiguration storage = YamlConfiguration.loadConfiguration(UUIDFile);
                    if (storage.getString("name") != null) {
                        if (storage.getString("name").equals(playerName)) {
                            return FileUtil.removeExtension(UUIDFile.getName());
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean hasDataPlayerName(String playerName) {
        String path = InventoryPagesPlus.plugin.getDataFolder() + "/database/" + playerName.length() + "/";
        File playerDatabaseFolder = new File(path);
        if (playerDatabaseFolder.exists()) {
            File[] listUUIDFile = playerDatabaseFolder.listFiles();
            if (listUUIDFile != null) {
                for (File UUIDFile : listUUIDFile) {
                    YamlConfiguration storage = YamlConfiguration.loadConfiguration(UUIDFile);
                    if (storage.getString("name") != null) {
                        if (storage.getString("name").equals(playerName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public PlayerInventoryDatabase getData(String playerName) {
        String playerUUID = getUUIDFromData(playerName, true);
        File file = getFile(playerName, playerUUID);
        return fromFile(file, playerName, playerUUID);
    }

    @Override
    public void disable() {
        // do nothing
    }

    @Override
    public void saveData(PlayerInventoryDatabase data) {
        File playerFile = getFile(data.getPlayerName(), data.getPlayerUUID());
        FileConfiguration playerDataCfg = YamlConfiguration.loadConfiguration(playerFile);
        PlayerInventoryDatabase playerInventoryData = InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(UUID.fromString(data.getPlayerUUID()));

        playerDataCfg.set("name", playerInventoryData.getPlayerName());
        playerDataCfg.set("uuid", playerInventoryData.getPlayerUUID());
        playerDataCfg.set("maxPage", playerInventoryData.getMaxPage());
        playerDataCfg.set("currentPage", playerInventoryData.getCurrentPage());
        playerDataCfg.set("prevItemPos", playerInventoryData.getPrevItemPos());
        playerDataCfg.set("nextItemPos", playerInventoryData.getNextItemPos());

        playerDataCfg.set("protection.pin", playerInventoryData.getProtectionPinHash());
        playerDataCfg.set("protection.attempts", playerInventoryData.getProtectionAttempts());
        playerDataCfg.set("protection.ban-until", playerInventoryData.getProtectionBanUntil());

        // save survival items
        for (Map.Entry<Integer, ArrayList<ItemStack>> pageItemEntry : playerInventoryData.getItems().entrySet()) {
            for (int slotNumber = 0; slotNumber < pageItemEntry.getValue().size(); slotNumber++) {
                int pageNumber = pageItemEntry.getKey();
                if (pageItemEntry.getValue().get(slotNumber) != null) {
                    playerDataCfg.set("items.main." + pageNumber + "." + slotNumber, StringUtil.toBase64(InventoryPagesPlus.nms.getItemStack(pageItemEntry.getValue().get(slotNumber))));
                } else {
                    playerDataCfg.set("items.main." + pageNumber + "." + slotNumber, null);
                }
            }
        }

        // save creative items
        if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(UUID.fromString(data.getPlayerUUID())).hasUsedCreative()) {
            for (int slotNumber = 0; slotNumber < 27; slotNumber++) {
                if (InventoryPagesPlus.getDatabaseManager().getPlayerInventoryDatabase(UUID.fromString(data.getPlayerUUID())).getCreativeItems().get(slotNumber) != null) {
                    playerDataCfg.set("items.creative.0." + slotNumber, StringUtil.toBase64(InventoryPagesPlus.nms.getItemStack(playerInventoryData.getCreativeItems().get(slotNumber))));
                } else {
                    playerDataCfg.set("items.creative.0." + slotNumber, null);
                }
            }
        }

        try {
            playerDataCfg.save(playerFile);
            DebugManager.debug("SAVING INV. FROM HASHMAP TO FILE PLAYER (" + data.getPlayerName() + ")", "Completed with no issues.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
