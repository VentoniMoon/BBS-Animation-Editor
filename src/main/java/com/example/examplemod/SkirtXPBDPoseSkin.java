package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physics 3.0 / Stage 1: follow the actor's REAL Chameleon bone pose, while
 * keeping source UV corners separate from the 60 shared cloth particles.
 *
 * The transforms are the actual native (bone hierarchy * cube rotation)
 * matrices, not heuristics derived from the distance between pivots. Every
 * source corner is transformed with posed * inverse(rest), so at zero XPBD
 * displacement it EXACTLY follows Chameleon's animated cube surface.
 *
 * The 60 anchor targets are obtained by transforming each shared rest
 * particle through its contributing bones and averaging the results.
 * Immutable, self-contained Java 8 code; no ModelVertex mutation.
 */
public final class SkirtXPBDPoseSkin
{
    public static final int PATCHES=40;
    public static final int NODES=60;

    public static final class Transform
    {
        public final String bone;
        public final float[] rest,posed;
        public Transform(String bone,float[] rest,float[] posed)
        {
            if(bone==null||bone.isEmpty())
                throw new IllegalArgumentException("Missing bone ID");
            this.bone=bone;
            this.rest=valid(rest,16).clone();
            this.posed=valid(posed,16).clone();
        }
    }
    public static final class Frame
    {
        /** Each original material corner under the exact current bone pose. */
        public final float[][][] patches;
        /** Desired cloth anchors under the current pose; rows 0-2. */
        public final float[][] target;
        /** Native source-quad UV stays in SkirtClothRestBinding. */
        public final float maxPoseReconstructionError;

        private Frame(float[][][] patches,float[][] target,float error)
        {
            this.patches=copy3(patches);
            this.target=copy2(target);
            maxPoseReconstructionError=error;
        }

        /** Native posed material corner plus the displacement of its
         * shared physical node from the SAME frame's anchor target. */
        public float[] corner(SkirtClothRestBinding.Binding binding,
                float[][] simulated,int patch,int vertex)
        {
            if(binding==null||simulated==null||
                    simulated.length!=NODES||patch<0||patch>=PATCHES||
                    vertex<0||vertex>=4)
                throw new IllegalArgumentException("Invalid skin pose");
            int node=binding.patches.get(patch).nodes[vertex];
            float[] state=valid(simulated[node],3);
            float[] result=new float[3];
            for(int i=0;i<3;i++)
                result[i]=patches[patch][vertex][i]+state[i]-target[node][i];
            return valid(result,3);
        }
    }
    /**
     * Interpolate actual native posed cloth corners and all shared node
     * anchors between two consecutive editor samples. Only the target and
     * the corners move; source UVs, rest geometry and the 20 pinned waist
     * topology are never modified. This is for collision integration, not
     * a replacement for the final exact render-time source pose.
     */
    public static Frame between(Frame from,Frame to,float alpha)
    {
        if(to==null)return from;
        if(from==null||alpha>=1F)return to;
        if(alpha<=0F)return from;
        if(!Float.isFinite(alpha)||from.target.length!=NODES||
                to.target.length!=NODES||from.patches.length!=PATCHES||
                to.patches.length!=PATCHES)
            throw new IllegalArgumentException("Bad cloth sweep");
        float[][] anchors=new float[NODES][3];
        float[][][] corners=new float[PATCHES][4][3];
        for(int n=0;n<NODES;n++)
            for(int k=0;k<3;k++)
                anchors[n][k]=from.target[n][k]+
                        (to.target[n][k]-from.target[n][k])*alpha;
        for(int p=0;p<PATCHES;p++)
            for(int v=0;v<4;v++)
                for(int k=0;k<3;k++)
                    corners[p][v][k]=from.patches[p][v][k]+
                            (to.patches[p][v][k]-from.patches[p][v][k])*alpha;
        return new Frame(corners,anchors,Math.max(
                from.maxPoseReconstructionError,to.maxPoseReconstructionError));
    }

    /** Largest world-model cloth-anchor jump; used only to allocate extra
     * microsteps during unusually fast keyed animation. */
    public static float maxTargetMotion(Frame from,Frame to)
    {
        if(from==null||to==null)return 0F;
        float longest=0F;
        for(int n=0;n<NODES;n++)
            longest=Math.max(longest,
                    lengthDifference(from.target[n],to.target[n]));
        return longest;
    }

    private SkirtXPBDPoseSkin(){}

