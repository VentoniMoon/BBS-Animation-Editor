package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;

/**
 * Cached runtime bridge for Character Animation Setup.
 *
 * It prevents updateAnimator() from being called every tick.
 */
public class CharacterAnimationSetupRuntimeController
{
    private EntityActor lastActor;
    private String lastSignature = "";

    public void apply(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int frame)
    {
        if (actorData == null || runtimeActor == null)
        {
            reset();
            return;
        }

        if (!CharacterAnimationSetupController.hasEffectiveSetup(
                actorData,
                frame
        ))
        {
            reset();
            return;
        }

        String signature =
                CharacterAnimationSetupController
                        .getRuntimeSignature(
                                actorData,
                                runtimeActor,
                                frame
                        );

        if (runtimeActor == this.lastActor &&
                signature.equals(this.lastSignature))
        {
            return;
        }

        CharacterAnimationSetupController.applyToRuntime(
                actorData,
                runtimeActor,
                frame
        );

        this.lastActor = runtimeActor;
        this.lastSignature = signature;
    }

    public void reset()
    {
        this.lastActor = null;
        this.lastSignature = "";
    }
}
