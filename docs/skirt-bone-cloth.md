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

## Regression guard: connected-grid cloth vs original skirt shape

The in-game screenshots following PR #179 show a stiff, vertical,
boxy hem instead of the previously rounded skirt, **without solving
thigh penetration**. The cause is that the experimental solver executed
multiple full-ring rest-length/shear constraints on every frame, even
when there was **no leg contact**. This overwrote the existing Jolt
pose rather than merely correcting a local overlap.

This patch explicitly preserves the old appearance:
* `bbs.editor.physics.skirt.connectedCloth` is now **false by default**.
  Original Jolt chain motion, skirt panel smoothing, adaptive width,
  visual Weld and all earlier changes remain available. To run a
  controlled A/B test of the grid, set
  `-Dbbs.editor.physics.skirt.connectedCloth=true`.
* In the opt-in grid, zero confirmed OBB overlap now returns the
  complete original 40-bone Jolt pose **without any modification**.
* A genuine thigh contact activates only the impacted panels and
  their immediately adjacent neighbors. Unaffected panel coordinates
  stay pinned to their original Jolt targets.
* Reduced angular bounds and positional corrections prevent global
  rigidization; waist roots remain pinned. A per-leg OBB that overlaps
  nearly all panels continues to be rejected.
* Pure Java 8 tests now assert **bitwise-identical native pose** for a
  normal walking frame with no leg contact, plus no response to a
  distant leg.

The grid remains a prototype, not true triangle-cloth contact.
Even if these invariants pass, the remaining high-step leg clipping
is unsolved and must be investigated separately. Do not re-enable
the opt-in solver as a production default until a neutral stance and
all six walking frames have been visually tested.

## Scale-aware local thigh clearance (0.85 skirt, November 2026)

The user's new visual A/B showed that Chameleon skirt **Global Transform
0.85** fits the character's silhouette much better, but the unchanged
Emoticons legs penetrate it more frequently than with scale 1.0.
The world-space physics capture already includes the attached morph
scale. **Do not multiply the model or the leg Jolt colliders again.**

A stateless contact-only correction keeps the authored 0.85:

* `SkirtScaleContactResponse.horizontalScale` derives the smaller valid
  horizontal scale from `Global Transform scaleX/scaleZ` (0.6–1 range).
  No scale changes are written to the morph, animation keys, NBT or Jolt.
* For the validated 20-strip skirt only, `SkirtVisibleThighContacts`
  examines the same **already-scaled real Chameleon cube surfaces** and
  parent Emoticons thigh OBBs. A reduced visual scale gets a small
  extra clearance margin (0.023 to at most 0.044 blocks) and a slightly
  stronger local hem angle response. Upper/root angular limits remain
  unchanged; hem swing remains capped at 0.68 rad.
* Return **direct** measured penetration depth per impacted upper bone.
  Neighbor movement is not misreported as actual collider contact.
  Implausible OBB hits on more than 12 of 20 panels cannot drive a new
  whole-ring expansion (previous "umbrella" failure).
* After existing `SkirtAdaptiveWidth.measure`, apply a maximum local
  lower width bump of about 0.10 at 0.85 (fading with smoothstep based
  on depth). One angular neighbor on either side receives only 20% of
  the contact intensity. The original 1.28 lower / 1.07 upper local
  width caps remain; the waist and unaffected panels never expand.
* A zero-contact/neutral pose and global scale >=1 keep the previous
  width results bitwise unmodified. Every timeline frame is
  deterministic, with no hidden accumulation during scrub or replay.
  No extra native Jolt steps, new Jolt JAR or connectedCloth enabled.
* `[Jolt][SkirtSurface]` logs `scale=` and `localContactPanels=`
  alongside contact counts so normal-vs-0.85 comparisons can be made.

A/B disable with
`-Dbbs.editor.physics.skirt.scaleContactCompensation=false`;
ordinary Jolt, Weld and the independent visual collision response
continue unchanged. `connectedCloth=false` remains the default.

