package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves explicitly enabled linear chains within one attached model.
 * The shared anchor is intentionally not simulated unless explicitly enabled.
 * This class never changes keys, bone hierarchy or Chameleon pose data.
 */
public final class BodyPartPhysicsChains
{
    private BodyPartPhysicsChains() {}

    public static final class Chain
    {
        public final AnimationBone root;
        public final List<AnimationBone> bones;
        public final BodyPartPhysicsSettings settings;

        private Chain(AnimationBone root, List<AnimationBone> bones,
                      BodyPartPhysicsSettings settings)
        {
            this.root = root;
            this.bones = Collections.unmodifiableList(bones);
            this.settings = settings;
        }
    }

    /**
     * A branch is not itself a physical chain. On invalid trees, report the
     * error rather than silently combining left/right/back strands.
     */
    public static List<Chain> collect(BodyPartModelData model)
    {
        List<Chain> result = new ArrayList<Chain>();
        if (model == null || model.getBones() == null) return result;

        Map<AnimationBone, Boolean> used =
                new IdentityHashMap<AnimationBone, Boolean>();

        for (AnimationBone bone : model.getBones())
        {
            if (bone == null || bone.getName() == null) continue;
            BodyPartPhysicsSettings settings = model.getPhysicsSettings(bone.getName());
            if (settings == null || !settings.isEnabled()) continue;

            List<AnimationBone> children=bone.getChildren();
            if(children!=null && children.size()>1)
            {
                // The selected bone is a SHARED anchor with several distinct
                // strands (often on head hairstyles). It stays animated:
                // each child independently starts a Jolt chain and inherits
                // the selected parent's settings. No bone is simulated twice.
                int previousCount=result.size();
                for(AnimationBone child:children)
                {
                    // Decorative single-bone attachments (ears, ribbons,
                    // ornaments) are not dynamic strands. A shared head
                    // anchor must not fail merely because one sibling has
                    // no joint to simulate.
                    if(child==null || child.getChildren()==null ||
                            child.getChildren().isEmpty())
                        continue;
                    appendLinearChain(child,settings,used,result);
                }
                if(result.size()==previousCount)
                    throw new IllegalStateException(
                            "Shared physics anchor "+bone.getName()+
                            " has no child chains with at least two bones");
            }
            else
            {
                appendLinearChain(bone,settings,used,result);
            }
        }

        return result;
    }

    /**
     * Traverse any linear strand, independent of its bone names or source
     * geo filename. Shared anchors split above; a fork AFTER the selected
     * root would duplicate a dynamic joint and is explicitly rejected.
     */
    private static void appendLinearChain(AnimationBone start,
            BodyPartPhysicsSettings settings,
            Map<AnimationBone, Boolean> used,List<Chain> result)
    {
        if(start==null)
            throw new IllegalStateException("Missing physics strand root");
        ArrayList<AnimationBone> chain=new ArrayList<AnimationBone>();
        Map<AnimationBone,Boolean> visited=
                new IdentityHashMap<AnimationBone,Boolean>();
        AnimationBone current=start;
        while(current!=null)
        {
            if(visited.put(current,Boolean.TRUE)!=null)
                throw new IllegalStateException("Cycle in chain: "+start.getName());
            if(used.containsKey(current))
                throw new IllegalStateException(
                        "Overlapping physical strands at "+current.getName());
            chain.add(current);
            List<AnimationBone> children=current.getChildren();
            if(children==null || children.isEmpty())break;
            if(children.size()!=1)
                throw new IllegalStateException(
                        "Physics strand "+start.getName()+
                        " forks at "+current.getName()+
                        "; select independent branch roots instead");
            current=children.get(0);
        }
        if(chain.size()<2)
            throw new IllegalStateException(
                    "Physics strand "+start.getName()+" requires at least two bones");
        for(AnimationBone bone:chain)used.put(bone,Boolean.TRUE);
        result.add(new Chain(start,chain,settings));
    }
}
