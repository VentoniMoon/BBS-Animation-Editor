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

/**
 * Единая система сохранения редактора.
 *
 * Отвечает за:
 *
 * - dirty state;
 * - сохранение Blockbuster Scene;
 * - сохранение Blockbuster Records;
 * - сохранение данных BBS Animation Editor;
 * - загрузку данных редактора.
 *
 * UI-состояние сюда не входит.
 */
public class EditorSaveController
{
    private static final String EDITOR_DIRECTORY =
            "editor";

    private static final String EDITOR_FILE_SUFFIX =
            ".editor.dat";

    private final EditorSceneState state;

    private boolean dirty;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public EditorSaveController(
            EditorSceneState state)
    {
        this.state = state;
        this.dirty = false;
    }


    public EditorSceneState getState()
    {
        return this.state;
    }


    /*
     * =========================================================
     * DIRTY
     * =========================================================
     */

    public boolean isDirty()
    {
        return this.dirty;
    }


    public void markDirty()
    {
        this.dirty = true;
    }


    public void markClean()
    {
        this.dirty = false;
    }


    /*
     * =========================================================
     * EDITOR DIRECTORY
     * =========================================================
     */

    /**
     * Возвращает:
     *
     * <world>/blockbuster/editor/
     */
    public File getEditorDirectory()
    {
        if (this.state == null)
        {
            return null;
        }

        File worldDirectory =
                this.state
                        .getSceneManager()
                        .getWorldDirectory();

        if (worldDirectory == null)
        {
            return null;
        }

        return new File(
                worldDirectory,
                EDITOR_DIRECTORY
        );
    }


    /*
     * =========================================================
     * EDITOR FILE
     * =========================================================
     */

    /**
     * Возвращает editor-файл текущей сцены.
     *
     * Например:
     *
     * scenes/1341.dat
     *
     * превращается в:
     *
     * editor/1341.editor.dat
     */
    public File getEditorFile()
    {
        if (this.state == null)
        {
            return null;
        }

        BlockbusterSceneManager manager =
                this.state.getSceneManager();

        if (manager == null)
        {
            return null;
        }

        File sceneFile =
                manager.getCurrentFile();

        if (sceneFile == null)
        {
            return null;
        }

        File directory =
                this.getEditorDirectory();

        if (directory == null)
        {
            return null;
        }

        String name =
                sceneFile.getName();

        int extension =
                name.lastIndexOf('.');

        if (extension > 0)
        {
            name =
                    name.substring(
                            0,
                            extension
                    );
        }

        return new File(
                directory,
                name
                        + EDITOR_FILE_SUFFIX
        );
    }


    /*
     * =========================================================
     * SAVE EVERYTHING
     * =========================================================
     */

    /**
     * Сохраняет всё состояние текущей сцены.
     *
     * Порядок:
     *
     * 1. Blockbuster Scene
     * 2. Blockbuster Records
     * 3. Editor data
     *
     * После успешного сохранения dirty = false.
     */
    public void save()
            throws IOException
    {
        if (this.state == null)
        {
            throw new IOException(
                    "Editor state is null."
            );
        }

        BlockbusterSceneManager manager =
                this.state.getSceneManager();

        if (manager == null)
        {
            throw new IOException(
                    "Scene manager is null."
            );
        }

        if (!manager.hasCurrentScene())
        {
            throw new IOException(
                    "There is no loaded scene."
            );
        }


        /*
         * Save editor keyframes FIRST. A failure to save original
         * scene/records must never prevent editor-only Pose,
         * Character and Body Parts changes from reaching disk.
         */
        saveEditorData();

        /*
         * The original scene/record save remains a separate step.
         * If it fails, the user receives an error, but the editor
         * keyframes have already been preserved.
         */
        manager.save();
        saveRecords();

        this.dirty = false;
    }


    /*
     * =========================================================
     * SAVE RECORDS
     * =========================================================
     */

    private void saveRecords()
            throws IOException
    {
        BlockbusterSceneManager manager =
                this.state.getSceneManager();

        for (
                BlockbusterSceneActorData actorData :
                this.state.getActors()
        )
        {
            if (actorData == null)
            {
                continue;
            }

            if (!actorData.hasRecord())
            {
                continue;
            }

            BlockbusterSceneActor actor =
                    actorData.getActor();

            if (actor == null)
            {
                continue;
            }

            BlockbusterRecord record =
                    actorData.getRecord();

            if (record == null)
            {
                continue;
            }

            File recordFile =
                    manager.getRecordFile(
                            actor
                    );

            if (recordFile == null)
            {
                continue;
            }


            /*
             * Единая точка записи Record.
             *
             * Здесь больше нет ручного создания
             * NBTTagCompound и FileOutputStream.
             */

            BlockbusterRecordIO.save(
                    record,
                    recordFile
            );
        }
    }


