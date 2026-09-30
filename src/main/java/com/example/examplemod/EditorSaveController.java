package com.example.examplemod;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
         * -----------------------------------------------------
         * 1. ORIGINAL BLOCKBUSTER SCENE
         * -----------------------------------------------------
         */

        manager.save();


        /*
         * -----------------------------------------------------
         * 2. BLOCKBUSTER RECORDS
         * -----------------------------------------------------
         *
         * Используем BlockbusterRecordIO как единственную
         * систему сериализации Record.
         */

        saveRecords();


        /*
         * -----------------------------------------------------
         * 3. EDITOR DATA
         * -----------------------------------------------------
         *
         * Character Timeline
         * Pose / AnimationData
         * Scene editor position
         * и будущие editor-only данные.
         */

        saveEditorData();


        /*
         * Только после успешного завершения всех
         * трёх операций считаем состояние сохранённым.
         */

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

        FileOutputStream output =
                new FileOutputStream(
                        file
                );

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