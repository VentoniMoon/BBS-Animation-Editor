package com.example.examplemod;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless 20Hz Jolt recorder for later .dat baking.
 *
 * The caller supplies the COMPLETE, clean world transform of every animated
 * bone, parent pivot, hair tip and passive collider for every Record tick.
 * If a frame is not available this class fails instead of simulating the
 * target pose repeatedly and calling that a deterministic seek.
 *
 * This is NOT the live Preview renderer and never changes Character,
 * Pose, Body Parts or Blockbuster keyframes. A future Forge bridge can
 * supply historical Chameleon render/pose samples from the actor Record.
 */
public final class JoltPhysicsOfflineRunner implements AutoCloseable
{
    public interface InputSource
    {
        FrameInput at(int frame);
    }

    public static final class FrameInput
    {
        public final String[] bones;
        public final float[][] boneWorld;
        public final float[] terminalTip,parentWorld;
        public final float stiffness,damping,gravity;
        public final float[] torsoA,torsoB;
        public final float torsoRadius;
        public final float[][] colliders,shapes;

        public FrameInput(String[] bones,float[][] boneWorld,
                float[] terminalTip,float[] parentWorld,
                float stiffness,float damping,float gravity,
                float[] torsoA,float[] torsoB,float torsoRadius,
                float[][] colliders,float[][] shapes)
        {
            if(bones==null||boneWorld==null||bones.length==0||
                    bones.length!=boneWorld.length||
                    terminalTip==null||terminalTip.length!=3||
                    parentWorld==null||parentWorld.length!=16)
                throw new IllegalArgumentException("Missing clean Jolt frame inputs");
            this.bones=bones.clone();
            this.boneWorld=copyArray(boneWorld);
            this.terminalTip=terminalTip.clone();
            this.parentWorld=parentWorld.clone();
            this.stiffness=stiffness;
            this.damping=damping;
            this.gravity=gravity;
            this.torsoA=torsoA==null?null:torsoA.clone();
            this.torsoB=torsoB==null?null:torsoB.clone();
            this.torsoRadius=torsoRadius;
            this.colliders=copyArray(colliders);
            this.shapes=copyArray(shapes);
        }
    }

    private final String modelId;
    private final File gameDir;
    private final BodyPartJoltPreviewAdapter bridge=new BodyPartJoltPreviewAdapter();
    private final JoltPhysicsTimeline<FrameInput,Map<String,float[]>> history;

    public JoltPhysicsOfflineRunner(File gameDir,String modelId,
            int startFrame,int frameBudget,final InputSource source)
    {
        if(gameDir==null || modelId==null || modelId.isEmpty() || source==null)
            throw new IllegalArgumentException("Missing offline Jolt setup");
        this.gameDir=gameDir;
        this.modelId=modelId;
        this.history=new JoltPhysicsTimeline<FrameInput,Map<String,float[]>>(
                startFrame,frameBudget,
                new JoltPhysicsTimeline.FrameSource<FrameInput>()
                {
                    @Override public FrameInput inputAt(int frame)
                    {
                        return source.at(frame);
                    }
                },
                new JoltPhysicsTimeline.Solver<FrameInput,Map<String,float[]>>()
                {
                    @Override public Map<String,float[]> initialize(
                            int frame,FrameInput input)
                    {
                        bridge.invalidate(JoltPhysicsOfflineRunner.this.modelId);
                        return tick(frame,input);
                    }
                    @Override public Map<String,float[]> advance(
                            int frame,FrameInput input)
                    {
                        return tick(frame,input);
                    }
                    @Override public void reset()
                    {
                        bridge.invalidate(JoltPhysicsOfflineRunner.this.modelId);
                    }
                },
                new JoltPhysicsTimeline.Copier<Map<String,float[]>>()
                {
                    @Override public Map<String,float[]> copy(Map<String,float[]> map)
                    {
                        return copyPose(map);
                    }
                });
    }

    private Map<String,float[]> tick(int frame,FrameInput input)
    {
        Map<String,float[]> sample=bridge.sample(
                gameDir,modelId,input.bones,frame,input.boneWorld,
                input.terminalTip,input.parentWorld,input.stiffness,
                input.damping,input.gravity,input.torsoA,input.torsoB,
                input.torsoRadius,null,input.colliders,input.shapes);
        if(sample==null || sample.size()!=input.bones.length)
            throw new IllegalStateException(
                    "Native Jolt could not evaluate recorded frame "+frame);
        return copyPose(sample);
    }

    /** A source revision must change after edits to keys, model, or colliders. */
    public Map<String,float[]> evaluate(int frame,long sourceRevision)
    {
        return history.evaluate(frame,sourceRevision);
    }

    public int cachedFrames() {return history.cachedFrames();}

    public Map<Integer,Map<String,float[]>> recordedFrames()
    {
        return history.snapshot();
    }

    /** Changes to BodyPart geometry/source poses must invalidate Jolt too. */
    public void invalidate() {history.invalidate();}

    @Override public void close()
    {
        history.close();
        bridge.clear();
    }

    private static float[][] copyArray(float[][] a)
    {
        if(a==null)return null;
        float[][] result=new float[a.length][];
        for(int i=0;i<a.length;i++)
            result[i]=a[i]==null?null:a[i].clone();
        return result;
    }

    private static Map<String,float[]> copyPose(Map<String,float[]> pose)
    {
        if(pose==null)throw new IllegalArgumentException("Missing Jolt result");
        Map<String,float[]> out=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,float[]> entry:pose.entrySet())
            out.put(entry.getKey(),entry.getValue().clone());
        return out;
    }
}
