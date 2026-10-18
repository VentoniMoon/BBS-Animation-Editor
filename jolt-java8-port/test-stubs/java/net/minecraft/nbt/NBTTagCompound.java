package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/** Compile-only Minecraft 1.12.2 NBT subset for collider settings smoke. */
public final class NBTTagCompound
{
    private final Map<String,Object> map=new HashMap<String,Object>();
    public void setBoolean(String k,boolean v){map.put(k,Boolean.valueOf(v));}
    public void setString(String k,String v){map.put(k,v);}
    public void setFloat(String k,float v){map.put(k,Float.valueOf(v));}
    public boolean getBoolean(String k){return Boolean.TRUE.equals(map.get(k));}
    public String getString(String k){Object x=map.get(k);return x instanceof String?(String)x:"";}
    public float getFloat(String k){Object x=map.get(k);return x instanceof Number?((Number)x).floatValue():0F;}
    public boolean hasKey(String k){return map.containsKey(k);}
}
