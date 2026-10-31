package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Private BSL shadow map for the existing reflected WorldClient.
 *
 * Reuses already compiled RenderGlobal chunk VBOs. The light-space
 * projection/orientation comes from the shaderpack; camera-relative
 * geometry is centered on the reflected eye, not the main eye.
 * No OptiFine sfb, shadow textures, world, entity tick or chunk queue
 * is modified. Always invoked on Minecraft's render thread.
 *
 * Initial scope: chunk/terrain casters, including alpha-test and optional
 * translucent layers. Actor casters will require a separate prepared-pose
 * shadow hook; actor receiving shadows continues to work.
 */
public final class CameraMirrorPrivateShadowPass implements AutoCloseable
{
    private static final String TAG="[BBS Editor][Mirror][Private Shadow] ";
    private final int width,height,depthCount,colorCount;
    private final int framebuffer;
    private final int[] depths;
    private final int[] colors;
    private boolean ready,closed;
    private static boolean logged;

    private CameraMirrorPrivateShadowPass(int w,int h,int dc,int cc)
    {
        width=w;height=h;depthCount=dc;colorCount=cc;
        depths=new int[dc];
        colors=new int[cc];
        int previousDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int created=GL30.glGenFramebuffers();
        framebuffer=created;
        try
        {
            for(int i=0;i<dc;i++)
            {
                depths[i]=GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,depths[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                // OptiFine's shadowtex are depth-compare samplers.
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL14.GL_TEXTURE_COMPARE_MODE,GL14.GL_COMPARE_R_TO_TEXTURE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL14.GL_TEXTURE_COMPARE_FUNC,GL11.GL_LEQUAL);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,
                        GL14.GL_DEPTH_COMPONENT24,w,h,0,
                        GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_INT,
                        (ByteBuffer)null);
            }
            for(int i=0;i<cc;i++)
            {
                colors[i]=GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,colors[i]);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,
                        w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,
                        (ByteBuffer)null);
            }
            bindDraw();
            checkComplete();
        }
        catch(Throwable problem)
        {
            close();
            throw problem instanceof RuntimeException?(RuntimeException)problem:
                    new IllegalStateException("Private shadow allocation",problem);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previousDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
            GL13.glActiveTexture(oldUnit);
        }
    }

    public static CameraMirrorPrivateShadowPass acquire(
            CameraMirrorPrivateShadowPass existing,Class<?> shaders)
            throws Exception
    {
        int dc=readInt(shaders,"usedShadowDepthBuffers");
        int cc=readInt(shaders,"usedShadowColorBuffers");
        int width=readInt(shaders,"shadowMapWidth");
        int height=readInt(shaders,"shadowMapHeight");
        if(dc<1||dc>2||cc<0||cc>2||width<1||height<1)
            throw new IllegalStateException("Unsupported OptiFine shadow "+
                    "targets: depths="+dc+" colors="+cc+
                    " size="+width+"x"+height);
        int cap=Integer.getInteger(
                "bbs.editor.camera.mirrorBSLShadowMaxSize",2048);
        cap=Math.max(256,Math.min(4096,cap));
        float factor=Math.min(1F,cap/(float)Math.max(width,height));
        int w=Math.max(1,Math.round(width*factor));
        int h=Math.max(1,Math.round(height*factor));
        if(existing!=null&&!existing.closed&&
                existing.width==w&&existing.height==h&&
                existing.depthCount==dc&&existing.colorCount==cc)
            return existing;
        if(existing!=null)existing.close();
        return new CameraMirrorPrivateShadowPass(w,h,dc,cc);
    }

    private static int readInt(Class<?> shader,String name)throws Exception
    {
        Field f=shader.getDeclaredField(name);
        f.setAccessible(true);
        return ((Number)f.get(null)).intValue();
    }

    public boolean ready(){return ready;}

    public int depth0(){return depths[0];}
    public int depth1(){return depths[Math.min(1,depthCount-1)];}
    public int color0(){return colorCount>0?colors[0]:0;}
    public int color1(){return colorCount>1?colors[1]:0;}

    private void bindDraw()
    {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depths[0],0);
        for(int i=0;i<colorCount;i++)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0+i,
                    GL11.GL_TEXTURE_2D,colors[i],0);
        if(colorCount>0)
        {
            IntBuffer draw=BufferUtils.createIntBuffer(colorCount);
            for(int i=0;i<colorCount;i++)
                draw.put(GL30.GL_COLOR_ATTACHMENT0+i);
            draw.flip();
            GL20.glDrawBuffers(draw);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
        else
        {
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
        }
        GL11.glViewport(0,0,width,height);
    }

    private void checkComplete()
    {
        int s=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if(s!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Private shadow FBO incomplete 0x"+
                    Integer.toHexString(s));
    }

    /**
     * The model/view matrices are passed in explicit column-major OpenGL
     * form, using the SAME transform for writing the shadow map and for
     * sampling it in the reflected GBuffer. The existing shadow-map
     * projection covers the reflected eye's neighborhood instead of
     * borrowing the main camera's shadow-map coverage.
     */
    public void render(Minecraft mc,PreviewCameraPose reflected,
            PreviewCameraPose main,float partialTicks,int shadowProgram,
            Object optiProgram,Class<?> shaders,
            FloatBuffer projection,FloatBuffer lightView,
            CameraMirrorBSLCompositor owner)throws Exception
    {
        ready=false;
        if(mc==null||mc.renderGlobal==null||shadowProgram<=0||
                projection==null||lightView==null)
            throw new IllegalStateException("Private shadow prerequisites missing");
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try
        {
            bindDraw();checkComplete();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glColorMask(true,true,true,true);
            GL11.glClearDepth(1.0);
            GL11.glClearColor(1F,1F,1F,1F);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT|
                    (colorCount>0?GL11.GL_COLOR_BUFFER_BIT:0));

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            projection.rewind();
            GL11.glLoadMatrix(projection);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            lightView.rewind();
            GL11.glLoadMatrix(lightView);
            GL20.glUseProgram(shadowProgram);
            // Use raw program + synchronized OptiFine bookkeeping. Native
            // Shaders.useProgram would bind the MAIN shared sfb.
            owner.bindPrivateShadowProgram(optiProgram,shadowProgram,
                    projection,lightView);

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            mc.getTextureManager().bindTexture(
                    TextureMap.LOCATION_BLOCKS_TEXTURE);
            Entity eye=mc.getRenderViewEntity();
            RenderGlobal world=mc.renderGlobal;
            try(CameraMirrorChunkVisibility.Scope chunks=
                    CameraMirrorChunkVisibility.begin(mc,reflected,main))
            {
                if(chunks==null)throw new IllegalStateException(
                        "Shadow chunk frustum could not be prepared");
                world.renderBlockLayer(BlockRenderLayer.SOLID,
                        partialTicks,2,eye);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);
                world.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED,
                        partialTicks,2,eye);
                world.renderBlockLayer(BlockRenderLayer.CUTOUT,
                        partialTicks,2,eye);
                if(depthCount>1)
                {
                    // Native OptiFine copies opaque depth to shadowtex1
                    // BEFORE translucent geometry.
                    int oldTexture=GL11.glGetInteger(
                            GL11.GL_TEXTURE_BINDING_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,depths[1]);
                    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,
                            0,0,0,0,width,height);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
                }
                if("true".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLShadowTranslucent","true")))
                {
                    // Match OptiFine's native shadow-translucent pass:
                    // no framebuffer alpha blending; shadow.glsl writes
                    // its own water/glass color and depth coverage.
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glDepthMask(true);
                    world.renderBlockLayer(BlockRenderLayer.TRANSLUCENT,
                            partialTicks,2,eye);
                }
            }
            int err=GL11.glGetError();
            if(err!=GL11.GL_NO_ERROR)
                throw new IllegalStateException(
                        "Private shadow GL error 0x"+Integer.toHexString(err));
            ready=true;
            if(!logged)
            {
                logged=true;
                System.out.println(TAG+"Private mirror shadow rendered "+
                        width+"x"+height+" depth="+depthCount+
                        " color="+colorCount+
                        " (shared compiled chunks; main shadow map intact)");
            }
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
            GL20.glUseProgram(oldProgram);
            GL13.glActiveTexture(oldUnit);
            GL11.glMatrixMode(oldMode);
        }
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;ready=false;
        if(framebuffer!=0)GL30.glDeleteFramebuffers(framebuffer);
        for(int x:depths)if(x>0)GL11.glDeleteTextures(x);
        for(int x:colors)if(x>0)GL11.glDeleteTextures(x);
    }
}
