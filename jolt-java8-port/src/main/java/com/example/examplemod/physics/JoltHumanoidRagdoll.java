package com.example.examplemod.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

/**
 * Physics 2.0 experimental full-body Jolt prototype, independent of Forge.
 *
 * The input positions are the actual CURRENT animated BOBJ bone head/tail
 * positions in model space, ordered parents-first. Unlike JoltHairChain, this
 * rig has multiple branches and a separate constraint for each humanoid
 * joint. All eleven stock Emoticons rigid segments are dynamic. An independent
 * flat floor provides a test contact until Minecraft block geometry is added.
 *
 * The Forge integration must guard loading behind the optional Jolt native
 * bootstrap. No shared mutable world, no physics in the legacy editor if the
 * preview feature is not explicitly enabled.
 */
public final class JoltHumanoidRagdoll implements AutoCloseable
{
    public static final float TICK=1F/20F;
    private static final int SUBSTEPS=3;

    private final ObjectLayerPairFilterTable pairFilter;
    private final BroadPhaseLayerInterfaceTable broadLayers;
    private final ObjectVsBroadPhaseLayerFilterTable broadFilter;
    private final PhysicsSystem world;
    private final BodyInterface interfaceBodies;
    private final TempAllocatorImpl allocator;
    private final JobSystemSingleThreaded jobs;
    private final Body[] body;
    private final TwoBodyConstraint[] joint;
    private Body ground;
    private boolean closed;

