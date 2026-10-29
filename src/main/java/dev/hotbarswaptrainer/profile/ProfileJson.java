package dev.hotbarswaptrainer.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/** Memory-only schema codec shared by loading, draft validation and saving. */
final class ProfileJson {
    static final int MAX_PROFILE_BYTES = 1024 * 1024;
    private final ItemCatalog catalog;

    ProfileJson(ItemCatalog catalog) { this.catalog = catalog; }

    static ProfileDraft draft(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        ProfileDraft.Slot[] slots = new ProfileDraft.Slot[LoadoutProfile.SLOT_COUNT];
        for (JsonElement element : root.getAsJsonArray("slots")) {
            JsonObject raw = element.getAsJsonObject();
            ProfileDraft.Slot slot = new ProfileDraft.Slot(SlotRule.Kind.valueOf(raw.get("kind").getAsString()));
            for (int index = 0; index < slots.length; index++) {
                if (slotName(index).equals(raw.get("slot").getAsString())) slots[index] = slot;
            }
            if (!raw.has("alternatives")) continue;
            for (JsonElement alternative : raw.getAsJsonArray("alternatives")) {
                JsonObject stack = alternative.getAsJsonObject();
                ProfileDraft.Candidate candidate = new ProfileDraft.Candidate(stack.get("item").getAsString(), stack.get("count").getAsInt());
                if (stack.has("nbt")) candidate.setNbt(stack.get("nbt").getAsString());
                if (stack.has("match")) candidate.setMatch(StackSpec.NbtMatch.valueOf(stack.get("match").getAsString()));
                if (stack.has("matchNbt")) candidate.setMatchNbt(stack.get("matchNbt").getAsString());
                slot.getAlternatives().add(candidate);
            }
        }
        return new ProfileDraft(root.get("id").getAsString(), root.get("name").getAsString(), Arrays.asList(slots));
    }

    static JsonObject encode(ProfileDraft draft) throws ProfileException {
        if (draft == null) throw new ProfileException("draft: missing profile draft");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", LoadoutProfile.SCHEMA_VERSION);
        root.addProperty("id", draft.getId());
        root.addProperty("name", draft.getName());
        JsonArray slots = new JsonArray();
        for (int index = 0; index < draft.getSlots().size(); index++) {
            String path = "draft.slots[" + index + "]";
            ProfileDraft.Slot input = draft.getSlots().get(index);
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", slotName(index));
            slot.addProperty("kind", input.getKind() == null ? null : input.getKind().name());
            JsonArray alternatives = new JsonArray();
            for (ProfileDraft.Candidate candidate : input.getAlternatives()) {
                if (candidate == null) throw new ProfileException(path + ".alternatives: missing candidate");
                JsonObject stack = new JsonObject();
                stack.addProperty("item", candidate.getItem());
                stack.addProperty("count", candidate.getCount());
                if (candidate.getNbt() != null) stack.addProperty("nbt", candidate.getNbt());
                if (candidate.getMatch() != null) stack.addProperty("match", candidate.getMatch().name());
                if (candidate.getMatchNbt() != null) stack.addProperty("matchNbt", candidate.getMatchNbt());
                alternatives.add(stack);
            }
            slot.add("alternatives", alternatives);
            slots.add(slot);
        }
        root.add("slots", slots);
        return root;
    }

    LoadoutProfile parse(String json, String source) throws ProfileException {
        try {
            if (json.length() > MAX_PROFILE_BYTES) throw new ProfileException(source + ": profile JSON exceeds 1 MiB characters");
            JsonObject root = object(new JsonParser().parse(json), source);
            keys(root, source, "schemaVersion", "id", "name", "slots");
            if (integer(required(root, "schemaVersion", source), source + ".schemaVersion") != LoadoutProfile.SCHEMA_VERSION) {
                throw new ProfileException(source + ".schemaVersion: supported version is 1");
            }
            String id = string(required(root, "id", source), source + ".id");
            String name = string(required(root, "name", source), source + ".name");
            JsonElement slotValue = required(root, "slots", source);
            if (!slotValue.isJsonArray()) throw new ProfileException(source + ".slots: expected an array");
            JsonArray slotsJson = slotValue.getAsJsonArray();
            SlotRule[] slots = new SlotRule[LoadoutProfile.SLOT_COUNT];
            for (int entry = 0; entry < slotsJson.size(); entry++) {
                String path = source + ".slots[" + entry + "]";
                JsonObject slot = object(slotsJson.get(entry), path);
                keys(slot, path, "slot", "kind", "alternatives");
                String slotName = string(required(slot, "slot", path), path + ".slot");
                int position = position(slotName, path + ".slot");
                if (slots[position] != null) throw new ProfileException(path + ": duplicate position " + slotName);
                SlotRule.Kind kind;
                try { kind = SlotRule.Kind.valueOf(string(required(slot, "kind", path), path + ".kind")); }
                catch (IllegalArgumentException failure) { throw new ProfileException(path + ".kind: expected REQUIRED, OPTIONAL, EMPTY, or FLEXIBLE"); }
                List<StackSpec> alternatives = new ArrayList<>();
                if (slot.has("alternatives")) {
                    JsonElement values = slot.get("alternatives");
                    if (!values.isJsonArray()) throw new ProfileException(path + ".alternatives: expected an array");
                    for (int index = 0; index < values.getAsJsonArray().size(); index++) {
                        alternatives.add(stack(values.getAsJsonArray().get(index), path + ".alternatives[" + index + "]"));
                    }
                }
                try { slots[position] = new SlotRule(kind, alternatives); }
                catch (IllegalArgumentException failure) { throw new ProfileException(path + ": " + failure.getMessage()); }
            }
            for (int index = 0; index < slots.length; index++) {
                if (slots[index] == null) throw new ProfileException(source + ".slots: missing " + slotName(index));
            }
            return new LoadoutProfile(id, name, Arrays.asList(slots));
        } catch (ProfileException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProfileException(source + ": " + failure.getMessage(), failure);
        }
    }

