package com.example.examplemod;

import java.util.Locale;

/**
 * View-only BetterLights inspector model. Serialized field paths remain
 * *exactly* those of BetterLightsMorph/ValueSerializer: grouping NEVER
 * alters NBT, native LightCaster settings or animation keys.
 */
public final class BetterLightsPanelModel
{
    public static final int LIGHT=0;
    public static final int SHADOWS=1;
    public static final int FLARE=2;
    public static final int FOG=3;
    public static final String[] TITLES={
            "LIGHT","SHADOWS","FLARE","FOG"};

    private BetterLightsPanelModel(){}

    public static int section(String path)
    {
        if(path==null)return LIGHT;
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.startsWith("shadows.")||
                lower.startsWith("shadow."))return SHADOWS;
        if(lower.startsWith("flare effect.")||
                lower.startsWith("flare."))return FLARE;
        if(lower.startsWith("volumetric fog effect.")||
                lower.startsWith("volumetric fog.")||
                lower.startsWith("fog."))return FOG;
        return LIGHT;
    }

    public static String label(String path)
    {
        if(path==null)return "";
        int at=path.lastIndexOf('.');
        String label=at<0?path:path.substring(at+1);
        return label.replaceAll("([a-z])([A-Z])","$1 $2").trim();
    }

    public static double rangeFraction(double value,double min,double max)
    {
        if(!Double.isFinite(value)||!Double.isFinite(min)||
                !Double.isFinite(max)||max<=min)return .5D;
        return Math.max(0D,Math.min(1D,(value-min)/(max-min)));
    }

    public static double rangedValue(double fraction,double min,double max)
    {
        if(!Double.isFinite(min)||!Double.isFinite(max)||max<=min)
            return Double.NaN;
        return min+Math.max(0D,Math.min(1D,fraction))*(max-min);
    }
}
