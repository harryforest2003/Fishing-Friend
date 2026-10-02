#!/usr/bin/env python3
"""Checks that each built jar links against every Minecraft release it claims to support.

Each jar in build/libs is compiled against one Minecraft version (minecraft_version in
versions/<name>/gradle.properties) but declares a range (mc_range). This script downloads the
client of every release in that range, plus the newest Fabric API for it, and confirms that
everything the jar uses from either still exists with the same name and signature:

  * classes, fields and methods it references (including through lambdas)
  * methods it overrides in Minecraft classes, and abstract methods it must implement
  * mixin targets and @Accessor / @Invoker members

1.21.x clients are remapped to Fabric's intermediary names first (that is what a 1.21.x jar
references at runtime). 26.x ships unobfuscated, so those clients are checked as-is.

Usage: ./gradlew build && python3 scripts/check_compat.py
Needs Python 3.9+ and Java on the PATH. Downloads are cached in build/compat-cache.
"""

import hashlib
import io
import json
import re
import struct
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build" / "compat-cache"
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_MAVEN = "https://maven.fabricmc.net"
TINY_REMAPPER = "0.14.1"


# --- Downloads -------------------------------------------------------------------------------

def download(url, dest, sha1=None):
    if dest.exists() and (sha1 is None or hashlib.sha1(dest.read_bytes()).hexdigest() == sha1):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"  downloading {url}")
    with urllib.request.urlopen(url) as response:
        data = response.read()
    if sha1 is not None and hashlib.sha1(data).hexdigest() != sha1:
        sys.exit(f"checksum mismatch for {url}")
    tmp = dest.with_suffix(".part")
    tmp.write_bytes(data)
    tmp.replace(dest)
    return dest


def release_versions():
    manifest = json.loads(download(MANIFEST_URL, CACHE / "version_manifest_v2.json").read_text())
    return {v["id"]: v for v in manifest["versions"] if v["type"] == "release"}


def client_jar(version, manifest_entry, unobfuscated):
    """The client jar for `version`, in the names a mod jar sees at runtime."""
    meta = json.loads(download(manifest_entry["url"], CACHE / version / "version.json",
                               manifest_entry["sha1"]).read_text())
    client = meta["downloads"]["client"]
    official = download(client["url"], CACHE / version / "client.jar", client["sha1"])
    if unobfuscated:
        return official

    remapped = CACHE / version / "client-intermediary.jar"
    if remapped.exists():
        return remapped
    mappings_jar = download(f"{FABRIC_MAVEN}/net/fabricmc/intermediary/{version}/intermediary-{version}-v2.jar",
                            CACHE / version / "intermediary-v2.jar")
    tiny = CACHE / version / "intermediary.tiny"
    with zipfile.ZipFile(mappings_jar) as z:
        tiny.write_bytes(z.read("mappings/mappings.tiny"))
    remapper = download(f"{FABRIC_MAVEN}/net/fabricmc/tiny-remapper/{TINY_REMAPPER}/tiny-remapper-{TINY_REMAPPER}-fat.jar",
                        CACHE / f"tiny-remapper-{TINY_REMAPPER}-fat.jar")
    print(f"  remapping {version} to intermediary")
    subprocess.run(["java", "-jar", str(remapper), str(official), str(remapped), str(tiny), "official", "intermediary"],
                   check=True, stdout=subprocess.DEVNULL)
    return remapped


def fabric_api_versions():
    metadata = download(f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml",
                        CACHE / "fabric-api-metadata.xml").read_text()
    return re.findall(r"<version>([^<]+)</version>", metadata)


def fabric_api_jar(minecraft, pinned=None, all_versions=None):
    """The Fabric API jar for `minecraft`: `pinned` if given, else the newest build for that version."""
    version = pinned
    if version is None:
        builds = [v for v in all_versions if v.endswith("+" + minecraft)]
        if not builds:
            sys.exit(f"no Fabric API build found for {minecraft}")
        version = max(builds, key=lambda v: tuple(int(n) for n in v.split("+")[0].split(".")))
    return download(f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/{version}/fabric-api-{version}.jar",
                    CACHE / "fabric-api" / f"fabric-api-{version}.jar")


# --- Class file parsing ----------------------------------------------------------------------

class Member:
    def __init__(self, access, name, desc, attributes):
        self.access, self.name, self.desc, self.attributes = access, name, desc, attributes

    @property
    def is_static(self):
        return bool(self.access & 0x0008)

    @property
    def is_private(self):
        return bool(self.access & 0x0002)

    @property
    def is_abstract(self):
        return bool(self.access & 0x0400)


