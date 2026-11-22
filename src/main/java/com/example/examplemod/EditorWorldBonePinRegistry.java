package com.example.examplemod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-level world pin data independent of a specific actor renderer.
 *
 * Locking remembers an absolute world anchor. solve() computes joint
 * PIVOT positions without any scaling; rig-specific local-Euler/quaternion
 * writing is a separate adapter and must not silently translate the tip
 * or stretch a leg. That adapter is not yet enabled for native Emoticons,
 * Chameleon or Blockbuster meshes.
 */
public final class EditorWorldBonePinRegistry
{
    public static final class Pin
    {
        public final String actorId,boneName;
        public final float[] world;
        private Pin(String actor,String bone,float[] point)
        {
            actorId=actor;boneName=bone;world=point.clone();
        }
    }

    private final Map<String,Pin> pins=
            new LinkedHashMap<String,Pin>();

    private static String key(String actor,String bone)
    {
        if(actor==null||actor.trim().isEmpty()||
                bone==null||bone.trim().isEmpty())
            throw new IllegalArgumentException("World pin needs actor and bone");
        return actor.length()+":"+actor+":"+bone;
    }

    public boolean pin(String actor,String bone,float[] world)
    {
        String id=key(actor,bone);
        if(world==null||world.length!=3)
            throw new IllegalArgumentException("World pin needs XYZ");
        for(float c:world)if(!Float.isFinite(c))
            throw new IllegalArgumentException("Non-finite world anchor");
        if(pins.size()>=128&&!pins.containsKey(id))
            return false;
        pins.put(id,new Pin(actor,bone,world));
        return true;
    }

    public boolean unpin(String actor,String bone)
    {
        return pins.remove(key(actor,bone))!=null;
    }

    public Pin find(String actor,String bone)
    {
        Pin value=pins.get(key(actor,bone));
        return value==null?null:new Pin(value.actorId,value.boneName,
                value.world);
    }

    public EditorWorldBoneIK.Result solve(String actor,String bone,
            float[][] worldJoints)
    {
        Pin pin=find(actor,bone);
        return pin==null?null:EditorWorldBoneIK.solve(
                worldJoints,pin.world,32,.0005F);
    }

    public int size(){return pins.size();}
    public void clear(){pins.clear();}
    public Map<String,Pin> snapshot()
    {
        Map<String,Pin> out=new LinkedHashMap<String,Pin>();
        for(Map.Entry<String,Pin> e:pins.entrySet())
            out.put(e.getKey(),find(e.getValue().actorId,
                    e.getValue().boneName));
        return Collections.unmodifiableMap(out);
    }
}
