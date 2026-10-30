package com.example.examplemod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

/** Java 8 regression: reflected world must NEVER mutate/replace main renderer. */
public final class CameraMirrorWorldIsolationSmokeTest
{
    private static void check(boolean ok,String problem)
    {
        if(!ok)throw new AssertionError(problem);
    }
    public static void main(String[] args) throws Throwable
    {
        Minecraft mc=new Minecraft();
        mc.world=new WorldClient("world one");
        RenderGlobal main=new RenderGlobal(mc);
        main.setWorldAndLoadRenderers(mc.world);
        mc.renderGlobal=main;

        final RenderGlobal[] separate={null};
        CameraMirrorWorldIsolation.draw(mc,new CameraMirrorWorldIsolation.Pass()
        {
            @Override public void render()
            {
                check(mc.renderGlobal!=main,"main renderer in mirror");
                separate[0]=mc.renderGlobal;
                check(separate[0].world==mc.world,"mirror world not attached");
            }
        });
        check(mc.renderGlobal==main,"main RenderGlobal not restored");
        check(main.world==mc.world,"main world changed");
        check(!main.destroyed,"main chunks disposed");

        // Reuse the same mirror chunks without a new renderer.
        int count=RenderGlobal.created;
        CameraMirrorWorldIsolation.draw(mc,new CameraMirrorWorldIsolation.Pass()
        {
            @Override public void render()
            {
                check(mc.renderGlobal==separate[0],
                        "mirror instance not reused");
            }
        });
        check(RenderGlobal.created==count,"new mirror renderer every frame");
        check(mc.renderGlobal==main,"main swapped after second pass");

        boolean threw=false;
        try
        {
            CameraMirrorWorldIsolation.draw(mc,
                    new CameraMirrorWorldIsolation.Pass()
            {
                @Override public void render()
                {
                    throw new IllegalStateException("intentional");
                }
            });
        }
        catch(IllegalStateException expected){threw=true;}
        check(threw,"exception swallowed");
        check(mc.renderGlobal==main,"main renderer leaked after error");
        check(!CameraMirrorWorldIsolation.rendering(),
                "mirror still in rendering state");

        mc.world=new WorldClient("world two");
        main.setWorldAndLoadRenderers(mc.world);
        CameraMirrorWorldIsolation.draw(mc,
                new CameraMirrorWorldIsolation.Pass()
        {
            @Override public void render()
            {
                check(mc.renderGlobal!=separate[0],
                        "old-world mirror renderer reused");
            }
        });
        check(separate[0].destroyed,"old mirror chunks not disposed");
        check(mc.renderGlobal==main,"main renderer leaked on world switch");

        CameraMirrorWorldIsolation.reset(mc);
        check(main==mc.renderGlobal,"main renderer disposed by reset");
        check(!main.destroyed,"main renderer destroyed by mirror reset");
        check(!CameraMirrorWorldIsolation.ready(),"mirror resources still live");
        System.out.println("Mirror world isolation smoke OK");
    }
}
