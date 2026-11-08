package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDataStorage;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager implements InventoryPagesPlusDataManager {

    public DatabaseManager() {}

    private ConcurrentHashMap<String, PlayerInventoryDatabase> playerInventoryDatabase = new ConcurrentHashMap<>();
    private HashMap<String, String> tempPlayerUUID = new HashMap<>(); // mainly use for loading old database which already was created
    public static File crashedFile = new File(InventoryPagesPlus.plugin.getDataFolder() + "/database/crashed.yml");
    public static FileConfiguration crashedData = YamlConfiguration.loadConfiguration(crashedFile);

    @Override
    public ConcurrentHashMap<String, PlayerInventoryDatabase> getPlayerInventoryDatabase() {
        return playerInventoryDatabase;
    }

    @Override
    public PlayerInventoryDatabase getPlayerInventoryDatabase(String playerName) {
        String playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);
        if (playerUUID == null)
            return null;

        return playerInventoryDatabase.get(playerUUID);
    }

    @Override
    public PlayerInventoryDatabase getPlayerInventoryDatabase(String playerName, boolean loadEmptyDatabase) {
        String playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);
        if (playerUUID == null)
            return null;

        if (loadEmptyDatabase && !playerInventoryDatabase.containsKey(playerUUID))
            loadPlayerInventory(playerName);

        return playerInventoryDatabase.get(playerUUID);
    }

    @Override
    public PlayerInventoryDatabase getPlayerInventoryDatabase(UUID UUID) {
        return playerInventoryDatabase.get(UUID.toString());
    }

    @Override
    public void loadPlayerInventory(String playerName) {
        long time = System.currentTimeMillis();
        String playerUUID;
        if (tempPlayerUUID.containsKey(playerName))
            playerUUID = tempPlayerUUID.get(playerName);
        else
            playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);

        if (playerUUID == null) {
            DebugManager.debug("LOADING DATABASE PLAYER (" + playerName + ")", "Canceled because cannot get the UUID.");
            return;
        }

        tempPlayerUUID.put(playerName, playerUUID);
        PlayerInventoryDatabase data = PlayerInventoryDataStorage.getPlayerInventoryData(playerName);
        playerInventoryDatabase.put(playerUUID, data);
        Player player = Bukkit.getPlayer(playerName);
        // Recover leftover display items only when unlocked; a locked player's slots 9..35 hold their real items.
        if (player != null && !data.isProtectionLocked())
            data.recoverDisplayItems();
        clearAndRemoveCrashedPlayer(playerName);
        if (player != null) {
            addCrashedPlayer(playerUUID);
            if (data.isProtectionLocked()) {
                // Leave the real items in slots 9..35 intact and frozen; never wipe them on join.
                MessageUtil.sendMessage(player, Messages.COMMAND_PROTECTION_LOCKED_REMINDER);
            } else {
                data.showPage(player.getGameMode());
            }
        }
        DebugManager.debug("LOADING DATABASE PLAYER (" + playerName + ")", "Completed with no issues. &b&l(" + (System.currentTimeMillis() - time + "ms)"));
    }

    @Override
    public HashMap<String, String> getTempPlayerUUID() {
        return tempPlayerUUID;
    }

    @Override
    public void updateAndSaveAllInventoriesToDatabase() {
        long time = System.currentTimeMillis();
        if (!Bukkit.getServer().getOnlinePlayers().isEmpty()) {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                String playerUUID = player.getUniqueId().toString();
                if (playerInventoryDatabase.containsKey(playerUUID)) {
                    savePlayerInventory(player.getName());
                }
            }
            DebugManager.debug("UPDATING AND SAVING ALL INVENTORIES", "Completed with no issues. &b&l(" + (System.currentTimeMillis() - time + "ms)"));
        }
    }

    @Override
    public void clearAndRemoveCrashedPlayer(String playerName) {
        long time = System.currentTimeMillis();
        if (crashedPlayersExist()) {
            String playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);
            if (hasCrashed(playerUUID) && Bukkit.getPlayer(playerName) != null) {
                Player player = Bukkit.getPlayer(playerName);
                PlayerInventoryDatabase data = playerInventoryDatabase.get(playerUUID);
                // A locked player's slots 9..35 hold their real items, not a displayed page, so never wipe them.
                if (data == null || !data.isProtectionLocked()) {
                    for (int i = 0; i < 27; i++) {
                        // This slot can not be loaded because it is considered as a skip slot
                        if (!Settings.SKIP_SLOTS.isEmpty())
                            if (Settings.SKIP_SLOTS.contains(i))
                                continue;
                        player.getInventory().setItem(i + 9, null);
                    }
                }
                crashedData.set("crashed." + player.getUniqueId().toString(), null);
                saveCrashedFile();
                DebugManager.debug("CLEARING CRASHED PLAYER (" + player.getName() + ")", "Completed with no issues. &b&l(" + (System.currentTimeMillis() - time + "ms)"));
            }
        }
    }

    @Override
    public void savePlayerInventory(String playerName) {
        DebugManager.debug("SAVING DATABASE PLAYER (" + playerName + ")", "Saving player's database...");
        long time = System.currentTimeMillis();
        saveCurrentPage(playerName);
        PlayerInventoryDataStorage.savePlayerInventoryData(playerInventoryDatabase.get(PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true)));
        DebugManager.debug("SAVING DATABASE PLAYER (" + playerName + ")", "Completed with no issues. &b&l(" + (System.currentTimeMillis() - time + "ms)"));
    }

    @Override
    public void saveCurrentPage(String playerName) {
        long time = System.currentTimeMillis();
        String playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);
        if (playerInventoryDatabase.containsKey(playerUUID)) {
            playerInventoryDatabase.get(playerUUID).saveCurrentPage();
        }
    }

    @Override
    public void saveCurrentPage(UUID UUID) {
        if (playerInventoryDatabase.containsKey(UUID.toString())) {
            playerInventoryDatabase.get(UUID.toString()).saveCurrentPage();
        }
    }

    @Override
    public void removeInvFromHashMap(String playerName) {
        String playerUUID = PlayerInventoryDataStorage.getPlayerUUIDFromData(playerName, true);
        if (playerInventoryDatabase.containsKey(playerUUID)) {
            playerInventoryDatabase.remove(playerUUID);
            clearAndRemoveCrashedPlayer(playerName);
            DebugManager.debug("REMOVING INV. FROM HASHMAP PLAYER (" + playerName + ")", "Completed with no issues.");
        }
    }

    public void saveCrashedFile() {
        try {
            crashedData.save(crashedFile);
            DebugManager.debug("SAVING CRASHED FILE", "Completed with no issues.");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public Boolean crashedPlayersExist() {
        if (crashedData.contains("crashed")) {
            return !crashedData.getConfigurationSection("crashed").getKeys(false).isEmpty();
        }
        return false;
    }

    public Boolean hasCrashed(String UUID) {
        return crashedData.contains("crashed." + UUID);
    }

    public void addCrashedPlayer(String UUID) {
        crashedData.set("crashed." + UUID, true);
        saveCrashedFile();
        DebugManager.debug("ADDING CRASHED FILE PLAYER (" + UUID + ")", "Completed with no issues.");
    }

}
