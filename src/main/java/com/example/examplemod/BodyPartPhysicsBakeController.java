package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import mchorse.mclib.utils.keyframes.KeyframeInterpolation;

/**
 * Experimental, opt-in Body Parts/Jolt bake buffer. Collects the ACTUAL
 * Chameleon local pose visible after applying the Jolt swing, with one
 * snapshot per timeline tick. No keys are changed until commit().
 *
 * Capture is driven by normal preview rendering; no extra Jolt step is
 * performed here. Gaps, missing bones and non-finite rotations reject the
 * entire bake instead of silently exporting a broken animation.
 */
public final class BodyPartPhysicsBakeController
{
    private BodyPartModelData target;
    private int requestedStart;
    private final TreeMap<Integer, Map<String,AnimationTransform>> frames =
            new TreeMap<Integer, Map<String,AnimationTransform>>();
    private String failure;
    private int waitingFrame=Integer.MIN_VALUE;
    private int waitTicks;

    public boolean isRecording() { return target!=null; }
    public int frameCount() { return frames.size(); }
    public String failure() { return failure; }

    /** Lockstep bake playback waits for a completed native pose of this tick. */
    public boolean hasCapturedFrame(int frame)
    {
        Map<String,AnimationTransform> sample=frames.get(frame);
        return sample!=null && !sample.isEmpty();
    }

    /** A recording can stop once its last active Body Part frame is captured. */
    public int getLastRecordableFrame()
    {
        return target==null ? -1 : target.getEndFrame();
    }

    /**
     * Return true if lockstep playback has stalled for ten seconds of
     * Minecraft update ticks without a completed Jolt pose. Fail explicitly
     * rather than leave the Timeline playing at the same frame forever.
     */
    public boolean hasTimedOutWaitingFor(int frame)
    {
        if(target==null)return false;
        if(failure!=null)return true;
        if(waitingFrame!=frame)
        {
            waitingFrame=frame;
            waitTicks=0;
        }
        if(++waitTicks<200)return false;
        failure="No completed Jolt pose for frame "+frame+
                " after 10 seconds. Check preview visibility, physics "+
                "chain settings and [Jolt] errors in latest.log.";
        System.err.println("[BBS Editor][Bake] "+failure);
        return true;
    }

    public String begin(BodyPartModelData model, int frame)
    {
        if(model==null || !model.hasMorphModel() || model.getBones()==null ||
                model.getBones().isEmpty())
            return "Select a Chameleon Body Part with animated bones first.";
        if(!BodyPartJoltPreviewAdapter.isRequested())
            return "Jolt preview is disabled in JVM options.";
        if(frame<model.getStartFrame() || frame>model.getEndFrame())
            return "Move timeline inside the selected Body Part range.";
        boolean hasPhysics=false;
        for(AnimationBone bone:model.getBones())
        {
            if(bone!=null && bone.getName()!=null &&
                    model.getPhysicsSettings(bone.getName()).isEnabled())
            {
                hasPhysics=true;
                break;
            }
        }
        if(!hasPhysics)
            return "Jolt bake NOT armed: no active physics chains. "+
                    "A successful bake disables live physics. "+
                    "Clear or revert the OLD baked keys, re-enable hair "+
                    "physics on this Body Part, then arm again.";
        reset();
        target=model;
        requestedStart=frame;
        System.out.println("[BBS Editor][Bake] Armed model="+model.getModelName()+
                " fromFrame="+frame+" (start normal timeline playback)");
        return "Jolt bake ARMED. Play timeline, then pause and press Ctrl+Shift+B.";
    }

    /**
     * Called once per successful native, VISIBLE Chameleon swing application.
     * Chameleon pose Euler angles are in radians; AnimationTransform stores
     * degrees. The original editor pose is evaluated BEFORE any key mutation.
     */
    public void capture(BodyPartModelData model,int frame,String boneName,
            float[] before,float[] after)
    {
        if(target==null || failure!=null || target!=model || frame<requestedStart ||
                frame>model.getEndFrame() || boneName==null ||
                before==null || after==null ||
                before.length!=3 || after.length!=3)
            return;
        for(int i=0;i<3;i++)
            if(!Float.isFinite(before[i]) || !Float.isFinite(after[i]))
            {
                failure="Non-finite Jolt Euler sample at frame "+frame;
                return;
            }
        AnimationBone bone=findBone(model,boneName);
        if(bone==null)return;

        Map<String,AnimationTransform> sample=frames.get(frame);
        if(sample==null)
        {
            sample=new LinkedHashMap<String,AnimationTransform>();
            frames.put(frame,sample);
        }
        // A frame may draw more than once (selection, shader and camera
        // passes). Freeze the FIRST valid result to prevent mixed poses.
        if(sample.containsKey(boneName))return;

        AnimationTransform clean=bone.getTransformAt(frame).copy();
        float x=clean.getRotationX()+degreesDifference(after[0],before[0]);
        float y=clean.getRotationY()+degreesDifference(after[1],before[1]);
        float z=clean.getRotationZ()+degreesDifference(after[2],before[2]);
        clean.setRotation(x,y,z);
        sample.put(boneName,clean);
    }

    private static float degreesDifference(float after,float before)
    {
        float radians=after-before;
        while(radians>(float)Math.PI)radians-=(float)(2D*Math.PI);
        while(radians<-(float)Math.PI)radians+=(float)(2D*Math.PI);
        return (float)Math.toDegrees(radians);
    }

    private static AnimationBone findBone(BodyPartModelData data,String name)
    {
        for(AnimationBone bone:data.getBones())
            if(bone!=null && name.equals(bone.getName()))
                return bone;
        return null;
    }

