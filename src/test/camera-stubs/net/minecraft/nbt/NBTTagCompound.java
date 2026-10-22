package net.minecraft.nbt;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
public class NBTTagCompound
{
    private final Map<String,Object> content=new LinkedHashMap<String,Object>();
    public void setString(String k,String v){content.put(k,v);}
    public void setInteger(String k,int v){content.put(k,v);}
    public void setDouble(String k,double v){content.put(k,v);}
    public void setFloat(String k,float v){content.put(k,v);}
    public void setBoolean(String k,boolean v){content.put(k,v);}
    public void setTag(String k,Object v){content.put(k,v);}
    public String getString(String k){Object v=content.get(k);return v instanceof String?(String)v:"";}
    public int getInteger(String k){Object v=content.get(k);return v instanceof Number?((Number)v).intValue():0;}
    public double getDouble(String k){Object v=content.get(k);return v instanceof Number?((Number)v).doubleValue():0;}
    public float getFloat(String k){Object v=content.get(k);return v instanceof Number?((Number)v).floatValue():0;}
    public boolean getBoolean(String k){return Boolean.TRUE.equals(content.get(k));}
    public boolean hasKey(String k){return content.containsKey(k);}
    public boolean hasKey(String k,int id){return id==99?content.get(k) instanceof Number:content.containsKey(k);}
    public NBTTagCompound getCompoundTag(String k){return content.get(k) instanceof NBTTagCompound?(NBTTagCompound)content.get(k):new NBTTagCompound();}
    public NBTTagList getTagList(String k,int type){return content.get(k) instanceof NBTTagList?(NBTTagList)content.get(k):new NBTTagList();}
    public Set<String> getKeySet(){return content.keySet();}
}
