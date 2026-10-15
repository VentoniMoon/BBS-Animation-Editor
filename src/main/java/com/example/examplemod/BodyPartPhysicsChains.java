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

            ArrayList<AnimationBone> chain = new ArrayList<AnimationBone>();
            AnimationBone current = bone;
            Map<AnimationBone, Boolean> visited =
                    new IdentityHashMap<AnimationBone, Boolean>();

            while (current != null)
            {
                if (visited.put(current, Boolean.TRUE) != null)
                    throw new IllegalStateException("Cycle in physics chain: " + bone.getName());
                if (used.containsKey(current))
                    throw new IllegalStateException("Overlapping physics chains at " + current.getName());

                chain.add(current);
                List<AnimationBone> children = current.getChildren();
                if (children == null || children.isEmpty()) break;
                if (children.size() != 1)
                    throw new IllegalStateException(
                            "Physics root " + bone.getName() + " branches at " + current.getName()
                            + "; select each strand's own root");
                current = children.get(0);
            }

            if (chain.size() < 2)
                throw new IllegalStateException(
                        "Physics chain " + bone.getName() + " has fewer than two joints");

            for (AnimationBone joint : chain) used.put(joint, Boolean.TRUE);
            result.add(new Chain(bone, chain, settings));
        }

        return result;
    }
}
