package com.example.examplemod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Native OptiFine CAMERA shader curves.
 *
 * The early transformer installs processReader() at OptiFine's shader source
 * boundary and applyUniforms() after useProgram(). Without the exact hook,
 * shader channels are NOT claimed to be active: the UI reports their absence
 * and the regular film-space Curve continues to work.
 *
 * No shader pack is mutated on disk. Runtime values never change pack
 * options, so stopping the editor restores each original default.
 */
public final class CameraShaderCurves
{
    private static final Map<String,CameraShaderCurveSource.Option> OPTIONS=
            new LinkedHashMap<String,CameraShaderCurveSource.Option>();
    private static final Map<Integer,Map<String,Integer>> LOCATIONS=
            new LinkedHashMap<Integer,Map<String,Integer>>();
    private static final Map<String,Double> FRAME=
            new LinkedHashMap<String,Double>();
    private static volatile boolean sourceHook;
    private static volatile boolean programHook;
    private static boolean active;
    private static int uploads;

    private CameraShaderCurves(){}

    /** Executed only while OptiFine is compiling a shader stage. */
    public static BufferedReader processReader(BufferedReader reader)
            throws IOException
    {
        StringBuilder source=new StringBuilder();
        String line;
        try(BufferedReader input=reader)
        {
            while((line=input.readLine())!=null)
                source.append(line).append('\n');
        }
        // During OptiFine's class initialization the option API may not yet
        // be ready. The converter requires an OptiFine slider annotation
        // even in that case, never unrestricted #define rewriting.
        Map<String,Float> actual=
                CameraShaderOptionDiscovery.currentNumericValues();
        Set<String> allowed=actual==null?null:actual.keySet();
        CameraShaderCurveSource.Result result=
                CameraShaderCurveSource.convert(source.toString(),allowed);
        if(!result.options.isEmpty())
            synchronized(OPTIONS)
            {
                for(Map.Entry<String,CameraShaderCurveSource.Option> entry:
                        result.options.entrySet())
                {
                    if(OPTIONS.containsKey(entry.getKey()))continue;
                    CameraShaderCurveSource.Option o=entry.getValue();
                    Float fromPack=actual==null?null:actual.get(entry.getKey());
                    OPTIONS.put(entry.getKey(),
                            new CameraShaderCurveSource.Option(o.name,
                                    fromPack==null?o.fallback:fromPack,
                                    o.integer,o.limits));
                }
            }
        sourceHook=true;
        return new BufferedReader(new StringReader(result.source));
    }

    /** Reset only when the shader pack is actually reloaded. */
    public static void clearPrograms()
    {
        synchronized(LOCATIONS){LOCATIONS.clear();}
    }
    public static void reset()
    {
        synchronized(OPTIONS){OPTIONS.clear();}
        clearPrograms();
        synchronized(FRAME){FRAME.clear();active=false;}
        sourceHook=false;
    }

    /** Called on CAMERA's native RenderTick START, before OptiFine draws. */
    public static void prepare(List<CameraClipDocument.Clip> clips,double time)
    {
        Map<String,Double> next=new LinkedHashMap<String,Double>();
        if(clips!=null)
        {
            List<CameraClipDocument.Clip> sorted=
                    new ArrayList<CameraClipDocument.Clip>(clips);
            Collections.sort(sorted,new java.util.Comparator<CameraClipDocument.Clip>(){
                @Override public int compare(CameraClipDocument.Clip a,
                        CameraClipDocument.Clip b)
                {return Integer.compare(a.layer,b.layer);}
            });
            Map<String,CameraShaderCurveSource.Option> known=options();
            for(CameraClipDocument.Clip clip:sorted)
            {
                if(clip.kind!=CameraClipDocument.Kind.CURVE)continue;
                String channel=clip.settings.get("channels");
                if(channel==null||!channel.startsWith("curve."))continue;
                String name=channel.substring(6);
                CameraShaderCurveSource.Option option=known.get(name);
                if(option==null)continue;
                CameraCurveSampler.Sample sample=
                        CameraCurveSampler.sampleShader(clip,time,
                                option.fallback);
                if(sample!=null)next.put(name,sample.value);
            }
        }
        synchronized(FRAME)
        {
            FRAME.clear();
            FRAME.putAll(next);
            active=true;
        }
    }
    public static void clearFrame()
    {
        synchronized(FRAME){FRAME.clear();active=false;}
    }

    /** One hook on every OptiFine useProgram, including its fast path. */
    public static void applyUniforms()
    {
        programHook=true;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if(program==0)return;
        Map<String,Integer> locations;
        synchronized(LOCATIONS)
        {
            locations=LOCATIONS.get(program);
            if(locations==null)
            {
                locations=new LinkedHashMap<String,Integer>();
                LOCATIONS.put(program,locations);
            }
        }
        Map<String,CameraShaderCurveSource.Option> options;
        synchronized(OPTIONS)
        {options=new LinkedHashMap<String,CameraShaderCurveSource.Option>(OPTIONS);}
        for(CameraShaderCurveSource.Option option:options.values())
        {
            String uniform=CameraShaderCurveSource.PREFIX+option.name;
            Integer id=locations.get(uniform);
            if(id==null)
            {
                id=GL20.glGetUniformLocation(program,uniform);
                locations.put(uniform,id);
            }
            if(id<0)continue;
            Double value;
            synchronized(FRAME){value=active?FRAME.get(option.name):null;}
            float resolved=value==null?option.fallback:value.floatValue();
            if(!Float.isFinite(resolved))resolved=option.fallback;
            if(option.integer)GL20.glUniform1i(id,Math.round(resolved));
            else GL20.glUniform1f(id,resolved);
            uploads++;
        }
    }

    public static Map<String,CameraShaderCurveSource.Option> options()
    {
        synchronized(OPTIONS)
        {return new LinkedHashMap<String,CameraShaderCurveSource.Option>(OPTIONS);}
    }
    /**
     * Numeric options may be known to OptiFine before the source compiler
     * has exposed their corresponding uniforms (or if an early core hook
     * was missing). Make these discoverable in the picker, but retain a
     * separate list of ACTUALLY patched variables for rendering.
     */
    public static Map<String,Float> availableOptions()
    {
        Map<String,Float> detected=
                CameraShaderOptionDiscovery.currentNumericValues();
        Map<String,Float> result=new LinkedHashMap<String,Float>();
        if(detected!=null)result.putAll(detected);
        synchronized(OPTIONS)
        {
            for(CameraShaderCurveSource.Option option:OPTIONS.values())
                if(!result.containsKey(option.name))
                    result.put(option.name,option.fallback);
        }
        return result;
    }

    public static boolean animatable(String name)
    {
        synchronized(OPTIONS){return OPTIONS.containsKey(name);}
    }

    public static boolean isReady()
    {
        return sourceHook&&programHook&&!options().isEmpty();
    }
    public static String state()
    {
        String discovery=CameraShaderOptionDiscovery.status();
        if(discovery.startsWith("No active")||
                discovery.startsWith("OptiFine Shaders not installed")||
                discovery.startsWith("OptiFine option API"))
            return discovery;
        if(!CameraShaderCurveTransformer.installed())
            return "OptiFine hook missing; restart Minecraft";
        if(!sourceHook)
            return "OptiFine hook ready; reload shader pack (F3+R)";
        if(options().isEmpty())
            return "No compatible GLSL variables; "+discovery;
        if(!programHook)
            return options().size()+" shader options; uniform hook pending";
        return options().size()+" shader options animated";
    }
}
