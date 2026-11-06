# Optional game acceptance mod

Excluded from the production jar. See `docs/production-smoke.md` for launch,
ownership and exact native/runtime contracts. All inventory mutations and probes
run on the integrated-server thread in the explicitly owned disposable save.

| File | Responsibility |
| --- | --- |
| `TrainerSmokeClient` | Owned-world bootstrap, baseline, existing real client input smoke, mode dispatch/results |
| `FaultAcceptance`, `InventoryCrashCut` | Earlier direct inventory-module fault checks and crash boundaries |
| `AcceptanceSupport` | Reflection on trainer state only, synchronous lifecycle setup, exact inventory/disk checks and evidence I/O |
| `MovingAcceptance` | M3-A environment/mode combinations, accepted vanilla movement, relaxed stops, airborne protection/restore and real disconnect setup; no terrain construction |
| `ProtectionAcceptance` | Transformed vanilla handlers/callbacks, phase-wide passive guards, real START/stop I/O failures/retries, ordinary behavior |
| `PhaseGuardAcceptance` | Non-running swaps, interruption protocol, open-screen READY gate, blocked dimension travel |
| `RemainingAcceptance`, `ExitObservation` | Production-mode dispatch, separate exit-request/cleanup phase evidence (including vanilla shutdown interruptions) and LAN stop |
| `CorruptRecoveryAcceptance` | Corrupt both test saves, fresh-process load/lock/raw preservation, controlled repair from archived valid backup |
| `SaveWriteFault`, `mixin/` | Actual compressed stream cuts and stop restore boundaries; no production test API |

`accept-*` modes are production-only. Their required smoke Mixins use pinned
1.16.1 intermediary names (`NbtIo.method_10634` for `writeCompressed`), and
`AcceptanceMixinPlugin` enables them only in those modes outside development.
Existing named `runClient` and normal/crash/recover modes retain their ordinary
behavior and do not install acceptance fault hooks.

Lifecycle setup sends READY/GO_ACK through the real public session interface and
accelerates its countdown through reflection. It does not prove physical input,
display timing or focus behavior. The earlier normal smoke retains those actual
client/rendered handshake requirements. Corrupt fixtures are re-signed where
needed to distinguish checksum rejection from schema/binding/snapshot rejection.

`scripts/run-remaining-acceptance.py` runs cases sequentially, checks process exit
codes/fresh reports, archives raw files (including partial temporary saves) before
the next launch, and verifies each successful case with a new `recover` process.
Failures stop the suite and retain evidence. Never seed over an active recovery;
only the explicit corrupt-check mode may repair its own archived injected record.

## Current UI acceptance entrypoints

`scripts/run-profile-editor-ui.py --run-name run-m2-ui-<unique> --launch`
always runs `client.UsabilityUiSmoke` via `hotbarswaptrainer.usabilityUi=true`.
`--usability` and `--history` are aliases for this same current-screen suite;
`--pb-hud` additionally verifies Full training and swap-count HUD states.
Every fresh run includes editor, finite/unlimited training, PB/history and actual
save/restore/LAN gates. Results are written to `usability-ui-result.txt`.
`--reopen` verifies persistence and reloads Chinese resources in a fresh process.
The old editorSmoke/historyReopen flags, driver and removed history navigation
are retired; historical manifests are refused on reopen.

The launcher verifies exact instance/world ownership and inactive seed recovery
data, copies only that disposable seed and refuses to overwrite previous runs.
These checks use actual production screens with programmatic input and remain
separate from physical input and subjective visual acceptance.

## M2 history/UI acceptance

`HistoryAcceptance` (`accept-history`) observes confirmed restoration before the
ResultStore call, exercises finite Single/Full, unlimited/invalid/stopped/zero
results, a real filesystem rejection and an unchecked statistics failure.
`HistoryNotificationMixin` throws from RESTORING/IDLE sends only while explicitly
armed by that owned case. It is disabled in normal/recover/development modes.

`client.ProfileUiSafetyChecks` extends the shared current UI workflow with PB
corrupt-record warnings, duplicate-ID/corrupt-options feedback and actual
PREPARING/RESTORING/ERROR_LOCKED/retry/LAN entry/save gates.
`UiSmokeControls` owns shared screen navigation; changed configuration controls
use `EditorText` localization keys. `UiRestoreGateMixin` applies only for the
production usabilityUi flag. Its bounded latch exposes RESTORING to the client;
a real POSIX write denial produces ERROR_LOCKED, with permissions restored in
finally before retry. `smoke.ProfileGateAcceptance` owns server-thread setup.
No production fault seam or command is added.

