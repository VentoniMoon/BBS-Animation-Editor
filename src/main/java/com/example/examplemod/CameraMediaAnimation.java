package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IMAGE and VIDEO transform curves on the fixed 20 TPS scene timeline.
 * Values are shared by the GUI preview and the exact offline film timestamp.
 * The saved clip retains its ordinary base transform; keyed overrides apply
 * only when keys exist. No GPU, Minecraft, or Jolt dependency.
 */
public final class CameraMediaAnimation
{
    public static final String[] FIELDS={
        "x","y","width","height","rotation","scaleX","scaleY","opacity"
    };

    public static boolean supports(CameraClipDocument.Clip clip)
    {
        return clip!=null&&(clip.kind==CameraClipDocument.Kind.IMAGE||
                clip.kind==CameraClipDocument.Kind.VIDEO);
    }

    public static Map<String,Double> sample(CameraClipDocument.Clip clip,
            double sceneTick)
    {
        Map<String,Double> result=new LinkedHashMap<String,Double>();
        for(String field:FIELDS)result.put(field,base(clip,field));
        if(!supports(clip)||clip.keys.isEmpty())return result;

        double local=sceneTick-clip.tick;
        CameraClipDocument.Key left=null,right=null;
        List<CameraClipDocument.Key> keys=clip.keys;
        // Key ordering is normally sorted by the document. Search without
        // assuming order to tolerate loaded legacy or hand-edited files.
        for(CameraClipDocument.Key key:keys)
        {
            if(key.tick<=local&&(left==null||key.tick>left.tick))
                left=key;
            if(key.tick>=local&&(right==null||key.tick<right.tick))
                right=key;
        }
        if(left==null)left=right;
        if(right==null)right=left;
        if(left==null)return result;
        double t=left==right||right.tick<=left.tick?0D:
                (local-left.tick)/(double)(right.tick-left.tick);
        String interpolation=left.interpolation;
        if(interpolation==null||interpolation.isEmpty())
            interpolation="linear";
        t=CameraInterpolation.sample(t,interpolation);
        for(String field:FIELDS)
        {
            double fallback=base(clip,field);
            double first=number(left.media,field,fallback);
            double last=number(right.media,field,fallback);
            result.put(field,first+(last-first)*t);
        }
        return result;
    }

    public static double base(CameraClipDocument.Clip clip,String name)
    {
        double fallback=("scaleX".equals(name)||"scaleY".equals(name)||
                "opacity".equals(name))?1D:
                ("x".equals(name)||"y".equals(name)||
                "width".equals(name)||"height".equals(name))?0.5D:0D;
        if(clip==null)return fallback;
        try
        {
            double value=Double.parseDouble(clip.settings.get(name));
            return Double.isFinite(value)?value:fallback;
        }
        catch(RuntimeException missing){return fallback;}
    }

    private static double number(Map<String,Double> map,String name,
            double fallback)
    {
        Double value=map.get(name);
        return value==null||!Double.isFinite(value)?fallback:value;
    }

    /** Capture current visible parameters as a newly authored media key. */
    public static void capture(CameraClipDocument.Clip clip,
            CameraClipDocument.Key key,int absoluteTick)
    {
        key.media.clear();
        key.media.putAll(sample(clip,(double)absoluteTick));
    }

    private CameraMediaAnimation(){}
}
