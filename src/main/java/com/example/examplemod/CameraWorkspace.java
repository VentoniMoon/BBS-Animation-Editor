package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * CAMERA workspace: BBS Animation Editor themed preset shelf, clip inspector,
 * layer timeline and three-column BBS FS-inspired clip picker. The normal POSE,
 * CHARACTER and BODY PARTS tools are never invoked for this mode.
 */
public final class CameraWorkspace
{
    private static final int HEADER=25, ROW=21, TIMELINE_HEIGHT=180;
    private static final long DOUBLE_MS=320;
    private CameraClipTrack.Clip selected;
    private Integer selectedKey;
    private CameraClipTrack.Clip drag;
    private Integer dragKey;
    private int dragStartX, dragStartFrame, dragStartLayer, dragStartDuration, dragOriginalKey;
    private boolean dragging, resizing, copied;
    private int playheadAtClick;
    private long lastLeft,lastRight;
    private CameraClipTrack.Clip lastLeftClip,lastRightClip;
    private boolean picker, presetPrompt, contextMenu;
    private int pickerFrame,pickerLayer;
    private String presetName="",editorValue="",editorField="";
    private boolean editingNumber, editingString;
    private int inspectorScroll,presetScroll;
    private int lastWidth,lastHeight,lastLeftWidth;
    private final List<String> presets=new ArrayList<String>();
    private String search="";
    private boolean searchFocused;
    private int lastClickX,lastClickY;
    private PreviewCameraPose freePose;
    private EditorCamera.Mode freeMode=EditorCamera.Mode.FREE;
    private boolean shotMode;

    public void resetForScene()
    {
        selected=null;selectedKey=null;drag=null;dragKey=null;
        picker=false;contextMenu=false;presetPrompt=false;
        shotMode=false;freePose=null;inspectorScroll=0;
    }

    public boolean isShotMode(){return shotMode;}

    public void toggleShot(EditorSceneViewport viewport)
    {
        if(viewport==null)return;
        if(!shotMode)
        {
            freePose=viewport.getCamera().sample();
            freeMode=viewport.getCamera().getMode();
            shotMode=true;
        }
        else
        {
            shotMode=false;
            if(freePose!=null)
            {
                viewport.getCamera().apply(freePose);
                viewport.setCameraMode(freeMode==EditorCamera.Mode.TRACK?
                    EditorCamera.Mode.FREE:freeMode);
            }
            freePose=null;
        }
    }

    public void updateShot(EditorSceneViewport viewport,CameraClipTrack clips,
        EditorSceneState scene,int frame)
    {
        if(!shotMode||viewport==null||clips==null)return;
        PreviewCameraPose base=freePose==null?viewport.getCamera().sample():freePose;
        viewport.applyCameraTrack(clips.sample(frame,base,scene));
    }

    private static int tint(){return EditorThemeManager.get().getAccent();}
    private static int bright(){return EditorThemeManager.get().getAccentBright();}
    private static final int BG=0xFF161A20, DARK=0xFF0E1319, LINE=0xFF34404C;
    private static final int TEXT=0xFFE0E6EC, MUTED=0xFFA0B3C0;
    private static void box(int x,int y,int w,int h,int color)
    {if(w>0&&h>0)Gui.drawRect(x,y,x+w,y+h,color);}
    private static void label(FontRenderer f,String s,int x,int y,int color)
    {f.drawString(s,x,y,color);}
    private static boolean inside(int x,int y,int bx,int by,int bw,int bh)
    {return x>=bx&&x<bx+bw&&y>=by&&y<by+bh;}

