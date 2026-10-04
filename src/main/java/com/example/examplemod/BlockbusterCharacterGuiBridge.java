package com.example.examplemod;

import mchorse.blockbuster.client.gui.GuiActor;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;


/**
 * Bridge между Character Editor и оригинальными
 * Blockbuster / Metamorph GUI.
 *
 * CharacterKey является источником истины.
 *
 *
 * MORPH:
 *
 * CharacterKey
 *      |
 *      v
 * EntityActor COPY
 *      |
 *      v
 * GuiActor
 *      |
 *      v
 * edited Morph
 *      |
 *      v
 * ТОТ ЖЕ CharacterKey
 *
 *
 * SKIN / EDIT:
 *
 * CharacterKey
 *      |
 *      v
 * Morph NBT
 *      |
 *      v
 * MorphManager
 *      |
 *      v
 * AbstractMorph
 *      |
 *      v
 * оригинальный Metamorph editor
 *      |
 *      +-- CustomMorph -> GuiCustomMorph
 *      |
 *      +-- AnimatedMorph -> GuiAnimatedMorph
 *      |
 *      +-- Emoticons -> GuiEmoticonsMorph
 *      |
 *      v
 * edited Morph
 *      |
 *      v
 * ТОТ ЖЕ CharacterKey
 */
public class BlockbusterCharacterGuiBridge
{
    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    private final Minecraft mc;

    private AnimationEditorScreen returnScreen;

    /**
     * Runtime Actor, используемый Preview.
     */
    private EntityActor runtimeActor;

    /**
     * Actor Data текущей сцены.
     */
    private BlockbusterSceneActorData actorData;

    /**
     * Конкретный CharacterKey, который сейчас редактируется.
     *
     * Если пользователь выбрал key @ 40,
     * здесь должен находиться именно этот объект.
     */
    private CharacterKey targetKey;

    /**
     * Fallback frame.
     *
     * Используется только если пользователь
     * не выбрал существующий CharacterKey.
     */
    private int targetFrame;

    /** Body Part model currently edited from Character Timeline. */
    private BodyPartModelData targetBodyPartModel;
    private int targetBodyPartFrame;


    /*
     * =========================================================
     * CONSTRUCTORS
     * =========================================================
     */

    public BlockbusterCharacterGuiBridge()
    {
        this.mc =
                Minecraft.getMinecraft();

        this.returnScreen =
                null;

        this.runtimeActor =
                null;

        this.actorData =
                null;

        this.targetKey =
                null;

        this.targetFrame =
                0;
    }


    public BlockbusterCharacterGuiBridge(
            Minecraft mc,
            AnimationEditorScreen screen)
    {
        this.mc =
                mc;

        this.returnScreen =
                screen;

        this.runtimeActor =
                null;

        this.actorData =
                null;

        this.targetKey =
                null;

        this.targetFrame =
                0;
    }


    /*
     * =========================================================
     * MORPH EDITOR
     * =========================================================
     */

    public void openMorphEditor(
            BlockbusterSceneActorData actorData,
            EntityActor sourceActor,
            CharacterKey selectedKey,
            int frame)
    {
        if (this.mc == null ||
                actorData == null ||
                sourceActor == null)
        {
            return;
        }


        /*
         * Возвращаемся именно в AnimationEditorScreen,
         * из которого был открыт GUI.
         */
        if (this.mc.currentScreen
                instanceof AnimationEditorScreen)
        {
            this.returnScreen =
                    (AnimationEditorScreen)
                            this.mc.currentScreen;
        }


        this.actorData =
                actorData;

        this.runtimeActor =
                sourceActor;

        this.targetFrame =
                Math.max(
                        0,
                        frame
                );


        /*
         * Если выбран конкретный key,
         * используем именно его.
         *
         * НЕ создаём новый key.
         */
        this.targetKey =
                resolveTargetKey(
                        actorData,
                        selectedKey,
                        CharacterKey.Type.MORPH,
                        this.targetFrame
                );

        System.out.println(
                "[BBS Animation Editor] Morph target = "
                        + (
                        this.targetKey == null
                                ? "NONE"
                                : this.targetKey.getType()
                                + " @ "
                                + this.targetKey.getFrame()
                )
        );


        /*
         * Создаём копию Actor только для
         * оригинального Blockbuster GUI.
         */
        EntityActor editorActor =
                copyActorForMorphEditor(
                        sourceActor
                );


        if (editorActor == null)
        {
            return;
        }


        this.mc.displayGuiScreen(
                new MorphGuiActor(
                        this.mc,
                        editorActor,
                        this
                )
        );
    }


