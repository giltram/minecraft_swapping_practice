package dev.hotbarswaptrainer.profile;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns profile file I/O at explicit reload/edit boundaries. Invalid files remain untouched. */
public final class ProfileRepository {
    static final int MAX_PROFILE_BYTES = ProfileJson.MAX_PROFILE_BYTES;
    private final Path directory;
    private final ProfileJson codec;
    private final AtomicReplacement replacement;

    public ProfileRepository(Path directory, ItemCatalog catalog) {
        this(directory, catalog, (pending, selected) -> Files.move(pending, selected,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    // Package-private filesystem fault seam; production always requests atomic replacement.
    interface AtomicReplacement { void replace(Path pending, Path selected) throws IOException; }

    ProfileRepository(Path directory, ItemCatalog catalog, AtomicReplacement replacement) {
        this.directory = directory.toAbsolutePath().normalize();
        this.codec = new ProfileJson(catalog);
        this.replacement = replacement;
    }

    public void installDefaultIfMissing() throws IOException {
        Files.createDirectories(directory);
        Path destination = directory.resolve("default.json");
        if (Files.exists(destination)) return;
        try (InputStream stream = ProfileRepository.class.getResourceAsStream("/defaults/default.json")) {
            if (stream == null) throw new IOException("Bundled default profile is missing");
            Files.copy(stream, destination);
        }
    }

    public ProfileLoadResult loadAll() {
        List<LoadoutProfile> profiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            for (Path file : listProfileFiles()) {
                try {
                    LoadoutProfile profile = load(file);
                    if (!ids.add(profile.getId())) throw new ProfileException(file + ": duplicate profile id " + profile.getId());
                    profiles.add(profile);
                } catch (ProfileException | IOException failure) {
                    errors.add(failure.getMessage());
                }
            }
        } catch (IOException failure) {
            errors.add(directory + ": " + failure.getMessage());
        }
        return new ProfileLoadResult(profiles, errors);
    }

    /** Candidate files for a local selector that must retain explicit source identity. May include invalid profiles. */
    public List<Path> listProfileFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : paths) files.add(path);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return Collections.unmodifiableList(files);
    }

    public LoadoutProfile load(Path file) throws IOException, ProfileException {
        return parse(text(readBytes(file)), file.toString());
    }

    public LoadoutProfile parse(String json, String source) throws ProfileException { return codec.parse(json, source); }
    public static String slotName(int index) { return ProfileJson.slotName(index); }

    /** A single-use optimistic edit binding. Reopen after a successful save. Not thread-safe. */
    public static final class Edit {
        private final ProfileRepository owner;
        private final Path source;
        private final byte[] original;
        private final LoadoutProfile expected;
        private final ProfileDraft draft;
        private boolean saved;

        private Edit(ProfileRepository owner, Path source, byte[] original, LoadoutProfile expected) {
            this.owner = owner;
            this.source = source;
            this.original = original;
            this.expected = expected;
            this.draft = ProfileJson.draft(text(original));
        }

        public ProfileDraft getDraft() { return draft; }
    }

    /** Select an existing direct-child JSON file explicitly; never redirect an edit by ID. */
    public Edit beginEdit(Path source, LoadoutProfile expected) throws IOException, ProfileException {
        Path selected = source.toAbsolutePath().normalize();
        if (!directory.equals(selected.getParent()) || !selected.getFileName().toString().endsWith(".json")) {
            throw new ProfileException("Select a profile JSON directly inside " + directory);
        }
        byte[] original = readBytes(selected);
        LoadoutProfile current = parse(text(original), selected.toString());
        if (!current.getId().equals(expected.getId()) || !current.getHash().equals(expected.getHash())) {
            throw new ProfileException("Profile changed. Reopen the editor before saving.");
        }
        requireUniqueId(selected, expected.getId());
        return new Edit(this, selected, original, current);
    }

    /** Does not read/write files or require applicability to Full mode. Uses the injected catalog. */
    public ProfileDraftValidation validateDraft(ProfileDraft draft) {
        try {
            return ProfileDraftValidation.valid(parse(serialize(ProfileJson.encode(draft)), "draft"));
        } catch (ProfileException failure) {
            return ProfileDraftValidation.invalid(failure.getMessage());
        }
    }

    public LoadoutProfile save(Edit edit) throws IOException, ProfileException {
        if (edit == null || edit.owner != this) throw new ProfileException("Edit belongs to another repository");
        if (edit.saved) throw new ProfileException("Edit already saved. Reopen the editor before saving.");
        ProfileDraftValidation validation = validateDraft(edit.draft);
        if (!validation.isValid()) throw new ProfileException(validation.getError());
        LoadoutProfile result = saveDocument(edit, ProfileJson.encode(edit.draft), validation.getProfile());
        edit.saved = true;
        return result;
    }

