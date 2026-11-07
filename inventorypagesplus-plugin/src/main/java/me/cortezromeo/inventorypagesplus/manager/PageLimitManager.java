package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class PageLimitManager {

    // Only ever compared or displayed, no loop counts up to it, so unlimited pages stay cheap.
    public static final int UNLIMITED = Integer.MAX_VALUE;
    private static final String PERMISSION_PREFIX = "inventorypagesplus.slotpage.";

    // Returns the last granted page index, 0 based like the stored max page.
    public static int getPermissionMaxPage(Player player) {
        int defaultMaxPage = Math.max(0, Settings.PAGE_PERMISSIONS_DEFAULT_MAX_PAGE - 1);
        // An offline player has no resolvable permissions, only the stored max page applies to them.
        if (player == null)
            return defaultMaxPage;

        if (player.isOp() || player.hasPermission("inventorypagesplus.admin"))
            return UNLIMITED;

        int bestLimit = -1;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            if (!attachment.getValue() || !attachment.getPermission().startsWith(PERMISSION_PREFIX))
                continue;

            int totalPages;
            try {
                totalPages = Integer.parseInt(attachment.getPermission().substring(PERMISSION_PREFIX.length()));
            } catch (NumberFormatException exception) {
                // A suffix that is not a number is a rank key, handled below.
                continue;
            }
            bestLimit = Math.max(bestLimit, Math.max(0, totalPages - 1));
        }

        int bestPriority = Integer.MIN_VALUE;
        ConfigurationSection ranks = InventoryPagesPlus.plugin.getConfig().getConfigurationSection("page-permissions.ranks");
        if (ranks != null) {
            for (String rank : ranks.getKeys(false)) {
                if (!player.hasPermission(PERMISSION_PREFIX + rank))
                    continue;

                int totalPages = ranks.getInt(rank + ".max-page", 0);
                if (totalPages < 1)
                    continue;

                // A matching rank outranks the number nodes, and the highest priority wins between ranks.
                int priority = ranks.getInt(rank + ".priority", 0);
                if (priority > bestPriority) {
                    bestPriority = priority;
                    bestLimit = totalPages - 1;
                }
            }
        }

        return bestLimit >= 0 ? bestLimit : defaultMaxPage;
    }

    // Item names and lore are colored before placeholders are replaced, so the label has to be colored here.
    public static String formatMaxPage(int effectiveMaxPage) {
        if (effectiveMaxPage == UNLIMITED)
            return InventoryPagesPlus.nms.addColor(Settings.PAGE_PERMISSIONS_UNLIMITED_LABEL);
        return String.valueOf(effectiveMaxPage + 1);
    }

}