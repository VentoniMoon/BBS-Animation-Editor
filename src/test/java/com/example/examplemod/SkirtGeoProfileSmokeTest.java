package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real 44-bone fixture transcribed from the supplied skirt.geo.json. */
public final class SkirtGeoProfileSmokeTest
{
    private static final class Bone {
        final String name;
        final List<Bone> children=new ArrayList<Bone>();
        Bone(String name){this.name=name;}
    }
    private static final String[] EDGES={
        "ALL:",
        "group42:ALL","group6:group42","group24:group6",
        "group5:group42","group26:group5",
        "group4:group42","group23:group4",
        "group2:group42","group22:group2",
        "group3:group42","group25:group3",
        "group18:group42","group29:group18",
        "group17:group42","group27:group17",
        "group:ALL","group43:group",
        "group9:group43","group37:group9",
        "group7:group43","group38:group7",
        "group8:group43","group40:group8",
        "group10:group43","group41:group10",
        "group16:group43","group39:group16",
        "group19:group43","group36:group19",
        "group20:group43","group35:group20",
        "group14:ALL","group31:group14",
        "group15:ALL","group32:group15",
        "group21:ALL","group30:group21",
        "group13:ALL","group34:group13",
        "group12:ALL","group28:group12",
        "group11:ALL","group33:group11"
    };
    private static void check(boolean yes,String message)
    {if(!yes)throw new AssertionError(message);}
    private static Map<String,Bone> hierarchy()
    {
        Map<String,Bone> all=new LinkedHashMap<String,Bone>();
        for(String edge:EDGES)
        {
            String[] pair=edge.split(":",-1);
            check(!all.containsKey(pair[0]),"Duplicate source "+pair[0]);
            all.put(pair[0],new Bone(pair[0]));
        }
        for(String edge:EDGES)
        {
            String[] pair=edge.split(":",-1);
            if(!pair[1].isEmpty())
                all.get(pair[1]).children.add(all.get(pair[0]));
        }
        return all;
    }
    private static SkirtGeoProfile.Plan<Bone> verify(Bone root)
    {return SkirtGeoProfile.inspect(root,b->b.children,b->b.name);}
    private static void reject(Bone root,String message)
    {
        boolean rejected=false;
        try{verify(root);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Did not reject "+message);
    }
    public static void main(String[] args)
    {
        Map<String,Bone> model=hierarchy();
        SkirtGeoProfile.Plan<Bone> plan=verify(model.get("ALL"));
        check(plan.allBones.size()==44,"Wrong total bone count");
        check(plan.stripCount()==20,"Wrong count of moving strips");
        check(plan.drivenBoneCount()==40,"Wrong count of physical bones");
        check(plan.physicsRoots.size()==8,"Expected 2 group anchors+6 sides");
        check(plan.waist==model.get("ALL"),"Wrong animated waist");
        boolean front=false,rear=false;
        for(Bone bone:plan.physicsRoots)
        {
            front|="group42".equals(bone.name);
            rear|="group43".equals(bone.name);
            check(!"ALL".equals(bone.name)&&!"group".equals(bone.name),
                    "Animated waist or rear 180-degree pivot simulated");
        }
        check(front&&rear,"Front/rear anchors missing");
        for(List<Bone> strip:plan.strips)
            check(strip.size()==2,"All source strips have 2 bones");

        Map<String,Bone> missing=hierarchy();
        missing.get("group42").children.remove(missing.get("group6"));
        reject(missing.get("ALL"),"front panel deletion");

        Map<String,Bone> moved=hierarchy();
        moved.get("ALL").children.remove(moved.get("group14"));
        moved.get("group43").children.add(moved.get("group14"));
        reject(moved.get("ALL"),"side attachment to rear");

        Map<String,Bone> shared=hierarchy();
        shared.get("group43").children.add(shared.get("group9"));
        reject(shared.get("ALL"),"repeated native bone");

        Map<String,Bone> fork=hierarchy();
        fork.get("group2").children.add(fork.get("group23"));
        reject(fork.get("ALL"),"illegal cloth branch fork");

        Map<String,Bone> cycle=hierarchy();
        cycle.get("group22").children.add(cycle.get("ALL"));
        reject(cycle.get("ALL"),"cycle");

        System.out.println("Real skirt.geo.json topology: 44 bones, "+
                "20 two-bone strips, 8 Jolt roots OK");
    }
}
