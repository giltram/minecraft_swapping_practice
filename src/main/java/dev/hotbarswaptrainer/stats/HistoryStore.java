package dev.hotbarswaptrainer.stats;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;

/** Instance-wide append-only history; directories are injected, never inferred from a save/player path. */
public final class HistoryStore implements ResultStore {
    public static final int MAX_RECORDS = 2048;
    public static final long MAX_SCAN_BYTES = 32L * 1024 * 1024;
    private final Path root;
    private final InMemoryResultStore latest = new InMemoryResultStore();
    private final Commit commit;
    interface Commit { void move(Path from, Path to) throws IOException; }
    public HistoryStore(Path root) { this(root, (from, to) -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)); }
    HistoryStore(Path root, Commit commit) { this.root = root.toAbsolutePath().normalize(); this.commit = commit; }

    @Override public synchronized void save(UUID player, SessionResult result) throws IOException {
        Objects.requireNonNull(player); Objects.requireNonNull(result);
        if (result.getTasks().size() > HistoryJson.MAX_TASKS) throw new IOException("History task limit exceeded");
        byte[] content = HistoryJson.encode(player, result);
        Path directory = root.resolve(player.toString()), target = directory.resolve(result.getSessionId() + ".json");
        safeParents(directory); Files.createDirectories(directory); safeParents(directory);
        Path lockPath = directory.resolve(".history.lock");
        if (Files.isSymbolicLink(lockPath)) throw new IOException("History lock is a symlink");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                byte[] original = readBounded(target);
                SessionResult parsed = HistoryJson.decode(original, player, result.getSessionId());
                if (!Arrays.equals(content, HistoryJson.encode(player, parsed))) throw new IOException("History ID conflict; original retained");
            } else {
                Path temporary = Files.createTempFile(directory, ".history-", ".tmp");
                try {
                    try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                        ByteBuffer buffer = ByteBuffer.wrap(content);
                        while (buffer.hasRemaining()) channel.write(buffer);
                        channel.force(true);
                    }
                    byte[] reread = readBounded(temporary);
                    HistoryJson.decode(reread, player, result.getSessionId());
                    if (!Arrays.equals(content, reread)) throw new IOException("History verification failed");
                    commit.move(temporary, target); // No fallback, no replacement of an existing final record.
                } finally { Files.deleteIfExists(temporary); }
            }
        }
        latest.save(player, result);
    }
    @Override public synchronized Optional<SessionResult> findLatest(UUID player) { return latest.findLatest(player); }
    @Override public synchronized void forget(UUID player) { latest.forget(player); }
    @Override public synchronized void clear() { latest.clear(); }

    public HistorySnapshot read(UUID player) {
        List<SessionResult> records = new ArrayList<>(); List<String> errors = new ArrayList<>();
        Path directory = root.resolve(player.toString());
        try {
            safeParents(directory);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return new HistorySnapshot(player, records, errors);
            long bytes = 0; int count = 0;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "*.json")) {
                for (Path file : entries) {
                    if (++count > MAX_RECORDS) { errors.add("History catalogue limit reached; results are incomplete."); break; }
                    try {
                        UUID session = UUID.fromString(file.getFileName().toString().replaceFirst("\\.json$", ""));
                        if (!file.getFileName().toString().equals(session + ".json")) throw new IOException("Invalid history filename");
                        long size = Files.size(file);
                        if (size > HistoryJson.MAX_BYTES) throw new IOException("History exceeds 2 MiB");
                        if (bytes + size > MAX_SCAN_BYTES) { errors.add("History read budget reached; results are incomplete."); break; }
                        bytes += size;
                        records.add(HistoryJson.decode(readBounded(file), player, session));
                    } catch (IOException | RuntimeException failure) {
                        errors.add(file.getFileName() + ": " + failure.getMessage());
                    }
                }
            }
        } catch (IOException | RuntimeException failure) { errors.add("Cannot read history: " + failure.getMessage()); }
        return new HistorySnapshot(player, records, errors);
    }
    private static byte[] readBounded(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("History must be a regular file");
        try (InputStream input = Files.newInputStream(path); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = input.read(buffer)) != -1) {
                if (output.size() + length > HistoryJson.MAX_BYTES) throw new IOException("History exceeds 2 MiB");
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }
    private void safeParents(Path path) throws IOException {
        for (Path parent = path; parent != null && parent.startsWith(root); parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw new IOException("History symlink directory refused");
    }
}
