package com.example.examplemod;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Isolated OptiFine terrain lightmap transform for the reflected chunk VBO.
 *
 * BSL's gbuffers_terrain vertex shader uses:
 *     lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
 * The packed chunk lightmap coordinates use the vanilla 0..240 scale.
 * RenderWorldLast may leave texture unit 1's matrix reset/reconfigured by
 * another render stage; our manual terrain pass does not call the normal
 * EntityRenderer.enableLightmap lifecycle.
 *
 * Preserve the matrix stack for texture unit 1, install Minecraft's
 * standard lightmap transform (1/256 scale, +8 texel offset), and restore
 * both the original texture matrix and the caller's active texture/mode
 * on close. The independent BSL sky, actors and main world aren't touched.
 */
public final class CameraMirrorTerrainLightmap implements AutoCloseable
{
    private static final String TAG="[BBS Editor][Mirror][Terrain Lightmap] ";
    private static boolean reported;
    private final int oldUnit,oldMode;
    private boolean closed;

    private CameraMirrorTerrainLightmap()
    {
        oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        try
        {
            if(!reported)
            {
                FloatBuffer prior=BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX,prior);
                reported=true;
                System.out.println(TAG+"before: scale="+
                        prior.get(0)+","+prior.get(5)+
                        " offset="+prior.get(12)+","+prior.get(13)+
                        "; applying vanilla chunk lightmap matrix");
            }
            GL11.glLoadIdentity();
            GL11.glScalef(1F/256F,1F/256F,1F/256F);
            GL11.glTranslatef(8F,8F,8F);
        }
        catch(Throwable error)
        {
            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMode);
            GL13.glActiveTexture(oldUnit);
            throw error instanceof RuntimeException?
                    (RuntimeException)error:new IllegalStateException(
                        "Mirror lightmap matrix setup failed",error);
        }
        GL11.glMatrixMode(oldMode);
        GL13.glActiveTexture(oldUnit);
    }

    public static CameraMirrorTerrainLightmap begin()
    {
        return new CameraMirrorTerrainLightmap();
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        // Restore using unit 1, regardless of whether the chunk renderer
        // left texture unit 0 or the lightmap unit selected.
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        try{GL11.glPopMatrix();}
        finally
        {
            GL11.glMatrixMode(oldMode);
            GL13.glActiveTexture(oldUnit);
        }
    }
}
