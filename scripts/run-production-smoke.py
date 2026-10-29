#!/usr/bin/env python3
"""Prepare or launch a separate, non-development 1.16.1 Fabric smoke instance.

No Gradle runtime classes, user launcher instances, accounts, or saves are used.
Preparation is the default; --launch explicitly starts the game window.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import shutil
import subprocess
import sys
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run-production"
CACHE = ROOT / ".gradle-home" / "caches"
MODULES = CACHE / "modules-2" / "files-2.1"
LOOM = CACHE / "fabric-loom"
OWNED = "Hotbar Swap Trainer isolated production smoke instance v1"
VERSION = "1.16.1"
CLIENT_SHA1 = "c9abbe8ee4fa490751ca70635340b7cf00db83ff"
LOADER = "net.fabricmc:fabric-loader:0.9.3+build.207"
FABRIC_API = "net.fabricmc.fabric-api:fabric-api:0.18.0+build.387-1.16.1"


def digest(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def checked_copy(source, destination, expected=None, algorithm="sha256"):
    if expected is not None and digest(source, algorithm) != expected:
        raise ValueError("Checksum mismatch: " + str(source))
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.is_file() or digest(destination) != digest(source):
        shutil.copy2(source, destination)
    return destination


def verification_hashes():
    namespace = {"v": "https://schema.gradle.org/dependency-verification"}
    tree = ET.parse(ROOT / "gradle" / "verification-metadata.xml")
    hashes = {}
    for component in tree.findall("v:components/v:component", namespace):
        coordinate = ":".join(component.get(key) for key in ("group", "name", "version"))
        for artifact in component.findall("v:artifact", namespace):
            hashes[(coordinate, artifact.get("name"))] = {
                element.get("value") for element in artifact.findall("v:sha256", namespace)
            }
    return hashes


def cached_module(coordinate, extension="jar"):
    group, name, version = coordinate.split(":")
    candidates = list((MODULES / group / name / version).glob("*/%s-%s.%s" % (name, version, extension)))
    if len(candidates) != 1:
        raise FileNotFoundError("Resolve this pinned artifact with Gradle first: " + coordinate)
    return candidates[0]


def verify_module_artifact(coordinate, source, hashes):
    expected = hashes.get((coordinate, source.name), set())
    if digest(source) not in expected:
        raise ValueError("Artifact is absent from checked-in Gradle verification hashes: " + coordinate)


def module(coordinate, hashes, destination=None):
    source = cached_module(coordinate)
    verify_module_artifact(coordinate, source, hashes)
    if destination is None:
        group, name, version = coordinate.split(":")
        destination = RUN / "libraries" / group.replace(".", "/") / name / version / source.name
    return checked_copy(source, destination)


def install_fabric_api(hashes):
    """The historical aggregate jar is a marker; its pinned POM owns the API module list."""
    pom = cached_module(FABRIC_API, "pom")
    verify_module_artifact(FABRIC_API, pom, hashes)
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    installed = {module(FABRIC_API, hashes, RUN / "mods" / cached_module(FABRIC_API).name).name}
    for dependency in ET.parse(pom).findall("m:dependencies/m:dependency", namespace):
        group, name, version = [dependency.findtext("m:" + field, namespaces=namespace)
                                for field in ("groupId", "artifactId", "version")]
        scope = dependency.findtext("m:scope", "compile", namespace)
        if scope not in ("compile", "runtime"):
            continue
        if (group, name) == ("net.fabricmc", "fabric-loader"):
            # The selected Loader 0.9.3 supersedes the aggregate's older minimum runtime.
            continue
        if group != "net.fabricmc.fabric-api" or not version or any(token in version for token in ("$", "[", "]", "(", ")", "SNAPSHOT")):
            raise ValueError("Review unexpected Fabric API dependency: " + repr((group, name, version)))
        coordinate = ":".join((group, name, version))
        source = cached_module(coordinate)
        installed.add(module(coordinate, hashes, RUN / "mods" / source.name).name)
    required_classes = {
        "net/fabricmc/fabric/api/network/ServerSidePacketRegistry.class",
        "net/fabricmc/fabric/api/network/ClientSidePacketRegistry.class",
        "net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.class",
        "net/fabricmc/fabric/api/client/keybinding/v1/KeyBindingHelper.class",
        "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents.class",
        "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.class",
        "net/fabricmc/fabric/api/command/v1/CommandRegistrationCallback.class",
    }
    for name in installed:
        with zipfile.ZipFile(RUN / "mods" / name) as archive:
            required_classes.difference_update(archive.namelist())
    if required_classes:
        raise ValueError("Missing trainer runtime API classes: " + repr(sorted(required_classes)))
    return installed


def obtain(url, destination, expected, offline):
    if destination.is_file() and digest(destination, "sha1") == expected:
        return destination
    if offline:
        raise FileNotFoundError("Missing pinned offline artifact: " + str(destination))
    if not url.startswith(("https://libraries.minecraft.net/", "https://piston-data.mojang.com/")):
        raise ValueError("Unexpected artifact host: " + url)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(destination.name + ".tmp")
    request = urllib.request.Request(url, headers={"Accept-Encoding": "identity"})
    with urllib.request.urlopen(request, timeout=60) as source, temporary.open("wb") as output:
        shutil.copyfileobj(source, output)
    if digest(temporary, "sha1") != expected:
        raise ValueError("Downloaded artifact checksum mismatch: " + url)
    temporary.replace(destination)
    return destination


def manifest_artifact(entry, offline):
    relative = PurePosixPath(entry["path"])
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("Invalid library path")
    target = RUN / "libraries" / str(relative)
    native_cache = LOOM / "natives/jars" / str(relative)
    if native_cache.is_file() and digest(native_cache, "sha1") == entry["sha1"]:
        return checked_copy(native_cache, target, entry["sha1"], "sha1")
    parts = relative.parts
    group = ".".join(parts[:-3])
    candidates = (MODULES / group / parts[-3] / parts[-2]).glob("*/" + parts[-1])
    for source in candidates:
        if digest(source, "sha1") == entry["sha1"]:
            return checked_copy(source, target, entry["sha1"], "sha1")
    return obtain(entry["url"], target, entry["sha1"], offline)


def allowed(library):
    rules = library.get("rules")
    if rules is None:
        return True
    result = False
    for rule in rules:
        operating_system = rule.get("os", {})
        matches = operating_system.get("name", "osx") == "osx"
        matches &= operating_system.get("arch", "x86_64") in ("x86_64", "amd64")
        matches &= not rule.get("features")
        if matches:
            result = rule["action"] == "allow"
    return result


def extract_natives(jar, target):
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            if entry.is_dir() or name.parts[0] == "META-INF":
                continue
            if name.is_absolute() or ".." in name.parts:
                raise ValueError("Invalid native archive path")
            destination = target / str(name)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(entry))


def remap_smoke(java, hashes):
    source = ROOT / "build" / "smoke" / "hotbar-swap-trainer-smoke.jar"
    if not source.is_file():
        raise FileNotFoundError("Build the separate smoke driver with scripts/gradle-jdk8.sh smokeJar")
    output = RUN / "mods" / "hotbar-swap-trainer-smoke.jar"
    mapping = LOOM / "mappings" / "yarn-1.16.1+build.21-v2.tiny"
    tools = [module("net.fabricmc:tiny-remapper:0.3.0.70", hashes)]
    tools += [module("org.ow2.asm:%s:8.0" % name, hashes)
              for name in ("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util")]
    tools += [module("net.fabricmc:tiny-mappings-parser:0.2.2.14", hashes)]
    named_game = next(LOOM.glob("1.16.1-mapped-*/*.jar"))
    classpath = [named_game, ROOT / "build/libs/hotbar-swap-trainer-0.1.0-dev.jar"]
    classpath += list((ROOT / ".gradle/loom-cache/remapped_mods").glob("*.jar"))
    # The CLI uses named jars only during this isolated transformation. They never enter the game classpath.
    command = [str(java), "-cp", os.pathsep.join(map(str, tools)), "net.fabricmc.tinyremapper.Main",
               str(source), str(output), str(mapping), "named", "intermediary"] + list(map(str, classpath))
    subprocess.run(command, cwd=ROOT, check=True)
    return output


def prepare(arguments):
    marker = RUN / ".hst-production-owned"
    if RUN.exists():
        if not marker.is_file() or marker.read_text().strip() != OWNED:
            raise ValueError("Refusing an existing unowned directory: " + str(RUN))
    else:
        RUN.mkdir()
        marker.write_text(OWNED + "\n")
    (RUN / "mods").mkdir(exist_ok=True)
    metadata = json.loads((LOOM / "minecraft-1.16.1-info.json").read_text())
    if metadata.get("id") != VERSION or metadata["downloads"]["client"]["sha1"] != CLIENT_SHA1:
        raise ValueError("Unexpected Minecraft version metadata")
    hashes = verification_hashes()
    java_home = Path(arguments.java_home or os.environ.get("TRAINER_JAVA_HOME", str(ROOT / ".toolchains/amazon-corretto-8.jdk/Contents/Home")))
    java = java_home / "bin" / "java"
    if not java.is_file():
        raise FileNotFoundError("Supply an Intel JDK8 with --java-home or TRAINER_JAVA_HOME")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    if 'version "1.8.' not in version.stderr:
        raise ValueError("Production smoke requires JDK8: " + version.stderr)
    architecture = subprocess.run(["file", str(java)], capture_output=True, text=True, check=True).stdout
    if "x86_64" not in architecture:
        raise ValueError("Production smoke requires the Intel original-native baseline: " + architecture)

    mod = ROOT / "build/libs/hotbar-swap-trainer-0.1.0.jar"
    with zipfile.ZipFile(mod) as archive:
        if any(name.startswith("dev/hotbarswaptrainer/smoke/") for name in archive.namelist()):
            raise ValueError("Smoke classes leaked into the production mod")
        body = json.loads(archive.read("fabric.mod.json"))
        if body["depends"]["minecraft"] != VERSION:
            raise ValueError("Production mod has an unexpected Minecraft version contract")
    checked_copy(mod, RUN / "mods" / mod.name)
    expected_mods = {mod.name} | install_fabric_api(hashes)
    if arguments.mode != "none":
        remap_smoke(java, hashes)
        expected_mods.add("hotbar-swap-trainer-smoke.jar")
    actual_mods = {path.name for path in (RUN / "mods").glob("*.jar")}
    if actual_mods != expected_mods:
        raise ValueError("Unexpected/stale mod jars in isolated instance: " + repr(actual_mods - expected_mods))

    loader = module(LOADER, hashes)
    with zipfile.ZipFile(loader) as archive:
        installer = json.loads(archive.read("fabric-installer.json"))
    classpath = [loader] + [module(entry["name"], hashes)
                             for entry in installer["libraries"]["common"] + installer["libraries"]["client"]]
    intermediary = LOOM / "mappings/steps/v2-intermediary-1.16.1.jar"
    with zipfile.ZipFile(intermediary) as archive:
        header = archive.read("mappings/mappings.tiny").splitlines()[0]
        if header != b"tiny\t2\t0\tofficial\tintermediary":
            raise ValueError("Unexpected production mapping namespaces")
    classpath.append(checked_copy(intermediary, RUN / "libraries/intermediary-1.16.1-v2.jar"))
    client = RUN / "libraries/minecraft-1.16.1-client.jar"
    cached_client = Path(arguments.official_client) if arguments.official_client else LOOM / "minecraft-1.16.1-client.jar"
    if cached_client.is_file() and digest(cached_client, "sha1") == CLIENT_SHA1:
        checked_copy(cached_client, client, CLIENT_SHA1, "sha1")
    else:
        obtain(metadata["downloads"]["client"]["url"], client, CLIENT_SHA1, arguments.offline)
    classpath.append(client)
    native_dir = RUN / "natives"
    native_dir.mkdir(exist_ok=True)
    native_artifacts = []
    for library in metadata["libraries"]:
        if not allowed(library):
            continue
        downloads = library.get("downloads", {})
        if "artifact" in downloads:
            classpath.append(manifest_artifact(downloads["artifact"], arguments.offline))
        native_classifier = library.get("natives", {}).get("osx")
        if native_classifier:
            entry = downloads["classifiers"][native_classifier]
            native_jar = manifest_artifact(entry, arguments.offline)
            native_artifacts.append({"path": str(native_jar), "sha1": entry["sha1"]})
            extract_natives(native_jar, native_dir)
    classpath = list(dict.fromkeys(classpath))
    assets = LOOM / "assets"
    index = assets / "indexes/1.16.json"
    if not index.is_file() or digest(index, "sha1") != metadata["assetIndex"]["sha1"]:
        raise ValueError("Prepare the verified 1.16 asset index with Gradle before production smoke")
    for entry in json.loads(index.read_text())["objects"].values():
        path = assets / "objects" / entry["hash"][:2] / entry["hash"]
        if not path.is_file() or path.stat().st_size != entry["size"]:
            raise ValueError("Missing 1.16 asset: " + str(path))
    log = metadata["logging"]["client"]["file"]
    logging_config = obtain(log["url"], RUN / "logging" / log["id"], log["sha1"], arguments.offline)
    username = "OfflineSmokePlayer"
    offline_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + username).encode()).digest(), version=3)
    command = [str(java), "-XstartOnFirstThread", "-Xmx2G", "-Dfabric.development=false",
               "-Djava.library.path=" + str(native_dir), "-Dorg.lwjgl.librarypath=" + str(native_dir),
               "-Dlog4j.configurationFile=" + str(logging_config),
               "-Dminecraft.launcher.brand=hotbar-production-smoke", "-Dminecraft.launcher.version=1"]
    if arguments.mode != "none":
        command += ["-Dhotbarswaptrainer.smoke=" + arguments.mode, "-Dhotbarswaptrainer.smoke.exit=true"]
    command += ["-cp", os.pathsep.join(map(str, classpath)), installer["mainClass"]["client"],
                "--username", username, "--uuid", offline_uuid.hex, "--accessToken", "0",
                "--version", VERSION, "--gameDir", str(RUN), "--assetsDir", str(assets),
                "--assetIndex", "1.16", "--userType", "legacy", "--versionType", "release",
                "--width", "1100", "--height", "700"]
    manifest = {"development": False, "workingDirectory": str(RUN), "game": VERSION, "loader": LOADER, "java": version.stderr.strip(),
                "javaArchitecture": architecture.strip(), "mode": arguments.mode,
                "productionMod": {"path": str(mod), "sha256": digest(mod)},
                "mods": [{"path": str(path), "sha256": digest(path)} for path in sorted((RUN / "mods").glob("*.jar"))],
                "classpath": [{"path": str(path), "sha256": digest(path)} for path in classpath],
                "nativeArchives": native_artifacts,
                "natives": [{"path": str(path), "sha256": digest(path)} for path in sorted(native_dir.rglob("*")) if path.is_file()],
                "command": command}
    (RUN / "launch-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (RUN / "launch-command.txt").write_text("cd " + shlex.quote(str(RUN)) + " && " + shlex.join(command) + "\n")
    print("Prepared independent production instance: " + str(RUN))
    print("Production mod SHA256: " + digest(mod))
    print("Game classpath contains only official game/libs and pinned Fabric launcher dependencies.")
    print("Launch with: python3 scripts/run-production-smoke.py --offline --mode " + arguments.mode + " --launch")
    if arguments.launch:
        environment = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            environment.pop(name, None)
        os.chdir(RUN)
        os.execve(str(java), command, environment)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launch", action="store_true", help="Start the client after preparation; default only prepares")
    parser.add_argument("--offline", action="store_true", help="Require all official artifacts in existing project caches")
    parser.add_argument("--java-home", help="Intel JDK8 home; defaults to TRAINER_JAVA_HOME or isolated project toolchain")
    parser.add_argument("--official-client", help="Optional cached unmodified official client jar; its Mojang SHA1 is required")
    acceptance = ["accept-protection", "accept-phase-guards", "accept-lan", "accept-corrupt-check"]
    acceptance += ["accept-corrupt-" + case for case in
                   ("schema", "checksum", "player", "world", "truncated", "snapshot-type", "wrong-type", "inactive")]
    acceptance += ["accept-exit-" + kind + "-" + phase for kind in ("disconnect", "shutdown")
                   for phase in ("preparing", "countdown", "running", "success")]
    acceptance += ["accept-write-" + target for target in ("uuid", "host")]
    acceptance += ["accept-stop-write-" + target for target in ("uuid", "host")]
    acceptance += ["accept-stop-before-clear", "accept-stop-after-clear"]
    parser.add_argument("--mode", choices=["normal", "normal-crash", "crash", "recover", "faults",
                        "cut-backup", "cut-setup", "cut-restored", "cut-cleared", "none"] + acceptance, default="normal")
    arguments = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("This acceptance launcher verifies the macOS Intel-native baseline")
    try:
        prepare(arguments)
    except (ValueError, OSError, subprocess.CalledProcessError) as failure:
        parser.exit(1, str(failure) + "\n")


if __name__ == "__main__":
    main()