    public void drawLeft(Minecraft mc,int width,int height,int left)
    {
        this.lastWidth=width;this.lastHeight=height;this.lastLeftWidth=left;
        FontRenderer f=mc.fontRenderer;
        int bottom=height-TIMELINE_HEIGHT;
        box(0,HEADER,left,bottom-HEADER,BG);
        box(left-1,HEADER,1,bottom-HEADER,LINE);
        box(0,HEADER,left,25,DARK);
        box(9,HEADER+7,2,11,tint());
        label(f,"PRESETS",16,HEADER+8,TEXT);
        box(7,HEADER+31,left-14,20,0xFF272F39);
        label(f,searchFocused?"Search: "+search+"_":"Search: "+search,14,HEADER+37,MUTED);
        label(f,"Saved Camera Clips",12,HEADER+60,bright());
        refreshPresetList();
        int shown=0;
        for(int i=presetScroll;i<presets.size();i++)
        {
            String name=presets.get(i);
            if(!search.isEmpty()&&!name.toLowerCase().contains(search.toLowerCase()))continue;
            int y=HEADER+82+shown*20;
            if(y+18>=bottom-27)break;
            box(8,y,left-16,19,0xFF242B32);
            label(f,"\u25a7 "+shorten(name,f,left-34),12,y+5,TEXT);
            shown++;
        }
        box(0,bottom-26,left,26,DARK);
        label(f,"Open Preset Folder",12,bottom-17,MUTED);
    }

    private static String shorten(String s,FontRenderer f,int limit)
    {
        if(s==null)return "";
        String original=s;
        int n=original.length();
        while(n>0&&f.getStringWidth(original.substring(0,n)+(n<original.length()?"...":""))>limit)n--;
        return n==0?"":original.substring(0,n)+(n<original.length()?"...":"");
    }

    public void drawInspector(Minecraft mc,int panelX,int top,int panelWidth,int bottom,
        CameraClipTrack track,int frame)
    {
        FontRenderer f=mc.fontRenderer;
        box(panelX,top,panelWidth,bottom-top,BG);
        box(panelX,top,1,bottom-top,LINE);
        box(panelX,top,panelWidth,25,DARK);
        box(panelX+9,top+7,2,11,tint());
        label(f,"CLIP PROPERTIES",panelX+16,top+8,TEXT);
        if(selected==null)
        {
            label(f,"No clip selected",panelX+10,top+39,MUTED);
            label(f,"Double-click empty track",panelX+10,top+55,MUTED);
            label(f,"to add a Camera Clip.",panelX+10,top+69,MUTED);
            box(panelX+9,top+89,panelWidth-18,21,0xFF303946);
            label(f,shotMode?"[SHOT] Show camera":"[FREE] Preview camera",
                panelX+16,top+96,bright());
            return;
        }
        label(f,selected.type.label+"  (Layer "+selected.layer+")",panelX+10,top+31,
            selected.type.color);
        String[] properties=properties();
        int visible=Math.max(0,(bottom-top-89)/20);
        inspectorScroll=Math.max(0,Math.min(inspectorScroll,
            Math.max(0,properties.length-visible)));
        for(int i=0;i<visible&&i+inspectorScroll<properties.length;i++)
        {
            String field=properties[i+inspectorScroll];
            int y=top+52+i*20;
            if(y+18>=bottom)break;
            box(panelX+8,y,panelWidth-16,19,(i&1)==0?0xFF242A32:0xFF1C2229);
            String value=displayProperty(field,frame);
            label(f,shorten(field,f,78),panelX+12,y+5,MUTED);
            String display=(editingNumber||editingString)&&field.equals(editorField)
                ?editorValue+"_":value;
            label(f,shorten(display,f,panelWidth-100),panelX+96,y+5,TEXT);
        }
        if(selectedKey!=null)
        {
            box(panelX+8,bottom-26,panelWidth-16,21,0xFF263F45);
            label(f,"KEY @ "+(selected.tick+selectedKey),panelX+15,bottom-20,bright());
        }
    }

