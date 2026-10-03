package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class BodyPartModelData
{
    private String modelName;
    private String attachmentBoneName;
    private BlockbusterModelAccess modelAccess;
    private List<AnimationBone> bones;

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

    public AnimationBone getBone(int index)
    {
        if (index < 0 || index >= this.bones.size())
        {
            return null;
        }

        return this.bones.get(index);
    }
}
