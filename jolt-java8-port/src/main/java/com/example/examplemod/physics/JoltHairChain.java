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
    private final float startY;
    private final SwingTwistConstraint[] joints;
    private boolean closed;

    public JoltHairChain(int count, float linkLength, float startY,
            float stiffness, float damping, float falloff) {
        if (count < 1 || count > 64 || !(linkLength >= 0.10F) ||
                !Float.isFinite(linkLength) || !Float.isFinite(startY) ||
                !Float.isFinite(stiffness) || !Float.isFinite(damping) ||
                !Float.isFinite(falloff))
            throw new IllegalArgumentException("Invalid chain parameters");
        this.linkLength = linkLength;
        this.startY = startY;
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
                pinShape, new RVec3(0D, startY, 0D), new Quat(),
                EMotionType.Kinematic, 0);
        this.pin = bodies.createBody(pinSpec);
        bodies.addBody(pin.getId(), EActivation.Activate);
        float radius = Math.min(0.045F, linkLength * 0.3F);
        CapsuleShape shape = new CapsuleShape(
                Math.max(0.005F, linkLength * 0.5F - radius), radius);
        float dampingRate = dampingPerSecond(damping);
        Body previous = pin;
        for (int i = 0; i < count; i++) {
            float centerY = startY - (i + 0.5F) * linkLength;
            BodyCreationSettings spec = new BodyCreationSettings(
                    shape, new RVec3(0D, centerY, 0D), new Quat(),
                    EMotionType.Dynamic, 0);
            spec.setLinearDamping(dampingRate);
            spec.setAngularDamping(dampingRate);
            spec.setGravityFactor(1F);
            Body body = bodies.createBody(spec);
            bodies.addBody(body.getId(), EActivation.Activate);
            links[i] = body;

            RVec3 pivot = new RVec3(0D, startY - i * linkLength, 0D);
            SwingTwistConstraintSettings jointSpec = new SwingTwistConstraintSettings();
            jointSpec.setPosition1(pivot);
            jointSpec.setPosition2(pivot);
            jointSpec.setTwistAxis1(new Vec3(0F, -1F, 0F));
            jointSpec.setTwistAxis2(new Vec3(0F, -1F, 0F));
            jointSpec.setPlaneAxis1(new Vec3(1F, 0F, 0F));
            jointSpec.setPlaneAxis2(new Vec3(1F, 0F, 0F));
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
            tuneSpring(constraint, stiffness, damping, falloff, i, count);
            previous = body;
        }
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
        world.close();
        jobs.close();
        allocator.close();
        broadFilter.close();
        broadLayers.close();
        pairFilter.close();
    }
}
