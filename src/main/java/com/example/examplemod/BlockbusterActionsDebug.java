package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.util.Set;

public class BlockbusterActionsDebug
{
    private static boolean done = false;


    public static void update()
    {
        if (done)
        {
            return;
        }

        if (Minecraft.getMinecraft().getIntegratedServer() == null)
        {
            return;
        }

        done = true;


        try
        {
            File worldDirectory =
                    Minecraft.getMinecraft()
                            .getIntegratedServer()
                            .getWorld(0)
                            .getSaveHandler()
                            .getWorldDirectory();


            File recordFile =
                    new File(
                            worldDirectory,
                            "blockbuster/records/1341_1.dat"
                    );


            System.out.println("");
            System.out.println("========================================");
            System.out.println("BBS ACTIONS STRUCTURE TEST");
            System.out.println("========================================");

            System.out.println(
                    "File: "
                            + recordFile.getAbsolutePath()
            );


            if (!recordFile.exists())
            {
                System.out.println(
                        "ERROR: record file does not exist!"
                );

                return;
            }


            /*
             * Load the complete record.
             */

            BlockbusterRecord record =
                    BlockbusterRecordIO.load(
                            recordFile
                    );


            /*
             * Read the raw NBT again.
             *
             * We intentionally do this separately from
             * BlockbusterRecord because Actions is not yet
             * represented by our model.
             */

            NBTTagCompound root =
                    net.minecraft.nbt.CompressedStreamTools
                            .readCompressed(
                                    new java.io.FileInputStream(
                                            recordFile
                                    )
                            );


            System.out.println("");
            System.out.println("ROOT KEYS:");
            printKeys(root);


            /*
             * Locate root Actions.
             */

            System.out.println("");
            System.out.println("========================================");
            System.out.println("ROOT ACTIONS");
            System.out.println("========================================");


            if (!root.hasKey("Actions", 10))
            {
                System.out.println(
                        "No root-level Actions compound found."
                );

                return;
            }


            NBTTagCompound actions =
                    root.getCompoundTag(
                            "Actions"
                    );


            System.out.println(
                    "Actions compound found."
            );


            System.out.println(
                    "Keys: "
                            + actions.getKeySet().size()
            );


            printCompound(
                    actions,
                    0
            );


            /*
             * Now inspect actions stored inside frames.
             */

            System.out.println("");
            System.out.println("========================================");
            System.out.println("FRAME ACTIONS");
            System.out.println("========================================");


            for (
                    int tick = 0;
                    tick < record.getLength();
                    tick++
            )
            {
                if (
                        record.getActions(tick) == null
                                ||
                                record.getActions(tick).isEmpty()
                )
                {
                    continue;
                }


                System.out.println(
                        "Tick "
                                + tick
                                + ": "
                                + record
                                .getActions(tick)
                                .size()
                                + " action(s)"
                );


                for (
                        int i = 0;
                        i <
                                record
                                        .getActions(tick)
                                        .size();
                        i++
                )
                {
                    BlockbusterRecordAction action =
                            record
                                    .getActions(tick)
                                    .get(i);


                    System.out.println(
                            "  Action "
                                    + i
                                    + ":"
                    );

                    System.out.println(
                            "    Type = "
                                    + action.getType()
                    );


                    NBTTagCompound actionNBT =
                            action.getNBT();


                    if (
                            actionNBT != null
                                    &&
                                    !actionNBT.hasNoTags()
                    )
                    {
                        System.out.println(
                                "    NBT:"
                        );

                        printCompound(
                                actionNBT,
                                3
                        );
                    }
                }
            }


            /*
             * Finish.
             */

            System.out.println("");
            System.out.println("========================================");
            System.out.println("BBS ACTIONS STRUCTURE TEST COMPLETE");
            System.out.println("========================================");
            System.out.println("");


        }
        catch (Exception e)
        {
            System.out.println("");
            System.out.println(
                    "BBS ACTIONS STRUCTURE TEST ERROR"
            );

            e.printStackTrace();
        }
    }


    private static void printKeys(
            NBTTagCompound compound
    )
    {
        Set<String> keys =
                compound.getKeySet();


        for (String key : keys)
        {
            NBTBase tag =
                    compound.getTag(key);


            System.out.println(
                    "  "
                            + key
                            + " ["
                            + getTypeName(tag)
                            + "]"
            );
        }
    }


    private static void printCompound(
            NBTTagCompound compound,
            int indent
    )
    {
        Set<String> keys =
                compound.getKeySet();


        for (String key : keys)
        {
            NBTBase tag =
                    compound.getTag(key);


            printIndent(indent);

            System.out.println(
                    key
                            + " ["
                            + getTypeName(tag)
                            + "]"
            );


            if (tag instanceof NBTTagCompound)
            {
                printCompound(
                        (NBTTagCompound) tag,
                        indent + 1
                );
            }
            else if (tag instanceof NBTTagList)
            {
                printList(
                        (NBTTagList) tag,
                        indent + 1
                );
            }
            else
            {
                printIndent(indent + 1);

                System.out.println(
                        "Value = "
                                + getReadableValue(tag)
                );
            }
        }
    }


    private static void printList(
            NBTTagList list,
            int indent
    )
    {
        System.out.println(
                "List size = "
                        + list.tagCount()
        );


        for (
                int i = 0;
                i < list.tagCount();
                i++
        )
        {
            NBTBase tag =
                    list.get(i);


            printIndent(indent);

            System.out.println(
                    "["
                            + i
                            + "] ["
                            + getTypeName(tag)
                            + "]"
            );


            if (tag instanceof NBTTagCompound)
            {
                printCompound(
                        (NBTTagCompound) tag,
                        indent + 1
                );
            }
            else if (tag instanceof NBTTagList)
            {
                printList(
                        (NBTTagList) tag,
                        indent + 1
                );
            }
            else
            {
                printIndent(indent + 1);

                System.out.println(
                        "Value = "
                                + getReadableValue(tag)
                );
            }
        }
    }


    private static String getTypeName(
            NBTBase tag
    )
    {
        if (tag == null)
        {
            return "null";
        }


        return tag.getClass()
                .getSimpleName();
    }


    private static String getReadableValue(
            NBTBase tag
    )
    {
        if (tag == null)
        {
            return "null";
        }


        if (tag instanceof net.minecraft.nbt.NBTTagByte)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagByte) tag)
                            .getByte()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagShort)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagShort) tag)
                            .getShort()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagInt)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagInt) tag)
                            .getInt()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagLong)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagLong) tag)
                            .getLong()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagFloat)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagFloat) tag)
                            .getFloat()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagDouble)
        {
            return String.valueOf(
                    ((net.minecraft.nbt.NBTTagDouble) tag)
                            .getDouble()
            );
        }


        if (tag instanceof net.minecraft.nbt.NBTTagString)
        {
            return "\""
                    + ((net.minecraft.nbt.NBTTagString) tag)
                    .getString()
                    + "\"";
        }


        return tag.toString();
    }


    private static void printIndent(
            int indent
    )
    {
        for (int i = 0; i < indent; i++)
        {
            System.out.print("  ");
        }
    }
}