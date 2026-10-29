package net.minecraft.client.gui;
public class ScaledResolution {
    private final net.minecraft.client.Minecraft mc;
    public ScaledResolution(net.minecraft.client.Minecraft mc){this.mc=mc;}
    public int getScaleFactor(){return mc.vanillaScaleFactor;}
    public int getScaledWidth(){return (mc.displayWidth+getScaleFactor()-1)/getScaleFactor();}
    public int getScaledHeight(){return (mc.displayHeight+getScaleFactor()-1)/getScaleFactor();}
}