package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Safety limiter for the *union* of two legitimate Emoticons leg contacts
 * against a short 20-panel skirt.
 *
 * An arbitrary global >12-panel guard used to cancel the entire correction
 * when the two thighs collectively overlapped much of the lower hem. Each
 * individual leg OBB could be valid (<12 panels), yet together they
 * triggered the same full-ring abort as one malformed giant OBB.
 *
 * Keep a small, contiguous contact island around the strongest physically
 * supported area for each leg. This only acts when the global union
 * exceeds 12 direct panels. Source OBBs, Jolt state, original cloth pose,
 * actor pose and skirt scale are never changed.
 */
public final class SkirtHemContactIslands
{
    public static final int PANELS=20;
    public static final int LEFT=0;
    public static final int RIGHT=1;
    public static final int UNKNOWN=2;
    public static final int GROUPS=3;
    private static final int LEG_WINDOW=5;
    private static final int UNKNOWN_WINDOW=2;

    public static final class Decision
    {
        public final boolean[] keep;
        public final int candidatePanels,keptPanels;
        public final boolean limited;
        Decision(boolean[] keep,int candidates,int kept,boolean limited)
        {
            this.keep=keep;
            this.candidatePanels=candidates;
            this.keptPanels=kept;
            this.limited=limited;
        }
    }

    private SkirtHemContactIslands(){}

    public static int sideOf(String owner)
    {
        if(owner==null)return UNKNOWN;
        String value=owner.toLowerCase(java.util.Locale.ROOT);
        if(value.contains("left"))return LEFT;
        if(value.contains("right"))return RIGHT;
        return UNKNOWN;
    }

    /**
     * @param ids exact authored upper bone names
     * @param roots world-space belt centers, 20 x 3
     * @param pressure contact swing demand in radians: groups x 20
     * @param penetration positive measured surface penetration: groups x 20
     * @param unionLimit legacy global guard threshold (12)
     */
    public static Decision choose(String[] ids,float[][] roots,
            float[][] pressure,float[][] penetration,int unionLimit)
    {
        if(ids==null||roots==null||pressure==null||penetration==null||
                ids.length!=PANELS||roots.length!=PANELS||
                pressure.length!=GROUPS||penetration.length!=GROUPS)
            return null;
        for(int g=0;g<GROUPS;g++)
        {
            if(pressure[g]==null||penetration[g]==null||
                    pressure[g].length!=PANELS||
                    penetration[g].length!=PANELS)return null;
            for(int i=0;i<PANELS;i++)
                if(!Float.isFinite(pressure[g][i])||
                        !Float.isFinite(penetration[g][i])||
                        pressure[g][i]<0F||penetration[g][i]<0F)
                    return null;
        }
        float cx=0F,cz=0F;
        for(int i=0;i<PANELS;i++)
        {
            if(ids[i]==null||roots[i]==null||roots[i].length!=3)
                return null;
            for(float v:roots[i])if(!Float.isFinite(v))return null;
            cx+=roots[i][0];cz+=roots[i][2];
        }
        cx/=PANELS;cz/=PANELS;
        final float centerX=cx,centerZ=cz;
        List<Integer> angular=new ArrayList<Integer>();
        for(int i=0;i<PANELS;i++)angular.add(Integer.valueOf(i));
        Collections.sort(angular,new Comparator<Integer>()
        {
            @Override public int compare(Integer x,Integer y)
            {
                int a=x.intValue(),b=y.intValue();
                int cmp=Double.compare(
                        Math.atan2(roots[a][2]-centerZ,roots[a][0]-centerX),
                        Math.atan2(roots[b][2]-centerZ,roots[b][0]-centerX));
                return cmp!=0?cmp:ids[a].compareTo(ids[b]);
            }
        });

        boolean[] candidates=new boolean[PANELS];
        int count=0;
        for(int i=0;i<PANELS;i++)
        {
            boolean touching=false;
            for(int g=0;g<GROUPS;g++)
                if(penetration[g][i]>.001F&&pressure[g][i]>.003F)
                    touching=true;
            candidates[i]=touching;
            if(touching)count++;
        }
        if(count<=unionLimit)
            return new Decision(candidates,count,count,false);

        boolean[] keep=new boolean[PANELS];
        for(int g=0;g<GROUPS;g++)
        {
            int width=g==UNKNOWN?UNKNOWN_WINDOW:LEG_WINDOW;
            int bestStart=-1;
            float bestScore=0F;
            for(int start=0;start<PANELS;start++)
            {
                float sum=0F;
                for(int step=0;step<width;step++)
                {
                    int index=angular.get((start+step)%PANELS).intValue();
                    if(candidates[index])
                        sum+=pressure[g][index]*
                                Math.min(.25F,penetration[g][index]);
                }
                if(sum>bestScore+1E-7F)
                {
                    bestScore=sum;
                    bestStart=start;
                }
            }
            if(bestStart<0)continue;
            for(int step=0;step<width;step++)
            {
                int index=angular.get((bestStart+step)%PANELS).intValue();
                if(pressure[g][index]>.003F&&
                        penetration[g][index]>.001F)
                    keep[index]=true;
            }
        }
        int kept=0;
        for(boolean yes:keep)if(yes)kept++;
        // The maximum union is five near each thigh, and at most two
        // unlabeled leg strips: 12 direct panels, never the full 20-ring.
        return new Decision(keep,count,kept,true);
    }
}
