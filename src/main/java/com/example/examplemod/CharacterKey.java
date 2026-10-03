package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Один ключ Character Timeline.
 *
 * CharacterKey является источником истины для изменений
 * состояния Actor в конкретный момент времени.
 *
 * ВАЖНО:
 *
 * В новой архитектуре один CharacterKey может содержать
 * одновременно несколько независимых частей состояния:
 *
 *     Data.Morph
 *     Data.Skin
 *     Data.Animation
 *     Data.Action
 *     Data.Bones
 *
 * Отсутствующее поле означает:
 *
 *     "это состояние на данном кадре не изменяется
 *      и должно быть унаследовано от предыдущего ключа".
 *
 * ---------------------------------------------------------
 *
 * Type сохраняется для обратной совместимости со старой
 * системой Character Timeline.
 *
 * НОВАЯ ЛОГИКА НЕ ДОЛЖНА использовать Type как признак
 * того, какие данные находятся внутри ключа.
 *
 * Старые значения:
 *
 *     MORPH
 *     SKIN
 *     ANIMATION
 *     ACTION
 *     BODY_PART_OVERRIDE
 *     CUSTOM
 *
 * пока сохраняются, чтобы не ломать существующие данные,
 * Morph/Skin bridge и старые .dat.
 *
 * Runtime EntityActor НЕ является хранилищем Character state.
 */
public class CharacterKey
{
    /**
     * Legacy type.
     *
     * Оставлен намеренно.
     *
     * На следующем этапе CharacterStateResolver перестанет
     * использовать Type для определения содержимого ключа.
     */
    public enum Type
    {
        SKIN,
        MORPH,
        ANIMATION,
        ACTION,
        BODY_PART_OVERRIDE,
        CUSTOM
    }

    private int frame;

    /**
     * Legacy / compatibility type.
     *
     * Не является полным описанием содержимого key.
     */
    private Type type;

    /**
     * Частичное состояние Character этого кадра.
     */
    private NBTTagCompound data;


    /*
     * =========================================================
     * CONSTRUCTORS
     * =========================================================
     */

    public CharacterKey()
    {
        this.frame = 0;
        this.type = Type.CUSTOM;
        this.data = new NBTTagCompound();
    }


    public CharacterKey(
            int frame,
            Type type)
    {
        this.frame =
                Math.max(
                        0,
                        frame
                );

        this.type =
                type == null
                        ? Type.CUSTOM
                        : type;

        this.data =
                new NBTTagCompound();
    }


    /**
     * Создать новый универсальный Character Key.
     *
     * Type устанавливается в CUSTOM исключительно для
     * обратной совместимости.
     */
    public static CharacterKey create(
            int frame)
    {
        return new CharacterKey(
                frame,
                Type.CUSTOM
        );
    }


    /*
     * =========================================================
     * FRAME
     * =========================================================
     */

    public int getFrame()
    {
        return this.frame;
    }


    public void setFrame(
            int frame)
    {
        this.frame =
                Math.max(
                        0,
                        frame
                );
    }


    /*
     * =========================================================
     * TYPE / LEGACY COMPATIBILITY
     * =========================================================
     */

    public Type getType()
    {
        return this.type;
    }


    /**
     * Legacy setter.
     *
     * Новый код не должен использовать Type для определения
     * содержимого CharacterKey.
     */
    public void setType(
            Type type)
    {
        this.type =
                type == null
                        ? Type.CUSTOM
                        : type;
    }


    public boolean isMorph()
    {
        return this.type == Type.MORPH;
    }


    public boolean isSkin()
    {
        return this.type == Type.SKIN;
    }


    public boolean isAnimation()
    {
        return this.type == Type.ANIMATION;
    }


    public boolean isAction()
    {
        return this.type == Type.ACTION;
    }


    public boolean isBodyPartOverride()
    {
        return this.type == Type.BODY_PART_OVERRIDE;
    }


    public boolean isCustom()
    {
        return this.type == Type.CUSTOM;
    }


    /*
     * =========================================================
     * CONTENT CHECKS
     * =========================================================
     */

    /**
     * Проверяет, содержит ли ключ Morph.
     *
     * Это новая правильная проверка.
     */
    public boolean hasMorphData()
    {
        return hasMorph();
    }


    /**
     * Проверяет, содержит ли ключ Skin.
     *
     * Это новая правильная проверка.
     */
    public boolean hasSkinData()
    {
        return hasSkin();
    }


