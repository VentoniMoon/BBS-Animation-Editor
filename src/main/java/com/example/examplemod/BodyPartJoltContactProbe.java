package com.example.examplemod;

/**
 * Read-only signed clearance probe for Jolt's actual oriented kinematic
 * Chameleon cubes. This is a diagnostic, NOT a collision solver or a
 * replacement for Jolt's narrow phase.
 *
 * A negative result means a point is inside the visual collider. Positive
 * means outside. Two symmetric strands should obtain symmetric clearances
 * against a symmetric chest collider when evaluated in the SAME world frame.
 * The test distinguishes a skewed source pose from a Jolt response error.
 */
public final class BodyPartJoltContactProbe
{
    private BodyPartJoltContactProbe() {}

    public static float signedDistance(float[] point,float[] box)
    {
        if(point==null || point.length!=3 || box==null || box.length!=10)
            throw new IllegalArgumentException("Expected xyz and box10");
        for(float v:point)if(!Float.isFinite(v))
            throw new IllegalArgumentException("Invalid xyz");
        for(float v:box)if(!Float.isFinite(v))
            throw new IllegalArgumentException("Invalid box");

        float x=box[3],y=box[4],z=box[5],w=box[6];
        float dx=point[0]-box[0],dy=point[1]-box[1],dz=point[2]-box[2];
        // Project world point onto box's three world-space local axes.
        float ax=(1F-2F*(y*y+z*z))*dx+
                2F*(x*y+w*z)*dy+2F*(x*z-w*y)*dz;
        float ay=2F*(x*y-w*z)*dx+
                (1F-2F*(x*x+z*z))*dy+2F*(y*z+w*x)*dz;
        float az=2F*(x*z+w*y)*dx+
                2F*(y*z-w*x)*dy+(1F-2F*(x*x+y*y))*dz;
        float px=Math.abs(ax)-box[7];
        float py=Math.abs(ay)-box[8];
        float pz=Math.abs(az)-box[9];
        float ex=Math.max(0F,px),ey=Math.max(0F,py),ez=Math.max(0F,pz);
        float exterior=(float)Math.sqrt(ex*ex+ey*ey+ez*ez);
        float interior=Math.min(0F,Math.max(px,Math.max(py,pz)));
        return exterior+interior;
    }

    public static final class Nearest
    {
        public final float clearance;
        public final int index;
        public Nearest(float clearance,int index)
        {
            this.clearance=clearance;
            this.index=index;
        }
    }

    /**
     * Native Jolt hair segment against installed oriented Box colliders.
     * Uses actual SOLVED physical endpoints + five samples along the link,
     * not Chameleon's pre-physics authored pivot positions. The computed
     * minimum is an approximate signed clearance, not a contact manifold.
     * Subtract the native hair capsule radius; this also exposes cases
     * where the line center stays outside yet the volume intersects.
     */
    public static Nearest nearestSolvedLink(float[] solvedLink,float[][] boxes)
    {
        if(solvedLink==null || solvedLink.length!=7 ||
                boxes==null || boxes.length==0)
            return new Nearest(Float.POSITIVE_INFINITY,-1);
        for(float v:solvedLink)
            if(!Float.isFinite(v))
                throw new IllegalArgumentException("Invalid native hair capsule");
        if(solvedLink[6]<=0F)
            throw new IllegalArgumentException("Invalid native hair radius");
        float minimum=Float.POSITIVE_INFINITY;
        int nearest=-1;
        for(int i=0;i<boxes.length;i++)
        {
            float[] box=boxes[i];
            if(box==null || box.length!=10)continue;
            for(int fraction=0;fraction<=4;fraction++)
            {
                float t=fraction*0.25F;
                float[] p=new float[]{
                        solvedLink[0]+t*(solvedLink[3]-solvedLink[0]),
                        solvedLink[1]+t*(solvedLink[4]-solvedLink[1]),
                        solvedLink[2]+t*(solvedLink[5]-solvedLink[2])};
                float distance=signedDistance(p,box)-solvedLink[6];
                if(distance<minimum){minimum=distance;nearest=i;}
            }
        }
        return new Nearest(minimum,nearest);
    }

    public static Nearest nearest(float[] point,float[][] boxes)
    {
        if(point==null || boxes==null || boxes.length==0)
            return new Nearest(Float.POSITIVE_INFINITY,-1);
        float min=Float.POSITIVE_INFINITY;
        int index=-1;
        for(int i=0;i<boxes.length;i++)
        {
            if(boxes[i]==null)continue;
            float distance=signedDistance(point,boxes[i]);
            if(distance<min){min=distance;index=i;}
        }
        return new Nearest(min,index);
    }
}
