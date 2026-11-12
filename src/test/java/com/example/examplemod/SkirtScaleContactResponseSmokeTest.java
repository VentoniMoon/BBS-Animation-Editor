package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java 8 scale-aware skirt contact regression. No Minecraft/JNI. */
public final class SkirtScaleContactResponseSmokeTest
{
    private static final String[][] BONES={
        {"group2","group22"},{"group6","group24"},
        {"group3","group25"},{"group4","group23"},
        {"group5","group26"},{"group18","group29"},
        {"group17","group27"},{"group7","group38"},
        {"group16","group39"},{"group8","group40"},
        {"group9","group37"},{"group10","group41"},
        {"group20","group35"},{"group19","group36"},
        {"group14","group31"},{"group15","group32"},
        {"group21","group30"},{"group13","group34"},
        {"group12","group28"},{"group11","group33"}
    };
    private SkirtScaleContactResponseSmokeTest(){}

    public static void main(String[] args)
    {
        scaleDoesNotChangeExistingModelOrBody();
        noContactMeansNoChangeAt085();
        compactScaleWidensOnlyRealLocalThighContact();
        skipFullRingFalsePositive();
        preserveAuthoredWidthCaps();
        System.out.println("SkirtScaleContactResponseSmokeTest OK");
    }

    private static void scaleDoesNotChangeExistingModelOrBody()
    {
        close(SkirtScaleContactResponse.horizontalScale(.85F,.85F),
                .85F,.00001F,"compact global scale");
        close(SkirtScaleContactResponse.horizontalScale(1F,1F),
                1F,.00001F,"normal global scale");
        close(SkirtScaleContactResponse.horizontalScale(.85F,1F),
                .85F,.00001F,"narrow horizontal dimension");
        close(SkirtScaleContactResponse.horizontalScale(0F,1F),
                1F,.00001F,"invalid model scale must safely opt out");
        need(SkirtScaleContactResponse.surfaceMargin(.85F)>
                SkirtScaleContactResponse.surfaceMargin(1F),
                "compact skirt needs slightly earlier contact");
        need(SkirtScaleContactResponse.angleGain(.85F)>
                SkirtScaleContactResponse.angleGain(1F),
                "compact skirt needs stronger LOCAL correction");
        need(SkirtScaleContactResponse.hemAngleCap(.85F)<
                .69F,"prevent umbrella bend");
    }

    private static void noContactMeansNoChangeAt085()
    {
        Fixture f=new Fixture();
        Map<String,SkirtAdaptiveWidth.Width> restored=
                SkirtScaleContactResponse.widen(f.strips,f.widths,
                        new LinkedHashMap<String,Float>(),.85F);
        checkEqualWidths(f.widths,restored,
                "rest pose must be unchanged even at scale .85");
        Map<String,Float> contact=new LinkedHashMap<String,Float>();
        contact.put(BONES[0][0],Float.valueOf(.13F));
        Map<String,SkirtAdaptiveWidth.Width> normal=
                SkirtScaleContactResponse.widen(f.strips,f.widths,contact,1F);
        checkEqualWidths(f.widths,normal,
                "normal scale keeps legacy seam width");
        need(f.widths.size()==40,"original width map mutated");
    }

    private static void compactScaleWidensOnlyRealLocalThighContact()
    {
        Fixture f=new Fixture();
        Map<String,Float> contact=new LinkedHashMap<String,Float>();
        contact.put(BONES[0][0],Float.valueOf(.13F));
        Map<String,SkirtAdaptiveWidth.Width> widened=
                SkirtScaleContactResponse.widen(f.strips,f.widths,contact,.85F);
        need(widened.size()==40,"all two-link panel widths retained");
        float boost=widened.get(BONES[0][1]).multiplier-1F;
        need(boost>.08F&&boost<.12F,
                "compact .85 should widen contacted lower hem locally: "+boost);
        need(widened.get(BONES[0][0]).multiplier>1F&&
                widened.get(BONES[0][0]).multiplier<=1.07F,
                "upper section must move less than the hem");
        close(widened.get(BONES[10][1]).multiplier,1F,.00001F,
                "opposite panel must not flare");
        int changed=0;
        for(String[] bones:BONES)
            if(widened.get(bones[1]).multiplier>1.001F)changed++;
        need(changed==3,"direct panel + only two angular neighbors: "+changed);
        close(f.widths.get(BONES[0][1]).multiplier,1F,.00001F,
                "input width map was modified");
        // The same frame must generate exactly the same values without
        // any hidden cached/stale response from the last animation tick.
        Map<String,SkirtAdaptiveWidth.Width> repeated=
                SkirtScaleContactResponse.widen(f.strips,f.widths,contact,.85F);
        checkEqualWidths(widened,repeated,
                "rerender must not progressively inflate garment");
    }

