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

        logger.info("BBS Animation Editor: Pre-initialization started.");
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        logger.info("BBS Animation Editor: Initialization complete.");
    }
}