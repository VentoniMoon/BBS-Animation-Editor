package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Optional editor.dat section for absolute bone anchors.
 * Never touches Blockbuster Record .dat files or the source Morph NBT.
 * Invalid entries are skipped instead of breaking an entire old scene.
 */
public final class EditorWorldBonePinPersistence
{
    private static final int MAX_PINS=128;

    private EditorWorldBonePinPersistence(){}

    public static NBTTagCompound write(EditorWorldBonePinRegistry registry)
    {
        NBTTagCompound out=new NBTTagCompound();
        NBTTagList list=new NBTTagList();
        if(registry!=null)
        {
            for(EditorWorldBonePinRegistry.Pin pin:
                    registry.snapshot().values())
            {
                if(pin==null||pin.world==null||pin.world.length!=3)
                    continue;
                NBTTagCompound item=new NBTTagCompound();
                item.setString("ActorId",pin.actorId);
                item.setString("Bone",pin.boneName);
                item.setFloat("X",pin.world[0]);
                item.setFloat("Y",pin.world[1]);
                item.setFloat("Z",pin.world[2]);
                list.appendTag(item);
            }
        }
        out.setTag("Pins",list);
        return out;
    }

    public static void read(EditorWorldBonePinRegistry registry,
            NBTTagCompound tag)
    {
        if(registry==null)return;
        registry.clear();
        if(tag==null||!tag.hasKey("Pins",9))return;
        NBTTagList list=tag.getTagList("Pins",10);
        for(int i=0;i<Math.min(MAX_PINS,list.tagCount());i++)
        {
            NBTTagCompound item=list.getCompoundTagAt(i);
            String actor=item.getString("ActorId");
            String bone=item.getString("Bone");
            if(actor.isEmpty()||bone.isEmpty()||
                    actor.length()>256||bone.length()>256||
                    !item.hasKey("X",5)||!item.hasKey("Y",5)||
                    !item.hasKey("Z",5))
                continue;
            float[] world=new float[]{item.getFloat("X"),
                    item.getFloat("Y"),item.getFloat("Z")};
            boolean valid=true;
            for(float v:world)if(!Float.isFinite(v))valid=false;
            if(valid)
                registry.pin(actor,bone,world);
        }
    }
}
