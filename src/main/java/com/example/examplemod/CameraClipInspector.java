package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * BBS FS-inspired clip inspector: typed, editable controls instead of a
 * one-line dump of the camera JSON settings. Fields are data-driven so all
 * registered clip types keep their own parameters without UI duplication.
 */
public final class CameraClipInspector
{
    public static final class Hit
    {
        public final String key;
        public final int component;
        public final int x, y, width;
        public final String value;
        public final boolean keyframe;
        public final boolean toggle;

        private Hit(String key,int component,int x,int y,int width,
                String value,boolean keyframe,boolean toggle)
        {
            this.key=key;this.component=component;
            this.x=x;this.y=y;this.width=width;this.value=value;
            this.keyframe=keyframe;this.toggle=toggle;
        }
        public boolean contains(int mx,int my)
        {
            return mx>=x&&mx<x+width&&my>=y&&my<y+19;
        }
    }

    private final List<Hit> hits=new ArrayList<Hit>();
    private int top, bottom, right, width, y, fullHeight;
    private int scroll;
    private Minecraft mc;
    private int accent;

    public Hit hit(int mouseX,int mouseY)
    {
        for(Hit field:hits)if(field.contains(mouseX,mouseY))return field;
        return null;
    }
    public int maxScroll()
    {
        return Math.max(0,fullHeight-(bottom-82));
    }

