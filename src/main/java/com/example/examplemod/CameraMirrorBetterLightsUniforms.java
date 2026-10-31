package com.example.examplemod;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * Rebase the BetterLights position uniforms already uploaded for the MAIN
 * CAMERA before RenderWorldLast. The optional BetterLights uniform hook is
 * not called on our manual private glUseProgram() path.
 *
 * Do not guess injected GLSL uniform names. Discover the active vec3/vec4
 * uniforms of the *actual compiled* program, compare their current values
 * with world positions registered by BetterLightsWorldEngine in the
 * expected MAIN-camera spaces, and write only proven position matches.
 *
 * This deliberately does not touch sun/moon/shadow, fog, color, matrices,
 * native OptiFine values, world-space light positions, or unknown formats.
 * Every modification is tracked through the compositor's uniform backup.
 */
public final class CameraMirrorBetterLightsUniforms
{
    public interface Writer
    {
        void write3(int pid,String name,float x,float y,float z);
        void write4(int pid,String name,float x,float y,float z,float w);
    }

    private static final class Uniform
    {
        final String name;
        final int type;
        Uniform(String name,int type){this.name=name;this.type=type;}
    }

    private static final Map<Integer,List<Uniform>> CACHE=
            new HashMap<Integer,List<Uniform>>();
    private static boolean foundLogged,notFoundLogged;
    private static int maxCandidateCount;
    private static String candidateExamples="";
    private CameraMirrorBetterLightsUniforms(){}

    public static void reset()
    {
        CACHE.clear();
        foundLogged=false;
        notFoundLogged=false;
        maxCandidateCount=0;
        candidateExamples="";
    }

    private static List<Uniform> list(int pid)
    {
        List<Uniform> cached=CACHE.get(pid);
        if(cached!=null)return cached;
        List<Uniform> result=new ArrayList<Uniform>();
        int n=Math.min(2048,GL20.glGetProgrami(pid,
                GL20.GL_ACTIVE_UNIFORMS));
        for(int i=0;i<n;i++)
        {
            IntBuffer st=BufferUtils.createIntBuffer(2);
            String base=GL20.glGetActiveUniform(pid,i,256,st);
            if(base==null||base.isEmpty())continue;
            int len=Math.min(128,Math.max(1,st.get(0)));
            int type=st.get(1);
            if(type!=GL20.GL_FLOAT_VEC3&&type!=GL20.GL_FLOAT_VEC4)
                continue;
            for(int k=0;k<len;k++)
            {
                String name=base;
                if(len>1)
                {
                    int index=name.indexOf("[0]");
                    if(index<0)continue;
                    name=name.substring(0,index)+"["+k+"]"+
                            name.substring(index+3);
                }
                if(GL20.glGetUniformLocation(pid,name)>=0)
                    result.add(new Uniform(name,type));
            }
        }
        CACHE.put(pid,result);
        return result;
    }

    private static boolean excluded(String name)
    {
        String n=name.toLowerCase(Locale.ROOT);
        return n.equals("cameraposition")||
                n.equals("previouscameraposition")||
                n.equals("sunposition")||
                n.equals("moonposition")||
                n.equals("shadowlightposition")||
                n.equals("upposition")||
                n.equals("relativeeyeposition")||
                n.equals("skycolor")||
                n.equals("fogcolor");
    }

    public static int apply(int pid,List<double[]> sources,
            double[] main,double[] mirror,float[] nativeView,
            float[] reflectedView,Writer writer)
    {
        if(pid<=0||sources==null||sources.isEmpty()||
                main==null||mirror==null||writer==null||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLLightUniforms","true")))
            return 0;
        List<Uniform> candidates=list(pid);
        maxCandidateCount=Math.max(maxCandidateCount,candidates.size());
        int fixed=0;
        for(Uniform uniform:candidates)
        {
            if(excluded(uniform.name))continue;
            int loc=GL20.glGetUniformLocation(pid,uniform.name);
            if(loc<0)continue;
            FloatBuffer data=BufferUtils.createFloatBuffer(16);
            GL20.glGetUniform(pid,loc,data);
            float[] observed={data.get(0),data.get(1),data.get(2)};
            if(!Float.isFinite(observed[0])||
                    !Float.isFinite(observed[1])||
                    !Float.isFinite(observed[2]))continue;
            boolean matched=false;
            for(double[] source:sources)
            {
                if(source==null||source.length<3)continue;
                int kind=CameraMirrorLightSpace.match(observed,source,
                        main,nativeView);
                if(kind==CameraMirrorLightSpace.UNKNOWN)continue;
                matched=true;
                if(kind==CameraMirrorLightSpace.WORLD)break;
                float[] updated=CameraMirrorLightSpace.reflected(kind,
                        source,mirror,reflectedView);
                if(updated==null)break;
                if(uniform.type==GL20.GL_FLOAT_VEC4)
                    writer.write4(pid,uniform.name,updated[0],
                            updated[1],updated[2],data.get(3));
                else writer.write3(pid,uniform.name,updated[0],
                        updated[1],updated[2]);
                fixed++;
                if(!foundLogged)
                {
                    foundLogged=true;
                    System.out.println("[BBS Editor][Mirror][BetterLights] "+
                            "Updated compiled GLSL light position "+
                            uniform.name+" in "+pid+
                            " ("+(kind==CameraMirrorLightSpace.EYE?
                                "view-space":"relative-space")+
                            "); original="+observed[0]+","+
                            observed[1]+","+observed[2]+
                            " reflected="+updated[0]+","+
                            updated[1]+","+updated[2]+
                            " (GL values restored after mirror)");
                }
                break;
            }
            if(!matched&&candidateExamples.length()<340)
            {
                String n=uniform.name.toLowerCase(Locale.ROOT);
                if(n.contains("light")||n.contains("caster")||
                        n.contains("source"))
                    candidateExamples+=uniform.name+";";
            }
        }
        if(fixed==0&&!notFoundLogged)
        {
            notFoundLogged=true;
            System.out.println("[BBS Editor][Mirror][BetterLights] "+
                    "No direct position-uniform match yet; sources="+
                    sources.size()+" vec3/4 candidates="+maxCandidateCount+
                    " light names="+candidateExamples+
                    " (possible BetterLights UBO/SSBO or view transform "+
                    "mismatch; no unsafe shader modifications)");
        }
        return fixed;
    }
}
