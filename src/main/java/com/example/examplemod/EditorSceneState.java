package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditorSceneState
{
    private final BlockbusterSceneManager sceneManager;
    private final SceneAnimationData animationData;

    private List<File> sceneFiles;

    private int selectedScene;
    private int selectedActor;

    public EditorSceneState()
    {
        this.sceneManager =
                new BlockbusterSceneManager();

        this.animationData =
                new SceneAnimationData();

        this.sceneFiles =
                new ArrayList<File>();

        this.selectedScene = -1;
        this.selectedActor = -1;
    }

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

    public BlockbusterSceneManager getSceneManager()
    {
        return this.sceneManager;
    }

    public SceneAnimationData getAnimationData()
    {
        return this.animationData;
    }

    public List<File> getSceneFiles()
    {
        return this.sceneFiles;
    }

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
                        selectedActor >= actors.size()
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

    /**
     * Загружает сцену по индексу из списка сцен.
     *
     * При смене сцены пользовательская
     * AnimationData предыдущей сцены очищается.
     */
    public boolean loadScene(int index)
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
         * Загружаем саму Blockbuster Scene.
         */
        this.sceneManager.load(file);

        /*
         * Теперь эта сцена становится текущей.
         */
        this.selectedScene =
                index;

        /*
         * После смены сцены актёр больше
         * не считается выбранным.
         */
        this.selectedActor =
                -1;

        /*
         * AnimationData принадлежит текущей
         * сцене и не должна переноситься
         * между сценами.
         */
        this.animationData.clear();

        return true;
    }

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
}