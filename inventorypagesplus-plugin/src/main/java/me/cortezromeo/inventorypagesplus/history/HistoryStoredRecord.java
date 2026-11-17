package me.cortezromeo.inventorypagesplus.history;

public final class HistoryStoredRecord {
    private final InventorySnapshotSummary summary;
    private final String extension;
    private final byte[] payload;

    public HistoryStoredRecord(InventorySnapshotSummary summary, String extension, byte[] payload) {
        this.summary = summary;
        this.extension = extension;
        this.payload = payload;
    }

    public InventorySnapshotSummary getSummary() {
        return summary;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String entryName() {
        return summary.playerUUID() + "/" + summary.id() + "." + extension;
    }
}
