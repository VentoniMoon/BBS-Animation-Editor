package com.example.examplemod;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.List;

public class BlockbusterActionViewDebug
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

        File worldDirectory =
                Minecraft.getMinecraft()
                        .getIntegratedServer()
                        .getWorld(0)
                        .getSaveHandler()
                        .getWorldDirectory();

        if (worldDirectory == null)
        {
            return;
        }

        File recordsDirectory =
                new File(
                        worldDirectory,
                        "blockbuster/records");

        if (!recordsDirectory.exists())
        {
            return;
        }

        File recordFile =
                new File(
                        recordsDirectory,
                        "1341_1.dat");

        if (!recordFile.exists())
        {
            System.out.println(
                    "[BBS Animation Editor] ACTION VIEW TEST: " +
                            "1341_1.dat not found");

            done = true;

            return;
        }

        System.out.println(
                "========================================");

        System.out.println(
                "[BBS Animation Editor] ACTION VIEW TEST");

        System.out.println(
                "========================================");

        BlockbusterRecord record;

        try
        {
            record =
                    BlockbusterRecordIO.load(recordFile);
        }
        catch (java.io.IOException e)
        {
            System.out.println(
                    "FAIL: record could not be loaded");

            e.printStackTrace();

            done = true;

            return;
        }

        if (record == null)
        {
            System.out.println(
                    "FAIL: record could not be loaded");

            done = true;

            return;
        }

        BlockbusterActionRegistry registry =
                record.getActionRegistry();

        if (registry == null)
        {
            System.out.println(
                    "FAIL: action registry is null");

            done = true;

            return;
        }

        System.out.println(
                "Record: " +
                        record.getFilename());

        System.out.println(
                "Frames: " +
                        record.getLength());

        boolean foundAction = false;

        for (int tick = 0;
             tick < record.getLength();
             tick++)
        {
            List<BlockbusterRecordAction> actions =
                    record.getActions(tick);

            if (actions == null || actions.isEmpty())
            {
                continue;
            }

            for (BlockbusterRecordAction action :
                    actions)
            {
                if (action == null)
                {
                    continue;
                }

                BlockbusterActionDefinition definition =
                        registry.getDefinition(
                                action.getType());

                BlockbusterActionView view =
                        new BlockbusterActionView(
                                action,
                                definition);

                System.out.println(
                        "----------------------------------------");

                System.out.println(
                        "Tick: " +
                                tick);

                System.out.println(
                        "Type: " +
                                view.getType());

                System.out.println(
                        "Name: " +
                                view.getName());

                if (!view.hasDefinition())
                {
                    System.out.println(
                            "Definition: MISSING");
                }
                else
                {
                    System.out.println(
                            "Definition: FOUND");

                    for (BlockbusterActionDefinition.Field field :
                            definition.getFields())
                    {
                        System.out.println(
                                "Field: " +
                                        field.getName() +
                                        " / " +
                                        field.getType());

                        if (view.hasField(field))
                        {
                            System.out.println(
                                    "  Value: " +
                                            getFieldValue(
                                                    view,
                                                    field));
                        }
                        else
                        {
                            System.out.println(
                                    "  Value: <not present>");
                        }
                    }
                }

                foundAction = true;

                break;
            }

            if (foundAction)
            {
                break;
            }
        }

        if (!foundAction)
        {
            System.out.println(
                    "FAIL: no actions found in record");
        }
        else
        {
            System.out.println(
                    "PASS: ActionView successfully " +
                            "resolved a real Blockbuster action");
        }

        System.out.println(
                "[BBS Animation Editor] ACTION VIEW TEST END");

        System.out.println(
                "========================================");

        done = true;
    }

    private static String getFieldValue(
            BlockbusterActionView view,
            BlockbusterActionDefinition.Field field)
    {
        switch (field.getType())
        {
            case BYTE:
                return String.valueOf(
                        view.getByte(field));

            case INT:
                return String.valueOf(
                        view.getInteger(field));

            case STRING:
                return view.getString(field);

            case COMPOUND:
                return view.getCompound(field).toString();

            default:
                return "<unsupported by debug>";
        }
    }
}