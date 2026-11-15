package com.example.examplemod;
import net.minecraft.nbt.NBTTagCompound;
public final class CharacterKey {
 public enum Type { CUSTOM }
 private final int frame;
 private NBTTagCompound data=new NBTTagCompound();
 public CharacterKey(int frame,Type type){this.frame=frame;}
 public int getFrame(){return frame;}
 public boolean hasData(String s){return data.hasKey(s);}
 public NBTTagCompound getData(){return data.copy();}
 public void setData(NBTTagCompound t){data=t.copy();}
}