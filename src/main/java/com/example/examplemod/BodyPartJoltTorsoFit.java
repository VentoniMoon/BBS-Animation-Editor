package com.example.examplemod;

import java.util.Arrays;

/**
 * Conservative, read-only fit of an Emoticons skinned TORSO surface to
 * a short capsule between renderer-evaluated Emoticons torso landmarks.
 *
 * Both mesh vertices and bone landmarks MUST be in the same model-local
 * world frame. We do not infer orientation from triangle winding, nor
 * use a fixed oversized "whole body" capsule. If the surface/axis is
 * inconsistent, return null; do not turn on native collision forces.
 *
 * This is a first-pass proxy, not triangle-perfect skin collision.
 */
public final class BodyPartJoltTorsoFit
{
    private BodyPartJoltTorsoFit() {}

    public static final class Fit
    {
        public final float[] a, b;
        public final float radius;
        public final int observedVertices, usableVertices;
        public final float medianDistance, largestDistance;
        Fit(float[] a, float[] b, float radius, int total,
                int usable, float medianDistance,float largestDistance)
        {
            this.a=a.clone();this.b=b.clone();
            this.radius=radius;this.observedVertices=total;
            this.usableVertices=usable;this.medianDistance=medianDistance;
            this.largestDistance=largestDistance;
        }
    }

    /**
     * Skin-weight-selected triangles supply surface vertices. The two
     * endpoints are native Emoticons bone pivot positions, not Chameleon.
     */
    public static Fit fit(float[] a,float[] b,
            float[] skinnedVertices,int[] torsoTriangleIndices)
    {
        if(!finite3(a) || !finite3(b) || skinnedVertices==null ||
                torsoTriangleIndices==null || skinnedVertices.length%3!=0)
            return null;
        float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        float lenSq=dx*dx+dy*dy+dz*dz;
        // Expected low_body-to-upper torso joint axis is short. Refuse
        // degenerate or whole-body proxies instead of silently guessing.
        if(lenSq<0.0125F || lenSq>1.00F)return null;
        boolean[] seen=new boolean[skinnedVertices.length/3];
        float[] distances=new float[Math.min(torsoTriangleIndices.length,seen.length)];
        int total=0,accepted=0;
        float largest=0F;
        for(int index:torsoTriangleIndices)
        {
            if(index<0 || index>=seen.length)return null;
            if(seen[index])continue;
            seen[index]=true;total++;
            int i=index*3;
            float x=skinnedVertices[i],y=skinnedVertices[i+1],z=skinnedVertices[i+2];
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                return null;
            float t=((x-a[0])*dx+(y-a[1])*dy+(z-a[2])*dz)/lenSq;
            // Do not let neck/head/hip triangles dominate the torso width.
            if(t<0.1F || t>0.9F)continue;
            float px=a[0]+t*dx,py=a[1]+t*dy,pz=a[2]+t*dz;
            float vx=x-px,vy=y-py,vz=z-pz;
            float distance=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);
            if(!(distance>0.005F) || distance>0.45F)continue;
            if(accepted>=distances.length)return null;
            distances[accepted++]=distance;
            largest=Math.max(largest,distance);
        }
        if(total<12 || accepted<12 || accepted<total*0.15F)
            return null;
        Arrays.sort(distances,0,accepted);
        float middle=distances[accepted/2];
        // A physical body has a meaningful fitted radius. Fail closed
        // when the mesh and bones appear to use different coordinate spaces.
        if(middle<0.065F || middle>0.28F)return null;
        // Conservative interior proxy: contacts before deep penetration,
        // with a small visible gap already supplied by the strand capsule.
        float radius=Math.max(0.06F,Math.min(0.22F,0.90F*middle));
        return new Fit(a,b,radius,total,accepted,middle,largest);
    }

    private static boolean finite3(float[] v)
    {
        if(v==null||v.length!=3)return false;
        for(float x:v)if(!Float.isFinite(x))return false;
        return true;
    }
}
