package com.example.examplemod;

/**
 * Model-local Emoticons anatomical conventions for ragdoll Jolt joints.
 *
 * This class intentionally has no Minecraft, Vecmath or JNI dependency,
 * so sign conventions can be tested on a plain Java 8 JVM.
 */
public final class RagdollAnatomy
{
    private RagdollAnatomy(){}

    /** Original Emoticons BOBJ faces negative model-local Z. */
    public static float forwardLocalZ()
    {
        return -1F;
    }

    /**
     * A hinge with positive rotation around +X bends a downward limb
     * toward negative Z (forward). Forearms bend forward, knees bend
     * backward, hence the opposite hinge axes.
     */
    public static float hingeAxisSign(String role)
    {
        if("left_shin".equals(role)||"right_shin".equals(role))
            return -1F;
        if("left_forearm".equals(role)||"right_forearm".equals(role))
            return 1F;
        throw new IllegalArgumentException("Not an Emoticons hinge: "+role);
    }
}
