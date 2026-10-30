package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.inventory.InventoryService;
import dev.hotbarswaptrainer.inventory.MinecraftItemCatalog;
import dev.hotbarswaptrainer.network.TrainerProtocol;
import dev.hotbarswaptrainer.profile.LoadoutProfile;
import dev.hotbarswaptrainer.profile.ProfileLoadResult;
import dev.hotbarswaptrainer.profile.ProfileRepository;
import dev.hotbarswaptrainer.task.TrainingMode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Client controls and readiness handshake. Vanilla still owns swapping and selection. */
public final class TrainerClient implements ClientModInitializer {
    private static TrainerClient instance;
    private final ClientSessionView session = new ClientSessionView();
    private TrainerKeyBindings keys;
    private dev.hotbarswaptrainer.stats.HistoryStore history;
    private ProfileRepository profileRepository;
    private ClientOptions options = ClientOptions.defaults();
    private ClientOptionsStore optionsStore;
    private boolean profileAvailable;
    private String configError = "";
    private String saveError = "";
    private String profileFeedback = "";

    @Override
    public void onInitializeClient() {
        instance = this;
        initializeOptions(new ProfileRepository(FabricLoader.getInstance().getConfigDir()
                .resolve("hotbar-swap-trainer/profiles"), new MinecraftItemCatalog()),
                new ClientOptionsStore(FabricLoader.getInstance().getConfigDir()
                        .resolve("hotbar-swap-trainer/client-options.json")));
        history = new dev.hotbarswaptrainer.stats.HistoryStore(FabricLoader.getInstance().getConfigDir()
                .resolve("hotbar-swap-trainer/history"));
        keys = new TrainerKeyBindings();
        ClientPackets.register(session::accept);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(new TrainerHud(this)::render);
    }

    /** Composition seam also used by file-backed tests without launching Minecraft. */
    void initializeOptions(ProfileRepository profiles, ClientOptionsStore store) {
        profileRepository = profiles;
        optionsStore = store;
        try { options = store.load(); }
        catch (IOException failure) {
            options = ClientOptions.defaults();
            configError = "Cannot read client-options.json. Using defaults; saving disabled. Repair or move the file, then restart.";
            LogManager.getLogger("HotbarSwapTrainer").error(configError, failure);
        }
        // Loading alone never creates or overwrites a configuration file.
        refreshProfiles();
    }

