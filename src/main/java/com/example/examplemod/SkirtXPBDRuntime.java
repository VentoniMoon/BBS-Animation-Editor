package com.example.examplemod;

import java.util.WeakHashMap;

/**
 * Physics 3.0 owner-aware XPBD runtime. Each editor BodyPartModelData
 * attachment track owns its cloth across short-lived ChameleonMorph clones;
 * other standalone morphs use their own identity. Never key by shared
 * 44-bone model name. Existing Jolt skirt/record/keys are never edited.
 *
 * A real editor timeline frame from fitted Emoticons colliders is preferred
 * to wall time so 60fps camera render / repeated shadow passes cannot
 * advance the same 20 TPS cloth sample more than once. Without a bridge
 * sample (external Blockbuster use), wall time is rate-limited to <=50Hz.
 */
public final class SkirtXPBDRuntime
{
    private static final WeakHashMap<Object,Actor> ACTORS=
            new WeakHashMap<Object,Actor>();
    public static final boolean ENABLED=Boolean.getBoolean(
            "bbs.editor.physics.skirt.xpbd");
    public static final boolean REPLACE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdReplace","true"));

    private static final long MIN_STEP_NANOS=18000000L;
    private static final long STALE_RESET_NANOS=600000000L;
    private static final float MAX_REST_DELTA=.025F;

    public static final class RenderFrame
    {
        public final SkirtClothSurfaceMesh.Snapshot source;
        public final SkirtClothRestBinding.Binding binding;
        public final SkirtXPBDSurfaceTopology.Topology topology;
        public final SkirtXPBDPoseSkin.Frame pose;
        public final SkirtXPBDSolver.Result solved;
        public final float[][] materials;
        public final int legBoxCount;
        private RenderFrame(SkirtClothSurfaceMesh.Snapshot source,
                SkirtClothRestBinding.Binding binding,
                SkirtXPBDSurfaceTopology.Topology topology,
                SkirtXPBDPoseSkin.Frame pose,
                SkirtXPBDSolver.Result solved,float[][] materials,
                int legBoxCount)
        {
            this.source=source;this.binding=binding;
            this.topology=topology;this.pose=pose;this.solved=solved;
            this.materials=materials;
            this.legBoxCount=legBoxCount;
        }
    }
    private static final class Actor
    {
        SkirtXPBDSolver.State solver;
        SkirtClothSharedSurface.Surface rest;
        String fingerprint;
        long lastUpdate;
        int lastTimelineFrame=Integer.MIN_VALUE;
        float[][] lastParticles;
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
            actor.lastTimelineFrame=Integer.MIN_VALUE;
            actor.lastWorldOrigin=null;
        }

        float dt=0F;
        if(collisions!=null)
        {
            int frame=collisions.timelineFrame;
            if(actor.lastTimelineFrame==Integer.MIN_VALUE)
                dt=1F/20F;
            else if(frame>actor.lastTimelineFrame&&
                    frame-actor.lastTimelineFrame<=2)
                dt=Math.min(.05F,(frame-actor.lastTimelineFrame)/20F);
            else if(frame!=actor.lastTimelineFrame)
            {
                actor.solver=SkirtXPBDSolver.create(raw,shared,posed);
                actor.lastWorldOrigin=null;
            }
            actor.lastTimelineFrame=frame;
            if(dt>0F)
            {
                // Follow actor-world locomotion with bounded inertial lag
                // rather than relying only on local Chameleon bone edits.
                float[] current=new float[]{
                        collisions.modelToWorld[12],
                        collisions.modelToWorld[13],
                        collisions.modelToWorld[14]};
                if(actor.lastWorldOrigin!=null)
                {
                    float[] delta=new float[3];
                    for(int i=0;i<3;i++)
                        delta[i]=current[i]-actor.lastWorldOrigin[i];
                    actor.solver.rootMotion(delta,collisions.modelToWorld);
                }
                actor.lastWorldOrigin=current;
            }
        }
        else
        {
            // No fitted body contacts: still simulate gravity + cloth
            // self-collision, but do NOT fabricate model-derived leg boxes.
            if(now-actor.lastUpdate>=MIN_STEP_NANOS)
                dt=Math.min(.03F,(now-actor.lastUpdate)/1000000000F);
        }
        // Even a paused timeline/render replay must keep the 20 waist
        // anchors on the CURRENT bone pose. advance(0) does not step
        // gravity or collisions, but updates fixed pins safely.
        SkirtXPBDSolver.Result output=
                actor.solver.advance(dt,posed,collisions);
        if(dt>0F)actor.lastUpdate=now;
        return new RenderFrame(raw,binding,topology,posed,output,
                materials,collisions==null?0:collisions.legBoxes.size());
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
