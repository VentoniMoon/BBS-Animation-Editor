package com.example.examplemod;

/**
 * Oblique OpenGL near-plane projection for shaderpack planar mirrors.
 *
 * The old GL_CLIP_PLANE0 only clips programmable GLSL geometry if its
 * vertex shader writes gl_ClipVertex / gl_ClipDistance. Most OptiFine
 * shaderpacks do not, so the BSL path drew the solid wall BEHIND the
 * mirror. Instead, put the existing mirror front half-space into the
 * projection's hardware near clipping plane. Normal clip-space clipping
 * then applies to every vertex shader that writes gl_Position.
 *
 * Input/output are column-major 4x4 OpenGL projection matrices, as used
 * by glGetFloat(GL_PROJECTION_MATRIX) and glLoadMatrix.
 *
 * Reference: standard oblique view-frustum near-plane replacement:
 * q = inverse(P) * (sign(A), sign(B), 1, 1)
 * P[2],P[6],P[10],P[14] = plane * (2 / dot(plane,q))
 * with P[10] offset by +1 for OpenGL clip-space z in [-w,+w].
 */
public final class CameraMirrorObliqueProjection
{
    private CameraMirrorObliqueProjection(){}

    public static float[] clip(float[] p,double[] plane)
    {
        if(p==null||p.length!=16||plane==null||plane.length!=4)
            throw new IllegalArgumentException("Invalid clip projection");
        for(float value:p)
            if(!Float.isFinite(value))
                throw new IllegalArgumentException("Nonfinite projection");
        for(double value:plane)
            if(!Double.isFinite(value))
                throw new IllegalArgumentException("Nonfinite clip plane");
        double norm=Math.sqrt(plane[0]*plane[0]+
                plane[1]*plane[1]+plane[2]*plane[2]);
        if(norm<1E-8||Math.abs(p[0])<1E-8||
                Math.abs(p[5])<1E-8||Math.abs(p[14])<1E-8)
            throw new IllegalArgumentException(
                    "Mirror oblique clip requires perspective projection");
        // Mirror front space is positive. For a reflected camera the eye
        // starts behind this plane (plane.w<0); keep only world in front
        // of the mirror, i.e. across the new near clipping boundary.
        if(plane[3]>=-1E-5D)
            throw new IllegalArgumentException(
                    "Reflected eye not behind mirror clip plane");
        double ax=plane[0]/norm,ay=plane[1]/norm;
        double az=plane[2]/norm,aw=plane[3]/norm;
        double sx=ax>=0D?1D:-1D;
        double sy=ay>=0D?1D:-1D;
        // Perspective P is column-major. This is its inverse applied to
        // opposite clip-space corner (sx, sy, 1, 1).
        double qx=(sx+p[8])/p[0];
        double qy=(sy+p[9])/p[5];
        double qz=-1D;
        double qw=(1D+p[10])/p[14];
        double dot=ax*qx+ay*qy+az*qz+aw*qw;
        if(!Double.isFinite(dot)||dot<=1E-6D)
            throw new IllegalArgumentException(
                    "Mirror plane cannot form forward-facing near clip");
        double scale=2D/dot;
        float[] out=p.clone();
        out[2]=(float)(ax*scale);
        out[6]=(float)(ay*scale);
        out[10]=(float)(az*scale+1D);
        out[14]=(float)(aw*scale);
        for(float value:out)
            if(!Float.isFinite(value))
                throw new IllegalArgumentException(
                        "Nonfinite oblique clipping result");
        return out;
    }

    /**
     * Compatibility mode for BSL/OptiFine fragment shaders whose ToNDC()
     * reconstructs view positions from only four diagonal inverse
     * projection terms. Such shaders CANNOT recover world coordinates
     * under an oblique projection because inverseProjection.w depends on
     * screen X and Y. That makes shadow sampling camera-dependent.
     *
     * Keep a conventional perspective projection, moving its symmetric
     * near plane close to the mirror intersection on the optical axis.
     * This is an approximation of the mirror's exact clipping plane, but
     * it keeps BSL shadow/fog/SSAO projection maths internally coherent.
     * Off-axis fragments may need a future dedicated shader clip pass.
     */
    public static float[] bslCompatible(float[] original,double[] plane)
    {
        if(original==null||original.length!=16||
                plane==null||plane.length!=4)
            throw new IllegalArgumentException("Invalid perspective/plane");
        // Eye space is looking down -Z; plane dot(0,0,-t,1)=0.
        double z=plane[2];
        double t=plane[3]/z;
        double near=original[14]/(original[10]-1D);
        double far=original[14]/(original[10]+1D);
        if(!Double.isFinite(t)||!Double.isFinite(near)||
                !Double.isFinite(far)||t<=0D||
                !(near>0D)||!(far>near))
            throw new IllegalArgumentException(
                    "No forward mirror plane for BSL-compatible projection");
        // Pull the near plane just behind the real mirror plane so the
        // mirror surface is not accidentally discarded by rounding.
        double candidate=Math.max(near,Math.min(t*0.98D,far*0.75D));
        float[] out=original.clone();
        out[10]=(float)(-(far+candidate)/(far-candidate));
        out[14]=(float)(-2D*far*candidate/(far-candidate));
        return out;
    }

    public static float nearPlane(float[] perspective)
    {
        return perspective[14]/(perspective[10]-1F);
    }

    /**
     * Homogeneous near-plane condition z_clip+w_clip >= 0. Used in
     * pure-Java regression tests to ensure the wall behind a mirror is
     * rejected while geometry on the reflected side stays visible.
     */
    static double nearDistance(float[] p,double x,double y,double z)
    {
        double clipZ=p[2]*x+p[6]*y+p[10]*z+p[14];
        double clipW=p[3]*x+p[7]*y+p[11]*z+p[15];
        return clipZ+clipW;
    }
}
