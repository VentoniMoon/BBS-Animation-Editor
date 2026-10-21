package com.example.examplemod;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OPTIONAL Jolt preview bridge. Java 8 / Forge classes have NO hard JNI
 * linkage; an absent physics prototype leaves the old renderer available.
 *
 * Enable ONLY for development:
 *   -Dbbs.editor.physics.jolt.preview=true
 *
 * Jolt JNI and the jolt-hair-prototype Java8 jar must both be on the launch
 * classpath. The experimental module is intentionally not a Forge mod.
 *
 * This bridge owns its native preview scenes and never exports keyframes.
 * Collision sources include native Emoticons torso proxies and oriented
 * Chameleon cuboids from other attached BodyParts.
 */
public final class BodyPartJoltPreviewAdapter {
    private static final boolean REQUESTED =
            Boolean.getBoolean("bbs.editor.physics.jolt.preview");
    private static final boolean RAW_DIAGNOSTIC =
            Boolean.getBoolean("bbs.editor.physics.jolt.diagnostic");
    /** Jolt preview body contacts default ON; opt out explicitly for A/B. */
    private static final boolean TORSO_CONTACTS =
            REQUESTED && Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.torsoContacts","true"));
    private static final boolean COMPOUND_TORSO =
            Boolean.getBoolean("bbs.editor.physics.jolt.compoundTorso");
    private static volatile boolean drawContacts =
            Boolean.getBoolean("bbs.editor.physics.jolt.drawColliders");
    /** Large teleports are scene changes, not 50ms kinematic velocity.
     * Defaults ON; disable for A/B tests only. Ordinary quick head turns
     * (<165 degrees / tick) and movements (<3 blocks / tick) stay physical.
     */
    /** Preserve contact poses after manual key edits on a paused frame.
     * Large orientation/position changes reseed once; sub-frame jitter does
     * not. Can be disabled for A/B tests. */
    private static final boolean RESEED_EDITED_POSE=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.reseedEditedPose","true"));
    private static final boolean RESEED_TELEPORTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.jolt.reseedTeleports","true"));
    private static final float MAX_ROOT_STEP=3F;
    private static final float MAX_PARENT_TURN=(float)Math.toRadians(165D);
    private static final String RIG =
            "com.example.examplemod.physics.JoltChameleonPreviewRig";
    private boolean attempted;
    private boolean available;
    private boolean diagnosticLogged;
    private Method create;
    private Method setGravity;
    private Method advance;
    /** Newer Jolt prototype: source-root key rotation as well as parent. */
    private Method advanceWithBones;
    private boolean warnedOldRotationApi;
    private Method settle;
    private Method localSwings;
    private Method setTorsoCollider;
    private Method setTorsoSegments;
    private Method setAdditionalBoxes;
    private Method setAdditionalShapes;
    private boolean warnedTypedColliders;
    private Method nativeHairSegments;
    private boolean torsoWarning;
    /** Optional prototype API: old downloaded JARs must still work. */
    private Method nativeDirections;
    private boolean nativeDirectionWarning;
    private boolean recreatedLastSample;
    /** Read from the editor's playback state by its render owner. Keep
     * this adapter independent of the GUI/actor state for JNI tests. */
    private boolean timelinePlaying;

    public void setTimelinePlaying(boolean playing)
    {
        this.timelinePlaying=playing;
    }

    /** Data for a fully integrated native frame, never a guessed current
     * frame after direct scrubbing. Copied buffers keep snapshots immutable
     * while the Jolt world continues evolving at its highest visited tick. */
    private static final class RecordedFrame {
        final Map<String,float[]> pose, raw;
        final float[][] hair,boxes,shapes;
        final float[] parent;
        RecordedFrame(Map<String,float[]> pose,Map<String,float[]> raw,
                float[][] hair,float[][] boxes,float[][] shapes,float[] parent)
        {
            this.pose=copy(pose);
            this.raw=copy(raw);
            this.hair=copyMatrices(hair);
            this.boxes=copyMatrices(boxes);
            this.shapes=copyMatrices(shapes);
            this.parent=parent==null?null:parent.clone();
        }
    }

    private static final class State {
        final AutoCloseable rig;
        final String[] bones;
        final float stiffness, damping, gravity;
        int frame;
        float[] lastRoot;
        float[] lastParent;
        float[][] lastBones;
        float[] lastTip;
        final boolean torsoEnabled;
        float[] lastTorsoA,lastTorsoB;
        float torsoRadius;
        final boolean compoundEnabled;
        float[][] lastMultiA,lastMultiB;
        float[] lastMultiR;
        float[][] lastAdditionalBoxes;
        float[][] lastAdditionalShapes;
        final boolean typedShapes;
        float[][] rawHairBodies;
        Map<String, float[]> sample=Collections.emptyMap();
        Map<String, float[]> raw=Collections.emptyMap();
        final java.util.NavigableMap<Integer,RecordedFrame> history=
                new java.util.TreeMap<Integer,RecordedFrame>();
        int presentedFrame=-1;

