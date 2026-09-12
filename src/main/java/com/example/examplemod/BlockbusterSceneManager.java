package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Управление Blockbuster Scene файлами.
 */
public class BlockbusterSceneManager
{
    private BlockbusterScene currentScene;
    private File currentFile;

    private final List<BlockbusterSceneActorData> actorData;

    public BlockbusterSceneManager()
    {
        this.currentScene = null;
        this.currentFile = null;

        this.actorData =
                new ArrayList<BlockbusterSceneActorData>();
    }

    /*
     * ---------------------------------------------------------
     * WORLD DIRECTORY
     * ---------------------------------------------------------
     */

    public File getWorldDirectory()
    {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft == null)
        {
            return null;
        }

        if (!minecraft.isSingleplayer())
        {
            return null;
        }

        if (minecraft.getIntegratedServer() == null)
        {
            return null;
        }

        if (
                minecraft.getIntegratedServer()
                        .getWorld(0) == null
        )
        {
            return null;
        }

        if (
                minecraft.getIntegratedServer()
                        .getWorld(0)
                        .getSaveHandler() == null
        )
        {
            return null;
        }

        return minecraft
                .getIntegratedServer()
                .getWorld(0)
                .getSaveHandler()
                .getWorldDirectory();
    }

    /*
     * ---------------------------------------------------------
     * SCENES DIRECTORY
     * ---------------------------------------------------------
     */

    public File getScenesDirectory()
    {
        File worldDirectory =
                this.getWorldDirectory();

        if (worldDirectory == null)
        {
            return null;
        }

        return new File(
                new File(
                        worldDirectory,
                        "blockbuster"
                ),
                "scenes"
        );
    }

    /*
     * ---------------------------------------------------------
     * RECORDS DIRECTORY
     * ---------------------------------------------------------
     */

    public File getRecordsDirectory()
    {
        File worldDirectory =
                this.getWorldDirectory();

        if (worldDirectory == null)
        {
            return null;
        }

        return new File(
                new File(
                        worldDirectory,
                        "blockbuster"
                ),
                "records"
        );
    }

    /*
     * ---------------------------------------------------------
     * SCENE FILES
     * ---------------------------------------------------------
     */

    public List<File> getSceneFiles()
    {
        List<File> result =
                new ArrayList<File>();

        File directory =
                this.getScenesDirectory();

        if (directory == null)
        {
            return result;
        }

        if (!directory.exists())
        {
            return result;
        }

        if (!directory.isDirectory())
        {
            return result;
        }

        File[] files =
                directory.listFiles();

        if (files == null)
        {
            return result;
        }

        for (File file : files)
        {
            if (file == null)
            {
                continue;
            }

            if (!file.isFile())
            {
                continue;
            }

            if (
                    file.getName()
                            .toLowerCase()
                            .endsWith(".dat")
            )
            {
                result.add(file);
            }
        }

        Collections.sort(
                result,
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

        return result;
    }

    /*
     * ---------------------------------------------------------
     * LOAD
     * ---------------------------------------------------------
     */

    public BlockbusterScene load(
            File file)
            throws IOException
    {
        if (file == null)
        {
            throw new IllegalArgumentException(
                    "Scene file cannot be null"
            );
        }

        if (!file.exists())
        {
            throw new IOException(
                    "Scene does not exist: "
                            + file.getAbsolutePath()
            );
        }

        FileInputStream input =
                new FileInputStream(
                        file
                );

        try
        {
            NBTTagCompound compound =
                    CompressedStreamTools
                            .readCompressed(
                                    input
                            );

            BlockbusterScene scene =
                    new BlockbusterScene(
                            file.getName()
                    );

            scene.fromNBT(
                    compound
            );

            this.currentScene =
                    scene;

            this.currentFile =
                    file;

            /*
             * После загрузки самой сцены
             * загружаем Records всех её актеров.
             */
            this.loadActorData();

            return scene;
        }
        finally
        {
            input.close();
        }
    }

    public BlockbusterScene load(
            String filename)
            throws IOException
    {
        File directory =
                this.getScenesDirectory();

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

        return this.load(
                file
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
        if (this.currentScene == null)
        {
            throw new IOException(
                    "There is no loaded scene"
            );
        }

        if (this.currentFile == null)
        {
            throw new IOException(
                    "Current scene has no file"
            );
        }

        this.save(
                this.currentScene,
                this.currentFile
        );
    }

    public void save(
            BlockbusterScene scene,
            File file)
            throws IOException
    {
        if (scene == null)
        {
            throw new IllegalArgumentException(
                    "Scene cannot be null"
            );
        }

        if (file == null)
        {
            throw new IllegalArgumentException(
                    "Scene file cannot be null"
            );
        }

        File parent =
                file.getParentFile();

        if (
                parent != null &&
                        !parent.exists()
        )
        {
            parent.mkdirs();
        }

        NBTTagCompound compound =
                scene.toNBT();

        FileOutputStream output =
                new FileOutputStream(
                        file
                );

        try
        {
            CompressedStreamTools
                    .writeCompressed(
                            compound,
                            output
                    );
        }
        finally
        {
            output.close();
        }

        this.currentScene =
                scene;

        this.currentFile =
                file;
    }

    /*
     * ---------------------------------------------------------
     * CURRENT SCENE
     * ---------------------------------------------------------
     */

    public BlockbusterScene
    getCurrentScene()
    {
        return this.currentScene;
    }

    public File getCurrentFile()
    {
        return this.currentFile;
    }

    public boolean hasCurrentScene()
    {
        return this.currentScene != null;
    }

    public void unload()
    {
        this.currentScene = null;
        this.currentFile = null;
        this.actorData.clear();
    }

    /*
     * ---------------------------------------------------------
     * RECORD LINK
     * ---------------------------------------------------------
     *
     * Scene Actor Id:
     *
     *     1341_1
     *
     * Record:
     *
     *     records/1341_1.dat
     */

    public File getRecordFile(
            BlockbusterSceneActor actor)
    {
        if (actor == null)
        {
            return null;
        }

        File directory =
                this.getRecordsDirectory();

        if (directory == null)
        {
            return null;
        }

        return new File(
                directory,
                actor.getId()
                        + ".dat"
        );
    }

    /**
     * Загружает Blockbuster Record,
     * связанный с указанным актером сцены.
     */
    public BlockbusterRecord loadActorRecord(
            BlockbusterSceneActor actor)
            throws IOException
    {
        if (actor == null)
        {
            throw new IllegalArgumentException(
                    "Actor cannot be null"
            );
        }

        File recordFile =
                this.getRecordFile(actor);

        if (recordFile == null)
        {
            throw new IOException(
                    "Records directory is not available"
            );
        }

        if (!recordFile.exists())
        {
            throw new IOException(
                    "Record does not exist: "
                            + recordFile.getAbsolutePath()
            );
        }

        BlockbusterRecord record =
                BlockbusterRecordIO.load(
                        recordFile
                );

        return record;
    }

    /**
     * Загружает данные всех актеров
     * текущей сцены.
     */
    public void loadActorData()
            throws IOException
    {
        if (this.currentScene == null)
        {
            throw new IOException(
                    "There is no loaded scene"
            );
        }

        this.actorData.clear();

        for (
                BlockbusterSceneActor actor :
                this.currentScene.getActors()
        )
        {
            BlockbusterSceneActorData data =
                    new BlockbusterSceneActorData(
                            actor
                    );

            File recordFile =
                    this.getRecordFile(
                            actor
                    );

            if (
                    recordFile != null &&
                            recordFile.exists()
            )
            {
                try
                {
                    BlockbusterRecord record =
                            this.loadActorRecord(
                                    actor
                            );

                    data.setRecord(
                            record
                    );

                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Loaded actor record: "
                                    + actor.getId()
                                    + " -> "
                                    + recordFile.getName()
                    );
                }
                catch (IOException exception)
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Failed to load actor record: "
                                    + actor.getId()
                                    + " -> "
                                    + exception.getMessage()
                    );
                }
            }
            else
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Record not found for actor: "
                                + actor.getId()
                );
            }

            this.actorData.add(
                    data
            );
        }
    }

    /**
     * Возвращает данные всех актеров
     * текущей сцены.
     */
    public List<BlockbusterSceneActorData> getActorData()
    {
        return this.actorData;
    }

    /**
     * Возвращает данные актера
     * по его идентификатору.
     */
    public BlockbusterSceneActorData getActorData(
            String actorId)
    {
        if (actorId == null)
        {
            return null;
        }

        for (
                BlockbusterSceneActorData data :
                this.actorData
        )
        {
            if (data == null)
            {
                continue;
            }

            if (
                    actorId.equals(
                            data.getId()
                    )
            )
            {
                return data;
            }
        }

        return null;
    }
}