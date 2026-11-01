package com.example.examplemod;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
    private static final Set<Integer> VIEWED_PROGRAMS=
            new HashSet<Integer>();
    private static int totalVectorCandidates;
    private static int maxCandidateCount;
    private static String candidateExamples="";
    // Once a screen-space encoding is PROVEN from native GL values,
    // remember its type for the same compiled program and uniform slot.
    // This lets us reproject from the registered WORLD light even when a
    // later native snapshot retains stale main-camera screen coordinates.
    private static final Map<String,Integer> SCREEN_ENCODINGS=
            new HashMap<String,Integer>();
    private static boolean cachedScreenLogged;
    private static int screenDiagnosticCount;
    private static double[] lastDiagnosticCamera;
    private CameraMirrorBetterLightsUniforms(){}

    public static void reset()
    {
        CACHE.clear();
        foundLogged=false;
        notFoundLogged=false;
        VIEWED_PROGRAMS.clear();
        totalVectorCandidates=0;
        CameraMirrorBetterLightsProbe.reset();
        maxCandidateCount=0;
        candidateExamples="";
        SCREEN_ENCODINGS.clear();
        cachedScreenLogged=false;
        screenDiagnosticCount=0;
        lastDiagnosticCamera=null;
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
        if(VIEWED_PROGRAMS.add(pid))
        {
            totalVectorCandidates+=result.size();
            CameraMirrorBetterLightsProbe.inspect(pid);
        }
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
            float[] reflectedView,float[] nativeProjection,
            float[] reflectedProjection,Writer writer)
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
            // BetterLights uses a separate positionScreenSpace vec4
            // for some effects. Correct it only when the native value
            // precisely matches a registered light's projection under
            // the MAIN camera. Do not invent a value for unknown encodings.
            if(uniform.name.startsWith("lights[")&&
                    uniform.name.endsWith(".positionScreenSpace")&&
                    uniform.type==GL20.GL_FLOAT_VEC4&&
                    reflectedProjection!=null&&reflectedView!=null)
            {
                float[] observed4={data.get(0),data.get(1),
                        data.get(2),data.get(3)};
                String key=pid+":"+uniform.name;
                // Confirm the original light slot maps to an actual
                // registered caster. Never infer source ordering.
                double[] slotSource=verifiedWorldSource(
                        pid,uniform.name,sources);
                if(slotSource!=null)
                {
                    int mode=CameraMirrorLightSpace.SCREEN_UNKNOWN;
                    if(nativeProjection!=null&&nativeView!=null)
                        mode=CameraMirrorLightSpace.matchScreen(observed4,
                                nativeProjection,nativeView,
                                slotSource,main);
                    if(mode!=CameraMirrorLightSpace.SCREEN_UNKNOWN)
                        SCREEN_ENCODINGS.put(key,mode);
                    else
                    {
                        Integer known=SCREEN_ENCODINGS.get(key);
                        if(known!=null)mode=known.intValue();
                    }
                    if(mode!=CameraMirrorLightSpace.SCREEN_UNKNOWN)
                    {
                        float[] corrected=CameraMirrorLightSpace.screen(
                                reflectedProjection,reflectedView,
                                slotSource,mirror,mode);
                        if(corrected!=null)
                        {
                            writer.write4(pid,uniform.name,
                                    corrected[0],corrected[1],
                                    corrected[2],corrected[3]);
                            fixed++;
                            matched=true;
                            if(!foundLogged)
                            {
                                foundLogged=true;
                                System.out.println(
                                    "[BBS Editor][Mirror][BetterLights] "+
                                    "Reprojected verified positionScreenSpace "+
                                    uniform.name+" program="+pid+
                                    " encoding="+mode);
                            }
                            if(nativeProjection!=null&&
                                    CameraMirrorLightSpace.matchScreen(
                                        observed4,nativeProjection,
                                        nativeView,slotSource,main)==
                                        CameraMirrorLightSpace.SCREEN_UNKNOWN&&
                                    !cachedScreenLogged)
                            {
                                cachedScreenLogged=true;
                                System.out.println(
                                    "[BBS Editor][Mirror][BetterLights] "+
                                    "Reprojected camera-moved light with "+
                                    "previously verified screen encoding "+
                                    mode+" (stale native coordinates ignored)");
                            }
                        }
                    }
                    if(!matched)
                        logUnmatchedScreen(uniform.name,observed4,
                                slotSource,main,mirror);
                }
                // A vec4 screen-space position is not an ordinary
                // world/eye-space XYZ. If its encoding was not verified,
                // leave it unchanged instead of guessing a conversion
                // from its first three components.
                continue;
            }
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
        return fixed;
    }

    private static double[] verifiedWorldSource(int pid,String name,
            List<double[]> sources)
    {
        int open=name.indexOf('['),close=name.indexOf(']',open+1);
        if(open<0||close<=open)return null;
        int index;
        try{index=Integer.parseInt(name.substring(open+1,close));}
        catch(NumberFormatException wrong){return null;}
        int loc=GL20.glGetUniformLocation(pid,
                "lights["+index+"].position");
        if(loc<0)return null;
        FloatBuffer raw=BufferUtils.createFloatBuffer(16);
        GL20.glGetUniform(pid,loc,raw);
        float[] position={raw.get(0),raw.get(1),raw.get(2)};
        for(double[] source:sources)
        {
            if(source==null||source.length<3)continue;
            if(Math.abs(position[0]-source[0])<0.2D&&
                    Math.abs(position[1]-source[1])<0.2D&&
                    Math.abs(position[2]-source[2])<0.2D)
                return source;
        }
        return null;
    }

    private static void logUnmatchedScreen(String name,
            float[] observed,double[] source,double[] main,double[] mirror)
    {
        if(screenDiagnosticCount>=8||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLScreenDiagnostics","true")))
            return;
        if(lastDiagnosticCamera!=null&&
                !CameraMirrorLightSpace.cameraMoved(
                        lastDiagnosticCamera,main,0.5D))
            return;
        lastDiagnosticCamera=main.clone();
        screenDiagnosticCount++;
        System.out.println("[BBS Editor][Mirror][BetterLights] "+
                "Unmatched positionScreenSpace after movement "+
                name+" observed="+java.util.Arrays.toString(observed)+
                " world="+java.util.Arrays.toString(source)+
                " main="+java.util.Arrays.toString(main)+
                " mirror="+java.util.Arrays.toString(mirror)+
                " (no guessed conversion applied)");
    }

    /** Print after the whole reflected frame, not after the FIRST sky or
     * terrain program. Previous diagnostics reported only one program
     * and made an incorrect global inference about the provider format. */
    public static void frameCompleted()
    {
        if(!notFoundLogged&&!foundLogged&&!VIEWED_PROGRAMS.isEmpty())
        {
            notFoundLogged=true;
            System.out.println("[BBS Editor][Mirror][BetterLights] "+
                    "No direct position-uniform match after entire frame; "+
                    "compiledPrograms="+VIEWED_PROGRAMS.size()+
                    " totalVec3/4="+totalVectorCandidates+
                    " maxPerProgram="+maxCandidateCount+
                    " possibleNames="+candidateExamples+
                    " (see [Mirror][LightProbe] for the actual GLSL "+
                    "program and provider uniform inventories)");
        }
    }
}
