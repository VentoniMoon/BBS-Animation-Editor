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
   main track at that frame and is saved through CharacterKey NBT.
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
`BodyPartModelData` on the current owner. Multiple normal shader,
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
