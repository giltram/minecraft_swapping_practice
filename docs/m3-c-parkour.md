# M3-C: concurrent parkour and inventory practice

Status (2026-11-06): implemented. Final-artifact automated checks passed; human route/physical-input acceptance remains pending. The user confirmed the rules
below and the explicit v2 conversion design. Runtime acceptance is in progress;
no human route-completion acceptance is claimed.

## Confirmed gameplay rules

| Event | Inventory task | Course |
| --- | --- | --- |
| START | One task in either existing mode, SETUP/countdown/GO_ACK unchanged | Prepare at start; first GO_ACK starts monotonic time |
| Task succeeds first | Freeze success; do not generate another task | Continue to finish |
| Course finishes first | Continue the current task on the finish platform | Freeze course time and progress |
| Both complete | Restore original inventory, then end session | Stay safely on finish; no automatic repeat |
| Fall/out of allowed route | Preserve current layout/task/progress/GO_ACK clock and backup | Count once, return to last checkpoint, timer continues; require matching teleport confirmation and fresh supported observation |
| Pause/focus loss/screen | Existing invalidation and SETUP retry, unless already successful | Timer/progress continue, including actual paused wall time |
| STOP/disconnect | Restore inventory through the existing confirmation/lock boundary | Freeze unfinished attempt; safe course-platform placement after restore |
| Restart/process cut | Recover original inventory first | Discard live attempt; never count offline time or infer success |

Parkour fixes the effective successful-task goal at **one**, even if a client
sends 0 or another count. The options count is disabled for Parkour and retained
for Stationary/Moving. There is no multi-run quota, unlimited Parkour loop,
combined score, fall time penalty or inventory-task restart on fall. An invalid
inventory attempt retries through the existing preparation path under the same
original backup. Moving STOP retains its existing in-place locomotion semantics.

## Confirmed layout conversion scope

Keep the base generator/spec `flat-64-v1` and exact v1 reads unchanged. v2
ownership/seal add target layout/course ID and seed, with source layout/seed in
the pending journal. The course ID includes its specification version. A layout
change archives exact original journal/seal bytes, then writes a new operation
before modifying terrain. Pending work replays that same target and cuboid.
Old v1 READY worlds are not rewritten on read. v1 pending operations finish as v1.
v2 requires an established predecessor and cannot claim a creation/initialization
origin. Reset without a predecessor and self-referencing operations are damaged
records, not permission to regenerate. Their raw bytes remain unchanged.

The user authorized this design, **not silent conversion of existing saves**.
Each actual flat/course switch requires the in-game destructive confirmation.
It rebuilds the entire already owned x/z=-32..31, y=0..255 volume, discarding its
blocks/container contents; no bounds expansion, world deletion, terrain outside
the cuboid, path changes or recovery rebinding. Reset preserves current layout;
checkpoint return changes no terrain. Unknown/damaged/unsupported data stays raw
and cannot authorize conversion. Generation and live checks do not imply durable
success: the saved chunks/settings/seal/player copies must pass readback first.

## Baseline and affected contracts

The initial index and working tree were clean. Read AGENTS.md, architecture,
M3-A/M3-B records, arena README, session/environment implementations, inventory
recovery, protocol, history/PB contracts and related tests. Historical proposals
do not override current code or confirmed M3-A free-movement behavior.

- `task`: inventory generation/validation only; no course or runtime dependency.
- `arena`: current `flat-64-v1` owns overworld x/z=-32..31, y=0..255. Journal
  schema 1 and seal version 1 bind exact spec/operation/path. Both live section
  checks and independent saved-chunk checks require flat blocks and air. READY
  reuse must never reinterpret that record as authorizing another layout.
- `environment`: owns accepted-position observation and environment behavior.
  Moving observes X/Z diagnostically; it never invalidates standing still or
  teleports on STOP. Course configuration and mutable attempt state must be
  separate, with runtime observations independent of inventory tasks.
- `session`: owns GO_ACK timing, successful-task quota, stop/restoration and
  results. The existing finite-quota exit cannot independently coordinate a
  course. Extend with a focused coordinator rather than accumulating course
  geometry and position rules in SessionManager.
