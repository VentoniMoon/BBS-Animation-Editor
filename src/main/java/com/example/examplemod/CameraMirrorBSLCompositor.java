package com.example.examplemod;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

/**
 * Experimental independent BSL/OptiFine G5 deferred compositor.
 *
 * Draws the SAME world and cached actors through OptiFine's ALREADY
 * COMPILED programs. Every color/depth texture it binds for sampling or
 * output belongs to CameraMirrorOptiFineTargets. OptiFine's dfb, sfb,
 * dfbColorTexturesFlip and beginRender/endRender are never changed.
 *
 * This early implementation intentionally requires a JVM opt-in and is
 * never used by the working no-shader mirror. Some pack-specific features
 * (custom uniforms, volumetrics, temporal history) may need more parity
 * work after visual game testing.
 */
public final class CameraMirrorBSLCompositor implements AutoCloseable
{
    private static final String TAG="[BBS Editor][Mirror][BSL Render] ";
    private static CameraMirrorBSLCompositor current;
    private static boolean firstFrameLogged;
    private static boolean skyStageLogged;
    private final CameraMirrorOptiFineTargets targets;
    private final PreviewCameraPose view;
    private final int mainProgram;
    private final int oldTextureUnit;
    private final int[] oldTextures=new int[16];
    private final int oldRead,oldDraw;
    private final int[] oldViewport=new int[4];
    private final List<UniformBackup> restored=
            new ArrayList<UniformBackup>();
    private final Class<?> shaders;
    private final Field worldRenderingFlag;
    private final Field[] safetyFlags;
    private final boolean[] originalFlags;
    private final Field activeProgramField;
    private final Field activeDrawBuffersField;
    private final Object originalOptiFineProgram;
    private final Object originalOptiFineDrawBuffers;
    private final Field activeProgramIdField;
    private final int originalOptiFineProgramId;
    private final Method getId;
    private final Method getDrawBuffers;
    private final Field finalProgram;
    private FloatBuffer mirrorProjection;
    private FloatBuffer mirrorView;
    private boolean opaqueDepthSaved;
    private boolean deferredExecuted;
    private boolean closed;

