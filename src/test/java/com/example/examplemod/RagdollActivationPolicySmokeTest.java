package com.example.examplemod;

/**
 * Pure Java 8 regression for "RAGDOLL shows 0.000 but does nothing"
 * in both installed Forge 0.0.2 and the Gradle client.
 */
public final class RagdollActivationPolicySmokeTest
{
    private static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }
    private static void weight(float actual,float expected,String label)
    {
        check(Math.abs(actual-expected)<1E-6F,
                label+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args)
    {
        // Installed mod must respond to a positive CHARACTER key without
        // requiring development-only Gradle -PenableJoltRagdoll.
        check(RagdollActivationPolicy.runtimeEnabled(null),
                "normal installed JAR should activate keyed physics");
        check(RagdollActivationPolicy.runtimeEnabled("true"),
                "explicit opt-in remains supported");
        check(!RagdollActivationPolicy.runtimeEnabled("false"),
                "explicit JVM opt-out must never start Jolt");
        check(!RagdollActivationPolicy.runtimeEnabled("FALSE"),
                "case-insensitive emergency opt-out");

        // Previously CREATE/UPDATE KEY saved a new 0% key and therefore
        // permanently kept the actor animated. A first key must be 100%.
        weight(RagdollActivationPolicy.initialKeyWeight(-1F,0F,false),
                1F,"first key enables ragdoll");
        weight(RagdollActivationPolicy.initialKeyWeight(-1F,.35F,true),
                .35F,"new key inherits authored blend");
        weight(RagdollActivationPolicy.initialKeyWeight(-1F,0F,true),
                0F,"existing channel remains animation when requested");
        weight(RagdollActivationPolicy.initialKeyWeight(0F,.5F,true),
                0F,"explicit 0 percent never overwritten");
        weight(RagdollActivationPolicy.initialKeyWeight(.25F,.5F,true),
                .25F,"updated key retains exact value");
        weight(RagdollActivationPolicy.initialKeyWeight(1F,0F,true),
                1F,"existing full ragdoll remains full");
        weight(RagdollActivationPolicy.initialKeyWeight(Float.NaN,0F,false),
                1F,"corrupt optional key treated as missing");
        weight(RagdollActivationPolicy.initialKeyWeight(9F,0F,true),
                1F,"clamp invalid positive values");
        System.out.println("RagdollActivationPolicySmokeTest OK");
    }
}
