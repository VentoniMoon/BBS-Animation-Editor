package com.example.examplemod;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.glu.GLU;

/**
 * Render the Jolt diagnostic AFTER the actor's shader/FBO pass, in exactly
 * the same GUI rectangle and editor camera space as EditorGizmoController.
 *
 * The earlier diagnostic drew in RenderManager's entity pass. In particular
 * OptiFine may still have a shader program / world state active there, so a
 * glBegin wireframe can be invisible even when an actual Jolt collider
 * exists. The existing gizmo is known to be visible in this framebuffer.
 *
 * No normal model render, physics tick or animation change is performed.
 * All OpenGL viewport, projection, shader and fixed-function state is saved.
 */
public final class JoltContactOverlayPass
{
    private JoltContactOverlayPass() {}

    public static void render(Minecraft mc,EditorCamera camera,
            int x,int y,int width,int height,Runnable draw)
    {
        if(mc==null || camera==null || draw==null ||
                width<1 || height<1)return;

        ScaledResolution scaled=new ScaledResolution(mc);
        int factor=Math.max(1,scaled.getScaleFactor());
        int fbWidth=Math.max(1,mc.displayWidth);
        int fbHeight=Math.max(1,mc.displayHeight);
        int px=Math.max(0,x*factor);
        int py=Math.max(0,fbHeight-(y+height)*factor);
        int pw=Math.min(width*factor,fbWidth-px);
        int ph=Math.min(height*factor,fbHeight-py);
        if(pw<=0||ph<=0)return;

        int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer vp=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,vp);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            GL11.glViewport(px,py,pw,ph);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(px,py,pw,ph);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try
            {
                GL11.glLoadIdentity();
                GLU.gluPerspective(60F,
                        (float)width/(float)height,0.05F,500F);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try
                {
                    GL11.glLoadIdentity();
                    GL11.glRotatef(-camera.getPitch(),1F,0F,0F);
                    GL11.glRotatef(-camera.getYaw(),0F,1F,0F);
                    GL11.glTranslated(-camera.getCameraX(),
                            -camera.getCameraY(),-camera.getCameraZ());

                    GL20.glUseProgram(0);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    draw.run();
                }
                finally
                {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            }
            finally
            {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
        }
        finally
        {
            GL20.glUseProgram(oldProgram);
            GL11.glPopAttrib();
            GL11.glViewport(vp.get(0),vp.get(1),vp.get(2),vp.get(3));
            GL11.glMatrixMode(oldMode);
        }
    }
}
