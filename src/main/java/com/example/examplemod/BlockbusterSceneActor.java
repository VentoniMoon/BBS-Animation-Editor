package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Один актер внутри Blockbuster Scene.
 *
 * Это наша модель данных, соответствующая оригинальному
 * Blockbuster Replay.
 */
public class BlockbusterSceneActor
{
    /*
     * ---------------------------------------------------------
     * ID
     * ---------------------------------------------------------
     *
     * Например:
     *
     * 1341_1
     * 1341_2
     */

    private String id;

    /*
     * ---------------------------------------------------------
     * BASIC DATA
     * ---------------------------------------------------------
     */

    private String name;
    private String target;

    private boolean invincible;
    private boolean enableBurning;
    private boolean teleportBack;

    private boolean invisible;
    private boolean enabled;
    private boolean fake;

    /*
     * ---------------------------------------------------------
     * PLAYER DATA
     * ---------------------------------------------------------
     */

    private float health;

    private int foodLevel;
    private int totalExperience;

    private boolean renderLast;
    private boolean playbackXPFoodLevel;

    /*
     * ---------------------------------------------------------
     * MORPH
     * ---------------------------------------------------------
     *
     * Пока сохраняем Morph как оригинальный NBT.
     *
     * Это важно:
     * мы не хотим потерять данные Metamorph.
     */

    private NBTTagCompound morph;

    /*
     * ---------------------------------------------------------
     * CONSTRUCTOR
     * ---------------------------------------------------------
     */

    public BlockbusterSceneActor()
    {
        this.id = "";

        this.name = "";
        this.target = "";

        this.invincible = false;
        this.enableBurning = true;
        this.teleportBack = true;

        this.invisible = false;
        this.enabled = true;
        this.fake = false;

        this.health = 20.0F;

        this.foodLevel = 20;
        this.totalExperience = 0;

        this.renderLast = false;
        this.playbackXPFoodLevel = false;

        this.morph = null;
    }

    /*
     * ---------------------------------------------------------
     * GETTERS / SETTERS
     * ---------------------------------------------------------
     */

    public String getId()
    {
        return this.id;
    }

    public void setId(String id)
    {
        this.id =
                id == null
                        ? ""
                        : id;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name =
                name == null
                        ? ""
                        : name;
    }

    public String getTarget()
    {
        return this.target;
    }

    public void setTarget(String target)
    {
        this.target =
                target == null
                        ? ""
                        : target;
    }

    public boolean isInvincible()
    {
        return this.invincible;
    }

    public void setInvincible(
            boolean invincible)
    {
        this.invincible =
                invincible;
    }

    public boolean isEnableBurning()
    {
        return this.enableBurning;
    }

    public void setEnableBurning(
            boolean enableBurning)
    {
        this.enableBurning =
                enableBurning;
    }

    public boolean isTeleportBack()
    {
        return this.teleportBack;
    }

    public void setTeleportBack(
            boolean teleportBack)
    {
        this.teleportBack =
                teleportBack;
    }

    public boolean isInvisible()
    {
        return this.invisible;
    }

