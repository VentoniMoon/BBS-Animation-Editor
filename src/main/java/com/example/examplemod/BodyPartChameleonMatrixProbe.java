package com.example.examplemod;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Reads native Chameleon bone matrices on the client rendering thread.
 * postRender() is the same native API already used by EditorGizmoController.
 * The model-view matrix starts at identity, so the result is in the
 * attached morph's local frame, not the Preview camera frame.
 */
public final class BodyPartChameleonMatrixProbe
{
    private BodyPartChameleonMatrixProbe() {}

    public static BodyPartPhysicsWorldPose.MatrixSource source(final Object morph)
    {
        if (morph == null || !morph.getClass().getName().endsWith(".ChameleonMorph"))
            return null;

        try
        {
            final Object chameleonModel = morph.getClass().getMethod("getModel").invoke(morph);
            if (chameleonModel == null) return null;
            final Object model = chameleonModel.getClass().getField("model").get(chameleonModel);
            if (model == null) return null;
            Class<?> modelClass = Class.forName("mchorse.chameleon.lib.data.model.Model");
            Class<?> renderer = Class.forName("mchorse.chameleon.lib.render.ChameleonRenderer");
            final Method postRender = renderer.getMethod("postRender", modelClass, String.class);
            return new BodyPartPhysicsWorldPose.MatrixSource()
            {
                @Override
                public float[] getBoneWorldMatrix(String name)
                {
                    int previousMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                    boolean pushed = false;
                    try
                    {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPushMatrix();
                        pushed = true;
                        GL11.glLoadIdentity();
                        Object ok = postRender.invoke(null, model, name);
                        if (ok instanceof Boolean && !((Boolean) ok).booleanValue())
                            return null;
                        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
                        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
                        float[] matrix = new float[16];
                        buffer.get(matrix);
                        return BodyPartPhysicsMatrixSpace.snapshot(matrix);
                    }
                    catch (Throwable error)
                    {
                        return null;
                    }
                    finally
                    {
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        if (pushed) GL11.glPopMatrix();
                        GL11.glMatrixMode(previousMode);
                    }
                }
            };
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }
}
