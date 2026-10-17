package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, read-only snapshot of the *installed* native Jolt collision
 * shapes at one animation frame. No GL, Forge, JNI or world-renderer calls.
 *
 * Input endpoints are in the same physical world coordinate space as Jolt.
 * All drawing and camera projection happen AFTER the Preview FBO is blitted
 * to the GUI. This separates physical truth from OpenGL render state.
 */
public final class JoltContactOverlaySnapshot
{
    public enum Kind { TORSO, HAIR }

    public static final class Shape
    {
        public final Kind kind;
        public final float[] a;
        public final float[] b;
        public final float radius;

        public Shape(Kind kind, float[] a, float[] b, float radius)
        {
            if(kind==null || !isValid(a) || !isValid(b) ||
                    !Float.isFinite(radius) || radius<=0F || radius>0.5F)
                throw new IllegalArgumentException("Invalid Jolt capsule snapshot");
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            if(dx*dx+dy*dy+dz*dz<0.000025F)
                throw new IllegalArgumentException("Zero length collision capsule");
            this.kind=kind;
            this.a=a.clone();
            this.b=b.clone();
            this.radius=radius;
        }
    }

    /** Actual kinematic Jolt box setup for an added BodyPart cube. */
    public static final class Box
    {
        /** World xyz, quaternion xyzw, positive half xyz. */
        public final float[] pose;

        public Box(float[] input)
        {
            if(input==null || input.length!=10)
                throw new IllegalArgumentException("Expected 10-float Jolt box");
            for(float value:input)
                if(!Float.isFinite(value))
                    throw new IllegalArgumentException("Non-finite Jolt box");
            for(int i=7;i<10;i++)
                if(!(input[i]>=0.003F) || input[i]>2F)
                    throw new IllegalArgumentException("Invalid Jolt box dimensions");
            float q=input[3]*input[3]+input[4]*input[4]+
                    input[5]*input[5]+input[6]*input[6];
            if(Math.abs(q-1F)>0.02F)
                throw new IllegalArgumentException("Invalid Jolt box quaternion");
            this.pose=input.clone();
        }
    }

    public final int frame;
    public final int activeScenes;
    public final int torsoCount;
    public final int hairCount;
    public final List<Shape> shapes;
    public final List<Box> boxes;
    public final int boxCount;

    public JoltContactOverlaySnapshot(int frame,int activeScenes,List<Shape> items)
    {
        this(frame,activeScenes,items,Collections.<Box>emptyList());
    }

    public JoltContactOverlaySnapshot(int frame,int activeScenes,List<Shape> items,
            List<Box> boxItems)
    {
        if(activeScenes<0 || items==null || boxItems==null)
            throw new IllegalArgumentException("Invalid Jolt overlay snapshot");
        List<Shape> copy=new ArrayList<Shape>(items.size());
        int hair=0,torso=0;
        for(Shape item:items)
        {
            if(item==null)
                throw new IllegalArgumentException("Null native Jolt shape");
            copy.add(new Shape(item.kind,item.a,item.b,item.radius));
            if(item.kind==Kind.HAIR)hair++;
            else torso++;
        }
        this.frame=frame;
        this.activeScenes=activeScenes;
        this.torsoCount=torso;
        this.hairCount=hair;
        this.shapes=Collections.unmodifiableList(copy);
        List<Box> boxCopy=new ArrayList<Box>(boxItems.size());
        for(Box box:boxItems)
        {
            if(box==null)throw new IllegalArgumentException("Null Jolt box");
            boxCopy.add(new Box(box.pose));
        }
        this.boxCount=boxCopy.size();
        this.boxes=Collections.unmodifiableList(boxCopy);
    }

    private static boolean isValid(float[] v)
    {
        if(v==null || v.length!=3)return false;
        for(float x:v)if(!Float.isFinite(x))return false;
        return true;
    }
}
