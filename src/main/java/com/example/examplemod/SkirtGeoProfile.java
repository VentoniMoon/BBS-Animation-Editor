package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Exact hierarchy of the user-supplied skirt.geo.json (Bedrock geometry
 * v1.12.0). Not the old hypothetical seven-strip skirt.
 *
 * ALL is the fixed/animated waistband. Its front anchor group42 drives
 * seven two-bone strips, while group -> group43 is the rear anchor with
 * an IMPORTANT authored 180-degree Y rotation in group. Six side strips
 * start as direct children of ALL. There are 20 physical strips (40
 * driven bones), four passive anchors, 44 bones in total.
 *
 * This helper checks NAMES AND PARENT CONNECTIONS before changing any
 * BodyPart physics settings. It is Java 8 and not coupled to Minecraft.
 */
public final class SkirtGeoProfile
{
    private static final String WAIST="ALL";
    private static final String FRONT="group42";
    private static final String REAR_TURN="group";
    private static final String REAR="group43";

    /** 3 centre + 2 outer + 2 corners, exactly as the source hierarchy. */
    private static final String[][] FRONT_322={
        {"group2","group6","group3"},
        {"group4","group5"},
        {"group18","group17"}
    };
    private static final String[][] REAR_322={
        {"group7","group16","group8"},
        {"group9","group10"},
        {"group20","group19"}
    };
    private static final String[] LEFT={"group14","group15","group21"};
    private static final String[] RIGHT={"group13","group12","group11"};

    /** Real leaf bones from skirt.geo.json; one per moving strip root. */
    private static final String[][] STRIP_LEAVES={
        {"group2","group22"},{"group6","group24"},
        {"group3","group25"},{"group4","group23"},
        {"group5","group26"},{"group18","group29"},
        {"group17","group27"},
        {"group7","group38"},{"group16","group39"},
        {"group8","group40"},{"group9","group37"},
        {"group10","group41"},{"group20","group35"},
        {"group19","group36"},
        {"group14","group31"},{"group15","group32"},
        {"group21","group30"},{"group13","group34"},
        {"group12","group28"},{"group11","group33"}
    };

    /**
     * Keep two shared anchors and six side-strip roots enabled in the
     * established Jolt chain collector. Do not make ALL or group dynamic.
     */
    private static final String[] PHYSICS_ROOTS={
        FRONT,REAR,
        "group14","group15","group21",
        "group13","group12","group11"
    };

    public static final class Plan<T>
    {
        public final T waist;
        public final List<List<T>> strips;
        public final List<T> physicsRoots;
        public final List<T> allBones;
        private Plan(T waist,List<List<T>> strips,
                List<T> roots,List<T> all)
        {
            this.waist=waist;
            this.strips=Collections.unmodifiableList(strips);
            this.physicsRoots=Collections.unmodifiableList(roots);
            this.allBones=Collections.unmodifiableList(all);
        }
        public int stripCount(){return strips.size();}
        public int drivenBoneCount()
        {
            int total=0;
            for(List<T> strip:strips)total+=strip.size();
            return total;
        }
    }
    private SkirtGeoProfile(){}

