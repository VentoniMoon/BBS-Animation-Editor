package com.example.examplemod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import mchorse.mclib.utils.ValueSerializer;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * The single BetterLights implementation for BOTH recorded actor lights and
 * CAMERA static lights. Runs at RenderTick.START, BEFORE the Minecraft world,
 * shader uniforms, OptiFine shadows and the editor's native actor stage.
 *
 * Each stable source ID owns exactly one native BetterLightsMorph LightCaster.
 * No synthetic EntityActor, Blockbuster morph.update(), ExpirableRunnable,
 * renderer pass, or second GL render. Native BetterLights properties are
 * applied by BetterLightsMorph itself; only its final position/direction
 * are supplied in world space by BetterLightsWorldState.
 *
 * BetterLights is optional; the only direct dependencies are Blockbuster and
 * MCLib already required by the editor. Its manager and LightCaster are
 * accessed reflectively. All ownership ends on editor/world/scene detach.
 */
public final class BetterLightsWorldEngine
{
    private static final Map<String,Slot> SLOTS=new HashMap<String,Slot>();
    private static World activeWorld;
    private static EditorSceneState activeScene;
    private static int activeSceneIndex=-1;
    private static boolean warned;

    private static final class Slot
    {
        AbstractMorph morph;
        Object caster;
        ValueSerializer baseline;
        String baseSignature;
        boolean registered;
    }

    public static synchronized void prepare(Minecraft mc,
            AnimationEditorScreen editor)
    {
        if(mc==null||mc.world==null||editor==null||
                !Loader.isModLoaded("better_lights"))
        {
            reset();
            return;
        }
        EditorSceneState scene=editor.getSceneState();
        if(scene==null){reset();return;}
        if(activeWorld!=mc.world||activeScene!=scene||
                activeSceneIndex!=scene.getSelectedScene())
        {
            reset();
            activeWorld=mc.world;
            activeScene=scene;
            activeSceneIndex=scene.getSelectedScene();
        }

        Set<String> present=new HashSet<String>();
        int tick=editor.getCurrentFrame();
        double time=CameraVisualFrameClock.sceneTime(tick);
        BetterLightsRig.bind(scene);

        // Actor lights exist in every animation mode. A light actor itself
        // is Record-backed and uses precisely the same fractional position
        // sample as the normal multi-actor renderer.
        for(BlockbusterSceneActorData actor:scene.getActors())
        {
            if(actor==null||actor.getId()==null||actor.getActor()==null||
                    !actor.hasRecord())continue;
            NBTTagCompound base=actor.getActor().getMorph();
            if(!BetterLightsRig.isLight(base))continue;
            if(!editor.showAllActorsInPreview() &&
                    actor!=scene.getSelectedActorData())continue;
            BlockbusterRecord record=actor.getRecord();
            if(record==null)continue;
            BlockbusterRecordFrame frame=CameraExportActorFrameSampler.sample(
                    record,tick,CameraVisualFrameClock.fraction(tick));
            if(frame==null)continue;
            ActorAnimationData rig=BetterLightsRig.forActor(actor);
            BetterLightsWorldState world=BetterLightsWorldState.actor(
                    frame.getX(),frame.getY(),frame.getZ(),
                    BetterLightsRig.offset(rig,time),
                    BetterLightsRig.targetOffset(rig,time));
            NBTTagCompound settings=BetterLightsRig.settingsAt(rig,time,base);
            String id="actor:"+actor.getId();
            present.add(id);
            update(id,base,settings,world);
        }

        // CAMERA statics share the SAME runtime slot and coordinate model.
        // They are never translated into dummy Blockbuster actor records.
        if(editor.isCameraMode())
            for(CameraSceneLights.Light light:scene.getCameraLights().all())
            {
                if(light==null||!light.enabled)continue;
                String id="camera:"+light.id;
                present.add(id);
                update(id,light.morph,light.morph,
                        BetterLightsWorldState.points(
                                light.x,light.y,light.z,
                                light.targetX,light.targetY,light.targetZ));
            }
        Iterator<Map.Entry<String,Slot>> it=SLOTS.entrySet().iterator();
        while(it.hasNext())
        {
            Map.Entry<String,Slot> entry=it.next();
            if(present.contains(entry.getKey()))continue;
            unregister(entry.getValue());
            it.remove();
        }
    }

