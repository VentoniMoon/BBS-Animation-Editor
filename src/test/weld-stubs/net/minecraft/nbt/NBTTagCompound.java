package net.minecraft.nbt;
import java.util.HashMap;
import java.util.Map;
public class NBTTagCompound {
    private final Map<String,Boolean> values=new HashMap<String,Boolean>();
    public void setBoolean(String key,boolean value){values.put(key,value);}
    public boolean getBoolean(String key){Boolean b=values.get(key);return b!=null&&b;}
    public void removeTag(String key){values.remove(key);}
}
