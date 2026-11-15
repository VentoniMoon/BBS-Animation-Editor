package com.example.examplemod;
import java.util.Random;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.multiplayer.WorldClient;
public final class ParticleEmitterConfigSmokeTest
{
    private static void need(boolean yes,String message)
    {
        if(!yes)throw new AssertionError(message);
    }
    public static void main(String[] ignored)
    {
        NBTTagCompound tag=ParticleEmitterConfig.defaults();
        need(ParticleEmitterConfig.isParticle(tag),"native particle Name");
        need(ParticleEmitterConfig.types().size()==5,
                "full runtime enum list must appear in picker");
        need(ParticleEmitterConfig.types().get(0).compareToIgnoreCase(
                ParticleEmitterConfig.types().get(1))<=0,
                "particle types must be alphabetic");
        need(ParticleEmitterConfig.changeType(tag,"flame"),
                "known enum type accepted");
        need(!ParticleEmitterConfig.changeType(tag,"made-up"),
                "unknown enum type rejected");
        need("flame".equals(ParticleEmitterConfig.type(tag)),
                "particle type persisted");
        need(!ParticleEmitterConfig.update(tag,"Frequency",0),
                "zero frequency not supported");
        need(ParticleEmitterConfig.update(tag,"Frequency",3),
                "frequency mutable");
        need(ParticleEmitterConfig.intValue(tag,"Frequency")==3,
                "frequency roundtrip");
        need(ParticleEmitterConfig.update(tag,"Cap",2500),
                "native emission cap");
        ParticleEmitterConfig.setArgs(tag,"2, 8");
        need(tag.getIntArray("Args").length==2,"args parser");
        tag.setString("FutureNativeField","keep me");
        NBTTagCompound normalized=ParticleEmitterConfig.normalize(tag);
        need("keep me".equals(normalized.getString("FutureNativeField")),
                "unknown native NBT must survive normalization");
        WorldClient world=new WorldClient();
        ParticleEmitterConfig.emit(world,normalized,1,2,3,0,
                new Random(1),0);
        need(world.count==0,"wrong interval should not spawn");
        ParticleEmitterConfig.update(normalized,"Frequency",1);
        ParticleEmitterConfig.update(normalized,"Count",4);
        ParticleEmitterConfig.update(normalized,"DX",0);
        ParticleEmitterConfig.update(normalized,"DY",0);
        ParticleEmitterConfig.update(normalized,"DZ",0);
        ParticleEmitterConfig.update(normalized,"Speed",0);
        ParticleEmitterConfig.emit(world,normalized,1,2,3,0,
                new Random(1),0);
        need(world.count==4,"one vanilla burst per logical tick");
        need(world.x==1D&&world.y==2D&&world.z==3D,
                "zero spread position must match source");
        need(world.args.length==2&&world.args[0]==2,
                "native extra arguments passed through");
        CameraSceneParticles scene=new CameraSceneParticles();
        CameraSceneParticles.Source a=scene.add("Flame",4,5,6);
        a.morph=normalized;a.yaw=90D;
        CameraSceneParticles saved=new CameraSceneParticles();
        saved.load(scene.save());
        need(saved.all().size()==1&&saved.selected()!=null,
                "static source and selection saved");
        need(saved.selected().x==4D&&saved.selected().yaw==90D,
                "static source location/rotation preserved");
        need("flame".equals(ParticleEmitterConfig.type(
                saved.selected().morph)),"source particle type preserved");
        need("keep me".equals(
                saved.selected().morph.getString("FutureNativeField")),
                "source retains opaque Blockbuster NBT on load");
        saved.removeSelected();
        need(saved.all().isEmpty(),"remove static source");
        System.out.println("ParticleEmitterConfigSmokeTest OK");
    }
}
