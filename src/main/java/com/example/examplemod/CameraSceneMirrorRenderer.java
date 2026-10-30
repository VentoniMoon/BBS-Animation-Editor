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
 * frame. Captures during RenderWorldLast AFTER the normal world and actor
 * pass, using the current World's existing chunk draw list and the prepared
 * actor pose. Only the framebuffer is separate, not the world/chunk renderer.
 * Active shaderpacks are guarded until a compatible reflection path exists.
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
    private static String captureStatus="MIRROR: awaiting first frame";
    private static boolean shaderDiscoveryComplete;
    private static Method optiFineShadersActive;
    private static Field optiFineShaderLoaded;
    private static Field minecraftFramebufferField;
    private static boolean framebufferFieldSearched;
    private CameraSceneMirrorRenderer(){}

    public static boolean isCapturing(){return capturing;}
    public static boolean isFailed(){return failed;}
    public static String status()
    {
        return captureStatus;
    }
    /** Short, editor-only diagnostic for a selected mirror surface. */
    public static String surfaceHint()
    {
        if(!capturedId.isEmpty())return "";
        if(blockedByShaderpack)return "SHADERPACK ON - REFLECTION PAUSED";
        if(failed)return "MIRROR ERROR - SEE latest.log";
        return captureStatus;
    }
    public static boolean hasCapturedReflection()
    {
        return !capturedId.isEmpty() && capturedPose!=null;
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
        // Reflection now shares the original world's existing render chunks.
        // Only release its private color/depth target on editor detach.
        failed=false;warned=false;
        blockedByShaderpack=false;
        captureStatus="MIRROR: awaiting first frame";
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
        if(capturing)return;
        if(failed)
        {
            captureStatus="MIRROR ERROR - SEE latest.log";
            return;
        }
        if(mc==null||mirrors==null||mc.world==null||
                camera==null||pass==null)
        {
            captureStatus="MIRROR: world not ready";
            return;
        }
        if("false".equalsIgnoreCase(System.getProperty(
                "bbs.editor.camera.mirrors","true")))
        {
            captureStatus="MIRROR DISABLED BY JVM FLAG";
            return;
        }
        captureStatus="MIRROR: waiting for capture";
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
            captureStatus="SHADERPACK ON - REFLECTION PAUSED";
            // Mirror-specific chunks may have been created with shaders OFF
            // earlier in this editor session. Dispose them after a pack is
            // enabled; otherwise they keep receiving world block updates.
            if(CameraMirrorWorldIsolation.ready())
                CameraMirrorWorldIsolation.reset(mc);
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
        if(picked==null)
        {
            captureStatus="MIRROR: outside view / back-facing";
            return;
        }

        final Framebuffer source=mc.getFramebuffer();
        if(source==null||source.framebufferObject<=0||
                source.framebufferWidth<1||source.framebufferHeight<1)
        {
            captureStatus="MIRROR: world framebuffer unavailable";
            return;
        }
        int max=Math.max(source.framebufferWidth,source.framebufferHeight);
        double scale=Math.min(1D,1024D/max);
        int w=Math.max(1,(int)Math.round(source.framebufferWidth*scale));
        int h=Math.max(1,(int)Math.round(source.framebufferHeight*scale));
        if(!resolveMinecraftFramebufferField())
        {
            captureStatus="MIRROR: framebuffer field unavailable";
            return;
        }
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
            // Minecraft 1.12 EntityRenderer and RenderGlobal both call
            // mc.getFramebuffer().bindFramebuffer() internally. Binding a
            // custom FBO with GL alone is NOT enough; they switch back to
            // Minecraft.framebufferMc during renderWorld(). Temporarily
            // redirect that private field to our isolated target instead.
            capturing=true;
            activeMirror=picked;
            try
            {
                minecraftFramebufferField.set(mc,texture);
                texture.bindFramebuffer(true);
                texture.framebufferClear();
                texture.bindFramebuffer(true);
                // Define the mirror plane ONLY during the auxiliary pass.
                // The normal world must never receive this clipping state.
                installFrontClipPlane(reflected);
                pass.draw(reflected);
            }
            finally
            {
                try
                {
                    // Do not let the next normal render, GUI or film export
                    // draw into the reflection target.
                    minecraftFramebufferField.set(mc,source);
                }
                finally
                {
                    capturing=false;
                    activeMirror=null;
                }
            }
            // No blit from Minecraft's primary framebuffer: that would
            // capture the PREVIOUS or normal camera view (a fake window).
            // The reflected world has already rendered into texture FBO.
            if(texture.framebufferTexture<=0)
                throw new IllegalStateException("Mirror texture missing");
            capturedId=picked.id;
            capturedPose=reflected;
            captureStatus=clipPlaneInstalled?
                    "MIRROR: captured isolated FBO (clip requested)":
                    "MIRROR: captured isolated FBO (clip unavailable)";
            sourceWidth=w;
            sourceHeight=h;
        }
        catch(Throwable t)
        {
            failed=true;
            capturedId="";
            capturedPose=null;
            captureStatus="MIRROR ERROR - SEE latest.log";
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
            // Even a broken third-party renderer that under/overflows its
            // matrix stack cannot prevent the framebuffer/program/clip state
            // from being restored on the normal native world pass.
            try
            {
                restoreMatrix(GL11.GL_MODELVIEW,
                        GL11.GL_MODELVIEW_STACK_DEPTH,modelviewDepth);
                restoreMatrix(GL11.GL_PROJECTION,
                        GL11.GL_PROJECTION_STACK_DEPTH,projectionDepth);
            }
            finally
            {
                try
                {
                    GL11.glMatrixMode(previousMatrixMode);
                    GL11.glPopAttrib();
                }
                finally
                {
                    if(!clipPreviouslyEnabled)
                        GL11.glDisable(GL11.GL_CLIP_PLANE0);
                    GL13.glActiveTexture(oldTexture);
                    GL20.glUseProgram(oldShader);
                }
            }
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
     * Forge 1.12.2 Minecraft.framebufferMc is private. The mapped field
     * name is used in the dev client, SRG is retained as fallback.
     * A missing field must fail closed; never copy the normal camera as
     * a pretend reflection.
     */
    private static boolean resolveMinecraftFramebufferField()
    {
        if(framebufferFieldSearched)return minecraftFramebufferField!=null;
        framebufferFieldSearched=true;
        for(String name:new String[]{"framebufferMc","field_147124_at"})
        {
            try
            {
                Field f=Minecraft.class.getDeclaredField(name);
                if(!Framebuffer.class.isAssignableFrom(f.getType()))
                    continue;
                f.setAccessible(true);
                minecraftFramebufferField=f;
                break;
            }
            catch(Throwable notFound){}
        }
        return minecraftFramebufferField!=null;
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
