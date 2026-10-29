package com.example.examplemod;

/** GUI Scale 1..4 and narrow-window PREVIEW bounds regressions. */
public final class EditorPreviewGeometrySmokeTest
{
    private static void check(boolean ok,String message)
    {
        if(!ok)throw new AssertionError(message);
    }
    private static EditorPreviewGeometry verify(int w,int h,int left,
            int right,int top,int timeline)
    {
        EditorPreviewGeometry g=EditorPreviewGeometry.calculate(
                w,h,left,right,top,timeline);
        check(g.left>=0&&g.top>=0,"negative origin");
        check(g.right<=w&&g.bottom<=h,"preview overflows window");
        check(g.right>=g.left&&g.bottom>=g.top,
                "invalid rectangle on small GUI");
        check(g.width==g.right-g.left&&g.height==g.bottom-g.top,
                "width/height mismatch");
        return g;
    }

    public static void main(String[] args)
    {
        // Example full-HD display at Minecraft GUI Scale 2 and 4.
        EditorPreviewGeometry large=verify(960,540,163,185,25,130);
        check(large.width==612&&large.canRender(),
                "normal CAMERA viewport should fit exactly");
        EditorPreviewGeometry small=verify(480,270,163,185,25,130);
        check(small.width==132&&small.canRender(),
                "high GUI Scale must shrink preview, never force 150px");
        check(small.right==295,
                "narrow preview must leave all 185px to inspector");
        // POSE uses its own sidebar width.
        check(verify(480,270,180,185,25,130).width==115,
                "POSE preview must also respect its sidebar");
        // When available area is below a useful threshold, skip the FBO.
        check(!verify(380,270,163,185,25,130).canRender(),
                "too-narrow preview should not draw on panels");
        check(!verify(960,100,163,185,25,130).canRender(),
                "timeline may consume remaining height");
        // Degenerate/unexpected input can never cause invalid GL viewport.
        verify(0,0,180,185,25,150);
        verify(1,1,180,185,25,150);
        verify(200,120,163,185,25,130);
        System.out.println("Editor preview GUI geometry regression PASS");
    }
}
