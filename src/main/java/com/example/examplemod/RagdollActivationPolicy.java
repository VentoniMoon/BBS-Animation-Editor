package com.example.examplemod;

/**
 * Tiny Java 8-only policy shared by PHYSICS key authoring and the
 * optional native Jolt preview bridge.
 *
 * Ragdoll must start when the user authors a positive Weight key even
 * in the installed Forge JAR (where Gradle runClient flags do not exist).
 * The native bridge still checks weight BEFORE attempting JNI startup,
 * and -Dbbs.editor.physics.jolt.ragdoll=false is a deliberate opt-out.
 */
public final class RagdollActivationPolicy
{
    private RagdollActivationPolicy(){}

    public static boolean runtimeEnabled(String jvmValue)
    {
        return !"false".equalsIgnoreCase(
                jvmValue==null?"true":jvmValue.trim());
    }

    public static float initialKeyWeight(
            float explicitAtFrame,float sampledWeight,boolean anyExistingKey)
    {
        if(Float.isFinite(explicitAtFrame) && explicitAtFrame>=0F)
            return clamp(explicitAtFrame);
        float sampled=clamp(sampledWeight);
        // The first "CREATE KEY" button should turn the solver ON.
        // Explicitly authored 0% keys and already-authored channels
        // remain 0% and can still be used to cut back to animation.
        return !anyExistingKey && sampled<=0F?1F:sampled;
    }

    private static float clamp(float weight)
    {
        if(!Float.isFinite(weight))return 0F;
        return Math.max(0F,Math.min(1F,weight));
    }
}
