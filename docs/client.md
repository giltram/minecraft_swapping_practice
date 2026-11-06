# Client and network

M3-A adds one environment action beside mode: Stationary / Moving, shared by both
inventory modes. Missing schema-1 environment defaults to STATIONARY without a
rewrite. Moving allows vanilla movement and stationary solutions in every phase;
there is no stop grace, extra HUD or movement gate. Initial START still requires
safe dry ground; later task replacement and restoration need no landing wait.
Environment is frozen at START (originally protocol 3; now protocol 4). History/PBs use the
actual environment; the existing two-column PB table labels the options-selected
environment in its title. See [M3-A contracts and acceptance](m3-a-moving-inventory.md).

M3-C adds Parkour to the same selector, in both inventory modes. It requires the
matching READY owned course. Options retain schema 1 and the original saved goal;
Parkour disables that input and sends an effective goal of one (also enforced on
the server). No second settings/world entry. A layout mismatch on Start opens
the existing destructive confirmation, then arena progress, then starts after
READY. Cancellation does not change terrain. Explicit arena reset preserves layout.

Trainer protocol 4 adds bounded course phase/platform/total/falls/nanoseconds,
independent of task phase/time. Arena protocol 2 adds FLAT/PARKOUR conversion
actions and the detached layout flag. Unknown/old wire versions are rejected;
the client never reports checkpoint/finish success. HUD course interpolation
continues through RETURNING, then freezes at FINISHED/STOPPED. First GO_ACK alone
starts course time; task GO_ACK semantics remain unchanged. Inventory-first holds
SUCCESS; course-first holds on the finish pad until inventory succeeds. Both are
required. Single-task PBs are explicitly labelled and separated by environment;
they never display course time as an inventory PB. New copy has en_us/zh_cn keys.

`client.TrainerClient` owns only the local HUD projection, accepted preferences and handshake flags. It assembles `ClientOptionsStore` for local configuration I/O and `ProfileRepository` for local profile validation. It never accesses integrated-server session objects. `network.TrainerProtocol` is the common immutable wire contract; `ServerPackets` and `client.ClientPackets` decode incoming buffers immediately and queue detached values on the game thread.

Every message includes protocol version, session/task UUID and revision. The server owns acceptance rules and results. The client rejects old revisions and recently closed sessions. A task change resets its READY, GO_ACK and interruption flags. Malformed or unsupported packets are discarded; a missing handshake eventually times out on the server.

P toggles training and O opens Training options (mode, environment, task count and hints). Both bindings appear in vanilla Controls. The client only calls `wasPressed()` on these two mod bindings. The `MinecraftClient` adapter lets vanilla consume its own Swap Hands queue, suppressing only premature swap packets before GO_ACK or after an interruption. RUNNING uses the original packet path, including rebound keys. Hotbar selection is untouched.

Session length uses the label **Number of successful training tasks
(0=unlimited)** directly above its input, without a separate title/help block. Positive values count successfully completed
training tasks only. The raw text stays on TrainerOptionsScreen through resize,
GUI scale and child returns. Blank, syntax errors and overflow block Start/Enter
without replacing accepted preferences. TrainerClient.parseGoal shares the exact
nonnegative signed-int validation used by accepted options. Legacy0/positive
values map directly; no migration or wire/history/PB change is needed.
The ordinary server IDLE/Ready catalogue message is omitted from O; other feedback,
including actual errors, remains visible.

READY requires the exact prepared ten-slot inventory, selected slot, focus, no screen and a released Swap Hands binding. GO_ACK is queued after the first GO HUD draw and before subsequently allowed vanilla swap packets. Rendering is earlier than physical display; the measured server acknowledgement interval is not a claim of frame-perfect input latency. Opening a screen, pausing or losing focus reports an interruption. The selector does not pause the integrated server so restoration can finish.

The HUD displays server phase, progress and time, interpolating elapsed time only while RUNNING.
Show swap counts is a local option, default OFF, which adds attempted/effective swaps.
Mode/profile labels, selections and mistakes are hidden; completed summaries show Avg/Best. Final times remain server values. When hints are enabled, Single Item names its explicit destination; Full Inventory labels its icons as one valid target and the selected hotbar/offhand rules describe alternatives. Finite and unlimited progress use the same projection. Rule/item editing is implemented separately in M2-C; history/PBs are available from the stopped/restored O screen (see M2-E below).

O → Target hints: ON/OFF controls only the target/destination/rule block, in both
modes. The preference defaults to false and persists across sessions, world
changes and client restarts through M2-A configuration below. Status/error text,
progress, timer, countdown and GO remain visible; swap counters follow their own option. In particular, rendered GO
still invokes `afterGoRendered` independently of the hint flag, so hiding hints
cannot suppress GO_ACK or stall preparation. The wire contract and completion
rules are unchanged. With Single Item hints off, the explicit destination is also
hidden; overlapping candidate pools can then make that task's destination
ambiguous to the player.

