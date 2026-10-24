package net.minecraft.client;

import java.io.File;

/** Isolated WAV monitor smoke stub, not part of production Minecraft. */
public final class Minecraft
{
    public File mcDataDir;
    private static Minecraft instance;

    public Minecraft(File dir){mcDataDir=dir;instance=this;}
    public static Minecraft getMinecraft(){return instance;}
}
