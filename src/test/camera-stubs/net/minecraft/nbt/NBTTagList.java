package net.minecraft.nbt;
import java.util.ArrayList;
public class NBTTagList
{
    private final ArrayList<NBTTagCompound> list=new ArrayList<NBTTagCompound>();
    public void appendTag(NBTTagCompound tag){list.add(tag);}
    public int tagCount(){return list.size();}
    public NBTTagCompound getCompoundTagAt(int n){return list.get(n);}
}
