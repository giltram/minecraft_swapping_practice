# Independent production-jar smoke test

`scripts/run-production-smoke.py` prepares a separate ordinary Fabric launch. It does not call Gradle's development `runClient`, and preparation does not open a game window.

```sh
scripts/gradle-jdk8.sh build smokeJar
python3 scripts/run-production-smoke.py --mode normal-crash
python3 scripts/run-production-smoke.py --offline --mode normal-crash --launch
```

The first preparation may download checksum-pinned official Minecraft libraries or the logging configuration. Set `--official-client /path/to/client.jar` to reuse an unmodified official client jar; Mojang's exact 1.16.1 SHA1 is required. Some Loom cache jars are repacked, so the helper does not accept their container checksum as the original download. `--java-home` or `TRAINER_JAVA_HOME` selects Intel JDK8; otherwise the isolated `.toolchains/amazon-corretto-8.jdk/Contents/Home` is used.

The generated `run-production/` directory has an ownership marker. The helper refuses an existing directory without that exact marker and refuses unexpected mod jars. It does not read a user's launcher instances, accounts or saves. The smoke driver creates its own marked `hst-smoke` world there.

For an existing world, the smoke driver verifies the exact `.hst-smoke-owned` marker before requesting `hst-smoke`. Only after that request does it automatically press vanilla's continue-without-backup button on the experimental `BackupPromptScreen`, matching both the screen title and button translation keys. Erase-cache remains disabled; unrelated prompts and saves are not accepted. Normal/crash modes still require genuine game focus for READY and rendered GO.

The production game classpath contains:

- The checksum-verified official 1.16.1 client and macOS libraries selected from the official version manifest.
- Loader 0.9.3+build.207 and the exact runtime dependencies in its `fabric-installer.json`, checked against the repository's Gradle verification hashes.
- Official→intermediary mappings. Loader performs its normal production game remap on first launch.

`build/libs/hotbar-swap-trainer-0.1.0.jar`, the original Fabric API aggregate jar and every API module declared by its checksum-verified POM are copied into `run-production/mods/`. This historical aggregate is a marker jar and does not bundle its modules. The helper verifies every module jar against the repository hashes and checks the trainer's required API classes exist. Neither development classes nor the development mod jar enter the game classpath. The optional smoke mod is remapped from named to intermediary during preparation. Named game classes are used only by this isolated build transformation. The production launch explicitly sets `fabric.development=false` and removes inherited Java-option environment variables.

Native libraries are freshly extracted into the isolated instance from the exact official macOS native jars; the original 3.2.1 LWJGL baseline is retained. No user's Java/native settings are modified. The version manifest's checksum-pinned logging configuration is used. Authentication is an offline test name, deterministic offline UUID and token `0`.

`--mode normal` runs the driver's normal checks. `normal-crash` runs normal checks and then the explicit crash phase; it intentionally exits with the driver's crash status. Reopen with `--mode recover --launch` to verify the persisted active transaction restores the baseline. `--mode none` omits the smoke driver for manual play; use a separate clean prepared instance if a previous smoke jar is present.

Use `--mode faults` for direct inventory-module persistence checks in the same owned world after earlier recovery has completed:

```sh
python3 scripts/run-production-smoke.py --offline --mode faults --launch
```

This mode replays an active record twice, then tests real POSIX write denial for the UUID playerdata and host level.dat directories, once during initial backup and once during the clear-active commit for each path. It checks retained recovery/interaction guards, resets the original permissions in `finally`, retries restoration, and reads both saved inventories/records after each retry. It refuses symlinked world/playerdata paths. The separate smoke mod owns the permission injection and direct inventory calls; production receives no test API or fault toggle. This mode does not start a training session or exercise GO/input timing, and can run without game focus by disabling pause-on-lost-focus in its isolated process. Its report is `run-production/smoke/fault-checks.json`, with raw recovery evidence and `result-faults.json`. These contracts describe the test mechanism; actual passing results belong in [testing.md](testing.md).

