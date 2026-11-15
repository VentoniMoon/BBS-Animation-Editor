package com.example.examplemod;
import java.util.ArrayList;import java.util.List;
public final class CharacterTrack {
 private final List<CharacterKey> keys=new ArrayList<CharacterKey>();
 public List<CharacterKey> getKeys(){return keys;}
 public CharacterKey getKeyAtFrame(int frame){
  for(CharacterKey k:keys)if(k.getFrame()==frame)return k;return null;
 }
 public void addKey(CharacterKey k){keys.add(k);}
}