# Physics 2.0 — Ragdoll

Status: **Stage 1 implemented (data, skeletal presets, CHARACTER key UI)**.
**No Jolt full-body simulation or hits are active yet.** This note documents
the shape of the next implementation so UI keys do not need migration.

## Stage 1: bone binding and CHARACTER physics channel

- Each actor already owns a CHARACTER timeline. Its **main** track's
  universal CharacterKey can now hold `Data.Ragdoll.Weight` (float 0..1),
  without changing the legacy CharacterKey Type or Morph/Skin/Bones.
- Existing main keys at the playhead are reused; a new key uses CUSTOM.
- Before the first ragdoll weight key: **0**, no physics. Between two
  explicit ragdoll keys: linear interpolation. After the last key:
  hold the last authored weight. A Morph/Skin key in between does not reset
  the ragdoll channel. Author adjacent 0 and 1 keys for a sudden impact.
- CHARACTER → RAGDOLL lists detected bones and has a weight slider,
  ADD KEY/UPDATE KEY, ±10%. Stage 1 is **authoring-only**; the inspector
  clearly says the Jolt solver comes next. The physical pose is not
  currently applied to the rendered actor.
- `RagdollSkeletonDefinition` binds **native** bone names only. Stock
  Blockbuster has a six-body biped. Stock Emoticons has **20 BOBJ
  bones**, but precisely **11 physical deform bodies**, not the previously
  assumed 15. There are no separate stock hand or foot deform bones.
  Missing bones and helpers must not create phantom rigid bodies.

### Verified Emoticons bone inventory (source assets)