    public void openBodyPartMorphEditor(
            BodyPartModelData bodyPart,
            int frame)
    {
        if (this.mc == null || bodyPart == null || !bodyPart.hasModel())
        {
            return;
        }

        if (this.mc.currentScreen instanceof AnimationEditorScreen)
        {
            this.returnScreen = (AnimationEditorScreen) this.mc.currentScreen;
        }

        this.targetBodyPartModel = bodyPart;
        this.targetBodyPartFrame = Math.max(0, frame);
        this.targetKey = null;
        this.targetFrame = this.targetBodyPartFrame;

        EntityActor editorActor = new EntityActor(this.mc.world);
        editorActor.setPosition(0, 0, 0);

        try
        {
            AbstractMorph source = bodyPart.getMorphAt(this.targetBodyPartFrame);

            if (source == null)
            {
                String modelName = bodyPart.getModelNameAt(this.targetBodyPartFrame);
                CustomMorph custom = new CustomMorph();
                custom.name = "blockbuster." + modelName;
                custom.updateModel(true);
                if (custom.model == null)
                {
                    return;
                }
                source = custom;
            }

            editorActor.morph.set(source.copy());
            this.runtimeActor = editorActor;
            this.mc.displayGuiScreen(new MorphGuiActor(this.mc, editorActor, this));
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }

    /*
     * =========================================================
     * RESOLVE TARGET KEY
     * =========================================================
     */

    private CharacterKey resolveTargetKey(
            BlockbusterSceneActorData actorData,
            CharacterKey selectedKey,
            CharacterKey.Type desiredType,
            int frame)
    {
        if (actorData == null)
        {
            System.out.println(
                    "[BBS Animation Editor] " +
                            "resolveTargetKey: actorData == null"
            );

            return null;
        }

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            System.out.println(
                    "[BBS Animation Editor] " +
                            "resolveTargetKey: timeline == null"
            );

            return null;
        }


        /*
         * =========================================================
         * 1. EXPLICITLY SELECTED CHARACTER KEY
         * =========================================================
         *
         * Если пользователь явно выбрал CharacterKey,
         * ВСЕГДА используем именно этот объект.
         *
         * Тип существующего ключа здесь НЕ имеет значения.
         *
         * Например:
         *
         * selectedKey = SKIN @ 40
         * desiredType = MORPH
         *
         * Всё равно возвращаем SKIN @ 40.
         *
         * Позже CharacterKey.setMorph() превратит
         * этот же объект в MORPH @ 40.
         *
         * Никакого нового ключа не создаётся.
         */

        if (selectedKey != null)
        {
            boolean belongsToTimeline = false;

            for (CharacterTrack track :
                    timeline.getTracks())
            {
                if (track == null)
                {
                    continue;
                }

                for (CharacterKey key :
                        track.getKeys())
                {
                    /*
                     * Нам нужна именно ссылка на объект.
                     *
                     * Не frame/type.
                     */
                    if (key == selectedKey)
                    {
                        belongsToTimeline = true;
                        break;
                    }
                }

                if (belongsToTimeline)
                {
                    break;
                }
            }

            if (belongsToTimeline)
            {
                System.out.println(
                        "[BBS Animation Editor] " +
                                "Using explicitly selected CharacterKey: "
                                + selectedKey.getType()
                                + " @ "
                                + selectedKey.getFrame()
                                + " for "
                                + desiredType
                );

                return selectedKey;
            }

            /*
             * Переданный selectedKey больше не находится
             * в Timeline текущего Actor.
             *
             * Не используем старую ссылку.
             */
            System.out.println(
                    "[BBS Animation Editor] " +
                            "Selected CharacterKey is not part " +
                            "of current Actor Timeline."
            );
        }


        /*
         * =========================================================
         * 2. EXISTING KEY AT CURRENT FRAME
         * =========================================================
         *
         * Если пользователь ничего не выбрал,
         * ищем существующий подходящий key
         * непосредственно на currentFrame.
         *
         * НИЧЕГО НЕ СОЗДАЁМ.
         */

        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }

