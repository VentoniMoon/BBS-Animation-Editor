package net.minecraft.client.renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
public class RenderGlobal
{
    public static int created,disposed;
    public final Minecraft mc;
    public WorldClient world;
    public boolean destroyed;
    public RenderGlobal(Minecraft mc)
    {
        this.mc=mc;created++;
    }
    public void setWorldAndLoadRenderers(WorldClient world)
    {
        this.world=world;
        if(world==null){destroyed=true;disposed++;}
    }
}