    /** Move complete raw rules: sourcePositions[destination] names its old position. */
    public LoadoutProfile saveSlotOrder(LoadoutProfile expected, int[] sourcePositions) throws IOException, ProfileException {
        if (sourcePositions == null || sourcePositions.length != LoadoutProfile.SLOT_COUNT) {
            throw new ProfileException("Layout must contain all ten positions");
        }
        boolean[] seen = new boolean[LoadoutProfile.SLOT_COUNT];
        List<SlotRule> expectedRules = new ArrayList<>();
        for (int source : sourcePositions) {
            if (source < 0 || source >= seen.length || seen[source]) {
                throw new ProfileException("Layout must use each source position exactly once");
            }
            seen[source] = true;
            expectedRules.add(expected.getSlots().get(source));
        }
        Path selected = null;
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : paths) {
                if (!expected.getId().equals(declaredId(readBytes(path)))) continue;
                if (selected != null) throw new ProfileException("Duplicate profile id: " + expected.getId());
                selected = path;
            }
        }
        if (selected == null) throw new ProfileException("Profile unavailable. Reopen the layout editor.");
        Edit edit = beginEdit(selected, expected);
        JsonObject root = new JsonParser().parse(text(edit.original)).getAsJsonObject();
        JsonObject[] oldSlots = new JsonObject[LoadoutProfile.SLOT_COUNT];
        for (JsonElement element : root.getAsJsonArray("slots")) {
            JsonObject slot = element.getAsJsonObject();
            for (int index = 0; index < oldSlots.length; index++) {
                if (slotName(index).equals(slot.get("slot").getAsString())) oldSlots[index] = slot;
            }
        }
        JsonArray slots = new JsonArray();
        for (int destination = 0; destination < sourcePositions.length; destination++) {
            JsonObject slot = oldSlots[sourcePositions[destination]];
            slot.addProperty("slot", slotName(destination));
            slots.add(slot);
        }
        root.add("slots", slots);
        return saveDocument(edit, root, new LoadoutProfile(expected.getId(), expected.getName(), expectedRules));
    }

    private LoadoutProfile saveDocument(Edit edit, JsonObject root, LoadoutProfile intended) throws IOException, ProfileException {
        String updated = serialize(root);
        byte[] bytes = updated.getBytes(StandardCharsets.UTF_8);
        LoadoutProfile result = parse(updated, edit.source.toString());
        if (!intended.getHash().equals(result.getHash()) || !edit.expected.getId().equals(result.getId())) {
            throw new ProfileException("Serialized profile changed semantics or ID");
        }
        checkSource(edit);
        Path pending = Files.createTempFile(directory, ".profile-", ".tmp");
        try {
            writeForced(pending, bytes, false);
            LoadoutProfile pendingProfile = load(pending);
            if (!result.getHash().equals(pendingProfile.getHash())) throw new ProfileException("Temporary profile changed semantics");
            checkSource(edit);
            Path backup = directory.resolve(edit.source.getFileName() + "." + UUID.randomUUID() + ".bak");
            writeForced(backup, edit.original, true);
            checkSource(edit);
            try {
                replacement.replace(pending, edit.source);
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException("Atomic profile replacement is unsupported; original profile retained", failure);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
        return result;
    }

    private static String serialize(JsonObject root) throws ProfileException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PROFILE_BYTES) {
            throw new ProfileException("Saved profile would exceed 1 MiB");
        }
        return json;
    }

    private void checkSource(Edit edit) throws IOException, ProfileException {
        byte[] currentBytes = readBytes(edit.source);
        if (!Arrays.equals(edit.original, currentBytes)) {
            throw new ProfileException("Profile changed. Reopen the editor before saving.");
        }
        LoadoutProfile current = parse(text(currentBytes), edit.source.toString());
        if (!edit.expected.getHash().equals(current.getHash())) throw new ProfileException("Profile changed during validation");
        requireUniqueId(edit.source, edit.expected.getId());
        if (!Arrays.equals(edit.original, readBytes(edit.source))) {
            throw new ProfileException("Profile changed. Reopen the editor before saving.");
        }
    }

    private void requireUniqueId(Path selected, String id) throws IOException, ProfileException {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : paths) {
                if (!path.equals(selected) && id.equals(declaredId(readBytes(path)))) {
                    throw new ProfileException("Duplicate profile id: " + id);
                }
            }
        }
    }

    // Even a schema-invalid file can make an ID ambiguous. Unidentifiable broken files are left alone.
    private static String declaredId(byte[] bytes) {
        try {
            JsonElement root = new JsonParser().parse(text(bytes));
            if (!root.isJsonObject()) return null;
            JsonElement id = root.getAsJsonObject().get("id");
            return id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString() ? id.getAsString() : null;
        } catch (RuntimeException invalid) { return null; }
    }

    private static byte[] readBytes(Path file) throws IOException, ProfileException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new ProfileException(file + ": not a regular profile file");
        if (Files.size(file) > MAX_PROFILE_BYTES) throw new ProfileException(file + ": profile exceeds 1 MiB");
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_PROFILE_BYTES) throw new ProfileException(file + ": profile exceeds 1 MiB");
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException failure) {
            throw new ProfileException(file + ": invalid UTF-8 profile source", failure);
        }
        return bytes;
    }

    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }

    private static void writeForced(Path file, byte[] bytes, boolean createNew) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE,
                createNew ? StandardOpenOption.CREATE_NEW : StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
}
