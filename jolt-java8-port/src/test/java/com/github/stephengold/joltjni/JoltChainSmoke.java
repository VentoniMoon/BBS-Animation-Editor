package com.github.stephengold.joltjni;

import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

/**
 * Java 8 native physics test: a dynamic segment swings from a kinematic pin.
 * No Minecraft/BBS classes; tests Jolt's exact SwingTwist joint engine.
 */
public final class JoltChainSmoke {
    private JoltChainSmoke() { }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if (!Jolt.newFactory()) throw new IllegalStateException("Jolt factory unavailable");
        Jolt.registerTypes();

        ObjectLayerPairFilterTable pairs = new ObjectLayerPairFilterTable(1);
        BroadPhaseLayerInterfaceTable layers = new BroadPhaseLayerInterfaceTable(1, 1);
        layers.mapObjectToBroadPhaseLayer(0, 0);
        ObjectVsBroadPhaseLayerFilterTable broad =
                new ObjectVsBroadPhaseLayerFilterTable(layers, 1, pairs, 1);
        PhysicsSystem world = new PhysicsSystem();
        world.init(64, 0, 128, 128, layers, broad, pairs);
        world.setGravity(0F, -9.81F, 0F);
        BodyInterface bodies = world.getBodyInterface();
        TempAllocatorImpl allocator = new TempAllocatorImpl(1024 * 1024);
        JobSystemSingleThreaded jobs = new JobSystemSingleThreaded(512);

        try {
            // Initial pose: horizontal strand. Gravity must rotate it down.
            SphereShape shape = new SphereShape(0.075F);
            BodyCreationSettings pinSettings = new BodyCreationSettings(
                    shape, new RVec3(0D, 1.5D, 0D), new Quat(),
                    EMotionType.Kinematic, 0);
            Body pin = bodies.createBody(pinSettings);
            bodies.addBody(pin.getId(), EActivation.Activate);
            BodyCreationSettings strandSettings = new BodyCreationSettings(
                    shape, new RVec3(0.5D, 1.5D, 0D), new Quat(),
                    EMotionType.Dynamic, 0);
            strandSettings.setLinearDamping(0.15F);
            strandSettings.setAngularDamping(0.15F);
            Body strand = bodies.createBody(strandSettings);
            bodies.addBody(strand.getId(), EActivation.Activate);

            SwingTwistConstraintSettings joint = new SwingTwistConstraintSettings();
            joint.setPosition1(new RVec3(0D, 1.5D, 0D));
            joint.setPosition2(new RVec3(0D, 1.5D, 0D));
            joint.setTwistAxis1(new Vec3(1F, 0F, 0F));
            joint.setTwistAxis2(new Vec3(1F, 0F, 0F));
            joint.setPlaneAxis1(new Vec3(0F, 1F, 0F));
            joint.setPlaneAxis2(new Vec3(0F, 1F, 0F));
            joint.setNormalHalfConeAngle((float)Math.toRadians(120));
            joint.setPlaneHalfConeAngle((float)Math.toRadians(120));
            joint.setTwistMinAngle((float)Math.toRadians(-45));
            joint.setTwistMaxAngle((float)Math.toRadians(45));
            SwingTwistConstraint swing = (SwingTwistConstraint) joint.create(pin, strand);
            world.addConstraint(swing);

            double startY = bodies.getPosition(strand.getId()).yy();
            double minY = startY;
            for (int tick = 0; tick < 100; tick++) {
                // One Minecraft tick == 50ms, at three Jolt substeps.
                int error = world.update(1F / 20F, 3, allocator, jobs);
                if (error != 0) throw new AssertionError("Jolt update flags=" + error);
                double y = bodies.getPosition(strand.getId()).yy();
                if (Double.isNaN(y) || Double.isInfinite(y))
                    throw new AssertionError("Non-finite strand position");
                minY = Math.min(minY, y);
            }
            double endY = bodies.getPosition(strand.getId()).yy();
            System.out.println("Jolt Java8 chain startY=" + startY
                    + " lowestY=" + minY + " endY=" + endY);
            if (minY > startY - 0.1D)
                throw new AssertionError("SwingTwist chain did not fall under gravity");
            if (endY >= startY - 0.1D)
                throw new AssertionError("SwingTwist chain did not reach a lowered pose");
            System.out.println("Jolt Java8 SwingTwist chain: PASS");
        } finally {
            world.close();
            jobs.close();
            allocator.close();
            broad.close();
            layers.close();
            pairs.close();
        }
    }
}
