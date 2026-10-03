package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class BodyPartModelData
{
    private final String modelName;
    private final String attachmentBoneName;
    private final BlockbusterModelAccess modelAccess;
    private final List<AnimationBone> bones;

    private int startFrame;
    private int endFrame;

    public BodyPartModelData(
            String modelName,
            String attachmentBoneName,
            BlockbusterModelAccess modelAccess,
            List<AnimationBone> bones,
            int startFrame,
            int endFrame)
    {
        this.modelName = modelName;
        this.attachmentBoneName = attachmentBoneName;
        this.modelAccess = modelAccess;
        this.bones = bones == null
                ? new ArrayList<AnimationBone>()
                : bones;
        this.startFrame = Math.max(0, startFrame);
        this.endFrame = Math.max(this.startFrame + 1, endFrame);
    }

    public String getModelName() { return this.modelName; }
    public String getAttachmentBoneName() { return this.attachmentBoneName; }
    public BlockbusterModelAccess getModelAccess() { return this.modelAccess; }
    public List<AnimationBone> getBones() { return this.bones; }

    public int getStartFrame() { return this.startFrame; }
    public int getEndFrame() { return this.endFrame; }

    public void setStartFrame(int frame)
    {
        this.startFrame = Math.max(0, Math.min(frame, this.endFrame - 1));
    }

    public void setEndFrame(int frame)
    {
        this.endFrame = Math.max(this.startFrame + 1, frame);
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
