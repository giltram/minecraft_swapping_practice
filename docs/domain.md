# Profile, task, and result contracts

These packages implement immutable training domain state and M2-B's separate editor drafts. `task` uses `profile` and Minecraft's NBT value types; it does not access players, registries, files, networking, clients, or mutable live inventory. `stats` depends only on task mode and result values. File access is confined to `ProfileRepository`; its injected `ItemCatalog` owns registry/materialization access. The private memory-only `ProfileJson` codec shares schema, catalog and NBT checks across loads and editor validation.

## Positions and whole stacks

`InventoryLayout` has exactly ten positions: hotbar `0..8`, offhand `9`. These numbers are not player screen-handler indices. The initial selected hotbar position is always `0`, independently of the item's initial position.

`StackValue` contains a registry identifier, exact positive count, and complete `CompoundTag`. Empty values use `StackValue.EMPTY`. Construction and NBT getters copy tags; immutable layouts and profiles may safely share `StackValue` references. Equality compares structural, typed NBT, so compound key order is irrelevant and different numeric types, counts, potion tags, or names remain different stacks. No hidden identity tags are added.

`InventoryLayout.withSwap(hotbarSlot)` produces an immutable proof state for the offhand exchange. It does not mutate Minecraft inventory. The runtime inventory adapter owns setup/restoration; player exchanges use vanilla packets.

## Profiles

`ProfileRepository(Path, ItemCatalog)` is explicitly reloaded by the application. `ItemCatalog.maximumStackSize(id)` returns zero for unknown items/air. `installDefaultIfMissing()` copies the bundled profile once and never overwrites a file. `loadAll()` returns valid frozen profiles and separate path-specific errors. Duplicate profile IDs are errors. A malformed file remains available for repair.

JSON schema version 1 has `id`, `name`, and a `slots` array containing exactly one rule for each `hotbar1..hotbar9` and `offhand`. Profile IDs have at most 128 lowercase identifier characters, matching the protocol limit. Explicit slot names let the parser reject duplicate or missing positions. Unknown fields are errors. A stack alternative has:

```json
{
  "item": "minecraft:potion",
  "count": 1,
  "nbt": "{Potion:\"minecraft:fire_resistance\",Extra:1}",
  "match": "FIELDS",
  "matchNbt": "{Potion:\"minecraft:fire_resistance\"}"
}
```

`nbt` and `matchNbt` are compound SNBT strings. `nbt` defaults to `{}`; `match` defaults to `EXACT`; `matchNbt` defaults to the generation NBT. `EXACT` compares the complete matching compound; `FIELDS` recursively requires named compound fields while retaining exact list/array/scalar values and types. Every generation template must satisfy its matching predicate. Registry identity and count always match exactly. Template syntax is validated here. `ItemCatalog.normalizeTemplate` freezes defaults added by actual runtime materialization before task generation. `inventory.MinecraftItemCatalog` provides that adapter: vanilla damageable items add typed `Damage:0` even when JSON omits NBT. Implicit EXACT predicates use this normalized NBT; explicitly configured `matchNbt` stays strict, and FIELDS keeps the configured subset. No NBT fields are stripped or ignored for conservation. Setup verifies the materialized ten-slot layout equals the frozen task before sending PREPARING; mismatch restores rather than waiting for a READY timeout. Regression tests exercise real ItemStack packet and save round-trips.

Before accepting a template, `NbtTemplateValidator` verifies a vanilla binary NBT round-trip entirely in memory. Each tag is limited to 32 KiB encoded, 64 nesting levels, and 64 KiB SNBT source characters. This rejects oversized modified-UTF strings and reserves space for both ten-position layouts in the legacy custom payload. Profiles are limited to 1 MiB. Failures include the profile/slot/NBT path; no source is truncated or rewritten. The boundary check permits byte-array/data streams for this pure value validation; file access remains confined to `ProfileRepository`.

Rules are `REQUIRED`, `OPTIONAL`, `EMPTY`, or `FLEXIBLE`. Required/optional rules need alternatives. Empty rules reject alternatives. Flexible alternatives are optional filler generation candidates. A profile needs at least one fixed trainable destination. The default has eight required positions, optional hotbar 8, and flexible hotbar 6, permitting 8, 9, or 10 stacks.

