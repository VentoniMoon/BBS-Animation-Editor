package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visual stretch for the validated skirt.geo.json (20 panels, 2 bones each).
 * Native Jolt stays unchanged. Only horizontal width changes, never the
 * vertical bone length, waistband, source animation, or any other attachment.
 *
 * Measure extra adjacent-panel separation AFTER coupled Jolt swing, relative
 * to the same pair's clean authored pose. Ignore normal source seam spacing.
 */
public final class SkirtAdaptiveWidth
{
    public static final float MAX_LOWER=1.28F;
    public static final float MAX_UPPER=1.07F;
    private static final int PANELS=20;
    private static final float IGNORE_GAP=0.008F;
    private static final float RESPONSE=0.88F;

    /** Chameleon scale field: 0 means scaleX, 2 means scaleZ. */
    public static final class Width
    {
        public final int axis;
        public final float multiplier;

        public Width(int axis,float multiplier)
        {
            this.axis=axis;
            this.multiplier=multiplier;
        }
    }

    private static final class Panel
    {
        final SkirtClothPoseCoupler.Strip strip;
        final double angle;
        final float[] rest,solved;
        Panel(SkirtClothPoseCoupler.Strip strip,double angle,
                float[] rest,float[] solved)
        {
            this.strip=strip;this.angle=angle;this.rest=rest;this.solved=solved;
        }
    }

    private SkirtAdaptiveWidth(){}

    public static Map<String,Width> measure(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> coupled)
    {
        Map<String,Width> result=new LinkedHashMap<String,Width>();
        if(strips==null || strips.size()!=PANELS || coupled==null)
            return result;

        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null || s.tipWorld==null ||
                    !valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(s.tipWorld,3)||
                    !valid(coupled.get(s.upper),4)||
                    !valid(coupled.get(s.lower),4)||
                    widthAxis(s.upper)<0 || widthAxis(s.lower)<0)
                return Collections.emptyMap();
            cx+=s.upperWorld[12];
            cz+=s.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;
        List<Panel> ring=new ArrayList<Panel>();
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            float[] root=translation(s.upperWorld);
            float[] hinge=translation(s.lowerWorld);
            float[] upperSwing=coupled.get(s.upper);
            float[] lowerSwing=coupled.get(s.lower);
            float[] upperRotation=worldAxisAngle(s.upperWorld,upperSwing);
            float[] lowerRotation=worldAxisAngle(s.lowerWorld,lowerSwing);
            // Two linked pivots: the lower rotation takes place under the
            // upper (parent) bone's already rotated world-space basis.
            float[] first=rotate(sub(hinge,root),upperRotation);
            float[] last=rotate(rotate(sub(s.tipWorld,hinge),
                    lowerRotation),upperRotation);
            float[] hem=add(root,add(first,last));
            if(!valid(hem,3))return Collections.emptyMap();
            double angle=Math.atan2(root[2]-cz,root[0]-cx);
            ring.add(new Panel(s,angle,s.tipWorld,hem));
        }
        Collections.sort(ring,new Comparator<Panel>()
        {
            @Override public int compare(Panel a,Panel b)
            {
                int cmp=Double.compare(a.angle,b.angle);
                return cmp!=0?cmp:a.strip.upper.compareTo(b.strip.upper);
            }
        });

        float[] excess=new float[PANELS];
        float[] baseline=new float[PANELS];
        for(int i=0;i<PANELS;i++)
        {
            Panel a=ring.get(i),b=ring.get((i+1)%PANELS);
            float original=horizontal(a.rest,b.rest);
            float displaced=horizontal(a.solved,b.solved);
            if(!Float.isFinite(original)||!Float.isFinite(displaced) ||
                    original<.025F || original>1.2F)
                return Collections.emptyMap();
            baseline[i]=original;
            excess[i]=Math.max(0F,displaced-original-IGNORE_GAP);
        }
        for(int i=0;i<PANELS;i++)
        {
            Panel p=ring.get(i);
            int prev=(i+PANELS-1)%PANELS;
            float averageSpacing=.5F*(baseline[prev]+baseline[i]);
            float extra=.5F*(excess[prev]+excess[i]);
            float bend=Math.max(0F,Math.min(MAX_LOWER-1F,
                    RESPONSE*extra/Math.max(.08F,averageSpacing)));
            float lower=1F+bend;
            float upper=1F+Math.min(MAX_UPPER-1F,bend*.25F);
            result.put(p.strip.upper,new Width(widthAxis(p.strip.upper),upper));
            result.put(p.strip.lower,new Width(widthAxis(p.strip.lower),lower));
        }
        return result;
    }

    /** The exact source profile has 14 X-wide front/back panels and 6
     * Z-wide side panels (including their lower bones). Never stretch Y. */
    public static int widthAxis(String bone)
    {
        if(bone==null)return -1;
        switch(bone)
        {
            case "group14":case "group31":
            case "group15":case "group32":
            case "group21":case "group30":
            case "group13":case "group34":
            case "group12":case "group28":
            case "group11":case "group33":
                return 2;
            case "group2":case "group22":
            case "group6":case "group24":
            case "group3":case "group25":
            case "group4":case "group23":
            case "group5":case "group26":
            case "group18":case "group29":
            case "group17":case "group27":
            case "group7":case "group38":
            case "group16":case "group39":
            case "group8":case "group40":
            case "group9":case "group37":
            case "group10":case "group41":
            case "group20":case "group35":
            case "group19":case "group36":
                return 0;
            default:return -1;
        }
    }

    private static float[] worldAxisAngle(float[] m,float[] aa)
    {
        float x=m[0]*aa[0]+m[4]*aa[1]+m[8]*aa[2];
        float y=m[1]*aa[0]+m[5]*aa[1]+m[9]*aa[2];
        float z=m[2]*aa[0]+m[6]*aa[1]+m[10]*aa[2];
        float len=(float)Math.sqrt(x*x+y*y+z*z);
        if(len<1E-7F)return new float[] {1,0,0,0};
        float a=Math.max(-(float)Math.PI,Math.min((float)Math.PI,aa[3]));
        return new float[] {x/len,y/len,z/len,a};
    }

    private static float[] rotate(float[] vec,float[] aa)
    {
        float c=(float)Math.cos(aa[3]),s=(float)Math.sin(aa[3]);
        float x=aa[0],y=aa[1],z=aa[2];
        float dot=x*vec[0]+y*vec[1]+z*vec[2];
        return new float[] {
                c*vec[0]+s*(y*vec[2]-z*vec[1])+(1-c)*dot*x,
                c*vec[1]+s*(z*vec[0]-x*vec[2])+(1-c)*dot*y,
                c*vec[2]+s*(x*vec[1]-y*vec[0])+(1-c)*dot*z
        };
    }
    private static float[] translation(float[] m)
    { return new float[] {m[12],m[13],m[14]}; }
    private static float[] sub(float[] a,float[] b)
    { return new float[] {a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static float[] add(float[] a,float[] b)
    { return new float[] {a[0]+b[0],a[1]+b[1],a[2]+b[2]}; }
    private static float horizontal(float[] a,float[] b)
    {
        float x=a[0]-b[0],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+z*z);
    }
    private static boolean valid(float[] value,int size)
    {
        if(value==null||value.length!=size)return false;
        for(float f:value)if(!Float.isFinite(f))return false;
        return true;
    }
}
