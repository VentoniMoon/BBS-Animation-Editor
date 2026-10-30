package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Scene-owned rectangular mirror surfaces. Unlike camera clips these objects
 * persist throughout the scene, have no timeline keys and never alter blocks.
 */
public final class CameraSceneMirrors
{
    public static final class Mirror
    {
        public final String id;
        public String name;
        public boolean enabled=true;
        public double x,y,z,yaw,width=2D,height=3D;

        public Mirror(String id,String name,double x,double y,double z,
                double yaw)
        {
            this.id=id;
            this.name=name;
            this.x=x;this.y=y;this.z=z;this.yaw=yaw;
        }
        public NBTTagCompound save()
        {
            NBTTagCompound t=new NBTTagCompound();
            t.setString("Id",id);
            t.setString("Name",name==null?"Mirror":name);
            t.setBoolean("Enabled",enabled);
            t.setDouble("X",x);t.setDouble("Y",y);t.setDouble("Z",z);
            t.setDouble("Yaw",yaw);
            t.setDouble("Width",width);
            t.setDouble("Height",height);
            return t;
        }
    }

    private final List<Mirror> mirrors=new ArrayList<Mirror>();
    private String selectedId="";
    public List<Mirror> all(){return Collections.unmodifiableList(mirrors);}
    public Mirror selected()
    {
        for(Mirror m:mirrors)if(m.id.equals(selectedId))return m;
        return null;
    }
    public void select(String id){selectedId=id==null?"":id;}
    public Mirror add(double x,double y,double z,double yaw)
    {
        if(mirrors.size()>=32)return null;
        Mirror m=new Mirror(UUID.randomUUID().toString(),
                "Mirror "+(mirrors.size()+1),x,y,z,yaw);
        mirrors.add(m);selectedId=m.id;
        return m;
    }
    public void removeSelected()
    {
        Mirror m=selected();
        if(m==null)return;
        int at=mirrors.indexOf(m);
        mirrors.remove(m);
        selectedId=mirrors.isEmpty()?"":mirrors.get(
                Math.min(at,mirrors.size()-1)).id;
    }
    public void clear(){mirrors.clear();selectedId="";}
    public NBTTagCompound save()
    {
        NBTTagCompound root=new NBTTagCompound();
        NBTTagList list=new NBTTagList();
        for(Mirror m:mirrors)list.appendTag(m.save());
        root.setTag("Mirrors",list);
        root.setString("Selected",selectedId);
        return root;
    }
    public void load(NBTTagCompound root)
    {
        clear();
        if(root==null)return;
        NBTTagList list=root.getTagList("Mirrors",10);
        for(int i=0;i<list.tagCount()&&mirrors.size()<32;i++)
        {
            NBTTagCompound t=list.getCompoundTagAt(i);
            String id=t.getString("Id");
            if(id.isEmpty())continue;
            boolean duplicate=false;
            for(Mirror old:mirrors)if(old.id.equals(id))duplicate=true;
            if(duplicate)continue;
            double x=t.getDouble("X"),y=t.getDouble("Y"),z=t.getDouble("Z");
            double yaw=t.getDouble("Yaw");
            double w=t.getDouble("Width"),h=t.getDouble("Height");
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(z)||!Double.isFinite(yaw)||
                    !Double.isFinite(w)||!Double.isFinite(h))continue;
            Mirror m=new Mirror(id,t.getString("Name"),x,y,z,yaw);
            m.enabled=t.hasKey("Enabled")?t.getBoolean("Enabled"):true;
            m.width=Math.max(0.25D,Math.min(64D,w));
            m.height=Math.max(0.25D,Math.min(64D,h));
            mirrors.add(m);
        }
        selectedId=root.getString("Selected");
        if(selected()==null&&!mirrors.isEmpty())
            selectedId=mirrors.get(0).id;
    }
}
