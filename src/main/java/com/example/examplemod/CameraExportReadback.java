package com.example.examplemod;

import java.nio.ByteBuffer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Read completed offline film frames, not the user's GUI screenshot.
 *
 * Must run on Minecraft's OpenGL render thread, after all scene/media passes
 * have finished, while no rendering is in progress. Coordinates are physical
 * framebuffer pixels, origin at the BOTTOM-LEFT.
 *
 * This helper deliberately refuses an unmatched rectangle: stretching a
 * GUI-sized preview to 1080p/4K is NOT high-resolution scene rendering.
 */
public final class CameraExportReadback
{
    private final CameraExportPlan plan;
    private final ByteBuffer pixels;

    public CameraExportReadback(CameraExportPlan plan)
    {
        if(plan==null)throw new IllegalArgumentException("Missing output plan");
        this.plan=plan;
        // Direct memory is mandatory for LWJGL 2 glReadPixels().
        this.pixels=BufferUtils.createByteBuffer(plan.frameBytes);
    }

    /**
     * Caller owns the returned buffer until it has been consumed by FFmpeg
     * or copied to the encoder queue. Its position is 0 and limit is
     * exactly width*height*4. The buffer is REUSED on the next readback.
     */
    public ByteBuffer capture(Framebuffer source,int x,int y,
            int width,int height)
    {
        if(source==null||source.framebufferObject<=0)
            throw new IllegalArgumentException("Invalid export framebuffer");
        if(width!=plan.width||height!=plan.height)
            throw new IllegalArgumentException(
                    "Offline frame is "+width+"x"+height+", requested "+
                    plan.width+"x"+plan.height+
                    ". Native output-resolution world render is required.");
        if(x<0||y<0||(long)x+width>source.framebufferWidth||
                (long)y+height>source.framebufferHeight)
            throw new IllegalArgumentException(
                    "Export frame lies outside its source framebuffer");

        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldPack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        boolean sourceBound=false;
        int sourceReadBuffer=GL30.GL_COLOR_ATTACHMENT0;
        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    source.framebufferObject);
            sourceBound=true;
            sourceReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            pixels.clear();
            GL11.glReadPixels(x,y,width,height,GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,pixels);
            int error=GL11.glGetError();
            if(error!=GL11.GL_NO_ERROR)
                throw new IllegalStateException(
                        "Offline glReadPixels failed: "+error);
            pixels.position(0);
            pixels.limit(plan.frameBytes);
            return pixels.asReadOnlyBuffer();
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,oldPack);
            // READ_BUFFER belongs to the bound framebuffer. Do not restore
            // the original FBO's value into the source's attachment state.
            if(sourceBound)GL11.glReadBuffer(sourceReadBuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
        }
    }

    public int byteLength(){return plan.frameBytes;}
}
