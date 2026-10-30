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