    private String[] properties()
    {
        if(selected==null)return new String[0];
        List<String> list=new ArrayList<String>();
        Collections.addAll(list,"Title","Start","Duration","Layer","Enabled","FadeIn","FadeOut");
        if(selected.type.keyed)
        {
            list.add("Key Local Frame");
            Collections.addAll(list,"x","y","z","yaw","pitch","roll","fov");
        }
        for(String key:selected.values.keySet())
        {
            if(!list.contains(key))list.add(key);
        }
        if(selected.type==CameraClipTrack.Type.IMAGE||selected.type==CameraClipTrack.Type.VIDEO||
           selected.type==CameraClipTrack.Type.AUDIO||selected.type==CameraClipTrack.Type.SUBTITLE||
           selected.type==CameraClipTrack.Type.TRACKER||selected.type==CameraClipTrack.Type.SPLINE||
           selected.type==CameraClipTrack.Type.LOOK)
        {
            if(selected.type==CameraClipTrack.Type.SUBTITLE)list.add("text");
            else if(selected.type==CameraClipTrack.Type.TRACKER||
                    selected.type==CameraClipTrack.Type.SPLINE||
                    selected.type==CameraClipTrack.Type.LOOK)list.add("target");
            else list.add("resource");
        }
        return list.toArray(new String[0]);
    }

    private String displayProperty(String field,int frame)
    {
        if(selected==null)return "";
        switch(field)
        {
        case "Title":return selected.title;
        case "Start":return ""+selected.tick;
        case "Duration":return ""+selected.duration;
        case "Layer":return ""+selected.layer;
        case "Enabled":return selected.enabled?"Yes":"No";
        case "FadeIn":return ""+selected.fadeIn;
        case "FadeOut":return ""+selected.fadeOut;
        case "Key Local Frame":return selectedKey==null?"-":""+selectedKey;
        default:
            if(selected.strings.containsKey(field))return selected.text(field);
            return selected.values.containsKey(field)?
                String.format(java.util.Locale.ROOT,"%.3f",selected.value(field,0)):"";
        }
    }

    public void drawTimeline(Minecraft mc,int width,int height,int left,
        EditorTimeline timeline,CameraClipTrack track,int currentFrame,int length)
    {
        FontRenderer f=mc.fontRenderer;
        int top=height-TIMELINE_HEIGHT;
        box(0,top,width,TIMELINE_HEIGHT,DARK);
        box(0,top,width,1,tint());
        box(0,top+1,width,34,0xFF162532);
        box(9,top+11,2,12,tint());
        label(f,"CAMERA TIMELINE",16,top+12,TEXT);
        label(f,"Scene "+length+" frames",152,top+12,MUTED);
        int start=left;
        box(0,top+35,left,TIMELINE_HEIGHT-35,0xFF0D1C27);
        int graphY=top+35;
        box(left,graphY,width-left,24,0xFF101E29);
        float scale=timeline.getPixelsPerFrame();
        int offset=timeline.getOffset();
        for(int tick=0;tick<Math.max(150,length+50);tick+=10)
        {
            int x=start+Math.round(tick*scale)-offset;
            if(x<start||x>=width)continue;
            box(x,graphY+10,1,TIMELINE_HEIGHT-45,(tick%20)==0?0xFF34546B:0xFF203547);
            label(f,(tick%20)==0?""+tick:"",x+2,graphY+3,MUTED);
        }
        for(int row=0;row<5;row++)
        {
            int layer=4-row;
            int y=graphY+24+row*ROW;
            box(0,y,left,ROW,(row&1)==0?0xFF0F202B:0xFF112531);
            label(f,"Layer "+layer,12,y+6,MUTED);
            box(left,y,width-left,ROW,(row&1)==0?0xFF162836:0xFF172D3C);
            box(0,y+ROW-1,width,1,0xFF2A3E4E);
        }
        for(CameraClipTrack.Clip c:track.all())
        {
            int row=4-c.layer;if(row<0||row>=5)continue;
            int y=graphY+24+row*ROW+2;
            int x=timeline.getFrameX(c.tick,left);
            int ex=timeline.getFrameX(c.tick+c.duration,left);
            if(ex<left||x>width)continue;
            int clipX=Math.max(left,x),clipW=Math.max(1,Math.min(width,ex)-clipX);
            box(clipX,y,clipW,ROW-4,c.enabled?c.type.color:0xFF515D65);
            if(c==selected)
            {
                box(clipX,y,clipW,1,0xFFFFFFFF);
                box(clipX,y+ROW-5,clipW,1,0xFFFFFFFF);
            }
            label(f,shorten(c.title,f,Math.max(10,clipW-5)),clipX+3,y+5,0xFFFFFFFF);
            if(c.type.keyed)
            {
                for(Integer k:c.keys.keySet())
                {
                    int keyX=timeline.getFrameX(c.tick+k,left);
                    if(keyX<clipX||keyX>=clipX+clipW)continue;
                    box(keyX-1,y+2,3,ROW-8,k.equals(selectedKey)&&selected==c?
                        0xFFFFFFFF:0xFFEAF9EE);
                }
            }
        }
        int cursor=timeline.getFrameX(currentFrame,left);
        if(cursor>=left&&cursor<width)
        {
            box(cursor,graphY,2,TIMELINE_HEIGHT-35,bright());
            box(cursor-3,graphY,8,5,bright());
        }
        label(f,"Paused",10,height-14,MUTED);
        label(f,shotMode?"SHOT":"FREE",width-37,top+12,bright());
    }

