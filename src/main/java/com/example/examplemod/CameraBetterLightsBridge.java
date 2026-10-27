package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraftforge.fml.common.Loader;

/**
 * The native CAMERA actor is a preview-only EntityActor: it is drawn in
 * RenderWorldLast but never updated by Minecraft's entity tick loop.
 *
 * Blockbuster 2.7.1's optional BetterLightsMorph registers its LightCaster
 * in update(EntityLivingBase), while render(...) subsequently updates its
 * world position and direction. Without a logical update, the cone/icon is
 * visible but the actual world light is missing.
 *
 * Use only Blockbuster's own morph lifecycle; no direct linkage against
 * BetterLights classes, no synthetic GL lights, no second world render.
 */
public final class CameraBetterLightsBridge
{
    private static final String LIGHT_MORPH =
            "mchorse.blockbuster_pack.morphs.BetterLightsMorph";

    private static final class State
    {
        AbstractMorph morph;
        int tick=Integer.MIN_VALUE;
    }

    private static final WeakHashMap<EntityActor,State> STATES=
            new WeakHashMap<EntityActor,State>();
    private static boolean logged,failed;

    public static void prepare(EntityActor actor,int tick)
    {
        if(actor==null||actor.world==null||!actor.world.isRemote||
                !Loader.isModLoaded("better_lights"))return;
        AbstractMorph morph=actor.getMorph();
        if(morph==null||!LIGHT_MORPH.equals(morph.getClass().getName()))
            return;
        synchronized(STATES)
        {
            State state=STATES.get(actor);
            if(state==null)
            {
                state=new State();
                STATES.put(actor,state);
            }
            if(state.morph!=morph)
            {
                state.morph=morph;
                state.tick=Integer.MIN_VALUE;
            }
            try
            {
                if(tick!=state.tick)
                {
                    // Exactly once per *logical* 20 TPS scene tick. An
                    // offline 60 FPS job must not advance the morph's
                    // animation or register a fresh light 3x per tick.
                    morph.update(actor);
                    state.tick=tick;
                    if(!logged)
                    {
                        logged=true;
                        System.out.println("[BBS CAMERA][BetterLights] "+
                                "Preview actor LightCaster registered "+
                                "through Blockbuster's morph update path");
                    }
                }
                else
                {
                    // Blockbuster's own immersive model GUI prolongs the
                    // temporary LightCaster runnable while an actor is
                    // paused. Do that here without an animation update.
                    keepAlive(morph);
                }
            }
            catch(Throwable exception)
            {
                if(!failed)
                {
                    failed=true;
                    System.err.println("[BBS CAMERA][BetterLights] "+
                            "Unable to maintain actor light: "+exception);
                }
            }
        }
    }

    /**
     * Best-effort access to BetterLightsMorph's public ExpirableRunnable
     * methods via its private dummy field, matching Blockbuster 2.7.1.
     * If an alternate version changes the field, its normal TTL applies.
     */
    private static void keepAlive(AbstractMorph morph)
    {
        try
        {
            Field dummyField=morph.getClass().getDeclaredField("dummy");
            dummyField.setAccessible(true);
            Object dummy=dummyField.get(morph);
            if(dummy==null)return;
            Method age=dummy.getClass().getMethod("getAge");
            Method lifetime=dummy.getClass().getMethod(
                    "setLifetime",int.class);
            int tick=((Number)age.invoke(dummy)).intValue();
            lifetime.invoke(dummy,tick+3);
        }
        catch(ReflectiveOperationException ignored)
        {
            // Never fail a CAMERA render due to an optional mod's ABI.
        }
        catch(SecurityException ignored){}
    }

    /**
     * Detach only the LightCaster owned by this preview entity. Native
     * BetterLightsMorph leaves a temporary light registered until its
     * separate WorldClientTickEvent runnable expires. That is too late for
     * editor scrubbing, changing morphs or deleting a CAMERA light, because
     * the old source continues illuminating its previous world position.
     *
     * Never ask getLightcaster() for cleanup: it lazily creates a caster.
     * Reflection keeps BetterLights optional at both compile and runtime.
     */
    public static void retire(EntityActor actor)
    {
        if(actor==null)return;
        synchronized(STATES)
        {
            STATES.remove(actor);
        }
        retireMorph(actor.getMorph(),actor.world);
    }

    public static void retireMorph(AbstractMorph morph,
            net.minecraft.world.World world)
    {
        if(morph==null||world==null||
                !LIGHT_MORPH.equals(morph.getClass().getName())||
                !Loader.isModLoaded("better_lights"))return;
        try
        {
            Field casterField=morph.getClass().getDeclaredField("lightCaster");
            casterField.setAccessible(true);
            Object caster=casterField.get(morph);
            if(caster==null)return;
            Class<?> api=Class.forName("dz.betterlights.BetterLightsMod");
            Object manager=api.getMethod("getLightManager").invoke(null);
            for(Method method:manager.getClass().getMethods())
            {
                if(!"removeLightCaster".equals(method.getName())||
                        method.getParameterTypes().length!=3)continue;
                Class<?>[] args=method.getParameterTypes();
                if(!args[0].isInstance(world)||!args[1].isInstance(caster)
                        ||args[2]!=boolean.class)continue;
                method.invoke(manager,world,caster,false);
                // The native runnable can still expire naturally; it must
                // not keep a second rendered source alive after detaching.
                break;
            }
        }
        catch(ReflectiveOperationException ignored)
        {
            // Optional BetterLights ABI; the native TTL is the fallback.
        }
        catch(SecurityException ignored){}
    }

    private CameraBetterLightsBridge(){}
}
