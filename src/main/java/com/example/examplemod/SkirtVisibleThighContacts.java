package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview-only clearance for actual skirt panel SURFACES against actual
 * Emoticons leg geometry. Jolt solves the central 2-bone chains; broad flat
 * panels reach farther than those tiny central native capsules. In particular,
 * a 3D leg collider can be installed yet pass through the visible hem.
 *
 * Uses the same captured world-space fitted OBBs passed into native Jolt.
 * Rotates ONLY skirt panel bones around their animated waist anchors.
 * Does not move the actor, change animation keys, touch the native Jolt world,
 * invent missing colliders, or alter hair/ragdoll physics.
 */
public final class SkirtVisibleThighContacts
{
    private static final int PANELS=20;
    private static final float WIDTH=.0625F; // actual 2px wide panels / 16
    private static final float SURFACE_MARGIN=.023F;
    private static final float MAX_EXTRA_SWING=.95F;
    private static final float MAX_FINAL_SWING=1.3F;

    public static final class Result
    {
        public final Map<String,float[]> swings;
        public final int touchedPanels;
        public final float peakOverlap;
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap)
        {
            this.swings=swings;
            this.touchedPanels=touchedPanels;
            this.peakOverlap=peakOverlap;
        }
    }

    private static final class Leg
    {
        final float[] p;
        final float[][] basis=new float[3][];
        Leg(float[] p)
        {
            this.p=p;
            float[] q={p[3],p[4],p[5],p[6]};
            basis[0]=rotateQuaternion(new float[]{1,0,0},q);
            basis[1]=rotateQuaternion(new float[]{0,1,0},q);
            basis[2]=rotateQuaternion(new float[]{0,0,1},q);
        }
    }

    private static final class Point
    {
        final float[] p,axis;
        Point(float[] p,float[] axis){this.p=p;this.axis=axis;}
    }

    private SkirtVisibleThighContacts(){}

    public static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected)
    {
        Map<String,float[]> copy=cloneSwings(starting);
        if(strips==null||strips.size()!=PANELS||selected==null||
                selected.boxes==null||selected.owners==null)
            return new Result(copy,0,0F);
        List<Leg> legs=new ArrayList<Leg>();
        for(int i=0;i<Math.min(selected.boxes.length,selected.owners.size());i++)
        {
            if(!BodyPartJoltContactBudget.isLeg(selected.owners.get(i)))continue;
            float[] box=selected.boxes[i];
            if(valid(box,10) && box[7]>.01F && box[8]>.01F && box[9]>.01F)
                legs.add(new Leg(box));
        }
        if(legs.isEmpty())return new Result(copy,0,0F);
        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null||s.tipWorld==null||!valid(s.tipWorld,3)||
                    !valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(copy.get(s.upper),4)||!valid(copy.get(s.lower),4)||
                    SkirtAdaptiveWidth.widthAxis(s.lower)<0)
                return new Result(copy,0,0F);
            cx+=s.upperWorld[12];
            cz+=s.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;

        boolean[] touched=new boolean[PANELS];
        float peak=0F;
        // Multiple small projected steps avoid abrupt rotations from deep
        // penetration. A late/frame-skipped native solver remains bounded.
        float[] added=new float[PANELS];
        for(int iteration=0;iteration<3;iteration++)
        {
            float[] need=new float[PANELS];
            float currentPeak=0F;
            for(int i=0;i<PANELS;i++)
            {
                SkirtClothPoseCoupler.Strip s=strips.get(i);
                float[] root=origin(s.upperWorld);
                float rx=root[0]-cx,rz=root[2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                float[] outward={rx/rr,0F,rz/rr};
                float[] hinge=origin(s.lowerWorld);
                float[] r1=worldSwing(s.upperWorld,copy.get(s.upper));
                float[] r2=worldSwing(s.lowerWorld,copy.get(s.lower));
                float[] upper=rotate(sub(hinge,root),r1);
                float[] lower=rotate(rotate(sub(s.tipWorld,hinge),r2),r1);
                float[] pivot=add(root,upper);
                float[] topAxis=rotate(widthAxisWorld(s.upperWorld,s.upper),r1);
                float[] hemAxis=rotate(rotate(widthAxisWorld(s.lowerWorld,s.lower),r2),r1);
                Point[] samples={
                        new Point(lerp(root,pivot,.65F),topAxis),
                        new Point(lerp(root,pivot,.92F),topAxis),
                        new Point(add(pivot,mul(lower,.25F)),hemAxis),
                        new Point(add(pivot,mul(lower,.50F)),hemAxis),
                        new Point(add(pivot,mul(lower,.80F)),hemAxis),
                        new Point(add(pivot,lower),hemAxis)};
                float deepest=0F, angle=0F;
                for(Point sample:samples)
                {
                    float[] side=mul(normalize(sample.axis),WIDTH);
                    for(int edge=-1;edge<=1;edge++)
                    {
                        float[] p=add(sample.p,mul(side,edge));
                        for(Leg leg:legs)
                        {
                            float overlap=outwardPenetration(p,leg,outward);
                            if(overlap>deepest)
                            {
                                deepest=overlap;
                                float distance=Math.max(.16F,length(sub(sample.p,root)));
                                angle=(float)Math.atan(overlap/distance);
                            }
                        }
                    }
                }
                if(deepest>0F)
                {
                    need[i]=Math.min(.55F,angle);
                    peak=Math.max(peak,deepest);
                    currentPeak=Math.max(currentPeak,deepest);
                }
            }
            if(currentPeak<=.0001F)break;
            for(int i=0;i<PANELS;i++)
            {
                // Limited lateral propagation keeps neighboring cloth strips
                // aligned without the legacy ring coupler undoing the contact.
                float target=Math.max(need[i],Math.max(
                        need[(i+1)%PANELS]*.38F,
                        need[(i+PANELS-1)%PANELS]*.38F));
                if(target<.002F)continue;
                float step=Math.min(Math.min(.43F,target),
                        MAX_EXTRA_SWING-added[i]);
                if(step<=.001F)continue;
                SkirtClothPoseCoupler.Strip s=strips.get(i);
                float[] root=origin(s.upperWorld);
                float rx=root[0]-cx,rz=root[2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                // Down cross outward: rotating a downward hanging panel by
                // positive angle about this tangent moves the cloth outward.
                float[] tangent={-rz/rr,0F,rx/rr};
                float[] before=worldSwing(s.upperWorld,copy.get(s.upper));
                float[] after=compose(new float[]{tangent[0],tangent[1],tangent[2],step},before);
                if(after[3]>MAX_FINAL_SWING)continue;
                float[] local=localSwing(s.upperWorld,after);
                if(!valid(local,4))continue;
                copy.put(s.upper,local);
                touched[i]=true;
                added[i]+=step;
            }
        }
        int total=0;for(boolean b:touched)if(b)total++;
        return new Result(copy,total,peak);
    }

    /** A point must actually overlap the leg's FULL box. If it does,
     * request just enough *outward* motion to clear the far radial face. */
    private static float outwardPenetration(float[] point,Leg leg,float[] radial)
    {
        float[] d=sub(point,new float[]{leg.p[0],leg.p[1],leg.p[2]});
        for(int k=0;k<3;k++)
            if(Math.abs(dot(d,leg.basis[k]))>leg.p[7+k]+SURFACE_MARGIN)
                return 0F;
        float support=SURFACE_MARGIN;
        for(int k=0;k<3;k++)
            support+=Math.abs(dot(radial,leg.basis[k]))*leg.p[7+k];
        return Math.max(0F,Math.min(.25F,support-dot(d,radial)));
    }

    private static float[] worldSwing(float[] m,float[] aa)
    {
        float x=m[0]*aa[0]+m[4]*aa[1]+m[8]*aa[2];
        float y=m[1]*aa[0]+m[5]*aa[1]+m[9]*aa[2];
        float z=m[2]*aa[0]+m[6]*aa[1]+m[10]*aa[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,aa[3]};
    }

    private static float[] localSwing(float[] m,float[] world)
    {
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Float.isFinite(det)||Math.abs(det)<1E-7F)return null;
        float inv=1F/det;
        float x=((e*i-f*h)*world[0]+(c*h-b*i)*world[1]+(b*f-c*e)*world[2])*inv;
        float y=((f*g-d*i)*world[0]+(a*i-c*g)*world[1]+(c*d-a*f)*world[2])*inv;
        float z=((d*h-e*g)*world[0]+(b*g-a*h)*world[1]+(a*e-b*d)*world[2])*inv;
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return null;
        return new float[]{x/n,y/n,z/n,world[3]};
    }

    private static float[] widthAxisWorld(float[] m,String bone)
    {
        int axis=SkirtAdaptiveWidth.widthAxis(bone);
        return axis==0?new float[]{m[0],m[1],m[2]}:
                new float[]{m[8],m[9],m[10]};
    }

    private static float[] compose(float[] extra,float[] original)
    {
        float[] a=quaternion(extra),b=quaternion(original);
        float x=a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1];
        float y=a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0];
        float z=a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3];
        float w=a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,2F*(float)Math.atan2(n,Math.abs(w))};
    }

    private static float[] quaternion(float[] aa)
    {
        float h=aa[3]*.5F;
        float sin=(float)Math.sin(h),cos=(float)Math.cos(h);
        return new float[]{aa[0]*sin,aa[1]*sin,aa[2]*sin,cos};
    }

    private static float[] rotate(float[] vec,float[] aa)
    {
        float[] q=quaternion(aa);
        return rotateQuaternion(vec,q);
    }

    private static float[] rotateQuaternion(float[] v,float[] q)
    {
        float x=q[0],y=q[1],z=q[2],w=q[3];
        float tx=2F*(y*v[2]-z*v[1]);
        float ty=2F*(z*v[0]-x*v[2]);
        float tz=2F*(x*v[1]-y*v[0]);
        return new float[]{v[0]+w*tx+y*tz-z*ty,
                v[1]+w*ty+z*tx-x*tz,
                v[2]+w*tz+x*ty-y*tx};
    }

    private static float[] origin(float[] m)
    {return new float[]{m[12],m[13],m[14]};}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static float[] mul(float[] a,float s)
    {return new float[]{a[0]*s,a[1]*s,a[2]*s};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return add(a,mul(sub(b,a),t));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float length(float[] a)
    {return (float)Math.sqrt(dot(a,a));}
    private static float[] normalize(float[] a)
    {float n=length(a);return n<1E-7F?new float[]{0,0,0}:mul(a,1F/n);}
    private static boolean valid(float[] a,int size)
    {
        if(a==null||a.length!=size)return false;
        for(float x:a)if(!Float.isFinite(x))return false;
        return true;
    }
    private static Map<String,float[]> cloneSwings(Map<String,float[]> source)
    {
        Map<String,float[]> output=new LinkedHashMap<String,float[]>();
        if(source!=null)
            for(Map.Entry<String,float[]> e:source.entrySet())
                if(e.getKey()!=null&&e.getValue()!=null)
                    output.put(e.getKey(),e.getValue().clone());
        return output;
    }
}
