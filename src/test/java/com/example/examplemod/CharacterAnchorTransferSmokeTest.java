package com.example.examplemod;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
public final class CharacterAnchorTransferSmokeTest
{
 private static void check(boolean b,String s){
  if(!b)throw new AssertionError(s);
 }
 private static void approx(float[] a,float[] b,String s){
  check(a.length==b.length,s+": wrong size");
  for(int i=0;i<a.length;i++)
   check(Math.abs(a[i]-b[i])<.0005F,
    s+"["+i+"]: "+a[i]+" != "+b[i]);
 }
 private static AnimationTransform t(float x,float y,float z,
         float rx,float ry,float rz,float sx,float sy,float sz)
 {
  AnimationTransform a=new AnimationTransform();
  a.setPosition(x,y,z);a.setRotation(rx,ry,rz);a.setScale(sx,sy,sz);
  return a;
 }
 public static void main(String[] unused)
 {
  float[] bone=AnchorTransferMath.composeBodyPart(
     t(75,20,-50,18,33,-21,1.1F,1.1F,1.1F));
  float[] local=AnchorTransferMath.composeBodyPart(
     t(-16,7,11,27,-35,48,1.2F,.8F,1.5F));
  float[] world=BodyPartPhysicsMatrixSpace.multiply(bone,local);
  float[] rebased=AnchorTransferMath.rebase(world,bone);
  approx(rebased,local,"world-preserving relative anchor");
  approx(BodyPartPhysicsMatrixSpace.multiply(bone,rebased),world,
       "no jump at transfer");
  float[] composed=AnchorTransferMath.composeBodyPart(
          AnchorTransferMath.toBodyPartTransform(local));
  approx(composed,local,"native BodyPart TRS exact");
  float[] reversed=AnchorTransferMath.inverseAffine(bone);
  approx(BodyPartPhysicsMatrixSpace.multiply(reversed,bone),
          BodyPartPhysicsMatrixSpace.identity(),
          "inverse affine with yaw/pitch/roll");
  approx(AnchorTransferMath.matrixFromNBT(
     AnchorTransferMath.matrixToNBT(world)),world,"matrix NBT copy");

  // Reproduction of the visual "bag shrinks/jumps at pickup" bug:
  // Emoticons' renderer multiplies userConfig.scale BEFORE bone.mat.
  // Native BOBJ bone.mat alone does not contain this root scale. The
  // old anchor cache assumed scale=1, so a 0.9375 (15/16) character
  // rendered the carried bag visibly smaller and closer to its origin.
  float[] actorRoot=AnchorTransferMath.composeBodyPart(
      t(160,32,-208,0,37,0,1F,1F,1F));
  float[] rightHand=AnchorTransferMath.composeBodyPart(
      t(7,22,-5,0,-12,24,1F,1F,1F));
  float[] actualHand=AnchorTransferMath.worldFromAnimatedBone(
      actorRoot,.9375F,rightHand);
  float[] ignoringMorphScale=
      BodyPartPhysicsMatrixSpace.multiply(actorRoot,rightHand);
  float[] sourceItem=AnchorTransferMath.composeBodyPart(
      t(172,44,-193,0,28,0,.8F,.8F,.8F));
  float[] wrongOffset=AnchorTransferMath.rebase(
      sourceItem,ignoringMorphScale);
  float[] wrongReconstructed=BodyPartPhysicsMatrixSpace.multiply(
      actualHand,wrongOffset);
  float translationError=Math.abs(
      wrongReconstructed[12]-sourceItem[12])+
      Math.abs(wrongReconstructed[13]-sourceItem[13])+
      Math.abs(wrongReconstructed[14]-sourceItem[14]);
  check(translationError>.02F,
      "old scale=1 approximation must reproduce the bag jump");
  check(Math.abs(wrongReconstructed[0]-sourceItem[0])>.01F,
      "old scale=1 approximation must reproduce bag scaling");
  float[] fixedOffset=AnchorTransferMath.rebase(
      sourceItem,actualHand);
  approx(BodyPartPhysicsMatrixSpace.multiply(
      actualHand,fixedOffset),sourceItem,
      "item world matrix unchanged after pickup at morph scale .9375");
  approx(BodyPartPhysicsMatrixSpace.multiply(
      actualHand,AnchorTransferMath.composeBodyPart(
          AnchorTransferMath.toBodyPartTransform(fixedOffset))),
      sourceItem,"real BodyPart T*Rz*Ry*Rx*S at pickup");
  try
  {
      AnchorTransferMath.worldFromAnimatedBone(
          actorRoot,0F,rightHand);
      throw new AssertionError("zero root scale must be rejected");
  }
  catch(IllegalArgumentException expected){}

  try
  {
   float[] singular=bone.clone();
   singular[0]=singular[1]=singular[2]=0F;
   AnchorTransferMath.inverseAffine(singular);
   throw new AssertionError("singular scale must be rejected");
  }
  catch(IllegalArgumentException expected){}

  BlockbusterSceneActorData actor1=new BlockbusterSceneActorData();
  BlockbusterSceneActorData actor2=new BlockbusterSceneActorData();
  List<BlockbusterSceneActorData> scene=Arrays.asList(actor1,actor2);
  NBTTagCompound morph=new NBTTagCompound();
  morph.setString("Name","item");
  morph.setString("Texture","sample");
  String id="actor:item-source";
  CharacterAnchorTransfers.Transfer grab=new CharacterAnchorTransfers.Transfer(
       id,"item-source","","actor-one","right_arm",20,
       morph,world,rebased);
  CharacterKey k=CharacterAnchorTransfers.upsert(
       actor1.getCharacterTimeline(),grab);
  check(k.hasData(CharacterAnchorTransfers.KEY),"CHARACTER key payload");
  check(k.getFrame()==20,"frame key creation");
  check(CharacterAnchorTransfers.resolve(scene,19).isEmpty(),
      "before pickup source should remain visible");
  check(!CharacterAnchorTransfers.hidesActor(scene,"item-source",19),
      "no premature empty");
  check(CharacterAnchorTransfers.hidesActor(scene,"item-source",20),
      "hide source only while held");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-one",20).size()==1,
      "receiver owns sole item");

