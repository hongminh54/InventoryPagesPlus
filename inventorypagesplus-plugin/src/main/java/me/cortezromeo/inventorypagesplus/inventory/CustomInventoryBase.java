package me.cortezromeo.inventorypagesplus.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class CustomInventoryBase implements InventoryHolder {

    protected Inventory inventory;
    protected Player owner;

    public CustomInventoryBase(Player owner) {
        this.owner = owner;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, getSlots(), getMenuTitle());
        this.setMenuItems();
        getOwner().openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract String getMenuTitle();

    public abstract int getSlots();

    public abstract void handleMenu(InventoryClickEvent event);

    public abstract void setMenuItems();

    public boolean isReadOnly() {
        return false;
    }

    public Player getOwner() {
        return owner;
    }

}