    private static float unwrap(float degrees,float previous)
    {
        while(degrees-previous>180F)degrees-=360F;
        while(degrees-previous< -180F)degrees+=360F;
        return degrees;
    }

    /** Writes to Body Parts key tracks only after every validation passes. */
    public String commit()
    {
        if(target==null)return "No armed Jolt bake session.";
        if(failure!=null)
        {
            String message="Bake rejected: "+failure;
            reset();
            return message;
        }
        if(frames.size()<2)
        {
            int count=frames.size();
            reset();
            return "Bake rejected: only "+count+" captured Jolt frames. "+
                    "Keep the selected model visible and play the timeline "+
                    "sequentially after arming.";
        }
        int first=frames.firstKey(),last=frames.lastKey();
        System.out.println("[BBS Editor][Bake] Commit check: recordedFrames="+
                frames.size()+" range="+first+".."+last+
                " distinctBonesFirstFrame="+frames.get(first).size());
        if(frames.size()!=last-first+1 || first>requestedStart)
        {
            StringBuilder missing=new StringBuilder();
            int count=0;
            for(int tick=requestedStart;tick<=last;tick++)
                if(!frames.containsKey(tick))
                {
                    count++;
                    if(count<=12)
                    {
                        if(missing.length()>0)missing.append(", ");
                        missing.append(tick);
                    }
                }
            String detail="Bake rejected: missing timeline frames "+
                    requestedStart+".."+last+" ("+count+
                    " missing; first: "+missing+
                    (count>12?", ...":"")+
                    "). Recording must advance in lockstep with Jolt.";
            System.out.println("[BBS Editor][Bake] "+detail);
            reset();
            return detail;
        }
        Map<String,AnimationTransform> initial=frames.get(first);
        if(initial==null || initial.isEmpty())
        {
            reset();
            return "Bake rejected: first frame has no Jolt bones.";
        }
        // Verify a complete, stable set of physical bones on every frame
        // BEFORE creating or replacing a single existing key.
        for(Map.Entry<Integer,Map<String,AnimationTransform>> entry:frames.entrySet())
        {
            if(!entry.getValue().keySet().equals(initial.keySet()))
            {
                String message="Bake rejected: physics bone coverage changed "+
                        "at frame "+entry.getKey();
                reset();
                return message;
            }
            for(String name:entry.getValue().keySet())
                if(findBone(target,name)==null)
                {
                    reset();
                    return "Bake rejected: missing bone "+name;
                }
        }
        // Stage a full write set with continuous Euler channels. Existing
        // movement/scale are taken from the clean editor pose at capture time.
        Map<AnimationBone,Map<Integer,AnimationTransform>> staged=
                new LinkedHashMap<AnimationBone,Map<Integer,AnimationTransform>>();
        for(String name:initial.keySet())
        {
            AnimationBone bone=findBone(target,name);
            Map<Integer,AnimationTransform> keys=
                    new LinkedHashMap<Integer,AnimationTransform>();
            AnimationTransform previous=null;
            for(Map.Entry<Integer,Map<String,AnimationTransform>> entry:frames.entrySet())
            {
                AnimationTransform value=entry.getValue().get(name).copy();
                if(previous!=null)
                {
                    value.setRotation(
                            unwrap(value.getRotationX(),previous.getRotationX()),
                            unwrap(value.getRotationY(),previous.getRotationY()),
                            unwrap(value.getRotationZ(),previous.getRotationZ()));
                }
                previous=value;
                keys.put(entry.getKey(),value);
            }
            staged.put(bone,keys);
        }
        // Bake one exact Jolt pose per game tick using the original LINEAR key mode.
        // From here onward only deterministic in-memory key writes happen.
        int count=0;
        for(Map.Entry<AnimationBone,Map<Integer,AnimationTransform>> track:staged.entrySet())
            for(Map.Entry<Integer,AnimationTransform> value:track.getValue().entrySet())
            {
                AnimationBone bone=track.getKey();
                int frame=value.getKey();
                AnimationKeyframe key=bone.getKeyframeAt(frame);
                if(key==null)
                {
                    bone.addKeyframe(frame);
                    key=bone.getKeyframeAt(frame);
                }
                AnimationTransform from=value.getValue();
                AnimationTransform to=key.getTransform();
                to.setPosition(from.getPositionX(),from.getPositionY(),from.getPositionZ());
                to.setRotation(from.getRotationX(),from.getRotationY(),from.getRotationZ());
                to.setScale(from.getScaleX(),from.getScaleY(),from.getScaleZ());
                key.setInterpolation(KeyframeInterpolation.LINEAR);
                count++;
            }
        // A baked track and live Jolt simultaneously would double-animate.
        // Keep settings editable; users can re-enable them for a new bake.
        for(AnimationBone bone:target.getBones())
            if(bone!=null && bone.getName()!=null)
            {
                BodyPartPhysicsSettings settings=target.getPhysicsSettings(bone.getName());
                if(settings.isEnabled())settings.setEnabled(false);
            }
        String output="Baked "+count+" Body Parts bone keys, "+
                first+".."+last+" ("+initial.size()+" bones), interpolation=LINEAR. "+
                "Live physics disabled for this model. Save and export.";
        System.out.println("[BBS Editor][Bake] "+output);
        reset();
        return output;
    }

    public void reset()
    {
        target=null;
        frames.clear();
        failure=null;
        requestedStart=0;
        waitingFrame=Integer.MIN_VALUE;
        waitTicks=0;
    }
}
