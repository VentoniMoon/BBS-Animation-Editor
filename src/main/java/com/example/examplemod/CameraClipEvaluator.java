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
        return evaluate(clips,(double)frame,editorPose,resolver);
    }

    /** Render-only sub-tick camera evaluation for preview and native MP4. */
    public static PreviewCameraPose evaluate(List<CameraClipDocument.Clip> clips,
            double frame,PreviewCameraPose editorPose,
            CameraClipDocument.TargetResolver resolver)
    {
        if(clips==null||editorPose==null||!Double.isFinite(frame))
            throw new IllegalArgumentException("Invalid CAMERA sample");
        return eval(clips,frame,editorPose,resolver,Integer.MAX_VALUE,0);
    }

    private static PreviewCameraPose eval(List<CameraClipDocument.Clip> clips,
            double frame,PreviewCameraPose fallback,
            CameraClipDocument.TargetResolver resolver,int maxLayer,int depth)
    {
        if(depth>3)return fallback;
        List<CameraClipDocument.Clip> active=new ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:clips)
            if(clip.layer<maxLayer&&clip.enabled&&frame>=clip.tick&&
                    frame<clip.tick+clip.duration)
                active.add(clip);
        Collections.sort(active,new Comparator<CameraClipDocument.Clip>(){
            @Override public int compare(CameraClipDocument.Clip a,
                    CameraClipDocument.Clip b)
            {return Integer.compare(a.layer,b.layer);}
        });
        PreviewCameraPose result=fallback;
        for(CameraClipDocument.Clip clip:active)
        {
            double weight=CameraClipEnvelope.factor(clip,frame);
            if(weight<=0D)continue;
            PreviewCameraPose applied=apply(
                    clips,clip,frame,result,fallback,resolver,depth);
            result=weight>=1D?applied:
                    PreviewCameraPose.interpolate(result,applied,weight);
        }
        return result;
    }

    private static PreviewCameraPose apply(List<CameraClipDocument.Clip> clips,
            CameraClipDocument.Clip clip,double frame,PreviewCameraPose current,
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
                if(Boolean.parseBoolean(clip.settings.get("reverse")))
                    travel=-travel;
                return withPosition(source,source.x+direction[0]*travel,
                        source.y+direction[1]*travel,source.z+direction[2]*travel);
            }
            case KEYFRAME:
            case PATH:
                return CameraClipChannels.sample(clip,frame,current);
            case TRANSLATE:
            {
                double[] delta=point(clip,"translate");
                if(Boolean.parseBoolean(clip.settings.get("local")))
                    delta=localVector(current,delta);
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
                double x=(frame-clip.tick)/period;
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
                    PreviewCameraPose past=eval(clips,Math.max(0D,frame-1D),
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
                double[] center=point(clip,"block");
                double[] offset=point(clip,"offset");
                double distance=Math.max(0.01D,Math.abs(number(clip,"distance",4D)));
                // Orbit can be used without setting a target: pivot is
                // in front of the underlying camera when no block is chosen.
                if(target==null&&!atBlock)
                {
                    double[] forward=lookVector(current.yaw,current.pitch);
                    center=new double[]{current.x+distance*forward[0],
                            current.y+distance*forward[1],
                            current.z+distance*forward[2]};
                }
                else if(target!=null&&!atBlock)
                    center=new double[]{target.x,target.y,target.z};
                double tx=center[0]+offset[0];
                double ty=center[1]+offset[1];
                double tz=center[2]+offset[2];
                double elapsed=frame-clip.tick;
                boolean absolute=Boolean.parseBoolean(clip.settings.get("absolute"));
                double yaw=number(clip,"yaw",0D)+number(clip,"yawSpeed",0D)*elapsed;
                double pitch=number(clip,"pitch",0D)+
                        number(clip,"pitchSpeed",0D)*elapsed;
                if(!absolute)
                {
                    yaw+=(target!=null&&Boolean.parseBoolean(
                            clip.settings.get("copy")))?target.yaw:current.yaw;
                    pitch+=(target!=null&&Boolean.parseBoolean(
                            clip.settings.get("copy")))?target.pitch:current.pitch;
                }
                double[] direction=lookVector(yaw,pitch);
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
                double focus=Math.max(0.01D,Math.abs(number(clip,"focus",5D)));
                PreviewCameraPose atStart=eval(clips,clip.tick,
                        fallback,resolver,clip.layer,depth+1);
                double from=number(clip,"fromFov",atStart.fov);
                double to=number(clip,"toFov",45D);
                double phase=clamp((frame-clip.tick)/
                        Math.max(1D,clip.duration-1D),0D,1D);
                double fov=clamp(from+(to-from)*easing(phase,
                        clip.settings.get("interp")),1D,170D);
                double initialTan=Math.tan(Math.toRadians(
                        clamp(from,1D,170D))*0.5D);
                double currentTan=Math.tan(Math.toRadians(fov)*0.5D);
                if(currentTan<EPS)return current;
                double backwards=focus*(initialTan/currentTan-1D);
                double[] direction=lookVector(current.yaw,current.pitch);
                return new PreviewCameraPose(
                    current.x-direction[0]*backwards,
                    current.y-direction[1]*backwards,
                    current.z-direction[2]*backwards,
                    current.yaw,current.pitch,current.roll,(float)fov,
                    current.nearPlane,current.farPlane);
            }
            case DRAG:
            {
                if(clip.layer==0)return current;
                int active=(int)number(clip,"active",127);
                double factor=clamp(number(clip,"factor",0.5D),0D,1D);
                int rate=(int)clamp(number(clip,"rate",20D),1D,120D);
                double elapsed=Math.max(0D,frame-clip.tick);
                int steps=Math.max(1,(int)Math.min(512D,
                        Math.ceil(elapsed*rate/20D)));
                double dt=elapsed/steps;
                double smoothing=1D-Math.pow(1D-factor,dt*rate/20D);
                PreviewCameraPose smoothed=eval(clips,clip.tick,fallback,
                        resolver,clip.layer,depth+1);
                // Pure evaluation: seeking/export is repeatable, no
                // incremental mutable physics history across render frames.
                for(int i=1;i<=steps;i++)
                {
                    PreviewCameraPose desired=eval(clips,clip.tick+i*dt,
                            fallback,resolver,clip.layer,depth+1);
                    smoothed=PreviewCameraPose.interpolate(
                            smoothed,desired,smoothing);
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
                // BBS FS Remapper owns a single keyframe channel. Keys store
                // local clip time in tick and remapped global frame in x.
                double from=number(clip,"from",clip.tick);
                double to=number(clip,"to",clip.tick+clip.duration);
                double local=frame-clip.tick;
                double remapped;
                if(clip.keys.isEmpty())
                {
                    double phase=local/Math.max(1,clip.duration);
                    remapped=from+(to-from)*easing(phase,clip.settings.get("interp"));
                }
                else
                {
                    CameraClipDocument.Key previous=clip.keys.get(0);
                    CameraClipDocument.Key next=clip.keys.get(clip.keys.size()-1);
                    for(CameraClipDocument.Key key:clip.keys)
                    {
                        if(key.tick<=local)previous=key;
                        if(key.tick>=local){next=key;break;}
                    }
                    if(next.tick<=previous.tick)remapped=previous.x;
                    else
                    {
                        double phase=clamp((local-previous.tick)/
                                (next.tick-previous.tick),0D,1D);
                        phase=CameraInterpolation.sample(phase,
                                previous.interpolation);
                        remapped=previous.x+(next.x-previous.x)*phase;
                    }
                }
                return eval(clips,Math.max(0D,remapped),
                        fallback,resolver,clip.layer,depth+1);
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
            double frame,CameraClipDocument.TargetResolver resolver)
    {
        String id=clip.settings.get("selector");
        return resolver==null||id==null||id.isEmpty()?null:resolver.resolve(id,(int)Math.floor(frame));
    }
    /** Camera-local right / up / forward translation (including roll). */
    private static double[] localVector(PreviewCameraPose pose,double[] v)
    {
        double yaw=Math.toRadians(pose.yaw);
        double pitch=Math.toRadians(pose.pitch);
        double roll=Math.toRadians(pose.roll);
        double[] forward=lookVector(pose.yaw,pose.pitch);
        double[] right={Math.cos(yaw),0D,Math.sin(yaw)};
        double[] up={-Math.sin(yaw)*Math.sin(pitch),
                Math.cos(pitch),Math.cos(yaw)*Math.sin(pitch)};
        double c=Math.cos(roll),t=Math.sin(roll);
        double[] result=new double[3];
        for(int i=0;i<3;i++)
            result[i]=v[0]*(right[i]*c+up[i]*t)+
                    v[1]*(up[i]*c-right[i]*t)+v[2]*forward[i];
        return result;
    }

    private static double[] lookVector(double yaw,double pitch)
    {
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch);
        return new double[]{-Math.sin(a)*Math.cos(b),-Math.sin(b),
                Math.cos(a)*Math.cos(b)};
    }
    private static double easing(double t,String name)
    {
        return CameraInterpolation.sample(t,name);
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
