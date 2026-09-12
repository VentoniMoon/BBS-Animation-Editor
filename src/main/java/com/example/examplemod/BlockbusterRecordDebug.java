package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;

public class BlockbusterRecordDebug
{
    private static boolean done = false;

    public static void update()
    {
        if (done)
        {
            return;
        }

        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft == null ||
                minecraft.getIntegratedServer() == null)
        {
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

        File recordFile =
                new File(
                        recordsDirectory,
                        "1341_1.dat"
                );

        System.out.println("");
        System.out.println(
                "========================================"
        );
        System.out.println(
                "BBS RECORD MODEL TEST"
        );
        System.out.println(
                "========================================"
        );

        System.out.println(
                "File: "
                        + recordFile.getAbsolutePath()
        );

        if (!recordFile.exists())
        {
            System.out.println(
                    "ERROR: Record file does not exist."
            );

            done = true;
            return;
        }

        try
        {
            BlockbusterRecord record =
                    BlockbusterRecordIO.load(
                            recordFile
                    );

            System.out.println(
                    "Record loaded successfully."
            );

            System.out.println(
                    "Filename: "
                            + record.getFilename()
            );

            System.out.println(
                    "Version: "
                            + record.getVersion()
            );

            System.out.println(
                    "PreDelay: "
                            + record.getPreDelay()
            );

            System.out.println(
                    "PostDelay: "
                            + record.getPostDelay()
            );

            System.out.println(
                    "Frame count: "
                            + record.getFrames().size()
            );

            System.out.println(
                    "Action timeline size: "
                            + record.getActions().size()
            );

            System.out.println(
                    "Length: "
                            + record.getLength()
            );

            System.out.println(
                    "Full length: "
                            + record.getFullLength()
            );

            /*
             * Frame 0
             */

            BlockbusterRecordFrame frame =
                    record.getFrame(0);

            if (frame == null)
            {
                System.out.println(
                        "ERROR: Frame 0 is null."
                );
            }
            else
            {
                System.out.println("");
                System.out.println(
                        "FRAME 0"
                );

                System.out.println(
                        "Position:"
                );

                System.out.println(
                        "  X = "
                                + frame.getX()
                );

                System.out.println(
                        "  Y = "
                                + frame.getY()
                );

                System.out.println(
                        "  Z = "
                                + frame.getZ()
                );

                System.out.println(
                        "Rotation:"
                );

                System.out.println(
                        "  RX = "
                                + frame.getRx()
                );

                System.out.println(
                        "  RY = "
                                + frame.getRy()
                );

                System.out.println(
                        "  RZ = "
                                + frame.getRz()
                );

                System.out.println(
                        "  RW = "
                                + frame.getRw()
                );

                System.out.println(
                        "Motion:"
                );

                System.out.println(
                        "  MX = "
                                + frame.getMotionX()
                );

                System.out.println(
                        "  MY = "
                                + frame.getMotionY()
                );

                System.out.println(
                        "  MZ = "
                                + frame.getMotionZ()
                );

                System.out.println(
                        "State:"
                );

                System.out.println(
                        "  Ground = "
                                + frame.isGround()
                );

                System.out.println(
                        "  Airborne = "
                                + frame.isAirborne()
                );

                System.out.println(
                        "  Sneaking = "
                                + frame.isSneaking()
                );

                System.out.println(
                        "  Sprinting = "
                                + frame.isSprinting()
                );

                System.out.println(
                        "  Elytra = "
                                + frame.isElytra()
                );

                System.out.println(
                        "Player:"
                );

                System.out.println(
                        "  Fall = "
                                + frame.getFall()
                );

                System.out.println(
                        "  HotbarSlot = "
                                + frame.getHotbarSlot()
                );

                System.out.println(
                        "  FoodLevel = "
                                + frame.getFoodLevel()
                );

                System.out.println(
                        "  TotalExperience = "
                                + frame.getTotalExperience()
                );

                /*
                 * Actions
                 */

                System.out.println("");
                System.out.println(
                        "FRAME 0 ACTIONS"
                );

                System.out.println(
                        "Action count: "
                                + frame.getActions().size()
                );

                for (int i = 0;
                     i < frame.getActions().size();
                     i++)
                {
                    BlockbusterRecordAction action =
                            frame.getActions().get(i);

                    if (action == null)
                    {
                        System.out.println(
                                "  Action "
                                        + i
                                        + ": null"
                        );

                        continue;
                    }

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

                    System.out.println(
                            "    Has Slot = "
                                    + actionNBT.hasKey(
                                    "Slot"
                            )
                    );

                    if (actionNBT.hasKey("Slot"))
                    {
                        System.out.println(
                                "    Slot = "
                                        + actionNBT
                                        .getInteger(
                                                "Slot"
                                        )
                        );
                    }

                    System.out.println(
                            "    Has ItemStack = "
                                    + actionNBT.hasKey(
                                    "ItemStack",
                                    10
                            )
                    );
                }
            }

            /*
             * Check synchronization.
             */

            System.out.println("");
            System.out.println(
                    "SYNCHRONIZATION TEST"
            );

            boolean synchronizedLists =
                    record.getFrames().size()
                            == record.getActions().size();

            System.out.println(
                    "Frames == Actions: "
                            + synchronizedLists
            );

            if (synchronizedLists)
            {
                System.out.println(
                        "BBS RECORD MODEL TEST SUCCESS"
                );
            }
            else
            {
                System.out.println(
                        "ERROR: Frame/action lists "
                                + "are not synchronized."
                );
            }
        }
        catch (Exception e)
        {
            System.out.println("");
            System.out.println(
                    "BBS RECORD MODEL TEST FAILED"
            );

            e.printStackTrace();
        }

        System.out.println(
                "========================================"
        );

        done = true;
    }
}