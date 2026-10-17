package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Three-zone model fit at 0°/80° and unrelated coordinate-system rejection. */
public final class JoltMultiTorsoFitSmoke
{
    private JoltMultiTorsoFitSmoke() {}

    private static void run(float incline)
    {
        float rad=(float)Math.toRadians(incline);
        float cy=(float)Math.cos(rad),sy=(float)Math.sin(rad);
        float[] a={0F,1.20F,0F};
        float[] b={0F,1.20F+0.4F*cy,0.4F*sy};
        List<Float> points=new ArrayList<Float>();
        List<Integer> faces=new ArrayList<Integer>();
        final int rings=12,round=24;
        for(int j=0;j<rings;j++)
            for(int k=0;k<round;k++)
            {
                float along=0.04F+0.93F*j/(rings-1F);
                float angle=(float)(Math.PI*2D*k/round);
                float radius=along<0.33F?0.14F:along<0.68F?0.18F:0.12F;
                float x=radius*(float)Math.cos(angle);
                float transverse=radius*(float)Math.sin(angle);
                points.add(x);
                points.add(a[1]+along*(b[1]-a[1])-sy*transverse);
                points.add(a[2]+along*(b[2]-a[2])+cy*transverse);
            }
        for(int row=0;row<rings-1;row++)
            for(int col=0;col<round;col++)
            {
                int aIndex=row*round+col, bIndex=row*round+(col+1)%round;
                int cIndex=(row+1)*round+col;
                faces.add(aIndex);faces.add(cIndex);faces.add(bIndex);
            }
        float[] vertices=new float[points.size()];
        int[] indices=new int[faces.size()];
        for(int i=0;i<vertices.length;i++)vertices[i]=points.get(i);
        for(int i=0;i<indices.length;i++)indices[i]=faces.get(i);
        BodyPartJoltTorsoFit.Fit fit=
                BodyPartJoltTorsoFit.fit(a,b,vertices,indices);
        if(fit==null)throw new AssertionError("Valid base fit missing X="+incline);
        BodyPartJoltMultiTorsoFit.Segments compound=
                BodyPartJoltMultiTorsoFit.fit(fit,vertices,indices);
        if(compound==null || compound.count()!=3)
            throw new AssertionError("Valid compound fit missing X="+incline);
        if(!(compound.radius[1]>compound.radius[0] &&
                compound.radius[1]>compound.radius[2]))
            throw new AssertionError("Multi-radius torso shape not preserved");
        for(int i=0;i<3;i++)
        {
            if(compound.witnesses[i]<12)
                throw new AssertionError("No skinned witnesses in region "+i);
            if(compound.radius[i]<0.05F || compound.radius[i]>0.23F)
                throw new AssertionError("Invalid radius "+i);
        }
        float[][] leaked=compound.starts();
        leaked[0][0]=120F;
        if(compound.a[0][0]==120F)
            throw new AssertionError("Caller mutated native-proxy geometry");
        if(BodyPartJoltMultiTorsoFit.fit(
                fit,new float[]{Float.NaN,0F,0F},new int[]{0,0,0})!=null)
            throw new AssertionError("NaN input passed torso fit");
        System.out.println("3-section skinned torso, X="+incline
                +" radii="+compound.radius[0]+"/"+compound.radius[1]+
                "/"+compound.radius[2]+": PASS");
    }

    public static void main(String[] args)
    {
        run(0F);
        run(80F);
    }
}
