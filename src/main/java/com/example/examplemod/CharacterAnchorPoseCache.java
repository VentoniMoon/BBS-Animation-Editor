package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Post-RENDER per-actor native pose capture, used only to author anchor
 * transfer keys. The key carries the resulting 4x4 local offset thereafter,
 * so normal playback never needs a second invisible render or a laggy
 * matrix sample from another actor.
 *
 * Explicit frame equality: an unavailable/stale parent bone is NEVER
 * approximated by a record-only hand position; this prevents seams.
 */
public final class CharacterAnchorPoseCache
{
    private static final class Pose
    {
        int frame;
        float[] actorWorld;
        AbstractMorph morph;
    }
    private static final Map<String,Pose> POSES=new HashMap<String,Pose>();
    private static EditorSceneState scene;
    private CharacterAnchorPoseCache(){}

    public static void bind(EditorSceneState current)
    {
        if(scene!=current)
        {
            POSES.clear();
            scene=current;
        }
    }
    public static void clear()
    {
        scene=null;
        POSES.clear();
    }
    public static void capture(BlockbusterSceneActorData data,
            EntityActor entity,int frame)
    {
        if(scene==null||data==null||entity==null||
                data.getId()==null||data.getId().isEmpty())return;
        Pose pose=new Pose();
        pose.frame=frame;
        pose.actorWorld=BodyPartJoltActorSpace.worldFromActor(
                entity.posX,entity.posY,entity.posZ,entity.renderYawOffset);
        pose.morph=entity.getMorph();
        POSES.put(data.getId(),pose);
    }
    /** Exact rendered animated parent bone in world coordinates. */
    public static float[] bone(String actorId,String bone,int frame)
    {
        Pose pose=POSES.get(actorId);
        if(pose==null||pose.frame!=frame||pose.morph==null||
                bone==null||bone.isEmpty())return null;
        float[] local=BodyPartEmoticonsAttachmentProbe
                .lastRenderedBoneMatrix(pose.morph,bone);
        if(local==null)return null;
        return BodyPartPhysicsMatrixSpace.multiply(pose.actorWorld,local);
    }
    public static float[] actor(String actorId,int frame)
    {
        Pose p=POSES.get(actorId);
        return p!=null&&p.frame==frame?
                BodyPartPhysicsMatrixSpace.snapshot(p.actorWorld):null;
    }
}