- `inventory`: backup once per session; task SETUP reuses that transaction.
  RESTORING/ERROR_LOCKED remain authoritative. Course and history errors never
  clear, replace or rebind recovery data.
- `stats`: current inventory aggregates are successful-task durations, not course
  times. Schema-1 completed finite history requires its exact success goal.
  Parkour completion metadata must not weaken legacy validation. Course results
  need explicit identity/spec/seed/rule/timing comparison conditions.
- `network/client`: protocol 3 currently has two environments and task-bound
  READY/GO_ACK. New course views/actions require bounded versioned DTOs and
  explicit stale-event handling, including when inventory tasks have finished.
  Reuse the environment selector, HUD and title-world entry.

The composition root injects arena readiness and runs recovery before arena work.
Keep arena independent of session/stats/network and session independent of arena
implementation. Share immutable course contracts below both terrain and runtime
consumers if needed; do not introduce a registration/plugin framework.

## Fixed-version source inspection

Inspected the local Loom-generated Minecraft 1.16.1 / Yarn 1.16.1+build.21
sources, not modern API documentation. Do not commit vanilla source copies.

- `ServerPlayNetworkHandler.onPlayerMove`: main-thread fence at source line 703;
  pending teleport branch at 713 skips movement acceptance. Accepted collision
  branch updates authoritative position and calls `increaseTravelMotionStats`
  at 796; rejected movement requests correction at 801.
- Accepted movement sets `onGround` from the packet at 791. Consequently that
  flag alone is insufficient platform evidence: also inspect feet height,
  actual support/collision, body clearance and sequential accepted observations.
- `teleportRequest` at 824 immediately updates server position, stores target
  and increments request ID. Seeing that new position does not establish arrival.
- `onTeleportConfirm` at 377 clears the pending target only for the matching ID.
  Re-entry must wait for this accepted boundary and fresh supported movement;
  retries, stale positions and system teleports must not advance a course.
- `Entity.move` at 499 resolves collision; verticalCollision/onGround are derived
  at 528–529 before the network handler's packet flag assignment.

Before implementation, inspect any further collision/save adapter methods used
and record exact hooks in version-hooks.md. Source inspection is not runtime or
human route acceptance.

## Execution plan

1. Confirm rules and conversion scope (done; table above).
2. Centralize one immutable deterministic basic course and conservative jump
   templates. Check bounds, gaps, elevations, head/body clearance, spawn and
   platform identity. Reuse ordinary full blocks; no consumable/equipment gates.
3. Extend strict ownership/seal/layout validation without changing v1 meaning.
   The proposed conversion uses explicit source/target descriptors, retains the
   original ownership bytes, and journals intent before bounded terrain work.
   Conversion design is confirmed. Reuse the existing
   server-thread section budget, operation replay and independent saved readback.
4. Implement the pure course attempt state machine and the narrow vanilla
   observation/teleport adapter. Gate start, sequential checkpoint contact,
   finish and falls on accepted supported positions. Keep checkpoint return
   separate from arena reset and exclude it from valid progress.
5. Add the small session coordinator for independent inventory/course completion,
   timing and results according to the confirmed table. Retain one recovery
   backup, original vanilla swap/selection paths and GO_ACK task timing.
   Stop/exit/environment/terrain operations must respect restoration gates.
6. Add Parkour to existing options/protocol/HUD, minimal en_us/zh_cn feedback and
   separate course results. Preserve legacy configuration/history and isolate
   Stationary/Moving/Parkour inventory PBs. Persist no live course attempt in
   recovery NBT; interruptions/restarts cannot invent completion or offline time.
7. Execute focused tests, boundaries and pinned JDK8 build. Add only excluded
   smoke fixtures for fault injection. Use independent owned production instances
   for runtime and real process cuts; independently verify both saved-player
   copies and arena layout after exit/reopen. Update architecture, module
   contracts and guide with the final implementation.

## Acceptance checklist