    public void drawPopups(Minecraft mc,int width,int height)
    {
        FontRenderer f=mc.fontRenderer;
        if(picker)
        {
            int w=Math.min(width-30,535),h=Math.min(height-36,310);
            int x=(width-w)/2,y=(height-h)/2;
            box(0,0,width,height,0xAA000000);
            box(x,y,w,h,BG);box(x,y,w,24,DARK);
            label(f,"ADD CAMERA CLIP",x+10,y+8,TEXT);
            label(f,"X",x+w-14,y+8,MUTED);
            int cw=(w-25)/3;
            CameraClipTrack.Category[] cats=CameraClipTrack.Category.values();
            String[] titles={"Overwrite","Modifiers","Misc"};
            for(int ci=0;ci<3;ci++)
            {
                int bx=x+8+ci*cw;
                label(f,titles[ci],bx,y+32,bright());
                int r=0;
                for(CameraClipTrack.Type type:CameraClipTrack.Type.values())
                {
                    if(type.category!=cats[ci])continue;
                    int yy=y+48+r*23;
                    if(yy+22<y+h)
                    {
                        box(bx,yy,3,19,type.color);
                        label(f,type.label,bx+9,yy+5,TEXT);
                    }
                    r++;
                }
            }
        }
        if(contextMenu&&selected!=null)
        {
            int x=Math.max(0,Math.min(lastClickX,width-132));
            int y=Math.max(HEADER,Math.min(lastClickY,height-71));
            box(x,y,132,70,BG);
            label(f,"Save as Preset",x+8,y+10,TEXT);
            label(f,"Copy Clip",x+8,y+31,TEXT);
            label(f,"Delete Clip",x+8,y+52,0xFFF3A0A0);
        }
        if(presetPrompt)
        {
            box(0,0,width,height,0x99000000);
            int w=Math.min(width-20,295),x=(width-w)/2,y=height/2-48;
            box(x,y,w,98,BG);box(x,y,w,24,DARK);
            label(f,"SAVE CAMERA PRESET",x+10,y+8,TEXT);
            box(x+10,y+32,w-20,20,0xFF303946);
            label(f,presetName+"_",x+16,y+39,TEXT);
            label(f,"Enter = Save    Esc = Cancel",x+12,y+69,MUTED);
        }
    }

    private static File directory()
    {
        File f=new File(Minecraft.getMinecraft().mcDataDir,
            "config/bbs-editor/presets/camera");
        f.mkdirs();return f;
    }

