package com.google.gson;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class JsonArray extends JsonElement implements Iterable<JsonElement> {
    private final List<JsonElement> values=new ArrayList<JsonElement>();
    public void add(JsonElement item){values.add(item);}
    public Iterator<JsonElement> iterator(){return values.iterator();}
}
