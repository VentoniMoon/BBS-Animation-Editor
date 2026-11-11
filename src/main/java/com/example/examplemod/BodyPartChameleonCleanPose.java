package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Temporarily removes only the preview-added Euler deltas from native
 * Chameleon current bone rotations while sampling the animated rest pose.
 * Native rotations are restored before returning to the renderer.
 */
public final class BodyPartChameleonCleanPose implements AutoCloseable
{
    private static final class Saved
    {
        final Object vector;
        final float x,y,z;
        Saved(Object v,float x,float y,float z)
        { this.vector=v;this.x=x;this.y=y;this.z=z; }
    }

    private static final class SavedScale
    {
        final Object vector;
        final float x,y,z;
        SavedScale(Object v,float x,float y,float z)
        { this.vector=v;this.x=x;this.y=y;this.z=z; }
    }

    private final List<Saved> saved = new ArrayList<Saved>();
    private final List<SavedScale> savedScales=new ArrayList<SavedScale>();

    private BodyPartChameleonCleanPose() {}

    public static BodyPartChameleonCleanPose begin(Object morph,
            Map<String, float[]> previewEulerDelta)
    {
        return begin(morph,previewEulerDelta,null);
    }

    public static BodyPartChameleonCleanPose begin(Object morph,
            Map<String,float[]> previewEulerDelta,
            Map<String,SkirtAdaptiveWidth.Width> previewWidth)
    {
        BodyPartChameleonCleanPose scope = new BodyPartChameleonCleanPose();
        boolean hasEuler=previewEulerDelta!=null&&!previewEulerDelta.isEmpty();
        boolean hasWidth=previewWidth!=null&&!previewWidth.isEmpty();
        if(morph==null||(!hasEuler&&!hasWidth))return scope;
        try
        {
            Object wrapper=morph.getClass().getMethod("getModel").invoke(morph);
            Object model=wrapper.getClass().getField("model").get(wrapper);
            Object roots=model.getClass().getField("bones").get(model);
            if(hasEuler)
                for (Map.Entry<String,float[]> entry:previewEulerDelta.entrySet())
                {
                    float[] delta=entry.getValue();
                    if (delta==null || delta.length!=3) continue;
                    Object bone=find(roots,entry.getKey());
                    if (bone==null) continue;
                    Object current=bone.getClass().getField("current").get(bone);
                    Object rotation=current.getClass().getField("rotation").get(current);
                    float x=value(rotation,"x"), y=value(rotation,"y"), z=value(rotation,"z");
                    scope.saved.add(new Saved(rotation,x,y,z));
                    float degrees=180F/(float)Math.PI;
                    set(rotation,"x",x-delta[0]*degrees);
                    set(rotation,"y",y-delta[1]*degrees);
                    set(rotation,"z",z-delta[2]*degrees);
                }
            // Strip only preview-added stretch from native CURRENT scales
            // while sampling clean geometry. Preserve original source scale.
            if(hasWidth)
                for(Map.Entry<String,SkirtAdaptiveWidth.Width> entry:previewWidth.entrySet())
                {
                    SkirtAdaptiveWidth.Width width=entry.getValue();
                    if(width==null||width.multiplier<=1F||width.multiplier>1.28F)
                        continue;
                    Object bone=find(roots,entry.getKey());
                    if(bone==null)continue;
                    Object current=bone.getClass().getField("current").get(bone);
                    Object scale=current.getClass().getField("scale").get(current);
                    float x=value(scale,"x"),y=value(scale,"y"),z=value(scale,"z");
                    scope.savedScales.add(new SavedScale(scale,x,y,z));
                    if(width.axis==0)set(scale,"x",x/width.multiplier);
                    else if(width.axis==2)set(scale,"z",z/width.multiplier);
                }
        }
        catch (Throwable error)
        {
            scope.close();
            throw new IllegalStateException("Cannot isolate clean Chameleon physics pose",error);
        }
        return scope;
    }

    private static Object find(Object list,String id) throws Exception
    {
        if (!(list instanceof List)) return null;
        for(Object bone:(List<?>)list)
        {
            if (id.equals(bone.getClass().getField("id").get(bone))) return bone;
            Object nested=find(bone.getClass().getField("children").get(bone),id);
            if(nested!=null)return nested;
        }
        return null;
    }

    private static float value(Object v,String field) throws Exception
    { return v.getClass().getField(field).getFloat(v); }

    private static void set(Object v,String field,float n) throws Exception
    { v.getClass().getField(field).setFloat(v,n); }

    @Override
    public void close()
    {
        for(int i=savedScales.size()-1;i>=0;i--)
        {
            SavedScale s=savedScales.get(i);
            try {set(s.vector,"x",s.x);set(s.vector,"y",s.y);set(s.vector,"z",s.z);}
            catch(Throwable ignored){}
        }
        savedScales.clear();
        for (int i=saved.size()-1;i>=0;i--)
        {
            Saved s=saved.get(i);
            try {set(s.vector,"x",s.x);set(s.vector,"y",s.y);set(s.vector,"z",s.z);}
            catch (Throwable ignored) {}
        }
        saved.clear();
    }
}
