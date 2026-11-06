# M3-A: moving inventory training

Status (2026-10-31): implemented. Unit/build and programmatic production checks
passed; physical-input/feel and the manual items below remain unverified.

The world-management exclusions below describe the scope of M3-A.
[M3-B](m3-b-practice-arena.md) now separately owns practice-save creation/lifecycle;
the confirmed free-movement and inventory-recovery rules here remain unchanged.

## Confirmed scope and product rules

The user explicitly simplified the original movement-enforcement request:
**Moving allows free vanilla movement; stopping never invalidates a task.**
There is no grace setting, minimum speed, sampling-window requirement, sprint
requirement or first-movement success gate. A stationary start/finish is valid.
Earlier proposals for 2 seconds / 5 ticks / 0.05 blocks were withdrawn, not
implemented rules. No movement HUD or new statistics are needed.

Both Single Item / Full Inventory use the same Stationary / Moving option.
START freezes environment and profile. PREPARING, countdown, GO, RUNNING,
feedback and task replacement all allow vanilla movement in Moving. The existing
rendered GO_ACK handshake and inventory-based success remain unchanged.

START retains its safe dry-ground gate. Subsequent task replacement may occur
in the air. STOP, finite completion, options and disconnect restore inventory
immediately in place, releasing protection only after confirmed saving. They do
not teleport, change game mode, zero velocity/fall distance or wait for landing.
Vanilla landing damage can follow release. Recovery failures retain existing
ERROR_LOCKED protection and raw records.

Keep Minecraft 1.16.1 / pinned Fabric / Java 8. Do not generate/clean terrain,
create a practice-world feature, introduce courses/checkpoints/solvers, remove
the disposable-world gate, or expand inventory recovery to locomotion/state.
Testing reuses the owned production launcher and its existing marked idle seed;
no terrain construction or exploration is part of the Moving acceptance driver.

## Investigation and implementation plan

Read AGENTS.md, architecture.md, domain.md, client.md, version-hooks.md, M2
history/PB and production/smoke documentation, then session/environment,
movement/protection Mixins, inventory, options/protocol/HUD/history contracts
and relevant tests. The initial working tree was clean.

1. Freeze the independent environment in TrainingSession; introduce the small
   SessionEnvironment boundary and MovingEnvironment, retaining Stationary's
   existing anchor/camera/velocity implementation.
2. Observe accepted movement through narrow vanilla adapters. Keep diagnostic
   state in environment, all session transitions/results in SessionManager,
   and task generation/validation and inventory recovery independent.
3. Persist optional environment in schema-1 options, default Stationary without
   rewriting old files. Preserve it across every accepted edit/profile fallback.
   Protocol 3 carries environment, with old Java request factories retaining
   Stationary defaults; old wire versions are rejected explicitly.
4. Persist actual versioned environment in unchanged history metadata/schema.
   Support both environments without merging PBs. Keep existing mode columns;
   the options environment determines the clearly labelled PB table. No second
   selector or new PB product model. Old query defaults remain Stationary.
5. Add focused tests and excluded production acceptance in the existing owned
   instance workflow. Run tests/build/boundaries and independent save decoding;
   record exact evidence and leave physical input/feel checks explicitly pending.

The partial stop-grace fields added before the final product decision were
removed from code, protocol, options, history and tests. They were never a
released configuration/history contract.

## Environment and observation contracts

TrainingEnvironment owns independent IDs: stationary-v1 / moving-v1.
SessionEnvironment.create selects exactly one of two implementations, without
registration/plugins. TrainingSession's final environmentType and instance are
created once. SessionManager publishes/saves that frozen type. A later START
cannot replace an active session's environment. TaskGenerator / TaskValidator
have no environment dependency.

MovingEnvironment.tick only checks the original ServerWorld invariant; it never
writes position, velocity, grounded state or fall distance. Movement packets run
through vanilla in every phase, including feedback and ERROR_LOCKED. Inventory
protection remains independently active until successful restoration. With no
session but an active recovery marker, the original fail-closed movement guard
remains. Stationary uses its original anchor interception/tick unchanged.

The exact pinned generated vanilla sources show onPlayerMove crossing
NetworkThreadUtils.forceMainThread before processing. Only the accepted
translation branch invokes ServerPlayerEntity.increaseTravelMotionStats(DDD)V
after collision checks and authoritative position update. The Mixin captures
server X/Z after the thread fence and observes processed X/Z at that invocation;
raw coordinates, keys, yaw/pitch and the statistic invocation arguments are
never used as displacement evidence. Rejection/pending teleport paths skip it.

HorizontalMovementObservation stores only the last accepted horizontal segment
in **blocks**, hypot(afterX-beforeX, afterZ-beforeZ). It stores no position
baseline across packets, so an external reset between packets cannot become a
teleport-spanning delta. teleportRequest resets the diagnostic observation;
confirmation does not contribute distance. Rotation, stationary movement and
vertical-only jumping have zero horizontal distance. Nonfinite segments reset
the value to zero. There is no threshold/window/clock: tiny accepted displacement
is diagnostic only, and no low TPS, missing packet or scheduling delay is scored
as a violation. No observation enters swaps, selection counts, history or PB.

## Verification record

Baseline before implementation: pinned offline test/build/smokeJar passed;
fresh --rerun-tasks executed 121 tests, zero failures/errors/skips. Initial
sandbox Gradle daemon socket rejection was resolved with required sandbox
permission. Boundary baseline: 80 Java files / 12 packages / 27 dependencies.