## M2-A local client options

`ClientOptions` is an immutable validated value containing `mode`, `environment`, `profile`,
`goal` (requested successes, not achieved progress), `showHints` and `showSwapCounts`.
`ClientOptionsStore` alone owns their file I/O. `TrainerClient` loads once during
client initialization, resolves profile availability through its local repository,
and saves accepted option changes. Neither component reads server session objects.

The file is `config/hotbar-swap-trainer/client-options.json`:

```json
{
  "schemaVersion": 1,
  "mode": "SINGLE_ITEM",
  "environment": "STATIONARY",
  "profile": "default",
  "goal": 10,
  "showHints": false,
  "showSwapCounts": false
}
```

A missing file uses those same defaults and is not created just by loading or
opening the screen. Accepted changes and explicit profile selections write the
complete validated value. Blank/invalid fixed-count drafts remain screen-local, so toggling another
option while a draft is invalid saves the last valid goal. Reinitializing the
screen with unchanged valid text does not write. All nonnegative signed-int goals,
including 0 and 2147483647, round trip.

Loading requires the original five fields with exact JSON types, supported schema, known
mode, legal profile ID and an integer goal in range. The optional boolean
showSwapCounts defaults to false and environment defaults to STATIONARY in legacy schema-1 files; loading never rewrites them. Unknown environments are rejected.
Unknown/duplicate fields,
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
explicit profile selection saves the resolved selection. With no valid profiles, Start is disabled; Edit item layout remains available
to display source-file errors and refresh after repair.
Other options and Done remain usable; no unavailable profile is saved. Selecting
a layout after repairing even an unchanged profile saves pending valid preferences.

