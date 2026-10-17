package com.example.examplemod.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;

/**
 * Standalone Java 8 prototype of a Jolt-based hanging bone chain.
 * Source API is intentionally independent of Minecraft/BBS/Chameleon.
 *
 * Jolt must be loaded and initialized by the caller before construction.
 * The root follows a kinematic body; each link is a real dynamic rigid body
 * with orientation and a SwingTwist cone joint. No guessed Euler rotations.
 *
 * Inspired by Wemppy BBS Physics Engine's BoneChainRig/PhysicsJoints (MIT).
 */
public final class JoltHairChain implements AutoCloseable {
    public static final float TICK = 1F / 20F;
    public static final int SUBSTEPS = 3;
    /**
     * Opt-in test parameter only. Wenmppy exposes bend as a chain setting;
     * ours remains 80 degrees unless explicitly configured at launch.
     */
    public static final String BEND_DEGREES_PROPERTY =
            "bbs.editor.physics.jolt.bendDegrees";

    private final ObjectLayerPairFilterTable pairFilter;
    private final BroadPhaseLayerInterfaceTable broadLayers;
    private final ObjectVsBroadPhaseLayerFilterTable broadFilter;
    private final PhysicsSystem world;
    private final BodyInterface bodies;
    private final TempAllocatorImpl allocator;
    private final JobSystemSingleThreaded jobs;
    private final Body pin;
    private final Body[] links;
    private final float linkLength;
    private final float startX;
    private final float startY;
    private final float startZ;
    private final float[] initialDirection;
    private final float[] initialRotation;
    private final float[][] initialRotations;
    private final SwingTwistConstraint[] joints;
    private boolean closed;

    /** Backward-compatible straight vertical chain. */
    public JoltHairChain(int count, float linkLength, float startY,
            float stiffness, float damping, float falloff) {
        this(count,linkLength,0F,startY,0F,0F,-1F,0F,
                stiffness,damping,falloff);
    }

    /** Straight chain from arbitrary world-space attachment and tangent. */
    public JoltHairChain(int count,float linkLength,
            float rootX,float rootY,float rootZ,
            float directionX,float directionY,float directionZ,
            float stiffness,float damping,float falloff) {
        this(straightPivots(count,linkLength,rootX,rootY,rootZ,
                directionX,directionY,directionZ),stiffness,damping,falloff);
    }

