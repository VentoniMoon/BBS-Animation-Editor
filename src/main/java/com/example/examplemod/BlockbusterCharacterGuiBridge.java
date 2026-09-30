package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.client.gui.GuiActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Bridge между Character Editor и оригинальными
 * Blockbuster GUI редакторами Morph / Skin.
 *
 * Morph:
 * CharacterKey -> GuiActor -> renderer.morph -> CharacterKey
 *
 * Skin:
 * CharacterKey -> CustomMorph -> GuiCustomMorph
 *             -> CustomMorph -> CharacterKey
 */
public class BlockbusterCharacterGuiBridge
{
    private final Minecraft mc;

    private AnimationEditorScreen returnScreen;

    private BlockbusterSceneActorData currentActorData;

    /**
     * Конкретный выбранный ключ.
     *
     * Если пользователь выбрал MORPH/SKIN key,
     * именно этот объект должен изменяться.
     */
    private CharacterKey targetKey;

    /**
     * Если конкретного ключа нет, используется этот frame.
     */
    private int targetFrame;

    private CharacterKey.Type openedEditorType;

    public BlockbusterCharacterGuiBridge()
    {
        this.mc = Minecraft.getMinecraft();

        this.returnScreen = null;

        this.targetFrame = 0;

        this.currentActorData = null;

        this.targetKey = null;

        this.openedEditorType = null;
    }

    public BlockbusterCharacterGuiBridge(
            Minecraft mc,
            AnimationEditorScreen returnScreen)
    {
        this.mc = mc;

        this.returnScreen = returnScreen;

        this.targetFrame = 0;

        this.currentActorData = null;

        this.targetKey = null;

        this.openedEditorType = null;
    }


    /*
     * =========================================================
     * MORPH
     * =========================================================
     */

    public void openMorphEditor(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            CharacterKey key,
            int frame)
    {
        if (this.mc == null ||
                actorData == null ||
                runtimeActor == null)
        {
            return;
        }

        this.returnScreen =
                this.mc.currentScreen instanceof AnimationEditorScreen
                        ? (AnimationEditorScreen) this.mc.currentScreen
                        : null;

        this.currentActorData = actorData;

        if (key != null &&
                key.getType() == CharacterKey.Type.MORPH)
        {
            this.targetKey = key;
            this.targetFrame = Math.max(0, key.getFrame());
        }
        else
        {
            this.targetKey = null;
            this.targetFrame = Math.max(0, frame);
        }

        /*
         * Перед открытием Blockbuster GUI выставляем
         * Morph соответствующего ключа.
         */
        AbstractMorph morph =
                getTargetMorph(
                        actorData,
                        this.targetKey,
                        runtimeActor
                );

        if (morph != null)
        {
            try
            {
                runtimeActor.morph.set(morph);
            }
            catch (Throwable error)
            {
                error.printStackTrace();
            }
        }

        this.openedEditorType =
                CharacterKey.Type.MORPH;

        this.mc.displayGuiScreen(
                new SceneGuiActor(
                        this.mc,
                        runtimeActor,
                        this
                )
        );
    }


    /*
     * =========================================================
     * SKIN
     * =========================================================
     */