    /**
     * Проверяет, является ли ключ универсальным по
     * содержимому.
     *
     * Важно:
     *
     * Type здесь НЕ учитывается.
     */
    public boolean hasCharacterData()
    {
        return this.data != null &&
                !this.data.hasNoTags();
    }


    /*
     * =========================================================
     * GENERIC DATA
     * =========================================================
     */

    /**
     * Возвращает независимую копию Data.
     */
    public NBTTagCompound getData()
    {
        return this.data.copy();
    }


    public void setData(
            NBTTagCompound data)
    {
        this.data =
                data == null
                        ? new NBTTagCompound()
                        : data.copy();
    }


    public boolean hasData(
            String key)
    {
        return key != null &&
                this.data.hasKey(key);
    }


    public boolean removeData(
            String key)
    {
        if (key == null)
        {
            return false;
        }

        if (!this.data.hasKey(key))
        {
            return false;
        }

        this.data.removeTag(key);

        return true;
    }


    public void clearData()
    {
        this.data =
                new NBTTagCompound();
    }


    public String getString(
            String key)
    {
        if (key == null)
        {
            return "";
        }

        return this.data.getString(key);
    }


    public void setString(
            String key,
            String value)
    {
        if (key == null)
        {
            return;
        }

        this.data.setString(
                key,
                value == null
                        ? ""
                        : value
        );
    }


    public boolean getBoolean(
            String key)
    {
        if (key == null)
        {
            return false;
        }

        return this.data.getBoolean(key);
    }


    public void setBoolean(
            String key,
            boolean value)
    {
        if (key == null)
        {
            return;
        }

        this.data.setBoolean(
                key,
                value
        );
    }


    public int getInteger(
            String key)
    {
        if (key == null)
        {
            return 0;
        }

        return this.data.getInteger(key);
    }


    public void setInteger(
            String key,
            int value)
    {
        if (key == null)
        {
            return;
        }

        this.data.setInteger(
                key,
                value
        );
    }


    public NBTTagCompound getCompound(
            String key)
    {
        if (key == null ||
                !this.data.hasKey(key, 10))
        {
            return new NBTTagCompound();
        }

        return this.data
                .getCompoundTag(key)
                .copy();
    }


    public void setCompound(
            String key,
            NBTTagCompound value)
    {
        if (key == null)
        {
            return;
        }

        if (value == null)
        {
            this.data.removeTag(key);
            return;
        }

        this.data.setTag(
                key,
                value.copy()
        );
    }


    /*
     * =========================================================
     * MORPH
     * =========================================================
     */

    /**
     * Устанавливает Morph этого CharacterKey.
     *
     * БЕЗОПАСНОЕ НОВОЕ ПОВЕДЕНИЕ:
     *
     * 1. Morph записывается в Data.Morph.
     * 2. Skin НЕ удаляется.
     * 3. Другие данные ключа НЕ изменяются.
     *
     * Legacy Type временно устанавливается в MORPH,
     * чтобы существующий код Morph продолжал работать.
     *
     * На следующем этапе Resolver перестанет зависеть
     * от этого Type.
     */
    public void setMorph(
            NBTTagCompound morph)
    {
        this.type =
                Type.MORPH;


        if (morph == null ||
                morph.hasNoTags())
        {
            this.data.removeTag(
                    "Morph"
            );

            return;
        }


        this.data.setTag(
                "Morph",
                morph.copy()
        );
    }


    public NBTTagCompound getMorph()
    {
        return getCompound(
                "Morph"
        );
    }


    public boolean hasMorph()
    {
        return this.data.hasKey(
                "Morph",
                10
        );
    }


    /**
     * Удалить Morph, не затрагивая Skin
     * и остальные данные ключа.
     */
    public void clearMorph()
    {
        this.data.removeTag(
                "Morph"
        );
    }


    /*
     * =========================================================
     * SKIN
     * =========================================================
     */

    /**
     * Устанавливает Skin этого CharacterKey.
     *
     * БЕЗОПАСНОЕ НОВОЕ ПОВЕДЕНИЕ:
     *
     * 1. Skin записывается в Data.Skin.
     * 2. Morph НЕ удаляется.
     * 3. Другие данные ключа НЕ изменяются.
     *
     * Legacy Type временно устанавливается в SKIN,
     * чтобы существующий код Skin продолжал работать.
     *
     * На следующем этапе Resolver перестанет зависеть
     * от этого Type.
     */
    public void setSkin(
            NBTTagCompound skin)
    {
        this.type =
                Type.SKIN;


        if (skin == null ||
                skin.hasNoTags())
        {
            this.data.removeTag(
                    "Skin"
            );

            return;
        }


        this.data.setTag(
                "Skin",
                skin.copy()
        );
    }