    public static <T> Plan<T> inspect(T root,
            Function<T,List<T>> children,Function<T,String> name)
    {
        if(root==null||children==null||name==null)
            throw new IllegalArgumentException("No skirt model or bone adapter");
        if(!WAIST.equals(name.apply(root)))
            throw new IllegalArgumentException(
                    "Select skirt root ALL, not an individual strip");

        Map<String,T> byName=new HashMap<String,T>();
        Map<T,Boolean> visited=new IdentityHashMap<T,Boolean>();
        List<T> discovered=new ArrayList<T>();
        register(root,children,name,byName,visited,discovered);
        if(byName.size()!=44)
            throw new IllegalArgumentException(
                    "Expected 44 skirt.geo.json bones, found "+byName.size());

        childrenExactly(byName,children,name,WAIST,
                FRONT,REAR_TURN,"group14","group15","group21",
                "group13","group12","group11");
        childrenExactly(byName,children,name,REAR_TURN,REAR);
        childrenExactly(byName,children,name,FRONT,
                "group6","group5","group4","group2","group3",
                "group18","group17");
        childrenExactly(byName,children,name,REAR,
                "group9","group7","group8","group10","group16",
                "group19","group20");

        List<List<T>> strips=new ArrayList<List<T>>();
        for(String[] link:STRIP_LEAVES)
        {
            childrenExactly(byName,children,name,link[0],link[1]);
            childrenExactly(byName,children,name,link[1]);
            List<T> pair=new ArrayList<T>();
            pair.add(require(byName,link[0]));
            pair.add(require(byName,link[1]));
            strips.add(Collections.unmodifiableList(pair));
        }

        // Verify declared 3+2+2 front/back sector partition. We must
        // not mix a mirrored/rear root with a front strip.
        Set<String> front=flatten(FRONT_322),rear=flatten(REAR_322);
        if(front.size()!=7||rear.size()!=7)
            throw new IllegalStateException("Invalid 3+2+2 group profile");
        for(int i=0;i<strips.size();i++)
        {
            String id=name.apply(strips.get(i).get(0));
            if(i<7&&!front.contains(id)||
                    i>=7&&i<14&&!rear.contains(id)||
                    i>=14&&i<17&&!contains(LEFT,id)||
                    i>=17&&!contains(RIGHT,id))
                throw new IllegalArgumentException("Skirt sector mismatch: "+id);
        }
        List<T> roots=new ArrayList<T>();
        for(String rootName:PHYSICS_ROOTS)
            roots.add(require(byName,rootName));
        return new Plan<T>(root,strips,roots,discovered);
    }

    private static boolean contains(String[] xs,String s)
    {
        for(String x:xs)if(x.equals(s))return true;
        return false;
    }
    private static Set<String> flatten(String[][] groups)
    {
        Set<String> all=new HashSet<String>();
        for(String[] group:groups)
            for(String name:group)
                if(!all.add(name))
                    throw new IllegalStateException("Duplicate strip in group");
        return all;
    }
    private static <T> T require(Map<String,T> byName,String id)
    {
        T value=byName.get(id);
        if(value==null)
            throw new IllegalArgumentException("Missing skirt bone: "+id);
        return value;
    }
    private static <T> void childrenExactly(Map<String,T> byName,
            Function<T,List<T>> children,Function<T,String> name,
            String parent,String... expected)
    {
        T bone=require(byName,parent);
        List<T> kids=children.apply(bone);
        Set<String> actual=new HashSet<String>();
        if(kids!=null)
            for(T kid:kids)
                if(kid==null||!actual.add(name.apply(kid)))
                    throw new IllegalArgumentException(
                            "Repeated/empty child at "+parent);
        Set<String> target=new HashSet<String>();
        for(String child:expected)target.add(child);
        if(!actual.equals(target)||target.size()!=expected.length)
            throw new IllegalArgumentException(
                    "Skirt child mapping mismatch at "+parent+
                    ": expected "+target+", found "+actual);
    }
    private static <T> void register(T node,
            Function<T,List<T>> children,Function<T,String> name,
            Map<String,T> byName,Map<T,Boolean> visited,List<T> all)
    {
        if(node==null||visited.put(node,true)!=null)
            throw new IllegalArgumentException("Skirt cycle or shared bone");
        String id=name.apply(node);
        if(id==null||id.isEmpty()||byName.put(id,node)!=null)
            throw new IllegalArgumentException("Duplicate/unnamed skirt bone: "+id);
        all.add(node);
        List<T> kids=children.apply(node);
        if(kids!=null)
            for(T kid:kids)
                register(kid,children,name,byName,visited,all);
    }
}
