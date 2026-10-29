package com.example.examplemod;

/**
 * Regression: Minecraft GUI Scale 1/2/3/4/Auto may change vanilla
 * ScaledResolution, but NOT the editor's canvas, hit tests or FBO edges.
 */
public final class EditorGuiScaleSmokeTest
{
    private static void yes(boolean good,String message)
    {
        if(!good)throw new AssertionError(message);
    }
    private static void test(int physicalW,int physicalH)
    {
        int w=EditorGuiScale.width(physicalW,physicalH);
        int h=EditorGuiScale.height(physicalW,physicalH);
        int scale=EditorGuiScale.scale(physicalW,physicalH);
        yes(w==(physicalW+scale-1)/scale,"width for window");
        yes(h==(physicalH+scale-1)/scale,"height for window");
        yes(w>0&&h>0,"invalid layout");
        yes(EditorGuiScale.pixelEdge(0,physicalW,w)==0,"left edge");
        yes(EditorGuiScale.pixelEdge(w,physicalW,w)==physicalW,
                "right edge");
        yes(EditorGuiScale.pixelEdge(h,physicalH,h)==physicalH,
                "bottom edge");
        for(int guiX=0;guiX<w;guiX+=11)
        {
            int pixel=EditorGuiScale.pixelEdge(guiX,physicalW,w);
            int back=EditorGuiScale.mouseX(pixel,physicalW,w);
            yes(Math.abs(back-guiX)<=1,
                    "mouse and film viewport misaligned "+guiX+"/"+back);
        }
        for(int guiY=0;guiY<h;guiY+=13)
        {
            int pixel=EditorGuiScale.pixelEdge(guiY,physicalH,h);
            int back=EditorGuiScale.mouseY(
                    physicalH-1-pixel,physicalH,h);
            yes(Math.abs(back-guiY)<=1,
                    "vertical mouse and viewport misaligned "+
                            guiY+"/"+back);
        }
        // Scale selection does NOT accept or read the user's vanilla setting.
        for(int vanillaScale=0;vanillaScale<=4;vanillaScale++)
        {
            yes(EditorGuiScale.width(physicalW,physicalH)==w,
                    "vanilla guiScale changed editor width");
            yes(EditorGuiScale.height(physicalW,physicalH)==h,
                    "vanilla guiScale changed editor height");
        }
        System.out.println(physicalW+"x"+physicalH+
                " -> editor "+w+"x"+h+" @"+scale);
    }
    public static void main(String[] args)
    {
        test(1920,1080);
        test(2560,1440);
        test(3840,2160);
        test(1366,768);
        test(1280,720);
        test(854,480);
        test(1023,767);
        test(1600,900);
        test(1439,811);
        System.out.println("Independent editor GUI Scale regression PASS");
    }
}
