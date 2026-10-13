package com.example.examplemod;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Stage 7: safely merge prepared main-track Character MorphAction entries into
 * a COPY of the original Blockbuster Record NBT.
 *
 * No disk writes and no modifications of the source record are performed.
 * Only MorphActions on explicitly exported ticks are replaced in the COPY.
 * All other actions and all other ticks are preserved.
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

        /*
         * Validate the entire export before applying anything.
         * One final MorphAction is allowed per tick. Never silently
         * choose between two different prepared actions on one tick.
         */
        TreeMap<Integer, NBTTagCompound> replacementByTick =
                new TreeMap<Integer, NBTTagCompound>();
        for (EditorCharacterMorphExport.PreparedAction next : prepared)
        {
            if (next == null || next.nbt == null)
            {
                throw new IOException("Null Character morph export action");
            }
            if (next.frame < 0 || next.frame >= frames.tagCount())
            {
                throw new IOException("Morph key at tick " + next.frame
                        + " exceeds original record length " + frames.tagCount());
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
            if (!next.nbt.hasKey("Morph", 10))
            {
                throw new IOException("Missing Morph in action at tick " + next.frame);
            }

            NBTTagCompound frame = frames.getCompoundTagAt(next.frame);
            if (frame.hasKey("Action") && !frame.hasKey("Action", 9))
            {
                throw new IOException("Unexpected Action tag at tick " + next.frame);
            }

            NBTTagCompound previous =
                    replacementByTick.put(next.frame, next.nbt.copy());
            if (previous != null && !previous.equals(next.nbt))
            {
                throw new IOException("Multiple exported MorphActions at tick "
                        + next.frame);
            }
        }

        /*
         * Build each affected tick's Action list afresh. Original Morph
         * actions on that tick are replaced by the new exported state,
         * while equipment, hotbar and all other actions keep their order.
         */
        for (Map.Entry<Integer, NBTTagCompound> entry
                : replacementByTick.entrySet())
        {
            NBTTagCompound frame = frames.getCompoundTagAt(entry.getKey());
            NBTTagList existing = frame.getTagList("Action", 10);
            NBTTagList rewritten = new NBTTagList();
            byte exportedType = entry.getValue().getByte("Type");

            for (int i = 0; i < existing.tagCount(); i++)
            {
                NBTTagCompound action = existing.getCompoundTagAt(i);
                byte existingType = action.getByte("Type");
                boolean isMorph = existingType == exportedType
                        || "morph".equals(registry.getString(
                                Integer.toString(existingType & 255)));
                if (!isMorph)
                {
                    rewritten.appendTag(action.copy());
                }
            }

            rewritten.appendTag(entry.getValue().copy());
            frame.setTag("Action", rewritten);
            registry.setString(
                    Integer.toString(exportedType & 255), "morph");
        }

        result.setTag("Actions", registry);
        // The original Blockbuster reads actions from Frames. Do not
        // introduce or regenerate editor-only ActionTimeline.
        return result;
    }
}
