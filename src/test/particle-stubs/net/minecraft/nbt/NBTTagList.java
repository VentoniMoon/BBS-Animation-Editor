package net.minecraft.nbt;
import java.util.ArrayList;
import java.util.List;
public class NBTTagList extends NBTBase
{
    private final List<NBTTagCompound> tags=new ArrayList<NBTTagCompound>();
    public void appendTag(NBTTagCompound tag){tags.add(tag);}
    public int tagCount(){return tags.size();}
    public NBTTagCompound getCompoundTagAt(int at){return tags.get(at);}
    public NBTTagList copy()
    {
        NBTTagList n=new NBTTagList();
        for(NBTTagCompound t:tags)n.appendTag(t.copy());
        return n;
    }
}
