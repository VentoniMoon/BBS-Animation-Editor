package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/**
 * Independent BBS-style camera key channels stored in one compact timeline
 * marker. Unlike a single pose interpolation, missing channels do not reset
 * the result provided by the lower camera layers.
 */
public final class CameraClipChannels
{
    private static final int CHANNELS=7;
    private static final int POSITION_END=3;
    private static final double EPS=1E-9D;

    public static PreviewCameraPose sample(CameraClipDocument.Clip clip,
            int absoluteFrame,PreviewCameraPose underneath)
    {
        if(clip==null||clip.keys.isEmpty())return underneath;
        final int local=absoluteFrame-clip.tick;
        double[] previous={underneath.x,underneath.y,underneath.z,
                underneath.yaw,underneath.pitch,underneath.roll,underneath.fov};
        boolean additive=clip.kind==CameraClipDocument.Kind.KEYFRAME&&
                Boolean.parseBoolean(clip.settings.get("additive"));
        for(int channel=0;channel<CHANNELS;channel++)
        {
            List<CameraClipDocument.Key> enabled=
                    new ArrayList<CameraClipDocument.Key>();
            for(CameraClipDocument.Key key:clip.keys)
                if((key.mask&(1<<channel))!=0)enabled.add(key);
            if(enabled.isEmpty())continue;
            CameraClipDocument.Key a=enabled.get(0);
            CameraClipDocument.Key b=enabled.get(enabled.size()-1);
            int index=0;
            for(int i=0;i<enabled.size();i++)
            {
                CameraClipDocument.Key key=enabled.get(i);
                if(key.tick<=local){a=key;index=i;}
                if(key.tick>=local){b=key;break;}
            }

            double value;
            if(a==b||Math.abs(b.tick-a.tick)<EPS)
                value=attribute(a,channel);
            else
            {
                double t=Math.max(0D,Math.min(1D,
                        (local-a.tick)/(double)(b.tick-a.tick)));
                double v0=attribute(a,channel),v1=attribute(b,channel);
                boolean angle=channel>=POSITION_END&&channel<=5;
                if(angle)v1=unwrap(v0,v1);
                String method=a.interpolation==null||
                        a.interpolation.isEmpty()
                        ?clip.settings.get(clip.kind==CameraClipDocument.Kind.PATH
                            ?(channel<POSITION_END?"interpPoint":"interpAngle")
                            :"interpolation"):a.interpolation;
                method=method==null?"linear":method.toLowerCase(
                        java.util.Locale.ROOT);
                if("hermite".equals(method)||"spline".equals(method)||
                        "catmull".equals(method))
                {
                    CameraClipDocument.Key before=index>0?enabled.get(index-1):null;
                    CameraClipDocument.Key after=index+2<enabled.size()
                            ?enabled.get(index+2):null;
                    double dv=v1-v0;
                    double slope0=before==null?dv/(b.tick-a.tick):
                            (v1-(angle?unwrap(v0,attribute(before,channel)):
                                attribute(before,channel)))/
                            Math.max(1,b.tick-before.tick);
                    double slope1=after==null?dv/(b.tick-a.tick):
                            ((angle?unwrap(v1,attribute(after,channel)):
                                attribute(after,channel))-v0)/
                            Math.max(1,after.tick-a.tick);
                    double m0=slope0*(b.tick-a.tick);
                    double m1=slope1*(b.tick-a.tick);
                    double t2=t*t,t3=t2*t;
                    value=(2*t3-3*t2+1)*v0+(t3-2*t2+t)*m0+
                            (-2*t3+3*t2)*v1+(t3-t2)*m1;
                }
                else
                {
                    t=easing(t,method);
                    value=v0+(v1-v0)*t;
                }
            }
            previous[channel]=additive?previous[channel]+value:value;
        }
        float fov=(float)Math.max(1D,Math.min(170D,previous[6]));
        return new PreviewCameraPose(previous[0],previous[1],previous[2],
                (float)previous[3],(float)previous[4],(float)previous[5],
                fov,underneath.nearPlane,underneath.farPlane);
    }

    private static double attribute(CameraClipDocument.Key key,int channel)
    {
        switch(channel)
        {
            case 0:return key.x;
            case 1:return key.y;
            case 2:return key.z;
            case 3:return key.yaw;
            case 4:return key.pitch;
            case 5:return key.roll;
            case 6:return key.fov;
            default:throw new IllegalArgumentException("Bad camera channel");
        }
    }
    private static double unwrap(double anchor,double target)
    {
        double change=(target-anchor)%360D;
        if(change>180D)change-=360D;
        if(change< -180D)change+=360D;
        return anchor+change;
    }
    private static double easing(double t,String interpolation)
    {
        if("step".equals(interpolation))return t>=1D?1D:0D;
        if("smooth".equals(interpolation)||"cubic".equals(interpolation)
                ||"smoothstep".equals(interpolation))return t*t*(3D-2D*t);
        if("easein".equals(interpolation))return t*t;
        if("easeout".equals(interpolation))return 1D-(1D-t)*(1D-t);
        return t;
    }

    private CameraClipChannels(){}
}
