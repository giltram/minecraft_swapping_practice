# M2-B: profile editing backend

Current GUI update: [multi-select layout and training-count input](m2-usability-plan.md)
reuse these backend contracts. [Current verification](m2-multiselect-acceptance.md)
records its own artifact and executed checks; earlier artifact results below are
historical, not automatic acceptance of the new GUI.

Scope: edit an existing profile's name and ten complete rules; keep its ID. No
new GUI, creation/copy/deletion, history/PBs, M3, protocol or recovery changes.
M2-A client options and the existing slot-order editor remain in place.

## Execution plan and contracts

1. Add a profile-specific mutable draft retaining raw schema defaults, separated
   from immutable loaded profiles. A repository edit handle binds a caller-selected
   JSON file, expected snapshot and exact source bytes; it cannot change ID/path.
2. Validate drafts through the existing schema, ItemCatalog and NBT parser. Return
   structural errors separately from task-mode applicability. Serialize/reparse
   before saving and compare normalized semantics; never use sampled seeds as a
   feasibility proof or require Full applicability to save a legal Single profile.
3. Share backed-up atomic replacement with saveSlotOrder. Reject stale, corrupt
   and ambiguous sources, preserve exact original bytes, clean our temporary file,
   and fail explicitly when atomic replacement is unsupported.
4. Cover complete edits/reloads, defaults/typed NBT, invalid drafts, applicability,
   snapshot isolation, source/backup safety and slot-order regressions. Verify
   reloaded tasks' incomplete initial states and TaskValidator solution witnesses.
5. Execute the requested offline test/build and boundary checks, then record
   results and future GUI integration here and update architecture/domain docs.

Public API design: `ProfileRepository.beginEdit(Path, LoadoutProfile)` returns an
`Edit` exposing a `ProfileDraft`; `validateDraft` returns structural validation
with either a frozen normalized profile or an error; `save(Edit)` revalidates and
returns the saved immutable snapshot. Draft slots/candidates expose ordinary
setters and a mutable candidate list; omitted NBT/matching fields stay null.
`task.ProfileApplicability` reports Single destinations and exact Full stack-count
conditions without file/catalog access. Generator failures remain distinct from
that assessment and from structural validation.

## Public interfaces and validation semantics

| API | Contract |
| --- | --- |
| `ProfileRepository.listProfileFiles()` / `load(path)` | Sorted candidate JSON paths and frozen loaded values. Discovery includes broken files; report load errors and retain the selected path. No backup/temp discovery. |
| `beginEdit(selectedPath, expectedSnapshot)` | Requires an existing direct-child regular UTF-8 JSON file, expected ID/hash, and no other declared same ID. Returns a repository-bound handle with exact source bytes; never changes the selected path/ID. |
| `Edit.getDraft()` | Mutable `ProfileDraft`: name, ten indexed `Slot` values, mutable candidate lists. Slot kind and every candidate field are editable; no API to add/remove positions or change ID. |
| `validateDraft(draft)` | `ProfileDraftValidation.isValid()` and first path-bearing `getError()` where available; `getProfile()` gives a fresh immutable normalized snapshot only when valid. No file writes/reads or task generation; uses the injected catalog. Includes serialized UTF-8 size limits. |
| `save(edit)` | Revalidate at save time, compare serialized/reparsed semantic hashes, reload/compare temporary JSON, back up/atomically replace only the bound file. Returns immutable saved value. Reopen after success; a failed edit can be corrected/retried if its source is still current. |
| `ProfileApplicability.assess(validSnapshot)` (task) | Exact Single destination list/`isSingleTrainable()` and Full counts/`hasFullStackCount()`. No registry, file access or sampling. |
| `TaskGenerationException.getReason()` (task) | `MODE_PRECONDITION` for unavailable/requested counts or destinations; `BOUNDED_SEARCH_EXHAUSTED` for this Full generation call's bounded failure. Existing exception type/message callers remain supported. |

Structural validity keeps schema 1 unchanged: REQUIRED/OPTIONAL must have
candidates, EMPTY must have none, FLEXIBLE may have zero or more, and at least one
REQUIRED/OPTIONAL destination is required. A profile with one destination is legal
and Single-trainable even if Full has no count in 8..10. Full count conditions
alone do not establish the existence of a nontrivial scramble. No generation call
is made to approve/reject a save, and bounded exhaustion is never reported as a
proof that the whole profile is impossible. `TaskValidator` remains the sole
completion/conservation predicate.

Candidate `nbt`, `match`, `matchNbt` are nullable draft fields: null means omitted,
not empty text. NBT is compound SNBT. Opening a draft retains raw schema fields,
rather than copying runtime-normalized matching NBT into explicit predicates.
An omitted EXACT matcher follows the normalized generation template (including
vanilla typed Damage:0). Explicit EXACT matching stays strict. FIELDS defaults to
raw generation NBT and otherwise keeps the configured subset. No fields/types
are stripped. A later raw NBT edit cannot accidentally inherit a stale implicit
EXACT predicate. Drafts/validation snapshots/saved snapshots are independent.

## Saving and failure behavior

The pure, package-private `ProfileJson` codec owns schema encoding/parsing and
calls the injected ItemCatalog and existing NBT validator. ProfileRepository
alone owns all file I/O; task imports no repository services. Saving shares one
persistence path with `saveSlotOrder`, which still moves complete raw rules and
only changes their destination names.