    public static Frame follow(SkirtClothRestBinding.Binding binding,
            List<Transform> poses)
    {
        if(binding==null||binding.nodeRest.length!=NODES||
                binding.patches.size()!=PATCHES||poses==null)
            throw new IllegalArgumentException("Incomplete skin binding");
        Map<String,float[]> maps=new HashMap<String,float[]>();
        for(Transform p:poses)
        {
            if(p==null||maps.containsKey(p.bone))
                throw new IllegalArgumentException("Repeated posed bone");
            float[] inv=inverse(p.rest);
            if(inv==null)
                throw new IllegalArgumentException("Non-invertible rest bone");
            maps.put(p.bone,mul(p.posed,inv));
        }
        if(maps.size()!=PATCHES)
            throw new IllegalArgumentException("Expected exactly 40 posed fabric cubes");

        float[][] sums=new float[NODES][3];
        int[] counts=new int[NODES];
        float[][][] corners=new float[PATCHES][4][3];
        float maxError=0F;
        for(int i=0;i<PATCHES;i++)
        {
            SkirtClothRestBinding.Patch patch=binding.patches.get(i);
            float[] transform=maps.get(patch.bone);
            if(transform==null)
                throw new IllegalArgumentException("Missing current bone: "+patch.bone);
            for(int v=0;v<4;v++)
            {
                int index=patch.nodes[v];
                if(index<0||index>=NODES)
                    throw new IllegalArgumentException("Bad shared node");
                corners[i][v]=position(transform,valid(patch.sourceRest[v],3));
                float[] target=position(transform,binding.nodeRest[index]);
                for(int j=0;j<3;j++)sums[index][j]+=target[j];
                counts[index]++;
                // Verify exact source rest reconstruction, independent of
                // the model's current rotation/scale.
                float[] source=patch.sourceRest[v];
                float[] rec=new float[3];
                for(int k=0;k<3;k++)
                    rec[k]=patch.materialOffset[v][k]+
                            binding.nodeRest[index][k];
                maxError=Math.max(maxError,lengthDifference(source,rec));
            }
        }
        for(int i=0;i<NODES;i++)
        {
            if(counts[i]<1)throw new IllegalArgumentException("Unbound node "+i);
            for(int j=0;j<3;j++)sums[i][j]/=counts[i];
        }
        return new Frame(corners,sums,maxError);
    }

    private static float lengthDifference(float[] a,float[] b)
    {
        float s=0F;
        for(int k=0;k<3;k++){float d=a[k]-b[k];s+=d*d;}
        return (float)Math.sqrt(s);
    }
    private static float[] position(float[] m,float[] p)
    {
        return valid(new float[]{
                m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],
                m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],
                m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]},3);
    }
    private static float[] inverse(float[] m)
    {
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9];
        float g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=c*h-b*i,C=b*f-c*e;
        float D=f*g-d*i,E=a*i-c*g,F=c*d-a*f;
        float G=d*h-e*g,H=b*g-a*h,I=a*e-b*d;
        float determinant=a*A+b*D+c*G;
        if(!Float.isFinite(determinant)||
                Math.abs(determinant)<1E-9F)return null;
        float inv=1F/determinant;
        float[] r={A*inv,D*inv,G*inv,0,
                B*inv,E*inv,H*inv,0,C*inv,F*inv,I*inv,0,0,0,0,1};
        float x=m[12],y=m[13],z=m[14];
        r[12]=-(r[0]*x+r[4]*y+r[8]*z);
        r[13]=-(r[1]*x+r[5]*y+r[9]*z);
        r[14]=-(r[2]*x+r[6]*y+r[10]*z);
        return r;
    }
    private static float[] mul(float[] a,float[] b)
    {
        float[] v=new float[16];
        for(int c=0;c<4;c++)
            for(int r=0;r<4;r++)
                for(int k=0;k<4;k++)
                    v[c*4+r]+=a[k*4+r]*b[c*4+k];
        return v;
    }
    private static float[] valid(float[] data,int count)
    {
        if(data==null||data.length!=count)
            throw new IllegalArgumentException("Bad pose XYZ/matrix");
        for(float v:data)
            if(!Float.isFinite(v))
                throw new IllegalArgumentException("Non-finite pose");
        return data;
    }
    private static float[][] copy2(float[][] a)
    {
        float[][] copy=new float[a.length][];
        for(int i=0;i<a.length;i++)copy[i]=a[i].clone();
        return copy;
    }
    private static float[][][] copy3(float[][][] a)
    {
        float[][][] result=new float[a.length][][];
        for(int i=0;i<a.length;i++)result[i]=copy2(a[i]);
        return result;
    }
}
