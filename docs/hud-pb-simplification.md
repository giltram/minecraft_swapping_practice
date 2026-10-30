# HUD and PB simplification

## Intended behavior

- Replace the history/filter/detail UI with a two-column, two-row table: Single
  Item and Full Inventory headings, then each mode's fastest successful task.
  Merge profile hashes, hint settings and requested lengths. Keep player and
  supported timing/scoring/environment validation; ignore invalid/stopped attempts.
  Stored history and the existing condition-specific query API remain compatible.
- HUD shows phase, progress and time; remove mode/profile, selections and mistakes.
  Add local showSwapCounts, default false, without sending it over the protocol.
  Keep GO rendering/acknowledgement and actual error messages independent of options.
- Completed summaries display Avg/Best without Session complete or success counts.
- Add optional boolean showSwapCounts to schema 1; existing valid files default
  false without rewriting on load. Preserve strict validation of other fields.

## Verification plan

Run meaningful mode PB, legacy options migration/persistence and HUD visibility tests,
then the full unit suite/build and scripts/check-boundaries.py. Review rendered
PB/HUD/options in the owned production smoke instance when available. Record exact
executed checks; programmatic rendering does not imply physical-input acceptance.

## Final implementation boundaries

PersonalBests owns the new mode-only fastest-task query. The existing strict
forConditions query and immutable history format are retained for compatibility.
TrainerHistoryScreen now renders only the two mode headings/times plus Back;
HistoryBrowser's existing internal paging/filter query remains compatible.
ClientOptionsStore accepts the optional schema-1 flag and retains strict types,
duplicate rejection and atomic save behavior. TrainerClient preserves the flag on
every other accepted preference/profile change. No new dependencies or cycles.
SessionManager keeps completion classification before changing display text; both
RESTORING and the post-restore summary omit the completed-session banner. Statistics
still run only after confirmed inventory restoration, outside its failure boundary.

## Executed verification — 2026-10-30

- `scripts/gradle-jdk8.sh --offline test build smokeJar`: passed; 120 tests,
  zero failures/errors/skips. New tests exercise mode PB merging/exclusions/ties,
  legacy options migration without rewriting, flag persistence and HUD visibility.
- `python3 scripts/check-boundaries.py`: passed, 80 Java files / 12 packages /
  27 directed package dependencies. `git diff --check`: passed.
- `python3 -m py_compile scripts/run-profile-editor-ui.py`: passed.
- Final production JAR, Java-8 class version 52 verified:
  `build/libs/hotbar-swap-trainer-0.1.0.jar`, SHA256
  `e5aefc9434a99290a037d9263d34afb7d5260d479f47a6cea39833d9f91e578f`.
- `python3 scripts/run-profile-editor-ui.py --pb-hud --run-name
  run-m2-ui-pb-hud-final-20261030 --launch`: passed 36 programmatic production-client
  stages, including real Single/Full successful tasks and minimum/normal PB tables,
  compact Avg/Best summaries, default-OFF counter option and RUNNING HUD ON/OFF.
- `python3 scripts/verify-m2-saves.py --run-name run-m2-ui-pb-hud-final-20261030`:
  independently decoded both saved player copies after process exit; all 41 inventory
  positions and selection matched the marked disposable baseline, no recovery data.

[Machine evidence](verification/pb-hud-2026-10-30.json),
[PB table](verification/pb-hud-2026-10-30/pb.png),
[minimum window](verification/pb-hud-2026-10-30/pb-minimum.png),
[options](verification/pb-hud-2026-10-30/options.png),
[HUD without counters](verification/pb-hud-2026-10-30/hud-swaps-off.png),
[completed summary](verification/pb-hud-2026-10-30/completed.png).
These are programmatic checks; human keyboard/focus acceptance remains separate.
No unrelated architecture changes were needed.

## Remove manual refresh follow-up

The PB page now has only Back. Opening it from options creates a fresh browser and
reads the latest saved history; resize still reuses that snapshot. The redundant
Refresh button and its action handler are removed. Earlier artifact screenshots and
36-stage results above predate this button removal and remain historical evidence.

Follow-up verification: `scripts/gradle-jdk8.sh --offline build smokeJar`,
`python3 scripts/check-boundaries.py` and `git diff --check` passed.
No new UI/gameplay acceptance run was performed for this button removal.
Updated JAR SHA256: `7f13b1da01928c5f0eaac83f1a39c5ba0380faa2ef611d13abdc6a78d034682e`.
