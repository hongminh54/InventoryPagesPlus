package me.cortezromeo.inventorypagesplus.storage;

import me.cortezromeo.inventorypagesplus.history.HistoryStoredRecord;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshot;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshotSummary;

import java.util.List;

public interface InventoryHistoryStorage {

    void save(InventorySnapshot snapshot, String pagesJson, String creativeItemsJson,
              String armorJson, String offhandJson, String hotbarJson, String contentHash) throws Exception;

    List<InventorySnapshotSummary> list(String playerUUID, String serverId,
                                        long fromMillis, long toMillis, int limit) throws Exception;

    InventorySnapshot get(String playerUUID, String snapshotId, long createdAt) throws Exception;

    String getLastContentHash(String playerUUID, String serverId) throws Exception;

    void prune(String playerUUID, String serverId, long cutoffMillis, int maxSnapshots) throws Exception;

    void pruneAll(long cutoffMillis) throws Exception;

    InventorySnapshotSummary adjacent(InventorySnapshotSummary reference, String serverId,
                                      long fromMillis, long toMillis, boolean older) throws Exception;

    List<InventorySnapshotSummary> expired(long cutoffMillis, String serverId, int limit) throws Exception;

    HistoryStoredRecord exportRecord(InventorySnapshotSummary summary) throws Exception;

    boolean deleteRecord(HistoryStoredRecord record) throws Exception;

    void close();
}
