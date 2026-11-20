package com.example.examplemod;

import java.util.WeakHashMap;

/**
 * Physics 3.0 owner-aware XPBD runtime. Each editor BodyPartModelData
 * attachment track owns its cloth across short-lived ChameleonMorph clones;
 * other standalone morphs use their own identity. Never key by shared
 * 44-bone model name. Existing Jolt skirt/record/keys are never edited.
 *
 * Fractional editor scene time from fitted Emoticons colliders advances a
 * fixed 60Hz simulation. Repeated shadow/mirror/video passes at the same
 * exact scene time NEVER advance a second step. Without editor contacts
 * (external Blockbuster use), wall time is rate-limited to <=50Hz.
 */
public final class SkirtXPBDRuntime
{
    private static final WeakHashMap<Object,Actor> ACTORS=
            new WeakHashMap<Object,Actor>();
    // Cloth activation is selected per Body Part in the editor. The
    // dev-only -D flag must not be required by an installed Forge JAR.
    // Explicit -Dbbs.editor.physics.skirt.xpbd=false still opts out.
    public static final boolean ENABLED=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbd","true"));
    public static final boolean REPLACE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdReplace","true"));

    private static final long MIN_STEP_NANOS=18000000L;
    private static final long STALE_RESET_NANOS=600000000L;
    private static final float MAX_REST_DELTA=.025F;
    private static final double STEP_SECONDS=1D/60D;
    private static final int MAX_STEPS_PER_SAMPLE=6;
    // Only allocate extra physical steps when a nominal 1/60 sample
    // sweeps farther than 0.20 model units, never for a normal walk.
    private static final float MAX_MOTION_PER_STEP=.20F;
    private static final int MAX_MOTION_SUBSTEPS=3;
    private static final double CLOCK_EPS=1E-6D;

    public static final class RenderFrame
    {
        public final SkirtClothSurfaceMesh.Snapshot source;
        public final SkirtClothRestBinding.Binding binding;
        public final SkirtXPBDSurfaceTopology.Topology topology;
        public final SkirtXPBDPoseSkin.Frame pose;
        public final SkirtXPBDSolver.Result solved;
        public final float[][] materials;
        public final int legBoxCount;
        /** Number of fixed 60Hz integration steps in this render sample. */
        public final int physicsSteps;
        /** Adaptive solver calls within the nominal 60Hz clock steps. */
        public final int motionSubsteps;
        public final double sampleTick;
        private RenderFrame(SkirtClothSurfaceMesh.Snapshot source,
                SkirtClothRestBinding.Binding binding,
                SkirtXPBDSurfaceTopology.Topology topology,
                SkirtXPBDPoseSkin.Frame pose,
                SkirtXPBDSolver.Result solved,float[][] materials,
                int legBoxCount,int steps,int substeps,double tick)
        {
            this.source=source;this.binding=binding;
            this.topology=topology;this.pose=pose;this.solved=solved;
            this.materials=materials;
            this.legBoxCount=legBoxCount;
            physicsSteps=steps;
            motionSubsteps=substeps;
            sampleTick=tick;
        }
    }
    private static final class Actor
    {
        SkirtXPBDSolver.State solver;
        SkirtClothSharedSurface.Surface rest;
        String fingerprint;
        long lastUpdate;
        double lastSampleTick=Double.NaN;
        double pendingSeconds;
        float[] lastWorldOrigin;
        // Previous *presented* actor sample, not the last Jolt/XPBD pose:
        // 20 TPS animation samples need real intermediate geometry for
        // each 60Hz step. These are immutable per-owner snapshots.
        SkirtXPBDPoseSkin.Frame lastPose;
        SkirtXPBDContacts.Frame lastContacts;
    }

    private SkirtXPBDRuntime(){}

