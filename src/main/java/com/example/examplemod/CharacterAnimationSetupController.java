package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.animation.model.ActionConfig;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJAction;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

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

    public static final String PARAM_NAME = NAME_TAG;
    public static final String PARAM_CLAMP = CLAMP_TAG;
    public static final String PARAM_RESET = RESET_TAG;
    public static final String PARAM_SPEED = SPEED_TAG;
    public static final String PARAM_FADE = FADE_TAG;
    public static final String PARAM_TICK = TICK_TAG;

    public static final String[] ACTIONS = new String[]
    {
        "Idle", "Running", "Sprinting", "Crouching", "Crouching Idle",
        "Swimming", "Swimming Idle", "Flying", "Flying Idle",
        "Riding", "Riding Idle", "Dying", "Falling", "Sleeping",
        "Jump", "Swipe", "Hurt", "Land", "Shoot", "Consume"
    };

    private CharacterAnimationSetupController() {}

    public static String toKey(String action)
    {
        if (action == null) return "";
        return action.replace(" ", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    public static String getActionKey(String action)
    {
        return toKey(action);
    }

    public static List<String> getAvailableAnimations(EntityActor runtimeActor)
    {
        List<String> result = new ArrayList<String>();

        if (runtimeActor == null || runtimeActor.morph == null)
        {
            return result;
        }

        try
        {
            AbstractMorph morph = runtimeActor.morph.get();

            if (!(morph instanceof AnimatedMorph))
            {
                return result;
            }

            AnimatedMorph animated = (AnimatedMorph) morph;
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

            for (BOBJAction action :
                    animated.animator.animation.data.actions.values())
            {
                if (action == null || action.name == null || action.name.isEmpty())
                {
                    continue;
                }

                if (!result.contains(action.name))
                {
                    result.add(action.name);
                }
            }

            Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }

        return result;
    }

    /**
     * Each ActionConfig field inherits independently from previous keys.
     * This is intentionally analogous to CharacterBodyPartOverrideController.
     */
    public static ActionConfig getEffectiveConfig(
            BlockbusterSceneActorData actorData,
            int frame,
            String action)
    {
        String actionKey = getActionKey(action);
        ActionConfig result = new ActionConfig(actionKey);

        if (actorData == null || actionKey.isEmpty())
        {
            return result;
        }

        String name = findLatestString(actorData, frame, actionKey, NAME_TAG);
        Boolean clamp = findLatestBoolean(actorData, frame, actionKey, CLAMP_TAG);
        Boolean reset = findLatestBoolean(actorData, frame, actionKey, RESET_TAG);
        Float speed = findLatestFloat(actorData, frame, actionKey, SPEED_TAG);
        Float fade = findLatestFloat(actorData, frame, actionKey, FADE_TAG);
        Integer tick = findLatestInteger(actorData, frame, actionKey, TICK_TAG);

        if (name != null) result.name = name;
        if (clamp != null) result.clamp = clamp.booleanValue();
        if (reset != null) result.reset = reset.booleanValue();
        if (speed != null) result.speed = speed.floatValue();
        if (fade != null) result.fade = fade.floatValue();
        if (tick != null) result.tick = tick.intValue();

        return result;
    }

    public static boolean hasActionData(
            CharacterKey key,
            String action)
    {
        return key != null &&
                getActionData(key, getActionKey(action)) != null;
    }

    public static boolean hasEffectiveActionData(
            BlockbusterSceneActorData actorData,
            int frame,
            String action)
    {
        if (actorData == null || action == null || action.isEmpty())
        {
            return false;
        }

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return false;
        }

        String actionKey = getActionKey(action);

        for (CharacterTrack track : timeline.getTracks())
        {
            if (track == null) continue;

            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > frame) continue;

                if (getActionData(key, actionKey) != null)
                {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasEffectiveSetup(
            BlockbusterSceneActorData actorData,
            int frame)
    {
        if (actorData == null)
        {
            return false;
        }

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return false;
        }

        for (CharacterTrack track : timeline.getTracks())
        {
            if (track == null) continue;

            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > frame) continue;

                NBTTagCompound setup = key.getCompound(SETUP_TAG);

                if (setup.hasKey(ACTIONS_TAG, 10) &&
                        !setup.getCompoundTag(ACTIONS_TAG).hasNoTags())
                {
                    return true;
                }
            }
        }

        return false;
    }

    public static String getAssignedAnimation(
            BlockbusterSceneActorData actorData,
            int frame,
            String action)
    {
        ActionConfig config =
                getEffectiveConfig(actorData, frame, action);

        return config.name == null ? "" : config.name;
    }

    /**
     * Writes only Name. Other fields stay inherited independently.
     */
    public static boolean assignAnimation(
            CharacterKey key,
            String action,
            String animation)
    {
        if (key == null || action == null || action.isEmpty())
        {
            return false;
        }

        String actionKey = getActionKey(action);
        NBTTagCompound data = key.getData();
        NBTTagCompound setup = getOrCreateCompound(data, SETUP_TAG);
        NBTTagCompound actions = getOrCreateCompound(setup, ACTIONS_TAG);
        NBTTagCompound actionData = getOrCreateCompound(actions, actionKey);

        String value = animation == null ? "" : animation;
        String old = actionData.hasKey(NAME_TAG, 8)
                ? actionData.getString(NAME_TAG)
                : null;

        if (value.equals(old))
        {
            return false;
        }

        actionData.setString(NAME_TAG, value);
        actions.setTag(actionKey, actionData);
        setup.setTag(ACTIONS_TAG, actions);
        data.setTag(SETUP_TAG, setup);
        key.setData(data);
        key.setType(CharacterKey.Type.ANIMATION);

        return true;
    }

    /**
     * Writes only one changed ActionConfig field to the selected key.
     */
    public static boolean setParameter(
            CharacterKey key,
            String action,
            String parameter,
            Object value)
    {
        if (key == null || action == null || action.isEmpty() || parameter == null)
        {
            return false;
        }

        String actionKey = getActionKey(action);
        NBTTagCompound data = key.getData();
        NBTTagCompound setup = getOrCreateCompound(data, SETUP_TAG);
        NBTTagCompound actions = getOrCreateCompound(setup, ACTIONS_TAG);
        NBTTagCompound actionData = getOrCreateCompound(actions, actionKey);

        boolean changed = false;

        if (PARAM_SPEED.equals(parameter))
        {
            float number = value instanceof Number
                    ? ((Number) value).floatValue() : 1F;

            if (!actionData.hasKey(SPEED_TAG, 99) ||
                    actionData.getFloat(SPEED_TAG) != number)
            {
                actionData.setFloat(SPEED_TAG, number);
                changed = true;
            }
        }
        else if (PARAM_FADE.equals(parameter))
        {
            int number = value instanceof Number
                    ? ((Number) value).intValue() : 5;

            if (!actionData.hasKey(FADE_TAG, 99) ||
                    actionData.getInteger(FADE_TAG) != number)
            {
                actionData.setInteger(FADE_TAG, number);
                changed = true;
            }
        }
        else if (PARAM_TICK.equals(parameter))
        {
            int number = value instanceof Number
                    ? ((Number) value).intValue() : 0;

            if (!actionData.hasKey(TICK_TAG, 99) ||
                    actionData.getInteger(TICK_TAG) != number)
            {
                actionData.setInteger(TICK_TAG, number);
                changed = true;
            }
        }
        else if (PARAM_CLAMP.equals(parameter))
        {
            boolean flag = value instanceof Boolean &&
                    ((Boolean) value).booleanValue();

            if (!actionData.hasKey(CLAMP_TAG, 99) ||
                    actionData.getBoolean(CLAMP_TAG) != flag)
            {
                actionData.setBoolean(CLAMP_TAG, flag);
                changed = true;
            }
        }
        else if (PARAM_RESET.equals(parameter))
        {
            boolean flag = value instanceof Boolean &&
                    ((Boolean) value).booleanValue();

            if (!actionData.hasKey(RESET_TAG, 99) ||
                    actionData.getBoolean(RESET_TAG) != flag)
            {
                actionData.setBoolean(RESET_TAG, flag);
                changed = true;
            }
        }
        else
        {
            return false;
        }

        if (!changed)
        {
            return false;
        }

        actions.setTag(actionKey, actionData);
        setup.setTag(ACTIONS_TAG, actions);
        data.setTag(SETUP_TAG, setup);
        key.setData(data);
        key.setType(CharacterKey.Type.ANIMATION);

        return true;
    }

    public static boolean setParameter(
            CharacterKey key,
            String action,
            float speed,
            float fade,
            int tick,
            boolean clamp,
            boolean reset)
    {
        boolean changed = false;

        changed |= setParameter(key, action, PARAM_SPEED, Float.valueOf(speed));
        changed |= setParameter(key, action, PARAM_FADE, Integer.valueOf((int) fade));
        changed |= setParameter(key, action, PARAM_TICK, Integer.valueOf(tick));
        changed |= setParameter(key, action, PARAM_CLAMP, Boolean.valueOf(clamp));
        changed |= setParameter(key, action, PARAM_RESET, Boolean.valueOf(reset));

        return changed;
    }

    /**
     * Apply the exact NBT format used by Emoticons ActionConfig.
     */
    public static void applyToRuntime(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int frame)
    {
        if (actorData == null ||
                runtimeActor == null ||
                runtimeActor.morph == null ||
                !hasEffectiveSetup(actorData, frame))
        {
            return;
        }

        try
        {
            AbstractMorph morph = runtimeActor.morph.get();

            if (!(morph instanceof AnimatedMorph))
            {
                return;
            }

            AnimatedMorph animated = (AnimatedMorph) morph;
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

            NBTTagCompound actions = new NBTTagCompound();

            /*
             * Preserve the Emoticons configuration that came from
             * the morph editor/model itself. Character Timeline
             * only overrides the action entries that are explicitly
             * present in its keys.
             */
            NBTTagCompound baseUserConfig =
                    animated.animator.userConfig.toNBT(null);

            if (baseUserConfig != null &&
                    baseUserConfig.hasKey(ACTIONS_TAG, 10))
            {
                actions =
                        baseUserConfig
                                .getCompoundTag(ACTIONS_TAG)
                                .copy();
            }

            for (String action : ACTIONS)
            {
                if (!hasEffectiveActionData(actorData, frame, action))
                {
                    continue;
                }

                ActionConfig config =
                        getEffectiveConfig(actorData, frame, action);

                NBTBase configNBT = config.toNBT();

                actions.setTag(
                        getActionKey(action),
                        configNBT
                );
            }

            if (actions.hasNoTags())
            {
                userData.removeTag(ACTIONS_TAG);
            }
            else
            {
                userData.setTag(ACTIONS_TAG, actions);
            }

            animated.userConfigData = userData;
            animated.userConfigChanged = true;
            animated.updateAnimator();
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }

    public static String getRuntimeSignature(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int frame)
    {
        if (actorData == null || runtimeActor == null || runtimeActor.morph == null)
        {
            return "";
        }

        StringBuilder signature = new StringBuilder();

        try
        {
            AbstractMorph morph = runtimeActor.morph.get();

            signature.append(System.identityHashCode(morph));

            if (morph instanceof AnimatedMorph)
            {
                AnimatedMorph animated = (AnimatedMorph) morph;
                signature.append('|').append(animated.animationName);

                for (String action : ACTIONS)
                {
                    if (!hasEffectiveActionData(actorData, frame, action))
                    {
                        continue;
                    }

                    ActionConfig config =
                            getEffectiveConfig(actorData, frame, action);

                    signature.append('|')
                            .append(getActionKey(action))
                            .append('=')
                            .append(config.name)
                            .append(',')
                            .append(config.clamp)
                            .append(',')
                            .append(config.reset)
                            .append(',')
                            .append(config.speed)
                            .append(',')
                            .append(config.fade)
                            .append(',')
                            .append(config.tick);
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return signature.toString();
    }

    private static NBTTagCompound getActionData(
            CharacterKey key,
            String actionKey)
    {
        if (key == null || actionKey == null || actionKey.isEmpty())
        {
            return null;
        }

        NBTTagCompound setup = key.getCompound(SETUP_TAG);

        if (!setup.hasKey(ACTIONS_TAG, 10))
        {
            return null;
        }

        NBTTagCompound actions = setup.getCompoundTag(ACTIONS_TAG);

        if (!actions.hasKey(actionKey, 10))
        {
            return null;
        }

        return actions.getCompoundTag(actionKey).copy();
    }

    private static NBTTagCompound getOrCreateCompound(
            NBTTagCompound parent,
            String key)
    {
        if (parent.hasKey(key, 10))
        {
            return parent.getCompoundTag(key);
        }

        return new NBTTagCompound();
    }

    private static String findLatestString(
            BlockbusterSceneActorData actorData,
            int frame,
            String actionKey,
            String field)
    {
        CharacterKey key =
                findLatestKeyWithField(
                        actorData.getCharacterTimeline(),
                        frame,
                        actionKey,
                        field
                );

        if (key == null) return null;

        NBTTagCompound data = getActionData(key, actionKey);
        return data != null && data.hasKey(field, 8)
                ? data.getString(field) : null;
    }

    private static Boolean findLatestBoolean(
            BlockbusterSceneActorData actorData,
            int frame,
            String actionKey,
            String field)
    {
        CharacterKey key =
                findLatestKeyWithField(
                        actorData.getCharacterTimeline(),
                        frame,
                        actionKey,
                        field
                );

        if (key == null) return null;

        NBTTagCompound data = getActionData(key, actionKey);
        return data != null && data.hasKey(field, 99)
                ? Boolean.valueOf(data.getBoolean(field)) : null;
    }

    private static Float findLatestFloat(
            BlockbusterSceneActorData actorData,
            int frame,
            String actionKey,
            String field)
    {
        CharacterKey key =
                findLatestKeyWithField(
                        actorData.getCharacterTimeline(),
                        frame,
                        actionKey,
                        field
                );

        if (key == null) return null;

        NBTTagCompound data = getActionData(key, actionKey);
        return data != null && data.hasKey(field, 99)
                ? Float.valueOf(data.getFloat(field)) : null;
    }

    private static Integer findLatestInteger(
            BlockbusterSceneActorData actorData,
            int frame,
            String actionKey,
            String field)
    {
        CharacterKey key =
                findLatestKeyWithField(
                        actorData.getCharacterTimeline(),
                        frame,
                        actionKey,
                        field
                );

        if (key == null) return null;

        NBTTagCompound data = getActionData(key, actionKey);
        return data != null && data.hasKey(field, 99)
                ? Integer.valueOf(data.getInteger(field)) : null;
    }

    private static CharacterKey findLatestKeyWithField(
            CharacterTimelineController timeline,
            int frame,
            String actionKey,
            String field)
    {
        if (timeline == null)
        {
            return null;
        }

        CharacterKey latest = null;

        for (CharacterTrack track : timeline.getTracks())
        {
            if (track == null) continue;

            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > frame)
                {
                    continue;
                }

                NBTTagCompound data =
                        getActionData(key, actionKey);

                if (data == null || !data.hasKey(field))
                {
                    continue;
                }

                if (latest == null ||
                        key.getFrame() > latest.getFrame())
                {
                    latest = key;
                }
            }
        }

        return latest;
    }
}
