package com.example.examplemod;

import java.util.ArrayList;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.Minecraft;

/** First CAMERA workspace surface using the existing Ocean/editor widgets. */
public final class CameraWorkspace
{
    /** Bound to EditorSceneState, persisted within <scene>.editor.dat. */
    public CameraClipDocument document=new CameraClipDocument();
    private EditorSceneState activeState;
    private final CameraAudioClips soundtrack=new CameraAudioClips();

    public void syncAudio(int frame,boolean playing)
    {
        soundtrack.update(document.clips(),frame,playing);
    }
    public void pauseAudio(){soundtrack.pause();}
    public void dispose()
    {
        soundtrack.clear();
        CameraVideoFrames.clear();
        CameraImageCache.clear();
    }
    private boolean shot;
    /** Read-only clip camera preview in Character, Pose and Body Parts. */
    private boolean crossModeShot;
    /** Explicit opt-in to writing the navigated FREE pose into clip keys. */
    private boolean recordingPreview;
    private PreviewCameraPose lastNavigationPose;
    private CameraClipDocument.Clip trackedCameraClip;
    private CameraClipDocument.Key trackedCameraKey;
    private boolean navigationDirty;
    private int navigationIdleTicks;

    /**
     * In EDIT VIEW moving/rotating the free camera is a third way to
     * author the selected View/Dolly pose (or an explicitly selected key).
     * The baseline is reset on selection changes to prevent accidental jumps.
     */
    public void captureNavigationPose(PreviewCameraPose pose)
    {
        if(shot||!recordingPreview||pose==null)
        {
            lastNavigationPose=null;return;
        }
        CameraClipDocument.Clip clip=document.selected();
        CameraClipDocument.Key key=selectedKey!=null&&keyOwner==clip
                ?selectedKey:
                clip!=null&&(clip.kind==CameraClipDocument.Kind.IDLE
                        ||clip.kind==CameraClipDocument.Kind.DOLLY)
                        &&!clip.keys.isEmpty()?clip.keys.get(0):null;
        if(clip!=trackedCameraClip||key!=trackedCameraKey)
        {
            if(navigationDirty)saveScene();
            recordingPreview=false;
            trackedCameraClip=null;trackedCameraKey=null;
            lastNavigationPose=null;navigationDirty=false;
            navigationIdleTicks=0;return;
        }
        if(lastNavigationPose==null){lastNavigationPose=pose;return;}
        boolean moved=Math.abs(pose.x-lastNavigationPose.x)>1E-5D||
                Math.abs(pose.y-lastNavigationPose.y)>1E-5D||
                Math.abs(pose.z-lastNavigationPose.z)>1E-5D||
                Math.abs(pose.yaw-lastNavigationPose.yaw)>1E-4F||
                Math.abs(pose.pitch-lastNavigationPose.pitch)>1E-4F||
                Math.abs(pose.roll-lastNavigationPose.roll)>1E-4F||
                Math.abs(pose.fov-lastNavigationPose.fov)>1E-4F;
        lastNavigationPose=pose;
        if(moved&&key!=null)
        {
            key.x=pose.x;key.y=pose.y;key.z=pose.z;
            key.yaw=pose.yaw;key.pitch=pose.pitch;
            key.roll=pose.roll;key.fov=pose.fov;
            navigationDirty=true;navigationIdleTicks=0;
        }
        else if(navigationDirty&&++navigationIdleTicks>=5)
        {
            saveScene();navigationDirty=false;
        }
    }

    public PreviewCameraPose sample(PreviewCameraPose editorPose,int sceneFrame,
            CameraClipDocument.TargetResolver resolver,boolean inCameraMode)
    {
        return (inCameraMode?shot:crossModeShot)
                ?document.sample(sceneFrame,editorPose,resolver):editorPose;
    }
    /**
     * A rendered MP4 must always use the authored film camera. The FREE/SHOT
     * toolbar toggle is an editor navigation aid and may not decide which
     * camera is encoded in the output video.
     */
    public PreviewCameraPose sampleForExport(PreviewCameraPose free,
            int frame,CameraClipDocument.TargetResolver resolver)
    {
        return document.sample(frame,free,resolver);
    }

    public boolean shot(){return shot;}
    public void toggleShotView()
    {
        shot=!shot;
        if(shot&&recordingPreview)stopPreviewRecording();
    }
    public void togglePreviewRecordingFromToolbar(AnimationEditorScreen screen,int frame)
    {
        togglePreviewRecording(screen,frame);
    }
    public boolean crossModeShot(){return crossModeShot;}
    public boolean recordingPreview(){return recordingPreview;}
    public void toggleCrossModeShot(){crossModeShot=!crossModeShot;}
    private CameraClipDocument.Key editablePoseKey(int frame,
            PreviewCameraPose free)
    {
        CameraClipDocument.Clip clip=document.selected();
        if(clip==null)return null;
        if(clip.kind!=CameraClipDocument.Kind.IDLE
                &&clip.kind!=CameraClipDocument.Kind.DOLLY
                &&clip.kind!=CameraClipDocument.Kind.PATH
                &&clip.kind!=CameraClipDocument.Kind.KEYFRAME)return null;
        if(selectedKey!=null&&keyOwner==clip)return selectedKey;
        if((clip.kind==CameraClipDocument.Kind.IDLE
                ||clip.kind==CameraClipDocument.Kind.DOLLY)
                &&!clip.keys.isEmpty())return clip.keys.get(0);
        if(!clip.kind.keyed||!clip.contains(frame))return null;
        // Path/Keyframe author the key at the current scene frame.
        document.keyAt(clip,frame,free);
        int relative=frame-clip.tick;
        for(CameraClipDocument.Key k:clip.keys)if(k.tick==relative)
        {
            selectedKey=k;keyOwner=clip;saveScene();return k;
        }
        return null;
    }
    /**
     * Called when changing editor mode. Authoring must not silently continue
     * after the actor/preview controls have a different meaning.
     */
    public void stopPreviewRecording()
    {
        if(!recordingPreview&&!navigationDirty)return;
        if(navigationDirty)saveScene();
        recordingPreview=false;navigationDirty=false;
        lastNavigationPose=null;trackedCameraClip=null;trackedCameraKey=null;
    }

    private void togglePreviewRecording(AnimationEditorScreen screen,int frame)
    {
        if(recordingPreview)
        {
            stopPreviewRecording();
            return;
        }
        CameraClipDocument.Key key=editablePoseKey(frame,
                screen.getCameraWorkspacePose());
        if(key==null)
        {
            lastError="Select View/Dolly or a Path/Keyframe clip first";
            return;
        }
        shot=false;
        recordingPreview=true;
        trackedCameraClip=document.selected();
        trackedCameraKey=key;
        screen.setCameraWorkspaceEditorPose(key);
        lastNavigationPose=screen.getCameraWorkspacePose();
        navigationDirty=false;navigationIdleTicks=0;
        lastError=null;
    }
    private boolean palette;
    private String activeSceneId;
    private File activeSceneFile;
    private String lastError;
    private final List<String> presetNames=new ArrayList<String>();
    private String savePresetName;
    private GuiTextField editField;
    private CameraClipDocument.Clip editClip;
    private String editKey;
    private boolean namingPreset;

