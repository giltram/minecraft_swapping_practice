# Verification

## Unified layout selection (2026-10-30)

Options no longer has a Profile button. Edit item layout lists the training
selection: a name click selects/persists its ID; the adjacent Edit opens the exact
source without changing that selection. Refresh/reflow never writes preferences.

Offline test/build/smoke compilation passed: 121 tests, zero failures/errors/skips.
Architecture boundaries passed (80 files, 12 packages, 27 edges, no cycles).
The isolated production UI run passed 28 programmatic stages, including selection,
disk persistence, separate editing and finite/unlimited training. Independent
post-exit decoding verified all 41 slots/selection and absent recovery markers in
both saved-player files. Screenshots were inspected. No human input acceptance or
recovery fault-matrix rerun is claimed. [Evidence and artifact hash](verification/layout-selection-2026-10-30.json);
[layout selector](verification/layout-selection-2026-10-30/selector.png),
[options without Profile](verification/layout-selection-2026-10-30/options.png).


Current configuration copy/layout verification is in
[m2-copy-layout-acceptance.md](m2-copy-layout-acceptance.md); the preceding complete
multi-select workflow is recorded in
[m2-multiselect-acceptance.md](m2-multiselect-acceptance.md). The interface uses one
training-count field (0=unlimited) and a direct multi-select hotbar/offhand editor.
Earlier reports below retain their original artifact hashes and dates; references
to the previous numeric-zero UI or separate slot-order entry describe historical
runs, not current instructions. Required human experience checks remain pending.


This document distinguishes automated checks from game acceptance. A build or a source audit does not prove crash recovery or playable input behavior.

Latest default-hint update: target hints now start OFF. `scripts/gradle-jdk8.sh
--offline build` passed and executed all 44 tests with zero failures/errors/skips;
`python3 scripts/check-boundaries.py` passed (54 files, 12 packages, 26 dependencies).
Production SHA256: `582c00907f2ee0a3ec67f582d4039749484904e3ca1d7696abfdb9744d985746`.
No live game acceptance was performed for this default-value change.

## Custom successful-task counts (2026-10-29)

The fixed length-cycle button is now a numeric input with a visible `0 = unlimited`
hint. All positive signed-int goals are accepted, including values above the old
10000 cap; 0 remains unlimited. Invalid/unfinished drafts disable Start/Enter and
leave the previous valid preference unchanged. The screen keeps its draft on
resize and when returning from layout editing.

`scripts/gradle-jdk8.sh --offline test build` passed with **44 tests**, zero
failures/errors/skips. Two new client input tests cover 0/1/custom/large counts,
leading zeroes, empty drafts, invalid syntax and integer overflow. Request/view
round trips cover 0, 1, 73, 10001 and 2147483647; a negative request is rejected.
`python3 scripts/check-boundaries.py` passed: 54 production Java files,
12 packages, 26 directed dependencies, no cycles. Production SHA256:
`7ee0380c89979abd98ee5cce11ce600474adba2465be3bb609f3f528002a9d8d`.

Actual game UI acceptance is pending: type/delete/paste a count; confirm the
0=unlimited hint; reject empty/overflow drafts; press Enter with 1 and
finish exactly one success; check 0 permits manual stop; resize and visit/return
from the editor while keeping a draft. Prior game acceptance evidence was not
rerun and belongs to its recorded earlier artifact.

## All-candidates editor and optional target hints (2026-10-29)

`scripts/gradle-jdk8.sh --offline test build` passed: **41 tests**, zero
failures/errors/skips. Three new geometry tests verify that all candidates remain
reachable in a 320×240 GUI (including a 100-item wrapped pool), clipped rows/gaps
cannot capture header/footer clicks or tooltips, and resizing/reflow clamps
scroll correctly. A new task test samples seven shared block alternatives and
accepts exchanging the two block destinations, including duplicate draws.
`python3 scripts/check-boundaries.py` passed: 54 production Java files,
12 packages, 26 directed dependencies, no cycles. The production archive contains
only class version 52, includes the new candidate geometry and excludes smoke
classes. SHA256:
`62655cd776f251c31933f83d69f3943721ba85796b9b9343d0d24cacecbfd88c`.

