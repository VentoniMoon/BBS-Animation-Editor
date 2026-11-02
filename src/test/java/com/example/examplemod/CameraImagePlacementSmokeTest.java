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
        close(cover.v0,0D,"wide source cover keeps top V=0");
        close(cover.v1,1D,"wide source cover keeps bottom V=1");

        // Regression for the user's upside-down IMAGE and VIDEO frames:
        // a top-left source pixel must land at top-left of the film. Both
        // media types call this SAME placement method in the compositor.
        // The old implementation sent V=1 to the film's top, which flipped
        // every BufferedImage/DynamicTexture vertically.
        CameraImagePlacement.Rect upright=CameraImagePlacement.calculate(
                0,0,320,240,640,480,"contain",false,false);
        close(upright.u0,0D,"unflipped left source pixel");
        close(upright.u1,1D,"unflipped right source pixel");
        close(upright.v0,0D,"unflipped top source pixel");
        close(upright.v1,1D,"unflipped bottom source pixel");
        CameraImagePlacement.Rect tallCover=CameraImagePlacement.calculate(
                0,0,320,180,400,800,"cover",false,false);
        close(tallCover.u0,0D,"tall cover keeps left U");
        close(tallCover.u1,1D,"tall cover keeps right U");
        close(tallCover.v0,0.359375D,"tall cover trims source top");
        close(tallCover.v1,0.640625D,"tall cover trims source bottom");

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
        close(mirrorX.v0,0D,"X-only flip keeps top upright");
        close(mirrorX.v1,1D,"X-only flip keeps bottom upright");
        close(mirrorY.u0,0D,"Y-only flip keeps left unchanged");
        close(mirrorY.u1,1D,"Y-only flip keeps right unchanged");
        CameraImagePlacement.Rect both=CameraImagePlacement.calculate(
                0,0,320,240,640,480,"contain",true,true);
        close(both.u0,1D,"explicit flipX starts at right");
        close(both.u1,0D,"explicit flipX ends at left");
        close(both.v0,1D,"explicit flipY starts at bottom");
        close(both.v1,0D,"explicit flipY ends at top");

        CameraImagePlacement.Rect legacy=CameraImagePlacement.calculate(
                0,0,540,960,1200,800,null,false,false);
        close(legacy.width()/legacy.height(),1.5,
                "saved IMAGE without fit property defaults to contain");
        System.out.println("CAMERA IMAGE aspect + UV orientation smoke PASS");
    }
}
