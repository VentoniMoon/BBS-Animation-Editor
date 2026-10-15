package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/**
 * Initial preview torso collision proxy, attached to the native Emoticons
 * low_body matrix captured after the normal actor render. This is deliberately
 * conservative and is NOT a fitted mesh collider. Units are Minecraft blocks.
 */
public final class BodyPartPhysicsBodyCapsules
{
    private BodyPartPhysicsBodyCapsules() {}

    public static List<BodyPartPhysicsCollisions.Capsule> forTorso(float[] lowBodyWorld)
    {
        List<BodyPartPhysicsCollisions.Capsule> result =
                new ArrayList<BodyPartPhysicsCollisions.Capsule>();
        if (lowBodyWorld == null || lowBodyWorld.length != 16) return result;
        try
        {
            // Native bone Y follows the animated body orientation; these
            // endpoints are in its local space, not world vertical.
            BodyPartChainSolver.Vec3 a = BodyPartPhysicsPoseAdapter.transformPoint(
                    lowBodyWorld, new BodyPartChainSolver.Vec3(0F, -0.10F, 0F));
            BodyPartChainSolver.Vec3 b = BodyPartPhysicsPoseAdapter.transformPoint(
                    lowBodyWorld, new BodyPartChainSolver.Vec3(0F, 0.42F, 0F));
            // Approximately 0.18 block torso radius. A later pass must
            // read actual actor/body geometry for accurate contacts.
            result.add(new BodyPartPhysicsCollisions.Capsule(a, b, 0.18F));
        }
        catch (IllegalArgumentException ignored)
        {
            // Invalid or missing native matrix: disable contacts rather than
            // send a collider to a guessed world location.
        }
        return result;
    }
}