    public void openSkinEditor(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            CharacterKey key,
            int frame)
    {
        if (this.mc == null ||
                actorData == null)
        {
            return;
        }

        this.returnScreen =
                this.mc.currentScreen instanceof AnimationEditorScreen
                        ? (AnimationEditorScreen) this.mc.currentScreen
                        : null;

        this.currentActorData = actorData;

        if (key != null &&
                key.getType() == CharacterKey.Type.SKIN)
        {
            this.targetKey = key;
            this.targetFrame = Math.max(0, key.getFrame());
        }
        else
        {
            this.targetKey = null;
            this.targetFrame = Math.max(0, frame);
        }

        CharacterKey sourceKey =
                this.targetKey;

        /*
         * Если конкретный SKIN key не выбран,
         * берём последний SKIN до текущего кадра.
         */
        if (sourceKey == null)
        {
            sourceKey =
                    findLatestKeyAtOrBeforeFrame(
                            actorData,
                            CharacterKey.Type.SKIN,
                            this.targetFrame
                    );
        }

        /*
         * Если SKIN отсутствует, пробуем MORPH.
         *
         * Это нужно для AnimatedMorph/Emoticons,
         * внутри которых может находиться CustomMorph.
         */
        if (sourceKey == null)
        {
            sourceKey =
                    findLatestKeyAtOrBeforeFrame(
                            actorData,
                            CharacterKey.Type.MORPH,
                            this.targetFrame
                    );
        }

        CustomMorph customMorph =
                createCustomMorph(
                        sourceKey,
                        runtimeActor
                );

        if (customMorph == null)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Unable to create CustomMorph for Skin Editor."
            );

