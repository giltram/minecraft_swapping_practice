# M2-A: client option persistence

Scope: mode, selected profile, requested successes and Target hints only. No
changes to recovery, timing, protocol, ordinary-world gates, history/PBs or rule editing.

Execution plan:

1. Add an immutable validated client option value and a client-only JSON store.
2. Load once at client initialization; atomically save accepted changes only.
   Preserve unreadable/invalid files and lock writes until repair and restart.
3. Resolve missing profiles against the local valid catalogue, report fallback,
   and leave the selector usable even when no profiles are available.
4. Cover round trips, defaults, malformed/invalid files, preservation, drafts,
   fallback and write failures. Run the requested offline test/build and boundaries.
5. Update architecture/client/test documentation with actual results and pending
   game checks, then stop at M2-A.

Completed 2026-10-29. Implementation: `ClientOptions` validates the saved value;
`ClientOptionsStore` owns strict schema-1 JSON and atomic replacement;
`TrainerClient` composes them with the local profile catalogue; the screen retains
drafts and displays errors/fallbacks. Loading never writes, and failed loads lock
configuration writes for the process. Explicit profile retry after repair also
confirms and saves pending valid choices, including a one-profile catalogue.

Executed verification:

- `scripts/gradle-jdk8.sh --offline test build`: passed; final `test` ran with
  **56 tests, 0 failures/errors/skips**, including 12 new persistence tests.
- `python3 scripts/check-boundaries.py`: passed; **56 production Java files,
  12 packages, 26 directed package dependencies**, no cycles.
- The first sandboxed Gradle launch could not bind its local daemon socket;
  the same offline command subsequently passed outside that sandbox restriction.

No game was launched for M2-A. Actual client exit/relaunch, GUI scales/readability,
profile repair interaction and visible disk-error feedback remain pending in
[the M2-A checklist](testing.md#m2-a-client-options-verification). Earlier M1 game
results do not verify this rebuilt artifact. No further M2 subtasks were started.
