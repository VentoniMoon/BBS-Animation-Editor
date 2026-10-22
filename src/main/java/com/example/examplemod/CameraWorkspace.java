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
import net.minecraft.client.Minecraft;

/** First CAMERA workspace surface using the existing Ocean/editor widgets. */
public final class CameraWorkspace
{
    public final CameraClipDocument document=new CameraClipDocument();
    private boolean shot;
    public PreviewCameraPose sample(PreviewCameraPose editorPose,int sceneFrame)
    {
        return shot?document.sample(sceneFrame,editorPose):editorPose;
    }
    public boolean shot(){return shot;}
    private boolean palette;
    private String activeSceneId;
    private File activeSceneFile;
    private String lastError;
    private final List<String> presetNames=new ArrayList<String>();
    private String savePresetName;
    private int presetScroll;

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
        if(id.equals(activeSceneId))return;
        activeSceneId=id;
        activeSceneFile=scene==null?null:sceneDocument(scene);
        document.clear();
        if(activeSceneFile!=null&&activeSceneFile.isFile())
        {
            try(Reader reader=new FileReader(activeSceneFile))
            {
                document.readJson(new JsonParser().parse(reader).getAsJsonObject());
            }
            catch(Exception e){lastError="Cannot read camera scene: "+e.getMessage();}
        }
        refreshPresets();
    }
    public void saveScene()
    {
        if(activeSceneFile==null)return;
        if(!activeSceneFile.getParentFile().isDirectory() &&
                !activeSceneFile.getParentFile().mkdirs())return;
        try(Writer writer=new FileWriter(activeSceneFile))
        {
            new GsonBuilder().setPrettyPrinting().create()
                    .toJson(document.toJson(),writer);
        }
        catch(IOException e){lastError="Cannot save camera scene: "+e.getMessage();}
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
        CameraClipDocument.Clip clip=document.selected();
        if(clip==null||requested==null)return false;
        String name=requested.trim().replaceAll("[^a-zA-Z0-9._-]","_");
        if(name.isEmpty()||name.equals(".")||name.equals(".."))return false;
        File folder=presetsDir();
        if(!folder.isDirectory()&&!folder.mkdirs())return false;
        try(Writer writer=new FileWriter(new File(folder,name+".json")))
        {
            new GsonBuilder().setPrettyPrinting().create()
                    .toJson(CameraClipDocument.clipToJson(clip),writer);
            refreshPresets();return true;
        }
        catch(IOException e){lastError="Cannot save preset: "+e.getMessage();return false;}
    }
    public boolean pastePreset(String name,int tick,int layer)
    {
        File file=new File(presetsDir(),name+".json");
        if(!file.isFile())return false;
        try(Reader reader=new FileReader(file))
        {
            CameraClipDocument.Clip clip=CameraClipDocument.clipFromJson(
                    new JsonParser().parse(reader).getAsJsonObject());
            clip.tick=Math.max(0,tick);clip.layer=Math.max(0,layer);
            int tries=0;
            while(document.overlaps(clip,null)&&tries++<64)clip.layer++;
            if(!document.add(clip))return false;
            saveScene();return true;
        }
        catch(Exception e){lastError="Cannot paste preset: "+e.getMessage();return false;}
    }
    private int pendingTick,pendingLayer;
    private int lastClickX,lastClickY,lastButton=-1;
    private long lastClick;
    private CameraClipDocument.Clip dragged;
    private int dragStartX,dragStartTick,dragStartLayer;
    private boolean cloneDrag;
    private int scaleStart,scaleEnd;
    private int right;
    private int top;
    private int bottom;
    private int rowHeight=23;
    private int rowBase;
    private int timelineX=180;
    private int timelineW;

    public void draw(AnimationEditorScreen screen,int frame,int length)
    {
        Minecraft mc=Minecraft.getMinecraft();
        int width=screen.width,height=screen.height;
        top=height-screen.getTimelineHeight();bottom=height;
        right=width-185;
        timelineW=Math.max(1,width-timelineX-8);
        rowBase=bottom-10;
        // FREE/SHOT selection is separate from the editor's FR/OR navigation.
        // Native camera driver samples this before vanilla renders the world.
        GuiScreen.drawRect(Math.max(181,(right+180)/2-58),top-28,
                Math.max(181,(right+180)/2+58),top-5,0xFF191E24);
        mc.fontRenderer.drawString(shot?"SHOT  [FREE]":"FREE  [SHOT]",
                Math.max(184,(right+180)/2-48),top-21,0xFF64CAFF);
        GuiScreen.drawRect(0,25,180,top,0xFF191E24);
        GuiScreen.drawRect(right,25,width,top,0xFF191E24);
        mc.fontRenderer.drawString("PRESETS",10,34,0xFF64CAFF);
        mc.fontRenderer.drawString("Saved clip collections",10,53,0xFF87939D);
        mc.fontRenderer.drawString("Right click clip to save",10,69,0xFF87939D);
        for(int i=0;i<presetNames.size()&&i<12;i++)
            mc.fontRenderer.drawString(presetNames.get(i),12,95+i*17,0xFFD1DAE0);
        if(lastError!=null)mc.fontRenderer.drawString(lastError,10,top-12,0xFFFF8888);
        mc.fontRenderer.drawString("CLIP PROPERTIES",right+8,34,0xFF64CAFF);
        CameraClipDocument.Clip selected=document.selected();
        if(selected!=null)
        {
            mc.fontRenderer.drawString(selected.kind.label,right+8,53,0xFFE7E7E7);
            mc.fontRenderer.drawString("Start: "+selected.tick,right+8,73,0xFFC0C5C9);
            mc.fontRenderer.drawString("Duration: "+selected.duration,right+8,89,0xFFC0C5C9);
            mc.fontRenderer.drawString("Layer: "+selected.layer,right+8,105,0xFFC0C5C9);
            mc.fontRenderer.drawString("Enabled: "+selected.enabled,right+8,121,0xFFC0C5C9);
            mc.fontRenderer.drawString("Keys: "+selected.keys.size(),right+8,137,0xFFC0C5C9);
            int yy=159;
            for(java.util.Map.Entry<String,String> property:selected.settings.entrySet())
            {
                if(yy+10>=top-10)break;
                mc.fontRenderer.drawString(property.getKey()+": "+property.getValue(),
                        right+8,yy,0xFF91A0A8);
                yy+=16;
            }
        }
        else mc.fontRenderer.drawString("Select a clip",right+8,54,0xFF8A959E);
        GuiScreen.drawRect(0,top,width,bottom,0xFF111D27);
        GuiScreen.drawRect(0,top,width,top+1,0xFF68C5EB);
        mc.fontRenderer.drawString("CAMERA TIMELINE",9,top+7,0xFF69CFFF);
        mc.fontRenderer.drawString("Scene: "+length+" frames",92,top+7,0xFFA6B4C0);
        int usable=Math.max(1,length);
        for(int n=0;n<=usable;n+=Math.max(1,usable/8))
        {
            int x=timelineX+(int)((long)n*timelineW/usable);
            GuiScreen.drawRect(x,top+28,x+1,bottom,0xFF334C5C);
            mc.fontRenderer.drawString(Integer.toString(n),x,top+16,0xFF9AB3C3);
        }
        for(int layer=0;layer<6;layer++)
        {
            int y=rowBase-(layer+1)*rowHeight;
            if(y<top+32)break;
            GuiScreen.drawRect(0,y,width,y+rowHeight-1,layer%2==0?0xFF142534:0xFF162A3A);
            mc.fontRenderer.drawString("Layer "+layer,10,y+6,0xFFA6B9C7);
        }
        for(CameraClipDocument.Clip clip:document.clips())
        {
            int x=timelineX+(int)((long)clip.tick*timelineW/usable);
            int x2=timelineX+(int)((long)(clip.tick+clip.duration)*timelineW/usable);
            int y=rowBase-(clip.layer+1)*rowHeight;
            if(y<top+32)continue;
            GuiScreen.drawRect(x,y+2,Math.max(x+3,x2),y+rowHeight-3,clip.kind.color);
            if(clip==selected)
            {
                GuiScreen.drawRect(x,y+1,x2,y+2,0xFFFFFFFF);
                GuiScreen.drawRect(x,y+rowHeight-3,x2,y+rowHeight-2,0xFFFFFFFF);
            }
            mc.fontRenderer.drawString(clip.title,x+4,y+6,0xFFFFFFFF);
            for(CameraClipDocument.Key key:clip.keys)
            {
                int kx=timelineX+(int)((long)(clip.tick+key.tick)*timelineW/usable);
                GuiScreen.drawRect(kx-1,y+4,kx+2,y+rowHeight-5,0xFFCBFFED);
            }
        }
        int cursor=timelineX+(int)((long)Math.max(0,frame)*timelineW/usable);
        GuiScreen.drawRect(cursor,top+26,cursor+1,bottom,0xFFE7BB65);
        if(palette)drawPalette(screen);
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
    public boolean click(AnimationEditorScreen screen,int mouseX,int mouseY,int button,int frame,int length)
    {
        int toggleX=Math.max(181,(right+180)/2-58);
        if(button==0&&mouseX>=toggleX&&mouseX<toggleX+116
                &&mouseY>=top-28&&mouseY<top-5)
        {
            shot=!shot;
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
                    document.add(CameraClipParameters.create(kind,pendingTick,
                            Math.max(1,Math.min(40,length-pendingTick)),pendingLayer));
                    palette=false;saveScene();return true;
                }
            }
            palette=false;return true;
        }
        if(mouseX<180&&mouseY>=85&&mouseY<85+Math.min(12,presetNames.size())*17&&button==0)
        {
            int i=(mouseY-85)/17;
            if(i>=0&&i<presetNames.size())
                pastePreset(presetNames.get(i),frame,0);
            return true;
        }
        if(mouseY<top||mouseX<timelineX||mouseY>=bottom)return false;
        int t=Math.max(0,Math.min(length-1,(int)((long)(mouseX-timelineX)*Math.max(1,length)/Math.max(1,timelineW))));
        int layer=(rowBase-mouseY)/rowHeight;
        if(layer<0||mouseY>rowBase)return true;
        CameraClipDocument.Clip hit=document.hit(t,layer);
        long now=System.currentTimeMillis();
        boolean twice=button==lastButton&&Math.abs(lastClickX-mouseX)<6&&Math.abs(lastClickY-mouseY)<6&&now-lastClick<350;
        lastClick=now;lastClickX=mouseX;lastClickY=mouseY;lastButton=button;
        if(button==0)
        {
            if(twice)
            {
                if(hit==null){pendingTick=t;pendingLayer=layer;palette=true;}
                else if(hit.kind.keyed){document.keyAt(hit,frame,screen.getCameraWorkspacePose());saveScene();}
                return true;
            }
            document.select(hit);
            dragged=hit;dragStartX=mouseX;
            if(hit!=null)
            {
                dragStartTick=hit.tick;dragStartLayer=hit.layer;
                cloneDrag=GuiScreen.isCtrlKeyDown();
            }
            return true;
        }
        if(button==1)
        {
            if(twice&&hit!=null){document.remove(hit);saveScene();}
            else if(hit!=null)
            {
                document.select(hit);
                // Enter preset name in chat input on the next iteration of
                // the inspector; do not silently save a hardcoded name.
            }
            return true;
        }
        return true;
    }
    public void move(int mouseX,int mouseY,int length)
    {
        if(dragged==null)return;
        int tick=dragStartTick+(int)((long)(mouseX-dragStartX)*Math.max(1,length)/Math.max(1,timelineW));
        int layer=Math.max(0,(rowBase-mouseY)/rowHeight);
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
        if(dragged!=null)saveScene();
        dragged=null;cloneDrag=false;
    }
}
