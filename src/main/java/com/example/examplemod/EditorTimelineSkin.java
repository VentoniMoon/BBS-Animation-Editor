package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Shared CAMERA-derived timeline skin used by POSE, BODY PARTS and
 * CHARACTER. Pure rendering: no mutations of keys, attachments or clips.
 * Hitboxes, frame positions, zoom and mouse behavior remain owned by their
 * respective timeline controllers.
 */
public final class EditorTimelineSkin
{
    private EditorTimelineSkin(){}

    /**
     * CAMERA/CHARACTER-style vertical grid over ONE populated track row.
     * Invoke immediately after the background and BEFORE keys or clip
     * bodies. The earlier POSE/BODY PARTS drawRuler() drew full-height
     * lines first, but per-track background fills subsequently erased
     * them. Keeping the grid within the row also avoids painting over
     * attachment bars, character handles or labels.
     *
     * Uses the same EditorTimeline frame->pixel transform and theme
     * intensity as CAMERA; zoom and horizontal scroll need no remapping.
     */
    public static void gridRow(EditorTimeline timeline,int timelineStartX,
            int right,int rowTop,int rowHeight)
    {
        if(timeline==null||right<=timelineStartX||rowHeight<2)return;
        float pixelsPerFrame=timeline.getPixelsPerFrame();
        if(pixelsPerFrame<=0F)return;
        int first=Math.max(0,
                (int)Math.floor(timeline.getOffset()/pixelsPerFrame)-2);
        int last=Math.max(first,
                (int)Math.ceil((timeline.getOffset()+
                        (right-timelineStartX))/pixelsPerFrame)+2);
        // When heavily zoomed out, thousands of frames can map to a
        // single pixel. Skip duplicate pixel columns, not meaningful
        // 5/20-frame divisions.
        int stride=pixelsPerFrame>=1F?1:
                Math.max(1,(int)Math.floor(1F/pixelsPerFrame));
        int majorStep=timeline.getZoom()>=2F?1:
                timeline.getZoom()>=1F?5:
                timeline.getZoom()>=0.5F?10:20;
        int accent=EditorThemeManager.get().getAccent();
        int low=shade(accent,0.10F);
        int mid=shade(accent,0.15F);
        int high=shade(accent,0.20F);
        // Align frame iteration to a globally stable subdivision so
        // scrolling never shifts which columns are drawn.
        int start=(first/stride)*stride;
        for(int frame=start;frame<=last;frame+=stride)
        {
            if(frame<0)continue;
            int x=timeline.getFrameX(frame,timelineStartX);
            if(x<timelineStartX||x>=right)continue;
            int color=frame%majorStep==0?high:
                    frame%5==0?mid:low;
            Gui.drawRect(x,rowTop,x+1,rowTop+rowHeight-1,color);
        }
    }

    private static int shade(int accent,float multiplier)
    {
        int r=Math.round(((accent>>16)&255)*multiplier);
        int g=Math.round(((accent>>8)&255)*multiplier);
        int b=Math.round((accent&255)*multiplier);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    public static void key(int x,int centerY,boolean selected)
    {
        int accent=EditorThemeManager.get().getAccent();
        int bright=EditorThemeManager.get().getAccentBright();
        // Match the small highlighted key handles *inside* CAMERA clips.
        Gui.drawRect(x-3,centerY-5,x+4,centerY+5,
                selected?0xFFFFF2B0:accent);
        Gui.drawRect(x-2,centerY-4,x+3,centerY+4,
                selected?0xFFFDE19A:0xFF122530);
        Gui.drawRect(x-1,centerY-3,x+2,centerY+3,
                selected?0xFFFFFFFF:bright);
    }

    /**
     * Solid continuous Body Part presence interval, styled as a CAMERA
     * clip with a top highlight, selected outline, and resize handles.
     */
    public static void clip(int left,int right,int y,int rowHeight,
            String title,boolean selected)
    {
        if(right-left<2)return;
        int accent=EditorThemeManager.get().getAccent();
        int bright=EditorThemeManager.get().getAccentBright();
        int top=y+3,bottom=y+rowHeight-3;
        Gui.drawRect(left,top,right,bottom,accent);
        Gui.drawRect(left,top,right,top+2,0x66FFFFFF);
        if(selected)
        {
            Gui.drawRect(left,top-1,right,top,0xFFFFFFFF);
            Gui.drawRect(left,bottom,right,bottom+1,0xFFFFFFFF);
            Gui.drawRect(left,top-1,left+1,bottom+1,0xFFFFFFFF);
            Gui.drawRect(right-1,top-1,right,bottom+1,0xFFFFFFFF);
        }
        if(right-left>8)
        {
            Gui.drawRect(left+1,top+3,left+3,bottom-3,0xDDEAF7FF);
            Gui.drawRect(right-3,top+3,right-1,bottom-3,
                    0xDDEAF7FF);
        }
        if(title!=null&&right-left>30)
        {
            Minecraft mc=Minecraft.getMinecraft();
            if(mc!=null&&mc.fontRenderer!=null)
            {
                String trimmed=mc.fontRenderer.trimStringToWidth(
                        title,Math.max(1,right-left-10));
                mc.fontRenderer.drawString(trimmed,left+5,y+6,
                        0xFFFFFFFF);
            }
        }
    }
}
