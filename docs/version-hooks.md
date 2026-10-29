# Minecraft 1.16.1 hook audit

Audit date: 2026-10-28. This document records **source/bytecode inspection**, not a
successful game launch or recovery fault-injection test. Runtime acceptance is tracked
in [testing.md](testing.md).

## Evidence and reproduction

The audit uses the exact official Minecraft 1.16.1 client bytecode, Fabric
intermediary mappings for 1.16.1, and Yarn `1.16.1+build.21:v2`. CFR 0.152 was used
to inspect method bodies. Minecraft sources are generated locally and are not
redistributed in this repository.

| Artifact | SHA-256 |
| --- | --- |
| Official `client.jar` | `b4831e7b63b10588ff06ea86322108d916c536446f3aea3ae988b7fe3e1f66ef` |
| Yarn v2 JAR | `cf3cc87c146042767d0d46191c9cd6bb47a22404956726fcb851c0acb851fa0b` |
| Intermediary v2 JAR | `1daffb2da1503d79ab3f9ea3cf258ec5045cc41204d2a1151321e26652f3c654` |
| CFR 0.152 JAR | `f686e8f3ded377d7bc87d216a90e9e9512df4156e75b06c655a16648ae8765b2` |

Official references: [version metadata](https://piston-meta.mojang.com/v1/packages/54fa3af57d041d2771e66d390197b2c0288e697c/1.16.1.json),
[client bytecode](https://piston-data.mojang.com/v1/objects/c9abbe8ee4fa490751ca70635340b7cf00db83ff/client.jar),
[intermediary](https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.1/intermediary-1.16.1-v2.jar),
[Yarn](https://maven.fabricmc.net/net/fabricmc/yarn/1.16.1+build.21/yarn-1.16.1+build.21-v2.jar).

Use the repository's JDK 8 Gradle launcher with `genSources` for named source
inspection. To independently inspect bytecode, use `javap -p -c` or CFR against the
official client JAR, and translate official names through intermediary to Yarn.
Relevant classes:

| Yarn class | Official name | Intermediary |
| --- | --- | --- |
| `ServerPlayNetworkHandler` | `zv` | `class_3244` |
| `ServerPlayerEntity` | `ze` | `class_3222` |
| `PlayerEntity` | `bec` | `class_1657` |
| `PlayerInventory` | `beb` | `class_1661` |
| `PlayerManager` | `abp` | `class_3324` |
| `IntegratedPlayerManager` | `eoy` | `class_1130` |
| `WorldSaveHandler` | `dai` | `class_29` |
| `LevelStorage.Session` | `dae$a` | `class_32$class_5143` |
| `LevelProperties` | `daj` | `class_31` |
| `MinecraftClient` | `dlx` | `class_310` |
| `ItemEntity` | `bbg` | `class_1542` |
| `ExperienceOrbEntity` | `aos` | `class_1303` |
| `PersistentProjectileEntity` | `beg` | `class_1665` |
| `TridentEntity` | `bfe` | `class_1685` |
| `HungerManager` | `bge` | `class_1702` |

## Main-thread packet boundary

The inspected `ServerPlayNetworkHandler` gameplay handlers begin by invoking:

```text
NetworkThreadUtils.forceMainThread(
  Packet, PacketListener, ServerWorld
)V
```

Exact Mixin INVOKE descriptor:

```text
Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V
```

Inject with `shift = At.Shift.AFTER` for guards or before-state capture. On the
Netty call the utility schedules execution on the server thread and throws the
vanilla off-thread exception; that invocation does not reach this injection.
Injecting at an unconditional handler HEAD would observe the packet twice or
mutate state on the wrong thread. Required injections must use `require = 1`
(or the Mixin config's corresponding `defaultRequire`) so version drift fails
explicitly.

### Swap and selected slot

`onPlayerAction(PlayerActionC2SPacket)` is intermediary `method_12066`, official
`zv.a(ry)`. After thread scheduling and updating the action timestamp, its
`SWAP_ITEM_WITH_OFFHAND` branch:

1. Rejects spectators by skipping the swap body.
2. Reads the old offhand stack.
3. Places the current main-hand stack into the offhand.
4. Places the old offhand stack into the main hand.
5. Calls `clearActiveItem()` and returns immediately.

Capture before-state after the thread boundary; observe at RETURN, only for an
accepted SWAP while the session is running. The two original `setStackInHand`
calls must remain untouched. RETURN observes each swap separately, including
multiple swaps in one tick. Freeze a completed task before another packet can
process; an end-tick poll alone can miss the completed intermediate state.

Other action branches handle `DROP_ITEM`, `DROP_ALL_ITEMS`, release-use, and
block-breaking actions. Guard them before their original branch can run while
a recovery transaction owns the player's inventory.

`onUpdateSelectedSlot(UpdateSelectedSlotC2SPacket)` is intermediary
`method_12056`, official `zv.a(sh)`. It validates the index against the hotbar size
before writing `player.inventory.selectedSlot`. Capture before-state after the
thread boundary and compare at RETURN. An invalid index or repeated selection
does not count as a slot change. Setup changes do not enter this packet path.

### Transfer and interaction guards

The following exact Yarn handlers were inspected and invoke the thread utility
above before gameplay changes. Reject the action and resynchronize inventory
for the protected player; record invalidity before evaluating success.

| Handler | Intermediary | Official packet |
| --- | --- | --- |
| `onClickWindow(ClickWindowC2SPacket)` | `method_12076` | `rj` |
| `onCreativeInventoryAction(CreativeInventoryActionC2SPacket)` | `method_12070` | `sk` |
| `onPickFromInventory(PickFromInventoryC2SPacket)` | `method_12084` | `rv` |
| `onCraftRequest(CraftRequestC2SPacket)` | `method_12061` | `rw` |
| `onButtonClick(ButtonClickC2SPacket)` | `method_12055` | `ri` |
| `onBookUpdate(BookUpdateC2SPacket)` | `method_12053` | `rm` |
| `onPlayerInteractItem(PlayerInteractItemC2SPacket)` | `method_12065` | `sr` |
| `onPlayerInteractBlock(PlayerInteractBlockC2SPacket)` | `method_12046` | `sq` |
| `onPlayerInteractEntity(PlayerInteractEntityC2SPacket)` | `method_12062` | `ro` |

Book updates mutate held-item NBT directly and must not be forgotten just because
ordinary right-click item use is blocked. Other screen-specific operations
(merchant selection, beacon update, rename) cannot be reached from a valid
normal-player handler, but starting with a foreign screen handler must be
rejected.

`onGameMessage(ChatMessageC2SPacket)` (`method_12048`, official `zv.a(rd)`) also
crosses this same thread boundary. Vanilla normalizes whitespace with Apache
`StringUtils.normalizeSpace`, then dispatches strings beginning with `/` to the
command manager. The mod applies that same normalization and blocks commands
while protected, preventing `/give`, `/clear`, `/kill`, or `/teleport` from
bypassing gameplay packet guards. Ordinary chat text retains its vanilla path.

## Complete inventory synchronization

`ServerPlayerEntity.openHandledScreen(ScreenHandler)` (`method_14204`) calls the
screen listener with all stacks. The corresponding `onContentsChanged` sends:

- `InventoryS2CPacket(handler.syncId, handler.getStacks())`.
- `ScreenHandlerSlotUpdateS2CPacket(-1, -1, inventory.getCursorStack())`.

Use the normal player screen handler's stack order, not a raw concatenation of
the three `PlayerInventory` lists. Explicitly send
`HeldItemChangeS2CPacket(selectedSlot)` for the selected hotbar index. This version
does **not** call that packet `UpdateSelectedSlotS2CPacket`.

`sendContentUpdates()` alone compares against the handler's cached stacks;
`markDirty()` alone is not a complete sync contract. The client preparation ACK
must independently verify the received inventory and selection.

## Player NBT and recovery ordering

`PlayerEntity.readCustomDataFromTag(CompoundTag)` (`method_5749`, official
`bec.a(le)`) deserializes `Inventory`, then assigns `SelectedItemSlot`, then loads
other player state. `ServerPlayerEntity` overrides the method (`ze.a(le)`), calls
super first, and subsequently loads game mode, credits, recipes, and spawn
metadata. It does not overwrite inventory after super.

`PlayerEntity.writeCustomDataToTag(CompoundTag)` (`method_5652`, official
`bec.b(le)`) serializes `Inventory` and `SelectedItemSlot` into the player compound.
`ServerPlayerEntity` overrides it (`ze.b(le)`), calls super first, then appends its
server-specific fields.

Recommended narrow ownership: attach the recovery holder to `ServerPlayerEntity`;
read its namespaced field at the server override's TAIL and append its copy at
the server write override's TAIL. These methods are genuinely declared in the
server class despite being named through inherited mappings. Both host-level
and UUID-player serializers use `Entity.toTag`, which invokes this same virtual
write method. The recovery field therefore accompanies whichever inventory
the player compound contains.

The read hook may capture a pending recovery record before network initialization.
Perform confirmed recovery after the player's data has loaded and before allowing
another game-thread action (for example at the controlled `onPlayerConnect`
boundary). Never send inventory packets through a not-yet-created network handler
from the raw NBT read callback. Damaged/unknown recovery records must retain their
raw data and keep the player locked.

## Integrated host save paths and acknowledgment

This section is a hard requirement for recovery, not an implementation detail.

### Load precedence

`PlayerManager.loadPlayerData(ServerPlayerEntity)` (`method_14600`, official
`abp.a(ze)`) reads `server.saveProperties.getPlayerData()` first. If the player
name equals the single-player owner's name and this compound is non-null, it
passes that compound to `player.fromTag`. Otherwise it delegates to
`WorldSaveHandler.loadPlayerData(PlayerEntity)` (`method_261`, official `dai.b(bec)`).

Thus for the integrated owner, **`level.dat` → `Data` → `Player` takes precedence
over `playerdata/<uuid>.dat`**. Writing only the UUID file cannot establish a
durable host recovery transaction.

`LevelStorage` tries `level.dat` first and `level.dat_old` if the first world
record cannot be read. `WorldSaveHandler`'s UUID loader reads `<uuid>.dat` and
does not itself fall back to `<uuid>.dat_old` in this version.

### Save behavior

`IntegratedPlayerManager.savePlayerData(ServerPlayerEntity)` overrides the
protected base method (`method_14577`, official `eoy.b(ze)`). For the owner, it
first stores `player.toTag(new CompoundTag())` in its private user-data cache;
then it calls the base save method, which writes the UUID player file.
`IntegratedPlayerManager.getUserData()` (`method_14567`, official `eoy.q()`) returns
that cached host compound.

`MinecraftServer.save(boolean, boolean, boolean)` (`method_3723`) saves worlds and
then calls `LevelStorage.Session`'s world-data writer with
`playerManager.getUserData()` as the host `Player` compound.

Both file writers create a temporary compressed-NBT file and use vanilla's
backup-and-replace utility. However:

- `WorldSaveHandler.savePlayerData` catches `Exception`, logs, and returns `void`.
- `LevelStorage.Session`'s world-data writer also catches `Exception`, logs, and
  returns `void`.
- `MinecraftServer.save(...)`'s boolean reflects iteration over worlds; it is
  **not a successful-persistence acknowledgment**.

### Confirmed transaction commits

The simplest verified integration route is:

1. Execute on the server thread with inventory interactions locked.
2. Set the desired recovery field state before player serialization.
3. Invoke `playerManager.saveAllPlayerData()` (`method_14617`), which also refreshes
   the integrated owner's cached compound.
4. Invoke the server world save so the refreshed compound reaches `level.dat`.
5. Read the compressed NBT back from `server.getSavePath(WorldSavePath.LEVEL_DAT)`
   and `server.getSavePath(WorldSavePath.PLAYERDATA)/<uuid>.dat`.
6. Confirm player identity, the complete 41-position inventory and selected slot,
   and the expected recovery transaction/active state in both authoritative
   candidates. A missing file, read error, mismatched fingerprint, or stale
   transaction is failure.

During backup this confirmation must finish before the original inventory is
replaced. During restore, replace the whole inventory from the snapshot, verify
it, serialize the cleared record state, and confirm that state before unlocking.
If confirmation fails, retain the recovery record and stay locked. A later retry
must be a complete replacement, never per-item giving or merging.

Readback confirms process-visible persistence and detects vanilla's swallowed
I/O failures. It does not establish an fsync/power-loss guarantee or protect
against artificial rollback of inconsistent world files. Keep ordinary-world
training disabled until the crash matrix has actually run.

### Normal exit boundaries

`PlayerManager.remove(ServerPlayerEntity)` (`method_14611`, official `abp.c(ze)`)
saves player data before removing the entity from its world and manager lists.
A HEAD hook can restore while the player is still managed. The network handler's
`onDisconnected(Text)` calls `PlayerManager.remove` and then requests integrated
server stop for the owner.

Fabric lifecycle API `0.18.0+build.387-1.16.1` emits `SERVER_STOPPING` at the HEAD of
`MinecraftServer.shutdown()`. Vanilla then closes networking, saves players,
disconnects them, and saves worlds. Therefore the lifecycle callback is early
enough to attempt restoration. A process kill cannot execute it; persistent NBT
recovery remains required.

## Passive protection hooks

These are declared overrides with inspected method bodies:

| Hook | Intended boundary |
| --- | --- |
| `ItemEntity.onPlayerCollision(PlayerEntity)` | Cancel at HEAD for the protected player before `inventory.insertStack` and pickup side effects. |
| `ExperienceOrbEntity.onPlayerCollision(PlayerEntity)` | Cancel at HEAD before Mending changes stacks or experience changes. |
| `PersistentProjectileEntity.onPlayerCollision(PlayerEntity)` | Cancel at HEAD before arrow/trident insertion and projectile removal; TridentEntity's override delegates to this method. |
| `ServerPlayerEntity.damage(DamageSource, float)` | Return `false` at HEAD for a protected player before health/armor changes. |
| `HungerManager.update(PlayerEntity)` | Cancel at HEAD for protected player before exhaustion consumption, saturation loss, food change, natural regeneration, or starvation. |
| `PlayerEntity.addExhaustion(float)` | Cancel at HEAD to stop accumulation while movement is frozen. |
| `PlayerEntity.canPickUp(ItemStack)` | Return false at HEAD: the armor-dispenser eligibility predicate uses this virtual method before splitting and equipping its stack. The dispenser then falls back to ordinary item dispensing. |
| `PlayerEntity.tickMovement()` → `HungerManager.setFoodLevel(int)` | Gate this one invocation: Peaceful food regeneration bypasses HungerManager.update. |
| `LivingEntity.heal(float)` | Cancel at HEAD to freeze natural and effect-based healing. |
| `LivingEntity.tickStatusEffects()` | Cancel at HEAD to freeze existing effect timers and effects that directly modify health/food. |

The inspected `tickMovement` body contains one direct food-level setter in its
Peaceful branch and calls `heal` for Peaceful healing. `tickStatusEffects` belongs
to LivingEntity and drives ongoing effects; freezing it also preserves their
remaining duration during stationary training. These guards need actual game
tests; inspecting them does not by itself establish protection against other
mods, commands, or new effects applied by world entities. Protection must cover
BACKING_UP/PREPARING through RESTORING/ERROR_LOCKED, not only RUNNING.

## Client swap queue and screen boundary

`MinecraftClient.handleInputEvents()` (`method_1508`, official `dlx.aL()`) contains
the original `while (options.swapHandsKey.wasPressed())` loop. Its SWAP body sends
a `PlayerActionC2SPacket`; it does not locally exchange inventory stacks.

A redirect scoped to `handleInputEvents` can gate this exact invocation:

```text
Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V
```

Filter only the SWAP action and forward other packets normally. Vanilla still
consumes its own press queue; the mod never calls `swapHandsKey.wasPressed()`.
Cancelling the whole input handler can leave old presses queued until GO and is
therefore unsuitable for this handshake. The GO-render callback must queue
`GO_ACK` before enabling this packet path. Transport ordering is only useful if
all callbacks are marshalled onto the same game-thread execution sequence.

`MinecraftClient.openScreen(Screen)` (`method_1507`, official `dlx.a(dqs)`) is a
local entry point that can invalidate/stop training immediately on opening an
inventory or pause screen. Opening a screen need not send any transfer packet,
so server packet guards cannot detect the local action by themselves. Window
focus must also be monitored independently of whether a screen is open.

## Prediction corrections and dimension boundary added during integration

`onPlayerInteractBlock(PlayerInteractBlockC2SPacket)` normally sends
`BlockUpdateS2CPacket(world, hit)` and `BlockUpdateS2CPacket(world, hit.offset(side))`
after handling the interaction. Canceling at the thread boundary must retain these
two corrections. Rejected dig actions also send `PlayerActionResponseS2CPacket`
with the authoritative block state and a block update, so client prediction is
undone even though the server never performs the operation.

`ClientPlayerInteractionManager.interactItem(PlayerEntity, World, Hand)` and
`interactBlock(ClientPlayerEntity, ClientWorld, Hand, BlockHitResult)` return
`ActionResult`. `BucketItem.placeFluid` can call `World.setBlockState` on the
client during prediction. A narrow client HEAD guard returns FAIL before these
use paths and reports FORBIDDEN; the independent server guards remain necessary.
No vanilla swap press queue is read by this guard.

`ServerPlayerEntity.changeDimension(ServerWorld)` returns `Entity`. Its body can
create destination portal/platform blocks. A HEAD guard returns the same player
while a recovery transaction is active and records the forbidden action before
any destination generation. The start gate rejects a player currently standing
in a Nether/End portal; StationaryEnvironment also retains its original
ServerWorld as a defensive invariant. Checking dimension only after transition
would be too late to prevent terrain effects.
