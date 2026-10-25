package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
public final class NBTTagCompound {
    private final Map<String,Object> data=new HashMap<String,Object>();
    public void setString(String k,String v){data.put(k,v);}
    public String getString(String k){Object v=data.get(k);return v==null?"":(String)v;}
    public void setDouble(String k,double v){data.put(k,v);}
    public double getDouble(String k){Object v=data.get(k);return v==null?0D:((Number)v).doubleValue();}
    public void setBoolean(String k,boolean v){data.put(k,v);}
    public boolean getBoolean(String k){Object v=data.get(k);return v!=null&&(Boolean)v;}
    public void setTag(String k,Object v){data.put(k,v);}
    public NBTTagCompound getCompoundTag(String k){Object v=data.get(k);return v instanceof NBTTagCompound?(NBTTagCompound)v:new NBTTagCompound();}
    public NBTTagList getTagList(String k,int type){Object v=data.get(k);return v instanceof NBTTagList?(NBTTagList)v:new NBTTagList();}
    public boolean hasKey(String k){return data.containsKey(k);}
    public boolean hasKey(String k,int type){return data.containsKey(k)&&((type==10&&data.get(k) instanceof NBTTagCompound)||(type==9&&data.get(k) instanceof NBTTagList));}
    public NBTTagCompound copy(){
        NBTTagCompound c=new NBTTagCompound();
        for(Map.Entry<String,Object> e:data.entrySet()){
            Object v=e.getValue();
            c.data.put(e.getKey(),v instanceof NBTTagCompound?((NBTTagCompound)v).copy():v instanceof NBTTagList?((NBTTagList)v).copy():v);
        }
        return c;
    }
}
