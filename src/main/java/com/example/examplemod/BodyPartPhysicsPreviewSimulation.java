package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;

/**
 * Preview-only, sequential frame physics. Samples come from the live native
 * Chameleon render pose. Seeking or reversing resets rather than guessing
 * missing animated parent frames. No keyframe writes.
 */
public final class BodyPartPhysicsPreviewSimulation
{
    private static final class State
    {
        BodyPartChainSolver solver;
        int lastFrame;
        int settingsHash;
        BodyPartChainSolver.Vec3[] filteredRelative;
        State(BodyPartChainSolver solver, int frame, int hash)
        {
            this.solver=solver;
            this.lastFrame=frame;
            this.settingsHash=hash;
        }
    }

    // In blocks: breathing-sized bone deformation should not continually
    // excite a strand. Translation of its root is never filtered.
    private static final float REST_DEAD_ZONE = 0.006F;
    private static final float REST_FOLLOW = 0.65F;

    private static BodyPartChainSolver.Vec3[] filterBoneMotion(
            State state, BodyPartChainSolver.Vec3[] pose)
    {
        BodyPartChainSolver.Vec3[] rest = new BodyPartChainSolver.Vec3[pose.length];
        rest[0] = pose[0];
        if (state.filteredRelative == null || state.filteredRelative.length != pose.length)
        {
            state.filteredRelative = new BodyPartChainSolver.Vec3[pose.length];
            for (int i = 1; i < pose.length; i++)
                state.filteredRelative[i] = pose[i].sub(pose[0]);
        }
        for (int i = 1; i < pose.length; i++)
        {
            BodyPartChainSolver.Vec3 wanted = pose[i].sub(pose[0]);
            BodyPartChainSolver.Vec3 previous = state.filteredRelative[i];
            BodyPartChainSolver.Vec3 delta = wanted.sub(previous);
            float distance = delta.length();
            if (distance > REST_DEAD_ZONE)
            {
                // Larger pose changes follow promptly; small changes within
                // the dead zone do not drive repeated physics impulses.
                float alpha = distance > 0.06F ? 1F : REST_FOLLOW;
                state.filteredRelative[i] = previous.add(delta.scale(alpha));
            }
            rest[i] = pose[0].add(state.filteredRelative[i]);
        }
        return rest;
    }

    private final Map<String, State> states =
            new HashMap<String, State>();

    public void clear() { states.clear(); }

    public BodyPartChainSolver.Vec3[] sample(String modelId, BodyPartPhysicsChains.Chain chain,
            int frame, BodyPartChainSolver.Vec3[] animatedWorld,
            java.util.List<BodyPartPhysicsCollisions.Capsule> colliders)
    {
        return sample(modelId,chain,frame,animatedWorld,colliders,null);
    }

    /**
     * Optional verified mesh-surface contact hook. Passing null preserves
     * the existing visual physics until coordinate alignment is validated.
     */
    public BodyPartChainSolver.Vec3[] sample(String modelId,
            BodyPartPhysicsChains.Chain chain, int frame,
            BodyPartChainSolver.Vec3[] animatedWorld,
            java.util.List<BodyPartPhysicsCollisions.Capsule> colliders,
            BodyPartChainSolver.SurfaceConstraint surface)
    {
        if (chain==null || animatedWorld==null || chain.settings==null ||
                !chain.settings.isEnabled()) return null;
        BodyPartPhysicsSettings settings=chain.settings;
        int hash=31*(31*Float.floatToIntBits(settings.getGravity())
                +Float.floatToIntBits(settings.getStiffness()))
                +Float.floatToIntBits(settings.getDamping());
        hash=31*hash+(settings.hasBodyCollisions()?1:0);
        String key=modelId+"/"+chain.root.getName();
        State state=states.get(key);
        /*
         * Preview can jump directly from frame 0 to frame 40. For the
         * static-pose test, advance all missing ticks using the currently
         * evaluated rest pose rather than treating frame 40 as frame zero.
         * A moving parent will later require a historical pose source.
         */
        // Never invent missing walking/running poses on a timeline seek.
        // Only integrate a genuinely consecutive frame. On a seek we
        // initialise from that frame's own rendered animation instead.
        if (state==null || frame<state.lastFrame ||
                frame>state.lastFrame+1 || state.settingsHash!=hash)
        {
            state=new State(new BodyPartChainSolver(animatedWorld),frame,hash);
            filterBoneMotion(state, animatedWorld); // initialize relative rest pose
            states.put(key,state);
        }
        else if (frame==state.lastFrame+1)
        {
            BodyPartChainSolver.Vec3[] driven = filterBoneMotion(state, animatedWorld);
            state.solver.step(driven,1F/20F,settings.getGravity(),
                    settings.getStiffness(),settings.getDamping(),
                    settings.hasBodyCollisions()?colliders:null,0.025F);
            if (surface != null && settings.hasBodyCollisions())
                state.solver.constrainSurface(driven[0],surface,0.02F);
        }
        state.lastFrame=frame;
        BodyPartChainSolver.Vec3[] result =
                new BodyPartChainSolver.Vec3[state.solver.size()];
        for(int i=0;i<result.length;i++) result[i]=state.solver.getPoint(i);
        return result;
    }
}