Target hints were initially ON; the current default is OFF. A changed setting lasts until client restart. The OFF branch hides only
the hint block, leaving rendered GO/GO_ACK outside it. No new live game/UI test
was performed for this update; the [manual checks](layout-customization.md)
remain pending. Earlier game/fault evidence applies to earlier artifact hashes.
No installed or bundled profile JSON was modified.

## Slot-order editor update (2026-10-29)

After the earlier M1 acceptance checkpoint below, the local profile slot-order
editor was added. `scripts/gradle-jdk8.sh --offline test build` passed with **37
tests, zero failures/errors/skips**, including four new profile persistence tests
and saved-layout Full task generation. `python3 scripts/check-boundaries.py`
passed with 53 production Java files, 12 packages, 26 directed dependencies and no
cycles. Production JAR SHA256:
`54e4b12ee4e8319db1dfd6a36b4009f3a7cd4fd23040c426fec628bf81e7bffd`.

The 25-scenario M1 game evidence below belongs to the earlier artifact hash; it
was not rerun for this UI/profile change. The independent read-only
`verify-owned-smoke-save.py` still confirmed the owned test save's exact baseline
and absent recovery markers. New UI checks are tracked in
[layout customization](layout-customization.md). The existing physical input and
GUI-scale acceptance checklist remains pending.

Rendering follow-up: a user screenshot exposed atlas spill from the 44px slot
buttons. The cards now draw their own backgrounds instead of sampling vanilla's
20px strips. `scripts/gradle-jdk8.sh --offline test build` passed again with all
37 tests executed and zero failures/errors/skips; `python3
scripts/check-boundaries.py` passed with the same 53 files / 12 packages / 26
dependencies. Updated production SHA256:
`358a9d86958fecf0a05572e1389f74f18b2685c942fc65515284491ee6845506`.
In-game visual confirmation of this fix remains pending.

## Automated commands

```sh
scripts/gradle-jdk8.sh test build
python3 scripts/check-boundaries.py
scripts/gradle-jdk8.sh resolvedArtifacts
```

Task tests cover 10,000 seeded Full Inventory tasks and 10,000 Single Item tasks, including 8/9/10 stacks, duplicate values, distinct and reordered NBT, overlapping alternatives, conservation violations and bounded impossible generation. Recovery tests cover all 41 named inventory positions, selected slot, deep copies, record checksums/binding/schema and malformed NBT. Protocol/client tests cover encoding, bounds, revisions and preparation state.

Inspect `build/test-results/test` and `build/reports/tests/test`; retain resolved artifact hashes from `build/resolved-artifacts.tsv`. Verify output classes use major version 52 (`javap -verbose`). Source generation and remapping are separate Gradle tasks.

## Game acceptance checklist

Use only a new marked disposable save. Record exact game, loader, API, Java/native architecture and remapped jar hash. Keep this checklist pending until the stated action is actually exercised.

- Enter single-player on Apple Silicon with Intel JDK8/Rosetta and unmodified original LWJGL; verify all required Mixins apply.
- Seed 36 main + 4 armor + offhand slots with distinguishable counts/NBT; select a nonzero hotbar slot. Start/stop repeatedly; compare serialized inventory and selected slot to baseline.
- Save while training. Inspect both `level.dat/Data/Player` and `playerdata/<uuid>.dat`: training Inventory and active recovery record must occur together.
- Disconnect and quit the game during preparation, countdown, RUNNING and success feedback; reopen, check exact original inventory and no training stacks or duplicate originals.
- Force-kill after initial backup save, after training setup, during training save, after restore replacement, before and after clear-active save. Reopen after each interruption and compare baseline. Replay active recovery twice to prove idempotency.
- Inject save failures/read-only paths, malformed schema/checksum/world/player identities, truncated and wrongly typed records. Save failure must prevent training replacement; recovery failure must retain raw data and lock interaction. Restore permissions and retry stop.
- Single and Full: finish exactly N successes (invalids do not count); unlimited stops manually. Check GO ACK begins timing only after rendered GO and before first allowed vanilla swap.
- Rebind Swap Hands, test number/scroll selection, no-op swaps, necessary temporary moves and multiple swaps in one server tick. Initialization contributes zero observations. A success is captured before another same-tick swap.
- Try inventory/crafting/creative clicks, dropped items, arrows/tridents, experience pickup, use/place/break/attack/container transfer, commands, death/hunger/status effects, LAN publication, pause and lost focus. Invalid action cannot score; ordinary play outside a transaction remains unchanged.
- Open inventory through feedback into the next task: PREPARING must wait for close/focus/released Swap Hands rather than running in a screen. Test GUI scaling, small windows and all visible alternative predicates.
- Run the remapped production jar in an independent 1.16.1 Fabric installation. `runClient` uses named development classes and does not satisfy this check.

