# M2 D → E → F execution record

Scope: durable history, local history/PB UI, integration acceptance. No M3 or recovery/timing/completion changes. Started 2026-10-30.

1. D: extend immutable results with optional frozen metadata (legacy constructor remains); inject a config-instance-wide player/session JSON store at the common root; isolate post-restore statistics/notification failures. Validate storage and protocol with temporary-directory tests.
2. E: public bounded history snapshot/query + pure PB service; local client reads only its UUID from the same injected instance directory, after IDLE/ERROR and LAN-closed gate. Explicit refresh only, no HUD I/O. Reuse EditorFormScreen for list/filter/page/detail/PB.
3. F: final tests/build/boundaries/archive audit; separate remapped production smoke, fault and UI runs using existing owned fixtures; independent post-exit NBT readback. Record artifact-specific evidence and keep physical input pending.

Contracts/decisions:
- History root is config/hotbar-swap-trainer/history, shared across worlds in this installation, partitioned by player UUID/session UUID. No paths cross packets or enter recovery NBT.
- schemaVersion 1; frozen player/session IDs, wall-clock start/end, mode, profile ID/name/hash, requested successes, declared Target hints, timing/scoring versions, stationary environment and explicit end state. Unknown legacy metadata is not PB eligible.
- SUCCESS only for fastest task. Finite PB requires COMPLETED with exact positive goal and successes; total/average are successful-task nanoseconds only, excluding invalid attempts, countdown and gaps. Groups include player/mode/hash/hints/timing/scoring/environment, plus goal for session PB. Task PB crosses lengths. Stable ties use ended time then session UUID then task order.
- Restoration confirmation completes before any history work. Crash between restoration and history commit can lose history; no cross-file transaction.
- Immutable append-only final records: exact canonical content repeats are idempotent; conflicting IDs preserve the original and fail. Same-directory forced temporary file, validated reread, atomic commit only. Preserve bad/unknown files; temporary files ignored. Bounded files/tasks/catalogue, partial read warnings.

Progress: D and E implemented and stage-verified (98 then 105 tests); final suite executed 107 tests. F passed 8 history runtime checks, the complete 25-case M1 regression with 58 verified production launch manifests, 65 real-client programmatic UI stages plus restart and independent post-exit readbacks. Final artifact/hash and evidence are in m2-acceptance.md and verification/m2-def-2026-10-30.json.

Remaining: human physical-input and subjective visual checks only. No full M2
acceptance claim, ordinary-world unlock or M3 work. The initial normal direct-stop
failure and locked-Mac blocker are historical; see the resumed verification below.

Resume 2026-10-30: user confirmed the Mac is unlocked; native UI inventory now works. Resuming final normal-crash/recover acceptance on the same production hash and owned instance. Earlier failure evidence remains archived. No production changes are planned unless a reproducible defect is established.

Final resumed verification: the unlocked normal run passed without using its
selection diagnostic; halt91/recover0 and independent saved readback passed.
The final smoke then replaced direct server stop with client END-tick STOP on the
same connection as prior vanilla packets, removed the diagnostic grace period,
and passed all four strict immediate restoration comparisons plus actual
halt91/fresh recover0. Production SHA256 remains unchanged. The old failed live
snapshot was not captured, so its exact mismatch is not claimed to be known.
Final evidence: verification/m2-normal-final-2026-10-30.json; requiredPending in
verification/m2-def-2026-10-30.json now contains human observations only.