    private JoltHumanoidRagdoll(float[][] head,float[][] tail,float[] radius,
            int[] parents,int[] kind,float[][] limits,float floorY)
    {
        int count=head.length;
        pairFilter=new ObjectLayerPairFilterTable(2);
        // 0: ragdoll bodies, 1: world proxy. Adjacent segments never
        // collide against each other. Real world and per-part body contacts
        // can be added once collision geometry fitting has been tested.
        pairFilter.enableCollision(0,1);
        broadLayers=new BroadPhaseLayerInterfaceTable(2,2);
        broadLayers.mapObjectToBroadPhaseLayer(0,0);
        broadLayers.mapObjectToBroadPhaseLayer(1,1);
        broadFilter=new ObjectVsBroadPhaseLayerFilterTable(
                broadLayers,2,pairFilter,2);
        world=new PhysicsSystem();
        world.init(Math.max(128,count*4),0,512,512,
                broadLayers,broadFilter,pairFilter);
        world.setGravity(0F,-9.81F,0F);
        interfaceBodies=world.getBodyInterface();
        allocator=new TempAllocatorImpl(2*1024*1024);
        jobs=new JobSystemSingleThreaded(1024);
        body=new Body[count];
        joint=new TwoBodyConstraint[count];

        try
        {
            for(int i=0;i<count;i++)
            {
                float dx=tail[i][0]-head[i][0];
                float dy=tail[i][1]-head[i][1];
                float dz=tail[i][2]-head[i][2];
                float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                float[] axis={dx/length,dy/length,dz/length};
                float[] q=axisRotation(axis);
                float r=radius[i];
                CapsuleShape shape=new CapsuleShape(
                        Math.max(0.005F,0.5F*length-r),r);
                BodyCreationSettings settings=new BodyCreationSettings(
                        shape,new RVec3(
                                (head[i][0]+tail[i][0])*0.5D,
                                (head[i][1]+tail[i][1])*0.5D,
                                (head[i][2]+tail[i][2])*0.5D),
                        new Quat(q[0],q[1],q[2],q[3]),
                        EMotionType.Dynamic,0);
                settings.setLinearDamping(0.35F);
                settings.setAngularDamping(0.55F);
                Body added;
                try
                {
                    added=interfaceBodies.createBody(settings);
                    if(added==null)
                        throw new IllegalStateException("Cannot allocate ragdoll body "+i);
                    interfaceBodies.addBody(added.getId(),EActivation.Activate);
                }
                finally
                {
                    settings.close();
                    shape.close();
                }
                body[i]=added;
            }

            for(int i=0;i<count;i++)
            {
                int parent=parents[i];
                if(parent<0)continue;
                RVec3 pivot=new RVec3(head[i][0],head[i][1],head[i][2]);
                if(kind[i]==2) // anatomical elbow or knee: one hinge axis
                {
                    HingeConstraintSettings settings=new HingeConstraintSettings();
                    settings.setPoint1(pivot);
                    settings.setPoint2(pivot);
                    // The initial hinge frame is in MODEL REST SPACE.
                    // Exact per-armature axes will be fitted to the BOBJ
                    // bind matrices in the next constraint-calibration pass.
                    Vec3 xAxis=new Vec3(1F,0F,0F);
                    Vec3 normal=new Vec3(0F,1F,0F);
                    settings.setHingeAxis1(xAxis);
                    settings.setHingeAxis2(xAxis);
                    settings.setNormalAxis1(normal);
                    settings.setNormalAxis2(normal);
                    settings.setLimitsMin((float)Math.toRadians(limits[i][3]));
                    settings.setLimitsMax((float)Math.toRadians(limits[i][4]));
                    try
                    {
                        joint[i]=settings.create(body[parent],body[i]);
                        world.addConstraint(joint[i]);
                    }
                    finally { settings.close(); }
                }
                else // shoulder, neck, torso, hip: ball with angular cone
                {
                    SwingTwistConstraintSettings settings=
                            new SwingTwistConstraintSettings();
                    settings.setPosition1(pivot);
                    settings.setPosition2(pivot);
                    float x=tail[i][0]-head[i][0];
                    float y=tail[i][1]-head[i][1];
                    float z=tail[i][2]-head[i][2];
                    float d=(float)Math.sqrt(x*x+y*y+z*z);
                    Vec3 twist=new Vec3(x/d,y/d,z/d);
                    float[] perpendicular=perpendicular(x/d,y/d,z/d);
                    Vec3 plane=new Vec3(perpendicular[0],
                            perpendicular[1],perpendicular[2]);
                    settings.setTwistAxis1(twist);
                    settings.setTwistAxis2(twist);
                    settings.setPlaneAxis1(plane);
                    settings.setPlaneAxis2(plane);
                    settings.setNormalHalfConeAngle(
                            (float)Math.toRadians(limits[i][0]));
                    settings.setPlaneHalfConeAngle(
                            (float)Math.toRadians(limits[i][0]));
                    settings.setTwistMinAngle(
                            (float)Math.toRadians(limits[i][1]));
                    settings.setTwistMaxAngle(
                            (float)Math.toRadians(limits[i][2]));
                    try
                    {
                        joint[i]=settings.create(body[parent],body[i]);
                        world.addConstraint(joint[i]);
                    }
                    finally { settings.close(); }
                }
            }

            // Preview-only collision floor, in the same model-local frame.
            // This does NOT yet query Minecraft blocks or move with actor.
            BoxShape floorShape=new BoxShape(new Vec3(12F,0.125F,12F),0.01F);
            BodyCreationSettings floorSpec=new BodyCreationSettings(
                    floorShape,new RVec3(0D,floorY-0.125D,0D),
                    new Quat(0F,0F,0F,1F),EMotionType.Static,1);
            try
            {
                ground=interfaceBodies.createBody(floorSpec);
                if(ground==null)
                    throw new IllegalStateException("Cannot allocate ragdoll floor");
                interfaceBodies.addBody(ground.getId(),EActivation.DontActivate);
            }
            finally
            {
                floorSpec.close();floorShape.close();
            }
        }
        catch(Throwable failed)
        {
            close();
            throw failed;
        }
    }

