package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Java 8 regression for real skirt.geo.json axes and bounded adaptive width. */
public final class SkirtAdaptiveWidthSmokeTest
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

    private SkirtAdaptiveWidthSmokeTest(){}

    public static void main(String[] args)
    {
        testSourceWidthAxes();
        testStaticAndSinglePanelGap();
        testMissingTipNeverStretches();
        testChameleonLocalScaleOnly();
        System.out.println("SkirtAdaptiveWidthSmokeTest OK");
    }

    private static void testSourceWidthAxes()
    {
        for(int i=0;i<BONES.length;i++)
            for(String name:BONES[i])
                expect(SkirtAdaptiveWidth.widthAxis(name)==(i>=14?2:0),
                        "wrong real geo width axis: "+name);
        expect(SkirtAdaptiveWidth.widthAxis("ALL")==-1,
                "the waistband must not stretch");
        expect(SkirtAdaptiveWidth.widthAxis("hair_1")==-1,
                "hair is not cloth");
    }

    private static void testStaticAndSinglePanelGap()
    {
        Fixture f=new Fixture();
        Map<String,SkirtAdaptiveWidth.Width> idle=
                SkirtAdaptiveWidth.measure(f.strips,f.raw);
        expect(idle.size()==40,"static profile should contain 40 widths");
        for(SkirtAdaptiveWidth.Width v:idle.values())
            near(v.multiplier,1F,.00001F,"static pose must not stretch");
        // Wide walking lunge bends ONE panel sharply.  Test that only
        // the necessary nearby seams widen and global scale remains bounded.
        f.raw.put(BONES[0][0],new float[] {1F,0F,0F,.4F});
        Map<String,SkirtAdaptiveWidth.Width> moving=
                SkirtAdaptiveWidth.measure(f.strips,f.raw);
        expect(moving.size()==40,"missing dynamic widths");
        float maximum=1F;
        int wide=0;
        for(int i=0;i<BONES.length;i++)
        {
            SkirtAdaptiveWidth.Width root=moving.get(BONES[i][0]);
            SkirtAdaptiveWidth.Width leaf=moving.get(BONES[i][1]);
            expect(root.axis==(i>=14?2:0),"root stretch wrong axis");
            expect(leaf.axis==(i>=14?2:0),"leaf stretch wrong axis");
            expect(root.multiplier>=1F&&root.multiplier<=1.07001F,
                    "upper skirt stretched excessively");
            expect(leaf.multiplier>=1F&&leaf.multiplier<=1.28001F,
                    "lower skirt stretched excessively");
            maximum=Math.max(maximum,leaf.multiplier);
            if(leaf.multiplier>1.001F)wide++;
        }
        expect(maximum>1.01F,"no stretch under single-panel lunge");
        expect(wide>=1&&wide<=8,"stretch should remain local: "+wide);
    }

    private static void testMissingTipNeverStretches()
    {
        Fixture f=new Fixture();
        SkirtClothPoseCoupler.Strip s=f.strips.remove(0);
        f.strips.add(new SkirtClothPoseCoupler.Strip(
                s.upper,s.lower,s.upperWorld,s.lowerWorld));
        expect(SkirtAdaptiveWidth.measure(f.strips,f.raw).isEmpty(),
                "must skip incomplete native geometry");
    }

    public static final class FakeMorph
    { public final FakePose pose=new FakePose(); }
    public static final class FakePose
    { public final Map<String,FakeTransform> bones=new HashMap<String,FakeTransform>(); }
    public static final class FakeTransform
    {
        public float rotateX,rotateY,rotateZ;
        public float scaleX=1.3F,scaleY=1.2F,scaleZ=.8F;
    }

    private static void testChameleonLocalScaleOnly()
    {
        FakeMorph morph=new FakeMorph();
        FakeTransform front=new FakeTransform(),side=new FakeTransform();
        morph.pose.bones.put("group22",front);
        morph.pose.bones.put("group31",side);
        expect(BodyPartChameleonPhysicsPose.multiplyLocalWidth(morph,
                "group22",new SkirtAdaptiveWidth.Width(0,1.25F)),
                "front scale should be applied");
        expect(BodyPartChameleonPhysicsPose.multiplyLocalWidth(morph,
                "group31",new SkirtAdaptiveWidth.Width(2,1.2F)),
                "side scale should be applied");
        near(front.scaleX,1.625F,.00001F,"front X width");
        near(front.scaleY,1.2F,.00001F,"front Y length");
        near(front.scaleZ,.8F,.00001F,"front Z depth");
        near(side.scaleZ,.96F,.00001F,"side Z width");
        near(side.scaleX,1.3F,.00001F,"side X depth");
        near(side.scaleY,1.2F,.00001F,"side Y length");
        expect(!BodyPartChameleonPhysicsPose.multiplyLocalWidth(morph,
                "group22",new SkirtAdaptiveWidth.Width(1,1.1F)),
                "vertical axis must be rejected");
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=new ArrayList<
                SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> raw=new HashMap<String,float[]>();

        Fixture()
        {
            for(int i=0;i<BONES.length;i++)
            {
                double phi=2*Math.PI*i/BONES.length;
                float x=.42F*(float)Math.cos(phi);
                float z=.42F*(float)Math.sin(phi);
                float[] upper=matrix(x,0F,z,i>=7&&i<14);
                float[] lower=matrix(x,-.15F,z,i>=7&&i<14);
                float[] tip={x,-.35F,z};
                strips.add(new SkirtClothPoseCoupler.Strip(
                        BONES[i][0],BONES[i][1],upper,lower,tip));
                raw.put(BONES[i][0],new float[] {1F,0F,0F,0F});
                raw.put(BONES[i][1],new float[] {1F,0F,0F,0F});
            }
        }
    }

    private static float[] matrix(float x,float y,float z,boolean reverse)
    {
        float[] m=new float[16];
        m[0]=reverse?-1F:1F;
        m[5]=1F;
        m[10]=reverse?-1F:1F;
        m[15]=1F;
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }

    private static void near(float value,float expected,float tolerance,String message)
    { expect(Math.abs(value-expected)<=tolerance,message+" "+value); }

    private static void expect(boolean ok,String message)
    { if(!ok)throw new AssertionError(message); }
}
