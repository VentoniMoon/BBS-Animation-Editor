# Jolt JNI 6.0.0 → Java 8 compatibility experiment

**Status: partial implementation, NOT verified as a working Jolt engine.** The existing Minecraft / Forge editor remains unchanged.

Upstream pinned dependencies:
- [stephengold/jolt-jni tag 6.0.0](https://github.com/stephengold/jolt-jni/tree/6.0.0), MIT license.
- [Wemppy4/bbs-physics-engine](https://github.com/Wemppy4/bbs-physics-engine), `BoneChainRig` and `PhysicsJoints`.

## Confirmed compatibility obstacle

Upstream `build.gradle` sets `options.release = 11`; its `JoltPhysicsObject` relies on `java.lang.ref.Cleaner`, introduced in Java 9. Existing released binaries cannot run on a Java 8 VM.

## Current component

`Java8Cleaner.java` implements the narrow `Cleaner.create()` / `register(referent, action)` API using a Java 8 `PhantomReference` queue and a daemon. It deliberately delegates atomic exactly-once freeing to the original `JoltPhysicsObject.executeCleanup` routine. **Do not use until native lifetime tests pass.** A cleanup action must not capture its referent.

Adapt upstream `JoltPhysicsObject.java` in the experimental source copy only:

```diff
- import java.lang.ref.Cleaner;
+ // Java8Cleaner resides in this package.
- private static Cleaner cleaner;
+ private static Java8Cleaner cleaner;
- cleaner = Cleaner.create();
+ cleaner = Java8Cleaner.create();
```

`cleaner.register(this, new CleanerRunnable(...))` remains unchanged.

Compile standalone checker without native libraries using JDK 8 `javac`, or JDK 11+ `javac --release 8`:

```sh
javac --release 8 -d out src/main/java/com/github/stephengold/joltjni/Java8Cleaner.java src/test/java/com/github/stephengold/joltjni/Java8CleanerSmoke.java
java -cp out com.github.stephengold.joltjni.Java8CleanerSmoke
```

On JDK 8, remove `--release 8`.

## Still to do before any editor integration

1. Materialize upstream Java sources **at tag 6.0.0**, retaining upstream copyright/license.
2. Compile **all production JNI bindings** with `--release 8`; patch other newer-language constructs/API as detected, do not silently alter JNI native signatures.
3. Verify native Jolt library version and ABI against rebuilt classes for Linux/Windows.
4. Run native smoke tests for allocation, `close()`, GC cleanup race, spring joint, stepping and teardown on real Java 8.
5. Port Wemppy's `BoneChainRig` to the legacy Chameleon bone API and validate matrices/render pose. Keep this behind an opt-in engine switch.
6. Prove playback determinism and baking to `.dat` before enabling by default.

Do not merge this branch on the basis of Java compilation alone. The native tests are required.
