package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.ProfileDraftValidation;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.task.ProfileApplicability;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** One game-thread editor transaction. No widgets, registry, live inventory or direct file I/O. */
final class ProfileEditorSession {
    final Path source;
    private final ProfileRepository repository;
    private final BooleanSupplier allowed;
    private LoadoutProfile expected;
    private ProfileRepository.Edit edit;
    private Baseline baseline;
    private final Map<ProfileDraft.Candidate, String> counts = new IdentityHashMap<>();
    private boolean reloadRequired;
    enum Feedback { NONE, SAVED, RESET, RELOADED, BLOCKED, RELOAD_REQUIRED, INVALID, SAVE_FAILED, REOPEN_FAILED, RELOAD_FAILED }
    private Feedback feedback = Feedback.NONE;
    Feedback feedback() { return feedback; }
    private String message = "";
    private void feedback(Feedback kind, String detail) { feedback = kind; message = detail; }

    ProfileEditorSession(ProfileRepository repository, Path source, LoadoutProfile expected,
                         BooleanSupplier allowed) throws Exception {
        this.repository = repository;
        this.source = source;
        this.allowed = allowed;
        requireAllowed();
        replaceEdit(expected);
    }

    static boolean allowed(boolean local, boolean lanClosed, boolean connected, TrainerProtocol.View view) {
        // Null is an unacknowledged join, not evidence of completed recovery.
        return local && lanClosed && connected && view != null
                && ("IDLE".equals(view.phase) || "ERROR".equals(view.phase));
    }

    ProfileDraft draft() { return edit.getDraft(); }
    LoadoutProfile expected() { return expected; }
    String message() { return message; }
    boolean reloadRequired() { return reloadRequired; }
    boolean canSave() { return allowed.getAsBoolean() && !reloadRequired; }
    String count(ProfileDraft.Candidate candidate) {
        return counts.getOrDefault(candidate, Integer.toString(candidate.getCount()));
    }
    void setCount(ProfileDraft.Candidate candidate, String text) {
        counts.put(candidate, text);
        // Preserve the last representable domain value; validate() blocks any invalid raw text.
        if (validCount(text)) candidate.setCount(Integer.parseInt(text));
    }
    static boolean validCount(String text) {
        if (text == null || !text.matches("[0-9]+")) return false;
        try { return Integer.parseInt(text) > 0; }
        catch (NumberFormatException overflow) { return false; }
    }

    List<ProfileDraft.Candidate> selectedItems(int index, String itemId) {
        List<ProfileDraft.Candidate> matches = new ArrayList<>();
        for (ProfileDraft.Candidate item : draft().getSlots().get(index).getAlternatives())
            if (itemId.equals(item.getItem())) matches.add(item);
        return matches;
    }
    ProfileDraft.Candidate selectItem(int index, String itemId) {
        List<ProfileDraft.Candidate> existing = selectedItems(index, itemId);
        if (!existing.isEmpty()) return existing.get(0);
        ProfileDraft.Slot slot = draft().getSlots().get(index);
        ProfileDraft.Candidate item = new ProfileDraft.Candidate(itemId, 1);
        slot.getAlternatives().add(item);
        if (slot.getKind() == SlotRule.Kind.EMPTY) slot.setKind(SlotRule.Kind.REQUIRED);
        return item;
    }
    boolean removalNeedsConfirmation(int index, List<ProfileDraft.Candidate> removed) {
        if (removed.isEmpty()) return false;
        for (ProfileDraft.Candidate item : removed)
            if (item.getNbt() != null || item.getMatch() != null || item.getMatchNbt() != null) return true;
        ProfileDraft.Slot slot = draft().getSlots().get(index);
        return removed.containsAll(slot.getAlternatives()) && slot.getKind() != SlotRule.Kind.REQUIRED;
    }
    void deselectItems(int index, List<ProfileDraft.Candidate> removed) {
        ProfileDraft.Slot slot = draft().getSlots().get(index);
        slot.getAlternatives().removeAll(removed);
        for (ProfileDraft.Candidate item : removed) counts.remove(item);
        if (slot.getAlternatives().isEmpty()) slot.setKind(SlotRule.Kind.EMPTY);
    }
    void setEmpty(int index) {
        deselectItems(index, new ArrayList<>(draft().getSlots().get(index).getAlternatives()));
    }

    String validate() {
        for (int slot = 0; slot < draft().getSlots().size(); slot++) {
            List<ProfileDraft.Candidate> candidates = draft().getSlots().get(slot).getAlternatives();
            for (int index = 0; index < candidates.size(); index++) {
                if (!validCount(count(candidates.get(index)))) return ProfileRepository.slotName(slot)
                        + " allowed item " + (index + 1) + ": quantity must be a positive whole number.";
            }
        }
        ProfileDraftValidation validation = repository.validateDraft(draft());
        return validation.isValid() ? "" : validation.getError();
    }

