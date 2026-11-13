package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

/**
 * One canonical ordered list of matched native box/primitive/source rows.
 * A skirt needs BOTH thighs and pelvis even if parent + attached models
 * exceed the old 32-shape budget. Hair keeps the legacy order.
 *
 * No guessed colliders: priorities affect only actual captured geometry.
 */
public final class BodyPartJoltContactBudget
{
    public static final int LIMIT=32;

    public static final class Contact
    {
        public final float[] box,shape;
        public final String owner;
        private final int ordinal;

        Contact(float[] box,float[] shape,String owner,int ordinal)
        {
            this.box=box.clone();
            this.shape=shape.clone();
            this.owner=owner;
            this.ordinal=ordinal;
        }
    }

    public static final class Selection
    {
        public final float[][] boxes,shapes;
        public final List<String> owners;
        public final int total,skipped;
        public final boolean leftUpperLeg,rightUpperLeg;

        Selection(List<Contact> list,int total,boolean skirt,int omitted)
        {
            boxes=new float[list.size()][];
            shapes=new float[list.size()][];
            List<String> labels=new ArrayList<String>();
            boolean left=false,right=false;
            for(int i=0;i<list.size();i++)
            {
                Contact c=list.get(i);
                boxes[i]=c.box.clone();
                shapes[i]=c.shape.clone();
                // Emoticons legs have broad, mostly rectangular geometry.
                // The native capsule uses the MINIMUM leg cross-section as
                // its radius, leaving corners of the rendered thigh without
                // contact. Preserve the full fitted cuboid for skirt cloth.
                if(skirt && isLeg(c.owner))
                {
                    System.arraycopy(c.box,0,shapes[i],0,10);
                    shapes[i][10]=JoltColliderPrimitives.BOX;
                }
                labels.add(c.owner);
                String n=normalize(c.owner);
                if(upperLeg(n,true))left=true;
                if(upperLeg(n,false))right=true;
            }
            owners=Collections.unmodifiableList(labels);
            this.total=total;
            this.skipped=omitted;
            leftUpperLeg=left;rightUpperLeg=right;
        }
    }

    private final List<Contact> candidates=new ArrayList<Contact>();

    public void add(float[] box,float[] shape,String owner)
    {
        if(box==null||box.length!=10 || shape==null||shape.length!=11 ||
                owner==null)return;
        for(float value:box)if(!Float.isFinite(value))return;
        for(float value:shape)if(!Float.isFinite(value))return;
        candidates.add(new Contact(box,shape,owner,candidates.size()));
    }

    /** Retain all historical callers' exact source OBBs. */
    public Selection select(boolean skirt)
    {
        return select(skirt,false);
    }

    /**
     * Experimental skirt-only tapered thigh contacts. The same selection is
     * used by native Jolt and preview's visual-cube correction; hair and
     * ragdoll paths never request the modified hull.
     */
    public Selection select(boolean skirt,boolean tapered)
    {
        List<Contact> sorted=new ArrayList<Contact>(candidates);
        if(skirt && tapered)sorted=taperedContacts(sorted);
        // Preserve old shape identity/order for all non-skirt models.
        // When the skirt would overflow, promote verified leg bones
        // before low-priority attached cubes / arms / head.
        if(skirt && sorted.size()>LIMIT)
            Collections.sort(sorted,new Comparator<Contact>()
            {
                @Override public int compare(Contact a,Contact b)
                {
                    int p=priority(a.owner)-priority(b.owner);
                    return p!=0?p:Integer.compare(a.ordinal,b.ordinal);
                }
            });
        if(sorted.size()>LIMIT)
            sorted=new ArrayList<Contact>(sorted.subList(0,LIMIT));
        int omitted=Math.max(0,candidates.size()-sorted.size());
        if(skirt && tapered)
        {
            Set<Integer> retainedSources=new HashSet<Integer>();
            for(Contact c:sorted)retainedSources.add(Integer.valueOf(c.ordinal));
            omitted=Math.max(0,candidates.size()-retainedSources.size());
        }
        return new Selection(sorted,candidates.size(),skirt,omitted);
    }

    private static List<Contact> taperedContacts(List<Contact> source)
    {
        // Co-centered fitted OBBs of one thigh are alternative envelopes,
        // NOT three different positions along the leg. Expanding all of
        // them would multiply overlapping contacts and exhaust 32 slots.
        List<Contact> unique=new ArrayList<Contact>();
        for(Contact c:source)
        {
            if(!SkirtLegCollisionProxy.eligible(c.owner,c.box))
            {
                unique.add(c);
                continue;
            }
            boolean duplicate=false;
            for(int index=0;index<unique.size();index++)
            {
                Contact old=unique.get(index);
                if(!old.owner.equals(c.owner) ||
                        !SkirtLegCollisionProxy.eligible(old.owner,old.box) ||
                        !SkirtLegCollisionProxy.duplicate(old.box,c.box))
                    continue;
                if(SkirtLegCollisionProxy.volume(c.box)>
                        SkirtLegCollisionProxy.volume(old.box))
                    unique.set(index,c);
                duplicate=true;
                break;
            }
            if(!duplicate)unique.add(c);
        }

        List<Contact> expanded=new ArrayList<Contact>();
        for(Contact c:unique)
        {
            if(!SkirtLegCollisionProxy.eligible(c.owner,c.box))
            {
                expanded.add(c);
                continue;
            }
            float[][] zones=SkirtLegCollisionProxy.split(c.box);
            if(zones==null)
            {
                expanded.add(c);
                continue;
            }
            String[] names={"upper","middle","lower"};
            for(int zone=0;zone<SkirtLegCollisionProxy.ZONES;zone++)
            {
                float[] box=zones[zone];
                float[] shape=new float[11];
                System.arraycopy(box,0,shape,0,10);
                shape[10]=JoltColliderPrimitives.BOX;
                expanded.add(new Contact(box,shape,
                        c.owner+"@skirt-"+names[zone],c.ordinal));
            }
        }
        return expanded;
    }

    private static int priority(String owner)
    {
        String n=normalize(owner);
        if(upperLeg(n,true)||upperLeg(n,false))return 0;
        if(n.contains("pelvis")||n.contains("hip")||
                n.contains("low_body"))return 1;
        if(leg(n))return 2;
        if(n.contains("body")||n.contains("torso")||
                n.contains("spine")||n.contains("chest"))return 3;
        if(!n.startsWith("emoticons."))return 4;
        return 5;
    }

    private static boolean upperLeg(String n,boolean left)
    {
        String side=left?"left":"right";
        return n.startsWith("emoticons.") &&
                ((n.contains(side+"_leg") || n.contains("leg_"+side) ||
                        n.contains(side+"_thigh") || n.contains("thigh_"+side)) &&
                !n.contains("lower") && !n.contains("knee") &&
                !n.contains("foot") && !n.contains("ankle"));
    }

    /** The lower leg aliases also contain "leg": never taper the shin. */
    public static boolean isUpperThigh(String owner)
    {
        String n=normalize(owner);
        return !n.startsWith("emoticons.low_") &&
                !n.contains("lower") && !n.contains("low_leg") &&
                (upperLeg(n,true)||upperLeg(n,false));
    }

    public static boolean isLeg(String owner)
    {
        return leg(normalize(owner));
    }

    private static boolean leg(String n)
    {
        return n.startsWith("emoticons.") &&
                (n.contains("leg") || n.contains("knee") ||
                        n.contains("thigh") || n.contains("foot"));
    }

    private static String normalize(String s)
    {return s==null?"":s.toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
}
