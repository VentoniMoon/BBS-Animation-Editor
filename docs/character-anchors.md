# CHARACTER Anchors — pickup and hand-off prototype

**BBS Animation Editor · Java 8 / Minecraft Forge 1.12.2**

This stage implements the two requested CHARACTER anchor actions as keyed,
**non-destructive ownership transitions**. It is not a ragdoll or
physics-enabled object-handling mechanism.

## User workflow

### 1 — Pick up a static actor-object

1. In a scene create a character actor **A** with a Record and a second
   standalone actor **ITEM** whose Morph is the object (book, sword, etc.).
2. Select character A and switch to **CHARACTER → ANCHORS**.
3. At a frame where the character reaches the object, select **ITEM**
   from SOURCE ITEM and a receiving hand bone from ATTACH TO BONE.
   The hand must be present in the rendered Emoticons skeleton.
4. Press **ADD / UPDATE KEY**. The key is on the ordinary CHARACTER
   main track at that frame (marked **@** on the CHARACTER timeline) and is
   saved through CharacterKey NBT.
5. From this frame, ITEM actor is visually hidden (equivalent to Empty),
   and a temporary BODY PART with ITEM's original Morph appears on A's
   selected bone. The source actor/Record/Morph is **never modified**.
6. Seek backward before the transfer: ITEM appears at its original
   actor position, the temporary BODY PART disappears.

### 2 — Hand off between two characters

1. After the pickup, select character **B**, place timeline at hand-off.
2. In B's CHARACTER → ANCHORS, choose the original ITEM source (the
   selector displays its current holder A) and B's receiving hand.
3. Press **ADD / UPDATE KEY**. This adds another event for the **same
   ItemId** and records the receiving bone's world-relative attachment
   transform at that exact native frame.
4. At the authored frame, A no longer renders the item BODY PART and
   B begins rendering the **one** temporary BODY PART. Seeking back
   restores A as owner.

The item is conceptually always one object. `Empty` is a **visual
state** and not a destructive morph replacement or a persisted removal.
The underlying original actor and BODY PART models remain in editor.dat.

## Persistence and evaluation

Each receiving actor has ordinary CHARACTER keys on its main track.
A key's `Data.AnchorTransfers` list holds one or more transitions;
this does **not overwrite existing Morph/Skin/Action payloads** on
the same CharacterKey.

Each transition contains the source actor ID, optional persistent
`BodyPartModelData.AnchorId`, stable item ID, receiving actor ID and
bone name, full source morph NBT, plus the 4×4 matrices `World` and
`Local`. No global scene state is destructively changed.

Runtime resolves all active transitions sorted by frame. For any
`ItemId` only its most recent event applies. Preview hides the
source model (actor or original part) and adds one temporary
`BodyPartModelData` on the current owner. For source BODY PARTS it
also copies original bone keys, Character timeline, morph keys,
physics/collision settings and Weld configuration into the transient
attachment; the saved original is not changed. Multiple normal shader,
mirror, export and UI passes do not produce extra stored BodyParts.
Scene switching resets all transient matrix and morph caches.

### Exact world-space rebasing

The editor samples actual animated Emoticons bone matrices **after**
the native actor render, keyed to the original 20 TPS scene frame.

```text
worldFromItem = worldFromOriginalOwnerBone * originalBoneFromItem
newBoneFromItem = inverse(worldFromNewOwnerBone) * worldFromItem
```

It stores `newBoneFromItem` on the CHARACTER key, preserving position,
rotation and scale simultaneously. The renderer uses Metamorph's
`T * Rz * Ry * Rx * S` order and converts between block positions and
editor pixel offsets (16 pixels per block). No Euler angle subtraction
is used, and singular/sheared transforms are rejected, not guessed.

For an original standalone item actor the editor uses the actor's
native rendered world transform; for an existing BodyPart it combines
its parent's native bone matrix and saved relative global transform;
for a second hand-off it uses the previous owner's native bone matrix
plus the item's previously authored offset.

### Safety / known boundaries

- **First tested target:** Emoticons BOBJ characters. The current exact
  bone capture uses `BodyPartEmoticonsAttachmentProbe`; a generic
  Blockbuster/Chameleon custom-rig matrix adapter still needs work.
- Both source and target should have an existing rendered sample of
  that exact frame before the key is authored. If not, CHARACTER
  displays `RENDER TARGET BONE FIRST` or `RENDER ITEM POSE FIRST`
  and does **not** create a potentially jumping anchor.
- This is an initial visual pickup/transfer system. There is not yet
  a separate physics-based "drop to floor" or blended ownership.
- A standalone item's own native morph animation and entity-specific
  render parameters may differ from its new Metamorph BODY PART
  projection; inspect pivot, 3D scale and orientation in-game.
