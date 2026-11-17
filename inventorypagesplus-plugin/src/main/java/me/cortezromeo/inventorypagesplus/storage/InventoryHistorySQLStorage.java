package me.cortezromeo.inventorypagesplus.storage;

import com.google.gson.Gson;
import me.cortezromeo.inventorypagesplus.history.HistoryStoredRecord;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.enums.DatabaseType;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshot;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshotSummary;
import me.cortezromeo.inventorypagesplus.history.SnapshotReason;
import me.cortezromeo.inventorypagesplus.util.StringUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InventoryHistorySQLStorage implements InventoryHistoryStorage {

    private final Connection connection;
    private final String table;
    private final String tableName;
    private final Logger logger;

    public InventoryHistorySQLStorage(Connection connection, DatabaseType databaseType, String tableName) throws SQLException {
        if (connection == null)
            throw new SQLException("History connection is null");
        if (databaseType != DatabaseType.H2 && databaseType != DatabaseType.MYSQL)
            throw new SQLException("History SQL storage requires H2 or MySQL");
        // Identifiers cannot be bound as JDBC parameters.
        if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
            throw new SQLException("Invalid configured history table identifier: " + tableName);
        this.connection = connection;
        this.tableName = tableName;
        this.table = "`" + tableName + "`";
        this.logger = InventoryPagesPlus.plugin.getLogger();
        String textType = databaseType == DatabaseType.MYSQL ? "LONGTEXT" : "TEXT";
        String query = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "ID VARCHAR(36) NOT NULL PRIMARY KEY, PLAYERUUID VARCHAR(50) NOT NULL, "
                + "PLAYERNAME VARCHAR(50), CREATEDAT BIGINT NOT NULL, REASON VARCHAR(32), SERVERID VARCHAR(64), "
                + "PAGECOUNT INT DEFAULT 0, CURRENTPAGE INT DEFAULT 0, MAXPAGE INT DEFAULT 0, "
                + "USEDCREATIVE BOOLEAN DEFAULT FALSE, CONTENTHASH VARCHAR(64), "
                + "PAGES " + textType + ", CREATIVEITEMS " + textType + ", ARMOR " + textType
                + ", OFFHAND " + textType + ", HOTBAR " + textType
                + (databaseType == DatabaseType.MYSQL ? ", INDEX IDX_HISTORY_PLAYER_TIME (PLAYERUUID, CREATEDAT)" : "") + ")";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.executeUpdate();
        }
        // Tables created before equipment support keep their rows; new columns decode as empty.
        addColumnIfMissing("ARMOR", textType);
        addColumnIfMissing("OFFHAND", textType);
        addColumnIfMissing("HOTBAR", textType);
        ensureHistoryIndex("TIME_ID", "CREATEDAT, ID");
        ensureHistoryIndex("SERVER_TIME", "SERVERID, CREATEDAT, ID");
        ensureHistoryIndex("PLAYER_SERVER_TIME", "PLAYERUUID, SERVERID, CREATEDAT, ID");
        if (databaseType == DatabaseType.H2) {
            // H2 index names are schema-global, so derive one per configured table.
            String indexName = "IDX_" + tableName.toUpperCase(Locale.ROOT) + "_PLAYER_TIME";
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + " (PLAYERUUID, CREATEDAT)")) {
                statement.executeUpdate();
            }
        }
    }

    private void ensureHistoryIndex(String suffix, String columns) throws SQLException {
        String index = "IDX_H_" + Integer.toUnsignedString(tableName.hashCode(), 16) + "_" + suffix;
        String actualTable = tableName;
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", null)) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    actualTable = tables.getString("TABLE_NAME");
                    break;
                }
            }
        }
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, actualTable, false, false)) {
            while (indexes.next())
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME")))
                    return;
        }
        try (PreparedStatement statement = connection.prepareStatement("CREATE INDEX " + index + " ON " + table + " (" + columns + ")")) {
            statement.executeUpdate();
        }
    }

    private void addColumnIfMissing(String column, String textType) throws SQLException {
        // H2/MySQL differ in identifier case, so match metadata case-insensitively.
        String actualTable = null;
        try (ResultSet tables = connection.getMetaData().getTables(null, null, "%", null)) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    actualTable = tables.getString("TABLE_NAME");
                    break;
                }
            }
        }
        if (actualTable == null)
            throw new SQLException("History table is missing after creation: " + tableName);
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, actualTable, "%")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME")))
                    return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + textType)) {
            statement.executeUpdate();
        }
    }

    @Override
    public void save(InventorySnapshot snapshot, String pagesJson, String creativeItemsJson,
                     String armorJson, String offhandJson, String hotbarJson, String contentHash) throws SQLException {
        if (pagesJson == null || creativeItemsJson == null || armorJson == null || offhandJson == null || hotbarJson == null)
            throw new IllegalArgumentException("History item JSON must not be null");
        String query = "INSERT INTO " + table + " (ID, PLAYERUUID, PLAYERNAME, CREATEDAT, REASON, SERVERID, "
                + "PAGECOUNT, CURRENTPAGE, MAXPAGE, USEDCREATIVE, CONTENTHASH, PAGES, CREATIVEITEMS, ARMOR, OFFHAND, HOTBAR) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, snapshot.getId());
            statement.setString(2, snapshot.getPlayerUUID());
            statement.setString(3, snapshot.getPlayerName());
            statement.setLong(4, snapshot.getCreatedAt());
            statement.setString(5, snapshot.getReason().name());
            statement.setString(6, snapshot.getServerId());
            statement.setInt(7, snapshot.getPageCount());
            statement.setInt(8, snapshot.getCurrentPage());
            statement.setInt(9, snapshot.getMaxPage());
            statement.setBoolean(10, snapshot.isUsedCreative());
            statement.setString(11, contentHash);
            statement.setString(12, pagesJson);
            statement.setString(13, creativeItemsJson);
            statement.setString(14, armorJson);
            statement.setString(15, offhandJson);
            statement.setString(16, hotbarJson);
            statement.executeUpdate();
        }
    }

    @Override
    public List<InventorySnapshotSummary> list(String playerUUID, String serverId,
                                               long fromMillis, long toMillis, int limit) throws SQLException {
        List<InventorySnapshotSummary> summaries = new ArrayList<>();
        boolean filterServer = serverId != null && !serverId.isEmpty();
        String query = "SELECT ID, PLAYERUUID, PLAYERNAME, CREATEDAT, REASON, SERVERID, "
                + "PAGECOUNT, CURRENTPAGE, MAXPAGE, USEDCREATIVE FROM " + table
                + " WHERE PLAYERUUID = ? AND CREATEDAT >= ? AND CREATEDAT < ?"
                + (filterServer ? " AND SERVERID = ?" : "") + " ORDER BY CREATEDAT DESC, ID DESC LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID);
            statement.setLong(2, fromMillis);
            statement.setLong(3, toMillis);
            int index = 4;
            if (filterServer)
                statement.setString(index++, serverId);
            statement.setInt(index, limit > 0 ? Math.min(limit, 1000) : 100);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next())
                    summaries.add(readSummary(result));
            }
        }
        return summaries;
    }

    @Override
    public InventorySnapshot get(String playerUUID, String snapshotId, long createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE ID = ? AND PLAYERUUID = ?")) {
            statement.setString(1, snapshotId);
            statement.setString(2, playerUUID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next())
                    return null;
                InventorySnapshotSummary summary = readSummary(result);
                return new InventorySnapshot(summary.getId(), summary.getPlayerUUID(), summary.getPlayerName(),
                        summary.getCreatedAt(), summary.getReason(), summary.getServerId(), summary.getCurrentPage(),
                        summary.getMaxPage(), summary.isUsedCreative(), StringUtil.pagesFromJson(result.getString("PAGES")),
                        StringUtil.itemsFromJson(result.getString("CREATIVEITEMS")),
                        StringUtil.itemsFromJson(result.getString("ARMOR"), 4),
                        StringUtil.itemFromJson(result.getString("OFFHAND")),
                        StringUtil.itemsFromJson(result.getString("HOTBAR"), 9));
            }
        }
    }

    @Override
    public String getLastContentHash(String playerUUID, String serverId) throws SQLException {
        boolean filterServer = serverId != null && !serverId.isEmpty();
        String query = "SELECT CONTENTHASH FROM " + table + " WHERE PLAYERUUID = ?"
                + (filterServer ? " AND SERVERID = ?" : "") + " ORDER BY CREATEDAT DESC, ID DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerUUID);
            if (filterServer)
                statement.setString(2, serverId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("CONTENTHASH") : null;
            }
        }
    }

    @Override
    public void prune(String playerUUID, String serverId, long cutoffMillis, int maxSnapshots) throws SQLException {
        if (cutoffMillis <= 0 && maxSnapshots <= 0)
            return;
        boolean filterServer = serverId != null && !serverId.isEmpty();
        String query = "SELECT ID, CREATEDAT FROM " + table + " WHERE PLAYERUUID = ?"
                + (filterServer ? " AND SERVERID = ?" : "") + " ORDER BY CREATEDAT DESC, ID DESC";
        try (PreparedStatement select = connection.prepareStatement(query);
             PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE ID = ?")) {
            select.setString(1, playerUUID);
            if (filterServer)
                select.setString(2, serverId);
            boolean hasDeletes = false;
            long count = 0;
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    count++;
                    if ((cutoffMillis > 0 && result.getLong("CREATEDAT") < cutoffMillis)
                            || (maxSnapshots > 0 && count > maxSnapshots)) {
                        delete.setString(1, result.getString("ID"));
                        delete.addBatch();
                        hasDeletes = true;
                    }
                }
            }
            if (hasDeletes)
                delete.executeBatch();
        }
    }

    @Override
    public void pruneAll(long cutoffMillis) throws SQLException {
        if (cutoffMillis <= 0)
            return;
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE CREATEDAT < ?")) {
            statement.setLong(1, cutoffMillis);
            statement.executeUpdate();
        }
    }

    @Override
    public InventorySnapshotSummary adjacent(InventorySnapshotSummary reference, String serverId,
                                             long fromMillis, long toMillis, boolean older) throws SQLException {
        boolean filterServer = serverId != null && !serverId.isEmpty();
        String comparison = older ? " < ?" : " > ?";
        String order = older ? " DESC" : " ASC";
        String query = "SELECT ID, PLAYERUUID, PLAYERNAME, CREATEDAT, REASON, SERVERID, PAGECOUNT, CURRENTPAGE, MAXPAGE, USEDCREATIVE FROM "
                + table + " WHERE PLAYERUUID = ? AND CREATEDAT >= ? AND CREATEDAT < ?"
                + " AND (CREATEDAT" + comparison + " OR (CREATEDAT = ? AND ID" + comparison + "))"
                + (filterServer ? " AND SERVERID = ?" : "") + " ORDER BY CREATEDAT" + order + ", ID" + order + " LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, reference.getPlayerUUID());
            statement.setLong(2, fromMillis);
            statement.setLong(3, toMillis);
            statement.setLong(4, reference.getCreatedAt());
            statement.setLong(5, reference.getCreatedAt());
            statement.setString(6, reference.getId());
            if (filterServer)
                statement.setString(7, serverId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readSummary(result) : null;
            }
        }
    }

    @Override
    public List<InventorySnapshotSummary> expired(long cutoffMillis, String serverId, int limit) throws SQLException {
        List<InventorySnapshotSummary> records = new ArrayList<>();
        boolean filterServer = serverId != null && !serverId.isEmpty();
        String query = "SELECT ID, PLAYERUUID, PLAYERNAME, CREATEDAT, REASON, SERVERID, PAGECOUNT, CURRENTPAGE, MAXPAGE, USEDCREATIVE FROM "
                + table + " WHERE CREATEDAT < ?" + (filterServer ? " AND SERVERID = ?" : "")
                + " ORDER BY CREATEDAT, ID LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, cutoffMillis);
            int index = 2;
            if (filterServer)
                statement.setString(index++, serverId);
            statement.setInt(index, Math.max(1, Math.min(200, limit)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next())
                    records.add(readSummary(result));
            }
        }
        return records;
    }

    @Override
    public HistoryStoredRecord exportRecord(InventorySnapshotSummary summary) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table
                + " WHERE ID = ? AND PLAYERUUID = ? AND CREATEDAT = ?")) {
            statement.setString(1, summary.getId());
            statement.setString(2, summary.getPlayerUUID());
            statement.setLong(3, summary.getCreatedAt());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next())
                    return null;
                Map<String, String> row = new LinkedHashMap<>();
                ResultSetMetaData metadata = result.getMetaData();
                long size = 0;
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    String value = result.getString(column);
                    size += value == null ? 0 : value.length();
                    if (size > Settings.HISTORY_MAX_RECORD_BYTES)
                        throw new IOException("History row exceeds archive size limit; source retained.");
                    row.put(metadata.getColumnLabel(column).toUpperCase(Locale.ROOT), value);
                }
                byte[] bytes = new Gson().toJson(row).getBytes(StandardCharsets.UTF_8);
                return new HistoryStoredRecord(summary, "json", bytes);
            }
        }
    }

    @Override
    public boolean deleteRecord(HistoryStoredRecord record) throws Exception {
        InventorySnapshotSummary summary = record.getSummary();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement lock = connection.prepareStatement("SELECT ID FROM " + table
                    + " WHERE ID = ? AND PLAYERUUID = ? AND CREATEDAT = ? FOR UPDATE")) {
                lock.setString(1, summary.getId());
                lock.setString(2, summary.getPlayerUUID());
                lock.setLong(3, summary.getCreatedAt());
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return false;
                    }
                }
            }
            HistoryStoredRecord current = exportRecord(summary);
            if (current == null || !Arrays.equals(current.getPayload(), record.getPayload())) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table
                    + " WHERE ID = ? AND PLAYERUUID = ? AND CREATEDAT = ?")) {
                statement.setString(1, summary.getId());
                statement.setString(2, summary.getPlayerUUID());
                statement.setLong(3, summary.getCreatedAt());
                boolean deleted = statement.executeUpdate() == 1;
                connection.commit();
                return deleted;
            }
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Unable to close inventory history connection", exception);
        }
    }

    private InventorySnapshotSummary readSummary(ResultSet result) throws SQLException {
        String id = result.getString("ID");
        String storedReason = result.getString("REASON");
        SnapshotReason reason;
        try {
            reason = SnapshotReason.valueOf(storedReason);
        } catch (IllegalArgumentException | NullPointerException exception) {
            logger.warning("Invalid history reason for snapshot " + id + ": " + storedReason + "; using PERIODIC.");
            reason = SnapshotReason.PERIODIC;
        }
        return new InventorySnapshotSummary(id, result.getString("PLAYERUUID"), result.getString("PLAYERNAME"),
                result.getLong("CREATEDAT"), reason, result.getString("SERVERID"), result.getInt("CURRENTPAGE"),
                result.getInt("MAXPAGE"), result.getInt("PAGECOUNT"), result.getBoolean("USEDCREATIVE"));
    }
}
