package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditorSceneState
{
    /*
     * =========================================================
     * CORE EDITOR STATE
     * =========================================================
     */

    private final BlockbusterSceneManager sceneManager;
    private final SceneAnimationData animationData;

    /*
     * Единая система сохранения редактора.
     *
     * Отвечает за:
     *
     * - Blockbuster Scene;
     * - Blockbuster Records;
     * - Character Timeline;
     * - Pose / AnimationData;
     * - другие данные редактора.
     */
    private final EditorSaveController saveController;


    /*
     * =========================================================
     * SCENE LIST
     * =========================================================
     */

    private List<File> sceneFiles;


    /*
     * =========================================================
     * SELECTION
     * =========================================================
     *
     * Эти данные являются UI-состоянием.
     *
     * Они НЕ сохраняются в editor.dat.
     */

    private int selectedScene;
    private int selectedActor;


    /*
     * =========================================================
     * SCENE POSITION
     * =========================================================
     *
     * Мировая позиция текущей сцены.
     */

    private double sceneX;
    private double sceneY;
    private double sceneZ;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public EditorSceneState()
    {
        /*
         * Blockbuster scene manager.
         */
        this.sceneManager =
                new BlockbusterSceneManager();


        /*
         * Animation data принадлежит
         * текущей открытой сцене.
         */
        this.animationData =
                new SceneAnimationData();


        /*
         * Save controller получает ссылку
         * на этот EditorSceneState.
         *
         * Создаётся после основных данных,
         * которыми он будет управлять.
         */
        this.saveController =
                new EditorSaveController(
                        this
                );


        /*
         * Список сцен.
         */
        this.sceneFiles =
                new ArrayList<File>();


        /*
         * Selection.
         */
        this.selectedScene = -1;
        this.selectedActor = -1;


        /*
         * Начальная позиция.
         */
        this.sceneX = 0.0D;
        this.sceneY = 0.0D;
        this.sceneZ = 0.0D;
    }


    /*
     * =========================================================
     * SCENE FILES
     * =========================================================
     */

    /**
     * Обновляет список доступных сцен.
     *
     * Если выбранная сцена больше не существует,
     * выбор сбрасывается.
     */
    public void refreshScenes()
    {
        this.sceneFiles =
                this.sceneManager.getSceneFiles();

        if (
                this.selectedScene < 0 ||
                        this.selectedScene >=
                                this.sceneFiles.size()
        )
        {
            this.selectedScene = -1;
            this.selectedActor = -1;
        }
    }


    /*
     * =========================================================
     * CORE GETTERS
     * =========================================================
     */

    public BlockbusterSceneManager getSceneManager()
    {
        return this.sceneManager;
    }


    public SceneAnimationData getAnimationData()
    {
        return this.animationData;
    }


    /**
     * Возвращает единую систему сохранения редактора.
     */
    public EditorSaveController getSaveController()
    {
        return this.saveController;
    }


    public List<File> getSceneFiles()
    {
        return this.sceneFiles;
    }


    /*
     * =========================================================
     * SCENE SELECTION
     * =========================================================
     */

    public int getSelectedScene()
    {
        return this.selectedScene;
    }


    public void setSelectedScene(
            int selectedScene)
    {
        if (
                selectedScene < 0 ||
                        selectedScene >=
                                this.sceneFiles.size()
        )
        {
            this.selectedScene = -1;
            this.selectedActor = -1;

            return;
        }

        this.selectedScene =
                selectedScene;
    }


    /*
     * =========================================================
     * ACTOR SELECTION
     * =========================================================
     */

    public int getSelectedActor()
    {
        return this.selectedActor;
    }


    public void setSelectedActor(
            int selectedActor)
    {
        List<BlockbusterSceneActorData> actors =
                getActors();

        if (
                selectedActor < 0 ||
                        selectedActor >=
                                actors.size()
        )
        {
            this.selectedActor = -1;

            return;
        }

        this.selectedActor =
                selectedActor;
    }


    public void resetSelection()
    {
        this.selectedScene = -1;
        this.selectedActor = -1;
    }


    /*
     * =========================================================
     * LOAD SCENE
     * =========================================================
     */

    /**
     * Загружает сцену по индексу из списка сцен.
     *
     * При смене сцены:
     *
     * 1. Загружается оригинальная Blockbuster Scene.
     * 2. Загружаются Records всех актёров.
     * 3. Очищается AnimationData предыдущей сцены.
     * 4. Восстанавливается позиция сцены.
     * 5. Загружается editor.dat этой сцены.
     */
    public boolean loadScene(
            int index)
            throws IOException
    {
        if (
                index < 0 ||
                        index >=
                                this.sceneFiles.size()
        )
        {
            return false;
        }


        File file =
                this.sceneFiles.get(index);


        /*
         * -----------------------------------------------------
         * 1. Загружаем оригинальную Blockbuster Scene.
         * -----------------------------------------------------
         *
         * BlockbusterSceneManager также загрузит
         * связанные Records.
         */
        this.sceneManager.load(
                file
        );


        /*
         * -----------------------------------------------------
         * 2. Эта сцена становится текущей.
         * -----------------------------------------------------
         */

        this.selectedScene =
                index;


        /*
         * -----------------------------------------------------
         * 3. Сбрасываем Actor selection.
         * -----------------------------------------------------
         */

        this.selectedActor =
                -1;


        /*
         * -----------------------------------------------------
         * 4. Очищаем AnimationData предыдущей сцены.
         * -----------------------------------------------------
         *
         * Pose данные не должны переходить
         * от одной сцены к другой.
         */
        this.animationData.clear();


        /*
         * -----------------------------------------------------
         * 5. Восстанавливаем мировую позицию.
         * -----------------------------------------------------
         *
         * Это fallback из Record.
         *
         * Если editor.dat существует,
         * его сохранённая позиция будет загружена ниже.
         */
        this.updateScenePositionFromRecords();


        /*
         * -----------------------------------------------------
         * 6. Загружаем данные самого редактора.
         * -----------------------------------------------------
         *
         * Здесь восстанавливаются:
         *
         * - Character Timeline;
         * - Pose / AnimationData;
         * - сохранённая позиция сцены;
         * - другие editor-only данные.
         *
         * Если editor.dat отсутствует,
         * это нормально: старые сцены продолжают работать.
         */
        this.saveController.loadEditorData();


        return true;
    }


    /*
     * =========================================================
     * ACTORS
     * =========================================================
     */

    /**
     * Возвращает всех актёров текущей сцены.
     */
    public List<BlockbusterSceneActorData>
    getActors()
    {
        List<BlockbusterSceneActorData> actors =
                this.sceneManager.getActorData();

        if (actors == null)
        {
            return new ArrayList<BlockbusterSceneActorData>();
        }

        return actors;
    }


    /**
     * Возвращает выбранного актёра текущей сцены.
     */
    public BlockbusterSceneActorData
    getSelectedActorData()
    {
        List<BlockbusterSceneActorData> actors =
                getActors();

        if (
                this.selectedActor < 0 ||
                        this.selectedActor >=
                                actors.size()
        )
        {
            return null;
        }

        return actors.get(
                this.selectedActor
        );
    }


    /**
     * Возвращает Record выбранного актёра.
     */
    public BlockbusterRecord
    getSelectedActorRecord()
    {
        BlockbusterSceneActorData actor =
                getSelectedActorData();

        if (
                actor == null ||
                        !actor.hasRecord()
        )
        {
            return null;
        }

        return actor.getRecord();
    }


    /*
     * =========================================================
     * SCENE LENGTH
     * =========================================================
     */

    /**
     * Длина сцены определяется самым длинным
     * Record среди всех актёров.
     */
    public int getSceneLength()
    {
        int maximumLength = 0;

        for (
                BlockbusterSceneActorData data :
                getActors()
        )
        {
            if (
                    data == null ||
                            !data.hasRecord()
            )
            {
                continue;
            }

            maximumLength =
                    Math.max(
                            maximumLength,
                            data.getLength()
                    );
        }

        return maximumLength;
    }


    /*
     * =========================================================
     * SCENE POSITION
     * =========================================================
     */

    public double getSceneX()
    {
        return this.sceneX;
    }


    public double getSceneY()
    {
        return this.sceneY;
    }


    public double getSceneZ()
    {
        return this.sceneZ;
    }


    /**
     * Устанавливает мировую позицию сцены.
     *
     * Изменение позиции считается изменением
     * сохраняемого состояния редактора.
     */
    public void setScenePosition(
            double x,
            double y,
            double z)
    {
        this.sceneX = x;
        this.sceneY = y;
        this.sceneZ = z;

        /*
         * Позиция относится к persistent editor state.
         */
        this.saveController.markDirty();
    }


    /*
     * =========================================================
     * SCENE POSITION FALLBACK
     * =========================================================
     */

    /**
     * Восстанавливает мировую позицию сцены
     * из первого актёра, у которого есть Record.
     *
     * Для этого используется первый кадр Record.
     *
     * Этот метод НЕ помечает состояние dirty,
     * поскольку восстановление происходит во время загрузки.
     */
    private void updateScenePositionFromRecords()
    {
        List<BlockbusterSceneActorData> actors =
                getActors();

        for (
                BlockbusterSceneActorData actor :
                actors
        )
        {
            if (
                    actor == null ||
                            !actor.hasRecord()
            )
            {
                continue;
            }


            BlockbusterRecord record =
                    actor.getRecord();


            if (
                    record == null ||
                            record.getFrames().isEmpty()
            )
            {
                continue;
            }


            BlockbusterRecordFrame frame =
                    record.getFrame(0);


            if (frame == null)
            {
                continue;
            }


            this.sceneX =
                    frame.getX();

            this.sceneY =
                    frame.getY();

            this.sceneZ =
                    frame.getZ();


            return;
        }


        /*
         * Если ни у одного актёра нет подходящего
         * Record, используем безопасную точку начала.
         */
        this.sceneX = 0.0D;
        this.sceneY = 0.0D;
        this.sceneZ = 0.0D;
    }
}