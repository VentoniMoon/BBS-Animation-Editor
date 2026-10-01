package com.example.examplemod;

import mchorse.blockbuster.client.gui.GuiActor;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;


/**
 * Bridge между Character Editor и Blockbuster Morph GUI.
 *
 * Поток:
 *
 * CharacterKey
 *      |
 *      v
 * EntityActor copy
 *      |
 *      v
 * GuiActor
 *      |
 *      v
 * Edited Morph
 *      |
 *      +--> CharacterKey
 *      |
 *      +--> Runtime EntityActor
 */
public class BlockbusterCharacterGuiBridge
{

    private final Minecraft mc;


    private AnimationEditorScreen returnScreen;


    /**
     * Реальный актёр в сцене.
     */
    private EntityActor runtimeActor;


    /**
     * Ключ таймлайна.
     */
    private CharacterKey targetKey;



    public BlockbusterCharacterGuiBridge()
    {
        this.mc =
                Minecraft.getMinecraft();
    }



    public BlockbusterCharacterGuiBridge(
            Minecraft mc,
            AnimationEditorScreen screen)
    {
        this.mc =
                mc;

        this.returnScreen =
                screen;
    }





    /*
     * =========================================================
     * OPEN MORPH
     * =========================================================
     */


    public void openMorphEditor(
            BlockbusterSceneActorData actorData,
            EntityActor sourceActor,
            CharacterKey key,
            int frame)
    {

        if(mc == null ||
                sourceActor == null)
        {
            return;
        }



        this.returnScreen =
                mc.currentScreen instanceof AnimationEditorScreen
                        ?
                        (AnimationEditorScreen) mc.currentScreen
                        :
                        this.returnScreen;



        /*
         * Запоминаем настоящий Actor
         */
        this.runtimeActor =
                sourceActor;



        this.targetKey =
                key;



        EntityActor editorActor =
                copyActor(
                        sourceActor
                );



        mc.displayGuiScreen(
                new MorphGuiActor(
                        mc,
                        editorActor,
                        this
                )
        );
    }





    /*
     * =========================================================
     * COPY ACTOR
     * =========================================================
     */


    private EntityActor copyActor(
            EntityActor source)
    {
        EntityActor copy =
                new EntityActor(
                        source.world
                );


        copy.setPosition(
                source.posX,
                source.posY,
                source.posZ
        );


        try
        {
            AbstractMorph morph = null;



            /*
             * Сначала берём Morph из CharacterKey.
             *
             * Timeline является главным источником.
             */
            if(targetKey != null &&
                    targetKey.hasData("Morph"))
            {
                NBTTagCompound morphNBT =
                        targetKey.getCompound(
                                "Morph"
                        );


                if(morphNBT != null &&
                        !morphNBT.hasNoTags())
                {
                    morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            morphNBT.copy()
                                    );


                    System.out.println(
                            "[BBS Animation Editor] Loaded Morph from CharacterKey"
                    );
                }
            }



            /*
             * Если в ключе Morph нет,
             * используем текущий Actor.
             */
            if(morph == null)
            {
                AbstractMorph runtimeMorph =
                        source.morph.get();


                if(runtimeMorph != null)
                {
                    morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            runtimeMorph.toNBT()
                                    );


                    System.out.println(
                            "[BBS Animation Editor] Loaded Morph from runtime Actor"
                    );
                }
            }



            if(morph != null)
            {
                copy.morph.set(
                        morph
                );
            }

        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }


        return copy;
    }





    /*
     * =========================================================
     * SAVE MORPH
     * =========================================================
     */


    private void saveMorph(
            AbstractMorph morph)
    {
        if(morph == null)
        {
            return;
        }


        try
        {
            NBTTagCompound nbt =
                    morph.toNBT();



            /*
             * ==========================================
             * SAVE INTO CHARACTER KEY
             * ==========================================
             */

            if(targetKey != null)
            {
                targetKey.setCompound(
                        "Morph",
                        nbt.copy()
                );

                if (returnScreen != null)
                {
                    returnScreen.refreshCharacterState();
                }

                System.out.println(
                        "[BBS Animation Editor] Morph saved key frame "
                                +
                                targetKey.getFrame()
                );
            }



            /*
             * ==========================================
             * UPDATE RUNTIME ACTOR
             * ==========================================
             */

            if(runtimeActor != null)
            {
                AbstractMorph replacement =
                        MorphManager.INSTANCE
                                .morphFromNBT(
                                        nbt.copy()
                                );


                if(replacement != null)
                {
                    runtimeActor.morph.set(
                            replacement
                    );

                    System.out.println(
                            "[BBS Animation Editor] Runtime morph updated"
                    );
                }
            }



            /*
             * ==========================================
             * FORCE TIMELINE REFRESH
             * ==========================================
             */

            if(returnScreen != null)
            {
                returnScreen.refreshCharacterState();
            }

        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }
    }





    /*
     * =========================================================
     * SKIN COMPATIBILITY
     * =========================================================
     */


    public void openSkinEditor(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            CharacterKey key,
            int frame)
    {
        System.out.println(
                "[BBS Animation Editor] Skin bridge disabled"
        );
    }



    public void syncCustomMorphToSkin(
            CustomMorph customMorph)
    {
        System.out.println(
                "[BBS Animation Editor] Skin save disabled"
        );
    }





    /*
     * =========================================================
     * CHECK
     * =========================================================
     */


    public boolean canOpen(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor)
    {
        return mc != null &&
                runtimeActor != null;
    }





    public CharacterKey getTargetKey()
    {
        return targetKey;
    }





    public void returnToEditor()
    {
        if(mc == null)
        {
            return;
        }



        mc.displayGuiScreen(
                returnScreen
        );
    }





    /*
     * =========================================================
     * GUI WRAPPER
     * =========================================================
     */


    private static class MorphGuiActor
            extends GuiActor
    {


        private final BlockbusterCharacterGuiBridge bridge;


        private final EntityActor actor;



        public MorphGuiActor(
                Minecraft mc,
                EntityActor actor,
                BlockbusterCharacterGuiBridge bridge)
        {
            super(
                    mc,
                    actor
            );


            this.actor =
                    actor;


            this.bridge =
                    bridge;
        }





        @Override
        public void closeScreen()
        {

            System.out.println(
                    "[BBS Animation Editor] Closing Morph editor"
            );


            try
            {
                AbstractMorph morph =
                        actor.morph.get();



                if(morph != null)
                {
                    System.out.println(
                            "[BBS Animation Editor] Saving morph "
                                    +
                                    morph.getClass().getName()
                    );


                    bridge.saveMorph(
                            morph
                    );
                }

            }
            catch(Throwable error)
            {
                error.printStackTrace();
            }



            super.closeScreen();



            bridge.returnToEditor();
        }
    }
}