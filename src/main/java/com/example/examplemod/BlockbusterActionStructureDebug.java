package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class BlockbusterActionStructureDebug
{
    private static boolean done = false;


    public static void update()
    {
        if (done)
        {
            return;
        }

        done = true;


        try
        {
            Minecraft minecraft =
                    Minecraft.getMinecraft();


            if (minecraft == null ||
                    minecraft.getIntegratedServer() == null)
            {
                System.out.println(
                        "ACTION STRUCTURE DEBUG: "
                                + "integrated server unavailable"
                );

                return;
            }


            File worldDirectory =
                    minecraft
                            .getIntegratedServer()
                            .getWorld(0)
                            .getSaveHandler()
                            .getWorldDirectory();


            File recordsDirectory =
                    new File(
                            worldDirectory,
                            "blockbuster/records"
                    );


            System.out.println("");
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "BBS ACTION STRUCTURE DATABASE SCAN"
            );

            System.out.println(
                    "========================================"
            );


            if (!recordsDirectory.exists())
            {
                System.out.println(
                        "ERROR: records directory does not exist:"
                );

                System.out.println(
                        recordsDirectory.getAbsolutePath()
                );

                return;
            }


            File[] files =
                    recordsDirectory.listFiles();


            if (files == null ||
                    files.length == 0)
            {
                System.out.println(
                        "No record files found."
                );

                return;
            }


            /*
             * Global statistics.
             *
             * type -> statistics
             */
            Map<Byte, GlobalActionStats>
                    globalStatistics =
                    new TreeMap<Byte, GlobalActionStats>();


            int recordsScanned = 0;

            int recordsFailed = 0;

            int totalFrames = 0;

            int totalActions = 0;

            boolean definitionsTested = false;


            /*
             * Sort files alphabetically so the
             * console output is deterministic.
             */
            List<File> recordFiles =
                    new ArrayList<File>();


            for (File file : files)
            {
                if (file.isFile() &&
                        file.getName().endsWith(".dat"))
                {
                    recordFiles.add(file);
                }
            }


            Collections.sort(
                    recordFiles,
                    new java.util.Comparator<File>()
                    {
                        @Override
                        public int compare(
                                File a,
                                File b
                        )
                        {
                            return a.getName()
                                    .compareToIgnoreCase(
                                            b.getName()
                                    );
                        }
                    }
            );


            /*
             * Scan every record.
             */
            for (File recordFile :
                    recordFiles)
            {
                try
                {
                    BlockbusterRecord record =
                            BlockbusterRecordIO.load(
                                    recordFile
                            );


                    if (record == null)
                    {
                        recordsFailed++;

                        continue;
                    }


                    recordsScanned++;

                    totalFrames +=
                            record.getLength();


                    BlockbusterActionRegistry registry =
                            record.getActionRegistry();

                    if (!definitionsTested &&
                            registry != null)
                    {
                        testActionDefinitions(registry);

                        definitionsTested = true;
                    }



                    for (
                            int tick = 0;
                            tick < record.getLength();
                            tick++
                    )
                    {
                        List<BlockbusterRecordAction>
                                actions =
                                record.getActions(tick);


                        if (actions == null)
                        {
                            continue;
                        }


                        for (
                                BlockbusterRecordAction action :
                                actions
                        )
                        {
                            if (action == null)
                            {
                                continue;
                            }


                            totalActions++;


                            byte type =
                                    action.getType();


                            GlobalActionStats stats =
                                    globalStatistics.get(
                                            type
                                    );


                            if (stats == null)
                            {
                                String name =
                                        registry == null
                                                ? null
                                                : registry.getName(
                                                type
                                        );


                                stats =
                                        new GlobalActionStats(
                                                type,
                                                name
                                        );


                                globalStatistics.put(
                                        type,
                                        stats
                                );
                            }


                            stats.count++;


                            stats.files.add(
                                    recordFile.getName()
                            );


                            stats.ticks.add(
                                    tick
                            );


                            NBTTagCompound actionNBT =
                                    action.getNBT();


                            if (actionNBT != null)
                            {
                                collectStructure(
                                        actionNBT,
                                        stats,
                                        ""
                                );


                                /*
                                 * Keep first example.
                                 */
                                if (stats.firstExample == null)
                                {
                                    stats.firstExample =
                                            actionNBT.copy();

                                    stats.firstFile =
                                            recordFile.getName();

                                    stats.firstTick =
                                            tick;
                                }
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    recordsFailed++;

                    System.out.println(
                            "FAILED RECORD: "
                                    + recordFile.getName()
                    );

                    e.printStackTrace();
                }
            }


            /*
             * General statistics.
             */
            System.out.println("");

            System.out.println(
                    "Records found: "
                            + recordFiles.size()
            );

            System.out.println(
                    "Records scanned: "
                            + recordsScanned
            );

            System.out.println(
                    "Records failed: "
                            + recordsFailed
            );

            System.out.println(
                    "Total frames: "
                            + totalFrames
            );

            System.out.println(
                    "Total actions: "
                            + totalActions
            );

            System.out.println(
                    "Unique action types: "
                            + globalStatistics.size()
            );


            /*
             * Print discovered action types.
             */
            System.out.println("");
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "GLOBAL ACTION TYPE DATABASE"
            );

            System.out.println(
                    "========================================"
            );


            for (
                    GlobalActionStats stats :
                    globalStatistics.values()
            )
            {
                printGlobalStats(
                        stats
                );
            }


            /*
             * Print all registered names found
             * in the scanned records.
             */
            Map<Byte, String> registered =
                    new TreeMap<Byte, String>();


            for (File recordFile :
                    recordFiles)
            {
                try
                {
                    BlockbusterRecord record =
                            BlockbusterRecordIO.load(
                                    recordFile
                            );


                    if (record == null ||
                            record.getActionRegistry() == null)
                    {
                        continue;
                    }


                    for (
                            Map.Entry<Byte, String> entry :
                            record
                                    .getActionRegistry()
                                    .getActions()
                                    .entrySet()
                    )
                    {
                        if (!registered.containsKey(
                                entry.getKey()
                        ))
                        {
                            registered.put(
                                    entry.getKey(),
                                    entry.getValue()
                            );
                        }
                    }
                }
                catch (Exception ignored)
                {
                }
            }


            System.out.println("");
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "ALL REGISTERED ACTION TYPES"
            );

            System.out.println(
                    "========================================"
            );


            for (
                    Map.Entry<Byte, String> entry :
                    registered.entrySet()
            )
            {
                GlobalActionStats stats =
                        globalStatistics.get(
                                entry.getKey()
                        );


                String status;


                if (stats == null)
                {
                    status =
                            "NOT USED";
                }
                else
                {
                    status =
                            "USED "
                                    + stats.count
                                    + " TIMES";
                }


                System.out.println(
                        "  "
                                + unsigned(
                                entry.getKey()
                        )
                                + " -> "
                                + entry.getValue()
                                + " ["
                                + status
                                + "]"
                );
            }


            System.out.println("");
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "ACTION DATABASE SCAN SUCCESS"
            );

            System.out.println(
                    "========================================"
            );

            System.out.println("");
        }
        catch (Exception e)
        {
            System.out.println("");
            System.out.println(
                    "ACTION DATABASE SCAN ERROR"
            );

            e.printStackTrace();
        }
    }


    private static void collectStructure(
            NBTTagCompound nbt,
            GlobalActionStats stats,
            String prefix
    )
    {
        Set<String> keys =
                nbt.getKeySet();


        for (String key : keys)
        {
            String path;


            if (prefix == null ||
                    prefix.length() == 0)
            {
                path = key;
            }
            else
            {
                path =
                        prefix
                                + "."
                                + key;
            }


            NBTBase tag =
                    nbt.getTag(key);


            if (tag == null)
            {
                stats.fields.add(
                        path
                                + " [null]"
                );

                continue;
            }


            stats.fields.add(
                    path
                            + " ["
                            + getNBTTypeName(
                            tag.getId()
                    )
                            + "]"
            );


            if (tag instanceof NBTTagCompound)
            {
                collectStructure(
                        (NBTTagCompound) tag,
                        stats,
                        path
                );
            }
            else if (tag instanceof NBTTagList)
            {
                NBTTagList list =
                        (NBTTagList) tag;


                stats.fields.add(
                        path
                                + ".<count> = "
                                + list.tagCount()
                );


                if (list.tagCount() > 0)
                {
                    NBTBase first =
                            list.get(0);


                    if (first instanceof NBTTagCompound)
                    {
                        collectStructure(
                                (NBTTagCompound) first,
                                stats,
                                path + "[]"
                        );
                    }
                    else if (first != null)
                    {
                        stats.fields.add(
                                path
                                        + "[].element ["
                                        + getNBTTypeName(
                                        first.getId()
                                )
                                        + "]"
                        );
                    }
                }
            }
        }
    }


    private static void printGlobalStats(
            GlobalActionStats stats
    )
    {
        System.out.println("");
        System.out.println(
                "----------------------------------------"
        );


        System.out.println(
                "TYPE "
                        + unsigned(stats.type)
                        + " -> "
                        + (
                        stats.name == null
                                ? "<unknown>"
                                : stats.name
                )
        );


        System.out.println(
                "Total occurrences: "
                        + stats.count
        );


        System.out.println(
                "Records containing type: "
                        + stats.files.size()
        );


        System.out.println(
                "Files:"
        );


        for (String file :
                stats.files)
        {
            System.out.println(
                    "  "
                            + file
            );
        }


        System.out.println(
                "Observed ticks:"
        );


        System.out.println(
                "  "
                        + formatTicks(
                        stats.ticks
                )
        );


        System.out.println(
                "Structure:"
        );


        for (String field :
                stats.fields)
        {
            System.out.println(
                    "  "
                            + field
            );
        }


        System.out.println(
                "First complete NBT example:"
        );


        if (stats.firstExample != null)
        {
            System.out.println(
                    "  File: "
                            + stats.firstFile
            );


            System.out.println(
                    "  Tick: "
                            + stats.firstTick
            );


            printNBT(
                    stats.firstExample,
                    "  "
            );
        }
        else
        {
            System.out.println(
                    "  <none>"
            );
        }
    }


    private static void printNBT(
            NBTTagCompound nbt,
            String indent
    )
    {
        List<String> keys =
                new ArrayList<String>(
                        nbt.getKeySet()
                );


        Collections.sort(
                keys
        );


        for (String key :
                keys)
        {
            NBTBase tag =
                    nbt.getTag(key);


            if (tag == null)
            {
                System.out.println(
                        indent
                                + key
                                + " [null]"
                );

                continue;
            }


            if (tag instanceof NBTTagCompound)
            {
                System.out.println(
                        indent
                                + key
                                + " [NBTTagCompound]"
                );


                printNBT(
                        (NBTTagCompound) tag,
                        indent + "  "
                );
            }
            else if (tag instanceof NBTTagList)
            {
                NBTTagList list =
                        (NBTTagList) tag;


                System.out.println(
                        indent
                                + key
                                + " [NBTTagList]"
                                + " size="
                                + list.tagCount()
                );


                if (list.tagCount() > 0)
                {
                    NBTBase first =
                            list.get(0);


                    if (first instanceof NBTTagCompound)
                    {
                        System.out.println(
                                indent
                                        + "  [0] "
                                        + "[NBTTagCompound]"
                        );


                        printNBT(
                                (NBTTagCompound) first,
                                indent + "    "
                        );
                    }
                    else if (first != null)
                    {
                        System.out.println(
                                indent
                                        + "  [0] ["
                                        + getNBTTypeName(
                                        first.getId()
                                )
                                        + "]"
                        );
                    }
                }
            }
            else
            {
                System.out.println(
                        indent
                                + key
                                + " ["
                                + getNBTTypeName(
                                tag.getId()
                        )
                                + "] = "
                                + tag.toString()
                );
            }
        }
    }


    private static String formatTicks(
            Set<Integer> ticks
    )
    {
        if (ticks == null ||
                ticks.isEmpty())
        {
            return "<none>";
        }


        StringBuilder builder =
                new StringBuilder();


        int displayed =
                Math.min(
                        ticks.size(),
                        40
                );


        int index = 0;


        for (Integer tick :
                ticks)
        {
            if (index >= displayed)
            {
                break;
            }


            if (index > 0)
            {
                builder.append(", ");
            }


            builder.append(
                    tick
            );


            index++;
        }


        if (ticks.size() > displayed)
        {
            builder.append(
                    ", ... (+"
                            + (
                            ticks.size()
                                    - displayed
                    )
                            + " more)"
            );
        }


        return builder.toString();
    }


    private static int unsigned(
            byte value
    )
    {
        return value & 0xFF;
    }


    private static String getNBTTypeName(
            int id
    )
    {
        switch (id)
        {
            case 1:
                return "NBTTagByte";

            case 2:
                return "NBTTagShort";

            case 3:
                return "NBTTagInt";

            case 4:
                return "NBTTagLong";

            case 5:
                return "NBTTagFloat";

            case 6:
                return "NBTTagDouble";

            case 7:
                return "NBTTagByteArray";

            case 8:
                return "NBTTagString";

            case 9:
                return "NBTTagList";

            case 10:
                return "NBTTagCompound";

            case 11:
                return "NBTTagIntArray";

            default:
                return "NBTTagUnknown";
        }
    }


    private static class GlobalActionStats
    {
        private final byte type;

        private final String name;

        private int count;

        private final Set<String> files =
                new TreeSet<String>();

        private final Set<Integer> ticks =
                new TreeSet<Integer>();

        private final Set<String> fields =
                new TreeSet<String>();

        private String firstFile;

        private int firstTick =
                -1;

        private NBTTagCompound firstExample;


        private GlobalActionStats(
                byte type,
                String name
        )
        {
            this.type = type;
            this.name = name;
        }
    }
    private static void testActionDefinitions(
            BlockbusterActionRegistry registry)
    {
        System.out.println(
                "[BBS Animation Editor] ACTION DEFINITIONS TEST");

        BlockbusterActionDefinition attack =
                registry.getDefinition((byte) 13);

        if (attack == null)
        {
            System.out.println(
                    "FAIL: attack definition missing");

            return;
        }

        System.out.println(
                "PASS: attack = " +
                        attack.getName());

        for (BlockbusterActionDefinition.Field field :
                attack.getFields())
        {
            System.out.println(
                    "  Field: " +
                            field.getName() +
                            " / " +
                            field.getType());
        }

        BlockbusterActionDefinition placeBlock =
                registry.getDefinition((byte) 7);

        if (placeBlock == null)
        {
            System.out.println(
                    "FAIL: place_block definition missing");

            return;
        }

        System.out.println(
                "PASS: place_block = " +
                        placeBlock.getName());

        for (BlockbusterActionDefinition.Field field :
                placeBlock.getFields())
        {
            System.out.println(
                    "  Field: " +
                            field.getName() +
                            " / " +
                            field.getType());
        }

        System.out.println(
                "Registered definitions: " +
                        registry.getDefinitions().size());

        System.out.println(
                "[BBS Animation Editor] ACTION DEFINITIONS TEST END");
    }
}