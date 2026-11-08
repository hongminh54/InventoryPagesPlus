package me.cortezromeo.inventorypagesplus.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.h2.jdbc.JdbcConnection;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

public class PlayerInventoryDataH2Storage implements PlayerInventoryStorage {
    private static JdbcConnection connection;
    private static String table;

    public PlayerInventoryDataH2Storage(String fileName, String tableName) {
        table = tableName;
        try {
            if (connection != null)
                disable();

            connection = new JdbcConnection("jdbc:h2:./" + InventoryPagesPlus.plugin.getDataFolder() + "/database/" + fileName + ";mode=MySQL", new Properties(), null, null, false);
            connection.setAutoCommit(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            Statement statement = connection.createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS " + table + " " +
                    "(UUID VARCHAR(50) not NULL, " +
                    " PLAYERNAME VARCHAR(50), " +
                    " ITEMS TEXT, " +
                    " CREATIVEITEMS TEXT, " +
                    " MAXPAGE INT, " +
                    " PAGE INT, " +
                    " PREVITEMPOS VARCHAR(50), " +
                    " NEXTITEMPOS VARCHAR(50), " +
                    " PROTECTIONPIN VARCHAR(255), " +
                    " PROTECTIONATTEMPTS INT DEFAULT 0, " +
                    " PROTECTIONBANUNTIL BIGINT DEFAULT 0, " +
                    " PRIMARY KEY (UUID))";
            statement.executeUpdate(sql);
            // CREATE TABLE IF NOT EXISTS leaves old tables untouched, so add the protection columns idempotently.
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS PROTECTIONPIN VARCHAR(255)");
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS PROTECTIONATTEMPTS INT DEFAULT 0");
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS PROTECTIONBANUNTIL BIGINT DEFAULT 0");
            DebugManager.debug("LOADING DATABASE (H2)", "Connected to table " + table + ".");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JdbcConnection getConnection() {
        return connection;
    }

    public PlayerInventoryDatabase fromH2(String playerName, String playerUUID) {
        HashMap<Integer, ArrayList<ItemStack>> pageItemHashMap = new HashMap<>();
        int maxPageDefault = Settings.INVENTORY_SETTINGS_MAX_PAGE_DEFAULT;
        if (maxPageDefault < 0)
            maxPageDefault = 0;
        PlayerInventoryDatabase data = new PlayerInventory(Bukkit.getPlayer(playerName), playerName, playerUUID, maxPageDefault,null, null, PlayerPageInventory.prevItem, PlayerPageInventory.prevPos, PlayerPageInventory.nextItem, PlayerPageInventory.nextPos, PlayerPageInventory.noPageItem);

        if (!hasDataUUID(playerUUID))
            return data;

        String query = "select * from " + table + " where UUID=?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, playerUUID);
            ResultSet resultSet = ps.executeQuery();

            while (resultSet.next()) {
                // Load protection first so setMaxPage's save/show side effects honor the locked state.
                data.setProtectionPinHash(resultSet.getString("PROTECTIONPIN"));
                data.setProtectionAttempts(resultSet.getInt("PROTECTIONATTEMPTS"));
                data.setProtectionBanUntil(resultSet.getLong("PROTECTIONBANUNTIL"));

                int maxPage = resultSet.getInt("MAXPAGE");
                int currentPage = resultSet.getInt("PAGE");
                String creativeItemsString = resultSet.getString("CREATIVEITEMS");

                data.setMaxPage(maxPage);
                if (Settings.INVENTORY_SETTINGS_USE_SAVED_CURRENT_PAGE)
                    data.setCurrentPage(currentPage);

                // load survival items
                Gson gson = new Gson();
                HashMap<Integer, ArrayList<String>> pageItemsBase64 = gson.fromJson(resultSet.getString("ITEMS"), new TypeToken<HashMap<Integer, ArrayList<String>>>(){}.getType());
                // Iterate the stored pages, not 0..maxPage, so pages above the stored limit survive a restart.
                for (Integer page : pageItemsBase64.keySet()) {
                    if (pageItemsBase64.get(page) == null)
                        continue;
                    else {
                        ArrayList<ItemStack> pageItems = new ArrayList<>(25);
                        boolean emptyPage = true;
                        for (int slotNumber = 0; slotNumber < 25; slotNumber++) {
                            ItemStack itemStack = StringUtil.itemStackFromBase64(pageItemsBase64.get(page).get(slotNumber));
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
                if (creativeItemsString != null) {
                    ArrayList<String> creativeItemsBase64 = gson.fromJson(creativeItemsString, new TypeToken<ArrayList<String>>(){}.getType());
                    ArrayList<ItemStack> creativeItemsItemStack = new ArrayList<>();
                    for (String base64Item : creativeItemsBase64)
                        creativeItemsItemStack.add(StringUtil.itemStackFromBase64(base64Item));
                    data.setCreativeItems(creativeItemsItemStack);
                }

                if (!Settings.INVENTORY_SETTINGS_FOCUS_USING_DEFAULT_ITEM_POS) {
                    data.setPrevItemPos(resultSet.getInt("PREVITEMPOS"));
                    data.setNextItemPos(resultSet.getInt("NEXTITEMPOS"));
                }
            }
            data.setPlayerName(playerName);
            data.setPlayerUUID(playerUUID);
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
        return data;
    }

    @Override
    public boolean hasDataPlayerName(String playerName) {
        String query = "select * from " + table + " where PLAYERNAME=?";
        try (PreparedStatement ps = connection.prepareStatement(query)){
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }
            rs.close();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;
    }

    private static boolean hasDataUUID(String playerUUID) {
        String query = "select * from " + table + " where UUID=?";
        try (PreparedStatement ps = connection.prepareStatement(query)){
            ps.setString(1, playerUUID);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }
            rs.close();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;
    }

    private static void initData(String playerUUID) {
        ArrayList<String> queries = new ArrayList<>();
        queries.add("INSERT INTO " + table + " (UUID, PLAYERNAME, ITEMS, CREATIVEITEMS, MAXPAGE, PAGE, PREVITEMPOS, NEXTITEMPOS, PROTECTIONPIN, PROTECTIONATTEMPTS, PROTECTIONBANUNTIL) values('" + playerUUID + "', '', '', '', 0, 0, '', '', null, 0, 0)");
        queries.forEach(cmd -> {
            try (PreparedStatement ps = connection.prepareStatement(cmd)) {
                ps.execute();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public PlayerInventoryDatabase getData(String playerName) {
        return fromH2(playerName, getUUIDFromData(playerName, true));
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

        String UUID = null;
        String query = "select * from " + table + " where PLAYERNAME=?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UUID = rs.getString("UUID");
            }
            rs.close();
            return UUID;
        } catch ( Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void saveData(PlayerInventoryDatabase playerInventoryData) {
        if (!hasDataUUID(playerInventoryData.getPlayerUUID()))
            initData(playerInventoryData.getPlayerUUID());

        String query = "UPDATE " + table + " "
                + "SET PLAYERNAME = ?,"
                + " ITEMS = ?,"
                + " CREATIVEITEMS = ?,"
                + " MAXPAGE = ?,"
                + " PAGE = ?,"
                + " PREVITEMPOS = ?,"
                + " NEXTITEMPOS = ?,"
                + " PROTECTIONPIN = ?,"
                + " PROTECTIONATTEMPTS = ?,"
                + " PROTECTIONBANUNTIL = ?"
                + " WHERE UUID = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, playerInventoryData.getPlayerName());

            HashMap<Integer, ArrayList<String>> pageItemsBase64 = new HashMap<>();
            for (int page : playerInventoryData.getItems().keySet()) {
                ArrayList<String> itemsBase64 = new ArrayList<>();
                boolean emptyPage = true;
                for (ItemStack itemStack : playerInventoryData.getItems().get(page)) {
                    itemStack = InventoryPagesPlus.nms.getItemStack(itemStack);
                    if (itemStack != null)
                        emptyPage = false;
                    itemsBase64.add(StringUtil.toBase64(itemStack));
                }
                if (!emptyPage)
                    pageItemsBase64.put(page, itemsBase64);
            }

            Gson gson = new Gson();
            preparedStatement.setString(2, gson.toJson(pageItemsBase64));

            if (playerInventoryData.hasUsedCreative()) {
                ArrayList<String> creativeItemsBase64 = new ArrayList<>();
                for (ItemStack itemStack : playerInventoryData.getCreativeItems()) {
                    itemStack = InventoryPagesPlus.nms.getItemStack(itemStack);
                    creativeItemsBase64.add(StringUtil.toBase64(itemStack));
                }
                preparedStatement.setString(3, gson.toJson(creativeItemsBase64));
            } else
                preparedStatement.setString(3, null);
            preparedStatement.setInt(4, playerInventoryData.getMaxPage());
            preparedStatement.setInt(5, playerInventoryData.getCurrentPage());
            preparedStatement.setInt(6, playerInventoryData.getPrevItemPos());
            preparedStatement.setInt(7, playerInventoryData.getNextItemPos());
            preparedStatement.setString(8, playerInventoryData.getProtectionPinHash());
            preparedStatement.setInt(9, playerInventoryData.getProtectionAttempts());
            preparedStatement.setLong(10, playerInventoryData.getProtectionBanUntil());
            preparedStatement.setString(11, playerInventoryData.getPlayerUUID());
            preparedStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void disable() {
        try {
            connection.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
