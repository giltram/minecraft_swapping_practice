package dev.hotbarswaptrainer.arena;

import java.io.IOException;

/** Bounded, replayable operation. The journal is authoritative; the cursor is only an optimization. */
final class ArenaLifecycle {
    interface Terrain {
        void validate(ArenaJournal.Record record) throws IOException;
        void configure() throws IOException;
        void applySection(int unit) throws IOException;
        boolean checkSection(int unit) throws IOException;
        void save(ArenaJournal.Record record) throws IOException;
        void checkSavedChunk(int chunk) throws IOException;
    }
    private enum Step { APPLY, CHECK, SAVE, READBACK, DONE }
    private static final int SECTIONS = ArenaSpec.TOTAL_CHUNKS * 16;
    private final ArenaJournal journal;
    private final Terrain terrain;
    private ArenaJournal.Record record;
    private Step step;
    private int cursor;
    private final boolean reuse;

    ArenaLifecycle(ArenaJournal journal, Terrain terrain, boolean reset) throws IOException {
        this(journal, terrain, reset, null);
    }

    ArenaLifecycle(ArenaJournal journal, Terrain terrain, boolean reset, ArenaLayout target) throws IOException {
        this.journal = journal; this.terrain = terrain; this.record = journal.read();
        terrain.validate(record); // Before any configuration, journal transition or terrain mutation.
        if (target != null) {
            record = journal.convert(record, target);
            terrain.validate(record);
        } else if (reset) {
            if (record.phase != ArenaJournal.Phase.READY) throw new IOException("An arena operation is already pending");
            record = journal.transition(record, ArenaJournal.Phase.RESETTING);
        }
        reuse = record.phase == ArenaJournal.Phase.READY;
        if (record.phase == ArenaJournal.Phase.CREATING)
            record = journal.transition(record, ArenaJournal.Phase.INITIALIZING);
        if (!reuse) terrain.configure();
        step = reuse ? Step.CHECK : Step.APPLY;
    }

    ArenaStatus tick() throws IOException {
        // Revalidate exact ownership on every batch; external edits stop work, never trigger repair.
        if (!record.equals(journal.read())) throw new IOException("Arena ownership changed during operation");
        if (step == Step.APPLY) {
            for (int budget = 0; budget < 4 && cursor < SECTIONS; budget++) terrain.applySection(cursor++);
            int percent = Math.min(69, cursor * 70 / SECTIONS);
            if (cursor == SECTIONS) {
                if (record.phase != ArenaJournal.Phase.VERIFYING)
                    record = journal.transition(record, ArenaJournal.Phase.VERIFYING);
                cursor = 0; step = Step.CHECK;
            }
            return ArenaStatus.of(ArenaStatus.State.BUILDING, percent);
        }
        if (step == Step.CHECK) {
            for (int budget = 0; budget < 4 && cursor < SECTIONS; budget++)
                if (!terrain.checkSection(cursor++)) throw new ChangedTerrain();
            int percent = reuse ? cursor * 100 / SECTIONS : 70 + cursor * 15 / SECTIONS;
            if (cursor == SECTIONS) { cursor = 0; step = reuse ? Step.DONE : Step.SAVE; }
            return step == Step.DONE ? ArenaStatus.of(ArenaStatus.State.READY, 100)
                    : ArenaStatus.of(ArenaStatus.State.CHECKING, percent);
        }
        if (step == Step.SAVE) {
            terrain.save(record);
            step = Step.READBACK;
            return ArenaStatus.of(ArenaStatus.State.SAVING, 85);
        }
        if (step == Step.READBACK) {
            terrain.checkSavedChunk(cursor++);
            if (cursor == ArenaSpec.TOTAL_CHUNKS) {
                record = journal.transition(record, ArenaJournal.Phase.READY);
                step = Step.DONE;
                return ArenaStatus.of(ArenaStatus.State.READY, 100);
            }
            return ArenaStatus.of(ArenaStatus.State.SAVING, 85 + cursor * 14 / ArenaSpec.TOTAL_CHUNKS);
        }
        return ArenaStatus.of(ArenaStatus.State.READY, 100);
    }

    static final class ChangedTerrain extends IOException {
        ChangedTerrain() { super("Arena blocks differ from the specification; explicit reset required"); }
    }
}
