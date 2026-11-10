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
- **UI revision**: the PHYSICS inspector follows the selected timeline row (no duplicate top tabs). Numeric fields use CAMERA's left/right arrows; click the center to type a value, Enter to save, Escape to cancel, keyboard UP/DOWN to nudge the focused number. Target uses a scrollable popup. Gravity presets use independent clickable hitboxes, including Mars 3.71.
- **Water Surface Y** is a world-coordinate horizontal plane, keyed alongside Water. Enable **USE SURFACE** to calculate immersion separately for each moving Jolt capsule; its visible fraction smoothly changes as a body crosses the waterline. With the toggle disabled, older scenes retain legacy whole-actor water behavior. The surface is a plane, not Minecraft block detection. 
- Water is approximated as an entire fluid volume around the actor: buoyancy applies an upward force and viscous drag scales velocities. It does NOT yet detect Minecraft water blocks or submersion depth.
- An updated **jolt-hair-prototype-java8.jar** is required for the new water surface. It contains `JoltHumanoidRagdoll.setEnvironment(float,float,float,float,float,float,boolean)`. The bridge falls back to the five-float legacy method if the sidecar is old, so Surface Y will have no effect until you replace the JAR. The Jolt 6.0.0 native DLL is unchanged.
- Existing Jolt native DLL **6.0.0** is unchanged.

## Intentionally not solved by this stage
- Record movement vs. fallen-body position, and teleporting back when Weight reaches zero.
- Collision with real Minecraft terrain (flat preview floor proxy remains).
- Exact replay/bake across arbitrary timeline seeks and video export. Physics export is still guarded by the existing opt-in.
- Actual Minecraft water-block immersion, swimming, currents and actor-local gravity direction. Surface Y is a manually entered horizontal plane.
- Simultaneous multiple impulses at precisely the same frame. Use one impulse event per character main key/frame in this first pass.

## Test sequence
- Verify old Ragdoll keys without editing or changing the sidecar.
- New Impact event at activation frame, repeat same preview frame, play across the event and seek backwards: check impulses do not repeat at a held frame.
- Gravity Earth → Moon → Zero with an actor falling, compare vertical trajectory.
- Water 0 → 1 with Buoyancy 1.05 and Drag .8: compare sinking and damping; then Water 1 → 0.
- Test actor yaw 0/90/180 with arrow-tip and base dragging, check hit direction.
- Multiple actors with independent physics keys; Morph/Skin remain unchanged; save and reopen.
- Verify the full game still works with the Physics option disabled.

## Acceptance checks — UI / water surface revision

- Selecting each PHYSICS lane changes the CAMERA-style right inspector. There are no duplicate RAG/HIT/GRAV/WATER tabs.
- Type `3.710` into Gravity Magnitude; ENTER saves. Click MARS and confirm it writes 3.71 and creates a Gravity key. Other presets must still work.
- Change Impact Target using dropdown; test mouse arrows, manual values, keyboard UP/DOWN and cancel with Escape.
- Enter Water Surface Y = `65`, enable USE SURFACE, Water Amount = `1`, Buoyancy = `1.05`. Above the plane, no water force should act; below the plane, limbs progressively receive drag/buoyancy.
- Disable USE SURFACE and confirm old whole-body water behavior; save and reopen, verify both surface height and toggle survive.
- Re-test elbows, knees and shoulders with a 100% ragdoll and strong impulse. More conservative limits reduce overbending but do not eliminate all interpenetration: adjacent capsules still overlap at joints and full mesh-aware contacts are future work.
