package com.example.examplemod;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only extraction of the active OptiFine G5 deferred/composite program
 * order and draw-buffer destinations for the mirror compositor.
 *
 * Shader pack files can specify DRAWBUFFERS/RENDERTARGETS and flip overrides
 * independently per pass. Hard-coding BSL as "2 deferred, 5 composite" is
 * insufficient; the exact attachment indices must come from these compiled
 * Program objects. This class never invokes Shaders.renderComposites, changes
 * shader programs, flips textures or binds an OpenGL framebuffer.
 */
public final class CameraMirrorOptiFinePlanning
{
    private CameraMirrorOptiFinePlanning(){}

    public static List<CameraMirrorCompositePlan> inspect(
            int colorCount)throws ReflectiveOperationException
    {
        Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
        Class<?> program=Class.forName("net.optifine.shaders.Program");
        Method getId=program.getMethod("getId");
        Method getName=program.getMethod("getName");
        Method draw=program.getMethod("getDrawBuffers");
        Method flip=program.getMethod("getBuffersFlip");
        List<CameraMirrorCompositePlan> plans=
                new ArrayList<CameraMirrorCompositePlan>();
        append(plans,shaders.getField("ProgramsDeferred").get(null),
                "deferred",getId,getName,draw,flip,colorCount);
        append(plans,shaders.getField("ProgramsComposite").get(null),
                "composite",getId,getName,draw,flip,colorCount);
        return plans;
    }

    private static void append(List<CameraMirrorCompositePlan> plans,
            Object array,String prefix,Method getId,Method getName,
            Method draw,Method flip,int colorCount)
            throws ReflectiveOperationException
    {
        if(array==null)return;
        for(int i=0;i<Array.getLength(array);i++)
        {
            Object p=Array.get(array,i);
            if(p==null||((Number)getId.invoke(p)).intValue()==0)continue;
            IntBuffer raw=(IntBuffer)draw.invoke(p);
            int[] mapping=null;
            if(raw!=null)
            {
                IntBuffer copy=raw.asReadOnlyBuffer();
                copy.position(0);
                mapping=new int[copy.limit()];
                copy.get(mapping);
            }
            Boolean[] flips=(Boolean[])flip.invoke(p);
            String name=String.valueOf(getName.invoke(p));
            if(name.isEmpty())name=prefix+i;
            try
            {
                plans.add(CameraMirrorCompositePlan.create(
                        name,mapping,flips,colorCount));
            }
            catch(IllegalArgumentException unsupported)
            {
                throw new IllegalStateException(
                        "Mirror cannot map "+name+": "+
                        unsupported.getMessage(),unsupported);
            }
        }
    }
}
