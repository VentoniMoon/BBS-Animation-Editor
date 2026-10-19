package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone harness with small type stubs. Compile the ACTUAL production
 * BodyPartPhysicsBakeController.java against these fixtures to regression-test
 * its transaction and frame coverage rules without launching Forge/JNI.
 */
public class BodyPartPhysicsBakeControllerSmokeTest
{
    private static void check(boolean success,String message)
    {
        if(!success)throw new AssertionError(message);
    }
    private static void near(float a,float b,String message)
    {
        if(Math.abs(a-b)>0.002F)
            throw new AssertionError(message+" expected "+b+" actual "+a);
    }
    private static BodyPartModelData model(String... names)
    {
        BodyPartModelData data=new BodyPartModelData();
        for(String name:names)data.bones.add(new AnimationBone(name));
        data.getPhysicsSettings(names[0]).setEnabled(true);
        return data;
    }
    public static void main(String[] args)
    {
        BodyPartPhysicsBakeController bake=new BodyPartPhysicsBakeController();
        BodyPartModelData data=model("braid");
        check(bake.begin(data,0).contains("ARMED"),"arm bake");
        check(data.bones.get(0).keys.isEmpty(),"no eager key mutation");
        for(int f=0;f<=2;f++)
        {
            bake.capture(data,f,"braid",
                    new float[]{0F,0F,0F},
                    new float[]{(float)Math.toRadians(10+f*15),0F,0F});
            bake.capture(data,f,"braid",
                    new float[]{0F,0F,0F},
                    new float[]{(float)Math.toRadians(120),0F,0F});
        }
        check(bake.frameCount()==3,"collect three frames");
        check(data.bones.get(0).keys.isEmpty(),"still no keys before commit");
        String result=bake.commit();
        check(result.startsWith("Baked 3 "),result);
        check(data.bones.get(0).keys.size()==3,"all baked keys");
        near(data.bones.get(0).keys.get(0).getTransform().getRotationX(),
                10F,"frame0 capture");
        near(data.bones.get(0).keys.get(1).getTransform().getRotationX(),
                25F,"frame1 capture");
        near(data.bones.get(0).keys.get(2).getTransform().getRotationX(),
                40F,"frame2 capture");
        check(!data.getPhysicsSettings("braid").isEnabled(),
                "prevent double-physics after commit");

        BodyPartModelData skipped=model("braid");
        check(bake.begin(skipped,0).contains("ARMED"),"second arm");
        bake.capture(skipped,0,"braid",new float[3],new float[3]);
        bake.capture(skipped,2,"braid",new float[3],new float[3]);
        check(bake.commit().contains("missing timeline frames"),
                "reject discontinuous animation");
        check(skipped.bones.get(0).keys.isEmpty(),
                "failed bake must preserve existing tracks");
        check(skipped.getPhysicsSettings("braid").isEnabled(),
                "failed bake must preserve live physics");

        BodyPartModelData partial=model("braid","loose_end");
        check(bake.begin(partial,0).contains("ARMED"),"third arm");
        bake.capture(partial,0,"braid",new float[3],new float[3]);
        bake.capture(partial,0,"loose_end",new float[3],new float[3]);
        bake.capture(partial,1,"braid",new float[3],new float[3]);
        check(bake.commit().contains("coverage changed"),
                "reject missing physical bone on later frame");
        check(partial.bones.get(0).keys.isEmpty(),"no partial commit");
        System.out.println("Jolt bake controller smoke tests passed");
    }
}

final class BodyPartJoltPreviewAdapter
{
    static boolean isRequested() {return true;}
}

final class BodyPartPhysicsSettings
{
    private boolean enabled;
    boolean isEnabled() {return enabled;}
    void setEnabled(boolean on) {enabled=on;}
}

final class AnimationTransform
{
    float x,y,z,px,py,pz,sx=1F,sy=1F,sz=1F;
    float getRotationX() {return x;}
    float getRotationY() {return y;}
    float getRotationZ() {return z;}
    float getPositionX() {return px;}
    float getPositionY() {return py;}
    float getPositionZ() {return pz;}
    float getScaleX() {return sx;}
    float getScaleY() {return sy;}
    float getScaleZ() {return sz;}
    void setRotation(float a,float b,float c) {x=a;y=b;z=c;}
    void setPosition(float a,float b,float c) {px=a;py=b;pz=c;}
    void setScale(float a,float b,float c) {sx=a;sy=b;sz=c;}
    AnimationTransform copy()
    {
        AnimationTransform t=new AnimationTransform();
        t.setRotation(x,y,z);t.setPosition(px,py,pz);t.setScale(sx,sy,sz);
        return t;
    }
}

final class AnimationKeyframe
{
    private final AnimationTransform transform=new AnimationTransform();
    AnimationTransform getTransform() {return transform;}
    void setInterpolation(
            mchorse.mclib.utils.keyframes.KeyframeInterpolation value) {}
}

final class AnimationBone
{
    final String name;
    final Map<Integer,AnimationKeyframe> keys=
            new LinkedHashMap<Integer,AnimationKeyframe>();
    AnimationBone(String name){this.name=name;}
    String getName(){return name;}
    AnimationTransform getTransformAt(int frame){return new AnimationTransform();}
    AnimationKeyframe getKeyframeAt(int frame){return keys.get(frame);}
    void addKeyframe(int frame){keys.put(frame,new AnimationKeyframe());}
}

final class BodyPartModelData
{
    final List<AnimationBone> bones=new ArrayList<AnimationBone>();
    final Map<String,BodyPartPhysicsSettings> settings=
            new LinkedHashMap<String,BodyPartPhysicsSettings>();
    boolean hasMorphModel(){return true;}
    List<AnimationBone> getBones(){return bones;}
    int getStartFrame(){return 0;}
    int getEndFrame(){return 100;}
    String getModelName(){return "test_chameleon";}
    BodyPartPhysicsSettings getPhysicsSettings(String name)
    {
        BodyPartPhysicsSettings s=settings.get(name);
        if(s==null){s=new BodyPartPhysicsSettings();settings.put(name,s);}
        return s;
    }
}
