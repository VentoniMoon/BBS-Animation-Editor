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
    // Enable the production test cloth by default in mod JAR launches.
    // Java/Gradle opt-out remains available for baseline comparison.
    public static final boolean ENABLED=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbd","true"));
    public static final boolean REPLACE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdReplace","true"));

    private static final long MIN_STEP_NANOS=18000000L;
    private static final long STALE_RESET_NANOS=600000000L;
    private static final float MAX_REST_DELTA=.025F;
    private static final double STEP_SECONDS=1D/60D;
    private static final int MAX_STEPS_PER_SAMPLE=6;
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
        public final double sampleTick;
        private RenderFrame(SkirtClothSurfaceMesh.Snapshot source,
                SkirtClothRestBinding.Binding binding,
                SkirtXPBDSurfaceTopology.Topology topology,
                SkirtXPBDPoseSkin.Frame pose,
                SkirtXPBDSolver.Result solved,float[][] materials,
                int legBoxCount,int steps,double tick)
        {
            this.source=source;this.binding=binding;
            this.topology=topology;this.pose=pose;this.solved=solved;
            this.materials=materials;
            this.legBoxCount=legBoxCount;
            physicsSteps=steps;
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
        }

        SkirtXPBDSolver.Result output=null;
        int steps=0;
        double renderTick=Double.NaN;
        if(collisions!=null)
        {
            renderTick=collisions.sampleTick;
            if(!Double.isFinite(actor.lastSampleTick))
            {
                // A new actor must be simulated at least once before the
                // first visible render. Subsequently use only scene time.
                actor.pendingSeconds=STEP_SECONDS;
            }
            else
            {
                double elapsedTicks=renderTick-actor.lastSampleTick;
                if(elapsedTicks>2D+CLOCK_EPS ||
                        elapsedTicks< -CLOCK_EPS)
                {
                    // Seek/reverse/large stall: never replay old impulses
                    // against a newly posed character or fitted leg set.
                    actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
                    actor.lastWorldOrigin=null;
                    actor.pendingSeconds=0D;
                }
                else if(elapsedTicks>CLOCK_EPS)
                    actor.pendingSeconds+=elapsedTicks/20D;
            }
            actor.lastSampleTick=renderTick;

            // Exactly three 1/60 steps per Minecraft 1/20 tick, with
            // fractional render clock delivery. This is independent of
            // the GPU's rendering FPS and is stable in export/shadow passes.
            while(actor.pendingSeconds+1E-8D>=STEP_SECONDS &&
                    steps<MAX_STEPS_PER_SAMPLE)
            {
                // Actor-root inertial lag runs once for this sample even
                // when we need multiple fixed simulation catch-up steps.
                if(steps==0)
                {
                    float[] current={
                            collisions.modelToWorld[12],
                            collisions.modelToWorld[13],
                            collisions.modelToWorld[14]};
                    if(actor.lastWorldOrigin!=null)
                    {
                        float[] delta=new float[3];
                        for(int i=0;i<3;i++)
                            delta[i]=current[i]-actor.lastWorldOrigin[i];
                        actor.solver.rootMotion(delta,
                                collisions.modelToWorld);
                    }
                    actor.lastWorldOrigin=current;
                }
                output=actor.solver.advance((float)STEP_SECONDS,
                        posed,collisions);
                actor.pendingSeconds-=STEP_SECONDS;
                steps++;
            }
            if(actor.pendingSeconds<0D)actor.pendingSeconds=0D;
            // On 60+fps views between fixed simulation samples, the
            // source pose may already differ. Resolve penetration against
            // the latest real fitted thigh boxes without adding gravity,
            // and without stepping a second time on a shadow/mirror pass.
            if(steps==0)
                output=actor.solver.advance(0F,posed,collisions);
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
        }
        if(steps>0)actor.lastUpdate=now;
        return new RenderFrame(raw,binding,topology,posed,output,
                materials,collisions==null?0:collisions.legBoxes.size(),
                steps,renderTick);
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