**Important:** This is a conservative, panel-local visual contact response,
not triangle-level continuous cloth collision. Large steps can still
penetrate rigid Chameleon cubes. Compare standing and 5–10 frame
walking tests at 0.85 before treating the penetration issue as fixed.

## Unresolved 0.85 skirt thigh intersections: inspect correction *results*

Changing gravity 1.0 / stiffness 0.55 / damping 0.25 to several
reasonable alternatives did not eliminate thigh penetration. The
current issue is **not primarily spring tuning**.

Code inspection shows two concrete constraints:
* `SkirtClothPoseCoupler` limits Jolt swings to 0.40 rad upper /
  0.30 rad lower before 4 smoothing passes and 10 neighbor clamps.
  That can significantly attenuate a collision reaction.
* `SkirtVisibleThighContacts.correct` also caps local correction and
  **discards all of it** when its direct contact filter reports more
  than 12 out of 20 skirt panels. An earlier in-game log captured
  1606 raw surface/body hits for 860 surface samples at frame 40 and
  all 20 panels changing. These raw hits can include multiple body
  boxes per source point; they do not prove 1606 distinct penetrations.
  The newer whole-ring guard may skip that frame entirely.

To distinguish these cases *before* changing collision impulses again,
add diagnostic JVM flag:
`-Dbbs.editor.physics.skirt.contactAudit=true`.

At each timeline tick, the compact `[Jolt][SkirtContactAudit]` line
includes the **actual input/current-frame selected leg OBBs** and the
predicted **clean decoded Chameleon bone pose** BEFORE and AFTER local
collision correction:
`preSources/preUnique/preRaw/prePanels/preDepth`;
`postUnique/postRaw/postPanels/postDepth`;
`correctedPanels`, `originalSwingAbove0.3`,
`coupledSwingAbove0.3`, `suspected` and `strongestLeg`.
`preUnique` counts an actual source point just once, even if multiple
leg OBBs penetrate it. `suspected=possible-all-ring-guard`
identifies a suspected whole-ring early abort; `filtered-or-angle-cap`
means there was a source intersection but no panel was changed.

**Important limitation:** the audit uses the clean decoded skirt cube
surface transformed with the requested *current-frame* Jolt pose. It
does **not** measure final GPU vertices after Weld and width, nor does
it correct the visual one-tick preview lag. A nonzero post count proves
the computed bone correction left penetration; a zero post count with
visible thigh poking through points toward a renderer/mesh-frame-space
disagreement. Compare it with the existing `[Jolt][SkirtPhase]` log.

No physics parameters, Jolt core, loaded DLL, or saved animation are
changed in this diagnostic PR. Use the same compact 0.85 skirt, known
walking animation, Weld ON, native contacts ON, and record all
`[SkirtContactAudit]` lines for five consecutive frames.

## Emoticons leg hull coverage recovery from actual in-game log

The user's `[Jolt][SkirtContactAudit]` capture at scale **0.85**,
frames 27–46, proves this was not a spring-parameter problem:
* `legBoxes=12` from the real Emoticons skin-parent capture;
  `preSources=860` throughout.
* All **20/20** skirt panels appear in contact throughout the entire
  20-frame interval. 621–736 of 860 source points are flagged as
  penetrating leg proxies (many repeated across multiple leg boxes).
* **18 out of 20** frames have `correctedPanels=0`; their
  `postUnique` is exactly equal to `preUnique`. The corresponding
  `suspected=possible-all-ring-guard` is consistent with the guard in
  `SkirtVisibleThighContacts` that ABORTS all local correction after
  more than 12 panels are touched.
* Only frames 33 and 42 adjusted 3 panels each, and the total
  intersection count dropped only a little. Existing
  `preDepth=postDepth=.25` is the numerical **cap** imposed by
  `outwardPenetration`, not a precise measured penetration depth.
* The original Jolt chain swing count over 0.3 rad (31–38 bones)
  drops to roughly 10–11 after `SkirtClothPoseCoupler` smoothing.
  This could be a secondary reason for insufficient movement, but
  raising spring stiffness/gravity/damping did not help the real gait.

