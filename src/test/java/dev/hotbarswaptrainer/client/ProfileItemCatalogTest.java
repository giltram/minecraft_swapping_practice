package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.profile.ProfileDraft;
import net.minecraft.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ProfileItemCatalogTest {
    @BeforeClass public static void registry() { Bootstrap.initialize(); }
    @Test public void searchMatchesIdAndLocalizedNameAndPreviewNeverMutatesDraft() {
        List<ProfileItemCatalog.Choice> choices = ProfileItemCatalog.items();
        ProfileItemCatalog.Choice sword = choices.stream().filter(item -> item.id.equals("minecraft:iron_sword")).findFirst().get();
        assertTrue(sword.matches("MINECRAFT:IRON_SWORD")); assertTrue(sword.matches(sword.name));
        assertFalse(sword.matches("impossible search"));
        assertTrue(choices.stream().noneMatch(item -> item.id.equals("minecraft:air")));
        ProfileDraft.Candidate candidate = new ProfileDraft.Candidate("minecraft:iron_sword", 1);
        assertFalse(ProfileItemCatalog.preview(candidate).isEmpty());
        assertNull(candidate.getNbt()); assertNull(candidate.getMatch()); assertNull(candidate.getMatchNbt());
        candidate.setNbt("{incomplete:"); assertFalse(ProfileItemCatalog.preview(candidate).isEmpty());
        assertEquals("{incomplete:", candidate.getNbt());
        StringBuilder deeplyNested = new StringBuilder();
        for (int i = 0; i < 10000; i++) deeplyNested.append("{a:");
        candidate.setNbt(deeplyNested.toString());
        assertFalse(ProfileItemCatalog.preview(candidate).isEmpty());
        assertEquals(deeplyNested.toString(), candidate.getNbt());
    }
}