    public static synchronized RenderFrame sample(Object morph,
            SkirtClothSurfaceMesh.Snapshot raw,
            SkirtClothSharedSurface.Surface shared,
            SkirtClothRestBinding.Binding binding,
            SkirtXPBDSurfaceTopology.Topology topology,
            SkirtXPBDPoseSkin.Frame posed,float[][] materials,
            SkirtXPBDContacts.Frame collisions)
    {
        if(!ENABLED||morph==null||raw==null||shared==null||
                binding==null||topology==null||posed==null||
                materials==null||materials.length!=40||
                shared.nodes.size()!=60)
            return null;
        long now=System.nanoTime();
        Object stableOwner=SkirtXPBDContacts.stableOwner(morph);
        if(!SkirtXPBDContacts.isClothEnabled(morph))
        {
            // Disabling CLOTH must not continue a previously running XPBD
            // state, and must not touch Jolt hair rigs on other models.
            ACTORS.remove(stableOwner);
            return null;
        }
        Actor actor=ACTORS.get(stableOwner);
        String signature=signature(raw,shared);
        boolean fresh=actor==null||!signature.equals(actor.fingerprint);
        if(fresh)
        {
            actor=new Actor();
            actor.fingerprint=signature;
            actor.rest=shared;
            actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
            actor.lastUpdate=now;
            ACTORS.put(stableOwner,actor);
        }
        else if(now-actor.lastUpdate>STALE_RESET_NANOS)
        {
            // Scrubbing, a long pause, actor deletion/recreate, menu freeze:
            // reset rather than applying a giant unbounded gravity impulse.
            actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
            actor.lastUpdate=now;
            actor.lastSampleTick=Double.NaN;
            actor.pendingSeconds=0D;
            actor.lastWorldOrigin=null;
            actor.lastPose=null;
            actor.lastContacts=null;
        }

        SkirtXPBDSolver.Result output=null;
        int steps=0;
        int motionSubsteps=0;
        double renderTick=Double.NaN;
        if(collisions!=null)
        {
            renderTick=collisions.sampleTick;
            // Pending time existed BEFORE receiving this new animation
            // pose. It determines where the first 1/60 boundary lies
            // between the last presented pose and the new one.
            double queuedBefore=actor.pendingSeconds;
            double elapsedSeconds=0D;
            if(!Double.isFinite(actor.lastSampleTick))
            {
                // A fresh character gets one first visible integration,
                // without guessing a preceding unobserved animation pose.
                actor.pendingSeconds=STEP_SECONDS;
                queuedBefore=0D;
            }
            else
            {
                double elapsedTicks=renderTick-actor.lastSampleTick;
                if(elapsedTicks>2D+CLOCK_EPS ||
                        elapsedTicks< -CLOCK_EPS)
                {
                    // Seek/rewind/teleport: don't interpolate across a
                    // discontinuity or carry the old collider poses.
                    actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
                    actor.lastWorldOrigin=null;
                    actor.lastPose=null;
                    actor.lastContacts=null;
                    actor.pendingSeconds=0D;
                    queuedBefore=0D;
                }
                else if(elapsedTicks>CLOCK_EPS)
                {
                    elapsedSeconds=elapsedTicks/20D;
                    actor.pendingSeconds+=elapsedSeconds;
                }
            }
            actor.lastSampleTick=renderTick;

            SkirtXPBDPoseSkin.Frame previousPose=actor.lastPose;
            SkirtXPBDContacts.Frame previousBodies=actor.lastContacts;
            SkirtXPBDPoseSkin.Frame startPose=
                    previousPose==null?posed:previousPose;
            SkirtXPBDContacts.Frame startBodies=
                    previousBodies==null?collisions:previousBodies;
            SkirtXPBDPoseSkin.Frame integratedPose=startPose;
            SkirtXPBDContacts.Frame integratedBodies=startBodies;
            float lastAlpha=0F;

            // Exactly three nominal 1/60 steps per 1/20 editor tick,
            // irrespective of export/render FPS. But every step gets an
            // actual *interpolated* source pose AND fitted thigh OBBs.
            while(actor.pendingSeconds+1E-8D>=STEP_SECONDS &&
                    steps<MAX_STEPS_PER_SAMPLE)
            {
                double elapsedBoundary=(steps+1)*STEP_SECONDS-queuedBefore;
                float alpha=elapsedSeconds>CLOCK_EPS?
                        (float)Math.max(0D,Math.min(1D,
                                elapsedBoundary/elapsedSeconds)):1F;
                SkirtXPBDPoseSkin.Frame nextPose=
                        SkirtXPBDPoseSkin.between(startPose,posed,alpha);
                SkirtXPBDContacts.Frame nextBodies=
                        SkirtXPBDContacts.between(startBodies,collisions,alpha);

                // Very fast kicks can cross a collider between two 60Hz
                // samples. Up to three additional *time-divided* updates
                // resolve intermediate thigh positions, without changing
                // total physical time or the ordinary-walk stiffness.
                float travel=Math.max(
                        SkirtXPBDPoseSkin.maxTargetMotion(integratedPose,nextPose),
                        SkirtXPBDContacts.maxMotion(integratedBodies,nextBodies));
                int subdivisions=Math.max(1,Math.min(MAX_MOTION_SUBSTEPS,
                        (int)Math.ceil(travel/MAX_MOTION_PER_STEP)));
                for(int micro=1;micro<=subdivisions;micro++)
                {
                    float mix=micro/(float)subdivisions;
                    SkirtXPBDPoseSkin.Frame poseStep=SkirtXPBDPoseSkin.between(
                            integratedPose,nextPose,mix);
                    SkirtXPBDContacts.Frame bodyStep=SkirtXPBDContacts.between(
                            integratedBodies,nextBodies,mix);
                    if(bodyStep!=null)
                    {
                        float[] origin={
                                bodyStep.modelToWorld[12],
                                bodyStep.modelToWorld[13],
                                bodyStep.modelToWorld[14]};
                        if(actor.lastWorldOrigin!=null)
                        {
                            float[] delta=new float[3];
                            for(int i=0;i<3;i++)
                                delta[i]=origin[i]-actor.lastWorldOrigin[i];
                            actor.solver.rootMotion(delta,bodyStep.modelToWorld);
                        }
                        actor.lastWorldOrigin=origin;
                    }
                    output=actor.solver.advance(
                            (float)(STEP_SECONDS/subdivisions),poseStep,bodyStep);
                    motionSubsteps++;
                }
                integratedPose=nextPose;
                integratedBodies=nextBodies;
                lastAlpha=alpha;
                actor.pendingSeconds-=STEP_SECONDS;
                steps++;
            }
            if(actor.pendingSeconds<0D)actor.pendingSeconds=0D;

            // At >60 FPS, or a render time that falls BETWEEN fixed 60Hz
            // steps, the cloth still needs the EXACT displayed native pose.
            // dt=0 may adjust penetration, but does not add gravity or a
            // second physical tick during mirrors, shadows and export.
            if(steps==0||lastAlpha<1F-1E-5F)
                output=actor.solver.advance(0F,posed,collisions);
            actor.lastPose=posed;
            actor.lastContacts=collisions;
        }
        else
        {
            // External morph without real Emoticons thigh fitting:
            // keep old rate-limited, wall-clock gravity-only behavior.
            float dt=0F;
            if(now-actor.lastUpdate>=MIN_STEP_NANOS)
                dt=Math.min(.03F,(now-actor.lastUpdate)/1000000000F);
            output=actor.solver.advance(dt,posed,null);
            steps=dt>0F?1:0;
            motionSubsteps=steps;
            actor.lastPose=null;
            actor.lastContacts=null;
        }
        if(steps>0)actor.lastUpdate=now;
        return new RenderFrame(raw,binding,topology,posed,output,
                materials,collisions==null?0:collisions.legBoxes.size(),
                steps,motionSubsteps,renderTick);
    }

    private static String signature(SkirtClothSurfaceMesh.Snapshot raw,
            SkirtClothSharedSurface.Surface shared)
    {
        StringBuilder s=new StringBuilder(256);
        for(SkirtClothSurfaceMesh.Patch patch:raw.patches)
            s.append(patch.bone).append(',');
        // Fixed rest topology must not silently be reused with a new
        // actor model/scale. Avoid per-frame pose (which may animate).
        for(SkirtClothSharedSurface.Node node:shared.nodes)
            for(float x:node.rest)
                s.append(Math.round(x/MAX_REST_DELTA)).append(',');
        return s.toString();
    }
}
