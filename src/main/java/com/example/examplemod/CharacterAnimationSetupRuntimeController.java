package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Applies Character Animation Setup to the actual preview morph.
 *
 * A clean UserData snapshot is kept per runtime morph so moving the
 * playhead backwards can restore the original Emoticons configuration.
 */
public class CharacterAnimationSetupRuntimeController
{
    private EntityActor lastActor;
    private AnimatedMorph lastMorph;
    private NBTTagCompound baselineUserData;

    private String lastSignature = "";

    public void apply(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int frame)
    {
        if (runtimeActor == null ||
                runtimeActor.morph == null)
        {
            reset();
            return;
        }

        AnimatedMorph animated;

        try
        {
            if (!(runtimeActor.morph.get() instanceof AnimatedMorph))
            {
                reset();
                return;
            }

            animated =
                    (AnimatedMorph) runtimeActor.morph.get();
        }
        catch (Throwable error)
        {
            reset();
            return;
        }

        /*
         * A new morph instance means CharacterStateResolver has
         * supplied a new baseline UserData. Capture it before any
         * Character Animation Setup override is applied.
         */
        if (runtimeActor != this.lastActor ||
                animated != this.lastMorph ||
                this.baselineUserData == null)
        {
            this.lastActor = runtimeActor;
            this.lastMorph = animated;

            this.baselineUserData =
                    animated.userConfigData == null
                            ? new NBTTagCompound()
                            : animated.userConfigData.copy();

            this.lastSignature = "";
        }

        String signature =
                CharacterAnimationSetupController
                        .getRuntimeSignature(
                                actorData,
                                runtimeActor,
                                frame
                        );

        /*
         * Include the absence of a setup in the cache state.
         * This allows a later frame without setup to restore the
         * clean Emoticons configuration.
         */
        if (!CharacterAnimationSetupController.hasEffectiveSetup(
                actorData,
                frame))
        {
            signature = "<NO_ANIMATION_SETUP>";
        }

        if (signature.equals(this.lastSignature))
        {
            return;
        }

        CharacterAnimationSetupController.applyToRuntime(
                actorData,
                runtimeActor,
                frame,
                this.baselineUserData
        );

        this.lastSignature = signature;
    }

    public void reset()
    {
        this.lastActor = null;
        this.lastMorph = null;
        this.baselineUserData = null;
        this.lastSignature = "";
    }
}
