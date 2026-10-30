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

        /*
         * In production Forge reads FMLCorePlugin from the JAR manifest.
         * Older IntelliJ / ForgeGradle run configurations may ignore
         * fml.coreMods.load, which previously left the Curve selector with
         * only five film channels and no actionable explanation.
         *
         * Register a fallback transformer BEFORE OptiFine's Shaders class
         * is first resolved. Never register twice if the core plugin was
         * already loaded early: double bytecode patches would be unsafe.
         */
        if(event.getSide().isClient()&&
                !"false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.shaderCurves","true")))
        {
            try
            {
                if(!CameraShaderCurveCorePlugin.registeredAtLaunch())
                {
                    net.minecraft.launchwrapper.Launch.classLoader
                            .registerTransformer(
                                    "com.example.examplemod."+
                                    "CameraShaderCurveTransformer");
                    logger.warn("CAMERA shader Curve: Forge early core plugin "+
                            "not registered. Installed fallback hook during "+
                            "mod preInit; check IntelliJ run configuration.");
                }
                // Force bytecode transformation before OptiFine uses it.
                // false means do not start Shaders static GL initialization.
                Class.forName("net.optifine.shaders.Shaders",false,
                        net.minecraft.launchwrapper.Launch.classLoader);
                if(!CameraShaderCurveTransformer.installed())
                    logger.warn("CAMERA shader Curve: OptiFine Shaders "+
                            "was not transformed; only film channels are "+
                            "guaranteed to work.");
            }
            catch(ClassNotFoundException optifineAbsent)
            {
                logger.info("CAMERA shader Curve: no OptiFine Shaders class.");
            }
            catch(Throwable shaderHookError)
            {
                logger.warn("CAMERA shader Curve startup hook: "+
                        shaderHookError.toString());
            }
        }

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