class ClassFile:
    def __init__(self, data):
        self.data, self.pos = data, 8
        count = self.u2()
        self.cp = [None] * count
        i = 1
        while i < count:
            tag = self.u1()
            if tag == 1:
                length = self.u2()
                self.cp[i] = ("Utf8", data[self.pos:self.pos + length].decode("utf-8", "replace"))
                self.pos += length
            elif tag in (3, 4):
                self.cp[i] = ("Const", self.u4())
            elif tag in (5, 6):
                self.cp[i] = ("Const", self.u4(), self.u4())
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                self.cp[i] = ({7: "Class", 8: "String", 16: "MethodType", 19: "Module", 20: "Package"}[tag], self.u2())
            elif tag in (9, 10, 11, 12, 17, 18):
                kind = {9: "Field", 10: "Method", 11: "InterfaceMethod", 12: "NameAndType",
                        17: "Dynamic", 18: "InvokeDynamic"}[tag]
                self.cp[i] = (kind, self.u2(), self.u2())
            elif tag == 15:
                self.cp[i] = ("MethodHandle", self.u1(), self.u2())
            else:
                raise ValueError(f"unknown constant pool tag {tag}")
            i += 1
        self.access = self.u2()
        self.name = self.class_name(self.u2())
        super_index = self.u2()
        self.super = self.class_name(super_index) if super_index else None
        self.interfaces = [self.class_name(self.u2()) for _ in range(self.u2())]
        self.fields = [self.member() for _ in range(self.u2())]
        self.methods = [self.member() for _ in range(self.u2())]
        self.attributes = self.attribute_table()

    def u1(self):
        self.pos += 1
        return self.data[self.pos - 1]

    def u2(self):
        self.pos += 2
        return struct.unpack_from(">H", self.data, self.pos - 2)[0]

    def u4(self):
        self.pos += 4
        return struct.unpack_from(">I", self.data, self.pos - 4)[0]

    def utf8(self, index):
        return self.cp[index][1]

    def class_name(self, index):
        return self.utf8(self.cp[index][1])

    def name_and_type(self, index):
        _, name, desc = self.cp[index]
        return self.utf8(name), self.utf8(desc)

    def member(self):
        access, name, desc = self.u2(), self.utf8(self.u2()), self.utf8(self.u2())
        return Member(access, name, desc, self.attribute_table())

    def attribute_table(self):
        table = {}
        for _ in range(self.u2()):
            name, length = self.utf8(self.u2()), self.u4()
            table[name] = self.data[self.pos:self.pos + length]
            self.pos += length
        return table

    def is_interface(self):
        return bool(self.access & 0x0200)

    def references(self):
        """Yields (kind, owner, name, desc) for every field and method this class refers to."""
        for entry in self.cp:
            if entry and entry[0] in ("Field", "Method", "InterfaceMethod"):
                owner = self.class_name(entry[1])
                name, desc = self.name_and_type(entry[2])
                yield ("field" if entry[0] == "Field" else "method"), owner, name, desc

    def class_references(self):
        for entry in self.cp:
            if entry and entry[0] == "Class":
                name = self.utf8(entry[1]).lstrip("[")
                if name.startswith("L"):
                    name = name[1:-1]
                yield name
        descs = [m.desc for m in self.fields + self.methods]
        descs += [self.name_and_type(e[2])[1] for e in self.cp if e and e[0] in ("Field", "Method", "InterfaceMethod")]
        for desc in descs:
            yield from re.findall(r"L([^;]+);", desc)

    def lambdas(self):
        """Yields (interface, method name, erased descriptor) for each lambda or method reference."""
        raw = self.attributes.get("BootstrapMethods")
        if raw is None:
            return
        count = struct.unpack_from(">H", raw, 0)[0]
        pos, bootstraps = 2, []
        for _ in range(count):
            ref, argc = struct.unpack_from(">HH", raw, pos)
            pos += 4
            bootstraps.append((ref, struct.unpack_from(f">{argc}H", raw, pos)))
            pos += 2 * argc
        for entry in self.cp:
            if not entry or entry[0] != "InvokeDynamic":
                continue
            ref, args = bootstraps[entry[1]]
            handle = self.cp[self.cp[ref][2]]
            if self.class_name(handle[1]) != "java/lang/invoke/LambdaMetafactory":
                continue
            name, desc = self.name_and_type(entry[2])
            interface = desc[desc.index(")") + 2:-1]
            yield interface, name, self.utf8(self.cp[args[0]][1])

    def annotations(self, attributes):
        """Parses annotations into {type descriptor: {element: value}} (values: str, list or dict)."""
        result = {}
        for key in ("RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations"):
            raw = attributes.get(key)
            if raw is None:
                continue
            reader = _Reader(raw)
            for _ in range(reader.u2()):
                desc, values = self._annotation(reader)
                result[desc] = values
        return result

    def _annotation(self, reader):
        desc = self.utf8(reader.u2())
        values = {}
        for _ in range(reader.u2()):
            name = self.utf8(reader.u2())
            values[name] = self._element(reader)
        return desc, values

    def _element(self, reader):
        tag = chr(reader.u1())
        if tag in "BCDFIJSZ":
            reader.u2()
            return None
        if tag in "sc":
            return self.utf8(reader.u2())
        if tag == "e":
            reader.u2()
            return self.utf8(reader.u2())
        if tag == "@":
            return self._annotation(reader)[1]
        if tag == "[":
            return [self._element(reader) for _ in range(reader.u2())]
        raise ValueError(f"unknown annotation element tag {tag}")


