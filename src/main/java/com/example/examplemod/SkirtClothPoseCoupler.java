package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-only coupling for the exact 20 two-link Chameleon skirt.
 *
 * The current native prototype owns one Jolt world per strip.  It cannot
 * constrain neighboring panels.  Before writing back their local swings,
 * this class projects all 20 into a common WORLD orientation, limits violent
 * motion, and constrains differences between neighboring panels.  Crucially,
 * this does not average mirrored rear LOCAL axes with front LOCAL axes.
 *
 * The animated waist/anchors are never changed. No NBT, key or Jolt JNI
 * changes, so the existing Java 8 experimental JAR is still supported.
 * This is a conservative visual bridge, not a full cloth collision solver.
 */
public final class SkirtClothPoseCoupler
{
    private static final int PANELS=20;
    private static final float[] MAX_ANGLE={0.40F,0.30F};
    private static final float[] SEAM_DELTA={0.12F,0.16F};

    public static final class Strip
    {
        public final String upper,lower;
        public final float[] upperWorld,lowerWorld;

        public Strip(String upper,String lower,float[] upperWorld,float[] lowerWorld)
        {
            this.upper=upper;
            this.lower=lower;
            this.upperWorld=upperWorld;
            this.lowerWorld=lowerWorld;
        }
    }

    private static final class Panel
    {
        final Strip strip;
        final double polar;
        final float[][] world=new float[2][];
        Panel(Strip strip,double polar)
        {
            this.strip=strip;
            this.polar=polar;
        }
    }

    private SkirtClothPoseCoupler(){}

    /** Return independent cloned samples if the entire valid skirt is absent. */
    public static Map<String,float[]> couple(List<Strip> strips,
            Map<String,float[]> raw)
    {
        Map<String,float[]> original=copy(raw);
        if(strips==null||strips.size()!=PANELS||raw==null)return original;
        try
        {
            double cx=0D,cz=0D;
            java.util.HashSet<String> names=new java.util.HashSet<String>();
            for(Strip s:strips)
            {
                if(s==null||s.upper==null||s.lower==null||
                        !names.add(s.upper)||!names.add(s.lower)||
                        !validMatrix(s.upperWorld)||!validMatrix(s.lowerWorld)||
                        !validSwing(raw.get(s.upper))||
                        !validSwing(raw.get(s.lower)))
                    return original;
                cx+=s.upperWorld[12];
                cz+=s.upperWorld[14];
            }
            cx/=PANELS;
            cz/=PANELS;
            final List<Panel> ring=new ArrayList<Panel>();
            for(Strip s:strips)
                ring.add(new Panel(s,Math.atan2(s.upperWorld[14]-cz,
                        s.upperWorld[12]-cx)));
            Collections.sort(ring,new Comparator<Panel>()
            {
                @Override public int compare(Panel a,Panel b)
                {
                    int angle=Double.compare(a.polar,b.polar);
                    return angle!=0?angle:a.strip.upper.compareTo(b.strip.upper);
                }
            });
            for(Panel p:ring)
            {
                p.world[0]=clamp(toWorldVector(p.strip.upperWorld,
                        raw.get(p.strip.upper)),MAX_ANGLE[0]);
                p.world[1]=clamp(toWorldVector(p.strip.lowerWorld,
                        raw.get(p.strip.lower)),MAX_ANGLE[1]);
            }

            // Damp discontinuities without forcing the entire ring rigid.
            for(int level=0;level<2;level++)
            {
                for(int iteration=0;iteration<4;iteration++)
                {
                    float[][] next=new float[PANELS][3];
                    for(int i=0;i<PANELS;i++)
                    {
                        float[] self=ring.get(i).world[level];
                        float[] left=ring.get((i+PANELS-1)%PANELS).world[level];
                        float[] right=ring.get((i+1)%PANELS).world[level];
                        for(int axis=0;axis<3;axis++)
                            next[i][axis]=.60F*self[axis]+
                                    .20F*(left[axis]+right[axis]);
                        next[i]=clamp(next[i],MAX_ANGLE[level]);
                    }
                    for(int i=0;i<PANELS;i++)
                        ring.get(i).world[level]=next[i];
                }
                // A hard neighboring angular difference protects against
                // a single native segment being expelled by a body collider.
                for(int iteration=0;iteration<10;iteration++)
                {
                    for(int i=0;i<PANELS;i++)
                    {
                        float[] a=ring.get(i).world[level];
                        float[] b=ring.get((i+1)%PANELS).world[level];
                        float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
                        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                        if(length<=SEAM_DELTA[level])continue;
                        float fraction=.5F*(length-SEAM_DELTA[level])/length;
                        for(int axis=0;axis<3;axis++)
                        {
                            float delta=(a[axis]-b[axis])*fraction;
                            a[axis]-=delta;
                            b[axis]+=delta;
                        }
                    }
                }
            }
            Map<String,float[]> coupled=copy(raw);
            for(Panel p:ring)
            {
                coupled.put(p.strip.upper,toLocalSwing(
                        p.strip.upperWorld,p.world[0]));
                coupled.put(p.strip.lower,toLocalSwing(
                        p.strip.lowerWorld,p.world[1]));
            }
            return coupled;
        }
        catch(IllegalArgumentException invalid)
        {
            return original; // never break existing physics for other models
        }
    }

