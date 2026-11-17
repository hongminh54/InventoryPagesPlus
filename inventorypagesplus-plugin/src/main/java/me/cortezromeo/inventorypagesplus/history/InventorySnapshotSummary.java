package me.cortezromeo.inventorypagesplus.history;

public record InventorySnapshotSummary(String id, String playerUUID, String playerName, long createdAt,
                                       SnapshotReason reason, String serverId, int currentPage, int maxPage,
                                       int pageCount, boolean usedCreative) {

    public InventorySnapshotSummary(String id, String playerUUID, String playerName, long createdAt,
                                    SnapshotReason reason, String serverId, int currentPage, int maxPage,
                                    int pageCount, boolean usedCreative) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.createdAt = createdAt;
        this.reason = reason;
        this.serverId = serverId == null ? "" : serverId;
        this.currentPage = Math.max(0, currentPage);
        this.maxPage = Math.max(0, maxPage);
        this.pageCount = Math.max(0, pageCount);
        this.usedCreative = usedCreative;
    }
}
