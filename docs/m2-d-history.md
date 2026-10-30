# M2-D durable history

Current GUI update: [multi-select layout and training-count input](m2-usability-plan.md)
reuse these backend contracts. [Current verification](m2-multiselect-acceptance.md)
records its own artifact and executed checks; earlier artifact results below are
historical, not automatic acceptance of the new GUI.

Implemented 2026-10-30. `HistoryStore(Path)` implements the unchanged ResultStore
save/findLatest/forget/clear signatures. The common composition root injects
config/hotbar-swap-trainer/history. Ownership is the local installation across
worlds, partitioned `<player UUID>/<session UUID>.json`; it is not a world backup.
findLatest retains the current-process latest-summary contract. `read(player)`
works after restart; forget/clear only clear the latest memory cache.

`SessionMetadata` freezes profile ID/name, hints declaration, timing/scoring and
stationary environment at START alongside the immutable profile/mode/goal. Start
and end are wall-clock milliseconds; task scores retain server nanoseconds. End
state is frozen on the first stop (including recovery retries): COMPLETED,
STOPPED, DISCONNECTED, SHUTDOWN or ABORTED. Wall clock reversal clamps end to start.
Protocol v2 adds exactly one declared boolean to START; old Java request factories
remain source compatible with hints=false. Neither common nor session reads client
state. A hints declaration is not anti-cheat evidence.

Schema 1 stores complete TaskResult fields and metadata. The legacy SessionResult
constructor creates explicit metadata=null, preserved but ineligible for PB.
Required/unknown/duplicate fields, integer syntax/ranges, UUID binding, enum,
UTF-8, depth, text lengths, task ID uniqueness and successful-duration sum overflow
are validated. A successful duration must be positive. Completed finite records
must contain exactly their positive success goal. Reads are bounded: 2 MiB/file,
4096 tasks/file, 2048 candidate files and 32 MiB/read. Exceeding a session's write
limit reports a missing history record without affecting training/recovery.
Read limits return a visible incomplete warning; retained records are sorted by
end descending (legacy=0), then canonical session UUID ascending. Pages contain
10 rows; filters cover mode, hints, goal, hash and end state. Single bad records
remain untouched and do not hide valid records. `.tmp` files are ignored.

Saving serializes/validates, acquires a player-directory file lock, checks existing
content, forces a same-directory unique temporary file, rereads/validates exact
bytes, and atomically renames. There is no non-atomic fallback. Canonically equal
repeats are idempotent; conflicting/damaged/unknown existing records fail without
replacement. Only this save's temporary file is cleaned up. Root is trusted and
injected; symlinks within the history root are refused. Cooperating writers use
the same lock; hostile filesystem races are outside this local-store contract.

`SessionManager.stop` confirms inventory.restore before removing protection/session
state and attempting statistics. Only the restore catch may set ERROR_LOCKED.
Metadata/aggregation/write/idle notification failures are outside that catch.
Write failure appears in the idle message and log. A notification failure also
cannot restore guards or training stacks. No history enters recovery NBT. Process
exit after restore confirmation but before history commit can lose that result;
active-session attempts also stay in memory until this final save, so a crashed
session is not reconstructed from recovery NBT and may have no history record;
history and inventory are deliberately not a cross-file transaction. Atomic rename
and force do not promise survival of arbitrary filesystem/power failure.

D verification: offline JDK8 test/build actually executed 98 tests with no failures
(after correcting the initial directory guard's treatment of macOS /var symlinks).
HistoryStoreTest adds 8 tests for fresh reads/full round trips, idempotency/conflict,
forget/clear, filtering/pages/ties, malformed/truncated/unknown/oversized data,
ignored temporary files, atomic/write failure preservation, finite/zero/partial
results and overflow. Architecture guard passed at 76 files/12 packages/26 edges.
Real runtime restoration/failure isolation belongs to M2-F, not this unit result.


F follow-up: final storage coverage is 10 tests in the 107-test suite, adding
catalogue/32 MiB budgets, invalid UTF-8/symlinks and generic commit failures. The
real production 8-check history acceptance additionally verifies persistence only
after both confirmed restoration copies, frozen options, real filesystem rejection,
unchecked statistics failure and simultaneous RESTORING/IDLE notification failures.
Exact artifact/runtime evidence is in [M2-F](m2-acceptance.md).
