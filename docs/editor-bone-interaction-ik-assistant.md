# Editor bone tools — staged integration notes

## Upstream reference (BBS by McHorse)

The original BBS editor separates viewport **form/bone picking** from
**keyframe/transform editing**. See:

- https://github.com/mchorse/bbs-mod/blob/master/src/client/java/mchorse/bbs_mod/ui/film/replays/UIReplaysEditor.java
  — `clickViewport` uses stencil picking and routes left-click to
  `pickForm`; the gizmo has separate hit priority.
- https://github.com/mchorse/bbs-mod/blob/master/src/client/java/mchorse/bbs_mod/ui/film/replays/UIReplaysEditorUtils.java
  — `pickForm` chooses the pose sheet, selects a bone and nearest
  keyframe or inserts when requested.
- https://github.com/mchorse/bbs-mod/blob/master/src/client/java/mchorse/bbs_mod/ui/framework/elements/input/UIPropTransform.java
  — shared transform editing, local/global axis support.

No upstream GPL implementation is pasted into the editor. This branch
implements the same interaction pattern against our own Forge 1.12.2
model/scene abstractions.

## Implemented and integrated in the viewport (PR #257)

- In **POSE** or **BODY PARTS**, while paused, the selected actor (or
  active Body Part child model) exposes a green **projected limb/joint
  wire highlight** under the cursor. Rendering uses the native gizmo
  pivot matrices for Blockbuster / Emoticons / Chameleon and does **not**
  change the texture/material of the character.
- **LMB on a highlighted limb/joint** selects the corresponding
  `AnimationBone`, creates or selects its `AnimationKeyframe` at the
  **current frame**, copies the interpolated local transform into a newly
  created key (no 0/0/0 pose jump), and routes drag to the existing
  `EditorGizmoController`. Position mode uses the screen-XY plane;
  rotation mode uses a direct Z-axis drag. Scale remains gizmo-only.
- Existing gizmo handles and mode buttons take precedence. Normal RMB
  viewport camera manipulation is retained. The overlay is GUI-only
  and is not included in rendered/exported video.
- **Limitation**: BBS has a native stencil form-ID pass, but our
  Forge/OptiFine/shared-FBO renderer currently has no reliable equivalent
  ID buffer. The picker tests projected joint pivots and limb segments,
  **not pixel-perfect occluded mesh surfaces**. Test with overlapping
  limbs before considering this final.

## World-space bone lock (experimental native Blockbuster POSE)

- In **POSE**, select a Blockbuster limb with a parent and click **PIN WORLD
  XYZ** (top-left of Preview), or press **Ctrl+Shift+P**. Click again / use the
  shortcut to remove that same bone's lock. Root-only bones are rejected.
- Capture uses `EditorGizmoController.getWorldBonePivot` with the actual
  prepared native model. The editor checks that its kinematic hierarchy is
  reasonably close (within 0.20 blocks); if not, pinning fails visibly rather
  than manufacturing a seemingly successful world lock.
- `EditorWorldBonePoseRetarget` converts the immutable world target through
  the actor's current Record X/Y/Z/yaw into model space. FABRIK preserves the
  measured length of every link. `EditorWorldBoneRetargetMath` converts
  direction changes into the native Blockbuster X/-Y/-Z rotation convention.
  **Only temporary POSE snapshots receive the corrected local Euler angles**:
  no keyframe spam, scaling, Morph changes or Record edits.
- The pin remains active in other editor modes and also applies while that
  actor is rendered in the background (after selecting a different actor).
  Individual actors retain independent world anchors.
- Anchors are saved as an optional `WorldBonePins` NBT field in the scene's
  `editor.dat` and restored on scene load. Older editor.dat files and original
  Blockbuster Records remain compatible. The first adapter currently allows
  **one pinned chain per actor**, replacing its previous anchor.
- When a pin moves outside the original limb's reach, the chain reaches as
  far as possible **without lengthening bones**. Preview shows an out-of-reach
  warning. Large FK/native mismatch or degenerate segments fail safely.
- This is the **first testable native adapter, not a verified production IK
  feature**. Emoticons/Chameleon, multi-pin constraints on the *same* actor,
  angle limits, rest-pose calibration and acceptance against all character
  model scales are pending. Do not claim they work yet.

## AI assistant (offline panel + approved actions)

- Open the integrated panel using **Ctrl+Shift+A**. Type a Russian or English
  command and hit Enter or PREVIEW. A separate **APPROVE** button is required
  for all scene mutations; Esc closes it. Editing the input invalidates an
  old proposal and approvals are one-time.
- `EditorAssistantCore` offers typed HELP, INSPECT, GOTO_FRAME,
  CREATE_BONE_KEY, PIN_BONE, UNPIN_BONE and UNKNOWN proposals. The screen
  validates actor IDs, bone names, timeline bounds, and supported pin rigs.
  Scene-level navigation works without a selected actor.
- No model, external API connection, password/token storage, network access,
  arbitrary source execution, silent commands or background worker exists.
  An eventual LLM must output a validated proposal and never write directly
  to the editor's animation data.

## Required manual checks before merging

1. Blockbuster / Emoticons / Chameleon: hover and LMB on *different*
   bones, create the key at exactly the current tick, no source-pose
   jump; switch MODE between Position and Rotation and drag a limb.
2. Drag an already keyed bone, undo/redo or re-open the scene; confirm
   no duplicate keys and no changes to unrelated actors.
3. Occlusion, overlapping bones, thin fingers, zoom, camera yaw,
   shader/FBO and a bodypart attached to a moving actor. Confirm green
   wire is not in exported video.
4. Blockbuster POSE world pin: create a pin on a foot at a paused frame,
   move the Record root while previewing and rendering at 60 FPS. Check the
   world position through several actor yaws, camera moves and multiple
   zooms; confirm that nearby joints bend rather than grow in scale.
5. Select another actor: the original actor's foot should remain pinned
   in the background. Save/re-open the scene and check that the anchor
   persists. Unpin and verify original authored movement returns.
6. Attempt to pin Emoticons / Chameleon, the actor root and degenerate
   bones: controls should refuse unsupported rigs without changing animation.
7. Assistant: Enter must only preview; APPROVE executes a valid allowlisted
   command exactly once; unknown text, stale actor selection, and rejected
   pin requests must never change a scene.

Build: `.github/workflows/editor-bone-tools-java8.yml` runs Java 8
FABRIK, local-Euler retarget and assistant authorization tests, plus full
Forge 1.12.2 compilation, producing a Jolt-bundled test JAR. All native
world-lock motion still requires interactive game acceptance. This branch
remains a draft against `main`.
