# M2 D/E/F acceptance matrix

Current GUI update: [multi-select layout and training-count input](m2-usability-plan.md)
reuse these backend contracts. [Current verification](m2-multiselect-acceptance.md)
records its own artifact and executed checks; earlier artifact results below are
historical, not automatic acceptance of the new GUI.

Checkpoint: 2026-10-30 (Asia/Shanghai). D/E implementation delivered; F automated
acceptance and pending human observations are recorded below. **M2 is not fully accepted while required
physical-input and subjective visual checks are pending.** Ordinary-world
training remains gated. No M3 work is included.

Production artifact: `build/libs/hotbar-swap-trainer-0.1.0.jar`, SHA256
`e4c24c96e48a2d40483f53c13cccd1c405b756c8b0a88ee64aa0ff2436fa126e`.
All executed production checks in this matrix use this hash.
[Consolidated verified evidence](verification/m2-def-2026-10-30.json) records
checks, manifests, screenshots, readbacks and required pending items. Java classes are
version 52 only; mod metadata, languages, defaults, required Mixins/refmap are
present; smoke/fault/UI-driver classes are absent. Audit:
[artifact evidence](verification/m2-artifact-2026-10-30.json).

Environment: official Minecraft 1.16.1, Loader 0.9.3+build.207, Fabric API
0.18.0+build.387-1.16.1, Intel Corretto 8u512 x86_64 under Rosetta on Apple Silicon,
original official LWJGL 3.2.1 native baseline. Launches set fabric.development=false;
full classpath/mod/native hashes and JVM architecture are in each launch manifest.
Build JDK and native-client JDK are independently selected; no dependency/native
substitution was made.

## Evidence classes and results

