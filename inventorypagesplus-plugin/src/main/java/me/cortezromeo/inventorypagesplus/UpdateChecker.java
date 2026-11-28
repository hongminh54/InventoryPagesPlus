package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class UpdateChecker {

    // Release tags are plain semver (for example 1.3.0); a leading v is still tolerated.
    private static final String GITHUB_REPOSITORY = "hongminh54/InventoryPagesPlus";
    private static final String API = "https://api.github.com/repos/" + GITHUB_REPOSITORY + "/releases/latest";
    private static final String DOWNLOAD = "https://github.com/" + GITHUB_REPOSITORY + "/releases/latest";

    public static void check() {
        if (!Settings.UPDATE_CHECKER_ENABLED)
            return;
        String currentVersion = InventoryPagesPlus.plugin.getDescription().getVersion();

        Bukkit.getScheduler().runTaskAsynchronously(InventoryPagesPlus.plugin, () -> {
            try {
                String latest = fetchLatestTag(API);
                if (latest == null || compare(currentVersion, latest) >= 0) {
                    DebugManager.debug("UPDATER", "Already on the latest version (" + currentVersion + ").");
                    return;
                }
                MessageUtil.log("&e[INV.PAGES+] &fA new version is available: &b" + latest
                        + " &7(you are running &f" + currentVersion + "&7)");
                MessageUtil.log("&e[INV.PAGES+] &fDownload: &9" + DOWNLOAD);
            } catch (Exception exception) {
                // A failed update check must never look like a plugin error.
                DebugManager.debug("UPDATER", "Update check failed: " + exception);
            }
        });
    }

    private static String fetchLatestTag(String apiUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestProperty("User-Agent", "InventoryPagesPlus-UpdateChecker");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                body.append(line);
            Object tag = ((JSONObject) new JSONParser().parse(body.toString())).get("tag_name");
            return tag == null ? null : normalize(tag.toString());
        } finally {
            connection.disconnect();
        }
    }

    // Compares dot separated versions numerically; non-numeric suffixes fall back to string order.
    private static int compare(String current, String latest) {
        String[] mine = normalize(current).split("\\.");
        String[] theirs = latest.split("\\.");
        for (int index = 0; index < Math.max(mine.length, theirs.length); index++) {
            int minePart = index < mine.length ? parse(mine[index]) : 0;
            int theirPart = index < theirs.length ? parse(theirs[index]) : 0;
            if (minePart != theirPart)
                return Integer.compare(minePart, theirPart);
        }
        return 0;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalize(String version) {
        return version.trim().toLowerCase(Locale.ROOT).startsWith("v") ? version.trim().substring(1) : version.trim();
    }
}
