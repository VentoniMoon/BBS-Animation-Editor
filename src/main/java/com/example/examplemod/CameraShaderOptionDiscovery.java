package com.example.examplemod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reflection-only integration: OptiFine is optional in Forge dev/CI. */
public final class CameraShaderOptionDiscovery
{
    private CameraShaderOptionDiscovery(){}

    /**
     * Return null while OptiFine hasn't made its active options available.
     * Source adapter still enforces the literal // [slider] annotation then.
     */
    public static Set<String> enabledNames()
    {
        try
        {
            Class<?> api=Class.forName("net.optifine.shaders.Shaders");
            Method get=api.getMethod("getShaderPackOptions");
            Object result=get.invoke(null);
            if(!(result instanceof Object[]))return null;
            Set<String> names=new LinkedHashSet<String>();
            for(Object raw:(Object[])result)
            {
                if(raw==null)continue;
                Class<?> c=raw.getClass();
                if(!(Boolean)c.getMethod("isEnabled").invoke(raw))continue;
                String name=(String)c.getMethod("getName").invoke(raw);
                String value=(String)c.getMethod("getValue").invoke(raw);
                if(name==null||value==null)continue;
                // Int-valued directives are often used as compile-time
                // sample counts; only explicit OptiFine slider integers
                // are candidates and the source parser still excludes #if.
                boolean integer=value.matches("[-+]?\\d+");
                if(integer)
                {
                    Object slider=api.getMethod("isShaderPackOptionSlider",
                            String.class).invoke(null,name);
                    if(!Boolean.TRUE.equals(slider))continue;
                }
                names.add(name);
            }
            return Collections.unmodifiableSet(names);
        }
        catch(Throwable optionalOptifineUnavailable)
        {
            return null;
        }
    }
}
