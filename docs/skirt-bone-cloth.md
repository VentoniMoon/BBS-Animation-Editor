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

## Correction to over-flared skirt after PR #171

The next in-game screenshot showed **umbrella-shaped overextension**
around the waist, despite the leg colliders being present. The
surface-clearance pass was too aggressive: it sampled high near the
waist, rotated each entire upper chain by as much as 0.95 radians,
propagated contact to neighbors, and repeated the entire operation
three times. Both ordinary resting thighs could therefore generate
continuous outward pressure all around the skirt.

The new conservative clearance policy:

* First ignore thigh box centers sitting within the inner 68% of the
  skirt waist radius. Resting hips can touch a contact proxy without
  inflating the garment.
* Test collisions on the **lower hanging fabric only**, never on
  the upper waist section. Do a single controlled projection instead
  of repeatedly accumulating three sweeps.
* Add at most 0.095 rad (5.4 degrees) to a directly contacted **upper**
  bone and 0.28 rad (16 degrees) to its **lower** bone; only 16% of the
  neighboring impact is carried sideways. The clean `ALL` waistband
  remains unchanged.
* Respect maximum final upper/lower angles and keep source Jolt physics,
  adaptive width, parent collider selection and hair/ragdoll unchanged.
* Test two thighs simultaneously: the entire ring must not inflate.

This is intentionally a **safe, lower-amplitude response**. Very wide
strides may still expose local intersections that need actual native
cloth-body constraints; do not solve those by restoring unlimited
outward rotation. The in-game neutral standing and walking poses both
need validation.

## Contact envelope correction after latest walk screenshots

The November 12 front-view photos show that the skirt has regained its
neutral bell shape, yet a raised leg still emerges through the front
panels. Investigation found the new conservative contact pass rejects
leg colliders solely because their **center** stays close to the waist
center. A high-step thigh can reach a panel with its far corner while
its center remains inward. Therefore the hem never gets an outward
correction even though the body collider exists.

The filter now considers the **whole oriented collider envelope**
(center radius + radial projection of its 3 rotated half-extents).
To avoid restoring the old umbrella/neutral-pose blowout, an envelope
that reaches the skirt only counts when its bone is moved outward from
the waist or its longest fitted axis is tilted by at least ~17°.
The following geometric surface overlap checks and tight per-panel
angular caps are unchanged. No synthetic thigh collider is created.

Standalone regressions cover: raised thigh edge reaching cloth with
the bone center still inside the waist, broad vertical neutral thigh
not expanding the skirt, and original wide-step/mirrored cases.

This repairs the **rejection gate**, not the full cloth simulation.
Minecraft gameplay testing is required to confirm whether the real
Emoticons pose uses the expected mesh-fitted bounds/axes. `[Jolt][SkirtSurface]` logs remain available every 20 frames.

## Collision contact and Weld Geometry observation

The latest user comparison showed that **WELD GEOMETRY ON** reduces
visible thigh penetration, but does not eliminate it. This is expected:
`ChameleonWeldPreviewPass` joins matching **parent-child cube faces for
rendering only**; it restores the vertices after each render. It does not
supply Jolt with new collider shapes or stitch adjacent skirt strips.

The original `SkirtVisibleThighContacts` collision pass sampled three
centerline points with a fixed +/-1px width. The actual `skirt.geo.json`
has 20 skirt panels, with **45-degree angled corner cubes**, individually
rotated cube geometry, and 1x2-pixel side panels. Centerline width samples
do not cover those real visible shapes.

The staged correction (on top of the raised thigh envelope PR) now:

* Reads real **decoded Chameleon quad vertices and quad face centers**
  from the parent/child selected cubes; respects cube rotation and bone
  initial pivot in the current attached actor world transform.
* Projects upper/lower source points through the same Jolt upper/lower
  world-space swings used by the visible skirt, and tests those surface
  points against the fitted Emoticons leg OBBs. The upper waistmost
  28% is excluded to avoid the old umbrella-style flare.
* Retains the previous centerline fallback if a Chameleon cube face is
  unavailable/hidden, and retains tight root/hem response limits.
  Source bones/animations/skin/physics JAR and weld implementation remain
  unchanged.
* Every 20 ticks adds `exactSamples=N exactHits=N weld=true/false` to
  `[Jolt][SkirtSurface]` diagnostics. A positive exact hit proves
  the real decoded skirt surface overlapped a fitted thigh OBB **at the
  same sample**; it does not prove perfect collision clearance.

The remaining limitation is fundamentally structural: twenty independent
Jolt chains with visual welding are still not a continuous constrained cloth
surface. No vertex sample fix alone can guarantee complete elimination of
severe leg clipping; for that, a genuinely coupled cloth rig/solver with
surface contacts is needed. Compare Weld ON/OFF and a high step after the
three CI checks, before merging.

## Circumferential ring seam Weld (experimental, 2026-11-12)

A new under-the-skirt screenshot shows abrupt, disconnected geometry at
the visible lower hem, especially around individual tilted front panels.
This is **not** just missing Jolt leg collisions: the older Chameleon
Weld only joins parent-to-child cube ends (vertical seams), never the
neighboring physical strips around the skirt circumference.

`ChameleonWeldPreviewPass` now recognizes only the exact 44-bone
`SkirtGeoProfile`, and when that Chameleon morph has **WELD GEOMETRY
ON**, it processes both the upper and lower levels of the twenty-panel
ring. The algorithm:

