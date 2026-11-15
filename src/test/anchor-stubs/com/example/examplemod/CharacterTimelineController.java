package com.example.examplemod;
import java.util.ArrayList;import java.util.List;
public final class CharacterTimelineController {
 private final List<CharacterTrack> tracks=new ArrayList<CharacterTrack>();
 public CharacterTrack ensureMainTrack(){
  if(tracks.isEmpty())tracks.add(new CharacterTrack());return tracks.get(0);
 }
 public List<CharacterTrack> getTracks(){return tracks;}
}