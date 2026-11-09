# BBS Animation Editor — PHYSICS workspace (Physics 2.0)

## What changed
- **MODE → PHYSICS** is a separate workspace with the same actor selector, native preview, playback clock and Character-style 20-TPS timeline.
- The CHARACTER inspector no longer contains the Ragdoll accordion. Existing scene keys are read unchanged: `Data.Ragdoll.Weight` remains backward compatible.
- PHYSICS has four independently selectable timeline rows: **Ragdoll, Impulse, Gravity, Water**. Other Character key data on the same frame are not modified.
- Preview includes an interactive **Impulse arrow**: drag the orange arrow tip to aim, drag its pale base to change the relative hit location. Model yaw is accounted for by the guide.

## UI workflow
1. Choose an actor from the original left ACTORS list. Open **MODE → PHYSICS**.
2. Select a track by clicking its left label; click on the film timeline to set the frame.
3. Double click an empty lane to add its key. Drag an existing key to another frame; right click a marker to delete only that PHYSICS channel on this frame.
4. **Ragdoll** — Weight 0..100%, ±10% and slider. Existing values are retained.
5. **Impulse** — Target rigid segment (pelvis, chest, head, upper/lower arms, thighs/shins), Strength 0..200, model-relative direction XYZ and hit-point offsets XYZ. Create at the desired frame; an impulse key automatically enables Weight 100% at that frame if no ragdoll is active. The arrow in Preview controls direction/base offset.
6. **Gravity** — separate keyed acceleration 0..100 (Earth 9.81 by default); presets Earth/Moon/Mars/Zero and a 0..30 slider.
7. **Water** — keyed whole-actor immersion Amount 0..1, Buoyancy multiplier, linear Drag and Angular Drag. Use Amount 1 for fully immersed, 0 for air. Coefficients are first-pass artistic controls, not realistic hydrodynamics.
8. Save normally with Ctrl+S. The channels are part of the existing scene's actor CHARACTER main-track NBT.

## Native runtime
- With the opt-in `-Dbbs.editor.physics.jolt.ragdoll=true`, the Emoticons bridge uses the existing per-actor Jolt ragdoll solver.
- **Impulse** fires only once when its source frame is crossed. It applies linear impulse at the requested hit point and does not repeatedly push the body every preview render.
- **Gravity and Water** are evaluated at every simulated tick. Scalars interpolate between authored keys.
- Water is approximated as an entire fluid volume around the actor: buoyancy applies an upward force and viscous drag scales velocities. It does NOT yet detect Minecraft water blocks or submersion depth.
- An updated **jolt-hair-prototype-java8.jar** is required for gravity/water. Its `JoltHumanoidRagdoll.setEnvironment(float,float,float,float,float)` method is missing from older sidecars; when absent the bridge warns once in the log.
- Existing Jolt native DLL **6.0.0** is unchanged.

## Intentionally not solved by this stage
- Record movement vs. fallen-body position, and teleporting back when Weight reaches zero.
- Collision with real Minecraft terrain (flat preview floor proxy remains).
- Exact replay/bake across arbitrary timeline seeks and video export. Physics export is still guarded by the existing opt-in.
- Physically derived water level, swimming, currents and actor-local gravity direction.
- Simultaneous multiple impulses at precisely the same frame. Use one impulse event per character main key/frame in this first pass.

## Test sequence
- Verify old Ragdoll keys without editing or changing the sidecar.
- New Impact event at activation frame, repeat same preview frame, play across the event and seek backwards: check impulses do not repeat at a held frame.
- Gravity Earth → Moon → Zero with an actor falling, compare vertical trajectory.
- Water 0 → 1 with Buoyancy 1.05 and Drag .8: compare sinking and damping; then Water 1 → 0.
- Test actor yaw 0/90/180 with arrow-tip and base dragging, check hit direction.
- Multiple actors with independent physics keys; Morph/Skin remain unchanged; save and reopen.
- Verify the full game still works with the Physics option disabled.
