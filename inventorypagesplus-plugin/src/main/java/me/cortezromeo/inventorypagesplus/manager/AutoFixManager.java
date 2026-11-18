package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.file.inventory.AutoFixDataFile;
import me.cortezromeo.inventorypagesplus.inventory.PlayerPageInventory;
import me.cortezromeo.inventorypagesplus.language.Messages;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AutoFixManager {

    private static final Map<String, ItemStack> oldTemplates = new LinkedHashMap<>();
    private static final Map<String, ItemStack> newTemplates = new LinkedHashMap<>();
    private static final Set<UUID> bypassFix = new HashSet<>();
    private static boolean scanRunning = false;

    public static boolean isEnable() {
        return Settings.AUTOFIX_ENABLED;
    }

    public static boolean isBypassed(Player player) {
        // Admins keep the original template items; the permission is a permanent opt-out.
        return bypassFix.contains(player.getUniqueId())
                || player.hasPermission("inventorypagesplus.autofix.bypass")
                || player.hasPermission("inventorypagesplus.admin");
    }

    public static boolean toggleBypass(Player player) {
        UUID uuid = player.getUniqueId();
        if (bypassFix.remove(uuid))
            return false;
        bypassFix.add(uuid);
        return true;
    }

    public static void reloadRules() {
        oldTemplates.clear();
        newTemplates.clear();
        FileConfiguration data = AutoFixDataFile.get();
        ConfigurationSection fixItem = data.getConfigurationSection("FixItem");
        if (fixItem == null)
            return;
        for (String key : data.getStringList("NameList")) {
            ConfigurationSection rule = fixItem.getConfigurationSection(key);
            if (rule == null)
                continue;
            ItemStack oldItem = stringBlobToItem(rule.getString("Old"));
            if (oldItem == null)
                continue;
            oldTemplates.put(key, oldItem);
            newTemplates.put(key, stringBlobToItem(rule.getString("New")));
        }
        DebugManager.debug("AUTOFIX", "Loaded " + oldTemplates.size() + " fix rule(s).");
    }

    public static List<String> getRuleKeys() {
        return new ArrayList<>(oldTemplates.keySet());
    }

    public static ItemStack getOldItem(String key) {
        ItemStack item = oldTemplates.get(key);
        return item == null ? null : item.clone();
    }

    public static ItemStack getNewItem(String key) {
        ItemStack item = newTemplates.get(key);
        return item == null ? null : item.clone();
    }

    public static int tryFix(Player player) {
        if (!isEnable() || isBypassed(player))
            return 0;
        // Always scan on join: items acquired after a previous scan must still be fixed.
        int count = fix(player);
        if (count > 0)
            MessageUtil.sendMessage(player, Messages.COMMAND_AUTOFIX_COMPLETED.replace("%amount%", String.valueOf(count)));
        return count;
    }

    public static int forceFix(Player player) {
        if (isBypassed(player))
            return 0;
        return fix(player);
    }

    public static void forceFixAll(CommandSender sender) {
        scanOnline(true, sender);
    }

    // Re-scan everyone online after a rule change, without the force command messages.
    public static void tryFixAllOnline() {
        scanOnline(false, null);
    }

    private static void scanOnline(boolean force, CommandSender sender) {
        if (scanRunning) {
            if (sender != null)
                MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_FORCE_ALL_RUNNING);
            return;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            if (sender != null)
                MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_FORCE_ALL_EMPTY);
            return;
        }
        scanRunning = true;
        if (sender != null)
            MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_FORCE_ALL_STARTED.replace("%total%", String.valueOf(players.size())));
        // Spread the scan over several ticks so fixing everyone never hurts the TPS.
        new BukkitRunnable() {
            int index = 0;
            int fixed = 0;

            @Override
            public void run() {
                for (int i = 0; i < Settings.AUTOFIX_PLAYERS_PER_TICK && index < players.size(); i++) {
                    Player player = players.get(index++);
                    if (player.isOnline() && !isBypassed(player))
                        fixed += force ? forceFix(player) : tryFix(player);
                }
                if (index >= players.size()) {
                    cancel();
                    scanRunning = false;
                    if (sender != null)
                        MessageUtil.sendMessage(sender, Messages.COMMAND_AUTOFIX_FORCE_ALL_COMPLETE
                                .replace("%fixed%", String.valueOf(fixed))
                                .replace("%total%", String.valueOf(players.size())));
                }
            }
        }.runTaskTimer(InventoryPagesPlus.plugin, 1L, 1L);
    }

    public static int fix(Player player) {
        int count = fixContents(player.getInventory());
        count += fixContents(player.getEnderChest());

        InventoryPagesPlusDataManager databaseManager = InventoryPagesPlus.getDatabaseManager();
        String playerUUID = player.getUniqueId().toString();
        if (databaseManager.getPlayerInventoryDatabase().containsKey(playerUUID)) {
            PlayerInventoryDatabase data = databaseManager.getPlayerInventoryDatabase(player.getUniqueId());
            // Sync the already fixed displayed page into storage before scanning the hidden pages.
            data.saveCurrentPage();
            for (List<ItemStack> pageItems : data.getItems().values())
                count += fixList(pageItems);
            count += fixList(data.getCreativeItems());
            // Only re-render and hit the database when something actually changed.
            if (count > 0) {
                if (!data.isProtectionLocked())
                    data.showPage(player.getGameMode());
                databaseManager.savePlayerInventory(player.getName());
            }
        }
        return count;
    }

    public static int fixContents(Inventory inventory) {
        if (inventory == null)
            return 0;
        boolean isPlayerInventory = inventory instanceof PlayerInventory;
        int count = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            // Skip slots belong to other plugins (MMOInventory,...) and must never be touched.
            if (isPlayerInventory && slot >= 9 && slot <= 35 && Settings.SKIP_SLOTS.contains(slot - 9))
                continue;
            ItemStack item = inventory.getItem(slot);
            if (!isItemFix(item))
                continue;
            ItemStack fixed = fix(item);
            if (fixed != null) {
                inventory.setItem(slot, fixed);
                count++;
            }
        }
        return count;
    }

    private static int fixList(List<ItemStack> items) {
        if (items == null)
            return 0;
        int count = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack item = items.get(slot);
            if (!isItemFix(item))
                continue;
            ItemStack fixed = fix(item);
            if (fixed != null) {
                items.set(slot, fixed);
                count++;
            }
        }
        return count;
    }

    public static ItemStack fix(ItemStack itemStack) {
        return buildFixedItem(itemStack, newTemplates.get(keyOf(itemStack)));
    }

    public static ItemStack buildFixedItem(ItemStack oldItem, ItemStack template) {
        if (template == null)
            return null;

        ItemStack fixed = template.clone();
        fixed.setAmount(oldItem.getAmount());

        ItemMeta oldMeta = oldItem.getItemMeta();
        ItemMeta newMeta = fixed.getItemMeta();
        if (oldMeta != null && newMeta != null) {
            if (Settings.AUTOFIX_TOGGLE_ENCHANT)
                for (Map.Entry<Enchantment, Integer> entry : oldMeta.getEnchants().entrySet())
                    newMeta.addEnchant(entry.getKey(), entry.getValue(), true);
            // The new template defines the new lore; only fall back to the old lore when it has none.
            if (!newMeta.hasLore() && oldMeta.hasLore())
                newMeta.setLore(oldMeta.getLore());
            fixed.setItemMeta(newMeta);
        }

        String customData = InventoryPagesPlus.nms.getCustomData(oldItem);
        if (!customData.isEmpty())
            fixed = InventoryPagesPlus.nms.addCustomData(fixed, customData);
        return fixed;
    }

    public static boolean isItemFix(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || !itemStack.hasItemMeta())
            return false;
        // Never touch the fake page navigation buttons.
        if (InventoryPagesPlus.nms.getCustomData(itemStack).equals(PlayerPageInventory.itemCustomData))
            return false;
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasDisplayName())
            return false;

        String key = keyOf(itemStack);
        ItemStack oldTemplate = oldTemplates.get(key);
        if (oldTemplate == null || !oldTemplate.hasItemMeta())
            return false;
        ItemMeta oldMeta = oldTemplate.getItemMeta();
        if (oldMeta == null || !oldMeta.hasDisplayName())
            return false;

        // "contains" so enhanced variants like "Name [+3]" still match the base template name.
        if (!InventoryPagesPlus.nms.stripColor(itemMeta.getDisplayName())
                .contains(InventoryPagesPlus.nms.stripColor(oldMeta.getDisplayName())))
            return false;

        ItemStack newTemplate = newTemplates.get(key);
        if (newTemplate == null)
            return false;
        // A fixed item can still match the old name (same name, or "Name [+n]" variants).
        // Fixing is idempotent, so only treat the item as fixable when fixing would change it.
        return !itemStack.isSimilar(buildFixedItem(itemStack, newTemplate));
    }

    public static String keyOf(ItemStack itemStack) {
        String name = InventoryPagesPlus.nms.stripColor(itemStack.getItemMeta().getDisplayName());
        if (name.contains("[+"))
            name = name.substring(0, name.lastIndexOf('[')).trim();
        return name + "-" + itemStack.getType().name();
    }

    public static String addFix(ItemStack oldItem, ItemStack newItem) {
        String key = keyOf(oldItem);
        FileConfiguration data = AutoFixDataFile.get();
        List<String> nameList = data.getStringList("NameList");
        if (!nameList.contains(key))
            nameList.add(key);
        data.set("NameList", nameList);

        ConfigurationSection fixItem = data.getConfigurationSection("FixItem");
        if (fixItem == null)
            fixItem = data.createSection("FixItem");
        ConfigurationSection rule = fixItem.getConfigurationSection(key);
        if (rule == null)
            rule = fixItem.createSection(key);
        rule.set("Old", itemToStringBlob(oldItem));
        rule.set("New", itemToStringBlob(newItem));

        AutoFixDataFile.save();
        reloadRules();
        return key;
    }

    public static void removeFix(String key) {
        FileConfiguration data = AutoFixDataFile.get();
        List<String> nameList = data.getStringList("NameList");
        nameList.remove(key);
        data.set("NameList", nameList);
        ConfigurationSection fixItem = data.getConfigurationSection("FixItem");
        if (fixItem != null)
            fixItem.set(key, null);

        AutoFixDataFile.save();
        reloadRules();
    }

    public static String itemToStringBlob(ItemStack itemStack) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("i", itemStack);
        return yaml.saveToString();
    }

    public static ItemStack stringBlobToItem(String data) {
        if (data == null || data.isEmpty())
            return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(data);
        } catch (Exception exception) {
            InventoryPagesPlus.plugin.getLogger().warning("Could not read an autofix item blob: " + exception.getMessage());
            return null;
        }
        return yaml.getItemStack("i");
    }
}