            CharacterKey key =
                    track.getKeyAtFrame(frame);

            if (key == null)
            {
                continue;
            }

            if (key.getType() != desiredType)
            {
                continue;
            }

            System.out.println(
                    "[BBS Animation Editor] " +
                            "Using existing "
                            + desiredType
                            + " key @ "
                            + frame
            );

            return key;
        }


        /*
         * =========================================================
         * 3. NOTHING FOUND
         * =========================================================
         *
         * Bridge НЕ создаёт CharacterKey.
         *
         * Если пользователь не выбрал ключ и подходящего
         * существующего ключа на currentFrame нет,
         * возвращаем null.
         */

        System.out.println(
                "[BBS Animation Editor] " +
                        "No existing "
                        + desiredType
                        + " key found at frame "
                        + frame
                        + ". No CharacterKey will be created."
        );

        return null;
    }



    /*
     * =========================================================
     * COPY ACTOR FOR MORPH EDITOR
     * =========================================================
     */

    private EntityActor copyActorForMorphEditor(
            EntityActor source)
    {
        if (source == null)
        {
            return null;
        }


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
            AbstractMorph morph =
                    null;


            /*
             * CharacterKey является главным источником.
             */
            if (this.targetKey != null &&
                    this.targetKey.hasMorph())
            {
                NBTTagCompound morphNBT =
                        this.targetKey.getMorph();


                if (morphNBT != null &&
                        !morphNBT.hasNoTags())
                {
                    morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            morphNBT.copy()
                                    );
                }
            }


            /*
             * Если ключ ещё пустой,
             * используем текущий Runtime Actor.
             */
            if (morph == null)
            {
                AbstractMorph runtimeMorph =
                        source.morph.get();


                if (runtimeMorph != null)
                {
                    morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            runtimeMorph.toNBT()
                                    );
                }
            }


            if (morph != null)
            {
                copy.morph.set(
                        morph
                );
            }
        }
        catch (Throwable error)
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
        if (morph == null)
        {
            return;
        }

        if (this.targetBodyPartModel != null)
        {
            saveBodyPartMorph(morph);
            return;
        }

        if (this.targetKey == null)
        {
            return;
        }


        try
        {
            NBTTagCompound morphNBT =
                    morph.toNBT();


            if (morphNBT == null ||
                    morphNBT.hasNoTags())
            {
                return;
            }


            /*
             * Главное сохранение.
             *
             * Никакого нового CharacterKey здесь
             * не создаётся.
             */
            this.targetKey.setMorph(
                    morphNBT.copy()
            );


            System.out.println(
                    "[BBS Animation Editor] " +
                            "Morph saved to CharacterKey @ " +
                            this.targetKey.getFrame()
            );


            /*
             * Runtime Actor обновляем только для
             * немедленного отображения результата.
             */
            if (this.runtimeActor != null)
            {
                AbstractMorph replacement =
                        MorphManager.INSTANCE
                                .morphFromNBT(
                                        morphNBT.copy()
                                );


                if (replacement != null)
                {
                    this.runtimeActor.morph.set(
                            replacement
                    );
                }
            }


            /*
             * Перечитываем Character state.
             */
            if (this.returnScreen != null)
            {
                this.returnScreen.refreshCharacterState();
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    private void saveBodyPartMorph(AbstractMorph morph)
    {
        if (this.targetBodyPartModel == null || morph == null)
        {
            return;
        }

        try
        {
            if (morph instanceof CustomMorph)
            {
                CustomMorph custom = (CustomMorph) morph;
                String modelName = custom.getKey();

                if (modelName != null && modelName.length() > 0)
                {
                    this.targetBodyPartModel.setModelKey(
                            this.targetBodyPartFrame,
                            modelName
                    );
                }
            }
            else
            {
                this.targetBodyPartModel.setMorphKey(
                        this.targetBodyPartFrame,
                        morph
                );
            }

            if (this.returnScreen != null)
            {
                this.returnScreen.refreshCharacterState();
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }

        this.targetBodyPartModel = null;
        this.targetBodyPartFrame = 0;
    }

    public void openBodyPartSkinEditor(
            BodyPartModelData bodyPart,
            int frame)
    {
        if (this.mc == null || bodyPart == null || !bodyPart.hasModel())
        {
            return;
        }

        if (this.mc.currentScreen instanceof AnimationEditorScreen)
        {
            this.returnScreen = (AnimationEditorScreen) this.mc.currentScreen;
        }

        this.targetBodyPartModel = bodyPart;
        this.targetBodyPartFrame = Math.max(0, frame);
        this.targetKey = null;
        this.targetFrame = this.targetBodyPartFrame;

        try
        {
            CharacterKey stateKey =
                    bodyPart.getOrCreateCharacterStateKey(
                            this.targetBodyPartFrame
                    );

            AbstractMorph sourceMorph = null;

            if (stateKey.hasSkin())
            {
                NBTTagCompound skin = stateKey.getSkin();
                if (skin != null && !skin.hasNoTags())
                {
                    sourceMorph =
                            MorphManager.INSTANCE.morphFromNBT(
                                    skin.copy()
                            );
                }
            }

            if (sourceMorph == null)
            {
                String modelName =
                        bodyPart.getModelNameAt(this.targetBodyPartFrame);

                CustomMorph morph = new CustomMorph();
                morph.name = "blockbuster." + modelName;
                morph.updateModel(true);

                if (morph.model == null)
                {
                    return;
                }

                sourceMorph = morph;
            }

            this.mc.displayGuiScreen(
                    new PlayerSkinEditorScreen(
                            this.mc,
                            sourceMorph,
                            this
                    )
            );
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * SKIN / EDITOR
     * =========================================================
     */

    public void openSkinEditor(
            BlockbusterSceneActorData actorData,
            EntityActor sourceActor,
            CharacterKey selectedKey,
            int frame)
    {
        if (this.mc == null ||
                actorData == null ||
                sourceActor == null)
        {
            return;
        }


        if (this.mc.currentScreen
                instanceof AnimationEditorScreen)
        {
            this.returnScreen =
                    (AnimationEditorScreen)
                            this.mc.currentScreen;
        }


        this.actorData =
                actorData;

        this.runtimeActor =
                sourceActor;

        this.targetFrame =
                Math.max(
                        0,
                        frame
                );


        /*
         * Для Skin действует тот же принцип:
         *
         * выбранный CharacterKey имеет абсолютный
         * приоритет.
         */
        this.targetKey =
                resolveTargetKey(
                        actorData,
                        selectedKey,
                        CharacterKey.Type.SKIN,
                        this.targetFrame
                );

        System.out.println(
                "[BBS Animation Editor] Skin target = "
                        + (
                        this.targetKey == null
                                ? "NONE"
                                : this.targetKey.getType()
                                + " @ "
                                + this.targetKey.getFrame()
                )
        );


        /*
         * Получаем Morph, который должен редактироваться.
         *
         * Приоритет:
         *
         * 1. Skin текущего key
         * 2. Morph текущего key
         * 3. Runtime Actor
         */
        AbstractMorph sourceMorph =
                createMorphForSkinEditor(
                        sourceActor
                );


        if (sourceMorph == null)
        {
            System.err.println(
                    "[BBS Animation Editor] " +
                            "Cannot open Skin editor: " +
                            "source Morph is null"
            );

            return;
        }


        System.out.println(
                "[BBS Animation Editor] " +
                        "Opening original editor for: " +
                        sourceMorph.getClass().getName()
        );


        /*
         * Теперь PlayerSkinEditorScreen НЕ знает,
         * является Morph CustomMorph или AnimatedMorph.
         *
         * Он сам через MorphManager найдёт
         * правильный оригинальный редактор.
         */
        PlayerSkinEditorScreen screen =
                new PlayerSkinEditorScreen(
                        this.mc,
                        sourceMorph,
                        this
                );


        this.mc.displayGuiScreen(
                screen
        );
    }


    /*
     * =========================================================
     * CREATE MORPH FOR SKIN EDITOR
     * =========================================================
     */

    private AbstractMorph createMorphForSkinEditor(
            EntityActor sourceActor)
    {
        try
        {
            /*
             * -------------------------------------------------
             * 1. SKIN DATA
             * -------------------------------------------------
             */
            if (this.targetKey != null &&
                    this.targetKey.hasSkin())
            {
                NBTTagCompound skinNBT =
                        this.targetKey.getSkin();


                if (skinNBT != null &&
                        !skinNBT.hasNoTags())
                {
                    AbstractMorph morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            skinNBT.copy()
                                    );


                    if (morph != null)
                    {
                        return morph;
                    }
                }
            }


            /*
             * -------------------------------------------------
             * 2. MORPH DATA
             * -------------------------------------------------
             *
             * Это особенно важно для существующего
             * Morph key, когда пользователь нажимает Skin/Edit.
             */
            if (this.targetKey != null &&
                    this.targetKey.hasMorph())
            {
                NBTTagCompound morphNBT =
                        this.targetKey.getMorph();


                if (morphNBT != null &&
                        !morphNBT.hasNoTags())
                {
                    AbstractMorph morph =
                            MorphManager.INSTANCE
                                    .morphFromNBT(
                                            morphNBT.copy()
                                    );


                    if (morph != null)
                    {
                        return morph;
                    }
                }
            }


            /*
             * -------------------------------------------------
             * 3. RUNTIME MORPH
             * -------------------------------------------------
             */
            if (sourceActor != null)
            {
                AbstractMorph runtimeMorph =
                        sourceActor.morph.get();


                if (runtimeMorph != null)
                {
                    NBTTagCompound runtimeNBT =
                            runtimeMorph.toNBT();


                    if (runtimeNBT != null &&
                            !runtimeNBT.hasNoTags())
                    {
                        return MorphManager.INSTANCE
                                .morphFromNBT(
                                        runtimeNBT.copy()
                                );
                    }
                }
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }


        return null;
    }


    /*
     * =========================================================
     * SAVE UNIVERSAL MORPH EDITOR
     * =========================================================
     *
     * Вызывается PlayerSkinEditorScreen после
     * editor.finishEdit().
     *
     * Это общий путь и для:
     *
     * CustomMorph
     * AnimatedMorph
     * GuiEmoticonsMorph
     * и других зарегистрированных Morph editors.
     */
    public void syncMorphEditorResult(
            AbstractMorph edited)
    {
        if (edited == null)
        {
            return;
        }


        if (this.targetBodyPartModel != null)
        {
            saveBodyPartSkin(edited);
            return;
        }

        if (this.targetKey == null)
        {
            System.err.println(
                    "[BBS Animation Editor] " +
                            "Cannot save edited Morph: " +
                            "target CharacterKey is null"
            );

            return;
        }


        try
        {
            NBTTagCompound editedNBT =
                    edited.toNBT();

            System.out.println(
                    "[BBS Animation Editor] SKIN RESULT NBT = " +
                            editedNBT
            );

            System.out.println(
                    "[BBS Animation Editor] TARGET KEY BEFORE SAVE = " +
                            this.targetKey.getType() +
                            " @ " +
                            this.targetKey.getFrame()
            );


            if (editedNBT == null ||
                    editedNBT.hasNoTags())
            {
                System.err.println(
                        "[BBS Animation Editor] " +
                                "Cannot save edited Morph: " +
                                "NBT is empty"
                );

                return;
            }


            /*
             * Skin/Edit всегда сохраняется в тот же
             * CharacterKey.
             *
             * setSkin() меняет тип ключа на SKIN.
             *
             * Никакого нового ключа не создаётся.
             */
            this.targetKey.setSkin(
                    editedNBT.copy()
            );

            System.out.println(
                    "[BBS Animation Editor] TARGET KEY AFTER SAVE = " +
                            this.targetKey.getType() +
                            " @ " +
                            this.targetKey.getFrame()
            );

            System.out.println(
                    "[BBS Animation Editor] SAVED SKIN NBT = " +
                            this.targetKey.getSkin()
            );


            System.out.println(
                    "[BBS Animation Editor] " +
                            "Edited Morph saved to CharacterKey @ " +
                            this.targetKey.getFrame() +
                            " : " +
                            edited.getClass().getName()
            );


            /*
             * Runtime Actor обновляем только для Preview.
             */
            if (this.runtimeActor != null)
            {
                AbstractMorph replacement =
                        MorphManager.INSTANCE
                                .morphFromNBT(
                                        editedNBT.copy()
                                );


                if (replacement != null)
                {
                    this.runtimeActor.morph.set(
                            replacement
                    );
                }
            }


            /*
             * Пересобираем Character state.
             */
            if (this.returnScreen != null)
            {
                System.out.println(
                        "[BBS Animation Editor] SKIN RESULT NBT = " +
                                editedNBT
                );

                System.out.println(
                        "[BBS Animation Editor] TARGET KEY BEFORE SAVE = " +
                                this.targetKey.getType() +
                                " @ " +
                                this.targetKey.getFrame()
                );

                this.returnScreen.refreshCharacterState();
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    private void saveBodyPartSkin(AbstractMorph edited)
    {
        if (this.targetBodyPartModel == null || edited == null)
        {
            return;
        }

        try
        {
            NBTTagCompound nbt = edited.toNBT();

            if (nbt == null || nbt.hasNoTags())
            {
                return;
            }

            CharacterKey key =
                    this.targetBodyPartModel.getOrCreateCharacterStateKey(
                            this.targetBodyPartFrame
                    );

            key.setSkin(nbt.copy());

            if (this.returnScreen != null)
            {
                this.returnScreen.refreshCharacterState();
            }

            this.targetBodyPartModel = null;
            this.targetBodyPartFrame = 0;
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * LEGACY COMPATIBILITY
     * =========================================================
     *
     * Оставляем метод, чтобы старые места проекта,
     * если они ещё используют его, не ломались.
     *
     * Новый универсальный редактор этим методом
     * больше не пользуется.
     */
    public void syncCustomMorphToSkin(
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph)
    {
        if (customMorph == null)
        {
            return;
        }


        syncMorphEditorResult(
                customMorph
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
        return this.mc != null &&
                actorData != null &&
                runtimeActor != null;
    }


    /*
     * =========================================================
     * ACCESS
     * =========================================================
     */

    public CharacterKey getTargetKey()
    {
        return this.targetKey;
    }


    public int getTargetFrame()
    {
        return this.targetFrame;
    }


    /*
     * =========================================================
     * RETURN
     * =========================================================
     */

    public void returnToEditor()
    {
        if (this.mc == null)
        {
            return;
        }


        this.mc.displayGuiScreen(
                this.returnScreen
        );
    }


    /*
     * =========================================================
     * MORPH GUI WRAPPER
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
            try
            {
                /*
                 * ВАЖНО:
                 *
                 * GuiActor изменяет выбранный Morph
                 * через renderer.morph.
                 */
                AbstractMorph morph =
                        this.renderer.morph;


                if (morph != null)
                {
                    System.out.println(
                            "[BBS Animation Editor] " +
                                    "Saving edited Morph: " +
                                    morph.getClass().getName()
                    );


                    this.bridge.saveMorph(
                            morph
                    );
                }
            }
            catch (Throwable error)
            {
                error.printStackTrace();
            }


            /*
             * Blockbuster завершает собственный GUI.
             */
            super.closeScreen();


            /*
             * Возвращаемся в BBS Animation Editor.
             */
            this.bridge.returnToEditor();
        }
    }
}