    public void setInvisible(
            boolean invisible)
    {
        this.invisible =
                invisible;
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void setEnabled(
            boolean enabled)
    {
        this.enabled =
                enabled;
    }

    public boolean isFake()
    {
        return this.fake;
    }

    public void setFake(
            boolean fake)
    {
        this.fake =
                fake;
    }

    public float getHealth()
    {
        return this.health;
    }

    public void setHealth(
            float health)
    {
        this.health =
                health;
    }

    public int getFoodLevel()
    {
        return this.foodLevel;
    }

    public void setFoodLevel(
            int foodLevel)
    {
        this.foodLevel =
                foodLevel;
    }

    public int getTotalExperience()
    {
        return this.totalExperience;
    }

    public void setTotalExperience(
            int totalExperience)
    {
        this.totalExperience =
                totalExperience;
    }

    public boolean isRenderLast()
    {
        return this.renderLast;
    }

    public void setRenderLast(
            boolean renderLast)
    {
        this.renderLast =
                renderLast;
    }

    public boolean isPlaybackXPFoodLevel()
    {
        return this.playbackXPFoodLevel;
    }

    public void setPlaybackXPFoodLevel(
            boolean playbackXPFoodLevel)
    {
        this.playbackXPFoodLevel =
                playbackXPFoodLevel;
    }

    public NBTTagCompound getMorph()
    {
        return this.morph;
    }

    public void setMorph(
            NBTTagCompound morph)
    {
        this.morph =
                morph;
    }

    /*
     * ---------------------------------------------------------
     * NBT
     * ---------------------------------------------------------
     */

    public void fromNBT(
            NBTTagCompound tag)
    {
        if (tag == null)
        {
            return;
        }

        this.id =
                tag.getString("Id");

        this.name =
                tag.getString("Name");

        this.target =
                tag.getString("Target");

        this.invincible =
                tag.getBoolean("Invincible");

        this.invisible =
                tag.getBoolean("Invisible");

        this.enableBurning =
                tag.getBoolean("EnableBurning");

        this.enabled =
                tag.hasKey("Enabled")
                        ? tag.getBoolean("Enabled")
                        : true;

        this.fake =
                tag.getBoolean("Fake");

        this.teleportBack =
                tag.hasKey("TP")
                        ? tag.getBoolean("TP")
                        : true;

        this.health =
                tag.hasKey("Health")
                        ? tag.getFloat("Health")
                        : 20.0F;

        this.foodLevel =
                tag.hasKey("FoodLevel")
                        ? tag.getInteger(
                        "FoodLevel"
                )
                        : 20;

        this.totalExperience =
                tag.hasKey("TotalExperience")
                        ? tag.getInteger(
                        "TotalExperience"
                )
                        : 0;

        this.renderLast =
                tag.hasKey("RenderLast")
                        && tag.getBoolean(
                        "RenderLast"
                );

        this.playbackXPFoodLevel =
                tag.hasKey(
                        "PlaybackXPFoodLevel"
                )
                        && tag.getBoolean(
                        "PlaybackXPFoodLevel"
                );

        if (tag.hasKey("Morph"))
        {
            this.morph =
                    tag.getCompoundTag(
                            "Morph"
                    );
        }
        else
        {
            this.morph = null;
        }
    }

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setString(
                "Id",
                this.id
        );

        tag.setString(
                "Name",
                this.name
        );

        tag.setString(
                "Target",
                this.target
        );

        if (this.morph != null)
        {
            tag.setTag(
                    "Morph",
                    this.morph.copy()
            );
        }

        tag.setBoolean(
                "Invincible",
                this.invincible
        );

        tag.setBoolean(
                "Invisible",
                this.invisible
        );

        tag.setBoolean(
                "EnableBurning",
                this.enableBurning
        );

        tag.setBoolean(
                "Enabled",
                this.enabled
        );

        tag.setBoolean(
                "Fake",
                this.fake
        );

        if (!this.teleportBack)
        {
            tag.setBoolean(
                    "TP",
                    false
            );
        }

        if (this.health != 20.0F)
        {
            tag.setFloat(
                    "Health",
                    this.health
            );
        }

        if (this.foodLevel != 20)
        {
            tag.setInteger(
                    "FoodLevel",
                    this.foodLevel
            );
        }

        if (this.totalExperience != 0)
        {
            tag.setInteger(
                    "TotalExperience",
                    this.totalExperience
            );
        }

        if (this.renderLast)
        {
            tag.setBoolean(
                    "RenderLast",
                    true
            );
        }

        if (this.playbackXPFoodLevel)
        {
            tag.setBoolean(
                    "PlaybackXPFoodLevel",
                    true
            );
        }

        return tag;
    }
}