# Jolt / XPBD / Ragdoll — installable JAR integrity

## Root cause of the missing physics in a previous test build

The early bone tools workflow compiled the new editor **without**
`-PenableJoltExperimental`, then patched `com.example.examplemod`
classes into the **old v0.0.2 release JAR**. Checking for a few JNI
classes alone could not establish that the hair/ragdoll prototype was
the revision required by the current source. Moreover, the three main
activation flags defaulted to `false` in the ordinary packaged game.

The revised workflow creates fresh Java 8 compatible Jolt 6.0.0
bindings and the **current checkout** physics prototype. It bundles
them directly with the full Forge build, verifies Linux JNI and Windows
DLL resources, checks the native ABI on real Java 8, and checks the
presence of all XPBD/Ragdoll/assistant/editor classes. Only on success
does it add the release marker:

`META-INF/bbsanimationeditor-verified-jolt6-java8.marker`

`EditorPhysicsRuntimeDefaults` uses this marker as a release-only
default. Without it, old unbundled JARs and development runClient
retain the previous opt-in behavior.

## Explicit runtime switches

All three features are enabled by default **only in the complete,
verified test JAR**, subject to each actor/body part's own settings:

- `bbs.editor.physics.jolt.preview` — Jolt hair and dynamic Body Parts.
- `bbs.editor.physics.skirt.xpbd` — XPBD skirt simulation; this uses
  the Java 8 cloth solver and must not step the 20 old Jolt skirt
  chains simultaneously.
- `bbs.editor.physics.jolt.ragdoll` — native Emoticons Ragdoll bridge;
  actual ragdoll motion still depends on actor PHYSICS keys/weight.

Any feature can be disabled with `-D<exact property>=false` or
manually enabled with `-D<exact property>=true`.

For an ordinary developer run, original switches still apply:
`-PenableJoltExperimental`, `-PenableSkirtXPBD`,
`-PskirtXPBDVolumeSkin`, `-PenableJoltRagdoll`. Those are Gradle
configuration options, **not** switches for the installed mod.

Optional visual rendering modes such as the XPBD detached overlay
and source-volume skin remain independent experimental options. The
bundled release defaults to normal XPBD replacement, not to drawing
two competing skins. Do not silently change the selected skirt
material/render mode for an existing scene.

## Build acceptance

- `javac --release 8` for the Jolt JNI/prototype; Jolt 6.0.0
  pinned upstream + matching ReleaseSp Windows/Linux native binaries.
- Native `JoltNativeSmoke` and `JoltJava8BootstrapSmoke` under Java 8.
- `./gradlew -PciDependencies -PenableJoltExperimental compileJava jar`.
- Integrity verification of the full JAR and required
  `SkirtXPBDRuntime`, `JoltChameleonPreviewRig`,
  `JoltHumanoidRagdoll`, `joltjni.dll`,
  `libjoltjni.so`, and new editor classes.
- Add release marker **after** checks, never in ordinary dev/class path.
- In-game manual acceptance still needed on Windows with Emoticons
  + Chameleon skirt + recorded pose + PHYSICS ragdoll weight.

### Verify after installing

Install only one `bbsanimationeditor` mod JAR in the game `mods`
directory. Duplicate old copies (including an accidentally named
`.jar`) can mask the new version. In `latest.log`, inspect
`[BBS Editor] Jolt Physics`, `[BBS Editor][Jolt]`,
`[BBS Editor][SkirtXPBD]`, `[BBS Editor][Ragdoll]`.
A successfully bundled and linked JAR is **not proof** that the current
scene has dynamic chains/cloth enabled or that the visual mesh is
correct. Verify a short paused and 60 FPS rendered sample before merge.