## Current execution results

Checkpoint: **2026-10-29**, implementation and the remaining automatable game acceptance are complete. Physical input and visual acceptance remain pending; ordinary-world training is still gated. See the [human checklist](manual-acceptance.md).

| Check | Executed result |
| --- | --- |
| JDK8 build, source generation and remapping | Passed. Latest `scripts/gradle-jdk8.sh --offline build smokeJar resolvedArtifacts` completed successfully; unchanged production/test tasks were up-to-date and the latest smoke sources were compiled. |
| JUnit | Fresh `scripts/gradle-jdk8.sh --offline test --rerun-tasks` executed all 33 tests with zero failures/errors/skips, including 20,000 seeded tasks and actual vanilla item packet/save normalization. The final build reused these fresh reports. |
| Architecture boundaries | `python3 scripts/check-boundaries.py` passed: 52 production Java files, 12 packages, 25 directed dependencies, no cycles. |
| Production artifact | Exact Minecraft 1.16.1 contract, class major version 52 only, refmap present, no smoke classes. SHA256 `64c643dff049d6fd1e59dfedff61f2aa41384fb9064255c6893ac962150adfc7`. |
| Development client | Intel Corretto8/Rosetta and original LWJGL3.2.1 entered the owned superflat save. Single3 and Full3 separately passed real READY/rendered-GO/GO_ACK, vanilla packet swaps and exact 41-slot/selection restoration with both save readbacks. |
| Complete production normal smoke suite | Passed after the PREPARING held-slot and initial-ground fixes: Single3, Full3, forbidden-drop INVALID plus replacement1, unlimited-stop2, exactly one injected no-op, same-client-batch vanilla swaps plus trailing swap. All four sessions restored 41 slots/selection and passed both save readbacks. Fresh result-normal reports passed. |
| Independent production launch | Passed: official client + non-development Fabric Loader loaded the remapped mod and completed the normal suite. The earlier failed START/partial runs remain in the event history. |
| Production normal exit from PREPARING | Passed this one exit case. After shutdown, independently decoded UUID playerdata and host level.dat both matched baseline inventory/UUID/selection exactly: 41 populated slots, selection6, no recovery marker. This is not a force-halt/load-time recovery result. |
| Intentional process halt and reload recovery | Passed for the RUNNING confirmed-save cut. After both authoritative files contained training inventory plus the active original snapshot, `Runtime.halt(91)` exited with observed status91. A fresh recover launch restored all 41 slots/selection, confirmed the cleared marker in both files and exited0. |
| Inventory-module crash boundaries | Four pairs passed: after confirmed backup, after setup before a training save, after active replay restored memory while disk still held training inventory, and after confirmed clear. Each actual halt91 was followed by recover0 and exact baseline/cleared-record readbacks. Pre-halt memory/disk NBT and both saved files are archived with verified SHA256. These are direct inventory-module boundaries, not injections inside SessionManager.stop or a filesystem write. |
| Repeated replay and real I/O failures | Five checks passed: replay the same active record twice, plus initial-backup and clear-commit failure independently for UUID playerdata and host level.dat. POSIX directory permissions denied a real write; failed confirmation retained the validated recovery record and blocked vanilla swaps. Exact permissions were restored in finally, followed by successful restore/retry and both save readbacks. |
| Remaining manual acceptance | Pending: physical/rebound keys, number/scroll input experience, actual focus/pause/screen UI behavior and GUI scales/readability. The automatic protection/exit/write/corrupt-load matrix passed below. |

Runtime testing found and corrected vanilla's implicit `Damage:0` materialization and the onGround update skipped by stationary movement cancellation. Those fixes are covered by the checks above; they do not establish completion of the remaining matrix.

The current production command is:

