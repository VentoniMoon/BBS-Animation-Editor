package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inspector parameter keys mirrored from BBS FS 1.12.2 clips.
 * Complex values (curves, Position, Placement, expressions) are deliberately
 * represented as schema entries rather than invented numerical substitutes.
 */
public final class CameraClipParameters
{
    private static final Map<CameraClipDocument.Kind,LinkedHashMap<String,String>>
            DEFAULTS=new LinkedHashMap<CameraClipDocument.Kind,LinkedHashMap<String,String>>();
    static
    {
        add(CameraClipDocument.Kind.IDLE,"position","camera");
        add(CameraClipDocument.Kind.DOLLY,"position","camera","distance","0.1","yaw","0","pitch","0","interp","linear");
        add(CameraClipDocument.Kind.PATH,"points","[]","interpPoint","hermite","interpAngle","hermite");
        add(CameraClipDocument.Kind.KEYFRAME,"additive","false","interpolation","linear","channels","XYZ Yaw Pitch Roll FOV Distance");
        add(CameraClipDocument.Kind.TRACKER,"selector","","offset","0,0,0","angle","0,0,0","fov","70","group","","look_at","false","relative","false","active","127");
        add(CameraClipDocument.Kind.SPLINE,"selector","","group","","start","0","end","100","interp","linear","path_rotation","1","offset","0,0,0","angle","0,0,0","fov","70","active","127");
        add(CameraClipDocument.Kind.TRANSLATE,"translate","0,0,0","active","7");
        add(CameraClipDocument.Kind.ANGLE,"angle","0,0,0,0","fov","0","active","120");
        add(CameraClipDocument.Kind.DRAG,"deterministic","true","factor","0.5","rate","60");
        add(CameraClipDocument.Kind.SHAKE,"shake","1","shakeAmount","0","noise","false","active","24");
        add(CameraClipDocument.Kind.MATH,"expression","","active","127");
        add(CameraClipDocument.Kind.REMAPPER,"channel","[]","from","0","to","100","interp","linear");
        add(CameraClipDocument.Kind.LOOK,"selector","","offset","0,0,0","relative","false","atBlock","false","forward","false","block","0,0,0");
        add(CameraClipDocument.Kind.ORBIT,"selector","","offset","0,0,0","copy","false","distance","0","yaw","0","pitch","0","absolute","false","atBlock","false","block","0,0,0");
        add(CameraClipDocument.Kind.DOLLY_ZOOM,"focus","0");
        add(CameraClipDocument.Kind.AUDIO,"audio","","offset","0","volume","1");
        add(CameraClipDocument.Kind.CURVE,"channels","sun_rotation");
        add(CameraClipDocument.Kind.SUBTITLE,"placement","center","color","FFFFFFFF","textShadow","true","background","0","backgroundOffset","2","shadow","0","shadowOpaque","false","transform","identity","lineHeight","12","maxWidth","0","font","","fontSize","9","image","","imageRight","true","imageScale","1","text","Subtitle","x","0.5","y","0.88","opacity","1");
        add(CameraClipDocument.Kind.IMAGE,"texture","","placement","center","color","FFFFFFFF","fullscreen","false","smooth","true","transform","identity","x","0.5","y","0.5","width","0.5","height","0.5","opacity","1");
        add(CameraClipDocument.Kind.VIDEO,"audio","","offset","0","volume","1","placement","center","color","FFFFFFFF","loop","false","fullscreen","false","smooth","true","transform","identity","video","","x","0.5","y","0.5","width","0.5","height","0.5","opacity","1");
    }
    private static void add(CameraClipDocument.Kind kind,String... pairs)
    {
        LinkedHashMap<String,String> values=new LinkedHashMap<String,String>();
        for(int i=0;i+1<pairs.length;i+=2)values.put(pairs[i],pairs[i+1]);
        DEFAULTS.put(kind,values);
    }
    public static Map<String,String> defaults(CameraClipDocument.Kind kind)
    {
        return new LinkedHashMap<String,String>(DEFAULTS.get(kind));
    }
    public static CameraClipDocument.Clip create(CameraClipDocument.Kind kind,
            int tick,int duration,int layer)
    {
        CameraClipDocument.Clip clip=new CameraClipDocument.Clip(
                kind,tick,duration,layer);
        clip.settings.putAll(defaults(kind));
        clip.settings.put("envelope.enabled","false");
        clip.settings.put("envelope.mode","0");
        clip.settings.put("envelope.fadeIn","10");
        clip.settings.put("envelope.fadeOut","10");
        clip.settings.put("envelope.pre","linear");
        clip.settings.put("envelope.post","linear");
        return clip;
    }
    private CameraClipParameters(){}
}
