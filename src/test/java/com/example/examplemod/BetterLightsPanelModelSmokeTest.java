package com.example.examplemod;

public final class BetterLightsPanelModelSmokeTest
{
    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args)
    {
        check(BetterLightsPanelModel.section("Enabled")==
                BetterLightsPanelModel.LIGHT,"root enabled");
        check(BetterLightsPanelModel.section("DirectionX")==
                BetterLightsPanelModel.LIGHT,"root direction");
        check(BetterLightsPanelModel.section("Shadows.Enabled")==
                BetterLightsPanelModel.SHADOWS,"nested shadows");
        check(BetterLightsPanelModel.section("Shadows.Fov offset")==
                BetterLightsPanelModel.SHADOWS,"shadow parameters");
        check(BetterLightsPanelModel.section("Flare Effect.Enabled")==
                BetterLightsPanelModel.FLARE,"nested flare");
        check(BetterLightsPanelModel.section("Flare Effect.Color")==
                BetterLightsPanelModel.FLARE,"flare color");
        check(BetterLightsPanelModel.section("Volumetric Fog Effect.Density")==
                BetterLightsPanelModel.FOG,"fog density");
        check(BetterLightsPanelModel.section(
                "Volumetric Fog Effect.Scattering Albedo")==
                BetterLightsPanelModel.FOG,"fog albedo");
        check("Direction X".equals(
                BetterLightsPanelModel.label("DirectionX")),"camel label");
        check("Density".equals(
                BetterLightsPanelModel.label("Volumetric Fog Effect.Density")),
                "nested label");
        check(BetterLightsPanelModel.rangeFraction(5,0,10)==.5D,
                "slider midpoint");
        check(BetterLightsPanelModel.rangedValue(.25D,-180,180)==-90D,
                "negative slider offset");
        check(BetterLightsPanelModel.rangedValue(-10D,0,90)==0D,
                "range clamps low");
        check(BetterLightsPanelModel.rangedValue(10D,0,90)==90D,
                "range clamps high");
        check(BetterLightsPanelModel.rangeFraction(500,0,90)==1D,
                "thumb clamps to max");
        check(Double.isNaN(BetterLightsPanelModel.rangedValue(
                .5D,Double.NEGATIVE_INFINITY,100)),
                "unbounded values must use direct number input");
        System.out.println("BetterLights sections and sliders PASS");
    }
}