    private void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            session.clear();
            return;
        }
        while (keys.toggle.wasPressed()) {
            if (session.active()) stop("Stopped by player");
            else if (client.currentScreen == null) start();
        }
        while (keys.options.wasPressed()) {
            if (client.currentScreen == null) {
                stop("Options opened");
                client.openScreen(new TrainerOptionsScreen(this));
            }
        }
        checkInterruption();
        TrainerProtocol.View view = session.get();
        if (view == null || !"PREPARING".equals(view.phase) || session.readySent()) return;
        if (client.currentScreen != null || !client.isWindowFocused() || client.options.keySwapHands.isPressed()) return;
        if (view.expectedInitial != null && view.expectedInitial.equals(InventoryService.readLayout(client.player.inventory))
                && client.player.inventory.selectedSlot == view.selectedSlot) {
            ClientPackets.send(TrainerProtocol.Request.signal(TrainerProtocol.Action.READY, view, ""));
            session.markReady();
        }
    }

    void afterGoRendered() {
        MinecraftClient client = MinecraftClient.getInstance();
        TrainerProtocol.View view = session.get();
        if (view == null || !"GO".equals(view.phase) || session.goSent()) return;
        if (client.currentScreen != null || !client.isWindowFocused() || client.options.keySwapHands.isPressed()) return;
        // Queue ACK before allowing a subsequent vanilla swap packet on this connection.
        ClientPackets.send(TrainerProtocol.Request.signal(TrainerProtocol.Action.GO_ACK, view, ""));
        session.markGo();
    }

    void checkInterruption() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!session.active() || session.interruptionSent()) return;
        if (client.currentScreen != null) interrupt("Screen opened");
        else if (!client.isWindowFocused()) interrupt("Window lost focus");
        else if (client.isPaused()) interrupt("Game paused");
    }

    private void interrupt(String reason) {
        TrainerProtocol.View view = session.get();
        if (view == null || !session.active() || session.interruptionSent()) return;
        ClientPackets.send(TrainerProtocol.Request.signal(TrainerProtocol.Action.INTERRUPT, view, reason));
        session.markInterrupted();
    }

    public static void onOpenScreen(Screen screen) {
        if (instance != null && screen != null) instance.interrupt("Screen opened");
    }

    /** Reject use before vanilla predicts buckets/blocks locally; the server also guards actual packets. */
    public static boolean preventItemPrediction() {
        if (instance == null || !instance.session.active()) return false;
        if (!instance.session.interruptionSent()) {
            ClientPackets.send(TrainerProtocol.Request.signal(TrainerProtocol.Action.FORBIDDEN, instance.session.get(), ""));
            instance.session.markInterrupted();
        }
        return true;
    }

    /** Called only by the small vanilla packet adapter, before vanilla sends a swap. */
    public static boolean allowVanillaSwap() {
        if (instance == null || !instance.session.active()) return true;
        TrainerProtocol.View view = instance.session.get();
        if (instance.session.interruptionSent()) return false;
        return "RUNNING".equals(view.phase) || ("GO".equals(view.phase) && instance.session.goSent());
    }

    void start() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isInSingleplayer() || client.player == null || session.active()) return;
        refreshProfiles();
        if (!profileAvailable) return;
        ClientPackets.send(TrainerProtocol.Request.start(mode(), profile(), goal(), showHints()));
    }

    void stop(String reason) {
        TrainerProtocol.View view = session.get();
        if (view != null && session.active()) {
            ClientPackets.send(TrainerProtocol.Request.signal(TrainerProtocol.Action.STOP, view, reason));
            session.markInterrupted();
        }
    }

    ClientSessionView session() { return session; }
    TrainingMode mode() { return options.mode; }
    String profile() { return options.profile; }
    int goal() { return options.goal; }
    boolean showHints() { return options.showHints; }
    boolean showSwapCounts() { return options.showSwapCounts; }
    void toggleSwapCounts() { acceptOptions(new ClientOptions(mode(), profile(), goal(), showHints(), !showSwapCounts())); }
    boolean profileAvailable() { return profileAvailable; }
    void toggleHints() { acceptOptions(new ClientOptions(mode(), profile(), goal(), !showHints(), showSwapCounts())); }
    ProfileRepository profileRepository() { return profileRepository; }
    HistoryBrowser historyBrowser() {
        MinecraftClient client = MinecraftClient.getInstance();
        return new HistoryBrowser(history, client.player.getUuid(), this::canEditProfiles);
    }
    boolean canEditProfiles() {
        MinecraftClient client = MinecraftClient.getInstance();
        return ProfileEditorSession.allowed(client.isInSingleplayer(),
                client.getServer() != null && !client.getServer().isRemote(),
                client.player != null && client.world != null, session.get());
    }
    void cycleMode() {
        acceptOptions(new ClientOptions(mode() == TrainingMode.SINGLE_ITEM ? TrainingMode.FULL_INVENTORY
                : TrainingMode.SINGLE_ITEM, profile(), goal(), showHints(), showSwapCounts()));
    }
    /** Commit only valid input; an unfinished edit must not replace the last usable goal. */
    static Integer parseGoal(String text) {
        if (text == null || !text.matches("[0-9]+")) return null;
        try { return Integer.parseInt(text); }
        catch (NumberFormatException overflow) { return null; }
    }
    boolean setGoal(String text) {
        Integer goal = parseGoal(text);
        if (goal == null) return false;
        acceptOptions(new ClientOptions(mode(), profile(), goal, showHints(), showSwapCounts()));
        return true;
    }
    List<String> refreshProfiles() {
        List<String> ids = new ArrayList<>();
        ProfileLoadResult loaded = profileRepository.loadAll();
        for (LoadoutProfile value : loaded.getProfiles()) ids.add(value.getId());
        profileAvailable = !ids.isEmpty();
        if (!profileAvailable) {
            profileFeedback = "";
        } else if (!ids.contains(profile())) {
            String fallback = ids.contains("default") ? "default" : ids.get(0);
            profileFeedback = "Profile '" + profile() + "' unavailable; using '" + fallback + "'.";
            options = new ClientOptions(mode(), fallback, goal(), showHints(), showSwapCounts());
            LogManager.getLogger("HotbarSwapTrainer").warn(profileFeedback);
        }
        if (!loaded.getErrors().isEmpty()) {
            LogManager.getLogger("HotbarSwapTrainer").warn("Profile load errors: {}", loaded.getErrors());
        }
        return ids;
    }
    /** Explicit layout selection also retries saving unchanged/repaired preferences. */
    boolean selectProfile(String id) {
        List<String> profiles = refreshProfiles();
        if (!profiles.contains(id)) return false;
        profileFeedback = "";
        options = new ClientOptions(mode(), id, goal(), showHints(), showSwapCounts());
        saveOptions();
        return true;
    }

    private void acceptOptions(ClientOptions accepted) {
        if (options.equals(accepted)) return;
        options = accepted;
        if (optionsStore == null) return; // Uninitialized client used by pure input tests.
        // Recheck on each accepted edit: a deleted profile must never be written as a valid selection.
        refreshProfiles();
        saveOptions();
    }

    private void saveOptions() {
        if (!profileAvailable || !configError.isEmpty()) return;
        try {
            optionsStore.save(options);
            saveError = "";
        } catch (IOException failure) {
            saveError = "Cannot save client-options.json. Changes apply for this process only; see the log and retry an option change.";
            LogManager.getLogger("HotbarSwapTrainer").error(saveError, failure);
        }
    }

    List<String> optionMessages() {
        List<String> messages = new ArrayList<>();
        if (!configError.isEmpty()) messages.add(configError);
        if (!saveError.isEmpty()) messages.add(saveError);
        if (!profileAvailable) messages.add("No valid profiles. Repair profiles/*.json, then reopen Edit item layout.");
        if (!profileFeedback.isEmpty()) messages.add(profileFeedback);
        return messages;
    }
}
