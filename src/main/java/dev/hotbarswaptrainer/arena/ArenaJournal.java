package dev.hotbarswaptrainer.arena;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Strict ownership and write-ahead operation journal; never adopts an existing directory. */
public final class ArenaJournal {
    public static final String DIRECTORY = "hotbar-swap-trainer-practice";
    public static final String FILE = ".hotbar-swap-trainer-arena.json";
    public enum Phase { CREATING, INITIALIZING, RESETTING, VERIFYING, READY }
    private static final String NONE = "none";
    private final Path world;

    public ArenaJournal(Path world) { this.world = world.toAbsolutePath().normalize(); }
    public Path world() { return world; }

    public Record openOrReserve() throws IOException {
        safePath(world.getParent());
        if (Files.exists(world, LinkOption.NOFOLLOW_LINKS)) return read();
        Files.createDirectory(world); // Exclusive reservation; a collision is an error, never an adoption.
        Record initial = new Record(UUID.randomUUID().toString(), UUID.randomUUID().toString(), NONE, Phase.CREATING);
        write(initial, null);
        return read();
    }

    public Record read() throws IOException {
        safePath(world);
        Path file = world.resolve(FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 8192)
            throw new IOException("Missing or invalid arena ownership journal: " + file);
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (reader.peek() != JsonToken.STRING || fields.containsKey(key)) throw new IOException("Invalid journal field");
                fields.put(key, reader.nextString());
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || fields.size() != 8
                    || !fields.keySet().containsAll(Arrays.asList("version", "id", "path", "spec", "bounds", "operation", "previous", "phase"))
                    || !"1".equals(fields.get("version")) || !ArenaSpec.ID.equals(fields.get("spec"))
                    || !ArenaSpec.BOUNDS.equals(fields.get("bounds")) || !world.toString().equals(fields.get("path")))
                throw new IOException("Unsupported or incorrectly bound arena ownership journal");
            uuid(fields.get("id")); uuid(fields.get("operation"));
            if (!NONE.equals(fields.get("previous"))) uuid(fields.get("previous"));
            Phase phase = Phase.valueOf(fields.get("phase"));
            if (phase == Phase.CREATING && !NONE.equals(fields.get("previous"))) throw new IOException("Invalid creation state");
            return new Record(fields.get("id"), fields.get("operation"), fields.get("previous"), phase);
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            throw new IOException("Damaged arena ownership journal", malformed);
        }
    }

    public Record transition(Record expected, Phase phase) throws IOException {
        if (!allowed(expected.phase, phase)) throw new IOException("Invalid arena lifecycle transition");
        Record next = phase == Phase.RESETTING
                ? new Record(expected.id, UUID.randomUUID().toString(), expected.operation, phase)
                : new Record(expected.id, expected.operation, expected.previous, phase);
        write(next, expected);
        return read();
    }

    private static boolean allowed(Phase from, Phase to) {
        return (from == Phase.CREATING && to == Phase.INITIALIZING)
                || ((from == Phase.INITIALIZING || from == Phase.RESETTING) && to == Phase.VERIFYING)
                || (from == Phase.VERIFYING && to == Phase.READY)
                || (from == Phase.READY && to == Phase.RESETTING);
    }

    private void write(Record next, Record expected) throws IOException {
        safePath(world);
        if (expected != null && !expected.equals(read())) throw new IOException("Arena journal changed during operation");
        Path destination = world.resolve(FILE);
        if (expected == null && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Ownership already exists");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", "1"); fields.put("id", next.id); fields.put("path", world.toString());
        fields.put("spec", ArenaSpec.ID); fields.put("bounds", ArenaSpec.BOUNDS);
        fields.put("operation", next.operation); fields.put("previous", next.previous); fields.put("phase", next.phase.name());
        byte[] data = (new Gson().toJson(fields) + "\n").getBytes(StandardCharsets.UTF_8);
        Path temporary = Files.createTempFile(world, ".arena-journal-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Refuse symlinks at every existing component, including world data subdirectories. */
    public static void safePath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize(), current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symlinked arena path: " + current);
        }
    }

    private static void uuid(String text) {
        if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException("Invalid UUID");
    }

    public static final class Record {
        public final String id, operation, previous;
        public final Phase phase;
        private Record(String id, String operation, String previous, Phase phase) {
            this.id = id; this.operation = operation; this.previous = previous; this.phase = phase;
        }
        public boolean firstOperation() { return NONE.equals(previous); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Record)) return false;
            Record that = (Record) other;
            return id.equals(that.id) && operation.equals(that.operation) && previous.equals(that.previous) && phase == that.phase;
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, operation, previous, phase); }
    }
}
