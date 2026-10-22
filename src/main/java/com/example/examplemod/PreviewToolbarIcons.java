package com.example.examplemod;

import net.minecraft.client.gui.Gui;

/**
 * Compact 1.12.2-safe pixel icons for the native preview toolbar.
 * No external textures, dependencies or Unicode font glyphs required.
 */
public final class PreviewToolbarIcons
{
    public static final int SHOT=0;
    public static final int RECORD=1;
    public static final int ALL_ACTORS=2;
    public static final int AUTOFOCUS=3;
    public static final int NAVIGATION=4;
    public static final int FILM_CAMERA=5;

    private static void pixel(int x,int y,int w,int h,int c)
    {
        Gui.drawRect(x,y,x+w,y+h,c);
    }

    /** Draw a 14px motif at the center of an 18x18 icon button. */
    public static void draw(int kind,int x,int y,int color,boolean enabled)
    {
        int ox=x+2,oy=y+2;
        switch(kind)
        {
            case SHOT:
                // Camera body + lens, not a word or mode abbreviation.
                pixel(ox+1,oy+4,10,7,color);
                pixel(ox+3,oy+2,5,2,color);
                pixel(ox+10,oy+5,3,5,color);
                pixel(ox+4,oy+6,4,3,0xFF151C21);
                if(enabled)pixel(ox+5,oy+7,2,2,0xFF73D9FF);
                break;
            case RECORD:
                // Pixel-round recording lamp.
                pixel(ox+5,oy+2,4,10,color);
                pixel(ox+3,oy+4,8,6,color);
                pixel(ox+4,oy+3,6,8,color);
                if(!enabled)pixel(ox+5,oy+5,4,4,0xFF262F35);
                break;
            case ALL_ACTORS:
                // Two heads and shoulders.
                pixel(ox+2,oy+3,4,4,color);
                pixel(ox+8,oy+3,4,4,color);
                pixel(ox+1,oy+9,6,3,color);
                pixel(ox+7,oy+9,6,3,color);
                break;
            case AUTOFOCUS:
                // Focus reticle with a hollow square center.
                pixel(ox+1,oy+1,4,1,color);
                pixel(ox+1,oy+1,1,4,color);
                pixel(ox+9,oy+1,4,1,color);
                pixel(ox+12,oy+1,1,4,color);
                pixel(ox+1,oy+12,4,1,color);
                pixel(ox+1,oy+9,1,4,color);
                pixel(ox+9,oy+12,4,1,color);
                pixel(ox+12,oy+9,1,4,color);
                pixel(ox+6,oy+6,2,2,color);
                break;
            case NAVIGATION:
                // Orbit ring with pivot; active = orbit, inactive = free.
                pixel(ox+4,oy+1,6,1,color);
                pixel(ox+2,oy+3,2,2,color);
                pixel(ox+1,oy+5,1,4,color);
                pixel(ox+2,oy+9,2,2,color);
                pixel(ox+4,oy+12,6,1,color);
                pixel(ox+10,oy+10,2,2,color);
                pixel(ox+12,oy+5,1,5,color);
                pixel(ox+10,oy+2,2,2,color);
                pixel(ox+6,oy+6,2,2,color);
                if(enabled)pixel(ox+11,oy+1,3,2,color);
                break;
            case FILM_CAMERA:
                // Clapperboard-style film shot symbol.
                pixel(ox+2,oy+5,10,8,color);
                pixel(ox+4,oy+8,6,3,0xFF151C21);
                pixel(ox+2,oy+2,10,3,color);
                pixel(ox+4,oy+2,2,3,0xFF151C21);
                pixel(ox+8,oy+2,2,3,0xFF151C21);
                break;
            default: break;
        }
    }

    private PreviewToolbarIcons() {}
}
