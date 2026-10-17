package com.example.examplemod;
/**
 * Compiler-only interface stub for testing generic collider extraction
 * without Forge, Minecraft, LWJGL or any live actor render.
 */
public final class BodyPartPhysicsWorldPose
{
    private BodyPartPhysicsWorldPose() {}
    public interface MatrixSource
    {
        float[] getBoneWorldMatrix(String boneName);
    }
}