        void remember(int tick)
        {
            history.put(Integer.valueOf(tick),
                    new RecordedFrame(sample,raw,rawHairBodies,
                            lastAdditionalBoxes,lastAdditionalShapes,lastParent));
            // Bound memory. Uncached older seek falls back to the earlier
            // conservative scene-recreation behavior.
            while(history.size()>1500)
                history.pollFirstEntry();
            presentedFrame=tick;
        }

        State(AutoCloseable rig, String[] bones, int frame,
                float stiffness, float damping, float gravity,
                float[] root, float[] parent, float[][] boneWorld,
                float[] terminalTip,float[] torsoA,float[] torsoB,
                float radius,BodyPartJoltMultiTorsoFit.Segments multi,
                float[][] attachedBoxes,float[][] attachedShapes,
                boolean typedShapes) {
            this.rig=rig;
            this.bones=bones.clone();
            this.frame=frame;
            this.stiffness=stiffness;
            this.damping=damping;
            this.gravity=gravity;
            this.lastRoot=root.clone();
            this.lastParent=parent.clone();
            this.lastBones=copyMatrices(boneWorld);
            this.lastTip=terminalTip.clone();
            this.torsoEnabled=torsoA!=null && torsoB!=null;
            this.lastTorsoA=torsoA==null?null:torsoA.clone();
            this.lastTorsoB=torsoB==null?null:torsoB.clone();
            this.torsoRadius=radius;
            this.compoundEnabled=multi!=null;
            this.lastMultiA=multi==null?null:multi.starts();
            this.lastMultiB=multi==null?null:multi.ends();
            this.lastMultiR=multi==null?null:multi.radii();
            this.lastAdditionalBoxes=copyMatrices(attachedBoxes);
            this.lastAdditionalShapes=typedShapes?copyMatrices(attachedShapes):null;
            this.typedShapes=typedShapes;
        }
    }

    private final Map<String, State> scenes = new HashMap<String, State>();

    public static boolean isRequested() { return REQUESTED; }

    public static boolean torsoContactsRequested() {
        return REQUESTED && TORSO_CONTACTS;
    }

    public static boolean compoundTorsoRequested() {
        return REQUESTED && TORSO_CONTACTS && COMPOUND_TORSO;
    }

    public static boolean drawContactsRequested() {
        return REQUESTED && drawContacts;
    }

    /** UI-only toggle; native physics and collision enablement are unaffected. */
    public static void toggleDrawContacts() {
        if(REQUESTED) drawContacts=!drawContacts;
    }

    /** Clear one scene after an explicit keyframe / source transform edit.
     *  Never call this from rendering; it would destroy fixed-step inertia.
     */
    public void invalidate(String modelId)
    {
        if(modelId!=null)dispose(modelId);
    }

    /** A scene was newly initialized and can publish a settled paused pose. */
    public boolean wasRecreatedThisSample() { return recreatedLastSample; }

