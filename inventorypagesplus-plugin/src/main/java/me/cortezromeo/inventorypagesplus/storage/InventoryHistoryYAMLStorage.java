package me.cortezromeo.inventorypagesplus.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.history.HistoryStoredRecord;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshot;
import me.cortezromeo.inventorypagesplus.history.InventorySnapshotSummary;
import me.cortezromeo.inventorypagesplus.history.SnapshotReason;
import me.cortezromeo.inventorypagesplus.util.StringUtil;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.stream.Stream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class InventoryHistoryYAMLStorage implements InventoryHistoryStorage {

    private final File directory;
    private final Logger logger;
    private Stream<Path> expiryScan;
    private Iterator<Path> expiryIterator;
    private static final Comparator<InventorySnapshotSummary> ORDER = Comparator
            .comparingLong(InventorySnapshotSummary::getCreatedAt).thenComparing(InventorySnapshotSummary::getId);

    public InventoryHistoryYAMLStorage() throws IOException {
        logger = InventoryPagesPlus.plugin.getLogger();
        File dataFolder = InventoryPagesPlus.plugin.getDataFolder().getCanonicalFile();
        directory = new File(dataFolder, "database/history").getCanonicalFile();
        if (!directory.toPath().startsWith(dataFolder.toPath()))
            throw new IOException("History directory is outside the plugin data folder");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Unable to create history directory: " + directory);
    }

    @Override
    public void save(InventorySnapshot snapshot, String pagesJson, String creativeItemsJson,
                     String armorJson, String offhandJson, String hotbarJson, String contentHash) throws IOException {
        if (pagesJson == null || creativeItemsJson == null || armorJson == null || offhandJson == null || hotbarJson == null)
            throw new IllegalArgumentException("History item JSON must not be null");
        File file = snapshotFile(snapshot.getPlayerUUID(), snapshot.getId(), snapshot.getCreatedAt());
        File folder = file.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("Unable to create history player directory: " + folder);
        if (file.exists())
            throw new IOException("History snapshot already exists: " + file);
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("player-name", snapshot.getPlayerName());
        configuration.set("player-uuid", snapshot.getPlayerUUID());
        configuration.set("created-at", snapshot.getCreatedAt());
        configuration.set("reason", snapshot.getReason().name());
        configuration.set("server-id", snapshot.getServerId());
        setContent(configuration, snapshot, pagesJson, creativeItemsJson, armorJson, offhandJson, hotbarJson, contentHash);
        write(file, configuration);
    }

    private void setContent(YamlConfiguration configuration, InventorySnapshot snapshot, String pagesJson,
                            String creativeItemsJson, String armorJson, String offhandJson, String hotbarJson,
                            String contentHash) {
        configuration.set("page-count", snapshot.getPageCount());
        configuration.set("current-page", snapshot.getCurrentPage());
        configuration.set("max-page", snapshot.getMaxPage());
        configuration.set("used-creative", snapshot.isUsedCreative());
        configuration.set("content-hash", contentHash);
        configuration.set("pages-json", pagesJson);
        configuration.set("creative-items-json", creativeItemsJson);
        configuration.set("armor-json", armorJson);
        configuration.set("offhand-json", offhandJson);
        configuration.set("hotbar-json", hotbarJson);
    }

    private void write(File file, YamlConfiguration configuration) throws IOException {
        File temp = File.createTempFile(file.getName(), ".tmp", file.getParentFile());
        try {
            configuration.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    @Override
    public List<InventorySnapshotSummary> list(String playerUUID, String serverId,
                                               long fromMillis, long toMillis, int limit) throws IOException {
        return select(playerUUID, serverId, fromMillis, toMillis, limit, null, true);
    }

    @Override
    public InventorySnapshot get(String playerUUID, String snapshotId, long createdAt) throws IOException {
        File file = snapshotFile(playerUUID, snapshotId, createdAt);
        if (!file.exists())
            return null;
        YamlConfiguration configuration = readMetadata(file, playerUUID);
        if (configuration == null || !hasValidJson(file, configuration))
            return null;
        InventorySnapshotSummary summary = readSummary(file, configuration);
        return new InventorySnapshot(summary.getId(), summary.getPlayerUUID(), summary.getPlayerName(),
                summary.getCreatedAt(), summary.getReason(), summary.getServerId(), summary.getCurrentPage(),
                summary.getMaxPage(), summary.isUsedCreative(), StringUtil.pagesFromJson(configuration.getString("pages-json")),
                StringUtil.itemsFromJson(configuration.getString("creative-items-json")),
                StringUtil.itemsFromJson(configuration.getString("armor-json"), 4),
                StringUtil.itemFromJson(configuration.getString("offhand-json")),
                StringUtil.itemsFromJson(configuration.getString("hotbar-json"), 9));
    }

    @Override
    public String getLastContentHash(String playerUUID, String serverId) throws IOException {
        for (File file : snapshotFiles(playerUUID)) {
            YamlConfiguration configuration = readMetadata(file, playerUUID);
            if (configuration != null && matchesServer(configuration, serverId))
                return configuration.getString("content-hash");
        }
        return null;
    }

    @Override
    public void prune(String playerUUID, String serverId, long cutoffMillis, int maxSnapshots) throws IOException {
        if (cutoffMillis <= 0 && maxSnapshots <= 0)
            return;
        long count = 0;
        for (File file : snapshotFiles(playerUUID)) {
            YamlConfiguration configuration = readMetadata(file, playerUUID);
            if (configuration == null || !matchesServer(configuration, serverId) || !hasValidJson(file, configuration))
                continue;
            count++;
            if ((cutoffMillis > 0 && configuration.getLong("created-at") < cutoffMillis)
                    || (maxSnapshots > 0 && count > maxSnapshots))
                Files.delete(file.toPath());
        }
    }

    @Override
    public void pruneAll(long cutoffMillis) throws IOException {
        if (cutoffMillis <= 0)
            return;
        File[] folders = directory.listFiles(File::isDirectory);
        if (folders == null)
            throw new IOException("Unable to list history directory: " + directory);
        for (File folder : folders) {
            try {
                playerFolder(folder.getName());
            } catch (IOException | IllegalArgumentException exception) {
                logger.warning("Skipping invalid history player directory " + folder + ": " + exception.getMessage());
                continue;
            }
            prune(folder.getName(), "", cutoffMillis, 0);
        }
    }

    private List<InventorySnapshotSummary> select(String playerUUID, String serverId, long from, long to,
                                                  int limit, InventorySnapshotSummary reference, boolean older) throws IOException {
        File folder = playerFolder(playerUUID);
        if (!folder.exists())
            return new ArrayList<>();
        int maximum = limit > 0 ? Math.min(limit, 1000) : 100;
        Comparator<InventorySnapshotSummary> order = older ? ORDER : ORDER.reversed();
        PriorityQueue<InventorySnapshotSummary> selected = new PriorityQueue<>(order);
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(folder.toPath(), "*.yml")) {
            for (Path path : paths) {
                File file = path.toFile();
                try {
                    long timestamp = timestamp(file);
                    if (timestamp < from || timestamp >= to)
                        continue;
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                YamlConfiguration config = readMetadata(file, playerUUID);
                if (config == null || !matchesServer(config, serverId))
                    continue;
                InventorySnapshotSummary summary = readSummary(file, config);
                if (reference != null && (older ? ORDER.compare(summary, reference) >= 0 : ORDER.compare(summary, reference) <= 0))
                    continue;
                selected.add(summary);
                if (selected.size() > maximum)
                    selected.poll();
            }
        }
        List<InventorySnapshotSummary> result = new ArrayList<>(selected);
        result.sort(order.reversed());
        return result;
    }

    @Override
    public InventorySnapshotSummary adjacent(InventorySnapshotSummary reference, String serverId,
                                             long fromMillis, long toMillis, boolean older) throws IOException {
        List<InventorySnapshotSummary> matches = select(reference.getPlayerUUID(), serverId, fromMillis, toMillis, 1, reference, older);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public List<InventorySnapshotSummary> expired(long cutoffMillis, String serverId, int limit) throws IOException {
        List<InventorySnapshotSummary> result = new ArrayList<>();
        try {
            if (expiryScan == null) {
                expiryScan = Files.walk(directory.toPath(), 2);
                expiryIterator = expiryScan.iterator();
            }
            int scanned = 0;
            while (scanned++ < 1000 && result.size() < Math.max(1, Math.min(200, limit)) && expiryIterator.hasNext()) {
                Path path = expiryIterator.next();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toString().endsWith(".yml"))
                    continue;
                File file = path.toFile();
                try {
                    if (timestamp(file) >= cutoffMillis)
                        continue;
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                YamlConfiguration config = readMetadata(file, file.getParentFile().getName());
                if (config != null && matchesServer(config, serverId))
                    result.add(readSummary(file, config));
            }
            if (!expiryIterator.hasNext())
                close();
        } catch (RuntimeException | IOException exception) {
            close();
            throw exception;
        }
        return result;
    }

    @Override
    public HistoryStoredRecord exportRecord(InventorySnapshotSummary summary) throws IOException {
        File file = snapshotFile(summary.getPlayerUUID(), summary.getId(), summary.getCreatedAt());
        YamlConfiguration config = readMetadata(file, summary.getPlayerUUID());
        if (config == null)
            return null;
        return new HistoryStoredRecord(summary, "yml", Files.readAllBytes(file.toPath()));
    }

    @Override
    public boolean deleteRecord(HistoryStoredRecord record) throws IOException {
        InventorySnapshotSummary summary = record.getSummary();
        Path path = snapshotFile(summary.getPlayerUUID(), summary.getId(), summary.getCreatedAt()).toPath();
        if (!Files.exists(path) || Files.size(path) > Settings.HISTORY_MAX_RECORD_BYTES
                || !Arrays.equals(Files.readAllBytes(path), record.getPayload()))
            return false;
        return Files.deleteIfExists(path);
    }

    @Override
    public void close() {
        if (expiryScan != null)
            expiryScan.close();
        expiryScan = null;
        expiryIterator = null;
    }

    private File playerFolder(String playerUUID) throws IOException {
        validateUUID(playerUUID);
        File folder = new File(directory, playerUUID).getCanonicalFile();
        if (!directory.equals(folder.getParentFile()))
            throw new IOException("History player directory escapes its parent: " + playerUUID);
        return folder;
    }

    private File snapshotFile(String playerUUID, String snapshotId, long createdAt) throws IOException {
        validateUUID(snapshotId);
        if (createdAt < 0)
            throw new IllegalArgumentException("Negative history timestamp");
        File folder = playerFolder(playerUUID);
        File candidate = new File(folder, createdAt + "-" + snapshotId + ".yml");
        if (Files.isSymbolicLink(candidate.toPath()))
            throw new IOException("History snapshot cannot be a symbolic link");
        File file = candidate.getCanonicalFile();
        if (!folder.equals(file.getParentFile()))
            throw new IOException("History snapshot path escapes its player directory");
        return file;
    }

    private List<File> snapshotFiles(String playerUUID) throws IOException {
        File folder = playerFolder(playerUUID);
        List<File> snapshots = new ArrayList<>();
        if (!folder.exists())
            return snapshots;
        File[] files = folder.listFiles((parent, name) -> name.endsWith(".yml"));
        if (files == null)
            throw new IOException("Unable to list history player directory: " + folder);
        for (File file : files) {
            try {
                if (!file.isFile() || Files.isSymbolicLink(file.toPath()))
                    throw new IllegalArgumentException("not a regular snapshot file");
                String id = snapshotId(file);
                snapshotFile(playerUUID, id, timestamp(file));
                snapshots.add(file);
            } catch (IOException | IllegalArgumentException exception) {
                logger.warning("Skipping invalid history snapshot path " + file + ": " + exception.getMessage());
            }
        }
        // Compare numeric prefixes so timestamps of different lengths still sort correctly.
        snapshots.sort(Comparator.comparingLong(this::timestamp).thenComparing(File::getName).reversed());
        return snapshots;
    }

    private YamlConfiguration readMetadata(File file, String playerUUID) {
        try {
            if (!file.isFile() || Files.isSymbolicLink(file.toPath()))
                throw new IllegalArgumentException("not a regular snapshot file");
            if (Files.size(file.toPath()) > Settings.HISTORY_MAX_RECORD_BYTES)
                throw new IllegalArgumentException("history record exceeds size limit; retained for recovery");
            String id = snapshotId(file);
            long createdAt = timestamp(file);
            if (!snapshotFile(playerUUID, id, createdAt).equals(file.getCanonicalFile()))
                throw new IllegalArgumentException("snapshot path does not match its identity");
            YamlConfiguration configuration = new YamlConfiguration();
            // loadConfiguration would swallow malformed YAML and return an empty configuration.
            configuration.load(file);
            String storedUUID = configuration.getString("player-uuid");
            validateUUID(storedUUID);
            Object storedTime = configuration.get("created-at");
            if (!playerUUID.equalsIgnoreCase(storedUUID)
                    || (!(storedTime instanceof Long) && !(storedTime instanceof Integer))
                    || configuration.getLong("created-at") != createdAt)
                throw new IllegalArgumentException("snapshot identity does not match its file name");
            if (!configuration.isInt("page-count") || !configuration.isInt("current-page")
                    || !configuration.isInt("max-page") || !configuration.isBoolean("used-creative")
                    || !configuration.isString("server-id") || !configuration.isString("content-hash")
                    || !configuration.isString("pages-json") || !configuration.isString("creative-items-json"))
                throw new IllegalArgumentException("missing or invalid snapshot fields");
            // Equipment keys are absent in snapshots written before equipment support.
            for (String key : new String[]{"armor-json", "offhand-json", "hotbar-json"})
                if (configuration.contains(key) && !configuration.isString(key))
                    throw new IllegalArgumentException("missing or invalid snapshot fields");
            return configuration;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            logger.warning("Skipping corrupt history snapshot " + file + ": " + exception.getMessage());
            return null;
        }
    }

    private InventorySnapshotSummary readSummary(File file, YamlConfiguration configuration) {
        String storedReason = configuration.getString("reason");
        SnapshotReason reason;
        try {
            reason = SnapshotReason.valueOf(storedReason);
        } catch (IllegalArgumentException | NullPointerException exception) {
            logger.warning("Invalid history reason in " + file + ": " + storedReason + "; using PERIODIC.");
            reason = SnapshotReason.PERIODIC;
        }
        return new InventorySnapshotSummary(snapshotId(file), configuration.getString("player-uuid"),
                configuration.getString("player-name"), configuration.getLong("created-at"), reason,
                configuration.getString("server-id"), configuration.getInt("current-page"),
                configuration.getInt("max-page"), configuration.getInt("page-count"), configuration.getBoolean("used-creative"));
    }

    private boolean hasValidJson(File file, YamlConfiguration configuration) {
        try {
            // Do not decode ItemStacks here. Retention must leave malformed payloads for recovery.
            JsonElement pages = JsonParser.parseString(configuration.getString("pages-json"));
            if (!pages.isJsonObject())
                throw new IllegalArgumentException("invalid item JSON structure");
            for (Map.Entry<String, JsonElement> entry : pages.getAsJsonObject().entrySet()) {
                Integer.parseInt(entry.getKey());
                if (!isSlotArray(entry.getValue()))
                    throw new IllegalArgumentException("invalid item JSON structure");
            }
            if (!isSlotArray(JsonParser.parseString(configuration.getString("creative-items-json"))))
                throw new IllegalArgumentException("invalid item JSON structure");
            for (String key : new String[]{"armor-json", "offhand-json", "hotbar-json"})
                if (configuration.contains(key)
                        && !isSlotArray(JsonParser.parseString(configuration.getString(key))))
                    throw new IllegalArgumentException("invalid item JSON structure");
            return true;
        } catch (RuntimeException exception) {
            logger.warning("Skipping corrupt history snapshot " + file + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean isSlotArray(JsonElement element) {
        if (!element.isJsonArray())
            return false;
        for (JsonElement slot : element.getAsJsonArray()) {
            if (!slot.isJsonNull() && !(slot.isJsonPrimitive() && slot.getAsJsonPrimitive().isString()))
                return false;
        }
        return true;
    }

    private boolean matchesServer(YamlConfiguration configuration, String serverId) {
        return serverId == null || serverId.isEmpty() || serverId.equals(configuration.getString("server-id"));
    }

    private String snapshotId(File file) {
        String name = file.getName();
        int separator = name.indexOf('-');
        if (separator <= 0 || !name.endsWith(".yml") || separator >= name.length() - 4)
            throw new IllegalArgumentException("invalid snapshot file name");
        String id = name.substring(separator + 1, name.length() - 4);
        validateUUID(id);
        return id;
    }

    private long timestamp(File file) {
        String name = file.getName();
        int separator = name.indexOf('-');
        if (separator <= 0)
            throw new IllegalArgumentException("invalid snapshot timestamp");
        String prefix = name.substring(0, separator);
        long createdAt = Long.parseLong(prefix);
        if (createdAt < 0 || !Long.toString(createdAt).equals(prefix))
            throw new IllegalArgumentException("invalid snapshot timestamp");
        return createdAt;
    }

    private void validateUUID(String value) {
        if (value == null || value.length() != 36 || !UUID.fromString(value).toString().equalsIgnoreCase(value))
            throw new IllegalArgumentException("Invalid history UUID: " + value);
    }
}
