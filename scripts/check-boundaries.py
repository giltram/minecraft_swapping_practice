#!/usr/bin/env python3
"""Check production Java package dependencies without needing a Minecraft build.

This is a source-level guard, not a Java compiler: imports and explicit qualified
references are checked; reflection, annotation descriptor strings, and runtime
behavior still require compilation and integration tests. See docs/architecture.md.
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path


PROJECT = "dev.hotbarswaptrainer"
CLIENT_PACKAGES = (PROJECT + ".client", PROJECT + ".mixin.client")
MEMORY_IO = {
    "java.io.ByteArrayInputStream", "java.io.ByteArrayOutputStream",
    "java.io.DataInputStream", "java.io.DataOutputStream", "java.io.IOException",
}
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+(?:\.\*)?)\s*;", re.MULTILINE)
QUALIFIED = re.compile(r"\b(?:dev\.hotbarswaptrainer|net\.minecraft|net\.fabricmc|java\.io|java\.nio\.file|java\.net)\.[\w.]+")
NON_CODE = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.DOTALL)


def within(name, prefix):
    return name == prefix or name.startswith(prefix + ".")


def mask_comments_and_literals(source):
    return NON_CODE.sub(lambda match: "".join("\n" if char == "\n" else " " for char in match.group()), source)


def read_source(path):
    source = mask_comments_and_literals(path.read_text(encoding="utf-8"))
    declaration = PACKAGE.search(source)
    if declaration is None:
        raise ValueError("Java source has no package declaration")
    package = declaration.group(1)
    references = {}
    for match in IMPORT.finditer(source):
        references[(source.count("\n", 0, match.start(1)) + 1, match.group(1))] = None
    for match in QUALIFIED.finditer(source):
        if declaration.start() <= match.start() < declaration.end():
            continue
        name = match.group().rstrip(".")
        # Import wildcards are already captured, without a second shortened name.
        if source[match.end():].startswith("*"):
            continue
        references[(source.count("\n", 0, match.start()) + 1, name)] = None
    return package, references


def boundary_error(package, class_name, imported):
    is_client = any(within(package, prefix) for prefix in CLIENT_PACKAGES)
    client_dependency = (
        within(imported, "net.minecraft.client")
        or within(imported, "net.fabricmc.fabric.api.client")
        or imported == "net.fabricmc.api.ClientModInitializer"
        or any(within(imported, prefix) for prefix in CLIENT_PACKAGES)
    )
    if not is_client and client_dependency:
        return "common code cannot depend on client code"

    is_task = within(package, PROJECT + ".task")
    is_profile = within(package, PROJECT + ".profile")
    if not is_task and not is_profile:
        return None

    if within(imported, "net.minecraft") and not within(imported, "net.minecraft.nbt"):
        return "domain code may use Minecraft NBT values, not live game/runtime classes"
    if within(imported, "net.fabricmc") or within(imported, "io.netty") or within(imported, "org.lwjgl"):
        return "domain code cannot depend on Fabric, networking, or UI services"

    repository = is_profile and class_name == "ProfileRepository"
    if any(within(imported, prefix) for prefix in ("java.io", "java.nio.file", "java.net")):
        if imported not in MEMORY_IO and (not repository or within(imported, "java.net")):
            return "only ProfileRepository owns profile file I/O; domain code cannot access files or networking"

    if within(imported, PROJECT):
        allowed = (PROJECT + ".task", PROJECT + ".profile") if is_task else (PROJECT + ".profile",)
        if not any(within(imported, prefix) for prefix in allowed):
            return "domain code cannot depend on inventory/session/input/environment/stats/network/UI services"
        if is_task and imported.startswith(PROJECT + ".profile.ProfileRepository"):
            return "task code cannot access the profile file repository"
    return None


def internal_package(imported, package_classes):
    for package in sorted(package_classes, key=len, reverse=True):
        if not imported.startswith(package + "."):
            continue
        tail = imported[len(package) + 1:]
        if tail == "*" or tail.split(".", 1)[0] in package_classes[package]:
            return package
    return None


def find_cycles(graph):
    """Return directed cycles, including indirect ones, with their closing node."""
    state = {}
    stack = []
    cycles = []

    def visit(node):
        state[node] = 1
        stack.append(node)
        for dependency in sorted(graph[node]):
            if state.get(dependency) == 1:
                cycles.append(stack[stack.index(dependency):] + [dependency])
            elif not state.get(dependency):
                visit(dependency)
        stack.pop()
        state[node] = 2

    for node in sorted(graph):
        if not state.get(node):
            visit(node)
    return cycles


def check(source_root):
    paths = sorted(source_root.rglob("*.java"))
    if not paths:
        print("No production Java sources found in " + str(source_root), file=sys.stderr)
        return 1
    sources = {}
    package_classes = defaultdict(set)
    errors = []
    for path in paths:
        try:
            package, references = read_source(path)
            sources[path] = (package, references)
            package_classes[package].add(path.stem)
        except (OSError, ValueError) as failure:
            errors.append(str(path) + ": " + str(failure))

    graph = {package: set() for package in package_classes}
    evidence = {}
    for path, (package, references) in sources.items():
        for (line, imported) in references:
            error = boundary_error(package, path.stem, imported)
            if error:
                errors.append("{}:{}: {}: {}".format(path, line, error, imported))
            dependency = internal_package(imported, package_classes)
            if dependency and dependency != package:
                graph[package].add(dependency)
                evidence.setdefault((package, dependency), "{}:{}".format(path, line))

    for cycle in find_cycles(graph):
        errors.append("Package dependency cycle: " + " -> ".join(cycle))
        for source, target in zip(cycle, cycle[1:]):
            errors.append("  {} imports {} at {}".format(source, target, evidence[(source, target)]))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Architecture boundaries passed: {} Java files, {} packages, {} directed package dependencies".format(
        len(paths), len(graph), sum(len(dependencies) for dependencies in graph.values())))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parent.parent / "src/main/java")
    return check(parser.parse_args().source_root)


if __name__ == "__main__":
    sys.exit(main())
