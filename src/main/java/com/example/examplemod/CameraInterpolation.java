package com.example.examplemod;

import java.util.Locale;

/**
 * Shared numerical easing for CAMERA preview and its inspector graph.
 * The list follows BBS FS Interpolations identifiers. Multi-point geometry
 * (Hermite/Bezier/Spline) additionally needs neighbor control points in the
 * channel evaluator; standalone curves here are an illustrative fallback.
 */
public final class CameraInterpolation
{
    public static final String[] OPTIONS={
        "linear","const","step",
        "sine_in","sine_out","sine_inout",
        "circle_in","circle_out","circle_inout",
        "quad_in","quad_out","quad_inout",
        "cubic_in","cubic_out","cubic_inout",
        "quart_in","quart_out","quart_inout",
        "quint_in","quint_out","quint_inout",
        "exp_in","exp_out","exp_inout",
        "back_in","back_out","back_inout",
        "elastic_in","elastic_out","elastic_inout",
        "bounce_in","bounce_out","bounce_inout",
        "cubic","hermite","bezier","auto","auto_clamped",
        "bspline","step_tick"
    };

    public static String displayName(String type)
    {
        if(type==null||type.isEmpty())return "Linear";
        String[] parts=type.replace('_',' ').split(" ");
        StringBuilder name=new StringBuilder();
        for(String part:parts)
        {
            if(part.isEmpty())continue;
            if(name.length()>0)name.append(' ');
            name.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }
        return name.toString();
    }
    public static boolean isKnown(String type)
    {
        if(type==null)return false;
        String name=type.toLowerCase(Locale.ROOT);
        for(String entry:OPTIONS)if(entry.equals(name))return true;
        return "smooth".equals(name)||"smoothstep".equals(name)||
                "easein".equals(name)||"easeout".equals(name);
    }
    private static double bounceOut(double x)
    {
        if(x<1D/2.75D)return 7.5625D*x*x;
        if(x<2D/2.75D){x-=1.5D/2.75D;return 7.5625D*x*x+0.75D;}
        if(x<2.5D/2.75D){x-=2.25D/2.75D;return 7.5625D*x*x+0.9375D;}
        x-=2.625D/2.75D;return 7.5625D*x*x+0.984375D;
    }
    private static double in(double t,String family)
    {
        if("sine".equals(family))return 1D-Math.cos(t*Math.PI/2D);
        if("circle".equals(family))return 1D-Math.sqrt(Math.max(0D,1D-t*t));
        if("quad".equals(family))return t*t;
        if("cubic".equals(family))return t*t*t;
        if("quart".equals(family))return Math.pow(t,4D);
        if("quint".equals(family))return Math.pow(t,5D);
        if("exp".equals(family))return t==0D?0D:Math.pow(2D,10D*t-10D);
        if("back".equals(family))return 2.70158D*t*t*t-1.70158D*t*t;
        if("elastic".equals(family))
        {
            if(t==0D||t==1D)return t;
            return -Math.pow(2D,10D*t-10D)*
                    Math.sin((t*10D-10.75D)*2D*Math.PI/3D);
        }
        if("bounce".equals(family))return 1D-bounceOut(1D-t);
        return t;
    }
    public static double sample(double t,String raw)
    {
        t=Math.max(0D,Math.min(1D,t));
        if(raw==null)return t;
        String kind=raw.trim().toLowerCase(Locale.ROOT);
        if("const".equals(kind)||"step".equals(kind)
                ||"step_tick".equals(kind))return t<1D?0D:1D;
        if("smooth".equals(kind)||"smoothstep".equals(kind)
                ||"cubic".equals(kind)||"hermite".equals(kind)
                ||"bezier".equals(kind)||"auto".equals(kind)
                ||"auto_clamped".equals(kind)||"bspline".equals(kind))
            return t*t*(3D-2D*t);
        if("easein".equals(kind))return t*t;
        if("easeout".equals(kind))return 1D-(1D-t)*(1D-t);
        if("linear".equals(kind))return t;
        int p=kind.lastIndexOf('_');
        if(p<0)return t;
        String family=kind.substring(0,p),mode=kind.substring(p+1);
        if("in".equals(mode))return in(t,family);
        if("out".equals(mode))return 1D-in(1D-t,family);
        if("inout".equals(mode))
        {
            if(t<0.5D)return in(t*2D,family)/2D;
            return 1D-in(2D-2D*t,family)/2D;
        }
        return t;
    }
    private CameraInterpolation(){}
}