    /**
     * General Chameleon model: one world-space pivot for each bone, plus
     * the terminal tip. Every segment has its OWN real length and direction.
     * Supports curved and unequal-length strands without flattening them.
     */
    public JoltHairChain(float[][] originalPivots,
            float stiffness,float damping,float falloff) {
        if (originalPivots==null || originalPivots.length<2 ||
                originalPivots.length>65)
            throw new IllegalArgumentException("Need 1-64 native bone segments");
        if (!Float.isFinite(stiffness) || !Float.isFinite(damping) ||
                !Float.isFinite(falloff))
            throw new IllegalArgumentException("Invalid strand settings");
        // Validate BEFORE native world allocation. Bad JVM properties must
        // never leak a half-initialized physics scene.
        float allowedSwingRadians=allowedSwingRadians();
        int count=originalPivots.length-1;
        float[][] points=new float[count+1][3];
        for(int i=0;i<points.length;i++) {
            if(originalPivots[i]==null || originalPivots[i].length!=3)
                throw new IllegalArgumentException("Invalid world pivot");
            for(int j=0;j<3;j++) {
                if(!Float.isFinite(originalPivots[i][j]))
                    throw new IllegalArgumentException("Non-finite world pivot");
                points[i][j]=originalPivots[i][j];
            }
        }
        float[] lengths=new float[count];
        float[][] directions=new float[count][3];
        float[][] rotations=new float[count][4];
        float total=0F;
        for(int i=0;i<count;i++) {
            float dx=points[i+1][0]-points[i][0];
            float dy=points[i+1][1]-points[i][1];
            float dz=points[i+1][2]-points[i][2];
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(!(length>=0.05F) || !Float.isFinite(length))
                throw new IllegalArgumentException("Short/invalid Jolt bone "+i);
            lengths[i]=length;
            total+=length;
            directions[i]=new float[] {dx/length,dy/length,dz/length};
            rotations[i]=rotationFromNegativeY(directions[i]);
        }
        this.linkLength=total/count;
        this.startX=points[0][0];
        this.startY=points[0][1];
        this.startZ=points[0][2];
        this.initialDirection=directions[0].clone();
        this.initialRotation=rotations[0].clone();
        this.initialRotations=rotations;
        this.pairFilter=new ObjectLayerPairFilterTable(1);
        this.broadLayers=new BroadPhaseLayerInterfaceTable(1,1);
        broadLayers.mapObjectToBroadPhaseLayer(0,0);
        this.broadFilter=new ObjectVsBroadPhaseLayerFilterTable(
                broadLayers,1,pairFilter,1);
        this.world=new PhysicsSystem();
        world.init(256,0,512,512,broadLayers,broadFilter,pairFilter);
        world.setGravity(0F,-9.81F,0F);
        this.bodies=world.getBodyInterface();
        this.allocator=new TempAllocatorImpl(2*1024*1024);
        this.jobs=new JobSystemSingleThreaded(1024);
        this.links=new Body[count];
        this.joints=new SwingTwistConstraint[count];

        // Inertia-only stand-ins: no invisible contact capsules.
        SphereShape pinShape=new SphereShape(0.025F);
        BodyCreationSettings pinSpec=new BodyCreationSettings(
                pinShape,new RVec3(startX,startY,startZ),
                new Quat(rotations[0][0],rotations[0][1],
                        rotations[0][2],rotations[0][3]),
                EMotionType.Kinematic,0);
        this.pin=bodies.createBody(pinSpec);
        bodies.addBody(pin.getId(),EActivation.Activate);
        pinSpec.close();
        pinShape.close();
        Body previous=pin;
        float dampingRate=dampingPerSecond(damping);
        for(int i=0;i<count;i++) {
            float length=lengths[i];
            float radius=Math.min(0.045F,length*0.30F);
            CapsuleShape capsule=new CapsuleShape(
                    Math.max(0.005F,length*0.5F-radius),radius);
            float[] q=rotations[i];
            BodyCreationSettings spec=new BodyCreationSettings(
                    capsule,new RVec3(
                            (points[i][0]+points[i+1][0])*0.5D,
                            (points[i][1]+points[i+1][1])*0.5D,
                            (points[i][2]+points[i+1][2])*0.5D),
                    new Quat(q[0],q[1],q[2],q[3]),
                    EMotionType.Dynamic,0);
            spec.setLinearDamping(dampingRate);
            spec.setAngularDamping(dampingRate);
            spec.setGravityFactor(1F);
            Body body=bodies.createBody(spec);
            bodies.addBody(body.getId(),EActivation.Activate);
            spec.close();
            capsule.close();
            links[i]=body;

            SwingTwistConstraintSettings jointSpec=new SwingTwistConstraintSettings();
            RVec3 pivot=new RVec3(points[i][0],points[i][1],points[i][2]);
            jointSpec.setPosition1(pivot);
            jointSpec.setPosition2(pivot);
            float[] direction=directions[i],plane=perpendicular(direction);
            jointSpec.setTwistAxis1(new Vec3(
                    direction[0],direction[1],direction[2]));
            jointSpec.setTwistAxis2(new Vec3(
                    direction[0],direction[1],direction[2]));
            jointSpec.setPlaneAxis1(new Vec3(plane[0],plane[1],plane[2]));
            jointSpec.setPlaneAxis2(new Vec3(plane[0],plane[1],plane[2]));
            float swingAngle=allowedSwingRadians;
            jointSpec.setNormalHalfConeAngle(swingAngle);
            jointSpec.setPlaneHalfConeAngle(swingAngle);
            float twistAngle=(float)Math.toRadians(45D);
            jointSpec.setTwistMinAngle(-twistAngle);
            jointSpec.setTwistMaxAngle(twistAngle);
            SwingTwistConstraint constraint=(SwingTwistConstraint)
                    jointSpec.create(previous,body);
            world.addConstraint(constraint);
            joints[i]=constraint;
            jointSpec.close();
            tuneSpring(constraint,stiffness,damping,falloff,i,count);
            previous=body;
        }
    }

    public void setGravity(float multiplier) {
        requireOpen();
        if (!Float.isFinite(multiplier) || multiplier < 0F || multiplier > 2F)
            throw new IllegalArgumentException("Invalid gravity multiplier");
        world.setGravity(0F, -9.81F * multiplier, 0F);
    }

    /** Advance exactly one Minecraft animation tick at a fixed 60Hz substep. */
    public void step() {
        requireOpen();
        int status = world.update(TICK, SUBSTEPS, allocator, jobs);
        if (status != 0)
            throw new IllegalStateException("Jolt update flags=" + status);
    }

    /** Move the root continuously; caller must invoke once before next step. */
    public void moveAnchor(double x, double y, double z,
            float qx, float qy, float qz, float qw) {
        requireOpen();
        bodies.moveKinematic(pin.getId(), new RVec3(x, y, z),
                new Quat(qx, qy, qz, qw), TICK);
    }

    public RVec3 position(int index) {
        requireOpen();
        return bodies.getPosition(links[index].getId());
    }

    public Quat rotation(int index) {
        requireOpen();
        return bodies.getRotation(links[index].getId());
    }

    public int size() { return links.length; }

    public float[] initialWorldRotation() { return initialRotation.clone(); }