The immediate repair isolates **each** parent Emoticons leg OBB:
it counts how many of the real 20 Chameleon skirt strips its predicted
surface points actually overlap in world space. An individual OBB that
covers **more than 12 of 20** strips is implausibly broad for one
human-like limb in this skirt and is skipped ONLY by the final
skirt-specific VISUAL body-contact solver, leaving all normal native
Jolt contacts untouched. If other local boxes remain, they still
generate actual correction and local adaptive width; no blanket
abort is necessary. If all fitted boxes are implausible, the old
global safety guard remains: there is no invented substitute contact.

The source box breakdown is logged per frame while
`-Dbbs.editor.physics.skirt.contactAudit=true`:

`[Jolt][SkirtContactShapes] frame=N scale=.85 maxLegPanels=12
 boxes=emoticons.right_leg#1(panels=20,hits=...,dxyz=...,
 half=...);...`

Where `dxyz` is the colliding OBB center relative to the mean
waist-anchor location; `half` is its REAL fitted OBB half-extents
in blocks. This is what can identify a bad skin fit or coordinate
transform. One additional A/B property
`-Dbbs.editor.physics.skirt.rejectBroadLegBoxes=false`
restores exactly the previous all-or-nothing visual response.

**Safety/limits:** This approach is deliberately conservative.
If several individually plausible boxes collectively cover all
20 strips, the existing final whole-ring guard still cancels the
correction. Native Jolt may still have badly fitted boxes; this
stage cannot repair the skinned-mesh extraction or guarantee
triangle-accurate contact. Do not claim complete elimination
of clipping until in-game `SkirtContactAudit` confirms
`postUnique` significantly decreases without a silhouette regression.

## 2026-11-12: separate upper skirt/hip proximity from true lower-hem contact

The follow-up real gait screenshots still show the lifted thigh poking through
the front of the compact **0.85** skirt, but the new
`[Jolt][SkirtContactShapes]` log exposed the root of a false positive:
three nested/overlapping fitted OBBs per upper thigh, each contacting
9–14 panels near the waist. The *union* of the left and right upper thigh
OBBs frequently marks all 20 panels as colliding.

Previously `SkirtVisibleThighContacts.correct()` claimed to inspect
"ONLY lower-fabric contact", but actually appended exact samples from
BOTH the upper cube tier and the lower cube tier. A contact point near
the natural waist–upper-thigh interface was then counted as a hem
collision. It could trip the 12/20 global anti-umbrella guard and abort
all visual thigh correction. Merely increasing stiffness or contact
force cannot repair this classification mistake.

This change (experimental and isolated):
* The corrective solver examines lower-tier decoded cube vertices only
  **below 68%** of the source root-to-tip length; it retains lower hem
  centerline probes at 80% and 100% when exact samples are missing.
* Per-leg broad-hull rejection now counts collisions against this
  same LOWER-hem contact region instead of upper+lower geometry.
* The existing `auditBonePose` retains its original full source
  sample coverage for historical log comparisons. The new
  `auditHemBonePose` independently counts exact lower-hem intersections.
  With `-Dbbs.editor.physics.skirt.contactAudit=true`, one extra
  `[Jolt][SkirtHemAudit]` line per frame shows
  `hemSources / hemBeforeUnique / hemAfterUnique /
  hemBeforePanels / hemAfterPanels / hemBoxes`.
* Opt out: `-Dbbs.editor.physics.skirt.hemOnlyContact=false`
  restores the previous all-tier corrective sampling and broad-box gate.
* Jolt native collision geometry and time stepping are **unchanged**.
  The 0.85 visual scale, Weld, bone keys, original panel rotation caps,
  and global 12-panel anti-umbrella fallback are unchanged.