`run-m2-runtime.py` and extended `run-profile-editor-ui.py` reuse the pinned
production launcher and copy only the exact verified, inactive owned UI seed into
fresh isolated directories. Reopen checks never use daily config/save data.

Normal unlimited-stop now sends STOP from client END tick through the same
connection after vanilla held-slot/witness packets. A direct server-thread stop
can overtake the client's unfinished packet batch and does not model a real P
press. Restoration comparison remains immediate and exact for all 41 slots and
selection; there is no grace-period acceptance or inventory correction. A failure
archives the observed NBT for diagnosis. Earlier direct-stop failures and an
intermediate selection-only diagnostic run remain archived, not overwritten.

## M2 item-first GUI acceptance

`client.UsabilityUiSmoke` is opt-in via `hotbarswaptrainer.usabilityUi` and only
accepts an explicitly owned isolated UI instance/world. The launcher defaults to this workflow; `--reopen` starts a separate process. `--refresh-smoke` archives
prior driver/manifest/results/screenshots before updating only this excluded mod.
`smoke.UsabilityTrainingAcceptance` supplies server-thread assertions after the
actual options START packet: independent saved-profile reload, finite/unlimited
solution through vanilla packet handlers, history/end state and confirmed two-file
inventory restoration. Acknowledgements/input are supplied programmatically;
this is not physical-input/subjective-experience acceptance. The production mod
contains none of these classes. See `docs/m2-usability-acceptance.md` for hashes,
actual results, screenshots and remaining manual work.

The base workflow is the direct multi-select revision (28 stages before the shared
safety extension),
inline per-item quantities, zero-selection EMPTY, one training-count field with
0=unlimited, no exchange/fixed-unlimited controls and no ordinary IDLE/Ready body
message. Main/smoke Mixin compiler exports are isolated; see build-support/README
for the resource-only remap regression that actual production launch exposed.
Current artifact evidence is in `docs/m2-multiselect-acceptance.md`.

## Compact PB / HUD follow-up

`python3 scripts/run-profile-editor-ui.py --pb-hud --run-name run-m2-ui-pb-hud-<unique> --launch`
uses a fresh marked instance and extends UsabilityUiSmoke with mode-only PB,
minimum/normal-window rendering, default-hidden swap counts and both RUNNING HUD
states. UsabilityTrainingAcceptance.readyToSolve exposes only a smoke-side setup
step, allowing a rendered interval before vanilla-handler solving. The driver
checks the completed Avg/Best message and restores/verifies both player saves.
It performs programmatic input; screenshots and result files are evidence for that
artifact, not human keyboard/focus acceptance. The production mod has no smoke API.

## Pending mining regression

`run-m2-runtime.py --mode accept-pending-mining --run-name run-m2-runtime-<unique> --launch`
uses the same verified inactive seed. `PendingMiningAcceptance` creates only its
own stone fixture in an already-loaded air position, queues active/delayed mining
through actual vanilla action handlers, then starts both modes/environments. It
checks the direct break boundary before any protected update, 400 subsequent
updates, exact training inventory/NBT, discarded flags, no post-restore replay,
fresh ordinary delayed mining and both restored saved inventories. Only its
created fixture is reset; no unknown terrain is removed or generated by the test.

## M3-B owned arena

`ArenaAcceptance` is selected only by independent `accept-arena-*` modes.
`ArenaUiSmokeAccess` exercises the real title and pause widgets and the client
coordinator; the production client exposes no test API. `ArenaRecoveryChecks`
observes the actual RESTORING boundary through the existing restore adapter and
uses real POSIX host-write denial. `ArenaExitRetryFault` and its two Mixins apply
only to `accept-arena-safety`, injecting one explicitly simulated final-exit save
exception. They never run in normal smoke, development or other acceptance modes.

Instance and world ownership is checked before fixtures/permission changes.
Only a known owned obstacle is placed; outside terrain is inspected only if
already loaded. `run-arena-matrix.py` launches sequential processes, archives
raw cut evidence without ever loading/rebinding those forensic copies, requires
actual halt91/new-process exit0, and independently decodes saved players. See
[workflow](../../docs/production-smoke.md#m3-b-practice-save-acceptance) and
[exact evidence](../../docs/m3-b-practice-arena.md).