- Transferred parts with an original MORPH should use Morph-backed
  BODY PARTS; the current selector cannot snapshot an attached model
  with no Morph NBT.
- No claim of zero-frame visual seams until tested with two actual
  actors, Blockbuster records and active 60 FPS render interpolation.

## Tests

GitHub Actions runs standalone Java 8
`CharacterAnchorTransferSmokeTest`, covering:

- inverse affine transforms; rotated/scaled BodyPart reconstruction;
- saving/loading 4×4 matrices through NBT;
- fail-closed singular parent transforms;
- pickup visibility before and after a CHARACTER key;
- hand-off actor A → actor B at a frame with no duplicated item owner;
- backward seeking restoring the prior representation;
- source Morph NBT and source BODY PART identity preserved.

Then run the regular pinned Forge 1.12.2 full compile.

### In-game verification (PR branch)

```powershell
git fetch origin
git switch --track origin/feature/character-anchor-transfers-20261115
runClient -PfixMixinAsm
```

Test a static item actor and a **right_hand/right_arm** Emoticons bone
in CHARACTER. Pause at pickup frame to author the key. Then create
a second receiving actor and hand it off. Examine exactly the
transfer boundary at frames f-1, f, f+1; verify no teleport at f,
reverse scrubbing, original Morph/skin, key save/load and native mirror
passes. If the selector reports unavailable pose, choose a frame with
both actors rendered or inspect native BOBJ bone capture diagnostics.

**Do not merge into main before the in-game A/B confirms seamless
handoff and correct native BODY PART render space.**

## Bugfix: small position/size jump when a free item becomes a BodyPart

**Report:** two screenshots of a bag on a stone stand, before/after
pickup into an actor's hand, show the carried bag slightly displaced
and visually smaller. The full 4x4 affine inverse in
`AnchorTransferMath.rebase()` was correct, but its **input native bone
matrix was missing a renderer transform**.

**Root cause:** In Emoticons 1.12.2,
`AnimatorController.render()` applies the actor morph's
`userConfig.scale` **before** yaw and before the native
`BOBJBone.mat` transform. The previous
`CharacterAnchorPoseCache.bone()` assumed only
`worldFromActor * bone.mat`. With a scale such as `0.9375`, a key
looked mathematically exact but the BODY PART was actually rendered
at `worldFromActor * S(.9375) * bone.mat * localOffset`, producing
the reported seam. A hand moving at fractional playback times could
also disagree because the cache used raw `renderYawOffset` while the
native renderer interpolated `prevRenderYawOffset` to it.

**Fix:** `CharacterAnchorPoseCache.capture()` now records both the
native Emoticons `animator.userConfig.scale` and the shortest-path
interpolated body yaw at the **same partial tick** the actor was
rendered. `bone()` composes
`worldFromActor * S(nativeMorphScale) * nativeBone.mat` before
rebasing. The standalone original item actor retains its own original
actor origin; its Morph still performs its normal child animation and
scaling when later attached. No manually guessed Y offset, item-specific
compensation or new playback interpolation is introduced.

At authoring time, the new BodyPart transform is composed again,
compared against the stored original world matrix, and a transfer key
is rejected if its TRS decomposition has a maximum matrix-element
error greater than `1e-3`. One informational log per authoring action
shows `targetMorphScale`, local offsets and `maxMatrixError`.

**Automated regression:** Java 8 smoke test intentionally recreates
the old bag seam with an animated-morph scale of `0.9375`: a carried
source shrinks and shifts when root scale is omitted, and the corrected
world/root/local composition reconstructs the source's complete
translation, rotation and scale. This test ensures the fix does not
silently regress.

**In-game retest:** On the **same item and frame** as the screenshot,
remove/re-create the original transfer key (older keys store the
previous offset and cannot correct themselves). Compare `f-1` and
`f` without changing the camera or character's pose. Confirm both
the bag's position and apparent size remain stable. Then animate the
character's hand, try the transfer into the second actor, reverse
scrub and reload the scene. If the bag still jumps, inspect
`[BBS Editor][Anchors] KEY` in `run/logs/latest.log` and include the
actor Morph type and scale before introducing any further compensation.

Note: the visual equivalence of a **CustomMorph** source actor and a
child BodyPart still needs manual in-game confirmation. The CI test
validates affine math and the precisely known Emoticons renderer root
transform, not actual GPU raster output.

## Editable transfer recipients (BODY PARTS integration)

Previously, ANCHORS added the handed-off object only to the private
renderer cache. The destination actor's BODY PARTS model list and
editor.dat never contained this object. This meant there was no
MODEL, GLOBAL TRANSFORM or keyed bone inspector for the new object.

Each transfer now registers **exactly one real BodyPartModelData** in
the receiving actor's `BodyPartsEditorController` collection. Its
stable identity is **ItemId + owner actor ID + CHARACTER key frame**,
not the preview renderer's temporary object or a UI timeline ID.

