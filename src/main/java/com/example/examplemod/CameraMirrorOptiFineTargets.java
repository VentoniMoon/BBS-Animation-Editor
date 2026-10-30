package com.example.examplemod;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Private multi-render-target resources for a single OptiFine G5 mirror.
 *
 * BSL 8.2 BetterLights reports eight color GBuffer outputs and three
 * depth textures. A vanilla Minecraft Framebuffer has only one color
 * attachment, and is not a valid target for BSL's world/composite programs.
 *
 * This object owns its OpenGL textures and FBO exclusively. It does not
 * allocate a Minecraft world, replace RenderGlobal, change OptiFine's
 * global dfb/sfb/FlipTextures, or rerun the normal scene/physics pipeline.
 *
 * One depth attachment is bound for geometry; two additional depth textures
 * are reserved for OptiFine's per-stage copies. Each color output has two
 * independent textures for composite ping-pong. This is the resource layer,
 * not a completed BSL composite executor.
 *
 * Must be created, resized and deleted on the Minecraft GL render thread.
 */
public final class CameraMirrorOptiFineTargets implements AutoCloseable
{
    public static final int MAX_SIZE=768;
    private int fbo;
    private int width,height,colorCount,depthCount;
    private int[][] colors;
    private int[] formats;
    private int[] depths;
    private int[] front;
    private boolean closed;
    private static String lastProblem="";

    public static String lastProblem(){return lastProblem;}
    public int width(){return width;}
    public int height(){return height;}
    public int framebuffer(){return fbo;}
    public int colorCount(){return colorCount;}
    public int depthCount(){return depthCount;}
    public int color(int index){return colors[index][front[index]];}
    public int colorBack(int index){return colors[index][1-front[index]];}
    public int depth(int index){return depths[index];}

    private CameraMirrorOptiFineTargets(){}

    public static CameraMirrorOptiFineTargets allocate(
            int requestedWidth,int requestedHeight,int colorBuffers,
            int depthBuffers)
    {
        return allocate(requestedWidth,requestedHeight,colorBuffers,
                depthBuffers,null);
    }