| Requirement / expected behavior | Execution / evidence type | Actual result and evidence location |
| --- | --- | --- |
| Preserve A/B/C and slot-order contracts; D storage, PB and protocol correctness | JUnit + JDK8 static/build checks | PASS: `scripts/gradle-jdk8.sh --offline test build` actually executed test, **107 tests, 0 failures/errors/skips**. Build/remapped production unchanged tasks were up-to-date. `build/test-results/test`, `build/reports/tests/test`, final build log archived with consolidated evidence. |
| Acyclic boundaries; no common→client, profile→task or domain runtime access | Source boundary guard | PASS: `python3 scripts/check-boundaries.py`, **78 Java files / 12 packages / 27 directed edges**. Client→stats is the only new package edge. |
| Fresh history read; all fields; repeated same contents; conflicts; cleanup keeps disk; exact order/filter/pages | Temporary-directory HistoryStoreTest | PASS. 10 tests cover these plus bad/truncated/duplicate/unknown/UTF-8 files, numeric/task/byte/catalogue bounds, symlinks, atomic/write failures and retained originals. No daily config/save writes. |
| Correct PB groups/hash/hints/goal/versions; zero/partial/unlimited; stable ties; incomplete warning | Pure PersonalBestsTest (5) and HistoryBrowserTest (1) | PASS. PB derives solely from valid snapshot; invalid/stopped do not score, finite PB requires exact COMPLETED goal. No asynchronous history transport exists, so stale history responses do not apply. Existing session stale-response tests pass. |
| Single/Full finite; unlimited invalid + stop; zero-success STOPPED; frozen hints/goal; post-restore persistence | Actual production server/vanilla-handler programmatic acceptance | PASS first history run: `run-production/smoke/remaining-acceptance/20261029T164722.606788Z/report.json`, 7 checks plus fresh recover process. Observer verifies both restored saved copies before save is called. Extended notification-failure run is tracked in consolidated evidence. |
| Statistics write/runtime/notification failure cannot retain training stacks or re-lock player | Real filesystem path rejection + isolated smoke injection | Actual filesystem rejection and unchecked store failure PASS in first history run. Final extended **8-check PASS**: `run-m2-runtime-20261030/smoke/history-checks.json`, `result-accept-history.json`. RESTORING/IDLE send failures are injected only by the separate smoke module, while the result store also throws. Original inventory and released session/guards verified. |
| Edit name/rule/candidate/count/NBT; keep raw drafts; resize/child return; cancel; save/reopen; actual training uses changed profile | Actual rendered production client with programmatic widget input and server packet handlers | PASS: **65 stages**, `run-m2-ui-20261030-errors/editor-ui-first-result.txt`; screens 01–17. Saves OPTIONAL/Dirt/count32/typed `{M2:1b}`, server reload asserts all fields, solves one task, confirms restoration and generated history. No physical-input claim. |
| Empty/multipage/long-name history; details/PB; filters; corruption/unknown warning; retained page; GUI geometry | Actual rendered client + pure geometry tests | PASS programmatic scope: empty history, 23 fixtures/3 pages, full hash/name snapshot, detail/filter/refresh, warnings, 320×240 Screen resize, native 640×480 window and GUI-scale change. `run-m2-ui-20261030-errors/screenshots/09-*` through `17-*`. Forced Screen dimensions are distinct from the native-window/scale check. Manual visual/tooltip usability remains pending. |
| Four options and history/PB survive full client exit/relaunch | Separate production process | First 35-stage fixture restart PASS: 23 records + PB + 2 preserved warning files, `run-m2-ui-20261030/editor-ui-result.txt`. Final 65-stage restart PASS with 25 records (23 fixtures + completed training + recovered stopped session), derived PB and 2 warnings: `run-m2-ui-20261030-errors/editor-ui-result.txt`. |
| Active/PREPARING/RESTORING/ERROR_LOCKED/LAN block entry/save; restored idle permits it | Actual client projection from live server session | PASS in stages 47–64. Test-only bounded restore latch exposes actual RESTORING; actual UUID-directory write denial produces ERROR_LOCKED; permissions reset in finally, retry confirms both saves; real LAN publication blocks entry. Draft/source unchanged through blocked saves. Screens `15-*`, `18-*`, `19-*`, `20-*`. |
| Normal start/stop and finite completion; real rendered READY/GO/GO_ACK, no-op, invalid replacement, unlimited stop | Production normal smoke | **PASS** after unlock and test-driver ordering correction: Single3, Full3, invalid replacement1, unlimited stop2, one no-op and same-client-batch swaps. All four strict immediate 41-slot/selection comparisons passed; no grace-period acceptance. Then actual halt91 at confirmed training save and fresh recover0 passed. [Final normal/crash/recover evidence](verification/m2-normal-final-2026-10-30.json). Earlier failed direct-stop attempt is retained as historical evidence. |
| M1 protection, phase/LAN, eight disconnect/shutdown phases, corrupt recovery loads, actual write/stop cuts | Existing owned 25-case production matrix, each followed by new recover process | **PASS all 25 cases**, each with a fresh recover process; 8 corrupt fixtures also had separate locked-load/repair processes. Report: `run-production/smoke/remaining-acceptance/20261029T165022.138372Z/report.json`. Final selected passing reports/hash/evidence are consolidated after completion. |
| Two authoritative saves after exit: all 41 positions, selection, UUID, no recovery marker | Independent standard-library typed NBT reader | Pre-run baseline PASS. PASS final post-exit independent readers: [regression saves](verification/m2-regression-save-readback-2026-10-30.json), [UI saves](verification/m2-ui-save-readback-2026-10-30.json), [runtime saves after final actual halt/recover](verification/m2-runtime-save-readback-2026-10-30.json). Exact 41 positions/selection6/UUID and absent marker in both files; permissions0755. |
| Corrupt config, stale/duplicate profiles, bad quantity/NBT, denied writes preserve source/draft and remain operable | Existing file/backend tests + actual stale/invalid UI workflow | Automated file/backend PASS; actual invalid count/NBT and stale-file UI PASS. Actual profile denied-write and duplicate-ID Save failures, retained source/draft and explicit reload PASS in stages 51–54; corrupt-options load through the client composition seam and real options-screen feedback/write lock PASS in stages 55–57. Screens `21-*`–`23-*`. Physical visual judgment remains pending. |

## Integration decisions, defects and limits

- Restored inventory no longer shares an exception catch with aggregation/idle
  notification. This closes the path where a post-restore exception could mark
  recovery failed even after the inventory had committed. History errors are
  reported separately, and test-only filesystem/runtime/notification faults verify
  that guards/session state stay released.
