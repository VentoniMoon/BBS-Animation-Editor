package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Opt-in first-stage Character export. Produces a separate Record copy in
 * <world>/editor/exports/. Source recordings and Ctrl+S are untouched.
 */
public final class EditorCharacterRecordExport
{
    private EditorCharacterRecordExport() {}

    public static File exportSelected(EditorSceneState state) throws IOException
    {
        if (state == null || state.getSceneManager() == null)
        {
            throw new IOException("No editor scene");
        }

        BlockbusterSceneActorData actorData = state.getSelectedActorData();
        if (actorData == null || actorData.getActor() == null || !actorData.hasRecord())
        {
            throw new IOException("Select an actor with a Record");
        }

        File original = state.getSceneManager().getRecordFile(actorData.getActor());
        if (original == null || !original.isFile())
        {
            throw new IOException("Original actor Record not found");
        }

        List<EditorCharacterMorphExport.PreparedAction> actions =
                EditorCharacterMorphExport.prepare(actorData);
        /*
         * Pose export is opt-in through the same explicit shortcut.
         * For native Blockbuster models the Pose action contains the
         * active Character morph and the sampled bone transforms.
         * No changes to the old Character-only path for other models.
         */
        ActorAnimationData pose = state.getAnimationData() == null
                ? null : state.getAnimationData().getActor(actorData.getId());
        boolean hasPose = false;
        if (pose != null)
        {
            for (AnimationBone bone : pose.getBones())
            {
                if (bone != null && bone.getKeyframes() != null
                        && !bone.getKeyframes().isEmpty())
                {
                    hasPose = true;
                    break;
                }
            }
        }

        BodyPartsEditorController parts = state.getBodyPartsController();
        boolean hasBodyParts = parts != null && !parts.getModels().isEmpty();

        /*
         * BodyParts may have independent tracks even when the actor has
         * no Pose keyframes. Bake both through the same native morph path.
         */
        if (hasPose || hasBodyParts)
        {
            actions = EditorBlockbusterPoseActions.prepare(
                    state, actorData, actions);
        }

        if (actions.isEmpty())
        {
            throw new IOException("No exportable Character or Pose keys for this actor");
        }

        NBTTagCompound originalNbt;
        try (FileInputStream input = new FileInputStream(original))
        {
            originalNbt = CompressedStreamTools.readCompressed(input);
        }

        NBTTagCompound merged = EditorCharacterMorphMerge.merge(originalNbt, actions);

        File world = state.getSceneManager().getWorldDirectory();
        if (world == null) throw new IOException("World directory unavailable");

        File folder = new File(new File(world, "editor"), "exports");
        if (!folder.isDirectory() && !folder.mkdirs())
        {
            throw new IOException("Cannot create export directory: " + folder);
        }

        File destination = new File(folder, actorData.getId() + ".dat");
        Path temporary = Files.createTempFile(folder.toPath(), actorData.getId() + ".", ".tmp");
        try
        {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile()))
            {
                CompressedStreamTools.writeCompressed(merged, output);
            }

            NBTTagCompound verification;
            try (FileInputStream input = new FileInputStream(temporary.toFile()))
            {
                verification = CompressedStreamTools.readCompressed(input);
            }
            if (verification == null || !verification.equals(merged))
            {
                throw new IOException("Export verification failed");
            }

            Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return destination;
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