In the editor:

1. Select the actor who receives the item and create the CHARACTER
   anchor key as before.
2. Switch to BODY PARTS. The received prop is a regular attachment
   on the chosen bone, available for MODEL, GLOBAL TRANSFORM and
   per-model bone, Morph/Skin/Character key editing as supported by
   that Morph.
3. Move/rotate/scale the item, save editor.dat and reopen. The
   editable transform persists. An existing old anchor key will
   automatically migrate into an editable model on the next editor
   draw, without manual recreation.
4. At hand-off, the next actor gets a **separate editable** attachment,
   inheriting the previous holder's edited state. Its new local
   offset is calculated using the previous holder's current GLOBAL
   TRANSFORM rather than the original transfer's stale matrix.
5. Scrubbing backward switches the rendered owner again. Both actors'
   BODY PARTS editor entries remain saved, but only the effective
   owner is rendered at each scene time. Repeated handoffs back to
   a prior actor use different event-frame identities.
6. Delete an anchor attachment by removing its CHARACTER transfer key
   (via CHARACTER → ANCHORS). Removing the BODY PARTS rectangle alone
   is intentionally disabled: otherwise the unchanged CHARACTER key
   would recreate the supposedly removed object.

The saved per-model NBT now includes `AnchorItemId` and
`AnchorEventFrame` alongside the existing Morph, attachment bone,
global transform, animation bones, Character timeline, physics and
collision settings. No standalone item actor is destructively
converted to Empty, so seeking before pickup restores the source.

### In-game regression checklist

- Test an **old editor.dat** made before this update with an existing
  ANCHORS pickup key. Open it and confirm an editable BODY PARTS row
  appears for the receiver. Save/reload and check no duplicates.
- Change the new attachment's global X/Y/Z and rotation/scale in BODY
  PARTS. Confirm the rendered bag follows the edits at paused frames.
- Transfer the edited bag to a second actor at a later key. Confirm
  the new actor inherits its visible world transform rather than
  jumping back to the earlier key's original position.
- Scrub before pickup, after pickup, before handoff and after handoff.
  Confirm exactly one visible bag at each frame and that both actors'
  earlier BODY PARTS edits remain accessible.
- Remove a transfer key and inspect both BODY PARTS and the renderer.
  The corresponding keyed attachment should disappear without
  deleting the original bag actor or its source Morph.
- Save/reopen and repeat the same checks with mirrors and export.

The only remaining acceptance criterion that CI cannot establish is
the actual in-game UI and native rendering correspondence.

## Third action — place / release an item into the world

The reverse action from pickup is now available under
**CHARACTER → ANCHORS → PLACE / RELEASE IN WORLD** (RU: **ПОЛОЖИТЬ В МИР**).

It creates an ordinary CHARACTER key for the selected item with:
- the original stable **ItemId** and original source actor/part IDs;
- **no receiving actor and no receiving bone**;
- the item's complete **world 4×4 matrix** captured from the animated
  holder bone and the **current editable BODY PARTS global transform**;
- a copy of the item's **current Morph NBT**, including edits made after
  the previous pickup.

The item switches to the world-released state on this exact frame.
The previous owner's Body Part stays in BODY PARTS for editing older
frames, but is no longer rendered at or after release. The original
source actor/part remains hidden; it must **not** reappear at its
original recorded location.

A separate, **render-only world object** with the same Morph and exact
saved world matrix is drawn through the existing Blockbuster detached
actor renderer. No extra scene actor, Record file or server entity is
created or saved. The renderer applies the full world affine matrix
(including rotation and scale) with the same parent conjugation in
native GBuffer, mirror and shadow replay. Position does not depend
on the current animated hand or subsequent source actor movement.

Subsequent ANCHORS events can **pick up the placed object again** from
its saved world pose, hand it to either actor and release it repeatedly.
Only one logical item is rendered at a given timeline time. Backward
seeking restores the exact previous owner (and its persistent editable
BODY PARTS), or the original item before the first pickup.

### In-game test: pickup → place → pickup

1. Use an actor holding a bag in a hand after the first pickup key
   (e.g. pickup at frame **40**).
2. In BODY PARTS, adjust the bag's global position, angles and/or scale.
3. Select the holding actor, go to CHARACTER → ANCHORS, choose the
   original item (shown as `[HELD]`), and move to frame **60**.
4. Press **PLACE / RELEASE IN WORLD**. Release must be on a *later*
   frame than the pickup. A `WORLD PLACED @ 60` status confirms
   the new CHARACTER key.
5. Move the actor's hands and body at frames 61–100. The bag should
   remain at the *exact world pose* it had at frame 60.
