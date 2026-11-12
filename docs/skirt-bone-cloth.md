# Physics 2.0 — Chameleon skirt, source-authored Bone Cloth

**Baseline:** `milestone/physics-2-ragdoll-stable-20261111`
(`ed3be3e316cb59d8bdbd11d7d9ae1a7a044a1eb1`). Active development
is on PR #166, not on `main`.

## Ground truth: uploaded skirt.geo.json

The user-provided Bedrock geometry (`format_version: 1.12.0`,
`identifier: geometry.unknown`) contains **44 named bones and 43
cubes**. There are **20 independent panels with two driven bones each**.

* **ALL**: visible waist belt (3 cubes), and the master animated skirt root.
* **Front anchor `group42`**: seven chains, grouped 3+2+2 for centre,
  outer and angled corners:
  * centre `group2→group22`, `group6→group24`,
    `group3→group25`
  * outer `group4→group23`, `group5→group26`
  * angled `group18→group29`, `group17→group27`
* **Rear anchor `group → group43`**: note `group` has a **180°
  authored Y rotation**. The rear panels are:
  * centre `group7→group38`, `group16→group39`,
    `group8→group40`
  * outer `group9→group37`, `group10→group41`
  * angled `group20→group35`, `group19→group36`
* **Left side**: `group14→group31`, `group15→group32`,
  `group21→group30`
* **Right side**: `group13→group34`, `group12→group28`,
  `group11→group33`.

The earlier assumed one-waist/seven-strip topology was **not** sufficient
for this file. That earlier generic preset remains available for other
models, but is not used for activating the whole submitted skirt.

## Activation

1. Import the skirt as a **Chameleon Body Part**, attached to the
   character waist or pelvis. Its model hierarchy must expose `ALL`.
2. Open **BODY PARTS → internal bone editor**, select exactly `ALL`,
   then **PHYSICS → CHAINS**.
3. Use **SKIRT 20 STRIPS SET**. The preset validates all 44 bone
   names **and parent links before writing any physics settings**.
4. It enables eight physics roots: `group42`, `group43`, the three
   left and three right side strip roots. Jolt then expands these
   eight configured roots into **20 independent two-link simulations**,
   following `BodyPartPhysicsChains.collect()`.
5. `ALL`, `group`, `group42` and `group43` stay animated;
   `group42` and `group43` are enabled **as shared anchor settings**,
   not as dynamic bodies. The authored rear Y=180° orientation is retained.
6. Preset defaults: Gravity 1, Stiffness 0.70, Damping 0.40,
   Body Collisions ON. The same per-bone settings serialize to scene NBT.
7. Clicking again disables **only those eight roots**. Older
   seven-strip panel presets are retained for other geometry.

## What is implemented and what still needs to be tested

The prototype reuses the Chameleon Jolt hair-chain backend instead of
adding a new native binary. Each of the 20 independent panels is an
existing two-link Jolt chain with the normal preview pose application and
parent Emoticons body collision sources.

Requires the usual experimental opt-in:
`-Dbbs.editor.physics.jolt.preview=true`.

**In-game validation still required** for real attachments, pelvis/thigh
collision coverage, rotation at 0/90/180 degrees, preserving the Y=180
rear group, very high-speed movement, and actual CAMERA export. These
results are **not** inferred merely from successful Java compilation.
Twenty separate Jolt worlds can also have a measurable performance cost.

## Visual panel coupling (November 12, 2026)

After the first in-game skirt test showed detached panel motion, the
renderer gained `SkirtClothPoseCoupler`. Only the verified 20-strip preset
uses it; unrelated Chameleon hair, ragdolls and physics configurations are
unchanged.

* Collect all 20 native Jolt results before publishing one frame.
* Convert the 40 local axis-angle swings to a **shared world basis**,
  respecting the back anchor's authored 180-degree yaw.
* Sort the source panels around the waist, smooth adjacent motion and
  limit violent individual bending (upper 0.40 rad, lower 0.30 rad),
  with neighboring deviation bounds (0.12/0.16 rad).
* Convert back to local Chameleon swing and apply with the existing
  renderer. If some native samples are absent, no partial skirt pose is
  published.
* Default ON. For A/B testing add
  `-Dbbs.editor.physics.skirt.coupling=false`.
* No replacement Jolt DLL/JAR is needed.

**Limit:** This is conservative **visual coupling** on top of the
existing 20 Jolt worlds, NOT real native cross-strip cloth constraints.
It prevents severe individual-panel divergence but does not guarantee
perfect seam stitching or correct cloth-vs-leg collision under every
pose. Real lateral native constraints and edge-aware cloth geometry
remain future Skirt B work and need in-game validation.

`Bake Physics` into Blockbuster `.dat` is **not a requirement**:
the intended output is direct physics rendering in BBS Animation Editor.

## Adaptive panel width (experimental)

The first walking test showed the skirt strips deflect correctly but
wide strides can still expose gaps at the hem. The optional
`SkirtAdaptiveWidth` visual pass addresses this without changing the
existing Jolt JNI artifact or the character's stride.