This does not create a full deformable cloth surface, and the new
0.68 fraction is an intentionally conservative starting point for
the current skirt topology. The test suite verifies that a hip-level
upper-cube overlap is ignored while a genuine lower-hem overlap still
drives a bounded local correction. In-game comparison of the same
five-frame raised-thigh gait is needed before merging.

## Multi-leg union overflow at frame 67–81: localized contact islands (experiment)

The uploaded `[SkirtHemAudit]` rows for 15 consecutive frames at
scale **0.85** prove that lower-hem sampling alone does **not** fix the
whole-ring guard issue. Exact lower-hem source samples: 331 per frame.
Between 174 and 236 unique points remain inside one or more fitted
Emoticons thigh OBBs. Each thigh box individually covers only about
5–11 panels, but the union of left and right legs covers **14–19**
panels. On 8 of these 15 frames, `correctedPanels=0` and
`hemAfterUnique=hemBeforeUnique`.

The old `SkirtVisibleThighContacts.correct` logic hard-aborted whenever
**more than 12 panels** had any contact, even if *all individual leg
boxes passed the 12-panel broad-hull filter*. This conflated the
union of two plausible separate legs with one impossible huge OBB.

Experimental remedy:
* Preserve per-side measured overlap and angular demand separately
  for Emoticons LEFT/RIGHT legs, plus a tightly bounded UNKNOWN
  owner group. Only applies when the direct union exceeds 12 panels.
* Sort the *actual authored waist anchors* by polar angle (never
  trust Chameleon group enumeration as circle order).
* Preserve the five most strongly overlapping **contiguous angular
  panels per leg**, and at most two for unlabeled bones. This caps
  the direct corrective footprint to 12/20 and keeps any single leg's
  correction local. One gentle neighboring response remains as before.
* Keep the previous response entirely untouched when the union has
  <=12 direct panels; explicitly reject the entire response if all
  individual colliders are impossibly overbroad. This protects the
  previously reported cylindrical umbrella regression.
* The contact width expansion now receives **only** selected direct
  bone depths; no phantom all-ring width increase. The original
  Jolt simulation, native body collision boxes, Weld, skirt 0.85
  scale, chain smoothing and angular limits are unchanged.
* Reversible A/B:
  `-Dbbs.editor.physics.skirt.localContactIslands=false`
  restores the exact earlier full-ring abort.
* With `-Dbbs.editor.physics.skirt.contactAudit=true`, the
  per-frame `[SkirtHemAudit]` also reports
  `directCandidates`, `directSelected`, and `islandsLimited`.
  The old `[SkirtContactAudit] suspected` distinguishes the
  per-leg-islands response from a true whole-ring abort.

This is **not** a final collision solver: a maximum of 12 rigid
two-bone panel strips cannot conform exactly to every thigh surface.
It repairs the inappropriate zero-response branch without making
the previous unsafe assumption that *all 14–19* contacting panels
should immediately swing outward. Compare *the exact same* five-frame
walking step before/after with Weld ON. Only an in-game decrease in
`hemAfterUnique` without a flared silhouette validates this path.

## Adaptive Hem Lift – limited collision-only extra swing at the skirt corner

At compact scale **0.85**, the user observes one skirt corner and two
front-adjacent panels still allowing the raised thigh to pass through.
Latest `[SkirtHemAudit]` frame 42–56 confirms that the existing
per-leg islands now operate (e.g. frame 46: 199 -> 144 remaining
hem-point intersections; frame 50: 186 -> 183), but repeated contact
points survive the normal bounded lower-bone bend.

This PR adds the opt-in-reversible **Adaptive Hem Lift** *after*
the existing Jolt → `SkirtClothPoseCoupler` → leg contact/island solver.
It is not another unconditional global stiffness or gravity change.

* Only the recognized 20-strip / 40-bone skirt on a valid compact
  `Global Transform` (horizontal visual scale < 0.995) can use it.
* Choose at most **one direct contact center plus two true angular
  neighbors per named left/right thigh** (maximum six lower bones
  overall). Respect the selected contact-island mask: never react to
  rejected broad OBBs or a fabricated contact.
