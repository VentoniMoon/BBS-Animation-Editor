package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Geometry-independent seven-strand (3+2+2) skirt topology.
 *
 * Neither model bone names nor attachment orientation are assumed.
 * Exactly seven independent linear chains must descend from a shared
 * animated waist bone. The waist itself is NOT simulated. This matches
 * the existing BodyPartPhysicsChains' shared-anchor Jolt path.
 *
 * The groups encode strip order only, not guessed front/back semantics.
 * Actual Chameleon model mapping is verified separately in the editor.
 */
public final class SkirtChainTopology
{
    public static final int[] GROUP_SIZES={3,2,2};
    public static final int STRANDS=7;
    public static final int MAX_DEPTH=16;

    public static final class Plan<T>
    {
        public final T waist;
        public final List<List<T>> strands;
        public final List<List<List<T>>> groups;

        private Plan(T waist,List<List<T>> all)
        {
            this.waist=waist;
            this.strands=Collections.unmodifiableList(all);
            List<List<List<T>>> out=new ArrayList<List<List<T>>>();
            int start=0;
            for(int size:GROUP_SIZES)
            {
                out.add(Collections.unmodifiableList(
                        new ArrayList<List<T>>(all.subList(start,start+size))));
                start+=size;
            }
            groups=Collections.unmodifiableList(out);
        }
    }
    private SkirtChainTopology(){}

    public static <T> Plan<T> inspect(T waist,
            Function<T,List<T>> children,
            Function<T,String> name)
    {
        if(waist==null||children==null||name==null)
            throw new IllegalArgumentException("Missing skirt waist or bone adapter");
        List<T> roots=children.apply(waist);
        if(roots==null||roots.size()!=STRANDS)
            throw new IllegalArgumentException(
                    "Skirt waist must have exactly seven direct branches (3+2+2)");
        Map<T,Boolean> visited=new IdentityHashMap<T,Boolean>();
        Set<String> names=new HashSet<String>();
        String waistName=name.apply(waist);
        if(waistName==null||waistName.trim().isEmpty())
            throw new IllegalArgumentException("Unnamed skirt waist");
        names.add(waistName);
        visited.put(waist,true);
        List<List<T>> output=new ArrayList<List<T>>();
        for(T first:roots)
        {
            if(first==null)throw new IllegalArgumentException("Null skirt branch");
            List<T> nodes=new ArrayList<T>();
            T node=first;
            while(node!=null)
            {
                if(visited.put(node,true)!=null)
                    throw new IllegalArgumentException(
                            "Cycle or repeated bone in skirt strand");
                String id=name.apply(node);
                if(id==null||id.trim().isEmpty())
                    throw new IllegalArgumentException("Unnamed cloth bone");
                // Physics settings persist by name, not bone identity.
                // Duplicate names would alias two strands to one key.
                if(!names.add(id))
                    throw new IllegalArgumentException(
                            "Duplicate skirt bone name: "+id);
                nodes.add(node);
                if(nodes.size()>MAX_DEPTH)
                    throw new IllegalArgumentException("Skirt strand too deep");
                List<T> next=children.apply(node);
                if(next==null||next.isEmpty())break;
                if(next.size()!=1)
                    throw new IllegalArgumentException(
                            "Skirt strand branches at "+id+
                            " — give every strip a separate root");
                node=next.get(0);
            }
            output.add(Collections.unmodifiableList(nodes));
        }
        return new Plan<T>(waist,output);
    }
}
