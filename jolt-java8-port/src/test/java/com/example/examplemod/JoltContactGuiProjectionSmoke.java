package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact gizmo-matched projection and immutable real-Jolt shape snapshot.
 * Pure Java 8, no JNI/Forge/OpenGL.
 */
public final class JoltContactGuiProjectionSmoke
{
    private static void near(String label,float actual,float expected,float allowed)
    {
        if(Math.abs(actual-expected)>allowed)
            throw new AssertionError(label+" expected "+expected+" got "+actual);
    }

    public static void main(String[] args)
    {
        EditorCamera camera=new EditorCamera();
        camera.setTarget(4D,2D,-3D);
        camera.setDistance(5D);

        float[] middle=JoltContactScreenProjection.project(4,2,-3,
                camera,180,25,850,520);
        if(middle==null)throw new AssertionError("Target invisible");
        near("gizmo same X",middle[0],605F,0.001F);
        near("gizmo same Y",middle[1],285F,0.001F);

        float[] higher=JoltContactScreenProjection.project(4,3,-3,
                camera,180,25,850,520);
        if(higher==null || higher[1]>=middle[1])
            throw new AssertionError("Projected world Y should move up on GUI");

        camera.rotate(90F,0F);
        float[] rotated=JoltContactScreenProjection.project(4,2,-3,
                camera,180,25,850,520);
        if(rotated==null)throw new AssertionError("Target lost under yaw");
        near("rotated target X",rotated[0],605F,0.001F);
        near("rotated target Y",rotated[1],285F,0.001F);

        if(JoltContactScreenProjection.project(
                camera.getCameraX(),camera.getCameraY(),camera.getCameraZ(),
                camera,180,25,850,520)!=null)
            throw new AssertionError("Near plane must reject camera origin");
        if(JoltContactScreenProjection.project(4,2,-3,
                camera,180,25,850,0)!=null)
            throw new AssertionError("Zero viewport incorrectly projected");

        List<JoltContactOverlaySnapshot.Shape> source=
                new ArrayList<JoltContactOverlaySnapshot.Shape>();
        float[] a={0F,1F,0F};
        float[] b={0F,2F,0F};
        source.add(new JoltContactOverlaySnapshot.Shape(
                JoltContactOverlaySnapshot.Kind.TORSO,a,b,.2F));
        source.add(new JoltContactOverlaySnapshot.Shape(
                JoltContactOverlaySnapshot.Kind.HAIR,
                new float[]{.2F,2F,0F},new float[]{.2F,1.5F,0F},.04F));
        JoltContactOverlaySnapshot snapshot=
                new JoltContactOverlaySnapshot(20,1,source);
        a[0]=888F;
        source.clear();
        if(snapshot.shapes.size()!=2 || snapshot.torsoCount!=1 ||
                snapshot.hairCount!=1 || snapshot.activeScenes!=1 ||
                snapshot.frame!=20 || snapshot.shapes.get(0).a[0]==888F)
            throw new AssertionError("Live physical snapshot not copied");
        try
        {
            snapshot.shapes.clear();
            throw new AssertionError("Snapshot list should be immutable");
        }
        catch(UnsupportedOperationException expected) { }

        System.out.println("Jolt GUI projection matches editor gizmo at yaw 0/90"+
                " and native shape snapshot is immutable: PASS");
    }
}
