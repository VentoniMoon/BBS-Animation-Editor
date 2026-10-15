package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Preview-only application of a small physical swing to Chameleon pose bones.
 * Chameleon AnimatedPoseTransform stores Euler radians. This first adapter
 * composes local swing matrices with existing Euler pose before extraction.
 * The native Chameleon rotation order still needs validation before enabling.
 */
public final class BodyPartChameleonPhysicsPose
{
    private BodyPartChameleonPhysicsPose() {}

    @SuppressWarnings("unchecked")
    public static boolean addLocalSwing(Object morph, String bone, float[] swing)
    {
        if (morph == null || bone == null || swing == null || swing.length != 4)
            return false;
        try
        {
            Field poseField = morph.getClass().getField("pose");
            Object pose = poseField.get(morph);
            if (pose == null) return false;
            // The original morph pose is already cloned by the editor.
            Object bones = pose.getClass().getField("bones").get(pose);
            if (!(bones instanceof Map)) return false;
            Map<Object,Object> map = (Map<Object,Object>)bones;
            Object transform = map.get(bone);
            if (transform == null)
            {
                Class<?> clazz = Class.forName(
                        "mchorse.chameleon.metamorph.pose.AnimatedPoseTransform");
                transform = clazz.getConstructor(String.class).newInstance(bone);
                map.put(bone,transform);
            }
            // Compose matrices against the clean keyframed pose. No
            // axis-angle component is treated as an Euler angle.
            float[] original = new float[] {
                get(transform, "rotateX"),
                get(transform, "rotateY"),
                get(transform, "rotateZ")
            };
            float maxAngle = 0.12F;
            float[] limited = new float[] {swing[0], swing[1], swing[2],
                    Math.max(-maxAngle, Math.min(maxAngle, swing[3]))};
            float[] composed = BodyPartPhysicsRotation.composeInBoneSpace(original, limited);
            set(transform, "rotateX", composed[0]);
            set(transform, "rotateY", composed[1]);
            set(transform, "rotateZ", composed[2]);
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static float get(Object target, String field) throws Exception
    {
        return target.getClass().getField(field).getFloat(target);
    }

    private static void set(Object target, String field, float value) throws Exception
    {
        target.getClass().getField(field).setFloat(target, value);
    }

    private static void add(Object transform, String field, float amount)
            throws Exception
    {
        Field f=transform.getClass().getField(field);
        f.setFloat(transform,f.getFloat(transform)+amount);
    }
}
