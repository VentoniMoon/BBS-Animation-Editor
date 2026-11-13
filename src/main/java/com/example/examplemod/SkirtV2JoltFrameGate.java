package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Guard the all-or-nothing skirt V2 publication gate.
 *
 * A single missing terminal-cube tip or unavailable native Jolt sample
 * previously withheld ALL twenty skirt strips. The connected 20-strip
 * solver must still require a complete ring, but other independently
 * sampled Jolt strips can be published as complete two-bone pairs.
 *
 * This class owns no physics state and never guesses a missing Jolt pose.
 * It works on a single captured frame only and is independent of Forge/JNI.
 */
public final class SkirtV2JoltFrameGate
{
    public static final int STRIPS=20;
    public static final int BONES=40;

    public static final class Decision
    {
        public final boolean fullRing;
        public final int expectedPairs, measuredStrips, validPairs;
        public final int receivedBones, recoveredBones;
        public final List<String> missingPairs;
        public final Map<String,float[]> recoveredSwings;

        private Decision(boolean fullRing,int expectedPairs,
                int measuredStrips,int validPairs,int receivedBones,
                Map<String,float[]> recoveredSwings,
                List<String> missingPairs)
        {
            this.fullRing=fullRing;
            this.expectedPairs=expectedPairs;
            this.measuredStrips=measuredStrips;
            this.validPairs=validPairs;
            this.receivedBones=receivedBones;
            this.recoveredBones=recoveredSwings.size();
            this.recoveredSwings=Collections.unmodifiableMap(recoveredSwings);
            this.missingPairs=Collections.unmodifiableList(missingPairs);
        }
    }

    private SkirtV2JoltFrameGate(){}

    public static Decision assess(List<String[]> expected,
            Map<String,float[]> samples,int stripCount)
    {
        Map<String,float[]> valid=new LinkedHashMap<String,float[]>();
        List<String> missing=new ArrayList<String>();
        Set<String> names=new HashSet<String>();
        int expectedSize=expected==null?0:expected.size();
        if(expected!=null)
            for(String[] pair:expected)
            {
                if(pair==null||pair.length!=2||
                        pair[0]==null||pair[1]==null||
                        pair[0].equals(pair[1])||
                        !names.add(pair[0])||!names.add(pair[1]))
                {
                    missing.add("(invalid-or-duplicate-chain)");
                    continue;
                }
                float[] upper=samples==null?null:samples.get(pair[0]);
                float[] lower=samples==null?null:samples.get(pair[1]);
                if(!valid(upper)||!valid(lower))
                {
                    missing.add(pair[0]+"/"+pair[1]);
                    continue;
                }
                // Never publish a lone upper/lower half, and never allow
                // mutable native matrices to leak into preview snapshots.
                valid.put(pair[0],upper.clone());
                valid.put(pair[1],lower.clone());
            }
        int completed=valid.size()/2;
        int received=samples==null?0:samples.size();
        boolean entire=expectedSize==STRIPS&&
                stripCount==STRIPS&&completed==STRIPS&&
                received==BONES&&missing.isEmpty();
        return new Decision(entire,expectedSize,stripCount,
                completed,received,valid,missing);
    }

    private static boolean valid(float[] values)
    {
        if(values==null||values.length!=4)return false;
        for(float v:values)if(!Float.isFinite(v))return false;
        return true;
    }
}
