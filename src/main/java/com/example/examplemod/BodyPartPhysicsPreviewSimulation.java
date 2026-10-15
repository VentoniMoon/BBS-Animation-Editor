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
        State(BodyPartChainSolver solver, int frame, int hash)
        {
            this.solver=solver;
            this.lastFrame=frame;
            this.settingsHash=hash;
        }
    }

    private final Map<String, State> states =
            new HashMap<String, State>();

    public void clear() { states.clear(); }

    public BodyPartChainSolver.Vec3[] sample(String modelId, BodyPartPhysicsChains.Chain chain,
            int frame, BodyPartChainSolver.Vec3[] animatedWorld)
    {
        if (chain==null || animatedWorld==null || chain.settings==null ||
                !chain.settings.isEnabled()) return null;
        BodyPartPhysicsSettings settings=chain.settings;
        int hash=31*(31*Float.floatToIntBits(settings.getGravity())
                +Float.floatToIntBits(settings.getStiffness()))
                +Float.floatToIntBits(settings.getDamping());
        String key=modelId+"/"+chain.root.getName();
        State state=states.get(key);
        /*
         * Preview can jump directly from frame 0 to frame 40. For the
         * static-pose test, advance all missing ticks using the currently
         * evaluated rest pose rather than treating frame 40 as frame zero.
         * A moving parent will later require a historical pose source.
         */
        if (state==null || frame<state.lastFrame || state.settingsHash!=hash)
        {
            state=new State(new BodyPartChainSolver(animatedWorld),0,hash);
            states.put(key,state);
        }
        int steps=Math.max(0,frame-state.lastFrame);
        for (int tick=0;tick<steps;tick++)
            state.solver.step(animatedWorld,1F/20F,settings.getGravity(),
                    settings.getStiffness(),settings.getDamping());
        state.lastFrame=frame;
        BodyPartChainSolver.Vec3[] result =
                new BodyPartChainSolver.Vec3[state.solver.size()];
        for(int i=0;i<result.length;i++) result[i]=state.solver.getPoint(i);
        return result;
    }
}
