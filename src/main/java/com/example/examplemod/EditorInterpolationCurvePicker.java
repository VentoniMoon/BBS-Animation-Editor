package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mchorse.mclib.utils.keyframes.Keyframe;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;
import mchorse.mclib.utils.keyframes.KeyframeEasing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Compact BBS FS-style grid of miniature interpolation graphs.
 * Shared by CAMERA inspector and POSE interpolation selection.
 *
 * CAMERA presets use CameraInterpolation; POSE additionally offers all
 * native McLib interpolation/easing pairs using the mclib:TYPE:EASING ID.
 * Hit testing uses exactly the same grid geometry as drawing.
 */
public final class EditorInterpolationCurvePicker
{
    public static final int COLUMNS=6;
    public static final int CELL_HEIGHT=30;

    private EditorInterpolationCurvePicker(){}

    public static String[] cameraOptions()
    {
        return CameraInterpolation.OPTIONS;
    }

    public static String[] poseOptions()
    {
        List<String> result=new ArrayList<String>();
        for(KeyframeInterpolation type:KeyframeInterpolation.values())
            for(KeyframeEasing easing:KeyframeEasing.values())
                result.add("mclib:"+type.name()+":"+easing.name());
        for(String option:CameraInterpolation.OPTIONS)
            result.add(option);
        return result.toArray(new String[result.size()]);
    }

    public static boolean isNative(String id)
    {
        return id!=null&&id.startsWith("mclib:");
    }

    public static KeyframeInterpolation nativeInterpolation(String id)
    {
        if(!isNative(id))return null;
        String[] pair=id.split(":");
        if(pair.length!=3)return null;
        try{return KeyframeInterpolation.valueOf(pair[1]);}
        catch(IllegalArgumentException invalid){return null;}
    }

    public static KeyframeEasing nativeEasing(String id)
    {
        if(!isNative(id))return null;
        String[] pair=id.split(":");
        if(pair.length!=3)return null;
        try{return KeyframeEasing.valueOf(pair[2]);}
        catch(IllegalArgumentException invalid){return null;}
    }

    public static String nativeId(KeyframeInterpolation interpolation,
            KeyframeEasing easing)
    {
        return "mclib:"+(interpolation==null?"LINEAR":
                interpolation.name())+":"+
                (easing==null?"IN":easing.name());
    }

    public static String displayName(String id)
    {
        if(isNative(id))
        {
            KeyframeInterpolation type=nativeInterpolation(id);
            KeyframeEasing easing=nativeEasing(id);
            return type==null?"McLib":
                    "McLib "+type.name()+" / "+
                    (easing==null?"IN":easing.name());
        }
        return CameraInterpolation.displayName(id);
    }

    public static int maxRowOffset(String[] entries,int visibleRows)
    {
        return Math.max(0,(entries.length+COLUMNS-1)/COLUMNS-
                Math.max(1,visibleRows));
    }

    public static int hit(String[] entries,int x,int y,int width,
            int visibleRows,int rowOffset,int mx,int my)
    {
        if(mx<x||mx>=x+width||my<y||my>=y+
                visibleRows*CELL_HEIGHT)return -1;
        int cellW=Math.max(1,width/COLUMNS);
        int col=Math.min(COLUMNS-1,(mx-x)/cellW);
        int row=(my-y)/CELL_HEIGHT;
        int index=(rowOffset+row)*COLUMNS+col;
        return index>=0&&index<entries.length?index:-1;
    }

