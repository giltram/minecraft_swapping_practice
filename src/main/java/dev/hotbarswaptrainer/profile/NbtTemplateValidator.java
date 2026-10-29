package dev.hotbarswaptrainer.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Memory-only binary NBT checks performed before any player inventory is replaced. */
final class NbtTemplateValidator {
    static final int MAX_BYTES = 32 * 1024;
    static final int MAX_TEXT_CHARS = 64 * 1024;
    static final int MAX_DEPTH = 64;

    private NbtTemplateValidator() { }

    static void validateText(String text) {
        if (text.length() > MAX_TEXT_CHARS) throw new IllegalArgumentException("SNBT text exceeds 64 KiB characters");
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == quote) quote = 0;
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '{' || character == '[') {
                if (++depth > MAX_DEPTH) throw new IllegalArgumentException("NBT nesting exceeds " + MAX_DEPTH + " levels");
            } else if (character == '}' || character == ']') {
                depth--;
            }
        }
    }

    static void validate(CompoundTag tag) {
        checkDepth(tag, 1);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Binary NBT exceeds 32 KiB per stack");
            CompoundTag decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            if (!tag.equals(decoded)) throw new IllegalArgumentException("NBT changed during binary serialization");
        } catch (IOException failure) {
            throw new IllegalArgumentException("NBT cannot be serialized to binary format: " + failure.getMessage(), failure);
        }
    }

    private static void checkDepth(Tag tag, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("NBT nesting exceeds " + MAX_DEPTH + " levels");
        if (tag instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) tag;
            for (String key : compound.getKeys()) checkDepth(compound.get(key), depth + 1);
        } else if (tag instanceof ListTag) {
            for (Tag child : (ListTag) tag) checkDepth(child, depth + 1);
        }
    }
}