    private void refreshPresetList()
    {
        File[] a=directory().listFiles((d,n)->n.endsWith(".dat"));
        presets.clear();
        if(a!=null)
        {
            Arrays.sort(a,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
            for(File file:a)presets.add(file.getName().substring(0,file.getName().length()-4));
        }
    }

    private void savePreset()
    {
        if(selected==null||presetName.trim().isEmpty())return;
        String safe=presetName.trim().replaceAll("[^a-zA-Z0-9_\\-\\p{L} ]","_");
        if(safe.isEmpty())return;
        File target=new File(directory(),safe+".dat");
        try(FileOutputStream out=new FileOutputStream(target))
        {
            CompressedStreamTools.writeCompressed(selected.toNBT(),out);
        }
        catch(IOException e){System.err.println("[BBS Editor][CAMERA] Preset save: "+e);}
        presetName="";presetPrompt=false;refreshPresetList();
    }

    private void loadPreset(String name,CameraClipTrack track,
        int frame,int layer)
    {
        File file=new File(directory(),name+".dat");
        try(FileInputStream in=new FileInputStream(file))
        {
            CameraClipTrack.Clip c=CameraClipTrack.Clip.fromNBT(
                CompressedStreamTools.readCompressed(in));
            if(c==null)return;
            c.tick=Math.max(0,frame);
            c.layer=Math.max(0,layer);
            while(track.conflicts(null,c.layer,c.tick,c.duration)&&c.layer<99)c.layer++;
            track.add(c);selected=c;selectedKey=null;
        }
        catch(IOException e){System.err.println("[BBS Editor][CAMERA] Preset load: "+e);}
    }

    public boolean click(int x,int y,int button,int width,int height,int left,
        EditorTimeline timeline,EditorPlaybackController playback,
        CameraClipTrack track,PreviewCameraPose camera)
    {
        this.lastWidth=width;this.lastHeight=height;this.lastLeftWidth=left;
        int top=height-TIMELINE_HEIGHT;
        long now=System.currentTimeMillis();

        if(presetPrompt)
        {
            if(button==0&&!inside(x,y,(width-295)/2,height/2-48,295,98))
                presetPrompt=false;
            return true;
        }
        if(picker)
        {
            int w=Math.min(width-30,535),h=Math.min(height-36,310);
            int bx=(width-w)/2,by=(height-h)/2,cw=(w-25)/3;
            if(button==0)
            {
                if(inside(x,y,bx,by,w,h))
                {
                    int ci=(x-bx-8)/cw;
                    if(ci>=0&&ci<3)
                    {
                        int row=(y-(by+48))/23;
                        if(row>=0)
                        {
                            int n=0;
                            for(CameraClipTrack.Type type:CameraClipTrack.Type.values())
                            {
                                if(type.category!=CameraClipTrack.Category.values()[ci])continue;
                                if(n++==row)
                                {
                                    CameraClipTrack.Clip c=new CameraClipTrack.Clip(type,camera);
                                    c.tick=pickerFrame;c.layer=pickerLayer;
                                    c.duration=Math.max(1,Math.min(40,Math.max(1,timeline.getLength()-pickerFrame)));
                                    while(track.conflicts(null,c.layer,c.tick,c.duration)&&c.layer<99)c.layer++;
                                    track.add(c);selected=c;selectedKey=null;
                                    picker=false;return true;
                                }
                            }
                        }
                    }
                }
                picker=false;
            }
            return true;
        }
        if(contextMenu)
        {
            int cx=Math.max(0,Math.min(lastClickX,width-132));
            int cy=Math.max(HEADER,Math.min(lastClickY,height-71));
            contextMenu=false;
            if(button==0&&inside(x,y,cx,cy,132,70))
            {
                int row=(y-cy)/23;
                if(row==0){presetPrompt=true;presetName=selected.title;}
                else if(row==1&&selected!=null)
                {
                    CameraClipTrack.Clip c=selected.copy();
                    c.tick=Math.min(timeline.getLastTick(),c.tick+5);
                    while(track.conflicts(null,c.layer,c.tick,c.duration)&&c.layer<99)c.layer++;
                    track.add(c);selected=c;selectedKey=null;
                }
                else if(row==2){track.remove(selected);selected=null;selectedKey=null;}
            }
            return true;
        }
        if(x<left&&y>=HEADER&&y<top)
        {
            if(inside(x,y,7,HEADER+31,left-14,22))
            {
                searchFocused=true;return true;
            }
            searchFocused=false;
            int index=0;
            for(int i=presetScroll;i<presets.size();i++)
            {
                String name=presets.get(i);
                if(!search.isEmpty()&&!name.toLowerCase().contains(search.toLowerCase()))continue;
                int yy=HEADER+82+index*20;
                if(y>=yy&&y<yy+20)
                {
                    loadPreset(name,track,playback.getCurrentFrame(),0);
                    return true;
                }
                index++;
            }
            return true;
        }
        if(x>=width-185&&y>=HEADER&&y<top)
        {
            if(selected==null)
            {
                if(inside(x,y,width-185+9,HEADER+89,167,21))
                    toggleShotFromClick=true;
                return true;
            }
            int idx=(y-(HEADER+52))/20+inspectorScroll;
            String[] p=properties();
            if(button==0&&idx>=0&&idx<p.length)
            {
                String field=p[idx];
                if("Enabled".equals(field)){selected.enabled=!selected.enabled;return true;}
                editorField=field;editorValue=displayProperty(field,playback.getCurrentFrame());
                editingString=field.equals("Title")||field.equals("resource")||
                    field.equals("target")||field.equals("text");
                editingNumber=!editingString;
            }
            return true;
        }
        if(y>=top)
        {
            int graphY=top+35;
            if(y>=graphY&&y<graphY+24)
            {
                int frame=Math.max(0,timeline.getFrameFromMouseX(x,left));
                playback.setCurrentFrame(frame);return true;
            }
            int row=(y-(graphY+24))/ROW;
            if(row<0||row>=5||x<left)return true;
            int layer=4-row;
            int frame=Math.max(0,timeline.getFrameFromMouseX(x,left));
            CameraClipTrack.Clip hit=track.at(layer,frame);
            if(button==0)
            {
                if(hit!=null)
                {
                    boolean doubleClick=now-lastLeft<DOUBLE_MS&&hit==lastLeftClip;
                    lastLeft=now;lastLeftClip=hit;
                    if(doubleClick&&hit.type.keyed)
                    {
                        hit.keys.put(Math.max(0,playback.getCurrentFrame()-hit.tick),camera);
                        selected=hit;selectedKey=Math.max(0,playback.getCurrentFrame()-hit.tick);
                        drag=null;return true;
                    }
                    selected=hit;selectedKey=null;
                    int edge=timeline.getFrameX(hit.tick+hit.duration,left);
                    resizing=Math.abs(x-edge)<5;
                    dragKey=null;
                    for(Integer k:hit.keys.keySet())
                    {
                        if(Math.abs(x-timeline.getFrameX(hit.tick+k,left))<=3)
                        {dragKey=k;selectedKey=k;break;}
                    }
                    drag=hit;dragStartX=x;dragStartFrame=hit.tick;
                    dragStartLayer=hit.layer;dragStartDuration=hit.duration;
                    dragOriginalKey=dragKey==null?-1:dragKey;playheadAtClick=frame;
                    dragging=false;copied=false;
                }
                else
                {
                    boolean doubleClick=now-lastLeft<DOUBLE_MS&&lastLeftClip==null;
                    lastLeft=now;lastLeftClip=null;
                    if(doubleClick)
                    {
                        picker=true;pickerFrame=frame;pickerLayer=layer;
                    }
                    else{playback.setCurrentFrame(frame);selected=null;selectedKey=null;}
                }
                return true;
            }
            if(button==1&&hit!=null)
            {
                if(now-lastRight<DOUBLE_MS&&hit==lastRightClip)
                {
                    if(selected==hit&&selectedKey!=null&&hit.keys.containsKey(selectedKey))
                        hit.keys.remove(selectedKey);
                    else track.remove(hit);
                    if(selected==hit){selected=null;selectedKey=null;}
                    contextMenu=false;lastRight=0;return true;
                }
                lastRight=now;lastRightClip=hit;selected=hit;
                contextMenu=true;lastClickX=x;lastClickY=y;return true;
            }
            return true;
        }
        return false;
    }

    /** One-shot instruction to the screen to toggle FREE/SHOT. */
    private boolean toggleShotFromClick;
    public boolean consumeShotToggle()
    {boolean v=toggleShotFromClick;toggleShotFromClick=false;return v;}

    public boolean drag(int x,int y,int button,EditorTimeline timeline,
        CameraClipTrack track)
    {
        if(drag==null||button!=0)return false;
        if(Math.abs(x-dragStartX)<3&&!dragging)return true;
        if(!copied&&(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||
             Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)))
        {
            if(dragKey!=null)
            {
                PreviewCameraPose key=drag.keys.get(dragKey);
                if(key!=null)drag.keys.put(Math.max(0,dragKey+1),key);
                dragKey=Math.max(0,dragKey+1);
            }
            else
            {
                CameraClipTrack.Clip copy=drag.copy();
                track.add(copy);drag=copy;selected=copy;
            }
            copied=true;
        }
        dragging=true;
        int delta=Math.round((x-dragStartX)/timeline.getPixelsPerFrame());
        if(dragKey!=null)
        {
            int next=Math.max(0,Math.min(drag.duration-1,dragOriginalKey+delta));
            PreviewCameraPose key=drag.keys.get(dragKey);
            if(key!=null&&(next==dragKey||!drag.keys.containsKey(next)))
            {
                drag.keys.remove(dragKey);
                drag.keys.put(next,key);selectedKey=next;
            }
            return true;
        }
        int newTick=Math.max(0,dragStartFrame+delta);
        if(resizing)
        {
            int next=Math.max(1,dragStartDuration+delta);
            if(!track.conflicts(drag,drag.layer,drag.tick,next))drag.duration=next;
        }
        else
        {
            int newLayer=dragStartLayer;
            // Dragging vertically uses exact row hit rather than pointer delta.
            int row=(y-(lastHeight-TIMELINE_HEIGHT+35+24))/ROW;
            if(row>=0&&row<5)newLayer=4-row;
            if(!track.conflicts(drag,newLayer,newTick,drag.duration))
            {drag.tick=newTick;drag.layer=newLayer;}
        }
        return true;
    }