            return;
        }

        /*
         * GuiCustomMorph.startEdit() требует
         * customMorph.model != null.
         */
        try
        {
            customMorph.updateModel(true);
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }

        if (customMorph.model == null)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "CustomMorph has no Blockbuster model."
            );

            return;
        }

        this.openedEditorType =
                CharacterKey.Type.SKIN;

        this.mc.displayGuiScreen(
                new PlayerSkinEditorScreen(
                        this.mc,
                        customMorph,
                        this
                )
        );
    }


    /*
     * =========================================================
     * CREATE CUSTOM MORPH
     * =========================================================
     */

    private CustomMorph createCustomMorph(
            CharacterKey sourceKey,
            EntityActor runtimeActor)
    {
        /*
         * 1. CharacterKey
         */
        if (sourceKey != null)
        {
            CustomMorph result =
                    createCustomMorphFromKey(sourceKey);

            if (result != null)
            {
                return result;
            }
        }

        /*
         * 2. Runtime actor
         */
        if (runtimeActor != null)
        {
            try
            {
                AbstractMorph morph =
                        runtimeActor.morph.get();

                CustomMorph result =
                        extractCustomMorph(morph);

                result =
                        copyAndPrepareCustomMorph(result);

                if (result != null)
                {
                    return result;
                }
            }
            catch (Throwable error)
            {
                error.printStackTrace();
            }
        }

        return null;
    }


    private CustomMorph createCustomMorphFromKey(
            CharacterKey key)
    {
        NBTTagCompound morphNBT =
                getMorphNBTFromKey(key);

        if (morphNBT == null ||
                morphNBT.hasNoTags())
        {
            return null;
        }

        AbstractMorph morph =
                createMorphFromNBT(morphNBT);

        CustomMorph customMorph =
                extractCustomMorph(morph);

        return copyAndPrepareCustomMorph(
                customMorph
        );
    }


    private CustomMorph copyAndPrepareCustomMorph(
            CustomMorph source)
    {
        if (source == null)
        {
            return null;
        }

        try
        {
            CustomMorph copy =
                    new CustomMorph();

            copy.copy(source);

            /*
             * В CustomMorph.copy() model тоже копируется.
             *
             * Дополнительно обновляем его по key.
             */
            copy.updateModel(true);

            if (copy.model == null)
            {
                return null;
            }

            return copy;
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Failed to prepare CustomMorph."
            );

            error.printStackTrace();

            return null;
        }
    }


    private AbstractMorph createMorphFromNBT(
            NBTTagCompound nbt)
    {
        if (nbt == null)
        {
            return null;
        }

        try
        {
            return MorphManager.INSTANCE.morphFromNBT(
                    nbt.copy()
            );
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Failed to create Morph from NBT."
            );

            error.printStackTrace();

            return null;
        }
    }


    /*
     * =========================================================
     * CUSTOM MORPH EXTRACTION
     * =========================================================
     */

    private CustomMorph extractCustomMorph(
            AbstractMorph morph)
    {
        if (morph == null)
        {
            return null;
        }

        if (morph instanceof CustomMorph)
        {
            return (CustomMorph) morph;
        }

        /*
         * Emoticons / AnimatedMorph.
         *
         * Не импортируем класс Emoticons напрямую.
         */
        try
        {
            Field placeholderField =
                    findField(
                            morph.getClass(),
                            "placeholder"
                    );

            if (placeholderField == null)
            {
                return null;
            }

            placeholderField.setAccessible(true);

            Object placeholder =
                    placeholderField.get(morph);

            return extractCustomMorphFromObject(
                    placeholder
            );
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }


    private CustomMorph extractCustomMorphFromObject(
            Object object)
    {
        if (object == null)
        {
            return null;
        }

        if (object instanceof CustomMorph)
        {
            return (CustomMorph) object;
        }

        /*
         * Optional-like объект.
         */
        try
        {
            Method isPresent =
                    findMethod(
                            object.getClass(),
                            "isPresent"
                    );

            if (isPresent != null)
            {
                Object value =
                        isPresent.invoke(object);

                if (value instanceof Boolean &&
                        !((Boolean) value).booleanValue())
                {
                    return null;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        try
        {
            Method isEmpty =
                    findMethod(
                            object.getClass(),
                            "isEmpty"
                    );

            if (isEmpty != null)
            {
                Object value =
                        isEmpty.invoke(object);

                if (value instanceof Boolean &&
                        ((Boolean) value).booleanValue())
                {
                    return null;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        try
        {
            Method get =
                    findMethod(
                            object.getClass(),
                            "get"
                    );

            if (get != null)
            {
                Object value =
                        get.invoke(object);

                if (value instanceof CustomMorph)
                {
                    return (CustomMorph) value;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return null;
    }


    /*
     * =========================================================
     * MORPH TARGET
     * =========================================================
     */

    private AbstractMorph getTargetMorph(
            BlockbusterSceneActorData actorData,
            CharacterKey key,
            EntityActor runtimeActor)
    {
        /*
         * 1. Конкретный выбранный key.
         */
        if (key != null)
        {
            AbstractMorph morph =
                    createMorphFromKey(key);

            if (morph != null)
            {
                return morph;
            }
        }

        /*
         * 2. Последний MORPH до targetFrame.
         */
        CharacterKey latest =
                findLatestKeyAtOrBeforeFrame(
                        actorData,
                        CharacterKey.Type.MORPH,
                        this.targetFrame
                );

        if (latest != null)
        {
            AbstractMorph morph =
                    createMorphFromKey(latest);

            if (morph != null)
            {
                return morph;
            }
        }

        /*
         * 3. Runtime actor остаётся последним fallback.
         */
        if (runtimeActor != null)
        {
            try
            {
                return runtimeActor.morph.get();
            }
            catch (Throwable ignored)
            {
            }
        }

        return null;
    }


    private AbstractMorph createMorphFromKey(
            CharacterKey key)
    {
        NBTTagCompound nbt =
                getMorphNBTFromKey(key);

        if (nbt == null ||
                nbt.hasNoTags())
        {
            return null;
        }

        return createMorphFromNBT(nbt);
    }


    private NBTTagCompound getMorphNBTFromKey(
            CharacterKey key)
    {
        if (key == null ||
                !key.hasData("Morph"))
        {
            return null;
        }

        try
        {
            return key.getCompound("Morph");
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }


    /*
     * =========================================================
     * SAVE MORPH
     * =========================================================
     */

    private void syncMorphToScene(
            EntityActor runtimeActor,
            AbstractMorph editedMorph)
    {
        if (editedMorph == null ||
                this.currentActorData == null)
        {
            return;
        }

        try
        {
            NBTTagCompound nbt =
                    editedMorph.toNBT();

            writeMorphToCharacterKey(
                    CharacterKey.Type.MORPH,
                    nbt
            );

            if (runtimeActor != null)
            {
                runtimeActor.morph.set(
                        editedMorph
                );
            }
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Failed to save Morph."
            );

            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * SAVE SKIN
     * =========================================================
     */

    public void syncCustomMorphToSkin(
            CustomMorph customMorph)
    {
        if (customMorph == null ||
                this.currentActorData == null)
        {
            return;
        }

        try
        {
            customMorph.updateModel(true);

            if (customMorph.model == null)
            {
                System.err.println(
                        "[BBS Animation Editor] "
                                + "Cannot save Skin: model is null."
                );

                return;
            }

            NBTTagCompound nbt =
                    customMorph.toNBT();

            writeMorphToCharacterKey(
                    CharacterKey.Type.SKIN,
                    nbt
            );
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Failed to save Skin."
            );

            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * TIMELINE ACCESS
     *
     * Здесь специально используется reflection.
     *
     * Мы не привязываем Bridge к конкретной реализации
     * BlockbusterSceneActorData / CharacterTrack.
     * =========================================================
     */

    private Object getTimeline(
            BlockbusterSceneActorData actorData)
    {
        if (actorData == null)
        {
            return null;
        }

        /*
         * Сначала метод getCharacterTimeline().
         */
        try
        {
            Method method =
                    findMethod(
                            actorData.getClass(),
                            "getCharacterTimeline"
                    );

            if (method != null)
            {
                return method.invoke(actorData);
            }
        }
        catch (Throwable ignored)
        {
        }

        /*
         * Затем несколько возможных имён поля.
         */
        String[] names =
                {
                        "characterTimeline",
                        "timeline",
                        "characterController"
                };

        for (String name : names)
        {
            try
            {
                Field field =
                        findField(
                                actorData.getClass(),
                                name
                        );

                if (field != null)
                {
                    field.setAccessible(true);

                    Object value =
                            field.get(actorData);

                    if (value != null)
                    {
                        return value;
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        return null;
    }


    @SuppressWarnings("unchecked")
    private List<?> getTimelineTracks(
            Object timeline)
    {
        if (timeline == null)
        {
            return null;
        }

        try
        {
            Method method =
                    findMethod(
                            timeline.getClass(),
                            "getTracks"
                    );

            if (method != null)
            {
                Object value =
                        method.invoke(timeline);

                if (value instanceof List)
                {
                    return (List<?>) value;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return null;
    }


    @SuppressWarnings("unchecked")
    private List<?> getTrackKeys(
            Object track)
    {
        if (track == null)
        {
            return null;
        }

        try
        {
            Method method =
                    findMethod(
                            track.getClass(),
                            "getKeys"
                    );

            if (method != null)
            {
                Object value =
                        method.invoke(track);

                if (value instanceof List)
                {
                    return (List<?>) value;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return null;
    }


    /*
     * =========================================================
     * FIND KEY AT FRAME
     * =========================================================
     */

    private CharacterKey findKeyAtFrame(
            BlockbusterSceneActorData actorData,
            CharacterKey.Type type,
            int frame)
    {
        Object timeline =
                getTimeline(actorData);

        if (timeline == null)
        {
            return null;
        }

        /*
         * Сначала используем официальный
         * getKeysAtFrame(int).
         */
        try
        {
            Method method =
                    findMethod(
                            timeline.getClass(),
                            "getKeysAtFrame",
                            int.class
                    );

            if (method != null)
            {
                Object value =
                        method.invoke(
                                timeline,
                                frame
                        );

                if (value instanceof List)
                {
                    for (Object object :
                            (List<?>) value)
                    {
                        if (object instanceof CharacterKey)
                        {
                            CharacterKey key =
                                    (CharacterKey) object;

                            if (key.getType() == type)
                            {
                                return key;
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        /*
         * Fallback через tracks.
         */
        List<?> tracks =
                getTimelineTracks(timeline);

        if (tracks == null)
        {
            return null;
        }

        for (Object track : tracks)
        {
            List<?> keys =
                    getTrackKeys(track);

            if (keys == null)
            {
                continue;
            }

            for (Object object : keys)
            {
                if (!(object instanceof CharacterKey))
                {
                    continue;
                }

                CharacterKey key =
                        (CharacterKey) object;

                if (key.getFrame() == frame &&
                        key.getType() == type)
                {
                    return key;
                }
            }
        }

        return null;
    }


    /*
     * =========================================================
     * FIND LATEST KEY
     * =========================================================
     */

    private CharacterKey findLatestKeyAtOrBeforeFrame(
            BlockbusterSceneActorData actorData,
            CharacterKey.Type type,
            int targetFrame)
    {
        Object timeline =
                getTimeline(actorData);

        if (timeline == null)
        {
            return null;
        }

        List<?> tracks =
                getTimelineTracks(timeline);

        if (tracks == null)
        {
            return null;
        }

        CharacterKey result = null;

        int bestFrame =
                Integer.MIN_VALUE;

        for (Object track : tracks)
        {
            List<?> keys =
                    getTrackKeys(track);

            if (keys == null)
            {
                continue;
            }

            for (Object object : keys)
            {
                if (!(object instanceof CharacterKey))
                {
                    continue;
                }

                CharacterKey key =
                        (CharacterKey) object;

                if (key.getType() != type)
                {
                    continue;
                }

                int frame =
                        key.getFrame();

                if (frame <= targetFrame &&
                        frame >= bestFrame)
                {
                    bestFrame = frame;
                    result = key;
                }
            }
        }

        return result;
    }


    /*
     * =========================================================
     * WRITE KEY
     * =========================================================
     */

    private void writeMorphToCharacterKey(
            CharacterKey.Type type,
            NBTTagCompound morphNBT)
    {
        if (this.currentActorData == null ||
                morphNBT == null)
        {
            return;
        }

        CharacterKey destination =
                null;

        /*
         * 1. Именно выбранный key.
         */
        if (this.targetKey != null &&
                this.targetKey.getType() == type)
        {
            destination =
                    this.targetKey;
        }

        /*
         * 2. Key на targetFrame.
         */
        if (destination == null)
        {
            destination =
                    findKeyAtFrame(
                            this.currentActorData,
                            type,
                            this.targetFrame
                    );
        }

        /*
         * 3. Создаём key.
         */
        if (destination == null)
        {
            destination =
                    createKey(
                            this.currentActorData,
                            this.targetFrame,
                            type
                    );
        }

        if (destination == null)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Unable to create CharacterKey."
            );

            return;
        }

        destination.setCompound(
                "Morph",
                morphNBT.copy()
        );
    }


    private CharacterKey createKey(
            BlockbusterSceneActorData actorData,
            int frame,
            CharacterKey.Type type)
    {
        Object timeline =
                getTimeline(actorData);

        if (timeline == null)
        {
            return null;
        }

        /*
         * Сначала ищем track с уже существующим key
         * этого типа.
         */
        List<?> tracks =
                getTimelineTracks(timeline);

        if (tracks != null)
        {
            for (int index = 0;
                 index < tracks.size();
                 index++)
            {
                Object track =
                        tracks.get(index);

                List<?> keys =
                        getTrackKeys(track);

                if (keys == null)
                {
                    continue;
                }

                boolean sameType =
                        false;

                for (Object object : keys)
                {
                    if (object instanceof CharacterKey)
                    {
                        CharacterKey key =
                                (CharacterKey) object;

                        if (key.getType() == type)
                        {
                            sameType = true;
                            break;
                        }
                    }
                }

                if (!sameType)
                {
                    continue;
                }

                try
                {
                    Method method =
                            findMethod(
                                    timeline.getClass(),
                                    "createKey",
                                    int.class,
                                    int.class,
                                    CharacterKey.Type.class
                            );

                    if (method != null)
                    {
                        Object value =
                                method.invoke(
                                        timeline,
                                        index,
                                        frame,
                                        type
                                );

                        if (value instanceof CharacterKey)
                        {
                            return (CharacterKey) value;
                        }
                    }
                }
                catch (Throwable ignored)
                {
                }
            }
        }

        /*
         * Последний вариант — новая Track.
         */
        try
        {
            Method method =
                    findMethod(
                            timeline.getClass(),
                            "createTrackWithKey",
                            int.class,
                            CharacterKey.Type.class
                    );

            if (method != null)
            {
                Object value =
                        method.invoke(
                                timeline,
                                frame,
                                type
                        );

                if (value instanceof CharacterKey)
                {
                    return (CharacterKey) value;
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
     * REFLECTION HELPERS
     * =========================================================
     */

    private Field findField(
            Class<?> clazz,
            String name)
    {
        Class<?> current =
                clazz;

        while (current != null)
        {
            try
            {
                Field field =
                        current.getDeclaredField(name);

                field.setAccessible(true);

                return field;
            }
            catch (NoSuchFieldException ignored)
            {
            }

            current =
                    current.getSuperclass();
        }

        return null;
    }


    private Method findMethod(
            Class<?> clazz,
            String name,
            Class<?>... parameterTypes)
    {
        Class<?> current =
                clazz;

        while (current != null)
        {
            try
            {
                Method method =
                        current.getDeclaredMethod(
                                name,
                                parameterTypes
                        );

                method.setAccessible(true);

                return method;
            }
            catch (NoSuchMethodException ignored)
            {
            }

            current =
                    current.getSuperclass();
        }

        return null;
    }


    /*
     * =========================================================
     * RETURN
     * =========================================================
     */

    public void returnToEditor()
    {
        this.openedEditorType = null;

        if (this.mc == null)
        {
            return;
        }

        if (this.returnScreen != null)
        {
            this.mc.displayGuiScreen(
                    this.returnScreen
            );
        }
        else
        {
            this.mc.displayGuiScreen(null);
        }
    }


    /*
     * =========================================================
     * CAN OPEN
     * =========================================================
     */

    public boolean canOpen(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor)
    {
        if (this.mc == null)
        {
            return false;
        }

        if (actorData == null)
        {
            return false;
        }

        /*
         * Для Morph нужен runtime EntityActor.
         *
         * Для Skin он тоже используется как fallback,
         * но сам Skin может быть восстановлен из CharacterKey.
         */
        if (runtimeActor == null)
        {
            return false;
        }

        return true;
    }


    /*
     * =========================================================
     * GETTERS
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


    public CharacterKey.Type getOpenedEditorType()
    {
        return this.openedEditorType;
    }


    /*
     * =========================================================
     * GUI ACTOR WRAPPER
     * =========================================================
     */

    private static class SceneGuiActor
            extends GuiActor
    {
        private final BlockbusterCharacterGuiBridge bridge;

        private final EntityActor runtimeActor;


        private SceneGuiActor(
                Minecraft mc,
                EntityActor actor,
                BlockbusterCharacterGuiBridge bridge)
        {
            super(
                    mc,
                    actor
            );

            this.bridge =
                    bridge;

            this.runtimeActor =
                    actor;
        }


        @Override
        public void closeScreen()
        {
            /*
             * КРИТИЧНО:
             *
             * GuiActor редактирует renderer.morph,
             * а не обязательно actor.morph.
             *
             * Поэтому забираем renderer.morph ДО super.closeScreen().
             */
            AbstractMorph editedMorph =
                    null;

            try
            {
                if (this.renderer != null)
                {
                    editedMorph =
                            this.renderer.morph;
                }
            }
            catch (Throwable ignored)
            {
            }

            if (editedMorph != null)
            {
                this.bridge.syncMorphToScene(
                        this.runtimeActor,
                        editedMorph
                );
            }

            /*
             * Даём Blockbuster выполнить собственное
             * сохранение renderer.morph -> actor.morph.
             */
            super.closeScreen();

            /*
             * Возвращаемся в Animation Editor.
             */
            this.bridge.returnToEditor();
        }
    }
}