- Determinism, template geometry and all writes within existing bounds.
- Legacy v1 reuse without rewrite; converted layout live/disk READY validation.
- Conversion/reset interruption replay, stable ownership and bounded work.
- Supported start, ordered checkpoints and finish; skip/below/teleport rejection.
- Falls, checkpoint return, repeated observations, stale/duplicate confirmations.
- Both inventory modes; finite/zero goals and both completion orderings.
- Confirmed fall task policy without backup, input or GO_ACK timing pollution.
- Pause/focus, stop, exit, disconnect and restart recovery; locked-state refusal.
- Old options/history, environment PB isolation and course comparison identity.
- Stationary/Moving and all M3-B enter/reuse/reset/exit regressions.

Record unit tests, programmatic movement, actual process interruptions, physical
keyboard input and human route completion separately. Human completion is still
required; neither geometry checks nor programmatic teleports establish playability.

## Checks executed during investigation

- `python3 scripts/check-boundaries.py`: passed; 100 production Java files,
  13 packages, 35 directed dependencies.
- `scripts/gradle-jdk8.sh --offline test build smokeJar`: BUILD SUCCESSFUL;
  test/compile tasks were UP-TO-DATE, so this was not a fresh unit-test execution.
  The first sandboxed launch failed to bind Gradle's local daemon socket; rerun
  with the required sandbox permission succeeded. No toolchain change.
- `scripts/gradle-jdk8.sh --offline test --rerun-tasks`: passed; 154 tests
  freshly executed, zero failures/errors/skips, as counted from JUnit XML.
- `git diff --check`: passed at the investigation checkpoint before implementation.
- No game launches, terrain conversion, process-cut checks, physical input or
  human route completion have been performed for M3-C.

The last bullet describes the initial investigation checkpoint only. Current
implementation/verification evidence follows; do not treat baseline artifact
results as acceptance of a later artifact.

## Implemented user flow and boundaries

Title -> the existing Enter practice arena -> O -> existing inventory mode plus
Parkour -> Start. Count is disabled/preserved; the server enforces goal=1 even for
0/7 requests. A flat/course mismatch opens the existing confirmation surface;
only confirmation queues conversion. On READY, normal START/countdown/GO_ACK
begins the run. START rechecks the current course/jump envelope (1,305 comparisons) and seal
before backup; missing support or obstacles requires explicit reset, with no
silent repair. Gold platforms are sequential checkpoints; emerald starts and
diamond finishes. Inventory first holds success; course first holds at finish.
Both complete restores inventory and ends, with no next run. P stops; a later P
starts a fresh attempt. Reset rebuilds current layout. Exit returns to title.

BasicCourse fixes geometry/version/seed; ArenaLayout projects it over the exact
old flat floor. Thirteen 3x3 platforms at y8 form twelve cardinal one-gap level
jumps. There are eleven intermediate checkpoints. Seed chooses rotation only.
Geometry and clearance checks use the old bounds, with no terrain beyond them.
Templates are conservative basic jumps; no human route completion is claimed.

CourseAttempt owns disposable progression/time. ParkourEnvironment verifies
accepted positions, feet/footprint, body clearance, actual support and ordinary
player locomotion. Landing also needs an accepted airborne sequence; a supported
position or large discontinuity alone cannot advance. Teleport request revokes
evidence; matching confirmation plus fresh supported arrival re-arms without
claiming another checkpoint. The pinned vanilla null-target duplicate-confirm
case is canceled only for an active course. Course finish time freezes even if
the inventory task continues. Returns do not modify terrain or inventory.

SessionManager coordinates the two existing success signals and final restoration.
Only inventory failure sets ERROR_LOCKED; course safe placement, history and HUD
work remain outside that boundary. The existing SETUP/input paths and GO_ACK task
clock are retained. Course time includes returns and pauses, without a penalty.
Stationary/Moving hooks default to no-ops for initialization/start/placement.

Config remains schema 1, accepting PARKOUR and preserving legacy defaults/count.
Trainer protocol 4 carries detached bounded course state; arena protocol 2 carries
layout conversion and the layout flag. No client progress/success action exists.
History schema 1 remains unchanged for original environments. Parkour schema 2
stores a separate CourseResult and requires a one-task goal; a completed session
requires both. Inventory PB labels explicitly say single-task and stay per
environment. No course PB table, aggregate score or leaderboard was introduced.

