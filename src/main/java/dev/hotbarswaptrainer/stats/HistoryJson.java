package dev.hotbarswaptrainer.stats;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.hotbarswaptrainer.task.TrainingMode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict bounded in-memory schema codec. No file ownership or registry access. */
final class HistoryJson {
    static final int MAX_BYTES = 2 * 1024 * 1024, MAX_TASKS = 4096;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private HistoryJson() { }

    static byte[] encode(UUID player, SessionResult result) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1); root.addProperty("playerId", player.toString());
        root.addProperty("sessionId", result.getSessionId().toString()); root.addProperty("mode", result.getMode().name());
        root.addProperty("profileHash", result.getProfileHash()); root.addProperty("requestedSuccesses", result.getRequestedSuccesses());
        root.add("metadata", GSON.toJsonTree(result.getMetadata()));
        root.add("tasks", GSON.toJsonTree(result.getTasks()));
        byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        decode(bytes, player, result.getSessionId()); // All writes obey the read contract.
        return bytes;
    }

    static SessionResult decode(byte[] bytes, UUID player, UUID session) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("History exceeds 2 MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            JsonElement value;
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false); value = read(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing history content");
            }
            JsonObject root = object(value, "schemaVersion,playerId,sessionId,mode,profileHash,requestedSuccesses,metadata,tasks");
            if (number(root, "schemaVersion") != 1) throw new IOException("Unknown history schemaVersion");
            if (!uuid(root, "playerId").equals(player) || !uuid(root, "sessionId").equals(session)) throw new IOException("History identity mismatch");
            String hash = string(root, "profileHash", 128);
            SessionMetadata metadata = null;
            if (!root.get("metadata").isJsonNull()) {
                JsonObject m = object(root.get("metadata"), "playerId,startedAtMillis,endedAtMillis,profileId,profileName,timingVersion,scoringVersion,environment,targetHints,endState");
                if (!uuid(m, "playerId").equals(player)) throw new IOException("Metadata player mismatch");
                if (!m.get("targetHints").isJsonPrimitive() || !m.getAsJsonPrimitive("targetHints").isBoolean()) throw new IOException("Invalid hints");
                metadata = new SessionMetadata(player, number(m, "startedAtMillis"), number(m, "endedAtMillis"),
                        string(m, "profileId", 128), string(m, "profileName", 256), m.get("targetHints").getAsBoolean(),
                        string(m, "timingVersion", 128), string(m, "scoringVersion", 128), string(m, "environment", 128),
                        SessionMetadata.EndState.valueOf(string(m, "endState", 32)));
                if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid profile hash");
            }
            if (!root.get("tasks").isJsonArray()) throw new IOException("Expected task array");
            List<TaskResult> tasks = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
            long sum = 0;
            for (JsonElement element : root.getAsJsonArray("tasks")) {
                if (tasks.size() >= MAX_TASKS) throw new IOException("Too many tasks");
                JsonObject t = object(element, "taskId,outcome,elapsedNanos,swapAttempts,effectiveSwaps,slotChanges,noOpSwaps,forbiddenActions,invalidReason");
                UUID id = uuid(t, "taskId");
                if (!ids.add(id)) throw new IOException("Duplicate task ID");
                TaskResult task = new TaskResult(id, TaskResult.Outcome.valueOf(string(t, "outcome", 16)), number(t, "elapsedNanos"),
                        integer(t, "swapAttempts"), integer(t, "effectiveSwaps"), integer(t, "slotChanges"),
                        integer(t, "noOpSwaps"), integer(t, "forbiddenActions"), string(t, "invalidReason", 2048));
                if (task.getOutcome() == TaskResult.Outcome.SUCCESS) {
                    if (task.getElapsedNanos() == 0) throw new IOException("Zero successful duration");
                    sum = Math.addExact(sum, task.getElapsedNanos());
                }
                tasks.add(task);
            }
            SessionResult result = new SessionResult(session, TrainingMode.valueOf(string(root, "mode", 32)), hash,
                    integer(root, "requestedSuccesses"), tasks, metadata);
            if (result.getRequestedSuccesses() > 0 && result.getSuccessCount() > result.getRequestedSuccesses()) throw new IOException("Success count exceeds goal");
            if (metadata != null && metadata.endState == SessionMetadata.EndState.COMPLETED
                    && (result.getRequestedSuccesses() == 0 || result.getSuccessCount() != result.getRequestedSuccesses())) throw new IOException("Invalid completed session");
            return result;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            throw new IOException("Invalid history: " + failure.getMessage(), failure);
        }
    }

    private static JsonElement read(JsonReader r, int depth) throws IOException {
        if (depth > 4) throw new IOException("History nesting limit");
        switch (r.peek()) {
            case BEGIN_OBJECT:
                JsonObject o = new JsonObject(); r.beginObject(); int fields = 0;
                while (r.hasNext()) {
                    String key = r.nextName();
                    if (++fields > 16 || key.length() > 64 || o.has(key)) throw new IOException("Unknown/duplicate/too many fields");
                    o.add(key, read(r, depth + 1));
                }
                r.endObject(); return o;
            case BEGIN_ARRAY:
                JsonArray a = new JsonArray(); r.beginArray();
                while (r.hasNext()) { if (a.size() >= MAX_TASKS) throw new IOException("Too many tasks"); a.add(read(r, depth + 1)); }
                r.endArray(); return a;
            case STRING:
                String s = r.nextString(); if (s.length() > 2048) throw new IOException("History string too long"); return new JsonPrimitive(s);
            case NUMBER:
                String n = r.nextString(); if (!n.matches("0|[1-9][0-9]{0,18}")) throw new IOException("Expected bounded unsigned integer");
                return new JsonPrimitive(Long.parseLong(n));
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("Invalid history token");
        }
    }
    private static JsonObject object(JsonElement e, String keys) throws IOException {
        if (!e.isJsonObject()) throw new IOException("Expected object");
        JsonObject o = e.getAsJsonObject(); Set<String> expected = new HashSet<>(Arrays.asList(keys.split(",")));
        if (!o.entrySet().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(expected)) throw new IOException("Missing/unknown history fields");
        return o;
    }
    private static String string(JsonObject o, String key, int maximum) throws IOException {
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().length() > maximum) throw new IOException("Invalid string: " + key);
        return e.getAsString();
    }
    private static long number(JsonObject o, String key) throws IOException {
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IOException("Invalid integer: " + key);
        return e.getAsLong();
    }
    private static int integer(JsonObject o, String key) throws IOException {
        long n = number(o, key); if (n > Integer.MAX_VALUE) throw new IOException("Integer overflow: " + key); return (int) n;
    }
    private static UUID uuid(JsonObject o, String key) throws IOException {
        String text = string(o, key, 36); UUID id = UUID.fromString(text);
        if (!id.toString().equals(text)) throw new IOException("Noncanonical UUID"); return id;
    }
}
