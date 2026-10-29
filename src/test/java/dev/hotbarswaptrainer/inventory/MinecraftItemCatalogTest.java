package dev.hotbarswaptrainer.inventory;

import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.profile.ProfileDraft;
import dev.hotbarswaptrainer.profile.SlotRule;
import dev.hotbarswaptrainer.profile.StackSpec;
import dev.hotbarswaptrainer.profile.StackValue;
import dev.hotbarswaptrainer.task.FullInventoryTaskGenerator;
import dev.hotbarswaptrainer.task.InventoryLayout;
import dev.hotbarswaptrainer.task.InventoryTask;
import dev.hotbarswaptrainer.task.SingleItemTaskGenerator;
import dev.hotbarswaptrainer.task.TaskValidator;
import io.netty.buffer.Unpooled;
import net.minecraft.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Checks the actual vanilla constructor/network boundary absent from pure generator tests. */
public class MinecraftItemCatalogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final MinecraftItemCatalog catalog = new MinecraftItemCatalog();

    @BeforeClass public static void initializeVanillaRegistry() { Bootstrap.initialize(); }

    @Test
    public void vanillaDefaultDamageIsFrozenWithoutDiscardingStructuralNbt() {
        for (String item : Arrays.asList("minecraft:iron_sword", "minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:bow", "minecraft:shield")) {
            StackValue raw = StackValue.of(item, 1, new CompoundTag());
            StackValue normalized = catalog.normalizeTemplate(raw);
            assertFalse(raw.getNbt().contains("Damage"));
            assertTrue(item, normalized.getNbt().contains("Damage", 3));
            assertEquals(0, normalized.getNbt().getInt("Damage"));
            assertEquals(normalized, catalog.normalizeTemplate(normalized));
            assertEquals(normalized, materialize(normalized));
            assertEquals(normalized, networkRoundTrip(normalized));
            ItemStack loaded = ItemStack.fromTag(InventoryService.toItemStack(normalized).toTag(new CompoundTag()));
            assertEquals(normalized, InventoryService.fromStack(loaded));
        }
        assertEquals(0, catalog.maximumStackSize("minecraft:air"));
        assertEquals(0, catalog.maximumStackSize("minecraft:missing_item"));
        assertEquals(64, catalog.maximumStackSize("minecraft:cobblestone"));
        assertEquals(1, catalog.maximumStackSize("minecraft:iron_sword"));
    }

    @Test
    public void explicitDamagePotionAndExtraNbtSurviveMaterializationAndPackets() throws Exception {
        for (StackValue value : Arrays.asList(
                StackValue.of("minecraft:iron_sword", 1, StringNbtReader.parse("{Damage:7,Custom:1b,display:{Name:'\"named tool\"'}}")),
                StackValue.of("minecraft:potion", 1, StringNbtReader.parse("{Potion:\"minecraft:fire_resistance\",Custom:2b}")),
                StackValue.of("minecraft:splash_potion", 1, StringNbtReader.parse("{Potion:\"minecraft:long_fire_resistance\"}")),
                StackValue.of("minecraft:water_bucket", 1, new CompoundTag()))) {
            StackValue normalized = catalog.normalizeTemplate(value);
            assertEquals(value, normalized);
            assertEquals(normalized, materialize(normalized));
            assertEquals(normalized, networkRoundTrip(normalized));
        }
    }

    @Test
    public void defaultProfileTasksRemainValidAfterVanillaMaterialization() throws Exception {
        ProfileRepository repository = new ProfileRepository(temporary.getRoot().toPath(), catalog);
        repository.installDefaultIfMissing();
        LoadoutProfile profile = repository.load(temporary.getRoot().toPath().resolve("default.json"));
        for (SlotRule rule : profile.getSlots()) {
            for (StackSpec alternative : rule.getAlternatives()) {
                StackValue actual = networkRoundTrip(alternative.getTemplate());
                assertEquals(alternative.getTemplate(), actual);
                assertTrue(alternative.matches(actual));
            }
        }
        TaskValidator validator = new TaskValidator();
        for (long seed = 0; seed < 100; seed++) {
            for (InventoryTask task : Arrays.asList(new SingleItemTaskGenerator().generate(profile, seed),
                    new FullInventoryTaskGenerator().generate(profile, seed))) {
                InventoryLayout actual = materialize(task.getInitial());
                assertEquals(task.getInitial(), actual);
                assertFalse(validator.validate(task, actual).isComplete());
                for (int swap : task.getSolution()) actual = actual.withSwap(swap);
                assertTrue(validator.validate(task, materialize(actual)).isComplete());
            }
        }
    }

    @Test
    public void editedProfileReloadUsesActualVanillaDefaultsAndMaterializedTaskWitnesses() throws Exception {
        java.nio.file.Path file = temporary.getRoot().toPath().resolve("default.json");
        ProfileRepository repository = new ProfileRepository(file.getParent(), catalog);
        repository.installDefaultIfMissing();
        LoadoutProfile original = repository.load(file);
        ProfileRepository.Edit edit = repository.beginEdit(file, original);
        ProfileDraft.Candidate sword = edit.getDraft().getSlots().get(0).getAlternatives().get(0);
        assertNull(sword.getMatchNbt());
        sword.setNbt("{Custom:2b,display:{Name:'\"edited sword\"'}}");
        edit.getDraft().setName("Edited runtime profile");
        LoadoutProfile saved = repository.save(edit);
        LoadoutProfile reloaded = new ProfileRepository(file.getParent(), catalog).load(file);
        assertEquals(saved.getHash(), reloaded.getHash());
        StackSpec spec = reloaded.getSlots().get(0).getAlternatives().get(0);
        assertEquals(spec.getTemplate().getNbt(), spec.getMatchNbt());
        assertTrue(spec.getTemplate().getNbt().contains("Damage", 3));
        assertEquals(2, spec.getTemplate().getNbt().getByte("Custom"));
        TaskValidator validator = new TaskValidator();
        for (int seed = 0; seed < 20; seed++) {
            for (InventoryTask task : Arrays.asList(new SingleItemTaskGenerator().generate(reloaded, seed),
                    new FullInventoryTaskGenerator().generate(reloaded, seed))) {
                InventoryLayout actual = materialize(task.getInitial());
                assertEquals(task.getInitial(), actual);
                assertFalse(validator.validate(task, actual).isComplete());
                for (int swap : task.getSolution()) actual = actual.withSwap(swap);
                assertTrue(validator.validate(task, materialize(actual)).isComplete());
            }
        }
        // Item count limits come from the real registry, through the same validation path.
        edit = repository.beginEdit(file, reloaded);
        edit.getDraft().getSlots().get(0).getAlternatives().get(0).setCount(2);
        assertFalse(repository.validateDraft(edit.getDraft()).isValid());
        assertEquals(reloaded.getHash(), repository.load(file).getHash());
    }

    private static StackValue materialize(StackValue value) {
        return InventoryService.fromStack(InventoryService.toItemStack(value));
    }

    private static InventoryLayout materialize(InventoryLayout layout) {
        List<StackValue> actual = new ArrayList<>();
        for (StackValue value : layout.getStacks()) actual.add(networkRoundTrip(value));
        return new InventoryLayout(actual);
    }

    private static StackValue networkRoundTrip(StackValue value) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeItemStack(InventoryService.toItemStack(value));
            return InventoryService.fromStack(buffer.readItemStack());
        } finally { buffer.release(); }
    }
}
