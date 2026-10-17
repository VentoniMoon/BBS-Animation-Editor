package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Native-model-local skinned torso fit and coordinate mismatch guards. */
public final class JoltTorsoFitSmoke
{
    private JoltTorsoFitSmoke() {}

    public static void main(String[] args)
    {
        // Typical upright torso with an actual skinned radius 0.15 blocks.
        List<Float> coordinates=new ArrayList<Float>();
        List<Integer> triangles=new ArrayList<Integer>();
        int n=24;
        for(int j=0;j<4;j++)
            for(int i=0;i<n;i++)
            {
                double radians=2D*Math.PI*i/n;
                coordinates.add((float)(0.15D*Math.cos(radians)));
                coordinates.add(1.25F+j*0.07F);
                coordinates.add((float)(0.15D*Math.sin(radians)));
            }
        for(int j=0;j<3;j++)
            for(int i=0;i<n;i++)
            {
                int a=j*n+i,b=j*n+(i+1)%n,c=(j+1)*n+i;
                triangles.add(a);triangles.add(c);triangles.add(b);
            }
        float[] vertices=new float[coordinates.size()];
        int[] indices=new int[triangles.size()];
        for(int i=0;i<vertices.length;i++)vertices[i]=coordinates.get(i);
        for(int i=0;i<indices.length;i++)indices[i]=triangles.get(i);
        float[] a={0F,1.20F,0F},b={0F,1.55F,0F};
        BodyPartJoltTorsoFit.Fit fit=BodyPartJoltTorsoFit.fit(
                a,b,vertices,indices);
        if(fit==null || Math.abs(fit.radius-0.135F)>0.005F)
            throw new AssertionError("Fitted torso radius lost "+(fit==null?"null":fit.radius));
        if(fit.usableVertices<12)
            throw new AssertionError("Insufficient mesh contact witnesses");
        if(BodyPartJoltTorsoFit.fit(
                new float[]{10F,1.20F,0F},new float[]{10F,1.55F,0F},
                vertices,indices)!=null)
            throw new AssertionError("Unaligned native bone/mesh spaces admitted");
        if(BodyPartJoltTorsoFit.fit(a,a,vertices,indices)!=null)
            throw new AssertionError("Degenerate capsule admitted");
        float[] invalid=vertices.clone();invalid[0]=Float.NaN;
        if(BodyPartJoltTorsoFit.fit(a,b,invalid,indices)!=null)
            throw new AssertionError("Nonfinite skinned geometry admitted");
        System.out.println("Mesh-fitted Emoticons torso proxy, fail-closed guards: PASS");
    }
}
