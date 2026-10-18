package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Complete Emoticons PARENT collision volume, independent of where a child
 * Chameleon hair morph happens to be attached. Uses all available skinned
 * mesh geometry and bone-weight labels: head, torso, both arms, both legs,
 * and remaining geometry (when not classifiable by bone names).
 *
 * The result is a sequence of world-space kinematic BOX shapes compatible
 * with the existing Jolt additionalShape bridge. A missing skinned mesh
 * fails closed instead of introducing guessed static cuboids.
 *
 * These segmented boxes are a skinned-mesh APPROXIMATION, not collision
 * against individual triangles; the latter requires a separate native
 * triangle mesh / deformable mesh solution.
 */
public final class BodyPartEmoticonsFullBodyColliders {
    public static final class Capture {
        public final List<float[]> boxes;
        public final List<float[]> shapes;
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

    private BodyPartEmoticonsFullBodyColliders() {}

    public static Capture capture(Object actorMorph,float[] actorWorld){
        List<float[]> boxes=new ArrayList<float[]>();
        List<float[]> shapes=new ArrayList<float[]>();
        List<String> owners=new ArrayList<String>();
        if(actorWorld==null||actorWorld.length!=16)
            return new Capture(boxes,shapes,owners,0,0);
        int groups=0,triangles=0;
        try{
            List<EmoticonsCollisionGeometryProvider.MeshSnapshot> sources=
                    EmoticonsCollisionGeometryProvider.snapshot(actorMorph);
            for(EmoticonsCollisionGeometryProvider.MeshSnapshot src:sources){
                if(src==null)continue;
                for(EmoticonsSkinCollisionGroups.Group group:
                        EmoticonsSkinCollisionGroups.Group.values()){
                    EmoticonsCollisionGeometryProvider.MeshSnapshot region=
                            EmoticonsSkinCollisionGroups.select(
                                    actorMorph,src,EnumSet.of(group));
                    if(region==null||region.triangleCount()<8)continue;
                    int slices=(group==EmoticonsSkinCollisionGroups.Group.HEAD)?1:
                            (group==EmoticonsSkinCollisionGroups.Group.TORSO)?2:3;
                    List<float[]> local=EmoticonsSkinnedBodyHull.boxes(
                            region.vertices,region.triangles,slices,.018F);
                    if(local.isEmpty())continue;
                    groups++;triangles+=region.triangleCount();
                    for(float[] shape:local){
                        if(boxes.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)
                            return new Capture(boxes,shapes,owners,groups,triangles);
                        float[] world=BodyPartJoltActorSpace.box(
                                actorWorld,shape);
                        float[] primitive=JoltColliderPrimitives.encode(
                                world,JoltColliderPrimitives.BOX,1F);
                        shapes.add(primitive);
                        boxes.add(JoltColliderPrimitives.toOutline(primitive));
                        owners.add("emoticons."+group.name());
                    }
                }
            }
        }catch(RuntimeException error){
            // Do not return a half-captured parent with inconsistent topology.
            return new Capture(new ArrayList<float[]>(),
                    new ArrayList<float[]>(),new ArrayList<String>(),0,0);
        }
        return new Capture(boxes,shapes,owners,groups,triangles);
    }
}