    private boolean initialize(File gameDir) {
        if(!REQUESTED)return false;
        if(attempted)return available;
        attempted=true;
        try {
            Class<?> bootstrap=Class.forName(
                    "com.example.examplemod.physics.JoltJava8Bootstrap");
            Method start=bootstrap.getMethod("available",File.class);
            available=Boolean.TRUE.equals(start.invoke(null,gameDir));
            if(available) {
                Class<?> klass=Class.forName(RIG);
                create=klass.getMethod("create",int.class,float[][].class,
                        float[].class,float[].class,
                        float.class,float.class,float.class);
                setGravity=klass.getMethod("setGravity",float.class);
                advance=klass.getMethod("advance",int.class,
                        float[].class,float[].class);
                try {
                    advanceWithBones=klass.getMethod("advance",int.class,
                            float[].class,float[].class,float[][].class);
                } catch(NoSuchMethodException older) {
                    if(!warnedOldRotationApi) {
                        warnedOldRotationApi=true;
                        System.err.println("[BBS Editor][Jolt][RootRotation] "
                                + "Installed prototype lacks animated root-bone rotation. "
                                + "Update jolt-hair-prototype-java8.jar.");
                    }
                }
                settle=klass.getMethod("settle",int.class);
                localSwings=klass.getMethod("localBoneSwings",
                        float[][].class,float[].class);
                if(TORSO_CONTACTS) {
                    try {
                        setTorsoCollider=klass.getMethod("setTorsoCollider",
                                float[].class,float[].class,float.class);
                    } catch(NoSuchMethodException missing) {
                        if(!torsoWarning) {
                            torsoWarning=true;
                            System.err.println("[BBS Editor][Jolt][Torso] "
                                    + "Installed experimental JAR lacks torso collider API. "
                                    + "Contacts disabled; update Jolt prototype from GitHub Actions.");
                        }
                    }
                }
                if(compoundTorsoRequested()) {
                    try {
                        setTorsoSegments=klass.getMethod("setTorsoSegments",
                                float[][].class,float[][].class,float[].class);
                    } catch(NoSuchMethodException older) {
                        System.err.println("[BBS Editor][Jolt][Compound] "
                                + "Updated Jolt prototype needed; using single fitted torso");
                    }
                }
                try {
                    setAdditionalShapes=klass.getMethod("setAdditionalShapes",
                            float[][].class);
                } catch(NoSuchMethodException older) {
                    // Optional primitive extension; legacy boxes still work.
                }
                try {
                    setAdditionalBoxes=klass.getMethod("setAdditionalBoxes",
                            float[][].class);
                } catch(NoSuchMethodException older) {
                    System.err.println("[BBS Editor][Jolt][BodyParts] "
                            + "Installed experimental JAR predates general collision boxes. "
                            + "Download the new Jolt artifact to enable attached-part contacts.");
                }
                try {
                    nativeHairSegments=klass.getMethod("nativeHairSegments");
                } catch(NoSuchMethodException older) {
                    // Drawing can be switched ON later in the inspector.
                    // Older physics prototype works without this debug API.
                    if(drawContacts)
                        System.err.println("[BBS Editor][Jolt][Overlay] "
                                + "Updated Jolt prototype needed for native hair segment overlay");
                }
                if(RAW_DIAGNOSTIC) {
                    try {
                        nativeDirections=klass.getMethod("rawPhysicalWorldDirections");
                    } catch(NoSuchMethodException oldPrototype) {
                        // Do not disable working physics because a diagnostic
                        // was added after the user's local JAR was downloaded.
                        if(!nativeDirectionWarning) {
                            nativeDirectionWarning=true;
                            System.err.println("[BBS Editor][Jolt][Raw] "
                                    + "Installed prototype JAR lacks raw direction API; "
                                    + "download a newer CI artifact for native-vs-Euler comparison");
                        }
                    }
                }
            }
        } catch(Throwable error) {
            available=false;
            System.err.println("[BBS Editor][Jolt] Optional Java8 prototype unavailable: "+error);
        }
        if(!available && !diagnosticLogged) {
            diagnosticLogged=true;
            System.err.println("[BBS Editor][Jolt] Preview unavailable (legacy solver disabled)");
        }
        return available;
    }