* Sorts adjacent source cubes by their true authored REST centers,
  without assuming that front/rear/side bone names are in angular order.
* Finds genuine vertical edge pairs of decoded Chameleon cube quads,
  incorporating cube rotation/pivot and animated bone matrices.
* Validates nearby source edges (max rest mismatch 0.115 blocks); joins
  corresponding top and bottom edge vertices in animated world space.
* Bounds each visual vertex motion to 0.11 blocks so a large leg lunge
  cannot turn the skirt into a rubber sheet or reopen the umbrella bug.
* Works only inside the scoped Chameleon render and restores every
  edited shared ModelVertex at the end. No .dat keys, actual geometry
  file or Jolt solver state is modified.
* Keeps original hair/other-model Weld behavior; supports an A/B flag
  `-Dbbs.editor.physics.skirt.ringWeld=false` (default true) while
  retaining ordinary Weld if enabled.

**Limit:** This is a render-time **surface seam** correction, not a
native linked cloth solver. If strips are too separated in rest or
animation space, or the one-cube-per-bone test fails, it safely skips
that seam instead of creating distorted stretched geometry. It can
reduce geometric breaks, but does not guarantee perfect collision with
thighs or a truly unified mesh at large angles. Test normal standing,
side views, lifted thighs and Weld ON/OFF.

## Phase alignment experiment for five-tick walks

The user supplied five successive walking frames: the raised leg passes
through the front skirt on three of them. This may be partly a **timing
problem** rather than merely missing body hulls. Native Jolt solves a
frame **after** the Emoticons parent actor is drawn, so
`BodyPartPhysicsFrameState` deliberately shows the preceding completed
physics frame. During playback, the parent leg can therefore already
be in tick N while the skirt is still showing tick N-1.

The experimental `SkirtPoseVisualLead` is enabled only for the
validated, complete **40-bone skirt**, only during normal timeline
playback. It estimates the missing tick N from the two completed
physics poses N-2 and N-1 using shortest-arc quaternion extrapolation.
Every bone's estimated extra swing is strictly capped at 0.12 radians.
Width extrapolation is bounded to 0.035 units per tick and never grows
past its normal upper/hem cap. Missing frames, altered topology,
paused frames, or timeline seeks use the original unpredicted pose.
The Jolt simulation, actor gait, colliders, and export bake remain
unchanged; no additional native stepping or extra actor render occurs.

* Toggle comparison: `-Dbbs.editor.physics.skirt.visualPhaseLead=false`
  to disable the experiment and return to the exact previous behavior.
* Every five frames while enabled, preview logs
  `[Jolt][SkirtPhase] actorFrame=... frozenPhysicsFrame=...
  offsetTicks=1 leadEnabled=true`, making the phase lag visible.
* Java 8 smoke verifies a complete 40-bone, five-tick motion, all
  angular correction bounds, deterministic repeated display,
  and that seeks/rewinds cannot resurrect stale prediction.

**Important:** This is an approximation, not true collision prediction.
It does not know future leg positions or guarantee no body penetration.
At sudden gait reversals, the estimate may overshoot. Evaluate the
same five frames with this flag ON/OFF and compare shape and clipping
before merging.

## Connected skirt grid experiment (November 12)

The latest six-frame walk still shows a thigh passing through the skirt
despite Weld, real Chameleon surface probes and one-tick pose lead.
These fixes improve appearance, but **20 separately simulated Jolt
hair-like strips do not form a shared physical cloth**.

`SkirtConnectedClothSolver` is an experimental post-Jolt, pure-Java-8
position-based constraint stage. It works on the validated 20-strip,
two-link Chameleon skirt only:

* Uses real world-space native rest joints, terminal tips and the Jolt
  swings to build 20 belt anchors, 20 midpoints and 20 hem endpoints.
* Pins all belt anchors to the animated actor. Projects vertical segment
  lengths, neighboring mid/hem ring links and cross-panel diagonal links
  together for nine iterations, so no strip can move entirely separately.
* Projects the shared nodes and link centers outward from the **actual
  fitted Emoticons leg OBBs**, with a small surface thickness. Badly
  oversized/overlapping leg boxes are ignored rather than inflating
  the entire skirt.
* Restricts midpoint/hem translation and subsequent bone rotations, so
  a bad collider cannot turn the skirt into a rigid umbrella. Converts
  corrected world links back to native Chameleon local axis-angle,
  including the rear Y=180 rest basis.
* Jolt is still responsible for time evolution; this grid is the shared
  constraint/contact solve applied to its pose before the existing
  adaptive-width and visual Weld stages. Old independent
  `SkirtVisibleThighContacts` is bypassed only while the unified
  grid is enabled.
* A/B flag:
  `-Dbbs.editor.physics.skirt.connectedCloth=false`
  restores the previous behavior.
* Log `[Jolt][SkirtClothGrid] frame=... panels=...
  colliders=... ignored=... contactBefore=... contactAfter=...
  seamStretch=...` every 20 ticks.

**Limitations:** This is a connected **bone rig**, not a complete
triangle-mesh cloth solver. Source cuboids remain rigid between the
two physical joints, and a rapid high-step can still cross cube
surfaces even if the joint endpoints are outside the leg. Contact
projection is intentionally bounded and may not resolve very deep
penetrations in one frame. In-game checks on neutral standing,
five-tick walking sequence, underside, and Weld ON/OFF must precede a
claim that clipping is fixed. This experimental change is not for
production release until validated.
