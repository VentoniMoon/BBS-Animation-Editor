package com.example.examplemod;

/**
 * Java 8 standalone position-based strand solver.
 * Inspired by BBS FS chain constraints; independent implementation.
 * World units are blocks, time is seconds. No renderer integration yet.
 */
public final class BodyPartChainSolver
{
    public static final class Vec3
    {
        public final float x, y, z;
        public Vec3(float x, float y, float z) { this.x=x; this.y=y; this.z=z; }
        public Vec3 add(Vec3 p) { return new Vec3(x+p.x,y+p.y,z+p.z); }
        public Vec3 sub(Vec3 p) { return new Vec3(x-p.x,y-p.y,z-p.z); }
        public Vec3 scale(float s) { return new Vec3(x*s,y*s,z*s); }
        public float length() { return (float)Math.sqrt(x*x+y*y+z*z); }
    }

    private final Vec3[] points, previous;
    private final float[] segmentLengths;

    public BodyPartChainSolver(Vec3[] pose)
    {
        if (pose == null || pose.length < 2) throw new IllegalArgumentException("Need at least 2 joints");
        points=new Vec3[pose.length];
        previous=new Vec3[pose.length];
        segmentLengths=new float[pose.length-1];
        for (int i=0;i<pose.length;i++)
        {
            if (pose[i]==null) throw new IllegalArgumentException("Null joint");
            points[i]=previous[i]=pose[i];
            if (i>0)
            {
                segmentLengths[i-1]=pose[i].sub(pose[i-1]).length();
                if (segmentLengths[i-1]<0.00001F) throw new IllegalArgumentException("Zero length");
            }
        }
    }

    public void reset(Vec3[] pose)
    {
        check(pose);
        for (int i=0;i<pose.length;i++) points[i]=previous[i]=pose[i];
    }

    private void check(Vec3[] pose)
    {
        if (pose==null || pose.length!=points.length) throw new IllegalArgumentException("Joint count changed");
        for (Vec3 p:pose) if (p==null) throw new IllegalArgumentException("Null joint");
    }

    public void step(Vec3[] animatedPose,float dt,float gravity,float stiffness,float damping)
    {
        step(animatedPose, dt, gravity, stiffness, damping, null, 0F);
    }

    public void step(Vec3[] animatedPose,float dt,float gravity,float stiffness,
            float damping, java.util.List<BodyPartPhysicsCollisions.Capsule> colliders,
            float strandRadius)
    {
        check(animatedPose);
        if (!(dt>0F) || dt>0.1F) throw new IllegalArgumentException("Invalid timestep");
        gravity=clamp(gravity,0F,2F);
        stiffness=clamp(stiffness,0F,1F);
        damping=clamp(damping,0F,1F);
        final float h=dt/2F;
        for (int sub=0;sub<2;sub++)
        {
            points[0]=previous[0]=animatedPose[0];
            for (int i=1;i<points.length;i++)
            {
                Vec3 current=points[i];
                Vec3 velocity=current.sub(previous[i]).scale(1F-damping);
                previous[i]=current;
                points[i]=current.add(velocity).add(new Vec3(0F,-9.81F*gravity*h*h,0F));
                points[i]=points[i].add(animatedPose[i].sub(points[i]).scale(stiffness*0.1F));
            }
            for (int iteration=0;iteration<8;iteration++)
            {
                points[0]=animatedPose[0];
                for (int i=1;i<points.length;i++)
                {
                    Vec3 delta=points[i].sub(points[i-1]);
                    float distance=delta.length();
                    if (distance<0.000001F)
                    {
                        delta=animatedPose[i].sub(animatedPose[i-1]);
                        distance=delta.length();
                        if (distance<0.000001F) continue;
                    }
                    Vec3 direction=delta.scale(1F/distance);
                    if (i==1) points[i]=points[i-1].add(direction.scale(segmentLengths[i-1]));
                    else
                    {
                        float correction=(distance-segmentLengths[i-1])*0.5F;
                        points[i-1]=points[i-1].add(direction.scale(correction));
                        points[i]=points[i].sub(direction.scale(correction));
                    }
                }
            }
            points[0]=animatedPose[0];
            // Alternate segment and particle contacts with distance constraints.
            // A final length-only pass used to undo collision separation.
            if (colliders != null && !colliders.isEmpty())
            {
                for (int contactPass=0;contactPass<5;contactPass++)
                {
                    points[0]=animatedPose[0];
                    for (int i=1;i<points.length;i++)
                        points[i]=BodyPartPhysicsCollisions.resolve(
                                points[i],strandRadius,colliders);

                    for (int i=1;i<points.length;i++)
                    {
                        Vec3[] endpoints=BodyPartPhysicsCollisions.resolveSegment(
                                points[i-1],points[i],strandRadius,colliders,i==1);
                        if (i>1) points[i-1]=endpoints[0];
                        points[i]=endpoints[1];
                    }

                    // Restore distances, then re-project contacts on the
                    // following pass. Root is never moved by a constraint.
                    points[0]=animatedPose[0];
                    for (int i=1;i<points.length;i++)
                    {
                        Vec3 delta=points[i].sub(points[i-1]);
                        float distance=delta.length();
                        if (distance<0.000001F) continue;
                        Vec3 direction=delta.scale(1F/distance);
                        float difference=distance-segmentLengths[i-1];
                        if (i==1)
                            points[i]=points[i].sub(direction.scale(difference));
                        else
                        {
                            points[i-1]=points[i-1].add(direction.scale(difference*0.5F));
                            points[i]=points[i].sub(direction.scale(difference*0.5F));
                        }
                    }
                }
                // Finish with contact separation, not length-only projection.
                // Severe overlaps with a pinned root can still be unsolvable.
                points[0]=animatedPose[0];
                for (int i=1;i<points.length;i++)
                    points[i]=BodyPartPhysicsCollisions.resolve(
                            points[i],strandRadius,colliders);
                for (int i=1;i<points.length;i++)
                {
                    Vec3[] endpoints=BodyPartPhysicsCollisions.resolveSegment(
                            points[i-1],points[i],strandRadius,colliders,i==1);
                    if (i>1) points[i-1]=endpoints[0];
                    points[i]=endpoints[1];
                }
            }
        }
    }

    private static float clamp(float x,float low,float high)
    { return Float.isNaN(x)?low:Math.max(low,Math.min(high,x)); }

    public Vec3 getPoint(int index) { return points[index]; }
    public int size() { return points.length; }
}
