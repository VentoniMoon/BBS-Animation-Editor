package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * CAMERA Curve scalar interpolation, shared by film-space channels and
 * native OptiFine shader options prefixed with "curve.". Existing film
 * channels keep their previous -1..1 delta semantics. Shader channels
 * carry raw numeric pack values (often far beyond that range).
 *
 * Key.x is a scalar and key.tick is relative to the clip. Multiple
 * independent channels may be stacked using CURVE clips on other layers.
 */
public final class CameraCurveSampler
{
    public static final class Sample
    {
        public final String channel;
        public final double value;
        Sample(String channel,double value)
        {this.channel=channel;this.value=value;}
    }
    public static Sample sample(CameraClipDocument.Clip clip,double frame)
    {
        if(clip==null||clip.kind!=CameraClipDocument.Kind.CURVE||
                !clip.enabled||frame<clip.tick||
                frame>=clip.tick+clip.duration)return null;
        String channel=clip.settings.get("channels");
        if(channel==null)return null;
        channel=channel.trim().toLowerCase(java.util.Locale.ROOT);
        if(!"brightness".equals(channel)&&!"red".equals(channel)&&
                !"green".equals(channel)&&!"blue".equals(channel)&&
                !"fade".equals(channel))return null;
        double value=sampleValue(clip,frame,
                "brightness".equals(channel)?0.25D:0D);
        double opacity=CameraClipEnvelope.factor(clip,frame);
        return new Sample(channel,clamp(value,-1D,1D)*opacity);
    }

    /**
     * Samples one native shader-pack option, including its unbounded value.
     * The envelope blends towards the real shader-pack default rather than
     * towards zero, so fading the clip does not corrupt the underlying pack.
     */
    public static Sample sampleShader(CameraClipDocument.Clip clip,
            double frame)
    {
        if(clip==null||clip.kind!=CameraClipDocument.Kind.CURVE||
                !clip.enabled||frame<clip.tick||
                frame>=clip.tick+clip.duration)return null;
        String channel=clip.settings.get("channels");
        if(channel==null||!channel.startsWith("curve.")||
                channel.length()<=6)return null;
        String name=channel.substring(6);
        CameraShaderCurveSource.Option option=
                CameraShaderCurves.options().get(name);
        if(option==null)return null;
        double value=sampleValue(clip,frame,option.fallback);
        double blend=CameraClipEnvelope.factor(clip,frame);
        return new Sample(channel,
                option.fallback+(value-option.fallback)*blend);
    }

    private static double sampleValue(CameraClipDocument.Clip clip,
            double frame,double fallback)
    {
        double value=number(clip,"value",fallback);
        if(!clip.keys.isEmpty())
        {
            java.util.List<CameraClipDocument.Key> keys=
                    new ArrayList<CameraClipDocument.Key>(clip.keys);
            Collections.sort(keys,new Comparator<CameraClipDocument.Key>(){
                @Override public int compare(CameraClipDocument.Key a,
                        CameraClipDocument.Key b)
                {return Integer.compare(a.tick,b.tick);}
            });
            double time=frame-clip.tick;
            CameraClipDocument.Key first=keys.get(0);
            CameraClipDocument.Key last=keys.get(keys.size()-1);
            if(time<=first.tick)value=first.x;
            else if(time>=last.tick)value=last.x;
            else
            {
                for(int i=1;i<keys.size();i++)
                {
                    CameraClipDocument.Key a=keys.get(i-1),b=keys.get(i);
                    if(time>b.tick)continue;
                    double t=(time-a.tick)/(double)Math.max(1,b.tick-a.tick);
                    t=CameraInterpolation.sample(t,
                            a.interpolation==null||a.interpolation.isEmpty()
                                    ?clip.settings.get("interp"):a.interpolation);
                    value=a.x+(b.x-a.x)*t;
                    break;
                }
            }
        }
        return value;
    }
    private static double number(CameraClipDocument.Clip clip,String name,double or)
    {
        try
        {
            double v=Double.parseDouble(clip.settings.get(name));
            return Double.isFinite(v)?v:or;
        }
        catch(RuntimeException invalid){return or;}
    }
    private static double clamp(double v,double a,double b)
    {return Math.max(a,Math.min(b,v));}
    private CameraCurveSampler(){}
}
