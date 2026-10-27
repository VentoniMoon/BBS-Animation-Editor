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

## Single native lighting engine (new architecture)

Both editors now drive **BetterLightsWorldEngine** in
`BbsFsWorldCameraDriver.renderTick(START)`. It owns one
BetterLights `LightCaster` per stable source ID (actor or CAMERA static).
Sources are prepared **before** the native Minecraft/OptiFine world pass
and its shader uniforms, not during `RenderWorldLast`.

- Actor and CAMERA lights use the same `BetterLightsWorldState`: world
  Origin, world Target, normalized direction, all in Minecraft blocks.
- Native Blockbuster/MCLib `ValueSerializer` applies **every installed
  BetterLights property**, including optional shadow, flare and fog values.
  Only position/direction are overridden with editor-owned world geometry.
- Source slots do **not** create an `EntityActor`, call
  `BetterLightsMorph.update`, create Blockbuster dummy runnables, or draw
  the light morph a second time. They register the caster through the
  provider's own temporary-light manager once per stable source.
- Changing native settings updates the one slot; switching world/scenes,
  deleting/disabling a light, or closing the editor unregisters it.
  Shader/BetterLights API access is isolated behind reflection, allowing
  the editor to load without BetterLights installed.
- The wireframe and movement gizmos are editorial overlays, not exported
  footage; for POSE they consume the same `BetterLightsWorldState` as the
  lighting backend.

**World coordinate contract:** no model pixels (no division by 16),
Steve skeleton offset, actor-facing yaw flip, or camera-relative transform.

## POSE — actor light rig

Select a BetterLightsMorph actor in **POSE**. Instead of a Steve skeleton,
the editor presents two independent control tracks, both rooted at the
actor's Record XYZ sampled at the current scene time:

- `Origin` — editable world-block **offset** for the light source.
- `Target` — editable world-block offset for a separate **aiming point**
  (initially four blocks along world +Z from Record). Moving Origin does
  not automatically move Target. The light always aims from current Origin
  toward current Target.

The original Record `.dat` is never modified by either track.
Keyframe interpolation and duplication work through existing AnimationBone
tracks. **RIG KEYS** shows XYZ controls for the selected light key (no
fake skeletal rotation or scale). **LIGHT** shows the native provider
properties; selecting an Origin key edits native settings on that key,
otherwise it edits the actor's base BetterLights morph.

`LightSettings` compounds are preserved in the scene editor.dat and
numeric light properties are interpolated by the existing settings sampler.
Switches are discrete. Pausing, seeking or rendering fractional 60 FPS
samples must update the **same** caster rather than spawning a new one.

**Migration:** old `Target` keys from the earlier Origin-relative
implementation may need manual repositioning. Existing key data is not
silently rewritten, since it is ambiguous whether it represented a point
or a direction vector.

## CAMERA — static light editor, no keys

CAMERA's **LIGHTS** tab retains the ADD/REMOVE source list, ORIGIN,
TARGET, and LIGHT properties. ORIGIN and TARGET are independent *absolute*
world coordinates, editable through axis handles or numeric fields.
No source creates a Record actor or a CAMERA timeline clip.

Static lights remain saved in scene editor.dat under
`CameraSceneLights` with stable IDs, enabled state, NBT settings and
world points. The rendering engine now consumes these saved values
**directly** instead of synthesizing an actor/Record each frame.

When the editor is not in CAMERA, scene-only static sources are not
registered. Animated light actors remain available in POSE and other
normal scene preview modes (subject to actor visibility settings).

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
7. Delete or disable sources, close CAMERA; verify immediate caster unregistration and that
   sources do not accumulate on scene changes. Repeat without BetterLights loaded.
8. Test an extreme FOV/9:16 preview; the lightweight projected XYZ
   handles may require tighter native-matrix alignment in future. They
   are not the skeletal EditorGizmoController and have no Rotate ring.

Successful Java 8 and full Forge compilation is not a substitute for these
shaderpack/client tests.
