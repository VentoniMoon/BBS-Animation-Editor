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
    /**
     * Read-only export/preview cloth clock diagnostics. Default OFF.
     * Enable only during short comparison captures:
     * -Dbbs.editor.physics.skirt.xpbdClockTrace=true
     */
    private static final boolean CLOCK_TRACE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdClockTrace","false"));
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
        /** Both real upper thighs bent: 0 walking, 1 deep seated. */
        public final float seatedBlend;
        /** Number of fixed 60Hz integration steps in this render sample. */
        public final int physicsSteps;
        public final double sampleTick;
        private RenderFrame(SkirtClothSurfaceMesh.Snapshot source,
                SkirtClothRestBinding.Binding binding,
                SkirtXPBDSurfaceTopology.Topology topology,
                SkirtXPBDPoseSkin.Frame pose,
                SkirtXPBDSolver.Result solved,float[][] materials,
                int legBoxCount,float seated,int steps,double tick)
        {
            this.source=source;this.binding=binding;
            this.topology=topology;this.pose=pose;this.solved=solved;
            this.materials=materials;
            this.legBoxCount=legBoxCount;
            seatedBlend=seated;
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
        float[][] lastPose;
        float[][] lastLegBoxes;
        float[] lastModelToWorld;
        float lastSeatedBlend;
        long lastTraceSlice=Long.MIN_VALUE;
        long lastSkinSurfaceFingerprint;
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
        long wallGapMs=actor==null?-1L:
                Math.max(0L,(now-actor.lastUpdate)/1000000L);
        String resetCause="NONE";
        String signature=signature(raw,shared);
        boolean fresh=actor==null||!signature.equals(actor.fingerprint);
        if(fresh)
        {
            resetCause=actor==null?"NEW":"MODEL_SIGNATURE";
            actor=new Actor();
            actor.fingerprint=signature;
            actor.rest=shared;
            actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
            actor.lastUpdate=now;
            ACTORS.put(stableOwner,actor);
        }
        else if(now-actor.lastUpdate>STALE_RESET_NANOS)
        {
            resetCause="STALE_WALL_CLOCK";
            // Scrubbing, a long pause, actor deletion/recreate, menu freeze:
            // reset rather than applying a giant unbounded gravity impulse.
            actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
            actor.lastUpdate=now;
            actor.lastSampleTick=Double.NaN;
            actor.pendingSeconds=0D;
            actor.lastWorldOrigin=null;
            actor.lastPose=null;
            actor.lastLegBoxes=null;
            actor.lastModelToWorld=null;
            actor.lastSkinSurfaceFingerprint=0L;
        }

        // Same scene tick, same pose and same fitted collision hull must
        // yield the EXACT same cloth shape on every preview/shadow/mirror
        // pass. Calling advance(0) repeatedly was re-running contact and
        // stretch projection until it alternated between two silhouettes.
        // A pose edit or a changed leg collider at the same tick is still
        // processed once, preserving scrubbing and interactive editing.
        if(collisions!=null&&Double.isFinite(actor.lastSampleTick)&&
                Math.abs(actor.lastSampleTick-collisions.sampleTick)<=CLOCK_EPS&&
                samePose(actor.lastPose,posed.target)&&
                samePose(actor.lastLegBoxes,collisions.legBoxes)&&
                samePose(actor.lastModelToWorld,collisions.modelToWorld)&&
                Math.abs(actor.lastSeatedBlend-collisions.seatedBlend)<1E-5F&&
                actor.lastSkinSurfaceFingerprint==
                        (collisions.skinSurface==null?0L:
                                collisions.skinSurface.fingerprint))
        {
            actor.lastUpdate=now;
            return new RenderFrame(raw,binding,topology,posed,
                    actor.solver.current(),materials,
                    collisions.legBoxes.size(),collisions.seatedBlend,
                    0,collisions.sampleTick);
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
                    resetCause="SCENE_TIME_SEEK";
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
        // This is a LAST VISIBLE SAMPLE timestamp, not a last integration
        // timestamp: a paused animation renders at 0 Hz physics but should
        // never be reset every 600ms while still visible.
        actor.lastUpdate=now;
        if(collisions!=null)
        {
            actor.lastPose=copy(posed.target);
            actor.lastLegBoxes=copy(collisions.legBoxes);
            actor.lastModelToWorld=collisions.modelToWorld.clone();
            actor.lastSeatedBlend=collisions.seatedBlend;
            actor.lastSkinSurfaceFingerprint=
                    collisions.skinSurface==null?0L:
                            collisions.skinSurface.fingerprint;
        }
        if(CLOCK_TRACE&&collisions!=null&&output!=null)
            logClockTrace(actor,stableOwner,collisions,posed,output,
                    steps,renderTick,wallGapMs,resetCause);
        return new RenderFrame(raw,binding,topology,posed,output,
                materials,collisions==null?0:collisions.legBoxes.size(),
                collisions==null?0F:collisions.seatedBlend,
                steps,renderTick);
    }

    /**
     * Compare physical geometry, native animation target and clock/reset
     * behavior using only final render samples (never mutate the solver).
     * The same format is emitted for preview and offline export.
     * A healthy unclipped skirt has a nonzero waist-to-hem span. When the
     * VISUAL hem remains at the waist but simulatedSpan is normal, the
     * defect is in the volume/vertex skinning pass, not in XPBD nodes.
     */
    private static void logClockTrace(Actor actor,Object owner,
            SkirtXPBDContacts.Frame contacts,SkirtXPBDPoseSkin.Frame pose,
            SkirtXPBDSolver.Result result,int steps,double sceneTick,
            long wallGapMs,String resetCause)
    {
        // At 20TPS, six slices correspond to ~100 ms. Log every reset
        // even when outside the scheduled slice so stalls are not hidden.
        long slice=Math.round(sceneTick*3D);
        boolean event=!"NONE".equals(resetCause);
        if(!event&&(slice%6L!=0L||actor.lastTraceSlice==slice))
            return;
        actor.lastTraceSlice=slice;
        if(result.particles.length<60||pose.target.length<60)return;
        float nativeSpan=0F,simSpan=0F,nativeVertical=0F,simVertical=0F;
        float maxSimY=Float.NEGATIVE_INFINITY;
        for(int c=0;c<20;c++)
        {
            float[] root=result.particles[c],hem=result.particles[40+c];
            float[] a=pose.target[c],b=pose.target[40+c];
            float ndx=a[0]-b[0],ndy=a[1]-b[1],ndz=a[2]-b[2];
            float sdx=root[0]-hem[0],sdy=root[1]-hem[1],
                    sdz=root[2]-hem[2];
            nativeSpan+=(float)Math.sqrt(ndx*ndx+ndy*ndy+ndz*ndz);
            simSpan+=(float)Math.sqrt(sdx*sdx+sdy*sdy+sdz*sdz);
            nativeVertical+=Math.abs(ndy);
            simVertical+=Math.abs(sdy);
            maxSimY=Math.max(maxSimY,hem[1]);
        }
        // Do not introduce dependencies from this Minecraft-free Java 8
        // cloth core to the camera export module. The reflective read is
        // diagnostic-only and never changes physics or timing.
        Integer filmFrame=exportFrameOrNull();
        String mode=filmFrame==null?"PREVIEW":"EXPORT";
        String frame=filmFrame==null?"-":filmFrame.toString();
        System.out.println("[BBS Editor][SkirtClockTrace] "+
                "mode="+mode+" outputFrame="+frame+
                " owner="+Integer.toHexString(System.identityHashCode(owner))+
                " tick="+round3(sceneTick)+
                " wallGapMs="+wallGapMs+
                " reset="+resetCause+
                " steps="+steps+
                " seated="+round3(contacts.seatedBlend)+
                " obbs="+contacts.legBoxes.size()+
                " nativeSpan="+round3(nativeSpan/20F)+
                " solvedSpan="+round3(simSpan/20F)+
                " nativeHeight="+round3(nativeVertical/20F)+
                " solvedHeight="+round3(simVertical/20F)+
                " legContacts="+result.legContacts+
                " selfContacts="+result.selfContacts+
                " maxLinkError="+round3(result.maxDistanceError));
    }

    private static Integer exportFrameOrNull()
    {
        try
        {
            Class<?> clock=Class.forName(
                    "com.example.examplemod.CameraExportRenderClock");
            Object sample=clock.getMethod("current").invoke(null);
            return sample==null?null:(Integer)sample.getClass()
                    .getField("outputFrame").get(sample);
        }
        catch(ReflectiveOperationException missingCameraRuntime)
        {
            // Java 8 pure cloth tests have no camera export classes.
            return null;
        }
    }

    private static float round3(double v)
    {
        return (float)(Math.round(v*1000D)/1000D);
    }

    private static boolean samePose(float[] a,float[] b)
    {
        if(a==null||b==null||a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
            if(!Float.isFinite(a[i])||!Float.isFinite(b[i])||
                    Math.abs(a[i]-b[i])>1E-5F)return false;
        return true;
    }

    private static boolean samePose(float[][] a,float[][] b)
    {
        if(a==null||b==null||a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
            if(!samePose(a[i],b[i]))return false;
        return true;
    }

    private static boolean samePose(float[][] a,
            java.util.List<float[]> b)
    {
        if(a==null||b==null||a.length!=b.size())return false;
        for(int i=0;i<a.length;i++)
            if(!samePose(a[i],b.get(i)))return false;
        return true;
    }

    private static float[][] copy(float[][] a)
    {
        float[][] out=new float[a.length][];
        for(int i=0;i<a.length;i++)out[i]=a[i].clone();
        return out;
    }

    private static float[][] copy(java.util.List<float[]> a)
    {
        float[][] out=new float[a.size()][];
        for(int i=0;i<a.size();i++)out[i]=a.get(i).clone();
        return out;
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
