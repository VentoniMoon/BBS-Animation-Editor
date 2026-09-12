package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BlockbusterSceneDebug
{
    /*
     * ---------------------------------------------------------
     * WORLD
     * ---------------------------------------------------------
     */

    public static File getWorldDirectory()
    {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Minecraft instance is null."
            );

            return null;
        }

        /*
         * ---------------------------------------------------------
         * INTEGRATED SERVER
         * ---------------------------------------------------------
         *
         * Blockbuster scenes belong to the actual world save.
         * Therefore we get the directory from the integrated
         * server rather than from the client-side WorldClient.
         */

        if (minecraft.isSingleplayer())
        {
            if (minecraft.getIntegratedServer() == null)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Integrated server is null."
                );

                return null;
            }

            if (
                    minecraft.getIntegratedServer()
                            .getWorld(0) == null
            )
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Integrated server world is null."
                );

                return null;
            }

            if (
                    minecraft.getIntegratedServer()
                            .getWorld(0)
                            .getSaveHandler() == null
            )
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Server save handler is null."
                );

                return null;
            }

            File worldDirectory =
                    minecraft.getIntegratedServer()
                            .getWorld(0)
                            .getSaveHandler()
                            .getWorldDirectory();

            if (worldDirectory == null)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Server world directory is null."
                );

                return null;
            }

            return worldDirectory;
        }

        /*
         * ---------------------------------------------------------
         * MULTIPLAYER
         * ---------------------------------------------------------
         *
         * For now our editor is being developed around local
         * Blockbuster worlds. We don't attempt to access a remote
         * server's filesystem.
         */

        System.out.println(
                "[BBS Animation Editor] "
                        + "Current world is not singleplayer."
        );

        return null;
    }

    /*
     * ---------------------------------------------------------
     * SCENES DIRECTORY
     * ---------------------------------------------------------
     */

    public static File getScenesDirectory()
    {
        File worldDirectory =
                getWorldDirectory();

        if (worldDirectory == null)
        {
            return null;
        }

        File blockbusterDirectory =
                new File(
                        worldDirectory,
                        "blockbuster"
                );

        return new File(
                blockbusterDirectory,
                "scenes"
        );
    }

    /*
     * ---------------------------------------------------------
     * SCENE FILES
     * ---------------------------------------------------------
     */

    public static File[] getSceneFiles()
    {
        File scenesDirectory =
                getScenesDirectory();

        if (scenesDirectory == null)
        {
            return new File[0];
        }

        if (!scenesDirectory.exists())
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scenes directory does not exist:"
            );

            System.out.println(
                    scenesDirectory
                            .getAbsolutePath()
            );

            return new File[0];
        }

        if (!scenesDirectory.isDirectory())
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scenes path is not a directory:"
            );

            System.out.println(
                    scenesDirectory
                            .getAbsolutePath()
            );

            return new File[0];
        }

        File[] files =
                scenesDirectory.listFiles();

        if (files == null)
        {
            return new File[0];
        }

        return files;
    }

    /*
     * ---------------------------------------------------------
     * DEBUG ALL SCENES
     * ---------------------------------------------------------
     */

    public static boolean debugAllScenes()
    {
        System.out.println(
                "[BBS Animation Editor] "
                        + "SCENE DEBUG START"
        );

        File worldDirectory =
                getWorldDirectory();

        /*
         * IMPORTANT:
         *
         * Do not consider the debug complete if the world
         * isn't loaded yet.
         */

        if (worldDirectory == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scene debug postponed: "
                            + "world is not loaded."
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "SCENE DEBUG END"
            );

            return false;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "World directory:"
        );

        System.out.println(
                worldDirectory
                        .getAbsolutePath()
        );

        File scenesDirectory =
                getScenesDirectory();

        if (scenesDirectory == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scenes directory is null."
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "SCENE DEBUG END"
            );

            return false;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Scenes directory:"
        );

        System.out.println(
                scenesDirectory
                        .getAbsolutePath()
        );

        File[] files =
                getSceneFiles();

        if (files.length == 0)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "No scene files found."
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "SCENE DEBUG END"
            );

            /*
             * The world is loaded, so the diagnostic itself
             * is complete even if there are no scenes.
             */

            return true;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Scene files found: "
                        + files.length
        );

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

            if (!file.getName()
                    .toLowerCase()
                    .endsWith(".dat"))
            {
                continue;
            }

            debugScene(file);
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "SCENE DEBUG END"
        );

        return true;
    }

    /*
     * ---------------------------------------------------------
     * DEBUG ONE SCENE
     * ---------------------------------------------------------
     */

    public static void debugScene(
            File file)
    {
        System.out.println(
                "[BBS Animation Editor] "
                        + "--------------------------------"
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "SCENE FILE:"
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + file.getAbsolutePath()
        );

        FileInputStream input =
                null;

        try
        {
            input =
                    new FileInputStream(
                            file
                    );

            NBTTagCompound root =
                    CompressedStreamTools
                            .readCompressed(
                                    input
                            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "ROOT TAG:"
            );

            debugTag(
                    root,
                    1
            );
        }
        catch (Exception e)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Could not read scene:"
            );

            System.err.println(
                    file.getAbsolutePath()
            );

            e.printStackTrace();
        }
        finally
        {
            if (input != null)
            {
                try
                {
                    input.close();
                }
                catch (IOException ignored)
                {
                }
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * NBT DEBUG
     * ---------------------------------------------------------
     */

    private static void debugTag(
            NBTBase tag,
            int depth)
    {
        if (tag == null)
        {
            return;
        }

        String indent =
                createIndent(depth);

        if (tag instanceof NBTTagCompound)
        {
            NBTTagCompound compound =
                    (NBTTagCompound) tag;

            for (String key :
                    compound.getKeySet())
            {
                NBTBase child =
                        compound.getTag(key);

                System.out.println(
                        "[BBS Animation Editor] "
                                + indent
                                + key
                                + " ["
                                + getTagTypeName(child)
                                + "]"
                );

                debugTag(
                        child,
                        depth + 1
                );
            }

            return;
        }

        if (tag instanceof NBTTagList)
        {
            NBTTagList list =
                    (NBTTagList) tag;

            System.out.println(
                    "[BBS Animation Editor] "
                            + indent
                            + "List size = "
                            + list.tagCount()
            );

            for (
                    int i = 0;
                    i < list.tagCount();
                    i++
            )
            {
                NBTBase child =
                        list.get(i);

                System.out.println(
                        "[BBS Animation Editor] "
                                + indent
                                + "["
                                + i
                                + "] ["
                                + getTagTypeName(child)
                                + "]"
                );

                debugTag(
                        child,
                        depth + 1
                );
            }

            return;
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + indent
                        + "Value = "
                        + tag
        );
    }

    /*
     * ---------------------------------------------------------
     * TAG TYPE
     * ---------------------------------------------------------
     */

    private static String getTagTypeName(
            NBTBase tag)
    {
        if (tag == null)
        {
            return "null";
        }

        return tag.getClass()
                .getSimpleName();
    }

    /*
     * ---------------------------------------------------------
     * INDENT
     * ---------------------------------------------------------
     */

    private static String createIndent(
            int depth)
    {
        StringBuilder builder =
                new StringBuilder();

        for (
                int i = 0;
                i < depth;
                i++
        )
        {
            builder.append("    ");
        }

        return builder.toString();
    }
}