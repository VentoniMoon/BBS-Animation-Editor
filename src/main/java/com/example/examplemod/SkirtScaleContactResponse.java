package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scale-aware, contact-only compensation for the exact 20-panel Chameleon
 * skirt. A visually scaled-down (e.g. 0.85) attachment has less clearance
 * around unchanged Emoticons legs. Keep its authored scale untouched: only
 * the lower plate widths near REAL leg contacts receive a temporary, bounded
 * local boost. Jolt, the waistband and any non-skirt attachment are unchanged.
 *
 * This is a stateless function of the current frame's contacts, so seeking,
 * playback/export and re-rendering the same frame cannot accumulate scale.
 */
public final class SkirtScaleContactResponse
{
    private static final int PANELS=20;
    private static final float MIN_SUPPORTED_SCALE=.60F;
    private static final float NEIGHBOR_FALLOFF=.20F;
    private static final float FULL_CONTACT_DEPTH=.13F;

    private SkirtScaleContactResponse(){}

    /** Global Transform is already present in the skirt's world matrices.
     * This number ONLY controls extra collision response; never rescale
     * the Jolt OBBs or the Chameleon model a second time. */
    public static float horizontalScale(float scaleX,float scaleZ)
    {
        if(!Float.isFinite(scaleX)||!Float.isFinite(scaleZ)||
                scaleX<=0F||scaleZ<=0F)return 1F;
        return clamp(Math.min(scaleX,scaleZ),MIN_SUPPORTED_SCALE,1F);
    }

    /** Additional conservative surface allowance; only relevant after
     * the collider has passed the normal Emoticons raised-leg gate. */
    public static float surfaceMargin(float scale)
    {
        return .023F+Math.min(.021F,shortfall(scale)*.10F);
    }

    /** Local bends respond slightly earlier and stronger at small scales,
     * but the original angular limits in SkirtVisibleThighContacts remain. */
    public static float angleGain(float scale)
    {
        return 1F+Math.min(.25F,shortfall(scale)*1.5F);
    }

    public static float hemAngleCap(float scale)
    {
        return .60F+Math.min(.08F,shortfall(scale)*.50F);
    }

    /**
     * @param current  Existing adaptive seam widths after Jolt coupling.
     * @param depthByUpperBone Actual contact depth keyed by each strip's
     *                         UPPER bone (zero/nonexistent means no contact).
     * @return New width values; inputs are never changed. The baseline is
     *         bitwise preserved when no contact or scale >= 1.
     */
    public static Map<String,SkirtAdaptiveWidth.Width> widen(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,SkirtAdaptiveWidth.Width> current,
            Map<String,Float> depthByUpperBone,float visualScale)
    {
        Map<String,SkirtAdaptiveWidth.Width> out=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        if(current!=null)out.putAll(current);
        if(strips==null||strips.size()!=PANELS||current==null||
                current.size()!=PANELS*2||depthByUpperBone==null||
                depthByUpperBone.isEmpty()||shortfall(visualScale)<=.0001F)
            return out;

        float cx=0F,cz=0F;
        java.util.HashSet<String> names=new java.util.HashSet<String>();
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            if(strip==null||strip.upperWorld==null||
                    strip.upperWorld.length!=16||strip.upper==null||
                    strip.lower==null||!names.add(strip.upper)||
                    !names.add(strip.lower)||
                    !current.containsKey(strip.upper)||
                    !current.containsKey(strip.lower))
                return out;
            cx+=strip.upperWorld[12];
            cz+=strip.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;
        final float centerX=cx,centerZ=cz;

        List<SkirtClothPoseCoupler.Strip> sorted=
                new ArrayList<SkirtClothPoseCoupler.Strip>(strips);
        Collections.sort(sorted,new Comparator<SkirtClothPoseCoupler.Strip>()
        {
            @Override public int compare(SkirtClothPoseCoupler.Strip a,
                    SkirtClothPoseCoupler.Strip b)
            {
                double aa=Math.atan2(a.upperWorld[14]-centerZ,
                        a.upperWorld[12]-centerX);
                double bb=Math.atan2(b.upperWorld[14]-centerZ,
                        b.upperWorld[12]-centerX);
                int order=Double.compare(aa,bb);
                return order!=0?order:a.upper.compareTo(b.upper);
            }
        });

        float[] contact=new float[PANELS];
        int directHits=0;
        for(int i=0;i<PANELS;i++)
        {
            Float depth=depthByUpperBone.get(sorted.get(i).upper);
            if(depth!=null && Float.isFinite(depth.floatValue()) &&
                    depth.floatValue()>.002F)
            {
                contact[i]=clamp(depth.floatValue()/FULL_CONTACT_DEPTH,0F,1F);
                directHits++;
            }
        }
        // The previously observed "all 20 panels touching a thigh" case
        // indicates a huge/wrong collider, NOT a localized leg impact.
        // Never use it to inflate the entire skirt.
        if(directHits==0||directHits>12)return out;

        final float maximumExtra=.018F+.56F*shortfall(visualScale);
        for(int i=0;i<PANELS;i++)
        {
            float intensity=Math.max(contact[i],Math.max(
                    contact[(i+PANELS-1)%PANELS]*NEIGHBOR_FALLOFF,
                    contact[(i+1)%PANELS]*NEIGHBOR_FALLOFF));
            if(intensity<=.001F)continue;
            // Smoothstep: response reaches zero with zero derivative as
            // the thigh leaves the surface, preventing visible popping.
            intensity=intensity*intensity*(3F-2F*intensity);
            float localExtra=maximumExtra*intensity;
            SkirtClothPoseCoupler.Strip strip=sorted.get(i);
            SkirtAdaptiveWidth.Width upper=current.get(strip.upper);
            SkirtAdaptiveWidth.Width lower=current.get(strip.lower);
            if(upper==null||lower==null||
                    upper.axis!=SkirtAdaptiveWidth.widthAxis(strip.upper)||
                    lower.axis!=SkirtAdaptiveWidth.widthAxis(strip.lower)||
                    !Float.isFinite(upper.multiplier)||
                    !Float.isFinite(lower.multiplier))continue;
            float lowerWidth=Math.min(SkirtAdaptiveWidth.MAX_LOWER,
                    Math.max(1F,lower.multiplier)+localExtra);
            float upperWidth=Math.min(SkirtAdaptiveWidth.MAX_UPPER,
                    Math.max(1F,upper.multiplier)+localExtra*.13F);
            out.put(strip.lower,new SkirtAdaptiveWidth.Width(
                    lower.axis,lowerWidth));
            out.put(strip.upper,new SkirtAdaptiveWidth.Width(
                    upper.axis,upperWidth));
        }
        return out;
    }

    private static float shortfall(float scale)
    {
        if(!Float.isFinite(scale))return 0F;
        return Math.max(0F,1F-clamp(scale,MIN_SUPPORTED_SCALE,1F));
    }
    private static float clamp(float x,float lo,float hi)
    {return Math.max(lo,Math.min(hi,x));}
}
