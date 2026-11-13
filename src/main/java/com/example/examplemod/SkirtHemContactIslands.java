package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Contact-driven angular selection for a 20-panel skirt hung from a
 * stationary waist. A plausible left and right thigh can touch more than
 * twelve panels together without either leg being a giant invalid collider.
 *
 * Retain only contiguous, measured contact regions near the strongest
 * contact on each leg. Strong contacts may expand past the legacy fixed
 * five-panel windows, but the safety budget leaves at least five of the
 * twenty panels untouched, and diffuse/full-ring evidence remains bounded.
 * This only selects panel bones: Jolt colliders, body pose, belt and
 * physics keys are never edited.
 */
public final class SkirtHemContactIslands
{
    public static final int PANELS=20;
    public static final int LEFT=0;
    public static final int RIGHT=1;
    public static final int UNKNOWN=2;
    public static final int GROUPS=3;

    /** Anti-umbrella budget: at least five panels retain their natural pose. */
    public static final int MAX_SELECTED_PANELS=15;
    /** Safety ceiling; actual island extent is determined by contact depth. */
    private static final int MAX_PER_THIGH=9;
    private static final int MAX_UNKNOWN=2;
    private static final int MAX_DIFFUSE=5;
    private static final int LEGACY_LEG_WINDOW=5;
    private static final int LEGACY_UNKNOWN_WINDOW=2;

    private static final float MIN_SEED_SCORE=.0003F;
    private static final float RELATIVE_SCORE=.27F;
    private static final float RELATIVE_DEPTH=.26F;
    private static final float MIN_CONTACT_DEPTH=.008F;

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

