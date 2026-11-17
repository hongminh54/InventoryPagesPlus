package me.cortezromeo.inventorypagesplus.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class StringUtil {

    public static String pagesToJson(Map<Integer, List<ItemStack>> pages) {
        try {
            Map<Integer, List<String>> encoded = new TreeMap<>();
            if (pages != null) {
                for (Map.Entry<Integer, List<ItemStack>> entry : new TreeMap<>(pages).entrySet()) {
                    List<String> items = encodeItems(entry.getValue(), 25);
                    if (items.stream().anyMatch(item -> item != null))
                        encoded.put(entry.getKey(), items);
                }
            }
            return new Gson().toJson(encoded);
        } catch (Exception exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Unable to encode history pages: " + exception.getMessage());
            return null;
        }
    }

    public static Map<Integer, List<ItemStack>> pagesFromJson(String json) {
        Map<Integer, List<ItemStack>> pages = new TreeMap<>();
        if (json == null || json.isEmpty())
            return pages;
        try {
            HashMap<Integer, ArrayList<String>> encoded = new Gson().fromJson(json,
                    new TypeToken<HashMap<Integer, ArrayList<String>>>() {
                    }.getType());
            if (encoded == null)
                throw new IllegalArgumentException("pages JSON is null");
            for (Map.Entry<Integer, ArrayList<String>> entry : encoded.entrySet()) {
                if (entry.getKey() == null) {
                    InventoryPagesPlus.plugin.getLogger().warning("Ignoring history page with a null index.");
                    continue;
                }
                List<ItemStack> items = decodeItems(entry.getValue(), 25);
                if (items.stream().anyMatch(item -> item != null))
                    pages.put(entry.getKey(), items);
            }
        } catch (Exception exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Unable to decode history pages: " + exception.getMessage());
        }
        return pages;
    }

    public static String itemsToJson(List<ItemStack> items) {
        return itemsToJson(items, 27);
    }

    public static String itemsToJson(List<ItemStack> items, int size) {
        try {
            return new Gson().toJson(encodeItems(items, size));
        } catch (Exception exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Unable to encode history items: " + exception.getMessage());
            return null;
        }
    }

    public static List<ItemStack> itemsFromJson(String json) {
        return itemsFromJson(json, 27);
    }

    public static List<ItemStack> itemsFromJson(String json, int size) {
        if (json == null || json.isEmpty())
            return new ArrayList<>();
        try {
            ArrayList<String> encoded = new Gson().fromJson(json, new TypeToken<ArrayList<String>>() {
            }.getType());
            if (encoded == null)
                throw new IllegalArgumentException("history items JSON is null");
            return decodeItems(encoded, size);
        } catch (Exception exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Unable to decode history items: " + exception.getMessage());
            return new ArrayList<>(Collections.nCopies(size, null));
        }
    }

    public static String itemToJson(ItemStack item) {
        return itemsToJson(Collections.singletonList(item), 1);
    }

    public static ItemStack itemFromJson(String json) {
        List<ItemStack> items = itemsFromJson(json, 1);
        return items.isEmpty() ? null : items.get(0);
    }

    private static List<String> encodeItems(List<ItemStack> items, int size) {
        List<String> encoded = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = items != null && slot < items.size() ? items.get(slot) : null;
            String value = item == null ? null : toBase64(item);
            // A failed non-null item must reject the snapshot rather than silently lose it.
            if (item != null && value == null)
                throw new IllegalArgumentException("item encoding failed at slot " + slot);
            encoded.add(value);
        }
        return encoded;
    }

    private static List<ItemStack> decodeItems(List<String> encoded, int size) {
        List<ItemStack> items = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = null;
            String value = encoded != null && slot < encoded.size() ? encoded.get(slot) : null;
            if (value != null) {
                try {
                    item = itemStackFromBase64(value);
                    if (item == null)
                        InventoryPagesPlus.plugin.getLogger().warning("History item decoded to null at slot " + slot + ".");
                } catch (Exception exception) {
                    InventoryPagesPlus.plugin.getLogger().warning("Unable to decode history item at slot " + slot + ": " + exception.getMessage());
                }
            }
            items.add(item);
        }
        return items;
    }

    public static String toBase64(ItemStack itemstack) {
        ItemStack[] arr = new ItemStack[1];
        arr[0] = itemstack;
        return toBase64(arr);
    }

    public static String toBase64(ItemStack[] contents) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(contents.length);

            for (ItemStack itemStack : contents) {
                dataOutput.writeObject(InventoryPagesPlus.nms.getItemStack(itemStack));
            }
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception exception) {
            // One unusable item must not abort the save of the whole database.
            InventoryPagesPlus.plugin.getLogger().warning("Unable to save the item stack: " + contents[0]);
            exception.printStackTrace();
            return null;
        }
    }

    public static ItemStack itemStackFromBase64(String data) {
        try {
            ItemStack[] stacks = stacksFromBase64(data);
            return stacks.length == 0 ? null : stacks[0];
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static Inventory inventoryFromBase64(String data) throws IOException {
        ItemStack[] stacks = stacksFromBase64(data);
        Inventory inventory = Bukkit.createInventory(null, (int) Math.ceil(stacks.length / 9D) * 9);

        for (int i = 0; i < stacks.length; i++) {
            inventory.setItem(i, stacks[i]);
        }

        return inventory;
    }

    public static ItemStack[] stacksFromBase64(String data) throws IOException {
        try {
            if (data == null || Base64Coder.decodeLines(data) == null) return new ItemStack[]{};
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] stacks = new ItemStack[dataInput.readInt()];

            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = InventoryPagesPlus.nms.getItemStack((ItemStack) dataInput.readObject());
            }
            dataInput.close();
            return stacks;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}