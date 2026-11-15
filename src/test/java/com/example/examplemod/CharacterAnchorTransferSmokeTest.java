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
  System.out.println("CharacterAnchorTransferSmokeTest OK");
 }
}