    private StackSpec stack(JsonElement value, String path) throws ProfileException {
        JsonObject stack = object(value, path);
        keys(stack, path, "item", "count", "nbt", "match", "matchNbt");
        String item = string(required(stack, "item", path), path + ".item");
        int maximum = catalog.maximumStackSize(item);
        if (maximum <= 0) throw new ProfileException(path + ".item: unknown item or air: " + item);
        int count = integer(required(stack, "count", path), path + ".count");
        if (count < 1 || count > maximum) throw new ProfileException(path + ".count: expected 1.." + maximum);
        CompoundTag nbt = nbt(stack, "nbt", new CompoundTag(), path);
        CompoundTag matchNbt = nbt(stack, "matchNbt", nbt, path);
        StackSpec.NbtMatch match = StackSpec.NbtMatch.EXACT;
        if (stack.has("match")) {
            try { match = StackSpec.NbtMatch.valueOf(string(stack.get("match"), path + ".match")); }
            catch (IllegalArgumentException failure) { throw new ProfileException(path + ".match: expected EXACT or FIELDS"); }
        }
        try {
            StackValue template = catalog.normalizeTemplate(StackValue.of(item, count, nbt));
            if (!item.equals(template.getItemId()) || count != template.getCount()) {
                throw new IllegalArgumentException("Runtime normalization changed the item or count");
            }
            if (match == StackSpec.NbtMatch.EXACT && !stack.has("matchNbt")) matchNbt = template.getNbt();
            return new StackSpec(template, match, matchNbt);
        }
        catch (IllegalArgumentException failure) { throw new ProfileException(path + ": " + failure.getMessage()); }
    }

    private static CompoundTag nbt(JsonObject value, String key, CompoundTag fallback, String path) throws ProfileException {
        if (!value.has(key)) return fallback.copy();
        try {
            String text = string(value.get(key), path + "." + key);
            NbtTemplateValidator.validateText(text);
            CompoundTag parsed = StringNbtReader.parse(text);
            NbtTemplateValidator.validate(parsed);
            return parsed;
        } catch (Exception failure) {
            throw new ProfileException(path + "." + key + ": invalid compound NBT: " + failure.getMessage());
        }
    }

    private static int position(String value, String path) throws ProfileException {
        if ("offhand".equals(value)) return 9;
        for (int index = 0; index < 9; index++) if (slotName(index).equals(value)) return index;
        throw new ProfileException(path + ": expected hotbar1..hotbar9 or offhand");
    }

    static String slotName(int index) { return index == 9 ? "offhand" : "hotbar" + (index + 1); }

    private static JsonObject object(JsonElement value, String path) throws ProfileException {
        if (!value.isJsonObject()) throw new ProfileException(path + ": expected an object");
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value, String path) throws ProfileException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new ProfileException(path + ": expected a string");
        return value.getAsString();
    }

    private static int integer(JsonElement value, String path) throws ProfileException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ProfileException(path + ": expected an integer");
        try { return new BigDecimal(value.getAsString()).intValueExact(); }
        catch (ArithmeticException failure) { throw new ProfileException(path + ": expected an integer"); }
    }

    private static JsonElement required(JsonObject object, String key, String path) throws ProfileException {
        if (!object.has(key) || object.get(key).isJsonNull()) throw new ProfileException(path + "." + key + ": missing value");
        return object.get(key);
    }

    private static void keys(JsonObject object, String path, String... allowedKeys) throws ProfileException {
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedKeys));
        for (java.util.Map.Entry<String, JsonElement> field : object.entrySet()) {
            if (!allowed.contains(field.getKey())) throw new ProfileException(path + "." + field.getKey() + ": unknown field");
        }
    }
}
