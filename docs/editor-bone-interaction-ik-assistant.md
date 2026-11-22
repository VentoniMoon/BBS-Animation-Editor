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

## World-space bone lock (foundation ONLY)

- `EditorWorldBonePinRegistry` captures immutable per-actor/per-bone
  WORLD XYZ anchors, isolated across actors.
- `EditorWorldBoneIK.solve` implements a FABRIK chain solver that
  retains **each input joint distance** exactly. Reachable targets
  converge; unreachable targets extend the chain only to its original
  full reach, with **zero mesh/limb scaling**.
- This is **not yet connected to live actor pose snapshots**: a
  model-specific local rotation/quaternion retarget pass is required,
  especially for Emoticons/Chameleon and actor yaw. It must use a
  per-frame render-only pose override rather than writing dozens of
  animation keys every viewport refresh. Pin controls, serialization,
  multiple-pin conflicts and native-rig acceptance tests are pending.
- Do not surface a toggle claiming to lock a bone until the rig adapter
  actually holds it in world space. The assistant action currently
  rejects PIN/UNPIN rather than pretending success.

## AI assistant (foundation ONLY)

- `EditorAssistantCore` contains offline deterministic parsing and
  typed `Proposal` actions: HELP, INSPECT, GOTO_FRAME, CREATE_BONE_KEY,
  PIN_BONE, UNPIN_BONE and UNKNOWN.
- No model, network, API key, background agent, chat UI or script
  executor is enabled. Proposals never mutate the scene.
- `AnimationEditorScreen.previewAssistantCommand` collects a narrow
  editor snapshot. `executeAssistantCommand` requires an **explicit
  approved=true** call. It can navigate to a bounded frame and add a
  selected-bone key. World pin actions fail safely until live IK is ready.
- Future LLM output must be validated into this allowlist; no direct
  Java/reflection/file system execution.

## Required manual checks before merging

1. Blockbuster / Emoticons / Chameleon: hover and LMB on *different*
   bones, create the key at exactly the current tick, no source-pose
   jump; switch MODE between Position and Rotation and drag a limb.
2. Drag an already keyed bone, undo/redo or re-open the scene; confirm
   no duplicate keys and no changes to unrelated actors.
3. Occlusion, overlapping bones, thin fingers, zoom, camera yaw,
   shader/FBO and a bodypart attached to a moving actor. Confirm green
   wire is not in exported video.
4. World IK pure tests assert fixed goal, unchanged root and unscaled
   segment lengths; live/native rig world anchoring must be separately
   implemented and visually validated.
5. Assistant smoke tests verify unapproved/unknown commands never edit.

Build: `.github/workflows/editor-bone-tools-java8.yml` runs Java 8
geometry/assistant tests and full Forge 1.12.2 compilation, producing a
Jolt-bundled test JAR. Branch is intentionally a draft against `main`.
