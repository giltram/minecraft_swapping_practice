# Hotbar Swap Trainer

Minecraft **Java 1.16.1**, Fabric, local single-player hotbar/offhand practice.

Use the mod-owned practice arena for training. Ordinary worlds still require the explicit disposable-test marker. Do not use a valuable save for recovery acceptance testing.

## Build

Use JDK 8, the checked-in Gradle 6.5 wrapper and the pinned dependencies. On macOS:

```sh
scripts/gradle-jdk8.sh build
```

The helper chooses JDK 8 independently of a system Java 25 default. Set `TRAINER_JAVA_HOME` to choose a different JDK 8. Apple Silicon client testing uses Intel JDK 8 under Rosetta because the original Minecraft 1.16.1 native libraries are Intel binaries:

```sh
TRAINER_JAVA_HOME=/path/to/intel-jdk8/Contents/Home scripts/gradle-jdk8.sh runClient
```

Build artifacts are in `build/libs/`; use the jar without the `-dev` or `-sources` suffix in an independent 1.16.1 Fabric instance with Fabric API installed. The mod metadata intentionally excludes other Minecraft versions.

Fabric Loader and Fabric API are separate dependencies. Prism's installed Fabric
Loader does not provide the `fabric`, `fabric-networking-v0`,
`fabric-lifecycle-events-v1` or `fabric-command-api-v1` mods. Install Fabric API for
Minecraft 1.16.1 in that instance's `minecraft/mods/` directory. The pinned Maven
`fabric-api-0.18.0+build.387-1.16.1.jar` is only an aggregate marker and does not
bundle its modules: installing that file alone is insufficient. The independent
smoke launcher installs the complete checksum-verified POM dependency set.

## Practice arena (M3-B)

1. On the main menu, click **Enter practice arena / 进入练习场**. First entry
   automatically creates the dedicated save; later entries reuse the same owned
   save after inspection. No download, datapack, commands or command blocks.
2. Wait for preparation/checking to finish. Use **O** for the existing mode,
   Stationary/Moving, profile and task-count controls; **P** starts/stops as before.
3. O → **Return to title / 返回主菜单**, or the pause menu's exit action, stops
   training, confirms original inventory restoration, saves and closes the world.
   It returns to the main menu; another world is never reopened automatically.
4. O → **Reset arena / 重置场地** asks once before discarding placed blocks and
   container contents inside the owned 64 × 64 area. Your inventory is restored
   first. The save and terrain outside this area remain. Reset restores terrain,
   not a general entity cleanup or arbitrary-world rollback.

The initial arena is a fixed Survival/Peaceful flat world: bedrock, two dirt
layers and grass, no structures/decoration/mob spawning, fixed day/clear weather,
and a 64-block world border. Entry recenters you at its safe spawn. Moving still
allows ordinary walking, sprinting and jumping; standing still never invalidates
an answer. Training STOP restores in place, without an arena teleport.

Preparation and reset show progress and disable training until saved checks
finish. Closing the process mid-operation leaves a journal; reopening resumes
only the same owned area. An altered READY arena asks for explicit reset instead
of silently clearing it. Inventory recovery runs before field operations.

On an error, data is kept. For a write failure, check free space/permissions and
Retry. For inventory recovery, use Retry inventory recovery (or P) first; locked
recovery cannot be bypassed by exit/reset. For missing/damaged/unsupported
ownership, inspect the log and restore a complete backup **at the original path**.
Do not edit bindings, move/clone recovery-bearing saves, or delete unknown data
to force acceptance. An unknown same-name directory is not adopted; back it up
and move that unknown directory aside before creating a new arena.

The managed save is `saves/hotbar-swap-trainer-practice`; ownership is independent
of `/swaptrainer enable-test-world`. That older marker never permits generation
or cleanup in an ordinary save. No save is automatically deleted.

## Disposable-world training

