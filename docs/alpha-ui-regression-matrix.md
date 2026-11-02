# BBS Animation Editor — Alpha UI and functional regression matrix

**Status:** checklist ready; visual and in-game observations are **pending**, not automatically passing. Test the branch `alpha/ui-parity-timelines-character-scene-interpolation` with the usual Forge 1.12.2 + OptiFine U_G5 + Better Lights + Blockbuster modpack. Check both Ocean and at least one non-Ocean editor theme.

## UI parity acceptance checks

| ID | Feature | Exact reproduction | Expected outcome | Status |
|---|---|---|---|---|
| UI-01 | Timeline visual hierarchy | Switch CAMERA → POSE → BODY PARTS → CHARACTER | Uniform dark track palette, thin theme highlight and matching playhead | [ ] Manual |
| UI-02 | Pose markers | Create several keys, select and drag one at different zoom levels | Shared CAMERA-style handle; no movement when merely selecting | [ ] Manual |
| UI-03 | Body Parts intervals | Attach a Chameleon hair / boots model with start/end frame | Bars look like CAMERA clips with highlight, outline and two edge grips | [ ] Manual |
| UI-04 | Body Parts hit area | Select attachment by clicking bar and its label; drag either edge | Bar and hitbox agree; no off-by-one or invisible handles | [ ] Manual |
| UI-05 | Character timeline | Select Character key with LMB, then inspect right panel | Key stays selected and relevant editor section opens | [ ] Manual |
| UI-06 | Character RMB safety | Single RMB on existing key; then double RMB on same key within 350ms | First RMB selects without deletion; second removes only that key | [ ] Manual |
| UI-07 | Character body part RMB | Perform same sequence on body-part Character track | First selects part; second removes only matching key | [ ] Manual |
| UI-08 | CHARACTER key edit | Select morph / skin / animation / generic key and change value | Relevant inspector section is accessible and change remains after seek | [ ] Manual |
| UI-09 | SCENE scroll | Create/load at least 12 .dat scenes; open SCENE and wheel down/up | Every file becomes accessible; click selects same file rendered under cursor | [ ] Manual |
| UI-10 | SCENE selected state | Scroll to bottom, load file #12, reopen list | Selected file tracked by original scene index, not visible row number | [ ] Manual |
| UI-11 | SCENE wheel capture | Open list above preview; wheel on dropdown | Only the dropdown scrolls, not zoom or actor list | [ ] Manual |
| UI-12 | POSE interpolation | Open interpolation popup | BBS FS-style 6-column thumbnail grid; native McLib choices and every CAMERA preset accessible with wheel | [ ] Manual |
| UI-13 | CAMERA interpolation | Open a KEYFRAME/PATH interpolation field | Same shared thumbnail presentation; all existing CAMERA IDs retained | [ ] Manual |
| UI-14 | Easing preview | Choose sine_in, bounce_out, elastic_inout, then linear | POSE curve graph updates; keys interpolate with the chosen easing | [ ] Manual |
| UI-15 | Legacy behavior | Load a scene written before EditorCurve was introduced | Existing McLib interpolation/Easing and Hermite control handles stay unchanged | [ ] Manual |
| UI-16 | WAV status | Load WAV, toggle mute/unmute | Status text is readable without the old opaque pink label plate | [ ] Manual |
| UI-17 | Theme parity | Switch Ocean → another theme → Ocean | Active accents update in tracks, clips, markers and interpolation grid | [ ] Manual |
| UI-18 | Scale | Test 16:9 and smaller editor windows | Menus are readable and clips do not draw beyond track boundaries | [ ] Manual |

## Data / animation regression checks

| ID | Feature | Expected outcome | Status |
|---|---|---|---|
| DATA-01 | Pose key creation, drag, Ctrl-copy, delete | Exact frame count and transforms retained | [ ] Manual |
| DATA-02 | Pose interpolation in memory | CAMERA preset interpolates X/Y/Z translation, angles and scale predictably | [ ] Manual |
| DATA-03 | Scene persistence | EditorCurve survives save/reopen and copied keys preserve it | [ ] Manual |
| DATA-04 | External Blockbuster .dat export | **Verify separately.** The new EditorCurve is stored in editor scene NBT and sampled in its preview; generic legacy McLib .dat export may not encode non-native CAMERA presets without curve baking. Treat loss as an alpha blocker or bake to linear subkeys | [ ] Manual |
| DATA-05 | Body Parts | Chameleon/Emoticons model keys, disable bones, weld, morph and skin replay identically | [ ] Manual |
| DATA-06 | Character | Skin, Morph, Animation, Action and Body Part Overrides survive save/load | [ ] Manual |
| DATA-07 | Lip Sync | Text phonemes, WAV waveform and monitor mute continue to work | [ ] Manual |
| DATA-08 | Multi-actor | Selection and record playback do not affect other actors' key contexts | [ ] Manual |

