package com.example.examplemod;

import net.minecraft.entity.Entity;
import net.optifine.shaders.Shaders;

/** Tests the actual reflective bridge, not the native actor pass's stub. */
public final class Preview3OptiFineEntityStageSmokeTest
{
    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        Entity morph=new Entity();
        BbsFsOptiFineEntityStage.beforeEntity(morph);
        check(Shaders.entityCount==0,"outside-stage metadata leaked");

        try (BbsFsOptiFineEntityStage ignored =
                     BbsFsOptiFineEntityStage.open())
        {
            check(Shaders.activeProgram==Shaders.entities,
                    "did not enter entity shader program");
            BbsFsOptiFineEntityStage.beforeEntity(morph);
            check(Shaders.entityCount==1,"missing entity shader metadata");
        }
        check(Shaders.activeProgram==Shaders.prior,
                "OptiFine cached shader program was not restored");
        check(Shaders.beginCount==1 && Shaders.endCount==1 &&
                Shaders.restoreCount==1,"incorrect shader program lifecycle");
        BbsFsOptiFineEntityStage.beforeEntity(morph);
        check(Shaders.entityCount==1,"entity metadata leaked after close");

        Shaders.isRenderingWorld=false;
        try (BbsFsOptiFineEntityStage ignored =
                     BbsFsOptiFineEntityStage.open())
        {
            BbsFsOptiFineEntityStage.beforeEntity(morph);
        }
        check(Shaders.beginCount==1 && Shaders.entityCount==1,
                "shader stage ran outside native world render");
        System.out.println("Preview FS OptiFine reflective entity shader state PASS");
    }
}
