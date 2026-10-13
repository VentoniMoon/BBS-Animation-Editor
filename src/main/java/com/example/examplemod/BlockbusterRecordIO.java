package com.example.examplemod;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

public class BlockbusterRecordIO
{
    public static final short SIGNATURE = 148;

    /*
     * ---------------------------------------------------------
     * LOAD
     * ---------------------------------------------------------
     */

    public static BlockbusterRecord load(
            File file
    ) throws IOException
    {
        if (file == null)
        {
            throw new IOException(
                    "Record file is null."
            );
        }

        if (!file.exists())
        {
            throw new IOException(
                    "Record file does not exist: "
                            + file.getAbsolutePath()
            );
        }

        if (!file.isFile())
        {
            throw new IOException(
                    "Record path is not a file: "
                            + file.getAbsolutePath()
            );
        }

        NBTTagCompound root;

        FileInputStream input =
                new FileInputStream(file);

        try
        {
            root =
                    CompressedStreamTools
                            .readCompressed(input);
        }
        finally
        {
            input.close();
        }

        /*
         * -----------------------------------------------------
         * VERSION
         * -----------------------------------------------------
         */

        short version =
                SIGNATURE;

        if (root.hasKey("Version", 2))
        {
            version =
                    root.getShort("Version");
        }

        if (version != SIGNATURE)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Warning: unexpected "
                            + "Blockbuster record version: "
                            + version
            );
        }

        /*
         * Remove .dat from filename.
         */

        String filename =
                file.getName();

        if (filename.endsWith(".dat"))
        {
            filename =
                    filename.substring(
                            0,
                            filename.length() - 4
                    );
        }

        /*
         * -----------------------------------------------------
         * BUILD MODEL
         * -----------------------------------------------------
         */

        BlockbusterRecord record =
                BlockbusterRecord.fromNBT(
                        filename,
                        root
                );

        record.setVersion(
                version
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Loaded Blockbuster record: "
                        + file.getName()
                        + " ("
                        + record.getLength()
                        + " frames)"
        );

        return record;
    }


    /*
     * ---------------------------------------------------------
     * SAVE
     * ---------------------------------------------------------
     */

    public static void save(
            BlockbusterRecord record,
            File file
    ) throws IOException
    {
        if (record == null)
        {
            throw new IOException(
                    "Record is null."
            );
        }

        if (file == null)
        {
            throw new IOException(
                    "Output file is null."
            );
        }

        /*
         * -----------------------------------------------------
         * CREATE DIRECTORY
         * -----------------------------------------------------
         */

        File parent =
                file.getParentFile();

        if (
                parent != null &&
                        !parent.exists()
        )
        {
            if (
                    !parent.mkdirs() &&
                            !parent.exists()
            )
            {
                throw new IOException(
                        "Could not create directory: "
                                + parent.getAbsolutePath()
                );
            }
        }

        /*
         * -----------------------------------------------------
         * BUILD ORIGINAL BLOCKBUSTER NBT
         * -----------------------------------------------------
         *
         * Всё содержимое Record теперь сериализуется
         * самим BlockbusterRecord.
         *
         * Это гарантирует:
         *
         * BlockbusterRecordIO.load()
         *        <->
         * BlockbusterRecordIO.save()
         *
         * используют одну и ту же модель.
         */

        NBTTagCompound root =
                record.toNBT();

        /*
         * -----------------------------------------------------
         * WRITE
         * -----------------------------------------------------
         */

        /*
         * Never truncate the original recording before the replacement
         * has been written and validated. Save a sibling temporary file
         * first, then preserve the old record as a timestamped backup.
         */
        File absoluteFile = file.getAbsoluteFile();
        File directory = absoluteFile.getParentFile();
        Path destination = absoluteFile.toPath();
        Path temporary = Files.createTempFile(
                directory.toPath(), absoluteFile.getName() + ".", ".tmp");

        boolean replaced = false;

        try
        {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile()))
            {
                CompressedStreamTools.writeCompressed(root, output);
                // Closing the stream flushes Java-side buffers. The
                // temporary file is immediately read back and verified.
                // Avoid FileDescriptor.sync(), which can fail on Windows/Java 8.
            }

            NBTTagCompound verification;
            try (FileInputStream input = new FileInputStream(temporary.toFile()))
            {
                verification = CompressedStreamTools.readCompressed(input);
            }

            if (verification == null || !verification.equals(root))
            {
                throw new IOException("Record NBT verification failed: " + absoluteFile);
            }

            if (Files.exists(destination))
            {
                Path backup = directory.toPath().resolve(
                        absoluteFile.getName() + ".backup-" + System.currentTimeMillis());
                Files.copy(destination, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }

            try
            {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            replaced = true;
        }
        finally
        {
            if (!replaced)
            {
                Files.deleteIfExists(temporary);
            }
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Saved Blockbuster record: "
                        + file.getAbsolutePath()
        );
    }
}