Source: [mchorse/emoticons default.bobj](https://github.com/mchorse/emoticons/blob/master/src/main/resources/assets/emoticons/models/entity/default.bobj),
[slim.bobj](https://github.com/mchorse/emoticons/blob/master/src/main/resources/assets/emoticons/models/entity/slim.bobj),
[ragdoll.bobj](https://github.com/mchorse/emoticons/blob/master/src/main/resources/assets/emoticons/models/entity/ragdoll.bobj).
The stock default/slim/3D/simple/plus variants all contain the same 20
armature names and parent hierarchy. These are the **actual** BOBJ names:

| Native bone | Native parent | Physics mapping |
|---|---|---|
| `anchor` | none | Coordinate root / animation only |
| `body` | `anchor` | Pelvis / root body |
| `low_body` | `body` | Chest / spine |
| `head` | `low_body` | Head |
| `left_arm` | `low_body` | Left upper arm |
| `low_left_arm` | `left_arm` | Left forearm / elbow hinge |
| `low_left_arm.end` | `low_left_arm` | Item attachment; not physical |
| `right_arm` | `low_body` | Right upper arm |
| `low_right_arm` | `right_arm` | Right forearm / elbow hinge |
| `low_right_arm.end` | `low_right_arm` | Item attachment; not physical |
| `right_leg` | `body` | Right thigh |
| `low_leg_right` | `right_leg` | Right shin / knee hinge |
| `left_leg` | `body` | Left thigh |
| `low_left_leg` | `left_leg` | Left shin / knee hinge |
| `misc_bone_1` | `anchor` | Emote helper; not physical |
| `misc_bone_2` | `anchor` | Emote helper; not physical |
| `misc_bone_3` | `anchor` | Emote helper; not physical |
| `misc_gbone_1` | none | Global helper; not physical |
| `misc_gbone_2` | none | Global helper; not physical |
| `misc_gbone_3` | none | Global helper; not physical |

The bundled `ragdoll.bobj` contains 22 bones: these same 20 names
plus `low_left_arm.item` and `low_right_arm.item` (two more item
attachment helpers). The special ragdoll animation's parent links are
not all identical to the default bind rig; physical binding must use the
**actual current armature** and reject/adjust incompatible topologies.

### Experimental native Jolt hookup (this implementation)

- `JoltHumanoidRagdoll` (Java 8 optional sidecar): 11 dynamic rigid
  Jolt bodies with lengths and pivots from actual BOBJ head/tail,
  SwingTwist shoulders/hips/neck/spine, Hinge elbows/knees, and local
  flat floor proxy. Includes native `impulse(index,vec,point)` hook.
- `RagdollEmoticonsJoltBridge`: native Emoticons animator runs first,
  samples the clean bone matrices, calls Jolt, blends 0..100% world
  orientation and position, then updates those same BOBJ deform bones.
  Uses per-actor state, 20 Hz physics steps, and native cleanup.
- Developer opt-in: `-Dbbs.editor.physics.jolt.ragdoll=true`. Requires
  existing Java 8 Jolt sidecar and Jolt native library on the IDE
  classpath. No physics mutation if the property is off, JNI missing
  or the influence is 0. Not enabled in export or public JAR by default.
- **Limitations**: only Emoticons hooked in this iteration; simple
  local floor proxy (not real Minecraft terrain), hinge axes initially
  X-aligned and not calibrated to each model's bind frame, seek back
  reseeds from current pose. These remain engineering gates before
  publishing Physics 2.0.

## First physical solver (next stage)

Use the existing Java 8 Jolt backend but **a separate multi-body world**
per actor. Do not repurpose `JoltHairChain`, which is intended for
one-dimensional hanging strands with a kinematic pin.

| Joint | Jolt constraint | Initial range (rest-frame degrees) |
|---|---|---|
| Pelvis → chest | SwingTwist | swing ±30, twist ±30 |
| Chest → head/neck | SwingTwist | swing ±45, twist ±55 |
| Chest → shoulder | SwingTwist | swing ±85, twist ±70 |
| Upper arm → forearm | Hinge | elbow 0..145 |
| Pelvis → thigh | SwingTwist | swing ±65, twist ±40 |
| Thigh → shin | Hinge | knee 0..145 |
| Shin → foot | SwingTwist | swing ±30, twist ±20 |

**These numbers are first-pass safety defaults**, not anatomy-fit
measurements. Hinge axes/signs are model-rest-pose-dependent: derive the
real bend direction before enabling dynamics. Blockbuster's simple
one-piece arms/legs must *not* invent elbows or knees.

Proposed physical step:
1. Resolve actor's native morph/armature and current animation rest pose.
2. Construct capsule/box bodies from actual geometry and pivot matrices.
3. Create joint frames from parent/child rest transforms. Disable collisions
   between directly joined parts; enable ground, world and safe nonadjacent
   body contacts.
4. At a `Weight = 0 → positive` transition, seed physical bodies from the
   *animated* pose and velocity, avoiding teleport/stretch. Animate the
   motor/kinematic targets gradually as the weight decreases.
5. Step Jolt at a fixed 20 Hz with 3 substeps, as existing editor physics.
   Sample rotational blend with quaternion SLERP and root translation with
   interpolation. At 0%, output exactly the original animated pose.
6. Seek backwards / change morph / change actor / open new scene:
   reset native state deterministically. Cached physics is per actor and
   never shared by unrelated BB/Emoticons models. Export samples the
   identical fixed-step solution (or baked frames), not the preview FPS.
7. Only after tests, expose ragdoll simulation and joint visualizers in
   CHARACTER. There is no silent physics activation in Stage 1.

## Impulse system proposal (after body simulation works)

Use **frame-stamped events**, not an always-on force slider.

`ImpulseEvent` fields:
- `id` (stable ID), `frame`, `actorId`, `targetBone` (canonical role),
  `pointWorld` (3D contact position), `directionWorld` (unit vector),
  `impulse` (Newton-seconds or consistently scaled game units),
  `radius`, `falloff` and `enabled`.
- Modes: **Impact** (one rigid body and reaction via joints),
  **Explosion** (radial impulses weighted by distance), **Push** (distributed
  impulse to torso/limbs). Recoil and bullets are ordinary Impact events,
  not different solver code paths.
- Physics contact uses the actual capsule/box collision shape; a raycast
  supplies actor, hit bone, local hit point and normal. The hit point is
  important: an off-center impulse produces *torque*, unlike a force at
  center of mass.
- Event IDs are applied **once per simulation timeline crossing**, never
  once per rendered frame. Seeking / exporting reconstructs from a known
  checkpoint and replays the same events. With ragdoll weight 0, impacts
  do not secretly rewrite pose keys; choose an explicit auto-blend 0→1
  key pair when a bullet should trigger a fall.
- Limit magnitudes, clamp invalid vectors and guard nonfinite transforms;
  no gameplay damage or external mod hooks in the first prototype.

## Verification gates

1. Pure Java 8: bone aliases, unique native bone per segment, parent order,
   joint limits and incomplete-model rejection.
2. Key timeline: Ragdoll weight saved/loaded via existing Character NBT,
   Morph/Skin preserved, 0 before first key, interpolation, hold, actor
   isolation, reverse seek.
3. Native Jolt: free fall, knee/elbow bend limits, pelvis-head chain stability,
   no numerical explosions, no connected-body self contact.
4. Visual: BB + Emoticons standing → falling, 0/50/100% blend, animation
   return, multiple actors, mirrored actor, BSL, fast rewind.
5. Repeated frame-stamped impact should fire only once and be invariant
   between preview FPS and 60 FPS PNG/video export.
