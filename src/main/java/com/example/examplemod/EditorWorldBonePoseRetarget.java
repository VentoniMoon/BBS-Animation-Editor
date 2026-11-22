package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental Blockbuster POSE world-pin adapter.
 *
 * Solves the complete parent chain in native model-pixel coordinates, then
 * changes LOCAL ROTATIONS on per-frame temporary AnimationTransform copies.
 * No AnimationKeyframe, Actor Record, mesh scale, or stored pose is mutated.
 * The actor is still free to move; a pinned tip stays at its absolute world
 * position while the connected joints bend to reach it.
 *
 * This specifically uses the non-Chameleon model convention from
 * EditorGizmoController.getWorldFromPivot(). Other native rigs require their
 * own verified pivot/rotation adapters before enabling world pins.
 */
public final class EditorWorldBonePoseRetarget
{
    public static final class Result
    {
        public final Map<AnimationBone,AnimationTransform> overrides;
        public final boolean reachable;
        public final float residualWorld;
        public final int iterations;

        private Result(Map<AnimationBone,AnimationTransform> overrides,
                boolean reachable,float residualWorld,int iterations)
        {
            this.overrides=Collections.unmodifiableMap(overrides);
            this.reachable=reachable;
            this.residualWorld=residualWorld;
            this.iterations=iterations;
        }
    }

    private EditorWorldBonePoseRetarget(){}

    public static boolean supports(AnimationBone tip)
    {
        return tip!=null&&tip.getParent()!=null;
    }

    public static float[] currentWorld(AnimationBone tip,float frame,
            BlockbusterRecordFrame record)
    {
        if(!supports(tip)||record==null)return null;
        List<AnimationBone> chain=chain(tip);
        Map<AnimationBone,AnimationTransform> pose=sample(chain,frame);
        AnimationTransform[] world=forward(chain,pose);
        return modelToWorld(world[world.length-1],record);
    }

    public static Result retarget(AnimationBone tip,float frame,
            BlockbusterRecordFrame record,float[] pinnedWorld)
    {
        if(!supports(tip)||record==null||!finite3(pinnedWorld))
            return null;
        List<AnimationBone> chain=chain(tip);
        if(chain.size()>64)return null;
        Map<AnimationBone,AnimationTransform> pose=sample(chain,frame);
        AnimationTransform[] original=forward(chain,pose);
        float[][] joints=positions(original);
        float[] target=worldToModel(pinnedWorld,record);
        EditorWorldBoneIK.Result solved;
        try
        {
            solved=EditorWorldBoneIK.solve(joints,target,64,.002F);
        }
        catch(IllegalArgumentException invalid)
        {
            // Rest joints can overlap (e.g. mirrored invisible detail bones).
            // Do not fabricate lengths or edit a native morph in that case.
            return null;
        }

        for(int i=0;i<chain.size()-1;i++)
        {
            // Recompute native-style FK after each parent rotation, instead
            // of applying all rotations in the original stale parent basis.
            AnimationTransform[] current=forward(chain,pose);
            float[] from=sub(position(current[i+1]),position(current[i]));
            float[] to=sub(solved.joints[i+1],solved.joints[i]);
            float[] parentEuler=i==0?new float[]{0F,0F,0F}:
                    angles(current[i-1]);
            AnimationBone bone=chain.get(i);
            AnimationTransform authored=pose.get(bone);
            float[] localEuler=new float[]{
                    bone.getBaseRotationX()+authored.getRotationX(),
                    bone.getBaseRotationY()+authored.getRotationY(),
                    bone.getBaseRotationZ()+authored.getRotationZ()};
            float[] aligned;
            try
            {
                aligned=EditorWorldBoneRetargetMath.alignLocalEuler(
                        parentEuler,localEuler,from,to);
            }
            catch(IllegalArgumentException degenerate)
            {
                return null;
            }
            float rx=EditorWorldBoneRetargetMath.nearestAngle(
                    aligned[0]-bone.getBaseRotationX(),authored.getRotationX());
            float ry=EditorWorldBoneRetargetMath.nearestAngle(
                    aligned[1]-bone.getBaseRotationY(),authored.getRotationY());
            float rz=EditorWorldBoneRetargetMath.nearestAngle(
                    aligned[2]-bone.getBaseRotationZ(),authored.getRotationZ());
            authored.setRotation(rx,ry,rz);
        }

        float[] actual=position(forward(chain,pose)[chain.size()-1]);
        float modelError=distance(actual,target);
        return new Result(new LinkedHashMap<AnimationBone,AnimationTransform>(pose),
                solved.reachable,modelError/16F,solved.iterations);
    }

