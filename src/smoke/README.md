# Optional game acceptance mod

Excluded from the production jar. See `docs/production-smoke.md` for launch,
ownership and exact native/runtime contracts. All inventory mutations and probes
run on the integrated-server thread in the explicitly owned disposable save.

| File | Responsibility |
| --- | --- |
| `TrainerSmokeClient` | Owned-world bootstrap, baseline, existing real client input smoke, mode dispatch/results |
| `FaultAcceptance`, `InventoryCrashCut` | Earlier direct inventory-module fault checks and crash boundaries |
| `AcceptanceSupport` | Reflection on trainer state only, synchronous lifecycle setup, exact inventory/disk checks and evidence I/O |
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

M2-C adds the opt-in `client.ProfileEditorUiSmoke` entrypoint, enabled only by
`hotbarswaptrainer.editorSmoke=true`. `scripts/run-profile-editor-ui.py` creates
a fresh run-profile-editor-ui instance, verifies the exact ownership marker and
absence of recovery data in every copied player save, and copies the existing
owned layout-UI disposable world. It refuses reuse/overwrite of a previous run.
It never starts training or changes inventory. Its programmatic screen input,
scaled reinitialization, rendered screenshots and result file do not establish
physical input/subjective visual acceptance. The test classes remain excluded
from the production artifact.

## M2 history/UI acceptance

`HistoryAcceptance` (`accept-history`) observes confirmed restoration before the
ResultStore call, exercises finite Single/Full, unlimited/invalid/stopped/zero
results, a real filesystem rejection and an unchecked statistics failure.
`HistoryNotificationMixin` throws from RESTORING/IDLE sends only while explicitly
armed by that owned case. It is disabled in normal/recover/development modes.

`ProfileEditorUiSmoke` optionally extends its M2-C workflow with
`hotbarswaptrainer.historyUi`: history fixtures/navigation, valid rule/candidate/
count/typed-NBT saving, real server training on that saved profile, actual
PREPARING/RESTORING/ERROR_LOCKED/retry/LAN entry/save gates. `UiRestoreGateMixin`
is enabled only for that explicit production UI flag. Its bounded latch allows
the client to observe RESTORING before the server completes restoration; a real
POSIX denied playerdata write produces ERROR_LOCKED and original permissions are
restored in finally before retry. No production fault seam or command is added.
`ProfileTrainingAcceptance` runs only on the owning server thread.

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
accepts an explicitly owned isolated UI instance/world. The launcher exposes
`--usability`; `--reopen` starts a separate process. `--refresh-smoke` archives
prior driver/manifest/results/screenshots before updating only this excluded mod.
`smoke.UsabilityTrainingAcceptance` supplies server-thread assertions after the
actual options START packet: independent saved-profile reload, finite/unlimited
solution through vanilla packet handlers, history/end state and confirmed two-file
inventory restoration. Acknowledgements/input are supplied programmatically;
this is not physical-input/subjective-experience acceptance. The production mod
contains none of these classes. See `docs/m2-usability-acceptance.md` for hashes,
actual results, screenshots and remaining manual work.

The current `--usability` workflow is the direct multi-select revision:28 stages,
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
