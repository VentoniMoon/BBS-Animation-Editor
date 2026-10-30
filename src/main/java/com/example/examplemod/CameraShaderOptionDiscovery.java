package com.example.examplemod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Introspection of the currently selected OptiFine 1.12.2 shader pack.
 * An option appearing here does NOT imply its GLSL program is patched:
 * CameraShaderCurves.options() reports the actual converted uniforms.
 *
 * No compile-time references to OptiFine are permitted.
 */
public final class CameraShaderOptionDiscovery
{
    private static volatile String lastStatus="Shader pack not checked";
    private CameraShaderOptionDiscovery(){}

    public static String status(){return lastStatus;}

    public static Set<String> enabledNames()
    {
        Map<String,Float> values=currentNumericValues();
        return values==null?null:Collections.unmodifiableSet(
                new LinkedHashSet<String>(values.keySet()));
    }

    /** Effective numeric values from the ACTIVE user-selected shader pack. */
    public static Map<String,Float> currentNumericValues()
    {
        Class<?> api;
        try
        {
            api=Class.forName("net.optifine.shaders.Shaders");
        }
        catch(ClassNotFoundException noOptiFine)
        {
            lastStatus="OptiFine Shaders not installed";
            return null;
        }
        catch(Throwable unavailable)
        {
            lastStatus="OptiFine Shaders cannot initialize: "+
                    unavailable.getClass().getSimpleName();
            return null;
        }
        try
        {
            boolean loaded=api.getField("shaderPackLoaded").getBoolean(null);
            if(!loaded)
            {
                lastStatus="No active OptiFine shader pack";
                return Collections.emptyMap();
            }
            Method get=api.getMethod("getShaderPackOptions");
            Object result=get.invoke(null);
            if(!(result instanceof Object[]))
            {
                lastStatus="OptiFine pack returned no shader options";
                return Collections.emptyMap();
            }
            Map<String,Float> names=new LinkedHashMap<String,Float>();
            for(Object raw:(Object[])result)
            {
                if(raw==null)continue;
                Class<?> c=raw.getClass();
                if(!Boolean.TRUE.equals(c.getMethod("isEnabled")
                        .invoke(raw)))continue;
                String name=(String)c.getMethod("getName").invoke(raw);
                String value=(String)c.getMethod("getValue").invoke(raw);
                if(name==null||value==null)continue;
                value=value.trim().replaceAll("[fF]$","");
                boolean integer=value.matches("[-+]?\\d+");
                if(integer)
                {
                    Object slider=api.getMethod("isShaderPackOptionSlider",
                            String.class).invoke(null,name);
                    if(!Boolean.TRUE.equals(slider))continue;
                }
                try
                {
                    float actual=Float.parseFloat(value);
                    if(Float.isFinite(actual))names.put(name,actual);
                }
                catch(NumberFormatException nonNumeric){}
            }
            lastStatus=names.isEmpty()?
                    "Active shader pack has no numeric options":
                    "OptiFine: "+names.size()+" numeric options detected";
            return Collections.unmodifiableMap(names);
        }
        catch(Throwable unsupported)
        {
            Throwable cause=unsupported.getCause();
            if(cause==null)cause=unsupported;
            lastStatus="OptiFine option API: "+
                    cause.getClass().getSimpleName();
            return null;
        }
    }
}
