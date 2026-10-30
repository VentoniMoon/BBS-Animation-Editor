package net.minecraft.client;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
public class Minecraft
{
    public WorldClient world;
    public RenderGlobal renderGlobal;
    private final RenderManager renderManager=new RenderManager();
    public RenderManager getRenderManager(){return renderManager;}
    public static class RenderManager
    {
        public WorldClient world;
        public void setWorld(WorldClient world){this.world=world;}
    }
}
