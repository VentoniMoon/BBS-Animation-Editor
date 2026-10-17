package com.example.examplemod;

import java.util.Arrays;

/**
 * Three short kinematic collision proxies fitted to the SAME skinned torso
 * mesh and the SAME animated low_body -> upper_body axis as the existing
 * single-capsule Jolt contact.
 *
 * Sections deliberately overlap slightly so hairs cannot slip through a
 * numerical seam. The fit fails closed on suspect bone/mesh correspondence.
 * This is a proxy, NOT an exact mesh or a shoulder/neck collider.
 */
public final class BodyPartJoltMultiTorsoFit
{
    private BodyPartJoltMultiTorsoFit() {}

    public static final int COUNT=3;

    public static final class Segments
    {
        public final float[][] a;
        public final float[][] b;
        public final float[] radius;
        public final int[] witnesses;
        public final float[] medianSurfaceDistance;

        private Segments(float[][] a,float[][] b,float[] radius,
                int[] witnesses,float[] medianSurfaceDistance)
        {
            this.a=deepCopy(a);
            this.b=deepCopy(b);
            this.radius=radius.clone();
            this.witnesses=witnesses.clone();
            this.medianSurfaceDistance=medianSurfaceDistance.clone();
        }

        public int count() { return radius.length; }
        public float[][] starts() { return deepCopy(a); }
        public float[][] ends() { return deepCopy(b); }
        public float[] radii() { return radius.clone(); }
    }

    /**
     * Input vertices are already CPU-skinned, filtered by torso weights,
     * and share the native Emoticons bone frame. The base fit is required:
     * a missing/invalid base fit must NEVER be masked by a new segment fit.
     */
    public static Segments fit(BodyPartJoltTorsoFit.Fit base,
            float[] vertices,int[] indices)
    {
        if(base==null || vertices==null || indices==null ||
                vertices.length%3!=0 || indices.length%3!=0)
            return null;

        final float[] a0=base.a,b0=base.b;
        float dx=b0[0]-a0[0],dy=b0[1]-a0[1],dz=b0[2]-a0[2];
        float sq=dx*dx+dy*dy+dz*dz;
        if(!Float.isFinite(sq) || sq<0.0125F || sq>1F)return null;
        // The inner intervals supply surface width. Outer intervals
        // overlap (0.34/0.32) so capsule ends don't leave contact seams.
        final float[] starts={0.00F,0.31F,0.62F};
        final float[] ends={0.39F,0.72F,1.00F};
        final float[] rangeLo={0.02F,0.35F,0.69F};
        final float[] rangeHi={0.32F,0.66F,0.98F};
        float[][] outA=new float[COUNT][3],outB=new float[COUNT][3];
        float[] radius=new float[COUNT],median=new float[COUNT];
        int[] count=new int[COUNT];
        boolean[] seen=new boolean[vertices.length/3];
        float[][] values=new float[COUNT][Math.min(indices.length,seen.length)];

        for(int id:indices)
        {
            if(id<0 || id>=seen.length)return null;
            if(seen[id])continue;
            seen[id]=true;
            int offset=id*3;
            float vx=vertices[offset],vy=vertices[offset+1],vz=vertices[offset+2];
            if(!Float.isFinite(vx)||!Float.isFinite(vy)||!Float.isFinite(vz))
                return null;
            float t=((vx-a0[0])*dx+(vy-a0[1])*dy+(vz-a0[2])*dz)/sq;
            float x=vx-(a0[0]+dx*t);
            float y=vy-(a0[1]+dy*t);
            float z=vz-(a0[2]+dz*t);
            float distance=(float)Math.sqrt(x*x+y*y+z*z);
            if(!Float.isFinite(distance))return null;
            for(int i=0;i<COUNT;i++)
            {
                if(t<rangeLo[i] || t>rangeHi[i])continue;
                if(distance<0.005F || distance>0.45F)continue;
                if(count[i]>=values[i].length)return null;
                values[i][count[i]++]=distance;
            }
        }
        for(int i=0;i<COUNT;i++)
        {
            if(count[i]<12)return null;
            Arrays.sort(values[i],0,count[i]);
            median[i]=values[i][count[i]/2];
            if(median[i]<0.055F || median[i]>0.31F ||
                    median[i]<base.medianDistance*0.52F ||
                    median[i]>base.medianDistance*1.42F)
                return null;
            radius[i]=clamp(median[i]*0.90F,0.055F,0.22F);
            for(int c=0;c<3;c++)
            {
                float delta=b0[c]-a0[c];
                outA[i][c]=a0[c]+delta*starts[i];
                outB[i][c]=a0[c]+delta*ends[i];
            }
        }
        return new Segments(outA,outB,radius,count,median);
    }

    private static float clamp(float v,float lo,float hi)
    {
        return Math.max(lo,Math.min(hi,v));
    }

    private static float[][] deepCopy(float[][] x)
    {
        float[][] copy=new float[x.length][];
        for(int i=0;i<x.length;i++)copy[i]=x[i].clone();
        return copy;
    }
}