    private void startTextField(AnimationEditorScreen screen,
            String initial,boolean preset,String key,CameraClipDocument.Clip clip)
    {
        namingPreset=preset;
        editClip=clip;
        editKey=key;
        keyField=null;
        int x=preset?Math.max(12,screen.width/2-100):right+115;
        int y=preset?screen.height/2:Math.min(top-35,158+16*(clip==null?0:
                new ArrayList<String>(clip.settings.keySet()).indexOf(key)));
        editField=new GuiTextField(42,Minecraft.getMinecraft().fontRenderer,x,y,
                preset?200:Math.max(80,screen.width-right-124),18);
        editField.setMaxStringLength(255);
        editField.setText(initial==null?"":initial);
        editField.setFocused(true);
    }
    public boolean keyTyped(char typedChar,int keyCode)
    {
        if(editField==null)
        {
            if(keyCode==1&&choosingMedia)
            {choosingMedia=false;return true;}
            if(keyCode==1&&inspector.isInterpolationOpen())
            {inspector.closeInterpolation();return true;}
            if(keyCode==1&&(palette||contextClip!=null))
            {
                palette=false;contextClip=null;return true;
            }
            return false;
        }
        if(keyCode==1){editField=null;return true;}
        if(keyCode==28||keyCode==156)
        {
            String value=editField.getText();
            if(namingPreset)saveSelectedPreset(value);
            else if(keyField!=null&&selectedKey!=null)
            {
                applyKeyField(keyField,value);
            }
            else if(editClip!=null&&editKey!=null)
            {
                if(!applyClipField(editClip,editKey,value))
                    lastError="Invalid clip field: "+editKey;
                else saveScene();
            }
            editField=null;editKey=null;keyField=null;return true;
        }
        editField.textboxKeyTyped(typedChar,keyCode);
        return true;
    }
    private static Double finiteDouble(String raw)
    {
        try
        {
            double d=Double.parseDouble(raw.trim());
            return Double.isFinite(d)?d:null;
        }
        catch(RuntimeException invalid){return null;}
    }
    private boolean applyClipField(CameraClipDocument.Clip clip,
            String key,String value)
    {
        if(clip==null)return false;
        if("@enabled".equals(key))
        {
            clip.enabled=Boolean.parseBoolean(value);return true;
        }
        if("@title".equals(key))
        {
            if(value==null||value.trim().isEmpty())return false;
            clip.title=value.trim();return true;
        }
        if("@start".equals(key)||"@duration".equals(key)||"@layer".equals(key))
        {
            Double n=finiteDouble(value);
            if(n==null||n<0D||n>Integer.MAX_VALUE)return false;
            int number=(int)Math.round(n);
            int tick=clip.tick,duration=clip.duration,layer=clip.layer;
            if("@start".equals(key))clip.tick=number;
            if("@duration".equals(key))clip.duration=Math.max(1,number);
            if("@layer".equals(key))clip.layer=number;
            if(document.overlaps(clip,clip))
            {
                clip.tick=tick;clip.duration=duration;clip.layer=layer;
                return false;
            }
            return true;
        }
        // The BBS-style inspector exposes X/Y/Z as independently editable
        // controls, while the persisted BBS parameter is still a CSV tuple.
        int componentAt=key.lastIndexOf('#');
        if(componentAt>0)
        {
            String parameter=key.substring(0,componentAt);
            if(!clip.settings.containsKey(parameter))return false;
            int index;
            try{index=Integer.parseInt(key.substring(componentAt+1));}
            catch(NumberFormatException invalid){return false;}
            String[] parts=clip.settings.get(parameter).split(",",-1);
            Double parsed=finiteDouble(value);
            if(index<0||index>=parts.length||parsed==null)return false;
            parts[index]=parsed.toString();
            StringBuilder rebuilt=new StringBuilder();
            for(int i=0;i<parts.length;i++)
            {
                if(i>0)rebuilt.append(',');
                rebuilt.append(parts[i].trim());
            }
            clip.settings.put(parameter,rebuilt.toString());
            return true;
        }
        if(clip.settings.containsKey(key))
        {
            if(!value.equalsIgnoreCase("true")
                    &&!value.equalsIgnoreCase("false")
                    &&("true".equalsIgnoreCase(clip.settings.get(key))
                        ||"false".equalsIgnoreCase(clip.settings.get(key))))
                return false;
            clip.settings.put(key,value);return true;
        }
        return false;
    }
    private boolean applyKeyField(String name,String value)
    {
        if(selectedKey==null||keyOwner==null)return false;
        if("interpolation".equals(name))
        {
            selectedKey.interpolation=value.trim().toLowerCase(
                    java.util.Locale.ROOT);
            saveScene();return true;
        }
        Double parsed=finiteDouble(value);
        if(parsed==null)return false;
        double n=parsed;
        if(name.startsWith("media."))
        {
            if(!CameraMediaAnimation.supports(keyOwner))return false;
            String channel=name.substring(6);
            boolean allowed=false;
            for(String field:CameraMediaAnimation.FIELDS)
                if(field.equals(channel)){allowed=true;break;}
            if(!allowed||("opacity".equals(channel)&&(n<0D||n>1D))||
                    (("width".equals(channel)||"height".equals(channel))&&
                            n<=0D))return false;
            selectedKey.media.put(channel,n);
            saveScene();return true;
        }
        if("mask".equals(name))
        {
            if(n<0D||n>127D)return false;
            selectedKey.mask=(int)Math.round(n);
            saveScene();return true;
        }
        if("tick".equals(name))
        {
            int at=(int)Math.round(n);
            if(at<0||at>=keyOwner.duration)return false;
            for(CameraClipDocument.Key other:keyOwner.keys)
                if(other!=selectedKey&&other.tick==at)return false;
            selectedKey.tick=at;
        }
        else if("x".equals(name))selectedKey.x=n;
        else if("y".equals(name))selectedKey.y=n;
        else if("z".equals(name))selectedKey.z=n;
        else if("yaw".equals(name))selectedKey.yaw=(float)n;
        else if("pitch".equals(name))selectedKey.pitch=(float)n;
        else if("roll".equals(name))selectedKey.roll=(float)n;
        else if("fov".equals(name))
        {
            if(n<1D||n>170D)return false;
            selectedKey.fov=(float)n;
        }
        else return false;
        java.util.Collections.sort(keyOwner.keys,
            new java.util.Comparator<CameraClipDocument.Key>()
            {
                @Override public int compare(CameraClipDocument.Key a,
                        CameraClipDocument.Key b)
                {return Integer.compare(a.tick,b.tick);}
            });
        saveScene();return true;
    }

