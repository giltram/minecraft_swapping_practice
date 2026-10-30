package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.SlotRule;
import static dev.hotbarswaptrainer.client.EditorText.text;

/** Shared natural-language rule summary, independent of screen navigation. */
final class ProfileSlotPresentation {
    static String summary(ProfileDraft.Slot rule) {
        String count = text("selectedCount", rule.getAlternatives().size());
        if (rule.getKind() == SlotRule.Kind.FLEXIBLE) return count + " · " + text("flexible");
        if (rule.getKind() == SlotRule.Kind.OPTIONAL) return count + " · " + text("optional");
        return count;
    }
    private ProfileSlotPresentation() { }
}