    public static void draw(String[] entries,int x,int y,int width,
            int visibleRows,int rowOffset,String selected,
            int mx,int my)
    {
        int bg=0xFF191E24;
        int accent=EditorThemeManager.get().getAccent();
        int bright=EditorThemeManager.get().getAccentBright();
        int cellW=Math.max(1,width/COLUMNS);
        Gui.drawRect(x-2,y-2,x+width+2,
                y+visibleRows*CELL_HEIGHT+2,0xFF080E12);
        Gui.drawRect(x,y,x+width,
                y+visibleRows*CELL_HEIGHT,bg);
        for(int row=0;row<visibleRows;row++)
        {
            for(int col=0;col<COLUMNS;col++)
            {
                int idx=(rowOffset+row)*COLUMNS+col;
                if(idx>=entries.length)break;
                String id=entries[idx];
                int cx=x+col*cellW+1;
                int cy=y+row*CELL_HEIGHT+1;
                int cw=cellW-2,ch=CELL_HEIGHT-2;
                boolean on=id.equals(selected);
                boolean hovered=mx>=cx&&mx<cx+cw&&
                        my>=cy&&my<cy+ch;
                Gui.drawRect(cx,cy,cx+cw,cy+ch,
                        hovered?0xFF394149:0xFF222A31);
                if(on)
                {
                    Gui.drawRect(cx,cy+ch-2,cx+cw,cy+ch,accent);
                    Gui.drawRect(cx,cy,cx+cw,cy+1,bright);
                }
                int color=on?0xFFFFFFFF:hovered?bright:0xFFB8CBD4;
                int fromX=cx+3,fromY=cy+ch-5;
                int plotWidth=Math.max(4,cw-6);
                int plotHeight=Math.max(4,ch-10);
                for(int p=0;p<=plotWidth;p++)
                {
                    double ratio=(double)p/plotWidth;
                    double value=sample(ratio,id);
                    if(Double.isNaN(value)||Double.isInfinite(value))
                        value=ratio;
                    value=Math.max(-0.15D,Math.min(1.15D,value));
                    int px=fromX+p;
                    int py=fromY-(int)Math.round(value*plotHeight);
                    py=Math.max(cy+2,Math.min(cy+ch-3,py));
                    Gui.drawRect(px,py,px+1,py+1,color);
                }
            }
        }
        // Fixed width, same geometry and scrolling affordance for both
        // popups. Selection changes are delegated to the caller.
        if(rowOffset>0)
            Gui.drawRect(x,y,x+width,y+1,bright);
        if(rowOffset<maxRowOffset(entries,visibleRows))
            Gui.drawRect(x,y+visibleRows*CELL_HEIGHT-1,x+width,
                    y+visibleRows*CELL_HEIGHT,accent);
        int hovered=hit(entries,x,y,width,visibleRows,rowOffset,mx,my);
        if(hovered>=0)
        {
            Minecraft mc=Minecraft.getMinecraft();
            if(mc!=null&&mc.fontRenderer!=null)
            {
                String title=displayName(entries[hovered]);
                int tooltipX=x+Math.max(0,
                        Math.min(width-mc.fontRenderer.getStringWidth(title)-8,
                                (mx-x)-8));
                int tooltipY=y-14;
                Gui.drawRect(tooltipX-2,tooltipY-2,
                        tooltipX+mc.fontRenderer.getStringWidth(title)+4,
                        tooltipY+11,0xEB080D12);
                mc.fontRenderer.drawString(title,tooltipX,tooltipY,
                        0xFFE0F1FC);
            }
        }
    }

    private static double sample(double t,String id)
    {
        if(!isNative(id))return CameraInterpolation.sample(t,id);
        KeyframeInterpolation type=nativeInterpolation(id);
        KeyframeEasing easing=nativeEasing(id);
        if(type==null||easing==null)return t;
        try
        {
            Keyframe a=new Keyframe(0,0D);
            Keyframe b=new Keyframe(20,1D);
            Keyframe pre=new Keyframe(-20,0D);
            Keyframe post=new Keyframe(40,1D);
            a.interp=type;a.easing=easing;
            pre.next=a;a.prev=pre;
            a.next=b;b.prev=a;
            b.next=post;post.prev=b;
            return a.interpolate(b,(float)t);
        }
        catch(Throwable unsupported){return t;}
    }
}
