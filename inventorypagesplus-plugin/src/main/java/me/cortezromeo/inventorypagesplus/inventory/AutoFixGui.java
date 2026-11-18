package me.cortezromeo.inventorypagesplus.inventory;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixInventoryFile;
import me.cortezromeo.inventorypagesplus.manager.AutoFixManager;
import me.cortezromeo.inventorypagesplus.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AutoFixGui extends CustomInventoryBase {

    private final FileConfiguration config;
    private final String title;
    private final int size;
    private final int prevSlot;
    private final int nextSlot;
    private final int addSlot;
    private final List<Integer> ruleSlots;
    private int page = 0;

    public AutoFixGui(Player owner) {
        super(owner);
        config = AutoFixInventoryFile.get();
        title = InventoryPagesPlus.nms.addColor(config.getString("title", "AutoFix"));
        size = config.getInt("size", 54);
        prevSlot = config.getInt("prev-page-slot", 45);
        nextSlot = config.getInt("next-page-slot", 53);
        addSlot = config.getInt("add-slot", 49);
        ruleSlots = config.getIntegerList("rule-slots");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getMenuTitle() {
        return title.replace("%page%", String.valueOf(page + 1));
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public void setMenuItems() {
        for (int slot : ruleSlots)
            inventory.setItem(slot, null);
        inventory.setItem(prevSlot, guiItem("prevPage"));
        inventory.setItem(nextSlot, guiItem("nextPage"));
        inventory.setItem(addSlot, guiItem("add"));

        List<String> keys = AutoFixManager.getRuleKeys();
        int perPage = ruleSlots.size();
        if (perPage == 0)
            return;
        page = Math.max(0, Math.min(page, (keys.size() - 1) / perPage));
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= keys.size())
                break;
            String key = keys.get(index);
            ItemStack display = AutoFixManager.getOldItem(key);
            if (display == null)
                continue;
            ItemMeta itemMeta = display.getItemMeta();
            if (itemMeta != null) {
                List<String> lore = itemMeta.hasLore() ? new ArrayList<>(itemMeta.getLore()) : new ArrayList<>();
                ItemStack newItem = AutoFixManager.getNewItem(key);
                String newItemName = newItem == null ? Material.AIR.name() : newItem.getType().name();
                for (String line : config.getStringList("rule-lore"))
                    lore.add(InventoryPagesPlus.nms.addColor(line.replace("%key%", key).replace("%newItem%", newItemName)));
                itemMeta.setLore(lore);
                display.setItemMeta(itemMeta);
            }
            inventory.setItem(ruleSlots.get(i), display);
        }
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == prevSlot) {
            if (page > 0) {
                page--;
                reopen();
            }
        } else if (rawSlot == nextSlot) {
            int perPage = Math.max(1, ruleSlots.size());
            int maxPage = Math.max(0, (AutoFixManager.getRuleKeys().size() - 1) / perPage);
            if (page < maxPage) {
                page++;
                reopen();
            }
        } else if (rawSlot == addSlot) {
            Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> new AutoFixAddGui(owner).open());
        } else if (ruleSlots.contains(rawSlot)) {
            int index = page * ruleSlots.size() + ruleSlots.indexOf(rawSlot);
            List<String> keys = AutoFixManager.getRuleKeys();
            if (index < keys.size()) {
                String key = keys.get(index);
                Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, () -> new AutoFixEditGui(owner, key).open());
            }
        }
    }

    // Reopen on the next tick so the %page% title refreshes without opening an inventory mid-click.
    private void reopen() {
        Bukkit.getScheduler().runTask(InventoryPagesPlus.plugin, this::open);
    }

    private ItemStack guiItem(String key) {
        String path = "items." + key;
        return ItemUtil.getItem(config.getString(path + ".type"),
                config.getString(path + ".value"),
                (short) config.getInt(path + ".data"),
                config.getInt(path + ".customModelData"),
                config.getString(path + ".name"),
                config.getStringList(path + ".lore"));
    }
}