package com.google.gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
public class JsonObject extends JsonElement {
    private final Map<String,JsonElement> entries=new LinkedHashMap<String,JsonElement>();
    public void addProperty(String key,String value){add(key,new JsonElement(value));}
    public void addProperty(String key,Number value){add(key,new JsonElement(value));}
    public void addProperty(String key,Boolean value){add(key,new JsonElement(value));}
    public void add(String key,JsonElement value){entries.put(key,value);}
    public JsonElement get(String key){return entries.get(key);}
    public boolean has(String key){return entries.containsKey(key);}
    public JsonArray getAsJsonArray(String key){return entries.get(key).getAsJsonArray();}
    public JsonObject getAsJsonObject(String key){return entries.get(key).getAsJsonObject();}
    public Set<Map.Entry<String,JsonElement>> entrySet(){return entries.entrySet();}
}
