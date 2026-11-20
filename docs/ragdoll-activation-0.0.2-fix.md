# PHYSICS → RAGDOLL did not activate (0.0.2)

## Observed
Both the installed 0.0.2 Forge JAR and ordinary Gradle `runClient` showed
`Weight %: 0.000`, `0%=анимация`, and the **ADD / UPDATE KEY** button.
Pressing the button in a new scene authored another 0% key and did not
begin Jolt physics. Even manually authoring a positive key had no effect
without a separate Java system property.

## Confirmed root causes
1. `RagdollEmoticonsJoltBridge.ENABLED` used
   `Boolean.getBoolean("bbs.editor.physics.jolt.ragdoll")`, whose default
   is **false**. Neither installed Minecraft nor plain `runClient` set
   this experimental JVM flag. It was never a user-visible PHYSICS setting.
2. `PhysicsWorkspace.authorDefault(RAGDOLL)` wrote the current sampled
   weight to a new key. The default sampler returns **0%** before the
   first ragdoll key. Therefore the button added an *inert* zero key.
3. Local `build.gradle` used to include the Java 8 JNI and native ragdoll
   sidecar only with `-PenableJoltExperimental`, even if both libraries
   were already installed in `libs/`. That also made normal development
   runs silently unable to find Jolt.

## Fix
- A **positive CHARACTER/PHYSICS Weight key** now activates the native
  Jolt bridge automatically. Non-ragdoll scenes still return immediately
  before JNI loading or physics initialization.
- Explicit `-Dbbs.editor.physics.jolt.ragdoll=false` remains a safe
  operator override for disabling this experimental feature.
- The **first** ADD / UPDATE KEY in a scene defaults to **100%**. Existing
  keys and deliberate 0% keys are never changed; custom intermediate
  weights still interpolate as authored.
- The PHYSICS panel now explains `0%=анимация / 100%=физика` and shows
  Jolt initialization status. Native failures and unsupported stock rig
  mappings also log their reason in `latest.log`.
- Plain local `runClient` now automatically includes both Java 8 Jolt
  sidecars when they already exist. Explicit `-PenableJoltExperimental`
  still errors loudly if either sidecar is missing. Experimental Jolt hair
  and XPBD skirt remain independently opt-in.
- The release workflow checks for
  `RagdollEmoticonsJoltBridge.class`,
  `RagdollActivationPolicy.class` and the actual
  `physics/JoltHumanoidRagdoll.class`, and builds a test JAR on the PR.

## Manual regression (Emoticons BOBJ rigs only)
1. Install **one** BBS Animation Editor JAR, Minecraft 1.12.2, Java 8 and
   compatible Blockbuster/McLib/Emoticons. For local runs, ensure
   `libs/jolt-jni-6.0.0-java8-bundled.jar` and
   `libs/jolt-hair-prototype-java8.jar` are present.
2. Select an **Emoticons** actor using an actual supported bone hierarchy.
   PHYSICS → RAGDOLL → move playhead to frame 20 → ADD / UPDATE KEY.
   The field should become **100.000** rather than **0.000**.
3. Start playback through frames 21–40. The body should begin to fall.
   The native solver only advances at later timeline frames; one paused
   frame at activation cannot exhibit a completed fall.
4. At frame 45, author a weight 0% key and verify return to authored
   animation. Reverse-seek to frame 10 and play again; inspect whether
   the solver restarts at the frame transition.
5. Save and reopen `editor.dat`, then repeat. Also test an actor using
   an intentionally unsupported armature: it must not crash Minecraft.
6. With `-Dbbs.editor.physics.jolt.ragdoll=false`, confirm intentional
   fallback to animation, without JNI initialization.
7. If weight reaches 100% but nothing falls, capture:

```powershell
Select-String -Path '.\run\logs\latest.log' `
    -Pattern '\[BBS Editor\]\[Ragdoll\]', 'Optional Jolt Physics', 'Jolt Physics' |
    Select-Object -Last 40 |
    ForEach-Object { $_.Line }
```

## Scope and limitations
The current native ragdoll solver intentionally supports fitted
**Emoticons** BOBJ skeletal hierarchies only. A Blockbuster or Chameleon
morph in the same UI is not proof of supported physics. The preview solver
currently has a local flat floor and is **disabled during CAMERA export**
pending deterministic native replay; this patch restores ordinary
interactive RAGDOLL preview and does not claim to solve film-export
physics, unsupported rigs or true Minecraft world-block collisions.
No change is made to XPBD skirt or the frozen `v0.0.2` release tag.
