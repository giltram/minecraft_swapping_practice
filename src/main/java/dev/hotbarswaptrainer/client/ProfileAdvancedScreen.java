package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.StackSpec;
import net.minecraft.client.gui.screen.Screen;

/** Nullable schema input stays nullable. Rendering controls never writes explicit defaults. */
final class ProfileAdvancedScreen extends EditorFormScreen {
    private final Screen parent;
    private final ProfileDraft.Candidate candidate;
    ProfileAdvancedScreen(Screen parent, ProfileDraft.Candidate candidate) {
        super("Advanced candidate settings"); this.parent = parent; this.candidate = candidate;
    }
    @Override protected void buildForm() {
        paragraph("Default means unspecified (null). An explicit {} is different. Invalid or incomplete SNBT is kept until you correct it or reset the draft.", 0xDDDDDD);
        paragraph("Generation NBT: " + (candidate.getNbt() == null ? "using default" : "explicit"), 0xFFDD88);
        field("Generation compound SNBT", candidate.getNbt() == null ? "" : candidate.getNbt(), 1048576, candidate::setNbt);
        action("Generation NBT: use default", () -> { candidate.setNbt(null); rebuild(); });
        action("Matching: " + (candidate.getMatch() == null ? "default (EXACT)" : candidate.getMatch()) + " (change)", () -> {
            candidate.setMatch(candidate.getMatch() == StackSpec.NbtMatch.EXACT ? StackSpec.NbtMatch.FIELDS : StackSpec.NbtMatch.EXACT);
            rebuild();
        });
        action("Matching mode: use default", () -> { candidate.setMatch(null); rebuild(); });
        paragraph("Match NBT: " + (candidate.getMatchNbt() == null ? "using default" : "explicit"), 0xFFDD88);
        field("Matching compound SNBT", candidate.getMatchNbt() == null ? "" : candidate.getMatchNbt(), 1048576, candidate::setMatchNbt);
        action("Match NBT: use default", () -> { candidate.setMatchNbt(null); rebuild(); });
        paragraph("EXACT compares the complete compound. FIELDS matches a typed subset. With default match NBT, EXACT follows the normalized generation template; FIELDS follows raw generation NBT.", 0xBBBBBB);
        footer("Back (keep draft)");
    }
    @Override public void onClose() { client.openScreen(parent); }
}
