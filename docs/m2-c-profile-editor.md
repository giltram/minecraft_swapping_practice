# M2-C: visual profile editor

## Plan and ownership

1. Add O → Edit profile → explicit source-file selection → profile overview →
   slot/candidate/advanced fields and searchable vanilla item picker. Keep the
   existing layout editor and M2-A options. No creation, copy or deletion.
2. A client-only `ProfileEditorSession` owns one repository Edit, selected Path,
   expected immutable snapshot, raw quantity text and opening baseline. Screens
   share this object; widget reinitialization, resize and child navigation never
   recreate the draft. All SNBT text stays in nullable draft fields. Cancel and
   Reset are in-memory only; destructive rule changes and dirty exits ask first.
3. Discover with listProfileFiles/load; beginEdit(path, expected) binds identity.
   Validate raw quantities before validateDraft, then separately assess Single
   and Full counts through ProfileApplicability (never sampled generation).
   Save rechecks local/stopped/restored client projection, uses save(Edit), then
   loads the same path and begins a fresh Edit. Errors retain the draft and require
   explicit reload after persistence failures. ProfileRepository owns all I/O.
4. Keep registry search/preview entirely in a client adapter. Use vanilla controls,
   fonts, item rendering and tooltips. Shared scroll-form geometry gates rendering,
   clicks and focus; reuse SlotCandidateLayout for the picker grid.
5. Test lifecycle/defaults/invalid text/save conflicts/gates/scroll hit bounds.
   Run offline test/build and boundary checks, attempt isolated game UI checks,
   then record exact results and pending manual acceptance.

Client → task is intentional for ProfileApplicability only (TrainingMode already
exists in client). No profile → task or common → client dependency is introduced.
Server START retains its independent reload/freeze; no protocol/recovery changes.

## Verification

Implementation is complete within M2-C. The editor does not add profile creation,
copy/deletion, history/PBs, M3, protocol changes or inventory/recovery/timing changes.

## Implemented page flow and semantics

- O retains M2-A preferences and the independent Slot order action. Edit profile
  opens an explicit file list with broken-file errors; each button captures its
  listed Path and the immutable value loaded from that file. ID stays read-only.
- Profile overview edits name and links all ten slots. Slot pages expose all four
  rule choices, every candidate, add/remove, and candidate item/quantity/advanced
  fields. The registry picker searches IDs and localized names, uses vanilla
  icon buttons/tooltips and SlotCandidateLayout wrapping. No player inventory is
  consulted. Validated normalized templates supply previews; invalid drafts use
  safe base icons without parsing unchecked SNBT in the renderer.
- One ProfileEditorSession owns source/expected/Edit, ProfileDraft, raw quantity
  strings and baseline. Screens retain it through navigation, resize and GUI
  scale. Input listeners are installed after restoring text. Nullable fields stay
  null until explicitly edited; {} stays explicit. Each advanced field has a
  separate Use default action. Blank/invalid quantity text blocks save even when
  the domain integer still holds its last valid value.
- Main Save alone writes. Cancel/Esc on a dirty profile, Reset, Reload source,
  candidate removal and EMPTY conversions with candidates confirm explicitly.
  Switching to the other rule kinds retains candidates. Child Back/selection
  only edits the shared draft. Reset uses an in-memory opening/save baseline.
- Validation shows the backend's path-bearing structural error. Single and Full
  counts are assessed separately; count feasibility is never labelled guaranteed
  nontrivial generation. Single-only profiles save. No sampled seeds are used.
- Entry/save fail closed until the client has IDLE/ERROR acknowledgement and a
  live local single-player world with LAN closed. The only integrated-server
  metadata read is its public LAN flag; no mutable server session is accessed.
  Save checks the gate again immediately before repository.save(Edit).
- Successful save reloads the same source and begins a fresh Edit, then refreshes
  options. Invalid input stays editable. Persistence failures retain raw drafts
  and lock further saving until explicit confirmed reload, including ambiguous
  I/O outcomes. Reload failure also retains the draft; no same-ID substitute is
  selected. A successful replacement followed by reopen failure is reported as
  saved-but-reopen-failed. START still independently reloads/freezes its profile.
- EditorViewport supplies identical half-open bounds for scissoring, pointer
  dispatch and tooltip eligibility. Forms, complete error text, ten slots and
  arbitrary candidate lists scroll above fixed footer controls. Tab reveals the
  focused control; text input, Enter, Esc and repeat-key lifecycle use vanilla
  behavior. Native button faces are limited to 200×20, avoiding 1.16.1 atlas
  seams; wide input fields/paragraphs remain usable.

## Automated checks

Completed 2026-10-30 (Asia/Shanghai):

- `scripts/gradle-jdk8.sh --offline test build`: BUILD SUCCESSFUL. **90 tests,
  zero failures/errors/skips**. The final exact invocation reused up-to-date test
  output; the preceding build on these same final production sources actually
  executed test. `smokeJar` was also compiled separately for game automation.
