package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Editor-owned static lights are independent of Blockbuster scene actors,
 * and contain no keyframes. Saved in editor.dat with stable IDs.
 *
 * The original BetterLightsMorph NBT remains opaque, preserving every
 * supported Light / Shadows / Flare / Volumetric Fog parameter, including
 * nested settings unknown to this editor version.
 */
public final class CameraSceneLights
{
    public static final class Light
    {
        public final String id;
        public String name;
        public double x,y,z,targetX,targetY,targetZ;
        public boolean enabled=true;
        public NBTTagCompound morph=new NBTTagCompound();

        public Light(String id,String name,double x,double y,double z)
        {
            this.id=id;this.name=name;
            this.x=x;this.y=y;this.z=z;
            this.targetX=x;this.targetY=y;this.targetZ=z+4D;
            this.morph.setString("Name","betterLights");
            this.morph.setBoolean("EnableAlways",true);
        }

        public NBTTagCompound save()
        {
            NBTTagCompound tag=new NBTTagCompound();
            tag.setString("Id",id);
            tag.setString("Label",name);
            tag.setDouble("X",x);tag.setDouble("Y",y);tag.setDouble("Z",z);
            tag.setDouble("TargetX",targetX);
            tag.setDouble("TargetY",targetY);
            tag.setDouble("TargetZ",targetZ);
            tag.setBoolean("Enabled",enabled);
            tag.setTag("Morph",morph.copy());
            return tag;
        }
    }

    private final List<Light> lights=new ArrayList<Light>();
    private String selectedId="";

    public List<Light> all()
    {
        return Collections.unmodifiableList(lights);
    }
    public Light selected()
    {
        for(Light l:lights)if(l.id.equals(selectedId))return l;
        return null;
    }
    public void select(String id)
    {
        selectedId=id==null?"":id;
    }
    public Light add(String name,double x,double y,double z)
    {
        if(lights.size()>=128)return null;
        Light light=new Light(UUID.randomUUID().toString(),
                name==null?"Light "+(lights.size()+1):name,x,y,z);
        lights.add(light);selectedId=light.id;return light;
    }
    public void removeSelected()
    {
        Light chosen=selected();
        if(chosen==null)return;
        lights.remove(chosen);
        selectedId=lights.isEmpty()?"":lights.get(
                Math.max(0,lights.size()-1)).id;
    }
    public void clear()
    {
        lights.clear();selectedId="";
    }
    public NBTTagCompound save()
    {
        NBTTagCompound compound=new NBTTagCompound();
        NBTTagList list=new NBTTagList();
        for(Light light:lights)list.appendTag(light.save());
        compound.setTag("Lights",list);
        compound.setString("Selected",selectedId);
        return compound;
    }
    public void load(NBTTagCompound compound)
    {
        clear();
        if(compound==null)return;
        NBTTagList list=compound.getTagList("Lights",10);
        for(int i=0;i<Math.min(list.tagCount(),128);i++)
        {
            NBTTagCompound tag=list.getCompoundTagAt(i);
            String id=tag.getString("Id");
            if(id.isEmpty())continue;
            Light l=new Light(id,tag.getString("Label"),
                    tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
            l.targetX=tag.hasKey("TargetX")?tag.getDouble("TargetX"):l.x;
            l.targetY=tag.hasKey("TargetY")?tag.getDouble("TargetY"):l.y;
            l.targetZ=tag.hasKey("TargetZ")?tag.getDouble("TargetZ"):l.z+4D;
            l.enabled=!tag.hasKey("Enabled")||tag.getBoolean("Enabled");
            if(tag.hasKey("Morph",10))
            {
                l.morph=tag.getCompoundTag("Morph").copy();
                if(!"betterLights".equals(l.morph.getString("Name")))
                    l.morph.setString("Name","betterLights");
                l.morph.setBoolean("EnableAlways",true);
            }
            lights.add(l);
        }
        select(compound.getString("Selected"));
        if(selected()==null&& !lights.isEmpty())
            selectedId=lights.get(0).id;
    }
}