## Verification methods and retained evidence

The final production matrix uses Minecraft 1.16.1, Loader 0.9.3+build.207, Fabric
API 0.18.0+build.387-1.16.1, Intel Corretto 8.512.09.1/Rosetta, original LWJGL
3.2.1 and fabric.development=false. Production artifact/manifest hashes are in
the machine records. All worlds are fresh explicitly owned run-arena-* instances
or a verified inactive owned seed; no valuable/unmarked save is loaded or changed.

The final `run-arena-matrix.py --parkour` passed all five cases/eight actual
processes, including damaged-course START refusal and non-final partial
inventory/input-count preservation:

- Normal: both inventory modes with incoming goals 0/1/7, both completion orders,
  supported sequential position driving, frozen one-task goal, fall/teleport
  barriers, exact backup/task-clock preservation, interruption SETUP, blocked
  lethal damage, real restore write denial/ERROR_LOCKED, reset, real exit,
  same-path course reopen and explicit conversion back to flat.
- Actual client connection disconnect while airborne: real lifecycle hook saw
  PARKOUR and active protection; restored both files and saved DISCONNECTED with
  unfinished course. This is not merely a direct disconnect-method invocation.
- Three real Runtime.halt(91) cuts: pending first conversion, pending course reset,
  active course/training transaction. Each raw save is archived for forensics,
  never loaded/rebound, then reopened at the original path in a new process.
  Pending layout work replays; inventory recovery precedes arena work/new runs.
- After exit, independent Python typed-NBT decoding checks all 41 inventory
  positions, original selection 6, UUID and absent recovery in both authoritative
  files. A separate standard-library region/palette decoder checks every block
  in all sixteen managed chunks (1,048,576 blocks), including v2 layout/seed/seal.
- Independent archived active-recovery inspection confirms both raw records still
  bind the original exact path and original inventory/selection; no course state
  is used to migrate/rebind them.

[Parkour matrix](verification/m3-c-2026-11-06/parkour-matrix.json) and
[cut recovery bindings](verification/m3-c-2026-11-06/cut-recovery-binding.json)
are artifact-specific. The earlier first runtime passed with its own earlier
artifact; it is retained in run-arena-m3c-20261106-first, not relabelled as final.

The initial updated suite failed two stale fixtures that still classified arena
protocol 2 / PARKOUR options as unsupported. Those fixtures now use genuinely
unknown values; no production gate was weakened. Current unit suite passed 172
tests with zero failures/errors/skips. `test build smokeJar` passed on pinned JDK8;
boundaries passed (105 production files, 14 packages, 41 directed dependencies),
git diff --check/Python compilation/language key parity passed. All 156 production
classes use major version 52 and no smoke/acceptance classes are in the jar.

The same final artifact passed the full original M3-B seven-case/twelve-process
matrix, including actual stop/exit/reuse/reset, real write failures and five
original lifecycle halt91/reopen pairs. The separate legacy marked-world run
passed all thirteen Stationary/Moving checks. Independent post-exit inventory
readback passed and all 625 original full chunks remained unchanged, with no
arena ownership created. These are executed regressions, not inferred results.

## Remaining manual acceptance and limits

Programmatic position driving invokes transformed vanilla handlers but supplies
coordinates and handshake/deadline setup; it does not integrate a human jump
trajectory or prove playability. Real process cuts/disconnect/write failures are
separate evidence. Physical keyboard/mouse operation, rebound Swap Hands/scroll,
real focus/pause interaction, visual readability/localized minimum-window layout,
subjective feel and a human completing the route have **not** been executed.

In the owned production instance, manually complete both inventory modes while
walking/jumping between every checkpoint. Verify start/countdown, approach/landing
space, direction and finish; try skipping, passing underneath and falling at
several checkpoints. Solve inventory first and course first, including pauses/
focus loss and stop in midair. Confirm safe Parkour stop versus in-place Moving
stop; retry with P; reset and exit/reenter. Repeat in en_us/zh_cn, with hints
on/off and rebound keys. Record artifact, original inventory and observed results.

