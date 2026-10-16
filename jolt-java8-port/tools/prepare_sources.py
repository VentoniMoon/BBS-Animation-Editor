#!/usr/bin/env python3
"""Rebuild a *temporary* Java 8 source tree from upstream Jolt-JNI tag 6.0.0.

Preserve upstream sources & license. Keep compatibility changes isolated and
verify expected signatures before editing, so upstream changes fail closed.
"""
import argparse
from pathlib import Path
import shutil

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = args.upstream.resolve()
    src = root / "src/main/java"
    if not (src / "com/github/stephengold/joltjni/JoltPhysicsObject.java").is_file():
        parser.error("Not a Jolt JNI source tree (expected 6.0.0)")
    dest = args.output.resolve()
    if dest == root or root in dest.parents:
        parser.error("output must be outside upstream repository")
    dest.mkdir(parents=True, exist_ok=True)
    dst = dest / "src"
    if dst.exists():
        shutil.rmtree(str(dst))
    shutil.copytree(str(src), str(dst))

    target = dst / "com/github/stephengold/joltjni/JoltPhysicsObject.java"
    data = target.read_text(encoding="utf-8")
    replacements = [
        ("import java.lang.ref.Cleaner;", "// Java8Cleaner lives in this package."),
        ("private static Cleaner cleaner;", "private static Java8Cleaner cleaner;"),
        ("cleaner = Cleaner.create();", "cleaner = Java8Cleaner.create();"),
    ]
    for before, after in replacements:
        if data.count(before) != 1:
            raise RuntimeError("Unexpected Jolt 6.0.0 source; missing or repeated: " + before)
        data = data.replace(before, after, 1)
    target.write_text(data, encoding="utf-8")

    project = Path(__file__).resolve().parent.parent
    shim = project / "src/main/java/com/github/stephengold/joltjni/Java8Cleaner.java"
    shutil.copy2(str(shim), str(dst / "com/github/stephengold/joltjni/Java8Cleaner.java"))
    shutil.copy2(str(root / "LICENSE"), str(dest / "UPSTREAM-LICENSE.txt"))
    print("Prepared", len(list(dst.rglob("*.java"))), "Java source files from Jolt JNI 6.0.0")

if __name__ == "__main__":
    main()
