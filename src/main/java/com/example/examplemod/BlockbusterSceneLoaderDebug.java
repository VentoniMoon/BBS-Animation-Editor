package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BlockbusterSceneLoaderDebug
{
    private static boolean done = false;

    public static void update()
    {
        if (done)
        {
            return;
        }

        BlockbusterSceneManager manager =
                new BlockbusterSceneManager();

        /*
         * Ждём, пока реально появится интегрированный сервер.
         */
        if (manager.getWorldDirectory() == null)
        {
            return;
        }

        List<File> scenes =
                manager.getSceneFiles();

        System.out.println(
                "[BBS Animation Editor] "
                        + "================================"
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "SCENE LOADER TEST"
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Scenes found: "
                        + scenes.size()
        );

        if (scenes.isEmpty())
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "No scenes found."
            );

            done = true;

            return;
        }

        File sceneFile =
                scenes.get(0);

        System.out.println(
                "[BBS Animation Editor] "
                        + "Loading scene:"
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + sceneFile.getAbsolutePath()
        );

        try
        {
            BlockbusterScene scene =
                    manager.load(
                            sceneFile
                    );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scene loaded successfully."
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Filename: "
                            + scene.getFilename()
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Title: "
                            + scene.getTitle()
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Loops: "
                            + scene.isLoops()
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Actors: "
                            + scene.getActors().size()
            );

            for (
                    BlockbusterSceneActor actor :
                    scene.getActors()
            )
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "--------------------------------"
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Actor ID: "
                                + actor.getId()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Actor name: "
                                + actor.getName()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Target: "
                                + actor.getTarget()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Enabled: "
                                + actor.isEnabled()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Invisible: "
                                + actor.isInvisible()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Fake: "
                                + actor.isFake()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Invincible: "
                                + actor.isInvincible()
                );

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Enable burning: "
                                + actor.isEnableBurning()
                );

                /*
                 * Morph
                 */

                if (actor.getMorph() != null)
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Morph:"
                    );

                    if (
                            actor.getMorph()
                                    .hasKey("Name")
                    )
                    {
                        System.out.println(
                                "[BBS Animation Editor] "
                                        + "  Name: "
                                        + actor.getMorph()
                                        .getString(
                                                "Name"
                                        )
                        );
                    }

                    if (
                            actor.getMorph()
                                    .hasKey(
                                            "ForcedSettings"
                                    )
                    )
                    {
                        System.out.println(
                                "[BBS Animation Editor] "
                                        + "  ForcedSettings: "
                                        + actor.getMorph()
                                        .getBoolean(
                                                "ForcedSettings"
                                        )
                        );
                    }
                }
                else
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Morph: null"
                    );
                }

                /*
                 * Record
                 */

                File recordFile =
                        manager.getRecordFile(
                                actor
                        );

                if (recordFile != null)
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Record path:"
                    );

                    System.out.println(
                            "[BBS Animation Editor] "
                                    + recordFile
                                    .getAbsolutePath()
                    );

                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Record exists: "
                                    + recordFile.exists()
                    );
                }
                else
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "Record path: null"
                    );
                }
            }

            System.out.println(
                    "[BBS Animation Editor] "
                            + "SCENE LOADER TEST SUCCESS"
            );
        }
        catch (IOException e)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Could not load scene."
            );

            e.printStackTrace();
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "================================"
        );

        done = true;
    }
}