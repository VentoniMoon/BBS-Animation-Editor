package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * CAMERA spline authored as editable per-clip knots, independent of GL.
 * Geometry interpolates with non-uniform (tick-aware) Hermite tangents,
 * while Look direction can follow the curve derivative.
 *
 * This supports local keys and optional actor transform. An external
 * BBS FS Form spline/bone can be added as a distinct source adapter later.
 */
public final class CameraSplinePath
{
    private static final double EPS=1E-6D;

    private static final class Knot
    {
        final CameraClipDocument.Key key;
        Knot(CameraClipDocument.Key k){key=k;}
    }

    public static PreviewCameraPose sample(CameraClipDocument.Clip clip,
            double frame,PreviewCameraPose beneath,
            CameraClipDocument.TargetResolver resolver)
    {
        if(clip.keys.isEmpty())return beneath;
        List<Knot> points=new ArrayList<Knot>();
        for(CameraClipDocument.Key k:clip.keys)points.add(new Knot(k));
        Collections.sort(points,new Comparator<Knot>(){
            @Override public int compare(Knot a,Knot b)
            {return Integer.compare(a.key.tick,b.key.tick);}
        });
        double local=Math.max(0D,Math.min(1D,
                (frame-clip.tick)/Math.max(1D,clip.duration-1D)));
        double start=number(clip,"start",0D)/100D;
        double end=number(clip,"end",100D)/100D;
        double progress=clamp(start+(end-start)*
                CameraInterpolation.sample(local,clip.settings.get("interp")),
                0D,1D);
        int first=points.get(0).key.tick;
        int last=points.get(points.size()-1).key.tick;
        double sampleTime=first+(last-first)*progress;
        int index=0;
        while(index<points.size()-2&&
                points.get(index+1).key.tick<sampleTime)index++;
        CameraClipDocument.Key a=points.get(index).key;
        CameraClipDocument.Key b=points.get(
                Math.min(index+1,points.size()-1)).key;
        double interval=Math.max(1D,b.tick-a.tick);
        double t=clamp((sampleTime-a.tick)/interval,0D,1D);
        CameraClipDocument.Key prev=points.get(Math.max(0,index-1)).key;
        CameraClipDocument.Key next=points.get(
                Math.min(points.size()-1,index+2)).key;
        String method=clip.settings.get("geometry");
        boolean smooth=method==null||!method.equalsIgnoreCase("linear");
        double[] pos=new double[3];
        double[] derivative=new double[3];
        for(int axis=0;axis<3;axis++)
        {
            double x0=coord(a,axis),x1=coord(b,axis);
            double d0=(x1-coord(prev,axis))/
                    Math.max(1D,b.tick-prev.tick);
            double d1=(coord(next,axis)-x0)/
                    Math.max(1D,next.tick-a.tick);
            // For the first/last knot, use the one-sided tangent.
            if(prev==a)d0=(x1-x0)/interval;
            if(next==b)d1=(x1-x0)/interval;
            if(smooth && a!=b)
            {
                double u=t*t,v=u*t;
                pos[axis]=(2*v-3*u+1)*x0+(v-2*u+t)*d0*interval+
                        (-2*v+3*u)*x1+(v-u)*d1*interval;
                derivative[axis]=(6*u-6*t)*x0+
                        (3*u-4*t+1)*d0*interval+
                        (-6*u+6*t)*x1+(3*u-2*t)*d1*interval;
            }
            else
            {
                pos[axis]=x0+(x1-x0)*t;
                derivative[axis]=x1-x0;
            }
        }

        double yaw=angle(a.yaw,b.yaw,t);
        double pitch=angle(a.pitch,b.pitch,t);
        double roll=angle(a.roll,b.roll,t);
        double fov=a.fov+(b.fov-a.fov)*t;
        double[] angles=vector(clip,"angle");
        boolean follow=number(clip,"path_rotation",1D)!=0D;
        // When moving backwards through a spline, face the direction of travel.
        double sign=end>=start?1D:-1D;
        double dx=derivative[0]*sign,dy=derivative[1]*sign,dz=derivative[2]*sign;
        if(follow&&dx*dx+dy*dy+dz*dz>EPS*EPS)
        {
            yaw=Math.toDegrees(Math.atan2(-dx,dz));
            pitch=Math.toDegrees(Math.atan2(-dy,
                    Math.sqrt(dx*dx+dz*dz)));
        }

        double[] offset=vector(clip,"offset");
        String selector=clip.settings.get("selector");
        boolean relative=selector!=null&&!selector.isEmpty()&&resolver!=null;
        if(relative)
        {
            CameraClipDocument.Target target=resolver.resolve(selector,
                    Math.max(0,(int)Math.floor(frame)));
            if(target!=null)
            {
                CameraClipDocument.Target nextTarget=resolver.resolve(selector,
                        Math.max(0,(int)Math.floor(frame))+1);
                double alpha=frame-Math.floor(frame);
                double tyaw=target.yaw;
                double tx=target.x,ty=target.y,tz=target.z;
                if(nextTarget!=null)
                {
                    tx+=(nextTarget.x-tx)*alpha;
                    ty+=(nextTarget.y-ty)*alpha;
                    tz+=(nextTarget.z-tz)*alpha;
                    tyaw=angle(target.yaw,nextTarget.yaw,alpha);
                }
                double radians=Math.toRadians(tyaw);
                double c=Math.cos(radians),s=Math.sin(radians);
                double px=pos[0],pz=pos[2];
                pos[0]=tx+px*c-pz*s;
                pos[1]+=ty;
                pos[2]=tz+px*s+pz*c;
                yaw+=tyaw;
            }
        }
        int channels=(int)number(clip,"active",127D);
        double x=pos[0]+offset[0],y=pos[1]+offset[1],z=pos[2]+offset[2];
        return new PreviewCameraPose(
                (channels&1)!=0?x:beneath.x,
                (channels&2)!=0?y:beneath.y,
                (channels&4)!=0?z:beneath.z,
                (float)((channels&8)!=0?yaw+angles[0]:beneath.yaw),
                (float)((channels&16)!=0?pitch+angles[1]:beneath.pitch),
                (float)((channels&32)!=0?roll+angles[2]:beneath.roll),
                (float)((channels&64)!=0?
                        clamp(fov,1D,170D):beneath.fov),
                beneath.nearPlane,beneath.farPlane);
    }

    private static double coord(CameraClipDocument.Key key,int axis)
    {return axis==0?key.x:axis==1?key.y:key.z;}

    private static double angle(double a,double b,double alpha)
    {
        double delta=(b-a)%360D;
        if(delta>180D)delta-=360D;
        if(delta< -180D)delta+=360D;
        return a+delta*alpha;
    }

    private static double[] vector(CameraClipDocument.Clip clip,String name)
    {
        double[] values={0D,0D,0D};
        String raw=clip.settings.get(name);
        if(raw==null)return values;
        String[] split=raw.split(",");
        for(int i=0;i<Math.min(3,split.length);i++)
            try
            {
                double number=Double.parseDouble(split[i].trim());
                if(Double.isFinite(number))values[i]=number;
            }
            catch(NumberFormatException ignored){}
        return values;
    }
    private static double number(CameraClipDocument.Clip clip,String field,double or)
    {
        try
        {
            double n=Double.parseDouble(clip.settings.get(field));
            return Double.isFinite(n)?n:or;
        }
        catch(RuntimeException ignored){return or;}
    }
    private static double clamp(double x,double a,double b)
    {return Math.max(a,Math.min(b,x));}

    private CameraSplinePath(){}
}
