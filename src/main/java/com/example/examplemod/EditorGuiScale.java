package com.example.examplemod;

/**
 * Editor-owned GUI coordinate system. Never reads or writes Minecraft's
 * gameSettings.guiScale. The virtual canvas is determined solely by the
 * physical display/window, so selecting Small / Normal / Large / Auto in
 * vanilla cannot resize or deform the BBS editor.
 *
 * All GUI layout, mouse hit tests and film/gizmo OpenGL viewports must use
 * these same integer dimensions and pixel-edge conversions.
 *
 * Pure Java 8: independent of Minecraft, Forge, GL and LWJGL.
 */
public final class EditorGuiScale
{
    private static final int MAX_SCALE=4;
    private static final int MIN_LOGICAL_WIDTH=720;
    private static final int MIN_LOGICAL_HEIGHT=420;

    public static int scale(int physicalWidth,int physicalHeight)
    {
        int w=Math.max(1,physicalWidth);
        int h=Math.max(1,physicalHeight);
        for(int scale=MAX_SCALE;scale>=2;scale--)
            if(ceilDiv(w,scale)>=MIN_LOGICAL_WIDTH &&
                    ceilDiv(h,scale)>=MIN_LOGICAL_HEIGHT)
                return scale;
        return 1;
    }

    private static int ceilDiv(int value,int divisor)
    {
        return (value+divisor-1)/divisor;
    }

    public static int width(int physicalWidth,int physicalHeight)
    {
        return ceilDiv(Math.max(1,physicalWidth),
                scale(physicalWidth,physicalHeight));
    }

    public static int height(int physicalWidth,int physicalHeight)
    {
        return ceilDiv(Math.max(1,physicalHeight),
                scale(physicalWidth,physicalHeight));
    }

    public static int mouseX(int rawX,int physicalWidth,int logicalWidth)
    {
        return (int)((long)rawX*Math.max(1,logicalWidth)/
                Math.max(1,physicalWidth));
    }

    public static int mouseY(int rawY,int physicalHeight,int logicalHeight)
    {
        return Math.max(1,logicalHeight)-1-
                (int)((long)rawY*Math.max(1,logicalHeight)/
                        Math.max(1,physicalHeight));
    }

    /** Convert a virtual GUI pixel EDGE into the real framebuffer edge. */
    public static int pixelEdge(int logicalCoordinate,int physicalSize,
            int logicalSize)
    {
        return (int)Math.max(0,Math.min(Math.max(1,physicalSize),
                Math.round(logicalCoordinate*(double)Math.max(1,physicalSize)/
                        Math.max(1,logicalSize))));
    }

    private EditorGuiScale(){}
}