1. Create a new ordinary test world with cheats enabled. No map download, datapack or command blocks are needed.
2. Stand safely on dry solid ground, close inventory screens, and run `/swaptrainer enable-test-world`. This creates only a marker in that save's root directory.
3. Press **O** to choose Single Item / Full Inventory, Stationary / Moving, and a custom successful-task count. Use the input labelled **Number of successful training tasks (0=unlimited)**. Empty/out-of-range input disables Start; Enter in the input starts when valid. **Target hints: ON/OFF** shows or hides the target icons, destination and slot rules; timing/progress/countdown/GO stay visible. Accepted choices persist in `config/hotbar-swap-trainer/client-options.json` (defaults: Single Item, Stationary, default profile, 10 successes, hints OFF). A damaged configuration is preserved with an error and saving disabled until repair and restart. Press **P** to start or stop. Both mod keys can be rebound in vanilla Controls.
4. Use your vanilla **Swap Hands** key, number keys and scroll wheel. The trainer observes the vanilla swaps and never solves tasks for you. In Single Item the HUD names the destination; Full Inventory shows one legal target and the selected slot's allowed alternatives.
5. Stop restores all 41 inventory positions and the original selected hotbar slot. Opening options stops first. Opening inventory, pausing or losing focus invalidates the active task; preparation waits for the game to be ready again.

Stationary retains your position while allowing camera rotation. Moving allows
vanilla walking, sprinting and jumping throughout training; stopping never makes
a task invalid and there is no minimum-speed or first-movement requirement. Start
from safe dry ground. Later tasks may start in the air; stopping or completing
restores inventory immediately where you are, without teleporting or clearing
velocity. Normal landing damage may follow restoration. Environment is frozen
for the session; change it from O after stopping. Ordinary marked test saves
receive no terrain preparation; the dedicated arena uses the separate flow above.

Profiles are JSON files in `config/hotbar-swap-trainer/profiles/`. The bundled `default.json` is installed only when missing; corrupt files are preserved and reported. See [domain contracts](domain.md) for the schema. Profiles are frozen for each session.

To select a training layout, open **O → Edit item layout…** and click its name.
The current selection is marked and saved immediately. To change its items, click
the adjacent **Edit → slot**. Click icons in
the searchable item catalogue to select or deselect as many allowed items as you
want. Green marks show selected catalogue items; every selected item/quantity is
displayed above it. Click a selected icon to change its quantity in the left-aligned field beneath
**Item quantity: name**, or use the adjacent **Delete** button to deselect it. Deselecting everything makes that slot empty.
Then **Done → Save layout**. One and many allowed items use the same layout.

**Advanced…** in the picker opens legacy random-empty/leftover rules and full
NBT/matching/default settings. Opening or adding items does not clear existing
settings. Removing custom items or the last item of a special rule asks first.
Main **Save layout** is the only profile write. Reset restores the last opened/
saved values; Cancel/Esc asks before discarding changes. Invalid/raw quantity and
SNBT survives reflow and child returns. Conflict/write failures retain edits and
require fixing the cause then confirming a reload. Saves retain exact backups
and use atomic replacement. Entry/save require local single-player, LAN closed
and completed restoration. Exchange positions is no longer a GUI action.

An `ERROR_LOCKED` recovery message means the backup is retained and interactions remain blocked. Stop can retry recovery; inspect the logs and preserve the save if validation fails. Never remove the recovery NBT to bypass a failure.

## Repository map

- [Architecture and boundaries](architecture.md)
- [Milestone execution record](milestone1-plan.md)
- [Version-specific hook evidence](version-hooks.md)
- [Profiles, tasks and results](domain.md)
- [Client and protocol](client.md)
- [Verification and recovery acceptance](testing.md)
- [Remaining physical input and visual checks](manual-acceptance.md)


## History and personal records (M2)

After stopping and confirmed inventory restoration, open O → PB in local
single-player with LAN closed. The 2×2 table shows Single Item and Full Inventory
with each mode's fastest successful task time for the environment selected in O.
The title labels Stationary or Moving; their PBs are separate. Reopening the page loads the latest saved history.
PB — means no eligible score. Profile configurations, hint settings and requested
session lengths are combined. Invalid/stopped attempts and unknown timing metadata
do not score. Damaged/unreadable history produces an incomplete-history warning.

The HUD shows phase, progress and time. O → Show swap counts optionally adds attempted
and effective swaps; it defaults OFF and persists locally. Selections/mistakes and
mode/profile labels are hidden. Finished-session feedback retains Avg and Best.

Records live in this installation's config/hotbar-swap-trainer/history, partitioned
by player/session UUID across worlds. Restart preserves history/PB. Do not treat
history as an inventory backup. Save failure appears in the trainer feedback/log
and never reverses confirmed inventory restoration. A crash between inventory
restore and history commit can omit that result. No delete/import/export/cloud
feature is provided. Ordinary-world training remains gated on the marked disposable
world requirement and pending manual acceptance.
