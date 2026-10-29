# Slot layout customization

Scope: let a local single-player user reorder an existing profile's ten complete
slot rules from the O options screen. Complete item/template editing is also available through the separate [M2-C visual editor](m2-c-profile-editor.md).
The same profile applies to Single Item and Full Inventory.

All-candidates/hint update:

1. Replace first-candidate cards and trimmed name summaries with a vertical list
   showing every icon/quantity, wrapped candidate rows and vanilla tooltips.
   Keep wrapping, scroll clamping and bounded hit testing in `SlotCandidateLayout`.
2. Add O → Target hints ON/OFF (default OFF, current client process only). Hide
   destination/target/rule hints in both modes while preserving rendered GO_ACK,
   countdown, status, timing and counters. No protocol/recovery changes.
3. Test minimum-window candidate reachability, clipped click/tooltip bounds and
   reflow; validate interchangeable shared block pools. Build and boundary checks
   passed with 41 tests; actual game UI/input acceptance is still pending.

The requested personal JSON is provided only in chat; no installed or bundled
profile is changed by this update.

Completed implementation:

1. Keep all file writes in `ProfileRepository`: validate a ten-position
   permutation, reject changed/ambiguous source profiles, retain the source JSON
   as a `.bak`, and atomically replace the profile.
2. Add a local client screen with two-click exchanges, preview, reset, save and
   cancel. Only allow entry/save after restoration. Reload the profile catalogue
   explicitly in options; training still reloads and freezes its own snapshot.
3. Verify persistence, cancellation by design (no write until Save), stale edits,
   invalid permutations, all rule/NBT fields, and generated tasks using the saved
   order. Run JDK8 tests/build and the boundary checker; record actual results.

No protocol or recovery changes. The client uses its own `ProfileRepository`
instance against the local config directory; it never accesses server objects.
Client → profile is an explicit dependency. Saving changes only destination names
and JSON formatting; rule contents and optional/flexible semantics are retained.
Backups use `<filename>.<uuid>.bak` and are excluded from `*.json` discovery.
Filesystems without atomic replacement reject the save, leaving the old profile.

Verification (2026-10-29):

- `scripts/gradle-jdk8.sh --offline test build` passed: 37 tests, zero
  failures/errors/skips. Four new persistence tests exercise exact backups,
  reloads, shuffled JSON entries, stale/corrupt/duplicate profiles, invalid
  permutations, successive edits, and 60 Full tasks following the saved order.
- `python3 scripts/check-boundaries.py` passed: 53 production Java files,
  12 packages, 26 directed dependencies, no cycles.
- `python3 scripts/verify-owned-smoke-save.py` independently confirmed the
  existing owned baseline in both save paths with no recovery marker.
- The independent production JAR loaded successfully under Intel JDK8/Rosetta
  with the original LWJGL3.2.1 in `run-layout-ui/`, without the smoke mod. That
  directory contains only a copy of the verified owned disposable smoke save;
  no training or UI edits were performed. The UI tool could not target the
  unbundled Java window, so this is a launch check only. The process was closed.

Manual UI acceptance remains pending: open O → Customize layout, exchange sword
and pickaxe, save/reopen and check persistence; repeat with Cancel/Esc and Reset
edits; exchange with offhand and verify rule hints; then exercise a Full session
and confirm its target and completion follow the saved layout. Check GUI scales.
Also inspect every candidate in a seven-item and a long wrapped pool, scroll to
offhand, confirm clipped rows cannot select through header/footer controls, and
start training with target hints OFF to check hidden targets/rules and visible
countdown/GO/timing. Repeat ON and check normal targets return.
Earlier M1 production fault/recovery evidence applies to the earlier artifact;
it is not counted as runtime acceptance of this editor.

User screenshot follow-up: the first editor's 44px vanilla buttons sampled past
the vanilla 20px texture strips, showing a language icon and stray button edges.
Slot cards now draw solid backgrounds with hover/focus borders, retaining the
same click regions, icons and exchange behavior. This changes only rendering;
item selection still uses JSON. Build/boundary recheck is recorded in testing.md;
actual in-game visual confirmation remains pending.

## M2-C coexistence

O now offers Slot order and Edit profile side by side. Slot order keeps its
existing complete-rule permutation contract. Edit profile chooses an explicit
source file and edits an independent M2-B draft. It does not reuse the old
ID-based saveSlotOrder operation for full edits. Profile ID remains read-only;
there is no creation/copy/deletion. The same saved profile serves both modes.

The new item picker reuses SlotCandidateLayout's icon wrapping positions.
EditorViewport/EditorFormScreen own form scrolling, clipped input/tooltip bounds
and focus reveal for all ten slot links, arbitrary candidate lists and full error
text. Buttons remain vanilla 20px controls; vanilla item rendering and tooltips
use newly created client registry stacks. Neither editor borrows player items.
The M2-C execution record distinguishes automated real-client screens from
physical input and manual visual acceptance.
