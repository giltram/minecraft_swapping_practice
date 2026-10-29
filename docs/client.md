# Client and network

`client.TrainerClient` owns only the local HUD projection, accepted preferences and handshake flags. It assembles `ClientOptionsStore` for local configuration I/O and `ProfileRepository` for local profile validation. It never accesses integrated-server session objects. `network.TrainerProtocol` is the common immutable wire contract; `ServerPackets` and `client.ClientPackets` decode incoming buffers immediately and queue detached values on the game thread.

Every message includes protocol version, session/task UUID and revision. The server owns acceptance rules and results. The client rejects old revisions and recently closed sessions. A task change resets its READY, GO_ACK and interruption flags. Malformed or unsupported packets are discarded; a missing handshake eventually times out on the server.

P toggles training and O opens the small mode/profile/length selector. Both bindings appear in vanilla Controls. The client only calls `wasPressed()` on these two mod bindings. The `MinecraftClient` adapter lets vanilla consume its own Swap Hands queue, suppressing only premature swap packets before GO_ACK or after an interruption. RUNNING uses the original packet path, including rebound keys. Hotbar selection is untouched.

Session length is a numeric `TextFieldWidget`, labelled Successes with a visible
`0 = unlimited` hint. Valid goals cover the entire nonnegative signed-int range
(0..2147483647); positive values count successful tasks only. The old 10000 cap
is removed from both request decoding and server START validation without changing
the wire encoding. The client accepts ASCII digits, permits an empty editing
draft, and disables Start/Enter for empty or overflowing values. Invalid edits do
not replace the last valid client preference. The screen retains its draft across
resize and visits to the layout editor; repeat-key events are scoped to the
options screen. The layout editor remains available while the goal draft is
invalid, provided restoration has finished. Tests: `TrainingGoalInputTest` and
`TrainerProtocolTest` (including 0, 1, 73, 10001 and the signed-int maximum).

READY requires the exact prepared ten-slot inventory, selected slot, focus, no screen and a released Swap Hands binding. GO_ACK is queued after the first GO HUD draw and before subsequently allowed vanilla swap packets. Rendering is earlier than physical display; the measured server acknowledgement interval is not a claim of frame-perfect input latency. Opening a screen, pausing or losing focus reports an interruption. The selector does not pause the integrated server so restoration can finish.

The HUD displays the server phase and counters, interpolating elapsed time only while RUNNING. Final times remain server values. When hints are enabled, Single Item names its explicit destination; Full Inventory labels its icons as one valid target and the selected hotbar/offhand rules describe alternatives. Finite and unlimited progress use the same projection. Rule/item editing is implemented separately in M2-C; history/PBs remain unimplemented.

O → Target hints: ON/OFF controls only the target/destination/rule block, in both
modes. The preference defaults to false and persists across sessions, world
changes and client restarts through M2-A configuration below. Status/error text,
progress, counters, countdown and GO remain visible. In particular, rendered GO
still invokes `afterGoRendered` independently of the hint flag, so hiding hints
cannot suppress GO_ACK or stall preparation. The wire contract and completion
rules are unchanged. With Single Item hints off, the explicit destination is also
hidden; overlapping candidate pools can then make that task's destination
ambiguous to the player.

## M2-A local client options

`ClientOptions` is an immutable validated value containing `mode`, `profile`,
`goal` (requested successes, not achieved progress) and `showHints`.
`ClientOptionsStore` alone owns their file I/O. `TrainerClient` loads once during
client initialization, resolves profile availability through its local repository,
and saves accepted option changes. Neither component reads server session objects.

The file is `config/hotbar-swap-trainer/client-options.json`:

```json
{
  "schemaVersion": 1,
  "mode": "SINGLE_ITEM",
  "profile": "default",
  "goal": 10,
  "showHints": false
}
```

A missing file uses those same defaults and is not created just by loading or
opening the screen. Accepted changes and explicit profile selections write the
complete validated value. Blank/overflowing numeric drafts remain screen-local, so toggling another
option while a draft is invalid saves the last valid goal. Reinitializing the
screen with unchanged valid text does not write. All nonnegative signed-int goals,
including 0 and 2147483647, round trip.

Loading requires all five fields with exact JSON types, supported schema, known
mode, legal profile ID and an integer goal in range. Unknown/duplicate fields,
trailing content, malformed JSON/UTF-8 and files above 16 KiB are rejected. A failed
load uses the complete safe defaults, logs the detailed path/cause and shows a
repair message in O. The original file remains untouched; configuration writes
stay disabled for that client process even when options are edited. Repair or
move the file and restart to enable saving again. In-memory options stay usable.

Saving writes and forces a unique temporary file in the same directory, then uses
`ATOMIC_MOVE` with replacement and cleans up the temporary file. There is no
non-atomic fallback. Save failures retain accepted in-memory preferences, log the
cause and display a retry message; a later accepted change retries saving. This
does not promise durability across arbitrary power loss/filesystem failure.

Profiles are checked on initialization, screen initialization, explicit profile
selection, accepted edits and START. A missing selection explicitly falls back to
`default` if valid, otherwise the first valid profile in repository filename order.
Loading/fallback alone does not rewrite the file; the next accepted change or
explicit profile selection saves the resolved selection. With no valid profiles, Start and Customize layout are
disabled, and Profile remains clickable to retry after repairing the local files.
Other options and Done remain usable; no unavailable profile is saved. Retrying
Profile after repairing even a single unchanged profile saves pending valid preferences.

