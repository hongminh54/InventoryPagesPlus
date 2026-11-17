package me.cortezromeo.inventorypagesplus.history;

import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.storage.InventoryHistoryStorage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class HistoryRetention implements AutoCloseable {
    private final Path directory;
    private DirectoryStream<Path> archiveScan;
    private Iterator<Path> archiveIterator;

    public HistoryRetention(Path dataFolder) throws IOException {
        Path root = dataFolder.toRealPath();
        directory = root.resolve("history-archives");
        if (Files.isSymbolicLink(directory))
            throw new IOException("History archive directory must not be a symbolic link.");
    }

    public int process(InventoryHistoryStorage storage, long cutoff, String serverId, int limit, boolean archive) throws Exception {
        List<HistoryStoredRecord> records = new ArrayList<>();
        long bytes = 0;
        for (InventorySnapshotSummary summary : storage.expired(cutoff, serverId, limit)) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("History maintenance interrupted.");
            HistoryStoredRecord record = storage.exportRecord(summary);
            if (record == null)
                continue;
            if (record.getPayload().length > Settings.HISTORY_MAX_RECORD_BYTES)
                throw new IOException("History record exceeds archive limit; source retained.");
            records.add(record);
            bytes += record.getPayload().length;
            if (bytes >= 16 * 1024 * 1024)
                break;
        }
        if (archive && !records.isEmpty())
            writeArchive(records);
        int removed = 0;
        for (HistoryStoredRecord record : records) {
            if (Thread.currentThread().isInterrupted())
                break;
            if (storage.deleteRecord(record))
                removed++;
        }
        return removed;
    }

    public Path writeArchive(List<HistoryStoredRecord> records) throws Exception {
        if (Files.isSymbolicLink(directory))
            throw new IOException("History archive directory must not be a symbolic link.");
        Files.createDirectories(directory);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long newest = 0;
        for (HistoryStoredRecord record : records) {
            InventorySnapshotSummary summary = record.getSummary();
            UUID.fromString(summary.playerUUID());
            UUID.fromString(summary.id());
            if (!record.entryName().matches("[a-fA-F0-9-]{36}/[a-fA-F0-9-]{36}\\.(json|yml)"))
                throw new IOException("Invalid history archive entry name.");
            digest.update(record.entryName().getBytes(StandardCharsets.UTF_8));
            digest.update(record.getPayload());
            newest = Math.max(newest, summary.createdAt());
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) {
            hash.append(Character.forDigit((value & 255) >>> 4, 16));
            hash.append(Character.forDigit(value & 15, 16));
        }
        Path destination = directory.resolve("archive-" + newest + "-" + hash + ".zip");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verify(destination, records);
            return destination;
        }
        Path temporary = Files.createTempFile(directory, "pending-", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temporary.toFile());
                 ZipOutputStream zip = new ZipOutputStream(file)) {
                for (HistoryStoredRecord record : records) {
                    zip.putNextEntry(new ZipEntry(record.entryName()));
                    zip.write(record.getPayload());
                    zip.closeEntry();
                }
                zip.finish();
                file.getChannel().force(true);
            }
            verify(temporary, records);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            verify(destination, records);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void verify(Path path, List<HistoryStoredRecord> records) throws IOException {
        if (Files.isSymbolicLink(path))
            throw new IOException("History archive must not be a symbolic link.");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            if (zip.size() != records.size())
                throw new IOException("History archive entry count mismatch; source retained.");
            for (HistoryStoredRecord record : records) {
                ZipEntry entry = zip.getEntry(record.entryName());
                if (entry == null || entry.getSize() != record.getPayload().length)
                    throw new IOException("History archive entry missing or truncated; source retained.");
                try (InputStream input = zip.getInputStream(entry)) {
                    if (!Arrays.equals(record.getPayload(), input.readNBytes(record.getPayload().length + 1)))
                        throw new IOException("History archive verification failed; source retained.");
                }
            }
        }
    }

    public int cleanArchives(long cutoff, int limit) throws IOException {
        if (cutoff <= 0 || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            close();
            return 0;
        }
        int removed = 0;
        int inspected = 0;
        try {
            if (archiveScan == null) {
                archiveScan = Files.newDirectoryStream(directory);
                archiveIterator = archiveScan.iterator();
            }
            while (inspected < 1000 && removed < limit && archiveIterator.hasNext()) {
                Path path = archiveIterator.next();
                inspected++;
                String name = path.getFileName().toString();
                if (!name.matches("archive-[0-9]+-[a-f0-9]{64}\\.zip") || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    continue;
                long newest;
                try {
                    newest = Long.parseLong(name.split("-")[1]);
                } catch (NumberFormatException exception) {
                    continue;
                }
                if (newest < cutoff && Files.deleteIfExists(path))
                    removed++;
            }
            if (!archiveIterator.hasNext())
                close();
        } catch (IOException | RuntimeException exception) {
            try {
                close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
        return removed;
    }

    @Override
    public void close() throws IOException {
        DirectoryStream<Path> scan = archiveScan;
        archiveScan = null;
        archiveIterator = null;
        if (scan != null)
            scan.close();
    }
}
