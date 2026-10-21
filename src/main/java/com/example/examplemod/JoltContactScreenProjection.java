package com.example.examplemod;

/**
 * Pure Java 8 mapping from Jolt native-world coordinates to screen pixels.
 * Kept algebraically identical to EditorGizmoController.project(): no GL,
 * Minecraft or OptiFine dependencies and independently testable in CI.
 */
public final class JoltContactScreenProjection
{
    private JoltContactScreenProjection() {}

    public static float[] project(double x,double y,double z,
            EditorCamera camera,int viewportX,int viewportY,
            int viewportWidth,int viewportHeight)
    {
        return camera==null?null:PreviewCameraProjection.project(
                x,y,z,camera.sample(),viewportX,viewportY,
                viewportWidth,viewportHeight);
    }
}
