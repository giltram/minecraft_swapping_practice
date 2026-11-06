package dev.hotbarswaptrainer.client;

import dev.hotbarswaptrainer.arena.ArenaStatus;
import net.minecraft.client.resource.language.I18n;

/** One non-pausing progress/error surface; no world/configuration wizard. */
final class PracticeArenaScreen extends EditorFormScreen {
    private final PracticeArenaClient arena;
    private String shown = "";
    PracticeArenaScreen(PracticeArenaClient arena) { super(copy("title")); this.arena = arena; }
    static String copy(String key, Object... args) { return I18n.translate("trainer.arena." + key, args); }

    @Override protected void buildForm() {
        ArenaStatus status = arena.status(); shown = signature();
        if (status.state == ArenaStatus.State.ERROR) {
            paragraph(copy("error." + status.problem.name().toLowerCase(java.util.Locale.ROOT)), 0xFFAA88);
            if (status.resetAllowed) action(copy("reset"), this::confirmReset);
            footer(copy(status.problem == ArenaStatus.Problem.RECOVERY ? "retryRecovery" : "retry"), copy("exit"));
        } else {
            paragraph(copy("progress." + status.state.name().toLowerCase(java.util.Locale.ROOT)), 0xDDDDDD);
            dynamicLine(() -> copy("percent", arena.status().percent), 0xDDDDDD);
            footer(copy("exit"));
        }
        if (arena.busy()) {
            setFooterActive(0, false);
            if (status.state == ArenaStatus.State.ERROR) setFooterActive(1, false);
        }
    }
    private String signature() { return arena.status().state + ":" + arena.status().problem + ":" + arena.status().resetAllowed + ":" + arena.busy(); }
    @Override public void tick() { super.tick(); if (!shown.equals(signature())) rebuild(); }
    private void confirmReset() {
        client.openScreen(new EditorConfirmScreen(this, copy("resetConfirm"), arena::reset));
    }
    @Override protected void footerAction(int choice) {
        if (arena.busy()) return;
        if (arena.status().state == ArenaStatus.State.ERROR && choice == 0) arena.retry(); else arena.exit();
    }
    @Override public void onClose() { /* Operations finish or fail explicitly; Esc cannot hide an unresolved operation. */ }
}
