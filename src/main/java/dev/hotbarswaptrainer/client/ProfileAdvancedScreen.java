package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.StackSpec;
import net.minecraft.client.gui.screen.Screen;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Nullable schema input stays nullable. Rendering controls never writes explicit defaults. */
final class ProfileAdvancedScreen extends EditorFormScreen {
    private final Screen parent;
    private final ProfileDraft.Candidate candidate;
    ProfileAdvancedScreen(Screen parent, ProfileDraft.Candidate candidate) {
        super(text("nbtSettings")); this.parent = parent; this.candidate = candidate;
    }
    @Override protected void buildForm() {
        paragraph(text("nbtHelp"), 0xDDDDDD);
        paragraph(text("generationState", text(candidate.getNbt() == null ? "default" : "explicit")), 0xFFDD88);
        field(text("generation"), candidate.getNbt() == null ? "" : candidate.getNbt(), 1048576, candidate::setNbt);
        action(text("generationDefault"), () -> { candidate.setNbt(null); rebuild(); });
        action(text("matching", candidate.getMatch() == null ? text("defaultExact") : candidate.getMatch()), () -> {
            candidate.setMatch(candidate.getMatch() == StackSpec.NbtMatch.EXACT ? StackSpec.NbtMatch.FIELDS : StackSpec.NbtMatch.EXACT);
            rebuild();
        });
        action(text("matchingDefault"), () -> { candidate.setMatch(null); rebuild(); });
        paragraph(text("matchState", text(candidate.getMatchNbt() == null ? "default" : "explicit")), 0xFFDD88);
        field(text("matchNbt"), candidate.getMatchNbt() == null ? "" : candidate.getMatchNbt(), 1048576, candidate::setMatchNbt);
        action(text("matchDefault"), () -> { candidate.setMatchNbt(null); rebuild(); });
        paragraph(text("matchingHelp"), 0xBBBBBB);
        footer(text("back"));
    }
    @Override public void onClose() { client.openScreen(parent); }
}
