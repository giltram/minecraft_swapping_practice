package dev.hotbarswaptrainer.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.hotbarswaptrainer.task.TrainingMode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** Client-only JSON I/O. Any failed load locks writes for this store's lifetime. */
final class ClientOptionsStore {
    static final int MAX_BYTES = 16 * 1024;
    private final Path file;
    private boolean loaded;
    private boolean savingAllowed;

    ClientOptionsStore(Path file) { this.file = file.toAbsolutePath(); }

    ClientOptions load() throws IOException {
        if (loaded) throw new IllegalStateException("Options must be loaded once per client process");
        loaded = true;
        try {
            byte[] bytes;
            try {
                if (Files.size(file) > MAX_BYTES) throw new IOException("Options exceed 16 KiB");
                bytes = Files.readAllBytes(file);
            } catch (NoSuchFileException missing) {
                savingAllowed = true;
                return ClientOptions.defaults();
            }
            if (bytes.length > MAX_BYTES) throw new IOException("Options exceed 16 KiB");
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            ClientOptions options = parse(json);
            savingAllowed = true;
            return options;
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            throw new IOException("Cannot read " + file + ": " + failure.getMessage()
                    + ". File preserved; saving disabled. Repair or move it, then restart.", failure);
        }
    }

    void save(ClientOptions options) throws IOException {
        if (!savingAllowed) throw new IOException("Options saving disabled after an unsuccessful load; repair or move " + file + " and restart.");
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", ClientOptions.SCHEMA_VERSION);
        json.addProperty("mode", options.mode.name());
        json.addProperty("profile", options.profile);
        json.addProperty("goal", options.goal);
        json.addProperty("showHints", options.showHints);
        json.addProperty("showSwapCounts", options.showSwapCounts);
        byte[] bytes = (new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());
        Path pending = Files.createTempFile(file.getParent(), ".client-options-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Same-directory rename only. Unsupported atomic replacement is a reported save failure.
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    private static ClientOptions parse(String json) throws IOException {
        TrainingMode mode = null;
        String profile = null;
        Integer schema = null;
        Integer goal = null;
        Boolean showHints = null;
        boolean showSwapCounts = false; // Existing schema-1 files predate the optional HUD preference.
        Set<String> seen = new HashSet<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!seen.add(key)) throw new IOException("Duplicate field: " + key);
                switch (key) {
                    case "schemaVersion": schema = integer(reader); break;
                    case "mode": mode = TrainingMode.valueOf(string(reader)); break;
                    case "profile": profile = string(reader); break;
                    case "goal": goal = integer(reader); break;
                    case "showHints":
                        if (reader.peek() != JsonToken.BOOLEAN) throw new IOException("showHints must be a boolean");
                        showHints = reader.nextBoolean();
                        break;
                    case "showSwapCounts":
                        if (reader.peek() != JsonToken.BOOLEAN) throw new IOException("showSwapCounts must be a boolean");
                        showSwapCounts = reader.nextBoolean();
                        break;
                    default: throw new IOException("Unknown field: " + key);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON content");
        }
        if (schema == null || schema != ClientOptions.SCHEMA_VERSION) throw new IOException("Supported schemaVersion is 1");
        if (mode == null || profile == null || goal == null || showHints == null) throw new IOException("Missing option field");
        return new ClientOptions(mode, profile, goal, showHints, showSwapCounts);
    }

    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IOException("Expected a string");
        return reader.nextString();
    }

    private static int integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IOException("Expected a nonnegative integer");
        String text = reader.nextString();
        if (!text.matches("0|[1-9][0-9]*")) throw new IOException("Expected a nonnegative integer");
        return Integer.parseInt(text);
    }
}
