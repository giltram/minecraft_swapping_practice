# Historical build compatibility

The project targets Minecraft 1.16.1 and Java 8. The Gradle wrapper, Loom version and game dependencies are pinned; the production Java code does not depend on this directory.

## Loom downloader correction

Loom 0.4.33's `DownloadUtil` requests `Accept-Encoding: gzip` but copies response bytes directly to its cache without decompressing them. Mojang's manifest endpoint can return actual gzip bytes, which then fail JSON parsing.

`settings.gradle` loads `LoomCompatibility.groovy` before plugin resolution. The helper:

1. Obtains the [official Loom 0.4.33 jar and POM](https://maven.fabricmc.net/net/fabricmc/fabric-loom/0.4.33/), using an existing Gradle cache entry when available.
2. Checks the complete input hashes and the exact `DownloadUtil.class` hash.
3. Changes one constant-pool UTF8 value, `gzip` to `identity`. Constant-pool indices, instructions and every other jar entry remain unchanged.
4. Produces the local Maven coordinate `net.fabricmc:fabric-loom:0.4.33-hst1`, with the original dependency list and fixed output jar/POM hashes.

Generated files stay under `.gradle/loom-compat/`; the helper never rewrites a shared Gradle cache entry. Jar entries use stored bytes and stable ordering. The source contains all expected input/output hashes, so a different upstream binary or generated result fails explicitly. Offline builds require these inputs or the already verified local outputs.

The plugin request remains `0.4.33`; changing it requires reviewing this helper. This correction does not replace Loom, the mappings, the Fabric API or the original game/native libraries.

## Mercury's Eclipse dependency ranges

Loom's Mercury/JDT dependencies contain open Eclipse version ranges. Resolving those ranges today can select Java 17/21 binaries; the observed failure was `IAdaptable` class version 61 on the Java 8 build JVM.

`build.gradle` fixes the 13 platform artifacts to the matching Eclipse 4.9 release set from [R-4.9-201809060745](https://archive.eclipse.org/eclipse/updates/4.9/R-4.9-201809060745/). Their cached jars were inspected: every class is version 52 or lower. The selected versions must still be confirmed by the resolved Gradle graph and a successful source remap; class-version inspection alone does not prove integration.

## Verification

Use `scripts/gradle-jdk8.sh` to avoid inheriting a system Java 25 default. `TRAINER_JAVA_HOME` selects a specific JDK 8; Apple Silicon's original-native client test uses an Intel JDK 8 under Rosetta.

`scripts/gradle-jdk8.sh resolvedArtifacts` records coordinates, filenames and SHA256s, including snapshot artifacts. [The test record](../docs/testing.md) distinguishes executed build checks from pending game acceptance. Preserve dependency verification metadata and the resolved graph when refreshing this baseline; top-level version pins alone do not freeze snapshot contents.

## Separate Mixin exports for main and smoke

Loom0.4.33 configures the same `MappingsProvider.mappingsMixinExport` for every
non-test source set. The smoke annotation processor has no main @Shadow fields,
so compiling smoke overwrote main's export. A later resource-only build reused
main compilation but remapped without `player→field_14140`; the production JAR
then failed at Mixin application before opening a world. Method refmaps alone
still looked complete and did not detect this field-remapping failure.

`build.gradle` keeps main's export at Loom's expected path, redirects smoke to
`build/loom-cache/smoke-mixin-map.tiny`, declares producer outputs and remap input,
and disables partial Java compilation for these two aggregating Mixin producers.
No dependency, game hook or upstream binary changes. Resource-only rebuilding
must keep `ServerPlayNetworkHandlerMixin`'s actual bytecode field `field_14140`,
not `player`, after compiling smoke. Current acceptance records that regression
check and a real production launch. This is a build-output ownership fix, not
an inventory-recovery implementation change.
