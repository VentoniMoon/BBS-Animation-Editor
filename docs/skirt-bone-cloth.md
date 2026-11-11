# Physics 2.0 — Chameleon Bone Cloth / Skirt A

**Baseline:** `milestone/physics-2-ragdoll-stable-20261111`, commit `ed3be3e316cb59d8bdbd11d7d9ae1a7a044a1eb1`.

This branch deliberately does **not** modify the working Ragdoll, Impulse,
Gravity or Water solvers. We render physics directly inside the editor;
Blockbuster animation bake is not a prerequisite.

## Authoring contract: seven independent strips (3+2+2)

A Chameleon skirt attached as a Body Part needs one explicitly animated
waistband bone with **exactly seven direct child chains**. Each strip may
contain one or several real, linearly parented Chameleon bones. The groups
`3 + 2 + 2` reflect the agreed seven-strip construction and strip order;
we cannot infer which are front/side/rear without the actual model.

```text
waist (follows pelvis / animated, NOT a dynamic link)
 ├─ skirt_0 -> skirt_0_1 -> ...             group 3
 ├─ skirt_1 -> skirt_1_1 -> ...
 ├─ skirt_2 -> skirt_2_1 -> ...
 ├─ skirt_3 -> skirt_3_1 -> ...             group 2
 ├─ skirt_4 -> skirt_4_1 -> ...
 ├─ skirt_5 -> skirt_5_1 -> ...             group 2
 └─ skirt_6 -> skirt_6_1 -> ...
```

The illustrative names above are NOT mandated. The engine reads the real
`AnimationBone` hierarchy, checks for exactly seven distinct linear
branches, and rejects cycles, repeated bone names, reused bones and
unexpected branch forks rather than silently attaching the wrong geometry.

## Enable prototype

1. Add the **Chameleon skirt morph** as a BODY PART attachment to the actor's
   waist / pelvis.
2. Enter the attached model's internal bone editor and select the shared
   **waistband** bone (the one directly parenting seven strips).
3. Open **BODY PARTS → PHYSICS → CHAINS** and click **SKIRT 3+2+2 SET**.
   If the bone hierarchy is incompatible the inspector shows the reason.
4. On success, the selected waist's existing `BodyPartPhysicsSettings` is
   activated with Gravity = 1, Stiffness = 0.7, Damping = 0.4 and Body
   Collisions = ON. The seven child strips are automatically handled as
   independent chains by `BodyPartPhysicsChains`; the waist remains animated.
5. Save the scene as normal. This configuration uses the already existing
   per-bone NBT field and remains compatible with older scenes.
6. A second click disables the skirt preset on that waist. Other physics
   settings outside this waist are untouched.

## Native physics and renderer

The first stage deliberately **reuses** `BodyPartJoltPreviewAdapter`,
`JoltChameleonPreviewRig` and `JoltHairChain`, which already support
animated root motion, bone-local rotations and parent actor collision
primitives, including Emoticons torso, arms and legs. No second Jolt native
library is added.

For this prototype, the existing opt-in system property is still required:
`-Dbbs.editor.physics.jolt.preview=true`.

Seven independent Jolt chains are initially appropriate for verifying
correct physical rotations, collisions and damping. **Skirt B** will add
neighbor-to-neighbor lateral constraints and better shape preservation
once a real skirt asset confirms the native bone layout. Skirt B is not
faked by drawing extra overlay geometry.

## Practical acceptance tests

- Selecting an invalid bone doesn't modify any physics settings.
- Activating a genuine 7-strip waist makes seven independent Jolt
  chains appear in the log, all parented to the moving waistband.
- Rotate the actor at 0/90/180 degrees; strips move by inertia without
  flipping their local bone axes.
- Walk, run, crouch and turn: cloth follows the waist, doesn't jitter
  under repeated Preview draws or get stuck to a previous actor.
- With body collisions ON, check hip/leg contacts for both sides
  and inspect overlap using the existing Jolt collider debug.
- Save/reopen and confirm enabled physics settings survive.
- Verify no side effects in PHYSICS ragdoll, water or CAMERA preview.
- **Direct video export** needs to be checked separately using the native
  editor export renderer: a preview-only sample is not proof of export.
  Do not introduce Blockbuster `.dat` baking as a workaround.

## What remains before calling skirt finished

The real Chameleon skirt model/geometry has not been committed to this
repository, so the final waist bone mapping cannot yet be verified.
Further work on Skirt B requires the native seven-strip model and a
visual test of pelvis/thigh contacts, frame timing and direct CAMERA
export. Nothing here claims a tested final cloth simulation.
