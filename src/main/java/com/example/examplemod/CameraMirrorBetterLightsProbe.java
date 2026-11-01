package com.example.examplemod;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * One-shot, read-only inventory of the ACTUAL linked shaders and installed
 * BetterLights 1.0.3 provider. Makes no assumptions about the closed
 * BetterLights uniform names/layout and NEVER invokes an unknown shader
 * callback or mutates a live light or GL program.
 */
public final class CameraMirrorBetterLightsProbe
{
    private static final String TAG="[BBS Editor][Mirror][LightProbe] ";
    private static final Set<Integer> PROGRAMS=new HashSet<Integer>();
    private static final Set<Integer> ACTIVE_LIGHT_PROGRAMS=
            new HashSet<Integer>();
    private static boolean providerChecked;
    private static int shown;
    private CameraMirrorBetterLightsProbe(){}

    public static void reset()
    {
        PROGRAMS.clear();
        ACTIVE_LIGHT_PROGRAMS.clear();
        providerChecked=false;
        shown=0;
    }

    public static void inspect(int pid)
    {
        if(pid<=0||PROGRAMS.size()>=20||!PROGRAMS.add(pid)||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLLightProbe","true")))
            return;
        inspectProvider();
        try
        {
            int total=GL20.glGetProgrami(pid,GL20.GL_ACTIVE_UNIFORMS);
            int n=Math.min(2048,total);
            int vec=0,scalar=0,other=0;
            List<String> suspicious=new ArrayList<String>();
            List<String> samples=new ArrayList<String>();
            for(int i=0;i<n;i++)
            {
                IntBuffer pair=BufferUtils.createIntBuffer(2);
                String name=GL20.glGetActiveUniform(pid,i,256,pair);
                if(name==null||name.length()==0)continue;
                int size=pair.get(0),type=pair.get(1);
                String lower=name.toLowerCase(Locale.ROOT);
                boolean isVector=type==GL20.GL_FLOAT_VEC3||
                        type==GL20.GL_FLOAT_VEC4;
                if(isVector)vec++;
                else if(type==org.lwjgl.opengl.GL11.GL_FLOAT||
                        type==GL20.GL_FLOAT_VEC2||
                        type==org.lwjgl.opengl.GL11.GL_INT)
                    scalar++;
                else other++;
                boolean key=lower.contains("light")||
                        lower.contains("caster")||
                        lower.contains("point")||
                        lower.contains("source")||
                        lower.contains("position")||
                        lower.contains("better")||
                        lower.contains("beam")||
                        lower.contains("volum");
                String printed=name+"["+size+"]:"+
                        Integer.toHexString(type);
                if(key&&suspicious.size()<24)suspicious.add(printed);
                if(samples.size()<16&&isVector)
                {
                    int loc=GL20.glGetUniformLocation(pid,name);
                    if(loc>=0)
                    {
                        FloatBuffer values=BufferUtils.createFloatBuffer(16);
                        GL20.glGetUniform(pid,loc,values);
                        samples.add(printed+"=("+
                                values.get(0)+","+values.get(1)+","+
                                values.get(2)+")");
                    }
                }
            }
            // The earlier "vec3/4 candidates=1" was printed at the FIRST
            // program, not after all stages. Log PER PROGRAM to distinguish
            // terrain from deferred/composite/sky and avoid false conclusions.
            inspectActiveLights(pid);
            System.out.println(TAG+"GLSL program="+pid+" uniforms="+total+
                    " vectorDeclarations="+vec+" scalar="+scalar+
                    " other="+other+" lightNames="+
                    limit(suspicious.toString(),700)+
                    " vecSamples="+limit(samples.toString(),1100));
        }
        catch(Throwable failure)
        {
            System.err.println(TAG+"Unable to inspect GLSL program "+pid+
                    ": "+failure);
        }
    }

    /**
     * A linked program may have lightCount=0 on the first inspection
     * and receive a real light later in the same frame. Capture that
     * transition once, with actual per-source positions, instead of
     * reporting only stale startup values.
     */
    public static void inspectAfterRefresh(int pid)
    {
        if("false".equalsIgnoreCase(System.getProperty(
                "bbs.editor.camera.mirrorBSLLightProbe","true")))
            return;
        int count=intUniform(pid,"lightCount",-1);
        if(count>0&&ACTIVE_LIGHT_PROGRAMS.add(pid))
            inspectActiveLights(pid);
    }

    private static int intUniform(int pid,String name,int absent)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return absent;
        IntBuffer value=BufferUtils.createIntBuffer(16);
        GL20.glGetUniform(pid,loc,value);
        return value.get(0);
    }

