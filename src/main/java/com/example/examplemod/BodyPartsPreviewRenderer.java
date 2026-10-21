package com.example.examplemod;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import mchorse.blockbuster.common.entity.EntityActor;

/**
 * Bridges the editor's Body Parts data to Metamorph's real body-part system.
 *
 * The important rule here is that the editor does NOT render an attached
 * model itself.  It creates the same runtime objects Blockbuster creates:
 *
 *   parent CustomMorph
 *       -> BodyPartManager
 *           -> BodyPart
 *               -> child CustomMorph
 *
 * Blockbuster's own LayerBodyPart then renders those objects.  This keeps
 * attachment transforms, model rendering, lighting and nested morph
 * rendering on the exact 1.12.2 code path used by Metamorph/Blockbuster.
 */
public class BodyPartsPreviewRenderer
{
    /* Runtime morphs used by the actual Metamorph body-part render pass. */
    private final Map<BodyPartModelData, AbstractMorph> preparedMorphs =
            new IdentityHashMap<BodyPartModelData, AbstractMorph>();

    /*
     * Preserve the unanimated pose of each runtime child morph. Some
     * morph-backed Body Parts reuse the same CustomMorph across preview
     * frames. Without this snapshot, child.customPose is already offset by
     * the previous editor frame and the next frame adds the key a second
     * time. The gizmo always evaluates base + current key, so the two
     * would drift apart. Weak keys avoid retaining temporary child morphs.
     */
    private final Map<CustomMorph, mchorse.blockbuster.api.ModelPose> originalChildPoses =
            new WeakHashMap<CustomMorph, mchorse.blockbuster.api.ModelPose>();

    /* Runtime-only Chameleon chain registry. Physics is deliberately not
     * applied until the actual attachment world matrices are available.
     * Export never uses this registry.
     */
    private boolean preparingPreview;
    private final Map<BodyPartModelData, List<BodyPartPhysicsChains.Chain>> previewPhysicsChains =
            new IdentityHashMap<BodyPartModelData, List<BodyPartPhysicsChains.Chain>>();
    private final Map<BodyPartModelData, String> reportedPhysicsChains =
            new IdentityHashMap<BodyPartModelData, String>();

    /*
     * Metamorph's BodyPart.recordMatrix() renders the parent with an identity
     * model-view and stores each part's native parent-limb matrix in lastMatrix.
     * This is a diagnostic snapshot, not an applied physics pose.
     */
    private final Map<BodyPartModelData, float[]> physicsAttachmentMatrices =
            new IdentityHashMap<BodyPartModelData, float[]>();
    private final Map<BodyPartModelData, String> physicsMatrixDiagnostics =
            new IdentityHashMap<BodyPartModelData, String>();
    private boolean recordingPhysicsMatrices;
    /** Optional reflection-only Java 8 Jolt preview; disabled by default. */
    private final BodyPartJoltPreviewAdapter joltPreview =
            new BodyPartJoltPreviewAdapter();
    private final java.util.Set<String> reportedJoltPreview =
            new java.util.HashSet<String>();
    private final Map<BodyPartModelData, String> reportedCollisionBone =
            new IdentityHashMap<BodyPartModelData, String>();

    /** Native parent bone snapshot made AFTER the ordinary actor render. */
    private final Map<BodyPartModelData, float[]> renderedAttachmentMatrices =
            new IdentityHashMap<BodyPartModelData, float[]>();
    private final Map<BodyPartModelData, Integer> renderedAttachmentFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    private final Map<BodyPartModelData, Boolean> reportedPostRenderMatrices =
            new IdentityHashMap<BodyPartModelData, Boolean>();
    /** Stable sampled pose shared by all render passes of a preview frame. */
    private final BodyPartPhysicsFrameState physicsFrames =
            new BodyPartPhysicsFrameState();
    /** Opt-in capture of the final VISIBLE Jolt/Chameleon pose for key baking.
     * Never changes live physics unless the user explicitly commits keys. */
    private final BodyPartPhysicsBakeController bakeController =
            new BodyPartPhysicsBakeController();

    public BodyPartPhysicsBakeController getBakeController()
    {
        return bakeController;
    }
    private final Map<BodyPartModelData, Boolean> visualProbeReported =
            new IdentityHashMap<BodyPartModelData, Boolean>();
    /* Snapshot the clean Chameleon pose rotation before applying the probe.
     * This stays separate from the modified preview morph. */
    private final Map<BodyPartModelData, Map<String, float[]>> previewEulerDeltas =
            new IdentityHashMap<BodyPartModelData, Map<String, float[]>>();

    /** Compare Jolt's intended direction with Chameleon's ACTUAL rendered pivots. */
    private static final boolean JOLT_DIRECTION_DIAGNOSTIC =
            Boolean.getBoolean("bbs.editor.physics.jolt.diagnostic");
    /** Visual only: quaternion interpolation, never an extra Jolt tick.
     * Disable for strict A/B diagnostics with
     * -Dbbs.editor.physics.jolt.visualInterpolation=false
     */
    private static final boolean JOLT_VISUAL_INTERPOLATION=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.visualInterpolation","true"));
    /**
     * Jolt needs the same child render space as visible Chameleon geometry:
     * ChameleonMorph.render() applies scale and Y=180 after the BodyPart.
     * Omitting Y180 lets native gravity point down while rendered hair goes up
     * as the parent is tilted around X.
     *
     * Keep an explicit false override for regression diagnostics.
     */
    private static final boolean JOLT_EXACT_CHILD_SPACE =
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.exactChildSpace", "true"));
    /** Sample generated AFTER a render; available for the next draw of that frame. */
    private final Map<BodyPartModelData, Map<String, float[]>> joltSampleDirections =
            new IdentityHashMap<BodyPartModelData, Map<String, float[]>>();
    private final Map<BodyPartModelData, Integer> joltSampleFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    /** Actual native Jolt link directions, not derived from Chameleon poses. */
    private final Map<BodyPartModelData, Map<String, float[]>> joltSampleNativeDirections =
            new IdentityHashMap<BodyPartModelData, Map<String, float[]>>();
    /** Emoticons parent frame when the physics sample was generated. */
    private final Map<BodyPartModelData, float[]> joltSampleParentMatrices =
            new IdentityHashMap<BodyPartModelData, float[]>();
    /** Precisely the physical sample applied to the child being drawn now. */
    private final Map<BodyPartModelData, Map<String, float[]>> joltAppliedDirections =
            new IdentityHashMap<BodyPartModelData, Map<String, float[]>>();
    private final Map<BodyPartModelData, Integer> joltAppliedFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    private final Map<BodyPartModelData, Map<String, float[]>> joltAppliedNativeDirections =
            new IdentityHashMap<BodyPartModelData, Map<String, float[]>>();
    private final Map<BodyPartModelData, float[]> joltAppliedParentMatrices =
            new IdentityHashMap<BodyPartModelData, float[]>();
    private final Map<BodyPartModelData, Map<String, String>> lastJoltDirectionReports =
            new IdentityHashMap<BodyPartModelData, Map<String, String>>();
    /** Log no more than once per second per strand, even during playback. */
    private final Map<BodyPartModelData, Map<String, Long>> lastJoltDirectionTimes =
            new IdentityHashMap<BodyPartModelData, Map<String, Long>>();
    private final Map<BodyPartModelData, Float> leftRootBaseRotation =
            new IdentityHashMap<BodyPartModelData, Float>();
    private final Map<BodyPartModelData, Float> leftRootAppliedRotation =
            new IdentityHashMap<BodyPartModelData, Float>();

    /* Actual child BodyParts from this preview pass, never re-rendered. */
    private final Map<BodyPartModelData, BodyPart> renderedPhysicsParts =
            new IdentityHashMap<BodyPartModelData, BodyPart>();
    /** All attached parts, including passive Chameleon collision-only forms. */
    private final Map<BodyPartModelData, BodyPart> renderedCollisionParts =
            new IdentityHashMap<BodyPartModelData, BodyPart>();
    private final java.util.List<BodyPartModelData> collisionPartOrder =
            new java.util.ArrayList<BodyPartModelData>();
    private final Map<BodyPartModelData, java.util.List<float[]>> attachedCollisionBoxes =
            new IdentityHashMap<BodyPartModelData, java.util.List<float[]>>();
    /** Parallel typed native shapes, matching box outlines exactly. */
    private final Map<BodyPartModelData, java.util.List<float[]>> attachedCollisionShapes =
            new IdentityHashMap<BodyPartModelData, java.util.List<float[]>>();
    private int attachedCollisionFrame = -1;
    /** Full animated PARENT Emoticons skin colliders: captured once per actor
     * render, never once per hair attachment. Same native world frame and
     * source ordering as the additional Chameleon BodyPart colliders. */
    private BodyPartEmoticonsFullBodyColliders.Capture parentEmoticonsColliders;
    /** Per-entity cached skin mesh bind geometry. Multi-actor preview must
     * never reuse the fitted arm/body boxes of a different actor. */
    private final java.util.Map<Integer,BodyPartEmoticonsFullBodyColliders.Cache>
            parentColliderCaches=new java.util.HashMap<Integer,BodyPartEmoticonsFullBodyColliders.Cache>();
    /** Enabled by default, can be disabled for legacy torso-only A/B:
     * -Dbbs.editor.physics.jolt.fullParentColliders=false */
    private static final boolean FULL_PARENT_COLLIDERS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.fullParentColliders","true"));
    private int parentEmoticonsColliderFrame=-1;
    private String reportedParentColliderState="";

    /* Emoticons provides model-local bone transforms. The renderer then
     * rotates/translates the complete actor. Both physical chains AND their
     * passive contacts must share the same actor-world frame, or hair responds
     * to bone rotations but not to the character walking through the world.
     * This is sampled only after the normal actor draw. */
    private float[] joltActorWorldFrame;
    private int joltActorWorldFrameTick = -1;

    /** Compare actual actor-world clearances of mirrored front strands.
     *  Off by default; entirely read-only and source-model agnostic. */
    private static final boolean JOLT_CONTACT_PROBE=
            Boolean.getBoolean("bbs.editor.physics.jolt.contactProbe");
    private final Map<String,Integer> lastContactProbeFrame=
            new java.util.HashMap<String,Integer>();

    private float[] actorFrameFor(int frame)
    {
        return joltActorWorldFrameTick==frame ? joltActorWorldFrame : null;
    }
    private final Map<String,Integer> lastSingleBoneGeometryFrame =
            new java.util.HashMap<String,Integer>();
    private final Map<BodyPartModelData,String> reportedCollisionCaptures =
            new IdentityHashMap<BodyPartModelData,String>();
    /** Real Emoticons skinned mesh fit for this exact rendered frame. */
    private final Map<BodyPartModelData, BodyPartJoltTorsoFit.Fit> joltTorsoFits =
            new IdentityHashMap<BodyPartModelData, BodyPartJoltTorsoFit.Fit>();
    private final Map<BodyPartModelData, BodyPartJoltMultiTorsoFit.Segments> joltMultiTorsoFits =
            new IdentityHashMap<BodyPartModelData, BodyPartJoltMultiTorsoFit.Segments>();
    private final Map<BodyPartModelData, Integer> joltTorsoFitFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    private final Map<BodyPartModelData, String> reportedJoltTorsoFit =
            new IdentityHashMap<BodyPartModelData, String>();

    public float[] getRenderedAttachmentMatrix(BodyPartModelData data, int frame)
    {
        Integer sampledFrame = renderedAttachmentFrames.get(data);
        if (sampledFrame == null || sampledFrame.intValue() != frame)
            return null;
        float[] matrix = renderedAttachmentMatrices.get(data);
        return matrix == null ? null : BodyPartPhysicsMatrixSpace.snapshot(matrix);
    }

    /**
     * Call only AFTER RenderManager.renderEntity on the normal pass.
     * Emoticons has evaluated its walking/running pose by this point.
     * No extra render, GL matrix manipulation or changes to keys.
     */
    private String lastEmoticonsGeometryReport = "";
    private int lastParentGravityProbeFrame=-1;

    /**
     * A true world-gravity vector projected into the rendered bone frame.
     * It MUST rotate in local coordinates when the bone tilts in world
     * space. Diagnostic only: never rotates the actual gravity vector.
     */
    private static String localWorldDown(float[] m)
    {
        if(m==null || m.length!=16)return "unavailable";
        float[] projected=new float[3];
        for(int axis=0;axis<3;axis++)
        {
            int k=axis*4;
            float length=(float)Math.sqrt(m[k]*m[k]+
                    m[k+1]*m[k+1]+m[k+2]*m[k+2]);
            if(!(length>0.00001F))return "singular";
            projected[axis]=-m[k+1]/length;
        }
        return java.util.Arrays.toString(projected);
    }

