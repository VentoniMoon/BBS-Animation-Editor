package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Lightweight CAMERA Curve for film-space visual channels. This is NOT an
 * OptiFine shader uniform adaptor; unsupported external shader attributes
 * (for example the legacy sun_rotation channel) return no film correction.
 *
 * Key.x is a scalar and key.tick is relative to the curve clip. Multiple
 * channels are achieved with separate CURVE clips on independent layers.
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
        double value=number(clip,"value",
                "brightness".equals(channel)?0.25D:0D);
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
        double opacity=CameraClipEnvelope.factor(clip,frame);
        return new Sample(channel,clamp(value,-1D,1D)*opacity);
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
