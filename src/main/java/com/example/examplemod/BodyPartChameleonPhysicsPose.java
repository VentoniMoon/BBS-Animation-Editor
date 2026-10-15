package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Preview-only application of a small physical swing to Chameleon pose bones.
 * Chameleon AnimatedPoseTransform stores Euler radians. This first adapter
 * applies the local axis-angle's rotation vector as an additive Euler offset;
 * it is deliberately limited to small swings and may need exact quaternion
 * composition once the visible test establishes the renderer's rotation order.
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
            float maxAngle = 0.35F;
            float angle = Math.max(-maxAngle, Math.min(maxAngle, swing[3]));
            add(transform,"rotateX",swing[0]*angle);
            add(transform,"rotateY",swing[1]*angle);
            add(transform,"rotateZ",swing[2]*angle);
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static void add(Object transform, String field, float amount)
            throws Exception
    {
        Field f=transform.getClass().getField(field);
        f.setFloat(transform,f.getFloat(transform)+amount);
    }
}
