# M2-E personal records

O → PB requires local single-player, LAN closed, a live player/world and an
IDLE/ERROR restoration acknowledgement. Unknown join state, active phases,
RESTORING and ERROR_LOCKED are refused; actions recheck the gate, pages close when
it is lost and screens never pause restoration.

The screen is a two-column, two-row table: Single Item and Full Inventory, then
PB seconds for each mode (or PB —). It exposes no filters, history list, hashes or
details. Back is the only footer action. A concise warning marks
incomplete history without overwhelming the table.

HistoryBrowser reads only the current player's UUID through the injected
HistoryStore on entry. Rendering, resize and query operations
never read files. No server session access or history network endpoint exists.
Screens do not parse JSON or own scoring logic. History records remain unchanged.

## Current PB contract

PersonalBests.fastestTaskForMode(snapshot, mode) returns an optional Best reference:

- Lowest positive SUCCESS duration for that mode and the snapshot's player.
- Combines profile hashes, hint settings and requested lengths, including successful
  tasks in stopped/disconnected/partial/unlimited sessions. INVALID/STOPPED attempts
  never score. Completing an entire finite session is not required.
- Null legacy metadata and unknown timing/scoring/environment versions are excluded.
- Ties use earliest session end, then canonical session UUID, then attempt order.
- Derived only from valid records in the explicitly refreshed immutable snapshot;
  no PB file/cache. These are random-practice personal records.

HistorySnapshot.query retains its stable filters and bounded pages for existing
consumers. PersonalBests.forConditions retains its original strict grouping by
mode/profile hash/hints/versions, plus exact completed goal for finite total/average.
Those APIs are no longer exposed by this simplified screen. Stored profile hashes
remain configuration fingerprints; changing configuration does not split the table.
See [HUD/PB implementation and verification](hud-pb-simplification.md).

## Historical verification before simplification

E verification: offline test/build/smokeJar passed; test actually executed **105**
tests, zero failures/errors/skips. Five PB tests cover grouping/hash/hints/goals,
unknown metadata/player/mode/versions, partial/unlimited/invalid/zero, ties and
incomplete warnings. HistoryBrowserTest covers no implicit I/O, blocked refresh,
page/filter retention and clamping. Protocol test covers both declared hint states
and trailing payload rejection. Boundaries passed: 78 files, 12 packages, 27 edges.
Real rendered-client and physical-input results are tracked separately in M2-F.


F follow-up: the options/history/editor screens passed 65 actual production-client
programmatic stages, including corrupt-options/profile-write/duplicate feedback and retained drafts,
saved rule/candidate/count/NBT training and live
PREPARING/RESTORING/ERROR_LOCKED/retry/LAN gates. A new process reread all four
options, 25 history records and derived PB with two preserved warning files.
See [M2-F](m2-acceptance.md) for exact artifact/evidence and pending human input.
