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
    private final SwingTwistConstraint[] joints;
    private boolean closed;

    public JoltHairChain(int count, float linkLength, float startY,
            float stiffness, float damping, float falloff) {
        this(count, linkLength, 0F, startY, 0F, 0F, -1F, 0F,
                stiffness, damping, falloff);
    }

    /**
     * Construct a strand at a genuine world-space bone pivot and direction.
     * The caller must derive these from the native clean Chameleon matrices;
     * no bone-space or render-space inversions belong inside the solver.
     */
    public JoltHairChain(int count, float linkLength,
            float rootX, float rootY, float rootZ,
            float directionX, float directionY, float directionZ,
            float stiffness, float damping, float falloff) {
        if (count < 1 || count > 64 || !(linkLength >= 0.10F) ||
                !Float.isFinite(linkLength) || !Float.isFinite(rootX) ||
                !Float.isFinite(rootY) || !Float.isFinite(rootZ) ||
                !Float.isFinite(directionX) || !Float.isFinite(directionY) ||
                !Float.isFinite(directionZ) ||
                !Float.isFinite(stiffness) || !Float.isFinite(damping) ||
                !Float.isFinite(falloff))
            throw new IllegalArgumentException("Invalid chain parameters");
        this.linkLength = linkLength;
        this.startX = rootX;
        this.startY = rootY;
        this.startZ = rootZ;
        float magnitude = (float) Math.sqrt(directionX * directionX +
                directionY * directionY + directionZ * directionZ);
        if (!(magnitude > 1E-6F) || !Float.isFinite(magnitude))
            throw new IllegalArgumentException("Zero-length initial direction");
        this.initialDirection = new float[] {directionX / magnitude,
                directionY / magnitude, directionZ / magnitude};
        this.initialRotation = rotationFromNegativeY(initialDirection);
        this.pairFilter = new ObjectLayerPairFilterTable(1);
        this.broadLayers = new BroadPhaseLayerInterfaceTable(1, 1);
        broadLayers.mapObjectToBroadPhaseLayer(0, 0);
        this.broadFilter =
                new ObjectVsBroadPhaseLayerFilterTable(broadLayers, 1, pairFilter, 1);
        this.world = new PhysicsSystem();
        world.init(256, 0, 512, 512, broadLayers, broadFilter, pairFilter);
        world.setGravity(0F, -9.81F, 0F);
        this.bodies = world.getBodyInterface();
        this.allocator = new TempAllocatorImpl(2 * 1024 * 1024);
        this.jobs = new JobSystemSingleThreaded(1024);
        this.links = new Body[count];
        this.joints = new SwingTwistConstraint[count];

        // No collisions in this first prototype. The physically shaped rods
        // still carry inertia but cannot push against invisible body capsules.
        SphereShape pinShape = new SphereShape(0.025F);
        BodyCreationSettings pinSpec = new BodyCreationSettings(
                pinShape, new RVec3(startX, startY, startZ),
                new Quat(initialRotation[0], initialRotation[1],
                        initialRotation[2], initialRotation[3]),
                EMotionType.Kinematic, 0);
        this.pin = bodies.createBody(pinSpec);
        bodies.addBody(pin.getId(), EActivation.Activate);
        pinSpec.close();
        pinShape.close();
        float radius = Math.min(0.045F, linkLength * 0.3F);
        CapsuleShape shape = new CapsuleShape(
                Math.max(0.005F, linkLength * 0.5F - radius), radius);
        float dampingRate = dampingPerSecond(damping);
        Body previous = pin;
        for (int i = 0; i < count; i++) {
            float distance = (i + 0.5F) * linkLength;
            BodyCreationSettings spec = new BodyCreationSettings(
                    shape, new RVec3(
                            startX + distance * initialDirection[0],
                            startY + distance * initialDirection[1],
                            startZ + distance * initialDirection[2]),
                    new Quat(initialRotation[0], initialRotation[1],
                            initialRotation[2], initialRotation[3]),
                    EMotionType.Dynamic, 0);
            spec.setLinearDamping(dampingRate);
            spec.setAngularDamping(dampingRate);
            spec.setGravityFactor(1F);
            Body body = bodies.createBody(spec);
            bodies.addBody(body.getId(), EActivation.Activate);
            spec.close();
            links[i] = body;

            RVec3 pivot = new RVec3(
                    startX + i * linkLength * initialDirection[0],
                    startY + i * linkLength * initialDirection[1],
                    startZ + i * linkLength * initialDirection[2]);
            SwingTwistConstraintSettings jointSpec = new SwingTwistConstraintSettings();
            jointSpec.setPosition1(pivot);
            jointSpec.setPosition2(pivot);
            float[] d = initialDirection;
            float[] plane = perpendicular(d);
            jointSpec.setTwistAxis1(new Vec3(d[0], d[1], d[2]));
            jointSpec.setTwistAxis2(new Vec3(d[0], d[1], d[2]));
            jointSpec.setPlaneAxis1(new Vec3(plane[0], plane[1], plane[2]));
            jointSpec.setPlaneAxis2(new Vec3(plane[0], plane[1], plane[2]));
            float angle = (float)Math.toRadians(80D);
            jointSpec.setNormalHalfConeAngle(angle);
            jointSpec.setPlaneHalfConeAngle(angle);
            float twist = (float)Math.toRadians(45D);
            jointSpec.setTwistMinAngle(-twist);
            jointSpec.setTwistMaxAngle(twist);
            SwingTwistConstraint constraint =
                    (SwingTwistConstraint)jointSpec.create(previous, body);
            world.addConstraint(constraint);
            joints[i] = constraint;
            jointSpec.close();
            tuneSpring(constraint, stiffness, damping, falloff, i, count);
            previous = body;
        }
        shape.close();
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

    /**
     * Physical end points, rather than body centres, for visual diagnostics.
     * Bone pose reconstruction should use the actual body quaternion.
     */
    public RVec3 attachmentPosition() {
        return new RVec3(startX, startY, startZ);
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
