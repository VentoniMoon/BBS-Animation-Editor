package com.example.examplemod;

/** Aspect/orientation regressions for IMAGE in horizontal and TikTok frames. */
public final class CameraImagePlacementSmokeTest
{
    private static void close(double got,double expected,String label)
    {
        if(Math.abs(got-expected)>1E-6D)
            throw new AssertionError(label+": "+got+" != "+expected);
    }

    public static void main(String[] args)
    {
        // 3:2 source must have identical aspect in landscape and portrait
        // output regardless of film's native pixel resolution.
        CameraImagePlacement.Rect land=CameraImagePlacement.calculate(
                480,270,1440,810,1200,800,"contain",false,false);
        CameraImagePlacement.Rect portrait=CameraImagePlacement.calculate(
                270,480,810,1440,1200,800,"contain",false,false);
        close(land.width()/land.height(),1.5,"16:9 preserves IMAGE aspect");
        close(portrait.width()/portrait.height(),1.5,
                "9:16 preserves IMAGE aspect");
        close((land.left+land.right)/2D,960,"landscape center X");
        close((portrait.top+portrait.bottom)/2D,960,"portrait center Y");
        close(portrait.width(),540,"portrait IMAGE fits bounding box");
        close(portrait.height(),360,"portrait IMAGE is not stretched tall");

        // Explicit old-style stretch is supported for deliberate distortion.
        CameraImagePlacement.Rect stretch=CameraImagePlacement.calculate(
                0,0,540,960,1200,800,"stretch",false,false);
        close(stretch.width(),540,"stretch width unchanged");
        close(stretch.height(),960,"stretch height unchanged");

        // "Cover" crops source UV *inside* the requested box.
        CameraImagePlacement.Rect cover=CameraImagePlacement.calculate(
                0,0,540,960,1200,800,"cover",false,false);
        close(cover.width(),540,"cover box width");
        close(cover.height(),960,"cover box height");
        if(!(cover.u0>0D && cover.u1<1D))
            throw new AssertionError("portrait cover crops source X UV");
        close(cover.v0,1D,"wide source cover keeps default upper V");
        close(cover.v1,0D,"wide source cover keeps default lower V");

        // Orientation is independent of aspect and is never affected by FPS.
        CameraImagePlacement.Rect normal=CameraImagePlacement.calculate(
                0,0,300,200,1200,800,"contain",false,false);
        CameraImagePlacement.Rect mirrorX=CameraImagePlacement.calculate(
                0,0,300,200,1200,800,"contain",true,false);
        CameraImagePlacement.Rect mirrorY=CameraImagePlacement.calculate(
                0,0,300,200,1200,800,"contain",false,true);
        close(mirrorX.u0,normal.u1,"horizontal mirror reverses U0");
        close(mirrorX.u1,normal.u0,"horizontal mirror reverses U1");
        close(mirrorY.v0,normal.v1,"vertical mirror reverses V0");
        close(mirrorY.v1,normal.v0,"vertical mirror reverses V1");
        close(mirrorX.width(),normal.width(),"X reflection preserves width");
        close(mirrorY.height(),normal.height(),"Y reflection preserves height");

        CameraImagePlacement.Rect legacy=CameraImagePlacement.calculate(
                0,0,540,960,1200,800,null,false,false);
        close(legacy.width()/legacy.height(),1.5,
                "saved IMAGE without fit property defaults to contain");
        System.out.println("CAMERA IMAGE aspect + UV orientation smoke PASS");
    }
}
