package com.example.examplemod;
import java.util.List;
import net.minecraft.client.multiplayer.WorldClient;

/** Temporary client render state, never changes server rules or total time. */
public final class CameraEnvironmentWorld
{
    private static WorldClient active;
    private static long time;
    private static float rain,thunder;
    private static boolean timeChanged,weatherChanged;
    public static void prepare(WorldClient world,List<CameraClipDocument.Clip> clips,double frame)
    {
        restore();
        if(world==null)return;
        CameraEnvironmentClip.Sample s=CameraEnvironmentClip.sample(clips,frame);
        if(s.time==null&&s.weather==null)return;
        active=world;
        timeChanged=s.time!=null;
        weatherChanged=s.weather!=null;
        if(timeChanged)
        {
            time=world.getWorldTime();
            world.getWorldInfo().setWorldTime(Math.max(0L,Math.round(s.time)));
        }
        if(weatherChanged)
        {
            rain=world.getRainStrength(1F);
            thunder=world.getThunderStrength(1F);
            world.setRainStrength(s.weather==0?0F:1F);
            world.setThunderStrength(s.weather==2?1F:0F);
        }
    }
    public static void restore()
    {
        WorldClient world=active;
        active=null;
        if(world==null)return;
        if(timeChanged)world.getWorldInfo().setWorldTime(time);
        if(weatherChanged)
        {
            world.setRainStrength(rain);
            world.setThunderStrength(thunder);
        }
        timeChanged=weatherChanged=false;
    }
    private CameraEnvironmentWorld(){}
}
