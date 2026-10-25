package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.lwjgl.opengl.GL11;
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
        public final int component, x, y, width, height;
        public final String value;
        public final boolean keyframe, toggle;
        Hit(String key,int component,int x,int y,int width,String value,
                boolean keyframe,boolean toggle)
        {
            this(key,component,x,y,width,19,value,keyframe,toggle);
        }
        Hit(String key,int component,int x,int y,int width,int height,
                String value,boolean keyframe,boolean toggle)
        {
            this.key=key;this.component=component;this.x=x;this.y=y;
            this.width=width;this.height=height;this.value=value;
            this.keyframe=keyframe;this.toggle=toggle;
        }
        public boolean contains(int mx,int my)
        {return mx>=x&&mx<x+width&&my>=y&&my<y+height;}
    }

    private static final int CONTENT_TOP=68;
    private final List<Hit> hits=new ArrayList<Hit>();
    private Minecraft mc;
    private int right,width,bottom,scroll,y,contentHeight,accent;
    private int physicalBottom,physicalWidth,clampedScroll;
    private float uiScale=0.8F;
    private boolean measuring;
    private final Set<String> openedSections=new HashSet<String>();
    private final Set<String> closedSections=new HashSet<String>();
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
    public int maxScroll(){return Math.max(0,contentHeight-Math.max(1,bottom-CONTENT_TOP));}
    public int clampedScroll(){return clampedScroll;}
    public float scale(){return uiScale;}
    /** Fold groups keep rarely used controls available without a giant wall. */
    public boolean toggleSection(String id)
    {
        if(id.startsWith("open:"))
        {
            String name=id.substring(5);
            if(!openedSections.add(name))openedSections.remove(name);
            return true;
        }
        if(id.startsWith("close:"))
        {
            String name=id.substring(6);
            if(!closedSections.add(name))closedSections.remove(name);
            return true;
        }
        return false;
    }
    private void rect(int x1,int y1,int x2,int y2,int c)
    {GuiScreen.drawRect(x1,y1,x2,y2,c);}
    private void text(String name,int x,int yy,int color,int maxWidth)
    {
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                name==null?"":name,Math.max(1,maxWidth)),x,yy,color);
    }
    private boolean visible(int pos,int h)
    {return pos>=CONTENT_TOP&&pos+h<=bottom-5;}
    private void section(String title)
    {
        int at=y-scroll;
        if(!measuring&&visible(at,16))
        {
            rect(right+5,at,width-6,at+16,0xFF2B2D30);
            rect(right+5,at,right+8,at+16,accent);
            text(title,right+13,at+4,0xFFE2E5E6,width-right-24);
        }
        y+=18;
    }
    /** Fold advanced sections by default; clicking the bar expands them. */
    private boolean foldSection(String title,boolean defaultClosed)
    {
        String id=selectedClip.kind.name()+":"+title;
        boolean expanded=defaultClosed?openedSections.contains(id):
                !closedSections.contains(id);
        int at=y-scroll;
        if(!measuring&&visible(at,16))
        {
            rect(right+5,at,width-6,at+16,0xFF2B2D30);
            rect(right+5,at,right+8,at+16,accent);
            text(title,right+13,at+4,0xFFE2E5E6,width-right-43);
            text(expanded?"v":">",width-22,at+3,0xFF6AC9F3,12);
            hits.add(new Hit("@fold:"+(defaultClosed?"open:":"close:")+id,
                    -1,right+5,at,width-right-11,16,"",false,false));
        }
        y+=18;
        return expanded;
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
    /**
     * Values retain their full stored precision; rounding is display-only.
     * Clicking the field still edits the unrounded source number.
     */
    private static String compact(String original)
    {
        if(original==null)return "";
        try
        {
            if(original.indexOf('.')<0&&original.indexOf('e')<0
                    &&original.indexOf('E')<0)return original;
            double parsed=Double.parseDouble(original.trim());
            if(!Double.isFinite(parsed))return original;
            return String.format(Locale.ROOT,"%.3f",parsed);
        }
        catch(NumberFormatException ignored){return original;}
    }
    private void field(String key,String title,String value,boolean onKey)
    {
        int at=y-scroll;
        if(!measuring&&visible(at,16))
        {
            int x=right+Math.min(91,(width-right)/2), w=width-x-10;
            text(title,right+9,at+4,0xFFADBBC1,x-right-12);
            rect(x,at,x+w,at+16,0xFF10151A);
            rect(x,at+15,x+w,at+16,0xFF384148);
            boolean toggle="true".equalsIgnoreCase(value)||
                    "false".equalsIgnoreCase(value);
            if(toggle)
            {
                boolean checked=Boolean.parseBoolean(value);
                text(checked?"On":"Off",x+5,at+4,0xFFD5E3E9,w-27);
                rect(x+w-17,at+2,x+w-5,at+14,checked?accent:0xFF58626A);
            }
            else text(compact(value),x+5,at+4,0xFFE6F0F3,w-10);
            hits.add(new Hit(key,-1,x,at,w,16,value,onKey,toggle));
        }
        y+=18;
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
            if(!measuring&&visible(pos,16))
            {
                int x=right+48,w=width-x-10;
                text(axisLabels[i],right+13,pos+4,0xFF9FBBCB,32);
                rect(x,pos,x+w,pos+16,0xFF10151A);
                rect(x,pos+15,x+w,pos+16,0xFF384148);
                text(compact(parts[i].trim()),x+5,pos+4,0xFFE6F0F3,w-10);
                hits.add(new Hit(key,i,x,pos,w,16,parts[i].trim(),false,false));
            }
            y+=18;
        }
    }
    private void channels(int first,int last)
    {
        String raw=selectedClip.settings.get("active");
        if(raw==null)return;
        int mask;
        try{mask=Integer.parseInt(raw);}catch(NumberFormatException e){return;}
        if(!foldSection("ACTIVE CHANNELS",true))return;
        String[] names={"X","Y","Z","Yaw","Pitch","Roll","FOV"};
        for(int i=first;i<=last;i++)
        {
            int pos=y-scroll;
            if(!measuring&&visible(pos,16))
            {
                boolean enabled=(mask&(1<<i))!=0;
                rect(right+12,pos,width-11,pos+16,0xFF10151A);
                text(names[i],right+19,pos+4,0xFFBFCDD5,width-right-58);
                rect(width-30,pos+2,width-17,pos+14,
                        enabled?accent:0xFF555E66);
                hits.add(new Hit("active",i,right+12,pos,width-right-23,
                        16,raw,false,true));
            }
            y+=17;
        }
    }
    private void button(String text,String action)
    {
        int at=y-scroll;
        if(!measuring&&visible(at,16))
        {
            rect(right+10,at,width-11,at+16,0xFF30383F);
            rect(right+10,at+15,width-11,at+16,accent);
            text(text,right+16,at+4,0xFFE5F4FA,width-right-33);
            hits.add(new Hit(action,-1,right+10,at,width-right-21,
                    16,"",false,false));
        }
        y+=18;
    }
    private void keyNav()
    {
        int at=y-scroll;
        if(!measuring&&visible(at,16))
        {
            String[] labels={"< key","key >","Delete"};
            String[] keys={"@key:prev","@key:next","@key:remove"};
            int bx=right+10,w=(width-right-24)/3;
            for(int i=0;i<3;i++)
            {
                int x=bx+i*(w+2);
                rect(x,at,x+w,at+16,0xFF30383F);
                text(labels[i],x+3,at+4,0xFFC7DBE5,w-5);
                hits.add(new Hit(keys[i],-1,x,at,w,16,"",false,false));
            }
        }
        y+=18;
    }
    private void keyEditor(CameraClipDocument.Key key,boolean point)
    {
        section(point?"PATH POINTS":"KEYFRAME CHANNELS");
        int at=y-scroll;
        if(!measuring&&visible(at,13))
            text("Keys: "+selectedClip.keys.size(),right+12,at+2,
                    0xFFB7C5CC,width-right-24);
        y+=14;
        button("Add key at playhead","@key:add");
        if(key==null)return;
        keyNav();
        if(!foldSection("SELECTED KEY",true))return;
        keyField(key,"tick","Frame",Integer.toString(key.tick));
        if(!point)
            keyField(key,"mask","Channel mask",Integer.toString(key.mask));
        cameraPose(key);
        interpolation("@key","Key interpolation",
                key.interpolation==null||key.interpolation.isEmpty()?
                        "linear":key.interpolation);
    }
    private void singleChannelEditor(CameraClipDocument.Key key,String title)
    {
        section(title);
        int at=y-scroll;
        if(!measuring&&visible(at,13))
            text("Keys: "+selectedClip.keys.size(),right+12,at+2,
                    0xFFB7C5CC,width-right-24);
        y+=14;
        button("Add key at playhead","@key:add");
        if(key==null)return;
        keyNav();
        keyField(key,"tick","Frame",Integer.toString(key.tick));
        keyField(key,"x","Value",Double.toString(key.x));
        interpolation("@key","Interpolation",
                key.interpolation==null||key.interpolation.isEmpty()
                        ?"linear":key.interpolation);
    }
    private void interpolation(String key,String title,String value)
    {
        int at=y-scroll;
        if(!measuring&&visible(at,49))
        {
            text(title,right+12,at+1,0xFFC6D5DE,width-right-24);
            int bx=right+11,by=at+12,bw=width-right-22;
            rect(bx,by,bx+bw,by+16,0xFF242A30);
            rect(bx,by+15,bx+bw,by+16,0xFF414D56);
            text(CameraInterpolation.displayName(value),bx+7,by+4,
                    0xFFE5EFF2,bw-25);
            text("v",bx+bw-13,by+3,0xFF6BC9F4,12);
            hits.add(new Hit("@interp:open:"+key,-1,bx,by,bw,16,
                    value,false,false));
            int gx=bx+1,gy=by+18,gw=bw-2,gh=19;
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
        y+=51;
    }
    private void envelope()
    {
        if(!foldSection("APPEARANCE",true))return;
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
        if(!foldSection("PLACEMENT",true))return;
        setting("placement","Anchor");
        setting("x","X");
        setting("y","Y");
        if(selectedClip.settings.containsKey("width"))setting("width","Width");
        if(selectedClip.settings.containsKey("height"))setting("height","Height");
        if(selectedClip.settings.containsKey("opacity"))setting("opacity","Opacity");
    }
    private void transform()
    {
        if(!foldSection("TRANSFORM",true))return;
        setting("rotation","Rotation");
        setting("scaleX","Scale X");
        setting("scaleY","Scale Y");
    }

    private void mediaKeyEditor(CameraClipDocument.Key key)
    {
        section("MEDIA ANIMATION");
        int at=y-scroll;
        if(!measuring&&visible(at,13))
            text("Transform keys: "+selectedClip.keys.size(),
                    right+12,at+2,0xFFB7C5CC,width-right-24);
        y+=14;
        button("Add media key at playhead","@key:add");
        if(key==null)return;
        keyNav();
        if(!foldSection("SELECTED MEDIA KEY",true))return;
        keyField(key,"tick","Frame",Integer.toString(key.tick));
        String[] labels={"X","Y","Width","Height","Rotation",
                "Scale X","Scale Y","Opacity"};
        for(int i=0;i<CameraMediaAnimation.FIELDS.length;i++)
        {
            String field=CameraMediaAnimation.FIELDS[i];
            Double value=key.media.get(field);
            if(value==null)value=CameraMediaAnimation.base(selectedClip,field);
            keyField(key,"media."+field,labels[i],value.toString());
        }
        interpolation("@key","Key interpolation",
                key.interpolation==null||key.interpolation.isEmpty()
                        ?"linear":key.interpolation);
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
                setting("local","Camera-local coordinates");
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
                setting("yawSpeed","Yaw / tick");
                setting("pitchSpeed","Pitch / tick");
                setting("distance","Distance");
                setting("atBlock","Use fixed center");
                point("block","CENTER","X","Y","Z");
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
                setting("focus","Focus distance");
                setting("fromFov","Start FOV");
                setting("toFov","End FOV");
                interpolation("interp","Interpolation",
                        value("interp","linear"));
                break;
            case MATH:
                section("EXPRESSION");
                setting("expression","Expression");
                channels(0,6);
                break;
            case REMAPPER:
                section("REMAP TIME");
                setting("from","Source start tick");
                setting("to","Source end tick");
                interpolation("interp","Interpolation",
                        value("interp","linear"));
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
                setting("fit","Fit (contain/cover/stretch)");
                setting("flipX","Mirror horizontally");
                setting("flipY","Mirror vertically");
                setting("smooth","Smooth");
                setting("color","Tint");
                placement();
                transform();
                mediaKeyEditor(key);
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
                setting("fit","Fit (contain/cover/stretch)");
                setting("flipX","Mirror horizontally");
                setting("flipY","Mirror vertically");
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
                mediaKeyEditor(key);
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
    /**
     * Two-pass layout: measure at compact row heights and then fit the
     * inspector to the available preview height.  Values and hit testing are
     * transformed back to GUI coordinates; only presentation is zoomed.
     */
    public void draw(AnimationEditorScreen screen,CameraClipDocument.Clip clip,
            CameraClipDocument.Key keyframe,boolean keySelected,
            int requestedScroll,int right,int timelineTop)
    {
        this.mc=Minecraft.getMinecraft();
        this.right=right;this.physicalWidth=screen.width;
        this.physicalBottom=timelineTop;
        this.accent=EditorThemeManager.get().getAccent();
        this.hits.clear();
        rect(right,25,screen.width,timelineTop,0xFF191D21);
        rect(right,25,right+2,timelineTop,0xFF30383E);
        text("CLIP PROPERTIES",right+10,34,
                EditorThemeManager.get().getAccentBright(),screen.width-right-18);
        if(clip==null)
        {
            text("Select a camera clip",right+9,53,
                    0xFF8C999F,screen.width-right-16);
            contentHeight=0;clampedScroll=0;closeInterpolation();return;
        }
        if(selectedClip!=clip){closeInterpolation();optionScroll=0;}
        selectedClip=clip;
        rect(right+7,46,screen.width-7,63,0xFF272E34);
        rect(right+7,46,right+10,63,clip.kind.color);
        text(clip.title,right+15,50,0xFFE8F1F3,screen.width-right-25);

        // First pass measures layout without creating controls or graphics.
        measuring=true;
        width=screen.width;
        bottom=timelineTop;
        scroll=0;
        layout(keyframe);
        contentHeight=y-CONTENT_TOP+5;

        // Adaptive fit, within legible bounds. Expanded long panels may
        // scroll rather than turn into unreadably tiny text.
        int available=Math.max(1,timelineTop-CONTENT_TOP-5);
        uiScale=Math.min(0.88F,Math.max(0.58F,
                available/(float)Math.max(1,contentHeight)));
        width=right+(int)Math.ceil((screen.width-right)/uiScale);
        bottom=CONTENT_TOP+(int)Math.floor((timelineTop-CONTENT_TOP)/uiScale);
        scroll=Math.max(0,Math.min(requestedScroll,maxScroll()));
        clampedScroll=scroll;

        // Header stays full size, inspector fields become more compact.
        GL11.glPushMatrix();
        try
        {
            GL11.glTranslated(right,(double)CONTENT_TOP,0D);
            GL11.glScaled(uiScale,uiScale,1D);
            GL11.glTranslated(-right,-(double)CONTENT_TOP,0D);
            measuring=false;
            layout(keyframe);
            if(maxScroll()>0)
            {
                int size=Math.max(12,(bottom-CONTENT_TOP)*(bottom-CONTENT_TOP)/
                        Math.max(1,contentHeight));
                int off=(int)((long)scroll*Math.max(0,bottom-CONTENT_TOP-size)/
                        Math.max(1,maxScroll()));
                rect(width-4,CONTENT_TOP+off,width-2,CONTENT_TOP+off+size,accent);
            }
            renderInterpolationPopup();
        }
        finally {GL11.glPopMatrix();}
        // Numeric input, checkbox clicks, popup options and drag gestures
        // must use actual screen pixels, not the virtual drawing space.
        for(int i=0;i<hits.size();i++)
        {
            Hit h=hits.get(i);
            hits.set(i,new Hit(h.key,h.component,
                    right+Math.round((h.x-right)*uiScale),
                    CONTENT_TOP+Math.round((h.y-CONTENT_TOP)*uiScale),
                    Math.max(1,Math.round(h.width*uiScale)),
                    Math.max(1,Math.round(h.height*uiScale)),
                    h.value,h.keyframe,h.toggle));
        }
    }

    private void layout(CameraClipDocument.Key keyframe)
    {
        y=CONTENT_TOP;
        section("GENERAL");
        field("@title","Title",selectedClip.title,false);
        field("@start","Start",Integer.toString(selectedClip.tick),false);
        field("@duration","Duration",Integer.toString(selectedClip.duration),false);
        field("@layer","Layer",Integer.toString(selectedClip.layer),false);
        field("@enabled","Enabled",Boolean.toString(selectedClip.enabled),false);
        clipSpecific(keyframe);
        envelope();
    }

}
