package com.example.examplemod;

import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Passive snapshot of an Emoticons bone's last renderer-evaluated matrix.
 * This NEVER invokes a second render. In prepareModels() the matrix can
 * belong to the previous render frame; it must not yet drive physics.
 */
public final class BodyPartEmoticonsAttachmentProbe
{
    private BodyPartEmoticonsAttachmentProbe() {}

    public static float[] lastRenderedBoneMatrix(Object morph, String boneName)
    {
        if (!(morph instanceof AnimatedMorph) || boneName == null)
            return null;

        BOBJBone bone = EmoticonsModelAccess.findBone((AnimatedMorph) morph, boneName);
        if (bone == null || bone.mat == null) return null;

        javax.vecmath.Matrix4f m = bone.mat;
        float[] result = new float[] {
            m.m00, m.m10, m.m20, m.m30,
            m.m01, m.m11, m.m21, m.m31,
            m.m02, m.m12, m.m22, m.m32,
            m.m03, m.m13, m.m23, m.m33
        };
        try
        {
            return BodyPartPhysicsMatrixSpace.snapshot(result);
        }
        catch (IllegalArgumentException ignored)
        {
            return null;
        }
    }
}