    public float[] initialWorldRotation(int boneIndex) {
        return initialRotations[boneIndex].clone();
    }

    /**
     * Physical end points, rather than body centres, for visual diagnostics.
     * Bone pose reconstruction should use the actual body quaternion.
     */
    public RVec3 attachmentPosition() {
        return new RVec3(startX, startY, startZ);
    }

    private static float[][] straightPivots(int count,float linkLength,
            float rootX,float rootY,float rootZ,
            float dx,float dy,float dz) {
        if(count<1 || count>64 || !(linkLength>=0.05F) ||
                !Float.isFinite(linkLength) || !Float.isFinite(rootX) ||
                !Float.isFinite(rootY) || !Float.isFinite(rootZ) ||
                !Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz))
            throw new IllegalArgumentException("Invalid straight strand");
        float m=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!(m>1E-6F) || !Float.isFinite(m))
            throw new IllegalArgumentException("Invalid strand direction");
        float[][] points=new float[count+1][3];
        for(int i=0;i<=count;i++) {
            points[i][0]=rootX+i*linkLength*dx/m;
            points[i][1]=rootY+i*linkLength*dy/m;
            points[i][2]=rootZ+i*linkLength*dz/m;
        }
        return points;
    }

    private static float[] perpendicular(float[] direction) {
        float x, y, z;
        if (Math.abs(direction[1]) < 0.9F) {
            x = -direction[2];
            y = 0F;
            z = direction[0];
        } else {
            x = 0F;
            y = direction[2];
            z = -direction[1];
        }
        float len = (float)Math.sqrt(x*x+y*y+z*z);
        return new float[] {x/len,y/len,z/len};
    }

    /** Quaternion mapping local -Y (the authored strand axis) to world tangent. */
    private static float[] rotationFromNegativeY(float[] d) {
        float w = 1F - d[1];
        float x = -d[2], z = d[0];
        if (w < 1E-5F) return new float[] {1F,0F,0F,0F};
        float length = (float)Math.sqrt(x*x+z*z+w*w);
        return new float[] {x/length,0F,z/length,w/length};
    }

    private static void tuneSpring(SwingTwistConstraint constraint,
            float stiffness, float damping, float falloff, int index, int total) {
        stiffness = clamp(stiffness);
        damping = clamp(damping);
        falloff = clamp(falloff);
        EMotorState state = stiffness == 0F ? EMotorState.Off : EMotorState.Position;
        constraint.setSwingMotorState(state);
        constraint.setTwistMotorState(state);
        if (state == EMotorState.Off) return;
        float along = total <= 1 ? 0F : index / (float)(total - 1);
        float share = 1F - falloff * along;
        float hz = (0.5F + 11.5F * stiffness) * (float)Math.sqrt(share);
        float ratio = 0.25F + 0.75F * damping;
        MotorSettings swing = constraint.getSwingMotorSettings();
        MotorSettings twist = constraint.getTwistMotorSettings();
        swing.getSpringSettings().setFrequency(hz);
        swing.getSpringSettings().setDamping(ratio);
        twist.getSpringSettings().setFrequency(hz);
        twist.getSpringSettings().setDamping(ratio);
    }

    private static float allowedSwingRadians() {
        String text=System.getProperty(BEND_DEGREES_PROPERTY);
        if(text==null || text.trim().isEmpty())
            return (float)Math.toRadians(80D);
        final float degrees;
        try {
            degrees=Float.parseFloat(text.trim());
        } catch(NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Invalid Jolt hair bend degrees: "+text,error);
        }
        if(!Float.isFinite(degrees) || degrees<10F || degrees>175F)
            throw new IllegalArgumentException(
                    "Jolt hair bend degrees must be between 10 and 175: "+text);
        return (float)Math.toRadians(degrees);
    }

    private static float dampingPerSecond(float knob) {
        float loss = Math.min(0.99F, Math.max(0F, knob));
        float perStep = (float)Math.pow(1F - loss, 1D / SUBSTEPS);
        return (1F - perStep) * SUBSTEPS / TICK;
    }

    private static float clamp(float n) {
        return Math.min(1F, Math.max(0F, n));
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Chain already closed");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        // Explicitly release native scene-owned objects before world teardown.
        // A Body returned by BodyInterface is borrowed from the PhysicsSystem.
        for (SwingTwistConstraint joint : joints) {
            if (joint != null) {
                world.removeConstraint(joint);
                joint.close();
            }
        }
        for (Body link : links) {
            if (link != null) {
                bodies.removeBody(link.getId());
                bodies.destroyBody(link.getId());
            }
        }
        bodies.removeBody(pin.getId());
        bodies.destroyBody(pin.getId());
        world.close();
        jobs.close();
        allocator.close();
        broadFilter.close();
        broadLayers.close();
        pairFilter.close();
    }
}
