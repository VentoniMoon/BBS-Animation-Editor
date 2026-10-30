package com.example.examplemod;

import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Experimental planar world reflection, one camera/framebuffer per editor
 * frame. The auxiliary scene pass is performed before Minecraft's normal
 * world pass, never from the GUI or from inside RenderWorldLast. Reflection
 * Recorded actors are re-rendered from the reflected camera using the
 * existing same-tick pose/Jolt cache; there is no second physics time step.
 *
 * OptiFine packs with incompatible auxiliary rendering are guarded: if a
 * secondary world pass fails, mirror capture is disabled for this session.
 */
public final class CameraSceneMirrorRenderer
{
    public interface ReflectionPass
    {
        void draw(PreviewCameraPose pose) throws Throwable;
    }
    private static Framebuffer texture;
    private static String capturedId="";
    private static PreviewCameraPose capturedPose;
    private static boolean capturing;
    private static boolean failed;
    private static boolean warned;
    private static int sourceWidth,sourceHeight;
    private static net.minecraft.world.World lastWorld;
    private CameraSceneMirrorRenderer(){}

    public static boolean isCapturing(){return capturing;}
    public static boolean isFailed(){return failed;}
    public static String status()
    {
        if(failed)return "Mirror renderer unavailable (see latest.log)";
        return capturedId.isEmpty()?"Mirror world view pending":
                "Planar world reflection active";
    }
    public static void reset()
    {
        capturedId="";
        capturedPose=null;
        if(texture!=null)
        {
            texture.deleteFramebuffer();
            texture=null;
        }
        lastWorld=null;
        failed=false;warned=false;
    }