`LoadoutProfile` and `StackSpec` are immutable snapshots. `getHash()` hashes normalized profile content and canonical structured NBT. A running session keeps its snapshot even if files change.

`ProfileRepository.saveSlotOrder(expected, sourcePositions)` persists a permutation:
`sourcePositions[destination]` identifies the old rule to place there. All ten
sources must occur exactly once. The repository resolves the expected profile ID
from valid files, rejects duplicate IDs and a changed profile hash, and checks the
source text again before replacement. It preserves raw rule fields (including
implicit normalization/matching defaults), reassigning only named destinations.
An exact `<filename>.<uuid>.bak` is written before an atomic replacement; no
non-atomic fallback is used. Errors leave the old profile intact. Existing frozen
snapshots stay unchanged; the next START reloads the edited layout. See
[layout customization](layout-customization.md) for the client flow.

## Full profile editing (M2-B backend)

`listProfileFiles()` returns sorted candidate paths (including broken files), so a
local selector can retain the selected path alongside `load(path)`'s snapshot.
`ProfileRepository.beginEdit(Path selectedFile, LoadoutProfile expected)` opens an
existing direct-child JSON file, checks its ID/semantic hash against the selected
snapshot and rejects duplicate declared IDs (including schema-invalid files).
It returns a repository-owned `Edit` binding the path and exact source bytes.
The path/ID cannot be changed through the edit API. The handle is single-use after
successful save; discard/reopen for another edit. Files are regular UTF-8 sources;
symlinks, oversized/unreadable sources and invalid UTF-8 fail closed.

`Edit.getDraft()` exposes a `ProfileDraft`: mutable name, ten fixed-position
`Slot` objects with mutable kind/candidate lists, and candidate setters for item,
count, generation SNBT, EXACT/FIELDS and matching SNBT. Null optional candidate
fields mean omission/schema defaults. Opening uses raw source fields, never the
normalized snapshot: changing generation NBT must keep an implicit EXACT matcher
implicit. FIELDS with omitted matching NBT uses raw generation NBT; explicit
matching NBT remains strict. Complete typed compounds/lists/arrays are retained.

`validateDraft(draft)` returns `ProfileDraftValidation.isValid()/getError()` and,
on success, `getProfile()` with a fresh immutable normalized value. Errors include
paths where available; invalid results have no profile. Validation reuses the
schema/catalog/NBT checks and the serialized UTF-8 size bound. It has no file I/O
and never generates tasks. A legal profile with just one fixed destination can be
saved even when it cannot supply 8..10 stacks for Full.

`save(Edit)` validates again, serializes/reparses, compares normalized hashes and
reloads the forced same-directory temporary file before writing. It checks exact
source bytes, current structure/semantics and ID uniqueness again before backup
and replacement. A forced `<filename>.<uuid>.bak` preserves exact original bytes;
replacement uses ATOMIC_MOVE with no fallback. Failure preserves the source and
cleans this save's temporary file; a completed backup is retained on replacement
failure. It never redirects a corrupt selection to another same-ID file. Existing
`saveSlotOrder` retains its public permutation contract and raw rule fields while
sharing this persistence path. No schema/protocol/default-profile changes.

This is optimistic conflict detection, not cross-process locking or a power-loss
transaction. See [M2-B details, GUI integration and actual verification](m2-b-profile-editing.md).

## Generation and validation

`ProfileApplicability.assess(validProfile)` reports immutable Single destination
indices (`isSingleTrainable`) and exact feasible Full counts (`hasFullStackCount`,
`getFullStackCounts`). The former guarantees a Single destination can be scrambled;
the latter is a count condition, not proof of a nontrivial Full task. There is no
seed sampling in assessment. `TaskGenerationException.getReason()` distinguishes
`MODE_PRECONDITION` from `BOUNDED_SEARCH_EXHAUSTED`. Neither is a structural profile
validation error; bounded failure describes only that generation call.

`SingleItemTaskGenerator.generate(profile, seed)` selects a fixed destination and one matching stack. The initial item occupies a different hotbar position. One exchange reaches offhand; two exchanges reach another hotbar destination. All other positions must be empty.

`FullInventoryTaskGenerator.generate(profile, seed)` selects a feasible stack count in 8..10; the overload accepts a specific count and rejects impossible requests. The generator freezes optional presence, selects alternatives and flexible fillers, creates a valid target, then scrambles it using legal hotbar/offhand exchanges. `InventoryTask.getSolution()` is the reversed exchange list. It is only a solvability witness.

