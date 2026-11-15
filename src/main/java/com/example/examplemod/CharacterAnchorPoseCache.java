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
        float animatedMorphRootScale=1F;
        AbstractMorph morph;
        BodyPartsPreviewRenderer bodyPartsRenderer;
    }
    private static final Map<String,Pose> POSES=new HashMap<String,Pose>();
    private static EditorSceneState scene;
    private static int sceneIndex=Integer.MIN_VALUE;
    private CharacterAnchorPoseCache(){}

    public static void bind(EditorSceneState current)
    {
        int index=current==null?Integer.MIN_VALUE:
                current.getSelectedScene();
        if(scene!=current||sceneIndex!=index)
        {
            POSES.clear();
            scene=current;
            sceneIndex=index;
        }
    }
    public static void clear()
    {
        scene=null;
        sceneIndex=Integer.MIN_VALUE;
        POSES.clear();
    }
    /**
     * Match RenderLivingBase and AnimatorController.interpolate() rather
     * than using the un-interpolated renderYawOffset. Both use shortest
     * body-yaw interpolation at the same native render partial tick.
     */
    private static float renderedBodyYaw(EntityActor entity,float partialTicks)
    {
        float d=entity.renderYawOffset-entity.prevRenderYawOffset;
        while(d < -180F)d+=360F;
        while(d >= 180F)d-=360F;
        return entity.prevRenderYawOffset+
                Math.max(0F,Math.min(1F,partialTicks))*d;
    }

    public static void capture(BlockbusterSceneActorData data,
            EntityActor entity,int frame,float partialTicks,
            BodyPartsPreviewRenderer bodyPartsRenderer)
    {
        if(scene==null||data==null||entity==null||
                data.getId()==null||data.getId().isEmpty())return;
        Pose pose=new Pose();
        pose.frame=frame;
        pose.actorWorld=BodyPartJoltActorSpace.worldFromActor(
                entity.posX,entity.posY,entity.posZ,
                renderedBodyYaw(entity,partialTicks));
        pose.morph=entity.getMorph();
        // Each actor owns a distinct native BODY PARTS renderer. Retain
        // only its latest prepared-frame reference; every query below
        // also validates the exact current frame before taking a copy.
        pose.bodyPartsRenderer=bodyPartsRenderer;
        if(pose.morph instanceof
                mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
        {
            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph animated=
                (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                        pose.morph;
            if(animated.animator!=null&&animated.animator.userConfig!=null)
                pose.animatedMorphRootScale=
                        animated.animator.userConfig.scale;
        }
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
        return AnchorTransferMath.worldFromAnimatedBone(
                pose.actorWorld,pose.animatedMorphRootScale,local);
    }

    /**
     * Snapshot the fully posed Metamorph/Blockbuster child exactly as
     * BODY PARTS rendered it at frame f. The unposed data.getMorphAt(f)
     * is NOT sufficient: its internal pose keys have not yet been
     * applied and caused a visible angular reset on release/handoff.
     *
     * No snapshot from a prior frame is ever accepted.
     */
    public static net.minecraft.nbt.NBTTagCompound posedPartMorph(
            String actorId,BodyPartModelData part,int frame)
    {
        Pose pose=POSES.get(actorId);
        if(pose==null||pose.frame!=frame||
                pose.bodyPartsRenderer==null||part==null)
            return null;
        return pose.bodyPartsRenderer.snapshotPreparedMorph(part);
    }

    /** Returns the exact native AnimatorController root scale sampled
     * at a frame, so seam reports can distinguish pose, scale and pivots.
     */
    public static float nativeAnimatedScale(String actorId,int frame)
    {
        Pose pose=POSES.get(actorId);
        return pose==null||pose.frame!=frame?
                Float.NaN:pose.animatedMorphRootScale;
    }

    public static float[] actor(String actorId,int frame)
    {
        Pose p=POSES.get(actorId);
        return p!=null&&p.frame==frame?
                BodyPartPhysicsMatrixSpace.snapshot(p.actorWorld):null;
    }
}
