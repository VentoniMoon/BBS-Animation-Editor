package com.example.examplemod;

import java.util.Locale;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;

/** Select a collision torso independently of the attached Body Part bone. */
public final class BodyPartPhysicsTorsoLocator
{
    private BodyPartPhysicsTorsoLocator() {}

    public static String findUpperTorsoName(Object morph)
    {
        if (!(morph instanceof AnimatedMorph)) return null;
        String[] preferred = {
                "upper_body", "up_body", "high_body", "body_upper",
                "chest", "torso", "body"
        };
        java.util.List<BOBJBone> bones =
                EmoticonsModelAccess.getBones((AnimatedMorph)morph);
        for (String candidate : preferred)
            for (BOBJBone bone : bones)
                if (bone != null && bone.name != null
                        && candidate.equals(bone.name.toLowerCase(Locale.ROOT)))
                    return bone.name;
        return null;
    }
}
