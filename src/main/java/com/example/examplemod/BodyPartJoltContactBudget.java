package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

        Selection(List<Contact> list,int total)
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
                labels.add(c.owner);
                String n=normalize(c.owner);
                if(upperLeg(n,true))left=true;
                if(upperLeg(n,false))right=true;
            }
            owners=Collections.unmodifiableList(labels);
            this.total=total;
            this.skipped=Math.max(0,total-list.size());
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

    public Selection select(boolean skirt)
    {
        List<Contact> sorted=new ArrayList<Contact>(candidates);
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
        return new Selection(sorted,candidates.size());
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

    private static boolean leg(String n)
    {
        return n.startsWith("emoticons.") &&
                (n.contains("leg") || n.contains("knee") ||
                        n.contains("thigh") || n.contains("foot"));
    }

    private static String normalize(String s)
    {return s==null?"":s.toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
}