A handle binds source bytes when opened; expected snapshot comparison at open
uses ID and normalized hash. After open, even a whitespace-only source edit is a
conflict. Before replacement, source structure/hash/bytes and duplicate IDs are
checked repeatedly. Duplicate declared IDs are rejected even in schema-invalid
JSON. Syntactically broken unrelated JSON with no recoverable ID is left alone;
unreadable, oversized or invalid UTF-8 candidates abort the uniqueness scan.
The selected source must always parse, so a matching copy cannot substitute for
a corrupt selection. Existing handles cannot be used by another repository.

The repository writes/forces its own `.profile-*.tmp` in the same directory,
reparses it and compares hashes, then creates/forces an exact
`<filename>.<uuid>.bak` using CREATE_NEW. It rechecks the source before
ATOMIC_MOVE + REPLACE_EXISTING. Unsupported atomic replacement is an explicit
IOException; there is no non-atomic fallback. Failure cleans only this operation's
temporary file and retains the source; a completed backup remains on replacement
failure. Backups are neither discovered nor deleted automatically.

Conflict detection is optimistic, intended for the existing single-thread local
editor. No cross-process lock/compare-and-swap is claimed: an uncooperative writer
can still race the last check and rename. No arbitrary power-loss guarantee or
backup-history feature is added. A future GUI must serialize its own draft/save
access; `Edit`/`ProfileDraft` are deliberately not thread-safe.

## Later GUI integration

1. Keep existing stopped/restored entry restrictions. Use `listProfileFiles()`
   and `load(path)` for rows holding the selected path and frozen expected value.
   Existing `loadAll()` remains compatible with M2-A and the slot-order editor.
2. Open an Edit for that path/snapshot. Bind controls only to its draft. Keep
   incomplete numeric text in the GUI until it can set an integer count; null
   optional fields select schema defaults, while `"{}"` is an explicit compound.
3. Call `validateDraft`. Show structural errors without changing files or training
   state. On success, call task.ProfileApplicability for separate mode indicators.
   Do not label Full count feasibility as guaranteed nontrivial generation.
4. Save with `save(edit)`, display ProfileException/IOException on failure, and
   reopen/reload after success. On stale/corrupt/duplicate errors require explicit
   reload/repair; never choose another file silently. Cancel simply discards Edit.
5. Training START keeps its existing independent reload/freeze boundary. Do not
   send editor state, source paths or NBT editing commands over the protocol.

M2-B ended without a GUI. The subsequent [M2-C visual editor](m2-c-profile-editor.md) now implements this integration using the same public contracts.

## Executed verification

Completed 2026-10-30 (Asia/Shanghai). Executed against the final Java sources:

- `scripts/gradle-jdk8.sh --offline test build`: **BUILD SUCCESSFUL**, **75 tests,
  0 failures, 0 errors, 0 skipped**. JUnit XML totals were inspected after completion.
  This includes all prior M2-A tests and 19 additional M2-B tests.
- `python3 scripts/check-boundaries.py`: passed, **60 production Java files,
  12 packages, 26 directed package dependencies**, no cycles/boundary violations.
- Initial sandboxed Gradle startup failed because its local daemon socket was
  denied. The same offline command passed with local process access; no network
  dependency fetch or environment workaround was introduced.

Coverage executed:

| Tests | Evidence |
| --- | --- |
| `ProfileEditingTest` (8) | Complete ten-slot edit/reload; all rule kinds and EXACT/FIELDS; full typed compounds/lists/byte-int-long arrays; implicit and explicit matching defaults; runtime-normalized templates; invalid inputs and size limits; unchanged old profiles/tasks; Single-only and optional-only legality; Full counts distinct from bounded exhaustion; normalized slot-order regression. |
| `ProfileEditSafetyTest` (10) | Exact Chinese UTF-8/CRLF backups; only selected source modified; source listing excludes backups/temps; byte-stale/semantic-stale/corrupt/invalid UTF-8 rejection; corrupt selection cannot redirect to copy; valid/schema-invalid duplicate IDs; path/repository binding; unsupported atomic and generic replacement failures; unrelated temp preservation; source damage during temporary readback; oversized output. |
| `MinecraftItemCatalogTest` (4 total, 1 new) | Actual vanilla registry/normalization, edited/reloaded tool NBT and implicit EXACT, registry count rejection, materialized/packet-round-tripped task initial states and solution witnesses. |
| Existing `ProfileLayoutPersistenceTest` (4) | All prior permutation, exact backup, shuffled entry, stale/corrupt/duplicate and generated Full task checks still pass. |

Saved/reloaded trainable fixtures exercise Single and Full generators, assert
INCOMPLETE initial states, apply the retained exchange witness and require
TaskValidator COMPLETE. Existing 10,000-task generator tests also remain green;
these executions verify generator behavior, not general feasibility by sampling.

All profile writes in these tests use TemporaryFolder directories. No installed
configuration, real save, bundled default JSON, M2-A implementation, recovery,
timing, protocol or ordinary-world gate was edited. The bundled default is only
copied into temporary test directories by existing fixture setup.

No Minecraft client/game or crash/persistence acceptance scenario was run for
M2-B. A successful test/build is not game acceptance. Later GUI integration,
visual/input acceptance and runtime error presentation remain pending. No other
architectural refactor is required for this backend; future GUI source selection
should use the explicit source API above. Work stops at M2-B.
