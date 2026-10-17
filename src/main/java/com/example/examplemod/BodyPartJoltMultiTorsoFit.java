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
    /**
     * Like a bone-marked collision form, size contact geometry from the
     * currently SKINNED torso, not just a fixed subdivision of the short
     * low_body-to-body pivot distance. For animated chest/shoulder geometry
     * extending above either pivot, the central axis follows those vertices.
     *
     * Robust 10/90% extents reject rare skinned outliers. Every dimension
     * remains conservative, and a suspect extended fit falls back to the
     * original measured bone-axis sections, never guessed colliders.
     */
    public static Segments fit(BodyPartJoltTorsoFit.Fit base,
            float[] vertices,int[] indices)
    {
        if(base==null || vertices==null || indices==null ||
                vertices.length%3!=0 || indices.length%3!=0)
            return null;
        float dx=base.b[0]-base.a[0];
        float dy=base.b[1]-base.a[1];
        float dz=base.b[2]-base.a[2];
        float axisSq=dx*dx+dy*dy+dz*dz;
        if(!Float.isFinite(axisSq) || axisSq<0.0125F || axisSq>1F)
            return null;

        float[] projected=new float[Math.min(vertices.length/3,indices.length)];
        boolean[] seen=new boolean[vertices.length/3];
        int count=0;
        for(int index:indices)
        {
            if(index<0 || index>=seen.length)return null;
            if(seen[index])continue;
            seen[index]=true;
            int p=index*3;
            float x=vertices[p],y=vertices[p+1],z=vertices[p+2];
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                return null;
            float t=((x-base.a[0])*dx+(y-base.a[1])*dy+
                    (z-base.a[2])*dz)/axisSq;
            float rx=x-(base.a[0]+dx*t);
            float ry=y-(base.a[1]+dy*t);
            float rz=z-(base.a[2]+dz*t);
            float radial=(float)Math.sqrt(rx*rx+ry*ry+rz*rz);
            // Ignore unrelated vertices that happen to carry a minority
            // torso skin influence; they must not stretch a collision body.
            if(t>=-1.5F && t<=2.5F && radial>=0.005F &&
                    radial<=0.45F)
                projected[count++]=t;
        }
        Segments baseSegments=fitZones(base,vertices,indices,0F,1F);
        if(baseSegments==null || count<36)return baseSegments;

        Arrays.sort(projected,0,count);
        float low=Math.min(0F,projected[(count-1)/10]);
        float high=Math.max(1F,projected[(count-1)*9/10]);
        low=Math.max(-1.25F,low);
        high=Math.min(2F,high);
        if(low>=-0.05F && high<=1.05F)return baseSegments;
        if(high-low>2.5F)return baseSegments;

        Segments extended=fitZones(base,vertices,indices,low,high);
        return extended==null?baseSegments:extended;
    }

    private static Segments fitZones(BodyPartJoltTorsoFit.Fit base,
            float[] vertices,int[] indices,float lo,float hi)
    {
        float[] a0=base.a,b0=base.b;
        float dx=b0[0]-a0[0],dy=b0[1]-a0[1],dz=b0[2]-a0[2];
        float sq=dx*dx+dy*dy+dz*dz;
        float span=hi-lo;
        if(!(span>0F)||!Float.isFinite(span))return null;

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
                float tMin=lo+span*rangeLo[i];
                float tMax=lo+span*rangeHi[i];
                if(t<tMin || t>tMax)continue;
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
                    median[i]>base.medianDistance*1.55F)
                return null;
            radius[i]=clamp(median[i]*0.90F,0.055F,0.22F);
            for(int c=0;c<3;c++)
            {
                float delta=b0[c]-a0[c];
                outA[i][c]=a0[c]+delta*(lo+span*starts[i]);
                outB[i][c]=a0[c]+delta*(lo+span*ends[i]);
            }
            // Native Jolt limits each kinematic capsule span to <= 0.7
            // blocks; fail closed, not a partially created physics body.
            float segmentLength=0F;
            for(int c=0;c<3;c++)
            {
                float d=outA[i][c]-outB[i][c];
                segmentLength+=d*d;
            }
            if(segmentLength<0.075F*0.075F ||
                    segmentLength>0.7F*0.7F)return null;
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