Live course attempts intentionally have no durable journal: a hard crash can lose
that attempt's result. It never creates success or offline time and does not alter
the durable original-inventory recovery. Ownership archives are metadata evidence,
not a terrain backup/rollback. Conflicting archive failure before an
operation intent fails closed with terrain untouched. No arbitrary power-loss,
hostile concurrent filesystem, other-mod/LAN, general entity cleanup or unknown
save acceptance is claimed. No unrelated architecture refactor was undertaken.

The final code passed the 172-test build, including impossible-origin rejection.
Earlier candidates are retained with -before-start-preflight /
-before-origin-validation suffixes and separate hashes; none is relabelled as
acceptance of the final artifact.


## Final artifact and additional regression evidence

Final production jar: `build/libs/hotbar-swap-trainer-0.1.0.jar`, SHA256
`94993cf408b4ceeff0388bcff5b2d10668d1e22eeec6033666727201495f73cb`.
[Combined record](verification/m3-c-2026-11-06.json),
[static checks](verification/m3-c-2026-11-06/static-checks.json),
[M3-B regression matrix](verification/m3-c-2026-11-06/arena-regression-matrix.json),
[legacy movement checks](verification/m3-c-2026-11-06/legacy-moving-checks.json),
[legacy inventory readback](verification/m3-c-2026-11-06/legacy-inventory-readback.json)
and [unchanged legacy terrain](verification/m3-c-2026-11-06/legacy-terrain-after.json)
retain the exact artifact/manifests and results. The full matrices comprise twenty
actual Minecraft processes. Legacy regression and its separate recovery add two
successful processes; incomplete/diagnostic attempts are listed separately.

A first final-artifact legacy run exited0 before producing its test report. It
is **incomplete, not passed**. The host retained an active recovery transaction;
raw world/log/manifest/baseline evidence was archived under the owned instance's
`smoke/failed-incomplete-moving`, never loaded/rebound. Fresh recovery at the
original path passed, then independent readback confirmed both original inventories
and cleared markers. The cause of the early client stop remains unproven.

An excluded passive stop trace initially used a named method string without a
production refmap, so that diagnostic launch failed before world load. Corrected
only that fixture to pinned intermediary `method_1592`/remap=false. The subsequent
same-path traced regression passed all thirteen checks and post-exit independent
inventory/625-chunk checks. Production jar/hash did not change during diagnosis.
[Incomplete attempt and recovery record](verification/m3-c-2026-11-06/legacy-incomplete-and-recovery.json)
keeps these outcomes distinct. The trace stays gated to the excluded Moving case;
it prints a stack but does not throw or alter gameplay.

| Required area | Executed evidence / remaining limit |
| --- | --- |
| Determinism/bounds/geometry | 1,000 seed unit fixtures, explicit one-gap invariants, full saved-volume decoder; human route completion pending |
| v1/v2 reuse/switch/replay | Strict ownership/seal tests, original-byte archives, five M3-C cases and seven M3-B cases |
| Start/ordered checkpoints/finish | Pure attempt tests and actual transformed position handlers in both modes |
| Skip/below/teleport rejection | Pure skip/below/discontinuity tests; actual server teleport-to-finish refusal |
| Falls/confirmation/repeated triggers | Actual partial inventory/input preservation, wrong/duplicate confirms, supported re-arm |
| One-task/both-finish rules | Both modes with incoming 0/1/7 normalized to one; both completion orders; no repeat |
| Fall inventory restart | Not applicable: user confirmed preserve-current-task; existing interruption SETUP still tested |
| Pause/focus/stop/exit/disconnect/restart | Protocol interruption, true disconnect, real halt91/reopen, real I/O locking and safe exit; physical focus/pause UI pending |
| Old config/history/environment PBs | Options/history/PB unit tests and real persisted regression results; visual PB follow-up pending |
| Stationary/Moving/M3-B regression | Full original matrix plus 13 marked-world checks and independent inventory/terrain verification |
