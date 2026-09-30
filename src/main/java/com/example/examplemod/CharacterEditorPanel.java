package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Панель Character Mode.
 *
 * Character Mode работает с состоянием Actor
 * и конкретным CharacterKey, выбранным в Character Timeline.
 *
 * ВАЖНО:
 *
 * currentFrame и selectedKey — разные понятия.
 *
 * currentFrame:
 *     положение playhead.
 *
 * selectedKey:
 *     конкретный CharacterKey, который сейчас редактируется.
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

    private static int getAccentColor()
    {
        return EditorThemeManager
                .get()
                .getAccent();
    }

    private static int getAccentBrightColor()
    {
        return EditorThemeManager
                .get()
                .getAccentBright();
    }

    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    private final CharacterEditorController characterController;

    /**
     * Actor, отображаемый Character Mode.
     */
    private BlockbusterSceneActorData selectedActor;

    /**
     * Конкретный CharacterKey, выбранный
     * в Character Timeline.
     *
     * НЕ связан напрямую с currentFrame.
     *
     * selectedKey остаётся выбранным, даже если
     * playhead переместился на другой кадр.
     */
    private CharacterKey selectedKey;

    /**
     * Текущая позиция playhead.
     */
    private int currentFrame;

    /*
     * =========================================================
     * ORIGINAL BLOCKBUSTER / METAMORPH GUI BRIDGE
     * =========================================================
     */

    private BlockbusterCharacterGuiBridge guiBridge;

    /**
     * Runtime EntityActor, который уже используется
     * BlockbusterActorPreviewRenderer.
     *
     * Новый Actor здесь НЕ создаётся.
     */
    private EntityActor runtimeActor;

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
     * GUI BRIDGE
     * =========================================================
     */

    public void setGuiBridge(
            BlockbusterCharacterGuiBridge bridge)
    {
        this.guiBridge = bridge;
    }

    public BlockbusterCharacterGuiBridge getGuiBridge()
    {
        return this.guiBridge;
    }

    public void setRuntimeActor(
            EntityActor actor)
    {
        this.runtimeActor = actor;
    }

    public EntityActor getRuntimeActor()
    {
        return this.runtimeActor;
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
     * ACTOR
     * =========================================================
     */

    public void setSelectedActor(
            BlockbusterSceneActorData actor)
    {
        /*
         * Если Actor действительно сменился,
         * CharacterKey старого Actor больше
         * нельзя использовать.
         */
        if (this.selectedActor != actor)
        {
            this.selectedKey = null;
        }

        this.selectedActor = actor;

        /*
         * Если Actor отсутствует,
         * ключ также обязательно сбрасываем.
         */
        if (actor == null)
        {
            this.selectedKey = null;
        }
    }

    public BlockbusterSceneActorData getSelectedActor()
    {
        return this.selectedActor;
    }

    /*
     * =========================================================
     * CURRENT FRAME
     * =========================================================
     */

    public void setCurrentFrame(
            int frame)
    {
        this.currentFrame =
                Math.max(
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
     * SELECTED CHARACTER KEY
     * =========================================================
     */

    /**
     * Устанавливает конкретный CharacterKey,
     * выбранный в Character Timeline.
     *
     * ВАЖНО:
     *
     * Здесь НЕ меняется currentFrame.
     *
     * Например:
     *
     * selectedKey = MORPH @ 40
     * currentFrame = 80
     *
     * Это допустимое состояние.
     */
    public void setSelectedKey(
            CharacterKey key)
    {
        this.selectedKey = key;
    }

    /**
     * Получить конкретный выбранный CharacterKey.
     */
    public CharacterKey getSelectedKey()
    {
        return this.selectedKey;
    }

    /**
     * Проверяет, существует ли выбранный ключ.
     */
    public boolean hasSelectedKey()
    {
        return this.selectedKey != null;
    }

    /**
     * Проверяет, относится ли выбранный ключ
     * к текущему Actor.
     *
     * CharacterKey сам по себе не хранит ссылку
     * на Actor, поэтому проверяем его наличие
     * внутри Character Timeline текущего Actor.
     */
    public boolean isSelectedKeyValid()
    {
        if (this.selectedActor == null ||
                this.selectedKey == null)
        {
            return false;
        }

        CharacterTimelineController timeline =
                this.selectedActor.getCharacterTimeline();

        if (timeline == null)
        {
            return false;
        }

        for (int i = 0;
             i < timeline.getTrackCount();
             i++)
        {
            CharacterTrack track =
                    timeline.getTrack(i);

            if (track == null)
            {
                continue;
            }

            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == this.selectedKey)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Если выбранный ключ больше не существует
     * в Timeline текущего Actor, он сбрасывается.
     *
     * Это защищает Character Mode от ситуации,
     * когда ключ был удалён или Actor был перезагружен.
     */
    public void validateSelectedKey()
    {
        if (this.selectedKey == null)
        {
            return;
        }

        if (!isSelectedKeyValid())
        {
            this.selectedKey = null;
        }
    }

    /**
     * Полностью снять выделение ключа.
     */
    public void clearSelectedKey()
    {
        this.selectedKey = null;
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
         * Ключ мог быть удалён из Timeline.
         *
         * Проверяем это перед отображением панели.
         */
        validateSelectedKey();

        drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                COLOR_PANEL
        );

        drawRect(
                this.x,
                this.y,
                this.x + 1,
                this.y + this.height,
                COLOR_BORDER
        );

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
                    cursorY,
                    mouseX,
                    mouseY
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
                getAccentColor()
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
                    getAccentColor()
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
                        ? getAccentBrightColor()
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
            int y,
            int mouseX,
            int mouseY)
    {
        String actorName = "—";
        String actorId = "—";
        String morph = "—";
        String record = "Not loaded";
        String frames = "0";

        if (this.selectedActor != null)
        {
            actorName =
                    this.selectedActor.getName();

            if (actorName == null ||
                    actorName.length() == 0)
            {
                actorName = "Unnamed";
            }

            actorId =
                    this.selectedActor.getId();

            if (actorId == null ||
                    actorId.length() == 0)
            {
                actorId = "—";
            }

            morph =
                    this.selectedActor.getMorphName();

            if (morph == null ||
                    morph.length() == 0)
            {
                morph = "Default";
            }

            /*
             * Если выбран конкретный Appearance key,
             * показываем его тип.
             *
             * При этом сам selectedKey остаётся
             * объектом, с которым будут работать
             * кнопки Morph / Skin.
             */
            if (this.selectedKey != null)
            {
                if (this.selectedKey.getType() ==
                        CharacterKey.Type.MORPH)
                {
                    morph = "MORPH KEY";
                }
                else if (this.selectedKey.getType() ==
                        CharacterKey.Type.SKIN)
                {
                    morph = "SKIN KEY";
                }
            }

            if (this.selectedActor.hasRecord())
            {
                record = "Loaded";

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

        boolean morphHovered =
                isInsideMorphButton(
                        y,
                        mouseX,
                        mouseY
                );

        drawButton(
                mc,
                morph,
                this.x + 68,
                y + 32,
                getMorphButtonWidth(),
                17,
                morphHovered
        );

        /*
         * Skin
         */

        boolean skinHovered =
                isInsideSkinButton(
                        y,
                        mouseX,
                        mouseY
                );

        drawButton(
                mc,
                "Skin",
                this.x + this.width - 47,
                y + 32,
                37,
                17,
                skinHovered
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
                        ? getAccentColor()
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

        /*
         * Selected key
         *
         * Диагностическая строка.
         *
         * ВАЖНО:
         * здесь показывается именно selectedKey,
         * а не ключ на currentFrame.
         */
        if (this.selectedKey != null)
        {
            String keyText =
                    this.selectedKey.getType().name()
                            + " @ "
                            + this.selectedKey.getFrame();

            drawColoredValue(
                    mc,
                    keyText,
                    this.x + 12,
                    y + 105,
                    getAccentBrightColor()
            );
        }
    }

    /*
     * =========================================================
     * BUTTON GEOMETRY
     * =========================================================
     */

    private int getMorphButtonWidth()
    {
        return Math.max(
                20,
                this.width - 122
        );
    }

    private void drawButton(
            Minecraft mc,
            String text,
            int x,
            int y,
            int width,
            int height,
            boolean hovered)
    {
        drawRect(
                x,
                y,
                x + width,
                y + height,
                hovered
                        ? COLOR_PANEL_HOVER
                        : COLOR_PANEL_DARK
        );

        drawRect(
                x,
                y,
                x + width,
                y + 1,
                hovered
                        ? getAccentColor()
                        : COLOR_BORDER
        );

        drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                COLOR_BORDER
        );

        mc.fontRenderer.drawString(
                text,
                x + 5,
                y + 5,
                hovered
                        ? getAccentBrightColor()
                        : COLOR_TEXT_SECONDARY
        );

        if (width >= 70)
        {
            mc.fontRenderer.drawString(
                    "…",
                    x + width - 10,
                    y + 4,
                    COLOR_TEXT_MUTED
            );
        }
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
        drawPartRow(mc, "Head", true, y);
        drawPartRow(mc, "Body", true, y + 18);
        drawPartRow(mc, "Left Arm", true, y + 36);
        drawPartRow(mc, "Right Arm", true, y + 54);
        drawPartRow(mc, "Left Leg", true, y + 72);
        drawPartRow(mc, "Right Leg", true, y + 90);
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
                        ? getAccentColor()
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

        /*
         * Перед обработкой кнопок убеждаемся,
         * что selectedKey всё ещё принадлежит
         * текущему Actor.
         */
        validateSelectedKey();

        int currentY =
                this.y + 30;

        /*
         * Appearance section.
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

        /*
         * Appearance contents.
         */

        if (this.openedSection ==
                Section.APPEARANCE)
        {
            int appearanceY =
                    currentY + 24 + 4;

            /*
             * Morph.
             */
            if (isInsideMorphButton(
                    appearanceY,
                    mouseX,
                    mouseY))
            {
                openMorphEditor();

                return true;
            }

            /*
             * Skin.
             */
            if (isInsideSkinButton(
                    appearanceY,
                    mouseX,
                    mouseY))
            {
                openSkinEditor();

                return true;
            }

            currentY +=
                    24 + 4 + 108;
        }
        else
        {
            currentY += 24;
        }

        currentY += 4;

        /*
         * Animation Setup.
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
         * Body Part Overrides.
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
         * Actor Settings.
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

    /*
     * =========================================================
     * MORPH / SKIN BUTTONS
     * =========================================================
     */

    private boolean isInsideMorphButton(
            int appearanceY,
            int mouseX,
            int mouseY)
    {
        int buttonX =
                this.x + 68;

        int buttonY =
                appearanceY + 32;

        int buttonWidth =
                getMorphButtonWidth();

        return mouseX >= buttonX &&
                mouseX < buttonX + buttonWidth &&
                mouseY >= buttonY &&
                mouseY < buttonY + 17;
    }

    private boolean isInsideSkinButton(
            int appearanceY,
            int mouseX,
            int mouseY)
    {
        int buttonX =
                this.x +
                        this.width -
                        47;

        int buttonY =
                appearanceY + 32;

        return mouseX >= buttonX &&
                mouseX < this.x +
                        this.width -
                        10 &&
                mouseY >= buttonY &&
                mouseY < buttonY + 17;
    }

    /*
     * =========================================================
     * OPEN MORPH EDITOR
     * =========================================================
     */

    private void openMorphEditor()
    {
        if (this.guiBridge == null)
        {
            return;
        }

        if (this.selectedActor == null)
        {
            return;
        }

        if (this.runtimeActor == null)
        {
            return;
        }

        if (!this.guiBridge.canOpen(
                this.selectedActor,
                this.runtimeActor))
        {
            return;
        }

        /*
         * Передаём:
         *
         * 1. Actor
         * 2. Runtime Actor
         * 3. конкретный selectedKey
         * 4. currentFrame
         *
         * Bridge сам решит:
         *
         * - использовать существующий selectedKey;
         * - либо создать новый ключ на currentFrame.
         */
        this.guiBridge.openMorphEditor(
                this.selectedActor,
                this.runtimeActor,
                this.selectedKey,
                this.currentFrame
        );
    }

    /*
     * =========================================================
     * OPEN SKIN EDITOR
     * =========================================================
     */

    private void openSkinEditor()
    {
        if (this.guiBridge == null)
        {
            return;
        }

        if (this.selectedActor == null)
        {
            return;
        }

        if (this.runtimeActor == null)
        {
            return;
        }

        if (!this.guiBridge.canOpen(
                this.selectedActor,
                this.runtimeActor))
        {
            return;
        }

        /*
         * Здесь используется тот же принцип:
         *
         * selectedKey — конкретный ключ,
         * currentFrame — только fallback для
         * создания нового ключа.
         */
        this.guiBridge.openSkinEditor(
                this.selectedActor,
                this.runtimeActor,
                this.selectedKey,
                this.currentFrame
        );
    }

    /*
     * =========================================================
     * SECTION HIT TEST
     * =========================================================
     */

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