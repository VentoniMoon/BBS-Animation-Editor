package com.example.examplemod;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class BlockbusterRecordIO
{
    public static final short SIGNATURE = 148;


    /*
     * LOAD
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
         * Check format version.
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
         * Build model.
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
     * SAVE
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
         * Create parent directory.
         */

        File parent =
                file.getParentFile();

        if (
                parent != null &&
                        !parent.exists()
        )
        {
            if (!parent.mkdirs() &&
                    !parent.exists())
            {
                throw new IOException(
                        "Could not create directory: "
                                + parent
                                .getAbsolutePath()
                );
            }
        }


        /*
         * Root NBT.
         */

        NBTTagCompound root =
                new NBTTagCompound();


        /*
         * Version.
         *
         * Blockbuster 1.12 uses signature 148.
         */

        root.setShort(
                "Version",
                SIGNATURE
        );


        /*
         * Delays.
         */

        root.setInteger(
                "PreDelay",
                record.getPreDelay()
        );

        root.setInteger(
                "PostDelay",
                record.getPostDelay()
        );


        /*
         * PlayerData.
         */

        if (record.getPlayerData() != null)
        {
            root.setTag(
                    "PlayerData",
                    record.getPlayerData()
            );
        }

        /*
         * Action registry
         *
         * Original Blockbuster stores the mapping
         * between numeric action IDs and their names
         * in the root "Actions" compound.
         */
        if (record.getActionRegistry() != null)
        {
            root.setTag(
                    "Actions",
                    record.getActionRegistry().toNBT()
            );
        }


        /*
         * Frames.
         */

        NBTTagList frameList =
                new NBTTagList();

        for (
                BlockbusterRecordFrame frame :
                record.getFrames()
        )
        {
            if (frame == null)
            {
                /*
                 * The original Blockbuster format
                 * expects compound tags in Frames.
                 *
                 * A null frame therefore becomes
                 * an empty frame compound.
                 */
                frameList.appendTag(
                        new NBTTagCompound()
                );
            }
            else
            {
                frameList.appendTag(
                        frame.toNBT()
                );
            }
        }

        root.setTag(
                "Frames",
                frameList
        );


        /*
         * Root-level Actions
         *
         * IMPORTANT:
         *
         * We intentionally do not generate the
         * root "Actions" compound yet.
         *
         * We have confirmed that the real file
         * contains it, but its exact semantics
         * still need to be reproduced from the
         * original Blockbuster Record implementation.
         */


        /*
         * Write compressed NBT.
         */

        FileOutputStream output =
                new FileOutputStream(file);

        try
        {
            CompressedStreamTools
                    .writeCompressed(
                            root,
                            output
                    );
        }
        finally
        {
            output.close();
        }


        System.out.println(
                "[BBS Animation Editor] "
                        + "Saved Blockbuster record: "
                        + file.getAbsolutePath()
        );
    }
}