package com.example.examplemod;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Diagnostics only: draw the actual radius/center/axis of the Jolt
 * kinematic capsule shapes and the real native hair rigid-body capsules.
 *
 * Called AFTER RenderManager rendered the actor, under the actor preview's
 * established CAMERA projection/modelview. The input coordinates are the
 * same world-space transforms fed to Jolt. Never edits the camera,
 * physics scene, actors, pose values or keyframes.
 */
public final class JoltContactWireframe
{
    private static final int SIDES=20;
    private static final int CAP_STEPS=5;

    private JoltContactWireframe() {}

    public static void drawTorsoSegments(float[][] starts,float[][] ends,
            float[] radii)
    {
        if(starts==null||ends==null||radii==null ||
                starts.length!=ends.length||starts.length!=radii.length)
            return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2F);
            for(int i=0;i<starts.length;i++)
            {
                float tint=(float)i/Math.max(1,starts.length-1);
                GL11.glColor4f(0.15F+0.55F*tint,1F-0.18F*tint,
                        0.34F,0.9F);
                drawCapsule(starts[i],ends[i],radii[i]);
            }
        }
        finally
        {
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
    }

    /** Source is from Jolt BodyInterface positions and rotations, not pivots. */
    public static void drawHairSegments(float[][] worldBodyCapsules)
    {
        if(worldBodyCapsules==null)return;
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1.4F);
            GL11.glColor4f(1F,0.18F,0.72F,0.9F);
            for(float[] shape:worldBodyCapsules)
            {
                if(shape==null||shape.length!=7)continue;
                drawCapsule(
                        new float[] {shape[0],shape[1],shape[2]},
                        new float[] {shape[3],shape[4],shape[5]},
                        shape[6]);
            }
        }
        finally
        {
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
    }

    private static void drawCapsule(float[] a,float[] b,float radius)
    {
        if(!valid(a)||!valid(b)||
                !Float.isFinite(radius)||radius<=0F)return;
        double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        double len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!(len>=0.005D && len<5D))return;
        double ax=dx/len,ay=dy/len,az=dz/len;
        double refx=Math.abs(ay)<0.85D?0D:1D;
        double refy=Math.abs(ay)<0.85D?1D:0D;
        // U = A cross reference; V = A cross U.
        double ux=ay*0D-az*refy,uy=az*refx-ax*0D,
                uz=ax*refy-ay*refx;
        double ul=Math.sqrt(ux*ux+uy*uy+uz*uz);
        if(!(ul>1E-8D))return;
        ux/=ul;uy/=ul;uz/=ul;
        double vx=ay*uz-az*uy,vy=az*ux-ax*uz,
                vz=ax*uy-ay*ux;
        double mx=(a[0]+b[0])*0.5D,
                my=(a[1]+b[1])*0.5D,
                mz=(a[2]+b[2])*0.5D;
        // Jolt CapsuleShape's halfCylinderExtent uses the same formula.
        double halfCylinder=Math.max(0.005D,len*0.5D-radius);
        GL11.glBegin(GL11.GL_LINES);
        for(int face=0;face<2;face++)
        {
            double sign=face==0?-1D:1D;
            for(int step=0;step<=CAP_STEPS;step++)
            {
                double beta=step*(Math.PI/2D)/CAP_STEPS;
                double radial=radius*Math.cos(beta);
                double along=sign*(halfCylinder+radius*Math.sin(beta));
                double nextBeta=(step+1D)*(Math.PI/2D)/CAP_STEPS;
                for(int j=0;j<SIDES;j++)
                {
                    double alpha=2D*Math.PI*j/SIDES;
                    double nextAlpha=2D*Math.PI*(j+1)/SIDES;
                    vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                            along,radial,alpha);
                    vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                            along,radial,nextAlpha);
                    if(step<CAP_STEPS)
                    {
                        vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                                along,radial,alpha);
                        vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                                sign*(halfCylinder+radius*Math.sin(nextBeta)),
                                radius*Math.cos(nextBeta),alpha);
                    }
                }
            }
        }
        for(int j=0;j<8;j++)
        {
            double angle=2D*Math.PI*j/8D;
            vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                    -halfCylinder,radius,angle);
            vertex(mx,my,mz,ax,ay,az,ux,uy,uz,vx,vy,vz,
                    halfCylinder,radius,angle);
        }
        GL11.glEnd();
    }

    private static void vertex(double mx,double my,double mz,
            double ax,double ay,double az,
            double ux,double uy,double uz,
            double vx,double vy,double vz,
            double along,double radial,double angle)
    {
        double c=radial*Math.cos(angle),s=radial*Math.sin(angle);
        GL11.glVertex3d(mx+ax*along+ux*c+vx*s,
                my+ay*along+uy*c+vy*s,
                mz+az*along+uz*c+vz*s);
    }

    private static boolean valid(float[] v)
    {
        if(v==null||v.length!=3)return false;
        for(float x:v)if(!Float.isFinite(x))return false;
        return true;
    }
}