    /** Extend media duration when the referenced file has a readable length. */
    private void extendMediaClip(CameraClipDocument.Clip clip)
    {
        if(clip==null)return;
        String source=clip.kind==CameraClipDocument.Kind.VIDEO
                ?clip.settings.get("video"):clip.settings.get("audio");
        File file=CameraImageCache.resolve(source);
        if(file==null||!file.isFile())
        {lastError="Media file was not found";return;}
        double seconds=0D;
        if(clip.kind==CameraClipDocument.Kind.AUDIO)
        {
            try
            {
                javax.sound.sampled.AudioFileFormat format=
                        javax.sound.sampled.AudioSystem.getAudioFileFormat(file);
                javax.sound.sampled.AudioFormat audio=format.getFormat();
                if(audio.getFrameRate()>0&&format.getFrameLength()>0)
                    seconds=format.getFrameLength()/audio.getFrameRate();
            }
            catch(Exception invalid)
            {lastError="Cannot read audio duration";return;}
        }
        else if(clip.kind==CameraClipDocument.Kind.VIDEO)
        {
            Process process=null;
            try
            {
                process=new ProcessBuilder("ffprobe","-v","error",
                        "-show_entries","format=duration",
                        "-of","default=noprint_wrappers=1:nokey=1",
                        file.getAbsolutePath()).redirectErrorStream(true).start();
                java.io.BufferedReader reader=new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                String first=reader.readLine();
                if(!process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))
                {process.destroyForcibly();lastError="FFprobe timed out";return;}
                seconds=Double.parseDouble(first);
            }
            catch(Exception invalid)
            {lastError="FFprobe not available";return;}
            finally{if(process!=null)process.destroy();}
        }
        if(!Double.isFinite(seconds)||seconds<=0D)
        {lastError="Media duration unavailable";return;}
        int original=clip.duration;
        clip.duration=Math.max(1,Math.min(Integer.MAX_VALUE-clip.tick,
                (int)Math.ceil(seconds*20D)));
        if(document.overlaps(clip,clip))
        {clip.duration=original;lastError="Media overlaps another clip";return;}
        saveScene();
    }

    private int presetScroll;
    private int inspectorScroll;
    private final CameraClipInspector inspector=new CameraClipInspector();
    private final List<String> actorTargets=new ArrayList<String>();
    private boolean choosingTarget;
    private int actorTargetScroll;
    private boolean choosingMedia;
    private String mediaProperty;
    private final List<File> mediaFiles=new ArrayList<File>();
    private int mediaScroll;

    private void collectMediaFiles(File directory,int depth,String[] extensions)
    {
        if(depth>3||mediaFiles.size()>120)return;
        File[] children=directory.listFiles();
        if(children==null)return;
        java.util.Arrays.sort(children);
        for(File child:children)
        {
            if(child.isDirectory())collectMediaFiles(child,depth+1,extensions);
            else
            {
                String name=child.getName().toLowerCase(java.util.Locale.ROOT);
                for(String ext:extensions)if(name.endsWith(ext))
                {mediaFiles.add(child);break;}
            }
            if(mediaFiles.size()>120)break;
        }
    }
    private void openMediaPicker(String property)
    {
        mediaProperty=property;mediaFiles.clear();mediaScroll=0;
        File root=new File("config/bbs-editor/media");
        if(!root.isDirectory())root.mkdirs();
        String[] types="texture".equals(property)||"image".equals(property)
                ?new String[]{".png",".jpg",".jpeg",".webp"}
                :"font".equals(property)?new String[]{".ttf",".otf"}
                :"video".equals(property)?new String[]{".mp4",".mov",".webm",".mkv"}
                :new String[]{".wav",".ogg",".mp3"};
        collectMediaFiles(root,0,types);
        choosingMedia=true;
    }
    private CameraClipDocument.Key selectedKey;
    private CameraClipDocument.Clip keyOwner;
    private String keyField;
    private CameraClipDocument.Clip contextClip;
    private static final String[] KEY_FIELDS={
        "tick","x","y","z","yaw","pitch","roll","fov",
        "mask","interpolation"
    };

    private String keyValue(CameraClipDocument.Key key,String field)
    {
        if("tick".equals(field))return Integer.toString(key.tick);
        if("mask".equals(field))return Integer.toString(key.mask);
        if("interpolation".equals(field))return key.interpolation;
        if("x".equals(field))return Double.toString(key.x);
        if("y".equals(field))return Double.toString(key.y);
        if("z".equals(field))return Double.toString(key.z);
        if("yaw".equals(field))return Float.toString(key.yaw);
        if("pitch".equals(field))return Float.toString(key.pitch);
        if("roll".equals(field))return Float.toString(key.roll);
        return Float.toString(key.fov);
    }
    private void beginKeyEdit(AnimationEditorScreen screen,String name,
            CameraClipDocument.Key key,int y)
    {
        editKey=null;editClip=null;
        keyField=name;namingPreset=false;
        editField=new GuiTextField(43,Minecraft.getMinecraft().fontRenderer,
                right+8,y,165,17);
        editField.setMaxStringLength(60);
        editField.setText(keyValue(key,name));
        editField.setFocused(true);
    }
    private int contextX,contextY;
    /** Shift-click multiple clips to save a reusable layered preset. */
    private final java.util.LinkedHashSet<CameraClipDocument.Clip> groupSelection=
            new java.util.LinkedHashSet<CameraClipDocument.Clip>();

    public boolean mouseScrolled(int mouseX,int mouseY,int direction)
    {
        if(editField!=null||palette)return true;
        if(choosingMedia)
        {
            mediaScroll=Math.max(0,Math.min(
                    Math.max(0,mediaFiles.size()-12),mediaScroll+(direction>0?-1:1)));
            return true;
        }
        if(choosingTarget)
        {
            actorTargetScroll=Math.max(0,Math.min(
                    Math.max(0,actorTargets.size()-12),
                    actorTargetScroll+(direction>0?-1:1)));
            return true;
        }
        if(mouseY>=25&&mouseY<top&&mouseX>=right)
        {
            if(inspector.isInterpolationOpen())inspector.scrollInterpolation(direction);
            else inspectorScroll=Math.max(0,Math.min(inspector.maxScroll(),
                    inspectorScroll+(direction>0?-44:44)));
            return true;
        }
        if(mouseX>=timelineX&&mouseY>=top&&mouseY<bottom)
        {
            if(GuiScreen.isCtrlKeyDown())
            {
                int anchor=cameraTimeline.getFrameFromMouseX(mouseX,timelineX);
                int oldX=cameraTimeline.getFrameX(anchor,timelineX);
                cameraTimeline.changeZoom(direction>0?0.25F:-0.25F);
                cameraTimeline.addOffset(
                        cameraTimeline.getFrameX(anchor,timelineX)-oldX);
                cameraTimeline.clampOffset(Math.max(0,cameraTimeline.getLength()-1),
                        Math.max(1,timelineW));
            }
            else if(GuiScreen.isShiftKeyDown())
            {
                cameraTimeline.addOffset(direction>0?-60:60);
                cameraTimeline.clampOffset(Math.max(0,cameraTimeline.getLength()-1),
                        Math.max(1,timelineW));
            }
            else
            {
                int highest=5;
                for(CameraClipDocument.Clip clip:document.clips())
                    highest=Math.max(highest,clip.layer);
                int visible=Math.max(1,(bottom-rowBase)/rowHeight);
                layerScroll=Math.max(0,Math.min(Math.max(0,highest-visible+1),
                        layerScroll+(direction>0?-1:1)));
            }
            return true;
        }
        if(mouseX<timelineX&&mouseY>=90&&mouseY<top)
        {
            presetScroll=Math.max(0,Math.min(
                    Math.max(0,presetNames.size()-Math.max(1,(top-95)/17)),
                    presetScroll-direction));
            return true;
        }
        return false;
    }

    private static File sceneDocument(File scene)
    {
        File directory=new File("config/bbs-editor/cameras");
        String name=scene.getName().replaceAll("[^a-zA-Z0-9._-]","_");
        return new File(directory,name+"-"+Integer.toHexString(
                scene.getAbsolutePath().hashCode())+".json");
    }
    public void syncScene(EditorSceneState state)
    {
        File scene=null;
        int i=state.getSelectedScene();
        if(i>=0&&i<state.getSceneFiles().size())scene=state.getSceneFiles().get(i);
        String id=scene==null?"":scene.getAbsolutePath();
        if(id.equals(activeSceneId)&&activeState==state)return;
        stopPreviewRecording();
        activeSceneId=id;
        activeState=state;
        activeSceneFile=scene==null?null:sceneDocument(scene);
        document=state.getCameraClips();
        selectedKey=null;keyOwner=null;contextClip=null;
        shot=false;crossModeShot=false;recordingPreview=false;
        trackedCameraClip=null;trackedCameraKey=null;
        lastNavigationPose=null;navigationDirty=false;
        groupSelection.clear();
        mediaDragged=null;mediaDraggedKey=null;mediaDirty=false;
        resizeEdge=0;dragged=null;draggedKey=null;
        layerScroll=0;inspectorScroll=0;editField=null;
        choosingTarget=false;actorTargets.clear();
        choosingMedia=false;mediaFiles.clear();
        soundtrack.clear();
        CameraVideoFrames.clear();
        CameraImageCache.clear();

        /*
         * One-way migration from early CAMERA preview builds that wrote
         * separate JSON files. Prefer embedded editor.dat data whenever it
         * exists; never overwrite it with the legacy sidecar.
         */
        if(document.clips().isEmpty()&&activeSceneFile!=null
                &&activeSceneFile.isFile())
        {
            try(Reader reader=new FileReader(activeSceneFile))
            {
                document.readJson(new JsonParser().parse(reader).getAsJsonObject());
                state.getSaveController().markDirty();
            }
            catch(Exception e){lastError="Cannot migrate camera scene: "+e.getMessage();}
        }
        refreshPresets();
    }
    public void saveScene()
    {
        if(activeState==null)return;
        EditorSaveController saves=activeState.getSaveController();
        if(saves==null)return;
        try
        {
            saves.saveEditorDataOnly();
            // Editor-only autosave must not mark Blockbuster record changes
            // clean: a later Ctrl+S is still responsible for the full save.
            saves.markDirty();
            lastError=null;
        }
        catch(IOException e)
        {
            saves.markDirty();
            lastError="Cannot save camera in editor.dat: "+e.getMessage();
        }
    }
    private File presetsDir(){return new File("config/bbs-editor/camera-presets");}
    private void refreshPresets()
    {
        presetNames.clear();
        File[] files=presetsDir().listFiles();
        if(files==null)return;
        java.util.Arrays.sort(files);
        for(File file:files)if(file.isFile()&&file.getName().endsWith(".json"))
            presetNames.add(file.getName().substring(0,file.getName().length()-5));
    }
    public boolean saveSelectedPreset(String requested)
    {
        CameraClipDocument.Clip selected=document.selected();
        if(selected==null||requested==null)return false;
        String name=requested.trim().replaceAll("[^a-zA-Z0-9._-]","_");
        if(name.isEmpty()||name.equals(".")||name.equals(".."))return false;
        File folder=presetsDir();
        if(!folder.isDirectory()&&!folder.mkdirs())return false;
        File destination=new File(folder,name+".json");
        if(destination.exists())
        {
            lastError="Preset already exists: "+name;return false;
        }

        java.util.List<CameraClipDocument.Clip> chosen=
                new java.util.ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:groupSelection)
            if(document.clips().contains(clip))chosen.add(clip);
        if(chosen.isEmpty()||!chosen.contains(selected))
        {
            chosen.clear();chosen.add(selected);
        }

        com.google.gson.JsonElement data;
        if(chosen.size()==1)
            data=CameraClipDocument.clipToJson(chosen.get(0));
        else
        {
            int first=Integer.MAX_VALUE,lowest=Integer.MAX_VALUE;
            for(CameraClipDocument.Clip clip:chosen)
            {
                first=Math.min(first,clip.tick);
                lowest=Math.min(lowest,clip.layer);
            }
            com.google.gson.JsonObject combined=new com.google.gson.JsonObject();
            combined.addProperty("presetVersion",1);
            com.google.gson.JsonArray items=new com.google.gson.JsonArray();
            for(CameraClipDocument.Clip clip:chosen)
            {
                CameraClipDocument.Clip relative=clip.copy();
                relative.tick-=first;
                relative.layer-=lowest;
                items.add(CameraClipDocument.clipToJson(relative));
            }
            combined.add("clips",items);
            data=combined;
        }
        try(Writer writer=new FileWriter(destination))
        {
            new GsonBuilder().setPrettyPrinting().create().toJson(data,writer);
            refreshPresets();lastError=null;return true;
        }
        catch(IOException error)
        {
            destination.delete();
            lastError="Cannot save preset: "+error.getMessage();
            return false;
        }
    }
    public boolean pastePreset(String name,int tick,int layer)
    {
        File file=new File(presetsDir(),name+".json");
        if(!file.isFile())return false;
        try(Reader reader=new FileReader(file))
        {
            com.google.gson.JsonObject root=new JsonParser().parse(reader)
                    .getAsJsonObject();
            java.util.List<CameraClipDocument.Clip> bundle=
                    new java.util.ArrayList<CameraClipDocument.Clip>();
            if(root.has("clips"))
            {
                for(com.google.gson.JsonElement element:root.getAsJsonArray("clips"))
                    bundle.add(CameraClipDocument.clipFromJson(element.getAsJsonObject()));
            }
            else bundle.add(CameraClipDocument.clipFromJson(root));
            if(bundle.isEmpty())return false;

            int chosenBump=-1;
            for(int bump=0;bump<64;bump++)
            {
                boolean collision=false;
                for(CameraClipDocument.Clip clip:bundle)
                {
                    CameraClipDocument.Clip candidate=clip.copy();
                    candidate.tick=Math.max(0,tick+clip.tick);
                    candidate.layer=Math.max(0,layer+clip.layer+bump);
                    if(document.overlaps(candidate,null))
                    {collision=true;break;}
                }
                if(!collision){chosenBump=bump;break;}
            }
            if(chosenBump<0)
            {
                lastError="No free layers for camera preset";return false;
            }
            for(CameraClipDocument.Clip clip:bundle)
            {
                clip.tick=Math.max(0,tick+clip.tick);
                clip.layer=Math.max(0,layer+clip.layer+chosenBump);
                if(!document.add(clip))
                {
                    lastError="Preset collision on insertion";
                    return false;
                }
            }
            saveScene();return true;
        }
        catch(Exception error)
        {
            lastError="Cannot paste preset: "+error.getMessage();
            return false;
        }
    }
    private int pendingTick,pendingLayer;
    private int lastClickX,lastClickY,lastButton=-1;
    private long lastClick;
    private CameraClipDocument.Clip dragged;
    private CameraClipDocument.Clip draggedKeyOwner;
    private CameraClipDocument.Key draggedKey;
    private int draggedKeyStart;
    private boolean cloneKey;
    /** -1 left trim handle, +1 right trim handle, 0 = moving the clip. */
    private int resizeEdge;
    private int dragStartDuration;
    private int dragStartX,dragStartTick,dragStartLayer;
    private CameraClipDocument.Clip mediaDragged;
    private CameraClipDocument.Key mediaDraggedKey;
    private int mediaMouseX,mediaMouseY,mediaFrameWidth,mediaFrameHeight;
    private double mediaStartX,mediaStartY;
    private boolean mediaDirty;
    private boolean cloneDrag;
    private boolean scrubbingTimeline;
    private CameraClipInspector.Hit numericDragCandidate;
    private CameraClipDocument.Clip numericDragClip;
    private CameraClipDocument.Key numericDragKey;
    private int numericStartX;
    private double numericStartValue;
    private boolean numericDragging;
    private int scaleStart,scaleEnd;
    private int right;
    private int top;
    private int bottom;
    private int rowHeight=20;
    private final EditorTimeline cameraTimeline=new EditorTimeline();
    private int rowBase;
    private int layerScroll;
    /** Compact CAMERA track and preset sidebar; Character stays unchanged. */
    public static final int CAMERA_LEFT_WIDTH=130;
    private int timelineX=CAMERA_LEFT_WIDTH;
    private static int trackTheme(float strength)
    {
        int accent=EditorThemeManager.get().getAccent();
        int r=(accent>>16)&255,g=(accent>>8)&255,b=accent&255;
        r=Math.max(0,Math.min(255,Math.round(r*strength)));
        g=Math.max(0,Math.min(255,Math.round(g*strength)));
        b=Math.max(0,Math.min(255,Math.round(b*strength)));
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    private int timelineW;

    private String selectedVideoProblem()
    {
        CameraClipDocument.Clip clip=document.selected();
        if(clip==null||clip.kind!=CameraClipDocument.Kind.VIDEO)return null;
        String source=clip.settings.get("video");
        if(source==null||source.trim().isEmpty())
            return "VIDEO: choose a source";
        return CameraVideoFrames.failureFor(source);
    }

    public void draw(AnimationEditorScreen screen,int frame,int length)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int width=screen.width,height=screen.height;
        top=height-screen.getTimelineHeight();bottom=height;
        right=width-185;
        timelineW=Math.max(1,width-timelineX-8);
        rowBase=top+35;
        cameraTimeline.setLength(Math.max(1,length));
        cameraTimeline.clampOffset(Math.max(0,length-1),timelineW);
        // Camera viewer and clip authoring buttons are drawn beside Play.
        // This prevents controls from covering the cinematic image.
        GuiScreen.drawRect(0,25,timelineX,top,0xFF191E24);
        GuiScreen.drawRect(right,25,width,top,0xFF191E24);
        mc.fontRenderer.drawString("PRESETS",10,34,0xFF64CAFF);
        mc.fontRenderer.drawString("Saved presets",10,53,0xFF87939D);
        mc.fontRenderer.drawString("RMB clip to save",10,69,0xFF87939D);
        int maxPresets=Math.max(0,(top-100)/17);
        for(int i=0;i<maxPresets&&presetScroll+i<presetNames.size();i++)
            mc.fontRenderer.drawString(presetNames.get(presetScroll+i),
                    12,95+i*17,0xFFD1DAE0);
        if(lastError!=null)mc.fontRenderer.drawString(lastError,10,top-12,0xFFFF8888);
        if(selectedVideoProblem()!=null)
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(
                            selectedVideoProblem(),Math.max(50,width-right-12)),
                    right+6,top-11,0xFFFF9494);
        CameraClipDocument.Clip selected=document.selected();
        CameraClipDocument.Key displayedKey=selectedKey;
        boolean activeKey=selectedKey!=null&&keyOwner==selected;
        if(!activeKey&&selected!=null&&!selected.keys.isEmpty())
            displayedKey=selected.keys.get(0);
        inspector.draw(screen,selected,displayedKey,
                activeKey,inspectorScroll,right,top);
        inspectorScroll=inspector.clampedScroll();
        // Same exact dark accent-derived palette as CharacterTimeline.
        int accent=EditorThemeManager.get().getAccent();
        GuiScreen.drawRect(0,top,width,bottom,trackTheme(0.07F));
        GuiScreen.drawRect(0,top,width,top+1,accent);
        GuiScreen.drawRect(0,top,width,top+35,trackTheme(0.12F));
        GuiScreen.drawRect(0,top,timelineX,top+35,trackTheme(0.17F));
        GuiScreen.drawRect(timelineX,top,width,top+20,trackTheme(0.17F));
        GuiScreen.drawRect(timelineX,top+20,width,top+35,trackTheme(0.09F));
        GuiScreen.drawRect(timelineX,top+34,width,top+35,trackTheme(0.20F));
        mc.fontRenderer.drawString("CAMERA",10,top+11,
                EditorThemeManager.get().getAccentBright());
        int visibleRows=Math.max(1,(bottom-rowBase)/rowHeight);
        for(int row=0;row<visibleRows;row++)
        {
            int layer=row+layerScroll;
            int y=rowBase+row*rowHeight;
            boolean active=selected!=null&&selected.layer==layer;
            GuiScreen.drawRect(timelineX,y,width,y+rowHeight,
                    active?trackTheme(0.22F):
                    layer%2==0?trackTheme(0.12F):trackTheme(0.09F));
            GuiScreen.drawRect(0,y,timelineX,y+rowHeight,
                    active?trackTheme(0.22F):trackTheme(0.12F));
            if(active)GuiScreen.drawRect(0,y,3,y+rowHeight,accent);
            GuiScreen.drawRect(0,y+rowHeight-1,width,y+rowHeight,
                    trackTheme(0.05F));
            mc.fontRenderer.drawString("Layer "+layer,10,y+6,
                    active?EditorThemeManager.get().getAccentBright():
                    0xFF9DA4A9);
        }
        int first=Math.max(0,
                cameraTimeline.getFrameFromMouseX(timelineX,timelineX)-2);
        // Fill the whole visible ruler even for an empty scene.
        int last=Math.max(first,
                cameraTimeline.getFrameFromMouseX(width+12,timelineX)+2);
        int step=cameraTimeline.getZoom()>=2F?1:
                cameraTimeline.getZoom()>=1F?5:
                cameraTimeline.getZoom()>=0.5F?10:20;
        for(int n=first;n<=last;n++)
        {
            int x=cameraTimeline.getFrameX(n,timelineX);
            if(x<timelineX||x>=width)continue;
            boolean major=n%step==0;
            boolean secondary=!major&&n%5==0;
            GuiScreen.drawRect(x,top+35,x+1,bottom,
                    major?trackTheme(0.20F):
                    secondary?trackTheme(0.15F):trackTheme(0.10F));
            GuiScreen.drawRect(x,top+35-(major?8:secondary?5:3),
                    x+1,top+35,major?0xFF9DA4A9:0xFF666D72);
            if(major)
            {
                String label=Integer.toString(n);
                mc.fontRenderer.drawString(label,
                        x-mc.fontRenderer.getStringWidth(label)/2,
                        top+23,0xFF9DA4A9);
            }
        }
        for(CameraClipDocument.Clip clip:document.clips())
        {
            int x=cameraTimeline.getFrameX(clip.tick,timelineX);
            int x2=cameraTimeline.getFrameX(clip.tick+clip.duration,timelineX);
            int y=rowBase+(clip.layer-layerScroll)*rowHeight;
            if(y<rowBase||y+rowHeight>bottom||x2<=timelineX||x>=width)continue;
            int lx=Math.max(x,timelineX),rx=Math.min(width,Math.max(lx+2,x2));
            GuiScreen.drawRect(lx,y+3,rx,y+rowHeight-3,
                    clip.enabled?clip.kind.color:0xFF58626B);
            GuiScreen.drawRect(lx,y+3,rx,y+5,0x66FFFFFF);
            if(clip==selected||groupSelection.contains(clip))
            {
                int highlight=groupSelection.contains(clip)?0xFFFFF2AB:0xFFFFFFFF;
                GuiScreen.drawRect(lx,y+2,rx,y+3,highlight);
                GuiScreen.drawRect(lx,y+rowHeight-3,rx,y+rowHeight-2,highlight);
                GuiScreen.drawRect(lx,y+2,lx+1,y+rowHeight-2,highlight);
                GuiScreen.drawRect(rx-1,y+2,rx,y+rowHeight-2,highlight);
            }
            if(rx-lx>24)
                mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                        clip.title,Math.max(1,rx-lx-10)),lx+5,y+6,0xFFFFFFFF);
            for(CameraClipDocument.Key key:clip.keys)
            {
                int kx=cameraTimeline.getFrameX(clip.tick+key.tick,timelineX);
                if(kx<lx||kx>=rx)continue;
                GuiScreen.drawRect(kx-2,y+7,kx+3,y+12,
                        key==selectedKey?0xFFFFF0A2:0xFFFFFFFF);
                GuiScreen.drawRect(kx-1,y+6,kx+2,y+13,
                        key==selectedKey?0xFFFFE07F:0xFF132635);
            }
            // Both edges are draggable. Draw clear grab handles, including
            // on short clips; hit testing uses the actual pixel boundaries.
            if(x>=timelineX&&x<width)
                GuiScreen.drawRect(x+1,y+6,x+3,y+rowHeight-6,0xDDEAF7FF);
            if(x2>=timelineX&&x2<width)
                GuiScreen.drawRect(x2-3,y+6,x2-1,y+rowHeight-6,0xDDEAF7FF);
        }
        int cursor=cameraTimeline.getFrameX(Math.max(0,frame),timelineX);
        if(cursor>=timelineX&&cursor<width)
        {
            GuiScreen.drawRect(cursor,top+19,cursor+1,bottom,
                    EditorThemeManager.get().getAccentBright());
            GuiScreen.drawRect(cursor-4,top+16,cursor+5,top+20,accent);
        }
        if(palette)drawPalette(screen);
        if(contextClip!=null)
        {
            GuiScreen.drawRect(contextX,contextY,contextX+150,
                    contextY+26,0xFF2A313B);
            GuiScreen.drawRect(contextX,contextY,contextX+150,
                    contextY+1,0xFF65C6F0);
            mc.fontRenderer.drawString("Save as Preset",
                    contextX+8,contextY+9,0xFFE4F0FA);
        }
        if(choosingTarget)
        {
            int menuWidth=250;
            int menuHeight=38+Math.min(12,actorTargets.size())*20;
            int mx=Math.max(timelineX+10,(right+timelineX-menuWidth)/2);
            int my=Math.max(35,(top-menuHeight)/2);
            GuiScreen.drawRect(mx-3,my-3,mx+menuWidth+3,
                    my+menuHeight+3,0xFF080D12);
            GuiScreen.drawRect(mx,my,mx+menuWidth,my+menuHeight,0xFF202A32);
            GuiScreen.drawRect(mx,my,mx+menuWidth,my+2,
                    EditorThemeManager.get().getAccent());
            mc.fontRenderer.drawString("TRACK AN ACTOR",mx+12,my+11,0xFFBDEBFF);
            for(int i=0;i<12&&actorTargetScroll+i<actorTargets.size();i++)
            {
                int sy=my+34+i*20;
                GuiScreen.drawRect(mx+6,sy,mx+menuWidth-6,sy+18,
                        i%2==0?0xFF283844:0xFF23313D);
                mc.fontRenderer.drawString(
                        mc.fontRenderer.trimStringToWidth(
                                actorTargets.get(actorTargetScroll+i),menuWidth-28),
                        mx+13,sy+5,0xFFE3EDF3);
            }
        }
        if(choosingMedia)
        {
            int menuWidth=Math.min(340,Math.max(185,right-timelineX-20));
            int menuHeight=49+Math.min(12,mediaFiles.size())*20;
            int mx=Math.max(timelineX+5,(right+timelineX-menuWidth)/2);
            int my=Math.max(30,(top-menuHeight)/2);
            GuiScreen.drawRect(mx-3,my-3,mx+menuWidth+3,
                    my+menuHeight+3,0xFF080C10);
            GuiScreen.drawRect(mx,my,mx+menuWidth,my+menuHeight,0xFF212A32);
            GuiScreen.drawRect(mx,my,mx+menuWidth,my+2,
                    EditorThemeManager.get().getAccent());
            mc.fontRenderer.drawString("MEDIA / "+mediaProperty,
                    mx+10,my+9,0xFF9DE6FF);
            mc.fontRenderer.drawString(
                    "config/bbs-editor/media (ESC to close)",
                    mx+10,my+24,0xFF899BA9);
            if(mediaFiles.isEmpty())
                mc.fontRenderer.drawString("No matching files. Enter a path manually.",
                        mx+10,my+42,0xFFC0C4C7);
            for(int i=0;i<12&&mediaScroll+i<mediaFiles.size();i++)
            {
                int yy=my+44+i*20;
                GuiScreen.drawRect(mx+6,yy,mx+menuWidth-6,yy+18,
                        i%2==0?0xFF2A3945:0xFF23313C);
                mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                        mediaFiles.get(mediaScroll+i).getName(),menuWidth-24),
                        mx+12,yy+5,0xFFE0EBEF);
            }
        }
        if(editField!=null)
        {
            if(namingPreset)
            {
                GuiScreen.drawRect(screen.width/2-116,screen.height/2-30,
                    screen.width/2+116,screen.height/2+42,0xF0161A20);
                mc.fontRenderer.drawString("SAVE CAMERA PRESET",screen.width/2-103,
                    screen.height/2-20,0xFF69CFFF);
            }
            editField.drawTextBox();
        }
    }
    private void drawPalette(AnimationEditorScreen screen)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int w=Math.min(480,screen.width-24),x=(screen.width-w)/2,y=Math.max(30,(screen.height-250)/2);
        GuiScreen.drawRect(x-3,y-3,x+w+3,y+233,0xFF080B0E);
        GuiScreen.drawRect(x,y,x+w,y+230,0xFF1C222A);
        mc.fontRenderer.drawString("ADD CAMERA CLIP",x+9,y+8,0xFF65CEFF);
        CameraClipDocument.Family[] families=CameraClipDocument.Family.values();
        for(int c=0;c<3;c++)
        {
            int cx=x+8+c*(w-16)/3;
            mc.fontRenderer.drawString(
                    c==0?"OVERWRITE":c==1?"MODIFIERS":"MISC",cx,y+31,0xFFB4CBD9);
            int i=0;
            for(CameraClipDocument.Kind kind:CameraClipDocument.Kind.values())
            {
                if(kind.family!=families[c])continue;
                int yy=y+48+i*18;
                GuiScreen.drawRect(cx,yy,cx+3,yy+15,kind.color);
                mc.fontRenderer.drawString(kind.label,cx+8,yy+3,0xFFE6EAED);
                i++;
            }
        }
    }
    /**
     * Left-drag an already visible Image, Video or Subtitle in the actual
     * film rectangle. Works in 16:9, portrait and custom aspect formats.
     * The selected clip follows its overlay; click can also select the
     * highest visible media layer, without moving the scene camera.
     */
    public boolean beginMediaDrag(int mouseX,int mouseY,int button,int frame,
            int filmX,int filmY,int filmW,int filmH)
    {
        if(button!=0||palette||choosingMedia||choosingTarget||
                editField!=null||filmW<1||filmH<1)return false;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null)return false;
        CameraClipDocument.Clip target=null;
        CameraClipDocument.Clip selected=document.selected();
        if(selected!=null&&selected.enabled&&selected.contains(frame)
                &&CameraMediaPlacement.contains(selected,mc.fontRenderer,
                        filmX,filmY,filmW,filmH,mouseX,mouseY,frame))
            target=selected;
        else
        {
            for(CameraClipDocument.Clip c:document.clips())
            {
                if(!c.enabled||!c.contains(frame)||
                        !CameraMediaPlacement.contains(c,mc.fontRenderer,
                                filmX,filmY,filmW,filmH,mouseX,mouseY,frame))
                    continue;
                if(target==null||c.layer>=target.layer)target=c;
            }
        }
        if(target==null)return false;
        if(document.selected()!=target)
        {
            document.select(target);
            selectedKey=null;keyOwner=null;inspectorScroll=0;
            groupSelection.clear();groupSelection.add(target);
        }
        mediaDragged=target;
        mediaDraggedKey=null;
        if(CameraMediaAnimation.supports(target)&&!target.keys.isEmpty())
        {
            // Do not silently overwrite every key's static base placement.
            // On drag, author/replace a key at the exact playhead instead.
            CameraClipDocument.Key existing=null;
            for(CameraClipDocument.Key key:target.keys)
                if(key.tick==frame-target.tick){existing=key;break;}
            if(existing==null)
            {
                CameraClipDocument.Key basis=target.keys.get(0);
                existing=new CameraClipDocument.Key(frame-target.tick,
                        new PreviewCameraPose(basis.x,basis.y,basis.z,
                                basis.yaw,basis.pitch,basis.roll,basis.fov,
                                0.05F,512F));
                CameraMediaAnimation.capture(target,existing,frame);
                target.keys.add(existing);
                java.util.Collections.sort(target.keys,
                    new java.util.Comparator<CameraClipDocument.Key>(){
                        @Override public int compare(CameraClipDocument.Key a,
                                CameraClipDocument.Key b)
                        {return Integer.compare(a.tick,b.tick);}
                    });
            }
            mediaDraggedKey=existing;
            selectedKey=existing;keyOwner=target;
        }
        mediaMouseX=mouseX;mediaMouseY=mouseY;
        mediaFrameWidth=filmW;mediaFrameHeight=filmH;
        java.util.Map<String,Double> visible=
                CameraMediaAnimation.sample(target,frame);
        mediaStartX=visible.get("x");
        mediaStartY=visible.get("y");
        mediaDirty=false;
        return true;
    }

    public void drawMediaSelection(int frame,int filmX,int filmY,
            int filmW,int filmH)
    {
        CameraClipDocument.Clip selected=document.selected();
        if(selected==null||!selected.enabled||!selected.contains(frame)
                ||!CameraMediaPlacement.canDrag(selected))return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null)return;
        int[] rect=CameraMediaPlacement.outline(selected,mc.fontRenderer,
                filmX,filmY,filmW,filmH,frame);
        if(rect==null)return;
        int left=Math.max(filmX,rect[0]),top=Math.max(filmY,rect[1]);
        int right=Math.min(filmX+filmW,rect[2]);
        int bottom=Math.min(filmY+filmH,rect[3]);
        if(right<=left||bottom<=top)return;
        int col=0xFF72D4F7;
        GuiScreen.drawRect(left,top,right,top+1,col);
        GuiScreen.drawRect(left,bottom-1,right,bottom,col);
        GuiScreen.drawRect(left,top,left+1,bottom,col);
        GuiScreen.drawRect(right-1,top,right,bottom,col);
        // Center crosshair is editing chrome and is not in the final FBO.
        int mx=filmX+(int)Math.round(filmW*CameraMediaPlacement.x(selected));
        int my=filmY+(int)Math.round(filmH*CameraMediaPlacement.y(selected));
        if(mx>=filmX+4&&mx<filmX+filmW-4&&my>=filmY+4&&my<filmY+filmH-4)
        {
            GuiScreen.drawRect(mx-4,my,mx+5,my+1,col);
            GuiScreen.drawRect(mx,my-4,mx+1,my+5,col);
        }
    }

    public boolean click(AnimationEditorScreen screen,int mouseX,int mouseY,int button,int frame,int length)
    {
        if(choosingMedia)
        {
            int menuWidth=Math.min(340,Math.max(185,right-timelineX-20));
            int menuHeight=49+Math.min(12,mediaFiles.size())*20;
            int mx=Math.max(timelineX+5,(right+timelineX-menuWidth)/2);
            int my=Math.max(30,(top-menuHeight)/2);
            if(button==0&&mouseX>=mx+6&&mouseX<mx+menuWidth-6
                    &&mouseY>=my+44&&mouseY<my+44+
                            Math.min(12,mediaFiles.size())*20)
            {
                int index=mediaScroll+(mouseY-my-44)/20;
                if(index>=0&&index<mediaFiles.size()
                        &&document.selected()!=null&&mediaProperty!=null)
                {
                    File root=new File("config/bbs-editor/media");
                    String file=root.toURI().relativize(
                            mediaFiles.get(index).toURI()).getPath();
                    document.selected().settings.put(mediaProperty,file);
                    lastError=null;
                    saveScene();
                }
            }
            choosingMedia=false;return true;
        }
        if(choosingTarget)
        {
            int menuWidth=250,menuHeight=38+Math.min(12,actorTargets.size())*20;
            int mx=Math.max(timelineX+10,(right+timelineX-menuWidth)/2);
            int my=Math.max(35,(top-menuHeight)/2);
            if(button==0&&mouseX>=mx+6&&mouseX<mx+menuWidth-6
                    &&mouseY>=my+34&&mouseY<my+34+Math.min(12,actorTargets.size())*20)
            {
                int index=actorTargetScroll+(mouseY-my-34)/20;
                if(index>=0&&index<actorTargets.size()
                        &&document.selected()!=null)
                {
                    String target=actorTargets.get(index);
                    document.selected().settings.put("selector",
                            "<none>".equals(target)?"":target);
                    saveScene();
                }
            }
            choosingTarget=false;
            return true;
        }
        if(editField!=null)
        {
            if(button==0)editField.mouseClicked(mouseX,mouseY,button);
            return true;
        }
        if(palette)
        {
            if(button!=0){palette=false;return true;}
            int w=Math.min(480,screen.width-24),x=(screen.width-w)/2,y=Math.max(30,(screen.height-250)/2);
            int column=(mouseX-x-8)/Math.max(1,(w-16)/3);
            if(mouseX<x+8||column<0||column>2){palette=false;return true;}
            CameraClipDocument.Family family=CameraClipDocument.Family.values()[column];
            int row=(mouseY-y-48)/18,i=0;
            if(mouseY>=y+48)for(CameraClipDocument.Kind kind:CameraClipDocument.Kind.values())
            {
                if(kind.family!=family)continue;
                if(i++==row)
                {
                    CameraClipDocument.Clip made=CameraClipParameters.create(
                            kind,pendingTick,Math.max(1,Math.min(40,length-pendingTick)),
                            pendingLayer);
                    if(kind==CameraClipDocument.Kind.IDLE
                            ||kind==CameraClipDocument.Kind.DOLLY)
                        made.keys.add(new CameraClipDocument.Key(0,
                                screen.getCameraWorkspacePose()));
                    if(document.add(made))
                    {
                        saveScene();
                        // A bare VIDEO layer is only its editing outline,
                        // not media. Prompt for its source immediately.
                        if(kind==CameraClipDocument.Kind.VIDEO)
                            openMediaPicker("video");
                    }
                    palette=false;return true;
                }
            }
            palette=false;return true;
        }
        if(contextClip!=null)
        {
            CameraClipDocument.Clip target=contextClip;
            contextClip=null;
            boolean secondRight=button==1&&target==document.selected()
                    &&lastButton==1
                    &&Math.abs(lastClickX-mouseX)<6
                    &&Math.abs(lastClickY-mouseY)<6
                    &&System.currentTimeMillis()-lastClick<350;
            if(secondRight)
            {
                document.remove(target);
                selectedKey=null;keyOwner=null;
                saveScene();lastButton=-1;
                return true;
            }
            if(button==0&&mouseX>=contextX&&mouseX<contextX+150
                    &&mouseY>=contextY&&mouseY<contextY+26)
            {
                document.select(target);
                startTextField(screen,target.title.replace(" ","_"),true,
                        null,target);
                return true;
            }
            // Click outside the context menu closes it; the same click is
            // otherwise handled normally by the underlying editor.
        }
        if(button==0&&mouseX>=right&&mouseX<screen.width
                &&mouseY>=25&&mouseY<top)
        {
            numericDragCandidate=null;numericDragging=false;
            CameraClipInspector.Hit hit=inspector.hit(mouseX,mouseY);
            CameraClipDocument.Clip clip=document.selected();
            if(hit==null||clip==null)return true;
            if(hit.key.startsWith("@fold:"))
            {
                inspector.toggleSection(hit.key.substring("@fold:".length()));
                inspectorScroll=0;
                return true;
            }
            Double raw=finiteDouble(hit.value);
            if(raw!=null&&!hit.toggle&&!hit.key.startsWith("@interp:")
                    &&!hit.key.startsWith("@key:")
                    &&!hit.key.startsWith("@media:")
                    &&!"selector".equals(hit.key))
            {
                numericDragCandidate=hit;numericDragClip=clip;
                numericDragKey=hit.keyframe?
                        (selectedKey!=null&&keyOwner==clip?selectedKey:
                            clip.keys.isEmpty()?null:clip.keys.get(0)):null;
                numericStartX=mouseX;numericStartValue=raw;
            }
            if(hit.key.startsWith("@interp:"))
            {
                String action=hit.key.substring("@interp:".length());
                if(action.startsWith("open:"))
                {
                    inspector.toggleInterpolation(action.substring(5));
                    return true;
                }
                if(action.startsWith("set:"))
                {
                    String encoded=action.substring(4);
                    int separator=encoded.lastIndexOf(':');
                    if(separator<0)return true;
                    String property=encoded.substring(0,separator);
                    String chosen=encoded.substring(separator+1);
                    if(!CameraInterpolation.isKnown(chosen))return true;
                    if("@key".equals(property))
                    {
                        if(selectedKey==null||keyOwner!=clip)
                        {
                            if(clip.keys.isEmpty())return true;
                            selectedKey=clip.keys.get(0);keyOwner=clip;
                        }
                        selectedKey.interpolation=chosen;
                    }
                    else if(clip.settings.containsKey(property))
                        clip.settings.put(property,chosen);
                    inspector.closeInterpolation();
                    saveScene();return true;
                }
            }
            if(hit.key.startsWith("@key:"))
            {
                String action=hit.key.substring(5);
                if("add".equals(action))
                {
                    int at=Math.min(clip.tick+clip.duration-1,
                            Math.max(clip.tick,frame));
                    boolean exists=false;
                    for(CameraClipDocument.Key existing:clip.keys)
                        if(existing.tick==at-clip.tick){exists=true;break;}
                    if(!exists)document.keyAt(
                            clip,at,screen.getCameraWorkspacePose());
                    for(CameraClipDocument.Key existing:clip.keys)
                        if(existing.tick==at-clip.tick)
                        {
                            selectedKey=existing;keyOwner=clip;
                            if(!exists)
                            {
                                if(clip.kind==CameraClipDocument.Kind.REMAPPER)
                                    existing.x=at;
                                else if(clip.kind==CameraClipDocument.Kind.CURVE)
                                    existing.x=0D;
                            }
                            break;
                        }
                    saveScene();
                }
                else if("prev".equals(action)||"next".equals(action))
                {
                    if(!clip.keys.isEmpty())
                    {
                        int at=clip.keys.indexOf(selectedKey);
                        if(at<0)at=0;
                        else at=Math.max(0,Math.min(clip.keys.size()-1,
                                at+("next".equals(action)?1:-1)));
                        selectedKey=clip.keys.get(at);keyOwner=clip;
                    }
                }
                else if("remove".equals(action))
                {
                    if(selectedKey!=null&&keyOwner==clip)
                    {clip.keys.remove(selectedKey);selectedKey=null;keyOwner=null;
                    saveScene();}
                }
                return true;
            }
            if(hit.key.startsWith("@media:browse:"))
            {
                openMediaPicker(hit.key.substring("@media:browse:".length()));
                return true;
            }
            if("@media:duration".equals(hit.key))
            {
                extendMediaClip(clip);
                return true;
            }
            if(hit.keyframe)
            {
                if(selectedKey==null||keyOwner!=clip)
                {
                    if(clip.keys.isEmpty())return true;
                    selectedKey=clip.keys.get(0);
                    keyOwner=clip;
                }
                beginKeyEdit(screen,hit.key,selectedKey,hit.y);
            }
            else if("@enabled".equals(hit.key))
            {
                clip.enabled=!clip.enabled;saveScene();return true;
            }
            else if("active".equals(hit.key)&&hit.component>=0)
            {
                try
                {
                    int mask=Integer.parseInt(clip.settings.get("active"));
                    mask^=1<<hit.component;
                    clip.settings.put("active",Integer.toString(mask));
                    saveScene();
                }
                catch(NumberFormatException invalid)
                {
                    lastError="Invalid camera channel mask";
                }
                return true;
            }
            else if("selector".equals(hit.key)&&activeState!=null)
            {
                actorTargets.clear();
                actorTargets.add("<none>");
                for(BlockbusterSceneActorData actor:activeState.getActors())
                    if(actor!=null&&actor.getId()!=null
                            &&!actor.getId().isEmpty())
                        actorTargets.add(actor.getId());
                actorTargetScroll=0;choosingTarget=true;
                return true;
            }
            else if(hit.toggle&&clip.settings.containsKey(hit.key))
            {
                clip.settings.put(hit.key,Boolean.toString(
                        !Boolean.parseBoolean(clip.settings.get(hit.key))));
                saveScene();return true;
            }
            else
            {
                String field=hit.component<0?hit.key:hit.key+"#"+hit.component;
                startTextField(screen,hit.value,false,field,clip);
            }
            if(editField!=null)
            {
                editField.x=hit.x;
                editField.y=hit.y;
                editField.width=Math.max(36,hit.width);
            }
            return true;
        }
        // Toolbar view/record buttons are handled centrally by the screen.
        if(mouseX<timelineX&&mouseY>=95&&mouseY<top&&button==0)
        {
            int i=(mouseY-95)/17+presetScroll;
            if(i>=0&&i<presetNames.size())
                pastePreset(presetNames.get(i),frame,0);
            return true;
        }
        if(mouseX<timelineX&&mouseY>=25&&mouseY<top)return true;
        if(mouseX>=right&&mouseY>=25&&mouseY<top)return true;
        if(mouseY<top||mouseX<timelineX||mouseY>=bottom)return false;
        int t=Math.max(0,Math.min(Math.max(0,length-1),
                cameraTimeline.getFrameFromMouseX(mouseX,timelineX)));
        if(button==0&&mouseY<top+35)
        {
            scrubbingTimeline=true;
            screen.setCameraWorkspaceFrame(t);
            return true;
        }
        int layer=(mouseY-rowBase)/rowHeight+layerScroll;
        if(layer<0||mouseY<rowBase)return true;
        CameraClipDocument.Clip hit=document.hit(t,layer);
        int clickedEdge=0;
        // Search displayed screen-space boundaries FIRST. Frame-only hit
        // testing misses the far right handle because clip.end is exclusive.
        if(button==0)
        {
            int nearest=6;
            for(CameraClipDocument.Clip candidate:document.clips())
            {
                if(candidate.layer!=layer)continue;
                int x1=cameraTimeline.getFrameX(candidate.tick,timelineX);
                int x2=cameraTimeline.getFrameX(
                        candidate.tick+candidate.duration,timelineX);
                if(x2<timelineX-6||x1>screen.width+6)continue;
                int leftDist=Math.abs(mouseX-x1);
                int rightDist=Math.abs(mouseX-x2);
                if(leftDist<=nearest)
                {
                    nearest=leftDist;hit=candidate;clickedEdge=-1;
                }
                if(rightDist<nearest)
                {
                    nearest=rightDist;hit=candidate;clickedEdge=1;
                }
            }
        }
        CameraClipDocument.Key marker=null;
        if(hit!=null)
        {
            for(CameraClipDocument.Key key:hit.keys)
            {
                int kx=cameraTimeline.getFrameX(hit.tick+key.tick,timelineX);
                if(Math.abs(mouseX-kx)<=4){marker=key;break;}
            }
        }
        long now=System.currentTimeMillis();
        boolean twice=button==lastButton&&Math.abs(lastClickX-mouseX)<6&&Math.abs(lastClickY-mouseY)<6&&now-lastClick<350;
        lastClick=now;lastClickX=mouseX;lastClickY=mouseY;lastButton=button;
        if(button==0)
        {
            if(twice&&clickedEdge==0)
            {
                if(hit==null){pendingTick=t;pendingLayer=layer;palette=true;}
                else if(hit.kind.keyed){document.keyAt(hit,frame,screen.getCameraWorkspacePose());saveScene();}
                return true;
            }
            if(clickedEdge==0&&GuiScreen.isShiftKeyDown()&&hit!=null)
            {
                if(!groupSelection.add(hit))groupSelection.remove(hit);
                document.select(hit);
                dragged=null;return true;
            }
            groupSelection.clear();
            if(hit!=null)groupSelection.add(hit);
            if(hit!=document.selected()){selectedKey=null;keyOwner=null;inspectorScroll=0;}
            document.select(hit);
            draggedKey=null;draggedKeyOwner=null;
            dragged=hit;dragStartX=mouseX;
            if(hit!=null)
            {
                dragStartTick=hit.tick;dragStartLayer=hit.layer;
                cloneDrag=clickedEdge==0&&GuiScreen.isCtrlKeyDown();
                resizeEdge=clickedEdge;
                dragStartDuration=hit.duration;
                if(resizeEdge==0)
                {
                    for(CameraClipDocument.Key key:hit.keys)
                    {
                        int markerX=cameraTimeline.getFrameX(hit.tick+key.tick,timelineX);
                        if(Math.abs(mouseX-markerX)<=3)
                        {
                            draggedKeyOwner=hit;draggedKey=key;
                            selectedKey=key;keyOwner=hit;
                            draggedKeyStart=key.tick;
                            cloneKey=cloneDrag;
                            dragged=null;
                            break;
                        }
                    }
                }
            }
            return true;
        }
        if(button==1)
        {
            if(marker!=null)
            {
                document.select(hit);contextClip=null;
                if(twice&&selectedKey==marker&&keyOwner==hit)
                {
                    hit.keys.remove(marker);
                    selectedKey=null;keyOwner=null;saveScene();
                    lastButton=-1;
                }
                else
                {
                    selectedKey=marker;keyOwner=hit;
                }
                return true;
            }
            if(twice&&hit!=null)
            {
                document.remove(hit);contextClip=null;
                groupSelection.remove(hit);
                selectedKey=null;keyOwner=null;
                saveScene();lastButton=-1;
            }
            else if(hit!=null)
            {
                document.select(hit);
                selectedKey=null;keyOwner=null;
                contextClip=hit;
                contextX=Math.max(0,Math.min(screen.width-154,mouseX));
                contextY=Math.max(27,Math.min(top-30,mouseY));
            }
            return true;
        }
        return true;
    }
    public void move(int mouseX,int mouseY,int length)
    {
        if(mediaDragged!=null)
        {
            if(mediaFrameWidth<1||mediaFrameHeight<1)return;
            double x=mediaStartX+(mouseX-mediaMouseX)/
                    (double)mediaFrameWidth;
            double y=mediaStartY+(mouseY-mediaMouseY)/
                    (double)mediaFrameHeight;
            // Off-frame positioning is intentionally allowed for transitions.
            x=Math.max(-2D,Math.min(3D,x));
            y=Math.max(-2D,Math.min(3D,y));
            if(mediaDraggedKey!=null)
            {
                mediaDraggedKey.media.put("x",x);
                mediaDraggedKey.media.put("y",y);
                mediaDirty=true;
            }
            else if(!Double.toString(x).equals(mediaDragged.settings.get("x"))||
                    !Double.toString(y).equals(mediaDragged.settings.get("y")))
            {
                mediaDragged.settings.put("x",Double.toString(x));
                mediaDragged.settings.put("y",Double.toString(y));
                mediaDirty=true;
            }
            return;
        }
        if(scrubbingTimeline)
        {
            AnimationEditorScreen ui=(AnimationEditorScreen)
                    Minecraft.getMinecraft().currentScreen;
            if(ui!=null)
                ui.setCameraWorkspaceFrame(Math.max(0,Math.min(
                        length-1,cameraTimeline.getFrameFromMouseX(
                                mouseX,timelineX))));
            return;
        }
        if(numericDragCandidate!=null)
        {
            int dx=mouseX-numericStartX;
            if(!numericDragging&&Math.abs(dx)<3)return;
            numericDragging=true;
            // Cancel the click-to-type field only after an actual drag.
            editField=null;editKey=null;keyField=null;
            double multiplier=GuiScreen.isShiftKeyDown()?0.01D:
                    GuiScreen.isCtrlKeyDown()?1D:0.1D;
            double value=numericStartValue+dx*multiplier;
            CameraClipInspector.Hit field=numericDragCandidate;
            if(field.keyframe&&numericDragKey!=null)
            {
                if(field.key.startsWith("media."))
                {
                    String channel=field.key.substring(6);
                    if("opacity".equals(channel))
                        value=Math.max(0D,Math.min(1D,value));
                    if("width".equals(channel)||"height".equals(channel))
                        value=Math.max(0.001D,value);
                    numericDragKey.media.put(channel,value);
                }
                else if("x".equals(field.key))numericDragKey.x=value;
                else if("y".equals(field.key))numericDragKey.y=value;
                else if("z".equals(field.key))numericDragKey.z=value;
                else if("yaw".equals(field.key))numericDragKey.yaw=(float)value;
                else if("pitch".equals(field.key))numericDragKey.pitch=(float)value;
                else if("roll".equals(field.key))numericDragKey.roll=(float)value;
                else if("fov".equals(field.key))
                    numericDragKey.fov=(float)Math.max(1D,Math.min(170D,value));
            }
            else if(numericDragClip!=null)
            {
                String key=field.component>=0?
                        field.key+"#"+field.component:field.key;
                applyClipField(numericDragClip,key,
                        Double.toString(value));
            }
            return;
        }
        if(draggedKey!=null&&draggedKeyOwner!=null)
        {
            int candidate=draggedKeyStart+Math.round(
                    (mouseX-dragStartX)/cameraTimeline.getPixelsPerFrame());
            candidate=Math.max(0,Math.min(draggedKeyOwner.duration-1,candidate));
            if(cloneKey&&candidate!=draggedKeyStart)
            {
                CameraClipDocument.Key copy=draggedKey.copy();
                draggedKeyOwner.keys.add(copy);draggedKey=copy;cloneKey=false;
            }
            if(!cloneKey)draggedKey.tick=candidate;
            return;
        }
        if(dragged==null)return;
        int tick=dragStartTick+Math.round(
                (mouseX-dragStartX)/cameraTimeline.getPixelsPerFrame());
        if(resizeEdge!=0)
        {
            int delta=Math.round((mouseX-dragStartX)/
                    cameraTimeline.getPixelsPerFrame());
            long boundary=resizeEdge<0?(long)dragStartTick+delta:
                    (long)dragStartTick+dragStartDuration+delta;
            if(boundary>=0L&&boundary<=Integer.MAX_VALUE)
                document.trim(dragged,resizeEdge<0,(int)boundary);
            return;
        }
        int layer=Math.max(0,(mouseY-rowBase)/rowHeight+layerScroll);
        if(cloneDrag)
        {
            CameraClipDocument.Clip copy=dragged.copy();
            copy.tick=Math.max(0,tick);copy.layer=layer;
            if(document.add(copy)){dragged=copy;cloneDrag=false;}
        }
        else document.move(dragged,tick,layer);
    }
    public void release()
    {
        if(mediaDirty)saveScene();
        mediaDragged=null;mediaDraggedKey=null;mediaDirty=false;
        scrubbingTimeline=false;
        if(numericDragging)saveScene();
        numericDragCandidate=null;numericDragClip=null;
        numericDragKey=null;numericDragging=false;
        if(navigationDirty){saveScene();navigationDirty=false;}
        if(dragged!=null||draggedKey!=null)
        {
            if(draggedKeyOwner!=null)
                java.util.Collections.sort(draggedKeyOwner.keys,
                    new java.util.Comparator<CameraClipDocument.Key>(){
                        @Override public int compare(CameraClipDocument.Key a,
                                CameraClipDocument.Key b)
                        {return Integer.compare(a.tick,b.tick);}
                    });
            saveScene();
        }
        dragged=null;draggedKey=null;draggedKeyOwner=null;
        cloneDrag=false;cloneKey=false;resizeEdge=0;
    }
}
