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

    private void geometryBegin()
    {
        targets.bindGeometry();
        GL11.glClearColor(0F,0F,0F,0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
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
            if(pid<=0){GL20.glUseProgram(0);return;}
            targets.bindGeometry();
            GL20.glUseProgram(pid);
            // Sky uses a farther projection than terrain. Capture the
            // terrain camera for deferred/composite matrix uniforms,
            // otherwise a sky projection would distort BSL world depth.
            if(mirrorProjection==null||"ProgramTerrain".equals(name))
                mirrorProjection=currentMatrix(GL11.GL_PROJECTION_MATRIX);
            if(mirrorView==null||"ProgramTerrain".equals(name))
                mirrorView=currentMatrix(GL11.GL_MODELVIEW_MATRIX);
            IntBuffer draw=(IntBuffer)getDrawBuffers.invoke(program);
            if(draw!=null)GL20.glDrawBuffers(draw.asReadOnlyBuffer());
            overrideCamera(pid);
        }
        catch(Throwable e)
        {
            throw new IllegalStateException(
                    "BSL mirror geometry stage "+name,e);
        }
    }

    /**
     * Runs each active deferred/composite shader against the private
     * mirrored GBuffer, followed by the user's actual BSL final shader
     * into the existing mirror texture. No real world or shadow pass
     * is started a second time.
     */
    public void compose(int outputFramebuffer,int outputWidth,
            int outputHeight)throws Exception
    {
        if(closed)throw new IllegalStateException("BSL scope closed");
        // The opaque snapshot is captured just before translucent terrain.
        // depthtex2 is captured after all prepared actors (same scene).
        // The native character pass still runs after translucent terrain,
        // so exact per-entity depth ordering needs further parity work.
        if(!opaqueDepthSaved&&targets.depthCount()>1)
            targets.copyDepth(1);
        if(targets.depthCount()>2)targets.copyDepth(2);
        List<CameraMirrorCompositePlan> plans=
                CameraMirrorOptiFinePlanning.inspect(targets.colorCount());
        Class<?> clazz=Class.forName("net.optifine.shaders.Program");
        Method getName=clazz.getMethod("getName");
        List<Object> programs=new ArrayList<Object>();
        Object[] categories={
            shaders.getField("ProgramsDeferred").get(null),
            shaders.getField("ProgramsComposite").get(null)
        };
        for(Object array:categories)
        {
            if(array==null)continue;
            for(int i=0;i<Array.getLength(array);i++)
            {
                Object program=Array.get(array,i);
                if(id(program)>0)programs.add(program);
            }
        }
        if(programs.size()!=plans.size())
            throw new IllegalStateException("BSL program/plan count mismatch");
        // The fullscreen pass owns its own ortho matrices.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0D,1D,0D,1D,-1D,1D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        try
        {
            for(int i=0;i<programs.size();i++)
            {
                Object program=programs.get(i);
                CameraMirrorCompositePlan plan=plans.get(i);
                targets.bindCompositeOutputs(plan);
                int pid=id(program);
                GL20.glUseProgram(pid);
                bindInputs(pid);
                overrideCamera(pid);
                drawQuad();
                targets.commitCompositeOutputs(plan);
            }
            Object finalStage=finalProgram.get(null);
            int pid=id(finalStage);
            if(pid<=0)
                throw new IllegalStateException("BSL ProgramFinal missing");
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,outputFramebuffer);
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
                        programs.size()+
                        " shader stages + final on private GBuffer");
            }
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static final int[] COMPOSITE_COLOR_UNITS={
        0,1,2,3,7,8,9,10
    };
    private static final int[] COMPOSITE_DEPTH_UNITS={6,11,12};

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
        samplerUnitOnly(program,"shadow",4);
        samplerUnitOnly(program,"watershadow",4);
        samplerUnitOnly(program,"shadowtex0",4);
        samplerUnitOnly(program,"shadowtex1",5);
        samplerUnitOnly(program,"shadowcolor",13);
        samplerUnitOnly(program,"shadowcolor0",13);
        samplerUnitOnly(program,"shadowcolor1",14);
        samplerUnitOnly(program,"noisetex",15);
        GL13.glActiveTexture(oldTextureUnit);
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
        write1(pid,"viewWidth",(float)targets.width());
        write1(pid,"viewHeight",(float)targets.height());
        write1(pid,"aspectRatio",(float)targets.width()/
                Math.max(1,targets.height()));
        if(mirrorProjection!=null)
        {
            writeMatrix(pid,"gbufferProjection",mirrorProjection,false);
            writeMatrix(pid,"gbufferProjectionInverse",mirrorProjection,true);
        }
        if(mirrorView!=null)
        {
            writeMatrix(pid,"gbufferModelView",mirrorView,false);
            writeMatrix(pid,"gbufferModelViewInverse",mirrorView,true);
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
