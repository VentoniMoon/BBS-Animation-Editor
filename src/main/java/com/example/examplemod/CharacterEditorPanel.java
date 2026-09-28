package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Панель Character Mode.
 *
 * Character Mode работает с самим Actor,
 * его внешним видом и настройками.
 *
 * Pose Mode отвечает за:
 *   Bones
 *   Keyframes
 *   Transform
 *   Interpolation
 *
 * Body Parts Mode будет отдельной системой.
 */
public class CharacterEditorPanel
{
    /*
     * =========================================================
     * SECTIONS
     * =========================================================
     */

    public enum Section
    {
        APPEARANCE,
        ANIMATION_SETUP,
        BODY_PART_OVERRIDES,
        ACTOR_SETTINGS
    }

    private Section openedSection =
            Section.APPEARANCE;

    /*
     * =========================================================
     * POSITION
     * =========================================================
     */

    private int x;
    private int y;
    private int width;
    private int height;

    /*
     * =========================================================
     * VISUAL
     * =========================================================
     */

    private static final int COLOR_PANEL =
            0xFF17191B;

    private static final int COLOR_PANEL_DARK =
            0xFF111315;

    private static final int COLOR_PANEL_LIGHT =
            0xFF1D2023;

    private static final int COLOR_PANEL_HOVER =
            0xFF25292D;

    private static final int COLOR_SELECTED =
            0xFF28343A;

    private static final int COLOR_BORDER =
            0xFF303438;

    private static final int COLOR_TEXT =
            0xFFE2E5E7;

    private static final int COLOR_TEXT_SECONDARY =
            0xFF9AA1A6;

    private static final int COLOR_TEXT_MUTED =
            0xFF666D72;

    private static final int COLOR_CYAN =
            0xFF66CCFF;

    private static final int COLOR_CYAN_BRIGHT =
            0xFF8BE1FF;

    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private final CharacterEditorController characterController;

    private BlockbusterSceneActorData selectedActor;

    private int currentFrame;

    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public CharacterEditorPanel(
            CharacterEditorController characterController)
    {
        this.characterController =
                characterController;
    }

    /*
     * =========================================================
     * POSITION
     * =========================================================
     */

    public void setPosition(
            int x,
            int y,
            int width,
            int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setBounds(
            int x,
            int y,
            int width,
            int height)
    {
        setPosition(
                x,
                y,
                width,
                height
        );
    }

    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    public void setSelectedActor(
            BlockbusterSceneActorData actor)
    {
        this.selectedActor = actor;
    }

    public BlockbusterSceneActorData getSelectedActor()
    {
        return this.selectedActor;
    }

    public void setCurrentFrame(
            int frame)
    {
        this.currentFrame = Math.max(
                0,
                frame
        );
    }

    public int getCurrentFrame()
    {
        return this.currentFrame;
    }

    /*
     * =========================================================
     * SECTION
     * =========================================================
     */

    public Section getOpenedSection()
    {
        return this.openedSection;
    }

    public void setOpenedSection(
            Section section)
    {
        if (section == null)
        {
            return;
        }

        this.openedSection = section;
    }

    private void toggleSection(
            Section section)
    {
        if (this.openedSection == section)
        {
            return;
        }

        this.openedSection = section;
    }

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            Minecraft mc,
            int mouseX,
            int mouseY)
    {
        if (mc == null ||
                mc.fontRenderer == null)
        {
            return;
        }

        /*
         * Background.
         */
        drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                COLOR_PANEL
        );

        /*
         * Left separator.
         */
        drawRect(
                this.x,
                this.y,
                this.x + 1,
                this.y + this.height,
                COLOR_BORDER
        );

        /*
         * Header.
         */
        drawHeader(
                mc,
                "CHARACTER"
        );

        int cursorY =
                this.y + 30;

        /*
         * -----------------------------------------------------
         * APPEARANCE
         * -----------------------------------------------------
         */

        cursorY =
                drawSection(
                        mc,
                        Section.APPEARANCE,
                        "APPEARANCE",
                        cursorY,
                        mouseX,
                        mouseY
                );

        if (this.openedSection ==
                Section.APPEARANCE)
        {
            cursorY += 4;

            drawAppearance(
                    mc,
                    cursorY
            );

            cursorY += 108;
        }

