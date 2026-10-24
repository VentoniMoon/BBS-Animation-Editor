package com.example.examplemod;

import java.util.Locale;

/**
 * Image overlay placement in film coordinates (no Minecraft/OpenGL calls).
 *
 * Width and height supplied by a clip define a BOUNDING BOX relative to
 * the film, not the dimensions of the source image. By default an IMAGE
 * is fitted inside that box without distorting its pixel aspect ratio.
 * The same math is used for the GUI preview and output-resolution MP4.
 */
public final class CameraImagePlacement
{
    public static final class Rect
    {
        public final double left,top,right,bottom,u0,v0,u1,v1;
        Rect(double left,double top,double right,double bottom,
                double u0,double v0,double u1,double v1)
        {
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            this.u0=u0;this.v0=v0;this.u1=u1;this.v1=v1;
        }
        public double width(){return right-left;}
        public double height(){return bottom-top;}
    }

    public static Rect calculate(double left,double top,
            double right,double bottom,int sourceWidth,int sourceHeight,
            String mode,boolean flipX,boolean flipY)
    {
        if(!Double.isFinite(left)||!Double.isFinite(top)||
                !Double.isFinite(right)||!Double.isFinite(bottom)||
                !(right>left)||!(bottom>top))
            throw new IllegalArgumentException("Invalid image clip bounds");
        double u0=0D,u1=1D;
        // DynamicTexture follows the pre-existing CAMERA texture upload
        // convention. Keep the base UV orientation intact; mirroring is
        // explicitly controlled per IMAGE clip, never inferred from FPS.
        double v0=1D,v1=0D;
        String fit=mode==null?"contain":mode.trim().toLowerCase(Locale.ROOT);
        if(!fit.equals("stretch")&&sourceWidth>0&&sourceHeight>0)
        {
            double bw=right-left,bh=bottom-top;
            double sourceAspect=(double)sourceWidth/(double)sourceHeight;
            double boxAspect=bw/bh;
            if(fit.equals("cover"))
            {
                // Crop UV within the placement box. Do NOT expand the quad
                // outside the intended overlay region.
                if(sourceAspect>boxAspect)
                {
                    double fraction=boxAspect/sourceAspect;
                    u0=(1D-fraction)/2D;
                    u1=1D-u0;
                }
                else
                {
                    double fraction=sourceAspect/boxAspect;
                    // V is reversed for this existing compositor upload path.
                    v0=(1D+fraction)/2D;
                    v1=1D-v0;
                }
            }
            else // "contain" by default, including older saved IMAGE clips.
            {
                double targetWidth=bw,targetHeight=bh;
                if(sourceAspect>boxAspect)targetHeight=bw/sourceAspect;
                else targetWidth=bh*sourceAspect;
                double cx=(left+right)/2D,cy=(top+bottom)/2D;
                left=cx-targetWidth/2D;right=cx+targetWidth/2D;
                top=cy-targetHeight/2D;bottom=cy+targetHeight/2D;
            }
        }
        if(flipX)
        {
            double temp=u0;u0=u1;u1=temp;
        }
        if(flipY)
        {
            double temp=v0;v0=v1;v1=temp;
        }
        return new Rect(left,top,right,bottom,u0,v0,u1,v1);
    }

    private CameraImagePlacement(){}
}
