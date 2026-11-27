package com.example.examplemod;
import java.util.List;

/** Independent world-only channels in the existing serializable clip keys. */
public final class CameraEnvironmentClip
{
    public static final int TIME=1, WEATHER=2;
    public static final class Sample
    {
        public Double time;
        public Integer weather;
    }
    public static Sample sample(List<CameraClipDocument.Clip> clips,double frame)
    {
        Sample out=new Sample();
        int timeLayer=-1,weatherLayer=-1;
        for(CameraClipDocument.Clip clip:clips)
        {
            if(!clip.enabled||clip.kind!=CameraClipDocument.Kind.ENVIRONMENT||
                    frame<clip.tick||frame>=(double)clip.tick+clip.duration)continue;
            if(clip.layer>=timeLayer)
            {
                Double v=time(clip,frame);
                if(v!=null){out.time=v;timeLayer=clip.layer;}
            }
            if(clip.layer>=weatherLayer)
            {
                Integer v=weather(clip,frame);
                if(v!=null){out.weather=v;weatherLayer=clip.layer;}
            }
        }
        return out;
    }
    static Double time(CameraClipDocument.Clip clip,double frame)
    {
        double local=frame-clip.tick;
        CameraClipDocument.Key before=null,after=null;
        for(CameraClipDocument.Key k:clip.keys)
        {
            if((k.mask&TIME)==0||!Double.isFinite(k.x))continue;
            if(k.tick<=local&&(before==null||k.tick>before.tick))before=k;
            if(k.tick>=local&&(after==null||k.tick<after.tick))after=k;
        }
        if(before==null)return null;
        if(after==null||before==after)return before.x;
        String easing=before.interpolation.isEmpty()?clip.settings.get("interp"):before.interpolation;
        double phase=CameraInterpolation.sample((local-before.tick)/(after.tick-before.tick),easing);
        return before.x+(after.x-before.x)*phase;
    }
    static Integer weather(CameraClipDocument.Clip clip,double frame)
    {
        CameraClipDocument.Key before=null;
        for(CameraClipDocument.Key k:clip.keys)
            if((k.mask&WEATHER)!=0&&k.tick<=frame-clip.tick&&
                    (before==null||k.tick>before.tick))before=k;
        return before==null?null:weatherCode(before.y);
    }
    static void capture(CameraClipDocument.Clip clip,CameraClipDocument.Key key,int frame)
    {
        Double time=time(clip,frame);
        Integer weather=weather(clip,frame);
        key.x=time==null?0D:time;
        key.y=weather==null?0D:weather;
        key.mask=TIME;
        for(CameraClipDocument.Key old:clip.keys)
            if(old.tick==key.tick)
            {
                key.mask=old.mask;key.x=old.x;key.y=old.y;
                key.interpolation=old.interpolation;break;
            }
    }
    private static int weatherCode(double value)
    {return (int)Math.max(0L,Math.min(2L,Math.round(value)));}
    public static String weatherName(double value)
    {
        switch(weatherCode(value))
        {
            case 1:return "Rain";
            case 2:return "Thunder";
            default:return "Clear";
        }
    }
    private CameraEnvironmentClip(){}
}