        cursorY += 4;

        /*
         * -----------------------------------------------------
         * ANIMATION SETUP
         * -----------------------------------------------------
         */

        cursorY =
                drawSection(
                        mc,
                        Section.ANIMATION_SETUP,
                        "ANIMATION SETUP",
                        cursorY,
                        mouseX,
                        mouseY
                );

        if (this.openedSection ==
                Section.ANIMATION_SETUP)
        {
            cursorY += 4;

            drawAnimationSetup(
                    mc,
                    cursorY
            );

            cursorY += 92;
        }

        cursorY += 4;

        /*
         * -----------------------------------------------------
         * BODY PART OVERRIDES
         * -----------------------------------------------------
         */

        cursorY =
                drawSection(
                        mc,
                        Section.BODY_PART_OVERRIDES,
                        "BODY PART OVERRIDES",
                        cursorY,
                        mouseX,
                        mouseY
                );

        if (this.openedSection ==
                Section.BODY_PART_OVERRIDES)
        {
            cursorY += 4;

            drawBodyPartOverrides(
                    mc,
                    cursorY
            );

            cursorY += 108;
        }

        cursorY += 4;

        /*
         * -----------------------------------------------------
         * ACTOR SETTINGS
         * -----------------------------------------------------
         */

        drawSection(
                mc,
                Section.ACTOR_SETTINGS,
                "ACTOR SETTINGS",
                cursorY,
                mouseX,
                mouseY
        );

