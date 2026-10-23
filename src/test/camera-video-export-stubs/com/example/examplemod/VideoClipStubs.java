package com.example.examplemod;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
final class CameraImageCache {
  static File resolve(String source) {
    return source==null?null:new File(source);
  }
}
final class CameraClipDocument {
  enum Kind {VIDEO,IMAGE,AUDIO}
  static final class Clip {
    Kind kind;
    int tick,duration;
    boolean enabled=true;
    final Map<String,String> settings=new LinkedHashMap<String,String>();
    Clip(Kind kind,int tick,int duration) {
      this.kind=kind;this.tick=tick;this.duration=duration;
    }
    boolean contains(int frame) {
      return frame>=tick&&frame<tick+duration;
    }
  }
}