    /**
     * Null means Jolt unavailable; the old legacy preview is not re-enabled.
     * An initialized or reset scene returns its bounded gravity-settled pose.
     * A nonempty map is one preview-only swing per native model bone.
     */
    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity) {
        return sample(gameDir,modelId,boneNames,frame,cleanBoneWorld,
                terminalTipWorld,cleanParentWorld,stiffness,damping,gravity,
                null,null,0F,null);
    }

    /**
     * Optional torso contact input from a native Emoticons render frame.
     * Omitting or rejecting a mesh fit keeps the old free-hanging Jolt rig.
     */
    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity,
            float[] torsoA,float[] torsoB,float torsoRadius) {
        return sample(gameDir,modelId,boneNames,frame,cleanBoneWorld,
                terminalTipWorld,cleanParentWorld,stiffness,damping,gravity,
                torsoA,torsoB,torsoRadius,null);
    }

    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity,
            float[] torsoA,float[] torsoB,float torsoRadius,
            BodyPartJoltMultiTorsoFit.Segments compound) {
        return sample(gameDir,modelId,boneNames,frame,cleanBoneWorld,
                terminalTipWorld,cleanParentWorld,stiffness,damping,gravity,
                torsoA,torsoB,torsoRadius,compound,null);
    }

    /** Backwards-compatible API for older callers and Jolt artifacts. */
    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity,
            float[] torsoA,float[] torsoB,float torsoRadius,
            BodyPartJoltMultiTorsoFit.Segments compound,
            float[][] attachedBoxes)
    {
        return sample(gameDir,modelId,boneNames,frame,cleanBoneWorld,
                terminalTipWorld,cleanParentWorld,stiffness,damping,gravity,
                torsoA,torsoB,torsoRadius,compound,attachedBoxes,null);
    }

    /** Extended authored Box/Sphere/Capsule geometry, optional for Java 8. */
    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity,
            float[] torsoA,float[] torsoB,float torsoRadius,
            BodyPartJoltMultiTorsoFit.Segments compound,
            float[][] attachedBoxes,float[][] attachedShapes) {
        recreatedLastSample=false;
        if(!initialize(gameDir))return null;
        if(modelId==null || boneNames==null || cleanBoneWorld==null ||
                boneNames.length!=cleanBoneWorld.length ||
                boneNames.length<1 || cleanParentWorld==null ||
                cleanBoneWorld[0]==null || cleanBoneWorld[0].length!=16 ||
                terminalTipWorld==null || terminalTipWorld.length!=3)return null;
        String id=modelId;
        float[] root=new float[] {cleanBoneWorld[0][12],
                cleanBoneWorld[0][13],cleanBoneWorld[0][14]};
        boolean useCompound=compoundTorsoRequested() &&
                setTorsoSegments!=null && compound!=null &&
                compound.count()==BodyPartJoltMultiTorsoFit.COUNT;
        boolean useTorso=!useCompound && TORSO_CONTACTS &&
                setTorsoCollider!=null &&
                torsoA!=null && torsoB!=null &&
                Float.isFinite(torsoRadius) && torsoRadius>=0.04F;
        float[][] compoundA=useCompound?compound.starts():null;
        float[][] compoundB=useCompound?compound.ends():null;
        float[] compoundR=useCompound?compound.radii():null;
        boolean useShapes=setAdditionalShapes!=null &&
                TORSO_CONTACTS && attachedShapes!=null &&
                attachedShapes.length>0;
        boolean useAdditional=(setAdditionalBoxes!=null || useShapes) &&
                TORSO_CONTACTS && attachedBoxes!=null &&
                attachedBoxes.length>0;
        if(attachedShapes!=null && attachedShapes.length>0 &&
                !useShapes && !warnedTypedColliders)
            for(float[] shape:attachedShapes)
                if(shape!=null && shape.length==11 && shape[10]!=0F)
                {
                    warnedTypedColliders=true;
                    System.err.println("[BBS Editor][Jolt][Shapes] "+
                            "Installed JNI JAR lacks typed primitives; using Box "+
                            "outlines as contact proxies until the JAR is updated.");
                    break;
                }
        float[][] boxes=useAdditional?copyMatrices(attachedBoxes):null;
        float[][] shapes=useShapes?copyMatrices(attachedShapes):null;
        State state=scenes.get(id);

        /*
         * Skin-fitted collider dimensions fluctuate during walk cycles.
         * Jolt's kinematic setters move existing shapes but cannot resize
         * them without rebuilding the rig and losing hair inertia.
         * For sequential ticks, keep the collider dimensions from the
         * initial scene while following the skinned center/orientation.
         * Genuine changes in count/type, settings or timeline position
         * still follow the original recreation path.
         */
        if(state!=null && frame==state.frame+1)
        {
            if(useTorso && state.torsoEnabled)
            {
                JoltKinematicColliderSizing.Capsule stable=
                        JoltKinematicColliderSizing.holdCapsule(
                                torsoA,torsoB,torsoRadius,
                                state.lastTorsoA,state.lastTorsoB,
                                state.torsoRadius);
                torsoA=stable.a;
                torsoB=stable.b;
                torsoRadius=stable.radius;
            }
            if(useCompound && state.compoundEnabled)
            {
                JoltKinematicColliderSizing.holdCompound(
                        compoundA,compoundB,compoundR,
                        state.lastMultiA,state.lastMultiB,state.lastMultiR);
            }
            if(useAdditional && state.typedShapes==useShapes)
            {
                if(useShapes)
                {
                    if(JoltKinematicColliderSizing.holdPrimitiveSizes(
                            shapes,state.lastAdditionalShapes,11))
                        JoltKinematicColliderSizing.holdPrimitiveSizes(
                                boxes,state.lastAdditionalBoxes,10);
                }
                else
                    JoltKinematicColliderSizing.holdPrimitiveSizes(
                            boxes,state.lastAdditionalBoxes,10);
            }
        }

        /* Paused-frame user edits may reseed, but transient render
         * differences during walking must never reinitialize Jolt. */
        boolean changedStaticPose=RESEED_EDITED_POSE && state!=null &&
                frame==state.frame &&
                !this.timelinePlaying &&
                JoltStaticPoseChange.significant(
                        state.lastParent,cleanParentWorld,
                        state.lastAdditionalBoxes,boxes);
        if(changedStaticPose)
            System.out.println("[BBS Editor][Jolt][StaticPoseEdit] "+
                    id+" frame="+frame+" reseed after changed parent/contact pose");
        // A render pass is not a physics tick. A paused timeline can draw
        // dozens of times with slightly different Chameleon partial-tick
        // matrices, or with the previously published physics pose. Those
        // matrices MUST NOT reset the native simulation at the same timeline
        // frame. The old pose-comparison branch rebuilt each hair rig
        // hundreds of times per second (observed 192 times at frame 22).
        //
        // Only a real change to chain settings / collision topology forces
        // a rebuild while paused. Geometry and source transforms are sampled
        // again on the next timeline frame, which is the canonical 20 Hz
        // physics clock. Explicit same-frame key editing is a separate
        // invalidate operation, not inferred from transient render matrices.
        // An already simulated tick remains deterministic on rewind:
        // read its recorded result without moving a live native rig backward.
        // The Jolt world stays at the highest simulated frame, so later
        // forward playback can continue from its true physics history.
        if(state!=null && frame<state.frame &&
                Arrays.equals(state.bones,boneNames) &&
                state.stiffness==stiffness &&
                state.damping==damping && state.gravity==gravity &&
                state.torsoEnabled==useTorso &&
                state.compoundEnabled==useCompound &&
                state.typedShapes==useShapes &&
                (useAdditional==(state.lastAdditionalBoxes!=null)) &&
                (!useAdditional || (useShapes?
                        JoltColliderPrimitives.sameTopology(shapes,state.lastAdditionalShapes):
                        sameBoxGeometry(boxes,state.lastAdditionalBoxes))))
        {
            RecordedFrame recorded=state.history.get(Integer.valueOf(frame));
            if(recorded!=null &&
                    !(RESEED_EDITED_POSE &&
                            JoltStaticPoseChange.significant(
                                    recorded.parent,cleanParentWorld,
                                    recorded.boxes,boxes)))
            {
                state.presentedFrame=frame;
                return copy(recorded.pose);
            }
            if(recorded!=null && RESEED_EDITED_POSE)
                System.out.println("[BBS Editor][Jolt][StaticPoseEdit] "+
                        id+" frame="+frame+" invalidated cached rewind pose");
        }
        if(state!=null && frame==state.frame && !changedStaticPose &&
                Arrays.equals(state.bones,boneNames) &&
                state.stiffness==stiffness && state.damping==damping &&
                state.gravity==gravity &&
                state.torsoEnabled==useTorso &&
                state.compoundEnabled==useCompound &&
                state.typedShapes==useShapes &&
                (useAdditional==(state.lastAdditionalBoxes!=null)) &&
                (!useAdditional || (useShapes?
                        JoltColliderPrimitives.sameTopology(shapes,state.lastAdditionalShapes):
                        sameBoxGeometry(boxes,state.lastAdditionalBoxes))) &&
                (!useTorso ||
                        Math.abs(state.torsoRadius-torsoRadius)<=0.025F) &&
                (!useCompound ||
                        !compoundShapeChanged(state.lastMultiA,state.lastMultiB,
                                state.lastMultiR,compoundA,compoundB,compoundR)))
        {
            state.presentedFrame=frame;
            return copy(state.sample);
        }
        boolean recreate=state==null || changedStaticPose ||
                frame<state.frame || frame>state.frame+1 ||
                !Arrays.equals(state.bones,boneNames) ||
                state.stiffness!=stiffness || state.damping!=damping ||
                state.gravity!=gravity ||
                state.torsoEnabled!=useTorso ||
                state.compoundEnabled!=useCompound ||
                state.typedShapes!=useShapes ||
                (useAdditional != (state.lastAdditionalBoxes!=null)) ||
                (useAdditional && (useShapes?
                        !JoltColliderPrimitives.sameTopology(shapes,state.lastAdditionalShapes):
                        !sameBoxGeometry(boxes,state.lastAdditionalBoxes))) ||
                (useTorso && (Math.abs(state.torsoRadius-torsoRadius)>0.025F ||
                  Math.abs(distance(state.lastTorsoA,state.lastTorsoB)-
                           distance(torsoA,torsoB))>0.065F)) ||
                (useCompound && compoundShapeChanged(
                        state.lastMultiA,state.lastMultiB,state.lastMultiR,
                        compoundA,compoundB,compoundR));
        // Show which SOURCE bone orientations actually arrive from the
        // animation/attachment render pass. Distinguishes missing input
        // rotation from a native motor/Chameleon output mapping problem.
        // Opt-in, only every five sequential 20Hz ticks (never per GUI draw).
        if(RAW_DIAGNOSTIC && state!=null && frame==state.frame+1 &&
                frame%5==0 && state.lastBones!=null &&
                state.lastBones.length==cleanBoneWorld.length)
        {
            float parentTurn=JoltPhysicsDiscontinuity.angularDistance(
                    state.lastParent,cleanParentWorld);
            float rootTurn=JoltPhysicsDiscontinuity.angularDistance(
                    state.lastBones[0],cleanBoneWorld[0]);
            float maxInternal=0F;
            for(int b=1;b<cleanBoneWorld.length;b++)
                maxInternal=Math.max(maxInternal,
                        JoltPhysicsDiscontinuity.angularDistance(
                                state.lastBones[b],cleanBoneWorld[b]));
            float degrees=180F/(float)Math.PI;
            System.out.println("[BBS Editor][Jolt][BoneDrive] "+id+
                    " frame="+frame+" parentStepDegrees="+(parentTurn*degrees)+
                    " rootStepDegrees="+(rootTurn*degrees)+
                    " maxInternalStepDegrees="+(maxInternal*degrees)+
                    " rootShift="+JoltPhysicsDiscontinuity.distance(
                            state.lastRoot,root)+
                    " animatedJointTargets="+(advanceWithBones!=null));
        }
        // Teleport boundaries: do not feed a 6-block move/180-degree
        // instantaneous scene jump into a 50ms Jolt kinematic velocity.
        // Only classify genuinely adjacent frames. Frame seeks already
        // reconstruct the scene above. Rotation test uses world matrices,
        // never flips an Euler channel to hide a discontinuity.
        if(!recreate && RESEED_TELEPORTS && frame==state.frame+1)
        {
            JoltPhysicsDiscontinuity.Kind motion=
                    JoltPhysicsDiscontinuity.classify(state.frame,frame,
                            state.lastRoot,root,state.lastParent,
                            cleanParentWorld,MAX_ROOT_STEP,MAX_PARENT_TURN);
            if(motion!=JoltPhysicsDiscontinuity.Kind.CONTINUOUS)
            {
                System.out.println("[BBS Editor][Jolt][Teleport] "+id+
                        " frame="+frame+" type="+motion+
                        " (reseed rather than huge kinematic impulse)");
                recreate=true;
            }
        }
        // Source-pose comparisons on a paused frame are deliberately
        // disabled: they compare live render/partial-tick matrices with an
        // earlier clean frame and can mistake applied physics for user edits.
        // Rebuilds for actual static key changes must be explicitly
        // invalidated by the editor, or reached via timeline seek.
        if(recreate) {
            if(state!=null)
            {
                String reason;
                if(changedStaticPose) reason="paused-pose-edit";
                else if(frame<state.frame || frame>state.frame+1)
                    reason="seek-or-skipped-frame";
                else if(!Arrays.equals(state.bones,boneNames))
                    reason="bone-topology";
                else if(state.stiffness!=stiffness ||
                        state.damping!=damping || state.gravity!=gravity)
                    reason="physics-settings";
                else if(state.torsoEnabled!=useTorso ||
                        state.compoundEnabled!=useCompound ||
                        state.typedShapes!=useShapes ||
                        (useAdditional!=(state.lastAdditionalBoxes!=null)))
                    reason="collider-mode-or-availability";
                else reason="collision-topology-or-teleport";
                System.out.println("[BBS Editor][Jolt][Recreate] "+id+
                        " previousFrame="+state.frame+
                        " frame="+frame+" reason="+reason);
            }
            dispose(id);
            try {
                Object instance=create.invoke(null,frame,cleanBoneWorld,
                        terminalTipWorld,cleanParentWorld,stiffness,damping,0.6F);
                if(!(instance instanceof AutoCloseable))
                    throw new IllegalStateException("Invalid Jolt preview scene");
                AutoCloseable scene=(AutoCloseable)instance;
                try {
                    setGravity.invoke(instance,gravity);
                    if(useTorso)
                        setTorsoCollider.invoke(instance,torsoA,torsoB,torsoRadius);
                    if(useCompound)
                        setTorsoSegments.invoke(instance,(Object)compoundA,
                                (Object)compoundB,(Object)compoundR);
                    if(useAdditional)
                    {
                        if(useShapes)setAdditionalShapes.invoke(instance,(Object)shapes);
                        else setAdditionalBoxes.invoke(instance,(Object)boxes);
                    }
                    // The source frame may be a static pose or direct seek.
                    // Relax only once on reset; never simulate every render.
                    settle.invoke(instance,24);
                    state=new State(scene,boneNames,frame,stiffness,damping,
                            gravity,root,cleanParentWorld,cleanBoneWorld,
                            terminalTipWorld,useTorso?torsoA:null,
                            useTorso?torsoB:null,useTorso?torsoRadius:0F,
                            useCompound?compound:null,boxes,shapes,useShapes);
                    state.sample=collect(scene,boneNames,cleanBoneWorld,cleanParentWorld);
                    state.raw=collectNativeDirections(scene,boneNames);
                    state.rawHairBodies=collectHairSegments(scene,boneNames);
                    scenes.put(id,state);
                    state.remember(frame);
                    recreatedLastSample=true;
                    // Compare mirrored/neighboring strands from their actual
                    // initial physical inputs, not from subjective camera
                    // perspective. A missing head chain never reaches here.
                    System.out.println("[BBS Editor][Jolt][ChainInit] id="+id+
                            " frame="+frame+" bones="+Arrays.toString(boneNames)+
                            " root="+Arrays.toString(root)+
                            " tip="+Arrays.toString(terminalTipWorld)+
                            " stiffness="+stiffness+" damping="+damping+
                            " gravity="+gravity+" torso="+
                            (useCompound?"compound":useTorso?"single":"none")+
                            " extraBoxes="+(boxes==null?0:boxes.length));
                    return copy(state.sample);
                } catch(Throwable failure) {
                    scene.close();
                    throw failure;
                }
            } catch(Throwable error) {
                report(id,error);
                return null;
            }
        }
        if(frame==state.frame)
        {
            state.presentedFrame=frame;
            return copy(state.sample);
        }
        try {
            if(useTorso)
                setTorsoCollider.invoke(state.rig,torsoA,torsoB,torsoRadius);
            if(useCompound)
                setTorsoSegments.invoke(state.rig,(Object)compoundA,
                        (Object)compoundB,(Object)compoundR);
            if(useAdditional)
            {
                if(useShapes)setAdditionalShapes.invoke(state.rig,(Object)shapes);
                else setAdditionalBoxes.invoke(state.rig,(Object)boxes);
            }
            if(advanceWithBones!=null)
                advanceWithBones.invoke(state.rig,frame,root,
                        cleanParentWorld,cleanBoneWorld);
            else
                advance.invoke(state.rig,frame,root,cleanParentWorld);
            Map<String,float[]> result=collect(state.rig,boneNames,
                    cleanBoneWorld,cleanParentWorld);
            state.sample=copy(result);
            state.raw=collectNativeDirections(state.rig,boneNames);
            state.rawHairBodies=collectHairSegments(state.rig,boneNames);
            state.frame=frame;
            state.lastRoot=root;
            state.lastParent=cleanParentWorld.clone();
            state.lastBones=copyMatrices(cleanBoneWorld);
            state.lastTip=terminalTipWorld.clone();
            if(useTorso) {
                state.lastTorsoA=torsoA.clone();
                state.lastTorsoB=torsoB.clone();
                state.torsoRadius=torsoRadius;
            }
            if(useCompound) {
                state.lastMultiA=copyMatrices(compoundA);
                state.lastMultiB=copyMatrices(compoundB);
                state.lastMultiR=compoundR.clone();
            }
            state.lastAdditionalBoxes=copyMatrices(boxes);
            state.lastAdditionalShapes=useShapes?copyMatrices(shapes):null;
            state.remember(frame);
            return result;
        } catch(Throwable error) {
            dispose(id);
            report(id,error);
            return null;
        }
    }

    /**
     * Raw rigid-body directions for the EXACT scene sample returned by
     * sample(). If the installed prototype predates this optional API,
     * returns an empty map and keeps the main preview working.
     */
    public Map<String,float[]> rawDirectionsFor(String modelId, int frame) {
        if(!RAW_DIAGNOSTIC || modelId==null)return Collections.emptyMap();
        State state=scenes.get(modelId);
        if(state==null || state.presentedFrame!=frame)
            return Collections.emptyMap();
        RecordedFrame recorded=state.history.get(Integer.valueOf(frame));
        return recorded==null?Collections.<String,float[]>emptyMap():
                copy(recorded.raw);
    }

    /**
     * Actual live Jolt body endpoints captured on the latest requested
     * animation frame, never extrapolated from an uncorrected Chameleon pose.
     */
    /**
     * Actual collision mode instantiated on this SAME native scene sample,
     * not merely a candidate fitted shape from the CPU skinned mesh.
     * 0=none, 1=one torso capsule, 3=three torso capsules.
     */
    public int contactModeFor(String modelId,int frame) {
        if(modelId==null)return 0;
        State state=scenes.get(modelId);
        if(state==null || state.presentedFrame!=frame ||
                !state.history.containsKey(Integer.valueOf(frame)))return 0;
        return state.compoundEnabled?3:state.torsoEnabled?1:0;
    }

    /** Box forms installed in an accepted Jolt sample (not guessed markup).
     *  A kinematic box reaches its target at the end of the stepped tick. */
    public float[][] additionalBoxesFor(String modelId,int frame)
    {
        if(!drawContactsRequested() || modelId==null)return null;
        State state=scenes.get(modelId);
        if(state==null || state.presentedFrame!=frame)return null;
        RecordedFrame recorded=state.history.get(Integer.valueOf(frame));
        return recorded==null?null:copyMatrices(recorded.boxes);
    }

    /** Only shapes actually installed by the native typed-collider API.
     *  Null means the older box-only Jolt prototype is in use.
     */
    public float[][] additionalShapesFor(String modelId,int frame)
    {
        if(!drawContactsRequested() || modelId==null)return null;
        State state=scenes.get(modelId);
        if(state==null || state.presentedFrame!=frame)return null;
        RecordedFrame recorded=state.history.get(Integer.valueOf(frame));
        return recorded==null?null:copyMatrices(recorded.shapes);
    }

    public float[][] hairSegmentsFor(String modelId,int frame) {
        if(!drawContactsRequested() || modelId==null)return null;
        State state=scenes.get(modelId);
        if(state==null || state.presentedFrame!=frame)return null;
        RecordedFrame recorded=state.history.get(Integer.valueOf(frame));
        return recorded==null?null:copyMatrices(recorded.hair);
    }

    private float[][] collectHairSegments(AutoCloseable rig,String[] bones) {
        if(!drawContacts || nativeHairSegments==null)return null;
        try {
            float[][] sample=(float[][])nativeHairSegments.invoke(rig);
            if(sample==null||sample.length!=bones.length)return null;
            for(float[] segment:sample) {
                if(segment==null||segment.length!=7)return null;
                for(float value:segment)if(!Float.isFinite(value))return null;
                if(!(segment[6]>0F && segment[6]<=0.1F))return null;
            }
            return copyMatrices(sample);
        } catch(Throwable error) {
            System.err.println("[BBS Editor][Jolt][Overlay] Native hair endpoints unavailable: "+error);
            return null;
        }
    }

    /** Only a change in count or dimensions recreates Jolt geometry.
     *  Pose changes are handled by moveKinematic each adjacent frame. */
    private static boolean sameBoxGeometry(float[][] now,float[][] old)
    {
        if(now==null || old==null)return now==old;
        if(now.length!=old.length)return false;
        for(int i=0;i<now.length;i++)
        {
            if(now[i]==null || old[i]==null ||
                    now[i].length!=10 || old[i].length!=10)return false;
            for(int axis=7;axis<10;axis++)
                if(Math.abs(now[i][axis]-old[i][axis])>0.02F)
                    return false;
        }
        return true;
    }

    private static boolean compoundShapeChanged(
            float[][] oldA,float[][] oldB,float[] oldRadius,
            float[][] newA,float[][] newB,float[] newRadius) {
        if(oldA==null||oldB==null||oldRadius==null||
                newA==null||newB==null||newRadius==null||
                oldRadius.length!=newRadius.length)return true;
        for(int i=0;i<newRadius.length;i++) {
            if(Math.abs(oldRadius[i]-newRadius[i])>0.022F ||
                    Math.abs(distance(oldA[i],oldB[i])-
                             distance(newA[i],newB[i]))>0.05F)return true;
        }
        return false;
    }

    private Map<String,float[]> collectNativeDirections(
            AutoCloseable rig, String[] names) {
        if(!RAW_DIAGNOSTIC || nativeDirections==null)
            return Collections.emptyMap();
        try {
            float[][] raw=(float[][])nativeDirections.invoke(rig);
            if(raw==null || raw.length!=names.length)
                throw new IllegalArgumentException("Wrong native Jolt segment count");
            Map<String,float[]> result=new LinkedHashMap<String,float[]>();
            for(int i=0;i<raw.length;i++) {
                if(raw[i]==null || raw[i].length!=3)
                    throw new IllegalArgumentException("Wrong native direction size");
                float norm=0F;
                for(float component:raw[i]) {
                    if(!Float.isFinite(component))
                        throw new IllegalArgumentException("Non-finite native direction");
                    norm+=component*component;
                }
                if(norm<0.98F || norm>1.02F)
                    throw new IllegalArgumentException("Unnormalized native direction");
                result.put(names[i],raw[i].clone());
            }
            return result;
        } catch(Throwable error) {
            // Diagnostics can fail independently of a healthy physics scene.
            if(!nativeDirectionWarning) {
                nativeDirectionWarning=true;
                System.err.println("[BBS Editor][Jolt][Raw] "
                        + "Native direction probe unavailable, physics unchanged: "+error);
            }
            return Collections.emptyMap();
        }
    }

    private Map<String,float[]> collect(AutoCloseable rig, String[] names,
            float[][] cleanBones, float[] parentWorld) throws Exception {
        float[][] swings=(float[][])localSwings.invoke(rig,cleanBones,parentWorld);
        if(swings==null || swings.length!=names.length)
            throw new IllegalStateException("Wrong Jolt pose bone count");
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(int i=0;i<swings.length;i++) {
            if(swings[i]==null || swings[i].length!=4)
                throw new IllegalStateException("Invalid Jolt swing");
            for(float value:swings[i])
                if(!Float.isFinite(value))
                    throw new IllegalStateException("Non-finite Jolt swing");
            result.put(names[i],swings[i].clone());
        }
        return result;
    }

    private static float distance(float[] a,float[] b) {
        if(a==null||b==null||a.length!=3||b.length!=3)
            return Float.NaN;
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }

    private static float[][] copyMatrices(float[][] source) {
        if(source==null)return null;
        float[][] out=new float[source.length][];
        for(int i=0;i<source.length;i++)
            out[i]=source[i]==null?null:source[i].clone();
        return out;
    }

    private static boolean sameMatrices(float[][] x, float[][] y) {
        if(x==null || y==null || x.length!=y.length)return false;
        for(int i=0;i<x.length;i++)
            if(!closeEnough(x[i],y[i]))return false;
        return true;
    }

    private static boolean closeEnough(float[] x, float[] y) {
        if(x==null || y==null || x.length!=y.length)return false;
        for(int i=0;i<x.length;i++)
            if(Math.abs(x[i]-y[i])>1E-5F)return false;
        return true;
    }

    private static Map<String,float[]> copy(Map<String,float[]> source) {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,float[]> e:source.entrySet())
            result.put(e.getKey(),e.getValue().clone());
        return result;
    }

    private void report(String id, Throwable failure) {
        Throwable cause=failure.getCause()==null?failure:failure.getCause();
        System.err.println("[BBS Editor][Jolt] Scene '"+id+
                "' unsupported/failed; no preview physics fallback: "+cause);
    }

    private void dispose(String id) {
        State removed=scenes.remove(id);
        if(removed!=null)try {removed.rig.close();}
        catch(Exception error) {
            System.err.println("[BBS Editor][Jolt] Scene cleanup failed: "+error);
        }
    }

    /** Call when leaving the editor or replacing the scene. */
    public void clear() {
        for(String id:new java.util.ArrayList<String>(scenes.keySet()))
            dispose(id);
    }
}
