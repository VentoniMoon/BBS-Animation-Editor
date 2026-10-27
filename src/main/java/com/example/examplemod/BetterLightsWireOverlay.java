package com.example.examplemod;

import java.lang.reflect.Method;
import java.util.Locale;
import mchorse.mclib.config.values.GenericBaseValue;
import mchorse.mclib.utils.ValueSerializer;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.opengl.GL11;

/**
 * World-anchored BetterLights source/beam guides drawn AFTER the preview
 * framebuffer has been composited. They are GUI guides, not world entities,
 * LightCasters, gizmo bones, or footage; exported frames remain clean.
 *
 * Distance and cone angles come from the installed Blockbuster/BetterLights
 * native ValueSerializer rather than invented editor light parameters.
 */
public final class BetterLightsWireOverlay
{
    private static String sampledTag;
    private static double distance=Double.NaN;
    private static double outer=Double.NaN;
    private static double inner=Double.NaN;

    private BetterLightsWireOverlay(){}

    private static void sample(NBTTagCompound tag)
    {
        if(tag==null)return;
        String signature=tag.toString();
        if(signature.equals(sampledTag))return;
        sampledTag=signature;
        distance=outer=inner=Double.NaN;
        try
        {
            AbstractMorph morph=MorphManager.INSTANCE.morphFromNBT(tag.copy());
            if(morph==null)return;
            Method getter=morph.getClass().getMethod("getValueManager");
            Object manager=getter.invoke(morph);
            if(!(manager instanceof ValueSerializer))return;
            for(GenericBaseValue<?> value:((ValueSerializer)manager).getValues())
            {
                if(value==null||!(value.getValue() instanceof Number))continue;
                String name=value.getPath().replace(" ","")
                        .replace("_","").toLowerCase(Locale.ROOT);
                double number=((Number)value.getValue()).doubleValue();
                if(name.equals("distance"))distance=number;
                else if(name.endsWith("outerangle"))outer=number;
                else if(name.endsWith("innerangle"))inner=number;
            }
        }
        catch(ReflectiveOperationException ignored){}
        catch(RuntimeException ignored){}
        catch(LinkageError ignored){}
    }

    /** Draw a clipped GUI-space segment without touching the world FBO. */
    public static void line(double x1,double y1,double x2,double y2,
            int argb,int left,int top,int width,int height)
    {
        if(!Double.isFinite(x1)||!Double.isFinite(y1)||
                !Double.isFinite(x2)||!Double.isFinite(y2))return;
        // Clip to the film rectangle; never paint onto neighbouring panels.
        double minX=left+1D,maxX=left+width-1D;
        double minY=top+1D,maxY=top+height-1D;
        if(maxX<=minX||maxY<=minY)return;
        double vx=x2-x1,vy=y2-y1;
        double[] p={-vx,vx,-vy,vy};
        double[] q={x1-minX,maxX-x1,y1-minY,maxY-y1};
        double a=0D,b=1D;
        for(int i=0;i<4;i++)
        {
            if(Math.abs(p[i])<1E-9)
            {
                if(q[i]<0D)return;
                continue;
            }
            double t=q[i]/p[i];
            if(p[i]<0D)a=Math.max(a,t);
            else b=Math.min(b,t);
            if(a>b)return;
        }
        double sx=x1+a*vx,sy=y1+a*vy;
        double ex=x1+b*vx,ey=y1+b*vy;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|
                GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT);
        try
        {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1.5F);
            GL11.glColor4f(((argb>>16)&255)/255F,
                    ((argb>>8)&255)/255F,(argb&255)/255F,
                    ((argb>>>24)&255)/255F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2d(sx,sy);
            GL11.glVertex2d(ex,ey);
            GL11.glEnd();
        }
        finally
        {
            GL11.glPopAttrib();
        }
    }

    private static float[] project(PreviewCameraPose camera,
            double[] pos,int left,int top,int w,int h)
    {
        return PreviewCameraProjection.project(pos[0],pos[1],pos[2],
                camera,left,top,w,h);
    }

    private static void segment(PreviewCameraPose camera,double[] a,double[] b,
            int color,int left,int top,int w,int h)
    {
        float[] p=project(camera,a,left,top,w,h);
        float[] q=project(camera,b,left,top,w,h);
        if(p!=null&&q!=null)line(p[0],p[1],q[0],q[1],
                color,left,top,w,h);
    }

    /** World origin and world target, as opposed to camera-relative pixels. */
    public static void draw(PreviewCameraPose camera,NBTTagCompound morph,
            double x,double y,double z,
            double tx,double ty,double tz,
            int left,int top,int width,int height)
    {
        if(camera==null||morph==null||width<=0||height<=0)return;
        sample(morph);
        if(!Double.isFinite(distance)||!Double.isFinite(outer)||
                distance<=0D||outer<=0D)return;
        double dx=tx-x,dy=ty-y,dz=tz-z;
        double norm=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(norm<1E-7D)return;
        dx/=norm;dy/=norm;dz/=norm;
        double[] axis={dx,dy,dz};
        double ux,uy=0D,uz;
        double flat=Math.sqrt(dx*dx+dz*dz);
        if(flat<1E-6D){ux=1D;uz=0D;}
        else{ux=-dz/flat;uz=dx/flat;}
        double[] cross={dy*uz-dz*uy,dz*ux-dx*uz,
                dx*uy-dy*ux};
        double d=Math.min(160D,distance);
        double maxRadius=Math.min(160D,d*Math.tan(
                Math.toRadians(Math.max(0D,Math.min(84D,outer)))));
        double[] origin={x,y,z};
        double[] center={x+dx*d,y+dy*d,z+dz*d};
        segment(camera,origin,center,0x99F64CE5,
                left,top,width,height);
        final int sides=32;
        double[][] ring=new double[sides][3];
        for(int i=0;i<sides;i++)
        {
            double theta=i*Math.PI*2D/sides;
            double u=maxRadius*Math.cos(theta),v=maxRadius*Math.sin(theta);
            ring[i][0]=center[0]+u*ux+v*cross[0];
            ring[i][1]=center[1]+u*uy+v*cross[1];
            ring[i][2]=center[2]+u*uz+v*cross[2];
        }
        for(int i=0;i<sides;i++)
        {
            segment(camera,ring[i],ring[(i+1)%sides],
                    0xDDF64CE5,left,top,width,height);
            if(i%8==0)
                segment(camera,origin,ring[i],0x9DF64CE5,
                        left,top,width,height);
        }
        if(Double.isFinite(inner)&&inner>0D&&inner<outer)
        {
            double radius=Math.min(maxRadius,d*Math.tan(Math.toRadians(
                    Math.min(84D,inner))));
            double[] last=null,first=null;
            for(int i=0;i<sides;i++)
            {
                double theta=i*Math.PI*2D/sides;
                double u=radius*Math.cos(theta),v=radius*Math.sin(theta);
                double[] point={center[0]+u*ux+v*cross[0],
                        center[1]+u*uy+v*cross[1],
                        center[2]+u*uz+v*cross[2]};
                if(last!=null)
                    segment(camera,last,point,0x7CF6A7ED,
                            left,top,width,height);
                else first=point;
                last=point;
            }
            if(last!=null&&first!=null)
                segment(camera,last,first,0x7CF6A7ED,
                        left,top,width,height);
        }
    }
}
