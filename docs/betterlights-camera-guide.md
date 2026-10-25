# Better Lights in BBS Animation Editor — Light Rig and CAMERA Scene Lights

## Screenshots / version scope

Designed against the **BetterLights 1.0.3.PREVIEW** integration in Blockbuster
2.7.1, Minecraft Forge 1.12.2 / Java 8. Its settings are discovered through
Blockbuster's own `BetterLightsMorph.getValueManager()` / ValueSerializer, so
the actual list of nested settings comes from the installed light provider.
The following group names and specific values are observed in the user's
original Light 0 screenshot, not a claim about every BetterLights build.

| Group / field | Screenshot state | Explanation |
|---|---|---|
| **Light** | Checked | Enables the actual illumination contribution. Disabling it is not necessarily the same as deleting the light object; other effects are independent |
| **Shadows** | Checked | Enables shadow casting/occlusion by the source, if the active shader, surface and BetterLights pipeline support it. Potentially expensive |
| **Flare effect** | Unchecked | Camera-facing bright flare/glare from the source; unlike Light, it is a visual lens effect, not a mechanism for lighting nearby blocks |
| **Volumetric Fog Effect** | Unchecked | Enables in-air scattering (visible illuminated haze/shafts) rather than only illuminated world surfaces. This can be expensive and depends on shader support |
| **Samples** | 20 | Number of volumetric ray-march/integration steps (quality/performance tradeoff). Higher values normally reduce noise/banding and increase work |
| **Scattering Albedo** | 0.5 | Fraction of extinction attributed to scattering instead of absorption. Larger values cause more light to be re-scattered into visible fog, subject to the shader model |
| **Density** | 20.0 | Density/strength of this source's volumetric medium. Greater density generally increases visible fog and attenuation. The screenshot does *not* establish physical units or linear visual response |
| **Anisotropy** | 0.4 | Angular bias of scattering. 0 is approximately isotropic, positive values emphasize forward scattering, negative values backscattering in common phase-function models. The exact implementation/range should be confirmed in BetterLights shaders |

**Important:** The white horizontal lines under the screenshot's group headings
are GUI separators/section elements, not evidence of individual unlabelled
numeric sliders. `Samples`, `Scattering Albedo`, `Density` and
`Anisotropy` are *nested under Volumetric Fog Effect*; their values may
be stored even when the enclosing effect is disabled.

The editor intentionally doesn't implement fake controls or assume an
intensity/range or softness property where the installed provider exposes
none. The properties list, boolean switches and hexadecimal color values
come from native Blockbuster `ValueSerializer`. This also preserves unknown
settings and nested configuration fields in the original morph NBT.

## POSE — actor light rig

Select a real BetterLightsMorph actor and open **Pose**. The ordinary Steve
skeleton is not used. Two virtual tracks are available:

- `Origin`: keyframe-controlled XYZ *offset* from the actor's Blockbuster
  Record world location. The original Record is not modified. Selecting an
  Origin key and opening **LIGHT** edits the full native settings on *that
  key*. Floats/ints/colors interpolate between keyed snapshots; enabled
  feature switches change discretely. Without an Origin key selected, LIGHT
  edits the base scene actor morph and is saved with Ctrl+S.
- `Target`: keyframe-controlled direction vector. When no Target key exists,
  the source's original BetterLights direction is untouched. Creating a
  Target key (default +Z) makes the vector control the spotlight's native
  LightCaster direction. Move XYZ through the ordinary keyframe Transform.

The POSE inspector has **RIG KEYS** (ordinary Transform for the selected
Origin/Target key) and **LIGHT** (full native morph settings). Existing
key duplication, interpolation, numeric editing and timeline persistence
are reused. Per-key light settings are saved as optional
`LightSettings` compounds on `AnimationKeyframe` in editor.dat, and are
copied when Ctrl-dragging a key. Keys sampled during 20/30/60 FPS preview
never recreate the native LightCaster per output frame.

## CAMERA — static light editor, no keys

In CAMERA, select the **LIGHTS** tab in the left sidebar. Use **+ ADD** to
create an editor-only light in front of the current film camera. It is NOT
added to the Blockbuster actors list or Record folder. A light can be
selected, disabled, removed, and saved automatically with the scene.

The right-side tabs:
- **ORIGIN**: world X/Y/Z of the source.
- **TARGET**: world X/Y/Z the source points toward.
- **LIGHT**: the same native BetterLights settings as for light actors,
  including Light, Shadows, Flare effect and Volumetric Fog nested values.

Origin and Target each display projected X/Y/Z move handles in CAMERA
preview. Drag an axis square to change the selected world coordinate, or
enter a precise numeric value. These are editing overlays only and must
not appear in output video.

Static scene lights are persisted under the optional
`CameraSceneLights` NBT tag in the scene's editor.dat, with stable UUIDs,
original morph NBT and world Origin/Target values. This does not touch
Minecraft world blocks or standalone BetterLights static sources placed
outside the editor. Scene-owned light objects are rendered by persistent
preview-only BetterLightsMorph instances in the native OptiFine world pass,
so the original light shader also participates in the recorded film pass.
The renderer drops cached preview morphs on world/camera detach and relies
on Blockbuster's temporary-caster lifecycle for final cleanup.

## Acceptance checks requiring real Minecraft/BetterLights

1. A BetterLightsMorph actor shows exactly two POSE tracks, Origin and
   Target. Existing normal characters still have their full skeletons.
   Origin key moves the light and Target key changes its beam direction.
2. Add Origin keys at frames 0, 20 and 40. Change native color, range/
   intensity where available, and Shadows/Flare/Fog toggles in LIGHT;
   verify the two positions and numeric interpolation, not only NBT.
3. Ctrl-drag an Origin key: both transform and light properties are copied.
   Ctrl+S, reopen, compare original light actor Record and saved key data.
4. In CAMERA, create two independent lights, move each Origin and Target
   with the projected gizmos, and edit native settings. No light keys
   should appear on the clip timeline.
5. Save editor.dat, reopen; all scene light coordinates, enabled flags,
   colors and nested settings should persist.
6. Render 20/60 FPS with multiple lights, shadows and volumetric effects.
   Compare the native MP4 with SHOT preview; watch for a one-frame
   LightCaster activation delay, shader shadow instability or unexpected
   light following the camera.
7. Delete or disable sources, close CAMERA and wait for the temporary
   BetterLights caster lifecycle; ensure sources disappear rather than
   accumulating on scene changes. Repeat without BetterLights loaded.
8. Test an extreme FOV/9:16 preview; the lightweight projected XYZ
   handles may require tighter native-matrix alignment in future. They
   are not the skeletal EditorGizmoController and have no Rotate ring.

Successful Java 8 and full Forge compilation is not a substitute for these
shaderpack/client tests.