    private static float[] position(int pid,String name,int size)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return null;
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        GL20.glGetUniform(pid,loc,b);
        float[] v=new float[size];
        for(int i=0;i<size;i++)v[i]=b.get(i);
        return v;
    }

    /**
     * The BetterLights injection has lights[i].position and
     * lights[i].positionScreenSpace, which differ from standard BSL.
     * Print the actual CURRENT active slots, not the initial all-zero
     * entries nor only the first program's few vec3 uniforms.
     */
    private static void inspectActiveLights(int pid)
    {
        int count=intUniform(pid,"lightCount",-1);
        if(count<0)return;
        StringBuilder active=new StringBuilder();
        int scanned=0,found=0;
        for(int i=0;i<Math.min(24,Math.max(count,8));i++)
        {
            String base="lights["+i+"].";
            int enabled=intUniform(pid,base+"isEnabled",-1);
            float[] xyz=position(pid,base+"position",3);
            float[] screen=position(pid,base+"positionScreenSpace",4);
            float[] color=position(pid,base+"color",3);
            int hasXYZ=xyz!=null &&
                    (Math.abs(xyz[0])+Math.abs(xyz[1])+
                     Math.abs(xyz[2])>0.0001F)?1:0;
            int hasColor=color!=null &&
                    (Math.abs(color[0])+Math.abs(color[1])+
                     Math.abs(color[2])>0.0001F)?1:0;
            if(enabled!=1&&hasXYZ==0&&hasColor==0)continue;
            scanned++;
            if(found>=8)continue;
            found++;
            if(active.length()>0)active.append("; ");
            active.append(i).append(":enabled=").append(enabled);
            if(xyz!=null)active.append(" xyz=")
                    .append(java.util.Arrays.toString(xyz));
            if(screen!=null)active.append(" screen=")
                    .append(java.util.Arrays.toString(screen));
            if(color!=null)active.append(" rgb=")
                    .append(java.util.Arrays.toString(color));
            int intensityLoc=GL20.glGetUniformLocation(pid,
                    base+"intensity");
            if(intensityLoc>=0)
            {
                FloatBuffer intensity=BufferUtils.createFloatBuffer(16);
                GL20.glGetUniform(pid,intensityLoc,intensity);
                active.append(" intensity=").append(intensity.get(0));
            }
        }
        System.out.println(TAG+"Light struct program="+pid+
                " lightCount="+count+" populatedSlots="+scanned+
                " examples="+limit(active.toString(),1500));
    }

    private static String limit(String s,int n)
    {
        return s.length()>n?s.substring(0,n)+"...":s;
    }

    private static void inspectProvider()
    {
        if(providerChecked)return;
        providerChecked=true;
        JarFile jar=null;
        try
        {
            Class<?> mod=Class.forName("dz.betterlights.BetterLightsMod",
                    false,CameraMirrorBetterLightsProbe.class
                            .getClassLoader());
            CodeSource code=mod.getProtectionDomain().getCodeSource();
            if(code==null||code.getLocation()==null)
                throw new IllegalStateException("No BetterLights code source");
            URL url=code.getLocation();
            if(!"file".equalsIgnoreCase(url.getProtocol()))
                throw new IllegalStateException("Unsupported provider URL");
            File source=new File(url.toURI());
            if(!source.isFile()||!source.getName().endsWith(".jar"))
                throw new IllegalStateException(
                        "BetterLights code source is not an accessible JAR");
            jar=new JarFile(source);
            List<String> classNames=new ArrayList<String>();
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()&&classNames.size()<24)
            {
                String name=entries.nextElement().getName();
                String lower=name.toLowerCase(Locale.ROOT);
                if(!name.startsWith("dz/betterlights/")||
                        !name.endsWith(".class")||
                        !(lower.contains("shader")||
                          lower.contains("uniform")||
                          lower.contains("patcher")))continue;
                classNames.add(name.substring(0,name.length()-6)
                        .replace('/','.'));
            }
            System.out.println(TAG+"BetterLights provider shader classes="+
                    limit(classNames.toString(),1600));
            for(String name:classNames)
            {
                String lower=name.toLowerCase(Locale.ROOT);
                if(!lower.contains("uniform")||
                        (!lower.contains("optifine")&&
                         !lower.contains("shader")))continue;
                try
                {
                    Class<?> c=Class.forName(name,false,
                            mod.getClassLoader());
                    List<String> methods=new ArrayList<String>();
                    for(Method method:c.getDeclaredMethods())
                    {
                        String m=method.getName().toLowerCase(Locale.ROOT);
                        if(!m.contains("uniform")&&!m.contains("shader")&&
                                !m.contains("program")&&
                                !m.contains("update")&&
                                !m.contains("render"))continue;
                        methods.add(method.getName()+"("+
                                method.getParameterTypes().length+
                                " args)");
                        if(methods.size()>=20)break;
                    }
                    List<String> fields=new ArrayList<String>();
                    for(Field f:c.getDeclaredFields())
                    {
                        fields.add(f.getName()+":"+
                                f.getType().getSimpleName());
                        if(fields.size()>=18)break;
                    }
                    System.out.println(TAG+"Provider handler="+name+
                            " methods="+limit(methods.toString(),850)+
                            " fields="+limit(fields.toString(),650));
                }
                catch(Throwable ignored)
                {
                    // Some private provider classes have optional ASM
                    // dependencies; names in the JAR are still useful.
                }
            }
        }
        catch(Throwable error)
        {
            System.out.println(TAG+"Provider introspection unavailable: "+
                    error.getClass().getSimpleName()+" "+
                    error.getMessage());
        }
        finally
        {
            if(jar!=null)try{jar.close();}catch(Exception ignored){}
        }
    }
}
