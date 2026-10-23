package com.example.examplemod;

import java.util.Collections;
import java.util.List;

class AnimationEditorScreen extends net.minecraft.client.gui.GuiScreen
{
  final EditorSceneState scene=new EditorSceneState();
  EditorSceneState getSceneState(){return scene;}
  int getCurrentFrame(){return 4;}
}
final class EditorSceneState
{
  final CameraClipDocument document=new CameraClipDocument();
  CameraClipDocument getCameraClips(){return document;}
}
final class CameraClipDocument
{
  static final class Clip {}
  List<Clip> clips(){return Collections.emptyList();}
}
final class CameraFilmCompositor
{
  static int nativeCalls;
  static boolean failNext;
  static void compositeNative(net.minecraft.client.Minecraft mc,
      net.minecraft.client.shader.Framebuffer film,
      List<CameraClipDocument.Clip> clips,int tick)
  {
    nativeCalls++;
    if(tick!=4)throw new AssertionError("Wrong film scene tick");
    if(failNext)
    {
      failNext=false;
      throw new IllegalStateException("Unsupported VIDEO in offline export");
    }
  }
}
final class BbsFsWorldFrameCapture
{
  static boolean ready=true;
  static boolean isNativeFrameReady(){return ready;}
}
final class BbsFsNativeActorPass
{
  static boolean didRenderThisFrame(){return true;}
}
