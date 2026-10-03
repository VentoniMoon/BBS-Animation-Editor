package com.example.examplemod;

import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

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

    /*
     * =========================================================
     * BODY PART OVERRIDES
     * =========================================================
     *
     * Секция сохраняет старую высоту 108 px.
     *
     * Одновременно отображаются 5 строк.
     *
     * Остальные кости доступны через:
     *
     *     - колесо мыши;
     *     - вертикальный scrollbar;
     *     - перетаскивание scrollbar.
     */

    private static final int BODY_PART_ROW_HEIGHT =
            16;

    private static final int BODY_PART_VISIBLE_ROWS =
            5;

    private static final int BODY_PART_LIST_HEIGHT =
            BODY_PART_VISIBLE_ROWS *
                    BODY_PART_ROW_HEIGHT;

    /**
     * Ширина scrollbar.
     */
    private static final int BODY_PART_SCROLLBAR_WIDTH =
            7;

    /**
     * Отступ между списком и scrollbar.
     */
    private static final int BODY_PART_SCROLLBAR_GAP =
            3;

    /**
     * Смещение списка костей.
     *
     * 0 = кости 0..4
     * 1 = кости 1..5
     * 2 = кости 2..6
     * и т.д.
     */
    private int bodyPartBoneOffset;

    /**
     * Активно ли перетаскивание scrollbar.
     */
    private boolean bodyPartScrollDragging;

    /**
     * Смещение курсора относительно верхушки thumb
     * во время drag.
     */
    private int bodyPartScrollDragOffset;

    /*
     * =========================================================
     * THEME
     * =========================================================
     */

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
        if (this.runtimeActor != actor)
        {
            this.runtimeActor = actor;
            resetBodyPartScroll();
        }
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
        if (this.selectedActor != actor)
        {
            this.selectedKey = null;

            resetBodyPartScroll();
        }

        this.selectedActor = actor;

        if (actor == null)
        {
            this.selectedKey = null;

            resetBodyPartScroll();
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

    public void setSelectedKey(
            CharacterKey key)
    {
        if (key == null)
        {
            this.selectedKey = null;

            resetBodyPartScroll();

            return;
        }

        if (this.selectedActor == null)
        {
            this.selectedKey = null;

            resetBodyPartScroll();

            return;
        }

        CharacterTimelineController timeline =
                this.selectedActor.getCharacterTimeline();

        if (timeline == null)
        {
            this.selectedKey = null;

            resetBodyPartScroll();

            return;
        }

        boolean found = false;

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

            for (CharacterKey timelineKey :
                    track.getKeys())
            {
                if (timelineKey == key)
                {
                    found = true;
                    break;
                }
            }

            if (found)
            {
                break;
            }
        }

        if (found)
        {
            if (this.selectedKey != key)
            {
                resetBodyPartScroll();
            }

            this.selectedKey = key;
        }
        else
        {
            this.selectedKey = null;

            resetBodyPartScroll();
        }
    }

    public CharacterKey getSelectedKey()
    {
        return this.selectedKey;
    }

    public boolean hasSelectedKey()
    {
        return this.selectedKey != null;
    }

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

    public void validateSelectedKey()
    {
        if (this.selectedKey == null)
        {
            return;
        }

        if (!isSelectedKeyValid())
        {
            this.selectedKey = null;

            resetBodyPartScroll();
        }
    }

    public void clearSelectedKey()
    {
        this.selectedKey = null;

        resetBodyPartScroll();
    }

    /*
     * =========================================================
     * BODY PART SCROLL RESET
     * =========================================================
     */

    private void resetBodyPartScroll()
    {
        this.bodyPartBoneOffset = 0;

        this.bodyPartScrollDragging = false;

        this.bodyPartScrollDragOffset = 0;
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

        /*
         * Если Body Parts закрыли,
         * drag больше не должен оставаться активным.
         */
        if (section != Section.BODY_PART_OVERRIDES)
        {
            this.bodyPartScrollDragging = false;
        }
    }

    private void toggleSection(
            Section section)
    {
        if (this.openedSection == section)
        {
            return;
        }

        this.openedSection = section;

        if (section != Section.BODY_PART_OVERRIDES)
        {
            this.bodyPartScrollDragging = false;
        }
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

        validateSelectedKey();

        /*
         * Прокрутка Body Parts обрабатывается только
         * когда соответствующая секция открыта.
         */
        if (this.openedSection ==
                Section.BODY_PART_OVERRIDES)
        {
            handleBodyPartScroll(
                    mouseX,
                    mouseY
            );
        }
        else
        {
            this.bodyPartScrollDragging = false;
        }

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
                    cursorY,
                    mouseX,
                    mouseY
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

    private AbstractMorph getCurrentMorph()
    {
        if (this.runtimeActor == null ||
                this.runtimeActor.morph == null)
        {
            return null;
        }

        try
        {
            return this.runtimeActor.morph.get();
        }
        catch (Throwable error)
        {
            return null;
        }
    }

    private List<String> getBodyPartBones()
    {
        AbstractMorph morph =
                getCurrentMorph();

        if (morph == null)
        {
            return null;
        }

        return CharacterBodyPartOverrideController
                .getBones(
                        morph
                );
    }

    private boolean canEditBodyPartOverrides()
    {
        return this.selectedKey != null &&
                isSelectedKeyValid();
    }

    /**
     * Нарисовать BODY PART OVERRIDES.
     *
     * ВАЖНО:
     *
     * Здесь намеренно НЕТ подсказки
     * "Mouse wheel to scroll".
     *
     * Список занимает фиксированные 80 px
     * и имеет отдельный scrollbar.
     */
    private void drawBodyPartOverrides(
            Minecraft mc,
            int y,
            int mouseX,
            int mouseY)
    {
        if (!canEditBodyPartOverrides())
        {
            drawHint(
                    mc,
                    "Select Character key",
                    this.x + 12,
                    y + 4
            );

            drawHint(
                    mc,
                    "in Character Timeline",
                    this.x + 12,
                    y + 20
            );

            return;
        }

        List<String> bones =
                getBodyPartBones();

        if (bones == null ||
                bones.isEmpty())
        {
            drawHint(
                    mc,
                    "No bones found",
                    this.x + 12,
                    y + 4
            );

            return;
        }

        int maxOffset =
                getBodyPartMaxOffset(
                        bones
                );

        this.bodyPartBoneOffset =
                Math.max(
                        0,
                        Math.min(
                                maxOffset,
                                this.bodyPartBoneOffset
                        )
                );

        /*
         * Заголовок.
         */
        drawHint(
                mc,
                "Bones",
                this.x + 12,
                y
        );

        /*
         * Список начинается строго здесь.
         */
        int listY =
                y + 16;

        /*
         * Ограничиваем область списка.
         *
         * Это визуально отделяет список костей
         * от остальной панели.
         */
        drawRect(
                this.x + 8,
                listY,
                this.x +
                        this.width -
                        BODY_PART_SCROLLBAR_WIDTH -
                        BODY_PART_SCROLLBAR_GAP -
                        8,
                listY +
                        BODY_PART_LIST_HEIGHT,
                COLOR_PANEL_DARK
        );

        /*
         * Строки.
         */
        for (int i = 0;
             i < BODY_PART_VISIBLE_ROWS;
             i++)
        {
            int boneIndex =
                    this.bodyPartBoneOffset +
                            i;

            if (boneIndex >= bones.size())
            {
                break;
            }

            String bone =
                    bones.get(
                            boneIndex
                    );

            drawBodyPartRow(
                    mc,
                    bone,
                    listY +
                            i *
                                    BODY_PART_ROW_HEIGHT,
                    mouseX,
                    mouseY
            );
        }

        /*
         * Scrollbar.
         */
        drawBodyPartScrollbar(
                mc,
                y,
                bones,
                mouseX,
                mouseY
        );
    }

    private int getBodyPartMaxOffset(
            List<String> bones)
    {
        if (bones == null)
        {
            return 0;
        }

        return Math.max(
                0,
                bones.size() -
                        BODY_PART_VISIBLE_ROWS
        );
    }

    private void drawBodyPartRow(
            Minecraft mc,
            String bone,
            int y,
            int mouseX,
            int mouseY)
    {
        int rowX =
                this.x + 8;

        int rowWidth =
                this.width -
                        8 -
                        BODY_PART_SCROLLBAR_WIDTH -
                        BODY_PART_SCROLLBAR_GAP -
                        8;

        boolean hovered =
                isInsideRect(
                        rowX,
                        y,
                        rowWidth,
                        BODY_PART_ROW_HEIGHT,
                        mouseX,
                        mouseY
                );

        if (hovered)
        {
            drawRect(
                    rowX,
                    y,
                    rowX + rowWidth,
                    y + BODY_PART_ROW_HEIGHT,
                    COLOR_PANEL_HOVER
            );
        }

        AbstractMorph morph =
                getCurrentMorph();

        CharacterTimelineController timeline =
                this.selectedActor != null
                        ? this.selectedActor.getCharacterTimeline()
                        : null;

        boolean enabled =
                CharacterBodyPartOverrideController
                        .isEnabled(
                                morph,
                                this.selectedActor,
                                this.currentFrame,
                                bone
                        );

        int stateColor =
                enabled
                        ? getAccentColor()
                        : COLOR_TEXT_MUTED;

        String displayName =
                bone;

        if (displayName.length() > 20)
        {
            displayName =
                    displayName.substring(
                            0,
                            17
                    ) + "...";
        }

        mc.fontRenderer.drawString(
                displayName,
                this.x + 12,
                y + 4,
                hovered
                        ? COLOR_TEXT
                        : COLOR_TEXT_SECONDARY
        );

        String state =
                enabled
                        ? "ON"
                        : "OFF";

        mc.fontRenderer.drawString(
                state,
                this.x +
                        this.width -
                        BODY_PART_SCROLLBAR_WIDTH -
                        BODY_PART_SCROLLBAR_GAP -
                        30,
                y + 4,
                stateColor
        );
    }

    /*
     * =========================================================
     * BODY PART SCROLLBAR
     * =========================================================
     */

    private int getBodyPartScrollbarX()
    {
        return this.x +
                this.width -
                8 -
                BODY_PART_SCROLLBAR_WIDTH;
    }

    private int getBodyPartScrollbarY(
            int sectionY)
    {
        return sectionY + 16;
    }

    private void drawBodyPartScrollbar(
            Minecraft mc,
            int sectionY,
            List<String> bones,
            int mouseX,
            int mouseY)
    {
        if (bones == null ||
                bones.size() <= BODY_PART_VISIBLE_ROWS)
        {
            return;
        }

        int scrollbarX =
                getBodyPartScrollbarX();

        int scrollbarY =
                getBodyPartScrollbarY(
                        sectionY
                );

        int scrollbarHeight =
                BODY_PART_LIST_HEIGHT;

        drawRect(
                scrollbarX,
                scrollbarY,
                scrollbarX +
                        BODY_PART_SCROLLBAR_WIDTH,
                scrollbarY +
                        scrollbarHeight,
                COLOR_PANEL_DARK
        );

        float visibleRatio =
                (float)
                        BODY_PART_VISIBLE_ROWS /
                        (float)
                                bones.size();

        int thumbHeight =
                Math.max(
                        12,
                        (int)
                                (
                                        scrollbarHeight *
                                                visibleRatio
                                )
                );

        int maxOffset =
                getBodyPartMaxOffset(
                        bones
                );

        int maxThumbTravel =
                scrollbarHeight -
                        thumbHeight;

        int thumbY =
                scrollbarY;

        if (maxOffset > 0 &&
                maxThumbTravel > 0)
        {
            float scrollRatio =
                    (float)
                            this.bodyPartBoneOffset /
                            (float)
                                    maxOffset;

            thumbY =
                    scrollbarY +
                            (int)
                                    (
                                            maxThumbTravel *
                                                    scrollRatio
                                    );
        }

        boolean hovered =
                isInsideRect(
                        scrollbarX,
                        thumbY,
                        BODY_PART_SCROLLBAR_WIDTH,
                        thumbHeight,
                        mouseX,
                        mouseY
                );

        drawRect(
                scrollbarX,
                thumbY,
                scrollbarX +
                        BODY_PART_SCROLLBAR_WIDTH,
                thumbY +
                        thumbHeight,
                hovered ||
                        this.bodyPartScrollDragging
                        ? getAccentColor()
                        : COLOR_BORDER
        );

        if (hovered ||
                this.bodyPartScrollDragging)
        {
            drawRect(
                    scrollbarX + 1,
                    thumbY + 1,
                    scrollbarX +
                            BODY_PART_SCROLLBAR_WIDTH -
                            1,
                    thumbY +
                            thumbHeight -
                            1,
                    getAccentBrightColor()
            );
        }
    }

    /*
     * =========================================================
     * BODY PART SCROLL INPUT
     * =========================================================
     */

    private void handleBodyPartScroll(
            int mouseX,
            int mouseY)
    {
        if (!canEditBodyPartOverrides())
        {
            this.bodyPartScrollDragging = false;

            return;
        }

        List<String> bones =
                getBodyPartBones();

        if (bones == null ||
                bones.size() <= BODY_PART_VISIBLE_ROWS)
        {
            this.bodyPartScrollDragging = false;

            this.bodyPartBoneOffset = 0;

            return;
        }

        int sectionY =
                getBodyPartSectionContentY();

        int listY =
                sectionY + 16;

        int listWidth =
                this.width -
                        16 -
                        BODY_PART_SCROLLBAR_WIDTH -
                        BODY_PART_SCROLLBAR_GAP;

        /*
         * -----------------------------------------------------
         * WHEEL
         * -----------------------------------------------------
         *
         * ВАЖНО:
         *
         * колесо учитывается ТОЛЬКО внутри списка костей.
         *
         * Поэтому прокрутка панели/других элементов
         * не должна восприниматься как прокрутка
         * Body Parts.
         */

        boolean mouseInsideList =
                isInsideRect(
                        this.x + 8,
                        listY,
                        listWidth,
                        BODY_PART_LIST_HEIGHT,
                        mouseX,
                        mouseY
                );

        if (mouseInsideList)
        {
            int wheel =
                    Mouse.getDWheel();

            if (wheel != 0)
            {
                if (wheel > 0)
                {
                    this.bodyPartBoneOffset =
                            Math.max(
                                    0,
                                    this.bodyPartBoneOffset - 1
                            );
                }
                else
                {
                    this.bodyPartBoneOffset =
                            Math.min(
                                    getBodyPartMaxOffset(
                                            bones
                                    ),
                                    this.bodyPartBoneOffset + 1
                            );
                }
            }
        }

        /*
         * -----------------------------------------------------
         * SCROLLBAR
         * -----------------------------------------------------
         */

        int scrollbarX =
                getBodyPartScrollbarX();

        int scrollbarY =
                getBodyPartScrollbarY(
                        sectionY
                );

        int scrollbarHeight =
                BODY_PART_LIST_HEIGHT;

        float visibleRatio =
                (float)
                        BODY_PART_VISIBLE_ROWS /
                        (float)
                                bones.size();

        int thumbHeight =
                Math.max(
                        12,
                        (int)
                                (
                                        scrollbarHeight *
                                                visibleRatio
                                )
                );

        int maxOffset =
                getBodyPartMaxOffset(
                        bones
                );

        int maxThumbTravel =
                scrollbarHeight -
                        thumbHeight;

        int thumbY =
                scrollbarY;

        if (maxOffset > 0 &&
                maxThumbTravel > 0)
        {
            float scrollRatio =
                    (float)
                            this.bodyPartBoneOffset /
                            (float)
                                    maxOffset;

            thumbY =
                    scrollbarY +
                            (int)
                                    (
                                            maxThumbTravel *
                                                    scrollRatio
                                    );
        }

        /*
         * Отпустили левую кнопку.
         */
        if (!Mouse.isButtonDown(0))
        {
            this.bodyPartScrollDragging = false;

            return;
        }

        /*
         * Начинаем drag только если курсор
         * действительно находится на thumb.
         */
        if (!this.bodyPartScrollDragging)
        {
            if (isInsideRect(
                    scrollbarX,
                    thumbY,
                    BODY_PART_SCROLLBAR_WIDTH,
                    thumbHeight,
                    mouseX,
                    mouseY
            ))
            {
                this.bodyPartScrollDragging = true;

                this.bodyPartScrollDragOffset =
                        mouseY -
                                thumbY;
            }

            return;
        }

        /*
         * -----------------------------------------------------
         * DRAG
         * -----------------------------------------------------
         */

        int desiredThumbY =
                mouseY -
                        this.bodyPartScrollDragOffset;

        int minThumbY =
                scrollbarY;

        int maxThumbY =
                scrollbarY +
                        maxThumbTravel;

        desiredThumbY =
                Math.max(
                        minThumbY,
                        Math.min(
                                maxThumbY,
                                desiredThumbY
                        )
                );

        if (maxThumbTravel > 0)
        {
            float ratio =
                    (float)
                            (
                                    desiredThumbY -
                                            minThumbY
                            ) /
                            (float)
                                    maxThumbTravel;

            int newOffset =
                    Math.round(
                            ratio *
                                    maxOffset
                    );

            this.bodyPartBoneOffset =
                    Math.max(
                            0,
                            Math.min(
                                    maxOffset,
                                    newOffset
                            )
                    );
        }
    }

    /**
     * Получить Y начала содержимого
     * BODY PART OVERRIDES.
     */
    private int getBodyPartSectionContentY()
    {
        int currentY =
                this.y + 30;

        /*
         * Appearance.
         */
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
         * Animation.
         */
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
         * Body Part header + content gap.
         */
        currentY +=
                24 + 4;

        return currentY;
    }

    /*
     * =========================================================
     * BODY PART INPUT
     * =========================================================
     */

    private boolean mouseClickedBodyPartOverrides(
            int sectionY,
            int mouseX,
            int mouseY)
    {
        if (!canEditBodyPartOverrides())
        {
            return false;
        }

        List<String> bones =
                getBodyPartBones();

        if (bones == null ||
                bones.isEmpty())
        {
            return false;
        }

        int maxOffset =
                getBodyPartMaxOffset(
                        bones
                );

        this.bodyPartBoneOffset =
                Math.max(
                        0,
                        Math.min(
                                maxOffset,
                                this.bodyPartBoneOffset
                        )
                );

        int listY =
                sectionY + 16;

        /*
         * -----------------------------------------------------
         * SCROLLBAR
         * -----------------------------------------------------
         *
         * Не даём клику по scrollbar
         * восприниматься как клик по кости.
         */

        int scrollbarX =
                getBodyPartScrollbarX();

        if (isInsideRect(
                scrollbarX,
                listY,
                BODY_PART_SCROLLBAR_WIDTH,
                BODY_PART_LIST_HEIGHT,
                mouseX,
                mouseY
        ))
        {
            return true;
        }

        /*
         * -----------------------------------------------------
         * BONES
         * -----------------------------------------------------
         */

        int listWidth =
                this.width -
                        16 -
                        BODY_PART_SCROLLBAR_WIDTH -
                        BODY_PART_SCROLLBAR_GAP;

        for (int i = 0;
             i < BODY_PART_VISIBLE_ROWS;
             i++)
        {
            int boneIndex =
                    this.bodyPartBoneOffset +
                            i;

            if (boneIndex >= bones.size())
            {
                break;
            }

            int rowY =
                    listY +
                            i *
                                    BODY_PART_ROW_HEIGHT;

            if (!isInsideRect(
                    this.x + 8,
                    rowY,
                    listWidth,
                    BODY_PART_ROW_HEIGHT,
                    mouseX,
                    mouseY
            ))
            {
                continue;
            }

            String bone =
                    bones.get(
                            boneIndex
                    );

            AbstractMorph morph =
                    getCurrentMorph();

            if (morph == null)
            {
                return true;
            }

            CharacterBodyPartOverrideController
                    .toggleBone(
                            this.selectedKey,
                            morph,
                            this.selectedActor,
                            this.currentFrame,
                            bone
                    );

            return true;
        }

        return false;
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
            int bodyPartY =
                    currentY + 24 + 4;

            if (mouseClickedBodyPartOverrides(
                    bodyPartY,
                    mouseX,
                    mouseY))
            {
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

        this.guiBridge.openSkinEditor(
                this.selectedActor,
                this.runtimeActor,
                this.selectedKey,
                this.currentFrame
        );
    }

    /*
     * =========================================================
     * RECT / HIT TEST
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

    private boolean isInsideRect(
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY)
    {
        return mouseX >= x &&
                mouseX < x + width &&
                mouseY >= y &&
                mouseY < y + height;
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