```sh
python3 scripts/run-production-smoke.py --offline --mode normal-crash --launch
# Only after this launch records normal=passed and crash=ready:
python3 scripts/run-production-smoke.py --offline --mode recover --launch
python3 scripts/run-production-smoke.py --offline --mode faults --launch
python3 scripts/run-inventory-cuts.py
```

See [production-smoke.md](production-smoke.md) and [smoke.md](smoke.md). Inspect the **latest boot segment** of `run-production/smoke/events.jsonl` and result modification times: events append, and an older result file can remain after a subsequent failed/incomplete launch. File existence alone is not a passing result. Keep development `run/smoke/` evidence distinct from production evidence.

[Earlier checkpoint evidence](verification/milestone1-2026-10-29.json) records the artifact hash, report counts, production events, normal/crash/recover results, observed exit codes, five fault checks, four direct inventory cut-pair reports and save hashes. Raw cut evidence remains under `run-production/smoke/inventory-cuts/`. Same-client-batch swaps were exercised; exact server tick IDs were not recorded. Human input/visual checks are not implied to have passed.

## Remaining acceptance execution

**25 distinct scenarios passed**, each followed by a separate production `recover`
process checking all 41 inventory positions, original selection and cleared record
in both UUID playerdata and host level.dat. All launches used the production hash
above, Intel Corretto8/Rosetta, original LWJGL3.2.1 and the exact owned smoke world.
Production trainer sources and its artifact hash did not change in this task.

| Scenario group | Executed result |
| --- | --- |
| Vanilla protection and failure/retry | 27 checks passed: 18 forbidden handler cases; passive item/XP/arrow/trident/damage/healing/food/status/equipment/movement guards in four phases; actual START and stop write failures for both save paths; ordinary swap/pickup/status/healing behavior after release. Failed START reached automatic restore and confirmed the already-original, cleared disk state. Failed stop retained the exact backup in ERROR_LOCKED until permissions reset and retry committed. |
| Phase, screen and dimension gates | Eight checks passed: swaps at PREPARING/COUNTDOWN/SUCCESS; interruption protocol at PREPARING/COUNTDOWN/RUNNING; a real server screen held through success feedback blocks READY until closed; actual dimension travel is rejected without loading terrain. Human focus/UI testing remains separate. |
| LAN | Actual publication stopped/restored an active transaction; another START was rejected. Fresh reopen passed. |
| Disconnect and client shutdown | All eight request/reopen pairs passed, from PREPARING, COUNTDOWN, RUNNING and SUCCESS for each exit type. Request phase and actual cleanup phase/hook are recorded separately. Vanilla may READY or invalidate while closing its UI; client shutdown normally restores through player removal before the server-stopping hook. COUNTDOWN/SUCCESS deadlines were extended to prevent expiry while queueing exits. |
| Actual corrupt-record load | Eight fixture/load/repair/reopen sequences passed: schema, checksum, player/world binding, missing original snapshot, wrong snapshot type, scalar raw recovery and inactive flag. Fixtures were re-signed where needed to isolate non-checksum rejection. Real load locked interactions; exact raw data survived a failed stop and both saves. Repair used only that fixture's archived valid backup. |
| Actual compressed writes and stop boundaries | Six halt91/recover0 pairs passed: UUID/host compressed saves interrupted after **32 bytes** during training and during actual SessionManager.stop, plus actual stop before/after confirmed clear. Partial temporary files and both authoritative saves are archived before recovery. Stop cases explicitly observed the real stop method. |
| Independent post-exit readback | `python3 scripts/verify-owned-smoke-save.py` passed after all clients exited. A separate standard-library typed NBT decoder confirmed exact inventory, selection6 and UUID, and no recovery key in both files. World/playerdata permissions are back to 0755. |

Run the current matrix with:

```sh
scripts/gradle-jdk8.sh --offline build smokeJar resolvedArtifacts
python3 scripts/run-remaining-acceptance.py --group all
python3 scripts/verify-owned-smoke-save.py
python3 scripts/check-boundaries.py
```

[Consolidated evidence](verification/milestone1-remaining-2026-10-29.json) selects
25 independently passed/reopened entries, verifies their production launch hashes
and records fresh unit/build/boundary checks. Some source run reports ended failed
at a later driver case; only individually passed entries with successful fresh
reopens are selected. Five failed driver attempts remain archived, including
phase observation, lifecycle ordering and startup/physics preconditions. They
are not counted as passing cases. Raw reports, logs, corrupted records and partial
saves remain under `run-production/smoke/remaining-acceptance/`.

