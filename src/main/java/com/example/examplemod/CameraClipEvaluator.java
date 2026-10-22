package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Time-based CAMERA clip stack. Lower layers are evaluated first, exactly as
 * BBS FS's Clips.getClips(tick) ordering. This class does not mutate the
 * editor's navigation camera and has no OpenGL dependencies.
 *
 * Clips that need real BBS FS systems (spline Form geometry, audio decoder,
 * arbitrary expression engine, image/video compositor) are intentionally not
 * pretended to be complete by this pose evaluator.
 */
public final class CameraClipEvaluator
{
    private static final double EPS=1E-6D;

    public static PreviewCameraPose evaluate(List<CameraClipDocument.Clip> clips,
            int frame,PreviewCameraPose editorPose,
            CameraClipDocument.TargetResolver resolver)
    {
        return eval(clips,frame,editorPose,resolver,Integer.MAX_VALUE,0);
    }

    private static PreviewCameraPose eval(List<CameraClipDocument.Clip> clips,
            int frame,PreviewCameraPose fallback,
            CameraClipDocument.TargetResolver resolver,int maxLayer,int depth)
    {
        if(depth>3)return fallback;
        List<CameraClipDocument.Clip> active=new ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:clips)
            if(clip.layer<maxLayer&&clip.enabled&&clip.contains(frame))
                active.add(clip);
        Collections.sort(active,new Comparator<CameraClipDocument.Clip>(){
            @Override public int compare(CameraClipDocument.Clip a,
                    CameraClipDocument.Clip b)
            {return Integer.compare(a.layer,b.layer);}
        });
        PreviewCameraPose result=fallback;
        for(CameraClipDocument.Clip clip:active)
            result=apply(clips,clip,frame,result,fallback,resolver,depth);
        return result;
    }

    private static PreviewCameraPose apply(List<CameraClipDocument.Clip> clips,
            CameraClipDocument.Clip clip,int frame,PreviewCameraPose current,
            PreviewCameraPose fallback,CameraClipDocument.TargetResolver resolver,int depth)
    {
        switch(clip.kind)
        {
            case IDLE:
            case DOLLY:
            {
                if(clip.keys.isEmpty())return current;
                PreviewCameraPose source=pose(clip.keys.get(0),current);
                if(clip.kind==CameraClipDocument.Kind.IDLE)return source;
                double frac=(double)(frame-clip.tick)/Math.max(1,clip.duration);
                frac=easing(frac,clip.settings.get("interp"));
                double[] direction=lookVector(
                        number(clip,"yaw",source.yaw),number(clip,"pitch",source.pitch));
                double travel=number(clip,"distance",0.1D)*frac;
                return withPosition(source,source.x+direction[0]*travel,
                        source.y+direction[1]*travel,source.z+direction[2]*travel);
            }
            case KEYFRAME:
            case PATH:
            {
                if(clip.keys.isEmpty())return current;
                CameraClipDocument.Key previous=clip.keys.get(0);
                CameraClipDocument.Key following=clip.keys.get(clip.keys.size()-1);
                int local=frame-clip.tick;
                for(CameraClipDocument.Key key:clip.keys)
                {
                    if(key.tick<=local)previous=key;
                    if(key.tick>=local){following=key;break;}
                }
                double t=previous==following?0:(double)(local-previous.tick)/
                        Math.max(1,following.tick-previous.tick);
                t=easing(t,clip.settings.get(
                        clip.kind==CameraClipDocument.Kind.PATH?"interpPoint":"interpolation"));
                PreviewCameraPose interpolated=PreviewCameraPose.interpolate(
                        pose(previous,current),pose(following,current),t);
                if(clip.kind==CameraClipDocument.Kind.KEYFRAME &&
                        Boolean.parseBoolean(clip.settings.get("additive")))
                    return new PreviewCameraPose(current.x+interpolated.x,
                        current.y+interpolated.y,current.z+interpolated.z,
                        current.yaw+interpolated.yaw,current.pitch+interpolated.pitch,
                        current.roll+interpolated.roll,
                        clampFov(current.fov+interpolated.fov),
                        current.nearPlane,current.farPlane);
                return interpolated;
            }
            case TRANSLATE:
            {
                double[] delta=point(clip,"translate");
                int bits=(int)number(clip,"active",7);
                return withPosition(current,
                        current.x+(((bits&1)!=0)?delta[0]:0D),
                        current.y+(((bits&2)!=0)?delta[1]:0D),
                        current.z+(((bits&4)!=0)?delta[2]:0D));
            }
            case ANGLE:
            {
                double[] delta=point(clip,"angle");
                int bits=(int)number(clip,"active",120);
                return new PreviewCameraPose(current.x,current.y,current.z,
                        current.yaw+((bits&8)!=0?(float)delta[0]:0F),
                        current.pitch+((bits&16)!=0?(float)delta[1]:0F),
                        current.roll+((bits&32)!=0?(float)delta[2]:0F),
                        clampFov(current.fov+((bits&64)!=0?
                            (float)number(clip,"fov",0D):0F)),
                        current.nearPlane,current.farPlane);
            }
            case SHAKE:
            {
                double intensity=number(clip,"shakeAmount",0D);
                double period=Math.max(0.05D,Math.abs(number(clip,"shake",1D)));
                double x=frame/period;
                boolean noise=Boolean.parseBoolean(clip.settings.get("noise"));
                int active=(int)number(clip,"active",24);
                double[] delta=new double[7];
                for(int i=0;i<7;i++)
                    if((active&(1<<i))!=0)
                        delta[i]=(noise?noise(x/(Math.PI/2),i):
                            (i%2==0?Math.sin(x):Math.cos(x)))*intensity;
                return new PreviewCameraPose(current.x+delta[0],
                    current.y+delta[1],current.z+delta[2],
                    current.yaw+(float)delta[3],current.pitch+(float)delta[4],
                    current.roll+(float)delta[5],
                    clampFov((float)(current.fov+delta[6])),
                    current.nearPlane,current.farPlane);
            }
            case LOOK:
            {
                CameraClipDocument.Target target=target(clip,frame,resolver);
                boolean atBlock=Boolean.parseBoolean(clip.settings.get("atBlock"));
                boolean forward=Boolean.parseBoolean(clip.settings.get("forward"));
                double[] point=point(clip,"block");
                if(target==null&&!atBlock&&!forward)return current;
                double dx,dy,dz;
                if(forward)
                {
                    PreviewCameraPose past=eval(clips,Math.max(0,frame-1),
                            fallback,resolver,clip.layer,depth+1);
                    dx=current.x-past.x;dy=current.y-past.y;dz=current.z-past.z;
                }
                else
                {
                    double[] offset=point(clip,"offset");
                    dx=(atBlock?point[0]:target.x)+offset[0]-current.x;
                    dy=(atBlock?point[1]:target.y)+offset[1]-current.y;
                    dz=(atBlock?point[2]:target.z)+offset[2]-current.z;
                }
                if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)<EPS)return current;
                float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                float pitch=(float)Math.toDegrees(Math.atan2(-dy,
                        Math.sqrt(dx*dx+dz*dz)));
                return new PreviewCameraPose(current.x,current.y,current.z,
                        yaw,pitch,current.roll,current.fov,
                        current.nearPlane,current.farPlane);
            }
            case TRACKER:
            {
                CameraClipDocument.Target target=target(clip,frame,resolver);
                if(target==null)return current;
                double[] offset=point(clip,"offset");
                double[] angles=point(clip,"angle");
                boolean look=Boolean.parseBoolean(clip.settings.get("look_at"));
                int active=(int)number(clip,"active",127);
                double x=look?current.x:target.x+offset[0];
                double y=look?current.y:target.y+offset[1];
                double z=look?current.z:target.z+offset[2];
                float yaw=target.yaw+(float)angles[0];
                float pitch=target.pitch+(float)angles[1];
                if(look)
                {
                    double dx=target.x+offset[0]-x;
                    double dy=target.y+offset[1]-y;
                    double dz=target.z+offset[2]-z;
                    if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>EPS)
                    {
                        yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                        pitch=(float)Math.toDegrees(Math.atan2(-dy,
                                Math.sqrt(dx*dx+dz*dz)));
                    }
                }
                return new PreviewCameraPose((active&1)!=0?x:current.x,
                    (active&2)!=0?y:current.y,(active&4)!=0?z:current.z,
                    (active&8)!=0?yaw:current.yaw,
                    (active&16)!=0?pitch:current.pitch,
                    (active&32)!=0?current.roll+(float)angles[2]:current.roll,
                    (active&64)!=0?(float)number(clip,"fov",70D):current.fov,
                    current.nearPlane,current.farPlane);
            }
            case ORBIT:
            {
                CameraClipDocument.Target target=target(clip,frame,resolver);
                boolean atBlock=Boolean.parseBoolean(clip.settings.get("atBlock"));
                if(target==null&&!atBlock)return current;
                double[] center=point(clip,"block");
                double[] offset=point(clip,"offset");
                double tx=(target==null?center[0]:target.x)+offset[0];
                double ty=(target==null?center[1]:target.y)+offset[1];
                double tz=(target==null?center[2]:target.z)+offset[2];
                double yaw=number(clip,"yaw",0D);
                double pitch=number(clip,"pitch",0D);
                if(target!=null&&Boolean.parseBoolean(clip.settings.get("copy")))
                {
                    yaw+=target.yaw;pitch+=target.pitch;
                }
                double[] direction=lookVector(yaw,pitch);
                double distance=number(clip,"distance",0D);
                double ex=tx-direction[0]*distance;
                double ey=ty-direction[1]*distance;
                double ez=tz-direction[2]*distance;
                double dx=tx-ex,dy=ty-ey,dz=tz-ez;
                return new PreviewCameraPose(ex,ey,ez,
                    (float)Math.toDegrees(Math.atan2(-dx,dz)),
                    (float)Math.toDegrees(Math.atan2(-dy,Math.sqrt(dx*dx+dz*dz))),
                    current.roll,current.fov,current.nearPlane,current.farPlane);
            }
            case DOLLY_ZOOM:
            {
                PreviewCameraPose start=eval(clips,clip.tick,
                        fallback,resolver,clip.layer,depth+1);
                double focus=-number(clip,"focus",0D);
                double a=Math.tan(Math.toRadians(Math.max(0.01F,start.fov))/2D);
                double b=Math.tan(Math.toRadians(Math.max(0.01F,current.fov))/2D);
                if(Math.abs(b)<EPS)return current;
                double distance=focus-focus*a/b;
                double[] look=lookVector(current.yaw,current.pitch);
                return withPosition(current,current.x+distance*look[0],
                    current.y+distance*look[1],current.z+distance*look[2]);
            }
            case DRAG:
            {
                if(clip.layer==0)return current;
                boolean deterministic=Boolean.parseBoolean(clip.settings.get("deterministic"));
                if(!deterministic)return current; // no mutable physics during seeking
                int active=(int)number(clip,"active",127);
                double factor=clamp(number(clip,"factor",0.5),0D,1D);
                int rate=(int)clamp(number(clip,"rate",60),1,300);
                int steps=Math.min(512,Math.max(1,(frame-clip.tick)*rate/20));
                PreviewCameraPose smoothed=eval(clips,clip.tick,fallback,
                        resolver,clip.layer,depth+1);
                for(int i=1;i<=steps;i++)
                {
                    int at=clip.tick+(int)((double)i*20D/rate);
                    PreviewCameraPose desired=eval(clips,at,fallback,resolver,
                            clip.layer,depth+1);
                    smoothed=PreviewCameraPose.interpolate(smoothed,desired,factor);
                }
                return new PreviewCameraPose(
                    (active&1)!=0?smoothed.x:current.x,
                    (active&2)!=0?smoothed.y:current.y,
                    (active&4)!=0?smoothed.z:current.z,
                    (active&8)!=0?smoothed.yaw:current.yaw,
                    (active&16)!=0?smoothed.pitch:current.pitch,
                    (active&32)!=0?smoothed.roll:current.roll,
                    (active&64)!=0?smoothed.fov:current.fov,
                    current.nearPlane,current.farPlane);
            }
            case REMAPPER:
            {
                int from=(int)number(clip,"from",clip.tick);
                int to=(int)number(clip,"to",clip.tick+clip.duration);
                double phase=(double)(frame-clip.tick)/Math.max(1,clip.duration);
                phase=easing(phase,clip.settings.get("interp"));
                int remapped=(int)Math.round(from+(to-from)*phase);
                return eval(clips,remapped,fallback,resolver,clip.layer,depth+1);
            }
            case SPLINE:
            {
                /*
                 * In the standalone editor a Spline clip can follow the
                 * control points stored with the camera clip itself.
                 * The BBS FS Form-spine attachment will need a separate
                 * world-to-bone transform adapter for exact parity.
                 */
                if(clip.keys.isEmpty())return current;
                CameraClipDocument.Key prev=clip.keys.get(0);
                CameraClipDocument.Key next=clip.keys.get(clip.keys.size()-1);
                double local=(frame-clip.tick)/(double)Math.max(1,clip.duration);
                local=clamp(local,0D,1D);
                double start=number(clip,"start",0D)/100D;
                double end=number(clip,"end",100D)/100D;
                double progress=clamp(start+(end-start)*easing(local,
                        clip.settings.get("interp")),0D,1D);
                int min=prev.tick,max=next.tick;
                double sample=min+(max-min)*progress;
                for(CameraClipDocument.Key key:clip.keys)
                {
                    if(key.tick<=sample)prev=key;
                    if(key.tick>=sample){next=key;break;}
                }
                double t=prev==next?0D:(sample-prev.tick)/
                        Math.max(1,next.tick-prev.tick);
                return PreviewCameraPose.interpolate(
                        pose(prev,current),pose(next,current),t);
            }
            case MATH:
                return CameraMathExpression.apply(clip.settings.get("expression"),
                        current,frame,clip.tick,clip.duration);
            case AUDIO:
            case CURVE:
            case SUBTITLE:
            case IMAGE:
            case VIDEO:
                // These either affect compositing/sound rather than the pose,
                // or need BBS FS splines / expression parser not yet ported.
                return current;
            default:
                return current;
        }
    }
    private static PreviewCameraPose pose(CameraClipDocument.Key k,
            PreviewCameraPose fallback)
    {
        return new PreviewCameraPose(k.x,k.y,k.z,k.yaw,k.pitch,k.roll,
                clampFov(k.fov),fallback.nearPlane,fallback.farPlane);
    }
    private static PreviewCameraPose withPosition(PreviewCameraPose source,
            double x,double y,double z)
    {
        return new PreviewCameraPose(x,y,z,source.yaw,source.pitch,source.roll,
                source.fov,source.nearPlane,source.farPlane);
    }
    private static float clampFov(float fov)
    {
        return (float)clamp(fov,1F,170F);
    }
    private static double clamp(double x,double min,double max)
    {
        return Math.max(min,Math.min(max,x));
    }
    private static double number(CameraClipDocument.Clip clip,String key,double fallback)
    {
        try
        {
            String raw=clip.settings.get(key);
            double v=Double.parseDouble(raw);
            return Double.isFinite(v)?v:fallback;
        }
        catch(RuntimeException invalid){return fallback;}
    }
    private static double[] point(CameraClipDocument.Clip clip,String key)
    {
        double[] out={0,0,0};
        String raw=clip.settings.get(key);
        if(raw==null)return out;
        String[] parts=raw.split(",");
        for(int i=0;i<Math.min(3,parts.length);i++)
            try {out[i]=Double.parseDouble(parts[i].trim());}
            catch(NumberFormatException ignored){}
        return out;
    }
    private static CameraClipDocument.Target target(CameraClipDocument.Clip clip,
            int frame,CameraClipDocument.TargetResolver resolver)
    {
        String id=clip.settings.get("selector");
        return resolver==null||id==null||id.isEmpty()?null:resolver.resolve(id,frame);
    }
    private static double[] lookVector(double yaw,double pitch)
    {
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch);
        return new double[]{-Math.sin(a)*Math.cos(b),-Math.sin(b),
                Math.cos(a)*Math.cos(b)};
    }
    private static double easing(double t,String name)
    {
        t=clamp(t,0D,1D);
        if("step".equalsIgnoreCase(name))return t>=1D?1D:0D;
        if("smooth".equalsIgnoreCase(name)||"smoothstep".equalsIgnoreCase(name)
                ||"cubic".equalsIgnoreCase(name))
            return t*t*(3D-2D*t);
        if("easeIn".equalsIgnoreCase(name))return t*t;
        if("easeOut".equalsIgnoreCase(name))return 1D-(1D-t)*(1D-t);
        return t;
    }
    private static double noise(double x,int channel)
    {
        int cell=(int)Math.floor(x);
        double t=x-cell;
        t=t*t*(3D-2D*t);
        return hash(cell,channel)*(1D-t)+hash(cell+1,channel)*t;
    }
    private static double hash(int cell,int channel)
    {
        int h=cell*374761393+channel*668265263;
        h=(h^(h>>>13))*1274126177;
        h=h^(h>>>16);
        return (h>>>8)/(double)(1<<24)*2D-1D;
    }
    private CameraClipEvaluator(){}
}