  CharacterAnchorTransfers.Transfer handoff=new CharacterAnchorTransfers.Transfer(
      id,"item-source","","actor-two","left_hand",45,morph,world,rebased);
  CharacterAnchorTransfers.upsert(actor2.getCharacterTimeline(),handoff);
  Map<String,CharacterAnchorTransfers.Transfer> at45=
      CharacterAnchorTransfers.resolve(scene,45);
  check(at45.size()==1,"handoff must not duplicate logical item");
  check(at45.get(id).ownerActorId.equals("actor-two"),
      "ownership must switch at exact authored frame");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-one",44).size()==1,
      "before handoff first actor still holds item");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-one",45).isEmpty(),
      "old holder becomes empty on handoff");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-two",45).size()==1,
      "new holder gains item on same frame");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-two",24).isEmpty(),
      "scrubbing backwards restores prior owner");
  // Two persistent editable BodyParts remain in the actor editors,
  // but only one can be rendered at the effective timeline owner.
  check(!CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,19),"no editable item before pickup");
  check(CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,20),"recipient editor part at pickup");
  check(CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,44),"previous owner until handoff");
  check(!CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,45),"old recipient stays editable but hidden");
  check(CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-two",45,45),"new recipient takes over exactly at frame");
  check(!CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-two",45,44),"new recipient must not show early");
  check(CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,25),"reverse scrub restores original editor part");
  // A second transfer back to the same owner is a different editable
  // instance: do not resurrect their original BodyPart at the new key.
  CharacterAnchorTransfers.Transfer returnKey=new CharacterAnchorTransfers.Transfer(
      id,"item-source","","actor-one","left_arm",65,morph,world,rebased);
  CharacterAnchorTransfers.upsert(actor1.getCharacterTimeline(),returnKey);
  check(!CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",20,65),"prior instance must stay hidden");
  check(CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",65,65),"new instance active on return");

  NBTTagCompound saved=k.getData();
  CharacterKey restored=new CharacterKey(20,CharacterKey.Type.CUSTOM);
  restored.setData(saved);
  check(restored.getData().getTagList(CharacterAnchorTransfers.KEY,10)
      .tagCount()==1,"save/load anchor key in Character NBT");
  check("sample".equals(CharacterAnchorTransfers.keys(
      actor1.getCharacterTimeline()).get(0).morph.getString("Texture")),
      "source morph NBT not replaced by EMPTY");

  NBTTagCompound another=new NBTTagCompound();
  another.setString("Name","custom");
  CharacterAnchorTransfers.Transfer part=new CharacterAnchorTransfers.Transfer(
      "part:p:stable-uuid","p","stable-uuid","actor-one","left_arm",
      55,another,world,rebased);
  CharacterAnchorTransfers.upsert(actor1.getCharacterTimeline(),part);
  check(CharacterAnchorTransfers.hidesPart(scene,"p","stable-uuid",55),
      "native body part hidden only once moved");
  check(!CharacterAnchorTransfers.hidesPart(scene,"p","stable-uuid",54),
      "body part restored when seeking before transfer");
  // World release: the source Record must remain visually hidden,
  // while the item is no longer parented to either character.
  // Its frozen full matrix/Morph comes from the release key, and
  // survives save/load, forward play and backwards seek.
  float[] droppedWorld=AnchorTransferMath.composeBodyPart(
      t(230,16,-205,17,31,-12,.7F,.7F,.7F));
  CharacterAnchorTransfers.Transfer release=
      new CharacterAnchorTransfers.Transfer(
          id,"item-source","","","",75,morph,droppedWorld,null);
  CharacterKey releasedKey=CharacterAnchorTransfers.upsert(
      actor1.getCharacterTimeline(),release);
  check(CharacterAnchorTransfers.resolve(scene,74).get(id).attached(),
      "before release the item still belongs to actor one");
  check(CharacterAnchorTransfers.resolve(scene,75).get(id).released(),
      "item becomes released exactly at the authored frame");
  check(CharacterAnchorTransfers.releasedItems(scene,74).isEmpty(),
      "no premature world copy");
  check(CharacterAnchorTransfers.releasedItems(scene,75).size()==1,
      "one world item on release");
  // A different item (source BODY PART p) is still independently held
  // by actor one at this frame. Releasing the bag must not affect it.
  check(CharacterAnchorTransfers.forOwner(scene,"actor-one",75).size()==1,
      "dropping only removes the released item from its parent");
  check(CharacterAnchorTransfers.hidesActor(
      scene,"item-source",80),
      "do not resurrect original actor at its Record coordinates");
  check(!CharacterAnchorTransfers.isActiveEditableAttachment(
      scene,id,"actor-one",65,75),
      "old holder's editable part is now hidden");
  approx(CharacterAnchorTransfers.releasedItems(scene,88).get(0).worldAtKey,
      droppedWorld,"released matrix stays fixed across frames");
  CharacterAnchorTransfers.Transfer loadedRelease=
      CharacterAnchorTransfers.Transfer.load(
          releasedKey.getData().getTagList(CharacterAnchorTransfers.KEY,10)
              .getCompoundTagAt(0),75);
  check(loadedRelease!=null&&loadedRelease.released(),
      "release survives CHARACTER key NBT roundtrip");
  approx(loadedRelease.worldAtKey,droppedWorld,
      "full release orientation/translation/scale persisted");
  check(loadedRelease.boneFromItem==null&&
      loadedRelease.ownerActorId.isEmpty(),
      "release must not inherit any bone owner");

  // BakedPose preserves the evaluated BODY PARTS internal-bone pose
  // (rotation, translation and scale) across a CHARACTER key save/load.
  // A receiving Body Part must not replay the old owner's keys on top.
  check(!loadedRelease.bakedPose,
      "legacy anchor keys must default to an unbaked Morph");
  NBTTagCompound rotatedMorph=morph.copy();
  rotatedMorph.setString("InternalPoseRotation","72.5");
  CharacterAnchorTransfers.Transfer posedRelease=
      new CharacterAnchorTransfers.Transfer(id,"item-source","","","",
          75,rotatedMorph,droppedWorld,null,true);
  CharacterAnchorTransfers.upsert(
      actor1.getCharacterTimeline(),posedRelease);
  CharacterAnchorTransfers.Transfer loadedPosed=
      CharacterAnchorTransfers.Transfer.load(
          actor1.getCharacterTimeline().ensureMainTrack()
              .getKeyAtFrame(75).getData()
              .getTagList(CharacterAnchorTransfers.KEY,10)
              .getCompoundTagAt(0),75);
  check(loadedPosed!=null&&loadedPosed.bakedPose,
      "world release retains the baked internal pose marker");
  check("72.5".equals(
      loadedPosed.morph.getString("InternalPoseRotation")),
      "world release preserves edited internal bone rotation");
  check(!morph.hasKey("InternalPoseRotation"),
      "baking must not alter the original source actor Morph");
  CharacterAnchorTransfers.Transfer posedHandoff=
      new CharacterAnchorTransfers.Transfer(
          id,"item-source","","actor-two","right_hand",90,
          rotatedMorph,droppedWorld,rebased,true);
  CharacterAnchorTransfers.upsert(
      actor2.getCharacterTimeline(),posedHandoff);
  CharacterAnchorTransfers.Transfer loadedHandoff=
      CharacterAnchorTransfers.Transfer.load(
          actor2.getCharacterTimeline().ensureMainTrack()
              .getKeyAtFrame(90).getData()
              .getTagList(CharacterAnchorTransfers.KEY,10)
              .getCompoundTagAt(0),90);
  check(loadedHandoff!=null&&loadedHandoff.bakedPose,
      "handoff retains the baked internal pose marker");
  check("72.5".equals(
      loadedHandoff.morph.getString("InternalPoseRotation")),
      "handoff preserves the rotated Morph, not its initial pose");

  // Re-grab the placed object under its ORIGINAL logical item ID,
  // without duplicating another actor or its source Morph.
  CharacterAnchorTransfers.Transfer regrab=
      new CharacterAnchorTransfers.Transfer(
          id,"item-source","","actor-two","right_hand",90,
          morph,droppedWorld,rebased);
  CharacterAnchorTransfers.upsert(actor2.getCharacterTimeline(),regrab);
  check(CharacterAnchorTransfers.releasedItems(scene,89).size()==1,
      "placed item remains static until next attachment");
  check(CharacterAnchorTransfers.releasedItems(scene,90).isEmpty(),
      "world proxy vanishes on re-grab without a duplicate");
  check(CharacterAnchorTransfers.forOwner(scene,"actor-two",90).size()==1,
      "re-grab restores a single held instance");
  check(CharacterAnchorTransfers.releasedItems(scene,80).size()==1,
      "reverse scrubbing brings back the placed state");
  check(CharacterAnchorTransfers.resolve(scene,64).get(id)
      .ownerActorId.equals("actor-two"),
      "rewinding before placement restores previous owner");
  check(!CharacterAnchorTransfers.hidesActor(
      scene,"item-source",19),
      "rewinding before pickup restores original item actor");

  CharacterAnchorTransfers.Transfer dropPart=
      new CharacterAnchorTransfers.Transfer(
          "part:p:stable-uuid","p","stable-uuid","","",
          70,another,droppedWorld,null);
  CharacterAnchorTransfers.upsert(
      actor1.getCharacterTimeline(),dropPart);
  check(CharacterAnchorTransfers.hidesPart(
      scene,"p","stable-uuid",75),
      "released source BODY PART stays hidden on original actor");
  check(CharacterAnchorTransfers.releasedItems(scene,75).size()==2,
      "separate original item and body part may both be released");
  check(CharacterAnchorTransfers.releasedItems(scene,71).size()==1,
      "part source is present in world before the other item's release");
  System.out.println("CharacterAnchorTransferSmokeTest OK");
 }
}
