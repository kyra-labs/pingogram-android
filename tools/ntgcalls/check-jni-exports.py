#!/usr/bin/env python3
"""Checks that each libntgcalls.so of an unpacked NTgCalls AAR exports exactly its JNI entry points.

The entry points are one `Java_*` symbol per native method of the AAR's classes (classes.jar and libs/*.jar), which the
runtime looks up by name, and `JNI_OnLoad`. tools/ntgcalls/0002-export-only-jni-entry-points.patch makes every other
symbol local; this fails if one is still exported, if a native method has no symbol, or if the relocations are not
packed (DT_ANDROID_REL or DT_ANDROID_RELA).

Usage: check-jni-exports.py <unpacked AAR dir> <readelf>
"""

import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ENTRY_POINTS = {"JNI_OnLoad"}


def mangle(name):
    """A class or method name as the JNI spec mangles it into a symbol."""
    out = []
    for ch in name:
        if ch == "_":
            out.append("_1")
        elif ch == ".":
            out.append("_")
        elif ch.isascii() and ch.isalnum():
            out.append(ch)
        else:
            out.append("_0%04x" % ord(ch))
    return "".join(out)


def native_symbols(jars):
    """The `Java_*` symbol of each native method of the classes in [jars]."""
    symbols = set()
    with tempfile.TemporaryDirectory() as tmp:
        for jar in jars:
            with zipfile.ZipFile(jar) as z:
                z.extractall(tmp)
        classes = [
            os.path.relpath(path, tmp)[: -len(".class")].replace(os.sep, ".")
            for path in glob.glob(os.path.join(tmp, "**", "*.class"), recursive=True)
        ]
        for start in range(0, len(classes), 200):
            listing = subprocess.run(
                ["javap", "-p", "-cp", tmp] + classes[start : start + 200], check=True, capture_output=True, text=True
            ).stdout
            owner = None
            for line in listing.splitlines():
                declared = re.match(r".*\b(?:class|interface|enum) ([\w.$]+).*\{", line)
                if declared:
                    owner = declared.group(1)
                method = re.search(r" native .* ([\w$]+)\(", line)
                if method:
                    symbols.add("Java_%s_%s" % (mangle(owner), mangle(method.group(1))))
    return symbols


def exported(readelf, library):
    """The symbols [library] defines and exports."""
    table = subprocess.run([readelf, "--dyn-syms", "-W", library], check=True, capture_output=True, text=True).stdout
    names = set()
    for line in table.splitlines():
        fields = line.split()
        # Num: Value Size Type Bind Vis Ndx Name; UND is an import, not an export.
        if len(fields) >= 8 and fields[0].endswith(":") and fields[6] != "UND" and fields[4] in ("GLOBAL", "WEAK"):
            names.add(fields[7].split("@")[0])
    return names


def main(aar, readelf):
    jars = [os.path.join(aar, "classes.jar")] + sorted(glob.glob(os.path.join(aar, "libs", "*.jar")))
    natives = native_symbols(jars)
    print("%d native methods in %s" % (len(natives), ", ".join(os.path.relpath(j, aar) for j in jars)))
    failed = False
    for library in sorted(glob.glob(os.path.join(aar, "jni", "*", "libntgcalls.so"))):
        exports = exported(readelf, library)
        extra = sorted(exports - natives - ENTRY_POINTS)
        missing = sorted((natives | ENTRY_POINTS) - exports)
        dynamic = subprocess.run([readelf, "-dW", library], check=True, capture_output=True, text=True).stdout
        # DT_ANDROID_RELA on 64-bit ABIs, DT_ANDROID_REL on armeabi-v7a.
        packed = "ANDROID_REL" in dynamic
        print("%s: %d exports, relocations %s" % (library, len(exports), "packed" if packed else "NOT packed"))
        for name in extra[:20]:
            print("::error::%s exports %s, which is no JNI entry point" % (library, name))
        for name in missing[:20]:
            print("::error::%s does not export %s" % (library, name))
        if not packed:
            print("::error::%s has no packed relocations (DT_ANDROID_REL or DT_ANDROID_RELA)" % library)
        failed = failed or bool(extra) or bool(missing) or not packed
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
