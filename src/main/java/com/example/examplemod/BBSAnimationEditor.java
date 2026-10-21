package com.example.examplemod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = BBSAnimationEditor.MODID,
        name = BBSAnimationEditor.NAME,
        version = BBSAnimationEditor.VERSION
)
public class BBSAnimationEditor
{
    public static final String MODID = "bbsanimationeditor";
    public static final String NAME = "BBS Animation Editor";
    public static final String VERSION = "0.1.0";

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();

        MinecraftForge.EVENT_BUS.register(new KeyHandler());

        // Real Blockbuster playback calls ChameleonRenderer directly, outside
        // our editor's actor Preview. Install the render-only Weld hook early,
        // before the Chameleon renderer is loaded for the first world frame.
        if(event.getSide().isClient())
        {
            try
            {
                // Install the per-instance morph field and NBT/copy hook
                // BEFORE resolving the ordinary Chameleon render class.
                net.minecraft.launchwrapper.Launch.classLoader.registerTransformer(
                        "com.example.examplemod.ChameleonWeldMorphTransformer");
                net.minecraft.launchwrapper.Launch.classLoader.registerTransformer(
                        "com.example.examplemod.ChameleonWeldRendererTransformer");

                Class.forName("mchorse.chameleon.metamorph.ChameleonMorph",
                        false,net.minecraft.launchwrapper.Launch.classLoader);
                Class.forName("mchorse.chameleon.lib.render.ChameleonRenderer",
                        false,net.minecraft.launchwrapper.Launch.classLoader);
                if(!ChameleonWeldRuntime.isRendererHookInstalled())
                    logger.warn("Chameleon Weld native per-part hook is incomplete; "+
                            "all Weld remains disabled until the hook is active.");
            }
            catch(ClassNotFoundException missingChameleon)
            {
                logger.info("Chameleon is not installed; native Weld hook skipped.");
            }
            catch(Throwable failure)
            {
                logger.warn("Chameleon Weld hook registration failed: "+
                        failure.toString());
            }
        }

        logger.info("BBS Animation Editor: Pre-initialization started.");
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        System.out.println(
                "[BBS Animation Editor] Initialization complete."
        );

        InterpolationDebug.runTest();
    }
}