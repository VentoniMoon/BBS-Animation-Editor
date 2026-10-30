package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
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
    private final boolean previousFogEnabled;
    private final int previousFogMode;
    private final float previousFogStart,previousFogEnd,previousFogDensity;
    private boolean closed;

    private CameraMirrorNativeAtmosphere(EntityRenderer renderer)
            throws IllegalAccessException
    {
        this.renderer=renderer;
        previousRed=fogRed.getFloat(renderer);
        previousGreen=fogGreen.getFloat(renderer);
        previousBlue=fogBlue.getFloat(renderer);
        previousFogEnabled=GL11.glIsEnabled(GL11.GL_FOG);
        previousFogMode=GL11.glGetInteger(GL11.GL_FOG_MODE);
        previousFogStart=GL11.glGetFloat(GL11.GL_FOG_START);
        previousFogEnd=GL11.glGetFloat(GL11.GL_FOG_END);
        previousFogDensity=GL11.glGetFloat(GL11.GL_FOG_DENSITY);
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
            // In the vanilla one-color mirror, update the clear color
            // to the reflected biome and clear the target as before.
            // In BSL, the geometry compositor ALREADY cleared each
            // independent colortex to its native neutral value (including
            // WHITE for gdepth). Calling glClear on all eight draw buffers
            // here would overwrite every GBuffer channel with the same
            // sky/fog color and destroy BSL's encoded material data.
            if(!CameraMirrorBSLCompositor.active())
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|
                        GL11.GL_DEPTH_BUFFER_BIT);
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
            // glPopAttrib restores the OpenGL fog bits, but it does NOT
            // restore GlStateManager's software cache. Restore both here
            // before the enclosing framebuffer guard pops its attribs,
            // otherwise the next native frame may skip fog state changes.
            if(previousFogMode==GL11.GL_LINEAR)
                GlStateManager.setFog(GlStateManager.FogMode.LINEAR);
            else if(previousFogMode==GL11.GL_EXP2)
                GlStateManager.setFog(GlStateManager.FogMode.EXP2);
            else
                GlStateManager.setFog(GlStateManager.FogMode.EXP);
            GlStateManager.setFogStart(previousFogStart);
            GlStateManager.setFogEnd(previousFogEnd);
            GlStateManager.setFogDensity(previousFogDensity);
            if(previousFogEnabled)GlStateManager.enableFog();
            else GlStateManager.disableFog();
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