* The previous normal hem limit is approx **0.675 rad** at scale 0.85.
  In a proven collision, allow up to **+0.28 rad** for the contact center
  (approx **0.955 rad / 55 degrees total**). Neighbors receive just
  38% of that extra budget (approx 0.781 rad total).
  Upper waist bones and unaffected skirt sections are never modified
  by this additional stage.
* Rebuild the **real decoded lower Chameleon cube points** after
  each trial rotation; measure uncapped radial depth inside the same
  accepted active animated Emoticons OBBs, not just a Jolt bone capsule.
  Inspect increasing swing trials through the safe budget, and commit
  only the best pose if it actually reduces residual geometric overlap.
  No imaginary outward force is applied when nothing intersects.
* Everything is stateless per completed physics frame. The same
  frame / same input produces the same corrections on replay, manual
  scrubbing or video export; no hidden cumulative panel stretching.
  `connectedCloth` stays off by default and standard Weld remains on.
* Debug on `-Dbbs.editor.physics.skirt.contactAudit=true`:
  `[SkirtHemAudit]` reports `liftedHemPanels` and
  `liftOverlapReduction` in addition to `hemBeforeUnique`,
  `hemAfterUnique`, `directCandidates`, `directSelected`.
* Disable only the new feature for A/B:
  `-Dbbs.editor.physics.skirt.adaptiveHemLift=false`.

**Known limitations:** These are 2-bone rigid Chameleon panels, not a
fully deformable continuous cloth mesh. In particular, moving the lower
joint may not fully clear a raised thigh underneath the UPPER part of
the same cuboid, and the source audit does not include the final GPU
Weld/width modifications. The extra lift is therefore bounded,
conditioned on actual improvement, and must be validated on the
same in-game wide-stride frames before merging to main.

## Adaptive Skirt Collision 2.0 — supported hinge-area two-bone correction

