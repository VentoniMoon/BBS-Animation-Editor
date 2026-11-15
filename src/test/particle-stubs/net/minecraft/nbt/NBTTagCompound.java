package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
public class NBTTagCompound extends NBTBase
{
    private final Map<String,NBTBase> data=new HashMap<String,NBTBase>();
    static class Value extends NBTBase
    {
        final Object value;Value(Object v){value=v;}
        public NBTBase copy()
        {return new Value(value instanceof int[]?((int[])value).clone():value);}
    }
    public void setTag(String k,NBTBase value){data.put(k,value);}
    public NBTBase getTag(String k){return data.get(k);}
    public Set<String> getKeySet(){return data.keySet();}
    public void setString(String k,String v){setTag(k,new Value(v));}
    public String getString(String k)
    {Object x=value(k);return x==null?"":String.valueOf(x);}
    public void setInteger(String k,int v){setTag(k,new Value(Integer.valueOf(v)));}
    public int getInteger(String k)
    {Object x=value(k);return x instanceof Number?((Number)x).intValue():0;}
    public void setDouble(String k,double v){setTag(k,new Value(Double.valueOf(v)));}
    public double getDouble(String k)
    {Object x=value(k);return x instanceof Number?((Number)x).doubleValue():0D;}
    public void setBoolean(String k,boolean v){setTag(k,new Value(Boolean.valueOf(v)));}
    public boolean getBoolean(String k)
    {return Boolean.TRUE.equals(value(k));}
    public void setIntArray(String k,int[] v){setTag(k,new Value(v.clone()));}
    public int[] getIntArray(String k)
    {Object x=value(k);return x instanceof int[]?((int[])x).clone():new int[0];}
    public boolean hasKey(String k){return data.containsKey(k);}
    public boolean hasKey(String k,int tag)
    {return hasKey(k)&&(!(tag==10)||data.get(k) instanceof NBTTagCompound);}
    public NBTTagCompound getCompoundTag(String k)
    {NBTBase x=data.get(k);return x instanceof NBTTagCompound?
            (NBTTagCompound)x:new NBTTagCompound();}
    public NBTTagList getTagList(String k,int ignored)
    {NBTBase x=data.get(k);return x instanceof NBTTagList?
            (NBTTagList)x:new NBTTagList();}
    private Object value(String k)
    {NBTBase x=data.get(k);return x instanceof Value?((Value)x).value:null;}
    public NBTTagCompound copy()
    {
        NBTTagCompound c=new NBTTagCompound();
        for(Map.Entry<String,NBTBase> e:data.entrySet())
            c.setTag(e.getKey(),e.getValue().copy());
        return c;
    }
}