    String applicability() {
        if (!validate().isEmpty()) return "Single: unavailable until structure is valid. Full counts: unavailable.";
        ProfileApplicability assessment = ProfileApplicability.assess(repository.validateDraft(draft()).getProfile());
        return "Single: " + (assessment.isSingleTrainable() ? "available" : "unavailable")
                + ". Full stack counts: " + assessment.getFullStackCounts()
                + ". Count feasibility does not guarantee a nontrivial Full task.";
    }
    LoadoutProfile validatedProfile() {
        return validate().isEmpty() ? repository.validateDraft(draft()).getProfile() : null;
    }

    boolean save() {
        if (!allowed.getAsBoolean()) {
            feedback(Feedback.BLOCKED, "Cannot save: wait for local single-player, LAN closed and completed restoration.");
            return false;
        }
        try {
            requireAllowed();
            if (reloadRequired) { feedback(Feedback.RELOAD_REQUIRED, "Reload this source explicitly before saving again."); return false; }
            String error = validate();
            if (!error.isEmpty()) { feedback(Feedback.INVALID, error); return false; }
            requireAllowed();
            repository.save(edit);
        } catch (Exception failure) {
            feedback(Feedback.SAVE_FAILED, readable(failure));
            // Includes I/O errors: do not guess whether replacement happened or redirect the source.
            reloadRequired = true;
            return false;
        }
        try {
            LoadoutProfile saved = repository.load(source);
            if (!saved.getId().equals(expected.getId())) throw new IllegalStateException("Source ID changed after saving");
            replaceEdit(saved);
            feedback(Feedback.SAVED, "Saved.");
            return true;
        } catch (Exception failure) {
            reloadRequired = true;
            feedback(Feedback.REOPEN_FAILED, readable(failure));
            return false;
        }
    }

    boolean reload() {
        try {
            requireAllowed();
            replaceEdit(repository.load(source));
            feedback(Feedback.RELOADED, "Reloaded.");
            return true;
        } catch (Exception failure) {
            reloadRequired = true;
            feedback(Feedback.RELOAD_FAILED, readable(failure));
            return false;
        }
    }

    private void replaceEdit(LoadoutProfile snapshot) throws Exception {
        ProfileRepository.Edit next = repository.beginEdit(source, snapshot);
        edit = next;
        expected = snapshot;
        counts.clear();
        baseline = new Baseline(draft());
        reloadRequired = false;
    }
    void reset() { baseline.restore(draft()); counts.clear(); feedback(Feedback.RESET, "Changes reset."); }
    boolean dirty() { return !baseline.signature.equals(signature(draft())); }
    private String signature(ProfileDraft draft) {
        StringBuilder result = new StringBuilder();
        append(result, draft.getName());
        for (ProfileDraft.Slot slot : draft.getSlots()) {
            append(result, slot.getKind());
            append(result, slot.getAlternatives().size());
            for (ProfileDraft.Candidate candidate : slot.getAlternatives()) {
                append(result, candidate.getItem()); append(result, count(candidate)); append(result, candidate.getNbt());
                append(result, candidate.getMatch()); append(result, candidate.getMatchNbt());
            }
        }
        return result.toString();
    }
    private static void append(StringBuilder out, Object value) {
        String text = value == null ? null : value.toString();
        out.append(text == null ? -1 : text.length()).append(':').append(text).append(';');
    }
    private void requireAllowed() {
        if (!allowed.getAsBoolean()) throw new IllegalStateException("Requires local single-player, LAN closed and confirmed completed restoration.");
    }
    static String readable(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static ProfileDraft.Candidate copy(ProfileDraft.Candidate value) {
        ProfileDraft.Candidate result = new ProfileDraft.Candidate(value.getItem(), value.getCount());
        result.setNbt(value.getNbt()); result.setMatch(value.getMatch()); result.setMatchNbt(value.getMatchNbt());
        return result;
    }
    private final class Baseline {
        final String name;
        final List<ProfileDraft.Slot> slots = new ArrayList<>();
        final String signature;
        Baseline(ProfileDraft draft) {
            name = draft.getName();
            for (ProfileDraft.Slot slot : draft.getSlots()) {
                ProfileDraft.Slot saved = new ProfileDraft.Slot(slot.getKind());
                for (ProfileDraft.Candidate candidate : slot.getAlternatives()) saved.getAlternatives().add(copy(candidate));
                slots.add(saved);
            }
            signature = signature(draft);
        }
        void restore(ProfileDraft draft) {
            draft.setName(name);
            for (int index = 0; index < slots.size(); index++) {
                ProfileDraft.Slot target = draft.getSlots().get(index), saved = slots.get(index);
                target.setKind(saved.getKind()); target.getAlternatives().clear();
                for (ProfileDraft.Candidate candidate : saved.getAlternatives()) target.getAlternatives().add(copy(candidate));
            }
        }
    }
}
