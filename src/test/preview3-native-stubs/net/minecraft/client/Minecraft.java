package net.minecraft.client;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
public class Minecraft {
    private Framebuffer framebufferMc=new Framebuffer();
    private Entity view=new Entity(new Object());
    public Object world=view.world;
    public GameSettings gameSettings=new GameSettings();
    public FakeRenderer entityRenderer=new FakeRenderer();
    public float getRenderPartialTicks(){return .5F;}
    public Entity getRenderViewEntity(){return view;}
    public void setRenderViewEntity(Entity entity){view=entity;}
    public Framebuffer getFramebuffer(){return framebufferMc;}
    public static class GameSettings {public float fovSetting=70F;}
    public static class FakeRenderer {
        public boolean fail;
        public int draws;
        public void renderWorld(float partialTicks,long nanos) {
            draws++;
            if(fail)throw new IllegalStateException("fake native failure");
        }
    }
}