    /** Choose one visible mirror; never recursively render N mirrors. */
    public static void capture(Minecraft mc,CameraSceneMirrors mirrors,
            PreviewCameraPose camera,ReflectionPass pass)
    {
        capturedId="";
        capturedPose=null;
        if(capturing||failed||mc==null||mirrors==null||
                mc.world==null||camera==null||pass==null||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrors","true")))return;
        if(lastWorld!=null&&lastWorld!=mc.world)reset();
        lastWorld=mc.world;
        CameraSceneMirrors.Mirror picked=null;
        double closest=Double.POSITIVE_INFINITY;
        for(CameraSceneMirrors.Mirror m:mirrors.all())
        {
            if(!m.enabled||m.width<=0D||m.height<=0D)continue;
            if(!visible(mc,m,camera))continue;
            double side=CameraMirrorMath.side(
                    m.x,m.z,camera.x,camera.z,m.yaw);
            // This mirror is opaque and only reflective on its front.
            if(side<=0.02D||side>128D)continue;
            double distance=Math.hypot(camera.x-m.x,camera.z-m.z);
            if(distance<closest){closest=distance;picked=m;}
        }
        if(picked==null)return;

        Framebuffer source=mc.getFramebuffer();
        if(source==null||source.framebufferObject<=0||
                source.framebufferWidth<1||source.framebufferHeight<1)return;
        int max=Math.max(source.framebufferWidth,source.framebufferHeight);
        double scale=Math.min(1D,1024D/max);
        int w=Math.max(1,(int)Math.round(source.framebufferWidth*scale));
        int h=Math.max(1,(int)Math.round(source.framebufferHeight*scale));
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldShader=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        IntBuffer oldViewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,oldViewport);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        try
        {
            if(texture==null)texture=new Framebuffer(w,h,true);
            else if(texture.framebufferWidth!=w||
                    texture.framebufferHeight!=h)
                texture.createBindFramebuffer(w,h);
            PreviewCameraPose reflected=CameraMirrorMath.reflect(camera,
                    picked.x,picked.y,picked.z,picked.yaw);
            capturing=true;
            try
            {
                // The stock Minecraft world renderer still owns all chunk,
                // fog and shader passes. It renders once into its usual FBO;
                // the next main world render overwrites those pixels.
                pass.draw(reflected);
            }
            finally {capturing=false;}

            // Snapshot before vanilla overwrites its framebuffer. World
            // clipping/depth are provided by the main pass later.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    source.framebufferObject);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    texture.framebufferObject);
            // Ignore unrelated OptiFine GL errors from earlier passes.
            for(int attempt=0;attempt<16 &&
                    GL11.glGetError()!=GL11.GL_NO_ERROR;attempt++){}
            GL30.glBlitFramebuffer(0,0,source.framebufferWidth,
                    source.framebufferHeight,0,0,w,h,
                    GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            int error=GL11.glGetError();
            if(error!=GL11.GL_NO_ERROR)
                throw new IllegalStateException("Reflection blit GL "+error);
            capturedId=picked.id;
            capturedPose=reflected;
            sourceWidth=source.framebufferWidth;
            sourceHeight=source.framebufferHeight;
        }
        catch(Throwable t)
        {
            failed=true;
            capturedId="";
            capturedPose=null;
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][Mirror] Auxiliary world pass "+
                        "disabled after failure: "+t);
                t.printStackTrace();
            }
        }
        finally
        {
            capturing=false;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(oldViewport.get(0),oldViewport.get(1),
                    oldViewport.get(2),oldViewport.get(3));
            GL11.glPopAttrib();
            GL13.glActiveTexture(oldTexture);
            GL20.glUseProgram(oldShader);
        }
    }

    private static boolean visible(Minecraft mc,
            CameraSceneMirrors.Mirror m,PreviewCameraPose camera)
    {
        int w=Math.max(1,mc.displayWidth),h=Math.max(1,mc.displayHeight);
        float[] center=PreviewCameraProjection.project(
                m.x,m.y,m.z,camera,0,0,w,h);
        if(inScreen(center,w,h))return true;
        double[][] corners=CameraMirrorMath.corners(
                m.x,m.y,m.z,m.yaw,m.width,m.height);
        for(double[] p:corners)
            if(inScreen(PreviewCameraProjection.project(
                    p[0],p[1],p[2],camera,0,0,w,h),w,h))
                return true;
        // A huge nearby rectangle may fully enclose the view although all
        // of its corners are outside. Keep it rather than flicker.
        return m.width>8D&&
                Math.hypot(camera.x-m.x,camera.z-m.z)<6D;
    }
    private static boolean inScreen(float[] p,int w,int h)
    {
        return p!=null&&p[0]>=-8&&p[0]<=w+8&&
                p[1]>=-8&&p[1]<=h+8;
    }

    /** Render static mirror quads directly into the native world depth. */
    public static void render(Minecraft mc,CameraSceneMirrors mirrors,
            PreviewCameraPose camera)
    {
        if(capturing||mc==null||mirrors==null||camera==null)return;
        RenderManager manager=mc.getRenderManager();
        if(manager==null)return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try
        {
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glColor4f(1F,1F,1F,1F);
            GL11.glTranslated(-manager.viewerPosX,
                    -manager.viewerPosY,-manager.viewerPosZ);
            for(CameraSceneMirrors.Mirror m:mirrors.all())
            {
                if(!m.enabled||CameraMirrorMath.side(m.x,m.z,camera.x,
                        camera.z,m.yaw)<=0.01D)continue;
                drawQuad(m);
            }
        }
        finally
        {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
            GL13.glActiveTexture(unit);
            GL20.glUseProgram(program);
        }
    }

    private static void drawQuad(CameraSceneMirrors.Mirror m)
    {
        double[][] corners=CameraMirrorMath.corners(m.x,m.y,m.z,
                m.yaw,m.width,m.height);
        // Place the face just ahead of the wall it is mounted on to avoid
        // coplanar z-fighting with the wooden/stone block behind it.
        double[] n=CameraMirrorMath.normal(m.yaw);
        for(double[] p:corners)
        {p[0]+=n[0]*0.015D;p[2]+=n[2]*0.015D;}
        boolean ready=texture!=null&&texture.framebufferTexture!=0&&
                capturedPose!=null&&m.id.equals(capturedId);
        float[][] uv=new float[4][];
        if(ready)
        {
            for(int i=0;i<4;i++)
            {
                float[] pixel=PreviewCameraProjection.project(
                        corners[i][0],corners[i][1],corners[i][2],
                        capturedPose,0,0,sourceWidth,sourceHeight);
                if(pixel==null){ready=false;break;}
                uv[i]=new float[]{pixel[0]/sourceWidth,
                        1F-pixel[1]/sourceHeight};
            }
        }
        if(ready)
        {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    texture.framebufferTexture);
            GL11.glColor4f(1F,1F,1F,1F);
        }
        else
        {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(0.13F,0.20F,0.25F,1F);
        }
        GL11.glBegin(GL11.GL_QUADS);
        for(int i=0;i<4;i++)
        {
            if(ready)GL11.glTexCoord2f(uv[i][0],uv[i][1]);
            GL11.glVertex3d(corners[i][0],corners[i][1],corners[i][2]);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2F);
        GL11.glColor4f(0.62F,0.72F,0.76F,1F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<4;i++)
            GL11.glVertex3d(corners[i][0],corners[i][1],corners[i][2]);
        GL11.glEnd();
    }
}