    private static final class Island
    {
        final int group;
        int left,right;
        int count;
        final int max;
        final float minScore,minDepth;
        Island(int group,int anchor,int max,float minScore,float minDepth)
        {
            this.group=group;
            this.left=this.right=anchor;
            this.max=max;
            this.count=1;
            this.minScore=minScore;
            this.minDepth=minDepth;
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
     * @param ids authored upper skirt bone names, independent of angular order
     * @param roots belt roots in world space
     * @param pressure correction demand in radians, one row per leg side
     * @param penetration measured surface overlap, one row per leg side
     * @param unionLimit original 12-panel trigger (unchanged)
     */
    public static Decision choose(String[] ids,float[][] roots,
            float[][] pressure,float[][] penetration,int unionLimit)
    {
        return choose(ids,roots,pressure,penetration,unionLimit,true);
    }

    /** Pure Java A/B hook for the original fixed five-panel islands. */
    public static Decision choose(String[] ids,float[][] roots,
            float[][] pressure,float[][] penetration,int unionLimit,
            boolean adaptive)
    {
        if(ids==null||roots==null||pressure==null||penetration==null||
                ids.length!=PANELS||roots.length!=PANELS||
                pressure.length!=GROUPS||penetration.length!=GROUPS||
                unionLimit<1||unionLimit>=PANELS)
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
        List<Integer> angular=new ArrayList<Integer>(PANELS);
        for(int i=0;i<PANELS;i++)angular.add(Integer.valueOf(i));
        Collections.sort(angular,new Comparator<Integer>()
        {
            @Override public int compare(Integer x,Integer y)
            {
                int a=x.intValue(),b=y.intValue();
                int cmp=Double.compare(
                        Math.atan2(roots[a][2]-centerZ,
                                roots[a][0]-centerX),
                        Math.atan2(roots[b][2]-centerZ,
                                roots[b][0]-centerX));
                return cmp!=0?cmp:ids[a].compareTo(ids[b]);
            }
        });

        boolean[] candidates=new boolean[PANELS];
        int candidateCount=0;
        for(int i=0;i<PANELS;i++)
        {
            boolean touching=false;
            for(int g=0;g<GROUPS;g++)
                if(penetration[g][i]>.001F&&pressure[g][i]>.003F)
                    touching=true;
            candidates[i]=touching;
            if(touching)candidateCount++;
        }
        // The already-working response for ordinary local contacts must
        // remain bit-for-bit identical to the prior implementation.
        if(candidateCount<=unionLimit)
            return new Decision(candidates,candidateCount,candidateCount,false);

        if(!adaptive)
            return legacyChoose(angular,candidates,pressure,penetration,
                    candidateCount);

        float[][] score=new float[GROUPS][PANELS];
        for(int g=0;g<GROUPS;g++)
            for(int i=0;i<PANELS;i++)
                if(candidates[i] && pressure[g][i]>.003F &&
                        penetration[g][i]>.001F)
                    score[g][i]=pressure[g][i]*
                            Math.min(.25F,penetration[g][i]);

        List<Island> islands=new ArrayList<Island>();
        addIsland(islands,angular,ids,pressure,penetration,score,LEFT);
        addIsland(islands,angular,ids,pressure,penetration,score,RIGHT);
        // Unknown owner must never widen a trusted named thigh's contact
        // island into the rear of the garment.
        if(islands.isEmpty())
            addIsland(islands,angular,ids,pressure,penetration,score,UNKNOWN);

        boolean[] keep=new boolean[PANELS];
        int chosen=0;
        for(Island island:islands)
        {
            int seed=angular.get(island.left).intValue();
            if(!keep[seed]){keep[seed]=true;chosen++;}
        }

        // Each next candidate must be adjacent to its selected physical
        // island AND meet both relative score and depth thresholds. Among
        // two thighs the strongest frontier wins with mild fairness bias,
        // so either leg can receive additional strips during a wide step.
        while(chosen<MAX_SELECTED_PANELS)
        {
            Island best=null;
            int bestDirection=0,bestPos=-1;
            float bestPriority=0F;
            for(Island island:islands)
            {
                if(island.count>=island.max)continue;
                for(int direction:new int[]{-1,1})
                {
                    int pos=direction<0?
                            (island.left+PANELS-1)%PANELS:
                            (island.right+1)%PANELS;
                    int i=angular.get(pos).intValue();
                    float value=score[island.group][i];
                    if(value<island.minScore ||
                            penetration[island.group][i]<island.minDepth)
                        continue;
                    float priority=value/
                            (1F+.075F*(island.count-1));
                    if(best==null || priority>bestPriority+1E-8F ||
                            (Math.abs(priority-bestPriority)<=1E-8F &&
                             (island.group<best.group ||
                              (island.group==best.group &&
                               (bestPos<0 || pos<bestPos)))))
                    {
                        best=island;
                        bestDirection=direction;
                        bestPos=pos;
                        bestPriority=priority;
                    }
                }
            }
            if(best==null)break;
            if(bestDirection<0)best.left=bestPos;
            else best.right=bestPos;
            best.count++;
            int index=angular.get(bestPos).intValue();
            if(!keep[index]){keep[index]=true;chosen++;}
        }

        return new Decision(keep,candidateCount,chosen,true);
    }

    private static void addIsland(List<Island> islands,
            List<Integer> angular,String[] ids,float[][] pressure,
            float[][] penetration,float[][] score,int group)
    {
        int seed=-1,groupCandidates=0;
        float peak=0F,peakDepth=0F,total=0F;
        for(int i=0;i<PANELS;i++)
        {
            float value=score[group][i];
            if(value<=0F)continue;
            groupCandidates++;
            total+=value;
            peakDepth=Math.max(peakDepth,penetration[group][i]);
            if(value>peak+1E-8F ||
                    (Math.abs(value-peak)<=1E-8F && seed>=0 &&
                            ids[i].compareTo(ids[seed])<0))
            {
                peak=value;
                seed=i;
            }
        }
        if(seed<0||peak<MIN_SEED_SCORE)return;
        // Uniform all-ring pressure is a suspicious broad/overlapping body
        // fit rather than a focused thigh. Do not allow such evidence to
        // open a 9-panel umbrella on its own.
        boolean diffuse=groupCandidates>=15 &&
                peak<=(total/groupCandidates)*1.30F;
        int max=group==UNKNOWN?MAX_UNKNOWN:
                diffuse?MAX_DIFFUSE:MAX_PER_THIGH;
        float minScore=Math.max(MIN_SEED_SCORE,peak*RELATIVE_SCORE);
        float minDepth=Math.max(MIN_CONTACT_DEPTH,
                peakDepth*RELATIVE_DEPTH);
        int anchor=angular.indexOf(Integer.valueOf(seed));
        islands.add(new Island(group,anchor,max,minScore,minDepth));
    }

    /** Original deterministic A/B selection, retained for troubleshooting. */
    private static Decision legacyChoose(List<Integer> angular,
            boolean[] candidates,float[][] pressure,
            float[][] penetration,int count)
    {
        boolean[] keep=new boolean[PANELS];
        for(int g=0;g<GROUPS;g++)
        {
            int width=g==UNKNOWN?LEGACY_UNKNOWN_WINDOW:
                    LEGACY_LEG_WINDOW;
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
        return new Decision(keep,count,kept,true);
    }
}