Prerequisite checkpoint before simplification: 126 tests passed with the then
proposed grace fields. That intermediate artifact is superseded and is not
M3-A acceptance evidence.

Final simplified implementation verification:

- `scripts/gradle-jdk8.sh --offline test build smokeJar`: passed. 129 tests
  executed, zero failures/errors/skips; the final comment-only rebuild retained
  the passing test outputs. Smoke-only recompilation after fixture correction
  passed. No toolchain versions were changed.
- `python3 scripts/check-boundaries.py`: passed, 84 production Java files,
  12 packages, 30 directed dependencies, no cycles/common-to-client violations.
- `git diff --check` and Python compilation of both changed launchers: passed.
- Production JAR: `build/libs/hotbar-swap-trainer-0.1.0.jar`; SHA256
  `9cccfbbf73e16306658a266f37218e3e528fcb13b57e807d9c4a36f4a8cfe162`.
  Every class has major version 52 and no smoke/UI-acceptance classes are present.
- Independent production runtime: Minecraft 1.16.1, Loader 0.9.3+build.207,
  pinned Fabric API, Intel Corretto 8.512.09.1 / Rosetta, original LWJGL 3.2.1,
  fabric.development=false. Used only the marked
  `run-m2-runtime-m3-a-20261031/saves/hst-smoke` instance/owned terrain.
- `python3 scripts/run-m2-runtime.py --mode accept-moving --run-name
  run-m2-runtime-m3-a-20261031 --launch`: final artifact passed 13 programmatic
  checks. Four environment/mode finite combinations, frozen environment/goal,
  Stationary regression, actual accepted horizontal/sprint/jump movement,
  camera/vertical zero, collision rejection against existing solid support,
  teleport reset, unchanged velocity/fall distance and input counters, unlimited
  replacement/invalid retry, airborne pickup/use/drop/transfer/block-use/lethal
  damage protection and stop restoration, and five interruption-protocol phases.
- `python3 scripts/verify-m2-saves.py --run-name run-m2-runtime-m3-a-20261031`:
  independently decoded both saved-player copies after exit; all 41 positions,
  selected slot 6 and UUID matched the baseline; recovery data absent.
- Initial `accept-moving-disconnect` fixture failed to establish airborne motion:
  it had not confirmed the pending login teleport before asking vanilla to move.
  The failed test intentionally left its client open. Only that identified owned
  JVM was terminated (exit 143); failed logs, manifest, baseline and raw saves were
  archived in `smoke/failed-moving-disconnect-login-setup`. A fresh `recover`
  launch passed, followed by independent exact two-file readback. Only the
  excluded fixture was corrected to confirm the login teleport; production
  movement/recovery code was not patched to accommodate the fixture.
- `python3 scripts/run-m2-runtime.py --mode accept-moving-disconnect --run-name
  run-m2-runtime-m3-a-20261031 --launch`: corrected fixture passed. A real client
  connection disconnect reached the actual disconnect hook in RUNNING, Moving,
  onGround=false, while protected. Independent post-exit readback again passed
  for all 41 slots/selection/cleared recovery. Persisted history has actual
  moving-v1 / FULL_INVENTORY / DISCONNECTED metadata.

[Machine evidence](verification/m3-a-2026-10-31.json) retains exact artifact,
runtime, checks, failure/recovery, disconnect lifecycle and independent readbacks.
The wrapper archives old launch files before reuse; the JSON retains each checked
manifest hash and saved-player hashes. Raw runtime artifacts stay in the owned
instance, not in the production JAR.

The driver directly invokes transformed handlers and accelerates deadlines.
Repeated stationary callbacks are not a real-duration/physical-input test. Real
wall pushing requires an existing wall; no wall was built. Screen/pause/focus
coverage above is the interruption protocol, not real GUI/physical interaction.
The recovery-only reopen above is one executed recovery case, not a rerun of the
full persistence/crash fault matrix.

## Manual acceptance still required

Use only the explicitly owned production instance and marked disposable save;
record exact artifact/runtime and original inventory/selection.

1. In both modes, select Moving, then walk/sprint/turn/jump while solving with
   real rebound Swap Hands, number keys and scroll. Stop as long as desired;
   the task must remain valid. Solve while completely stationary as well.
2. Verify Stationary still anchors while allowing camera rotation. Return to
   Moving; no velocity reset/teleport should occur. Check finite and unlimited
   repeated tasks, including airborne replacement and completion.
3. Open inventory/pause, lose focus, open O, stop and disconnect while moving
   and airborne. Confirm existing interruption behavior, exact inventory restore,
   no forced landing/position correction, and ordinary movement/damage afterward.
4. Against an existing wall, confirm accepted horizontal observation is zero
   when pushing without actual horizontal movement. Check rotation/vertical-only
   jumps and teleport/correction exclusion without changing any terrain.
5. Reopen options/history after restart: default Stationary for old config,
   preserved environment, frozen active settings, readable old history and
   environment-separated two-mode PBs. Independently decode both saved-player
   copies after exit; all 41 slots/selection and cleared recovery must match.

Physical input, focus/pause interaction and subjective movement feel are never
inferred from compilation or programmatic packet/protocol tests. Real network
disconnect/shutdown and the existing full recovery fault matrix must be labelled
separately from direct lifecycle calls.
