package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Native CAMERA inspector.  Each Kind has an explicit BBS FS panel layout,
 * based on the UI*Clip classes in BBS FS 2.8 (1.12.2 alpha).
 * Drawing helpers are shared, but no field is auto-generated from the JSON map.
 */
public final class CameraClipInspector
{
    public static final class Hit
    {
        public final String key;
        public final int component, x, y, width;
        public final String value;
        public final boolean keyframe, toggle;
        Hit(String key,int component,int x,int y,int width,String value,
                boolean keyframe,boolean toggle)
        {
            this.key=key;this.component=component;this.x=x;this.y=y;
            this.width=width;this.value=value;
            this.keyframe=keyframe;this.toggle=toggle;
        }
        public boolean contains(int mx,int my)
        {return mx>=x&&mx<x+width&&my>=y&&my<y+19;}
    }

    private final List<Hit> hits=new ArrayList<Hit>();
    private Minecraft mc;
    private int right,width,bottom,scroll,y,contentHeight,accent;
    private String openInterpolation="";
    private int optionScroll;
    private CameraClipDocument.Clip selectedClip;
    private String popupField="";
    public Hit hit(int mouseX,int mouseY)
    {
        // Overlay menus are rendered last, so their hitboxes win.
        for(int i=hits.size()-1;i>=0;i--)
            if(hits.get(i).contains(mouseX,mouseY))return hits.get(i);
        return null;
    }
    public boolean isInterpolationOpen(){return !openInterpolation.isEmpty();}
    public void closeInterpolation(){openInterpolation="";}
    public void toggleInterpolation(String key)
    {
        if(key.equals(openInterpolation))openInterpolation="";
        else {openInterpolation=key;optionScroll=0;}
    }
    public void scrollInterpolation(int direction)
    {
        optionScroll=Math.max(0,Math.min(
                Math.max(0,CameraInterpolation.OPTIONS.length-6),
                optionScroll+(direction>0?-1:1)));
    }
    public int maxScroll(){return Math.max(0,contentHeight-Math.max(1,bottom-82));}
    private void rect(int x1,int y1,int x2,int y2,int c)
    {GuiScreen.drawRect(x1,y1,x2,y2,c);}
    private void text(String name,int x,int yy,int color,int maxWidth)
    {
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                name==null?"":name,Math.max(1,maxWidth)),x,yy,color);
    }
    private boolean visible(int pos,int h)
    {return pos>=82&&pos+h<=bottom-8;}
    private void section(String title)
    {
        int at=y-scroll;
        if(visible(at,19))
        {
            rect(right+5,at,width-6,at+19,0xFF2B2D30);
            rect(right+5,at,right+8,at+19,accent);
            text(title,right+13,at+5,0xFFE2E5E6,width-right-24);
        }
        y+=24;
    }
    private static String nice(String s)
    {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i++)
        {
            char c=s.charAt(i);
            if(c=='_'||c=='.')out.append(' ');
            else if(i>0&&Character.isUpperCase(c)&&Character.isLowerCase(s.charAt(i-1)))
                out.append(' ').append(c);
            else out.append(c);
        }
        if(out.length()>0)out.setCharAt(0,Character.toUpperCase(out.charAt(0)));
        return out.toString();
    }
    private void field(String key,String title,String value,boolean onKey)
    {
        int at=y-scroll;
        if(visible(at,19))
        {
            int x=right+Math.min(77,(width-right)/2), w=width-x-10;
            text(title,right+9,at+5,0xFFADBBC1,x-right-12);
            rect(x,at,x+w,at+19,0xFF10151A);
            rect(x,at+18,x+w,at+19,0xFF384148);
            boolean toggle="true".equalsIgnoreCase(value)||
                    "false".equalsIgnoreCase(value);
            if(toggle)
            {
                boolean checked=Boolean.parseBoolean(value);
                text(checked?"On":"Off",x+5,at+5,0xFFD5E3E9,w-27);
                rect(x+w-17,at+3,x+w-5,at+15,checked?accent:0xFF58626A);
            }
            else text(value,x+5,at+5,0xFFE6F0F3,w-10);
            hits.add(new Hit(key,-1,x,at,w,value,onKey,toggle));
        }
        y+=23;
    }
    private void setting(String key){setting(key,nice(key));}
    private void setting(String key,String title)
    {
        String v=selectedClip.settings.get(key);
        if(v!=null)field(key,title,v,false);
    }
    private void keyField(CameraClipDocument.Key key,String name,String title,String value)
    {if(key!=null)field(name,title,value,true);}
    private void cameraPose(CameraClipDocument.Key key)
    {
        if(key==null)return;
        section("POSITION");
        keyField(key,"x","X",Double.toString(key.x));
        keyField(key,"y","Y",Double.toString(key.y));
        keyField(key,"z","Z",Double.toString(key.z));
        section("ANGLE");
        keyField(key,"yaw","Yaw",Float.toString(key.yaw));
        keyField(key,"pitch","Pitch",Float.toString(key.pitch));
        keyField(key,"roll","Roll",Float.toString(key.roll));
        keyField(key,"fov","FOV",Float.toString(key.fov));
    }
    private void point(String key,String heading,String... axisLabels)
    {
        String raw=selectedClip.settings.get(key);
        if(raw==null)return;
        section(heading);
        String[] parts=raw.split(",",-1);
        for(int i=0;i<Math.min(axisLabels.length,parts.length);i++)
        {
            int pos=y-scroll;
            if(visible(pos,19))
            {
                int x=right+48,w=width-x-10;
                text(axisLabels[i],right+13,pos+5,0xFF9FBBCB,32);
                rect(x,pos,x+w,pos+19,0xFF10151A);
                rect(x,pos+18,x+w,pos+19,0xFF384148);
                text(parts[i].trim(),x+5,pos+5,0xFFE6F0F3,w-10);
                hits.add(new Hit(key,i,x,pos,w,parts[i].trim(),false,false));
            }
            y+=23;
        }
    }
    private void channels(int first,int last)
    {
        String raw=selectedClip.settings.get("active");
        if(raw==null)return;
        int mask;
        try{mask=Integer.parseInt(raw);}catch(NumberFormatException e){return;}
        section("ACTIVE CHANNELS");
        String[] names={"X","Y","Z","Yaw","Pitch","Roll","FOV"};
        for(int i=first;i<=last;i++)
        {
            int pos=y-scroll;
            if(visible(pos,19))
            {
                boolean enabled=(mask&(1<<i))!=0;
                rect(right+12,pos,width-11,pos+19,0xFF10151A);
                text(names[i],right+19,pos+5,0xFFBFCDD5,width-right-58);
                rect(width-30,pos+4,width-17,pos+16,
                        enabled?accent:0xFF555E66);
                hits.add(new Hit("active",i,right+12,pos,width-right-23,
                        raw,false,true));
            }
            y+=22;
        }
    }
    private void button(String text,String action)
    {
        int at=y-scroll;
        if(visible(at,19))
        {
            rect(right+10,at,width-11,at+19,0xFF30383F);
            rect(right+10,at+18,width-11,at+19,accent);
            text(text,right+16,at+5,0xFFE5F4FA,width-right-33);
            hits.add(new Hit(action,-1,right+10,at,width-right-21,"",false,false));
        }
        y+=23;
    }
    private void keyEditor(CameraClipDocument.Key key,boolean point)
    {
        section(point?"PATH POINTS":"KEYFRAME CHANNELS");
        int countAt=y-scroll;
        if(visible(countAt,18))
            text("Keys: "+selectedClip.keys.size(),right+12,countAt+5,
                    0xFFB7C5CC,width-right-24);
        y+=21;
        button("Add key at playhead","@key:add");
        if(key==null)return;
        button("Previous key","@key:prev");
        button("Next key","@key:next");
        button("Delete selected key","@key:remove");
        keyField(key,"tick","Frame",Integer.toString(key.tick));
        if(!point)
        {
            keyField(key,"mask","Channel mask",Integer.toString(key.mask));
        }
        cameraPose(key);
        interpolation("@key","Key interpolation",
                key.interpolation==null||key.interpolation.isEmpty()?
                        "linear":key.interpolation);
    }
    private void singleChannelEditor(CameraClipDocument.Key key,String title)
    {
        section(title);
        int at=y-scroll;
        if(visible(at,18))
            text("Keys: "+selectedClip.keys.size(),right+12,at+5,
                    0xFFB7C5CC,width-right-24);
        y+=21;
        button("Add key at playhead","@key:add");
        if(key==null)return;
        button("Previous key","@key:prev");
        button("Next key","@key:next");
        button("Delete selected key","@key:remove");
        keyField(key,"tick","Frame",Integer.toString(key.tick));
        keyField(key,"x","Value",Double.toString(key.x));
        interpolation("@key","Interpolation",
                key.interpolation==null||key.interpolation.isEmpty()
                        ?"linear":key.interpolation);
    }
    private void interpolation(String key,String title,String value)
    {
        int at=y-scroll;
        if(visible(at,78))
        {
            text(title,right+12,at+1,0xFFC6D5DE,width-right-24);
            int bx=right+11,by=at+15,bw=width-right-22;
            rect(bx,by,bx+bw,by+21,0xFF242A30);
            rect(bx,by+20,bx+bw,by+21,0xFF414D56);
            text(CameraInterpolation.displayName(value),bx+7,by+6,
                    0xFFE5EFF2,bw-25);
            text("v",bx+bw-13,by+5,0xFF6BC9F4,12);
            hits.add(new Hit("@interp:open:"+key,-1,bx,by,bw,value,false,false));
            int gx=bx+1,gy=by+25,gw=bw-2,gh=35;
            rect(gx,gy,gx+gw,gy+gh,0xFF13191E);
            for(int i=1;i<4;i++)
            {
                rect(gx+i*gw/4,gy,gx+i*gw/4+1,gy+gh,0xFF263039);
                rect(gx,gy+i*gh/4,gx+gw,gy+i*gh/4+1,0xFF263039);
            }
            for(int x=0;x<gw;x++)
            {
                double u=x/(double)Math.max(1,gw-1);
                double yy=CameraInterpolation.sample(u,value);
                int py=gy+gh-1-(int)Math.round(yy*(gh-1));
                if(py<gy)py=gy;
                if(py>=gy+gh)py=gy+gh-1;
                rect(gx+x,py,gx+x+1,py+1,accent);
            }
        }
        y+=83;
    }
    private void envelope()
    {
        section("APPEARANCE");
        setting("envelope.enabled","Enabled");
        setting("envelope.mode","Mode");
        setting("envelope.fadeIn","Fade in");
        setting("envelope.fadeOut","Fade out");
        if(selectedClip.settings.containsKey("envelope.pre"))
            interpolation("envelope.pre","Before",selectedClip.settings.get("envelope.pre"));
        if(selectedClip.settings.containsKey("envelope.post"))
            interpolation("envelope.post","After",selectedClip.settings.get("envelope.post"));
    }
    private void placement()
    {
        section("PLACEMENT");
        setting("placement","Anchor");
        setting("x","X");
        setting("y","Y");
        if(selectedClip.settings.containsKey("width"))setting("width","Width");
        if(selectedClip.settings.containsKey("height"))setting("height","Height");
        if(selectedClip.settings.containsKey("opacity"))setting("opacity","Opacity");
    }
    private void transform()
    {
        section("TRANSFORM");
        setting("rotation","Rotation");
        setting("scaleX","Scale X");
        setting("scaleY","Scale Y");
    }

    /** Deliberately explicit per-type layouts; no settings-map reflection. */
    private void clipSpecific(CameraClipDocument.Key key)
    {
        switch(selectedClip.kind)
        {
            case IDLE:
                cameraPose(key);
                break;
            case DOLLY:
                cameraPose(key);
                section("DOLLY");
                setting("distance","Distance");
                setting("reverse","Reverse");
                setting("yaw","Dolly yaw");
                setting("pitch","Dolly pitch");
                interpolation("interp","Interpolation",value("interp","linear"));
                break;
            case PATH:
                keyEditor(key,true);
                interpolation("interpPoint","Position interpolation",
                        value("interpPoint","hermite"));
                interpolation("interpAngle","Angle interpolation",
                        value("interpAngle","hermite"));
                break;
            case KEYFRAME:
                section("KEYFRAME");
                setting("additive","Additive");
                keyEditor(key,false);
                break;
            case TRACKER:
                section("TARGET");
                setting("selector","Entity");
                setting("group","Group");
                point("offset","OFFSET","X","Y","Z");
                point("angle","ANGLE","Yaw","Pitch","Roll");
                section("CAMERA");
                setting("fov","FOV");
                setting("look_at","Look at");
                setting("relative","Relative");
                channels(0,6);
                break;
            case SPLINE:
                section("TARGET");
                setting("selector","Entity");
                setting("group","Group");
                section("PROGRESS");
                setting("start","Start");
                setting("end","End");
                interpolation("interp","Interpolation",value("interp","linear"));
                section("SPLINE");
                setting("path_rotation","Path rotation");
                point("offset","OFFSET","X","Y","Z");
                point("angle","ANGLE","Yaw","Pitch","Roll");
                section("CAMERA");
                setting("fov","FOV");
                channels(0,6);
                break;
            case TRANSLATE:
                point("translate","TRANSLATE","X","Y","Z");
                channels(0,2);
                break;
            case ANGLE:
                point("angle","ANGLE","Yaw","Pitch","Roll","FOV");
                // Old camera scene JSON can store FOV separately.
                if(selectedClip.settings.containsKey("fov"))
                {
                    section("CAMERA");
                    setting("fov","FOV delta");
                }
                channels(3,6);
                break;
            case SHAKE:
                section("SHAKE");
                setting("shake","Shake");
                setting("shakeAmount","Shake amount");
                setting("noise","Noise");
                channels(0,6);
                break;
            case LOOK:
                section("TARGET");
                setting("selector","Entity");
                setting("relative","Relative");
                point("offset","OFFSET","X","Y","Z");
                section("LOOK AT BLOCK");
                setting("atBlock","At block");
                point("block","BLOCK","X","Y","Z");
                setting("forward","Forward");
                break;
            case ORBIT:
                section("TARGET");
                setting("selector","Entity");
                section("ORBIT");
                setting("absolute","Absolute");
                setting("copy","Copy");
                setting("yaw","Yaw");
                setting("pitch","Pitch");
                setting("distance","Distance");
                point("offset","OFFSET","X","Y","Z");
                break;
            case DRAG:
                section("DRAG");
                setting("deterministic","Deterministic");
                setting("factor","Factor");
                setting("rate","Rate");
                channels(0,6);
                break;
            case DOLLY_ZOOM:
                section("DOLLY ZOOM");
                setting("focus","Focus");
                break;
            case MATH:
                section("EXPRESSION");
                setting("expression","Expression");
                channels(0,6);
                break;
            case REMAPPER:
                singleChannelEditor(key,"TIME REMAPPING");
                break;
            case CURVE:
                section("SHADER CURVE");
                setting("channels","Channel");
                singleChannelEditor(key,"CURVE KEYS");
                break;
            case IMAGE:
                section("IMAGE");
                setting("texture","Texture");
                button("Browse images","@media:browse:texture");
                setting("fullscreen","Fullscreen");
                setting("smooth","Smooth");
                setting("color","Tint");
                placement();
                transform();
                break;
            case AUDIO:
                section("AUDIO");
                setting("audio","Audio file");
                button("Browse audio","@media:browse:audio");
                setting("offset","Offset");
                setting("volume","Volume");
                button("Extend to audio length","@media:duration");
                break;
            case VIDEO:
                section("VIDEO");
                setting("video","Video file");
                button("Browse videos","@media:browse:video");
                setting("audio","Audio track");
                setting("offset","Offset");
                setting("volume","Volume");
                button("Extend to video length","@media:duration");
                setting("loop","Loop");
                setting("fullscreen","Fullscreen");
                setting("smooth","Smooth");
                setting("color","Tint");
                placement();
                transform();
                break;
            case SUBTITLE:
                section("SUBTITLE");
                setting("text","Text");
                setting("color","Color");
                setting("textShadow","Text shadow");
                setting("background","Background");
                setting("backgroundOffset","Background offset");
                setting("shadow","Shadow");
                setting("shadowOpaque","Opaque shadow");
                section("FONT");
                setting("font","Font file");
                button("Browse fonts","@media:browse:font");
                setting("fontSize","Font size");
                setting("lineHeight","Line height");
                setting("maxWidth","Max width");
                section("IMAGE");
                setting("image","Image file");
                button("Browse images","@media:browse:image");
                setting("imageRight","Image right");
                setting("imageScale","Image scale");
                placement();
                transform();
                break;
            default:break;
        }
    }
    private String value(String key,String fallback)
    {
        String v=selectedClip.settings.get(key);
        return v==null?fallback:v;
    }
    private void renderInterpolationPopup()
    {
        if(openInterpolation.isEmpty())return;
        int topY=Math.max(89,bottom-156);
        int lx=right+5,rx=width-6;
        rect(lx-2,topY-23,rx+2,topY+6*21+4,0xFF090D11);
        rect(lx,topY-21,rx,topY,0xFF293038);
        text("INTERPOLATION",lx+7,topY-15,0xFF9BDFFE,rx-lx-14);
        for(int i=0;i<6&&i+optionScroll<CameraInterpolation.OPTIONS.length;i++)
        {
            String option=CameraInterpolation.OPTIONS[i+optionScroll];
            int at=topY+i*21;
            rect(lx,at,rx,at+20,i%2==0?0xFF23292F:0xFF1C2329);
            text(CameraInterpolation.displayName(option),lx+7,at+6,
                    0xFFE3EBEE,rx-lx-15);
            hits.add(new Hit("@interp:set:"+openInterpolation+":"+option,-1,
                    lx,at,rx-lx,option,false,false));
        }
    }
    public void draw(AnimationEditorScreen screen,CameraClipDocument.Clip clip,
            CameraClipDocument.Key keyframe,boolean keySelected,
            int scroll,int right,int timelineTop)
    {
        this.mc=Minecraft.getMinecraft();
        this.right=right;this.width=screen.width;this.bottom=timelineTop;
        this.scroll=scroll;this.accent=EditorThemeManager.get().getAccent();
        this.hits.clear();
        rect(right,25,width,bottom,0xFF191D21);
        rect(right,25,right+2,bottom,0xFF30383E);
        text("CLIP PROPERTIES",right+10,34,
                EditorThemeManager.get().getAccentBright(),width-right-18);
        if(clip==null)
        {
            text("Select a camera clip",right+9,67,0xFF8C999F,width-right-16);
            contentHeight=0;closeInterpolation();return;
        }
        if(selectedClip!=clip){closeInterpolation();optionScroll=0;}
        selectedClip=clip;
        rect(right+7,53,width-7,76,0xFF272E34);
        rect(right+7,53,right+10,76,clip.kind.color);
        text(clip.title,right+15,60,0xFFE8F1F3,width-right-25);
        y=82;
        section("GENERAL");
        field("@title","Title",clip.title,false);
        field("@start","Start",Integer.toString(clip.tick),false);
        field("@duration","Duration",Integer.toString(clip.duration),false);
        field("@layer","Layer",Integer.toString(clip.layer),false);
        field("@enabled","Enabled",Boolean.toString(clip.enabled),false);
        clipSpecific(keyframe);
        envelope();
        contentHeight=y-82+10;
        if(maxScroll()>0)
        {
            int h=Math.max(12,(bottom-82)*(bottom-82)/
                    Math.max(1,contentHeight));
            int off=(int)((long)scroll*Math.max(0,bottom-82-h)/
                    Math.max(1,maxScroll()));
            rect(width-4,82+off,width-2,82+off+h,accent);
        }
        renderInterpolationPopup();
    }
}