Tests: `ClientOptionsStoreTest`, `ClientOptionsPersistenceTest` and the existing
`TrainingGoalInputTest`. See [testing.md](testing.md#m2-a-client-options-verification)
for executed checks and pending game acceptance.

O → Edit item layout is the sole layout entry. Its first page selects the training
layout; each adjacent Edit action opens name, nine hotbar icons, offhand and
Save/Cancel/Reset. Exchange positions has been removed from the GUI and
editor coordinator. The old saveSlotOrder backend remains compatible. Slot click
opens the searchable multi-select item catalogue directly. SlotCandidateLayout
continues to own pure icon wrapping geometry.

Validation requires an actual 1.16.1 client: check rebound Swap Hands, pre-held/repeated keys during countdown, multiple same-tick packets, pause/focus loss, GUI scales and target rule readability. See `docs/testing.md` for the final task's executed versus pending checks.

A client interaction-manager guard rejects item/block use before vanilla prediction (including bucket fluid placement) and reports FORBIDDEN. Server packet guards remain independent, retain vanilla block/dig corrections, and own invalidation/counters. ERROR views include the current valid profile catalogue so a repaired/deleted profile cannot trap selection.

## M2-C visual profile editor

O → Edit item layout is the sole training-layout selector. Clicking a layout name
selects and immediately persists its ID without opening the editor or editing the
profile file. The selected row is marked; the adjacent Edit button opens that exact
source for editing, without changing the training selection. Duplicate IDs after
the first valid file cannot be selected, matching repository catalogue order.
Refresh/reflow only reload the catalogue. There is no separate Profile button in O.

O → Edit item layout → Edit beside a profile → slot → multi-select catalogue.
Click catalogue icons to select/deselect any number of allowed items, staying on
one page. Every selected icon/count appears above the catalogue, and catalogue
icons are marked green. Click a selected icon to edit its quantity in the left-aligned field below
“Item quantity: name”; the adjacent Delete button deselects it. Explicitly deselecting the final item makes the slot EMPTY.
One and many REQUIRED items use the same count presentation. The picker heading
is simply the position and count (for example Hotbar1 ·2).
Existing OPTIONAL/FLEXIBLE profiles keep their kind on opening or additive edits.
Advanced rule/NBT pages remain a secondary picker footer action: retaining them
keeps OPTIONAL/FLEXIBLE/custom NBT editable, at the small cost of one extra entry.
No catalogue-count/instruction line or multiplication-sign quantity label is shown.

| Component | Ownership / contract |
| --- | --- |
| ProfileEditorSession | Exact source/expected/Edit, ProfileDraft, per-item raw quantities, reset baseline, validation/applicability and typed save/reload feedback. Selection adds/removes actual allowed items in memory. No pending single-item proposal or exchange state. |
| ProfileSlotPresentation | Uniform localized allowed-item counts; special legacy rule semantics are described only where relevant. |
| ProfileSourceScreen / ProfileEditorScreen | Exact source choice; name and hotbar/offhand previews; Save/Cancel/Reset/reload after errors. |
| ProfileItemPickerScreen / ProfileItemCatalog | Name/ID search, scrollable marked catalogue, complete selected-icon strip, inline per-item quantities, safe normalized previews and vanilla tooltips. Query/active icon are view state; counts/SNBT stay in the shared draft. |
| ProfileSlotScreen / ProfileCandidateScreen / ProfileAdvancedScreen | Secondary natural-language rules and complete per-item NBT/matching/default settings, preserving null versus explicit {}. |
| TrainerOptionsScreen / TrainerClient.parseGoal | One raw training-count input, explicit0 hint and shared valid-goal parser; existing ClientOptionsStore persistence. |
| EditorViewport / EditorFormScreen / EditorConfirmScreen | Shared clipped scroll/input/tooltip geometry, Tab focus reveal, inline errors and fixed footers. |

Ordinary toggles are direct edits, with no mandatory selection/quantity confirmation
page. Removing custom NBT/matching items or the last item of a special rule asks
before destructive removal. Adding items never replaces other allowed items or
normalizes their NBT/defaults. Main Save alone writes through the repository.

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
opened/saved baseline; all rule changes confirm first. Switching
to REQUIRED/OPTIONAL/FLEXIBLE preserves all allowed items. Advanced item removal is also
confirmed; ordinary catalogue toggles ask only for custom/special-rule removal. Full count feasibility is labelled as a count condition only; legal
Single-only drafts save, with no seed sampling.

Save calls repository.save(Edit), then load(samePath)/beginEdit(samePath, loaded)
and refreshes options. Invalid input stays editable. Persistence errors preserve
the draft, show the readable error and require explicit confirmed Reload source
before another save, including when replacement succeeded but reopening failed.
Reload never searches for another same-ID file. See [M2-C acceptance](m2-c-profile-editor.md).


## M2-E history and scrollable options

TrainerOptionsScreen now shares EditorFormScreen/EditorViewport so preferences,
long feedback and the PB entry scroll above fixed Start/Done actions.
Goal drafts survive resize and child returns; invalid text never overwrites the
accepted option. History uses the same canEditProfiles local/LAN-closed/live-world
and acknowledged IDLE/ERROR gate at entry and navigation. Active,
RESTORING, ERROR_LOCKED and unknown join projections fail closed. Pages close when
the gate is lost; restoration is never paused.

HistoryBrowser owns the current-player UUID and explicitly refreshed immutable snapshot.
Its filter/page query API remains available internally; the screen exposes no filters,
pagination or details. The client root injects the common installation history directory.
Entry reads through HistoryStore; rendering and resizing never read disk.
The 2×2 PB table shows Single Item / Full Inventory and their fastest successful task
times (three decimal seconds, or PB —) for the options-selected environment, which
is labelled in the title. PersonalBests.fastestTaskForMode merges profile
hashes, hints, goals and partial/unlimited sessions while retaining player and supported
version checks and an explicit environment match. Its old query defaults to
Stationary. Warnings mark incomplete reads. History files and the stricter
forConditions API remain compatible. See [current PB contract](m2-e-history-pb.md).

Protocol version is now **3**: START carries the declared Target hints boolean
and environment enum; views carry the frozen environment. It is frozen once, not read from client mutable state at
stop. Other request factories remain source compatible. Server-thread queueing and
session/revision acknowledgement guards are unchanged. This declaration is not an
anti-cheat proof. History itself needs no protocol messages or server object access.

Profile display names share the domain/history bound of 256 Java characters.
The editor enforces it at input; source files with longer names are preserved and
reported as invalid before they can start an unrecordable training session.

## M3-B arena client boundary

PracticeArenaClient owns title launch preflight, detached arena status and the
exit handshake; PracticeArenaScreen reuses the existing non-pausing scroll form.
TitleScreenMixin adds one entry and GameMenuScreenMixin routes the existing
owned-world exit. O adds reset/exit only after the server identifies an arena.
No duplicate enter action or replacement training/profile/PB controls. All new
copy uses trainer.arena.* in en_us/zh_cn.

Client requests are queued at END_CLIENT_TICK after vanilla input. EXIT stops and
restores on the server; ordered inventory/held-slot packets precede EXIT_RESTORED.
The client acknowledges with EXIT_COMMIT at its end tick, after its vanilla slot
echo. The server checks the restored snapshot and both saved players before
EXIT_READY permits disconnect. The existing vanilla disconnect loop renders
until actual server-thread death. No fixed sleep, automatic previous-world load,
client session-object access or client-authored recovery snapshot is involved.

Incoming statuses are tied to the actual client player/connection object; queued
work from a departed connection is discarded. Server readiness remains
authoritative even if client UI/progress notifications fail. See the
[arena contract](../src/main/java/dev/hotbarswaptrainer/arena/README.md) and
[M3-B acceptance](m3-b-practice-arena.md).