class _Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def u1(self):
        self.pos += 1
        return self.data[self.pos - 1]

    def u2(self):
        self.pos += 2
        return struct.unpack_from(">H", self.data, self.pos - 2)[0]


class Jar:
    """The classes of one or more jars. Jars nested under META-INF/jars (as in Fabric API) are included."""

    def __init__(self, *paths):
        self.sources = {}
        self.cache = {}
        for path in paths:
            self._add(zipfile.ZipFile(path))
        self.names = set(self.sources)

    def _add(self, archive):
        for entry in archive.namelist():
            if entry.endswith(".class"):
                self.sources.setdefault(entry[:-6], archive)
            elif entry.startswith("META-INF/jars/") and entry.endswith(".jar"):
                self._add(zipfile.ZipFile(io.BytesIO(archive.read(entry))))

    def get(self, name):
        if name not in self.cache:
            archive = self.sources.get(name)
            self.cache[name] = ClassFile(archive.read(name + ".class")) if archive else None
        return self.cache[name]

    def supertypes(self, name, include_self=True):
        """All classes and interfaces above `name` that are in this jar, plus whether the chain leaves the jar."""
        seen, queue, leaves = [], [name], False
        while queue:
            current = queue.pop(0)
            if current in seen:
                continue
            cls = self.get(current)
            if cls is None:
                leaves = leaves or current != name
                continue
            seen.append(current)
            queue.extend(([cls.super] if cls.super else []) + cls.interfaces)
        if not include_self and seen and seen[0] == name:
            seen = seen[1:]
        return seen, leaves

    def resolve(self, kind, owner, name, desc):
        """Where `owner.name desc` is declared: a class name, "external" (outside this jar), or None."""
        if name == "<init>":
            cls = self.get(owner)
            return owner if cls and any(m.name == name and m.desc == desc for m in cls.methods) else None
        classes, leaves = self.supertypes(owner)
        for current in classes:
            members = self.get(current).fields if kind == "field" else self.get(current).methods
            if any(m.name == name and m.desc == desc for m in members):
                return current
        return "external" if leaves else None


# --- The check -------------------------------------------------------------------------------

def check(mod, baseline, target):
    """Returns a list of problems running `mod` (built against `baseline`) on `target`."""
    problems = set()
    mod_classes = {name: mod.get(name) for name in mod.names}

    def owned(name):
        return baseline.get(name) is not None

    for cls in mod_classes.values():
        for ref in set(cls.class_references()):
            if owned(ref) and target.get(ref) is None:
                problems.add(f"missing class {ref} (used by {cls.name})")

        for kind, owner, name, desc in set(cls.references()):
            if not owned(owner):
                continue
            expected = baseline.resolve(kind, owner, name, desc)
            if expected is None:
                continue
            found = target.resolve(kind, owner, name, desc)
            if found is None or (found == "external" and expected != "external"):
                problems.add(f"missing {kind} {owner}.{name}{desc} (used by {cls.name})")

        for interface, name, desc in cls.lambdas():
            if owned(interface) and target.resolve("method", interface, name, desc) is None:
                problems.add(f"lambda target {interface}.{name}{desc} is gone (used by {cls.name})")

        problems.update(check_overrides(cls, baseline, target, owned))
        problems.update(check_mixin(cls, target, owned))
    return sorted(problems)