    private static void skipFullRingFalsePositive()
    {
        Fixture f=new Fixture();
        Map<String,Float> bad=new LinkedHashMap<String,Float>();
        for(String[] pair:BONES)bad.put(pair[0],Float.valueOf(.20F));
        Map<String,SkirtAdaptiveWidth.Width> guarded=
                SkirtScaleContactResponse.widen(f.strips,f.widths,bad,.85F);
        checkEqualWidths(f.widths,guarded,
                "all-20-panel false contact must not inflate skirt");
    }

    private static void preserveAuthoredWidthCaps()
    {
        Fixture f=new Fixture();
        Map<String,SkirtAdaptiveWidth.Width> nearLimit=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        for(Map.Entry<String,SkirtAdaptiveWidth.Width> e:f.widths.entrySet())
        {
            boolean lower=e.getKey().equals(BONES[0][1]);
            float base=lower?1.26F:1.04F;
            nearLimit.put(e.getKey(),new SkirtAdaptiveWidth.Width(
                    e.getValue().axis,base));
        }
        Map<String,Float> pressure=new LinkedHashMap<String,Float>();
        pressure.put(BONES[0][0],Float.valueOf(5F));
        Map<String,SkirtAdaptiveWidth.Width> out=
                SkirtScaleContactResponse.widen(f.strips,nearLimit,
                        pressure,.75F);
        close(out.get(BONES[0][1]).multiplier,SkirtAdaptiveWidth.MAX_LOWER,
                .00001F,"hem width hard cap");
        need(out.get(BONES[0][0]).multiplier<=SkirtAdaptiveWidth.MAX_UPPER,
                "upper width cap");
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=
                new ArrayList<SkirtClothPoseCoupler.Strip>();
        final Map<String,SkirtAdaptiveWidth.Width> widths=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        Fixture()
        {
            for(int i=0;i<BONES.length;i++)
            {
                double angle=2D*Math.PI*i/BONES.length;
                float x=.35F*(float)Math.cos(angle);
                float z=.35F*(float)Math.sin(angle);
                String upper=BONES[i][0],lower=BONES[i][1];
                strips.add(new SkirtClothPoseCoupler.Strip(
                        upper,lower,matrix(x,0,z),matrix(x,-.16F,z),
                        new float[]{x,-.36F,z}));
                widths.put(upper,new SkirtAdaptiveWidth.Width(
                        SkirtAdaptiveWidth.widthAxis(upper),1F));
                widths.put(lower,new SkirtAdaptiveWidth.Width(
                        SkirtAdaptiveWidth.widthAxis(lower),1F));
            }
        }
    }
    private static float[] matrix(float x,float y,float z)
    {
        return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,x,y,z,1};
    }
    private static void checkEqualWidths(
            Map<String,SkirtAdaptiveWidth.Width> a,
            Map<String,SkirtAdaptiveWidth.Width> b,String message)
    {
        need(a.size()==b.size(),message+" (size)");
        for(Map.Entry<String,SkirtAdaptiveWidth.Width> entry:a.entrySet())
        {
            SkirtAdaptiveWidth.Width other=b.get(entry.getKey());
            need(other!=null && entry.getValue().axis==other.axis,
                    message+" (axis)");
            close(other.multiplier,entry.getValue().multiplier,
                    .00001F,message+" bone="+entry.getKey());
        }
    }
    private static void close(float actual,float expected,float error,String label)
    {need(Math.abs(actual-expected)<=error,label+": "+actual);}
    private static void need(boolean value,String reason)
    {if(!value)throw new AssertionError(reason);}
}
