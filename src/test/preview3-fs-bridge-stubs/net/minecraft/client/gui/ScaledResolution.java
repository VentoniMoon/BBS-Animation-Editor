package net.minecraft.client.gui;
public class ScaledResolution {
    private final net.minecraft.client.Minecraft mc;
    public ScaledResolution(net.minecraft.client.Minecraft mc){this.mc=mc;}
    public int getScaleFactor(){return 1;}
    public int getScaledWidth(){return mc.displayWidth;}
    public int getScaledHeight(){return mc.displayHeight;}
}