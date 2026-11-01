package com.example.examplemod;

import java.lang.reflect.Method;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * BSL-compatible exact mirror clipping.
 *
 * The ordinary BSL terrain shader does not write gl_ClipVertex and
 * OpenGL's fixed GL_CLIP_PLANE0 therefore has no effect. An oblique
 * projection clips correctly but BSL's sparse inverse-projection
 * reconstruction produces camera-moving lighting/shadows.
 *
 * Clone (NEVER relink/mutate) the existing compiled OptiFine program,
 * attach the original fragment shader and a private vertex shader whose
 * main() writes gl_ClipVertex in eye coordinates. Existing GL_CLIP_PLANE0
 * then rejects fragments *at the real mirror plane*, while the symmetric
 * camera projection and the BSL shadow-space math remain unchanged.
 *
 * Each clone owns its uniform state; values must be copied from the
 * native compiled program immediately before every borrowed draw.
 * Failed compiler/GL introspection falls back to the original program.
 */
public final class CameraMirrorShaderClip
{
    private static final String TAG="[BBS Editor][Mirror][GLSL Clip] ";
    private static final Map<Integer,Integer> COPIES=
            new HashMap<Integer,Integer>();
    private static final Map<Integer,Boolean> UNSUPPORTED=
            new HashMap<Integer,Boolean>();
    private static boolean once,warning;

    private CameraMirrorShaderClip(){}

    public static void release()
    {
        for(int id:COPIES.values())if(id>0)GL20.glDeleteProgram(id);
        COPIES.clear();
        UNSUPPORTED.clear();
        once=false;
        warning=false;
    }