    public void captureRenderedAttachmentMatrices(EntityActor actor, int frame)
    {
        if (actor == null || actor.getMorph() == null) return;
        Object morph = actor.getMorph();
        float partial = BlockbusterPreviewAnimationState.isPlaying()
                ? net.minecraft.client.Minecraft.getMinecraft().getRenderPartialTicks()
                : 0F;
        float yaw = actor.prevRenderYawOffset +
                (actor.renderYawOffset - actor.prevRenderYawOffset)*partial;
        joltActorWorldFrame=BodyPartJoltActorSpace.worldFromActor(
                actor.posX,actor.posY,actor.posZ,yaw);
        joltActorWorldFrameTick=frame;
        if(JOLT_CONTACT_PROBE && frame%5==0 &&
                lastParentGravityProbeFrame!=frame)
        {
            lastParentGravityProbeFrame=frame;
            for(String name:new String[]{"body","low_body","head"})
            {
                float[] local=BodyPartEmoticonsAttachmentProbe
                        .lastRenderedBoneMatrix(morph,name);
                if(local==null)continue;
                float[] world=BodyPartJoltActorSpace.matrix(
                        joltActorWorldFrame,local);
                System.out.println("[BBS Animation Editor][Jolt][WorldGravityProbe] "+
                        "frame="+frame+" bone="+name+
                        " actorPitch="+actor.rotationPitch+
                        " actorYaw="+yaw+
                        " worldDownInBone="+localWorldDown(world)+
                        " boneOrigin="+java.util.Arrays.toString(
                                new float[]{world[12],world[13],world[14]}));
            }
        }
        if (frame == 0 || frame == 20)
        {
            java.util.List<EmoticonsCollisionGeometryProvider.MeshSnapshot> surfaces =
                    EmoticonsCollisionGeometryProvider.snapshot(morph);
            int vertices=0, triangles=0;
            for (EmoticonsCollisionGeometryProvider.MeshSnapshot mesh : surfaces)
            {
                vertices+=mesh.vertexCount();
                triangles+=mesh.triangleCount();
            }
            EmoticonsCollisionGeometryProvider.Bounds meshBounds =
                    EmoticonsCollisionGeometryProvider.bounds(surfaces);
            String report="meshes="+surfaces.size()+", vertices="+vertices
                    +", triangles="+triangles
                    +", meshLocalBounds="+(meshBounds==null?"missing":meshBounds.toString());
            if (!report.equals(lastEmoticonsGeometryReport))
            {
                lastEmoticonsGeometryReport=report;
                System.out.println("[BBS Animation Editor][Physics] "
                        + "Emoticons skinned collision geometry available: "+report
                        + " (mesh-local snapshot, collision solver not yet connected)");
            }
        }
        // Independent from hair parent bones: HEAD, TORSO, both ARMS and
        // both LEGS always have collision coverage if skinned mesh exists.
        // Never use the old torso-only proxy simultaneously with this
        // skinned parent hull: overlapping contacts falsely push hair aside.
        parentEmoticonsColliders=null;
        parentEmoticonsColliderFrame=-1;
        if(BodyPartJoltPreviewAdapter.torsoContactsRequested() &&
                FULL_PARENT_COLLIDERS)
        {
            BodyPartEmoticonsFullBodyColliders.Cache skinCache=
                    parentColliderCaches.get(Integer.valueOf(actor.getEntityId()));
            if(skinCache==null)
            {
                skinCache=new BodyPartEmoticonsFullBodyColliders.Cache();
                parentColliderCaches.put(Integer.valueOf(actor.getEntityId()),skinCache);
            }
            BodyPartEmoticonsFullBodyColliders.Capture hull=
                    BodyPartEmoticonsFullBodyColliders.capture(
                            morph,actorFrameFor(frame),skinCache);
            if(hull.isReady())
            {
                parentEmoticonsColliders=hull;
                parentEmoticonsColliderFrame=frame;
            }
            String report="groups="+hull.groups+
                    " triangles="+hull.triangles+
                    " shapes="+hull.shapes.size()+
                    " frame="+frame+
                    " (head/torso/arms/legs; skinned mesh)";
            if(!report.substring(0,report.indexOf(" frame=")).equals(
                    reportedParentColliderState))
            {
                reportedParentColliderState=report.substring(
                        0,report.indexOf(" frame="));
                System.out.println("[BBS Animation Editor][Jolt][ParentSkin] "+report);
            }
        }
        captureAttachedChameleonColliders(morph,frame);
        for (Map.Entry<BodyPartModelData, List<BodyPartPhysicsChains.Chain>> entry :
                previewPhysicsChains.entrySet())
        {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            BodyPartModelData data = entry.getKey();
            float[] matrix = BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                    morph, data.getAttachmentBoneName());
            if (matrix == null)
            {
                renderedAttachmentMatrices.remove(data);
                renderedAttachmentFrames.remove(data);
                continue;
            }
            renderedAttachmentMatrices.put(data, matrix);
            renderedAttachmentFrames.put(data, Integer.valueOf(frame));
            if (BodyPartJoltPreviewAdapter.torsoContactsRequested())
            {
                JoltTorsoSkinFit fitted =
                        fitNativeJoltTorso(morph, matrix);
                BodyPartJoltTorsoFit.Fit torsoFit =
                        fitted==null?null:fitted.single;
                BodyPartJoltMultiTorsoFit.Segments multi =
                        fitted==null?null:fitted.compound;
                if (torsoFit != null)
                {
                    joltTorsoFits.put(data,torsoFit);
                    joltTorsoFitFrames.put(data,Integer.valueOf(frame));
                    if(multi!=null)joltMultiTorsoFits.put(data,multi);
                    else joltMultiTorsoFits.remove(data);
                }
                else
                {
                    joltTorsoFits.remove(data);
                    joltMultiTorsoFits.remove(data);
                    joltTorsoFitFrames.remove(data);
                }
                String outcome = torsoFit == null
                        ? "unavailable (native torso landmarks/skin mesh differ; "
                                + "contacts OFF, no guessed capsule)"
                        : String.format(java.util.Locale.ROOT,
                                "fitted radius=%.3f blocks, points=%d/%d "
                                + "segments=%s (proxy, not triangle-mesh collision)",
                                torsoFit.radius,torsoFit.usableVertices,
                                torsoFit.observedVertices,
                                multi==null?"single-only":
                                        String.format(java.util.Locale.ROOT,
                                            "(%.3f,%.3f,%.3f)",
                                            multi.radius[0],multi.radius[1],multi.radius[2]));
                // A stable status indicator, no per-frame logging storms.
                String prior=reportedJoltTorsoFit.get(data);
                boolean wasReady=prior != null && prior.startsWith("fitted");
                boolean ready=torsoFit!=null;
                if(prior==null || wasReady!=ready)
                {
                    reportedJoltTorsoFit.put(data,outcome);
                    System.out.println("[BBS Animation Editor][Jolt][Torso] "
                            + outcome + " frame=" + frame);
                }
            }
            BodyPart childPart = renderedPhysicsParts.get(data);
            if (childPart != null)
            {
                diagnoseVisualHairResult(data, childPart, frame);
                diagnoseJoltRenderedDirections(data, childPart, matrix, frame);
                // Evaluate world-chain directions from the currently rendered
                // Emoticons basis. The write-back remains disabled until the
                // local Chameleon rotation convention has been validated.
                String torsoName = BodyPartPhysicsTorsoLocator.findUpperTorsoName(morph);
                float[] torsoMatrix = torsoName == null ? null :
                        BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                                morph, torsoName);
                String collisionStatus = torsoMatrix == null
                        ? "upper torso unavailable (collisions skipped)"
                        : "upper torso=" + torsoName;
                if (!collisionStatus.equals(reportedCollisionBone.get(data)))
                {
                    reportedCollisionBone.put(data, collisionStatus);
                    System.out.println("[BBS Animation Editor][Physics] "
                            + "Body collision " + collisionStatus
                            + ", attachment=" + data.getAttachmentBoneName());
                }
                if ((frame == 0 || frame == 20 || frame == 60)
                        && childPart.morph != null && childPart.morph.get() != null)
                {
                    diagnoseEmoticonsCollisionSpace(morph, data, childPart,
                            matrix, torsoMatrix, frame);
                }
                inspectTorsoRecoveryCandidate(morph, data, childPart, matrix, frame);
                probePhysicsBoneMatrices(data, childPart, matrix, torsoMatrix, frame);
            }
            if (!reportedPostRenderMatrices.containsKey(data))
            {
                reportedPostRenderMatrices.put(data, Boolean.TRUE);
                System.out.println("[BBS Animation Editor][Physics] "
                        + "Emoticons attachment matrix captured after normal render "
                        + "(first frame=" + frame + "; subsequent frames silent)");
            }
        }
    }

    /**
     * From a completed normal actor render, collect decoded cube colliders
     * from ALL added Chameleon parts, even those with no physics of their
     * own. This works for arbitrary static/animated attachments, including
     * a two-cube rotated chest model. No second actor render is initiated.
     * The physics system never collides a dynamic strand with itself.
     */
    private void captureAttachedChameleonColliders(Object actorMorph,int frame)
    {
        attachedCollisionBoxes.clear();
        attachedCollisionShapes.clear();
        attachedCollisionFrame=frame;
        if(!BodyPartJoltPreviewAdapter.torsoContactsRequested())return;
        for(BodyPartModelData data:collisionPartOrder)
        {
            BodyPart part=renderedCollisionParts.get(data);
            if(part==null || part.morph==null || part.morph.get()==null ||
                    !part.morph.get().getClass().getName().endsWith(".ChameleonMorph"))
                continue;
            float[] parent=BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                    actorMorph,data.getAttachmentBoneName());
            if(parent==null)
            {
                reportColliderCapture(data,"missing parent bone matrix: "+
                        data.getAttachmentBoneName());
                continue;
            }
            Object child=part.morph.get();
            BodyPartPhysicsWorldPose.MatrixSource local=
                    BodyPartChameleonMatrixProbe.source(child);
            if(local==null)
            {
                reportColliderCapture(data,"no Chameleon post-render matrix source");
                continue;
            }
            try(BodyPartChameleonCleanPose clean=
                    BodyPartChameleonCleanPose.begin(
                            child,previewEulerDeltas.get(data)))
            {
                float[] attached=worldFromRenderedChameleonChild(parent,part);
                float[] actorFrame=actorFrameFor(frame);
                if(actorFrame!=null)
                    attached=BodyPartJoltActorSpace.matrix(actorFrame,attached);
                BodyPartPhysicsWorldPose.MatrixSource world=
                        BodyPartPhysicsWorldPose.worldSource(attached,local);
                java.util.Set<String> driven=new java.util.HashSet<String>();
                for(BodyPartPhysicsChains.Chain chain:getPreviewPhysicsChains(data))
                    for(AnimationBone bone:chain.bones)
                        driven.add(bone.getName());
                BodyPartChameleonCollisionBoxes.Capture captured=
                        BodyPartChameleonCollisionBoxes.capture(child,world,driven,
                            new BodyPartChameleonCollisionBoxes.SettingsSource()
                            {
                                @Override public BodyPartCollisionSettings forBone(String id)
                                {
                                    return data.peekCollisionSettings(id);
                                }
                            });
                if(!captured.boxes.isEmpty())
                {
                    attachedCollisionBoxes.put(data,captured.boxes);
                    attachedCollisionShapes.put(data,captured.shapes);
                }
                reportColliderCapture(data,"attached cubes="+captured.boxes.size()+
                        " visitedBones="+captured.visitedBones+
                        " candidateCubes="+captured.visitedCubes+
                        " rejected="+captured.rejectedCubes+
                        " simulatedBonesExcluded="+driven.size()+
                        (captured.error==null?"":" error="+captured.error));
            }
            catch(RuntimeException error)
            {
                reportColliderCapture(data,"collider capture failed: "
                        +error.getClass().getSimpleName());
            }
        }
    }

    private void reportColliderCapture(BodyPartModelData data,String message)
    {
        if(!message.equals(reportedCollisionCaptures.get(data)))
        {
            reportedCollisionCaptures.put(data,message);
            System.out.println("[BBS Animation Editor][Jolt][BodyPartBoxes] "
                    +data.getModelName()+" "+message);
        }
    }

    /**
     * Every passive Chameleon cube can collide with an active hair chain,
     * including the static head/scalp in that SAME attached model. The
     * capture phase already excludes ALL driven physics bones of each part,
     * so no dynamic hair cube is also created as a kinematic collider.
     *
     * A native first-link contact is only excluded when the root sits
     * inside that specific owning body shape; external chest and parent
     * limb contacts remain active.
     */
    private float[][] passiveCollisionBoxes(BodyPartModelData owner,int frame)
    {
        if(frame!=attachedCollisionFrame)return null;
        java.util.List<float[]> combined=new java.util.ArrayList<float[]>();
        // User-authored Chameleon colliders have priority when the 32-form
        // budget is full. Parent geometry fills the remaining slots.
        for(BodyPartModelData data:collisionPartOrder)
        {
            java.util.List<float[]> source=attachedCollisionBoxes.get(data);
            if(source==null)continue;
            for(float[] box:source)
            {
                if(combined.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)
                    break;
                combined.add(box.clone());
            }
        }
        if(frame==parentEmoticonsColliderFrame &&
                parentEmoticonsColliders!=null)
            for(float[] box:parentEmoticonsColliders.boxes)
            {
                if(combined.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)break;
                combined.add(box.clone());
            }
        return combined.isEmpty()?null:
                combined.toArray(new float[combined.size()][]);
    }

    /** Corresponding native Jolt primitive rows in exactly the same order
     *  as passiveCollisionBoxes(), for World-space kinematic contacts. */
    private float[][] passiveCollisionShapes(int frame)
    {
        if(frame!=attachedCollisionFrame)return null;
        java.util.List<float[]> result=new java.util.ArrayList<float[]>();
        for(BodyPartModelData data:collisionPartOrder)
        {
            java.util.List<float[]> shapes=attachedCollisionShapes.get(data);
            if(shapes==null)continue;
            for(float[] shape:shapes)
            {
                if(result.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)break;
                result.add(shape.clone());
            }
        }
        if(frame==parentEmoticonsColliderFrame &&
                parentEmoticonsColliders!=null)
            for(float[] shape:parentEmoticonsColliders.shapes)
            {
                if(result.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)break;
                result.add(shape.clone());
            }
        return result.isEmpty()?null:result.toArray(new float[result.size()][]);
    }

    /** Stable per-box provenance for exactly the same ordering as
     *  passiveCollisionBoxes() forwards to Jolt. This is debug metadata
     *  only and never changes native collision groups or dynamics.
     */
    private java.util.List<String> passiveCollisionSources(int frame)
    {
        java.util.List<String> result=new java.util.ArrayList<String>();
        if(frame!=attachedCollisionFrame)return result;
        for(BodyPartModelData data:collisionPartOrder)
        {
            java.util.List<float[]> forms=attachedCollisionBoxes.get(data);
            if(forms==null)continue;
            String model=data.getModelNameAt(frame);
            if(model==null || model.isEmpty())model=data.getModelName();
            if(model==null || model.isEmpty())model="unknown";
            for(int i=0;i<forms.size() &&
                    result.size()<BodyPartChameleonCollisionBoxes.MAX_BOXES;i++)
                result.add(model);
        }
        if(frame==parentEmoticonsColliderFrame &&
                parentEmoticonsColliders!=null)
            for(String name:parentEmoticonsColliders.owners)
            {
                if(result.size()>=BodyPartChameleonCollisionBoxes.MAX_BOXES)break;
                result.add(name);
            }
        return result;
    }

    /**
     * Only snapshots of INSTALLED native collision bodies at the requested
     * animation frame. No GL calls are made here: the caller draws the
     * projected capsules in normal Minecraft GUI space AFTER blitting the
     * completed Preview FBO, just as the working screen-space gizmo does.
     */
    public JoltContactOverlaySnapshot snapshotJoltContactShapes(int frame)
    {
        java.util.List<JoltContactOverlaySnapshot.Shape> shapes =
                new java.util.ArrayList<JoltContactOverlaySnapshot.Shape>();
        java.util.List<JoltContactOverlaySnapshot.Box> boxes =
                new java.util.ArrayList<JoltContactOverlaySnapshot.Box>();
        int activeScenes=0;
        boolean anyBoxesAdded=false;
        java.util.List<String> boxOwners=passiveCollisionSources(frame);
        if(!BodyPartJoltPreviewAdapter.drawContactsRequested())
            return new JoltContactOverlaySnapshot(frame,0,shapes);

        for(Map.Entry<BodyPartModelData,List<BodyPartPhysicsChains.Chain>> entry:
                previewPhysicsChains.entrySet())
        {
            BodyPartModelData data=entry.getKey();
            if(entry.getValue()==null || entry.getValue().isEmpty())
                continue;
            int mode=0;
            for(BodyPartPhysicsChains.Chain chain:entry.getValue())
            {
                String id=data.getTimelineId()+"/"+chain.root.getName();
                int sceneMode=joltPreview.contactModeFor(id,frame);
                mode=Math.max(mode,sceneMode);
                float[][] segments=joltPreview.hairSegmentsFor(id,frame);
                if(segments==null)continue;
                activeScenes++;
                if(!anyBoxesAdded)
                {
                    float[][] installed=joltPreview.additionalBoxesFor(id,frame);
                    float[][] primitiveRows=joltPreview.additionalShapesFor(id,frame);
                    if(installed!=null)
                    {
                        for(int index=0;index<installed.length;index++)
                            try
                            {
                                String owner=index<boxOwners.size()
                                        ?boxOwners.get(index):"unknown";
                                int primitive=JoltColliderPrimitives.BOX;
                                if(primitiveRows!=null &&
                                        index<primitiveRows.length &&
                                        primitiveRows[index]!=null &&
                                        primitiveRows[index].length==11)
                                    primitive=(int)primitiveRows[index][10];
                                boxes.add(new JoltContactOverlaySnapshot.Box(
                                        installed[index],owner,primitive));
                            }
                            catch(IllegalArgumentException ignored) {}
                        anyBoxesAdded=true;
                    }
                }
                for(float[] link:segments)
                {
                    if(link==null || link.length!=7)continue;
                    try
                    {
                        shapes.add(new JoltContactOverlaySnapshot.Shape(
                                JoltContactOverlaySnapshot.Kind.HAIR,
                                new float[] {link[0],link[1],link[2]},
                                new float[] {link[3],link[4],link[5]},
                                link[6],id));
                    }
                    catch(IllegalArgumentException ignored)
                    {
                        // Never draw a fabricated physical link.
                    }
                }
            }

            Integer capturedFrame=joltTorsoFitFrames.get(data);
            if(mode==0 || capturedFrame==null ||
                    capturedFrame.intValue()!=frame)
                continue;
            if(mode==3)
            {
                BodyPartJoltMultiTorsoFit.Segments multi =
                        joltMultiTorsoFits.get(data);
                if(multi!=null)
                {
                    float[] actorWorld=actorFrameFor(frame);
                    if(actorWorld!=null)
                        multi=BodyPartJoltMultiTorsoFit.world(multi,actorWorld);
                    float[][] starts=multi.starts(),ends=multi.ends();
                    float[] radii=multi.radii();
                    for(int i=0;i<radii.length;i++)
                        try
                        {
                            shapes.add(new JoltContactOverlaySnapshot.Shape(
                                    JoltContactOverlaySnapshot.Kind.TORSO,
                                    starts[i],ends[i],radii[i]));
                        }
                        catch(IllegalArgumentException ignored) {}
                }
            }
            else if(mode==1)
            {
                BodyPartJoltTorsoFit.Fit single=joltTorsoFits.get(data);
                if(single!=null)
                    try
                    {
                        float[] actorWorld=actorFrameFor(frame);
                        float[] a=actorWorld==null?single.a:
                                BodyPartJoltActorSpace.point(actorWorld,single.a);
                        float[] b=actorWorld==null?single.b:
                                BodyPartJoltActorSpace.point(actorWorld,single.b);
                        shapes.add(new JoltContactOverlaySnapshot.Shape(
                                JoltContactOverlaySnapshot.Kind.TORSO,
                                a,b,single.radius));
                    }
                    catch(IllegalArgumentException ignored) {}
            }
        }
        return new JoltContactOverlaySnapshot(frame,activeScenes,shapes,boxes,
                actorFrameFor(frame)!=null);
    }

    /**
     * The ONLY source of kinematic Jolt torso contacts: an independently
     * rendered Emoticons bone axis and the CPU-skinned, torso-weight-selected
     * mesh from that same normal render. A failed fit disables contacts.
     * Do not substitute BodyPartPhysicsBodyCapsules' old guessed 0.18 radius.
     */
    private static final class JoltTorsoSkinFit
    {
        final BodyPartJoltTorsoFit.Fit single;
        final BodyPartJoltMultiTorsoFit.Segments compound;
        JoltTorsoSkinFit(BodyPartJoltTorsoFit.Fit single,
                BodyPartJoltMultiTorsoFit.Segments compound)
        {
            this.single=single;
            this.compound=compound;
        }
    }

    private static JoltTorsoSkinFit fitNativeJoltTorso(
            Object emoticonsMorph,float[] lowBodyMatrix)
    {
        if(lowBodyMatrix==null)return null;
        String torsoName=BodyPartPhysicsTorsoLocator.findUpperTorsoName(emoticonsMorph);
        if(torsoName==null)return null;
        float[] torso=BodyPartEmoticonsAttachmentProbe.lastRenderedBoneMatrix(
                emoticonsMorph,torsoName);
        if(torso==null)return null;
        float[] a={lowBodyMatrix[12],lowBodyMatrix[13],lowBodyMatrix[14]};
        float[] b={torso[12],torso[13],torso[14]};
        for(EmoticonsCollisionGeometryProvider.MeshSnapshot mesh:
                EmoticonsCollisionGeometryProvider.snapshot(emoticonsMorph))
        {
            if(mesh==null || !"body".equals(mesh.name))continue;
            EmoticonsCollisionGeometryProvider.MeshSnapshot region =
                    EmoticonsSkinCollisionGroups.select(
                            emoticonsMorph,mesh,
                            java.util.EnumSet.of(
                                    EmoticonsSkinCollisionGroups.Group.TORSO));
            if(region==null || region.triangleCount()<5)continue;
            BodyPartJoltTorsoFit.Fit base =
                    BodyPartJoltTorsoFit.fit(a,b,region.vertices,region.triangles);
            if(base==null)return null;
            BodyPartJoltMultiTorsoFit.Segments compound =
                    BodyPartJoltPreviewAdapter.compoundTorsoRequested()
                    ? BodyPartJoltMultiTorsoFit.fit(base,
                            region.vertices,region.triangles)
                    : null;
            return new JoltTorsoSkinFit(base,compound);
        }
        return null;
    }

    private final Map<BodyPartModelData, BodyPartMeshContactTracker> torsoTrackers =
            new IdentityHashMap<BodyPartModelData, BodyPartMeshContactTracker>();
    /** Avoid rebuilding the skinned torso mesh repeatedly for the same frame. */
    private final Map<BodyPartModelData, Integer> torsoTrackerSampledFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    /** Per-cube starting penetration metric, for comparison only. */
    private final Map<BodyPartModelData, Map<String, Integer>> cubeBaselineNegative =
            new IdentityHashMap<BodyPartModelData, Map<String, Integer>>();
    private final Map<BodyPartModelData, EmoticonsMeshCollisionSolver> torsoCollisionMeshes =
            new IdentityHashMap<BodyPartModelData, EmoticonsMeshCollisionSolver>();
    private final Map<BodyPartModelData, Integer> torsoCollisionMeshFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    /** Stable torso-side choice from the real hair volumes, not root pivot. */
    private final Map<BodyPartModelData, Float> backHairOutwardSigns =
            new IdentityHashMap<BodyPartModelData, Float>();

    /**
     * Read-only simulation of candidate torso contact correction.
     * Does not alter Chameleon bones, timeline or position solver state.
     * Rebuilds from the current CPU-skinned Emoticons body every frame.
     */
    private void inspectTorsoRecoveryCandidate(Object morph,
            BodyPartModelData data, BodyPart part, float[] lowBody, int frame)
    {
        if (part == null || part.morph == null || part.morph.get() == null)
            return;
        Integer sampledFrame = torsoTrackerSampledFrames.get(data);
        if (sampledFrame != null && sampledFrame.intValue() == frame)
            return;
        try
        {
            BodyPartPhysicsWorldPose.MatrixSource local =
                    BodyPartChameleonMatrixProbe.source(part.morph.get());
            if (local == null) return;
            BodyPartChainSolver.Vec3[] joints=new BodyPartChainSolver.Vec3[4];
            float[] attachment=BodyPartPhysicsAttachment.worldMatrix(lowBody,part);
            for (int i=0;i<4;i++)
            {
                float[] bone=local.getBoneWorldMatrix("back_strand_"+(i+1));
                if(bone==null)return;
                joints[i]=BodyPartPhysicsPoseAdapter.position(
                        BodyPartPhysicsMatrixSpace.multiply(attachment,bone));
            }
            for (EmoticonsCollisionGeometryProvider.MeshSnapshot mesh :
                    EmoticonsCollisionGeometryProvider.snapshot(morph))
            {
                if(!"body".equals(mesh.name))continue;
                EmoticonsCollisionGeometryProvider.MeshSnapshot region =
                        EmoticonsSkinCollisionGroups.select(morph,mesh,
                                java.util.EnumSet.of(
                                        EmoticonsSkinCollisionGroups.Group.TORSO));
                if(region==null || region.triangleCount()==0)return;
                EmoticonsMeshCollisionSolver surface =
                        new EmoticonsMeshCollisionSolver(
                                java.util.Collections.singletonList(region));
                BodyPartMeshContactTracker tracker=torsoTrackers.get(data);
                if(tracker==null)
                {
                    tracker=new BodyPartMeshContactTracker();
                    torsoTrackers.put(data,tracker);
                }
                torsoCollisionMeshes.put(data,surface);
                torsoCollisionMeshFrames.put(data,Integer.valueOf(frame));
                BodyPartMeshContactTracker.Result report =
                        tracker.inspect(frame,joints,surface);
                torsoTrackerSampledFrames.put(data, Integer.valueOf(frame));
                // These native matrices reflect the pose actually rendered
                // in the preview, including previously applied physics.
                // Sample early frames to locate immediate cube penetration.
                if(frame<=5 || frame==20 || frame==60)
                {
                    for (int cubeIndex=1;cubeIndex<=4;cubeIndex++)
                    {
                        String boneName="back_strand_"+cubeIndex;
                        BodyPartChameleonCubeProbe.Cube cube =
                                BodyPartChameleonCubeProbe.getReference(boneName);
                        float[] boneMatrix=local.getBoneWorldMatrix(boneName);
                        if(cube==null || boneMatrix==null)continue;
                        float[] worldBone=BodyPartPhysicsMatrixSpace.multiply(
                                attachment,boneMatrix);
                        BodyPartChainSolver.Vec3[] samples=cube.faceSamples(worldBone);
                        int close=0,negative=0;
                        float smallest=Float.POSITIVE_INFINITY;
                        for(BodyPartChainSolver.Vec3 sample:samples)
                        {
                            EmoticonsMeshCollisionSolver.Contact contact=
                                    surface.nearest(sample,0.18F);
                            if(contact==null)continue;
                            close++;
                            smallest=Math.min(smallest,contact.distance);
                            if(surface.windingSignedDistance(sample,contact)<-0.002F)
                                negative++;
                        }
                        Map<String,Integer> baseline=cubeBaselineNegative.get(data);
                        if(baseline==null)
                        {
                            baseline=new java.util.HashMap<String,Integer>();
                            cubeBaselineNegative.put(data,baseline);
                        }
                        if(frame==0)baseline.put(boneName,Integer.valueOf(negative));
                        Integer initial=baseline.get(boneName);
                        System.out.println("[BBS Animation Editor][Physics] "
                                +"BACK CUBE CONTACT frame="+frame
                                +" bone="+boneName
                                +" faceSamplesNear="+close+"/"+samples.length
                                +" windingNegative="+negative
                                +" initialWindingNegative="+
                                        (initial==null?"unknown":initial.toString())
                                +" negativeDelta="+
                                        (initial==null?"unknown":
                                                Integer.toString(negative-initial.intValue()))
                                +" nearestDistance="+
                                        (close==0?"none":Float.toString(smallest))
                                +" (reference geo; signs are not solid classification)");
                    }
                }
                if(frame==0 || frame==20 || frame==60)
                    System.out.println("[BBS Animation Editor][Physics] "
                            +"TORSO RECOVERY CANDIDATE frame="+frame
                            +" swept="+report.sweptCrossings
                            +" segmentCrossings="+report.staticCrossings
                            +" suggestedParticles="+report.recoveryCandidates
                            +" maxShift="+report.largestCorrection
                            +" (diagnostic only; winding side not verified)");
                break;
            }
        }
        catch(RuntimeException error)
        {
            if(frame==0 || frame==20 || frame==60)
                System.out.println("[BBS Animation Editor][Physics] "
                        +"TORSO RECOVERY CANDIDATE unavailable: "
                        +error.getClass().getSimpleName());
        }
    }

    /**
     * Coordinate-space probe only. Mesh vertices are in the skinned mesh's
     * frame; BOBJ bone matrices and attached Chameleon are sampled separately.
     * We intentionally do not collide until that relationship is established.
     */
    private final Map<BodyPartModelData, Integer> collisionSpaceLoggedFrame =
            new IdentityHashMap<BodyPartModelData, Integer>();

    private void diagnoseEmoticonsCollisionSpace(Object morph,
            BodyPartModelData data, BodyPart childPart, float[] lowBody,
            float[] torso, int frame)
    {
        Integer previous = collisionSpaceLoggedFrame.get(data);
        if (previous != null && previous.intValue() == frame) return;
        try
        {
            BodyPartPhysicsWorldPose.MatrixSource local =
                    BodyPartChameleonMatrixProbe.source(childPart.morph.get());
            if (local == null) return;
            float[] root = local.getBoneWorldMatrix("back_strand_1");
            float[] last = local.getBoneWorldMatrix("back_strand_4");
            if (root == null || last == null) return;
            float[] attachment = BodyPartPhysicsAttachment.worldMatrix(lowBody, childPart);
            BodyPartChainSolver.Vec3 rootPos = BodyPartPhysicsPoseAdapter.position(
                    BodyPartPhysicsMatrixSpace.multiply(attachment, root));
            BodyPartChainSolver.Vec3 lastPos = BodyPartPhysicsPoseAdapter.position(
                    BodyPartPhysicsMatrixSpace.multiply(attachment, last));
            BodyPartChainSolver.Vec3 lowPos = BodyPartPhysicsPoseAdapter.position(lowBody);
            BodyPartChainSolver.Vec3 bodyPos = torso == null ? null :
                    BodyPartPhysicsPoseAdapter.position(torso);
            // Diagnostic assumption ONLY: BOBJ matrices and CPU-skinned mesh
            // share the same actor/model-local origin. Confirm with contacts
            // before allowing any collision forces to use this coordinate map.
            if (frame == 0 || frame == 20 || frame == 60)
            {
                java.util.List<EmoticonsCollisionGeometryProvider.MeshSnapshot> meshes =
                        EmoticonsCollisionGeometryProvider.snapshot(morph);
                EmoticonsMeshCollisionSolver surface =
                        new EmoticonsMeshCollisionSolver(meshes);
                StringBuilder distances = new StringBuilder();
                for (int i = 1; i <= 4; i++)
                {
                    float[] bone = local.getBoneWorldMatrix("back_strand_" + i);
                    if (bone == null) continue;
                    BodyPartChainSolver.Vec3 p = BodyPartPhysicsPoseAdapter.position(
                            BodyPartPhysicsMatrixSpace.multiply(attachment, bone));
                    EmoticonsMeshCollisionSolver.Contact contact =
                            surface.nearest(p, 0.5F);
                    distances.append(" back_strand_").append(i).append('=');
                    distances.append(contact == null ? "none-within-0.5" :
                            Float.toString(contact.distance));
                    EmoticonsMeshCollisionSolver.RayProbe parity =
                            surface.probeRayPositiveX(p);
                    distances.append("[rayHits=").append(parity.intersections)
                            .append(",odd=").append(parity.odd).append(']');
                }
                // Original BOBJ vertex influences, independent of skinned
                // vertex position. Diagnostic only; no collision forces.
                if (frame == 0)
                {
                    EmoticonsSkinCollisionGroups.Result groups =
                            EmoticonsSkinCollisionGroups.analyze(morph, "body");
                    System.out.println("[BBS Animation Editor][Physics] "
                            + "SKIN COLLISION GROUPS mesh=body " + groups);
                    for (EmoticonsCollisionGeometryProvider.MeshSnapshot mesh : meshes)
                    {
                        if (!"body".equals(mesh.name)) continue;
                        for (EmoticonsSkinCollisionGroups.Group group :
                                EmoticonsSkinCollisionGroups.Group.values())
                        {
                            EmoticonsCollisionGeometryProvider.MeshSnapshot region =
                                    EmoticonsSkinCollisionGroups.select(
                                            morph, mesh,
                                            java.util.EnumSet.of(group));
                            System.out.println("[BBS Animation Editor][Physics] "
                                    + "SKIN GROUP REGION name=" + group
                                    + " triangles=" + (region == null ?
                                            "unavailable" : Integer.toString(region.triangleCount()))
                                    + " (diagnostic only)");
                        }
                        break;
                    }

                }
                // Actual skin-weight-selected geometry contact queries.
                // Read-only until the mesh-local/strand coordinate transform
                // and outward directions are independently verified.
                for (EmoticonsCollisionGeometryProvider.MeshSnapshot mesh : meshes)
                {
                    if (!"body".equals(mesh.name)) continue;
                    for (EmoticonsSkinCollisionGroups.Group group :
                            new EmoticonsSkinCollisionGroups.Group[] {
                                    EmoticonsSkinCollisionGroups.Group.TORSO,
                                    EmoticonsSkinCollisionGroups.Group.LEFT_ARM,
                                    EmoticonsSkinCollisionGroups.Group.RIGHT_ARM })
                    {
                        EmoticonsCollisionGeometryProvider.MeshSnapshot region =
                                EmoticonsSkinCollisionGroups.select(
                                        morph, mesh, java.util.EnumSet.of(group));
                        if (region == null) continue;
                        EmoticonsMeshCollisionSolver filtered =
                                new EmoticonsMeshCollisionSolver(
                                        java.util.Collections.singletonList(region));
                        StringBuilder contacts = new StringBuilder();
                        for (int boneNumber = 1; boneNumber <= 4; boneNumber++)
                        {
                            float[] bone = local.getBoneWorldMatrix(
                                    "back_strand_" + boneNumber);
                            if (bone == null) continue;
                            BodyPartChainSolver.Vec3 point =
                                    BodyPartPhysicsPoseAdapter.position(
                                            BodyPartPhysicsMatrixSpace.multiply(
                                                    attachment, bone));
                            EmoticonsMeshCollisionSolver.Contact hit =
                                    filtered.nearest(point, 0.5F);
                            contacts.append(" b").append(boneNumber).append('=')
                                    .append(hit == null ? "none" :
                                            Float.toString(hit.distance));
                            if (hit != null)
                                contacts.append("[windingSigned=")
                                        .append(filtered.windingSignedDistance(point, hit))
                                        .append(",tri=").append(hit.triangleIndex)
                                        .append(']');
                        }
                        if (group == EmoticonsSkinCollisionGroups.Group.TORSO)
                        {
                            StringBuilder crossings = new StringBuilder();
                            BodyPartChainSolver.Vec3 previousPoint = null;
                            for (int boneNumber = 1; boneNumber <= 4; boneNumber++)
                            {
                                float[] bone = local.getBoneWorldMatrix(
                                        "back_strand_" + boneNumber);
                                if (bone == null) break;
                                BodyPartChainSolver.Vec3 point =
                                        BodyPartPhysicsPoseAdapter.position(
                                                BodyPartPhysicsMatrixSpace.multiply(
                                                        attachment, bone));
                                if (previousPoint != null)
                                {
                                    EmoticonsMeshCollisionSolver.SegmentHit crossing =
                                            filtered.intersectSegment(previousPoint, point);
                                    crossings.append(" segment")
                                            .append(boneNumber - 1).append('-')
                                            .append(boneNumber).append('=');
                                    if (crossing == null) crossings.append("none");
                                    else crossings.append("t=").append(crossing.fraction)
                                            .append(",tri=").append(crossing.triangleIndex);
                                }
                                previousPoint = point;
                            }
                            System.out.println("[BBS Animation Editor][Physics] "
                                    + "TORSO SEGMENT CROSSINGS frame=" + frame
                                    + crossings.toString()
                                    + " (geometric only; no corrections)");
                        }
                        System.out.println("[BBS Animation Editor][Physics] "
                                + "GROUP CONTACT frame=" + frame
                                + " group=" + group
                                + " triangles=" + filtered.triangleCount()
                                + " distances:" + contacts
                                + " (mesh-local alignment unverified; no forces)");
                    }
                    break;
                }
                // Filter only the native 'body' mesh to a moving upper-torso
                // height band. A candidate region, not confirmed collision.
                if (bodyPos != null)
                {
                    EmoticonsCollisionGeometryProvider.MeshSnapshot torsoCandidates =
                            EmoticonsTorsoGeometry.selectBodyTorso(
                                    meshes, bodyPos.y, lowPos.y);
                    if (torsoCandidates != null)
                    {
                        java.util.List<EmoticonsCollisionGeometryProvider.MeshSnapshot> subset =
                                java.util.Collections.singletonList(torsoCandidates);
                        EmoticonsMeshCollisionSolver torsoSurface =
                                new EmoticonsMeshCollisionSolver(subset);
                        StringBuilder measurements = new StringBuilder();
                        for (int i = 1; i <= 4; i++)
                        {
                            float[] bone = local.getBoneWorldMatrix("back_strand_" + i);
                            if (bone == null) continue;
                            BodyPartChainSolver.Vec3 point =
                                    BodyPartPhysicsPoseAdapter.position(
                                        BodyPartPhysicsMatrixSpace.multiply(attachment, bone));
                            EmoticonsMeshCollisionSolver.Contact hit =
                                    torsoSurface.nearest(point, 0.5F);
                            measurements.append(" b").append(i).append('=')
                                    .append(hit == null ? "none" : Float.toString(hit.distance));
                        }
                        System.out.println("[BBS Animation Editor][Physics] "
                                + "TORSO CANDIDATE frame=" + frame
                                + " triangles=" + torsoSurface.triangleCount()
                                + " boneY=(" + bodyPos.y + "," + lowPos.y + ")"
                                + " distances:" + measurements.toString()
                                + " (mesh-local assumed; diagnostic only)");
                    }
                }
                // Break down ambiguous multi-shell ray parity by source mesh.
                // This is read-only: mesh-local coordinates are not yet
                // certified against the attached child-bone space.
                for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++)
                {
                    EmoticonsCollisionGeometryProvider.MeshSnapshot mesh =
                            meshes.get(meshIndex);
                    java.util.List<EmoticonsCollisionGeometryProvider.MeshSnapshot> one =
                            java.util.Collections.singletonList(mesh);
                    EmoticonsMeshCollisionSolver meshSurface =
                            new EmoticonsMeshCollisionSolver(one);
                    EmoticonsCollisionGeometryProvider.Bounds bounds =
                            EmoticonsCollisionGeometryProvider.bounds(one);
                    StringBuilder meshResults = new StringBuilder();
                    for (int i = 1; i <= 4; i++)
                    {
                        float[] bone = local.getBoneWorldMatrix("back_strand_" + i);
                        if (bone == null) continue;
                        BodyPartChainSolver.Vec3 point =
                                BodyPartPhysicsPoseAdapter.position(
                                    BodyPartPhysicsMatrixSpace.multiply(attachment, bone));
                        EmoticonsMeshCollisionSolver.Contact hit =
                                meshSurface.nearest(point, 0.5F);
                        EmoticonsMeshCollisionSolver.RayProbe ray =
                                meshSurface.probeRayPositiveX(point);
                        meshResults.append(" b").append(i).append(":dist=")
                                .append(hit == null ? "none" : Float.toString(hit.distance))
                                .append(",hits=").append(ray.intersections);
                    }
                    System.out.println("[BBS Animation Editor][Physics] "
                            + "MESH PART frame=" + frame
                            + " index=" + meshIndex
                            + " name=" + mesh.name
                            + " triangles=" + meshSurface.triangleCount()
                            + " bounds=" + (bounds == null ? "none" : bounds.toString())
                            + meshResults.toString()
                            + " (diagnostic only)");
                }
                System.out.println("[BBS Animation Editor][Physics] "
                        + "MESH CONTACT PROBE frame=" + frame
                        + " indexedTriangles=" + surface.triangleCount()
                        + " distances(mesh-local assumed):" + distances.toString()
                        + " (ray parity is heuristic, not validated solid classification; no correction)");
            }
            collisionSpaceLoggedFrame.put(data, Integer.valueOf(frame));
            System.out.println("[BBS Animation Editor][Physics] COLLISION SPACE frame=" + frame
                    + " low_body.native=(" + lowPos.x + "," + lowPos.y + "," + lowPos.z + ")"
                    + " body.native=" + (bodyPos == null ? "missing" :
                    "(" + bodyPos.x + "," + bodyPos.y + "," + bodyPos.z + ")")
                    + " back_root.attached=(" + rootPos.x + "," + rootPos.y + "," + rootPos.z + ")"
                    + " back_last.attached=(" + lastPos.x + "," + lastPos.y + "," + lastPos.z + ")"
                    + " (bone-frame vs mesh-local bounds; transform unverified)");
        }
        catch (RuntimeException error)
        {
            System.out.println("[BBS Animation Editor][Physics] COLLISION SPACE unavailable: "
                    + error.getClass().getSimpleName());
        }
    }

    /**
     * One-shot sanity check of the actual native Chameleon model pose after
     * the ordinary render. A successful write to morph.pose is not proof
     * that a bone was rotated during draw. No second render is invoked.
     */
    /**
     * The only trustworthy A/B boundary: measure native Chameleon pivots
     * AFTER its normal render, and compare with the physical direction that
     * produced the Jolt axis-angle applied to THIS SAME frame. Never infer an
     * axis sign from a screenshot or change the simulated pose here.
     */
    /**
     * Native ChameleonRenderer.postRender() is invoked under identity by our
     * matrix probe. It therefore omits the OUTER ChameleonMorph.render()
     * matrix. Metamorph's nested morph renderer supplies implicit +180 Y
     * yaw after the BodyPart transform, and the child morph's own scale.
     */
    private static float[] worldFromRenderedChameleonChild(
            float[] parentWorld,BodyPart part)
    {
        float[] worldPart=BodyPartPhysicsAttachment.worldMatrix(parentWorld,part);
        if(!BodyPartJoltPreviewAdapter.isRequested() || !JOLT_EXACT_CHILD_SPACE)
            return worldPart;
        if(part==null || part.morph==null || part.morph.get()==null ||
                !part.morph.get().getClass().getName().endsWith(".ChameleonMorph"))
            return worldPart;
        float scale=BodyPartChameleonChildRenderSpace.renderScale(
                part.morph.get(),
                net.minecraft.client.Minecraft.getMinecraft().getRenderPartialTicks());
        return BodyPartChameleonChildRenderSpace.withChildRender(worldPart,scale);
    }

    private void diagnoseJoltRenderedDirections(BodyPartModelData data,
            BodyPart part, float[] lowBodyWorld, int frame)
    {
        if (!JOLT_DIRECTION_DIAGNOSTIC || part == null || part.morph == null ||
                part.morph.get() == null || lowBodyWorld == null)
            return;
        Integer sampledFrame = joltAppliedFrames.get(data);
        Map<String, float[]> expected = joltAppliedDirections.get(data);
        if (sampledFrame == null || sampledFrame.intValue() != frame ||
                expected == null || expected.isEmpty())
            return;
        try
        {
            BodyPartPhysicsWorldPose.MatrixSource local =
                    BodyPartChameleonMatrixProbe.source(part.morph.get());
            if (local == null) return;
            float[] attachment =
                    worldFromRenderedChameleonChild(lowBodyWorld,part);
            float[] actorFrame=actorFrameFor(frame);
            if(actorFrame!=null)
                attachment=BodyPartJoltActorSpace.matrix(actorFrame,attachment);
            BodyPartPhysicsWorldPose.MatrixSource visual =
                    BodyPartPhysicsWorldPose.worldSource(attachment, local);
            Map<String, String> previous = lastJoltDirectionReports.get(data);
            if (previous == null)
            {
                previous = new java.util.HashMap<String, String>();
                lastJoltDirectionReports.put(data, previous);
            }
            Map<String, Long> lastTimes = lastJoltDirectionTimes.get(data);
            if (lastTimes == null)
            {
                lastTimes = new java.util.HashMap<String, Long>();
                lastJoltDirectionTimes.put(data, lastTimes);
            }
            long now = System.currentTimeMillis();
            Map<String, float[]> nativeVectors = joltAppliedNativeDirections.get(data);
            float[] sourceParent = joltAppliedParentMatrices.get(data);
            float sourceShift = sourceParent == null ? Float.NaN :
                    BodyPartJoltPoseDiagnostic.parentRotationChangeDegrees(
                            sourceParent, lowBodyWorld);
            for (BodyPartPhysicsChains.Chain chain : getPreviewPhysicsChains(data))
            {
                // Root and interior links have an ACTUAL next pivot.
                // Never invent a leaf end point in the diagnostic.
                for (int i = 0; i + 1 < chain.bones.size(); i++)
                {
                    String bone = chain.bones.get(i).getName();
                    String next = chain.bones.get(i + 1).getName();
                    float[] wanted = expected.get(bone);
                    if (wanted == null) continue;
                    float[] current = visual.getBoneWorldMatrix(bone);
                    float[] child = visual.getBoneWorldMatrix(next);
                    if (current == null || child == null) continue;
                    float[] actual = BodyPartJoltPoseDiagnostic.direction(current, child);
                    float error = BodyPartJoltPoseDiagnostic.errorDegrees(wanted, actual);
                    float[] raw = nativeVectors == null ? null : nativeVectors.get(bone);
                    float bridgeError = raw == null ? Float.NaN :
                            BodyPartJoltPoseDiagnostic.errorDegrees(raw, wanted);
                    // If the parent has been edited on a PAUSED timeline
                    // frame, the frozen Jolt pose still belongs to the old
                    // parent for this draw. Do not classify that transient
                    // as a bad Chameleon writeback.
                    String cause = Float.isFinite(sourceShift) && sourceShift >= 1F
                            ? "SOURCE_CHANGED" : Float.isFinite(bridgeError) &&
                            bridgeError >= 10F ? "BRIDGE_MISMATCH" :
                            error >= 10F ? "WRITEBACK_MISMATCH" :
                            raw != null && raw[1] > 0.2F ? "NATIVE_UP" : "aligned";
                    String report = String.format(java.util.Locale.ROOT,
                            "frame=%d bone=%s raw=%s intended=(%.2f,%.2f,%.2f) "
                                    + "rendered=(%.2f,%.2f,%.2f) bridgeDeg=%.1f "
                                    + "errorDeg=%.1f parentChangeDeg=%.1f",
                            frame, bone,
                            raw == null ? "unavailable" :
                                    String.format(java.util.Locale.ROOT,
                                            "(%.2f,%.2f,%.2f)",
                                            raw[0],raw[1],raw[2]),
                            wanted[0],wanted[1],wanted[2],
                            actual[0],actual[1],actual[2],
                            bridgeError,error,sourceShift);
                    Long lastTime = lastTimes.get(bone);
                    if (!report.equals(previous.get(bone)) &&
                            (lastTime == null || now - lastTime.longValue() >= 1000L))
                    {
                        previous.put(bone, report);
                        lastTimes.put(bone, Long.valueOf(now));
                        System.out.println("[BBS Animation Editor][Jolt][Compare] "
                                + cause + " " + report + " (world-space segment direction)");

                        /*
                         * Probe the ACTUAL decoded Chameleon quads, after
                         * ModelParser mirrored their X coordinates and after
                         * each cube's own pivot/rotation. The old static
                         * Horst reference is only a labeled fallback.
                         */
                        BodyPartChameleonCubeProbe.Cube reference =
                                BodyPartChameleonCubeProbe.getReference(bone);
                        try
                        {
                            BodyPartChameleonRenderedCubeProbe.Result loaded =
                                    BodyPartChameleonRenderedCubeProbe.inspect(
                                            part.morph.get(),bone,current);
                            BodyPartJoltCubeGeometry.Measurement geometry = loaded != null
                                    ? loaded.measurement : reference != null
                                    ? BodyPartJoltCubeGeometry.measure(current,
                                            reference.pivot,reference.origin,
                                            reference.size,reference.inflate)
                                    : null;
                            if (geometry != null)
                            {
                                float cubeVsPivot =
                                        BodyPartJoltCubeGeometry.alignmentDegrees(
                                                geometry.cubeDown,actual);
                                float tipVsPivot =
                                        BodyPartJoltCubeGeometry.alignmentDegrees(
                                                geometry.pivotToTip,actual);
                                float cubeVsNative = raw == null ? Float.NaN :
                                        BodyPartJoltCubeGeometry.alignmentDegrees(
                                                geometry.cubeDown,raw);
                                String classification = Float.isFinite(sourceShift)
                                        && sourceShift >= 1F ? "SOURCE_CHANGED"
                                        : cubeVsPivot > 90F ? "CUBE_REVERSED"
                                        : "CUBE_ALIGNED";
                                String source = loaded == null
                                        ? "HorstReferenceUnverified" : "ActualChameleonQuads";
                                int cubes = loaded == null ? 1 : loaded.cubeCount;
                                int vertices = loaded == null ? 0 : loaded.vertexCount;
                                System.out.println(String.format(java.util.Locale.ROOT,
                                        "[BBS Animation Editor][Jolt][Cube] %s "
                                        + "frame=%d bone=%s source=%s cubes=%d vertices=%d "
                                        + "cubeDown=(%.2f,%.2f,%.2f) "
                                        + "pivotNext=(%.2f,%.2f,%.2f) "
                                        + "cubePivotDeg=%.1f pivotTipDeg=%.1f "
                                        + "cubeNativeDeg=%.1f cubeCenterY=%.3f "
                                        + "cubeTipY=%.3f pivotTipDeltaY=%.3f "
                                        + "cubeBoundsY=[%.3f,%.3f]",
                                        classification,frame,bone,source,cubes,vertices,
                                        geometry.cubeDown[0],geometry.cubeDown[1],
                                        geometry.cubeDown[2],
                                        actual[0],actual[1],actual[2],
                                        cubeVsPivot,tipVsPivot,cubeVsNative,
                                        geometry.cubeCenterWorld[1],
                                        geometry.cubeTipWorld[1],
                                        geometry.pivotToTipWorldY,
                                        geometry.minWorldY,geometry.maxWorldY));
                            }
                        }
                        catch (RuntimeException geometryError)
                        {
                            System.err.println("[BBS Animation Editor][Jolt][Cube] "
                                    + "Measurement unavailable for " + bone
                                    + ": " + geometryError.getMessage());
                        }
                    }
                }
            }
        }
        catch (RuntimeException error)
        {
            System.err.println("[BBS Animation Editor][Jolt][Compare] "
                    + "Post-render direction comparison unavailable: " + error);
        }
    }

    private void diagnoseVisualHairResult(BodyPartModelData data, BodyPart part, int frame)
    {
        if (frame != 20 && frame != 60) return;
        if (part == null || part.morph == null || part.morph.get() == null) return;
        String key = data.getTimelineId() + ":" + frame;
        if (key.equals(lastVisualHairDiagnostic)) return;
        try
        {
            Object morph = part.morph.get();
            // Read only the current matrices; the caller has already rendered.
            BodyPartPhysicsWorldPose.MatrixSource source =
                    BodyPartChameleonMatrixProbe.source(morph);
            if (source == null) return;
            float[] root = source.getBoneWorldMatrix("left_strand_1");
            float[] next = source.getBoneWorldMatrix("left_strand_2");
            if (root == null || next == null) return;
            float[] relative = BodyPartPhysicsMatrixSpace.multiply(
                    BodyPartPhysicsAttachment.localMatrix(part), root);
            float[] child = BodyPartPhysicsMatrixSpace.multiply(
                    BodyPartPhysicsAttachment.localMatrix(part), next);
            BodyPartChainSolver.Vec3 direction = BodyPartPhysicsPoseAdapter.position(child)
                    .sub(BodyPartPhysicsPoseAdapter.position(relative));
            lastVisualHairDiagnostic = key;
            System.out.println("[BBS Animation Editor][Physics] VISUAL CHECK frame=" + frame
                    + " partY=" + part.rotate.y
                    + " chameleonRootToChild=(" + direction.x + "," + direction.y
                    + "," + direction.z + ")"
                    + " transitionActive=" + chameleonTransitionActive(morph)
                    + " cleanPoseX=" + leftRootBaseRotation.get(data)
                    + " physicsPoseX=" + leftRootAppliedRotation.get(data)
                    + " currentPoseX=" + readChameleonPoseRotationX(morph, "left_strand_1")
                    + " nativeBone=" + describeChameleonNativeBone(morph, "left_strand_1"));
        }
        catch (Throwable error)
        {
            lastVisualHairDiagnostic = key;
            System.out.println("[BBS Animation Editor][Physics] VISUAL CHECK unavailable: "
                    + error.getClass().getSimpleName());
        }
    }

    private String lastHairABLabel = "";

    @SuppressWarnings("unchecked")
    private static boolean setChameleonPoseRotationX(Object morph, String bone, float radians)
    {
        try
        {
            Object pose = morph.getClass().getField("pose").get(morph);
            if (pose == null) return false;
            Object raw = pose.getClass().getField("bones").get(pose);
            if (!(raw instanceof Map)) return false;
            Map<String, Object> bones = (Map<String, Object>) raw;
            Object transform = bones.get(bone);
            if (transform == null)
            {
                Class<?> type = Class.forName(
                        "mchorse.chameleon.metamorph.pose.AnimatedPoseTransform");
                transform = type.getConstructor(String.class).newInstance(bone);
                bones.put(bone, transform);
            }
            transform.getClass().getField("rotateX").setFloat(transform, radians);
            return true;
        }
        catch (Throwable ignored) { return false; }
    }

    /** Read only the actual post-render bone state and attached geometry. */
    private static String describeChameleonNativeBone(Object morph, String name)
    {
        try
        {
            Object wrapper = morph.getClass().getMethod("getModel").invoke(morph);
            Object model = wrapper.getClass().getField("model").get(wrapper);
            Object roots = model.getClass().getField("bones").get(model);
            Object found = findChameleonNativeBone(roots, name);
            if (found == null) return "missing";
            Object transform = found.getClass().getField("current").get(found);
            Object rotation = transform.getClass().getField("rotation").get(transform);
            java.lang.reflect.Field x = rotation.getClass().getField("x");
            java.lang.reflect.Field y = rotation.getClass().getField("y");
            java.lang.reflect.Field z = rotation.getClass().getField("z");
            Object cubes = found.getClass().getField("cubes").get(found);
            Object children = found.getClass().getField("children").get(found);
            return "rotationDeg=(" + x.getFloat(rotation) + ","
                    + y.getFloat(rotation) + "," + z.getFloat(rotation)
                    + ") cubes=" + ((java.util.List<?>) cubes).size()
                    + " children=" + ((java.util.List<?>) children).size();
        }
        catch (Throwable error)
        {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    private static Object findChameleonNativeBone(Object list, String name) throws Exception
    {
        if (!(list instanceof java.util.List)) return null;
        for (Object bone : (java.util.List<?>) list)
        {
            if (name.equals(bone.getClass().getField("id").get(bone))) return bone;
            Object child = findChameleonNativeBone(
                    bone.getClass().getField("children").get(bone), name);
            if (child != null) return child;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static float[] readChameleonPoseEuler(Object morph, String bone)
    {
        try
        {
            Object pose=morph.getClass().getField("pose").get(morph);
            Object transforms=pose.getClass().getField("bones").get(pose);
            Object t=((Map<?, ?>)transforms).get(bone);
            if(t==null) return new float[] {0F,0F,0F};
            return new float[] {
                    t.getClass().getField("rotateX").getFloat(t),
                    t.getClass().getField("rotateY").getFloat(t),
                    t.getClass().getField("rotateZ").getFloat(t)};
        }
        catch(Throwable error) {return null;}
    }

    @SuppressWarnings("unchecked")
    private static Float readChameleonPoseRotationX(Object morph, String bone)
    {
        try
        {
            Object pose = morph.getClass().getField("pose").get(morph);
            if (pose == null) return null;
            Object bones = pose.getClass().getField("bones").get(pose);
            if (!(bones instanceof Map)) return null;
            Object transform = ((Map<?, ?>) bones).get(bone);
            if (transform == null) return Float.valueOf(0F);
            return Float.valueOf(transform.getClass().getField("rotateX").getFloat(transform));
        }
        catch (Throwable ignored) { return null; }
    }

    private static boolean chameleonTransitionActive(Object morph)
    {
        try
        {
            Object animation = morph.getClass().getField("animation").get(morph);
            return ((Boolean) animation.getClass().getMethod("isInProgress")
                    .invoke(animation)).booleanValue();
        }
        catch (Throwable ignored) { return false; }
    }

    private String lastVisualHairDiagnostic = "";

    public float[] getPhysicsAttachmentMatrix(BodyPartModelData data)
    {
        float[] matrix = physicsAttachmentMatrices.get(data);
        return matrix == null ? null : BodyPartPhysicsMatrixSpace.snapshot(matrix);
    }

    private void capturePhysicsAttachmentMatrices(AbstractMorph parent,
            EntityActor actor, int frame, Map<BodyPartModelData, BodyPart> parts)
    {
        if (recordingPhysicsMatrices || parts.isEmpty() || parent == null || actor == null)
            return;
        recordingPhysicsMatrices = true;
        try
        {
            // Uses Metamorph's own bone attachment transform, including
            // any animated parent anchor; never changes morph keyframes.
            BodyPart.recordMatrix(parent, actor, 0F);
            for (Map.Entry<BodyPartModelData, BodyPart> entry : parts.entrySet())
            {
                javax.vecmath.Matrix4f matrix = entry.getValue().lastMatrix;
                if (matrix == null)
                {
                    reportPhysicsMatrix(entry.getKey(), "native attachment matrix missing");
                    continue;
                }
                float[] nativeMatrix = new float[] {
                    matrix.m00, matrix.m10, matrix.m20, matrix.m30,
                    matrix.m01, matrix.m11, matrix.m21, matrix.m31,
                    matrix.m02, matrix.m12, matrix.m22, matrix.m32,
                    matrix.m03, matrix.m13, matrix.m23, matrix.m33
                };
                physicsAttachmentMatrices.put(entry.getKey(), nativeMatrix);
                reportPhysicsMatrix(entry.getKey(),
                        "native parent attachment matrix captured ("
                        + entry.getValue().limb + ")");
                probePhysicsBoneMatrices(entry.getKey(), entry.getValue(), nativeMatrix, null, frame);
            }
        }
        catch (Throwable error)
        {
            // Native Metamorph versions may differ; keep normal Preview alive.
            for (BodyPartModelData data : parts.keySet())
                reportPhysicsMatrix(data, "native capture unavailable: "
                        + error.getClass().getSimpleName());
        }
        finally
        {
            recordingPhysicsMatrices = false;
        }
    }

    private void probePhysicsBoneMatrices(BodyPartModelData data, BodyPart part,
            float[] parentMatrix, float[] torsoMatrix, int frame)
    {
        List<BodyPartPhysicsChains.Chain> chains = getPreviewPhysicsChains(data);
        if (chains.isEmpty() || part == null || part.morph == null)
            return;
        BodyPartPhysicsWorldPose.MatrixSource local =
                BodyPartChameleonMatrixProbe.source(part.morph.get());
        if (local == null)
        {
            reportPhysicsBoneProbe(data, "native Chameleon matrix provider unavailable");
            return;
        }

        /* Temporarily remove PREVIEW deltas from the native current
         * transforms. The normal rendered pose is restored in finally. */
        BodyPartChameleonCleanPose cleanScope;
        try
        {
            cleanScope = BodyPartChameleonCleanPose.begin(
                    part.morph.get(), previewEulerDeltas.get(data));
        }
        catch (RuntimeException error)
        {
            reportPhysicsBoneProbe(data, "clean physics pose unavailable: "
                    + error.getClass().getSimpleName());
            return;
        }
        /* Metamorph records parent limb BEFORE its BodyPart transform. */
        try
        {
            float[] attached = worldFromRenderedChameleonChild(parentMatrix,part);
            float[] actorWorld=actorFrameFor(frame);
            if(actorWorld!=null)
                attached=BodyPartJoltActorSpace.matrix(actorWorld,attached);
            BodyPartPhysicsWorldPose.MatrixSource world =
                    BodyPartPhysicsWorldPose.worldSource(attached, local);
            boolean advanced = false;
            boolean initializedJolt = false;
            for (BodyPartPhysicsChains.Chain chain : chains)
            {
                AnimationBone finalBone = chain.bones.get(chain.bones.size()-1);
                // A chain is identified by configured bone hierarchy, not the
                // author of the original hairstyle. Its terminal pivot comes
                // from the actual decoded Chameleon cube geometry below.
                BodyPartChainSolver.Vec3[] joints =
                        new BodyPartChainSolver.Vec3[chain.bones.size()+1];
                BodyPartChainSolver.Vec3 last = null;
                int jointIndex = 0;
                for (AnimationBone bone : chain.bones)
                {
                    float[] matrix = world.getBoneWorldMatrix(bone.getName());
                    if (matrix == null)
                    {
                        reportPhysicsBoneProbe(data,
                                "native Chameleon bone matrix missing: " + bone.getName());
                        return;
                    }
                    BodyPartChainSolver.Vec3 current =
                            BodyPartPhysicsPoseAdapter.position(matrix);
                    if (last != null && current.sub(last).length() < 0.00001F)
                    {
                        reportPhysicsBoneProbe(data,
                                "zero-length world strand segment: " + bone.getName());
                        return;
                    }
                    joints[jointIndex++] = current;
                    last = current;
                }
                // Read Chameleon's decoded, already rotated cube geometry.
                // This supports arbitrary Blockbench models, including head
                // hair and renamed strand bones. No hardcoded hair.geo values
                // or source-JSON sign assumptions influence native Jolt.
                float[] terminalMatrix=world.getBoneWorldMatrix(finalBone.getName());
                BodyPartChameleonRenderedCubeProbe.Result tipGeometry =
                        BodyPartChameleonRenderedCubeProbe.inspect(
                                part.morph.get(), finalBone.getName(), terminalMatrix);
                boolean hasTip=false;
                if(tipGeometry!=null && tipGeometry.measurement!=null)
                {
                    float[] xyz=tipGeometry.measurement.cubeTipWorld;
                    BodyPartChainSolver.Vec3 tip=
                            new BodyPartChainSolver.Vec3(xyz[0],xyz[1],xyz[2]);
                    float segmentLength=tip.sub(last).length();
                    if(Float.isFinite(segmentLength) &&
                            segmentLength>=0.05F && segmentLength<=1.0F)
                    {
                        joints[jointIndex]=tip;
                        hasTip=true;
                    }
                }
                if(!hasTip)
                {
                    reportPhysicsBoneProbe(data,
                            "No valid Chameleon rendered-cube tip for "
                            +finalBone.getName()+" (requires a terminal cube)");
                    continue;
                }
                // A one-bone hairstyle has only one physical link, not a
                // full head-shaped collider. Record its actual attachment
                // pivot, tip and decoded geometry bounds before changing
                // the orientation algorithm. Log at most once per timeline
                // frame to avoid thousands of duplicate render messages.
                if(chain.bones.size()==1 && tipGeometry!=null)
                {
                    String key=data.getTimelineId()+"/"+finalBone.getName();
                    Integer previousFrame=lastSingleBoneGeometryFrame.get(key);
                    if(previousFrame==null || previousFrame.intValue()!=frame)
                    {
                        lastSingleBoneGeometryFrame.put(key,Integer.valueOf(frame));
                        System.out.println("[BBS Animation Editor][Jolt][OneBoneGeometry] "+
                                key+" frame="+frame+
                                " pivot=["+last.x+","+last.y+","+last.z+"]"+
                                " tip=["+joints[jointIndex].x+","+
                                    joints[jointIndex].y+","+
                                    joints[jointIndex].z+"]"+
                                " cubeWorldY=["+
                                    tipGeometry.measurement.minWorldY+","+
                                    tipGeometry.measurement.maxWorldY+"]"+
                                " cubeCount="+tipGeometry.cubeCount);
                    }
                }
                if(JOLT_CONTACT_PROBE && frame%5==0)
                {
                    String sourceId=data.getTimelineId()+"/"+chain.root.getName();
                    Integer previous=lastContactProbeFrame.get(sourceId);
                    if(previous==null || previous.intValue()!=frame)
                    {
                        lastContactProbeFrame.put(sourceId,Integer.valueOf(frame));
                        float[][] contacts=passiveCollisionBoxes(data,frame);
                        if(contacts!=null && contacts.length>0)
                        {
                            float[] point={joints[0].x,joints[0].y,joints[0].z};
                            float[] center={
                                    (joints[0].x+joints[1].x)*0.5F,
                                    (joints[0].y+joints[1].y)*0.5F,
                                    (joints[0].z+joints[1].z)*0.5F};
                            float[] tail={joints[jointIndex].x,
                                    joints[jointIndex].y,joints[jointIndex].z};
                            BodyPartJoltContactProbe.Nearest rootGap=
                                    BodyPartJoltContactProbe.nearest(point,contacts);
                            BodyPartJoltContactProbe.Nearest linkGap=
                                    BodyPartJoltContactProbe.nearest(center,contacts);
                            BodyPartJoltContactProbe.Nearest tailGap=
                                    BodyPartJoltContactProbe.nearest(tail,contacts);
                            java.util.List<String> owners=passiveCollisionSources(frame);
                            String closest=linkGap.index<0 ||
                                    linkGap.index>=owners.size()?"unknown":
                                    owners.get(linkGap.index);
                            System.out.println(
                                    "[BBS Animation Editor][Jolt][ContactProbe] "+
                                    "frame="+frame+" strand="+sourceId+
                                    " contactSource="+closest+
                                    " boxes="+contacts.length+
                                    " rootClearance="+rootGap.clearance+
                                    " firstLinkClearance="+linkGap.clearance+
                                    " terminalClearance="+tailGap.clearance+
                                    " (negative=inside OBB; NOT a contact impulse)");
                        }
                    }
                }
                /*
                 * Optional real Jolt physical chain: drive it with the CLEAN
                 * native-world Chameleon pose and convert its quaternion
                 * rotations to preview-only bone swings. This branch bypasses
                 * the experimental Verlet/capsule/mesh solver completely.
                 * Keep the feature off until it is tested in Forge 1.12.2.
                 */
                if (BodyPartJoltPreviewAdapter.isRequested() && hasTip)
                {
                    AnimationBone parent = chain.root.getParent();
                    float[] parentWorld = parent == null ? attached :
                            world.getBoneWorldMatrix(parent.getName());
                    if (parentWorld != null)
                    {
                        int boneCount = chain.bones.size();
                        String[] names = new String[boneCount];
                        float[][] nativeWorld = new float[boneCount][];
                        boolean complete = true;
                        for (int k = 0; k < boneCount; k++)
                        {
                            names[k] = chain.bones.get(k).getName();
                            nativeWorld[k] = world.getBoneWorldMatrix(names[k]);
                            if (nativeWorld[k] == null)
                            {
                                complete = false;
                                break;
                            }
                        }
                        if (complete)
                        {
                            BodyPartChainSolver.Vec3 tip = joints[joints.length - 1];
                            float[] terminal = {tip.x, tip.y, tip.z};
                            String stateId = data.getTimelineId() + "/" +
                                    chain.root.getName();
                            Integer contactFrame=joltTorsoFitFrames.get(data);
                            BodyPartJoltTorsoFit.Fit contact =
                                    contactFrame!=null &&
                                    contactFrame.intValue()==frame
                                    ? joltTorsoFits.get(data):null;
                            BodyPartJoltMultiTorsoFit.Segments segments =
                                    contactFrame!=null &&
                                    contactFrame.intValue()==frame
                                    ? joltMultiTorsoFits.get(data):null;
                            // Per-bone Emoticons colliders supersede legacy torso
                            // capsules. Do not stack both collision systems.
                            // Otherwise two overlapping approximation systems
                            // compete and can squeeze hair sideways.
                            if(frame==parentEmoticonsColliderFrame &&
                                    parentEmoticonsColliders!=null &&
                                    parentEmoticonsColliders.isReady())
                            {
                                contact=null;
                                segments=null;
                            }
                            float[] colliderA=contact==null?null:contact.a;
                            float[] colliderB=contact==null?null:contact.b;
                            BodyPartJoltMultiTorsoFit.Segments nativeSegments=segments;
                            if(actorWorld!=null)
                            {
                                if(colliderA!=null)
                                    colliderA=BodyPartJoltActorSpace.point(
                                            actorWorld,colliderA);
                                if(colliderB!=null)
                                    colliderB=BodyPartJoltActorSpace.point(
                                            actorWorld,colliderB);
                                nativeSegments=BodyPartJoltMultiTorsoFit.world(
                                        segments,actorWorld);
                            }
                            Map<String, float[]> physical = joltPreview.sample(
                                    net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                                    stateId, names, frame, nativeWorld, terminal,
                                    parentWorld, chain.settings.getStiffness(),
                                    chain.settings.getDamping(),
                                    chain.settings.getGravity(),
                                    !chain.settings.hasBodyCollisions() || contact==null
                                            ?null:colliderA,
                                    !chain.settings.hasBodyCollisions() || contact==null
                                            ?null:colliderB,
                                    !chain.settings.hasBodyCollisions() || contact==null
                                            ?0F:contact.radius,
                                    chain.settings.hasBodyCollisions()?nativeSegments:null,
                                    chain.settings.hasBodyCollisions()
                                            ?passiveCollisionBoxes(data,frame):null,
                                    chain.settings.hasBodyCollisions()
                                            ?passiveCollisionShapes(frame):null);
                            if (physical != null)
                            {
                                // Compare REAL physical links from the native
                                // solver with INSTALLED passive box shapes.
                                // Earlier ContactProbe measured clean animated
                                // (pre-Jolt) pivots and could not distinguish
                                // a collision filtering failure from a valid
                                // Jolt contact with an undersized body proxy.
                                if(JOLT_CONTACT_PROBE && frame%5==0)
                                {
                                    float[][] nativeLinks=
                                            joltPreview.hairSegmentsFor(stateId,frame);
                                    float[][] nativeBoxes=
                                            joltPreview.additionalBoxesFor(stateId,frame);
                                    if(nativeLinks!=null && nativeBoxes!=null &&
                                            nativeBoxes.length>0)
                                    {
                                        java.util.List<String> sources=
                                                passiveCollisionSources(frame);
                                        for(int linkIndex=0;
                                                linkIndex<nativeLinks.length;
                                                linkIndex++)
                                        {
                                            BodyPartJoltContactProbe.Nearest nearest=
                                                    BodyPartJoltContactProbe.nearestSolvedLink(
                                                            nativeLinks[linkIndex],nativeBoxes);
                                            String source=nearest.index>=0 &&
                                                    nearest.index<sources.size()
                                                    ?sources.get(nearest.index):
                                                    "unknown";
                                            System.out.println(
                                                    "[BBS Animation Editor][Jolt][NativeContactProbe] "+
                                                    "frame="+frame+" strand="+stateId+
                                                    " physicalLink="+linkIndex+
                                                    " nearestModel="+source+
                                                    " nearestBox="+nearest.index+
                                                    " approximateClearance="+
                                                            nearest.clearance+
                                                    " (negative=sampled capsule overlaps OBB; "+
                                                    "real Jolt pose, not authored bone)");
                                        }
                                    }
                                }
                                if (JOLT_DIRECTION_DIAGNOSTIC)
                                {
                                    try
                                    {
                                        Map<String, float[]> predicted =
                                                BodyPartJoltPoseDiagnostic.expectedDirections(
                                                        names, nativeWorld, terminal, physical);
                                        Integer previousFrame = joltSampleFrames.get(data);
                                        if (previousFrame == null || previousFrame.intValue() != frame)
                                        {
                                            joltSampleDirections.remove(data);
                                            joltSampleNativeDirections.remove(data);
                                            joltSampleParentMatrices.remove(data);
                                            joltSampleFrames.put(data, Integer.valueOf(frame));
                                        }
                                        Map<String, float[]> stored = joltSampleDirections.get(data);
                                        if (stored == null)
                                        {
                                            stored = new java.util.LinkedHashMap<String, float[]>();
                                            joltSampleDirections.put(data, stored);
                                        }
                                        stored.putAll(predicted);
                                        // Save exactly the attachment frame
                                        // supplied to the rigid-body solver.
                                        joltSampleParentMatrices.put(data,
                                                parentMatrix.clone());
                                        Map<String, float[]> raw =
                                                joltPreview.rawDirectionsFor(stateId, frame);
                                        if (!raw.isEmpty())
                                        {
                                            Map<String, float[]> direct =
                                                    joltSampleNativeDirections.get(data);
                                            if (direct == null)
                                            {
                                                direct = new java.util.LinkedHashMap<String, float[]>();
                                                joltSampleNativeDirections.put(data, direct);
                                            }
                                            direct.putAll(raw);
                                        }
                                    }
                                    catch (RuntimeException error)
                                    {
                                        System.err.println("[BBS Animation Editor][Jolt][Compare] "
                                                + "Cannot predict direction for " + stateId
                                                + ": " + error.getMessage());
                                    }
                                }
                                initializedJolt |= joltPreview.wasRecreatedThisSample();
                                if (reportedJoltPreview.add(stateId))
                                    System.out.println("[BBS Animation Editor][Jolt] "
                                            + "Native quaternion preview active: " + stateId
                                            + " (childRenderSpace="
                                            + (JOLT_EXACT_CHILD_SPACE ? "scale+Y180" : "legacyNoChildYaw")
                                            + ", requestedBodyCollisions="+chain.settings.hasBodyCollisions()
                                            + ", nativeContacts="+BodyPartJoltPreviewAdapter.torsoContactsRequested()
                                            + ", export not enabled)");
                                for (Map.Entry<String, float[]> swing : physical.entrySet())
                                {
                                    physicsFrames.accept(data, frame,
                                            swing.getKey(), swing.getValue(), true);
                                    advanced = true;
                                }
                                // Never combine two competing solvers for one hair strand.
                                continue;
                            }
                        }
                    }
                }
                // Jolt is the only physics solver for preview hair.
                // Missing JNI, invalid matrices, or a rejected native sample
                // must not silently run the former Verlet/mesh fallback.
                if (BodyPartJoltPreviewAdapter.isRequested())
                    reportPhysicsBoneProbe(data,
                            "Jolt sample unavailable for " + chain.root.getName()
                            + " (legacy solver disabled)");

            }
            // Physics writes happen AFTER normal rendering. On a paused
            // frame, an actual bone edit may publish the completed result
            // for the NEXT draw without waiting for the timeline to advance.
            // A paused or seeked Jolt scene uses the actual completed sample
            // at that frame on its next draw. Previously the editor showed
            // the preceding frame's physical swing after a manual tick and
            // only published once upon scene initialization, exaggerating
            // consecutive-frame jitter. Playback still freezes the sample
            // for the entire render frame; no mid-draw reentrant stepping.
            if (initializedJolt ||
                    (advanced && !BlockbusterPreviewAnimationState.isPlaying()))
                physicsFrames.publishEditedFrame(data,frame);
            reportPhysicsBoneProbe(data, advanced
                    ? "Jolt native preview advanced; multi-segment visual pose queued"
                    : "Jolt preview bone matrices valid: " + chains.size()
                      + " chains (no visible physics delta yet)");
        }
        catch (RuntimeException error)
        {
            reportPhysicsBoneProbe(data, "world strand matrices invalid: "
                    + error.getClass().getSimpleName());
        }
        finally
        {
            cleanScope.close();
        }
    }

    private final Map<BodyPartModelData, String> physicsBoneProbeDiagnostics =
            new IdentityHashMap<BodyPartModelData, String>();

    private void reportPhysicsBoneProbe(BodyPartModelData data, String message)
    {
        if (!message.equals(physicsBoneProbeDiagnostics.get(data)))
        {
            physicsBoneProbeDiagnostics.put(data, message);
            System.out.println("[BBS Animation Editor][Physics] " + message);
        }
    }

    private void reportPhysicsMatrix(BodyPartModelData data, String message)
    {
        if (!message.equals(physicsMatrixDiagnostics.get(data)))
        {
            physicsMatrixDiagnostics.put(data, message);
            System.out.println("[BBS Animation Editor][Physics] " + message);
        }
    }

    public List<BodyPartPhysicsChains.Chain> getPreviewPhysicsChains(BodyPartModelData data)
    {
        List<BodyPartPhysicsChains.Chain> chains = previewPhysicsChains.get(data);
        return chains == null
                ? java.util.Collections.<BodyPartPhysicsChains.Chain>emptyList()
                : chains;
    }

    private void prepareChameleonPhysicsChains(BodyPartModelData data, AbstractMorph morph)
    {
        if (data == null || morph == null ||
                !morph.getClass().getName().endsWith(".ChameleonMorph"))
            return;

        try
        {
            List<BodyPartPhysicsChains.Chain> chains = BodyPartPhysicsChains.collect(data);
            previewPhysicsChains.put(data, chains);

            StringBuilder signature = new StringBuilder();
            for (BodyPartPhysicsChains.Chain chain : chains)
            {
                if (signature.length() > 0) signature.append(", ");
                signature.append(chain.root.getName()).append("(")
                        .append(chain.bones.size()).append(")");
            }
            String value = signature.toString();
            if (!value.equals(reportedPhysicsChains.get(data)))
            {
                reportedPhysicsChains.put(data, value);
                if (!value.isEmpty())
                    System.out.println("[BBS Animation Editor][Physics] Chameleon preview chains: " + value
                            + "; awaiting attachment world matrices");
            }
        }
        catch (IllegalStateException error)
        {
            previewPhysicsChains.remove(data);
            String value = "invalid: " + error.getMessage();
            if (!value.equals(reportedPhysicsChains.get(data)))
            {
                reportedPhysicsChains.put(data, value);
                System.err.println("[BBS Animation Editor][Physics] " + value);
            }
        }
    }

    public AbstractMorph getPreparedMorph(BodyPartModelData data)
    {
        return data == null ? null : this.preparedMorphs.get(data);
    }
    /**
     * Rebuild the parent morph's real Metamorph body-part list for the
     * current editor frame.
     *
     * This is called BEFORE RenderCustomActor renders the actor, so its
     * original LayerBodyPart sees the generated BodyPart objects.
     */
    public void prepare(
            BodyPartsEditorController controller,
            EntityActor actor,
            int frame)
    {
        prepareModels(actor, frame, controller == null ? null : controller.getModels());
    }

    /**
     * Prepare actor-specific attachments without switching the active
     * editor selection or altering its controller.
     */
    public void prepareModels(
            EntityActor actor,
            int frame,
            List<BodyPartModelData> models)
    {
        if (actor == null)
        {
            return;
        }

        AbstractMorph abstractMorph =
                actor.getMorph();

        /*
         * Both Blockbuster CustomMorph and Emoticons AnimatedMorph
         * expose the same Metamorph IBodyPartProvider contract.
         *
         * Previously this renderer accepted only CustomMorph.  That
         * accidentally made Body Parts a no-op for AnimatedMorph:
         * prepare() returned before even creating the BodyPart objects.
         *
         * This is the exact abstraction used by Metamorph itself.
         */
        if (!(abstractMorph instanceof IBodyPartProvider))
        {
            return;
        }

        BodyPartManager manager =
                ((IBodyPartProvider) abstractMorph)
                        .getBodyPart();

        if (manager == null)
        {
            return;
        }

        /*
         * The editor is the source of truth for the current frame.
         * Do not leave body parts from a previous frame alive.
         */
        physicsFrames.beginFrame(frame);
        manager.parts.clear();
        this.preparedMorphs.clear();
        this.previewEulerDeltas.clear();
        this.joltAppliedDirections.clear();
        this.joltAppliedFrames.clear();
        this.joltAppliedNativeDirections.clear();
        this.joltAppliedParentMatrices.clear();
        this.previewPhysicsChains.clear();
        this.physicsAttachmentMatrices.clear();
        this.renderedPhysicsParts.clear();
        this.renderedCollisionParts.clear();
        this.collisionPartOrder.clear();
        this.attachedCollisionBoxes.clear();
        this.attachedCollisionShapes.clear();
        this.attachedCollisionFrame=-1;
        this.parentEmoticonsColliders=null;
        this.parentEmoticonsColliderFrame=-1;

        // Do not probe/log last-rendered matrices here: prepareModels()
        // runs on every draw BEFORE the actor's current render and therefore
        // the passive sample is stale and was spamming the log.
        if (models == null || models.isEmpty())
        {
            return;
        }

        this.preparingPreview = true;
        Map<BodyPartModelData, BodyPart> nativePhysicsParts =
                new IdentityHashMap<BodyPartModelData, BodyPart>();
        try
        {
        for (BodyPartModelData data : models)
        {
            if (data == null ||
                    !data.hasModel() ||
                    frame < data.getStartFrame() ||
                    frame > data.getEndFrame())
            {
                continue;
            }

            BodyPart part =
                    createBodyPart(
                            data,
                            frame
                    );

            if (part != null)
            {
                manager.parts.add(part);
                this.renderedCollisionParts.put(data,part);
                this.collisionPartOrder.add(data);
                if (!getPreviewPhysicsChains(data).isEmpty())
                {
                    nativePhysicsParts.put(data, part);
                    renderedPhysicsParts.put(data, part);
                }
            }
        }
        }
        finally
        {
            this.preparingPreview = false;
        }
        /*
         * Disabled: BodyPart.recordMatrix() performs a second full parent
         * render while previewing the actor. It is not an invisible matrix
         * query; the extra render can duplicate the actor in the viewport.
         * Restore normal preview first. Physics visual writes are already
         * disabled until a safe attachment sampling hook is available.
         */
        // if (!nativePhysicsParts.isEmpty())
        //     capturePhysicsAttachmentMatrices(abstractMorph, actor, frame, nativePhysicsParts);
    }

    /**
     * Export-only snapshot of the same genuine Metamorph BodyParts used by
     * Preview. Never changes the source actor or the editor model tracks.
     * A fresh renderer instance should be used by the exporter.
     */
    public net.minecraft.nbt.NBTTagList exportParts(
            BodyPartsEditorController controller, int frame)
            throws java.io.IOException
    {
        net.minecraft.nbt.NBTTagList result =
                new net.minecraft.nbt.NBTTagList();
        if (controller == null)
        {
            return result;
        }

        this.preparedMorphs.clear();
        for (BodyPartModelData data : controller.getModels())
        {
            if (data == null || !data.hasModel()
                    || frame < data.getStartFrame()
                    || frame > data.getEndFrame())
            {
                continue;
            }

            BodyPart part = createBodyPart(data, frame);
            if (part == null)
            {
                throw new java.io.IOException(
                        "Unable to export attached BodyPart '"
                        + data.getModelNameAt(frame) + "' on bone '"
                        + data.getAttachmentBoneName() + "' at tick " + frame);
            }

            /*
             * Baked Body Parts contain a new internal-bone pose on every
             * Record tick. As with a parent CustomMorph, the child must
             * interpolate between successive snapshots over ONE tick.
             *
             * ChameleonMorph is an optional dependency, so identify it by
             * name and use Metamorph's common IAnimationProvider API.
             * Do not alter transitions of other morph types (notably
             * Emoticons), whose timing may be intentionally different.
             */
            AbstractMorph child = part.morph == null
                    ? null : part.morph.get();
            boolean isChameleon = child != null
                    && "mchorse.chameleon.metamorph.ChameleonMorph"
                            .equals(child.getClass().getName());
            if ((child instanceof CustomMorph || isChameleon)
                    && child instanceof mchorse.metamorph.api.morphs.utils.IAnimationProvider)
            {
                mchorse.metamorph.api.morphs.utils.Animation animation =
                        ((mchorse.metamorph.api.morphs.utils.IAnimationProvider)
                                child).getAnimation();
                if (animation != null)
                {
                    animation.duration = 1;
                    animation.animates = true;
                    animation.ignored = false;
                    animation.interp =
                            mchorse.mclib.utils.Interpolation.LINEAR;
                }
            }

            net.minecraft.nbt.NBTTagCompound tag =
                    new net.minecraft.nbt.NBTTagCompound();
            part.toNBT(tag);
            result.appendTag(tag);
        }

        return result;
    }

    /**
     * Create one genuine Metamorph BodyPart.
     */
    private BodyPart createBodyPart(
            BodyPartModelData data,
            int frame)
    {
        if (data == null)
        {
            return null;
        }

        /* Chameleon and other Metamorph add-ons are stored as real morphs. */
        if (data.hasMorphModel())
        {
            AbstractMorph morph = null;
            CharacterKey stateKey = data.getCharacterStateKeyAt(frame);

            if (stateKey != null && stateKey.hasSkin())
            {
                try
                {
                    morph = MorphManager.INSTANCE.morphFromNBT(stateKey.getSkin());
                }
                catch (Throwable error)
                {
                    error.printStackTrace();
                }
            }

            if (morph == null)
            {
                morph = data.getMorphAt(frame);
            }

            if (morph != null)
            {
                morph = ChameleonBodyPartAnimations.apply(data, frame, morph);
                // Preview-only registration; exportParts uses a different
                // renderer instance and never calls prepareModels().
                // No keys or morph rotations are modified here.
                if (this.preparingPreview)
                    prepareChameleonPhysicsChains(data, morph);
                return createMorphBodyPart(data, morph, frame);
            }
        }

        String modelName =
                data.getModelNameAt(frame);

        BlockbusterModelAccess modelAccess =
                data.getModelAccess();

        if (!modelName.equals(data.getModelName()))
        {
            modelAccess = new BlockbusterModelAccess(null);

            if (!modelAccess.loadModelByName(modelName))
            {
                return null;
            }
        }

        if (modelAccess == null)
        {
            return null;
        }

        if (modelName == null ||
                modelName.length() == 0)
        {
            return null;
        }

        Object apiModelObject =
                modelAccess.getApiModel();

        if (!(apiModelObject instanceof Model))
        {
            /*
             * The editor deliberately refuses to cast ModelCustom to
             * Model.  They are different classes in Blockbuster 1.12.2.
             *
             * If the render-side model has no API-model reference, try
             * resolving it again through BlockbusterModelAccess.
             */
            BlockbusterModelAccess access =
                    modelAccess;

            if (!access.loadModelByName(modelName))
            {
                return null;
            }

            apiModelObject =
                    access.getApiModel();
        }

        if (!(apiModelObject instanceof Model))
        {
            return null;
        }

        Model apiModel =
                (Model) apiModelObject;

        CustomMorph child =
                createChildMorph(
                        modelName,
                        apiModel,
                        data,
                        frame
                );

        if (child == null)
        {
            return null;
        }

        BodyPart part =
                new BodyPart();

        part.limb =
                data.getAttachmentBoneName();

        if (part.limb == null ||
                part.limb.length() == 0)
        {
            return null;
        }

        /*
         * BodyPart transforms are expressed in Minecraft world/model
         * units.  The editor's global translation is expressed in model
         * pixels, hence the same /16 conversion used by Blockbuster's
         * renderer.
         */
        AnimationTransform global =
                data.getGlobalTransform();

        if (global != null)
        {
            part.translate.set(
                    global.getPositionX() / 16.0F,
                    global.getPositionY() / 16.0F,
                    global.getPositionZ() / 16.0F
            );

            part.rotate.set(
                    global.getRotationX(),
                    global.getRotationY(),
                    global.getRotationZ()
            );

            part.scale.set(
                    global.getScaleX(),
                    global.getScaleY(),
                    global.getScaleZ()
            );
        }

        /*
         * The attached morph must use the real actor entity.  Metamorph's
         * default is false (DummyEntity), which is useful for the morph GUI
         * but is wrong for our live actor preview.
         */
        part.useTarget = true;
        part.enabled = true;
        part.animate = false;

        part.morph.setDirect(child);
        this.preparedMorphs.put(data, child);

        return part;
    }

    /** Build a BodyPart around a non-Blockbuster Metamorph (notably Chameleon). */
    private BodyPart createMorphBodyPart(BodyPartModelData data, AbstractMorph child, int frame)
    {
        BodyPart part = new BodyPart();
        part.limb = data.getAttachmentBoneName();

        if (part.limb == null || part.limb.length() == 0)
        {
            return null;
        }

        AnimationTransform global = data.getGlobalTransform();
        if (global != null)
        {
            part.translate.set(global.getPositionX() / 16.0F,
                    global.getPositionY() / 16.0F,
                    global.getPositionZ() / 16.0F);
            part.rotate.set(global.getRotationX(), global.getRotationY(), global.getRotationZ());
            part.scale.set(global.getScaleX(), global.getScaleY(), global.getScaleZ());
        }

        /*
         * CustomMorphs selected from the Metamorph browser enter this
         * morph-backed path, not createChildMorph(). Without applying the
         * editor pose here, internal keyframe values change in the UI but
         * the actual attached model keeps its original pose.
         */
        if (child instanceof CustomMorph)
        {
            applyInternalBonePose((CustomMorph) child, data, frame);
        }
        else if (isEmoticonsAnimatedMorph(child))
        {
            applyEmoticonsInternalBonePose(child, data, frame);
        }
        else
        {
            applyChameleonInternalBonePose(child, data, frame);
        }

        // Visual probe uses the completed physics sample from the immediately
        // preceding frame. A seek or pause never applies stale swings.
        if (this.preparingPreview && !isEmoticonsAnimatedMorph(child)
                && !(child instanceof CustomMorph))
        {
            Float cleanRotation = readChameleonPoseRotationX(child, "left_strand_1");
            if (cleanRotation != null)
                leftRootBaseRotation.put(data, cleanRotation);
            /*
             * Controlled post-A/B test: use the actual physics swing again.
             * The manual X=+45-degree test visibly lowered this Y=180-degree
             * attachment, while the calculated negative X lifted it.
             * Reverse the test swing only on the left root, pending a full
             * handedness/basis correction across all chains.
             */
            Map<String, float[]> swings =
                    !bakeController.isRecording() &&
                    JOLT_VISUAL_INTERPOLATION &&
                    BodyPartJoltPreviewAdapter.isRequested() &&
                    BlockbusterPreviewAnimationState.isPlaying()
                        ? physicsFrames.forRenderInterpolated(data,frame,
                                net.minecraft.client.Minecraft.getMinecraft()
                                        .getRenderPartialTicks())
                        : physicsFrames.forRender(data,frame);
            if (swings != null)
            {
                int appliedCount = 0;
                for (Map.Entry<String, float[]> entry : swings.entrySet())
                {
                    float[] swing = entry.getValue();
                    // The Jolt mapper returns a local-space swing.
                    // Do not apply historical Verlet sign inversions.
                    float[] corrected = new float[] {
                            swing[0], swing[1], swing[2],
                            swing[3]
                    };
                    float[] before = readChameleonPoseEuler(child, entry.getKey());
                    float angleLimit = physicsFrames.isJoltForRender(
                            data, frame, entry.getKey()) ? (float)Math.PI : 1.20F;
                    if (BodyPartChameleonPhysicsPose.addLocalSwing(
                            child, entry.getKey(), corrected, angleLimit))
                    {
                        if (JOLT_DIRECTION_DIAGNOSTIC &&
                                physicsFrames.isJoltForRender(data, frame, entry.getKey()))
                        {
                            Integer predictedFrame = joltSampleFrames.get(data);
                            Map<String, float[]> predictions = joltSampleDirections.get(data);
                            if (predictedFrame != null && predictedFrame.intValue() == frame &&
                                    predictions != null && predictions.containsKey(entry.getKey()))
                            {
                                Map<String, float[]> applied = joltAppliedDirections.get(data);
                                if (applied == null)
                                {
                                    applied = new java.util.LinkedHashMap<String, float[]>();
                                    joltAppliedDirections.put(data, applied);
                                }
                                applied.put(entry.getKey(), predictions.get(entry.getKey()).clone());
                                joltAppliedFrames.put(data, Integer.valueOf(frame));
                                float[] parentSample = joltSampleParentMatrices.get(data);
                                if (parentSample != null)
                                    joltAppliedParentMatrices.put(data, parentSample.clone());
                                Map<String, float[]> direct = joltSampleNativeDirections.get(data);
                                if (direct != null && direct.containsKey(entry.getKey()))
                                {
                                    Map<String, float[]> used = joltAppliedNativeDirections.get(data);
                                    if (used == null)
                                    {
                                        used = new java.util.LinkedHashMap<String, float[]>();
                                        joltAppliedNativeDirections.put(data, used);
                                    }
                                    used.put(entry.getKey(), direct.get(entry.getKey()).clone());
                                }
                            }
                        }
                        appliedCount++;
                        float[] after = readChameleonPoseEuler(child, entry.getKey());
                        if (bakeController.isRecording() &&
                                physicsFrames.isJoltForRender(data,frame,entry.getKey()))
                            bakeController.capture(data,frame,entry.getKey(),before,after);
                        if (before != null && after != null)
                        {
                            Map<String, float[]> offsets = previewEulerDeltas.get(data);
                            if (offsets == null)
                            {
                                offsets = new java.util.HashMap<String, float[]>();
                                previewEulerDeltas.put(data, offsets);
                            }
                            offsets.put(entry.getKey(), new float[] {
                                    after[0] - before[0], after[1] - before[1],
                                    after[2] - before[2]});
                        }
                        if ("left_strand_1".equals(entry.getKey()))
                        {
                            Float rotation = readChameleonPoseRotationX(
                                    child, "left_strand_1");
                            if (rotation != null)
                                leftRootAppliedRotation.put(data, rotation);
                        }
                    }
                }
                if (appliedCount > 0 && !visualProbeReported.containsKey(data))
                {
                    visualProbeReported.put(data, Boolean.TRUE);
                    System.out.println("[BBS Animation Editor][Physics] "
                            + "multi-segment strand preview enabled: "
                            + appliedCount + " bone rotations (terminal tips pending)");
                }
            }

        }

        part.useTarget = true;
        part.enabled = true;
        /* Chameleon animations must keep ticking while the actor preview plays. */
        part.animate = true;
        part.morph.setDirect(child);
        this.preparedMorphs.put(data, child);
        return part;
    }

    /**
     * Detect Emoticons' AnimatedMorph without making Emoticons a hard
     * runtime requirement for users who only install Blockbuster.
     */
    private boolean isEmoticonsAnimatedMorph(AbstractMorph child)
    {
        if (child == null)
        {
            return false;
        }

        try
        {
            return Class.forName(
                    "mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph"
            ).isInstance(child);
        }
        catch (ClassNotFoundException ignored)
        {
            return false;
        }
    }

    /**
     * Apply internal-bone keys to Emoticons' AnimatedMorph pose.
     *
     * Emoticons does not read ModelBone fields for this morph. Its renderer
     * reads AnimatedMorph.pose (or the result of PoseAnimation.calculatePose)
     * and applies AnimatorPoseTransform values to the BOBJ armature. Therefore
     * editing only the editor-side AnimationBone never changes the rendered
     * model; update a cloned pose on the morph that will actually be rendered.
     */
    private void applyEmoticonsInternalBonePose(
            AbstractMorph child,
            BodyPartModelData data,
            int frame)
    {
        if (child == null || data == null)
        {
            return;
        }

        try
        {
            Class<?> morphClass = Class.forName(
                    "mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph"
            );

            if (!morphClass.isInstance(child))
            {
                return;
            }

            java.lang.reflect.Field poseField = morphClass.getField("pose");
            Object pose = poseField.get(child);
            Class<?> poseClass = Class.forName(
                    "mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedPose"
            );

            if (pose != null)
            {
                pose = poseClass.getMethod("clone").invoke(pose);
            }
            else
            {
                pose = poseClass.newInstance();
            }

            java.lang.reflect.Field bonesField = poseClass.getField("bones");
            Object bonesObject = bonesField.get(pose);

            if (!(bonesObject instanceof java.util.Map))
            {
                return;
            }

            java.util.Map<Object, Object> poseBones =
                    (java.util.Map<Object, Object>) bonesObject;
            Class<?> transformClass = Class.forName(
                    "mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorPoseTransform"
            );

            int matchedBones = 0;

            for (AnimationBone bone : data.getBones())
            {
                if (bone == null || bone.getName() == null
                        || bone.getKeyframes().isEmpty())
                {
                    continue;
                }

                AnimationTransform transform = bone.getTransformAt(frame);

                if (transform == null)
                {
                    continue;
                }

                Object poseTransform = poseBones.get(bone.getName());

                if (poseTransform == null)
                {
                    for (java.util.Map.Entry<Object, Object> entry : poseBones.entrySet())
                    {
                        if (entry.getKey() != null
                                && String.valueOf(entry.getKey())
                                        .equalsIgnoreCase(bone.getName()))
                        {
                            poseTransform = entry.getValue();
                            break;
                        }
                    }
                }

                if (poseTransform == null)
                {
                    poseTransform = transformClass
                            .getConstructor(String.class)
                            .newInstance(bone.getName());
                    poseBones.put(bone.getName(), poseTransform);
                }

                addPoseFloat(poseTransform, "x", transform.getPositionX());
                addPoseFloat(poseTransform, "y", transform.getPositionY());
                addPoseFloat(poseTransform, "z", transform.getPositionZ());

                /* BOBJ bone rotations are radians. Editor values are degrees. */
                addPoseFloat(poseTransform, "rotateX",
                        (float) Math.toRadians(transform.getRotationX()));
                addPoseFloat(poseTransform, "rotateY",
                        (float) Math.toRadians(transform.getRotationY()));
                addPoseFloat(poseTransform, "rotateZ",
                        (float) Math.toRadians(transform.getRotationZ()));

                /*
                 * Emoticons multiplies the source bone scale by this pose
                 * scale. Treat the editor scale as a multiplier, not as an
                 * additive delta like Chameleon's pose representation.
                 */
                multiplyPoseFloat(poseTransform, "scaleX", transform.getScaleX());
                multiplyPoseFloat(poseTransform, "scaleY", transform.getScaleY());
                multiplyPoseFloat(poseTransform, "scaleZ", transform.getScaleZ());

                matchedBones++;
            }

            poseField.set(child, pose);

            /* Pose is refreshed every Preview frame; do not flood the client log. */
        }
        catch (ClassNotFoundException ignored)
        {
            /* Emoticons isn't installed; this adapter is optional. */
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor][BodyParts] Could not apply Emoticons internal pose"
            );
            error.printStackTrace();
        }
    }

    private void multiplyPoseFloat(
            Object target,
            String fieldName,
            float multiplier) throws Exception
    {
        java.lang.reflect.Field field = target.getClass().getField(fieldName);
        field.setFloat(target, field.getFloat(target) * multiplier);
    }

    /**
     * Apply internal-bone keyframes to a Chameleon morph.
     *
     * Chameleon renders its bones from ChameleonMorph.pose. Editing the
     * underlying ModelBone.current values would be discarded because the
     * Chameleon renderer resets the model pose before every render. Instead,
     * clone the morph's AnimatedPose and add the editor's offsets to its
     * AnimatedPoseTransform entries.
     *
     * Reflection keeps Chameleon an optional runtime dependency.
     */
    private void applyChameleonInternalBonePose(
            AbstractMorph child,
            BodyPartModelData data,
            int frame)
    {
        if (child == null || data == null)
        {
            return;
        }

        try
        {
            Class<?> chameleonMorphClass =
                    Class.forName("mchorse.chameleon.metamorph.ChameleonMorph");

            if (!chameleonMorphClass.isInstance(child))
            {
                return;
            }

            java.lang.reflect.Field poseField =
                    chameleonMorphClass.getField("pose");

            Object pose =
                    poseField.get(child);

            if (pose != null)
            {
                pose = pose.getClass().getMethod("clone").invoke(pose);
            }
            else
            {
                pose = Class.forName(
                        "mchorse.chameleon.metamorph.pose.AnimatedPose"
                ).newInstance();
            }

            java.lang.reflect.Field bonesField =
                    pose.getClass().getField("bones");

            Object bonesObject =
                    bonesField.get(pose);

            if (!(bonesObject instanceof java.util.Map))
            {
                return;
            }

            java.util.Map<Object, Object> poseBones =
                    (java.util.Map<Object, Object>) bonesObject;

            Class<?> poseTransformClass =
                    Class.forName(
                            "mchorse.chameleon.metamorph.pose.AnimatedPoseTransform"
                    );

            int matchedBones = 0;

            for (AnimationBone bone : data.getBones())
            {
                if (bone == null || bone.getName() == null
                        || bone.getKeyframes().isEmpty())
                {
                    continue;
                }

                AnimationTransform transform =
                        bone.getTransformAt(frame);

                if (transform == null)
                {
                    continue;
                }

                Object poseTransform =
                        poseBones.get(bone.getName());

                if (poseTransform == null)
                {
                    for (java.util.Map.Entry<Object, Object> entry
                            : poseBones.entrySet())
                    {
                        if (entry.getKey() != null
                                && String.valueOf(entry.getKey())
                                        .equalsIgnoreCase(bone.getName()))
                        {
                            poseTransform = entry.getValue();
                            break;
                        }
                    }
                }

                if (poseTransform == null)
                {
                    poseTransform = poseTransformClass
                            .getConstructor(String.class)
                            .newInstance(bone.getName());

                    poseBones.put(bone.getName(), poseTransform);
                }

                addPoseFloat(poseTransform, "x", transform.getPositionX());
                addPoseFloat(poseTransform, "y", transform.getPositionY());
                addPoseFloat(poseTransform, "z", transform.getPositionZ());

                /* Chameleon stores pose rotations in radians, unlike Blockbuster. */
                addPoseFloat(poseTransform, "rotateX",
                        (float) Math.toRadians(transform.getRotationX()));
                addPoseFloat(poseTransform, "rotateY",
                        (float) Math.toRadians(transform.getRotationY()));
                addPoseFloat(poseTransform, "rotateZ",
                        (float) Math.toRadians(transform.getRotationZ()));

                /* Chameleon's pose scale is an additive offset from 1.0. */
                addPoseFloat(poseTransform, "scaleX", transform.getScaleX() - 1.0F);
                addPoseFloat(poseTransform, "scaleY", transform.getScaleY() - 1.0F);
                addPoseFloat(poseTransform, "scaleZ", transform.getScaleZ() - 1.0F);

                matchedBones++;
            }

            poseField.set(child, pose);

            if (matchedBones > 0)
            {
                System.out.println(
                        "[BBS Animation Editor][BodyParts] Chameleon internal pose applied: model="
                                + child.name + ", frame=" + frame
                                + ", matchedBones=" + matchedBones
                );
            }
        }
        catch (ClassNotFoundException ignored)
        {
            /* Chameleon isn't installed; this adapter is optional. */
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor][BodyParts] Could not apply Chameleon internal pose"
            );
            error.printStackTrace();
        }
    }

    private void addPoseFloat(
            Object target,
            String fieldName,
            float delta) throws Exception
    {
        java.lang.reflect.Field field =
                target.getClass().getField(fieldName);

        field.setFloat(
                target,
                field.getFloat(target) + delta
        );
    }

    /**
     * Apply the Body Parts internal-bone keyframes to a CustomMorph pose.
     */
    private void applyInternalBonePose(
            CustomMorph child,
            BodyPartModelData data,
            int frame)
    {
        if (child == null || data == null)
        {
            return;
        }

        try
        {
            child.updateModel(true);

            Model apiModel = child.model;
            if (apiModel == null)
            {
                String name = child.name == null ? "" : child.name;
                if (name.startsWith("blockbuster."))
                {
                    name = name.substring("blockbuster.".length());
                }

                if (!name.isEmpty())
                {
                    BlockbusterModelAccess access =
                            new BlockbusterModelAccess(null);

                    if (access.loadModelByName(name)
                            && access.getApiModel() instanceof Model)
                    {
                        apiModel = (Model) access.getApiModel();
                        child.model = apiModel;
                    }
                }
            }

            if (apiModel == null)
            {
                return;
            }

            /*
             * Start from the morph's actual pose rather than an empty pose.
             * A blank ModelProperties silently replaces the model's standing
             * pose and can lose limb defaults/properties. Convert every source
             * transform to LimbProperties so Blockbuster keeps the per-limb
             * rendering flags as well as its transform.
             */
            CustomMorph.ModelProperties pose =
                    new CustomMorph.ModelProperties();

            mchorse.blockbuster.api.ModelPose sourcePose;
            if (this.originalChildPoses.containsKey(child))
            {
                sourcePose = this.originalChildPoses.get(child);
            }
            else
            {
                sourcePose = child.customPose != null
                        ? child.customPose
                        : apiModel.getPose(child.currentPose);
                /*
                 * Cache before editor offsets are applied, including null.
                 * The source pose is only read/copied below; the animated
                 * result is a separate ModelProperties instance.
                 */
                this.originalChildPoses.put(child, sourcePose);
            }

            if (sourcePose != null)
            {
                pose.size = new float[] {
                        sourcePose.size[0],
                        sourcePose.size[1],
                        sourcePose.size[2]
                };

                for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelTransform> entry
                        : sourcePose.limbs.entrySet())
                {
                    if (entry.getKey() == null || entry.getValue() == null)
                    {
                        continue;
                    }

                    CustomMorph.LimbProperties properties =
                            new CustomMorph.LimbProperties();
                    properties.copy(entry.getValue());
                    pose.limbs.put(entry.getKey(), properties);
                }

                for (mchorse.blockbuster.api.formats.obj.ShapeKey shape : sourcePose.shapes)
                {
                    pose.shapes.add(shape.copy());
                }
            }

            pose.updateLimbs(apiModel, false);

            int matchedBones = 0;
            int missingBones = 0;

            for (AnimationBone bone : data.getBones())
            {
                if (bone == null || bone.getName() == null
                        || bone.getKeyframes().isEmpty())
                {
                    /*
                     * An unkeyed bone must retain the source model's standing
                     * transform. getTransformAt() returns identity for an
                     * empty track, and writing that identity to every limb
                     * was erasing the model's default pose on every preview
                     * refresh.
                     */
                    continue;
                }

                mchorse.blockbuster.api.ModelTransform target =
                        pose.limbs.get(bone.getName());

                if (target == null)
                {
                    /* Model limb names are normally exact, but tolerate case-only
                     * differences from third-party/custom model metadata. */
                    for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelTransform> entry
                            : pose.limbs.entrySet())
                    {
                        if (entry.getKey() != null
                                && entry.getKey().equalsIgnoreCase(bone.getName()))
                        {
                            target = entry.getValue();
                            break;
                        }
                    }
                }

                if (target == null)
                {
                    missingBones++;
                    continue;
                }

                AnimationTransform transform =
                        bone.getTransformAt(frame);

                if (transform == null)
                {
                    continue;
                }

                /*
                 * The copied ModelTransform already stores the rest-pose
                 * translation in Blockbuster's render coordinate system.
                 * AnimationBone pivots are converted for editor interaction;
                 * using localY/localZ here mixes coordinate systems and can
                 * mirror the limb. Internal position keys are offsets.
                 */
                target.translate[0] += transform.getPositionX();
                target.translate[1] += transform.getPositionY();
                target.translate[2] += transform.getPositionZ();

                /* Keyframe rotations/scales are offsets from the source pose. */
                target.rotate[0] += transform.getRotationX();
                target.rotate[1] += transform.getRotationY();
                target.rotate[2] += transform.getRotationZ();
                target.scale[0] *= transform.getScaleX();
                target.scale[1] *= transform.getScaleY();
                target.scale[2] *= transform.getScaleZ();
                matchedBones++;
            }

            child.customPose = pose;
            child.currentPose = "";
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] Failed to apply Body Parts internal bone pose"
            );
            error.printStackTrace();
        }
    }

    /**
     * Build the child CustomMorph which Metamorph's BodyPart will render.
     */
    private CustomMorph createChildMorph(
            String modelName,
            Model apiModel,
            BodyPartModelData data,
            int frame)
    {
        CustomMorph child = null;

        CharacterKey stateKey = data.getCharacterStateKeyAt(frame);

        if (stateKey != null && stateKey.hasSkin())
        {
            try
            {
                AbstractMorph savedSkin = MorphManager.INSTANCE.morphFromNBT(stateKey.getSkin());
                if (savedSkin instanceof CustomMorph)
                {
                    child = (CustomMorph) savedSkin;
                    child.model = apiModel;
                }
            }
            catch (Throwable error)
            {
                error.printStackTrace();
            }
        }

        if (child == null)
        {
            child = new CustomMorph();
            child.name = "blockbuster." + modelName;
            child.model = apiModel;
        }

        /*
         * CustomMorph.updateModel() is the normal Blockbuster model
         * resolution path.  The API model is assigned first so the morph
         * remains valid even before ModelHandler's next update.
         */
        child.updateModel(true);

        if (child.model == null)
        {
            child.model = apiModel;
        }

        /*
         * Use the same internal-bone pose pipeline for both model-picker
         * attachments and morph-picker attachments. The old duplicate path
         * skipped every internal bone whenever a model key selected a name
         * different from the attachment's original modelName.
         */
        applyInternalBonePose(child, data, frame);

        CharacterBodyPartOverrideController.apply(
                child,
                data.getCharacterTimeline(),
                frame
        );

        return child;
    }

    public void clear()
    {
        bakeController.reset();
        parentColliderCaches.clear();
        parentEmoticonsColliders=null;
        parentEmoticonsColliderFrame=-1;
        reportedParentColliderState="";
        joltPreview.clear();
        joltActorWorldFrame=null;
        joltActorWorldFrameTick=-1;
        lastContactProbeFrame.clear();
        reportedJoltPreview.clear();
        joltTorsoFits.clear();
        joltMultiTorsoFits.clear();
        joltTorsoFitFrames.clear();
        reportedJoltTorsoFit.clear();

        joltSampleDirections.clear();
        joltSampleFrames.clear();
        joltSampleNativeDirections.clear();
        joltSampleParentMatrices.clear();
        joltAppliedDirections.clear();
        joltAppliedFrames.clear();
        joltAppliedNativeDirections.clear();
        joltAppliedParentMatrices.clear();
        lastJoltDirectionReports.clear();
        lastJoltDirectionTimes.clear();
        physicsFrames.clear();
        /*
         * There is no renderer-owned LayerRenderer anymore.
         * Body parts live inside the actor's CustomMorph and are rebuilt
         * on the next prepare() call.
         */
    }
}
