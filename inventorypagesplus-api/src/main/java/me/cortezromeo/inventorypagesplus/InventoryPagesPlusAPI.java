package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.manager.InventoryPagesPlusDataManager;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;

public interface InventoryPagesPlusAPI {

    PlayerInventoryDatabase getPlayerInventoryDatabase();

    InventoryPagesPlusDataManager getInventoryPagesPlusDataManager();

}
