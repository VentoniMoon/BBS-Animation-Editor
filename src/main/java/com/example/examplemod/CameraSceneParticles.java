package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Scene-owned STATIC particle emitters; no actor, record or keyframe.
 * NBT preserves all ParticleMorph data, including custom Morph mode fields.
 */
public final class CameraSceneParticles
{
    public static final class Source
    {
        public final String id;
        public String name;
        public double x,y,z,yaw,pitch,roll;
        public boolean enabled=true;
        public NBTTagCompound morph=ParticleEmitterConfig.defaults();

        public Source(String id,String name,double x,double y,double z)
        {
            this.id=id;this.name=name;this.x=x;this.y=y;this.z=z;
        }
        public NBTTagCompound save()
        {
            NBTTagCompound tag=new NBTTagCompound();
            tag.setString("Id",id);
            tag.setString("Label",name);
            tag.setDouble("X",x);tag.setDouble("Y",y);tag.setDouble("Z",z);
            tag.setDouble("Yaw",yaw);
            tag.setDouble("Pitch",pitch);
            tag.setDouble("Roll",roll);
            tag.setBoolean("Enabled",enabled);
            tag.setTag("Morph",morph.copy());
            return tag;
        }
    }
    private final List<Source> sources=new ArrayList<Source>();
    private String selectedId="";
    public List<Source> all(){return Collections.unmodifiableList(sources);}
    public Source selected()
    {
        for(Source s:sources)
            if(s.id.equals(selectedId))return s;
        return null;
    }
    public void select(String id){selectedId=id==null?"":id;}
    public Source add(String name,double x,double y,double z)
    {
        if(sources.size()>=128)return null;
        Source s=new Source(UUID.randomUUID().toString(),
                name==null?"Particle "+(sources.size()+1):name,x,y,z);
        sources.add(s);selectedId=s.id;return s;
    }
    public void removeSelected()
    {
        Source old=selected();
        if(old==null)return;
        sources.remove(old);
        selectedId=sources.isEmpty()?"":sources.get(
                Math.max(0,sources.size()-1)).id;
    }
    public void clear(){sources.clear();selectedId="";}
    public NBTTagCompound save()
    {
        NBTTagCompound result=new NBTTagCompound();
        NBTTagList list=new NBTTagList();
        for(Source s:sources)list.appendTag(s.save());
        result.setTag("Sources",list);
        result.setString("Selected",selectedId);
        return result;
    }
    public void load(NBTTagCompound saved)
    {
        clear();
        if(saved==null)return;
        NBTTagList list=saved.getTagList("Sources",10);
        for(int i=0;i<Math.min(list.tagCount(),128);i++)
        {
            NBTTagCompound tag=list.getCompoundTagAt(i);
            String id=tag.getString("Id");
            if(id.isEmpty())continue;
            Source s=new Source(id,tag.getString("Label"),
                    tag.getDouble("X"),tag.getDouble("Y"),
                    tag.getDouble("Z"));
            s.yaw=tag.getDouble("Yaw");
            s.pitch=tag.getDouble("Pitch");
            s.roll=tag.getDouble("Roll");
            s.enabled=!tag.hasKey("Enabled")||
                    tag.getBoolean("Enabled");
            if(tag.hasKey("Morph",10))
                s.morph=ParticleEmitterConfig.normalize(
                        tag.getCompoundTag("Morph"));
            sources.add(s);
        }
        select(saved.getString("Selected"));
        if(selected()==null&&!sources.isEmpty())
            selectedId=sources.get(0).id;
    }
}
