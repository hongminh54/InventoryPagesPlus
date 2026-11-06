package me.cortezromeo.inventorypagesplus.util;

import me.clip.placeholderapi.PlaceholderAPI;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.language.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {

    public static void throwErrorMessage(String message) {
        Bukkit.getLogger().severe(message);
        log("&4&l[INVENTORY PAGES ERROR] &c&lIf this error affect player's experience, please contact me through discord: Cortez_Romeo");
    }

    public static void sendBroadCast(String message) {
        if (message.equals(""))
            return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            sendMessage(p, message);
        }
    }

    public static void log(String message) {
        Bukkit.getConsoleSender().sendMessage(InventoryPagesPlus.nms.addColor(message));
    }

    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(InventoryPagesPlus.nms.addColor(message.replace("%prefix%", Messages.PREFIX)));
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || message.isEmpty())
            return;

        if (InventoryPagesPlus.isPapiSupport())
            message = PlaceholderAPI.setPlaceholders(player, message);

        sendMessage((CommandSender) player, message);
    }

    // only use for testing plugin
    public static void devMessage(String message) {
        log("[DEV] " + message);
    }

    public static void devMessage(Player player, String message) {
        player.sendMessage("[DEV] " + message);
    }

}