    public static JoltHumanoidRagdoll create(float[][] head,float[][] tail,
            float[] radius,int[] parents,int[] kind,float[][] limits,
            float floorY)
    {
        if(head==null||tail==null||radius==null||parents==null||
                kind==null||limits==null||head.length<5||
                head.length>32||tail.length!=head.length||
                radius.length!=head.length||parents.length!=head.length||
                kind.length!=head.length||limits.length!=head.length||
                !Float.isFinite(floorY))
            throw new IllegalArgumentException("Invalid ragdoll topology");
        for(int i=0;i<head.length;i++)
        {
            if(head[i]==null||head[i].length!=3||
                    tail[i]==null||tail[i].length!=3||
                    limits[i]==null||limits[i].length!=5)
                throw new IllegalArgumentException("Malformed body "+i);
            float dist=0F;
            for(int k=0;k<3;k++)
            {
                if(!Float.isFinite(head[i][k])||
                        !Float.isFinite(tail[i][k]))
                    throw new IllegalArgumentException("Nonfinite pivot");
                float dx=head[i][k]-tail[i][k];
                dist+=dx*dx;
            }
            if(dist<0.035F*0.035F||dist>4F||
                    !Float.isFinite(radius[i])||
                    radius[i]<0.025F||radius[i]>0.4F||
                    parents[i]>=i||parents[i]<-1||
                    (i==0)!=(parents[i]<0)||
                    (kind[i]!=1&&kind[i]!=2))
                throw new IllegalArgumentException("Unphysical body "+i);
            for(int k=0;k<5;k++)
                if(!Float.isFinite(limits[i][k]))
                    throw new IllegalArgumentException("Nonfinite joint limits");
            if(kind[i]==2&&limits[i][3]>=limits[i][4])
                throw new IllegalArgumentException("Invalid hinge");
            if(kind[i]==1&&limits[i][0]<=0F&&i>0)
                throw new IllegalArgumentException("Invalid angular cone");
        }
        return new JoltHumanoidRagdoll(head,tail,radius,parents,kind,
                limits,floorY);
    }

    public void step()
    {
        if(closed)throw new IllegalStateException("Closed ragdoll");
        int status=world.update(TICK,SUBSTEPS,allocator,jobs);
        if(status!=0)throw new IllegalStateException(
                "Jolt ragdoll update flags="+status);
    }

    /** Center XYZ and quaternion XYZW for each dynamic rigid body. */
    public float[][] poses()
    {
        if(closed)throw new IllegalStateException("Closed ragdoll");
        float[][] result=new float[body.length][7];
        for(int i=0;i<body.length;i++)
        {
            RVec3 p=interfaceBodies.getPosition(body[i].getId());
            Quat q=interfaceBodies.getRotation(body[i].getId());
            result[i]=new float[]{(float)p.xx(),(float)p.yy(),(float)p.zz(),
                    q.getX(),q.getY(),q.getZ(),q.getW()};
        }
        return result;
    }

    /** Explicit one-shot impact hook for the future keyed impulse track. */
    public void impulse(int index,float ix,float iy,float iz,
            float atX,float atY,float atZ)
    {
        if(closed||index<0||index>=body.length||
                !Float.isFinite(ix)||!Float.isFinite(iy)||
                !Float.isFinite(iz)||!Float.isFinite(atX)||
                !Float.isFinite(atY)||!Float.isFinite(atZ))
            throw new IllegalArgumentException("Invalid ragdoll impulse");
        float length=(float)Math.sqrt(ix*ix+iy*iy+iz*iz);
        if(length>200F)
            throw new IllegalArgumentException("Unbounded ragdoll impulse");
        interfaceBodies.addImpulse(body[index].getId(),
                new Vec3(ix,iy,iz),new RVec3(atX,atY,atZ));
    }

    public static float[] axisRotation(float[] direction)
    {
        if(direction==null||direction.length!=3)
            throw new IllegalArgumentException("Direction required");
        float y=direction[1],x=direction[0],z=direction[2];
        float w=1F-y, qx=-z,qz=x;
        if(w<1E-5F)return new float[]{1F,0F,0F,0F};
        float norm=(float)Math.sqrt(qx*qx+qz*qz+w*w);
        return new float[]{qx/norm,0F,qz/norm,w/norm};
    }

    private static float[] perpendicular(float x,float y,float z)
    {
        float a,b,c;
        if(Math.abs(y)<0.9F){a=-z;b=0F;c=x;}
        else {a=0F;b=z;c=-y;}
        float n=(float)Math.sqrt(a*a+b*b+c*c);
        return new float[]{a/n,b/n,c/n};
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        for(TwoBodyConstraint c:joint)
            if(c!=null){world.removeConstraint(c);c.close();}
        for(Body b:body)
            if(b!=null)
            {
                interfaceBodies.removeBody(b.getId());
                interfaceBodies.destroyBody(b.getId());
            }
        if(ground!=null)
        {
            interfaceBodies.removeBody(ground.getId());
            interfaceBodies.destroyBody(ground.getId());
            ground=null;
        }
        world.close();jobs.close();allocator.close();broadFilter.close();
        broadLayers.close();pairFilter.close();
    }
}
