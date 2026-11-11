package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure Java 8 fixture: skirt waist stays animated, 7 strips are distinct. */
public final class SkirtChainTopologySmokeTest
{
    private static final class Bone
    {
        final String name;
        final List<Bone> kids=new ArrayList<Bone>();
        Bone(String name){this.name=name;}
        Bone child(String name)
        {
            Bone b=new Bone(name);kids.add(b);return b;
        }
    }
    private static void ok(boolean yes,String msg)
    {if(!yes)throw new AssertionError(msg);}
    private static void invalid(Bone waist,String context)
    {
        boolean failed=false;
        try{SkirtChainTopology.inspect(waist,b->b.kids,b->b.name);}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed,"Rejected invalid skirt topology: "+context);
    }
    public static void main(String[] args)
    {
        Bone waist=new Bone("skirt-waist");
        int[] lengths={3,2,2,4,3,2,3};
        for(int i=0;i<7;i++)
        {
            Bone bone=waist.child("strip_"+i+"_0");
            for(int n=1;n<lengths[i];n++)
                bone=bone.child("strip_"+i+"_"+n);
        }
        SkirtChainTopology.Plan<Bone> p=SkirtChainTopology.inspect(
                waist,b->b.kids,b->b.name);
        ok(p.waist==waist,"Waist must be animation-driven anchor");
        ok(p.strands.size()==7,"Wrong skirt chain count");
        ok(p.groups.size()==3,"Missing 3+2+2 groups");
        ok(p.groups.get(0).size()==3&&p.groups.get(1).size()==2&&
                p.groups.get(2).size()==2,"Wrong skirt group cardinality");
        for(int i=0;i<7;i++)
        {
            ok(p.strands.get(i).size()==lengths[i],
                    "Lost native bone in strip "+i);
            ok(!p.strands.get(i).contains(waist),
                    "Animated waist erroneously simulated");
        }
        Bone tooFew=new Bone("waist");
        for(int i=0;i<6;i++)tooFew.child("s"+i);
        invalid(tooFew,"missing 7th chain");
        Bone fork=new Bone("waist");
        for(int i=0;i<7;i++)fork.child("s"+i);
        fork.kids.get(1).child("a");
        fork.kids.get(1).child("b");
        invalid(fork,"fork in strip");
        Bone shared=new Bone("waist");
        Bone duplicate=shared.child("x");
        for(int i=1;i<7;i++)shared.kids.add(duplicate);
        invalid(shared,"reused cloth bone");
        Bone cycle=new Bone("waist");
        for(int i=0;i<7;i++)cycle.child("s"+i);
        cycle.kids.get(0).kids.add(cycle);
        invalid(cycle,"cycle back to waist");
        System.out.println("Skirt Bone Cloth 3+2+2 topology OK");
    }
}