    /** Only used for initial native/kinematic pivot parity checks. */
    public static boolean pivotMatches(AnimationBone bone,float frame,
            BlockbusterRecordFrame record,float[] nativeWorld,
            float maxDifferenceBlocks)
    {
        float[] estimated=currentWorld(bone,frame,record);
        return estimated!=null&&finite3(nativeWorld)&&
                distance(estimated,nativeWorld)<=maxDifferenceBlocks;
    }

    private static List<AnimationBone> chain(AnimationBone tip)
    {
        List<AnimationBone> chain=new ArrayList<AnimationBone>();
        AnimationBone cursor=tip;
        while(cursor!=null&&chain.size()<65)
        {
            if(chain.contains(cursor))return Collections.emptyList();
            chain.add(cursor);
            cursor=cursor.getParent();
        }
        Collections.reverse(chain);
        return chain;
    }

    private static Map<AnimationBone,AnimationTransform> sample(
            List<AnimationBone> chain,float frame)
    {
        Map<AnimationBone,AnimationTransform> result=
                new LinkedHashMap<AnimationBone,AnimationTransform>();
        for(AnimationBone bone:chain)
        {
            AnimationTransform key=bone.getTransformAt(frame);
            result.put(bone,key==null?
                    new AnimationTransform():key.copy());
        }
        return result;
    }

    private static AnimationTransform[] forward(List<AnimationBone> chain,
            Map<AnimationBone,AnimationTransform> pose)
    {
        AnimationTransform[] matrices=new AnimationTransform[chain.size()];
        for(int i=0;i<chain.size();i++)
        {
            AnimationBone bone=chain.get(i);
            AnimationTransform authored=pose.get(bone);
            AnimationTransform local=new AnimationTransform();
            local.setPosition(bone.getLocalX()+authored.getPositionX(),
                    bone.getLocalY()+authored.getPositionY(),
                    bone.getLocalZ()+authored.getPositionZ());
            local.setRotation(bone.getBaseRotationX()+authored.getRotationX(),
                    bone.getBaseRotationY()+authored.getRotationY(),
                    bone.getBaseRotationZ()+authored.getRotationZ());
            // Preserve authored scale exactly; IK never changes it.
            local.setScale(authored.getScaleX(),authored.getScaleY(),
                    authored.getScaleZ());
            matrices[i]=i==0?local:matrices[i-1].combine(local);
        }
        return matrices;
    }

    private static float[][] positions(AnimationTransform[] world)
    {
        float[][] p=new float[world.length][3];
        for(int i=0;i<world.length;i++)p[i]=position(world[i]);
        return p;
    }

    private static float[] angles(AnimationTransform t)
    {
        return new float[]{t.getRotationX(),t.getRotationY(),t.getRotationZ()};
    }

    private static float[] position(AnimationTransform t)
    {
        return new float[]{t.getPositionX(),t.getPositionY(),t.getPositionZ()};
    }

    private static float[] sub(float[] a,float[] b)
    {
        return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};
    }

    private static float[] modelToWorld(AnimationTransform model,
            BlockbusterRecordFrame record)
    {
        return modelToWorld(position(model),record);
    }

    private static float[] modelToWorld(float[] model,
            BlockbusterRecordFrame record)
    {
        float yaw=(float)Math.toRadians(180D-record.getYaw());
        float cos=(float)Math.cos(yaw),sin=(float)Math.sin(yaw);
        float x=-model[0]/16F,y=-model[1]/16F+1.501F,
                z=-model[2]/16F;
        return new float[]{
                (float)record.getX()+cos*x+sin*z,
                (float)record.getY()+y,
                (float)record.getZ()-sin*x+cos*z};
    }

    private static float[] worldToModel(float[] world,
            BlockbusterRecordFrame record)
    {
        float yaw=(float)Math.toRadians(180D-record.getYaw());
        float cos=(float)Math.cos(yaw),sin=(float)Math.sin(yaw);
        float dx=world[0]-(float)record.getX();
        float dz=world[2]-(float)record.getZ();
        float x=cos*dx-sin*dz,z=sin*dx+cos*dz;
        return new float[]{-16F*x,
                -16F*(world[1]-(float)record.getY()-1.501F),
                -16F*z};
    }

    private static float distance(float[] a,float[] b)
    {
        double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }

    private static boolean finite3(float[] a)
    {
        if(a==null||a.length!=3)return false;
        for(float v:a)if(!Float.isFinite(v))return false;
        return true;
    }
}