    /*
     * =========================================================
     * SAVE EDITOR DATA
     * =========================================================
     */

    private void saveEditorData()
            throws IOException
    {
        File file =
                this.getEditorFile();

        if (file == null)
        {
            throw new IOException(
                    "Editor file cannot be determined."
            );
        }


        /*
         * Создаём:
         *
         * <world>/blockbuster/editor/
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
                        "Could not create editor directory: "
                                + parent.getAbsolutePath()
                );
            }
        }


        /*
         * EditorSceneSerializer отвечает только
         * за данные самого редактора.
         */

        NBTTagCompound root =
                EditorSceneSerializer.write(
                        this.state
                );


        /*
         * Записываем editor.dat.
         */

        File absolute = file.getAbsoluteFile();
        Path temp = Files.createTempFile(
                absolute.getParentFile().toPath(),
                absolute.getName() + ".", ".tmp");
        try
        {
            try (FileOutputStream output = new FileOutputStream(temp.toFile()))
            {
                CompressedStreamTools.writeCompressed(root, output);
                // Closing the stream flushes Java-side buffers. The
                // subsequent NBT read-back validates the saved bytes.
                // FileDescriptor.sync() fails on some Windows/Java 8
                // configurations even after a successful write.
            }

            NBTTagCompound verified;
            try (FileInputStream input = new FileInputStream(temp.toFile()))
            {
                verified = CompressedStreamTools.readCompressed(input);
            }

            if (verified == null || !verified.equals(root))
            {
                throw new IOException("Editor NBT verification failed: " + absolute);
            }

            try
            {
                Files.move(temp, absolute.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException unsupported)
            {
                Files.move(temp, absolute.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("[BBS Animation Editor] Saved editor keyframes: "
                    + absolute.getAbsolutePath());
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }


    /*
     * =========================================================
     * LOAD EDITOR DATA
     * =========================================================
     */

    /**
     * Загружает данные редактора для уже открытой
     * Blockbuster Scene.
     *
     * Если editor-файла нет, это нормально.
     *
     * В таком случае сцена считается старой сценой,
     * для которой ещё нет данных BBS Animation Editor.
     */
    public void loadEditorData()
            throws IOException
    {
        File file =
                this.getEditorFile();

        if (file == null)
        {
            this.dirty = false;

            return;
        }

        if (!file.exists())
        {
            /*
             * Старого editor-файла нет.
             *
             * Это нормальная ситуация.
             *
             * Blockbuster Scene всё равно уже загружена,
             * а editor-only данные просто остаются пустыми.
             */

            this.dirty = false;

            return;
        }


        if (!file.isFile())
        {
            throw new IOException(
                    "Editor path is not a file: "
                            + file.getAbsolutePath()
            );
        }


        /*
         * -----------------------------------------------------
         * READ
         * -----------------------------------------------------
         */

        FileInputStream input =
                new FileInputStream(
                        file
                );

        try
        {
            NBTTagCompound root =
                    CompressedStreamTools
                            .readCompressed(
                                    input
                            );

            if (root == null)
            {
                throw new IOException(
                        "Editor file contains no data: "
                                + file.getAbsolutePath()
                );
            }


            /*
             * Передаём NBT в единый сериализатор редактора.
             */

            EditorSceneSerializer.read(
                    this.state,
                    root
            );
        }
        finally
        {
            input.close();
        }


        /*
         * Загрузка не является изменением данных.
         */

        this.dirty = false;
    }


    /*
     * =========================================================
     * SAVE EDITOR DATA ONLY
     * =========================================================
     */

    /**
     * Сохраняет только editor.dat.
     *
     * Blockbuster Scene и Records при этом
     * не перезаписываются.
     *
     * Используется, когда нужно сохранить только
     * данные BBS Animation Editor.
     */
    public void saveEditorDataOnly()
            throws IOException
    {
        if (this.state == null)
        {
            throw new IOException(
                    "Editor state is null."
            );
        }

        BlockbusterSceneManager manager =
                this.state.getSceneManager();

        if (manager == null ||
                !manager.hasCurrentScene())
        {
            throw new IOException(
                    "There is no loaded scene."
            );
        }

        saveEditorData();

        this.dirty = false;
    }
}