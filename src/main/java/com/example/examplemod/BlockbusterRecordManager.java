package com.example.examplemod;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BlockbusterRecordManager
{
    private BlockbusterRecord currentRecord;
    private File currentFile;

    public BlockbusterRecordManager()
    {
        this.currentRecord = null;
        this.currentFile = null;
    }

    /*
     * ---------------------------------------------------------
     * WORLD PATH
     * ---------------------------------------------------------
     */

    /**
     * Returns the current Minecraft world's save directory.
     *
     * Example:
     *
     * saves/MyWorld
     */
    public File getWorldDirectory()
    {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Minecraft instance is null"
            );

            return null;
        }

        /*
         * -----------------------------------------------------
         * Preferred source:
         * integrated server world
         * -----------------------------------------------------
         */

        if (minecraft.getIntegratedServer() != null)
        {
            try
            {
                net.minecraft.world.WorldServer world =
                        minecraft.getIntegratedServer()
                                .getWorld(0);

                if (world != null)
                {
                    if (world.getSaveHandler() != null)
                    {
                        File directory =
                                world.getSaveHandler()
                                        .getWorldDirectory();

                        if (directory != null)
                        {
                            System.out.println(
                                    "[BBS Animation Editor] "
                                            + "World directory: "
                                            + directory
                                            .getAbsolutePath()
                            );

                            return directory;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                System.err.println(
                        "[BBS Animation Editor] "
                                + "Could not get integrated server "
                                + "world directory"
                );

                e.printStackTrace();
            }
        }

        /*
         * -----------------------------------------------------
         * Fallback:
         * client world
         * -----------------------------------------------------
         */

        if (minecraft.world != null)
        {
            if (minecraft.world.getSaveHandler() != null)
            {
                File directory =
                        minecraft.world
                                .getSaveHandler()
                                .getWorldDirectory();

                if (directory != null)
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Fallback world directory: "
                                    + directory
                                    .getAbsolutePath()
                    );

                    return directory;
                }
            }
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "World directory is unavailable"
        );

        return null;
    }

    /**
     * Returns Blockbuster's world-local directory.
     *
     * <world>/blockbuster/
     */
    public File getBlockbusterDirectory()
    {
        File worldDirectory =
                this.getWorldDirectory();

        if (worldDirectory == null)
        {
            return null;
        }

        return new File(
                worldDirectory,
                "blockbuster"
        );
    }

    /**
     * Returns the directory containing
     * Blockbuster player recordings.
     *
     * <world>/blockbuster/records/
     */
    public File getRecordingsDirectory()
    {
        File blockbusterDirectory =
                this.getBlockbusterDirectory();

        if (blockbusterDirectory == null)
        {
            return null;
        }

        return new File(
                blockbusterDirectory,
                "records"
        );
    }

    /**
     * Returns Blockbuster's scene directory.
     *
     * <world>/blockbuster/scenes/
     *
     * We don't use scenes yet, but keeping this
     * accessor here will be useful later when
     * we connect the editor to Blockbuster scenes.
     */
    public File getScenesDirectory()
    {
        File blockbusterDirectory =
                this.getBlockbusterDirectory();

        if (blockbusterDirectory == null)
        {
            return null;
        }

        return new File(
                blockbusterDirectory,
                "scenes"
        );
    }

    /*
     * ---------------------------------------------------------
     * DIRECTORY
     * ---------------------------------------------------------
     */

    public boolean ensureRecordingsDirectory()
    {
        File directory =
                this.getRecordingsDirectory();

        if (directory == null)
        {
            return false;
        }

        if (directory.exists())
        {
            return directory.isDirectory();
        }

        return directory.mkdirs();
    }

    /*
     * ---------------------------------------------------------
     * RECORDING FILES
     * ---------------------------------------------------------
     */

    public List<File> getRecordingFiles()
    {
        List<File> recordings =
                new ArrayList<File>();

        File directory =
                this.getRecordingsDirectory();

        System.out.println(
                "[BBS Animation Editor] "
                        + "Searching Blockbuster recordings..."
        );

        if (directory == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Recordings directory is null"
            );

            return recordings;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Recordings directory: "
                        + directory.getAbsolutePath()
        );

        if (!directory.exists())
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Recordings directory does not exist"
            );

            return recordings;
        }

        if (!directory.isDirectory())
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Recordings path is not a directory"
            );

            return recordings;
        }

        File[] files =
                directory.listFiles();

        if (files == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "directory.listFiles() returned null"
            );

            return recordings;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Files found in records directory: "
                        + files.length
        );

        for (File file : files)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Found: "
                            + file.getName()
            );

            if (!file.isFile())
            {
                continue;
            }

            String name =
                    file.getName()
                            .toLowerCase();

            if (name.endsWith(".dat"))
            {
                recordings.add(file);
            }
        }

        Collections.sort(
                recordings,
                new Comparator<File>()
                {
                    @Override
                    public int compare(
                            File first,
                            File second)
                    {
                        return first.getName()
                                .compareToIgnoreCase(
                                        second.getName()
                                );
                    }
                }
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Blockbuster recordings found: "
                        + recordings.size()
        );

        return recordings;
    }

    public int getRecordingCount()
    {
        return this.getRecordingFiles()
                .size();
    }

    /*
     * ---------------------------------------------------------
     * LOAD
     * ---------------------------------------------------------
     */

    public BlockbusterRecord load(
            File file)
            throws IOException
    {
        if (file == null)
        {
            throw new IllegalArgumentException(
                    "File cannot be null"
            );
        }

        if (!file.exists())
        {
            throw new IOException(
                    "Recording does not exist: "
                            + file.getAbsolutePath()
            );
        }

        if (!file.isFile())
        {
            throw new IOException(
                    "Recording is not a file: "
                            + file.getAbsolutePath()
            );
        }

        BlockbusterRecord record =
                BlockbusterRecordIO.load(
                        file
                );

        this.currentRecord =
                record;

        this.currentFile =
                file;

        System.out.println(
                "[BBS Animation Editor] "
                        + "Current Blockbuster record: "
                        + file.getAbsolutePath()
        );

        return record;
    }

    public BlockbusterRecord load(
            String filename)
            throws IOException
    {
        if (
                filename == null ||
                        filename.isEmpty()
        )
        {
            throw new IllegalArgumentException(
                    "Filename cannot be empty"
            );
        }

        File directory =
                this.getRecordingsDirectory();

        if (directory == null)
        {
            throw new IOException(
                    "Minecraft world is not loaded"
            );
        }

        File file =
                new File(
                        directory,
                        filename
                );

        return this.load(file);
    }

    /*
     * ---------------------------------------------------------
     * UNLOAD
     * ---------------------------------------------------------
     */

    public void unload()
    {
        this.currentRecord = null;
        this.currentFile = null;

        System.out.println(
                "[BBS Animation Editor] "
                        + "Blockbuster record unloaded"
        );
    }

    /*
     * ---------------------------------------------------------
     * SAVE
     * ---------------------------------------------------------
     */

    public void save()
            throws IOException
    {
        if (this.currentRecord == null)
        {
            throw new IOException(
                    "There is no loaded Blockbuster record"
            );
        }

        if (this.currentFile == null)
        {
            throw new IOException(
                    "Current Blockbuster record has no file"
            );
        }

        BlockbusterRecordIO.save(
                this.currentRecord,
                this.currentFile
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Current record saved"
        );
    }

    public void saveAs(
            File file)
            throws IOException
    {
        if (this.currentRecord == null)
        {
            throw new IOException(
                    "There is no loaded Blockbuster record"
            );
        }

        if (file == null)
        {
            throw new IllegalArgumentException(
                    "File cannot be null"
            );
        }

        BlockbusterRecordIO.save(
                this.currentRecord,
                file
        );

        this.currentFile =
                file;

        System.out.println(
                "[BBS Animation Editor] "
                        + "Record saved as: "
                        + file.getAbsolutePath()
        );
    }

    /*
     * ---------------------------------------------------------
     * CURRENT RECORD
     * ---------------------------------------------------------
     */

    public boolean hasCurrentRecord()
    {
        return this.currentRecord != null;
    }

    public BlockbusterRecord getCurrentRecord()
    {
        return this.currentRecord;
    }

    public File getCurrentFile()
    {
        return this.currentFile;
    }

    public String getCurrentFilename()
    {
        if (this.currentRecord == null)
        {
            return null;
        }

        return this.currentRecord.getFilename();
    }

    /*
     * ---------------------------------------------------------
     * FRAMES
     * ---------------------------------------------------------
     */

    public int getCurrentLength()
    {
        if (this.currentRecord == null)
        {
            return 0;
        }

        return this.currentRecord.getLength();
    }

    public BlockbusterRecordFrame getFrame(
            int tick)
    {
        if (this.currentRecord == null)
        {
            return null;
        }

        return this.currentRecord.getFrame(
                tick
        );
    }

    public boolean hasFrame(
            int tick)
    {
        return this.getFrame(tick) != null;
    }

    public BlockbusterRecordFrame getOrCreateFrame(
            int tick)
    {
        if (this.currentRecord == null)
        {
            return null;
        }

        return this.currentRecord
                .getOrCreateFrame(
                        tick
                );
    }

    public void setFrame(
            int tick,
            BlockbusterRecordFrame frame)
    {
        if (this.currentRecord == null)
        {
            return;
        }

        this.currentRecord.setFrame(
                tick,
                frame
        );
    }
}