* The supplied `skirt.geo.json` has 14 X-wide front/back panels and
  6 Z-wide side panels. Only these 40 proven panel bones are eligible.
* After world-space lateral coupling, measure horizontal distance between
  neighboring **simulated hem tips** and the same clean authored hem tips.
  Width only increases if the simulated gap grows; static seams get no
  compensation.
* The top bone gains at most 7% width; the bottom gains at most 28%.
  Chameleon's `scaleX` or `scaleZ` is multiplied, keeping user-authored
  scale and the vertical `scaleY` intact.
* Width changes share Jolt's frame sampling, time interpolation and
  20-strip atomic publish. Consecutive frames smoothly approach their
  targets (maximum 7% scale change per tick).
* `ALL`, `group42`, `group`, `group43`, other attachments,
  and saved animation keys remain unchanged.
* A/B flag: `-Dbbs.editor.physics.skirt.adaptiveWidth=false`.
  Existing `-Dbbs.editor.physics.skirt.coupling=false` disables the
  whole coupling path. Both default to true.
* Existing body/leg contacts are retained and visual width does **not**
  replace native cloth-vs-thigh collisions.

**Limitations:** This is a bounded visual width correction, not a
continuously stitched 3D cloth mesh.  Some gaps remain when the required
expansion exceeds 28%, panels rotate off their intended width axis, or
native contacts miss a leg. Real in-game testing (step, twist, paused
seek, export) is still required.

## Emoticons leg contacts (November 12, 2026)

The skirt attaches to the Emoticons `body` bone, but the **passive Jolt
collision hull is captured from the full parent actor**. Its leg/hip
proxies follow animated Emoticons bone matrices and do not need to be
children of the skirt's `ALL` bone.

The previous implementation had a contact-cap hazard: it gathered
parent geometry in source-armature order and permanently stopped fitting
late bones at the 32-shape Jolt budget; when attachments consumed the
same global 32 slots, lower-priority objects could further hide thighs.

The new skirt-specific pipeline:
* Preserve up to 128 real parent bone geometry candidates in a bounded
  cache (the **native** budget remains 32).
* Assemble matching box fallback, typed primitive and owner names once,
  so collider metadata cannot drift across paths.
* **Only when the total exceeds 32**, select actual left/right upper-leg
  colliders first, then pelvis, knees/lower legs, torso, other attachments.
  Skirt detection does not depend on the lateral-coupling A/B flag.
* Hair and non-skirt attachments retain the old selection order.
* Every 20 animation frames, log
  `[Jolt][SkirtLegContacts] leftThigh=true rightThigh=true installed=...`
  when both captured thigh proxies reach the native Jolt request. If
  either is false, emit a warning. No fake colliders are invented.

The display of `true` proves selection/forwarding, **not** actual
collision impulses or a triangle-perfect garment. For a first in-game
test, enable `Show Jolt Colliders` and capture a wide walking stride
with/without body collisions. Older Jolt JARs may use boxes instead
of the newer capsule primitive API. Missing thigh source geometry still
requires investigation of the actual Emoticons rig/skin.

## Skirt surface vs moving thighs (experimental)

The November 12 wide-step screenshot still shows the **visible Chameleon
panel** intersecting the character's thigh. This is possible even with
`leftThigh=true rightThigh=true`: the old native limb capsules and Jolt's
central 2-link chain capsules are much thinner than the actual visual
cubes, and the ring smoothing limits bone deflection.

The correction is limited to the validated 20-panel skirt:

* For the skirt only, use each real skinned Emoticons **leg box** as a
  typed BOX collider instead of an inscribed capsule. Hair keeps the
  original capsule primitives.
* After native Jolt and lateral coupling, `SkirtVisibleThighContacts`
  checks sampled **panel surfaces** (left/center/right edges on upper and
  lower sections) against the same selected thigh/lower-leg world OBBs.
* When a panel intersects an actual leg collider, rotate its upper bone
  outward around the animated waist. Include a small response from
  neighboring panels, limit extra swing and clamp the maximum result.
  The panel's original bone animation, native physics/Jolt scene and
  unmodified waist are not touched.
* The adaptive width pass runs afterward, so it can cover seams caused
  by the outward panel deflection. The routine never makes up missing
  Emoticons collision geometry.
* Log every 20 timeline frames:
  `[BBS Animation Editor][Jolt][SkirtSurface] activePanels=...
  maxPenetration=... thighLeft=... thighRight=...`.
* A/B flag `-Dbbs.editor.physics.skirt.visibleThighContacts=false`
  disables only this visual clearance and preserves existing Jolt.

This is a bounded visual/contact correction, **not** a replacement for
native woven cloth or polygon-perfect collisions; extreme leg poses,
wide cubes and unusually rotated garment segments may still intersect.
In-game inspection of the collider overlay and an animated walking test
are required before declaring the bug solved.
