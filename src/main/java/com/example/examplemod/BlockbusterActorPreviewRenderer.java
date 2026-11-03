package com.example.examplemod;

import java.util.List;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class BlockbusterActorPreviewRenderer
{
    private EntityActor actor;
    private boolean preparedForMirror;
    private BlockbusterSceneActorData preparedActorData;
    private int preparedActorFrame;
    private static boolean nativeRasterStateReported;

    /** A/B rendering diagnostics, enabled only by the explicit F10 probe. */
    private static boolean mixedActorDiagnostics;
    private static final java.util.Set<String> printedActorDiagnostics =
            new java.util.HashSet<String>();
    private static final java.util.Set<String> printedMeshDiagnostics =
            new java.util.HashSet<String>();

    public static void setMixedActorDiagnostics(boolean enabled)
    {
        mixedActorDiagnostics=enabled;
        printedActorDiagnostics.clear();
        printedMeshDiagnostics.clear();
    }

    private void diagnoseActorGeometry(
            BlockbusterSceneActorData data,EntityActor actor,
            java.util.List<AnimationBoneSnapshot> snapshots)
    {
        if(!mixedActorDiagnostics||data==null||actor==null)return;
        AbstractMorph morph=actor.getMorph();
        if(morph==null)return;
        boolean selected=this.bodyPartsController!=null;
        String signature=data.getId()+"|"+selected+"|"+
                morph.getClass().getName();
        if(!printedActorDiagnostics.add(signature))return;
        StringBuilder details=new StringBuilder();
        int count=snapshots==null?0:snapshots.size();
        int changed=0;
        if(snapshots!=null)
            for(AnimationBoneSnapshot snap:snapshots)
                if(snap!=null&&(
                        Math.abs(snap.getPositionX())>0.0001F||
                        Math.abs(snap.getPositionY())>0.0001F||
                        Math.abs(snap.getPositionZ())>0.0001F||
                        Math.abs(snap.getRotationX())>0.0001F||
                        Math.abs(snap.getRotationY())>0.0001F||
                        Math.abs(snap.getRotationZ())>0.0001F||
                        Math.abs(snap.getScaleX()-1F)>0.0001F||
                        Math.abs(snap.getScaleY()-1F)>0.0001F||
                        Math.abs(snap.getScaleZ()-1F)>0.0001F))
                    changed++;
        if(morph instanceof CustomMorph)
        {
            CustomMorph bb=(CustomMorph)morph;
            details.append(" model=").append(bb.getKey())
                    .append(" customPose=")
                    .append(bb.customPose==null||bb.customPose.limbs==null
                            ?"null":bb.customPose.limbs.size())
                    .append(" nativeParts=")
                    .append(bb.parts==null||bb.parts.parts==null
                            ?-1:bb.parts.parts.size())
                    .append(" skin=").append(bb.skin);
        }
        else if(morph instanceof AnimatedMorph)
        {
            AnimatedMorph em=(AnimatedMorph)morph;
            details.append(" animation=").append(em.animationName);
            if(em.animator==null||em.animator.animation==null||
                    em.animator.animation.meshes==null)
                details.append(" animator=unloaded");
            else
            {
                details.append(" meshes=")
                        .append(em.animator.animation.meshes.size());
                for(mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh mesh:
                        em.animator.animation.meshes)
                {
                    if(mesh!=null)
                    {
                        mchorse.emoticons.skin_n_bones.api.animation.AnimationMeshConfig
                                config=em.animator.userConfig==null||
                                em.animator.userConfig.meshes==null
                                ?null:em.animator.userConfig.meshes.get(mesh.name);
                        details.append(" [").append(mesh.name)
                                .append(" alpha=").append(mesh.alpha)
                                .append(" configVisible=")
                                .append(config==null?"default":config.visible)
                                .append(" vbo=").append(mesh.vertexBuffer)
                                .append(" indices=")
                                .append(mesh.data==null||mesh.data.indexData==null
                                        ?-1:mesh.data.indexData.length)
                                .append("]");
                    }
                }
            }
        }
        // Record origin distinguishes an actual depth overlap of two
        // actors at the same location from missing Emoticons body meshes.
        // Client array state and active shader help identify cross-morph
        // OpenGL contamination. Runs only for one opt-in A/B snapshot.
        String gl=" program="+
                org.lwjgl.opengl.GL11.glGetInteger(
                        org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM)+
                " clientUnit="+org.lwjgl.opengl.GL11.glGetInteger(
                        org.lwjgl.opengl.GL13.GL_CLIENT_ACTIVE_TEXTURE)+
                " vertexArray="+org.lwjgl.opengl.GL11.glIsEnabled(
                        org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY)+
                " normalArray="+org.lwjgl.opengl.GL11.glIsEnabled(
                        org.lwjgl.opengl.GL11.GL_NORMAL_ARRAY)+
                " arrayBuffer="+org.lwjgl.opengl.GL11.glGetInteger(
                        org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING);
        System.out.println("[BBS Editor][MultiActor A/B] actor="+data.getId()+
                " role="+(selected?"SELECTED":"background")+
                " morph="+morph.getClass().getSimpleName()+
                " pos=("+actor.posX+","+actor.posY+","+actor.posZ+")"+
                " invisible="+actor.isInvisible()+
                " keys="+count+" changed="+changed+details+gl);
    }


    /**
     * A visible Emoticons head with a missing/triangulated body can originate
     * either from CPU skinning (NaN/huge vertex coordinates) or a later GL
     * pipeline state conflict. Sample the actual float buffer AFTER native
     * AnimatorController.renderAnimation -> AnimationMesh.updateMesh(), not
     * just the mesh metadata that was available before drawing. Read-only.
     */
    private void diagnoseRenderedEmoticons(BlockbusterSceneActorData data,
            EntityActor actor)
    {
        if(!mixedActorDiagnostics||data==null||actor==null||
                !(actor.getMorph() instanceof AnimatedMorph))return;
        AnimatedMorph em=(AnimatedMorph)actor.getMorph();
        if(em.animator==null||em.animator.animation==null||
                em.animator.animation.meshes==null)return;
        boolean selected=bodyPartsController!=null;
        String key=data.getId()+"|"+selected;
        if(!printedMeshDiagnostics.add(key))return;
        for(mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh m:
                em.animator.animation.meshes)
        {
            if(m==null||!"body".equals(m.name)||m.vertices==null)continue;
            java.nio.FloatBuffer b=m.vertices;
            int limit=b.limit(),bad=0,extreme=0;
            float maxAbs=0F;
            for(int i=0;i+3<limit;i+=4)
            {
                for(int j=0;j<3;j++)
                {
                    float v=b.get(i+j);
                    if(Float.isNaN(v)||Float.isInfinite(v))bad++;
                    else
                    {
                        maxAbs=Math.max(maxAbs,Math.abs(v));
                        if(Math.abs(v)>1000F)extreme++;
                    }
                }
            }
            System.out.println("[BBS Editor][MultiActor Mesh] actor="+
                    data.getId()+" role="+(selected?"SELECTED":"background")+
                    " bodyVertices="+(limit/4)+" nonFinite="+bad+
                    " extreme="+extreme+" maxAbs="+maxAbs+
                    " alphaFunc="+GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)+
                    " alphaRef="+GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)+
                    " depthFunc="+GL11.glGetInteger(GL11.GL_DEPTH_FUNC)+
                    " depthWrite="+GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)+
                    " cull="+GL11.glIsEnabled(GL11.GL_CULL_FACE));
            return;
        }
        System.out.println("[BBS Editor][MultiActor Mesh] actor="+
                data.getId()+" role="+(selected?"SELECTED":"background")+
                " body=unavailable");
    }

    /**
     * Blockbuster 1.12's ModelExtrudedLayer renders the 3D outer skin via
     * RenderCustomModel.lastTexture, a STATIC value shared by every morph.
     * A different actor (or an Emoticons mesh) can leave the previous skin
     * there. Then a selected BB morph's outer layer is generated using the
     * wrong actor's texture. Seed the correct BB texture per actor and
     * restore the previous value afterwards, including exceptions.
     */
    private static final class OuterSkinTextureScope implements AutoCloseable
    {
        private final net.minecraft.util.ResourceLocation previous;
        private final boolean active;

        private OuterSkinTextureScope(AbstractMorph morph)
        {
            active=morph instanceof CustomMorph;
            previous=active
                    ?mchorse.blockbuster.client.render.RenderCustomModel.lastTexture
                    :null;
            if(active)
            {
                CustomMorph custom=(CustomMorph)morph;
                net.minecraft.util.ResourceLocation texture=custom.skin;
                if(texture==null && custom.model!=null)
                    texture=custom.model.defaultTexture;
                mchorse.blockbuster.client.render.RenderCustomModel.lastTexture=
                        texture;
            }
        }

        static OuterSkinTextureScope begin(AbstractMorph morph)
        {
            return new OuterSkinTextureScope(morph);
        }

        @Override public void close()
        {
            if(active)
                mchorse.blockbuster.client.render.RenderCustomModel.lastTexture=
                        previous;
        }
    }
    private float preparedPartialTicks;

    private String loadedMorphSignature = "";
    // CharacterStateResolver (selected actor) and this renderer can both
    // install an actor morph. A matching NBT signature alone is insufficient:
    // after one owner installs a replacement, the other cache can report
    // "loaded" while still holding a stale per-morph animator/layer setup.
    private AbstractMorph loadedMorphReference;

    private double previousX;
    private double previousY;
    private double previousZ;

    private boolean hasPreviousPosition;

    private int lastPreviewFrame = -1;

    private final EditorEmoticonsRecordPlayback emoticonsRecordPlayback =
            new EditorEmoticonsRecordPlayback();

    private final BodyPartsPreviewRenderer bodyPartsPreviewRenderer =
            new BodyPartsPreviewRenderer();

    private BodyPartsEditorController bodyPartsController;


    /*
     * =========================================================
     * ACTOR CREATION
     * =========================================================
     */

    private EntityActor getOrCreateActor(Minecraft mc)
    {
        if (mc == null ||
                mc.world == null)
        {
            return null;
        }

        if (this.actor == null ||
                this.actor.world != mc.world ||
                this.actor.isDead)
        {
            // Old preview morphs may still own a live native BetterLights
            // caster even though the detached EntityActor has stopped.
            if(this.actor!=null)
                CameraBetterLightsBridge.retire(this.actor);
            this.actor =
                    new EntityActor(
                            mc.world
                    );

            this.actor.noClip = true;
            this.actor.isDead = false;

            this.loadedMorphSignature = "";
        this.loadedMorphReference = null;

            this.previousX = 0.0D;
            this.previousY = 0.0D;
            this.previousZ = 0.0D;

            this.hasPreviousPosition = false;

            this.lastPreviewFrame = -1;
            this.emoticonsRecordPlayback.reset();
        }

        return this.actor;
    }


    /*
     * =========================================================
     * MORPH
     * =========================================================
     */

    private void updateMorph(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            EntityActor entityActor)
    {
        if (mc == null ||
                actorData == null ||
                entityActor == null)
        {
            return;
        }

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }

        NBTTagCompound morphNBT =
                CharacterMorphResolver.getCurrentMorph(
                        actorData,
                        BlockbusterPreviewAnimationState.getFrame()
                );

        if (morphNBT == null)
        {
            return;
        }

        String signature =
                morphNBT.toString();

        if (signature.equals(this.loadedMorphSignature) &&
                entityActor.getMorph() == this.loadedMorphReference)
        {
            return;
        }

        try
        {
            AbstractMorph morph =
                    MorphManager.INSTANCE.morphFromNBT(
                            morphNBT.copy()
                    );

            if (morph != null)
            {
                // The old morph's caster lives outside the Minecraft entity.
                // Retire it BEFORE replacing the actor's morph reference.
                if(entityActor.getMorph()!=morph)
                    CameraBetterLightsBridge.retire(entityActor);
                /*
                 * Mirror CharacterStateResolver: attach the morph to the
                 * runtime actor BEFORE creating its Emoticons animator.
                 * Background actors previously did this in reverse order.
                 */
                entityActor.morph.setDirect(
                        morph
                );

                if (EmoticonsActorPreviewRenderer
                        .isSupported(morph))
                {
                    EmoticonsActorPreviewRenderer
                            .prepareMorph(
                                    (AnimatedMorph) morph
                            );
                }

                this.loadedMorphSignature = signature;
                this.loadedMorphReference = morph;

                this.previousX = 0.0D;
                this.previousY = 0.0D;
                this.previousZ = 0.0D;

                this.hasPreviousPosition = false;

                this.lastPreviewFrame = -1;

                this.emoticonsRecordPlayback.reset();
            }
        }
        catch (Exception exception)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to load actor morph"
            );

            exception.printStackTrace();
        }
    }


    /*
     * =========================================================
     * BBS ANIMATION POSE
     * =========================================================
     */

    private void applyAnimationPose(
            EntityActor entityActor)
    {
        if (entityActor == null)
        {
            return;
        }

        AbstractMorph currentMorph =
                entityActor.getMorph();

        if (!(currentMorph instanceof CustomMorph))
        {
            return;
        }

        CustomMorph customMorph =
                (CustomMorph) currentMorph;

        List<AnimationBoneSnapshot> snapshots =
                BlockbusterPreviewAnimationState
                        .getSnapshots();

        if (snapshots == null ||
                snapshots.isEmpty())
        {
            customMorph.customPose = null;
            return;
        }

        Minecraft mc =
                Minecraft.getMinecraft();

        if (mc == null)
        {
            return;
        }

        float partialTicks;

        int visualTick=BlockbusterPreviewAnimationState.getFrame();
        if (BlockbusterPreviewAnimationState.isPlaying()||
                CameraVisualFrameClock.hasSample(visualTick))
        {
            partialTicks=CameraVisualFrameClock.partialTick(
                    visualTick,mc.getRenderPartialTicks());
        }
        else
        {
            partialTicks=0F;
        }

        ModelPose basePose =
                customMorph.getPose(
                        entityActor,
                        true,
                        partialTicks
                );

        if (basePose == null)
        {
            return;
        }

        CustomMorph.ModelProperties animationPose =
                customMorph.convertProp(
                        basePose.copy()
                );

        if (animationPose == null)
        {
            return;
        }

        for (AnimationBoneSnapshot snapshot :
                snapshots)
        {
            if (snapshot == null)
            {
                continue;
            }

            String boneName =
                    snapshot.getName();

            if (boneName == null ||
                    boneName.isEmpty())
            {
                continue;
            }

            ModelTransform transform =
                    animationPose.limbs.get(
                            boneName
                    );

            if (transform == null)
            {
                continue;
            }

            transform.translate[0] +=
                    snapshot.getPositionX();

            transform.translate[1] +=
                    snapshot.getPositionY();

            transform.translate[2] +=
                    snapshot.getPositionZ();

            transform.rotate[0] +=
                    snapshot.getRotationX();

            transform.rotate[1] +=
                    snapshot.getRotationY();

            transform.rotate[2] +=
                    snapshot.getRotationZ();

            transform.scale[0] *=
                    snapshot.getScaleX();

            transform.scale[1] *=
                    snapshot.getScaleY();

            transform.scale[2] *=
                    snapshot.getScaleZ();
        }

        customMorph.customPose =
                animationPose;
    }



    /*
     * =========================================================
     * CHAMELEON ANIMATION POSE
     * =========================================================
     *
     * Chameleon does not consume Blockbuster CustomMorph.customPose.
     * Its renderer resets ModelBone.current and then rebuilds the
     * current pose from ChameleonMorph.pose every render.
     *
     * Therefore changing ModelBone.current directly is not sufficient:
     * Chameleon immediately overwrites it in applyPose(). The editor
     * snapshot must be converted into a real AnimatedPose instead.
     */
    private void applyChameleonAnimationPose(
            AbstractMorph morph)
    {
        if (morph == null ||
                !morph.getClass().getName().endsWith(".ChameleonMorph"))
        {
            return;
        }

        List<AnimationBoneSnapshot> snapshots =
                BlockbusterPreviewAnimationState.getSnapshots();

        if (snapshots == null)
        {
            return;
        }

        try
        {
            Class<?> poseClass =
                    Class.forName(
                            "mchorse.chameleon.metamorph.pose.AnimatedPose"
                    );

            Class<?> transformClass =
                    Class.forName(
                            "mchorse.chameleon.metamorph.pose.AnimatedPoseTransform"
                    );

            Object pose =
                    poseClass.newInstance();

            java.lang.reflect.Field bonesField =
                    poseClass.getField("bones");

            Object poseBones =
                    bonesField.get(pose);

            if (!(poseBones instanceof java.util.Map))
            {
                return;
            }

            java.util.Map poseMap =
                    (java.util.Map) poseBones;

            java.lang.reflect.Constructor<?> transformConstructor =
                    transformClass.getConstructor(
                            String.class
                    );

            java.lang.reflect.Field xField =
                    transformClass.getField("x");

            java.lang.reflect.Field yField =
                    transformClass.getField("y");

            java.lang.reflect.Field zField =
                    transformClass.getField("z");

            java.lang.reflect.Field rotateXField =
                    transformClass.getField("rotateX");

            java.lang.reflect.Field rotateYField =
                    transformClass.getField("rotateY");

            java.lang.reflect.Field rotateZField =
                    transformClass.getField("rotateZ");

            java.lang.reflect.Field scaleXField =
                    transformClass.getField("scaleX");

            java.lang.reflect.Field scaleYField =
                    transformClass.getField("scaleY");

            java.lang.reflect.Field scaleZField =
                    transformClass.getField("scaleZ");

            final float degreesToRadians =
                    (float) (Math.PI / 180.0D);

            for (AnimationBoneSnapshot snapshot : snapshots)
            {
                if (snapshot == null ||
                        snapshot.getName() == null ||
                        snapshot.getName().isEmpty())
                {
                    continue;
                }

                Object transform =
                        transformConstructor.newInstance(
                                snapshot.getName()
                        );

                /*
                 * AnimationBone stores local editor translation.
                 * Chameleon AnimatedPoseTransform adds these values
                 * directly to ModelBone.initial.translate.
                 */
                xField.setFloat(
                        transform,
                        snapshot.getPositionX()
                );

                yField.setFloat(
                        transform,
                        snapshot.getPositionY()
                );

                zField.setFloat(
                        transform,
                        snapshot.getPositionZ()
                );

                /*
                 * Editor rotations are degrees.
                 * Chameleon AnimatedPoseTransform expects radians.
                 */
                rotateXField.setFloat(
                        transform,
                        snapshot.getRotationX()
                                * degreesToRadians
                );

                rotateYField.setFloat(
                        transform,
                        snapshot.getRotationY()
                                * degreesToRadians
                );

                rotateZField.setFloat(
                        transform,
                        snapshot.getRotationZ()
                                * degreesToRadians
                );

                scaleXField.setFloat(
                        transform,
                        snapshot.getScaleX()
                );

                scaleYField.setFloat(
                        transform,
                        snapshot.getScaleY()
                );

                scaleZField.setFloat(
                        transform,
                        snapshot.getScaleZ()
                );

                poseMap.put(
                        snapshot.getName(),
                        transform
                );
            }

            /*
             * The Chameleon renderer reads this public field from the
             * actual morph immediately before rendering its model.
             */
            java.lang.reflect.Field poseField =
                    morph.getClass().getField("pose");

            poseField.set(
                    morph,
                    pose
            );
        }
        catch (Throwable ignored)
        {
            /*
             * Chameleon is optional. Reflection failures must never
             * break the editor or the Blockbuster preview.
             */
        }
    }

    /*
     * =========================================================
     * MOVEMENT
     * =========================================================
     */

    private void updateMovementState(
            EntityActor entityActor,
            BlockbusterRecordFrame frame,
            int previewFrame)
    {
        if (entityActor == null ||
                frame == null)
        {
            return;
        }

        /*
         * =====================================================
         * SAME FRAME
         * =====================================================
         *
         * При паузе текущий кадр постоянно приходит снова.
         * Поэтому не изменяем состояние движения повторно.
         */

        if (this.lastPreviewFrame ==
                previewFrame)
        {
            if (!BlockbusterPreviewAnimationState.isPlaying())
            {
                entityActor.prevLimbSwingAmount =
                        entityActor.limbSwingAmount;
            }

            return;
        }

        /*
         * =====================================================
         * FIRST FRAME
         * =====================================================
         */

        if (!this.hasPreviousPosition)
        {
            this.previousX =
                    frame.getX();

            this.previousY =
                    frame.getY();

            this.previousZ =
                    frame.getZ();

            entityActor.prevLimbSwingAmount =
                    0.0F;

            entityActor.limbSwingAmount =
                    0.0F;

            entityActor.limbSwing =
                    0.0F;

            this.hasPreviousPosition =
                    true;

            this.lastPreviewFrame =
                    previewFrame;

            return;
        }

        /*
         * =====================================================
         * CALCULATE MOVEMENT
         * =====================================================
         */

        double dx =
                frame.getX() -
                        this.previousX;

        double dz =
                frame.getZ() -
                        this.previousZ;

        float movement =
                (float) Math.sqrt(
                        dx * dx +
                                dz * dz
                ) * 4.0F;

        if (movement > 1.0F)
        {
            movement = 1.0F;
        }

        /*
         * =====================================================
         * LIMB SWING
         * =====================================================
         *
         * В Minecraft 1.12.2 у используемого здесь EntityActor
         * нет поля prevLimbSwing.
         *
         * Поэтому работаем только с доступными:
         *
         * limbSwing
         * limbSwingAmount
         * prevLimbSwingAmount
         *
         * prevLimbSwingAmount сохраняет предыдущее значение
         * интенсивности движения ног.
         */

        entityActor.prevLimbSwingAmount =
                entityActor.limbSwingAmount;

        entityActor.limbSwingAmount +=
                (
                        movement -
                                entityActor.limbSwingAmount
                ) * 0.4F;

        entityActor.limbSwing +=
                entityActor.limbSwingAmount;

        /*
         * =====================================================
         * SAVE POSITION
         * =====================================================
         */

        this.previousX =
                frame.getX();

        this.previousY =
                frame.getY();

        this.previousZ =
                frame.getZ();

        this.lastPreviewFrame =
                previewFrame;
    }


    /*
     * =========================================================
     * EMOTICONS
     * =========================================================
     */

    /**
     * A manual timeline step is a simulation tick, even while paused.
     * The native Emoticons Animator relies on historic record states for
     * walking phase, action blending and jump transitions; on a backward or
     * nonadjacent seek the bridge replays those states deterministically.
     */
    private void updateEmoticonsAnimation(
            EntityActor entityActor,
            BlockbusterSceneActorData actorData,
            int previewFrame)
    {
        if(entityActor==null || actorData==null)return;
        AbstractMorph morph=entityActor.getMorph();
        if(!(morph instanceof AnimatedMorph))return;
        BlockbusterRecord record=actorData.getRecord();
        if(record==null)
        {
            // Do not attempt to time-replay an actor with no recording.
            // The ordinary preview can still tick it during playback.
            if(BlockbusterPreviewAnimationState.isPlaying())
                EmoticonsActorPreviewRenderer.updateMorph(
                        (AnimatedMorph)morph,entityActor);
            return;
        }
        this.emoticonsRecordPlayback.update(
                entityActor,(AnimatedMorph)morph,record,previewFrame);
    }

    /*
     * =========================================================
     * ENTITY TICK STATE
     * =========================================================
     */

    private void updateEntityTickState(
            EntityActor entityActor,
            int previewFrame)
    {
        if (entityActor == null)
        {
            return;
        }

        entityActor.ticksExisted =
                Math.max(
                        0,
                        previewFrame
                );
    }


    public void setBodyPartsController(
            BodyPartsEditorController controller)
    {
        this.bodyPartsController = controller;
    }

    private List<BodyPartModelData> passiveBodyPartsModels;
    private boolean restoreUneditedNativeParts;

    public void setPassiveBodyPartsModels(List<BodyPartModelData> models)
    {
        setPassiveBodyPartsModels(models,false);
    }

    public void setPassiveBodyPartsModels(List<BodyPartModelData> models,
            boolean preserveOriginalWhenEmpty)
    {
        this.passiveBodyPartsModels = models;
        this.restoreUneditedNativeParts = preserveOriginalWhenEmpty;
    }

    public AbstractMorph getPreparedBodyPartMorph(BodyPartModelData data)
    {
        return this.bodyPartsPreviewRenderer.getPreparedMorph(data);
    }

    /**
     * Immutable physics-shape snapshot for the post-Preview GUI overlay.
     * Do not attempt immediate-mode GL drawing inside actor shaders/FBO.
     */
    public JoltContactOverlaySnapshot snapshotJoltContactShapes(int frame)
    {
        return this.bodyPartsPreviewRenderer.snapshotJoltContactShapes(frame);
    }

    /*
     * =========================================================
     * DRAW ACTOR
     * =========================================================
     */

    /**
     * Isolate per-actor OpenGL client arrays, not just server attributes.
     * Emoticons uses VBO-backed vertex/normal/UV pointers while Blockbuster
     * models can leave different client pointers enabled. The world guard
     * saves them once for the whole scene, which is insufficient when the
     * next actor uses a different morph renderer.
     *
     * This also reconciles Minecraft's GL cache for selected actors: the
     * previous multi-actor resync only covered background actor draws.
     */
    public void drawActor(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            BlockbusterRecordFrame frame,
            EditorCamera camera,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight)
    {
        if(mc==null || mc.world==null || actorData==null || frame==null ||
                camera==null || viewportWidth<=0 || viewportHeight<=0)
        {
            preparedForMirror=false;
            return;
        }

        BbsFsGlStateResync.Snapshot beforeActor=
                BbsFsGlStateResync.capture();
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        try
        {
            org.lwjgl.opengl.GL13.glClientActiveTexture(
                    org.lwjgl.opengl.GL13.GL_TEXTURE0);
            drawActorUnisolated(mc,actorData,frame,camera,viewportX,
                    viewportY,viewportWidth,viewportHeight);
        }
        finally
        {
            try
            {
                GL11.glPopClientAttrib();
            }
            finally
            {
                BbsFsGlStateResync.restore(beforeActor);
            }
        }
    }

    private void drawActorUnisolated(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            BlockbusterRecordFrame frame,
            EditorCamera camera,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight)
    {
        preparedForMirror=false;
        if (mc == null ||
                mc.world == null ||
                actorData == null ||
                frame == null ||
                camera == null ||
                viewportWidth <= 0 ||
                viewportHeight <= 0)
        {
            return;
        }

        // A BetterLights source is NOT an actor to draw in RenderWorldLast.
        // Its one persistent native LightCaster was already synchronized
        // at RenderTick.START, before OptiFine's world lighting pass.
        // Rendering a BetterLightsMorph here would overwrite the caster
        // position with an entity matrix and create a second temporary light.
        if(actorData.getActor()!=null&&BetterLightsRig.isLight(
                actorData.getActor().getMorph()))
        {
            if(this.actor!=null)reset();
            return;
        }

        EntityActor entityActor =
                getOrCreateActor(mc);

        if (entityActor == null)
        {
            return;
        }

        updateMorph(
                mc,
                actorData,
                entityActor
        );
        /*
         * A recorded actor may already contain Metamorph Body Parts in
         * its original morph NBT. Import them once into the editor's
         * Body Parts timeline before prepare() replaces runtime parts
         * with the editor-evaluated attachments.
         */
        if (this.bodyPartsController != null)
        {
            this.bodyPartsController.importRecordedBodyParts(actorData);
        }

        CharacterBodyPartPreviewState.set(
                actorData,
                BlockbusterPreviewAnimationState.getFrame()
        );

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }

        if (!sceneActor.isEnabled() ||
                sceneActor.isInvisible())
        {
            this.hasPreviousPosition = false;
            this.lastPreviewFrame = -1;
            this.emoticonsRecordPlayback.reset();

            AbstractMorph morph =
                    entityActor.getMorph();

            if (morph instanceof CustomMorph)
            {
                ((CustomMorph) morph).customPose = null;
            }

            EmoticonsPreviewAnimationState.clear();

            return;
        }

        entityActor.isDead = false;
        entityActor.noClip = true;

        boolean playing=BlockbusterPreviewAnimationState.isPlaying() ||
                CameraExportRenderClock.current()!=null;

        /*
         * =====================================================
         * PARTIAL TICKS
         * =====================================================
         *
         * При воспроизведении используем Minecraft render
         * partial ticks.
         *
         * При паузе строго 0, чтобы актёр не продолжал
         * визуально интерполироваться между состояниями.
         */

        float actorPartialTicks;

        int visualTick=BlockbusterPreviewAnimationState.getFrame();
        boolean preSampled=CameraVisualFrameClock.hasSample(visualTick);
        actorPartialTicks=playing||preSampled
                ?CameraVisualFrameClock.partialTick(
                        visualTick,mc.getRenderPartialTicks()):0F;

        int previewFrame =
                BlockbusterPreviewAnimationState
                        .getFrame();

        if (previewFrame < 0)
        {
            previewFrame = 0;
        }

        /*
         * =====================================================
         * ENTITY TICK
         * =====================================================
         */

        if (playing)
        {
            updateEntityTickState(
                    entityActor,
                    previewFrame
            );
        }

        /*
         * =====================================================
         * POSITION
         * =====================================================
         */

        entityActor.posX =
                frame.getX();

        entityActor.posY =
                frame.getY();

        entityActor.posZ =
                frame.getZ();

        if(preSampled)
        {
            // Record already sampled at tick + alpha. Let morph/limb
            // animation use alpha without interpolating entity translation
            // a second time from this renderer's previous frame.
            entityActor.prevPosX=entityActor.posX;
            entityActor.prevPosY=entityActor.posY;
            entityActor.prevPosZ=entityActor.posZ;
        }
        else if (playing)
        {
            if (this.hasPreviousPosition)
            {
                entityActor.prevPosX =
                        this.previousX;

                entityActor.prevPosY =
                        this.previousY;

                entityActor.prevPosZ =
                        this.previousZ;
            }
            else
            {
                entityActor.prevPosX =
                        entityActor.posX;

                entityActor.prevPosY =
                        entityActor.posY;

                entityActor.prevPosZ =
                        entityActor.posZ;
            }
        }
        else
        {
            /*
             * На паузе prevPos и pos должны быть одинаковыми.
             * Иначе Minecraft может продолжать интерполяцию
             * положения даже при остановленном Timeline.
             */

            entityActor.prevPosX =
                    entityActor.posX;

            entityActor.prevPosY =
                    entityActor.posY;

            entityActor.prevPosZ =
                    entityActor.posZ;
        }

        /*
         * =====================================================
         * MOVEMENT
         * =====================================================
         */

        // Emoticons locomotion is reconstructed by its own deterministic
        // Record playback clock below. Its native Animator must not inherit
        // an accumulated limbSwing from whichever frame the user last saw.
        if(!(entityActor.getMorph() instanceof AnimatedMorph))
        {
            BlockbusterRecord record=actorData.getRecord();
            BlockbusterRecordFrame source=record==null
                    ?null:record.getFrame(previewFrame);
            updateMovementState(entityActor,
                    source==null?frame:source,previewFrame);
        }

        /*
         * При паузе полностью фиксируем интенсивность
         * движения ног.
         */

        if (!playing)
        {
            entityActor.prevLimbSwingAmount =
                    entityActor.limbSwingAmount;
        }

        /*
         * =====================================================
         * ROTATION
         * =====================================================
         *
         * Сохраняем предыдущее состояние поворота перед
         * установкой нового кадра.
         *
         * Это позволяет Minecraft корректно интерполировать
         * вращение во время воспроизведения.
         */

        float newYaw =
                frame.getYaw();

        float newPitch =
                frame.getPitch();

        float newYawHead =
                frame.getYawHead();

        float newBodyYaw;

        if (frame.hasBodyYaw())
        {
            newBodyYaw =
                    frame.getBodyYaw();
        }
        else
        {
            newBodyYaw =
                    newYaw;
        }


        /*
         * =====================================================
         * PAUSED
         * =====================================================
         *
         * На паузе никакой интерполяции быть не должно.
         */

        if (!playing||preSampled)
        {
            entityActor.rotationYaw =
                    newYaw;

            entityActor.prevRotationYaw =
                    newYaw;

            entityActor.rotationPitch =
                    newPitch;

            entityActor.prevRotationPitch =
                    newPitch;

            entityActor.rotationYawHead =
                    newYawHead;

            entityActor.prevRotationYawHead =
                    newYawHead;

            entityActor.renderYawOffset =
                    newBodyYaw;

            entityActor.prevRenderYawOffset =
                    newBodyYaw;
        }
        else
        {
            /*
             * =================================================
             * PLAYING
             * =================================================
             *
             * Сначала сохраняем старое значение.
             */

            entityActor.prevRotationYaw =
                    entityActor.rotationYaw;

            entityActor.prevRotationPitch =
                    entityActor.rotationPitch;

            entityActor.prevRotationYawHead =
                    entityActor.rotationYawHead;

            entityActor.prevRenderYawOffset =
                    entityActor.renderYawOffset;


            /*
             * Затем устанавливаем состояние нового кадра.
             */

            entityActor.rotationYaw =
                    newYaw;

            entityActor.rotationPitch =
                    newPitch;

            entityActor.rotationYawHead =
                    newYawHead;

            entityActor.renderYawOffset =
                    newBodyYaw;
        }

        /*
         * =====================================================
         * ENTITY STATE
         * =====================================================
         */

        entityActor.setSneaking(
                frame.isSneaking()
        );

        entityActor.setSprinting(
                frame.isSprinting()
        );

        entityActor.onGround =
                frame.isGround();

        entityActor.fallDistance =
                frame.getFallDistance();

        /*
         * =====================================================
         * SWING
         * =====================================================
         */

        entityActor.prevSwingProgress =
                entityActor.swingProgress;

        entityActor.swingProgress =
                0.0F;

        /*
         * =====================================================
         * CUSTOM ANIMATION POSE
         * =====================================================
         */

        applyAnimationPose(
                entityActor
        );

        /*
         * applyAnimationPose replaces CustomMorph.customPose each frame.
         * Therefore Character bone fixed flags must be applied AFTER it,
         * immediately before rendering, not only in the UI state update.
         */
        if (entityActor.getMorph() instanceof CustomMorph)
        {
            CharacterBodyPartOverrideController.apply(
                    entityActor.getMorph(),
                    actorData.getCharacterTimeline(),
                    previewFrame
            );
        }

        /*
         * ChameleonMorph has its own ModelBone hierarchy and does not
         * use CustomMorph.customPose. Apply the same editor snapshots
         * directly to that hierarchy after the normal pose pass.
         */
        applyChameleonAnimationPose(
                entityActor.getMorph()
        );

        /*
         * =====================================================
         * EMOTICONS ANIMATION
         * =====================================================
         */

        updateEmoticonsAnimation(
                entityActor,
                actorData,
                previewFrame
        );

        if(preSampled && entityActor.getMorph() instanceof AnimatedMorph)
        {
            // Deterministic Emoticons history playback restores the *integer*
            // record state to advance the Animator exactly once per tick.
            // Put the render-only subframe pose back afterward.
            entityActor.posX=entityActor.prevPosX=frame.getX();
            entityActor.posY=entityActor.prevPosY=frame.getY();
            entityActor.posZ=entityActor.prevPosZ=frame.getZ();
            entityActor.rotationYaw=entityActor.prevRotationYaw=frame.getYaw();
            entityActor.rotationPitch=entityActor.prevRotationPitch=frame.getPitch();
            entityActor.rotationYawHead=entityActor.prevRotationYawHead=
                    frame.getYawHead();
            entityActor.renderYawOffset=entityActor.prevRenderYawOffset=
                    frame.hasBodyYaw()?frame.getBodyYaw():frame.getYaw();
        }

        /*
         * =====================================================
         * RENDER MANAGER
         * =====================================================
         */

        RenderManager renderManager =
                mc.getRenderManager();

        if (renderManager == null)
        {
            return;
        }

        double oldViewerPosX =
                renderManager.viewerPosX;

        double oldViewerPosY =
                renderManager.viewerPosY;

        double oldViewerPosZ =
                renderManager.viewerPosZ;

        /*
         * The Preview framebuffer is full-screen sized, but the actor is
         * rendered into the Preview viewport inside that framebuffer.
         *
         * The old code used mc.displayWidth/displayHeight for the
         * perspective aspect ratio.  That is wrong when the editor
         * Preview is only a rectangle of the screen: the GL viewport is
         * previewWidth x previewHeight, while the projection was using
         * the whole display aspect.  The Gizmo uses the Preview aspect,
         * so the two projections no longer described the same camera.
         *
         * Keep framebuffer dimensions out of the actor projection.
         */
        int previewWidth =
                viewportWidth;

        int previewHeight =
                viewportHeight;

        if (previewWidth <= 0 ||
                previewHeight <= 0)
        {
            return;
        }

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        /*
         * =====================================================
         * SAVE OPENGL STATE
         * =====================================================
         */

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

        /*
         * =====================================================
         * SAVE MATRICES
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        /*
         * =====================================================
         * RENDER MANAGER CAMERA
         * =====================================================
         */

        boolean nativeGBuffer=BbsFsNativeActorPass.isNativeEntityStage();
        // Native Minecraft entity transforms use coordinates relative to
        // RenderManager.viewerPos. Previously we set viewerPos=0 and
        // reconstructed a separate editor camera matrix. OptiFine still
        // held the ORIGINAL camera matrices in shader uniforms, causing
        // diagonal self-shadow samples on sunlit actor polygons.
        if(!nativeGBuffer)
        {
            renderManager.viewerPosX = 0.0D;
            renderManager.viewerPosY = 0.0D;
            renderManager.viewerPosZ = 0.0D;
        }

        /*
         * =====================================================
         * PROJECTION
         * =====================================================
         */

        if(nativeGBuffer)
        {
            // Same projection + modelview that native OptiFine uploaded as
            // gbufferProjection and gbufferModelViewInverse. Never rebuild
            // yaw/pitch/translation while sampling the sun shadow atlas.
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            if(!BbsFsWorldProjection.loadForNativeWorld())
                throw new IllegalStateException(
                        "Native entity projection was not captured");
            if(!BbsFsNativeActorPass.loadNativeEntityModelView())
                throw new IllegalStateException(
                        "Native entity modelview was not captured");
        }
        else
        {
            GL11.glMatrixMode(
                    GL11.GL_PROJECTION
            );

            GL11.glLoadIdentity();

            float aspect =
                    (float) previewWidth /
                            (float) previewHeight;

            /*
             * Match the native Minecraft/OptiFine depth written before the GUI.
             * The capture's X/Y crop is corrected in the projection matrix.
             * Only fall back to the historical editor camera if the world pass
             * has not provided a usable projection (e.g. an empty dimension).
             */
            if (BbsFsNativeActorPass.isRendering())
            {
                /*
                 * RenderWorldLastEvent is still in the full-size world target.
                 * The actual Minecraft projection must NOT be film-cropped yet.
                 * The film capture will perform the crop once for everyone.
                 */
                if (!BbsFsWorldProjection.loadForNativeWorld())
                    GLU.gluPerspective(camera.getFov(), aspect,
                            camera.getNearPlane(),
                            Math.max(camera.getFarPlane(), 500.0F));
            }
            else if (!BbsFsWorldProjection.loadForPreview(
                    previewWidth, previewHeight))
            {
                GLU.gluPerspective(
                        camera.getFov(), aspect,
                        camera.getNearPlane(),
                        Math.max(camera.getFarPlane(), 500.0F)
                );
            }

            /*
             * =====================================================
             * MODELVIEW
             * =====================================================
             */

            GL11.glMatrixMode(
                    GL11.GL_MODELVIEW
            );

            GL11.glLoadIdentity();

            GL11.glRotatef(-camera.getRoll(), 0F, 0F, 1F);
            GL11.glRotatef(
                    -camera.getPitch(),
                    1.0F,
                    0.0F,
                    0.0F
            );

            GL11.glRotatef(
                    -camera.getYaw(),
                    0.0F,
                    1.0F,
                    0.0F
            );

            double cameraX =
                    camera.getCameraX();

            double cameraY =
                    camera.getCameraY();

            double cameraZ =
                    camera.getCameraZ();

            GL11.glTranslated(
                    -cameraX,
                    -cameraY,
                    -cameraZ
            );

        }

        diagnoseActorGeometry(actorData,entityActor,
                BlockbusterPreviewAnimationState.getSnapshots());

        /*
         * =====================================================
         * ACTOR RENDER STATE
         * =====================================================
         */

        /*
         * Preview world and preceding actor passes can restore actual GL
         * texture bindings via glPopAttrib without updating Minecraft's
         * GlStateManager texture binding cache. Force a known texture
         * binding on the default texture unit before the morph renderer
         * selects its own atlas / skin.
         */
        GlStateManager.setActiveTexture(
                net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit
        );
        GlStateManager.bindTexture(0);

        // One diagnostics line helps distinguish a leaked raster mode from
        // duplicate faces or shaderpack-side shading, without log spam.
        if (!nativeRasterStateReported && BbsFsNativeActorPass.isRendering())
        {
            nativeRasterStateReported=true;
            System.out.println("[BBS Editor][Actor Raster] "+
                    "nativeSunShadows="+Boolean.getBoolean(
                            "bbs.editor.camera.nativeActorSunShadows")+
                    " polygonOffsetFill="+GL11.glIsEnabled(
                            GL11.GL_POLYGON_OFFSET_FILL)+
                    " polygonStipple="+GL11.glIsEnabled(
                            GL11.GL_POLYGON_STIPPLE)+
                    " alphaToCoverage="+GL11.glIsEnabled(
                            org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE));
        }

        // The editor actor is drawn at RenderWorldLast, after OptiFine's
        // native shadow and transparency passes. The visible GBuffer must
        // not inherit the shadow map's depth polygon offset or any screen-
        // door raster coverage from an earlier pass. Those states can cause
        // diagonal stripes on otherwise opaque Emoticons/Chameleon faces.
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
        GL11.glDisable(org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);

        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

        GlStateManager.depthMask(true);

        GlStateManager.enableAlpha();

        GlStateManager.enableBlend();

        GlStateManager.enableTexture2D();

        /*
         * RenderManager.renderEntity(...) does not initialize the per-entity
         * lightmap; vanilla does this in renderEntityStatic(...). We render
         * with explicit record coordinates, so we must reproduce that
         * vanilla step here. Without it, the previous texture/lightmap
         * coordinates can make a recorded actor appear fullbright under
         * OptiFine shaders, while the surrounding world remains correct.
         */
        int packedLight = entityActor.getBrightnessForRender();
        if (entityActor.isBurning())
            packedLight = 15728880; // vanilla burning-entity lighting
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
                net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                (float) (packedLight & 65535),
                (float) (packedLight >>> 16));

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GlStateManager.enableLighting();

        RenderHelper.enableStandardItemLighting();

        /*
         * =====================================================
         * ACTOR RENDER
         * =====================================================
         */

        /*
         * Body Parts must be rendered as a real RenderCustomModel layer.
         * This gives us the same GL transform context used by
         * Blockbuster's own LayerBodyPart.
         */
        if (this.bodyPartsController != null)
        {
            this.bodyPartsPreviewRenderer.prepare(
                    this.bodyPartsController,
                    entityActor,
                    previewFrame
            );
        }
        else if (this.passiveBodyPartsModels != null)
        {
            this.bodyPartsPreviewRenderer.prepareModels(
                    entityActor,previewFrame,this.passiveBodyPartsModels,
                    this.restoreUneditedNativeParts);
        }

        /*
         * Experimental Chameleon Weld wraps ONLY this actor's draw.
         * Restore shared ModelCube vertices even on render failure. Disabled
         * by default; never changes Jolt, scene records or morph data.
         */
        try (ChameleonWeldPreviewPass weld=
                this.bodyPartsPreviewRenderer.beginWeldPreview(actorPartialTicks);
             OuterSkinTextureScope skin=
                OuterSkinTextureScope.begin(entityActor.getMorph());
             BbsFsActorShadowSamplingProbe shadowProbe=
                BbsFsActorShadowSamplingProbe.begin())
        {
            // Match OptiFine's RenderGlobal entity pass (entity ID/material
            // uniforms), in addition to the correct world lightmap coords.
            BbsFsOptiFineEntityStage.beforeEntity(entityActor);
            renderManager.renderEntity(
                    entityActor,
                    entityActor.posX - (nativeGBuffer ? oldViewerPosX : 0D),
                    entityActor.posY - (nativeGBuffer ? oldViewerPosY : 0D),
                    entityActor.posZ - (nativeGBuffer ? oldViewerPosZ : 0D),
                    entityActor.rotationYaw,
                    actorPartialTicks,
                    true
            );
            diagnoseRenderedEmoticons(actorData,entityActor);
        }

        /*
         * Snapshot native Emoticons animated limb transforms only AFTER the
         * existing actor render. No second pass or visible duplicate.
         * These frame-tagged snapshots are not fed into physics yet.
         */
        this.bodyPartsPreviewRenderer.captureRenderedAttachmentMatrices(
                entityActor, previewFrame);
        // From here the cached actor is ready for a second VIEW ONLY.
        // The mirror pass must never call drawActor() / prepare() again.
        preparedPartialTicks=actorPartialTicks;
        preparedActorData=actorData;
        preparedActorFrame=previewFrame;
        preparedForMirror=true;

        /*
         * =====================================================
         * LIGHTING
         * =====================================================
         */

        RenderHelper.disableStandardItemLighting();

        /*
         * =====================================================
         * RESTORE MATRICES
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();

        /*
         * =====================================================
         * RESTORE RENDER MANAGER
         * =====================================================
         */

        renderManager.viewerPosX =
                oldViewerPosX;

        renderManager.viewerPosY =
                oldViewerPosY;

        renderManager.viewerPosZ =
                oldViewerPosZ;

        /*
         * =====================================================
         * RESTORE OPENGL STATE
         * =====================================================
         *
         * После glPopAttrib() намеренно не вызываем
         * enable/disable GL-состояний.
         */

        GL11.glPopAttrib();

        /*
         * =====================================================
         * RESTORE MATRIX MODE
         * =====================================================
         */

        GL11.glMatrixMode(
                oldMatrixMode
        );
    }


    /*
     * =========================================================
     * GET ACTOR
     * =========================================================
     */

    public EntityActor getActor()
    {
        return this.actor;
    }

    /**
     * Render an already evaluated actor from another camera. Does NOT call
     * updateMorph, applyAnimationPose, updateEntityTickState,
     * updateEmoticonsAnimation, BodyParts.prepare, capture matrices,
     * Weld/Bake or Jolt sampling. Only RenderManager draws its existing pose.
     * Each normal native frame has already run drawActor() exactly once.
     */
    public void renderPreparedMirror(Minecraft mc,PreviewCameraPose view,
            int targetWidth,int targetHeight)
    {
        renderPreparedCamera(mc,view,targetWidth,targetHeight,true);
    }

    /** Read-only replay into the native main film GBuffer, with no
     * mirror plane clipping and no timeline/physics re-evaluation. */
    public void renderPreparedNative(Minecraft mc,PreviewCameraPose view,
            int targetWidth,int targetHeight)
    {
        renderPreparedCamera(mc,view,targetWidth,targetHeight,false);
    }

    private void renderPreparedCamera(Minecraft mc,PreviewCameraPose view,
            int targetWidth,int targetHeight,boolean mirror)
    {
        if(preparedActorData==null||mc==null||mc.world==null)return;
        // WorldGlGuard restores GL driver attributes, but its glPopAttrib
        // does not restore Minecraft's cached alpha/depth/texture flags and
        // function values. Isolate EACH prepared native replay just like
        // an ordinary actor draw, regardless of draw order.
        BbsFsGlStateResync.Snapshot beforeReplay=
                BbsFsGlStateResync.capture();
        try
        {
            try(CharacterBodyPartPreviewState.Scope context=
                    CharacterBodyPartPreviewState.enter(
                            preparedActorData,preparedActorFrame))
            {
                renderPreparedCameraScoped(mc,view,targetWidth,targetHeight,mirror);
            }
        }
        finally
        {
            BbsFsGlStateResync.restore(beforeReplay);
        }
    }

    private void renderPreparedCameraScoped(Minecraft mc,PreviewCameraPose view,
            int targetWidth,int targetHeight,boolean mirror)
    {
        if(!preparedForMirror||actor==null||actor.world!=mc.world||
                actor.isDead||actor.getMorph()==null||view==null||
                targetWidth<=0||targetHeight<=0)return;
        RenderManager rm=mc.getRenderManager();
        if(rm==null)return;
        try(BbsFsWorldGlGuard guard=new BbsFsWorldGlGuard(mc))
        {
            boolean nativeGBuffer=!mirror&&
                    BbsFsNativeActorPass.isNativeEntityStage();
            double nativeViewerX=rm.viewerPosX;
            double nativeViewerY=rm.viewerPosY;
            double nativeViewerZ=rm.viewerPosZ;
            if(!nativeGBuffer)
                rm.viewerPosX=rm.viewerPosY=rm.viewerPosZ=0D;
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            if(!mirror&&BbsFsWorldProjection.isCaptured())
            {
                if(!BbsFsWorldProjection.loadForNativeWorld())
                    GLU.gluPerspective(view.fov,
                            (float)targetWidth/targetHeight,
                            Math.max(0.02F,view.nearPlane),
                            Math.max(64F,view.farPlane));
            }
            else GLU.gluPerspective(view.fov,
                    (float)targetWidth/targetHeight,
                    Math.max(0.02F,view.nearPlane),
                    Math.max(64F,view.farPlane));
            // The oblique near clip is valid ONLY for the private mirror.
            // Never clip the visible main world or its actor silhouettes.
            if(mirror)
                CameraSceneMirrorRenderer.installShaderObliqueProjection(view);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            if(nativeGBuffer)
            {
                if(!BbsFsNativeActorPass.loadNativeEntityModelView())
                    throw new IllegalStateException(
                            "Missing native modelview during actor replay");
            }
            else
            {
                GL11.glLoadIdentity();
                GL11.glRotatef(-view.roll,0F,0F,1F);
                GL11.glRotatef(-view.pitch,1F,0F,0F);
                GL11.glRotatef(-view.yaw,0F,1F,0F);
                GL11.glTranslated(-view.x,-view.y,-view.z);
            }

            GlStateManager.setActiveTexture(
                    net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(0);
            // Replay to the main world/mirror, not OptiFine's shadow atlas.
            // The caller may have just used depth-offset caster geometry.
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
            GL11.glDisable(org.lwjgl.opengl.GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            int brightness=actor.isBurning()?15728880:
                    actor.getBrightnessForRender();
            net.minecraft.client.renderer.OpenGlHelper
                    .setLightmapTextureCoords(
                            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit,
                            (float)(brightness&65535),
                            (float)(brightness>>>16));
            mc.entityRenderer.enableLightmap();
            try
            {
                GlStateManager.enableLighting();
                RenderHelper.enableStandardItemLighting();
                if(mirror)
                    CameraMirrorOptiFineBackend.stage("ProgramEntities");
                else
                    BbsFsOptiFineEntityStage.beforeEntity(actor);
                try(OuterSkinTextureScope skin=
                            OuterSkinTextureScope.begin(actor.getMorph());
                    BbsFsActorShadowSamplingProbe shadowProbe=
                            !mirror?BbsFsActorShadowSamplingProbe.begin():null)
                {
                    rm.renderEntity(actor,
                            actor.posX-(nativeGBuffer?nativeViewerX:0D),
                            actor.posY-(nativeGBuffer?nativeViewerY:0D),
                            actor.posZ-(nativeGBuffer?nativeViewerZ:0D),
                            actor.rotationYaw,preparedPartialTicks,true);
                }
            }
            finally
            {
                RenderHelper.disableStandardItemLighting();
                mc.entityRenderer.disableLightmap();
            }
        }
    }

    /**
     * Replay this frame's PREPARED actor geometry into the current shadow
     * depth target. This does not advance the scene, evaluate animations,
     * prepare Body Parts, bake physics, or run Jolt again.
     *
     * The caller supplies the shadow projection and light-space modelview;
     * RenderManager receives absolute actor coordinates with a zero viewer
     * origin, just as the cached mirror replay does. In particular, do
     * NOT call renderPreparedMirror(), which installs a perspective camera
     * and ProgramEntities rather than preserving ProgramShadow.
     *
     * The caller must rebind its shadow program/FBO after a morph renderer
     * because third-party render layers can change GL bindings.
     */
    public boolean renderPreparedShadow(Minecraft mc)
    {
        if(preparedActorData==null)return false;
        try(CharacterBodyPartPreviewState.Scope context=
                CharacterBodyPartPreviewState.enter(
                        preparedActorData,preparedActorFrame))
        {
            return renderPreparedShadowScoped(mc);
        }
    }

    private boolean renderPreparedShadowScoped(Minecraft mc)
    {
        if(mc==null||!preparedForMirror||actor==null||
                actor.world!=mc.world||actor.isDead||
                actor.getMorph()==null)return false;
        // A BetterLightsMorph is a source of illumination, not an opaque
        // humanoid mesh; rendering it into a shadow map is incorrect.
        if(actor.getMorph().getClass().getName()
                .endsWith(".BetterLightsMorph"))return false;
        RenderManager manager=mc.getRenderManager();
        if(manager==null)return false;
        try(BbsFsWorldGlGuard guard=new BbsFsWorldGlGuard(mc))
        {
            manager.viewerPosX=manager.viewerPosY=manager.viewerPosZ=0D;
            GlStateManager.setActiveTexture(
                    net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(0);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            // Alpha-tested hair, clothing, Body Parts, Chameleon and
            // Emoticons all share their visible silhouette with this
            // caster path; do not replace them with bounding boxes.
            GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);
            GlStateManager.disableBlend();
            GlStateManager.disableLighting();
            manager.renderEntity(actor,actor.posX,actor.posY,actor.posZ,
                    actor.rotationYaw,preparedPartialTicks,true);
            return true;
        }
    }

    /** The selected actor's opt-in Jolt -> Body Parts key bake session. */
    public BodyPartPhysicsBakeController getPhysicsBakeController()
    {
        return this.bodyPartsPreviewRenderer.getBakeController();
    }


    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        if (this.actor != null)
        {
            CameraBetterLightsBridge.retire(this.actor);
            this.actor.setDead();
        }

        this.actor = null;
        this.preparedForMirror=false;
        this.preparedActorData=null;
        this.preparedActorFrame=0;
        this.passiveBodyPartsModels=null;
        this.restoreUneditedNativeParts=false;

        this.loadedMorphSignature = "";
        this.loadedMorphReference = null;

        this.previousX = 0.0D;
        this.previousY = 0.0D;
        this.previousZ = 0.0D;

        this.hasPreviousPosition = false;

        this.lastPreviewFrame = -1;

        this.emoticonsRecordPlayback.reset();

        this.bodyPartsPreviewRenderer.clear();

        // This renderer may belong to a BACKGROUND actor removed when its
        // owner becomes selected. EmoticonsPreviewAnimationState is shared
        // by ALL preview actors; clearing it here used to erase the selected
        // actor's pose and leave only part of a mixed scene visible.
        // The scene's owner restores/clears that shared state at scene exit.
    }
}