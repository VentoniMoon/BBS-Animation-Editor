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

Future **Skirt B** is a coupled cloth model with lateral constraints
between neighboring strips, avoiding large visible gaps. This is not
yet implemented; a group of independent hanging chains cannot be
claimed to act like a single stitched cloth surface.

`Bake Physics` into Blockbuster `.dat` is **not a requirement**:
the intended output is direct physics rendering in BBS Animation Editor.
