package com.example.examplemod;

/**
 * Pure Java8 regression fixture for an actual hair body1.geo.json seam.
 * Model coordinates are in the same scaled 1/16 units as Chameleon quads.
 */
public final class ChameleonWeldGeometrySmokeTest
{
    private static final float EPS=1E-5F;

    private static float[] identity()
    {
        return new float[] {
            1,0,0,0,
            0,1,0,0,
            0,0,1,0,
            0,0,0,1
        };
    }

    private static float[] zRotation(float degrees,float cx,float cy,float cz)
    {
        double angle=Math.toRadians(degrees);
        float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
        float[] m=identity();
        m[0]=c; m[1]=s;
        m[4]=-s;m[5]=c;
        m[12]=cx-c*cx+s*cy;
        m[13]=cy-s*cx-c*cy;
        m[14]=0F;
        return m;
    }

    private static float[][] face(float minX,float maxX,float y,
            float minZ,float maxZ)
    {
        return new float[][] {
            {minX/16F,y/16F,minZ/16F},
            {maxX/16F,y/16F,minZ/16F},
            {maxX/16F,y/16F,maxZ/16F},
            {minX/16F,y/16F,maxZ/16F}
        };
    }

    private static void near(float got,float expected)
    {
        if(Math.abs(got-expected)>EPS)
            throw new AssertionError(got+" != "+expected);
    }

    private static void verify(String name,float[][] corners,
            float[] source,float[] target)
    {
        ChameleonWeldGeometry.Seam result=ChameleonWeldGeometry.weld(
                identity(),identity(),source,target,corners,corners,0.5F,1E-4F);
        if(result==null)throw new AssertionError(name+" seam not resolved");
        for(int i=0;i<4;i++)
        {
            float[] a=ChameleonWeldGeometry.transform(
                    source,result.sourceLocal[i]);
            float[] b=ChameleonWeldGeometry.transform(
                    target,result.targetLocal[result.targetOrder[i]]);
            for(int k=0;k<3;k++)near(a[k],b[k]);
        }
    }

    public static void main(String[] args)
    {
        // Parent DOWN / child UP joint coordinates for the seven joints
        // in the user's hair body1.geo.json.
        float[][] leftTop=face(-4.08F,-2.08F,9F,-2.08F,-1.08F);
        float[][] leftBottom=face(-4.08F,-2.08F,6F,-2.08F,-1.08F);
        float[][] rightTop=face(2.08F,4.08F,9F,-2.08F,-1.08F);
        float[][] rightBottom=face(2.08F,4.08F,6F,-2.08F,-1.08F);
        float[][] backTop=face(-4.08F,4.08F,9F,0.92F,2.08F);
        float[][] backMid=face(-4.08F,4.08F,6F,0.92F,2.08F);
        float[][] backBottom=face(-4.08F,4.08F,3F,0.92F,2.08F);
        verify("left_strand_1 -> 2",leftTop,identity(),
                zRotation(42F,-3.08F/16F,9F/16F,0F));
        verify("left_strand_2 -> 3",leftBottom,identity(),
                zRotation(-30F,-3.08F/16F,6F/16F,0F));
        verify("right_strand_1 -> 2",rightTop,identity(),
                zRotation(-40F,3.08F/16F,9F/16F,0F));
        verify("right_strand_2 -> 3",rightBottom,identity(),
                zRotation(35F,3.08F/16F,6F/16F,0F));
        verify("back_strand_1 -> 2",backTop,identity(),
                zRotation(22F,0F,9F/16F,0F));
        verify("back_strand_2 -> 3",backMid,identity(),
                zRotation(-35F,0F,6F/16F,0F));
        verify("back_strand_3 -> 4",backBottom,identity(),
                zRotation(47F,0F,3F/16F,0F));

        // A rigidly translated/rotated actor still has coincident welded
        // world corners; the solver uses actual matrices, never Euler hacks.
        float[] common=zRotation(15F,0,0,0);
        common[12]=12F;common[13]=5F;common[14]=-3F;
        float[] target=zRotation(-55F,0,9F/16F,0);
        target[12]+=12F;target[13]+=5F;target[14]-=3F;
        verify("world transformed hair",backTop,common,target);

        // No change to the rest mesh when both bones are aligned.
        ChameleonWeldGeometry.Seam neutral=ChameleonWeldGeometry.weld(
                identity(),identity(),identity(),identity(),
                backTop,backTop,0.5F,1E-4F);
        if(neutral==null)throw new AssertionError("neutral seam missing");
        for(int n=0;n<4;n++)for(int k=0;k<3;k++)
            near(neutral.sourceLocal[n][k],backTop[n][k]);

        // The same position but wrong-sized face is NOT a matching seam.
        if(ChameleonWeldGeometry.weld(identity(),identity(),
                identity(),identity(),leftTop,rightTop,0.5F,1E-4F)!=null)
            throw new AssertionError("unrelated faces welded together");

        // The solver must neither modify source vertices nor introduce
        // physics changes. ParentShare controls which side of the seam
        // remains rigid (0 = parent unchanged, 1 = child unchanged).
        float[][] before=face(-4.08F,4.08F,9F,0.92F,2.08F);
        float[] swing=zRotation(25F,0,9F/16F,0);
        ChameleonWeldGeometry.Seam parentFixed=ChameleonWeldGeometry.weld(
                identity(),identity(),identity(),swing,
                before,before,0F,1E-4F);
        for(int n=0;n<4;n++)for(int k=0;k<3;k++)
        {
            near(parentFixed.sourceLocal[n][k],before[n][k]);
            near(before[n][k],backTop[n][k]);
        }
        System.out.println("Chameleon Weld: seven original hair-body seams PASS");
    }
}
