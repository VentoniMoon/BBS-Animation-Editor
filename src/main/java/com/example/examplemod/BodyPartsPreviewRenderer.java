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
    private final BodyPartPhysicsPreviewSimulation livePhysics =
            new BodyPartPhysicsPreviewSimulation();

    /** Native parent bone snapshot made AFTER the ordinary actor render. */
    private final Map<BodyPartModelData, float[]> renderedAttachmentMatrices =
            new IdentityHashMap<BodyPartModelData, float[]>();
    private final Map<BodyPartModelData, Integer> renderedAttachmentFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    private final Map<BodyPartModelData, Boolean> reportedPostRenderMatrices =
            new IdentityHashMap<BodyPartModelData, Boolean>();
    /* One-frame-delayed, bounded visual probe; only the left root is enabled.
     * Rebuilt from the clean keyed pose on each render, never accumulated. */
    private final Map<BodyPartModelData, float[]> pendingLeftRootSwings =
            new IdentityHashMap<BodyPartModelData, float[]>();
    private final Map<BodyPartModelData, Integer> pendingLeftRootFrames =
            new IdentityHashMap<BodyPartModelData, Integer>();
    private final Map<BodyPartModelData, Boolean> visualProbeReported =
            new IdentityHashMap<BodyPartModelData, Boolean>();
    /* Snapshot the clean Chameleon pose rotation before applying the probe.
     * This stays separate from the modified preview morph. */
    private final Map<BodyPartModelData, Float> leftRootBaseRotation =
            new IdentityHashMap<BodyPartModelData, Float>();
    private final Map<BodyPartModelData, Float> leftRootAppliedRotation =
            new IdentityHashMap<BodyPartModelData, Float>();

    /* Actual child BodyParts from this preview pass, never re-rendered. */
    private final Map<BodyPartModelData, BodyPart> renderedPhysicsParts =
            new IdentityHashMap<BodyPartModelData, BodyPart>();

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
    public void captureRenderedAttachmentMatrices(EntityActor actor, int frame)
    {
        if (actor == null || actor.getMorph() == null) return;
        Object morph = actor.getMorph();
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
            BodyPart childPart = renderedPhysicsParts.get(data);
            if (childPart != null)
            {
                diagnoseVisualHairResult(data, childPart, frame);
                // Evaluate world-chain directions from the currently rendered
                // Emoticons basis. The write-back remains disabled until the
                // local Chameleon rotation convention has been validated.
                probePhysicsBoneMatrices(data, childPart, matrix, frame);
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
     * One-shot sanity check of the actual native Chameleon model pose after
     * the ordinary render. A successful write to morph.pose is not proof
     * that a bone was rotated during draw. No second render is invoked.
     */
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
                    + " currentPoseX=" + readChameleonPoseRotationX(morph, "left_strand_1"));
        }
        catch (Throwable error)
        {
            lastVisualHairDiagnostic = key;
            System.out.println("[BBS Animation Editor][Physics] VISUAL CHECK unavailable: "
                    + error.getClass().getSimpleName());
        }
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
                probePhysicsBoneMatrices(entry.getKey(), entry.getValue(), nativeMatrix, frame);
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
            float[] parentMatrix, int frame)
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

        /* Metamorph records parent limb BEFORE its BodyPart transform. */
        try
        {
            float[] attached = BodyPartPhysicsAttachment.worldMatrix(parentMatrix, part);
            BodyPartPhysicsWorldPose.MatrixSource world =
                    BodyPartPhysicsWorldPose.worldSource(attached, local);
            boolean advanced = false;
            int appliedSwings = 0;
            for (BodyPartPhysicsChains.Chain chain : chains)
            {
                BodyPartChainSolver.Vec3[] joints =
                        new BodyPartChainSolver.Vec3[chain.bones.size()];
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
                BodyPartChainSolver.Vec3[] simulated =
                        livePhysics.sample(data.getTimelineId(), chain, frame, joints);
                if (simulated != null && frame > data.getStartFrame())
                {
                    for (int i = 1; i < simulated.length; i++)
                        if (simulated[i].sub(joints[i]).length() > 0.00001F)
                            advanced = true;

                    /*
                     * Physics follows actual WORLD positions but Chameleon
                     * needs local bone rotations. Each bone aims its original
                     * child-pivot direction toward the simulated direction.
                     * Skip the last bone until its actual tip is available.
                     * The editor's source animation keys remain unchanged.
                     */
                    for (int i = 0; i + 1 < chain.bones.size(); i++)
                    {
                        AnimationBone bone = chain.bones.get(i);
                        float[] basis = world.getBoneWorldMatrix(bone.getName());
                        if (basis == null) continue;
                        BodyPartChainSolver.Vec3 restWorld =
                                joints[i + 1].sub(joints[i]);
                        BodyPartChainSolver.Vec3 solvedWorld =
                                simulated[i + 1].sub(simulated[i]);
                        BodyPartChainSolver.Vec3 restLocal =
                                BodyPartPhysicsPoseAdapter.toLocalDirection(basis, restWorld);
                        BodyPartChainSolver.Vec3 solvedLocal =
                                BodyPartPhysicsPoseAdapter.toLocalDirection(basis, solvedWorld);
                        float[] swing = BodyPartPhysicsPoseAdapter.localSwing(
                                restLocal, solvedLocal);
                        if ("left_strand_1".equals(chain.root.getName()) && i == 0)
                        {
                            pendingLeftRootSwings.put(data, swing);
                            pendingLeftRootFrames.put(data, Integer.valueOf(frame));
                        }
                        /*
                         * Root-only test revealed a wrong upward rotation and
                         * jitter. Keep solver diagnostics but disable pose
                         * mutation until we can compare native Chameleon
                         * bone basis with solved world direction.
                         */
                        if ("left_strand_1".equals(chain.root.getName()) && i == 0
                                && (frame == 20 || frame == 60))
                            reportPhysicsSwingProbe(data, frame, restWorld,
                                    solvedWorld, swing);
                    }
                }
            }
            reportPhysicsBoneProbe(data, advanced
                    ? "world strand solver advanced; unstable visual swings disabled; writes="
                            + appliedSwings + " (visual result unverified)"
                    : "world strand joint matrices valid: " + chains.size()
                      + " chains (no visible physics delta yet)");
        }
        catch (RuntimeException error)
        {
            reportPhysicsBoneProbe(data, "world strand matrices invalid: "
                    + error.getClass().getSimpleName());
        }
    }

    private final Map<BodyPartModelData, String> physicsSwingDiagnostics =
            new IdentityHashMap<BodyPartModelData, String>();

    private void reportPhysicsSwingProbe(BodyPartModelData model, int frame,
            BodyPartChainSolver.Vec3 original,
            BodyPartChainSolver.Vec3 simulated, float[] swing)
    {
        String key = model.getTimelineId() + ":" + frame;
        if (key.equals(physicsSwingDiagnostics.get(model))) return;
        physicsSwingDiagnostics.put(model, key);
        System.out.println("[BBS Animation Editor][Physics] LEFT ROOT frame=" + frame
                + " restDir=(" + original.x + "," + original.y + "," + original.z + ")"
                + " solvedDir=(" + simulated.x + "," + simulated.y + "," + simulated.z + ")"
                + " localSwingAxis=(" + swing[0] + "," + swing[1] + "," + swing[2] + ")"
                + " angleRad=" + swing[3] + " (render write disabled)");
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
        manager.parts.clear();
        this.preparedMorphs.clear();
        this.previewPhysicsChains.clear();
        this.physicsAttachmentMatrices.clear();
        this.renderedPhysicsParts.clear();

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
             * Child pose snapshots also arrive every tick. Give nested
             * Blockbuster morphs the same one-tick rendering transition
             * as the actor's parent CustomMorph.
             */
            if (part.morph != null
                    && part.morph.get() instanceof CustomMorph)
            {
                CustomMorph child = (CustomMorph) part.morph.get();
                child.animation.duration = 1;
                child.animation.animates = true;
                child.animation.ignored = false;
                child.animation.interp =
                        mchorse.mclib.utils.Interpolation.LINEAR;
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
            Integer sampled = pendingLeftRootFrames.get(data);
            float[] swing = pendingLeftRootSwings.get(data);
            if (sampled != null && sampled.intValue() == frame - 1 && swing != null)
            {
                boolean applied = BodyPartChameleonPhysicsPose.addLocalSwing(
                        child, "left_strand_1", swing);
                if (applied)
                {
                    Float rotation = readChameleonPoseRotationX(child, "left_strand_1");
                    if (rotation != null) leftRootAppliedRotation.put(data, rotation);
                }
                if (applied && !visualProbeReported.containsKey(data))
                {
                    visualProbeReported.put(data, Boolean.TRUE);
                    System.out.println("[BBS Animation Editor][Physics] "
                            + "LEFT ROOT visual probe active (one-frame delay, diagnostic max 0.45 rad)");
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
        /*
         * There is no renderer-owned LayerRenderer anymore.
         * Body parts live inside the actor's CustomMorph and are rebuilt
         * on the next prepare() call.
         */
    }
}
