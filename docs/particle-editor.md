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

## Fixed: particles invisible inside the paused single-player editor

Minecraft 1.12.2 pauses its world by default while a `GuiScreen` is
open. The editor intentionally keeps this pause to avoid advancing
unrelated world entities, actors and scene physics.

Vanilla `ParticleManager.addEffect()` stores new particles in a **queue**.
The queue is flushed by `ParticleManager.updateEffects()`, but
`Minecraft.runTick()` skips that method while the game is paused.
Before this fix, both CAMERA static sources and POSE particle actors
spawned particles into the queue without ever making them renderable.
Closing the editor unpaused the world and flushed the queue, causing the
reported brief burst of particles AFTER exiting the editor.

`CameraParticleWorldEngine.tick()`, already called exactly once from
the editor's normal `updateScreen()` client tick, now conditionally
calls `mc.effectRenderer.updateEffects()` when (and only when) the
editor's world is paused. This drains the queue and advances particle
lifetime at **20 TPS**, without invoking another world renderer or
unpausing Minecraft. On unpaused/multiplayer clients, vanilla already
calls `updateEffects()`, so the editor deliberately does not duplicate
it. Source settings, ParticleMorph NBT, keyframes, emitter frequency and
shader/mirror rendering are unchanged.

### Check in the game

1. Run `runClient -PfixMixinAsm` with the updated branch.
2. Open the editor in a single-player world and add a static CAMERA
   particle source, select `flame`, set a visible Count and Duration.
3. Confirm particles show **while the editor remains open**, animate
   over time and no longer appear only after leaving the editor.
4. Select a vanilla ParticleMorph actor with a Record, play its timeline
   and verify the same behavior. Check PAUSE and re-play.
5. Optionally inspect `[BBS Editor][Particles] Paused world:` in
   `run/logs/latest.log`; it should be printed only once per editor
   session when sources are active.
6. Recheck a shader-enabled scene or mirror. No additional world render
   pass or frame-buffer attachment is introduced by this fix.

The client-only particle manager is intentionally ticked while the
world remains paused. This advances all already-present vanilla client
particles in the same manager, not only editor-owned ones; it does NOT
tick world entities, the integrated server, Minecraft physics or
the editor timeline.

## UI consistency pass — CAMERA scene selector and particle inspectors

The existing particle emitters, their world preview, all ParticleMorph
NBT keys and timeline interpolation remain unchanged. This UI-only pass
addresses narrow Cyrillic layout and visual mismatches reported in the
editor screenshots.

- **CAMERA section selector** is now a framed, themed dropdown like
  the SCENE search menu: dark input, accent underline, selected row,
  hover state and consistent clickable popup bounds. It still offers
  CLIPS, LIGHTS, MIRRORS and PARTICLES.
- **Static PARTICLES inspector** matches the CAMERA CLIPS and LIGHTS
  layout: header/source label, underlined POSITION/SETTINGS tabs, a
  left-label/right-value row pattern, and grouped WORLD POSITION,
  ROTATION and SOURCE headings. It preserves XYZ, Yaw/Pitch/Roll and
  Enabled, without introducing keyframes. Mouse wheel scrolls the
  position fields on shorter editor windows.
- **Shared particle settings** (static and keyed actor) use the same
  grouped sections: EMISSION (Frequency, Duration, Delay, Cap, Speed,
  Count), VANILLA PROPERTIES (offset X/Y/Z and spread DX/DY/DZ), and
  GENERAL (Args and LocalRotation). Numeric arrow buttons and text
  fields use bounded horizontal rectangles. The vanilla particle type
  selector still includes all EnumParticleTypes and scrolls within
  the right panel rather than opening beyond its bottom edge.
- **POSE particle actor tabs** now use the same segmented active
  underline. The Origin-key hint is clipped and moved away from the
  Transform controls.
- New RU/EN translations cover compact heading and narrow row labels
  without altering serialized values.

### Manual layout test

1. Open the editor in Russian and select CAMERA -> PARTICLES.
2. Compare the dropdown with the scene picker: identical dark framing,
   highlighted active row and no menu overlap after it is closed.
3. Select a static source; check the XYZ values near world-coordinate
   limits, wheel scroll down to Yaw/Pitch/Roll and Enabled, and edit
   values with keyboard and arrows. Save and reload without value change.
4. Open SETTINGS; scroll every parameter and the full vanilla particle
   catalog. Verify dropdown contents and every arrow/input remain
   inside the 185px inspector even with smaller GUI scales.
5. Select a ParticleMorph actor in POSE; switch between POSITION and
   SETTINGS, check selected-key editing and visual alignment with
   normal TRANSFORM panel.
6. Switch back to LIGHTS, MIRRORS and CLIPS to verify the selector
   still routes to the original independent panels.