    public static int program(int original,Object optifineProgram)
    {
        if(original<=0||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLShaderPlaneClip",
                        "true")))return original;
        Integer cloned=COPIES.get(original);
        if(cloned!=null)return cloned.intValue();
        if(UNSUPPORTED.containsKey(original))return original;
        try
        {
            int id=cloneShader(original,optifineProgram);
            COPIES.put(original,id);
            if(!once)
            {
                once=true;
                System.out.println(TAG+"Cloned BSL terrain vertex program "+
                        original+" -> "+id+
                        " with gl_ClipVertex; symmetric BSL projection "+
                        "and native terrain shaders preserved");
            }
            return id;
        }
        catch(Throwable failure)
        {
            UNSUPPORTED.put(original,Boolean.TRUE);
            if(!warning)
            {
                warning=true;
                System.err.println(TAG+"Shader clip unavailable; "+
                        "using unmodified terrain: "+failure);
            }
            return original;
        }
    }

    private static int cloneShader(int original,Object optifineProgram)
            throws Exception
    {
        Class<?> shaderClass=Class.forName("net.optifine.shaders.Shaders");
        Class<?> programClass=Class.forName("net.optifine.shaders.Program");
        String programName=(String)programClass.getMethod("getRealProgramName")
                .invoke(optifineProgram);
        if(programName==null||programName.isEmpty()||
                "none".equals(programName)||
                "unknown".equals(programName))
            throw new IllegalStateException("Cannot resolve native shaderpack program");
        // OptiFine 1.12.2 DETACHES and DELETES native stage shader objects
        // immediately after linking. glGetAttachedShaders() therefore
        // returns zero even for fully working programs. Invoke its PRIVATE
        // shader compilation pipeline instead: this processes includes,
        // options and any BetterLights coremod source transformations
        // exactly as the original native program did.
        Object shaderPack=shaderClass.getMethod("getShaderPack")
                .invoke(null);
        int dimension=Minecraft.getMinecraft().world==null?0:
                Minecraft.getMinecraft().world.provider.getDimension();
        String world="/shaders/world"+dimension+"/"+programName;
        String plain="/shaders/"+programName;
        String base=hasSource(shaderPack,world+".vsh")?world:plain;
        if(!hasSource(shaderPack,base+".vsh")||
                !hasSource(shaderPack,base+".fsh"))
            throw new IllegalStateException("No shaderpack stages at "+base);
        Method vert=shaderClass.getDeclaredMethod("createVertShader",
                programClass,String.class);
        Method frag=shaderClass.getDeclaredMethod("createFragShader",
                programClass,String.class);
        Method geom=shaderClass.getDeclaredMethod("createGeomShader",
                programClass,String.class);
        vert.setAccessible(true);
        frag.setAccessible(true);
        geom.setAccessible(true);
        int vertex=0,fragment=0,geometry=0,custom=0,program=0;
        try
        {
            vertex=((Number)vert.invoke(null,optifineProgram,
                    base+".vsh")).intValue();
            fragment=((Number)frag.invoke(null,optifineProgram,
                    base+".fsh")).intValue();
            if(hasSource(shaderPack,base+".gsh"))
                geometry=((Number)geom.invoke(null,optifineProgram,
                        base+".gsh")).intValue();
            if(vertex<=0||fragment<=0)
                throw new IllegalStateException(
                        "Native shaderpack stage compilation unavailable "+base);
            // This is the already-preprocessed OptiFine shader source.
            // Unlike a raw shaderpack file, it includes all #include,
            // user shader options and BetterLights source patches.
            String source=GL20.glGetShaderSource(vertex,
                    Math.max(8192,GL20.glGetShaderi(vertex,
                            GL20.GL_SHADER_SOURCE_LENGTH)));
            String patched=injectClipVertex(source);
            // Fixed-function gl_ClipVertex alone is not reliably honored
            // by every OptiFine/driver shader combination. Also reject
            // behind-mirror fragments inside the PRIVATE fragment shader.
            // This keeps BSL's ordinary symmetric projection unchanged.
            String fragmentSource=GL20.glGetShaderSource(fragment,
                    Math.max(8192,GL20.glGetShaderi(fragment,
                            GL20.GL_SHADER_SOURCE_LENGTH)));
            String clippedFragment=injectFragmentClip(fragmentSource);
            int privateFragment=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(privateFragment,clippedFragment);
            GL20.glCompileShader(privateFragment);
            if(GL20.glGetShaderi(privateFragment,GL20.GL_COMPILE_STATUS)==0)
            {
                String reason=GL20.glGetShaderInfoLog(privateFragment,4096);
                GL20.glDeleteShader(privateFragment);
                throw new IllegalStateException(
                        "Mirror fragment clip compilation: "+reason);
            }
            // Replace only the newly compiled transient fragment shader,
            // never touch the original OptiFine linked program.
            GL20.glDeleteShader(fragment);
            fragment=privateFragment;
            custom=GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(custom,patched);
            GL20.glCompileShader(custom);
            if(GL20.glGetShaderi(custom,GL20.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Mirror clipped vertex: "+
                        GL20.glGetShaderInfoLog(custom,4096));
            program=GL20.glCreateProgram();
            GL20.glAttachShader(program,custom);
            GL20.glAttachShader(program,fragment);
            if(geometry>0)GL20.glAttachShader(program,geometry);
            // OptiFine binds static attributes for built-in shaderpack
            // entities and Block VBOs. Preserve the ACTUAL native binding.
            int n=Math.min(64,GL20.glGetProgrami(original,
                    GL20.GL_ACTIVE_ATTRIBUTES));
            for(int i=0;i<n;i++)
            {
                IntBuffer meta=BufferUtils.createIntBuffer(2);
                String name=GL20.glGetActiveAttrib(original,i,256,meta);
                int loc=GL20.glGetAttribLocation(original,name);
                if(loc>=0)GL20.glBindAttribLocation(program,loc,name);
            }
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Mirror clipped shader link: "+
                        GL20.glGetProgramInfoLog(program,4096));
            // A BetterLights-injected native fragment must not be silently
            // replaced by a vanilla fragment missing its light array.
            if(GL20.glGetUniformLocation(program,
                    "bbsMirrorClipPlane")<0)
                throw new IllegalStateException(
                        "Private fragment clip plane uniform optimized away");
            if(GL20.glGetUniformLocation(original,"lightCount")>=0&&
                    GL20.glGetUniformLocation(program,"lightCount")<0)
                throw new IllegalStateException(
                        "Recompiled shader lost BetterLights uniforms");
            int result=program;
            program=0;
            return result;
        }
        finally
        {
            if(program>0)GL20.glDeleteProgram(program);
            if(custom>0)GL20.glDeleteShader(custom);
            if(vertex>0)GL20.glDeleteShader(vertex);
            if(fragment>0)GL20.glDeleteShader(fragment);
            if(geometry>0)GL20.glDeleteShader(geometry);
        }
    }

    private static boolean hasSource(Object shaderPack,String name)
            throws Exception
    {
        if(shaderPack==null)return false;
        Method stream=shaderPack.getClass().getMethod(
                "getResourceAsStream",String.class);
        InputStream input=(InputStream)stream.invoke(shaderPack,name);
        if(input==null)return false;
        input.close();
        return true;
    }

    private static String injectClipVertex(String source)
    {
        if(source==null||source.length()<50)
            throw new IllegalStateException("Native GLSL source missing");
        int main=source.indexOf("void main");
        if(main<0)throw new IllegalStateException("No GLSL main()");
        int begin=source.indexOf('{',main);
        if(begin<0)throw new IllegalStateException("Malformed GLSL main");
        int depth=0;
        int end=-1;
        for(int i=begin;i<source.length();i++)
        {
            char c=source.charAt(i);
            if(c=='{')depth++;
            if(c=='}'&&--depth==0){end=i;break;}
            if(c!='}')continue;
        }
        if(end<0)throw new IllegalStateException("Unclosed GLSL main");
        // Preserve exactly the original shader behavior. This additional
        // output is consumed by the compatibility clip-plane unit only.
        // Using original eye-space position works for terrain vertices.
        return source.substring(0,main)+
                "varying vec4 bbsMirrorClipEye;\n"+
                source.substring(main,end)+
                "\n    bbsMirrorClipEye = gl_ModelViewMatrix * gl_Vertex;\n"+
                "    gl_ClipVertex = bbsMirrorClipEye;\n"+
                source.substring(end);
    }

    private static String injectFragmentClip(String source)
    {
        if(source==null||source.length()<50)
            throw new IllegalStateException("Native fragment GLSL missing");
        int main=source.indexOf("void main");
        if(main<0)throw new IllegalStateException("No fragment main()");
        int begin=source.indexOf('{',main);
        if(begin<0)throw new IllegalStateException("Malformed fragment main");
        // Explicit discard is independent of driver support for legacy
        // user clip planes and executes before BSL's material/depth writes.
        return source.substring(0,main)+
                "varying vec4 bbsMirrorClipEye;\n"+
                "uniform vec4 bbsMirrorClipPlane;\n"+
                source.substring(main,begin+1)+
                "\n    if (dot(bbsMirrorClipPlane, "+
                "bbsMirrorClipEye) < 0.0) discard;\n"+
                source.substring(begin+1);
    }

    /**
     * Mirror-owned program has a separate uniform location table.
     * Copy active main-program uniforms (including BetterLights structs)
     * before overriding camera/matrix uniforms on the clone.
     */
    public static void copyUniforms(int source,int target)
    {
        if(source==target||source<=0||target<=0)return;
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try
        {
            GL20.glUseProgram(target);
            int n=Math.min(2048,GL20.glGetProgrami(source,
                    GL20.GL_ACTIVE_UNIFORMS));
            for(int i=0;i<n;i++)
            {
                IntBuffer meta=BufferUtils.createIntBuffer(2);
                String name=GL20.glGetActiveUniform(source,i,256,meta);
                int size=meta.get(0),type=meta.get(1);
                if(name==null||name.isEmpty())continue;
                size=Math.min(size,128);
                for(int item=0;item<size;item++)
                {
                    String field=name;
                    if(size>1)
                    {
                        int marker=name.indexOf("[0]");
                        if(marker<0)break;
                        field=name.substring(0,marker)+
                                "["+item+"]"+name.substring(marker+3);
                    }
                    int from=GL20.glGetUniformLocation(source,field);
                    int to=GL20.glGetUniformLocation(target,field);
                    if(from<0||to<0)continue;
                    if(isInteger(type))
                    {
                        IntBuffer value=BufferUtils.createIntBuffer(16);
                        GL20.glGetUniform(source,from,value);
                        if(type==GL20.GL_INT_VEC2)
                            GL20.glUniform2i(to,value.get(0),value.get(1));
                        else if(type==GL20.GL_INT_VEC3)
                            GL20.glUniform3i(to,value.get(0),
                                    value.get(1),value.get(2));
                        else if(type==GL20.GL_INT_VEC4)
                            GL20.glUniform4i(to,value.get(0),
                                    value.get(1),value.get(2),value.get(3));
                        else GL20.glUniform1i(to,value.get(0));
                    }
                    else
                    {
                        FloatBuffer value=BufferUtils.createFloatBuffer(16);
                        GL20.glGetUniform(source,from,value);
                        if(type==GL20.GL_FLOAT_VEC2)
                            GL20.glUniform2f(to,value.get(0),value.get(1));
                        else if(type==GL20.GL_FLOAT_VEC3)
                            GL20.glUniform3f(to,value.get(0),
                                    value.get(1),value.get(2));
                        else if(type==GL20.GL_FLOAT_VEC4)
                            GL20.glUniform4f(to,value.get(0),
                                    value.get(1),value.get(2),
                                    value.get(3));
                        else if(type==GL20.GL_FLOAT_MAT4)
                            GL20.glUniformMatrix4(to,false,value);
                        else if(type==GL20.GL_FLOAT_MAT3)
                            GL20.glUniformMatrix3(to,false,value);
                        else if(type==GL20.GL_FLOAT_MAT2)
                            GL20.glUniformMatrix2(to,false,value);
                        else if(type==GL11.GL_FLOAT)
                            GL20.glUniform1f(to,value.get(0));
                    }
                }
            }
        }
        finally{GL20.glUseProgram(previous);}
    }

    private static boolean isInteger(int type)
    {
        return type==GL11.GL_INT||type==GL20.GL_BOOL||
                type==GL20.GL_INT_VEC2||type==GL20.GL_INT_VEC3||
                type==GL20.GL_INT_VEC4||
                type==GL20.GL_SAMPLER_2D||
                type==GL20.GL_SAMPLER_3D||
                type==GL20.GL_SAMPLER_CUBE||
                type==GL20.GL_SAMPLER_2D_SHADOW||
                type==GL20.GL_SAMPLER_1D||
                type==GL20.GL_BOOL_VEC2||
                type==GL20.GL_BOOL_VEC3||
                type==GL20.GL_BOOL_VEC4;
    }
}
