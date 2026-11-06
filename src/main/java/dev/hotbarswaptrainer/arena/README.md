# Owned practice arena contract

M3-B owns one save, `saves/hotbar-swap-trainer-practice`. It never adopts a
directory because of its name or the old disposable-test marker. Client launch
preflight reserves a missing directory exclusively and persists ownership before
vanilla creation. An existing directory must already have a supported, valid,
exact-path-bound journal. Missing/damaged records and symlinks fail closed.

## Files and public interfaces

- `ArenaSpec`: sole owner of v1 seed, flat generator, rules, spawn and bounds.
  Survival/Peaceful, no structures/lakes/features/mob spawning/day/weather/fire
  cycle; bedrock y=0, dirt y=1..2, grass y=3, air y=4..255. Border is 64 blocks,
  centered at 0/0; mod block writes are restricted to x/z=-32..31, y=0..255 in
  the overworld. Vanilla creates its own spawn chunks outside these bounds.
- `ArenaLaunch.prepare(saves)`: disk-only preflight before vanilla can load/write.
  Returns create vs load. Validates journal, file tree, exact generator and seal.
  Creation interrupted before level.dat can resume only with the known
  pre-server artifacts; other data is preserved and rejected.
- `ArenaJournal`: strict version-1 JSON in `.hotbar-swap-trainer-arena.json`.
  Stable world UUID, absolute normalized path, exact spec/bounds, operation UUID,
  previous operation and phase. Forced same-directory temporary file plus atomic
  replacement; no non-atomic fallback. Transitions compare the expected record.
- `PracticeArenaService`: server lifecycle/readiness and detached status. Root
  composition injects publishing; this package never imports session or network.
- `ArenaStatus`: bounded detached status/progress/problem projection.

Private implementation: `ArenaLifecycle` is a bounded replayable state machine;
`MinecraftArenaTerrain` is its server-thread vanilla adapter; `ArenaSavedChunks`
reads region files read-only; `ArenaSeal` atomically stores an independent gzip
NBT operation seal at `data/hotbar-swap-trainer-arena.dat`. A seal alone is never
ownership/readiness proof. It must match the journal's identity/path/spec and
current operation (or its previous operation during a pending reset).

## Lifecycle and invariants

`CREATING -> INITIALIZING -> VERIFYING -> READY`.
Explicit reset: `READY -> RESETTING -> VERIFYING -> READY`.

Pending initialization/reset/verification is replayed from the first bounded
section, with the same operation identity and cuboid. No cursor durability is
needed. Each tick handles at most four sections (16,384 block comparisons),
then returns to the tick loop. Ownership is reread before each batch. READY
reuse inspects without rewriting terrain; changed ground requires explicit
destructive-reset confirmation. Terrain/schema/ownership inconsistencies do not
cause automatic repair. Errors preserve pending state and raw data.

Reset discards blocks/block-entity contents only in the owned cuboid; it does not
delete regions/worlds or provide entity cleanup/arbitrary terrain rollback. Direct
section replacement avoids old block removal callbacks, drops, comparators and
neighbor propagation outside the bounds. It maintains vanilla section counts,
heightmaps, lighting, dirty state and client updates. Initial/reset arrival first
places a small safe support/headspace inside the owned area and discards pending
mining; later entry recenters only after successful inspection/recovery. Training
STOP and Moving restoration retain their original in-place behavior.

After in-memory verification: confirm both player saves (unchanged inventory and
cleared recovery), flush chunks, read exact level settings, atomically commit and
read the operation seal, and independently read all 16 saved chunks and compare
every block and require no remaining block-entity data. Exit instead compares
current blocks and complete container NBT, preserving player changes. Only then atomically commit READY and enable START. Vanilla's save
return and dirty bit are not persistence acknowledgments. A retry re-dirties all
managed chunks. The read-only region reader accepts vanilla's unpadded final
sector during an open world but rejects truncated actual payloads.

SessionManager remains the recovery authority. Root ticks it first. Arena work
waits for recovery, and RESET/RETRY/EXIT first call
`SessionManager.restoreBeforeWorldOperation`. RESTORING/ERROR_LOCKED cannot be
implicitly retried by an arena action. An explicit ordinary STOP/recovery retry
is required. No recovery records are copied, moved, rebound or interpreted by
this package. The training-world predicate grants owned READY saves separately
from ordinary worlds' unchanged explicit disposable-test marker.

Client arena requests are queued at END_CLIENT_TICK, after vanilla input. Exit
first stops/restores on the server and publishes EXIT_RESTORED. The client queues
EXIT_COMMIT at its next end tick, after processing the ordered restoration packets
and echoing the selected slot. The server compares the exact restored snapshot,
confirms saving, and sends EXIT_READY before vanilla disconnect. This prevents a
late vanilla selected-slot echo from overwriting restored selection. Failed exit
saving retains the exit intent/proof; Retry repeats this barrier, not arena reset.
Vanilla then renders while awaiting actual server thread
termination (which follows saving and session-lock closure); there is no timed
sleep assumption. The service releases its server/owner references at STOPPED.

This is process-visible readback and single-writer optimistic file validation,
not protection against arbitrary power loss, hostile concurrent file replacement,
other mods or manual inconsistent rollback. Do not move/clone a recovery-bearing
save or edit bindings to make it load elsewhere.

Tests: `src/test/.../arena` and `network/ArenaPacketsTest`. Excluded production
fixtures: `src/smoke/.../Arena*`, run with `scripts/run-arena-acceptance.py`.
Executed vs pending acceptance: [M3-B record](../../../../../../docs/m3-b-practice-arena.md).
