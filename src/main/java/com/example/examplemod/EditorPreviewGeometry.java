package com.example.examplemod;

/**
 * Available PREVIEW rectangle measured in Minecraft GUI coordinates.
 *
 * This is deliberately separate from the physical framebuffer and the
 * camera's output resolution. If Minecraft GUI Scale makes the GUI smaller
 * than our fixed sidebars, the viewport must SHRINK or become unavailable;
 * it must never grow past its actual right/bottom bounds.
 *
 * Pure Java 8 to exercise all boundary cases without booting Minecraft.
 */
public final class EditorPreviewGeometry
{
    public static final int MIN_RENDER_WIDTH=48;
    public static final int MIN_RENDER_HEIGHT=48;

    public final int left,top,right,bottom,width,height;

    private EditorPreviewGeometry(int left,int top,int right,int bottom)
    {
        this.left=left;
        this.top=top;
        this.right=right;
        this.bottom=bottom;
        this.width=Math.max(0,right-left);
        this.height=Math.max(0,bottom-top);
    }

    public static EditorPreviewGeometry calculate(int screenWidth,
            int screenHeight,int leftPanelWidth,int rightPanelWidth,
            int topBarHeight,int timelineHeight)
    {
        int sw=Math.max(0,screenWidth),sh=Math.max(0,screenHeight);
        int left=Math.min(sw,Math.max(0,leftPanelWidth));
        int top=Math.min(sh,Math.max(0,topBarHeight));
        int right=Math.max(left,sw-Math.max(0,rightPanelWidth));
        int bottom=Math.max(top,sh-Math.max(0,timelineHeight));
        return new EditorPreviewGeometry(left,top,right,bottom);
    }

    public boolean canRender()
    {
        return width>=MIN_RENDER_WIDTH&&height>=MIN_RENDER_HEIGHT;
    }
}
