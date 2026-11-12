package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Java 8: two rows of 20 textured skirt cuboids become a coherent ring. */
public final class SkirtContinuityRingSmokeTest
{
    private static final int N=20;
    private static final float R=.40F;
    private static final float EPS=1E-4F;
    private SkirtContinuityRingSmokeTest(){}

    public static void main(String[] args)
    {
        idleRingKeepsAuthoringGeometry();
        movingRingSharesNeighborEdges();
        largeImpactDoesNotTeleportPanels();
        incompleteRigDoesNotWeld();
        System.out.println("SkirtContinuityRingSmokeTest OK");
    }

    private static void idleRingKeepsAuthoringGeometry()
    {
        List<SkirtContinuityRing.Strip> strips=make(0F,-1);
        SkirtContinuityRing.Result stitched=SkirtContinuityRing.stitch(strips);
        require(stitched.lateralSeams>=30,
                "most of upper and lower lateral boundaries should join "+
                stitched.lateralSeams);
        require(stitched.verticalSeams>=18,
                "both rows should join vertically "+
                stitched.verticalSeams);
        require(stitched.joinedVertices>40,
                "no source cube corners were welded");
        for(SkirtContinuityRing.Strip strip:strips)
            for(SkirtContinuityRing.Cube c:new SkirtContinuityRing.Cube[]{
                    strip.upper,strip.lower})
            {
                float[][] joined=stitched.targets.get(c.name);
                require(joined!=null,"missing "+c.name);
                for(int i=0;i<8;i++)
                    require(distance(joined[i],c.posed[i])<.03F,
                            "rest seam displaced more than a pixel");
            }
    }

    private static void movingRingSharesNeighborEdges()
    {
        List<SkirtContinuityRing.Strip> strips=make(.065F,5);
        SkirtContinuityRing.Result stitched=SkirtContinuityRing.stitch(strips);
        require(stitched.lateralSeams>=30,
                "lateral topology lost in moving pose");
        require(stitched.joinedVertices>=60,
                "moving ring has no shared boundary vertices");
        // Each level's adjacent sides are ONE pose-space seam, although
        // the originating cubes were animated independently.
        for(int i=0;i<N;i++)
        {
            int j=(i+1)%N;
            for(int level=0;level<2;level++)
            {
                String a=name(i,level),b=name(j,level);
                Map<String,float[][]> adjusted=stitched.targets;
                for(int y=0;y<2;y++)for(int z=0;z<2;z++)
                    require(distance(adjusted.get(a)[4+y*2+z],
                            adjusted.get(b)[y*2+z])<.03F,
                            "ring seam broken at "+a+" -> "+b);
            }
            // The upper cube's LOW edge and the lower cube's HIGH edge
            // are also a synchronized pose for every geometric strip.
            for(int x=0;x<2;x++)for(int z=0;z<2;z++)
                require(distance(stitched.targets.get(name(i,0))[x*4+z],
                        stitched.targets.get(name(i,1))[x*4+2+z])<.03F,
                        "vertical seam detached at "+i);
        }
    }

    private static void largeImpactDoesNotTeleportPanels()
    {
        List<SkirtContinuityRing.Strip> strips=make(1.1F,0);
        SkirtContinuityRing.Result out=SkirtContinuityRing.stitch(strips);
        require(out.skippedGroups>0,"violent pose should reject unsafe stretch");
        for(SkirtContinuityRing.Strip strip:strips)
            for(SkirtContinuityRing.Cube cube:new SkirtContinuityRing.Cube[]{
                    strip.upper,strip.lower})
                for(int i=0;i<8;i++)
                    require(distance(out.targets.get(cube.name)[i],
                            cube.posed[i])<=.281F,
                            "pose continuity teleported cube vertices");
    }

    private static void incompleteRigDoesNotWeld()
    {
        List<SkirtContinuityRing.Strip> strips=make(0F,-1);
        strips.remove(strips.size()-1);
        SkirtContinuityRing.Result out=SkirtContinuityRing.stitch(strips);
        require(out.targets.isEmpty() && out.joinedVertices==0,
                "must not activate on arbitrary shorter attachments");
    }

    private static List<SkirtContinuityRing.Strip> make(float shift,int moving)
    {
        List<SkirtContinuityRing.Strip> out=
                new ArrayList<SkirtContinuityRing.Strip>();
        for(int i=0;i<N;i++)
        {
            SkirtContinuityRing.Cube upper=cube(i,0,shift,moving);
            SkirtContinuityRing.Cube lower=cube(i,1,shift,moving);
            out.add(new SkirtContinuityRing.Strip(upper,lower));
        }
        return out;
    }

    private static String name(int strip,int level)
    {
        return "panel"+strip+(level==0?"top":"bottom");
    }

    private static SkirtContinuityRing.Cube cube(
            int index,int level,float shift,int moving)
    {
        double angle=2*Math.PI*index/N;
        float rx=(float)Math.cos(angle),rz=(float)Math.sin(angle);
        float tx=-rz,tz=rx;
        float width=R*(float)Math.sin(Math.PI/N);
        float[] ys=level==0?new float[]{.18F,.38F}:
                new float[]{-.02F,.18F};
        float[][] rest=new float[8][3],pose=new float[8][3];
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)
        {
            float tangent=(x==0?-width:width);
            float radial=z==0?-.015F:.015F;
            int at=x*4+y*2+z;
            rest[at]=new float[]{
                    (R+radial)*rx+tangent*tx,
                    ys[y],(R+radial)*rz+tangent*tz};
            pose[at]=rest[at].clone();
            if(index==moving)
            {
                pose[at][0]+=rx*shift;
                pose[at][2]+=rz*shift;
            }
        }
        return new SkirtContinuityRing.Cube(name(index,level),rest,pose);
    }

    private static float distance(float[] a,float[] b)
    {
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
    private static void require(boolean cond,String message)
    {if(!cond)throw new AssertionError(message);}
}