The launcher also accepts `--mode cut-backup`, `cut-setup`, `cut-restored` and `cut-cleared`. These prepare direct inventory-module boundaries around confirmed backup, unsaved training setup, restored memory before clear, and the completed clear-active commit. They do not inject into `SessionManager.stop`. See the exact memory/disk/record contracts in [Inventory crash cuts](smoke.md#inventory-crash-cuts). Every cut checks and archives its memory/disk state before halt91; a following `recover` launch must exit0 with a fresh passing result.

With the owned world already created and other test clients stopped, run all four pairs with:

```sh
python3 scripts/run-inventory-cuts.py
```

This runner invokes the production launcher offline, checks actual exit codes and fresh result files, and archives each cut's baseline, raw pre-recovery save files/hashes, prepared-state NBT/report, logs and launch manifests before recovery can overwrite them. Evidence is retained under `run-production/smoke/inventory-cuts/<UTC-run-id>/`; its `report.json` records each pair and only reports `passed` when all pairs finish. The presence of these modes or a prepared-cut `ready` result does not establish an executed recovery pass.

`recover`, `faults` and `cut-*` set pause-on-lost-focus to false on their first client tick, after options exist, and can perform persistence checks in the background. The setting is confined to the isolated test process; the driver does not write an options file here. Normal/crash modes continue to require genuine focus and rendered GO, so these background checks do not replace their handshake/input evidence.

Normal mode sends swaps in one client queue batch and checks frozen success against a trailing swap. Exact server tick IDs are not recorded, so this does not independently prove same-server-tick execution.

Preparation writes `launch-manifest.json` with the production mod hash, all classpath/mod/native hashes, JVM details and exact command; `launch-command.txt` contains the shell-quoted command. Runtime driver evidence is in `run-production/smoke/`. Keep those results distinct from development `run/smoke/` results in [testing.md](testing.md). Preparing a launch or inspecting its classpath does not establish that the client or recovery test passed.

Events append across launches and earlier result files can remain. Confirm the latest boot segment and result modification times, including all five fault-report checks for a `faults` pass, rather than treating file existence as success.

## Remaining M1 acceptance runner

After building `smokeJar` and recovering any prior active transaction:

```sh
python3 scripts/run-remaining-acceptance.py --group all
# Focused groups: protection, phases, exits, corruption, writes.
# Select one or more exact cases with repeated --case arguments.
```

This uses the same independent official client, pinned Fabric installation and
exact owned world. Each case archives its process log, launch manifest, fresh
reports, baseline and raw authoritative/temporary save files with SHA256 before
the next process launches. A new `recover` process must then verify all 41 slots,
selection and cleared recovery in both save paths. Evidence lives under
`smoke/remaining-acceptance/<UTC-run-id>/report.json`; failed runs are retained.

| Group | Mechanism / acceptance boundary |
| --- | --- |
| protection | Actual transformed vanilla packet handlers and entity callbacks on the server thread; passive guards in four phases; real START/stop I/O failures/retries for both save paths; ordinary behavior after release |
| phases | Pre/post-GO swap guards, interruption protocol, actual server screen held through feedback blocking READY, blocked dimension change; real LAN publication stops/restores and rejects restart |
| exits | Actual connection disconnect and client shutdown requested from PREPARING, COUNTDOWN, RUNNING, SUCCESS; separately record the request phase and the real cleanup hook/phase. Vanilla may READY or invalidate while closing its UI; client shutdown normally restores through player removal before the server-stopping hook |
| corruption | Eight fixtures in both saved player copies: schema, checksum, player/world binding, missing snapshot, wrong snapshot type, scalar raw record, inactive flag. Fresh load must lock, preserve exact raw data through a failed stop/save, then recover only from that fixture's archived valid backup |
| writes | Actual compressed UUID/host save writes halted after 32 bytes, during training and actual stop; actual stop before/after confirmed clear. Partial temp files remain archived; each cut requires exit91 and fresh recovery |

The separate required smoke Mixins apply only to production `accept-*` modes.
They add no fault interface to the trainer and are disabled in development and
normal/crash/recover smoke modes. See `src/smoke/README.md` for ownership.

These tests set up phases through the public session interface, supply READY and
GO_ACK, and accelerate countdown through reflection. Exit cases extend the
observation deadline for COUNTDOWN/SUCCESS so ordinary expiry cannot silently
advance the phase. The actual cleanup phase is retained separately, including
vanilla screen interruption during shutdown. This proves lifecycle/guard behavior, not physical
input, normal feedback duration, rendered GO, focus, or subjective GUI quality.
Those retain their separate normal-smoke and human acceptance requirements.

## M2 history and UI integration

```sh
scripts/gradle-jdk8.sh --offline smokeJar
python3 scripts/run-m2-runtime.py --mode accept-history --launch
python3 scripts/run-m2-runtime.py --mode normal-crash --launch
# Expect actual halt91 only after normal passed and crash ready, then:
python3 scripts/run-m2-runtime.py --mode recover --launch
python3 scripts/run-profile-editor-ui.py --history --run-name run-m2-ui-<unique-name> --launch
python3 scripts/run-profile-editor-ui.py --history --reopen --run-name run-m2-ui-<same-name> --launch
```

These wrappers reuse the exact pinned non-development production launcher and
verify/copy only the idle `.hst-smoke-owned` seed from run-layout-ui, preserving
partial archived vanilla temporary files. New instances receive their own exact
ownership marker; existing unknown directories are refused. M2 runtime launch
manifests/results/logs are archived before reuse. UI first-run result files should
be copied to a distinct evidence filename before a deliberate reopen overwrites
the current result. Never run concurrent clients against the same instance.

`accept-history` adds 8 actual lifecycle/filesystem/runtime/notification checks.
The extended UI driver performs programmatic widget input, actual native window
and scale changes, bounded forced Screen-size geometry, saved-profile training
and live entry/save state transitions. A bounded test-only restore latch permits
a RESTORING observation; real directory write denial causes ERROR_LOCKED, followed
by permission restoration and retry. These drivers are absent from the production
JAR and do not prove physical keyboard/mouse input or subjective tooltip quality.
Final artifact-specific outcomes are in [M2 acceptance](m2-acceptance.md).

Independent post-exit M2 readback:

```sh
python3 scripts/verify-m2-saves.py --run-name run-m2-runtime-20261030
python3 scripts/verify-m2-saves.py --run-name run-m2-ui-20261030-errors
```

This read-only wrapper reuses the standard-library typed NBT decoder, checks exact
instance/world ownership, and compares both saved players to the owned runtime
baseline or immutable UI seed, including all 41 positions/selection/UUID/marker.
It must be run after the corresponding client exits. Final normal-rendered
smoke passed with strict immediate comparison after moving test STOP to the client
END-tick connection queue. Actual halt91/recover0 and independent readback also
passed; the previous direct-stop failure is archived separately. Saved-file
readback remains distinct from live/rendered acceptance. See the M2 matrix and
its final normal evidence. Keep the normal test window focused; recovery-only
mode runs without focus. Human physical/subjective checks remain pending.

## M2 configuration usability

The current item-first editor and fixed/unlimited options use the isolated
`--usability` workflow in `scripts/run-profile-editor-ui.py`, with independent
`--reopen` and owned-save readback. This preserves the historical M2-C driver.
The production JAR never includes UI/acceptance classes. See
[m2-multiselect-acceptance.md](m2-multiselect-acceptance.md) for exact commands, final
artifact, screenshots, actual programmatic results and pending human checks.
