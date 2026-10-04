package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BodyPartModelData
{
    private static int NEXT_TIMELINE_ID = 1;
    private final String timelineId;

    private String modelName;
    private String attachmentBoneName;
    private BlockbusterModelAccess modelAccess;
    private List<AnimationBone> bones;

    /** Model replacements made by the Character Timeline. */
    private final TreeMap<Integer, String> modelKeys =
            new TreeMap<Integer, String>();

    /**
     * Character state keys belonging ONLY to this Body Part.
     * The existing Character systems (Skin, Animation and Bones)
     * operate on these keys exactly like they operate on Actor keys.
     */
    private final CharacterTimelineController characterTimeline =
            new CharacterTimelineController();

    private int startFrame;
    private int endFrame;

    /* Global transform of the whole attached model. */
    private final AnimationTransform globalTransform =
            new AnimationTransform();

    public BodyPartModelData(
            String modelName,
            String attachmentBoneName,
            BlockbusterModelAccess modelAccess,
            List<AnimationBone> bones,
            int startFrame,
            int endFrame)
    {
        this.timelineId = "bodypart:" + NEXT_TIMELINE_ID++;
        this.modelName = modelName == null ? "" : modelName;
        this.attachmentBoneName = attachmentBoneName == null
                ? ""
                : attachmentBoneName;
        this.modelAccess = modelAccess;
        this.bones = bones == null
                ? new ArrayList<AnimationBone>()
                : bones;
        this.startFrame = Math.max(0, startFrame);
        this.endFrame = Math.max(this.startFrame + 1, endFrame);
    }

    public String getTimelineId() { return this.timelineId; }
    public String getModelName() { return this.modelName; }
    public String getAttachmentBoneName() { return this.attachmentBoneName; }
    public BlockbusterModelAccess getModelAccess() { return this.modelAccess; }
    public List<AnimationBone> getBones() { return this.bones; }

    public boolean hasModel()
    {
        return this.modelAccess != null &&
                this.modelName != null &&
                this.modelName.length() > 0 &&
                this.bones != null &&
                !this.bones.isEmpty();
    }

    public int getStartFrame() { return this.startFrame; }
    public int getEndFrame() { return this.endFrame; }

    public AnimationTransform getGlobalTransform()
    {
        return this.globalTransform;
    }

    public void setAttachmentBoneName(String name)
    {
        if (name != null && name.length() > 0)
        {
            this.attachmentBoneName = name;
        }
    }

    public void setModel(
            String modelName,
            BlockbusterModelAccess modelAccess,
            List<AnimationBone> bones)
    {
        this.modelName =
                modelName == null ? "" : modelName;
        this.modelAccess = modelAccess;
        this.bones =
                bones == null
                        ? new ArrayList<AnimationBone>()
                        : bones;
    }

    public void setStartFrame(int frame)
    {
        this.startFrame =
                Math.max(
                        0,
                        Math.min(
                                frame,
                                this.endFrame - 1
                        )
                );
    }

    public void setEndFrame(int frame)
    {
        this.endFrame =
                Math.max(
                        this.startFrame + 1,
                        frame
                );
    }

    public String getModelNameAt(int frame)
    {
        String result = this.modelName;
        Map.Entry<Integer, String> entry =
                this.modelKeys.floorEntry(Math.max(0, frame));

        if (entry != null && entry.getValue() != null && entry.getValue().length() > 0)
        {
            result = entry.getValue();
        }

        return result == null ? "" : result;
    }

    public boolean hasModelKeyAt(int frame)
    {
        return this.modelKeys.containsKey(frame);
    }

    public void setModelKey(int frame, String modelName)
    {
        if (frame < 0 || modelName == null || modelName.length() == 0)
        {
            return;
        }

        this.modelKeys.put(frame, modelName);
    }

    public void removeModelKey(int frame)
    {
        this.modelKeys.remove(frame);
    }

    public boolean moveModelKey(int oldFrame, int newFrame)
    {
        if (oldFrame == newFrame)
        {
            return true;
        }

        if (!this.modelKeys.containsKey(oldFrame) ||
                this.modelKeys.containsKey(newFrame))
        {
            return false;
        }

        String value = this.modelKeys.remove(oldFrame);
        this.modelKeys.put(newFrame, value);
        return true;
    }

    public List<Integer> getModelKeyFrames()
    {
        return new ArrayList<Integer>(this.modelKeys.keySet());
    }

    public CharacterTimelineController getCharacterTimeline()
    {
        return this.characterTimeline;
    }

    public CharacterKey getCharacterStateKeyAt(int frame)
    {
        CharacterKey latest = null;
        int target = Math.max(0, frame);

        for (CharacterTrack track : this.characterTimeline.getTracks())
        {
            if (track == null) continue;

            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > target)
                {
                    continue;
                }

                if (latest == null || key.getFrame() > latest.getFrame())
                {
                    latest = key;
                }
            }
        }

        return latest;
    }

    public CharacterKey getOrCreateCharacterStateKey(int frame)
    {
        int target = Math.max(0, frame);
        CharacterKey existing = getCharacterStateKeyAt(target);

        if (existing != null && existing.getFrame() == target)
        {
            return existing;
        }

        CharacterTimelineController timeline = this.characterTimeline;
        CharacterTrack track = timeline.ensureMainTrack();
        CharacterKey key = CharacterKey.create(target);
        track.addKey(key);
        return key;
    }

    public void removeCharacterStateKey(int frame)
    {
        int target = Math.max(0, frame);
        CharacterTimelineController timeline = this.characterTimeline;

        for (CharacterTrack track : timeline.getTracks())
        {
            if (track == null) continue;
            CharacterKey key = track.getKeyAtFrame(target);

            if (key != null)
            {
                track.removeKey(key);
            }
        }
    }

    public List<Integer> getCharacterStateKeyFrames()
    {
        List<Integer> result = new ArrayList<Integer>();

        for (CharacterTrack track : this.characterTimeline.getTracks())
        {
            if (track == null) continue;

            for (CharacterKey key : track.getKeys())
            {
                if (key != null && !result.contains(key.getFrame()))
                {
                    result.add(key.getFrame());
                }
            }
        }

        java.util.Collections.sort(result);
        return result;
    }

    public boolean replaceModelByName(String newModelName)
    {
        if (newModelName == null || newModelName.length() == 0)
        {
            return false;
        }

        BlockbusterModelAccess access = new BlockbusterModelAccess(null);

        if (!access.loadModelByName(newModelName))
        {
            return false;
        }

        List<AnimationBone> newBones = new ArrayList<AnimationBone>();
        List<BlockbusterLimbData> limbs = access.getLimbData();

        if (limbs != null)
        {
            for (BlockbusterLimbData limb : limbs)
            {
                if (limb == null) continue;

                AnimationBone bone = new AnimationBone(limb.getName());
                bone.setLocalPosition(limb.getX(), limb.getY(), limb.getZ());
                newBones.add(bone);
            }

            for (AnimationBone bone : newBones)
            {
                for (BlockbusterLimbData limb : limbs)
                {
                    if (limb != null && limb.getName().equals(bone.getName()))
                    {
                        String parentName = limb.getParentName();

                        if (parentName != null)
                        {
                            for (AnimationBone parent : newBones)
                            {
                                if (parentName.equals(parent.getName()))
                                {
                                    bone.setParent(parent);
                                    break;
                                }
                            }
                        }

                        break;
                    }
                }
            }
        }

        if (newBones.isEmpty())
        {
            return false;
        }

        this.modelName = newModelName;
        this.modelAccess = access;
        this.bones = newBones;
        return true;
    }

    public AnimationBone getBone(int index)
    {
        if (index < 0 || index >= this.bones.size())
        {
            return null;
        }

        return this.bones.get(index);
    }
}
