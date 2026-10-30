package com.example.examplemod;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;

/**
 * Experimental OptiFine G5 *forward-only* reflection bridge.
 *
 * A shaderpack is eligible only when it uses ONE color output and does not
 * install deferred/composite/final programs. That restrictive contract is
 * deliberate: multipass shaderpacks require their own GBuffer ping-pong and
 * composite graph and MUST NEVER write the normal world's buffers.
 *
 * Uses existing active OptiFine shader programs, shadow textures and prepared
 * scene state with a private mirror FBO. No extra world, render tick, Jolt
 * simulation, shadow render, or OptiFine beginRender/endRender call.
 *
 * Native forward shaderpack programs are borrowed through Shaders.useProgram,
 * not glUseProgram, to keep OptiFine's cached shader state synchronized.
 * The scope restores the original active program even after exceptions.
 *
 * This is NOT full G5 deferred shaderpack parity; see camera-mirrors.md.
 */
public final class CameraMirrorOptiFineBackend
{
    public static final class Support
    {
        public final boolean usable;
        public final String message;
        private Support(boolean usable,String message)
        {
            this.usable=usable;this.message=message;
        }
    }
    private static Class<?> shaders;
    private static Field worldFlag,shadowFlag,activeProgram;
    private static Field colorCount,depthCount,shaderName,colorFormats;
    private static Field[] stages;
    private static Field compositePrograms,deferredPrograms,finalProgram;
    private static Method useProgram,getId;
    private static boolean lookedUp;
    private static boolean reported;
    private static String lastAssessment="";
    private static Scope current;
    private static final String[] STAGES={
        "ProgramSkyBasic","ProgramSkyTextured","ProgramClouds",
        "ProgramTerrain","ProgramWater","ProgramEntities"
    };
    private CameraMirrorOptiFineBackend(){}

    public static Support inspect()
    {
        try
        {
            if(!lookedUp)resolve();
            if(shaders==null)
                return new Support(false,"OptiFine program API missing");
            String pack=shaderName==null?"Unknown":
                    String.valueOf(shaderName.get(null));
            if(!worldFlag.getBoolean(null))
                return report(new Support(false,
                        pack+": OptiFine frame not active at WorldLast"));
            if(shadowFlag.getBoolean(null))
                return report(new Support(false,
                        pack+": OptiFine shadow pass active"));
            int color=colorCount.getInt(null);
            int depth=depthCount.getInt(null);
            int deferred=countPrograms(deferredPrograms.get(null));
            int composite=countPrograms(compositePrograms.get(null));
            int finalId=programId(finalProgram.get(null));
            int[] formats=getColorFormats(color);
            String details="pack="+pack+" colors="+color+" depth="+depth+
                    " deferred="+deferred+" composite="+composite+
                    " final="+(finalId>0)+
                    " formats="+Arrays.toString(formats);
            if(color!=1||depth!=1||deferred>0||composite>0||
                    finalId>0)
            {
                return report(new Support(false,
                        "Multipass shaderpack: "+details));
            }
            return report(new Support(true,
                    "Forward shaderpack: "+details));
        }
        catch(Throwable t)
        {
            if(!reported)
            {
                reported=true;
                System.err.println("[BBS Editor][Mirror][Shader] "+
                        "OptiFine G5 inspection failed: "+t);
            }
            return new Support(false,
                    "OptiFine API incompatible: "+
                    t.getClass().getSimpleName());
        }
    }

    private static Support report(Support support)
    {
        if(!support.message.equals(lastAssessment))
        {
            lastAssessment=support.message;
            System.out.println("[BBS Editor][Mirror][Shader] "+
                    support.message);
        }
        return support;
    }

    public static final class Scope implements AutoCloseable
    {
        private final Object previous;
        private final PreviewCameraPose pose;
        private final int framebuffer;
        private final List<SavedUniform> uniforms=new ArrayList<SavedUniform>();
        private boolean closed;
        private Scope(Object previous,PreviewCameraPose pose,int framebuffer)
        {
            this.previous=previous;this.pose=pose;
            this.framebuffer=framebuffer;
            // GL draw-buffer state belongs to the parent reflection GL
            // attrib scope, not to OptiFine's global frame.
        }

        public void stage(String name)
        {
            if(closed)return;
            int which=-1;
            for(int i=0;i<STAGES.length;i++)
                if(STAGES[i].equals(name)){which=i;break;}
            if(which<0)return;
            try
            {
                Object program=stages[which].get(null);
                if(program==null)return;
                useProgram.invoke(null,program);
                // useProgram can change OptiFine's drawBuffers. A forward
                // mirror has exactly one color target.
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                int id=programId(program);
                if(id>0)uploadViewUniforms(id,pose,uniforms);
            }
            catch(Throwable e)
            {
                throw new IllegalStateException(
                        "OptiFine mirror shader stage "+name+" failed",e);
            }
        }

        @Override public void close()
        {
            if(closed)return;
            closed=true;
            current=null;
            Throwable failure=null;
            try
            {
                // The main OptiFine final/composite pass will run AFTER
                // RenderWorldLast. It must see the original camera's
                // program uniforms, not the mirror's camera/projection.
                for(int i=uniforms.size()-1;i>=0;i--)
                    uniforms.get(i).restore();
            }
            catch(Throwable t){failure=t;}
            finally
            {
                try
                {
                    if(previous!=null)useProgram.invoke(null,previous);
                }
                catch(Throwable t)
                {
                    if(failure==null)failure=t;
                    else failure.addSuppressed(t);
                }
            }
            if(failure!=null)
                throw new IllegalStateException(
                        "Unable to restore OptiFine mirror shader state",
                        failure);
        }
    }