    /**
     * @param nativeFormats actual OptiFine gbuffersFormat[] enums (null to
     * use 16-bit float). Matching these matters for BSL lighting/encoding;
     * the same world does not imply all colortex buffers use RGBA16F.
     */
    public static CameraMirrorOptiFineTargets allocate(
            int requestedWidth,int requestedHeight,int colorBuffers,
            int depthBuffers,int[] nativeFormats)
    {
        if(requestedWidth<=0||requestedHeight<=0||
                colorBuffers<1||colorBuffers>8||
                depthBuffers<1||depthBuffers>3)
            throw new IllegalArgumentException("Invalid mirror GBuffer dimensions");
        int maxWidth=GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        int maxColors=GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        int maxDraw=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        if(colorBuffers>maxColors||colorBuffers>maxDraw)
        {
            lastProblem="GPU MRT limit: color="+maxColors+
                    ", draw="+maxDraw+", BSL requires "+colorBuffers;
            throw new IllegalStateException(lastProblem);
        }
        float scale=Math.min(1F,(float)MAX_SIZE/
                Math.max(requestedWidth,requestedHeight));
        int w=Math.max(1,Math.round(requestedWidth*scale));
        int h=Math.max(1,Math.round(requestedHeight*scale));
        if(w>maxWidth||h>maxWidth)
            throw new IllegalStateException("Mirror texture exceeds hardware limit");
        State saved=new State();
        CameraMirrorOptiFineTargets out=new CameraMirrorOptiFineTargets();
        out.width=w;out.height=h;
        out.colorCount=colorBuffers;out.depthCount=depthBuffers;
        out.colors=new int[colorBuffers][2];
        out.formats=new int[colorBuffers];
        out.depths=new int[depthBuffers];
        out.front=new int[colorBuffers];
        try
        {
            out.fbo=GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,out.fbo);
            for(int i=0;i<colorBuffers;i++)
            {
                int format=nativeFormats!=null&&i<nativeFormats.length?
                        nativeFormats[i]:GL30.GL_RGBA16F;
                // Reject unknown formats instead of silently violating
                // BSL's shader-specific colortex data encoding.
                if(!isSupportedColorFormat(format))
                    throw new IllegalStateException(
                            "Unsupported OptiFine colortex"+i+
                            " internal format: "+format);
                out.formats[i]=format;
                for(int side=0;side<2;side++)
                    out.colors[i][side]=makeColorTexture(w,h,format);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0+i,
                        GL11.GL_TEXTURE_2D,out.colors[i][0],0);
            }
            for(int i=0;i<depthBuffers;i++)
                out.depths[i]=makeDepthTexture(w,h);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,
                    out.depths[0],0);
            out.useAllDrawBuffers();
            out.requireComplete("geometry");
            lastProblem="";
            return out;
        }
        catch(Throwable error)
        {
            out.close();
            lastProblem="Mirror GBuffer allocation: "+error.getMessage();
            throw error instanceof RuntimeException?(RuntimeException)error:
                    new IllegalStateException(lastProblem,error);
        }
        finally{saved.restore();}
    }

    /** Mirror geometry writes to 8 independent GBuffer color attachments. */
    public void bindGeometry()
    {
        checkOpen();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        for(int i=0;i<colorCount;i++)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,
                    color(i),0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,
                depths[0],0);
        useAllDrawBuffers();
        requireComplete("geometry rebind");
        GL11.glViewport(0,0,width,height);
    }

    /** Attach only the requested back buffers, in shader output order.
     * Unused attachments are detached to avoid texture feedback hazards.
     * The front texture of every channel remains available for sampling. */
    public void bindCompositeOutputs(CameraMirrorCompositePlan stage)
    {
        checkOpen();
        if(stage==null)throw new IllegalArgumentException("Missing stage");
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
        for(int i=0;i<colorCount;i++)
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0+i,GL11.GL_TEXTURE_2D,0,0);
        IntBuffer draw=BufferUtils.createIntBuffer(stage.targets.length);
        for(int i=0;i<stage.targets.length;i++)
        {
            int slot=stage.targets[i];
            if(slot<0||slot>=colorCount)
                throw new IllegalArgumentException("Invalid colortex "+slot);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0+slot,GL11.GL_TEXTURE_2D,
                    colorBack(slot),0);
            draw.put(GL30.GL_COLOR_ATTACHMENT0+slot);
        }
        draw.flip();
        GL20.glDrawBuffers(draw);
        requireComplete("composite "+stage.name);
        GL11.glViewport(0,0,width,height);
    }

    public void commitCompositeOutputs(CameraMirrorCompositePlan stage)
    {
        checkOpen();
        for(int i=0;i<stage.targets.length;i++)
            if(stage.flip[i])
                front[stage.targets[i]]=1-front[stage.targets[i]];
    }

    /** Copy the live mirror depth into OptiFine depthtex1/2 at the normal
     * opaque/translucent boundary. This modifies PRIVATE textures only. */
    public void copyDepth(int index)
    {
        checkOpen();
        if(index<=0||index>=depthCount)
            throw new IllegalArgumentException("Invalid depth snapshot "+index);
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,depths[index]);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,
                    0,0,0,0,width,height);
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
            GL13.glActiveTexture(oldActive);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
        }
    }

    public void swapColor(int index)
    {
        checkOpen();
        if(index<0||index>=colorCount)
            throw new IllegalArgumentException("Invalid colortex "+index);
        front[index]=1-front[index];
    }

    private static boolean isSupportedColorFormat(int fmt)
    {
        // Common OptiFine 1.12.2 shaderpack format enum values.
        switch(fmt)
        {
            case 32849: // RGB8
            case 32856: // RGBA8
            case 32859: // RGBA16
            case 34842: // RGBA16F
            case 34843: // RGB16F
            case 34836: // RGBA32F
            case 34837: // RGB32F
            case 33321: // R8
            case 33323: // RG8
            case 33325: // R16F
            case 33327: // RG16F
                return true;
            default:return false;
        }
    }

    private static int makeColorTexture(int width,int height,int format)
    {
        int id=GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,
                GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,
                GL12.GL_CLAMP_TO_EDGE);
        int channels=(format==32849||format==34843||format==34837)?
                GL11.GL_RGB:
                (format==33321||format==33325)?GL11.GL_RED:
                (format==33323||format==33327)?GL30.GL_RG:GL11.GL_RGBA;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,width,height,
                0,channels,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
        return id;
    }

    private static int makeDepthTexture(int width,int height)
    {
        int id=GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,
                GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,
                GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,
                GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,
                width,height,0,GL11.GL_DEPTH_COMPONENT,
                GL11.GL_UNSIGNED_INT,(java.nio.ByteBuffer)null);
        return id;
    }

    private void useAllDrawBuffers()
    {
        IntBuffer draw=BufferUtils.createIntBuffer(colorCount);
        for(int i=0;i<colorCount;i++)
            draw.put(GL30.GL_COLOR_ATTACHMENT0+i);
        draw.flip();
        GL20.glDrawBuffers(draw);
    }

    private void requireComplete(String phase)
    {
        int result=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if(result!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Mirror "+phase+
                    " framebuffer incomplete: 0x"+
                    Integer.toHexString(result));
    }
    private void checkOpen()
    {
        if(closed||fbo==0)
            throw new IllegalStateException("Mirror target already released");
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        // Deleting currently bound textures must not leave texture ids
        // dangling in the ordinary BBS FS world pipeline. Caller captures
        // and restores the draw FBO/texture state around this operation.
        if(colors!=null)
            for(int[] side:colors)
                for(int id:side)if(id>0)GL11.glDeleteTextures(id);
        if(depths!=null)
            for(int id:depths)if(id>0)GL11.glDeleteTextures(id);
        if(fbo>0)GL30.glDeleteFramebuffers(fbo);
        fbo=0;
    }

    /** Preserve and restore Minecraft/OptiFine's draw/read framebuffers. */
    private static final class State
    {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int textureUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final IntBuffer viewport=BufferUtils.createIntBuffer(4);
        State(){GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);}
        void restore()
        {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL13.glActiveTexture(textureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
        }
    }
}
