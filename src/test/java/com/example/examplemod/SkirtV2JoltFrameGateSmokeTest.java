package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java 8 regression for the skirt V2 all-or-nothing freeze. */
public final class SkirtV2JoltFrameGateSmokeTest
{
    private static final List<String[]> STRANDS=new ArrayList<String[]>();
    static
    {
        for(int i=0;i<20;i++)
            STRANDS.add(new String[]{"upper"+i,"lower"+i});
    }

    public static void main(String[] args)
    {
        fullRingEntersClothSolver();
        missingOnePairDoesNotFreezeNineteenOtherPairs();
        neverPublishHalfATornStrand();
        duplicateAndInvalidPoseFailClosed();
        extraBoneDoesNotSneakIntoFullRing();
        System.out.println("SkirtV2JoltFrameGateSmokeTest OK");
    }

    private static Map<String,float[]> full()
    {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(int i=0;i<20;i++)
        {
            result.put("upper"+i,new float[]{0,0,1,.1F*i});
            result.put("lower"+i,new float[]{1,0,0,.2F*i});
        }
        return result;
    }

    private static void fullRingEntersClothSolver()
    {
        Map<String,float[]> raw=full();
        SkirtV2JoltFrameGate.Decision d=
                SkirtV2JoltFrameGate.assess(STRANDS,raw,20);
        need(d.fullRing && d.validPairs==20 && d.recoveredBones==40,
                "a full 20x2 rig must enter cloth V2");
        need(d.missingPairs.isEmpty(),"no missing strands");
        raw.get("upper0")[3]=99F;
        need(d.recoveredSwings.get("upper0")[3]==0F,
                "captured Jolt poses must be defensive clones");
    }

    private static void missingOnePairDoesNotFreezeNineteenOtherPairs()
    {
        Map<String,float[]> raw=full();
        raw.remove("lower9");
        raw.remove("upper9");
        SkirtV2JoltFrameGate.Decision d=
                SkirtV2JoltFrameGate.assess(STRANDS,raw,19);
        need(!d.fullRing,"incomplete ring must never enter connected solver");
        need(d.validPairs==19 && d.recoveredBones==38,
                "nineteen complete strands must still be publishable");
        need(d.missingPairs.size()==1 &&
                d.missingPairs.get(0).equals("upper9/lower9"),
                "diagnostics must identify exact missing strand");
    }

    private static void neverPublishHalfATornStrand()
    {
        Map<String,float[]> raw=full();
        raw.remove("lower9");
        SkirtV2JoltFrameGate.Decision d=
                SkirtV2JoltFrameGate.assess(STRANDS,raw,20);
        need(!d.fullRing&&d.validPairs==19&&d.recoveredBones==38,
                "one-sided Jolt output must not break the chain");
        need(!d.recoveredSwings.containsKey("upper9"),
                "do not publish a lone swinging upper bone");
    }

    private static void duplicateAndInvalidPoseFailClosed()
    {
        Map<String,float[]> raw=full();
        raw.put("upper4",new float[]{1F,0F,0F,Float.NaN});
        List<String[]> invalid=new ArrayList<String[]>(STRANDS);
        invalid.set(10,new String[]{"upper0","lower10"});
        SkirtV2JoltFrameGate.Decision d=
                SkirtV2JoltFrameGate.assess(invalid,raw,20);
        need(!d.fullRing&&d.missingPairs.size()==2,
                "duplicate and non-finite input must be rejected");
        need(d.validPairs==18,
                "remaining independent Jolt strands must still animate");
    }

    private static void extraBoneDoesNotSneakIntoFullRing()
    {
        Map<String,float[]> raw=full();
        raw.put("unrelated",new float[]{0F,1F,0F,.3F});
        SkirtV2JoltFrameGate.Decision d=
                SkirtV2JoltFrameGate.assess(STRANDS,raw,20);
        need(!d.fullRing&&d.validPairs==20,
                "extraneous bone should trigger diagnostics but not wipe Jolt");
        need(d.recoveredBones==40,
                "unrelated bone must not appear in skirt preview");
    }

    private static void need(boolean ok,String why)
    {
        if(!ok)throw new AssertionError(why);
    }
}
