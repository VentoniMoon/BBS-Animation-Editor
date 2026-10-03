package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.animation.model.ActionConfig;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Character Animation Setup.
 *
 * Хранит настройки Emoticons actions непосредственно
 * внутри универсального CharacterKey.
 *
 * Структура:
 *
 * Data
 *   AnimationSetup
 *     Actions
 *       idle
 *         Name
 *         Clamp
 *         Reset
 *         Speed
 *         Fade
 *         Tick
 *
 * Отсутствие action в текущем key означает наследование
 * последнего состояния этого action с предыдущего key.
 */
public class CharacterAnimationSetupController
{
    public static final String SETUP_TAG = "AnimationSetup";
    public static final String ACTIONS_TAG = "Actions";

    public static final String NAME_TAG = "Name";
    public static final String CLAMP_TAG = "Clamp";
    public static final String RESET_TAG = "Reset";
    public static final String SPEED_TAG = "Speed";
    public static final String FADE_TAG = "Fade";
    public static final String TICK_TAG = "Tick";

    /**
     * Точный список actions из Emoticons 1.1.2.
     */
    public static final String[] ACTIONS = new String[]
    {
        "Idle",
        "Running",
        "Sprinting",
        "Crouching",
        "Crouching Idle",
        "Swimming",
        "Swimming Idle",
        "Flying",
        "Flying Idle",
        "Riding",
        "Riding Idle",
        "Dying",
        "Falling",
        "Sleeping",
        "Jump",
        "Swipe",
        "Hurt",
        "Land",
        "Shoot",
        "Consume"
    };

    public static String toKey(String action)
    {
        if (action == null)
        {
            return "";
        }

        return action
                .replace(" ", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    public static String getActionKey(String action)
    {
        return toKey(action);
    }

    /**
     * Получить все реально доступные BOBJ animations
     * текущего Emoticons morph.
     *
     * Список НЕ захардкожен.
     *
     * Поэтому сюда автоматически попадают и пользовательские
     * animations, загруженные Emoticons из его custom-emotes
     * директории.
     */
    public static List<String> getAvailableAnimations(
            EntityActor runtimeActor)
    {
        List<String> result =
                new ArrayList<String>();

        if (runtimeActor == null ||
                runtimeActor.morph == null)
        {
            return result;
        }

        try
        {
            AbstractMorph morph =
                    runtimeActor.morph.get();

            if (!(morph instanceof AnimatedMorph))
            {
                return result;
            }

            AnimatedMorph animated =
                    (AnimatedMorph) morph;

            animated.initiateAnimator();

            if (animated.animator == null)
            {
                return result;
            }

            animated.animator.fetchAnimation();

            if (animated.animator.animation == null ||
                    animated.animator.animation.data == null ||
                    animated.animator.animation.data.actions == null)
            {
                return result;
            }

            for (mchorse.emoticons.skin_n_bones.api.bobj.BOBJAction action :
                    animated.animator.animation.data.actions.values())
            {
                if (action == null ||
                        action.name == null ||
                        action.name.isEmpty())
                {
                    continue;
                }

                if (!result.contains(action.name))
                {
                    result.add(action.name);
                }
            }

            Collections.sort(
                    result,
                    String.CASE_INSENSITIVE_ORDER
            );
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }

        return result;
    }

    /**
     * Получить effective config конкретного action
     * на текущем кадре.
     */
    public static ActionConfig getEffectiveConfig(
            BlockbusterSceneActorData actorData,
            int frame,
            String action)
    {
        ActionConfig result =
                new ActionConfig();

        if (actorData == null ||
                action == null ||
                action.isEmpty())
        {
            return result;
        }

        String key =
                getActionKey(action);

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return result;
        }

        CharacterKey latest =
                null;

        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }

            for (CharacterKey characterKey :
                    track.getKeys())
            {
                if (characterKey == null ||
                        characterKey.getFrame() > frame)
                {
                    continue;
                }

                NBTTagCompound actionData =
                        getActionData(
                                characterKey,
                                key
                        );

                if (actionData == null ||
                        actionData.hasNoTags())
                {
                    continue;
                }

                if (latest == null ||
                        characterKey.getFrame() >
                                latest.getFrame())
                {
                    latest = characterKey;
                }
            }
        }