These tests supply READY/GO_ACK and accelerate countdown through the public session
interface/reflection. They do not claim physical input, normal feedback duration,
rendered timing or subjective UI quality. The earlier normal smoke's real
client/rendered handshake evidence remains separate.

## Persistence limits

The transaction is embedded in the same full player NBT as vanilla Inventory. Initial backup and restoration save both require exact disk readback from UUID playerdata and host level.dat, including player UUID, inventory, selection and active record. Vanilla save return values are not treated as acknowledgments. Recovery replaces every slot rather than merging/giving stacks.

Readback verifies the process-visible saved bytes; it does not promise durability across arbitrary power loss, filesystem corruption or inconsistent manual rollback. Statistics remain independent of inventory recovery. Ordinary save training stays gated until crash and failure acceptance is complete.

## M2-A client options verification

Checkpoint **2026-10-29**. Only local client option persistence changed here.
Earlier M1 runtime results and artifact hashes above belong to their recorded
checkpoints; they do not establish game acceptance for the artifact rebuilt here.

| Executed check | Result |
| --- | --- |
| `scripts/gradle-jdk8.sh --offline test build` | Passed. The final `test` task executed; 56 tests, zero failures/errors/skips. Build, jar/source remapping and assembly succeeded. The first sandbox launch was blocked by its daemon socket restriction; the same offline command passed with the restriction lifted. |
| `ClientOptionsStoreTest` | Five tests: defaults without file creation; both modes/hint states and goal boundaries across replacement/reload; schema/type/range/ID validation; malformed/truncated/duplicate/oversized/non-UTF-8 preservation and write lock; actual replacement failure with temporary-file cleanup and retained sentinel data. |
| `ClientOptionsPersistenceTest` | Seven tests: all four preferences through a new client composition; invalid drafts never written, including another option edited during an invalid draft; corrupt-load safe defaults and preservation across edits; missing-profile fallback; empty-catalogue deletion/repair; retry of the same repaired profile; save error feedback and successful retry. These use temporary files and real profile parsing, without a Minecraft launch. |
| Existing input/protocol tests | Passed in the same full suite, including unchanged valid-goal and invalid-input contracts. |
| `python3 scripts/check-boundaries.py` | Passed: 56 production Java files, 12 packages, 26 directed package dependencies; no boundary violations or cycles. |

Pending game acceptance (not claimed by unit/build success):

- In an isolated client configuration and a new marked disposable world, change
  mode/profile/goal/hints, quit the client normally and relaunch; verify all four
  restored selections and unchanged training request behavior. With no file,
  verify Single Item/default/10/OFF and no file created just by opening O.
- Leave goal empty or overflowing, toggle another option, resize and visit/return
  from the layout editor; verify the draft/error, disabled Start and last valid
  goal on disk.
- Remove the selected profile while stopped; verify the visible default/first
  fallback and working selection. Remove all valid profiles, verify disabled
  Start/layout and usable Done/Profile retry, then repair and retry selection.
- Start with a damaged/unsupported configuration; verify visible error/repair
  instructions, usable safe options, and byte-for-byte preserved source after
  edits and exit. Move/repair it and restart before testing saving again.
- Exercise a client-config write failure in the isolated config directory;
  verify the error is readable at supported GUI scales and a later accepted
  option change retries. Check HUD hints and GO behavior with restored ON/OFF.

No save recovery, training timing, wire encoding or ordinary-world gate changes
are part of M2-A; no additional M2 implementation is included.


## M2-B profile backend verification

2026-10-30: `scripts/gradle-jdk8.sh --offline test build` passed with **75 tests,
0 failures/errors/skips**, including the existing M2-A tests and 19 new editing,
source/backup/atomic-failure and actual-vanilla-normalization regressions.
`python3 scripts/check-boundaries.py` passed: **60 production Java files,
12 packages, 26 directed package dependencies**, no cycles. Initial sandboxed
Gradle daemon startup was blocked by local socket permissions; the same offline
command passed with local process access.

