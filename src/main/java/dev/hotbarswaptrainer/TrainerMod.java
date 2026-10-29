package dev.hotbarswaptrainer;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.MinecraftItemCatalog;
import dev.hotbarswaptrainer.network.ServerPackets;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.session.SessionManager;
import dev.hotbarswaptrainer.stats.InMemoryResultStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.WorldSavePath;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Files;

/** Common composition root. Runtime modules never import client classes. */
public final class TrainerMod implements ModInitializer {
    public static final InventoryService INVENTORY = new InventoryService();
    public static SessionManager SESSIONS;

    @Override public void onInitialize() {
        ProfileRepository profiles = new ProfileRepository(FabricLoader.getInstance().getConfigDir()
                .resolve("hotbar-swap-trainer/profiles"), new MinecraftItemCatalog());
        try { profiles.installDefaultIfMissing(); }
        catch (Exception failure) { LogManager.getLogger("HotbarSwapTrainer").error("Cannot install default profile", failure); }
        SESSIONS = new SessionManager(INVENTORY, profiles, new InMemoryResultStore());
        ServerPackets.register(SESSIONS::handle);
        ServerTickEvents.END_SERVER_TICK.register(SESSIONS::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(SESSIONS::shutdown);
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(
                CommandManager.literal("swaptrainer").then(CommandManager.literal("enable-test-world").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player.getServer().isDedicated() || player.getServer().isRemote()
                            || !player.getServer().isHost(player.getGameProfile()) || InventoryService.isProtected(player)) {
                        context.getSource().sendError(new LiteralText("Only the local owner can mark an idle disposable test world"));
                        return 0;
                    }
                    java.nio.file.Path marker = player.getServer().getSavePath(WorldSavePath.ROOT).resolve(".hotbar-swap-trainer-test");
                    try {
                        if (!Files.exists(marker)) Files.write(marker, java.util.Collections.singletonList("Disposable Hotbar Swap Trainer test world"));
                    } catch (java.io.IOException failure) {
                        context.getSource().sendError(new LiteralText("Could not mark this test world: " + failure.getMessage()));
                        return 0;
                    }
                    context.getSource().sendFeedback(new LiteralText("This world is marked for experimental training. Press P to start or O for options."), false);
                    return 1;
                }))));
    }
}
