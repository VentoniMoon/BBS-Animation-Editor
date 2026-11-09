package com.example.examplemod;

/**
 * Emoticons anatomical directions for Jolt ragdoll hinges.
 *
 * Emoticons faces model-local -Z. Jolt's positive hinge angle, with
 * a downward shin, bends toward +Z when its world axis is +X.
 * Choosing an unconditionally signed BOBJ X axis is not sufficient:
 * some bind matrices contain a reflected X axis or authored turns.
 *
 * The input vectors are all in the SAME current model-space frame.
 * No Minecraft, Vecmath or JNI classes are used by this class.
 */
public final class RagdollAnatomy
{
    private RagdollAnatomy(){}

    public static float forwardLocalZ()
    {
        return -1F;
    }

    /**
     * Decide whether to flip the candidate hinge axis read from the
     * real parent BOBJ transform. Knees flex toward the character's
     * BACK, elbows toward FRONT. With the Jolt hinge convention, the
     * desired positive-angle axis is cross(desiredBend, childDirection).
     *
     * If the child direction nearly aligns with desired flexion,
     * keep the parent basis axis: the cross is underdetermined there.
     */
    public static float hingeAxisMultiplier(String role,float[] axis,
            float[] childDirection,float[] actorForward)
    {
        if(role==null||axis==null||childDirection==null||
                actorForward==null||axis.length!=3||
                childDirection.length!=3||actorForward.length!=3)
            throw new IllegalArgumentException("Invalid hinge frame");

        final float direction;
        if("left_shin".equals(role)||"right_shin".equals(role))
            direction=-1F;
        else if("left_forearm".equals(role)||
                "right_forearm".equals(role))
            direction=1F;
        else
            throw new IllegalArgumentException("Not a ragdoll hinge: "+role);

        for(int i=0;i<3;i++)
            if(!Float.isFinite(axis[i])||
                    !Float.isFinite(childDirection[i])||
                    !Float.isFinite(actorForward[i]))
                throw new IllegalArgumentException("Nonfinite hinge vector");

        float dx=direction*actorForward[0];
        float dy=direction*actorForward[1];
        float dz=direction*actorForward[2];
        float x=dy*childDirection[2]-dz*childDirection[1];
        float y=dz*childDirection[0]-dx*childDirection[2];
        float z=dx*childDirection[1]-dy*childDirection[0];
        float length2=x*x+y*y+z*z;
        if(length2<1E-6F)return 1F;
        return x*axis[0]+y*axis[1]+z*axis[2]<0F?-1F:1F;
    }
}
