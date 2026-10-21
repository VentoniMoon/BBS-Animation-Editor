package com.example.examplemod;

/**
 * Explicit bridge between the editor's orbit camera convention and vanilla
 * Minecraft 1.12.2 first-person EntityRenderer.orientCamera convention.
 *
 * Editor yaw=0 views toward -Z and yaw=90 toward -X. The vanilla camera
 * entity yaw=180 views toward -Z and yaw=90 toward -X; vanilla's camera
 * transform includes entityYaw + 180 degrees.
 *
 * Do not change these axes while fixing individual Blockbuster/Chameleon
 * model transforms: native world, selected actors and gizmo MUST agree.
 */
public final class BbsFsCameraConventions
{
    private BbsFsCameraConventions() {}

    public static float entityYaw(float editorYaw)
    {
        return 180F - editorYaw;
    }

    public static float entityPitch(float editorPitch)
    {
        return -editorPitch;
    }

    /** Forge CameraSetup yaw is applied to the GL world camera. */
    public static float cameraSetupYaw(float editorYaw)
    {
        return 360F - editorYaw;
    }

    public static float cameraSetupPitch(float editorPitch)
    {
        return -editorPitch;
    }

    public static float cameraSetupRoll(float editorRoll)
    {
        return -editorRoll;
    }
}
