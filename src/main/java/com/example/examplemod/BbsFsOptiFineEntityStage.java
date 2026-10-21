package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.entity.Entity;

/**
 * Places editor-recorded actors into OptiFine's ordinary ENTITIES shader
 * program rather than the late-world textured/composite shader.
 *
 * RenderWorldLastEvent occurs after RenderGlobal's normal entity pass has
 * ended but BEFORE OptiFine Shaders.endRender(). RenderManager.renderEntity
 * alone therefore uses the wrong shader program (and can appear fullbright).
 *
 * OptiFine is optional: all access is reflective. Save and restore its own
 * activeProgram through Shaders.useProgram, NOT raw GL20.glUseProgram:
 * otherwise OptiFine's cached program/draw-buffer state becomes inconsistent.
 */
public final class BbsFsOptiFineEntityStage implements AutoCloseable
{
    private static boolean initialized;
    private static boolean unavailableReported;
    private static Class<?> shadersClass;
    private static Field renderingWorld;
    private static Field activeProgram;
    private static Method beginEntities;
    private static Method nextEntity;
    private static Method endEntities;
    private static Method useProgram;

    private final Object previousProgram;
    private final boolean entered;

    private BbsFsOptiFineEntityStage(Object previousProgram, boolean entered)
    {
        this.previousProgram = previousProgram;
        this.entered = entered;
    }

    private static synchronized void initialize()
    {
        if (initialized) return;
        initialized = true;

        try
        {
            shadersClass = Class.forName("net.optifine.shaders.Shaders");
            renderingWorld = shadersClass.getField("isRenderingWorld");
            activeProgram = shadersClass.getField("activeProgram");
            Class<?> programClass = activeProgram.getType();
            beginEntities = shadersClass.getMethod("beginEntities");
            nextEntity = shadersClass.getMethod("nextEntity", Entity.class);
            endEntities = shadersClass.getMethod("endEntities");
            useProgram = shadersClass.getMethod("useProgram", programClass);
        }
        catch (ClassNotFoundException absent)
        {
            // OptiFine is not installed; vanilla entity lighting still works.
            shadersClass = null;
        }
        catch (ReflectiveOperationException incompatible)
        {
            reportOnce("OptiFine entity shader hooks unavailable: " +
                    incompatible.getClass().getSimpleName());
            shadersClass = null;
        }
    }

    public static BbsFsOptiFineEntityStage open()
    {
        initialize();
        if (shadersClass == null) return new BbsFsOptiFineEntityStage(null, false);
        try
        {
            if (!renderingWorld.getBoolean(null))
                return new BbsFsOptiFineEntityStage(null, false);

            Object previous = activeProgram.get(null);
            beginEntities.invoke(null);
            return new BbsFsOptiFineEntityStage(previous, true);
        }
        catch (Exception error)
        {
            reportOnce("OptiFine entity stage begin failed: " +
                    error.getClass().getSimpleName());
            return new BbsFsOptiFineEntityStage(null, false);
        }
    }

    public static void beforeEntity(Entity actor)
    {
        if (shadersClass == null || actor == null) return;
        try
        {
            if (renderingWorld.getBoolean(null))
                nextEntity.invoke(null, actor);
        }
        catch (Exception error)
        {
            reportOnce("OptiFine entity metadata failed: " +
                    error.getClass().getSimpleName());
        }
    }

    @Override
    public void close()
    {
        if (!entered) return;
        try
        {
            endEntities.invoke(null);
        }
        catch (Exception error)
        {
            reportOnce("OptiFine entity shader end failed: " +
                    error.getClass().getSimpleName());
        }
        finally
        {
            if (previousProgram != null)
            {
                try
                {
                    useProgram.invoke(null, previousProgram);
                }
                catch (Exception error)
                {
                    reportOnce("OptiFine shader restore failed: " +
                            error.getClass().getSimpleName());
                }
            }
        }
    }

    private static void reportOnce(String message)
    {
        if (unavailableReported) return;
        unavailableReported = true;
        System.err.println("[BBS Editor][FS Preview] " + message);
    }
}
