package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Explicit Ctrl+Shift+E export into the actor's native Blockbuster records/.
 * Verifies the new NBT before replacing the Record and stores a unique
 * backup of the previous .dat. Ctrl+S remains editor-project-only.
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
            /*
             * Dispatch by actual Metamorph type, not NBT name prefixes:
             * Emoticons uses AnimatedMorph.Pose, while Blockbuster uses
             * CustomMorph.CustomPose. Keep these formats independent.
             */
            net.minecraft.nbt.NBTTagCompound starting =
                    actorData.getActor().getMorph();
            mchorse.metamorph.api.morphs.AbstractMorph source =
                    starting == null ? null
                    : mchorse.metamorph.api.MorphManager.INSTANCE
                            .morphFromNBT(starting.copy());

            if (source instanceof
                    mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
            {
                actions = EditorEmoticonsPoseExport.prepare(
                        state, actorData, actions);
            }
            else if (source != null && source.getClass().getName()
                    .endsWith(".ChameleonMorph"))
            {
                actions = EditorChameleonPoseExport.prepare(
                        state, actorData, actions);
            }
            else
            {
                actions = EditorBlockbusterPoseActions.prepare(
                        state, actorData, actions);
            }
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

        /*
         * Record exports belong to the exact native records/<actor>.dat path.
         * Do not overwrite it until the complete new compressed NBT has
         * passed a read-back check. Both temp and destination reside on the
         * same filesystem so the final replacement can be atomic.
         */
        File destination = original.getCanonicalFile();
        File records = destination.getParentFile();
        if (records == null || !records.isDirectory())
        {
            throw new IOException("Records directory not found: " + records);
        }

        Path temporary = Files.createTempFile(
                records.toPath(), actorData.getId() + ".", ".tmp");
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
                throw new IOException("Record export verification failed; original untouched");
            }

            /*
             * Every successful overwrite keeps the preceding native Record.
             * CREATE_NEW prevents even two exports in the same millisecond
             * from silently overwriting an older backup.
             */
            File backupDirectory = new File(
                    new File(world, "editor"), "record-backups");
            if (!backupDirectory.isDirectory() && !backupDirectory.mkdirs())
            {
                throw new IOException("Cannot create backup directory: " + backupDirectory);
            }

            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            String prefix = actorData.getId() + "-" + stamp + "-";
            Path backup = Files.createTempFile(
                    backupDirectory.toPath(), prefix, ".dat");
            boolean backupComplete = false;
            try
            {
                Files.copy(original.toPath(), backup,
                        StandardCopyOption.REPLACE_EXISTING);
                backupComplete = true;
            }
            finally
            {
                if (!backupComplete) Files.deleteIfExists(backup);
            }

            try
            {
                Files.move(temporary, destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                throw new IOException(
                        "Atomic Record replacement not supported; original untouched. "
                        + "Backup saved at " + backup, e);
            }

            return destination;
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
