package com.google.gson;
public class JsonElement {
    private final Object primitive;
    public JsonElement(){this.primitive=null;}
    public JsonElement(Object value){this.primitive=value;}
    public String getAsString(){return String.valueOf(primitive);}
    public int getAsInt(){return Integer.parseInt(getAsString());}
    public float getAsFloat(){return Float.parseFloat(getAsString());}
    public double getAsDouble(){return Double.parseDouble(getAsString());}
    public boolean getAsBoolean(){return Boolean.parseBoolean(getAsString());}
    public JsonObject getAsJsonObject(){return (JsonObject)this;}
    public JsonArray getAsJsonArray(){return (JsonArray)this;}
}