    public static Scope begin(PreviewCameraPose pose,int framebuffer)
    {
        Support support=inspect();
        if(!support.usable)
            throw new IllegalStateException(support.message);
        if(current!=null)
            throw new IllegalStateException("Recursive shader reflection");
        try
        {
            Scope scope=new Scope(activeProgram.get(null),pose,framebuffer);
            current=scope;
            return scope;
        }
        catch(Throwable e)
        {
            throw new IllegalStateException("OptiFine reflection init",e);
        }
    }

    public static void stage(String name)
    {
        // OptiFine multipass geometry uses compiled GLSL programs, but
        // writes to our private eight-color GBuffer instead of Shaders.dfb.
        // The forward-only backend remains unchanged.
        if(CameraMirrorBSLCompositor.active())
        {
            CameraMirrorBSLCompositor.stage(name);
            return;
        }
        Scope scope=current;
        if(scope!=null)scope.stage(name);
    }
    public static boolean active(){return current!=null;}

    /**
     * Exact attachment formats used by the ACTIVE OptiFine shaderpack.
     * Returning a copy avoids giving the mirror any write access to
     * Shaders.gbuffersFormat or the normal BBS FS framebuffer.
     */
    public static int[] getColorFormats(int count)throws Exception
    {
        if(!lookedUp)resolve();
        if(colorFormats==null)
            throw new IllegalStateException(
                    "OptiFine gbuffersFormat unavailable");
        int[] live=(int[])colorFormats.get(null);
        if(live==null||count>live.length)
            throw new IllegalStateException(
                    "OptiFine GBuffer layout not initialized");
        return Arrays.copyOf(live,count);
    }

    private static int countPrograms(Object array)throws Exception
    {
        int count=0;
        if(array!=null)
            for(int i=0;i<Array.getLength(array);i++)
                if(programId(Array.get(array,i))>0)count++;
        return count;
    }
    private static int programId(Object p)throws Exception
    {
        return p==null?0:((Number)getId.invoke(p)).intValue();
    }

    private static void resolve()throws Exception
    {
        lookedUp=true;
        shaders=Class.forName("net.optifine.shaders.Shaders");
        worldFlag=shaders.getField("isRenderingWorld");
        shadowFlag=shaders.getField("isShadowPass");
        activeProgram=shaders.getField("activeProgram");
        Class<?> type=activeProgram.getType();
        getId=type.getMethod("getId");
        useProgram=shaders.getMethod("useProgram",type);
        colorCount=hidden("usedColorBuffers");
        depthCount=hidden("usedDepthBuffers");
        colorFormats=hidden("gbuffersFormat");
        try{shaderName=shaders.getField("currentShaderName");}
        catch(NoSuchFieldException ignored){shaderName=null;}
        compositePrograms=shaders.getField("ProgramsComposite");
        deferredPrograms=shaders.getField("ProgramsDeferred");
        finalProgram=shaders.getField("ProgramFinal");
        stages=new Field[STAGES.length];
        for(int i=0;i<STAGES.length;i++)
            stages[i]=shaders.getField(STAGES[i]);
    }
    private static Field hidden(String name)throws Exception
    {
        Field f=shaders.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Snapshot GL uniforms before the mirror adjusts them. The original
     * main-camera values are needed by OptiFine's final composite pass. */
    private static final class SavedUniform
    {
        final int program,location;
        final boolean matrix;
        final FloatBuffer value;
        private SavedUniform(int program,int location,boolean matrix,
                FloatBuffer value)
        {
            this.program=program;this.location=location;
            this.matrix=matrix;this.value=value;
        }
        static SavedUniform capture(int program,int location,
                boolean matrix)
        {
            FloatBuffer val=BufferUtils.createFloatBuffer(matrix?16:4);
            GL20.glGetUniform(program,location,val);
            val.rewind();
            return new SavedUniform(program,location,matrix,val);
        }
        void restore()
        {
            GL20.glUseProgram(program);
            value.rewind();
            if(matrix)GL20.glUniformMatrix4(location,false,value);
            else GL20.glUniform3(location,value);
        }
    }

    private static void uploadViewUniforms(int program,
            PreviewCameraPose pose,List<SavedUniform> previous)
    {
        // Mirror camera position must not inherit the ordinary camera's
        // uniform. Current GL projection/modelview is set by the mirror
        // terrain pass before each stage.
        int loc=GL20.glGetUniformLocation(program,"cameraPosition");
        if(loc>=0)
        {
            previous.add(SavedUniform.capture(program,loc,false));
            GL20.glUniform3f(loc,(float)pose.x,
                    (float)pose.y,(float)pose.z);
        }
        uploadMatrix(program,"gbufferProjection",
                GL11.GL_PROJECTION_MATRIX,false,previous);
        uploadMatrix(program,"gbufferProjectionInverse",
                GL11.GL_PROJECTION_MATRIX,true,previous);
        uploadMatrix(program,"gbufferModelView",
                GL11.GL_MODELVIEW_MATRIX,false,previous);
        uploadMatrix(program,"gbufferModelViewInverse",
                GL11.GL_MODELVIEW_MATRIX,true,previous);
    }
    private static void uploadMatrix(int program,String uniform,
            int matrixType,boolean invert,List<SavedUniform> previous)
    {
        int loc=GL20.glGetUniformLocation(program,uniform);
        if(loc<0)return;
        previous.add(SavedUniform.capture(program,loc,true));
        FloatBuffer buf=BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(matrixType,buf);
        buf.rewind();
        if(invert)
        {
            Matrix4f mat=new Matrix4f();
            mat.load(buf);
            Matrix4f inverse=Matrix4f.invert(mat,null);
            if(inverse==null)return;
            buf.clear();
            inverse.store(buf);
            buf.flip();
        }
        GL20.glUniformMatrix4(loc,false,buf);
    }
}