        if (this.openedSection ==
                Section.ACTOR_SETTINGS)
        {
            cursorY += 4;

            drawActorSettings(
                    mc,
                    cursorY
            );
        }
    }

    /*
     * =========================================================
     * HEADER
     * =========================================================
     */

    private void drawHeader(
            Minecraft mc,
            String text)
    {
        drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + 25,
                COLOR_PANEL_DARK
        );

        drawRect(
                this.x,
                this.y + 24,
                this.x + this.width,
                this.y + 25,
                COLOR_BORDER
        );

        drawRect(
                this.x + 9,
                this.y + 7,
                this.x + 11,
                this.y + 18,
                COLOR_CYAN
        );

        mc.fontRenderer.drawString(
                text,
                this.x + 16,
                this.y + 8,
                COLOR_TEXT
        );
    }

    /*
     * =========================================================
     * SECTION
     * =========================================================
     */

    private int drawSection(
            Minecraft mc,
            Section section,
            String title,
            int y,
            int mouseX,
            int mouseY)
    {
        boolean hovered =
                mouseX >= this.x + 5 &&
                        mouseX < this.x + this.width - 5 &&
                        mouseY >= y &&
                        mouseY < y + 24;

        boolean opened =
                this.openedSection == section;

        drawRect(
                this.x + 5,
                y,
                this.x + this.width - 5,
                y + 24,
                hovered
                        ? COLOR_PANEL_HOVER
                        : opened
                        ? COLOR_SELECTED
                        : COLOR_PANEL_LIGHT
        );

        if (opened)
        {
            drawRect(
                    this.x + 5,
                    y,
                    this.x + 7,
                    y + 24,
                    COLOR_CYAN
            );
        }

        String arrow =
                opened
                        ? "▼"
                        : "▶";

        mc.fontRenderer.drawString(
                arrow,
                this.x + 10,
                y + 8,
                opened
                        ? COLOR_CYAN_BRIGHT
                        : COLOR_TEXT_SECONDARY
        );

        mc.fontRenderer.drawString(
                title,
                this.x + 24,
                y + 8,
                opened
                        ? COLOR_TEXT
                        : COLOR_TEXT_SECONDARY
        );

        return y + 24;
    }

    /*
     * =========================================================
     * APPEARANCE
     * =========================================================
     */

    private void drawAppearance(
            Minecraft mc,
            int y)
    {
        String actorName =
                "—";

        String actorId =
                "—";

        String morph =
                "—";

        String record =
                "Not loaded";

        String frames =
                "0";

        if (this.selectedActor != null)
        {
            actorName =
                    this.selectedActor.getName();

            if (actorName == null ||
                    actorName.length() == 0)
            {
                actorName =
                        "Unnamed";
            }

            actorId =
                    this.selectedActor.getId();

            if (actorId == null ||
                    actorId.length() == 0)
            {
                actorId =
                        "—";
            }

            morph =
                    this.selectedActor.getMorphName();

            if (morph == null ||
                    morph.length() == 0)
            {
                morph =
                        "Default";
            }

            if (this.selectedActor.hasRecord())
            {
                record =
                        "Loaded";

                frames =
                        String.valueOf(
                                this.selectedActor.getLength()
                        );
            }
        }

        /*
         * Actor
         */
        drawLabel(
                mc,
                "Actor",
                this.x + 12,
                y
        );

        drawValue(
                mc,
                actorName,
                this.x + 70,
                y
        );

        /*
         * ID
         */
        drawLabel(
                mc,
                "ID",
                this.x + 12,
                y + 18
        );

        drawValue(
                mc,
                actorId,
                this.x + 70,
                y + 18
        );

        /*
         * Morph
         */
        drawLabel(
                mc,
                "Morph",
                this.x + 12,
                y + 36
        );

        drawValue(
                mc,
                morph,
                this.x + 70,
                y + 36
        );

        /*
         * Record
         */
        drawLabel(
                mc,
                "Record",
                this.x + 12,
                y + 54
        );

        int recordColor =
                this.selectedActor != null &&
                        this.selectedActor.hasRecord()
                        ? COLOR_CYAN
                        : COLOR_TEXT_MUTED;

        drawColoredValue(
                mc,
                record,
                this.x + 70,
                y + 54,
                recordColor
        );

        /*
         * Frames
         */
        drawLabel(
                mc,
                "Frames",
                this.x + 12,
                y + 72
        );

        drawValue(
                mc,
                frames,
                this.x + 70,
                y + 72
        );

        /*
         * Current frame
         */
        drawLabel(
                mc,
                "Current",
                this.x + 12,
                y + 90
        );

        drawValue(
                mc,
                String.valueOf(
                        this.currentFrame
                ),
                this.x + 70,
                y + 90
        );
    }

    /*
     * =========================================================
     * ANIMATION SETUP
     * =========================================================
     */

    private void drawAnimationSetup(
            Minecraft mc,
            int y)
    {
        drawAnimationRow(
                mc,
                "Idle",
                "Not assigned",
                y
        );

        drawAnimationRow(
                mc,
                "Walk",
                "Not assigned",
                y + 18
        );

        drawAnimationRow(
                mc,
                "Run",
                "Not assigned",
                y + 36
        );

        drawAnimationRow(
                mc,
                "Jump",
                "Not assigned",
                y + 54
        );

        drawAnimationRow(
                mc,
                "Actions",
                "Configure...",
                y + 72
        );
    }

    private void drawAnimationRow(
            Minecraft mc,
            String name,
            String value,
            int y)
    {
        drawLabel(
                mc,
                name,
                this.x + 12,
                y
        );

        drawValueBox(
                mc,
                value,
                this.x + 68,
                y - 3,
                this.width - 80
        );
    }

    /*
     * =========================================================
     * BODY PART OVERRIDES
     * =========================================================
     */

    private void drawBodyPartOverrides(
            Minecraft mc,
            int y)
    {
        drawPartRow(
                mc,
                "Head",
                true,
                y
        );

        drawPartRow(
                mc,
                "Body",
                true,
                y + 18
        );

        drawPartRow(
                mc,
                "Left Arm",
                true,
                y + 36
        );

        drawPartRow(
                mc,
                "Right Arm",
                true,
                y + 54
        );

        drawPartRow(
                mc,
                "Left Leg",
                true,
                y + 72
        );

        drawPartRow(
                mc,
                "Right Leg",
                true,
                y + 90
        );
    }

    private void drawPartRow(
            Minecraft mc,
            String name,
            boolean enabled,
            int y)
    {
        drawLabel(
                mc,
                name,
                this.x + 12,
                y
        );

        String state =
                enabled
                        ? "ON"
                        : "OFF";

        int color =
                enabled
                        ? COLOR_CYAN
                        : COLOR_TEXT_MUTED;

        mc.fontRenderer.drawString(
                state,
                this.x +
                        this.width -
                        35,
                y,
                color
        );
    }

    /*
     * =========================================================
     * ACTOR SETTINGS
     * =========================================================
     */

    private void drawActorSettings(
            Minecraft mc,
            int y)
    {
        drawHint(
                mc,
                "Actor-level settings",
                this.x + 12,
                y
        );

        drawHint(
                mc,
                "will be configured here",
                this.x + 12,
                y + 14
        );
    }

    /*
     * =========================================================
     * TEXT
     * =========================================================
     */

    private void drawLabel(
            Minecraft mc,
            String text,
            int x,
            int y)
    {
        mc.fontRenderer.drawString(
                text,
                x,
                y,
                COLOR_TEXT_SECONDARY
        );
    }

    private void drawValue(
            Minecraft mc,
            String text,
            int x,
            int y)
    {
        drawColoredValue(
                mc,
                text,
                x,
                y,
                COLOR_TEXT
        );
    }

    private void drawColoredValue(
            Minecraft mc,
            String text,
            int x,
            int y,
            int color)
    {
        if (text == null)
        {
            text = "—";
        }

        if (text.length() > 18)
        {
            text =
                    text.substring(
                            0,
                            15
                    ) + "...";
        }

        mc.fontRenderer.drawString(
                text,
                x,
                y,
                color
        );
    }

    private void drawValueBox(
            Minecraft mc,
            String text,
            int x,
            int y,
            int width)
    {
        drawRect(
                x,
                y,
                x + width,
                y + 17,
                COLOR_PANEL_DARK
        );

        drawRect(
                x,
                y,
                x + width,
                y + 1,
                COLOR_BORDER
        );

        mc.fontRenderer.drawString(
                text,
                x + 5,
                y + 5,
                COLOR_TEXT_SECONDARY
        );

        mc.fontRenderer.drawString(
                "▼",
                x + width - 10,
                y + 5,
                COLOR_TEXT_MUTED
        );
    }

    private void drawHint(
            Minecraft mc,
            String text,
            int x,
            int y)
    {
        mc.fontRenderer.drawString(
                text,
                x,
                y,
                COLOR_TEXT_MUTED
        );
    }

    /*
     * =========================================================
     * INPUT
     * =========================================================
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (mouseButton != 0)
        {
            return false;
        }

        int currentY =
                this.y + 30;

        /*
         * Appearance
         */
        if (isInsideSection(
                currentY,
                mouseX,
                mouseY))
        {
            toggleSection(
                    Section.APPEARANCE
            );

            return true;
        }

        if (this.openedSection ==
                Section.APPEARANCE)
        {
            currentY +=
                    24 + 4 + 108;
        }
        else
        {
            currentY += 24;
        }

        currentY += 4;

        /*
         * Animation Setup
         */
        if (isInsideSection(
                currentY,
                mouseX,
                mouseY))
        {
            toggleSection(
                    Section.ANIMATION_SETUP
            );

            return true;
        }

        if (this.openedSection ==
                Section.ANIMATION_SETUP)
        {
            currentY +=
                    24 + 4 + 92;
        }
        else
        {
            currentY += 24;
        }

        currentY += 4;

        /*
         * Body Part Overrides
         */
        if (isInsideSection(
                currentY,
                mouseX,
                mouseY))
        {
            toggleSection(
                    Section.BODY_PART_OVERRIDES
            );

            return true;
        }

        if (this.openedSection ==
                Section.BODY_PART_OVERRIDES)
        {
            currentY +=
                    24 + 4 + 108;
        }
        else
        {
            currentY += 24;
        }

        currentY += 4;

        /*
         * Actor Settings
         */
        if (isInsideSection(
                currentY,
                mouseX,
                mouseY))
        {
            toggleSection(
                    Section.ACTOR_SETTINGS
            );

            return true;
        }

        return false;
    }

    private boolean isInsideSection(
            int y,
            int mouseX,
            int mouseY)
    {
        return mouseX >= this.x + 5 &&
                mouseX < this.x +
                        this.width -
                        5 &&
                mouseY >= y &&
                mouseY < y + 24;
    }

    /*
     * =========================================================
     * RECT
     * =========================================================
     */

    private void drawRect(
            int left,
            int top,
            int right,
            int bottom,
            int color)
    {
        GuiScreen.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }
}