    private boolean visible(int at,int height)
    {
        return at>=80&&at+height<=bottom-8;
    }
    private void rectangle(int x1,int y1,int x2,int y2,int color)
    {
        GuiScreen.drawRect(x1,y1,x2,y2,color);
    }
    private void text(String value,int x,int y,int color,int available)
    {
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(value,Math.max(1,available)),
                x,y,color);
    }
    private static String label(String key)
    {
        if(key.startsWith("@"))key=key.substring(1);
        if("interpPoint".equals(key))return "Position interpolation";
        if("interpAngle".equals(key))return "Angle interpolation";
        if("look_at".equals(key))return "Look at";
        if("atBlock".equals(key))return "At block";
        if("path_rotation".equals(key))return "Path rotation";
        if("shakeAmount".equals(key))return "Shake amount";
        if("textShadow".equals(key))return "Text shadow";
        if("fontSize".equals(key))return "Font size";
        if("fadeIn".equals(key))return "Fade in";
        if("fadeOut".equals(key))return "Fade out";
        if("fullscreen".equals(key))return "Fullscreen";
        if("fov".equals(key))return "FOV";
        if("yaw".equals(key))return "Yaw";
        if("pitch".equals(key))return "Pitch";
        if("x".equals(key)||"y".equals(key)||"z".equals(key))
            return key.toUpperCase(java.util.Locale.ROOT);
        String result=key.replace('_',' ').replace('.',' ');
        return Character.toUpperCase(result.charAt(0))+result.substring(1);
    }
    private static boolean vector(String key,String value)
    {
        if(value==null||!value.contains(","))return false;
        String[] parts=value.split(",",-1);
        if(parts.length<2||parts.length>4)return false;
        for(String part:parts)
        {
            try
            {
                if(!Double.isFinite(Double.parseDouble(part.trim())))return false;
            }
            catch(RuntimeException invalid){return false;}
        }
        return true;
    }
    private static boolean isToggle(String value)
    {
        return "true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value);
    }
    private static String group(String key)
    {
        if(key.startsWith("envelope."))return "ENVELOPE";
        if("selector".equals(key)||"group".equals(key)||"relative".equals(key)
                ||"look_at".equals(key)||"atBlock".equals(key)
                ||"forward".equals(key)||"copy".equals(key)
                ||"absolute".equals(key))return "TARGET";
        if("position".equals(key)||"offset".equals(key)||"angle".equals(key)
                ||"translate".equals(key)||"yaw".equals(key)
                ||"pitch".equals(key)||"roll".equals(key)
                ||"distance".equals(key)||"fov".equals(key)
                ||"block".equals(key))return "TRANSFORM";
        if(key.toLowerCase(java.util.Locale.ROOT).contains("interp")
                ||"channel".equals(key)||"channels".equals(key)
                ||"points".equals(key)||"active".equals(key)
                ||"additive".equals(key)||"path_rotation".equals(key))
            return "ANIMATION";
        if("texture".equals(key)||"video".equals(key)||"audio".equals(key)
                ||"text".equals(key)||"font".equals(key)
                ||"fontSize".equals(key)||"placement".equals(key))
            return "MEDIA";
        return "CLIP SETTINGS";
    }
    private void section(String name)
    {
        int pos=y-scroll;
        if(visible(pos,21))
        {
            rectangle(right+5,pos,width-5,pos+19,0xFF29323A);
            rectangle(right+5,pos,right+8,pos+19,accent);
            text(name,right+14,pos+6,0xFFE7EDF2,width-right-24);
        }
        y+=25;
    }
    private void scalar(String key,String value,boolean isKey)
    {
        int pos=y-scroll;
        if(visible(pos,21))
        {
            text(label(key),right+12,pos+5,0xFFB5C1C9,
                    Math.max(65,(width-right)/2-12));
            int sx=right+Math.max(102,(width-right)/2),w=width-sx-10;
            rectangle(sx,pos,sx+w,pos+19,0xFF0C141B);
            rectangle(sx,pos+18,sx+w,pos+19,0xFF364B58);
            if(isToggle(value))
            {
                int bx=sx+w-18;
                rectangle(bx,pos+3,bx+12,pos+15,0xFF526574);
                if(Boolean.parseBoolean(value))
                    rectangle(bx+2,pos+5,bx+10,pos+13,accent);
                text(Boolean.parseBoolean(value)?"On":"Off",sx+6,pos+5,
                        0xFFD8E7EF,w-28);
            }
            else text(value,sx+6,pos+5,0xFFE5F0F4,w-12);
            hits.add(new Hit(key,-1,sx,pos,w,value,isKey,isToggle(value)));
        }
        y+=23;
    }
    private void coordinates(String key,String value)
    {
        String[] parts=value.split(",",-1);
        int pos=y-scroll;
        if(visible(pos,42))
        {
            text(label(key),right+12,pos+1,0xFFC8D6E0,width-right-24);
            int inner=width-right-24;
            int partWidth=inner/parts.length;
            String[] names=parts.length==4?
                    new String[]{"X","Y","Z","W"}:
                    parts.length==3?
                    new String[]{"X","Y","Z"}:
                    new String[]{"X","Y"};
            for(int i=0;i<parts.length;i++)
            {
                int x=right+12+i*partWidth;
                int w=i==parts.length-1?inner-i*partWidth-3:partWidth-4;
                rectangle(x,pos+17,x+w,pos+36,0xFF0D161F);
                rectangle(x,pos+35,x+w,pos+36,0xFF39505F);
                text(names[i]+":",x+3,pos+22,0xFF7ACBF0,16);
                text(parts[i].trim(),x+21,pos+22,0xFFE9F3F7,w-23);
                hits.add(new Hit(key,i,x,pos+17,w,parts[i].trim(),false,false));
            }
        }
        y+=43;
    }
    private void settingsGroup(String title,List<Map.Entry<String,String>> entries)
    {
        if(entries==null||entries.isEmpty())return;
        section(title);
        for(Map.Entry<String,String> entry:entries)
        {
            String key=entry.getKey(),value=entry.getValue();
            if(vector(key,value))coordinates(key,value);
            else scalar(key,value,false);
        }
    }
    public void draw(AnimationEditorScreen screen,
            CameraClipDocument.Clip clip,CameraClipDocument.Key keyframe,
            boolean editKey,int scroll,int right,int timelineTop)
    {
        this.mc=Minecraft.getMinecraft();
        this.right=right;this.width=screen.width;
        this.top=timelineTop;this.bottom=timelineTop;
        this.scroll=scroll;
        this.accent=EditorThemeManager.get().getAccent();
        this.hits.clear();
        rectangle(right,25,width,top,0xFF171D23);
        rectangle(right,25,right+2,top,0xFF253D4B);
        text("CLIP PROPERTIES",right+12,34,
                EditorThemeManager.get().getAccentBright(),width-right-24);
        if(clip==null)
        {
            text("Select a camera clip",right+12,67,0xFF8397A6,width-right-24);
            fullHeight=0;return;
        }
        rectangle(right+7,53,width-7,76,0xFF252E36);
        rectangle(right+7,53,right+10,76,clip.kind.color);
        text(clip.kind.label+"  /  "+clip.title,right+16,60,
                0xFFF0F5F8,width-right-32);
        y=82;
        section("GENERAL");
        scalar("@title",clip.title,false);
        scalar("@start",Integer.toString(clip.tick),false);
        scalar("@duration",Integer.toString(clip.duration),false);
        scalar("@layer",Integer.toString(clip.layer),false);
        scalar("@enabled",Boolean.toString(clip.enabled),false);

        // View and Dolly have an initial camera pose even without a manually
        // selected key marker. Show it where BBS exposes Position and Angle.
        if((clip.kind==CameraClipDocument.Kind.IDLE
                ||clip.kind==CameraClipDocument.Kind.DOLLY)
                &&keyframe!=null)
        {
            section("CAMERA POSE");
            scalar("x",Double.toString(keyframe.x),true);
            scalar("y",Double.toString(keyframe.y),true);
            scalar("z",Double.toString(keyframe.z),true);
            scalar("yaw",Float.toString(keyframe.yaw),true);
            scalar("pitch",Float.toString(keyframe.pitch),true);
            scalar("roll",Float.toString(keyframe.roll),true);
            scalar("fov",Float.toString(keyframe.fov),true);
        }

        Map<String,List<Map.Entry<String,String>>> groups=
                new LinkedHashMap<String,List<Map.Entry<String,String>>>();
        for(String name:new String[]{"TARGET","TRANSFORM","ANIMATION",
                "MEDIA","CLIP SETTINGS","ENVELOPE"})
            groups.put(name,new ArrayList<Map.Entry<String,String>>());
        for(Map.Entry<String,String> setting:clip.settings.entrySet())
            groups.get(group(setting.getKey())).add(setting);
        for(Map.Entry<String,List<Map.Entry<String,String>>> part:groups.entrySet())
            settingsGroup(part.getKey(),part.getValue());

        if(editKey&&keyframe!=null&&clip.kind!=CameraClipDocument.Kind.IDLE
                &&clip.kind!=CameraClipDocument.Kind.DOLLY)
        {
            section("SELECTED KEYFRAME");
            scalar("tick",Integer.toString(keyframe.tick),true);
            scalar("x",Double.toString(keyframe.x),true);
            scalar("y",Double.toString(keyframe.y),true);
            scalar("z",Double.toString(keyframe.z),true);
            scalar("yaw",Float.toString(keyframe.yaw),true);
            scalar("pitch",Float.toString(keyframe.pitch),true);
            scalar("roll",Float.toString(keyframe.roll),true);
            scalar("fov",Float.toString(keyframe.fov),true);
            scalar("mask",Integer.toString(keyframe.mask),true);
            scalar("interpolation",keyframe.interpolation,true);
        }
        fullHeight=y-82+8;
        // Small scroll indicator. Content is clipped by the viewport bounds.
        if(maxScroll()>0)
        {
            int h=Math.max(14,(top-82)*(top-82)/Math.max(1,fullHeight));
            int off=(int)((long)scroll*Math.max(0,top-82-h)/
                    Math.max(1,maxScroll()));
            rectangle(width-4,82+off,width-2,82+off+h,accent);
        }
    }
}