## 3D/render/physics regression checks

| ID | Feature | Expected outcome | Status |
|---|---|---|---|
| RENDER-01 | OptiFine BSL + BetterLights | Static, actor, and native sources illuminate without camera-relative drift | [ ] Manual |
| RENDER-02 | Sun shadows | Actor shadows visible in main view and private planar mirror; no terrain artifacts | [ ] Manual |
| RENDER-03 | Mirrors | Correct near clipping, no blue/ghost blocks, no granular artifacts or flipped geometry | [ ] Manual |
| RENDER-04 | Jolt | Hair/Body Parts gravity, torso/head/arm collision, rotation, jumping and baked keys | [ ] Manual |
| RENDER-05 | CAMERA clips | View, modifiers, image, video, sound, screen shake, crop/aspect ratios | [ ] Manual |
| RENDER-06 | Better Lights | LIGHT actor + static CAMERA sources; editable origin and target; shadows, fog and flare | [ ] Manual |
| RENDER-07 | Export | PNG/FFmpeg video 1080p & 2K, 60fps, audio once, media overlays and no UI contamination | [ ] Manual |

## Alpha exit criteria

- No blocker or data-loss regression in any DATA section; no broken main-world or mirror rendering.
- Main UI and control acceptance cases UI-01 through UI-18 run at least once in Minecraft.
- Verify `EDITOR` scene persistence **and** the external Blockbuster `.dat` output separately.
- Record screenshots and one short log with the exact failing step for any issue.
- Do not interpret a successful Java build, static inspection, or this checklist as an in-game pass.

## Suggested test command

```powershell
git fetch origin
git switch --track origin/alpha/ui-parity-timelines-character-scene-interpolation
.\gradlew.bat -Dbbs.editor.camera.nativeActorSunShadows=true runClient
```

The native sun-shadow flag is opt-in. Its use here preserves the user's previously verified sunlight caster path during alpha regression.


## Alpha inspector / numeric / timeline finish pass (PR #146)

These checks remain **unverified in-game** until explicitly tested.

| ID | Interaction | Verification | Status |
|---|---|---|---|
| FIN-01 | Numeric ± mouse | On a POSE X=0 field, click > exactly 2/4/8/16 times | 0.125/0.25/0.5/1 respectively (step 0.0625) | [ ] |
| FIN-02 | Numeric ± keyboard | Focus a numeric value and press ↑ exactly 32 times | Exactly +1 from starting value (step 0.03125) | [ ] |
| FIN-03 | Downwards/negative numbers | Click < repeatedly and use ↓ on negative positions/angles | Symmetric fine steps, no accumulation/rounding loss on save/reopen | [ ] |
| FIN-04 | Numeric field persistence | Edit POSE/BODY PARTS global transform and CAMERA key field by arrow, then seek away/back | Updated number persists and keyed scene values change | [ ] |
| FIN-05 | LIGHT numbers | Edit static source X/Y/Z and target X/Y/Z, and BetterLights native parameters via arrow/keyboard | Exactly expected value, valid bounds, saves in scene | [ ] |
| FIN-06 | Integer-only fields | Adjust start, duration, layer, tick, channel mask, Character fade/tick | Integer +1/-1; no fractional frame or invalid mask | [ ] |
| FIN-07 | Character style | Open APPEARANCE, ANIMATION SETUP, BODY PART OVERRIDES, ANIMATION PARAMETERS | Uniform CAMERA dark sections, accent strips, values and hover visuals | [ ] |
| FIN-08 | Pose/BodyParts/Physics style | Toggle selected Body Parts model and Jolt physics panel | Consistent dark header, fields, highlights and no clipping | [ ] |
| FIN-09 | Back from Body Part model | Enter selected internal Body Part model and inspect top ruler beside WAV label | BACK button visible, clickable, returns to overview | [ ] |
| FIN-10 | LMB playhead drag | Drag ruler continuously from start to end in Pose, Character, both BodyParts subviews and CAMERA | Live playhead and animation frame follow cursor without losing grip | [ ] |
| FIN-11 | Delete confirmation removed | RMB once then RMB twice quickly on same Pose key | First selects; second deletes key immediately, without modal | [ ] |
| FIN-12 | Delete safety | RMB on two different keys, or press once and wait >350ms | No unintended deletion | [ ] |
| FIN-13 | Body Parts alternating rows | Compare actor attachment + model-bone views against CAMERA timeline | Alternating dark tracks across both populated and empty rows; selected lane remains visible | [ ] |
| FIN-14 | Renderer unchanged | Repeat the earlier working BSL sun shadows, BetterLights drift and mirror/font tests | No regression in scene and export | [ ] |

**Precision rule:** 0.0625 = 1/16, 0.03125 = 1/32. Decimal UI rounding is not the source of truth. Whole-frame and count fields are intentionally integers.