def check_overrides(cls, baseline, target, owned):
    supers = [s for s in [cls.super] + cls.interfaces if s and owned(s)]
    if not supers:
        return []
    problems = []
    for method in cls.methods:
        if method.is_static or method.is_private or method.name.startswith("<"):
            continue
        overrides = any(baseline.resolve("method", s, method.name, method.desc) not in (None, "external") for s in supers)
        if overrides and all(target.resolve("method", s, method.name, method.desc) in (None, "external") for s in supers):
            problems.append(f"{cls.name}.{method.name}{method.desc} no longer overrides anything")

    implemented = {(m.name, m.desc) for m in cls.methods if not m.is_abstract}
    abstract = {}
    for s in supers:
        for name in target.supertypes(s)[0]:
            for m in target.get(name).methods:
                key = (m.name, m.desc)
                if m.is_abstract:
                    abstract.setdefault(key, name)
                elif not m.is_static:
                    implemented.add(key)
    if not cls.is_interface() and not cls.access & 0x0400:
        for key, owner in abstract.items():
            if key not in implemented:
                problems.append(f"{cls.name} does not implement {owner}.{key[0]}{key[1]}")
    return problems


def check_mixin(cls, target, owned):
    annotations = cls.annotations(cls.attributes)
    mixin = annotations.get("Lorg/spongepowered/asm/mixin/Mixin;")
    if mixin is None:
        return []
    targets = [t[1:-1] for t in mixin.get("value", [])] + [t.replace(".", "/") for t in mixin.get("targets", [])]
    problems = [f"mixin target {t} is gone" for t in targets if target.get(t) is None]
    for method in cls.methods:
        method_annotations = cls.annotations(method.attributes)
        accessor = method_annotations.get("Lorg/spongepowered/asm/mixin/gen/Accessor;")
        invoker = method_annotations.get("Lorg/spongepowered/asm/mixin/gen/Invoker;")
        for t in targets:
            if accessor is not None:
                field = accessor.get("value")
                field_type = method.desc[method.desc.index(")") + 1:]
                if field_type == "V":
                    field_type = method.desc[1:method.desc.index(")")]
                if field and target.resolve("field", t, field, field_type) is None:
                    problems.append(f"accessor target {t}.{field}:{field_type} is gone")
            if invoker is not None:
                name = invoker.get("value")
                if name and target.resolve("method", t, name, method.desc) is None:
                    problems.append(f"invoker target {t}.{name}{method.desc} is gone")
    return problems


# --- Version ranges --------------------------------------------------------------------------

def version_key(version):
    return tuple(int(part) for part in version.split("."))


def in_range(version, spec):
    for clause in spec.split():
        match = re.fullmatch(r"(>=|<=|>|<|=)?(\d+(?:\.\d+)*)-?", clause)
        if not match:
            sys.exit(f"cannot parse version range clause '{clause}'")
        op, bound = match.group(1) or "=", version_key(match.group(2))
        v = version_key(version)
        ok = {">=": v >= bound, "<=": v <= bound, ">": v > bound, "<": v < bound, "=": v == bound}[op]
        if not ok:
            return False
    return True


def read_properties(path):
    props = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def main():
    root_props = read_properties(ROOT / "gradle.properties")
    releases = release_versions()
    fabric_api_builds = fabric_api_versions()
    failed = False

    for version_dir in sorted((ROOT / "versions").iterdir()):
        props = read_properties(version_dir / "gradle.properties")
        unobfuscated = props["unobfuscated"] == "true"
        jar_path = ROOT / "build" / "libs" / f"{root_props['archives_base_name']}-{root_props['mod_version']}+mc{props['mc_label']}.jar"
        if not jar_path.exists():
            sys.exit(f"{jar_path.name} not found, run ./gradlew build first")

        compiled_against = props["minecraft_version"]
        targets = sorted((v for v in releases if re.fullmatch(r"\d+(\.\d+)+", v) and in_range(v, props["mc_range"])),
                         key=version_key)
        print(f"{jar_path.name}: built against {compiled_against}, claims {props['mc_range']}")
        if compiled_against not in targets:
            print(f"  FAIL: {compiled_against} is outside the claimed range")
            failed = True

        mod = Jar(jar_path)
        baseline = Jar(client_jar(compiled_against, releases[compiled_against], unobfuscated),
                       fabric_api_jar(compiled_against, pinned=props["fabric_api_version"]))
        for version in targets:
            target = Jar(client_jar(version, releases[version], unobfuscated),
                         fabric_api_jar(version, all_versions=fabric_api_builds))
            problems = check(mod, baseline, target)
            if problems:
                failed = True
                print(f"  {version}: FAIL")
                for problem in problems:
                    print(f"      {problem}")
            else:
                print(f"  {version}: ok")

    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