    public void release(){drag=null;dragKey=null;resizing=false;dragging=false;copied=false;}

    public boolean key(char c,int keyCode,CameraClipTrack track,EditorTimeline timeline)
    {
        if(presetPrompt)
        {
            if(keyCode==Keyboard.KEY_ESCAPE){presetPrompt=false;return true;}
            if(keyCode==Keyboard.KEY_RETURN||keyCode==Keyboard.KEY_NUMPADENTER)
            {savePreset();return true;}
            if(keyCode==Keyboard.KEY_BACK&&presetName.length()>0)
                presetName=presetName.substring(0,presetName.length()-1);
            else if(c>=32&&presetName.length()<64)presetName+=c;
            return true;
        }
        if(picker)
        {
            if(keyCode==Keyboard.KEY_ESCAPE)picker=false;
            return true;
        }
        if(editingNumber||editingString)
        {
            if(keyCode==Keyboard.KEY_ESCAPE)
            {editingNumber=false;editingString=false;return true;}
            if(keyCode==Keyboard.KEY_RETURN||keyCode==Keyboard.KEY_NUMPADENTER)
            {
                commitEdit(track,timeline);
                editingNumber=false;editingString=false;return true;
            }
            if(keyCode==Keyboard.KEY_BACK&&editorValue.length()>0)
                editorValue=editorValue.substring(0,editorValue.length()-1);
            else if(c>=32&&editorValue.length()<180&&
                (editingString||Character.isDigit(c)||c=='-'||c=='.'))
                editorValue+=c;
            return true;
        }
        if(searchFocused)
        {
            if(keyCode==Keyboard.KEY_ESCAPE){searchFocused=false;return true;}
            if(keyCode==Keyboard.KEY_BACK&&!search.isEmpty())
                search=search.substring(0,search.length()-1);
            else if(c>=32&&search.length()<64)search+=c;
            return true;
        }
        if(contextMenu&&keyCode==Keyboard.KEY_ESCAPE){contextMenu=false;return true;}
        if((keyCode==Keyboard.KEY_DELETE||keyCode==Keyboard.KEY_BACK)&&selected!=null)
        {
            if(selectedKey!=null)selected.keys.remove(selectedKey);
            else track.remove(selected);
            selected=null;selectedKey=null;return true;
        }
        return false;
    }

