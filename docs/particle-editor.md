# Particle Editor — Physics-independent scene effect sources

Implementation PR #213, Minecraft 1.12.2 / Java 8.

## Blockbuster source of truth

The **vanilla** portion of `mchorse.blockbuster_pack.morphs.ParticleMorph`
uses native NBT keys `Frequency`, `Duration`, `Delay`, `Cap`,
`Type`, `Speed`, `Count`, `X/Y/Z`, `DX/DY/DZ`,
`LocalRotation` and `Args`. These are preserved by
`ParticleEmitterConfig`; unknown NBT keys are not discarded.
The type selector is generated from all available
`EnumParticleTypes.values()` rather than a hardcoded subset.

## Two emitter modes

- **POSE actor particles:** select an actor whose morph NBT has
  `Name: "particle"`. The actor's POSE uses a virtual `Origin` bone
  and the separate **POSITION / SETTINGS** inspector tabs. Moving the
  keyed Origin changes the emitter's world-space position relative
  to the recorded actor. Particle settings can be changed on an
  Origin key without overwriting the actor's saved base morph.
  Settings NBT is persisted under `ParticleSettings` on each
  `AnimationKeyframe`. Numeric emission properties interpolate
  between keys while discrete type/flags step.
- **CAMERA static particles:** use the CAMERA section selector,
  **PARTICLES**, then **+ ADD**. A source holds its own world-space
  XYZ, yaw/pitch/roll, enabled flag and native ParticleMorph settings.
  It is saved as a scene-only `CameraSceneParticles` NBT compound,
  with NO actor record or timeline key. The XYZ editing gizmo is
  shared with native CAMERA LIGHTS/POSE controls.

Both modes use the same scrollable particle type picker and settings
inspector. Click the current type to open a vertically scrollable
enum list; use the wheel or arrow buttons to cycle. Native block/item
particles may need valid `Args` per the selected particle type.

The CAMERA navigation is now a single dropdown:
`CLIPS / LIGHTS / MIRRORS / PARTICLES`. Other CAMERA tabs retain
their existing state and persistent sources.

## Emission and render safety

Vanilla particles are emitted once per editor logical **20TPS** update
and then owned by Minecraft's normal world particle lifetime. Do not
emit inside world redraws, preview frames, mirror/shadow passes or
extra FBO exports. Particle velocity/dispersion respects local
yaw, pitch and roll if `LocalRotation` is enabled.

This first implementation specifically covers **vanilla** ParticleMorph
emission. The NBT structure for nested `MORPH` particle modes is
preserved, but no generic nested-morph static particle renderer or
deterministic timeline rewind/MP4 export has been added. Rendering
behavior of paused singleplayer worlds, special-argument particles,
keyed actor sources and OptiFine/BSL requires in-game checking.

## Test procedure

Start IntelliJ's Gradle `runClient -PfixMixinAsm` with the usual
Blockbuster/Emoticons dependencies. Load a scene that has an actor
with `Name: "particle"` and a Record.

1. In POSE, select Origin and insert two position keys. Move one
   with its gizmo. Confirm the particle spawn position follows actor
   playback and save/load retains both Origin keys.
2. In actor SETTINGS set particle type (e.g. flame), frequency,
   duration, speed, count, X/Y/Z, DX/DY/DZ, LocalRotation, Args.
   Save/load and confirm keys and native morph are preserved.
3. In CAMERA open selector -> PARTICLES -> + ADD, drag its native XYZ
   gizmo, rotate it with Yaw/Pitch/Roll number fields, and choose a
   particle type by scrolling through the complete enum list.
4. Confirm static source effects, enable/disable, add/remove, scene
   save/load and interaction with CLIPS/LIGHTS/MIRRORS.
5. Check for duplicate particle emission in shader/BSL/mirror passes.
   Confirm that state is cleared on changing scenes.

Plain Java 8 CI: `ParticleEmitterConfigSmokeTest` validates native
values, all available type entries, numeric validation, frequency
control, extra args and static-source NBT roundtrip.
Full Forge compilation is tested by project CI.
