package com.example.examplemod;
import net.minecraft.nbt.NBTTagCompound;
public final class CameraSceneLightsSmokeTest {
    private static void check(boolean yes,String label){
        if(!yes)throw new AssertionError(label);
    }
    public static void main(String[] args) {
        CameraSceneLights scene=new CameraSceneLights();
        CameraSceneLights.Light one=scene.add("Key Light",12D,70.5D,-31D);
        one.targetX=16D;one.targetY=69D;one.targetZ=-29D;
        one.enabled=false;
        one.morph.setBoolean("Light/Enabled",true);
        one.morph.setBoolean("Shadows/Enabled",true);
        one.morph.setBoolean("Flare/Enabled",false);
        one.morph.setBoolean("Fog/Enabled",true);
        one.morph.setDouble("Samples",20D);
        one.morph.setDouble("ScatteringAlbedo",0.5D);
        one.morph.setDouble("Density",20D);
        one.morph.setDouble("Anisotropy",0.4D);
        CameraSceneLights.Light two=scene.add("Fill Light",5D,64D,-5D);
        two.morph.setString("custom-vendor-field","keep");
        NBTTagCompound stored=scene.save();
        CameraSceneLights loaded=new CameraSceneLights();
        loaded.load(stored);
        check(loaded.all().size()==2,"save two independent scene lights");
        check(loaded.selected()!=null&&loaded.selected().id.equals(two.id),
                "restore selected light UUID");
        CameraSceneLights.Light restored=loaded.all().get(0);
        check(restored.id.equals(one.id),"stable first UUID");
        check("Key Light".equals(restored.name),"preserve name");
        check(restored.x==12D&&restored.y==70.5D&&restored.z==-31D,
                "restore world Origin");
        check(restored.targetX==16D&&restored.targetY==69D&&restored.targetZ==-29D,
                "restore separate world Target");
        check(!restored.enabled,"disabled status persisted");
        check(restored.morph.getBoolean("Light/Enabled")&&
                restored.morph.getBoolean("Shadows/Enabled")&&
                !restored.morph.getBoolean("Flare/Enabled")&&
                restored.morph.getBoolean("Fog/Enabled"),
                "preserve four native feature groups");
        check(restored.morph.getDouble("Samples")==20D&&
                restored.morph.getDouble("ScatteringAlbedo")==0.5D&&
                restored.morph.getDouble("Density")==20D&&
                restored.morph.getDouble("Anisotropy")==0.4D,
                "preserve all shown volumetric parameters");
        check("keep".equals(loaded.selected().morph.getString(
                "custom-vendor-field")),"unknown native settings remain untouched");
        stored.getTagList("Lights",10).getCompoundTagAt(0)
                .setString("Label","mutated");
        check("Key Light".equals(restored.name),"stored NBT detached");
        loaded.removeSelected();
        check(loaded.all().size()==1&&loaded.selected()!=null,
                "delete selected source without leaving dangling selection");
        loaded.clear();
        check(loaded.all().isEmpty()&&loaded.selected()==null,
                "scene load reset never leaks lights");
        System.out.println("CAMERA keyless BetterLights scene object persistence PASS");
    }
}