- Initial history directory validation incorrectly rejected macOS's system `/var`
  alias in temporary-directory tests. The guard now checks the explicitly injected
  history root and its descendants, while treating its parent as trusted.
- The O page now shares the existing clipped scroll form to make all new/old
  actions and long error messages reachable in small windows.
- The old normal driver stopped unlimited training directly on the server thread,
  bypassing the ordering of vanilla packets queued by the client. The final driver
  queues STOP at client END tick on that same connection after prior input. It
  retains strict immediate 41-slot/selection comparison and archives observed NBT
  on any mismatch; the intermediate selection grace-period diagnostic was removed.
  Final normal/halt91/recover0 and independent readback passed on the same production
  hash. The earlier failed live snapshot was not captured, so its exact differing
  slots/selection cannot be reconstructed or conclusively attributed. Its logs and
  saved files remain archived; no production change was justified or made.
- A screenshot taken immediately after a forced Screen resize still captured the
  preceding framebuffer. The driver now captures that view on the following
  rendered step. This is a test-evidence timing correction, not a production fix.
- Profile hash remains the existing full normalized profile hash, including ID
  and name; renames normally split the hash group. No A/B/C hash contract changed.
- History is installation-wide across worlds. It is not inventory recovery data;
  crash between confirmed restoration and history commit can omit a result.
  Active-session attempts are also memory-only until final save; recovery does not
  reconstruct an interrupted session history from inventory NBT.
  Reads have explicit file/task/catalogue/byte limits and warn when incomplete.
  PB has no separate persistent cache. Hints are client declared, not anti-cheat.

M1 retest rationale: D changes START's wire DTO, frozen session metadata and the
post-restore lifecycle catch, so all existing phase/exit/protection/write/corrupt-load
cases are selected again, including actual halt/reopen. Recovery algorithms and
NBT schema are unchanged. Direct inventory cut acceptance from older hashes is
retained as historical evidence, not relabelled as a final-artifact pass.

## Pending required checks and next operations

The agent cannot supply human physical mouse/keyboard observations. Keep these
pending; passing JUnit, screenshots, synthetic widget input, supplied server
acknowledgements and real process cuts are separate evidence types.

The unlocked rerun and driver-corrected final run are complete. The latter's
normal/crash/recover evidence, raw before/after saves and log manifests are under
`run-m2-runtime-20261030/smoke/m2-evidence-20261030/normal-ordered-stop/`.
The earlier failed attempt remains under `normal-failed/`; an intermediate unlocked
run passed without triggering its temporary selection diagnostic and remains under
`normal-unlocked/`. Only the final strict ordered-STOP run is selected as final
normal evidence. Nothing below is established by that programmatic run.

1. In the owned disposable UI instance, use real O/mouse/keyboard and wheel to
   repeat options/profile/slot-order edits, save/cancel/Back, count overflow/raw
   SNBT, search/focus/Tab/Enter/Esc, long candidate lists and clipped tooltips,
   including potion/custom-NBT details. Record resolution, GUI scale, binding and
   pass/fail. Repeat at native minimum size and several GUI scales/locales.
2. With the smoke driver removed via a separately prepared owned manual instance,
   inspect the visible disabled controls and readable state transitions with real
   input. Live PREPARING/RESTORING/ERROR_LOCKED/retry/LAN gates already have actual
   programmatic-client evidence, including retained source/draft and two-save
   restoration; this remaining item is the physical-input/visual observation.
   Never inject failures into a real save.
3. In isolated fixture config only, physically inspect the already automated
   corrupt-options/duplicate-profile/denied-write feedback and repair actions.
   Source/draft preservation and explicit reload have programmatic-client evidence;
   confirm the instructions and focus/mouse operation are clear to a human.
4. Complete the [physical input checklist](manual-acceptance.md), including rebound
   Swap Hands, number/wheel input, focus/pause/screens and hints ON/OFF for Single
   and Full. Do not unlock ordinary-world training until required acceptance passes.

No unrelated architectural refactor is needed. Storage/query/PB/UI ownership is
explicit and directed. Future work should address the pending observations above,
not expand into M3.
