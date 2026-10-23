package com.example.examplemod;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

final class CameraClipDocument
{
    enum Kind { AUDIO, VIDEO }
    static final class Clip
    {
        Kind kind;
        int tick,duration;
        boolean enabled=true;
        Map<String,String> settings=new LinkedHashMap<String,String>();
        Clip(Kind kind,int tick,int duration)
        {this.kind=kind;this.tick=tick;this.duration=duration;}
    }
}
final class CameraImageCache
{
    static File resolve(String path)
    {return path==null||path.trim().isEmpty()?null:new File(path);}
}
