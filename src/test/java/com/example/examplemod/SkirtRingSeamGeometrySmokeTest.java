package com.example.examplemod;

/** Pure Java 8 tests for circumferential skirt ring seams. */
public final class SkirtRingSeamGeometrySmokeTest
{
    private SkirtRingSeamGeometrySmokeTest(){}

    public static void main(String[] args)
    {
        coincidentRestEdgesShareAnimatedWorldLine();
        reverseWindingStillSeams();
        boundExtremeStretchWithoutUmbrella();
        rejectFarUnrelatedModelEdges();
        rejectSingularOrMalformedFrames();
        System.out.println("SkirtRingSeamGeometrySmokeTest OK");
    }

    private static void coincidentRestEdgesShareAnimatedWorldLine()
    {
        float[] id=identity();
        float[] offset=translation(.10F,.10F,0F);
        SkirtRingSeamGeometry.Plan p=SkirtRingSeamGeometry.plan(
                id,id,id,offset,
                edge(.1F,0F),edge(.1F,0F));
        need(p!=null,"matched vertical ring seam");
        for(int k=0;k<2;k++)
        {
            float[] a=ChameleonWeldGeometry.transform(id,p.sourceLocal[k]);
            float[] b=ChameleonWeldGeometry.transform(offset,p.targetLocal[k]);
            close(distance(a,b),0F,.0001F,"seam endpoint must be common");
        }
    }

    private static void reverseWindingStillSeams()
    {
        float[] id=identity();
        SkirtRingSeamGeometry.Edge a=edge(.1F,0F);
        SkirtRingSeamGeometry.Edge reverse=new SkirtRingSeamGeometry.Edge(
                new float[]{.1F,-.3F,0F},new float[]{.1F,0F,0F});
        SkirtRingSeamGeometry.Plan p=SkirtRingSeamGeometry.plan(
                id,id,id,translation(.05F,0F,0F),a,reverse);
        need(p!=null&&p.reverse,"reverse source corner order");
        close(p.remainingGap,0F,.0001F,"reverse endpoint alignment");
    }

    private static void boundExtremeStretchWithoutUmbrella()
    {
        float[] id=identity();
        float[] offset=translation(.35F,.02F,0F);
        SkirtRingSeamGeometry.Plan p=SkirtRingSeamGeometry.plan(
                id,id,id,offset,edge(0F,0F),edge(0F,0F));
        need(p!=null,"moderately widened pose remains valid");
        for(int k=0;k<2;k++)
        {
            float[] src=edge(0F,0F).points[k];
            float[] moved=ChameleonWeldGeometry.transform(id,
                    p.sourceLocal[k]);
            need(distance(src,moved)<=
                    SkirtRingSeamGeometry.MAX_VERTEX_MOVE+.0001F,
                    "bounded source cube edge displacement");
            float[] other=ChameleonWeldGeometry.transform(offset,
                    p.targetLocal[k]);
            float[] original=ChameleonWeldGeometry.transform(offset,src);
            need(distance(original,other)<=
                    SkirtRingSeamGeometry.MAX_VERTEX_MOVE+.0001F,
                    "bounded neighbor displacement");
        }
        need(p.remainingGap>.1F,"must not stretch a huge cloth gap into rubber");
    }

    private static void rejectFarUnrelatedModelEdges()
    {
        float[] id=identity();
        need(SkirtRingSeamGeometry.plan(id,translation(2F,0F,0F),
                id,id,edge(0F,0F),edge(0F,0F))==null,
                "rest-neighbor validation must prevent false seam");
    }

    private static void rejectSingularOrMalformedFrames()
    {
        float[] singular=identity();
        singular[0]=0F;
        need(SkirtRingSeamGeometry.plan(identity(),identity(),
                singular,identity(),edge(0F,0F),edge(0F,0F))==null,
                "singular inverse should fail closed");
        need(SkirtRingSeamGeometry.plan(identity(),identity(),
                identity(),identity(),null,edge(0F,0F))==null,
                "incomplete ring must fail closed");
    }

    private static SkirtRingSeamGeometry.Edge edge(float x,float z)
    {
        return new SkirtRingSeamGeometry.Edge(
                new float[]{x,0F,z},new float[]{x,-.3F,z});
    }
    private static float[] identity()
    {
        return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    }
    private static float[] translation(float x,float y,float z)
    {
        float[] a=identity();a[12]=x;a[13]=y;a[14]=z;return a;
    }
    private static float distance(float[] a,float[] b)
    {
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
    private static void need(boolean yes,String message)
    {if(!yes)throw new AssertionError(message);}
    private static void close(float a,float b,float tol,String label)
    {need(Math.abs(a-b)<=tol,label+" "+a+" != "+b);}
}