After PR #186, in-game frames 99–108 at authored `Global Transform
0.85` show that Adaptive Hem Lift really operates:
9/10 frames have additional lower lifts, and the measured full lower
hem source-point collisions fall by 31.6% in aggregate. Still,
1,382 hem source-point crossings remain across ten ticks. The
remaining front/corner hole is often ABOVE the final bottom 32% of
the skirt, around the upper/lower cuboid hinge. For example at frame
101, the full leg audit sees 14 contacted panels yet only one direct
lower hem candidate is selected; no additional hem lift occurs.

New independent collision-only second stage, reversible via
`-Dbbs.editor.physics.skirt.adaptiveMidCollision=false`:

* Keep original native Jolt, `SkirtClothPoseCoupler`, native Emoticons
  leg OBBs, 20x2 bone chains, `0.85` authored scale, Weld and existing
  `AdaptiveHemLift` unchanged. This code runs only AFTER those stages.
* Evaluate real decoded **upper and lower Chameleon cube vertices from
  40–70% of waist-to-tip length**, excluding 0–40% near the resting
  waist. No global cloth-ring constraints or speculative widening.
  Only the active/accepted OBBs from the existing outer-leg gating
  are eligible; discovery and evaluation use this SAME collider set.
  Each source point contributes at most its deepest OBB overlap so
  the three overlapping Emoticons-fit boxes do not triple-count it.
* A middle candidate must be within two actual angular neighbors of
  an already accepted lower-hem contact (and report nontrivial
  middle-fabric penetration). This prevents reactivating rejected
  broad colliders or using unrelated waist-grazing geometry.
* Select one true angular contact center and up to two neighbors PER
  named left/right leg, using the existing deterministic contact-ring
  selection; no more than six mid-correction panels per frame.
* Trial rotations pivot around the existing upper and lower bones.
  A root section can receive up to **0.13 rad** extra (absolute cap
  0.65 rad); the lower section is tested from its current adaptive
  hem angle, within that method's existing collision-only cap.
* Commit a trial only when real mid-fabric penetration DECREASES,
  the combined mid+hem residual decreases, and the lower hem
  residual does not worsen by more than a tiny numeric tolerance.
  Reject all unhelpful trials and retain the exact pre-stage pose.
  All operations are deterministic and stateless for timeline scrub
  and direct video rendering.
* `[Jolt][SkirtHemAudit]` and `[Jolt][SkirtContactAudit]` now include
  `midCandidates`, `jointLiftPanels`, `midOverlapReduction`.
  These reflect collision improvement in the accepted fitted
  source-point geometry, **not** a full GPU Weld mesh guarantee.

Standalone Java 8 smoke tests compare the same contact with the new
stage disabled/enabled; verify a real beneficial middle-joint motion,
no model-scale-one change, no waist-only false alarm, no far panel
flare, no native source-pose mutation, and deterministic rerender.
The final proof still requires identical in-game walking frames
with Weld enabled: compare appearance and the logged metrics before
treating raised thigh clipping as solved.

## 2026-11-12: recover thigh contact from actual outer skirt surface (PR #188)

The 20-frame `[Jolt][SkirtHemAudit]` capture from the 0.85 garment
(frames 115–134) contains 4,077 source-point overlaps before correction
and 2,837 after it, a 30.4% aggregate reduction. Joint mid-fabric
correction operates on 17 of 20 ticks. Frames **118** and **127**
are conspicuous: `hemBeforeUnique=197/195`, respectively,
but `directCandidates=0`, `correctedPanels=0`,
`midCandidates=0`, `jointLiftPanels=0`, and there is no improvement.
This establishes a **detection/correction mismatch**, not that the new
two-bone solver has insufficient angular range on those ticks.

Before checking decoded garment surfaces, the visual solver used a
pure heuristic based on thigh OBB horizontal center/radius support
and the tilt of its longest axis. On a compact skirt, a crossed leg
can intersect exposed cloth while its fit still has an inboard center,
so the heuristic rejects a truly relevant shape. Conversely, simply
removing the heuristic reintroduces the already observed inverted
"umbrella" on standing and weak hip-grazing contact.

The new fallback, controlled by
`-Dbbs.editor.physics.skirt.surfaceEvidenceLegGate=true` by default,
works **only after** the existing per-OBB broad-hull guard, **only**
on compact visual scale (<0.995) and **only** for a shape rejected by
the old center/tilt check:

* Transform actual decoded Chameleon upper/lower cube vertices from
  40–70% of skirt length and lower hem vertices from 68% to tip using
  the current native-coupled two-bone pose.
* For each individual bone strip, take the maximum radial projection
  of these real vertices. Only points in the outer 0.03-block band
  of that panel/region are possible evidence, not innermost faces,
  unknown fake fallback probes or top/waist contact.
* Require **at least two** real outer surface sample points inside
  the fitted, same-frame Emoticons thigh OBB by at least 0.038 blocks
  of radial penetration (including the existing scale-aware surface
  allowance). Benign resting thigh grazing at the inner hem is not
  enough to activate a previously rejected leg.
* Once accepted, use the *exact same OBB* for normal hem contacts,
  contact islands, Adaptive Hem Lift, and upper+lower mid correction.
  No changes to native Jolt bodies, stiffness/gravity/damping, authored
  scale 0.85, Weld, or the original normal center/tilt acceptance path.
* Result adds `gateRescuedBoxes` and `gateRejectedBoxes`,
  logged per-frame in both `[SkirtContactAudit]` and
  `[SkirtHemAudit]`. With the A/B flag set to **false** the
  original preliminary-gate behavior is preserved.

This is a conservative **surface evidence** fallback, not a promise
that every source-point intersection is a visible penetration:
full/hem audit also includes broad inner/back surfaces and colliders
the corrective stage intentionally rejects. Existing global anti-
umbrella protection and spatially limited per-leg contact islands
remain unchanged. Compare identical gait frames around 118, 127,
and the subsequent stride, with Weld enabled; in-game clipping,
number of rescued boxes, and `hemAfterUnique` must all be inspected.
