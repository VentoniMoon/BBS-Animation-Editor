package com.example.examplemod;

/**
 * Pure math bridge between world-space chain simulation and Chameleon's
 * bone-local direction. No OpenGL state, no actor assumptions, no key writes.
 *
 * The caller supplies the actual rendered parent basis (including the actor,
 * parent anchor, attachment and all parent bones), not guessed Euler signs.
 * Matrices use OpenGL column-major layout; translation is in m[12..14].
 */
public final class BodyPartPhysicsPoseAdapter
{
    private BodyPartPhysicsPoseAdapter() {}

    public static BodyPartChainSolver.Vec3 position(float[] worldMatrix)
    {
        require(worldMatrix);
        return new BodyPartChainSolver.Vec3(worldMatrix[12], worldMatrix[13], worldMatrix[14]);
    }

    public static BodyPartChainSolver.Vec3 transformPoint(
            float[] worldMatrix, BodyPartChainSolver.Vec3 local)
    {
        require(worldMatrix);
        return new BodyPartChainSolver.Vec3(
                worldMatrix[0]*local.x + worldMatrix[4]*local.y + worldMatrix[8]*local.z + worldMatrix[12],
                worldMatrix[1]*local.x + worldMatrix[5]*local.y + worldMatrix[9]*local.z + worldMatrix[13],
                worldMatrix[2]*local.x + worldMatrix[6]*local.y + worldMatrix[10]*local.z + worldMatrix[14]);
    }

    /** Converts a world direction into the actual parent's local axes.
     * A 3x3 inversion is necessary: attachments may have non-uniform scale.
     */
    public static BodyPartChainSolver.Vec3 toLocalDirection(
            float[] parentWorldMatrix, BodyPartChainSolver.Vec3 worldDirection)
    {
        require(parentWorldMatrix);
        float a=parentWorldMatrix[0], b=parentWorldMatrix[4], c=parentWorldMatrix[8];
        float d=parentWorldMatrix[1], e=parentWorldMatrix[5], f=parentWorldMatrix[9];
        float g=parentWorldMatrix[2], h=parentWorldMatrix[6], i=parentWorldMatrix[10];
        float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (Math.abs(det)<1e-8F || !Float.isFinite(det))
            throw new IllegalArgumentException("Singular parent bone matrix");
        float inv=1F/det;
        return new BodyPartChainSolver.Vec3(
                ((e*i-f*h)*worldDirection.x+(c*h-b*i)*worldDirection.y+(b*f-c*e)*worldDirection.z)*inv,
                ((f*g-d*i)*worldDirection.x+(a*i-c*g)*worldDirection.y+(c*d-a*f)*worldDirection.z)*inv,
                ((d*h-e*g)*worldDirection.x+(b*g-a*h)*worldDirection.y+(a*e-b*d)*worldDirection.z)*inv);
    }

    /**
     * Minimal signed rotation taking original local segment onto solved local
     * segment. Returned as axis-angle (radians), not Euler or a Chameleon pose
     * key. The render adapter must compose this with the existing bone pose.
     */
    public static float[] localSwing(
            BodyPartChainSolver.Vec3 restLocal,
            BodyPartChainSolver.Vec3 solvedLocal)
    {
        float al=restLocal.length(), bl=solvedLocal.length();
        if (al<1e-7F || bl<1e-7F)
            return new float[] {1F,0F,0F,0F};
        float ax=restLocal.x/al, ay=restLocal.y/al, az=restLocal.z/al;
        float bx=solvedLocal.x/bl, by=solvedLocal.y/bl, bz=solvedLocal.z/bl;
        float cx=ay*bz-az*by, cy=az*bx-ax*bz, cz=ax*by-ay*bx;
        float sin=(float)Math.sqrt(cx*cx+cy*cy+cz*cz);
        float dot=Math.max(-1F,Math.min(1F,ax*bx+ay*by+az*bz));
        if (sin<1e-7F)
        {
            if (dot>0F) return new float[] {1F,0F,0F,0F};
            // Stable perpendicular for 180-degree reversal.
            if (Math.abs(ax)<0.9F) {cx=0F;cy=-az;cz=ay;}
            else {cx=-ay;cy=ax;cz=0F;}
            float norm=(float)Math.sqrt(cx*cx+cy*cy+cz*cz);
            return new float[] {cx/norm,cy/norm,cz/norm,(float)Math.PI};
        }
        return new float[] {cx/sin,cy/sin,cz/sin,(float)Math.atan2(sin,dot)};
    }

    private static void require(float[] matrix)
    {
        if (matrix==null || matrix.length!=16)
            throw new IllegalArgumentException("Expected 4x4 column-major matrix");
    }
}
