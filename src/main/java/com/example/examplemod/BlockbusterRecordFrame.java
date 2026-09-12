package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class BlockbusterRecordFrame
{
    /*
     * Position
     */

    private double x;
    private double y;
    private double z;

    /*
     * Player rotation
     *
     * RX = yaw
     * RY = pitch
     * RZ = yawHead
     * RW = bodyYaw
     */

    private float yaw;
    private float pitch;
    private float yawHead;

    private boolean hasBodyYaw;
    private float bodyYaw;

    /*
     * Mount
     */

    private boolean mounted;
    private float mountYaw;
    private float mountPitch;

    /*
     * Motion
     */

    private float motionX;
    private float motionY;
    private float motionZ;

    /*
     * Entity state
     */

    private float fallDistance;

    private boolean airborne;
    private boolean sneaking;
    private boolean sprinting;
    private boolean ground;
    private boolean flyingElytra;

    /*
     * Client data
     */

    private byte activeHands;

    private float roll;

    /*
     * Player data
     */

    private int hotbarSlot;
    private int foodLevel;
    private int totalExperience;

    /*
     * Actions
     */

    private final List<BlockbusterRecordAction> actions =
            new ArrayList<BlockbusterRecordAction>();


    public BlockbusterRecordFrame()
    {
    }


    /*
     * NBT
     */

    public static BlockbusterRecordFrame fromNBT(
            NBTTagCompound nbt
    )
    {
        BlockbusterRecordFrame frame =
                new BlockbusterRecordFrame();

        /*
         * Position
         */

        frame.x =
                nbt.getDouble("X");

        frame.y =
                nbt.getDouble("Y");

        frame.z =
                nbt.getDouble("Z");


        /*
         * Motion
         */

        frame.motionX =
                nbt.getFloat("MX");

        frame.motionY =
                nbt.getFloat("MY");

        frame.motionZ =
                nbt.getFloat("MZ");


        /*
         * Rotation
         */

        frame.yaw =
                nbt.getFloat("RX");

        frame.pitch =
                nbt.getFloat("RY");

        frame.yawHead =
                nbt.getFloat("RZ");


        /*
         * Body yaw
         */

        if (nbt.hasKey("RW"))
        {
            frame.hasBodyYaw = true;

            frame.bodyYaw =
                    nbt.getFloat("RW");
        }


        /*
         * Mount
         */

        if (
                nbt.hasKey("MRX") &&
                        nbt.hasKey("MRY")
        )
        {
            frame.mounted = true;

            frame.mountYaw =
                    nbt.getFloat("MRX");

            frame.mountPitch =
                    nbt.getFloat("MRY");
        }


        /*
         * Fall distance
         */

        frame.fallDistance =
                nbt.getFloat("Fall");


        /*
         * Entity states
         */

        frame.airborne =
                nbt.getBoolean("Airborne");

        frame.flyingElytra =
                nbt.getBoolean("Elytra");

        frame.sneaking =
                nbt.getBoolean("Sneaking");

        frame.sprinting =
                nbt.getBoolean("Sprinting");

        frame.ground =
                nbt.getBoolean("Ground");


        /*
         * Active hands
         */

        if (nbt.hasKey("Hands"))
        {
            frame.activeHands =
                    nbt.getByte("Hands");
        }


        /*
         * Camera roll
         */

        if (nbt.hasKey("Roll"))
        {
            frame.roll =
                    nbt.getFloat("Roll");
        }


        /*
         * Player data
         */

        if (nbt.hasKey("HotbarSlot"))
        {
            frame.hotbarSlot =
                    nbt.getInteger(
                            "HotbarSlot"
                    );
        }

        if (nbt.hasKey("FoodLevel"))
        {
            frame.foodLevel =
                    nbt.getInteger(
                            "FoodLevel"
                    );
        }

        if (nbt.hasKey("TotalExperience"))
        {
            frame.totalExperience =
                    nbt.getInteger(
                            "TotalExperience"
                    );
        }


        /*
         * Actions
         */

        if (nbt.hasKey("Action", 9))
        {
            NBTTagList actionList =
                    nbt.getTagList(
                            "Action",
                            10
                    );

            for (
                    int i = 0;
                    i < actionList.tagCount();
                    i++
            )
            {
                NBTTagCompound actionNBT =
                        actionList.getCompoundTagAt(i);

                frame.actions.add(
                        BlockbusterRecordAction.fromNBT(
                                actionNBT
                        )
                );
            }
        }

        return frame;
    }


    /*
     * Convert frame back to Blockbuster NBT.
     */

    public NBTTagCompound toNBT()
    {
        NBTTagCompound nbt =
                new NBTTagCompound();


        /*
         * Position
         */

        nbt.setDouble(
                "X",
                this.x
        );

        nbt.setDouble(
                "Y",
                this.y
        );

        nbt.setDouble(
                "Z",
                this.z
        );


        /*
         * Motion
         */

        nbt.setFloat(
                "MX",
                this.motionX
        );

        nbt.setFloat(
                "MY",
                this.motionY
        );

        nbt.setFloat(
                "MZ",
                this.motionZ
        );


        /*
         * Rotation
         */

        nbt.setFloat(
                "RX",
                this.yaw
        );

        nbt.setFloat(
                "RY",
                this.pitch
        );

        nbt.setFloat(
                "RZ",
                this.yawHead
        );


        /*
         * Body yaw
         */

        if (this.hasBodyYaw)
        {
            nbt.setFloat(
                    "RW",
                    this.bodyYaw
            );
        }


        /*
         * Mount
         */

        if (this.mounted)
        {
            nbt.setFloat(
                    "MRX",
                    this.mountYaw
            );

            nbt.setFloat(
                    "MRY",
                    this.mountPitch
            );
        }


        /*
         * Fall distance
         */

        nbt.setFloat(
                "Fall",
                this.fallDistance
        );


        /*
         * States
         */

        nbt.setBoolean(
                "Airborne",
                this.airborne
        );

        nbt.setBoolean(
                "Elytra",
                this.flyingElytra
        );

        nbt.setBoolean(
                "Sneaking",
                this.sneaking
        );

        nbt.setBoolean(
                "Sprinting",
                this.sprinting
        );

        nbt.setBoolean(
                "Ground",
                this.ground
        );


        /*
         * Active hands
         */

        if (this.activeHands > 0)
        {
            nbt.setByte(
                    "Hands",
                    this.activeHands
            );
        }


        /*
         * Roll
         */

        if (this.roll != 0)
        {
            nbt.setFloat(
                    "Roll",
                    this.roll
            );
        }


        /*
         * Player data
         */

        nbt.setInteger(
                "HotbarSlot",
                this.hotbarSlot
        );

        nbt.setInteger(
                "FoodLevel",
                this.foodLevel
        );

        nbt.setInteger(
                "TotalExperience",
                this.totalExperience
        );


        /*
         * Actions
         */

        if (!this.actions.isEmpty())
        {
            NBTTagList actionList =
                    new NBTTagList();

            for (
                    BlockbusterRecordAction action :
                    this.actions
            )
            {
                if (action != null)
                {
                    actionList.appendTag(
                            action.getNBT()
                    );
                }
            }

            nbt.setTag(
                    "Action",
                    actionList
            );
        }

        return nbt;
    }


    /*
     * Position
     */

    public double getX()
    {
        return this.x;
    }

    public void setX(
            double x
    )
    {
        this.x = x;
    }


    public double getY()
    {
        return this.y;
    }

    public void setY(
            double y
    )
    {
        this.y = y;
    }


    public double getZ()
    {
        return this.z;
    }

    public void setZ(
            double z
    )
    {
        this.z = z;
    }


    /*
     * Rotation
     */

    public float getYaw()
    {
        return this.yaw;
    }

    public void setYaw(
            float yaw
    )
    {
        this.yaw = yaw;
    }


    public float getPitch()
    {
        return this.pitch;
    }

    public void setPitch(
            float pitch
    )
    {
        this.pitch = pitch;
    }


    public float getYawHead()
    {
        return this.yawHead;
    }

    public void setYawHead(
            float yawHead
    )
    {
        this.yawHead = yawHead;
    }


    public boolean hasBodyYaw()
    {
        return this.hasBodyYaw;
    }

    public void setHasBodyYaw(
            boolean hasBodyYaw
    )
    {
        this.hasBodyYaw =
                hasBodyYaw;
    }


    public float getBodyYaw()
    {
        return this.bodyYaw;
    }

    public void setBodyYaw(
            float bodyYaw
    )
    {
        this.bodyYaw = bodyYaw;
        this.hasBodyYaw = true;
    }


    /*
     * Compatibility aliases
     *
     * Эти методы оставляем, чтобы старый код
     * редактора не пришлось сразу переписывать.
     */

    public float getRx()
    {
        return this.yaw;
    }

    public void setRx(
            float value
    )
    {
        this.yaw = value;
    }


    public float getRy()
    {
        return this.pitch;
    }

    public void setRy(
            float value
    )
    {
        this.pitch = value;
    }


    public float getRz()
    {
        return this.yawHead;
    }

    public void setRz(
            float value
    )
    {
        this.yawHead = value;
    }


    public float getRw()
    {
        return this.bodyYaw;
    }

    public void setRw(
            float value
    )
    {
        this.bodyYaw = value;
        this.hasBodyYaw = true;
    }


    /*
     * Mount
     */

    public boolean isMounted()
    {
        return this.mounted;
    }

    public void setMounted(
            boolean mounted
    )
    {
        this.mounted = mounted;
    }


    public float getMountYaw()
    {
        return this.mountYaw;
    }

    public void setMountYaw(
            float mountYaw
    )
    {
        this.mountYaw = mountYaw;
    }


    public float getMountPitch()
    {
        return this.mountPitch;
    }

    public void setMountPitch(
            float mountPitch
    )
    {
        this.mountPitch = mountPitch;
    }


    /*
     * Motion
     */

    public float getMotionX()
    {
        return this.motionX;
    }

    public void setMotionX(
            float motionX
    )
    {
        this.motionX = motionX;
    }


    public float getMotionY()
    {
        return this.motionY;
    }

    public void setMotionY(
            float motionY
    )
    {
        this.motionY = motionY;
    }


    public float getMotionZ()
    {
        return this.motionZ;
    }

    public void setMotionZ(
            float motionZ
    )
    {
        this.motionZ = motionZ;
    }


    /*
     * State
     */

    public float getFall()
    {
        return this.fallDistance;
    }

    public float getFallDistance()
    {
        return this.fallDistance;
    }

    public void setFall(
            float fall
    )
    {
        this.fallDistance = fall;
    }

    public void setFallDistance(
            float fallDistance
    )
    {
        this.fallDistance =
                fallDistance;
    }


    public boolean isGround()
    {
        return this.ground;
    }

    public boolean isOnGround()
    {
        return this.ground;
    }

    public void setGround(
            boolean ground
    )
    {
        this.ground = ground;
    }

    public void setOnGround(
            boolean ground
    )
    {
        this.ground = ground;
    }


    public boolean isAirborne()
    {
        return this.airborne;
    }

    public void setAirborne(
            boolean airborne
    )
    {
        this.airborne =
                airborne;
    }


    public boolean isSneaking()
    {
        return this.sneaking;
    }

    public void setSneaking(
            boolean sneaking
    )
    {
        this.sneaking =
                sneaking;
    }


    public boolean isSprinting()
    {
        return this.sprinting;
    }

    public void setSprinting(
            boolean sprinting
    )
    {
        this.sprinting =
                sprinting;
    }


    public boolean isElytra()
    {
        return this.flyingElytra;
    }

    public boolean isFlyingElytra()
    {
        return this.flyingElytra;
    }

    public void setElytra(
            boolean elytra
    )
    {
        this.flyingElytra =
                elytra;
    }

    public void setFlyingElytra(
            boolean elytra
    )
    {
        this.flyingElytra =
                elytra;
    }


    /*
     * Hands / roll
     */

    public byte getActiveHands()
    {
        return this.activeHands;
    }

    public void setActiveHands(
            byte activeHands
    )
    {
        this.activeHands =
                activeHands;
    }


    public float getRoll()
    {
        return this.roll;
    }

    public void setRoll(
            float roll
    )
    {
        this.roll = roll;
    }


    /*
     * Player data
     */

    public int getHotbarSlot()
    {
        return this.hotbarSlot;
    }

    public void setHotbarSlot(
            int hotbarSlot
    )
    {
        this.hotbarSlot =
                hotbarSlot;
    }


    public int getFoodLevel()
    {
        return this.foodLevel;
    }

    public void setFoodLevel(
            int foodLevel
    )
    {
        this.foodLevel =
                foodLevel;
    }


    public int getTotalExperience()
    {
        return this.totalExperience;
    }

    public void setTotalExperience(
            int totalExperience
    )
    {
        this.totalExperience =
                totalExperience;
    }


    /*
     * Actions
     */

    public List<BlockbusterRecordAction> getActions()
    {
        return this.actions;
    }
}