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
