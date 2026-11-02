package com.example.examplemod;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Experimental late native OptiFine sun-shadow actor overlay.
 *
 * WorldLast occurs after vanilla entities and native shadow-map terrain.
 * Our recorded actors are only prepared then. For shaderpacks which read
 * the native shadow map in their deferred/composite pass, we can augment
 * the existing native depth atlas with those PREPARED actors before the
 * final composite. No terrain rerender, no actor animation/physics tick.
 *
 * This is deliberately OPT-IN: packs that calculate sun shadows in their
 * GBuffer geometry shaders have already shaded terrain before WorldLast.
 * In those packs a late atlas augmentation cannot retroactively change
 * the terrain pixels. Correct general support will require moving actor
 * preparation before OptiFine's native shadow render event.
 *
 * DO NOT clear native depth textures or redirect OptiFine's own sfb.
 * Attach the native depth textures to a short-lived private draw-only FBO
 * with no color targets; restore ALL GL state before the normal composite.
 */
public final class BbsFsNativeActorSunShadowOverlay
{
    private static final String TAG=
            "[BBS Editor][Actor Sun Shadow] ";
    private static boolean logged,warning;

    private BbsFsNativeActorSunShadowOverlay(){}

    public static void render(Minecraft mc,
            AnimationEditorScreen editor,PreviewCameraPose mainEye)
    {
        if(!Boolean.getBoolean(
                    "bbs.editor.camera.nativeActorSunShadows")||
                mc==null||editor==null||mainEye==null||
                mc.world==null)return;
        int fbo=0;
        int priorDraw=GL11.glGetInteger(
                GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int priorRead=GL11.glGetInteger(
                GL30.GL_READ_FRAMEBUFFER_BINDING);
        int priorProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int priorMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        int old4=0,old5=0;
        boolean pushed=false,projectionPushed=false,modelPushed=false;
        try
        {
            Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
            Object program=shaders.getField("ProgramShadow").get(null);
            if(program==null)return;
            int pid=((Number)program.getClass().getMethod("getId")
                    .invoke(program)).intValue();
            int count=intField(shaders,"usedShadowDepthBuffers");
            int width=intField(shaders,"shadowMapWidth");
            int height=intField(shaders,"shadowMapHeight");
            if(pid<=0||count<1||width<=0||height<=0)return;
            IntBuffer depths=(IntBuffer)field(shaders,
                    "sfbDepthTextures").get(null);
            if(depths==null||depths.limit()<count)return;
            float[] projection=matrix(shaders,
                    "shadowProjection","faShadowProjection");
            float[] view=matrix(shaders,
                    "shadowModelView","faShadowModelView");
            if(projection==null||view==null)return;

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            pushed=true;
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();projectionPushed=true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();modelPushed=true;

            // Native OptiFine binds shadowtex as samplers. When using the
            // same textures as depth attachments, unbind these sampler
            // units temporarily to avoid a read/write feedback loop.
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            old4=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE5);
            old5=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glViewport(0,0,width,height);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);
            GL11.glColorMask(false,false,false,false);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadMatrix(buffer(projection));
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadMatrix(buffer(view));
            // Native OptiFine shadowModelView consumes geometry relative
            // to the main view. The prepared actors use absolute world
            // coordinates and RenderManager.viewerPos=0.
            GL11.glTranslated(-mainEye.x,-mainEye.y,-mainEye.z);

            int total=0;
            // Both shadowtex0 and shadowtex1 must receive opaque actor
            // casters (the second depth texture is the pre-translucent
            // snapshot). We only write depth; colored translucent actor
            // shadows are not implemented by this experiment.
            for(int i=0;i<Math.min(2,count);i++)
            {
                int texture=depths.get(i);
                if(texture<=0)continue;
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,
                        texture,0);
                int status=GL30.glCheckFramebufferStatus(
                        GL30.GL_FRAMEBUFFER);
                if(status!=GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException(
                            "Native actor shadow FBO incomplete 0x"+
                            Integer.toHexString(status));
                GL20.glUseProgram(pid);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                total+=editor.renderPreparedActorsInShadow(mc);
                // Actor RenderManager may change the current program,
                // active texture, or FBO. Reset before the next depth map.
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
                GL11.glDrawBuffer(GL11.GL_NONE);
                GL11.glReadBuffer(GL11.GL_NONE);
                GL11.glViewport(0,0,width,height);
                GL11.glColorMask(false,false,false,false);
                GL11.glDepthMask(true);
            }
            if(total>0&&!logged)
            {
                logged=true;
                System.out.println(TAG+
                        "Augmented native OptiFine depth maps with "+
                        total+" prepared actor draws, width="+width+
                        " height="+height+" (late composite experiment)");
            }
        }
        catch(Throwable error)
        {
            if(!warning)
            {
                warning=true;
                System.err.println(TAG+
                        "Native shadow overlay unavailable: "+error);
                error.printStackTrace();
            }
        }
        finally
        {
            if(modelPushed)
            {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            }
            if(projectionPushed)
            {
                GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,priorRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,priorDraw);
            if(fbo>0)GL30.glDeleteFramebuffers(fbo);
            if(pushed)GL11.glPopAttrib();
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,old4);
            GL13.glActiveTexture(GL13.GL_TEXTURE5);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,old5);
            GL13.glActiveTexture(priorUnit);
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
            GL20.glUseProgram(priorProgram);
            GL11.glMatrixMode(priorMode);
        }
    }

    private static Field field(Class<?> clazz,String name)
            throws Exception
    {
        Field value=clazz.getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }

    private static int intField(Class<?> clazz,String name)
            throws Exception
    {
        return ((Number)field(clazz,name).get(null)).intValue();
    }

    private static float[] matrix(Class<?> clazz,String...names)
            throws Exception
    {
        for(String name:names)
        {
            Field f;
            try{f=field(clazz,name);}
            catch(NoSuchFieldException absent){continue;}
            Object value=f.get(null);
            if(value instanceof float[]&&((float[])value).length>=16)
            {
                float[] out=new float[16];
                System.arraycopy(value,0,out,0,16);
                return out;
            }
            if(value instanceof FloatBuffer&&
                    ((FloatBuffer)value).limit()>=16)
            {
                FloatBuffer b=((FloatBuffer)value).duplicate();
                b.position(0);
                float[] out=new float[16];
                b.get(out);
                return out;
            }
        }
        return null;
    }

    private static FloatBuffer buffer(float[] matrix)
    {
        FloatBuffer out=BufferUtils.createFloatBuffer(16);
        out.put(matrix,0,16);
        out.flip();
        return out;
    }
}
