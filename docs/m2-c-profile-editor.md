# M2-C: visual profile editor

Current usability flow supersedes the earlier M2-C page hierarchy. See
[m2-usability-plan.md](m2-usability-plan.md) and the current-artifact
[acceptance record](m2-multiselect-acceptance.md). Earlier results below retain their
original artifact hashes and do not certify the latest UI.


## Current flow and ownership

O → Edit item layout → exact source selection → main name/hotbar/offhand → slot
→ searchable multi-select catalogue. No rule or add-item prerequisite. Clicking
catalogue items toggles membership; all selections are displayed and marked.
Quantities edit inline, and deselecting the last item becomes EMPTY. One and many
allowed items share one presentation. Done returns without writing; main Save
calls the same file-bound ProfileRepository edit handle. Reset/dirty exit and
conflict/reload retain existing safeguards. No exchange-position action remains.

ProfileEditorSession owns actual allowed items and raw count/SNBT, not an extra
single-item proposal. Opening and additive edits preserve complete custom NBT and
OPTIONAL/FLEXIBLE semantics. Destructive custom removal confirms first. Advanced
rule/NBT controls remain a secondary picker action. O uses one training-count
field with0=unlimited and omits its routine server IDLE/Ready message. See the
[current acceptance](m2-multiselect-acceptance.md); prior results below are historical.

## Earlier M2-C automated checks (historical artifact)

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

## Earlier M2-C actual client acceptance and limits (historical artifact)

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

This historical M2-C run did not include history/PBs or M3. Current GUI and
existing M2-D/E verification are linked above; M3 remains unimplemented.
