package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Complete skinned Emoticons PARENT collision hull. Hair can be attached
 * to ANY bone: every available head/torso/arm/leg mesh group is captured
 * independently from that attachment.
 *
 * Mesh geometry is fitted in each physical region's bone LOCAL frame once
 * and then driven by current post-render Emoticons bone matrices each tick.
 * Fixed dimensions are vital: a changing world-aligned limb AABB would
 * otherwise trigger a Jolt rig reconstruction whenever the limb rotates.
 *
 * These are conservative kinematic BOX approximations, not triangle-
 * perfect collisions with a deformable skinned mesh.
 */
public final class BodyPartEmoticonsFullBodyColliders {
    public static final class Capture {
        public final List<float[]> boxes,shapes;
        public final List<String> owners;
        public final int groups,triangles;
        Capture(List<float[]> boxes,List<float[]> shapes,
                List<String> owners,int groups,int triangles){
            this.boxes=Collections.unmodifiableList(boxes);
            this.shapes=Collections.unmodifiableList(shapes);
            this.owners=Collections.unmodifiableList(owners);
            this.groups=groups;this.triangles=triangles;
        }
        public boolean isReady(){return !shapes.isEmpty();}
    }
    private static final class LocalBox {
        final String bone,owner;
        final float[] local;
        LocalBox(String bone,String owner,float[] local){
            this.bone=bone;this.owner=owner;this.local=local.clone();
        }
    }
    public static final class Cache {
        private final List<LocalBox> local=new ArrayList<LocalBox>();
        private int groups,triangles;
        private String meshFingerprint="";
        public void clear(){
            local.clear();groups=triangles=0;meshFingerprint="";
        }
    }
    private BodyPartEmoticonsFullBodyColliders() {}

    private static Capture empty(){
        return new Capture(new ArrayList<float[]>(),
                new ArrayList<float[]>(),new ArrayList<String>(),0,0);
    }

    public static Capture capture(Object morph,float[] actorFrame,Cache cache){
        if(!(morph instanceof AnimatedMorph) ||
                actorFrame==null || actorFrame.length!=16 || cache==null)
            return empty();
        List<EmoticonsCollisionGeometryProvider.MeshSnapshot> sources=
                EmoticonsCollisionGeometryProvider.snapshot(morph);
        StringBuilder token=new StringBuilder();
        for(EmoticonsCollisionGeometryProvider.MeshSnapshot src:sources)
            if(src!=null)token.append(src.name).append(':')
                    .append(src.vertices.length).append(':')
                    .append(src.triangles.length).append(';');
        if(!token.toString().equals(cache.meshFingerprint))
            cache.clear();
        if(cache.local.isEmpty()){
            cache.meshFingerprint=token.toString();
            List<BOBJBone> bones=EmoticonsModelAccess.getBones((AnimatedMorph)morph);
            for(EmoticonsCollisionGeometryProvider.MeshSnapshot src:sources){
                if(src==null)continue;
                for(EmoticonsSkinCollisionGroups.Group group:
                        EmoticonsSkinCollisionGroups.Group.values()){
                    // OTHER collects unweighted/unknown vertices. When actual
                    // body groups exist it could form a huge second collider
                    // spanning the entire actor and repel hair sideways.
                    // Preserve it only for meshes with no recognized groups.
                    if(group==EmoticonsSkinCollisionGroups.Group.OTHER &&
                            cache.groups>0)continue;
                    EmoticonsCollisionGeometryProvider.MeshSnapshot region=
                            EmoticonsSkinCollisionGroups.select(
                                    morph,src,EnumSet.of(group));
                    if(region==null||region.triangleCount()<8)continue;
                    String name=bestBone(bones,group);
                    float[] matrix=name==null?null:
                            BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                                    morph,name);
                    int slices=(group==EmoticonsSkinCollisionGroups.Group.HEAD)?1:
                            (group==EmoticonsSkinCollisionGroups.Group.TORSO)?2:3;
                    List<float[]> local=matrix==null?
                            EmoticonsSkinnedBodyHull.boxes(
                                    region.vertices,region.triangles,slices,.018F):
                            EmoticonsSkinnedBodyHull.boneLocalBoxes(
                                    region.vertices,region.triangles,
                                    matrix,slices,.018F);
                    if(local.isEmpty())continue;
                    // If no reliable bone matrix exists, the hull is still
                    // able to follow root actor motion without guessed bones.
                    if(matrix==null)name=null;
                    cache.groups++;cache.triangles+=region.triangleCount();
                    for(float[] shape:local){
                        if(cache.local.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)
                            break;
                        cache.local.add(new LocalBox(name,
                                "emoticons."+group.name(),shape));
                    }
                }
            }
        }
        List<float[]> shapes=new ArrayList<float[]>();
        List<float[]> boxes=new ArrayList<float[]>();
        List<String> owners=new ArrayList<String>();
        try{
            for(LocalBox part:cache.local){
                float[] bone=part.bone==null?
                        BodyPartPhysicsMatrixSpace.identity():
                        BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                                morph,part.bone);
                if(bone==null)return empty();
                float[] toWorld=BodyPartPhysicsMatrixSpace.multiply(actorFrame,bone);
                float[] world=EmoticonsSkinnedBodyHull.transformRigid(
                        toWorld,part.local);
                float[] primitive=JoltColliderPrimitives.encode(
                        world,JoltColliderPrimitives.BOX,1F);
                boxes.add(JoltColliderPrimitives.toOutline(primitive));
                shapes.add(primitive);
                owners.add(part.owner);
            }
        }catch(RuntimeException error){return empty();}
        return new Capture(boxes,shapes,owners,cache.groups,cache.triangles);
    }

    /** Select an actually rendered skinning bone, never assume an armature
     * is attached to the same Emoticons bone as the user's hair BodyPart.
     */
    private static String bestBone(List<BOBJBone> bones,
            EmoticonsSkinCollisionGroups.Group group)
    {
        if(bones==null || group==EmoticonsSkinCollisionGroups.Group.OTHER)
            return null;
        String chosen=null;
        int score=Integer.MAX_VALUE;
        for(BOBJBone bone:bones){
            if(bone==null||bone.name==null||
                    EmoticonsSkinCollisionGroups.classify(bone.name)!=group)
                continue;
            String n=bone.name.toLowerCase(Locale.ROOT);
            int rating=n.length();
            if(n.startsWith("low_")||n.contains("lower"))rating+=12;
            if(group==EmoticonsSkinCollisionGroups.Group.TORSO &&
                    (n.equals("body")||n.equals("upper_body")))rating-=20;
            if(rating<score){score=rating;chosen=bone.name;}
        }
        return chosen;
    }
}