Full generation uses at most 32 sampled targets and 16 random scrambles per target. After unsuccessful scrambles it scans all position/token pairs to find a definite mismatch and creates it with at most three exchanges. Only if every pair matches is that specific sampled multiset trivial. A bounded sampling failure reports that fact and does not claim the entire profile is mathematically impossible.

Each present position independently samples one entry from its alternatives;
there is no cross-slot deduplication. Two REQUIRED positions with the same
candidate pool (for example building/spare blocks) accept interchanged generated
stacks under the global conservation check, and may both sample the same value.
`TaskGeneratorTest.sharedBlockPoolsCanBeIndependentlySampledAndInterchanged`
exercises seven block alternatives and both repeated and exchanged values.

`TaskValidator.validate(task, actual)` is the sole success predicate:

1. Compare complete stack multisets, including empty positions. Any item/count/full-NBT change returns `INVALID`.
2. Check actual positions against the frozen rules. Required and present optional rules accept any configured alternative. Absent optional/empty rules require empty. Flexible accepts a whole leftover stack or empty under the global conservation check.
3. Return `COMPLETE` or `INCOMPLETE`; the concrete target is one witness, never the only permitted arrangement.

The inventory/session adapter separately checks armor, main positions 9..35, cursor, and forbidden actions before accepting success. This keeps mutable game state out of domain validation.

## Current-session results

`TaskResult` records `SUCCESS`, `INVALID`, or `STOPPED`, elapsed monotonic nanoseconds, swaps/effective swaps/no-op swaps, slot changes, forbidden actions, and a reason. Counters require `swapAttempts = effectiveSwaps + noOpSwaps`. Mistakes are explicitly `noOpSwaps + forbiddenActions`; extra valid exchanges do not count as mistakes. Timing quality is `server-go-ack-to-action`, not a claim of frame-perfect visual reaction time.

`SessionResult` preserves all attempts but computes total/average/best time from successes only. Zero requested successes means unlimited; zero average/best means there is no successful attempt. `ResultStore.save(playerId, result)` is outside recovery and must be idempotent for the same player/session. `findLatest(playerId)` returns an optional result; `forget(playerId)` and `clear()` follow disconnect/world shutdown. M1's server-thread-confined `InMemoryResultStore` retains only each player's latest result; durable history is M2 work.

## Verification locations

- `profile/ProfileRepositoryTest`: schema/path errors, item limits, broken file preservation, one-time defaults, typed structural NBT, deep copies, normalized hashes, potion predicates.
- `profile/ProfileLayoutPersistenceTest`: saved slot permutations (independent of JSON entry order), exact backups, fresh reload and Full task generation, preserved alternatives/NBT/optional/flexible rules, invalid permutations, stale/corrupt sources and duplicate IDs.
- `profile/ProfileEditingTest`: complete ten-slot edits/reloads, all kinds and match modes, typed NBT/defaults/runtime normalization, invalid drafts, snapshot isolation, Single-only saves, Full count versus bounded exhaustion, and reloaded task witnesses.
- `profile/ProfileEditSafetyTest`: file selection, exact UTF-8/CRLF backups, byte-level stale changes, corrupt/duplicate sources, temporary-file cleanup, unsupported/failed atomic moves and slot-order failure regressions. Uses temporary directories and a package-private repository replacement seam.
- `inventory/MinecraftItemCatalogTest`: also verifies edited/reloaded profiles with actual vanilla defaults, registry count limits and materialized task witnesses.
- `task/TaskGeneratorTest`: 10,000 Full tasks plus 10,000 Single tasks, all 8/9/10 counts, blank/arbitrary offhand, duplicate stacks, distinct NBT, overlapping alternatives, nontrivial initial states, inverse witnesses, deterministic seeds, bounded errors, conservation, and optional/flexible rules.
- `stats/SessionResultTest`: successful-only aggregates and idempotent latest-result replacement.
- `inventory/RecoveryRecordTest`: all 41 positions, selected slot, deep copies, checksums, world/player binding, corrupt/unsupported/inactive records, malformed snapshot rejection.

Actual executed checks and remaining runtime acceptance gates are recorded in `docs/testing.md`; test source presence alone does not indicate a passed check.
