package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Select only the contacting corner and its two angular neighbours for
 * a bounded, contact-driven extra swing of the LOWER skirt link.
 * Independent of Chameleon bone enumeration and free of mutable state:
 * a seek/repeated render produces exactly the same selection.
 *
 * Physics, global morph scale, upper waist bones and adjacent non-skirt
 * accessories are never edited. Actual collision reduction is checked
 * by SkirtVisibleThighContacts after applying a trial lower-link swing.
 */
public final class SkirtAdaptiveHemLift
{
    public static final int PANELS=20;
    public static final int GROUPS=SkirtHemContactIslands.GROUPS;
    public static final float MAX_EXTRA_ANGLE=.28F;
    public static final float STEP=.04F;
    public static final float SECONDARY_WEIGHT=.38F;

    private SkirtAdaptiveHemLift(){}

    /**
     * @param selected a DIRECT selected-contact mask from the existing
     *                 leg-island safety system (never extrapolate from
     *                 an excluded OBB or unselected panel)
     * @param sideDemand measured per-side swing demand
     * @param sideDepth measured per-side leg penetration
     * @return bounded weights: one strongest panel and its two actual
     *         angular neighbors per leg, maximum six moving panels.
     */
    public static float[] weights(String[] ids,float[][] root,
            boolean[] selected,float[][] sideDemand,float[][] sideDepth)
    {
        float[] none=new float[PANELS];
        if(ids==null||ids.length!=PANELS||root==null||
                root.length!=PANELS||selected==null||
                selected.length!=PANELS||sideDemand==null||
                sideDepth==null||sideDemand.length!=GROUPS||
                sideDepth.length!=GROUPS)return none;
        float cx=0F,cz=0F;
        for(int i=0;i<PANELS;i++)
        {
            if(ids[i]==null||root[i]==null||root[i].length!=3)
                return none;
            for(float v:root[i])if(!Float.isFinite(v))return none;
            cx+=root[i][0];cz+=root[i][2];
        }
        for(int g=0;g<GROUPS;g++)
        {
            if(sideDemand[g]==null||sideDemand[g].length!=PANELS||
                    sideDepth[g]==null||sideDepth[g].length!=PANELS)
                return none;
            for(int i=0;i<PANELS;i++)
                if(!Float.isFinite(sideDemand[g][i])||
                        !Float.isFinite(sideDepth[g][i])||
                        sideDemand[g][i]<0F||sideDepth[g][i]<0F)
                    return none;
        }
        final float centerX=cx/PANELS,centerZ=cz/PANELS;
        List<Integer> circle=new ArrayList<Integer>(PANELS);
        for(int i=0;i<PANELS;i++)circle.add(Integer.valueOf(i));
        Collections.sort(circle,new Comparator<Integer>()
        {
            @Override public int compare(Integer a,Integer b)
            {
                int x=a.intValue(),y=b.intValue();
                int order=Double.compare(
                        Math.atan2(root[x][2]-centerZ,root[x][0]-centerX),
                        Math.atan2(root[y][2]-centerZ,root[y][0]-centerX));
                return order!=0?order:ids[x].compareTo(ids[y]);
            }
        });
        float[] weight=new float[PANELS];
        int[] topGroups={
                SkirtHemContactIslands.LEFT,
                SkirtHemContactIslands.RIGHT};
        // Ignore unknown owners when a named thigh already supplies a
        // reliable center. Unknown can be used only as a single fallback.
        boolean foundKnown=false;
        for(int g:topGroups)
        {
            int best=-1;
            float score=.0001F;
            for(int i=0;i<PANELS;i++)
            {
                float candidate=sideDemand[g][i]*sideDepth[g][i];
                if(selected[i] && candidate>score)
                {
                    best=i;
                    score=candidate;
                }
            }
            if(best>=0)
            {
                foundKnown=true;
                applyNeighborhood(circle,best,selected,weight);
            }
        }
        if(!foundKnown)
        {
            int g=SkirtHemContactIslands.UNKNOWN;
            int best=-1;
            float score=.0001F;
            for(int i=0;i<PANELS;i++)
            {
                float candidate=sideDemand[g][i]*sideDepth[g][i];
                if(selected[i]&&candidate>score)
                {
                    best=i;score=candidate;
                }
            }
            if(best>=0)applyNeighborhood(circle,best,selected,weight);
        }
        return weight;
    }

    private static void applyNeighborhood(List<Integer> circle,int center,
            boolean[] selected,float[] weight)
    {
        int location=circle.indexOf(Integer.valueOf(center));
        if(location<0)return;
        weight[center]=1F;
        for(int offset:new int[]{-1,1})
        {
            int neighbor=circle.get((location+PANELS+offset)%PANELS).intValue();
            if(selected[neighbor])
                weight[neighbor]=Math.max(weight[neighbor],SECONDARY_WEIGHT);
        }
    }

    /** The old 0.60–0.68 rad hem cap remains the rest-state maximum.
     * This cap is ONLY used while the affected panel still intersects
     * a valid, active thigh OBB. */
    public static float collisionCap(float baseCap,float weight)
    {
        if(!Float.isFinite(baseCap)||!Float.isFinite(weight))return baseCap;
        return Math.min(1.02F,
                baseCap+MAX_EXTRA_ANGLE*Math.max(0F,Math.min(1F,weight)));
    }

    public static float extraBudget(float weight)
    {
        if(!Float.isFinite(weight))return 0F;
        return MAX_EXTRA_ANGLE*Math.max(0F,Math.min(1F,weight));
    }
}