    private CameraMirrorBSLCompositor(CameraMirrorOptiFineTargets targets,
            PreviewCameraPose view)throws Exception
    {
        this.targets=targets;this.view=view;
        shaders=Class.forName("net.optifine.shaders.Shaders");
        // OptiFine's beginWater() can invoke GLOBAL renderDeferred() if
        // isRenderingWorld remains true. That would composite the normal
        // world in the middle of our reflection. Gate native shader hooks
        // OFF only for this auxiliary pass; our compiled GLSL programs are
        // selected explicitly with raw OpenGL. Restore flags in close().
        worldRenderingFlag=shaders.getField("isRenderingWorld");
        String[] states={"isRenderingWorld","isRenderingDfb",
                "isCompositeRendered","isRenderingSky"};
        safetyFlags=new Field[states.length];
        originalFlags=new boolean[states.length];
        for(int i=0;i<states.length;i++)
        {
            Field field=shaders.getDeclaredField(states[i]);
            field.setAccessible(true);
            safetyFlags[i]=field;
            originalFlags[i]=field.getBoolean(null);
        }
        activeProgramField=shaders.getField("activeProgram");
        activeDrawBuffersField=shaders.getDeclaredField("activeDrawBuffers");
        activeDrawBuffersField.setAccessible(true);
        originalOptiFineProgram=activeProgramField.get(null);
        originalOptiFineDrawBuffers=activeDrawBuffersField.get(null);
        activeProgramIdField=shaders.getField("activeProgramID");
        originalOptiFineProgramId=activeProgramIdField.getInt(null);
        Class<?> clazz=Class.forName("net.optifine.shaders.Program");
        getId=clazz.getMethod("getId");
        getDrawBuffers=clazz.getMethod("getDrawBuffers");
        finalProgram=shaders.getField("ProgramFinal");
        mainProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        oldTextureUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        IntBuffer vp=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,vp);
        for(int i=0;i<4;i++)oldViewport[i]=vp.get(i);
        try
        {
            for(int i=0;i<16;i++)
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                oldTextures[i]=GL11.glGetInteger(
                        GL11.GL_TEXTURE_BINDING_2D);
            }
        }
        finally{GL13.glActiveTexture(oldTextureUnit);}
    }

    public static CameraMirrorBSLCompositor begin(
            CameraMirrorOptiFineTargets targets,PreviewCameraPose view)
            throws Exception
    {
        if(current!=null)
            throw new IllegalStateException("Recursive BSL mirror compositor");
        CameraMirrorBSLCompositor result=
                new CameraMirrorBSLCompositor(targets,view);
        current=result;
        try
        {
            // Prevent the native OptiFine terrain/water hooks from
            // rendering deferred stages into its SHARED main-world dfb.
            // The private executor sets the actual GLSL programs itself.
            result.worldRenderingFlag.setBoolean(null,false);
            result.geometryBegin();
            return result;
        }
        catch(Throwable t)
        {
            try{result.close();}
            catch(Throwable restore){t.addSuppressed(restore);}
            if(t instanceof Exception)throw (Exception)t;
            throw new IllegalStateException("BSL geometry init failed",t);
        }
    }

    public static void stage(String name)
    {
        if(current!=null)current.geometryStage(name);
    }
    public static boolean active(){return current!=null;}

    /** The native OptiFine deferred boundary is before translucent water.
     * Running it in compose() AFTER all terrain corrupted depth and color
     * inputs. This invokes ONLY the private mirrored shader targets. */
    public static void runDeferredPasses()
    {
        if(current==null)return;
        // Raw GBuffer diagnostic must bypass ALL postprocessing stages.
        if(Integer.getInteger("bbs.editor.camera.mirrorBSLDebugColor",-1)>=0)
            return;
        try{current.deferred();}
        catch(Exception e)
        {
            throw new IllegalStateException(
                    "BSL mirror private deferred pass failed",e);
        }
    }

    /** Native BSL copies depthtex1 BEFORE water/translucent geometry.
     * Copy the private mirror depth at the same boundary. */
    public static void beforeTranslucent()
    {
        if(current==null)return;
        if(current.targets.depthCount()>1)
        {
            current.targets.copyDepth(1);
            current.opaqueDepthSaved=true;
        }
    }

    private int id(Object program)throws Exception
    {
        return program==null?0:((Number)getId.invoke(program)).intValue();
    }

    private void geometryBegin()throws Exception
    {
        targets.bindGeometry();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        // Copy the ACTIVE pack's actual per-attachment clear contract.
        // In OptiFine, a false gbuffersClear[i] deliberately preserves
        // temporal colortex history across frames. Clearing every color
        // attachment to black destroyed BSL's persistent data.
        boolean[] clear=(boolean[])shaders.getField(
                "gbuffersClear").get(null);
        org.lwjgl.util.vector.Vector4f[] values=
                (org.lwjgl.util.vector.Vector4f[])shaders.getField(
                        "gbuffersClearColor").get(null);
        GL11.glDepthMask(true);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<targets.colorCount();i++)
        {
            if(clear!=null&&i<clear.length&&!clear[i])continue;
            org.lwjgl.util.vector.Vector4f value=
                    values!=null&&i<values.length?values[i]:null;
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0+i);
            if(value==null)
                GL11.glClearColor(i==1?1F:0F,
                        i==1?1F:0F,i==1?1F:0F,i==1?1F:0F);
            else GL11.glClearColor(value.x,value.y,value.z,value.w);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        targets.bindGeometry();
    }

    /** Use an OptiFine world program, but NOT Shaders.useProgram(). The
     * latter mutates global dfb activeDrawBuffers and program tracking.
     * GL program and all modified uniforms are explicitly restored. */
    private void geometryStage(String name)
    {
        try
        {
            Object program=shaders.getField(name).get(null);
            int pid=id(program);
            // Most packs provide gbuffers_terrain, but not individual
            // terrain_solid/terrain_cutout shaders. The normal OptiFine
            // pipeline falls back to ProgramTerrain for these stages.
            // Without this fallback, switching to native per-layer names
            // would leave the geometry pass unshaded.
            if(pid<=0&&name.startsWith("ProgramTerrain")&&
                    !"ProgramTerrain".equals(name))
            {
                program=shaders.getField("ProgramTerrain").get(null);
                pid=id(program);
            }
            // Some packs compile only one sky variant. Log the exact
            // compiled program used: a silent ProgramSkyTextured fallback
            // hid the fact that BSL skybasic was never invoked.
            if(pid<=0&&("ProgramSkyTextured".equals(name)||
                    "ProgramSkyBasic".equals(name)))
            {
                String backup="ProgramSkyBasic".equals(name)?
                        "ProgramSkyTextured":"ProgramSkyBasic";
                program=shaders.getField(backup).get(null);
                pid=id(program);
            }
            if(name.startsWith("ProgramSky")&&!skyStageLogged)
            {
                skyStageLogged=true;
                System.out.println(TAG+"Sky program requested="+name+
                        " compiledId="+pid+
                        " (BSL procedural clouds/sky require skybasic)");
            }
            if(pid<=0){GL20.glUseProgram(0);return;}
            targets.bindGeometry();
            GL20.glUseProgram(pid);
            // Sky uses a farther projection than terrain. Capture the
            // terrain camera for deferred/composite matrix uniforms,
            // otherwise a sky projection would distort BSL world depth.
            if(mirrorProjection==null||name.startsWith("ProgramTerrain"))
                mirrorProjection=currentMatrix(GL11.GL_PROJECTION_MATRIX);
            if(mirrorView==null||"ProgramTerrain".equals(name))
                mirrorView=currentMatrix(GL11.GL_MODELVIEW_MATRIX);
            IntBuffer draw=(IntBuffer)getDrawBuffers.invoke(program);
            if(draw!=null)GL20.glDrawBuffers(draw.asReadOnlyBuffer());
            // Raw glUseProgram deliberately bypasses OptiFine's
            // useProgram (which would rewrite the MAIN dfb state). Give
            // the borrowed GLSL program the native sampler unit layout
            // it expects. Skybasic uses these for clouds and atmospheric
            // lighting; terrain needs the lightmap at unit 1.
            samplerUnitOnly(pid,"texture",0);
            samplerUnitOnly(pid,"gtexture",0);
            samplerUnitOnly(pid,"lightmap",1);
            samplerUnitOnly(pid,"normals",2);
            samplerUnitOnly(pid,"specular",3);
            samplerUnitOnly(pid,"noisetex",15);
            samplerUnitOnly(pid,"shadowtex0",4);
            samplerUnitOnly(pid,"shadowtex1",5);
            samplerUnitOnly(pid,"shadowcolor0",13);
            samplerUnitOnly(pid,"shadowcolor1",14);
            bindNativeShadowImages();
            bindNativeNoiseImage();
            GL13.glActiveTexture(oldTextureUnit);
            overrideCamera(pid);
        }
        catch(Throwable e)
        {
            throw new IllegalStateException(
                    "BSL mirror geometry stage "+name,e);
        }
    }

    /**
     * Execute BSL's actual deferred programs at the opaque/translucent
     * boundary. Preserve GL state, texture bindings and shader program
     * before returning to RenderGlobal's water pass.
     */
    private void deferred()throws Exception
    {
        if(deferredExecuted)return;
        if(!opaqueDepthSaved&&targets.depthCount()>1)
        {
            targets.copyDepth(1);
            opaqueDepthSaved=true;
        }
        fullscreen(new DrawAction()
        {
            @Override public void draw()throws Exception
            {
                runStagePrograms("ProgramsDeferred",0);
            }
        },true);
        deferredExecuted=true;
    }

    /** Composite only runs AFTER the reflected opaque/water/actor scene.
     * In particular, deferred has ALREADY run before water. */
    public void compose(final int outputFramebuffer,final int outputWidth,
            final int outputHeight,final boolean tonemapInMain)
            throws Exception
    {
        if(closed)throw new IllegalStateException("BSL scope closed");
        int rawColor=Integer.getInteger(
                "bbs.editor.camera.mirrorBSLDebugColor",-1);
        if(rawColor>=0&&rawColor<targets.colorCount())
        {
            blitRawColor(rawColor,outputFramebuffer,outputWidth,
                    outputHeight);
            return;
        }
        if(!deferredExecuted)deferred();
        if(targets.depthCount()>2)targets.copyDepth(2);
        fullscreen(new DrawAction()
        {
            @Override public void draw()throws Exception
            {
                int offset=countPrograms("ProgramsDeferred");
                int count=runStagePrograms("ProgramsComposite",offset);
                // The outer RenderWorldLast pass feeds the normal BSL
                // deferred/final frame. Do NOT apply BSL final here as well:
                // that tonemaps and dithers the reflected scene twice.
                // Pre-final colortex0 is HDR and must be copied to the
                // mirror's RGBA16F display target, not a clamping RGBA8.
                // A legacy private-final diagnostic remains available.
                if(tonemapInMain||Boolean.getBoolean(
                        "bbs.editor.camera.mirrorBSLSkipFinal"))
                {
                    blitRawColor(0,outputFramebuffer,
                            outputWidth,outputHeight);
                    if(!firstFrameLogged)
                    {
                        firstFrameLogged=true;
                        System.out.println(TAG+"PASS: "+(offset+count)+
                                " shader stages, linear HDR mirror; "+
                                "main BSL frame owns final tonemapping");
                    }
                    return;
                }
                Object finalStage=finalProgram.get(null);
                int pid=id(finalStage);
                if(pid<=0)
                    throw new IllegalStateException("BSL ProgramFinal missing");
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                        outputFramebuffer);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glViewport(0,0,outputWidth,outputHeight);
                GL20.glUseProgram(pid);
                bindInputs(pid);
                overrideCamera(pid);
                drawQuad();
                if(!firstFrameLogged)
                {
                    firstFrameLogged=true;
                    System.out.println(TAG+"PASS: "+
                            (offset+count)+
                            " shader stages + final on private GBuffer"+
                            " (deferred before translucent)");
                }
            }
        },false);
    }

    private int countPrograms(String field)throws Exception
    {
        Object array=shaders.getField(field).get(null);
        if(array==null)return 0;
        int count=0;
        for(int i=0;i<Array.getLength(array);i++)
            if(id(Array.get(array,i))>0)count++;
        return count;
    }

    private int runStagePrograms(String field,int offset)throws Exception
    {
        List<CameraMirrorCompositePlan> plans=
                CameraMirrorOptiFinePlanning.inspect(targets.colorCount());
        Object array=shaders.getField(field).get(null);
        if(array==null)return 0;
        int count=0;
        for(int i=0;i<Array.getLength(array);i++)
        {
            Object program=Array.get(array,i);
            int pid=id(program);
            if(pid<=0)continue;
            int index=offset+count;
            if(index>=plans.size())
                throw new IllegalStateException(
                        "BSL composite plan count mismatch: "+field);
            CameraMirrorCompositePlan plan=plans.get(index);
            targets.bindCompositeOutputs(plan);
            GL20.glUseProgram(pid);
            bindInputs(pid);
            overrideCamera(pid);
            drawQuad();
            targets.commitCompositeOutputs(plan);
            count++;
        }
        return count;
    }

    private interface DrawAction
    {
        void draw()throws Exception;
    }

    /** Prevent the fullscreen shader stages from leaving texture units,
     * matrices, blend/depth state, viewport or a private FBO bound while
     * RenderGlobal resumes rendering translucent blocks. */
    private void fullscreen(DrawAction action,boolean resumeGeometry)
            throws Exception
    {
        final int priorProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int priorMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final int priorRead=GL11.glGetInteger(
                GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int priorDraw=GL11.glGetInteger(
                GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final IntBuffer priorViewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,priorViewport);
        final int[] priorTextures=new int[16];
        try
        {
            for(int i=0;i<16;i++)
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                priorTextures[i]=GL11.glGetInteger(
                        GL11.GL_TEXTURE_BINDING_2D);
            }
        }
        finally{GL13.glActiveTexture(priorUnit);}
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0D,1D,0D,1D,-1D,1D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            action.draw();
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            try
            {
                for(int i=0;i<16;i++)
                {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,priorTextures[i]);
                }
            }
            finally
            {
                GL13.glActiveTexture(priorUnit);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,priorRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,priorDraw);
                GL11.glViewport(priorViewport.get(0),priorViewport.get(1),
                        priorViewport.get(2),priorViewport.get(3));
                GL11.glMatrixMode(priorMode);
                GL20.glUseProgram(priorProgram);
                if(resumeGeometry)targets.bindGeometry();
            }
        }
    }

    private static final int[] COMPOSITE_COLOR_UNITS={
        0,1,2,3,7,8,9,10
    };
    private static final int[] COMPOSITE_DEPTH_UNITS={6,11,12};

    private static boolean rawDebugLogged;
    private static boolean preFinalLogged;

    private void blitRawColor(int color,int destination,int width,int height)
    {
        // This draws exactly what the reflected world wrote into one
        // PRIVATE colortex. If this image is wrong, repairing composite
        // uniforms alone cannot fix the reflection.
        targets.bindGeometry();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                targets.framebuffer());
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0+color);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,destination);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBlitFramebuffer(0,0,targets.width(),targets.height(),
                0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
        boolean afterComposite=Boolean.getBoolean(
                "bbs.editor.camera.mirrorBSLSkipFinal");
        if(afterComposite)
        {
            if(!preFinalLogged)
            {
                preFinalLogged=true;
                System.out.println(TAG+
                        "DEBUG: post-composite colortex0 BEFORE BSL final "+
                        "(diagnose duplicate tonemapping)");
            }
        }
        else if(!rawDebugLogged)
        {
            rawDebugLogged=true;
            System.out.println(TAG+"DEBUG: raw mirrored colortex"+color+
                    " blitted without deferred/composite/final");
        }
    }

    private void bindInputs(int program)
    {
        // OptiFine 1.12.2 assigns sampler slots inside Shaders.useProgram.
        // We intentionally do NOT call that method (it mutates main
        // dfb/FlipTextures), so any program not yet visited this frame
        // may have ALL samplers defaulting to unit 0. Reading existing
        // sampler uniform values was the cause of the bright/wall-colored
        // mirror: colortex and depthtex were bound over one another on
        // GL_TEXTURE0. Match OptiFine's documented fixed slot layout.
        for(int i=0;i<targets.colorCount();i++)
            assignSampler(program,"colortex"+i,
                    COMPOSITE_COLOR_UNITS[i],targets.color(i));
        for(int i=0;i<targets.depthCount();i++)
            assignSampler(program,"depthtex"+i,
                    COMPOSITE_DEPTH_UNITS[i],targets.depth(i));
        assignSampler(program,"gcolor",0,targets.color(0));
        if(targets.colorCount()>1)
            assignSampler(program,"gdepth",1,targets.color(1));
        if(targets.colorCount()>2)
            assignSampler(program,"gnormal",2,targets.color(2));
        if(targets.colorCount()>3)
            assignSampler(program,"composite",3,targets.color(3));
        for(int i=4;i<targets.colorCount();i++)
            assignSampler(program,"gaux"+(i-3),
                    COMPOSITE_COLOR_UNITS[i],targets.color(i));
        if(targets.depthCount()>0)
            assignSampler(program,"gdepthtex",6,targets.depth(0));

        // Shadow/noise images already belong to the active main OptiFine
        // shader frame. They are read-only here; NEVER replace their
        // bindings with mirror textures or alter global shadow FBOs.
        int shadowUnit=4;
        try
        {
            Field waterShadow=shaders.getDeclaredField("waterShadowEnabled");
            waterShadow.setAccessible(true);
            if(waterShadow.getBoolean(null))shadowUnit=5;
        }
        catch(ReflectiveOperationException ignore){}
        samplerUnitOnly(program,"shadow",shadowUnit);
        samplerUnitOnly(program,"watershadow",4);
        samplerUnitOnly(program,"shadowtex0",4);
        samplerUnitOnly(program,"shadowtex1",5);
        samplerUnitOnly(program,"shadowcolor",13);
        samplerUnitOnly(program,"shadowcolor0",13);
        samplerUnitOnly(program,"shadowcolor1",14);
        samplerUnitOnly(program,"noisetex",15);
        // The ordinary OptiFine frame may have rebound texture units for
        // terrain/lightmap after its last shadow pass. The auxiliary
        // compositor must bind the native shadow maps as INPUTS instead
        // of assuming texture units 4/5/13/14 still contain them.
        // Only texture bindings are changed; global sfb/shadow render
        // targets and their contents remain completely untouched.
        bindNativeShadowImages();
        // BSL uses noisetex for screen-space dithering and spatial
        // sampling. At RenderWorldLast GL_TEXTURE15 is NOT guaranteed to
        // remain bound to OptiFine's noise image. Always bind the actual
        // pack resource rather than sampling an unrelated stale texture.
        bindNativeNoiseImage();
        GL13.glActiveTexture(oldTextureUnit);
    }

    private static boolean noiseDiagnosticLogged;

    private void bindNativeNoiseImage()
    {
        try
        {
            Field enabled=nativeField("noiseTextureEnabled");
            Field nativeNoise=nativeField("noiseTexture");
            if(nativeNoise==null||enabled==null||
                    !enabled.getBoolean(null))return;
            Object resource=nativeNoise.get(null);
            if(resource==null)return;
            // ICustomTexture is optional and OptiFine's concrete texture
            // classes vary; never make it a Forge compile dependency.
            Method getTextureId=resource.getClass().getMethod(
                    "getTextureId");
            int id=((Number)getTextureId.invoke(resource)).intValue();
            if(id>0)
            {
                bindReadOnlyTexture(15,id);
                if(!noiseDiagnosticLogged)
                {
                    noiseDiagnosticLogged=true;
                    System.out.println(TAG+
                            "Bound native BSL noisetex on unit 15");
                }
            }
        }
        catch(ReflectiveOperationException error)
        {
            if(!noiseDiagnosticLogged)
            {
                noiseDiagnosticLogged=true;
                System.err.println(TAG+
                        "OptiFine noisetex unavailable: "+error);
            }
        }
    }

    private void bindNativeShadowImages()
    {
        try
        {
            Field depths=nativeField("sfbDepthTextures");
            if(depths!=null)
            {
                IntBuffer images=(IntBuffer)depths.get(null);
                if(images!=null)
                {
                    if(images.limit()>0)bindReadOnlyTexture(4,images.get(0));
                    if(images.limit()>1)bindReadOnlyTexture(5,images.get(1));
                }
            }
            Field colors=nativeField("sfbColorTextures");
            if(colors!=null)
            {
                IntBuffer images=(IntBuffer)colors.get(null);
                if(images!=null)
                {
                    if(images.limit()>0)bindReadOnlyTexture(13,images.get(0));
                    if(images.limit()>1)bindReadOnlyTexture(14,images.get(1));
                }
            }
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot bind read-only OptiFine shadow samplers",e);
        }
    }

    private void bindReadOnlyTexture(int unit,int texture)
    {
        if(texture<=0)return;
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
    }

    private void samplerUnitOnly(int program,String name,int unit)
    {
        int loc=GL20.glGetUniformLocation(program,name);
        if(loc<0)return;
        restored.add(UniformBackup.captureInt(program,loc));
        GL20.glUniform1i(loc,unit);
    }

    private void assignSampler(int program,String name,int unit,int texture)
    {
        int loc=GL20.glGetUniformLocation(program,name);
        if(loc<0)return;
        if(unit<0||unit>=16)
            throw new IllegalStateException("Invalid mirror sampler "+
                    name+" unit="+unit);
        restored.add(UniformBackup.captureInt(program,loc));
        GL20.glUniform1i(loc,unit);
        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
    }

    private void overrideCamera(int pid)
    {
        write3(pid,"cameraPosition",(float)view.x,
                (float)view.y,(float)view.z);
        // BSL temporal reprojection must not use the MAIN camera's
        // previous position while the mirror uses a reflected camera.
        // Until mirror-only history is implemented, initialize the
        // temporal matrices/position to the current reflected view.
        write3(pid,"previousCameraPosition",(float)view.x,
                (float)view.y,(float)view.z);
        write1(pid,"eyeAltitude",(float)view.y);
        write1(pid,"near",Math.max(0.02F,view.nearPlane));
        write1(pid,"far",Math.max(64F,view.farPlane));
        write1(pid,"viewWidth",(float)targets.width());
        write1(pid,"viewHeight",(float)targets.height());
        write1(pid,"aspectRatio",(float)targets.width()/
                Math.max(1,targets.height()));
        // GLSL programs are selected via raw glUseProgram to avoid
        // modifying the main OptiFine program stack. Thus the normal
        // Shaders.useProgram() environment-uniform initialization is
        // NOT executed for this camera. Copy the current WORLD time,
        // weather, sky and fog values from the active native renderer.
        overrideEnvironment(pid);
        if(mirrorProjection!=null)
        {
            writeMatrix(pid,"gbufferProjection",mirrorProjection,false);
            writeMatrix(pid,"gbufferProjectionInverse",mirrorProjection,true);
            writeMatrix(pid,"gbufferPreviousProjection",mirrorProjection,false);
        }
        if(mirrorView!=null)
        {
            writeMatrix(pid,"gbufferModelView",mirrorView,false);
            writeMatrix(pid,"gbufferModelViewInverse",mirrorView,true);
            writeMatrix(pid,"gbufferPreviousModelView",mirrorView,false);
        }
    }

    /** Copy non-view-dependent OptiFine environment state into each
     * compiled BSL program, restoring all uniforms at scope exit.
     * Shadow/sun direction require reflected-view transforms and are
     * handled separately from these world-space scalars. */
    private void overrideEnvironment(int pid)
    {
        copyFloat(pid,"fogDensity","fogDensity");
        copyInt(pid,"fogMode","fogMode");
        copy3(pid,"fogColor","fogColorR","fogColorG","fogColorB");
        copy3(pid,"skyColor","skyColorR","skyColorG","skyColorB");
        copyFloat(pid,"sunAngle","sunAngle");
        copyFloat(pid,"shadowAngle","shadowAngle");
        copyFloat(pid,"rainStrength","rainStrength");
        copyFloat(pid,"wetness","wetness");
        copyFloat(pid,"frameTime","frameTime");
        copyFloat(pid,"frameTimeCounter","frameTimeCounter");
        copyInt(pid,"frameCounter","frameCounter");
        copyInt(pid,"moonPhase","moonPhase");
        copyInt(pid,"isEyeInWater","isEyeInWater");
        // Skybasic/clouds depend on day cycle and temporal wind motion.
        // The normal pack programs may have not been activated again
        // since Shader.useProgram last uploaded these uniforms.
        copyNumberInt(pid,"worldTime","worldTime");
        copyNumberInt(pid,"worldDay","worldDay");
        copyFloat(pid,"nightVision","nightVision");
        copyFloat(pid,"blindness","blindness");
        // OptiFine stores the world sun/moon/shadow light in the MAIN
        // camera's view coordinates. A reflected camera must rotate those
        // vectors into its own view-space, not blindly reuse main-camera
        // values. Ignore translations: these are directional vectors.
        copyReflectedDirection(pid,"sunPosition","sunPosition");
        copyReflectedDirection(pid,"moonPosition","moonPosition");
        copyReflectedDirection(pid,"upPosition","upPosition");
        copyReflectedDirection(pid,"shadowLightPosition",
                "shadowLightPosition");
    }

    private void copyReflectedDirection(int pid,String uniform,
            String sourceName)
    {
        if(mirrorView==null)return;
        try
        {
            Field sourceField=nativeField(sourceName);
            Field mainField=nativeField("faModelView");
            if(sourceField==null||mainField==null)return;
            float[] source=(float[])sourceField.get(null);
            float[] main=(float[])mainField.get(null);
            if(source==null||source.length<3||
                    main==null||main.length<16)return;
            FloatBuffer buf=BufferUtils.createFloatBuffer(16);
            buf.put(main,0,16);
            buf.flip();
            Matrix4f from=new Matrix4f();
            from.load(buf);
            Matrix4f inverse=Matrix4f.invert(from,null);
            if(inverse==null)return;
            Vector4f world=Matrix4f.transform(inverse,
                    new Vector4f(source[0],source[1],source[2],0F),null);
            mirrorView.rewind();
            Matrix4f reflected=new Matrix4f();
            reflected.load(mirrorView);
            mirrorView.rewind();
            Vector4f result=Matrix4f.transform(reflected,world,null);
            write3(pid,uniform,result.x,result.y,result.z);
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot transform BSL light direction "+uniform,e);
        }
    }

    private Field nativeField(String name)
    {
        try
        {
            Field field=shaders.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
        catch(ReflectiveOperationException unavailable){return null;}
    }

    private void copyFloat(int pid,String uniform,String field)
    {
        try
        {
            Field source=nativeField(field);
            if(source!=null)
                write1(pid,uniform,source.getFloat(null));
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot read OptiFine environment "+field,e);
        }
    }

    /** OptiFine builds differ on worldTime's Java field type (int/long).
     * Read Number without assuming Field.getInt will always succeed. */
    private void copyNumberInt(int pid,String uniform,String field)
    {
        try
        {
            Field source=nativeField(field);
            if(source==null)return;
            Object value=source.get(null);
            if(!(value instanceof Number))return;
            int loc=GL20.glGetUniformLocation(pid,uniform);
            if(loc<0)return;
            restored.add(UniformBackup.captureInt(pid,loc));
            GL20.glUniform1i(loc,((Number)value).intValue());
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot read BSL time uniform "+field,e);
        }
    }

    private void copyInt(int pid,String uniform,String field)
    {
        try
        {
            Field source=nativeField(field);
            if(source==null)return;
            int loc=GL20.glGetUniformLocation(pid,uniform);
            if(loc<0)return;
            restored.add(UniformBackup.captureInt(pid,loc));
            GL20.glUniform1i(loc,source.getInt(null));
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot read OptiFine environment "+field,e);
        }
    }

    private void copy3(int pid,String uniform,String r,String g,String b)
    {
        try
        {
            Field rf=nativeField(r);
            Field gf=nativeField(g);
            Field bf=nativeField(b);
            if(rf==null||gf==null||bf==null)return;
            write3(pid,uniform,rf.getFloat(null),gf.getFloat(null),
                    bf.getFloat(null));
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException(
                    "Cannot read OptiFine environment "+uniform,e);
        }
    }

    private static FloatBuffer currentMatrix(int target)
    {
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(target,b);
        b.rewind();
        return b;
    }

    private void writeMatrix(int pid,String name,FloatBuffer source,
            boolean inverse)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return;
        restored.add(UniformBackup.capture(pid,loc,16));
        FloatBuffer value=BufferUtils.createFloatBuffer(16);
        source.rewind();
        if(inverse)
        {
            Matrix4f matrix=new Matrix4f();
            matrix.load(source);
            Matrix4f inv=Matrix4f.invert(matrix,null);
            if(inv==null)return;
            inv.store(value);
        }
        else value.put(source);
        value.flip();
        source.rewind();
        GL20.glUniformMatrix4(loc,false,value);
    }

    private void write3(int pid,String name,float x,float y,float z)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return;
        restored.add(UniformBackup.capture(pid,loc,3));
        GL20.glUniform3f(loc,x,y,z);
    }
    private void write1(int pid,String name,float value)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return;
        restored.add(UniformBackup.capture(pid,loc,1));
        GL20.glUniform1f(loc,value);
    }

    private static void drawQuad()
    {
        // glTexCoord2f updates the currently active fixed-function texture
        // coordinate set. OptiFine composite GLSL reads gl_TexCoord[0].
        // The caller may have left the lightmap or shadow unit active.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glColor4f(1F,1F,1F,1F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0F,0F);
        GL11.glVertex2f(0F,0F);
        GL11.glTexCoord2f(1F,0F);
        GL11.glVertex2f(1F,0F);
        GL11.glTexCoord2f(1F,1F);
        GL11.glVertex2f(1F,1F);
        GL11.glTexCoord2f(0F,1F);
        GL11.glVertex2f(0F,1F);
        GL11.glEnd();
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        current=null;
        Throwable failure=null;
        try
        {
            for(int i=restored.size()-1;i>=0;i--)
                restored.get(i).restore();
        }
        catch(Throwable t){failure=t;}
        finally
        {
            // Perform OpenGL restoration even if reflection of one
            // OptiFine Java state field fails. The original shader frame
            // must not be left bound to our private FBO.
            try
            {
                for(int i=0;i<safetyFlags.length;i++)
                    safetyFlags[i].setBoolean(null,originalFlags[i]);
                activeProgramField.set(null,originalOptiFineProgram);
                activeProgramIdField.setInt(null,originalOptiFineProgramId);
                activeDrawBuffersField.set(null,originalOptiFineDrawBuffers);
            }
            catch(Throwable t)
            {
                if(failure==null)failure=t;else failure.addSuppressed(t);
            }
            try
            {
                for(int i=0;i<16;i++)
                {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTextures[i]);
                }
            }
            catch(Throwable t)
            {
                if(failure==null)failure=t;else failure.addSuppressed(t);
            }
            finally
            {
                try
                {
                    GL13.glActiveTexture(oldTextureUnit);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
                    GL11.glViewport(oldViewport[0],oldViewport[1],
                            oldViewport[2],oldViewport[3]);
                    GL20.glUseProgram(mainProgram);
                }
                catch(Throwable t)
                {
                    if(failure==null)failure=t;
                    else failure.addSuppressed(t);
                }
            }
        }
        if(failure!=null)
            throw new IllegalStateException(
                    "BSL mirror state restoration failed",failure);
    }

    private static final class UniformBackup
    {
        final int program,location,count;
        final FloatBuffer value;
        final int integerValue;
        UniformBackup(int program,int location,int count,FloatBuffer value,
                int integerValue)
        {
            this.program=program;this.location=location;
            this.count=count;this.value=value;
            this.integerValue=integerValue;
        }
        static UniformBackup capture(int program,int location,int count)
        {
            // LWJGL 2 glGetUniformf requires 16 float slots even when
            // the uniform contains just one or three components.
            FloatBuffer b=BufferUtils.createFloatBuffer(16);
            GL20.glGetUniform(program,location,b);
            b.rewind();
            return new UniformBackup(program,location,count,b,0);
        }
        static UniformBackup captureInt(int program,int location)
        {
            IntBuffer b=BufferUtils.createIntBuffer(16);
            GL20.glGetUniform(program,location,b);
            return new UniformBackup(program,location,0,null,b.get(0));
        }
        void restore()
        {
            GL20.glUseProgram(program);
            if(count==0)GL20.glUniform1i(location,integerValue);
            else if(count==1)GL20.glUniform1f(location,value.get(0));
            else if(count==16)
            {
                value.rewind();
                GL20.glUniformMatrix4(location,false,value);
            }
            else GL20.glUniform3f(location,value.get(0),
                    value.get(1),value.get(2));
        }
    }
}
