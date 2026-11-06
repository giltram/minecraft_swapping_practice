# Targeted review fixes — 2026-11-01

Source: [code-review.md](code-review.md). Existing M3-A work is retained.

## Confirmed issues and changes

1. **Confirmed and fixed.** Pending vanilla mining bypasses packet guards. Add a required interaction-manager
   Mixin guarding the actual break boundary and discarding active/delayed mining
   at START through `inventory.PendingBlockBreaking` and on protected updates
   (never pause it for replay). Verify both environments in
   an owned production instance, including ordinary mining after restoration.
2. **Confirmed and fixed.** Profile names accepted by profile/editing can exceed history's 256-character
   contract. Make `LoadoutProfile.MAX_NAME_LENGTH` the shared domain limit for
   validation, UI, metadata and history decoding. Preserve invalid source bytes;
   verify 256/257 boundaries and a profile-to-history round trip.
3. **Confirmed and fixed.** The old default/history UI driver targets removed screens. Consolidate onto
   the current usability driver, migrate real restore/locked/LAN/save gates and
   PB/history restart checks, remove the obsolete driver and document aliases.
   Execute fresh isolated default/history/PB runs and inspect their result files.

## Intentionally retained

- History's documented 4096-attempt bound: changing it requires a separate storage
  and long-session product decision; bounded resource use is useful, while very
  long sessions can still lose history. This task does not change scoring/storage.
- Fail-closed editing when an unreadable sibling prevents ID uniqueness checks:
  swallowing the error could overwrite an ambiguous profile. The cost is that
  one unreadable file prevents editing unrelated profiles until repaired.

## Verification

- `scripts/gradle-jdk8.sh --offline test build smokeJar --rerun-tasks`: all 11
  tasks executed, 23 test classes / 131 tests, no failures/errors/skips. The initial
  sandbox invocation could not open Gradle's local socket; the authorized offline
  execution outside that sandbox succeeded.
- `python3 scripts/check-boundaries.py`: 86 production Java files, 12 packages,
  31 directed edges; no reported cycles or checked boundary violations.
- All 8 Python scripts parse; en_us/zh_cn keys agree; production JAR has 124
  Java-8 classes and no smoke driver classes. `git diff --check` passed.
- Final production `accept-pending-mining`: 8 real-client/server checks across
  both modes/environments and active/delayed queues. START immediately discards
  both pending flags; direct pre-tick breaks and 400 protected updates leave the
  block and complete inventory/NBT intact. After restore, old work stays cleared
  and a new vanilla delayed break succeeds. Independent typed NBT decoding after
  process exit confirms both complete 41-slot saves/selection and cleared recovery.
- Final production UI: default, `--history`, `--usability` and `--pb-hud` all
  passed in separate fresh owned instances. Both history and PB/HUD then passed
  a fresh-process `--reopen`: profile/options, training and gate-stop history, PBs,
  two preserved corrupt-history warnings and Chinese resource/screens verified.
  All four fresh runs execute real PREPARING/RESTORING/ERROR_LOCKED/retry/LAN save
  gates. Expected corruption/write-denial exceptions are injected evidence, not
  unhandled failures. Fresh result files were checked, not just process exits.
- After each of those six UI processes, independent typed NBT decoding matched
  both complete 41-slot inventories and selection against the owned seed and
  confirmed cleared recovery. PB/incomplete-warning/Chinese screenshots were
  inspected. See [machine evidence](verification/code-review-fixes-2026-11-01.json)
  and [build log](verification/code-review-fixes-2026-11-01/build.log).

Production SHA256: `827c339f99a25b55caaa5f5b7c45f3abbe4b8bfc04463e90e2eff17a066e7e49`.

These checks do not establish physical keyboard/focus/feel acceptance or a fresh
full crash/persistence fault matrix. All game runs use fresh marked disposable
instances copied from the verified inactive owned seed; daily saves are untouched.

## Architecture assessment

The mining adapter follows the existing inventory-owned vanilla bridge pattern
(`RecoveryHolder`); session code does not depend on Mixin internals. Stats now
explicitly depends on profile's immutable text bounds, with no reverse edge.
Current UI navigation and safety scenarios have separate smoke-only owners and
one entrypoint. No unrelated architectural refactor was needed.
