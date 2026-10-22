package com.example.examplemod;

/**
 * Shared BBS FS-style clip envelope. The same weight is used for camera
 * modifiers, film overlays and soundtrack, so crossfades align in time.
 *
 * BBS FS defaults to disabled envelope (weight 1); modes 0,1,2 represent
 * fade-in/out, fade-in only and fade-out only respectively.
 */
public final class CameraClipEnvelope
{
    public static double factor(CameraClipDocument.Clip clip,int frame)
    {
        if(clip==null||!Boolean.parseBoolean(
                clip.settings.get("envelope.enabled")))return 1D;
        double time=frame-clip.tick;
        double duration=Math.max(1,clip.duration);
        double fadeIn=Math.max(0,number(clip,"envelope.fadeIn",10D));
        double fadeOut=Math.max(0,number(clip,"envelope.fadeOut",10D));
        int mode=(int)number(clip,"envelope.mode",0D);

        double lowOut=mode==2?0D:
                mode==1&&fadeIn<=0D?duration:fadeIn;
        double highIn=mode==1?duration:
                duration-(fadeOut<=0D&&mode==2?duration:fadeOut);
        double incoming=lowOut<=0D?1D:clamp(time/lowOut);
        double outgoing=highIn>=duration?1D:
                clamp((duration-time)/(duration-highIn));
        double factor;
        if(time<lowOut)
            factor=easing(incoming,clip.settings.get("envelope.pre"));
        else if(time>highIn)
            factor=easing(outgoing,clip.settings.get("envelope.post"));
        else factor=1D;
        return clamp(factor);
    }
    private static double easing(double t,String curve)
    {
        t=clamp(t);
        if(curve==null)return t;
        String method=curve.toLowerCase(java.util.Locale.ROOT);
        if("step".equals(method))return t>=1D?1D:0D;
        if("smooth".equals(method)||"cubic".equals(method)||
                "smoothstep".equals(method))return t*t*(3D-2D*t);
        if("easein".equals(method))return t*t;
        if("easeout".equals(method))return 1D-(1D-t)*(1D-t);
        return t;
    }
    private static double number(CameraClipDocument.Clip clip,
            String field,double fallback)
    {
        try
        {
            double n=Double.parseDouble(clip.settings.get(field));
            return Double.isFinite(n)?n:fallback;
        }
        catch(RuntimeException invalid){return fallback;}
    }
    private static double clamp(double n)
    {
        return Math.max(0D,Math.min(1D,n));
    }
    private CameraClipEnvelope(){}
}