    private static void update(String id,NBTTagCompound base,
            NBTTagCompound settings,BetterLightsWorldState world)
    {
        Slot slot=SLOTS.get(id);
        if(base==null||world==null)
        {
            if(slot!=null){unregister(slot);SLOTS.remove(id);}
            return;
        }
        try
        {
            String signature=base.toString();
            if(slot==null)
            {
                slot=new Slot();
                SLOTS.put(id,slot);
            }
            if(!signature.equals(slot.baseSignature))
            {
                unregister(slot);
                slot.morph=MorphManager.INSTANCE.morphFromNBT(base.copy());
                if(slot.morph==null||!slot.morph.getClass().getName()
                        .endsWith(".BetterLightsMorph"))
                    throw new IllegalStateException("Invalid BetterLights morph");
                Object manager=slot.morph.getClass().getMethod(
                        "getValueManager").invoke(slot.morph);
                if(!(manager instanceof ValueSerializer))
                    throw new IllegalStateException("Missing native values");
                // Keep an untouched native baseline. Frame samples MUST NOT
                // accumulate previous values on backward seeks/scrubbing.
                AbstractMorph defaults=MorphManager.INSTANCE.morphFromNBT(
                        base.copy());
                Object baseline=defaults.getClass().getMethod(
                        "getValueManager").invoke(defaults);
                if(!(baseline instanceof ValueSerializer))
                    throw new IllegalStateException("Missing baseline");
                slot.baseline=(ValueSerializer)baseline;
                slot.caster=slot.morph.getClass().getMethod(
                        "getLightcaster").invoke(slot.morph);
                slot.baseSignature=signature;
                slot.registered=false;
            }

            ValueSerializer values=(ValueSerializer)slot.morph.getClass()
                    .getMethod("getValueManager").invoke(slot.morph);
            values.copyValues(slot.baseline);
            if(settings!=null)values.fromNBT(settings);

            // BetterLightsMorph stores its serialized values separately from
            // the actual LightCaster properties; mirror its normal update
            // sequence without invoking its actor or dummy-entity lifecycle.
            invoke(slot.morph,"updateAnimation",
                    new Class<?>[]{float.class},new Object[]{0F});
            invoke(slot.morph,"updateLightcaster",
                    new Class<?>[0],new Object[0]);

            // updateLightcaster resets the cached position to the morph's
            // model matrix (not the editor world point). Overwrite ONLY the
            // same caster's final world coordinates and normalized direction.
            invoke(slot.caster,"pos",
                    new Class<?>[]{float.class,float.class,float.class},
                    new Object[]{(float)world.x,(float)world.y,(float)world.z});
            invoke(slot.caster,"direction",
                    new Class<?>[]{float.class,float.class,float.class},
                    new Object[]{world.directionX,world.directionY,
                            world.directionZ});
            if(!slot.registered)
            {
                manager("addTemporaryLightCaster",activeWorld,slot.caster);
                slot.registered=true;
            }
        }
        catch(Throwable exception)
        {
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][LightEngine] "+
                        "Native BetterLights binding failed: "+exception);
                exception.printStackTrace();
            }
            if(slot!=null){unregister(slot);SLOTS.remove(id);}
        }
    }

    private static Object invoke(Object subject,String name,
            Class<?>[] signature,Object[] args)throws Exception
    {
        if(subject==null)throw new IllegalArgumentException(name+" receiver");
        Class<?> klass=subject.getClass();
        Method method=null;
        while(klass!=null)
        {
            try{method=klass.getDeclaredMethod(name,signature);break;}
            catch(NoSuchMethodException ignored){klass=klass.getSuperclass();}
        }
        if(method==null)throw new NoSuchMethodException(name);
        method.setAccessible(true);
        return method.invoke(subject,args);
    }

    private static void manager(String name,World world,Object caster)
            throws Exception
    {
        Class<?> better=Class.forName("dz.betterlights.BetterLightsMod");
        Object manager=better.getMethod("getLightManager").invoke(null);
        for(Method method:manager.getClass().getMethods())
        {
            Class<?>[] p=method.getParameterTypes();
            if(name.equals(method.getName())&&p.length==3&&
                    p[0].isInstance(world)&&p[1].isInstance(caster)&&
                    p[2]==boolean.class)
            {
                method.invoke(manager,world,caster,false);
                return;
            }
        }
        throw new NoSuchMethodException("BetterLights manager."+name);
    }

    private static void unregister(Slot slot)
    {
        if(slot==null)return;
        if(slot.registered&&activeWorld!=null&&slot.caster!=null)
            try{manager("removeLightCaster",activeWorld,slot.caster);}
            catch(Exception ignored){}
        slot.registered=false;
        slot.caster=null;
        slot.morph=null;
        slot.baseline=null;
        slot.baseSignature=null;
    }

    public static synchronized void reset()
    {
        for(Slot slot:SLOTS.values())unregister(slot);
        SLOTS.clear();
        activeWorld=null;
        activeScene=null;
        activeSceneIndex=-1;
    }

    private BetterLightsWorldEngine(){}
}
