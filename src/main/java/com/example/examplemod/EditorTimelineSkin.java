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