    private static float[] toWorldVector(float[] matrix,float[] swing)
    {
        float angle=Math.max(-(float)Math.PI,
                Math.min((float)Math.PI,swing[3]));
        float x=swing[0],y=swing[1],z=swing[2];
        float[] axis={
                matrix[0]*x+matrix[4]*y+matrix[8]*z,
                matrix[1]*x+matrix[5]*y+matrix[9]*z,
                matrix[2]*x+matrix[6]*y+matrix[10]*z};
        float size=norm(axis);
        if(size<1E-7F)return new float[3];
        return new float[] {axis[0]*angle/size,
                axis[1]*angle/size,axis[2]*angle/size};
    }

    private static float[] toLocalSwing(float[] m,float[] v)
    {
        float angle=norm(v);
        if(angle<1E-7F)return new float[] {1F,0F,0F,0F};
        // Invert the complete native matrix basis, including authored rear
        // Y=180 and possible nonuniform/negative attachment scale.
        float a=m[0],b=m[4],c=m[8];
        float d=m[1],e=m[5],f=m[9];
        float g=m[2],h=m[6],i=m[10];
        float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Float.isFinite(det)||Math.abs(det)<1E-7F)
            throw new IllegalArgumentException("Singular skirt basis");
        float inv=1F/det;
        float x=((e*i-f*h)*v[0]+(c*h-b*i)*v[1]+(b*f-c*e)*v[2])*inv;
        float y=((f*g-d*i)*v[0]+(a*i-c*g)*v[1]+(c*d-a*f)*v[2])*inv;
        float z=((d*h-e*g)*v[0]+(b*g-a*h)*v[1]+(a*e-b*d)*v[2])*inv;
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        if(length<1E-7F||!Float.isFinite(length))
            throw new IllegalArgumentException("Invalid skirt local axis");
        return new float[] {x/length,y/length,z/length,angle};
    }

    private static float[] clamp(float[] v,float max)
    {
        float length=norm(v);
        if(length<=max)return v.clone();
        float scale=max/length;
        return new float[] {v[0]*scale,v[1]*scale,v[2]*scale};
    }

    private static float norm(float[] v)
    {
        return (float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
    }

    private static boolean validMatrix(float[] m)
    {
        if(m==null||m.length!=16)return false;
        for(float f:m)if(!Float.isFinite(f))return false;
        return true;
    }

    private static boolean validSwing(float[] q)
    {
        if(q==null||q.length!=4)return false;
        for(float f:q)if(!Float.isFinite(f))return false;
        return true;
    }

    private static Map<String,float[]> copy(Map<String,float[]> raw)
    {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        if(raw!=null)
            for(Map.Entry<String,float[]> e:raw.entrySet())
                if(e.getValue()!=null)result.put(e.getKey(),e.getValue().clone());
        return result;
    }
}