    private void commitEdit(CameraClipTrack track,EditorTimeline timeline)
    {
        if(selected==null)return;
        String s=editorValue.trim();
        if(editingString)
        {
            if("Title".equals(editorField))selected.title=s;
            else selected.strings.put(editorField,s);
            return;
        }
        double number;
        try{number=Double.parseDouble(s);}catch(Exception e){return;}
        if(!Double.isFinite(number))return;
        switch(editorField)
        {
        case "Start":
            int t=Math.max(0,(int)number);
            if(!track.conflicts(selected,selected.layer,t,selected.duration))selected.tick=t;
            break;
        case "Duration":
            int d=Math.max(1,(int)number);
            if(!track.conflicts(selected,selected.layer,selected.tick,d))selected.duration=d;
            break;
        case "Layer":
            int layer=Math.max(0,(int)number);
            if(!track.conflicts(selected,layer,selected.tick,selected.duration))selected.layer=layer;
            break;
        case "FadeIn":selected.fadeIn=Math.max(0,number);break;
        case "FadeOut":selected.fadeOut=Math.max(0,number);break;
        case "Key Local Frame":
            if(selectedKey!=null)
            {
                PreviewCameraPose p=selected.keys.remove(selectedKey);
                selectedKey=Math.max(0,(int)number);
                if(p!=null)selected.keys.put(selectedKey,p);
            }
            break;
        default:
            selected.values.put(editorField,number);
            if(selected.type.keyed&&selectedKey!=null&&selected.keys.containsKey(selectedKey)
                &&Arrays.asList("x","y","z","yaw","pitch","roll","fov").contains(editorField))
            {
                PreviewCameraPose old=selected.keys.get(selectedKey);
                selected.keys.put(selectedKey,new PreviewCameraPose(
                    "x".equals(editorField)?number:old.x,
                    "y".equals(editorField)?number:old.y,
                    "z".equals(editorField)?number:old.z,
                    "yaw".equals(editorField)?(float)number:old.yaw,
                    "pitch".equals(editorField)?(float)number:old.pitch,
                    "roll".equals(editorField)?(float)number:old.roll,
                    "fov".equals(editorField)?Math.max(1,Math.min(170,(float)number)):old.fov,
                    old.nearPlane,old.farPlane));
            }
            break;
        }
    }

    public boolean wheel(int x,int y,int direction,int height,int width)
    {
        if(x<lastLeftWidth&&y>=HEADER&&y<height-TIMELINE_HEIGHT)
        {presetScroll=Math.max(0,presetScroll-direction);return true;}
        if(x>=width-185&&y>=HEADER&&y<height-TIMELINE_HEIGHT)
        {inspectorScroll=Math.max(0,inspectorScroll-direction);return true;}
        return false;
    }
}