        if (latest != null)
        {
            NBTTagCompound actionData =
                    getActionData(
                            latest,
                            key
                    );

            if (actionData != null)
            {
                applyNBTToConfig(
                        result,
                        actionData,
                        key
                );
            }
        }

        return result;
    }

    /**
     * Проверить, существует ли явная настройка action
     * на конкретном key.
     */
    public static boolean hasActionData(
            CharacterKey key,
            String action)
    {
        if (key == null)
        {
            return false;
        }

        NBTTagCompound data =
                getActionData(
                        key,
                        getActionKey(action)
                );

        return data != null &&
                !data.hasNoTags();
    }

    /**
     * Получить имя назначенной animation.
     *
     * Пустая строка означает "не назначено".
     */
    public static String getAssignedAnimation(
            BlockbusterSceneActorData actorData,
            int frame,
            String action)
    {
        ActionConfig config =
                getEffectiveConfig(
                        actorData,
                        frame,
                        action
                );

        return config.name == null
                ? ""
                : config.name;
    }

    /**
     * Записать назначение animation и текущие параметры
     * в конкретный CharacterKey.
     */
    public static boolean assignAnimation(
            CharacterKey key,
            String action,
            String animation)
    {
        if (key == null ||
                action == null ||
                action.isEmpty())
        {
            return false;
        }

        String actionKey =
                getActionKey(action);

        NBTTagCompound setup =
                key.getCompound(
                        SETUP_TAG
                );

        NBTTagCompound actions =
                setup.hasKey(
                        ACTIONS_TAG,
                        10
                )
                ? setup.getCompoundTag(
                        ACTIONS_TAG
                ).copy()
                : new NBTTagCompound();

        NBTTagCompound actionData =
                actions.hasKey(
                        actionKey,
                        10
                )
                ? actions.getCompoundTag(
                        actionKey
                ).copy()
                : new NBTTagCompound();

        actionData.setString(
                NAME_TAG,
                animation == null
                        ? ""
                        : animation
        );

        actions.setTag(
                actionKey,
                actionData
        );

        setup.setTag(
                ACTIONS_TAG,
                actions
        );

        key.setCompound(
                SETUP_TAG,
                setup
        );

        key.setType(
                CharacterKey.Type.ANIMATION
        );

        return true;
    }

    /**
     * Изменить один параметр action на конкретном key.
     */
    public static boolean setParameter(
            CharacterKey key,
            String action,
            float speed,
            float fade,
            int tick,
            boolean clamp,
            boolean reset)
    {
        if (key == null ||
                action == null ||
                action.isEmpty())
        {
            return false;
        }

        String actionKey =
                getActionKey(action);

        NBTTagCompound setup =
                key.getCompound(
                        SETUP_TAG
                );

        NBTTagCompound actions =
                setup.hasKey(
                        ACTIONS_TAG,
                        10
                )
                ? setup.getCompoundTag(
                        ACTIONS_TAG
                ).copy()
                : new NBTTagCompound();

        NBTTagCompound actionData =
                actions.hasKey(
                        actionKey,
                        10
                )
                ? actions.getCompoundTag(
                        actionKey
                ).copy()
                : new NBTTagCompound();

        if (actionData.hasKey(NAME_TAG))
        {
            actionData.setString(
                    NAME_TAG,
                    actionData.getString(NAME_TAG)
            );
        }

        actionData.setFloat(
                SPEED_TAG,
                speed
        );

        actionData.setFloat(
                FADE_TAG,
                fade
        );

        actionData.setInteger(
                TICK_TAG,
                tick
        );

        actionData.setBoolean(
                CLAMP_TAG,
                clamp
        );

        actionData.setBoolean(
                RESET_TAG,
                reset
        );

        actions.setTag(
                actionKey,
                actionData
        );

        setup.setTag(
                ACTIONS_TAG,
                actions
        );

        key.setCompound(
                SETUP_TAG,
                setup
        );

        key.setType(
                CharacterKey.Type.ANIMATION
        );

        return true;
    }

    /**
     * Немедленно применить effective Animation Setup
     * к runtime AnimatedMorph.
     *
     * Runtime здесь не является хранилищем:
     * он получает уже разрешённое состояние из Timeline.
     */
    public static void applyToRuntime(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int frame)
    {
        if (actorData == null ||
                runtimeActor == null ||
                runtimeActor.morph == null)
        {
            return;
        }

        try
        {
            AbstractMorph morph =
                    runtimeActor.morph.get();

            if (!(morph instanceof AnimatedMorph))
            {
                return;
            }

            AnimatedMorph animated =
                    (AnimatedMorph) morph;

            animated.initiateAnimator();

            if (animated.animator == null)
            {
                return;
            }

            animated.animator.fetchAnimation();

            if (animated.animator.animation == null)
            {
                return;
            }

            NBTTagCompound userData =
                    animated.userConfigData == null
                            ? new NBTTagCompound()
                            : animated.userConfigData.copy();

            NBTTagCompound actions =
                    new NBTTagCompound();

            for (String action :
                    ACTIONS)
            {
                ActionConfig config =
                        getEffectiveConfig(
                                actorData,
                                frame,
                                action
                        );

                if (config.name == null ||
                        config.name.isEmpty())
                {
                    continue;
                }

                NBTTagCompound actionTag =
                        new NBTTagCompound();

                actionTag.setString(
                        NAME_TAG,
                        config.name
                );

                if (!config.clamp)
                {
                    actionTag.setBoolean(
                            CLAMP_TAG,
                            false
                    );
                }

                if (!config.reset)
                {
                    actionTag.setBoolean(
                            RESET_TAG,
                            false
                    );
                }

                if (config.speed != 1)
                {
                    actionTag.setFloat(
                            SPEED_TAG,
                            config.speed
                    );
                }

                if (config.fade != 5)
                {
                    actionTag.setInteger(
                            FADE_TAG,
                            (int) config.fade
                    );
                }

                if (config.tick != 0)
                {
                    actionTag.setInteger(
                            TICK_TAG,
                            config.tick
                    );
                }

                actions.setTag(
                        getActionKey(action),
                        actionTag
                );
            }

            if (actions.hasNoTags())
            {
                userData.removeTag(
                        ACTIONS_TAG
                );
            }
            else
            {
                userData.setTag(
                        ACTIONS_TAG,
                        actions
                );
            }

            animated.userConfigData =
                    userData;

            animated.userConfigChanged =
                    true;

            animated.updateAnimator();
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }

    private static NBTTagCompound getActionData(
            CharacterKey key,
            String actionKey)
    {
        if (key == null ||
                actionKey == null ||
                actionKey.isEmpty())
        {
            return null;
        }

        NBTTagCompound setup =
                key.getCompound(
                        SETUP_TAG
                );

        if (!setup.hasKey(
                ACTIONS_TAG,
                10
        ))
        {
            return null;
        }

        NBTTagCompound actions =
                setup.getCompoundTag(
                        ACTIONS_TAG
                );

        if (!actions.hasKey(
                actionKey,
                10
        ))
        {
            return null;
        }

        return actions.getCompoundTag(
                actionKey
        ).copy();
    }

    private static void applyNBTToConfig(
            ActionConfig config,
            NBTTagCompound tag,
            String key)
    {
        config.name =
                tag.hasKey(NAME_TAG)
                        ? tag.getString(NAME_TAG)
                        : "";

        if (tag.hasKey(CLAMP_TAG))
        {
            config.clamp =
                    tag.getBoolean(
                            CLAMP_TAG
                    );
        }

        if (tag.hasKey(RESET_TAG))
        {
            config.reset =
                    tag.getBoolean(
                            RESET_TAG
                    );
        }

        if (tag.hasKey(SPEED_TAG))
        {
            config.speed =
                    tag.getFloat(
                            SPEED_TAG
                    );
        }

        if (tag.hasKey(FADE_TAG))
        {
            config.fade =
                    tag.getFloat(
                            FADE_TAG
                    );
        }

        if (tag.hasKey(TICK_TAG))
        {
            config.tick =
                    tag.getInteger(
                            TICK_TAG
                    );
        }
    }
}
