package me.cortezromeo.inventorypagesplus.util;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemUtil {

    public static ItemStack getItem(String type, String value, short itemData, int customModelData, String name, List<String> lore) {
        ItemStack itemStack;

        if (type.equalsIgnoreCase("customhead"))
            itemStack = InventoryPagesPlus.nms.getHeadItemFromBase64(value);
        else if (type.equalsIgnoreCase("playerhead"))
            itemStack = InventoryPagesPlus.nms.getHeadItemFromPlayerName(value);
        else if (type.equalsIgnoreCase("material"))
            itemStack = InventoryPagesPlus.nms.createItemStack(value, 1, itemData, customModelData);
        else {
            InventoryPagesPlus.plugin.getLogger().severe("ITEM TYPE " + type + " DOES NOT EXIST!");
            itemStack = new ItemStack(Material.BEDROCK);
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return itemStack;

        itemMeta.setDisplayName(InventoryPagesPlus.nms.addColor(name));

        List<String> coloredLore = new ArrayList<>();
        for (String string : lore)
            coloredLore.add(InventoryPagesPlus.nms.addColor(string));
        itemMeta.setLore(coloredLore);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

}
