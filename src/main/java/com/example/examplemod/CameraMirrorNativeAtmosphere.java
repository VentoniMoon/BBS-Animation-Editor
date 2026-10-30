package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Mirrors must use VANILLA's current biome/weather/fog calculation rather
 * than approximating fog distance or color from a hard-coded render distance.
 *
 * Minecraft 1.12.2 EntityRenderer.setupFog(-1) is the sky pass and
 * setupFog(0) is the terrain pass. Both methods are private, so resolve
 * the installed Minecraft/OptiFine renderer at runtime (MCP and SRG names).
 *
 * This is intentionally not an OptiFine shaderpack renderer. Shaderpacks
 * own a deferred framebuffer/composite graph, which must not be re-entered
 * from RenderWorldLast with only an extra Minecraft Framebuffer.
 */
public final class CameraMirrorNativeAtmosphere implements AutoCloseable
{
    private static Method setupFog;
    private static Method updateFogColor;
    private static Field fogRed,fogGreen,fogBlue;
    private static boolean resolved;
    private static boolean warned;

    private final EntityRenderer renderer;
    private final float previousRed,previousGreen,previousBlue;
    private boolean closed;

    private CameraMirrorNativeAtmosphere(EntityRenderer renderer)
            throws IllegalAccessException
    {
        this.renderer=renderer;
        previousRed=fogRed.getFloat(renderer);
        previousGreen=fogGreen.getFloat(renderer);
        previousBlue=fogBlue.getFloat(renderer);
    }

    public static CameraMirrorNativeAtmosphere begin(Minecraft mc,
            float partialTicks)
    {
        if(mc==null||mc.entityRenderer==null)return null;
        CameraMirrorNativeAtmosphere scope=null;
        try
        {
            if(!resolved)resolve(mc.entityRenderer.getClass());
            if(setupFog==null||updateFogColor==null||
                    fogRed==null||fogGreen==null||fogBlue==null)
                throw new IllegalStateException(
                        "Native EntityRenderer fog methods unavailable");
            scope=new CameraMirrorNativeAtmosphere(mc.entityRenderer);
            // Compute the same sky/biome/weather fog color from the ACTIVE
            // reflected view entity used by the normal world pipeline.
            updateFogColor.invoke(mc.entityRenderer,partialTicks);
            // The mirror target was just cleared with the previous camera's
            // clear color. Clear again with the reflected biome fog color.
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            return scope;
        }
        catch(Throwable failure)
        {
            // Even a throwing third-party fog handler must not leave the
            // native EntityRenderer's cached fog colors altered.
            if(scope!=null)
                try{scope.close();}
                catch(Throwable restoreError)
                {failure.addSuppressed(restoreError);}
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][Mirror] Native fog unavailable: "
                        +failure);
            }
            return null;
        }
    }

    public void sky(float partialTicks)
    {
        invoke(-1,partialTicks);
    }

    public void terrain(float partialTicks)
    {
        invoke(0,partialTicks);
    }

    private void invoke(int phase,float partialTicks)
    {
        try
        {
            setupFog.invoke(renderer,phase,partialTicks);
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot access native fog",e);
        }
        catch(InvocationTargetException e)
        {
            throw new IllegalStateException("Native fog pass failed",
                    e.getCause());
        }
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        try
        {
            fogRed.setFloat(renderer,previousRed);
            fogGreen.setFloat(renderer,previousGreen);
            fogBlue.setFloat(renderer,previousBlue);
            // The enclosing mirror framebuffer guard saves the actual
            // OpenGL fog/color state with glPushAttrib(GL_ALL_ATTRIB_BITS).
        }
        catch(IllegalAccessException e)
        {
            throw new IllegalStateException("Fog state restore failed",e);
        }
    }

    private static void resolve(Class<?> cls)
    {
        resolved=true;
        setupFog=method(cls,new String[]{"setupFog","func_78468_a"},
                int.class,float.class);
        updateFogColor=method(cls,
                new String[]{"updateFogColor","func_78466_h"},float.class);
        fogRed=field(cls,new String[]{"fogColorRed","field_78518_n","n"});
        fogGreen=field(cls,new String[]{"fogColorGreen","field_78519_o","o"});
        fogBlue=field(cls,new String[]{"fogColorBlue","field_78533_p","p"});
    }

    private static Method method(Class<?> cls,String[] names,
            Class<?>... args)
    {
        for(Class<?> current=cls;current!=null;
                current=current.getSuperclass())
            for(String name:names)
            {
                try
                {
                    Method m=current.getDeclaredMethod(name,args);
                    m.setAccessible(true);
                    return m;
                }
                catch(ReflectiveOperationException missing){}
            }
        return null;
    }

    private static Field field(Class<?> cls,String[] names)
    {
        for(Class<?> current=cls;current!=null;
                current=current.getSuperclass())
            for(String name:names)
            {
                try
                {
                    Field f=current.getDeclaredField(name);
                    if(f.getType()!=float.class)continue;
                    f.setAccessible(true);
                    return f;
                }
                catch(ReflectiveOperationException missing){}
            }
        return null;
    }
}