- `python3 scripts/check-boundaries.py`: passed, **71 production Java files,
  12 packages, 26 directed package dependencies**, no cycles. Client already
  depended on task; the new ProfileApplicability use is documented explicitly.
- `python3 -m py_compile scripts/run-profile-editor-ui.py`: passed.
- The initial sandbox Gradle daemon could not bind its local socket. Offline
  execution with local process access passed; no dependency downloads or runtime
  substitutions were introduced.

New tests: ProfileEditorSessionTest (11) covers isolated drafts, no-write opening,
raw invalid quantities/SNBT, reset, nullable versus explicit {}, repeated saves
with fresh handles, frozen old snapshots, exact selected path with filename/ID
mismatch, stale/corrupt/duplicate sources, explicit reload, save gate rechecks,
Single-only save and removed-candidate input. EditorViewportTest (3) covers long
forms at several scaled sizes, focus reachability, clipped header/footer/input
bounds and retained-scroll reflow. ProfileItemCatalogTest (1) uses the actual
vanilla registry for ID/display-name search and safe previews of incomplete and
deeply nested raw SNBT. Existing M2-A/M2-B and slot-order tests remain green.

## Actual client acceptance and limits

The opt-in smoke driver is excluded from the production JAR. It runs the actual
production mod in Minecraft 1.16.1 with Intel JDK8/Rosetta, original LWJGL 3.2.1,
pinned Fabric dependencies and offline test identity. The launcher uses a fresh
`run-profile-editor-ui/` directory and copies only the previously owned disposable
world after checking its exact ownership marker and absence of recovery data in
host/canonical UUID saves. Archived partial vanilla temporary files are preserved
verbatim. No installed user configuration, valuable save or bundled default is
edited. The driver never starts training or changes inventory; fixture profile
writes are confined to this isolated instance.

The real-client run exercises 30 staged steps: O/source entry, exact file binding,
offhand navigation and a 122-candidate list, searchable registry selection,
focused text and Tab/Enter/Esc dispatch, unfinished quantity/SNBT across children,
actual window resize and GUI scale, destructive rule/exit confirmations, reset,
save/fresh reopen, stale-file rejection and explicit reload, the retained M2-A
unfinished goal and the existing slot-order editor. Screenshots capture options,
picker, minimum-size advanced controls, validation/confirmation, save/reopen and
conflict feedback. A first pass exposed oversized native button atlas seams;
those were corrected before the final production-artifact run.

The final run **passed all 30 staged steps** and exited normally (code 0),
including confirmed whole-profile cancellation followed by a clean reopen.
Post-exit independent typed NBT decoding compared both host level.dat and UUID
playerdata to the inactive owned seed: complete Inventory, SelectedItemSlot and
UUID were unchanged, and neither saved player compound contained recovery data.
This proves this editor run left the fixture inventory intact; it is not a new
crash-recovery acceptance claim. The tested production JAR SHA256 is
`a4b8bdd366baf1d5d8144d257192f13bcbebbc50b4b067354ba0eb6b8ab24bf0`.
All production classes are Java 8 (52); the UI driver is absent from that JAR.

The final run/evidence result is recorded in
[verification/m2-c-profile-editor-2026-10-30.json](verification/m2-c-profile-editor-2026-10-30.json).
Raw result/logs/screenshots and launch hashes remain in the owned run directory;
earlier passes and failed preparation directories are archived separately.
Preparation initially stopped safely on treating interrupted-write temporary
files as canonical player saves and on missing offline cache paths. These are
not counted as game passes. The launcher now recognizes canonical UUID files
and uses the existing checksum-verified client/log cache.

This is actual rendered-client automation with programmatic input. It does not
establish physical mouse/keyboard behavior or subjective usability. Pending
manual checks:

1. In this owned disposable instance, use real O/mouse/keyboard to edit, save,
   discard/cancel, reopen, use all advanced default actions and inspect vanilla
   tooltips (including potion/custom-NBT details).
2. Exercise real wheel scrolling and clipped icons at top/bottom, keyboard focus
   throughout a long candidate list and item grid, minimum windows, other GUI
   scales and localized/long item names. Automated geometry/input checks passed,
   but manual tooltip/drag/repeated-input acceptance is still pending.
3. Verify blocked entry/save during an actual training/restore/error-locked/LAN
   transition, plus visible corrupt/duplicate/write-denial feedback. Those gates
   and file failures have unit/backend coverage; runtime M2-C automation covers
   idle editing and stale-file feedback only.
4. Full training after a visual edit and inventory/crash recovery are not rerun
   in M2-C. Prior M1 results refer to their own artifacts; compilation or this
   editor run does not establish new recovery/timing acceptance.

No further architectural refactor is needed for M2-C. Work stops here; history,
PBs and M3 remain out of scope.
