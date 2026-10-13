package com.example.examplemod;

import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Stage 7: safely merge prepared main-track Character MorphAction entries into
 * a COPY of the original Blockbuster Record NBT.
 *
 * No disk writes and no modifications of the source record are performed.
 * Conflicting original MorphActions are deliberately rejected until an
 * explicit replacement policy is available in the export UI.
 */
public final class EditorCharacterMorphMerge
{
    private EditorCharacterMorphMerge() {}

    public static NBTTagCompound merge(
            NBTTagCompound original,
            List<EditorCharacterMorphExport.PreparedAction> prepared)
            throws IOException
    {
        if (original == null || !original.hasKey("Frames", 9))
        {
            throw new IOException("Record has no Frames list");
        }

        NBTTagCompound result = original.copy();
        if (prepared == null || prepared.isEmpty())
        {
            return result;
        }

        NBTTagList frames = result.getTagList("Frames", 10);
        NBTTagCompound registry = result.hasKey("Actions", 10)
                ? result.getCompoundTag("Actions")
                : new NBTTagCompound();

        // Validate ALL entries first so a conflict cannot produce a
        // partially merged result.
        for (EditorCharacterMorphExport.PreparedAction next : prepared)
        {
            if (next == null || next.nbt == null)
            {
                throw new IOException("Null Character morph export action");
            }
            if (next.frame < 0 || next.frame >= frames.tagCount())
            {
                throw new IOException("Morph key at tick " + next.frame
                        + " exceeds original record length " + frames.tagCount()
                        + "; extending Record frames is not yet supported");
            }

            byte type = next.nbt.getByte("Type");
            int unsignedId = type & 255;
            String registryKey = Integer.toString(unsignedId);
            if (registry.hasKey(registryKey, 8)
                    && !"morph".equals(registry.getString(registryKey)))
            {
                throw new IOException("Action type " + unsignedId
                        + " is not morph in this Record");
            }

            NBTTagCompound frame = frames.getCompoundTagAt(next.frame);
            if (frame.hasKey("Action") && !frame.hasKey("Action", 9))
            {
                throw new IOException("Legacy Action tag at tick " + next.frame
                        + " needs conversion before merging");
            }

            NBTTagList actions = frame.getTagList("Action", 10);
            for (int i = 0; i < actions.tagCount(); i++)
            {
                NBTTagCompound existing = actions.getCompoundTagAt(i);
                int existingType = existing.getByte("Type") & 255;
                String mapped = registry.getString(Integer.toString(existingType));
                boolean isMorph = existingType == unsignedId || "morph".equals(mapped);
                if (isMorph && !existing.equals(next.nbt))
                {
                    throw new IOException("Conflicting MorphAction at tick "
                            + next.frame + "; original action preserved");
                }
            }
        }

        for (EditorCharacterMorphExport.PreparedAction next : prepared)
        {
            int id = next.nbt.getByte("Type") & 255;
            registry.setString(Integer.toString(id), "morph");

            NBTTagCompound frame = frames.getCompoundTagAt(next.frame);
            NBTTagList actions = frame.getTagList("Action", 10);
            boolean alreadyPresent = false;
            for (int i = 0; i < actions.tagCount(); i++)
            {
                if (actions.getCompoundTagAt(i).equals(next.nbt))
                {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent)
            {
                actions.appendTag(next.nbt.copy());
                frame.setTag("Action", actions);
            }
        }

        result.setTag("Actions", registry);
        // The original Blockbuster reads actions from Frames. Do not
        // introduce or regenerate editor-only ActionTimeline.
        return result;
    }
}
