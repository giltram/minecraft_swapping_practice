# Milestone 1 execution

Started 2026-10-28. This is the execution record; architecture.md contains the contracts.

1. Pin Gradle 6.5 / Loom 0.4.33 / MC 1.16.1 and obtain named sources.
2. Audit host-player persistence and vanilla action hooks before inventory replacement.
3. Implement guarded inventory recovery. Keep training restricted to an explicitly marked disposable world until crash-recovery acceptance is complete.
4. Implement immutable profiles/tasks, bounded generation, conservation validation and seeded tests.
5. Integrate server session orchestration, versioned packets, stationary protection, client preparation/GO acknowledgments and HUD/controls.
6. Build/remap, run automated checks, launch the real client if the local runtime permits it, and record remaining manual acceptance separately.

Ownership: root owns build, runtime/session/inventory/server hooks and integration; task_domain owns profile/task/stats; client_network owns client/protocol; hook_audit owns the version-hook audit. No concurrent edits to shared files.

Safety gate: a marker named `.hotbar-swap-trainer-test` inside the world directory is required. Creating a marker is an explicit choice to use a disposable test world; normal saves stay closed until the documented fault-injection matrix passes.

## Integration checkpoint

- Fixed toolchain is building with JDK8. genSources, build, remapJar and remapSourcesJar executed successfully; Gradle verification metadata and resolved artifact hashes are checked in.
- 33 JUnit tests passed, including 20,000 seeded tasks and actual vanilla item packet/save materialization regressions; architecture boundary checker passes 52 production Java files with no cycles.
- Intel JDK8/Rosetta with original LWJGL3.2.1 entered the owned superflat smoke save. Single3 and Full3 sessions restored all 41 slots and original selection, including both authoritative disk readbacks.
- Runtime testing caught two issues absent from pure task tests: vanilla adds Damage:0 to tools, and canceling movement bypasses the ordinary onGround update. Templates now normalize through MinecraftItemCatalog before freezing; stationary retains a validated supported anchor and onGround flag.
- The independent production jar completed the normal smoke sequence, intentional RUNNING halt/reload recovery, four direct inventory-module crash boundaries and genuine I/O failure/retry checks. Manual rebound input/GUI and the remaining fault matrix are pending; see testing.md for precise scope and earlier failed attempts.

## Resume checkpoint — 2026-10-29

- Offline build, smokeJar and resolvedArtifacts succeeded. The final `--offline build smokeJar` also passed; JUnit reports remain 33/0/0/0 (tests/failures/errors/skips), reused because production/test sources did not change. Boundary and production jar checks passed. Production SHA256 is recorded in testing.md.
- An earlier partial production run completed two Single tasks, then entered chat/PREPARING and exited normally. Independent post-exit NBT decoding confirmed all 41 slots, UUID and selection6 match baseline in both playerdata and host level.dat, with no active recovery record.
- The user resumed and explicitly requested automatic testing without question popups because those steal focus. The production normal-crash suite passed Single3/Full3/invalid-replacement1/unlimited-stop2, exactly one no-op and same-client-batch swaps. Actual halt91 then recover0 passed exact restored inventory and cleared marker in both files.
- Separate smoke-only `faults` checks passed repeated replay and four POSIX write failures/retries. `scripts/run-inventory-cuts.py` passed all four halt91/recover0 pairs and preserves pre-halt memory/disk evidence. These direct inventory checks do not claim actual mid-write or SessionManager.stop fault injection. Checkpoint JSON contains the precise results/hashes.
- The smoke driver now automatically continues only the experimental warning for its verified owned-world request; the request flag is cleared before confirmation or on join. Recover/faults/cut modes disable automatic pause in the isolated process at first client tick; normal modes retain real focus/rendered-GO requirements. An initial auxiliary-driver options access before initialization failed before opening the world and was corrected to first tick; the subsequent fault suite passed.
- Final build and production jar checks passed with the same production SHA256. Remaining acceptance: physical/rebound/scroll input and GUI, remaining protection/disconnect/shutdown phases, actual mid-write/stop injection and game-load corruption cases beyond existing units. Keep ordinary saves gated.
- A final `faults` run using the completed driver passed all five checks and exited0, exercising guarded automatic world confirmation. All test clients are closed; original inventory and permissions are restored. Evidence is in testing.md and verification/milestone1-2026-10-29.json.
- Do not treat old `result-*.json` files as fresh evidence: inspect the latest events boot segment and result modification times.
- Preserve failed test data. Keep ordinary-world training gated until the remaining acceptance matrix passes. No user account or valuable save is required for these tests.

## Remaining automatic acceptance completed — 2026-10-29

- Extended only the separate smoke mod and verification scripts/docs; production
  source/hash remains unchanged. Fault Mixins apply only to production `accept-*`
  modes; normal/development/recovery modes do not install them.
- 25 scenarios passed with independent fresh reopens: 27 protection checks, eight
  phase/screen/dimension checks, actual LAN stop/restart rejection, eight exit
  pairs, eight corrupt-record load/repair cases and six actual write/stop cuts.
- Actual compressed writes halted after 32 bytes in UUID/host saves during
  training and actual stop. Actual stop before/after clear also passed. Corrupt
  records retained exact raw data through real load, failed stop and both saves.
- Fresh `--offline test --rerun-tasks` passed all 33 JUnit tests. Final
  `--offline build smokeJar resolvedArtifacts`, boundary checker and Python compile
  checks passed. Production hash stays
  `64c643dff049d6fd1e59dfedff61f2aa41384fb9064255c6893ac962150adfc7`;
  classes remain major52, with refmap and no smoke classes in production.
- After all clients exited, the independent standard-library NBT decoder verified
  all41 positions, selection6, UUID and absent recovery key in both saved copies.
  World/playerdata permissions are 0755. Failed driver attempts/raw data remain.
- [Consolidated evidence](verification/milestone1-remaining-2026-10-29.json) records
  exact reports, assertions, launch/save hashes and earlier partial-run provenance.
- Remaining acceptance is [physical input and UI/visual experience](manual-acceptance.md).
  Lifecycle setup supplied acknowledgments and accelerated countdown; it is not
  physical/rendered-input evidence. Ordinary saves stay gated. M2/M3 were not started.
