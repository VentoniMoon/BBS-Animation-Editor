package com.example.examplemod;

/**
 * Skirt-only, three-zone thigh hull. A skirt hangs from a pinned belt; a
 * constant-width OBB near the hip can push its upper link into an umbrella.
 * Divide each genuine Emoticons upper-thigh OBB along its BONE-LOCAL Y axis
 * (so the profile follows gait rotation), reducing the width near the hip
 * while retaining full lower-thigh clearance.
 *
 * This changes only the passive shapes supplied to skirt Jolt chains and
 * the matching skirt visual solver. No actor morph, animation key, source
 * capture or ragdoll collider is changed.
 */
public final class SkirtLegCollisionProxy
{
    public static final int ZONES=3;
    private static final float TOP_WIDTH=.70F;
    private static final float MIDDLE_WIDTH=.85F;
    private static final float LOWER_WIDTH=1F;
    /** Small axial overlap prevents seams in the otherwise discrete taper. */
    private static final float AXIAL_OVERLAP=1.06F;

    private SkirtLegCollisionProxy(){}

    public static boolean eligible(String owner,float[] box)
    {
        if(!BodyPartJoltContactBudget.isUpperThigh(owner) ||
                box==null||box.length!=10)return false;
        for(float value:box)if(!Float.isFinite(value))return false;
        // Fail closed for implausible or oversized skin fit; the old broad
        // collider guard must still reject those, not disguise their errors.
        if(box[7]<.035F||box[9]<.035F||
                box[7]>.45F||box[9]>.45F||
                box[8]<.08F||box[8]>.55F)return false;
        float q2=box[3]*box[3]+box[4]*box[4]+
                box[5]*box[5]+box[6]*box[6];
        return q2>.25F&&q2<2.25F;
    }

    /**
     * The +Y end of the source bone frame is proximal (toward the belt).
     * We never determine this using world-space height: a raised thigh
     * must not flip its tapered profile as it rotates through horizontal.
     *
     * Boxes have [world xyz, quaternion xyzw, half XYZ] layout.
     * Returns null for invalid shape rather than inventing a leg volume.
     */
    public static float[][] split(float[] source)
    {
        if(source==null||source.length!=10)return null;
        for(float value:source)if(!Float.isFinite(value))return null;
        float halfY=source[8];
        if(source[7]<=0F||source[9]<=0F||halfY<=0F)return null;
        float x=source[3],y=source[4],z=source[5],w=source[6];
        float norm=(float)Math.sqrt(x*x+y*y+z*z+w*w);
        if(norm<.5F||norm>1.5F)return null;
        x/=norm;y/=norm;z/=norm;w/=norm;
        // Rotate local +Y by the normalized source quaternion.
        float axisX=2F*(x*y-z*w);
        float axisY=1F-2F*(x*x+z*z);
        float axisZ=2F*(y*z+x*w);
        float[][] parts=new float[ZONES][];
        for(int zone=0;zone<ZONES;zone++)
        {
            float[] b=source.clone();
            float offset=halfY*(2F-2F*zone)/3F;
            b[0]+=axisX*offset;
            b[1]+=axisY*offset;
            b[2]+=axisZ*offset;
            float width=zone==0?TOP_WIDTH:
                    zone==1?MIDDLE_WIDTH:LOWER_WIDTH;
            b[7]*=width;
            b[8]=halfY/3F*AXIAL_OVERLAP;
            b[9]*=width;
            parts[zone]=b;
        }
        return parts;
    }

    /**
     * Emoticons sometimes fits the same thigh as 3 nearly concentric boxes.
     * Keep the largest representative only when their centers, local axes
     * and extents agree. Distant separate pieces of one named bone survive.
     */
    public static boolean duplicate(float[] a,float[] b)
    {
        if(a==null||b==null||a.length!=10||b.length!=10)
            return false;
        float center2=0F;
        for(int i=0;i<3;i++)
        {
            if(!Float.isFinite(a[i])||!Float.isFinite(b[i]))return false;
            float d=a[i]-b[i];center2+=d*d;
        }
        if(center2>.08F*.08F)return false;
        for(int i=7;i<10;i++)
        {
            if(!Float.isFinite(a[i])||!Float.isFinite(b[i]) ||
                    Math.abs(a[i]-b[i])>.075F)return false;
        }
        float qa=0F,qb=0F,dot=0F;
        for(int i=3;i<7;i++)
        {
            qa+=a[i]*a[i];qb+=b[i]*b[i];dot+=a[i]*b[i];
        }
        if(qa<.25F||qb<.25F)return false;
        return Math.abs(dot)/(float)Math.sqrt(qa*qb)>.985F;
    }

    public static float volume(float[] b)
    {
        return 8F*b[7]*b[8]*b[9];
    }
}
