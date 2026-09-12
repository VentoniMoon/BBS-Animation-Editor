package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

import java.io.File;

public class BlockbusterRecordRoundTripDebug
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
            File worldDirectory =
                    net.minecraft.client.Minecraft
                            .getMinecraft()
                            .getIntegratedServer()
                            .getWorld(0)
                            .getSaveHandler()
                            .getWorldDirectory();


            File recordsDirectory =
                    new File(
                            worldDirectory,
                            "blockbuster/records"
                    );


            File originalFile =
                    new File(
                            recordsDirectory,
                            "1341_1.dat"
                    );


            File testFile =
                    new File(
                            recordsDirectory,
                            "1341_1_test.dat"
                    );


            System.out.println(
                    ""
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "BBS RECORD ROUND-TRIP TEST"
            );

            System.out.println(
                    "========================================"
            );


            /*
             * Check original file.
             */

            System.out.println(
                    "Original file:"
            );

            System.out.println(
                    originalFile
                            .getAbsolutePath()
            );


            if (!originalFile.exists())
            {
                System.out.println(
                        "ERROR: original record does not exist!"
                );

                return;
            }


            /*
             * Delete previous test file.
             */

            if (testFile.exists())
            {
                if (!testFile.delete())
                {
                    System.out.println(
                            "ERROR: could not delete "
                                    + "old test file!"
                    );

                    return;
                }
            }


            /*
             * LOAD ORIGINAL
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "[1] Loading original record..."
            );


            BlockbusterRecord original =
                    BlockbusterRecordIO.load(
                            originalFile
                    );


            if (original == null)
            {
                System.out.println(
                        "ERROR: original record is null!"
                );

                return;
            }


            System.out.println(
                    "Original loaded."
            );


            /*
             * ORIGINAL DATA
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "[2] Original record data:"
            );

            printRecordInfo(
                    original
            );


            /*
             * SAVE COPY
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "[3] Saving test copy..."
            );


            BlockbusterRecordIO.save(
                    original,
                    testFile
            );


            if (!testFile.exists())
            {
                System.out.println(
                        "ERROR: test file was not created!"
                );

                return;
            }


            System.out.println(
                    "Test file created:"
            );

            System.out.println(
                    testFile.getAbsolutePath()
            );


            /*
             * LOAD COPY
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "[4] Loading test copy..."
            );


            BlockbusterRecord reloaded =
                    BlockbusterRecordIO.load(
                            testFile
                    );


            if (reloaded == null)
            {
                System.out.println(
                        "ERROR: reloaded record is null!"
                );

                return;
            }


            System.out.println(
                    "Test copy loaded."
            );


            /*
             * COMPARE
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "[5] Comparing records..."
            );


            int passed = 0;
            int failed = 0;


            /*
             * Length
             */

            if (
                    original.getLength()
                            ==
                            reloaded.getLength()
            )
            {
                passed++;

                System.out.println(
                        "PASS: length = "
                                + original.getLength()
                );
            }
            else
            {
                failed++;

                System.out.println(
                        "FAIL: length"
                );

                System.out.println(
                        "  Original: "
                                + original.getLength()
                );

                System.out.println(
                        "  Reloaded: "
                                + reloaded.getLength()
                );
            }


            /*
             * PreDelay
             */

            if (
                    original.getPreDelay()
                            ==
                            reloaded.getPreDelay()
            )
            {
                passed++;

                System.out.println(
                        "PASS: PreDelay = "
                                + original.getPreDelay()
                );
            }
            else
            {
                failed++;

                System.out.println(
                        "FAIL: PreDelay"
                );
            }


            /*
             * PostDelay
             */

            if (
                    original.getPostDelay()
                            ==
                            reloaded.getPostDelay()
            )
            {
                passed++;

                System.out.println(
                        "PASS: PostDelay = "
                                + original.getPostDelay()
                );
            }
            else
            {
                failed++;

                System.out.println(
                        "FAIL: PostDelay"
                );
            }


            /*
             * PlayerData
             */

            if (
                    compareNBT(
                            original.getPlayerData(),
                            reloaded.getPlayerData()
                    )
            )
            {
                passed++;

                System.out.println(
                        "PASS: PlayerData"
                );
            }
            else
            {
                failed++;

                System.out.println(
                        "FAIL: PlayerData"
                );
            }


            /*
             * First frame
             */

            if (
                    original.getFrame(0) != null
                            &&
                            reloaded.getFrame(0) != null
            )
            {
                BlockbusterRecordFrame a =
                        original.getFrame(0);

                BlockbusterRecordFrame b =
                        reloaded.getFrame(0);


                /*
                 * Position
                 */

                if (
                        nearlyEqual(
                                a.getX(),
                                b.getX()
                        )
                                &&
                                nearlyEqual(
                                        a.getY(),
                                        b.getY()
                                )
                                &&
                                nearlyEqual(
                                        a.getZ(),
                                        b.getZ()
                                )
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 position"
                    );
                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 position"
                    );

                    System.out.println(
                            "  Original: "
                                    + a.getX()
                                    + ", "
                                    + a.getY()
                                    + ", "
                                    + a.getZ()
                    );

                    System.out.println(
                            "  Reloaded: "
                                    + b.getX()
                                    + ", "
                                    + b.getY()
                                    + ", "
                                    + b.getZ()
                    );
                }


                /*
                 * Rotation
                 */

                if (
                        nearlyEqual(
                                a.getYaw(),
                                b.getYaw()
                        )
                                &&
                                nearlyEqual(
                                        a.getPitch(),
                                        b.getPitch()
                                )
                                &&
                                nearlyEqual(
                                        a.getYawHead(),
                                        b.getYawHead()
                                )
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 rotation"
                    );
                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 rotation"
                    );
                }


                /*
                 * Motion
                 */

                if (
                        nearlyEqual(
                                a.getMotionX(),
                                b.getMotionX()
                        )
                                &&
                                nearlyEqual(
                                        a.getMotionY(),
                                        b.getMotionY()
                                )
                                &&
                                nearlyEqual(
                                        a.getMotionZ(),
                                        b.getMotionZ()
                                )
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 motion"
                    );
                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 motion"
                    );
                }


                /*
                 * Player states
                 */

                if (
                        a.isSneaking()
                                ==
                                b.isSneaking()
                                &&
                                a.isSprinting()
                                        ==
                                        b.isSprinting()
                                &&
                                a.isOnGround()
                                        ==
                                        b.isOnGround()
                                &&
                                a.isAirborne()
                                        ==
                                        b.isAirborne()
                                &&
                                a.isFlyingElytra()
                                        ==
                                        b.isFlyingElytra()
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 player states"
                    );
                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 player states"
                    );
                }


                /*
                 * Actions
                 */

                int originalActions =
                        original.getActions(0) == null
                                ? 0
                                : original
                                .getActions(0)
                                .size();


                int reloadedActions =
                        reloaded.getActions(0) == null
                                ? 0
                                : reloaded
                                .getActions(0)
                                .size();


                if (
                        originalActions
                                ==
                                reloadedActions
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 actions = "
                                    + originalActions
                    );
                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 actions"
                    );

                    System.out.println(
                            "  Original: "
                                    + originalActions
                    );

                    System.out.println(
                            "  Reloaded: "
                                    + reloadedActions
                    );
                }


                /*
                 * Action types
                 */

                if (
                        compareActionTypes(
                                original,
                                reloaded,
                                0
                        )
                )
                {
                    passed++;

                    System.out.println(
                            "PASS: frame 0 action types"
                    );

                    BlockbusterActionRegistry originalRegistry =
                            original.getActionRegistry();

                    BlockbusterActionRegistry loadedRegistry =
                            reloaded.getActionRegistry();


                    /*
                     * Compare registry size.
                     */
                    if (
                            originalRegistry.size()
                                    ==
                                    loadedRegistry.size()
                    )
                    {
                        System.out.println(
                                "PASS: action registry size = "
                                        + originalRegistry.size()
                        );

                        passed++;
                    }
                    else
                    {
                        System.out.println(
                                "FAIL: action registry size: "
                                        + originalRegistry.size()
                                        + " != "
                                        + loadedRegistry.size()
                        );

                        failed++;
                    }


                    /*
                     * Compare every action ID and name.
                     */
                    boolean registryEqual = true;

                    for (
                            java.util.Map.Entry<Byte, String> entry :
                            originalRegistry.getActions().entrySet()
                    )
                    {
                        byte id = entry.getKey();

                        String originalName =
                                entry.getValue();

                        String loadedName =
                                loadedRegistry.getName(id);

                        if (
                                loadedName == null
                                        ||
                                        !loadedName.equals(originalName)
                        )
                        {
                            registryEqual = false;

                            System.out.println(
                                    "FAIL: action "
                                            + (id & 0xFF)
                                            + " = "
                                            + originalName
                                            + " -> "
                                            + loadedName
                            );
                        }
                    }


                    if (registryEqual)
                    {
                        System.out.println(
                                "PASS: all action registry entries"
                        );

                        passed++;
                    }
                    else
                    {
                        failed++;
                    }

                }
                else
                {
                    failed++;

                    System.out.println(
                            "FAIL: frame 0 action types"
                    );
                }
            }
            else
            {
                failed++;

                System.out.println(
                        "FAIL: frame 0 does not exist"
                );
            }


            /*
             * Final result
             */

            System.out.println(
                    ""
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "ROUND-TRIP RESULT"
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "Passed: "
                            + passed
            );

            System.out.println(
                    "Failed: "
                            + failed
            );


            if (failed == 0)
            {
                System.out.println(
                        ""
                );

                System.out.println(
                        "ROUND-TRIP TEST SUCCESS"
                );

                System.out.println(
                        "The known record data survived "
                                + "load -> save -> load."
                );
            }
            else
            {
                System.out.println(
                        ""
                );

                System.out.println(
                        "ROUND-TRIP TEST FAILED"
                );
            }


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    ""
            );
        }
        catch (Exception e)
        {
            System.out.println(
                    ""
            );

            System.out.println(
                    "ROUND-TRIP TEST ERROR"
            );

            e.printStackTrace();
        }
    }


    private static void printRecordInfo(
            BlockbusterRecord record
    )
    {
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
                "Frames: "
                        + record.getFrames().size()
        );

        System.out.println(
                "Actions timeline: "
                        + record.getActions().size()
        );

        System.out.println(
                "Full length: "
                        + record.getFullLength()
        );


        if (record.getFrame(0) != null)
        {
            BlockbusterRecordFrame frame =
                    record.getFrame(0);

            System.out.println(
                    "Frame 0 position: "
                            + frame.getX()
                            + ", "
                            + frame.getY()
                            + ", "
                            + frame.getZ()
            );

            System.out.println(
                    "Frame 0 rotation: "
                            + frame.getYaw()
                            + ", "
                            + frame.getPitch()
                            + ", "
                            + frame.getYawHead()
            );


            int actions =
                    record.getActions(0) == null
                            ? 0
                            : record
                            .getActions(0)
                            .size();

            System.out.println(
                    "Frame 0 actions: "
                            + actions
            );
        }
    }


    private static boolean compareActionTypes(
            BlockbusterRecord a,
            BlockbusterRecord b,
            int tick
    )
    {
        if (
                a.getActions(tick) == null
                        ||
                        b.getActions(tick) == null
        )
        {
            return
                    a.getActions(tick)
                            ==
                            b.getActions(tick);
        }


        if (
                a.getActions(tick).size()
                        !=
                        b.getActions(tick).size()
        )
        {
            return false;
        }


        for (
                int i = 0;
                i < a.getActions(tick).size();
                i++
        )
        {
            byte typeA =
                    a.getActions(tick)
                            .get(i)
                            .getType();

            byte typeB =
                    b.getActions(tick)
                            .get(i)
                            .getType();


            if (typeA != typeB)
            {
                return false;
            }
        }


        return true;
    }


    private static boolean compareNBT(
            NBTTagCompound a,
            NBTTagCompound b
    )
    {
        if (a == null || b == null)
        {
            return a == b;
        }

        return a.equals(b);
    }


    private static boolean nearlyEqual(
            double a,
            double b
    )
    {
        return Math.abs(a - b) < 0.00001D;
    }


    private static boolean nearlyEqual(
            float a,
            float b
    )
    {
        return Math.abs(a - b) < 0.00001F;
    }
}