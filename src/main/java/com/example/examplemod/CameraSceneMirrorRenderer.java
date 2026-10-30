package com.example.examplemod;

import java.nio.IntBuffer;
import java.nio.DoubleBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static CameraSceneMirrors.Mirror activeMirror;
    private static boolean clipPlaneInstalled;
    private static boolean capturing;
    private static boolean failed;
    private static boolean warned;
    private static int sourceWidth,sourceHeight;
    private static net.minecraft.world.World lastWorld;
    private static boolean blockedByShaderpack;
    private static boolean shaderDiscoveryComplete;
    private static Method optiFineShadersActive;
    private static Field optiFineShaderLoaded;
    private CameraSceneMirrorRenderer(){}

    public static boolean isCapturing(){return capturing;}
    public static boolean isFailed(){return failed;}
    public static String status()
    {
        if(failed)return "Mirror capture disabled (see latest.log)";
        if(blockedByShaderpack)
            return "Shaderpack active - safe mirror fallback";
        return capturedId.isEmpty()?"Mirror world view pending":
                (clipPlaneInstalled?
                        "Reflection / GL clip requested":
                        "Reflection / clip not installed");
    }
    public static void reset()
    {
        capturedId="";
        capturedPose=null;
        activeMirror=null;
        clipPlaneInstalled=false;
        if(texture!=null)
        {
            texture.deleteFramebuffer();
            texture=null;
        }
        lastWorld=null;
        // Drop the secondary chunk/view-frustum renderer and its world
        // listeners. The editor's normal RenderGlobal remains untouched.
        CameraMirrorWorldIsolation.reset(Minecraft.getMinecraft());
        failed=false;warned=false;
        blockedByShaderpack=false;
    }

    /** Choose one visible mirror; never recursively render N mirrors. */
    public static void capture(Minecraft mc,CameraSceneMirrors mirrors,
            PreviewCameraPose camera,ReflectionPass pass)
    {
        capturedId="";
        capturedPose=null;
        activeMirror=null;
        clipPlaneInstalled=false;
        blockedByShaderpack=false;
        if(capturing||failed||mc==null||mirrors==null||
                mc.world==null||camera==null||pass==null||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrors","true")))return;
        if(lastWorld!=null&&lastWorld!=mc.world)reset();
        lastWorld=mc.world;
        // OptiFine maintains global shader program, framebuffer, draw-buffer
        // and shadow-pass state beyond OpenGL's attrib/matrix stacks.
        // A second renderWorld() cannot safely run alongside a shaderpack.
        // Opt-in remains available only for targeted shader development.
        if(shaderpackActive() &&
                !Boolean.getBoolean("bbs.editor.camera.mirrorUnsafeShaders"))
        {
            blockedByShaderpack=true;
            return;
        }
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
        int previousMatrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean clipPreviouslyEnabled=GL11.glIsEnabled(
                GL11.GL_CLIP_PLANE0);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        // GL attribute stacks do NOT preserve the projection/modelview
        // matrices. A secondary native render changes both.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        int projectionDepth=GL11.glGetInteger(
                GL11.GL_PROJECTION_STACK_DEPTH);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        int modelviewDepth=GL11.glGetInteger(
                GL11.GL_MODELVIEW_STACK_DEPTH);
        GL11.glMatrixMode(previousMatrixMode);

        try
        {
            if(texture==null)texture=new Framebuffer(w,h,true);
            else if(texture.framebufferWidth!=w||
                    texture.framebufferHeight!=h)
                texture.createBindFramebuffer(w,h);
            PreviewCameraPose reflected=CameraMirrorMath.reflect(camera,
                    picked.x,picked.y,picked.z,picked.yaw);
            capturing=true;
            activeMirror=picked;
            try
            {
                // Reject blocks and actors BEHIND the mirror (including
                // a wall into which the reflected eye may have been placed).
                // The native camera event reinstalls this after Minecraft
                // sets up the render view, in case shaders reset GL state.
                installFrontClipPlane(reflected);
                // The stock Minecraft world renderer still owns all chunk,
                // fog and shader passes. It renders once into its usual FBO;
                // the next main world render overwrites those pixels.
                pass.draw(reflected);
            }
            finally
            {
                capturing=false;
                activeMirror=null;
            }

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
            activeMirror=null;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(oldViewport.get(0),oldViewport.get(1),
                    oldViewport.get(2),oldViewport.get(3));
            // Vanilla/OptiFine must see exactly the same matrix stacks
            // after the reflected view as before it. Restore even when the
            // renderer returned from an unusual render error path.
            restoreMatrix(GL11.GL_MODELVIEW,
                    GL11.GL_MODELVIEW_STACK_DEPTH,modelviewDepth);
            restoreMatrix(GL11.GL_PROJECTION,
                    GL11.GL_PROJECTION_STACK_DEPTH,projectionDepth);
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glPopAttrib();
            if(!clipPreviouslyEnabled)
                GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL13.glActiveTexture(oldTexture);
            GL20.glUseProgram(oldShader);
        }
    }

    private static void restoreMatrix(int mode,int depthConstant,
            int expectedDepth)
    {
        GL11.glMatrixMode(mode);
        int depth=GL11.glGetInteger(depthConstant);
        // Guard against unbalanced matrix pushes inside third-party mods.
        for(int i=0;i<64&&depth>expectedDepth;i++,depth--)
            GL11.glPopMatrix();
        if(depth==expectedDepth)
            GL11.glPopMatrix();
    }

    /**
     * Install the mirror half-space in eye coordinates. This is invoked
     * once before the auxiliary render and again after native camera setup.
     * Force modelview identity while defining the plane: OpenGL transforms
     * the coefficients with the modelview in effect at glClipPlane time.
     *
     * Never apply to the normal world pass or editor GUI.
     */
    public static void installFrontClipPlane(PreviewCameraPose virtualCamera)
    {
        if(!capturing||activeMirror==null||virtualCamera==null)return;
        CameraSceneMirrors.Mirror mirror=activeMirror;
        double[] plane=CameraMirrorMath.frontClipPlane(virtualCamera,
                mirror.x,mirror.y,mirror.z,mirror.yaw,0.02D);
        DoubleBuffer data=BufferUtils.createDoubleBuffer(4);
        data.put(plane);
        data.flip();
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try
        {
            GL11.glLoadIdentity();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0,data);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            clipPlaneInstalled=true;
        }
        finally
        {
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
        }
    }

    /**
     * Runtime-safe OptiFine detection: do not import optional OptiFine
     * classes or depend on a particular Forge mapping at compile time.
     */
    private static boolean shaderpackActive()
    {
        if(!shaderDiscoveryComplete)
        {
            shaderDiscoveryComplete=true;
            try
            {
                Class<?> config=Class.forName("net.optifine.Config");
                optiFineShadersActive=config.getMethod("isShaders");
            }
            catch(Throwable absent){}
            try
            {
                Class<?> shader=Class.forName("net.optifine.shaders.Shaders");
                optiFineShaderLoaded=shader.getField("shaderPackLoaded");
            }
            catch(Throwable absent){}
        }
        try
        {
            if(optiFineShadersActive!=null &&
                    Boolean.TRUE.equals(optiFineShadersActive.invoke(null)))
                return true;
        }
        catch(Throwable reflectionProblem){}
        try
        {
            return optiFineShaderLoaded!=null &&
                    optiFineShaderLoaded.getBoolean(null);
        }
        catch(Throwable reflectionProblem){return false;}
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
                // Homogeneous texture coordinates: ordinary 2D UVs would
                // interpolate incorrectly at oblique mirror angles.
                double yaw=Math.toRadians(capturedPose.yaw);
                double pitch=Math.toRadians(capturedPose.pitch);
                double dz=corners[i][2]-capturedPose.z;
                double dx=corners[i][0]-capturedPose.x;
                double localZ=Math.sin(yaw)*dx+Math.cos(yaw)*dz;
                double depth=Math.sin(pitch)*
                        (corners[i][1]-capturedPose.y)-
                        Math.cos(pitch)*localZ;
                float q=(float)Math.max(0.001D,depth);
                uv[i]=new float[]{pixel[0]/sourceWidth,
                        1F-pixel[1]/sourceHeight,q};
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
            if(ready)GL11.glTexCoord4f(uv[i][0]*uv[i][2],
                    uv[i][1]*uv[i][2],0F,uv[i][2]);
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
