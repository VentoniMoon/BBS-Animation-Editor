package net.minecraft.nbt;
import java.util.ArrayList;
import java.util.List;
public final class NBTTagList{
    private final List<NBTTagCompound> entries=new ArrayList<NBTTagCompound>();
    public void appendTag(NBTTagCompound c){entries.add(c);}
    public int tagCount(){return entries.size();}
    public NBTTagCompound getCompoundTagAt(int i){return entries.get(i);}
    public NBTTagList copy(){
        NBTTagList list=new NBTTagList();
        for(NBTTagCompound c:entries)list.appendTag(c.copy());
        return list;
    }
}