6. Scrub to 59/60/61 and back to 39/40: no double item, no teleport
   to the original source Record position, no visual seam.
7. At frame 110, select either actor and the same logical item
   (`[WORLD]`) and create an ordinary pickup key. The item should
   disappear from its static position and reappear in the hand
   without moving across the transfer frame.
8. Save editor.dat, reload, check the same sequence, then test
   mirror and exported film frames.

A release **freezes** an item, it does **not** apply gravity or
dynamic Jolt physics. To place it naturally on a table/floor, adjust
the body's held item transform or animate the hand into position
before authoring the release key. The current version does not
offer free-world gizmo editing of the released proxy.

### Automated regression

The Java 8 anchor smoke test verifies that a release:
- only takes effect on or after its key;
- hides the source actor/body part but displays no body-part owner;
- retains the entire Morph, world matrix and ownerless state in NBT;
- stays at one unchanged world pose across later scene frames;
- disappears from the world when the same item is re-grabbed;
- restores the prior owner, release and original actor on reverse seek;
- does not affect a second independent item held by the same actor.

Shader/GBuffer, mirror and real-world screen placement still require
gameplay testing. No original Record or Morph NBT is overwritten.

## Fix: edited BODY PART angle reset at world release or actor handoff

### Two independent root causes

1. **Lost internal BODY PARTS pose.** The old release/handoff
   authoring path serialized `BodyPartModelData.getMorphAt(frame).toNBT()`.
   That method reads the *unposed base Morph*. The actual on-screen
   child receives its internal pose only later in
   `BodyPartsPreviewRenderer.createMorphBodyPart()` through
   `applyInternalBonePose()` / Emoticons / Chameleon variants.
   Consequently the created key retained a correct parent-space
   position, but a bag rotated via a BODY PART bone key returned to
   its original angle.
2. **CustomPose was cleared by the released-world renderer.** The
   synthetic world actor (correctly) has no POSE animation snapshots.
   However `BlockbusterActorPreviewRenderer.applyAnimationPose()`
   historically assigned `CustomMorph.customPose=null` whenever
   no POSE snapshots were present. This erased even a correctly
   saved `CustomPose` on a world-released item.

### Changes

- Every actor render retains a *read-only reference* to its prepared
  native BODY PART renderer at the exact frame in
  `CharacterAnchorPoseCache`. On a CHARACTER transfer key click,
  `snapshotPreparedMorph(data)` serializes the **already-evaluated**
  child Morph, including the user's visual internal bone edits.
- New CHARACTER key NBT has `BakedPose=true`. This indicates that
  the Morph includes the visual source pose and **must not** inherit
  and reapply the old owner's internal bone keys, Morph keys or skin
  timeline on the destination. The new owner receives an editable
  snapshot and can animate it independently.
- Existing keys without `BakedPose` still load with their old
  behavior for compatibility. Previously authored *release/handoff*
  keys must be removed and recreated; the original actor/part and
  previous source BODY PARTS edits need not be erased.
- On world-released objects `BlockbusterActorPreviewRenderer`
  no longer calls native editor POSE/Chameleon/Emoticons replay that
  would reset their saved static Morph. Only world-released actors
  use this guard; normal actor animation is unchanged.
- When the current-frame native BODY PART snapshot is not available,
  the authoring UI shows `RENDER ITEM POSE FIRST` and **does not**
  silently create another wrong-angle key.

### Retest: edited bag rotation

1. Frame 40: original bag actor is picked up and registered as a BODY PART.
2. Frame 55: rotate the **bag's internal bone** (not only GLOBAL
   TRANSFORM) by a clearly visible angle; verify the edited pose in
   the normal viewport.
3. At frame 60: select the holding actor → CHARACTER → ANCHORS,
   remove any *old* release key at that frame, then **PLACE / RELEASE
   IN WORLD** again. The bag should keep the exact angle, size and
   world position at frames 59/60/61, and remain stable at frame 100.
4. Reverse scrub to 55 and confirm the edited held bag reappears.
5. Remove any old handoff key, create a fresh actor A → actor B
   handoff and check the bag's internal bone orientation at f-1/f,
   with **one** visible copy. In B's BODY PARTS inspector create
   *new* bone edits, then test release from B.
6. Save and reload editor.dat; compare the same frames again,
   including mirror and shader-enabled rendering. If `RENDER ITEM
   POSE FIRST` appears, ensure both actors were actually drawn at
   that precise frame before clicking.
7. For diagnostics inspect:
   `Select-String -Path '.\\run\\logs\\latest.log' -SimpleMatch '[BBS Editor][Anchors]'`

Automated Java 8 regression checks the `BakedPose` key and edited
Morph NBT roundtrip plus old-key default behavior. Full Forge compile
checks integration. Final GPU-rendered visual parity requires this
in-game test.