Tests: `ClientOptionsStoreTest`, `ClientOptionsPersistenceTest` and the existing
`TrainingGoalInputTest`. See [testing.md](testing.md#m2-a-client-options-verification)
for executed checks and pending game acceptance.

`TrainerLayoutScreen` is opened from O → Customize layout only after the client
observes restoration complete in local single-player. It loads a frozen profile
through a separate client-owned `ProfileRepository`; it never accesses server
services or mutable inventory. Two slot clicks exchange the complete rules.
Each row shows its slot, rule kind, candidate count and **every** candidate icon
with quantities. Long pools wrap, the list scrolls vertically, and each icon has
the vanilla item tooltip (including potion details). Reset edits restores the opening order, Cancel/Esc
writes nothing, and Save persists the selected profile for both modes through
`saveSlotOrder`. Errors keep the editor open. Saving also rechecks that no
transaction is active. Profiles are discovered locally on explicit profile
selection, so file changes do not depend on an earlier server catalogue packet.
The server still independently reloads/validates/freezes the profile on START.
There is no protocol change or inventory capture in the editor.

Slot cards draw their own backgrounds, sized to fit all their candidates.
Vanilla 1.16.1 `AbstractButtonWidget` samples a fixed 20px-high atlas strip without
vertical scaling; using that renderer for taller cards spills into adjacent
button states and the language icon. Ordinary 20px action buttons remain vanilla.
`SlotCandidateLayout` owns the pure wrapping/scroll/hit-test geometry. OpenGL
scissoring clips list rendering between the header and footer, and hit testing
uses those same bounds so a clipped row cannot capture action-button clicks.
Swaps, resets and resizing recalculate heights and clamp the previous scroll.
Tests live in `client/SlotCandidateLayoutTest`.

Validation requires an actual 1.16.1 client: check rebound Swap Hands, pre-held/repeated keys during countdown, multiple same-tick packets, pause/focus loss, GUI scales and target rule readability. See `docs/testing.md` for the final task's executed versus pending checks.

A client interaction-manager guard rejects item/block use before vanilla prediction (including bucket fluid placement) and reports FORBIDDEN. Server packet guards remain independent, retain vanilla block/dig corrections, and own invalidation/counters. ERROR views include the current valid profile catalogue so a repaired/deleted profile cannot trap selection.

## M2-C visual profile editor

O → Edit profile → source-file list → profile overview → slot → candidate →
advanced NBT or searchable item picker. The old Slot order editor remains a
separate O action. The source list uses listProfileFiles/load, including visible
errors for broken files. A row captures both Path and expected LoadoutProfile;
beginEdit never guesses a target from ID. The editor's ID is read-only.

| Component | Ownership / contract |
| --- | --- |
| ProfileEditorSession | One explicit source, expected snapshot and Edit; independent ProfileDraft; per-candidate raw count strings; opening/reset baseline; structural validation and separate ProfileApplicability; save/reload/error coordination. No direct file I/O or widgets. |
| ProfileSourceScreen / ProfileEditorScreen | Discovery and exact source selection; name, ten slot links, validation, Single and Full count indicators, save/cancel/reset/explicit reload. |
| ProfileSlotScreen / ProfileCandidateScreen / ProfileAdvancedScreen | Rule choices and all candidates; item/count input; nullable generation/matching SNBT and EXACT/FIELDS, with explicit use-default actions. Child Back retains the shared draft, never saves. |
| ProfileItemCatalog / ProfileItemPickerScreen | Client registry enumeration, ID/localized-name search, safe base icons for invalid drafts or validated normalized template previews, and vanilla tooltips. No real inventory access. Reuses SlotCandidateLayout wrapping geometry. |
| EditorViewport / EditorFormScreen / EditorConfirmScreen | Shared scroll bounds, clipped vanilla controls/text, pointer/tooltip gating, focus reveal/Tab, repeat-key lifecycle and scrollable confirmations. Every form and arbitrarily long errors/lists scroll above fixed footer actions. |

Entry and Save require local single-player with a live player/world, LAN closed
(public server publication metadata only), and an IDLE/ERROR client projection.
Null/unacknowledged join and all active/RESTORING/ERROR_LOCKED phases fail closed.
No server session is accessed. The gate is checked in the action as well as button
state. Editor screens do not pause restoration. START remains independently owned
by the server, including reloading and freezing the saved profile.

Widgets restore values before installing change listeners. Null optional fields
stay unspecified; explicit {} and explicit EXACT remain explicit. Advanced
Use default sets null. Invalid count text stays in ProfileEditorSession even if
the integer draft retains its last valid value: validation/save checks raw text
first, never substitutes a default. Incomplete SNBT stays in ProfileDraft. These
values survive child returns, resize and GUI-scale reinitialization.

Dirty Cancel/Esc requires confirmation. Reset confirms replacement with the last
opened/saved baseline; EMPTY confirms deleting nonempty candidate lists. Switching
to REQUIRED/OPTIONAL/FLEXIBLE preserves all candidates. Candidate removal is also
confirmed. Full count feasibility is labelled as a count condition only; legal
Single-only drafts save, with no seed sampling.

Save calls repository.save(Edit), then load(samePath)/beginEdit(samePath, loaded)
and refreshes options. Invalid input stays editable. Persistence errors preserve
the draft, show the readable error and require explicit confirmed Reload source
before another save, including when replacement succeeded but reopening failed.
Reload never searches for another same-ID file. See [M2-C acceptance](m2-c-profile-editor.md).
