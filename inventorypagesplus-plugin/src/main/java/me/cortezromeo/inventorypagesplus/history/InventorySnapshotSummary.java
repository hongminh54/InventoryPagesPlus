package me.cortezromeo.inventorypagesplus.history;

public final class InventorySnapshotSummary {

    private final String id;
    private final String playerUUID;
    private final String playerName;
    private final long createdAt;
    private final SnapshotReason reason;
    private final String serverId;
    private final int currentPage;
    private final int maxPage;
    private final int pageCount;
    private final boolean usedCreative;

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

    public String getId() {
        return id;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public SnapshotReason getReason() {
        return reason;
    }

    public String getServerId() {
        return serverId;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getMaxPage() {
        return maxPage;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isUsedCreative() {
        return usedCreative;
    }
}
