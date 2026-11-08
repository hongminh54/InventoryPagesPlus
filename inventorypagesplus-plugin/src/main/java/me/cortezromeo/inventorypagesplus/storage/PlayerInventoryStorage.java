package me.cortezromeo.inventorypagesplus.storage;

public interface PlayerInventoryStorage {
    boolean hasDataPlayerName(String playerName);

    String getUUIDFromData(String playerName, boolean naturalCheck);

    void saveData(PlayerInventoryDatabase data);

    PlayerInventoryDatabase getData(String playerName);

    void disable();
}
