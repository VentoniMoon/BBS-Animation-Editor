package com.example.examplemod;

import net.minecraft.client.gui.FontRenderer;

/**
 * Shared geometry for Image, Video and Subtitle overlays. Inverse-transform
 * mouse coordinates exactly as the compositor translates, rotates and scales
 * each overlay. Coordinates are relative to the FILM frame, not editor canvas.
 */
public final class CameraMediaPlacement
{
    private CameraMediaPlacement() {}

    public static boolean supports(CameraClipDocument.Clip c)
    {
        return c!=null&&(c.kind==CameraClipDocument.Kind.IMAGE||
                c.kind==CameraClipDocument.Kind.VIDEO||
                c.kind==CameraClipDocument.Kind.SUBTITLE);
    }

    public static boolean canDrag(CameraClipDocument.Clip c)
    {
        return supports(c)&&!((c.kind==CameraClipDocument.Kind.IMAGE||
                c.kind==CameraClipDocument.Kind.VIDEO)&&
                Boolean.parseBoolean(c.settings.get("fullscreen")));
    }

    public static double number(CameraClipDocument.Clip c,String key,
            double fallback)
    {
        if(c==null)return fallback;
        try
        {
            double value=Double.parseDouble(c.settings.get(key));
            return Double.isFinite(value)?value:fallback;
        }
        catch(RuntimeException invalid){return fallback;}
    }

    public static double x(CameraClipDocument.Clip c)
    {
        return number(c,"x",0.5D);
    }

    public static double y(CameraClipDocument.Clip c)
    {
        return number(c,"y",c!=null&&
                c.kind==CameraClipDocument.Kind.SUBTITLE?0.88D:0.5D);
    }

    private static double[] bounds(CameraClipDocument.Clip clip,
            FontRenderer font,int frameW,int frameH)
    {
        if(clip.kind==CameraClipDocument.Kind.IMAGE||
                clip.kind==CameraClipDocument.Kind.VIDEO)
            return new double[]{
                    Math.abs(number(clip,"width",0.5D))*frameW/2D,
                    Math.abs(number(clip,"height",0.5D))*frameH/2D,
                    0D};
        String raw=clip.settings.get("text");
        if(raw==null||raw.isEmpty()||font==null)return null;
        String[] lines=raw.replace("\\n","\n").split("\n",-1);
        int maxWidth=(int)number(clip,"maxWidth",0D);
        if(maxWidth<=0)maxWidth=frameW-12;
        maxWidth=Math.max(20,Math.min(frameW,maxWidth));
        int width=0;
        for(int i=0;i<Math.min(32,lines.length);i++)
            width=Math.max(width,font.getStringWidth(
                    font.trimStringToWidth(lines[i],maxWidth)));
        int lineHeight=(int)Math.max(8D,
                Math.min(64D,number(clip,"lineHeight",12D)));
        int height=Math.min(32,lines.length)*lineHeight;
        return new double[]{Math.max(6D,width/2D+3D),
                height/2D+4D,height/2D};
    }

    public static boolean contains(CameraClipDocument.Clip clip,
            FontRenderer font,int frameX,int frameY,int frameW,int frameH,
            int mouseX,int mouseY)
    {
        if(!canDrag(clip)||frameW<1||frameH<1||
                mouseX<frameX||mouseX>=frameX+frameW||
                mouseY<frameY||mouseY>=frameY+frameH)return false;
        double[] shape=bounds(clip,font,frameW,frameH);
        if(shape==null)return false;
        double centerX=frameX+frameW*x(clip);
        double centerY=frameY+frameH*y(clip);
        double dx=mouseX-centerX,dy=mouseY-centerY;
        double rad=Math.toRadians(number(clip,"rotation",0D));
        double cosine=Math.cos(rad),sine=Math.sin(rad);
        double sx=number(clip,"scaleX",1D),sy=number(clip,"scaleY",1D);
        if(Math.abs(sx)<1E-4D||Math.abs(sy)<1E-4D)return false;
        double px=(cosine*dx+sine*dy)/sx;
        double py=(-sine*dx+cosine*dy)/sy;
        return Math.abs(px)<=shape[0]&&
                py>=shape[2]-shape[1]&&py<=shape[2]+shape[1];
    }

    /** Editor-only axis-aligned rectangle around transformed overlay. */
    public static int[] outline(CameraClipDocument.Clip clip,
            FontRenderer font,int fx,int fy,int fw,int fh)
    {
        if(!canDrag(clip))return null;
        double[] shape=bounds(clip,font,fw,fh);
        if(shape==null)return null;
        double sx=number(clip,"scaleX",1D),sy=number(clip,"scaleY",1D);
        double rad=Math.toRadians(number(clip,"rotation",0D));
        double c=Math.abs(Math.cos(rad)),s=Math.abs(Math.sin(rad));
        double halfW=c*Math.abs(sx)*shape[0]+s*Math.abs(sy)*shape[1];
        double halfH=s*Math.abs(sx)*shape[0]+c*Math.abs(sy)*shape[1];
        double cx=fx+fw*x(clip)-Math.sin(rad)*sy*shape[2];
        double cy=fy+fh*y(clip)+Math.cos(rad)*sy*shape[2];
        return new int[]{(int)Math.floor(cx-halfW),(int)Math.floor(cy-halfH),
                (int)Math.ceil(cx+halfW),(int)Math.ceil(cy+halfH)};
    }
}