[M2-B execution record](m2-b-profile-editing.md) documents public APIs, exact mode
preconditions versus bounded generation errors, test coverage and future GUI
integration. Writes used temporary directories; installed configuration, real
saves and the bundled default were unchanged. No game was launched and these
results do not count as game/UI/recovery acceptance. M2-C was not started.


## M2-C visual profile editor verification

2026-10-30: `scripts/gradle-jdk8.sh --offline test build` passed on the final
production sources. **90 JUnit tests, zero failures/errors/skips** (15 new client
coordination/viewport/registry tests); the exact final command reused test output
from the immediately preceding successful final-source test/build execution.
`python3 scripts/check-boundaries.py` passed: **71 production Java files,
12 packages, 26 directed dependencies**, no cycles. `smokeJar` and Python launcher
syntax checks passed separately. Initial sandbox daemon socket restrictions were
resolved by allowing local process access for the same offline command.

The separate M2-C driver runs the production mod in an explicitly owned new
`run-profile-editor-ui/` configuration and a verified inactive disposable-world
copy, with the original Intel JDK8/LWJGL3.2.1 baseline. It never starts training or
changes inventory. The real-renderer screen flow, programmatic input, resize,
122-candidate reachability, cancel/reset/save/reopen and stale-file feedback are
detailed in [the M2-C record](m2-c-profile-editor.md), with the compact
[executed evidence](verification/m2-c-profile-editor-2026-10-30.json). Raw logs,
result, screenshots and launch hashes remain in the owned run directory.

The final actual-client run passed all **30 staged steps** and exited with code
0. It includes 122-candidate reachability, native text/Tab/Enter/Esc dispatch,
actual 640×480 window resizing and GUI-scale change, confirmed cancellation and
clean reopen, save/fresh handle, stale-source rejection and explicit reload.
Independent post-exit typed NBT readback found both saved copies' complete
inventory/selection/UUID unchanged from the owned seed and no recovery key.
The production JAR was Java 8 class version 52 and excluded the optional driver.

The first actual-client pass exposed overly wide vanilla button texture seams;
those were fixed and rerun. Preparation failures are preserved separately and
are not counted as passes. The final evidence records actual-client status and
independent post-exit typed inventory/recovery readback. Earlier M1 recovery
results still belong to their original artifacts.

Pending: physical mouse/keyboard and wheel/tooltip acceptance, other GUI scales
and localizations, actual active/restore/ERROR_LOCKED/LAN transitions while editing,
corrupt/duplicate/write-denial runtime presentation, and training after a saved
visual edit. Unit/build/automated UI success does not establish these manual or
recovery/timing scenarios. No daily configuration, real save or bundled default
was changed. M2-A/M2-B and slot sorting are retained; no history/PB/M3 work follows.

## M2-D/E/F final-artifact verification (2026-10-30)

The implementation, contracts, artifact hash, exact evidence classes and remaining
required observations are consolidated in [m2-acceptance.md](m2-acceptance.md).
The final `scripts/gradle-jdk8.sh --offline test build` actually executed **107**
tests, zero failures/errors/skips; production compilation/remapping reused unchanged
outputs. `python3 scripts/check-boundaries.py` passed at 78 production files,
12 packages, 27 directed dependencies. New history tests use only temporary roots.
The production archive remains Java 8/class52 with required resources/refmap and
without any smoke/fault/UI driver classes. No native/dependency baseline changed.

D/E implementation success does not establish full M2 acceptance. The current
production runtime, actual process halt/reopen, rendered UI automation, independent
NBT readers and still-pending human physical input are reported separately in
that matrix. Older M1/A/B/C results above retain their original hashes and scope.

The unlocked final normal rendered smoke **passed all four stages**, followed by
actual halt91 at a confirmed training save and fresh recover0. STOP now travels
from client END tick on the same connection as preceding vanilla input; the final
driver retains strict immediate inventory/selection comparison with no grace
period. Both authoritative saved copies independently match all 41 original
positions, selection6 and UUID with no recovery marker after exit. See
[final normal evidence](verification/m2-normal-final-2026-10-30.json).
The earlier direct-stop failure remains archived, and its exact live mismatch
cannot be reconstructed; no production change/hash update was made. Along with
the separate 25-case matrix, 65-stage UI/restart and 8 history checks, automatable
verification is complete. Required human physical-input/visual acceptance remains
pending and the ordinary-world gate stays enabled.