    public NBTTagCompound getSkin()
    {
        return getCompound(
                "Skin"
        );
    }


    public boolean hasSkin()
    {
        return this.data.hasKey(
                "Skin",
                10
        );
    }


    /**
     * Удалить Skin, не затрагивая Morph
     * и остальные данные ключа.
     */
    public void clearSkin()
    {
        this.data.removeTag(
                "Skin"
        );
    }


    /*
     * =========================================================
     * APPEARANCE
     * =========================================================
     */

    /**
     * Удаляет только Morph и Skin.
     *
     * Остальные данные CharacterKey остаются.
     *
     * Type намеренно не меняется для обратной совместимости.
     */
    public void clearAppearanceData()
    {
        this.data.removeTag(
                "Morph"
        );

        this.data.removeTag(
                "Skin"
        );
    }


    /**
     * Проверяет наличие любого appearance data.
     */
    public boolean hasAppearanceData()
    {
        return hasMorph() ||
                hasSkin();
    }


    /*
     * =========================================================
     * COPY
     * =========================================================
     */

    /**
     * Создаёт полностью независимую копию ключа.
     *
     * Frame, legacy Type и весь внутренний NBT
     * копируются независимо.
     */
    public CharacterKey copy()
    {
        CharacterKey copy =
                new CharacterKey(
                        this.frame,
                        this.type
                );

        copy.data =
                this.data.copy();

        return copy;
    }


    /*
     * =========================================================
     * NBT SERIALIZATION
     * =========================================================
     */

    /**
     * Сохраняет CharacterKey.
     *
     * Старое поле Type сохраняется намеренно:
     * это позволяет старым системам и старым .dat
     * продолжать работать во время перехода.
     *
     * Data теперь может содержать одновременно:
     *
     *     Morph
     *     Skin
     *     Bones
     *     Animation
     *     Action
     */
    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag =
                new NBTTagCompound();


        tag.setInteger(
                "Frame",
                this.frame
        );


        /*
         * Legacy compatibility.
         */
        tag.setString(
                "Type",
                this.type.name()
        );


        tag.setTag(
                "Data",
                this.data.copy()
        );


        return tag;
    }


    /*
     * =========================================================
     * NBT DESERIALIZATION
     * =========================================================
     */

    public static CharacterKey fromNBT(
            NBTTagCompound tag)
    {
        CharacterKey key =
                new CharacterKey();


        if (tag == null)
        {
            return key;
        }


        /*
         * -----------------------------------------------------
         * FRAME
         * -----------------------------------------------------
         */

        if (tag.hasKey("Frame"))
        {
            key.frame =
                    Math.max(
                            0,
                            tag.getInteger(
                                    "Frame"
                            )
                    );
        }


        /*
         * -----------------------------------------------------
         * TYPE
         * -----------------------------------------------------
         *
         * Читаем старый Type, если он существует.
         *
         * Это НЕ удаляется, потому что существующий
         * Morph/Skin код всё ещё может его использовать
         * до следующего этапа миграции.
         */

        if (tag.hasKey("Type"))
        {
            try
            {
                key.type =
                        Type.valueOf(
                                tag.getString(
                                        "Type"
                                )
                        );
            }
            catch (IllegalArgumentException error)
            {
                key.type =
                        Type.CUSTOM;
            }
        }


        /*
         * -----------------------------------------------------
         * DATA
         * -----------------------------------------------------
         */

        if (tag.hasKey(
                "Data",
                10))
        {
            key.data =
                    tag.getCompoundTag(
                            "Data"
                    ).copy();
        }


        /*
         * -----------------------------------------------------
         * LEGACY COMPATIBILITY
         * -----------------------------------------------------
         *
         * Старые ключи могли иметь Type=CUSTOM,
         * но содержать Morph или Skin.
         *
         * Восстанавливаем старое поведение.
         *
         * Если Type уже явно указан как MORPH/SKIN,
         * сохраняем его.
         */

        if (key.type == Type.CUSTOM)
        {
            if (key.hasMorph())
            {
                key.type =
                        Type.MORPH;
            }
            else if (key.hasSkin())
            {
                key.type =
                        Type.SKIN;
            }
        }


        return key;
    }
}