package com.example.examplemod;

import mchorse.mclib.utils.keyframes.Keyframe;
import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

public class InterpolationPanel
{
    /*
     * =========================================================
     * COLORS
     * =========================================================
     */

    private static final int PANEL_BACKGROUND =
            0xFF252629;

    private static final int PANEL_BORDER =
            0xFF111214;

    private static final int HEADER_BACKGROUND =
            0xFF303134;

    private static final int HEADER_BOTTOM =
            0xFF18191B;

    private static final int HEADER_ACCENT =
            0xFF66CCFF;

    private static final int FIELD_BACKGROUND =
            0xFF303134;

    private static final int FIELD_HOVER =
            0xFF3A3B3E;

    private static final int FIELD_ACTIVE =
            0xFF45474A;

    private static final int FIELD_BORDER =
            0xFF18191B;

    private static final int FIELD_ACTIVE_BORDER =
            0xFF66CCFF;

    private static final int TEXT =
            0xFFE0E0E0;

    private static final int TEXT_BRIGHT =
            0xFFF5F5F5;

    private static final int TEXT_SECONDARY =
            0xFFAAAAAA;

    private static final int TEXT_MUTED =
            0xFF777777;

    private static final int TEXT_DARK =
            0xFF55585C;

    private static final int ACCENT =
            0xFF66CCFF;

    private static final int ACCENT_BRIGHT =
            0xFF8BE1FF;

    private static final int GRAPH_BACKGROUND =
            0xFF18191B;

    private static final int GRAPH_GRID =
            0xFF2A2C2F;

    private static final int GRAPH_AXIS =
            0xFF484B4F;

    private static final int GRAPH_BORDER =
            0xFF36383B;

    private static final int GRAPH_CURVE =
            0xFF66CCFF;

    private static final int GRAPH_POINT =
            0xFFFFFFFF;

    private static final int DROPDOWN_BACKGROUND =
            0xFF202124;

    private static final int DROPDOWN_SELECTED =
            0xFF34373A;

    private static final int DROPDOWN_HOVER =
            0xFF3A3D40;

    private static final int DROPDOWN_BORDER =
            0xFF111214;

    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    private final Minecraft mc;

    private int x;
    private int y;
    private int width;
    private int height;

    private boolean dropdownOpen;

    private int dropdownScroll;

    private KeyframeInterpolation selectedInterpolation;
    private KeyframeEasing selectedEasing;

    private Runnable interpolationChanged;

    private KeyframeInterpolation hoveredInterpolation;

    private long animationStartTime;

    private final List<KeyframeInterpolation> interpolations;

    public InterpolationPanel()
    {
        this.mc =
                Minecraft.getMinecraft();

        this.selectedInterpolation =
                KeyframeInterpolation.LINEAR;

        this.selectedEasing =
                KeyframeEasing.IN;

        this.hoveredInterpolation =
                null;

        this.dropdownOpen =
                false;

        this.dropdownScroll =
                0;

        this.animationStartTime =
                System.currentTimeMillis();

        this.interpolations =
                new ArrayList<KeyframeInterpolation>();

        for (
                KeyframeInterpolation interpolation :
                KeyframeInterpolation.values()
        )
        {
            this.interpolations.add(
                    interpolation
            );
        }
    }

    /*
     * =========================================================
     * BOUNDS
     * =========================================================
     */

    public void setBounds(
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

    /*
     * =========================================================
     * INTERPOLATION
     * =========================================================
     */

    public void setSelectedInterpolation(
            KeyframeInterpolation interpolation)
    {
        if (interpolation == null)
        {
            interpolation =
                    KeyframeInterpolation.LINEAR;
        }

        this.selectedInterpolation =
                interpolation;
    }

    public KeyframeInterpolation
    getSelectedInterpolation()
    {
        return this.selectedInterpolation;
    }

    /*
     * =========================================================
     * EASING
     * =========================================================
     */

    public void setSelectedEasing(
            KeyframeEasing easing)
    {
        if (easing == null)
        {
            easing =
                    KeyframeEasing.IN;
        }

        this.selectedEasing =
                easing;
    }

    public KeyframeEasing
    getSelectedEasing()
    {
        return this.selectedEasing;
    }

    /*
     * =========================================================
     * CALLBACK
     * =========================================================
     */

    public void setInterpolationChanged(
            Runnable interpolationChanged)
    {
        this.interpolationChanged =
                interpolationChanged;
    }

    public boolean isDropdownOpen()
    {
        return this.dropdownOpen;
    }

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            int mouseX,
            int mouseY)
    {
        if (
                this.width <= 0 ||
                        this.height <= 0
        )
        {
            return;
        }

        /*
         * =====================================================
         * PANEL FRAME
         * =====================================================
         */

        Gui.drawRect(
                this.x - 1,
                this.y - 1,
                this.x + this.width + 1,
                this.y + this.height + 1,
                PANEL_BORDER
        );

        Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                PANEL_BACKGROUND
        );

        /*
         * =====================================================
         * HEADER
         * =====================================================
         */

        final int headerHeight = 25;

        Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + headerHeight,
                HEADER_BACKGROUND
        );

        /*
         * Cyan marker.
         */

        Gui.drawRect(
                this.x + 8,
                this.y + 7,
                this.x + 10,
                this.y + 18,
                HEADER_ACCENT
        );

        /*
         * Header title.
         */

        this.mc.fontRenderer.drawString(
                "INTERPOLATION",
                this.x + 16,
                this.y + 8,
                TEXT
        );

        /*
         * Header bottom line.
         */

        Gui.drawRect(
                this.x,
                this.y + headerHeight - 1,
                this.x + this.width,
                this.y + headerHeight,
                HEADER_BOTTOM
        );

        /*
         * =====================================================
         * SELECTOR
         * =====================================================
         */

        int selectorX =
                this.x + 7;

        int selectorY =
                this.y + 30;

        int selectorWidth =
                this.width - 14;

        int selectorHeight =
                20;

        boolean selectorHovered =
                isInside(
                        mouseX,
                        mouseY,
                        selectorX,
                        selectorY,
                        selectorWidth,
                        selectorHeight
                );

        int selectorBackground;

        if (this.dropdownOpen)
        {
            selectorBackground =
                    FIELD_ACTIVE;
        }
        else if (selectorHovered)
        {
            selectorBackground =
                    FIELD_HOVER;
        }
        else
        {
            selectorBackground =
                    FIELD_BACKGROUND;
        }

        /*
         * Outer border.
         */

        Gui.drawRect(
                selectorX,
                selectorY,
                selectorX + selectorWidth,
                selectorY + selectorHeight,
                FIELD_BORDER
        );

        /*
         * Inner field.
         */

        Gui.drawRect(
                selectorX + 1,
                selectorY + 1,
                selectorX + selectorWidth - 1,
                selectorY + selectorHeight - 1,
                selectorBackground
        );

        /*
         * Active/hover accent.
         */

        if (
                this.dropdownOpen ||
                        selectorHovered
        )
        {
            Gui.drawRect(
                    selectorX + 1,
                    selectorY + selectorHeight - 2,
                    selectorX + selectorWidth - 1,
                    selectorY + selectorHeight - 1,
                    FIELD_ACTIVE_BORDER
            );
        }

        /*
         * Selected interpolation name.
         */

        String selectedName =
                getInterpolationName(
                        this.selectedInterpolation
                );

        this.mc.fontRenderer.drawString(
                selectedName,
                selectorX + 7,
                selectorY + 6,
                this.dropdownOpen
                        ? TEXT_BRIGHT
                        : TEXT
        );

        /*
         * Dropdown arrow.
         */

        String arrow =
                this.dropdownOpen
                        ? "▲"
                        : "▼";

        int arrowWidth =
                this.mc.fontRenderer.getStringWidth(
                        arrow
                );

        this.mc.fontRenderer.drawString(
                arrow,
                selectorX +
                        selectorWidth -
                        arrowWidth -
                        7,
                selectorY + 6,
                this.dropdownOpen
                        ? ACCENT_BRIGHT
                        : TEXT_SECONDARY
        );

        /*
         * =====================================================
         * EASING
         * =====================================================
         */

        /*
         * Small section marker.
         */

        Gui.drawRect(
                this.x + 8,
                this.y + 57,
                this.x + 10,
                this.y + 67,
                0xFF55585C
        );

        this.mc.fontRenderer.drawString(
                "Easing",
                this.x + 15,
                this.y + 58,
                TEXT_MUTED
        );

        String easingName =
                getEasingName(
                        this.selectedEasing
                );

        int easingWidth =
                this.mc.fontRenderer.getStringWidth(
                        easingName
                );

        this.mc.fontRenderer.drawString(
                easingName,
                this.x +
                        this.width -
                        easingWidth -
                        8,
                this.y + 58,
                TEXT_SECONDARY
        );

        /*
         * =====================================================
         * GRAPH
         * =====================================================
         */

        int graphX =
                this.x + 8;

        int graphY =
                this.y + 72;

        int graphWidth =
                this.width - 16;

        int graphHeight =
                Math.max(
                        18,
                        this.height - 80
                );

        if (!this.dropdownOpen)
        {
            drawGraph(
                    this.selectedInterpolation,
                    this.selectedEasing,
                    graphX,
                    graphY,
                    graphWidth,
                    graphHeight
            );
        }

        /*
         * =====================================================
         * DROPDOWN
         * =====================================================
         */

        if (this.dropdownOpen)
        {
            drawDropdown(
                    mouseX,
                    mouseY
            );
        }
    }

    /*
     * =========================================================
     * DROPDOWN
     * =========================================================
     */

    private void drawDropdown(
            int mouseX,
            int mouseY)
    {
        final int headerHeight = 25;
        final int itemHeight = 18;

        int dropdownX =
                this.x;

        int dropdownY =
                this.y +
                        headerHeight +
                        25;

        int dropdownWidth =
                this.width;

        int visibleItems =
                Math.max(
                        1,
                        this.height / itemHeight
                );

        int maxScroll =
                Math.max(
                        0,
                        this.interpolations.size()
                                - visibleItems
                );

        this.dropdownScroll =
                clamp(
                        this.dropdownScroll,
                        0,
                        maxScroll
                );

        int dropdownHeight =
                visibleItems *
                        itemHeight;

        /*
         * =====================================================
         * SHADOW
         * =====================================================
         */

        Gui.drawRect(
                dropdownX - 3,
                dropdownY - 3,
                dropdownX +
                        dropdownWidth +
                        3,
                dropdownY +
                        dropdownHeight +
                        3,
                0xDD080909
        );

        /*
         * =====================================================
         * FRAME
         * =====================================================
         */

        Gui.drawRect(
                dropdownX - 1,
                dropdownY - 1,
                dropdownX +
                        dropdownWidth +
                        1,
                dropdownY +
                        dropdownHeight +
                        1,
                DROPDOWN_BORDER
        );

        Gui.drawRect(
                dropdownX,
                dropdownY,
                dropdownX + dropdownWidth,
                dropdownY + dropdownHeight,
                DROPDOWN_BACKGROUND
        );

        /*
         * Top accent.
         */

        Gui.drawRect(
                dropdownX,
                dropdownY,
                dropdownX + dropdownWidth,
                dropdownY + 1,
                ACCENT
        );

        this.hoveredInterpolation =
                null;

        /*
         * =====================================================
         * ITEMS
         * =====================================================
         */

        for (
                int visibleIndex = 0;
                visibleIndex < visibleItems;
                visibleIndex++
        )
        {
            int index =
                    this.dropdownScroll +
                            visibleIndex;

            if (
                    index >=
                            this.interpolations.size()
            )
            {
                break;
            }

            KeyframeInterpolation interpolation =
                    this.interpolations.get(
                            index
                    );

            int itemY =
                    dropdownY +
                            visibleIndex *
                                    itemHeight;

            boolean hovered =
                    mouseX >= dropdownX
                            &&
                            mouseX <
                                    dropdownX +
                                            dropdownWidth
                            &&
                            mouseY >= itemY
                            &&
                            mouseY <
                                    itemY +
                                            itemHeight;

            boolean selected =
                    interpolation ==
                            this.selectedInterpolation;

            /*
             * Row background.
             */

            if (selected)
            {
                Gui.drawRect(
                        dropdownX + 1,
                        itemY,
                        dropdownX +
                                dropdownWidth - 1,
                        itemY +
                                itemHeight,
                        hovered
                                ? DROPDOWN_HOVER
                                : DROPDOWN_SELECTED
                );
            }
            else if (hovered)
            {
                Gui.drawRect(
                        dropdownX + 1,
                        itemY,
                        dropdownX +
                                dropdownWidth - 1,
                        itemY +
                                itemHeight,
                        DROPDOWN_HOVER
                );
            }

            if (hovered)
            {
                this.hoveredInterpolation =
                        interpolation;
            }

            /*
             * Selected marker.
             */

            if (selected)
            {
                Gui.drawRect(
                        dropdownX + 1,
                        itemY,
                        dropdownX + 4,
                        itemY + itemHeight,
                        ACCENT
                );
            }

            /*
             * Hover marker.
             */

            if (
                    hovered &&
                            !selected
            )
            {
                Gui.drawRect(
                        dropdownX + 1,
                        itemY,
                        dropdownX + 3,
                        itemY + itemHeight,
                        0xFF55585C
                );
            }

            String name =
                    getInterpolationName(
                            interpolation
                    );

            this.mc.fontRenderer.drawString(
                    name,
                    dropdownX + 10,
                    itemY + 5,
                    selected
                            ? TEXT_BRIGHT
                            : hovered
                            ? TEXT
                            : TEXT_SECONDARY
            );
        }

        /*
         * =====================================================
         * SCROLL INDICATORS
         * =====================================================
         */

        if (this.dropdownScroll > 0)
        {
            Gui.drawRect(
                    dropdownX,
                    dropdownY,
                    dropdownX +
                            dropdownWidth,
                    dropdownY + 1,
                    ACCENT
            );

            this.mc.fontRenderer.drawString(
                    "▲",
                    dropdownX +
                            dropdownWidth -
                            12,
                    dropdownY + 2,
                    ACCENT_BRIGHT
            );
        }

        if (this.dropdownScroll < maxScroll)
        {
            Gui.drawRect(
                    dropdownX,
                    dropdownY +
                            dropdownHeight -
                            1,
                    dropdownX +
                            dropdownWidth,
                    dropdownY +
                            dropdownHeight,
                    ACCENT
            );

            this.mc.fontRenderer.drawString(
                    "▼",
                    dropdownX +
                            dropdownWidth -
                            12,
                    dropdownY +
                            dropdownHeight -
                            10,
                    ACCENT_BRIGHT
            );
        }

        /*
         * =====================================================
         * FLOATING GRAPH
         * =====================================================
         */

        if (
                this.hoveredInterpolation != null
        )
        {
            int graphWidth =
                    125;

            int graphHeight =
                    82;

            int graphX =
                    dropdownX +
                            dropdownWidth +
                            7;

            int graphY =
                    dropdownY;

            /*
             * Prevent the graph from going too far
             * outside the screen.
             */

            if (
                    graphX +
                            graphWidth >
                            this.mc.currentScreen.width
            )
            {
                graphX =
                        dropdownX -
                                graphWidth -
                                7;
            }

            drawFloatingGraph(
                    this.hoveredInterpolation,
                    this.selectedEasing,
                    graphX,
                    graphY,
                    graphWidth,
                    graphHeight
            );
        }
    }

    /*
     * =========================================================
     * FLOATING GRAPH
     * =========================================================
     */

    private void drawFloatingGraph(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight)
    {
        /*
         * Shadow.
         */

        Gui.drawRect(
                graphX - 3,
                graphY - 3,
                graphX +
                        graphWidth +
                        3,
                graphY +
                        graphHeight +
                        3,
                0xDD080909
        );

        /*
         * Frame.
         */

        Gui.drawRect(
                graphX - 1,
                graphY - 1,
                graphX +
                        graphWidth +
                        1,
                graphY +
                        graphHeight +
                        1,
                DROPDOWN_BORDER
        );

        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BACKGROUND
        );

        /*
         * Top accent.
         */

        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + 1,
                ACCENT
        );

        /*
         * Bottom and side borders.
         */

        Gui.drawRect(
                graphX,
                graphY +
                        graphHeight -
                        1,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        Gui.drawRect(
                graphX,
                graphY,
                graphX + 1,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        Gui.drawRect(
                graphX +
                        graphWidth -
                        1,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        drawGraphGrid(
                graphX,
                graphY,
                graphWidth,
                graphHeight
        );

        drawCurve(
                interpolation,
                easing,
                graphX,
                graphY,
                graphWidth,
                graphHeight,
                GRAPH_CURVE
        );

        drawAnimatedPoint(
                interpolation,
                easing,
                graphX,
                graphY,
                graphWidth,
                graphHeight
        );
    }

    /*
     * =========================================================
     * GRAPH
     * =========================================================
     */

    private void drawGraph(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight)
    {
        /*
         * Background.
         */

        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BACKGROUND
        );

        /*
         * Border.
         */

        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + 1,
                GRAPH_BORDER
        );

        Gui.drawRect(
                graphX,
                graphY +
                        graphHeight -
                        1,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        Gui.drawRect(
                graphX,
                graphY,
                graphX + 1,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        Gui.drawRect(
                graphX +
                        graphWidth -
                        1,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                GRAPH_BORDER
        );

        drawGraphGrid(
                graphX,
                graphY,
                graphWidth,
                graphHeight
        );

        drawCurve(
                interpolation,
                easing,
                graphX,
                graphY,
                graphWidth,
                graphHeight,
                GRAPH_CURVE
        );

        drawAnimatedPoint(
                interpolation,
                easing,
                graphX,
                graphY,
                graphWidth,
                graphHeight
        );
    }

    /*
     * =========================================================
     * GRAPH GRID
     * =========================================================
     */

    private void drawGraphGrid(
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight)
    {
        /*
         * Quarter horizontal line.
         */

        int quarterY =
                graphY +
                        graphHeight / 4;

        Gui.drawRect(
                graphX + 1,
                quarterY,
                graphX + graphWidth - 1,
                quarterY + 1,
                GRAPH_GRID
        );

        /*
         * Middle horizontal axis.
         */

        int middleY =
                graphY +
                        graphHeight / 2;

        Gui.drawRect(
                graphX + 1,
                middleY,
                graphX + graphWidth - 1,
                middleY + 1,
                GRAPH_AXIS
        );

        /*
         * Three-quarter horizontal line.
         */

        int threeQuarterY =
                graphY +
                        graphHeight * 3 / 4;

        Gui.drawRect(
                graphX + 1,
                threeQuarterY,
                graphX + graphWidth - 1,
                threeQuarterY + 1,
                GRAPH_GRID
        );

        /*
         * Vertical center.
         */

        int middleX =
                graphX +
                        graphWidth / 2;

        Gui.drawRect(
                middleX,
                graphY + 1,
                middleX + 1,
                graphY +
                        graphHeight -
                        1,
                GRAPH_GRID
        );

        /*
         * Quarter vertical lines.
         */

        int quarterX =
                graphX +
                        graphWidth / 4;

        int threeQuarterX =
                graphX +
                        graphWidth * 3 / 4;

        Gui.drawRect(
                quarterX,
                graphY + 1,
                quarterX + 1,
                graphY +
                        graphHeight -
                        1,
                GRAPH_GRID
        );

        Gui.drawRect(
                threeQuarterX,
                graphY + 1,
                threeQuarterX + 1,
                graphY +
                        graphHeight -
                        1,
                GRAPH_GRID
        );
    }

    /*
     * =========================================================
     * CURVE
     * =========================================================
     */

    private void drawCurve(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight,
            int color)
    {
        int previousX =
                graphX;

        int previousY =
                graphY +
                        graphHeight;

        int samples =
                60;

        for (
                int i = 0;
                i <= samples;
                i++
        )
        {
            float factor =
                    i /
                            (float) samples;

            double value =
                    calculateInterpolation(
                            interpolation,
                            easing,
                            factor
                    );

            value =
                    clamp(
                            value,
                            -0.25D,
                            1.25D
                    );

            int pointX =
                    graphX +
                            Math.round(
                                    factor *
                                            graphWidth
                            );

            int pointY =
                    graphY +
                            graphHeight -
                            Math.round(
                                    (float)
                                            (
                                                    (value + 0.25D)
                                                            / 1.5D
                                                            * graphHeight
                                            )
                            );

            pointY =
                    clamp(
                            pointY,
                            graphY,
                            graphY +
                                    graphHeight
                    );

            if (i > 0)
            {
                drawLine(
                        previousX,
                        previousY,
                        pointX,
                        pointY,
                        color
                );
            }

            previousX =
                    pointX;

            previousY =
                    pointY;
        }
    }

    /*
     * =========================================================
     * ANIMATED POINT
     * =========================================================
     */

    private void drawAnimatedPoint(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight)
    {
        float animationFactor =
                getAnimationFactor();

        double animatedValue =
                calculateInterpolation(
                        interpolation,
                        easing,
                        animationFactor
                );

        animatedValue =
                clamp(
                        animatedValue,
                        -0.25D,
                        1.25D
                );

        int pointX =
                graphX +
                        Math.round(
                                animationFactor *
                                        graphWidth
                        );

        int pointY =
                graphY +
                        graphHeight -
                        Math.round(
                                (float)
                                        (
                                                (animatedValue + 0.25D)
                                                        / 1.5D
                                                        * graphHeight
                                        )
                        );

        pointY =
                clamp(
                        pointY,
                        graphY,
                        graphY +
                                graphHeight
                );

        /*
         * Outer glow.
         */

        Gui.drawRect(
                pointX - 4,
                pointY - 4,
                pointX + 5,
                pointY + 5,
                0xFF252629
        );

        /*
         * Cyan center border.
         */

        Gui.drawRect(
                pointX - 3,
                pointY - 3,
                pointX + 4,
                pointY + 4,
                ACCENT
        );

        /*
         * White center.
         */

        Gui.drawRect(
                pointX - 2,
                pointY - 2,
                pointX + 3,
                pointY + 3,
                GRAPH_POINT
        );
    }

    /*
     * =========================================================
     * INTERPOLATION CALCULATION
     * =========================================================
     */

    private double calculateInterpolation(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            float factor)
    {
        Keyframe previous =
                new Keyframe(
                        0,
                        0.0D
                );

        Keyframe next =
                new Keyframe(
                        20,
                        1.0D
                );

        Keyframe previousPrevious =
                new Keyframe(
                        -20,
                        0.0D
                );

        Keyframe nextNext =
                new Keyframe(
                        40,
                        1.0D
                );

        previous.interp =
                interpolation;

        previous.easing =
                easing;

        previous.prev =
                previousPrevious;

        previous.next =
                next;

        next.prev =
                previous;

        next.next =
                nextNext;

        previousPrevious.prev =
                previousPrevious;

        previousPrevious.next =
                previous;

        nextNext.prev =
                next;

        nextNext.next =
                nextNext;

        return previous.interpolate(
                next,
                factor
        );
    }

    /*
     * =========================================================
     * ANIMATION
     * =========================================================
     */

    private float getAnimationFactor()
    {
        long elapsed =
                System.currentTimeMillis()
                        -
                        this.animationStartTime;

        return (
                elapsed % 1200L
        ) /
                1200.0F;
    }

    /*
     * =========================================================
     * NAMES
     * =========================================================
     */

    private String getInterpolationName(
            KeyframeInterpolation interpolation)
    {
        if (interpolation == null)
        {
            return "";
        }

        String key =
                interpolation.getKey();

        String localized =
                I18n.format(
                        key
                );

        if (
                localized == null ||
                        localized.equals(key)
        )
        {
            return makeReadableName(
                    interpolation.name()
            );
        }

        return localized;
    }

    private String makeReadableName(
            String name)
    {
        String lower =
                name.toLowerCase();

        if (lower.equals("const"))
        {
            return "Constant";
        }

        if (lower.equals("linear"))
        {
            return "Linear";
        }

        if (lower.equals("quad"))
        {
            return "Quadratic";
        }

        if (lower.equals("cubic"))
        {
            return "Cubic";
        }

        if (lower.equals("hermite"))
        {
            return "Hermite";
        }

        if (lower.equals("exp"))
        {
            return "Exponential";
        }

        if (lower.equals("bezier"))
        {
            return "Bezier";
        }

        if (lower.equals("back"))
        {
            return "Back";
        }

        if (lower.equals("elastic"))
        {
            return "Elastic";
        }

        if (lower.equals("bounce"))
        {
            return "Bounce";
        }

        if (lower.equals("sine"))
        {
            return "Sine";
        }

        if (lower.equals("quart"))
        {
            return "Quartic";
        }

        if (lower.equals("quint"))
        {
            return "Quintic";
        }

        if (lower.equals("circle"))
        {
            return "Circle";
        }

        return name;
    }

    private String getEasingName(
            KeyframeEasing easing)
    {
        if (easing == null)
        {
            return "In";
        }

        String name =
                easing.name();

        if (
                name == null ||
                        name.length() == 0
        )
        {
            return "In";
        }

        String lower =
                name.toLowerCase();

        if (lower.equals("in"))
        {
            return "In";
        }

        if (lower.equals("out"))
        {
            return "Out";
        }

        if (
                lower.equals("in_out") ||
                        lower.equals("inout")
        )
        {
            return "In / Out";
        }

        String result =
                name.substring(
                        0,
                        1
                ).toUpperCase();

        if (name.length() > 1)
        {
            result +=
                    name.substring(
                            1
                    ).toLowerCase();
        }

        return result;
    }

    /*
     * =========================================================
     * CLICK
     * =========================================================
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        /*
         * Header toggles dropdown.
         */

        if (
                mouseButton == 0 &&
                        isInside(
                                mouseX,
                                mouseY,
                                this.x,
                                this.y,
                                this.width,
                                25
                        )
        )
        {
            this.dropdownOpen =
                    !this.dropdownOpen;

            this.hoveredInterpolation =
                    null;

            return true;
        }

        /*
         * Selector toggles dropdown.
         */

        int selectorX =
                this.x + 7;

        int selectorY =
                this.y + 30;

        int selectorWidth =
                this.width - 14;

        int selectorHeight =
                20;

        if (
                mouseButton == 0 &&
                        isInside(
                                mouseX,
                                mouseY,
                                selectorX,
                                selectorY,
                                selectorWidth,
                                selectorHeight
                        )
        )
        {
            this.dropdownOpen =
                    !this.dropdownOpen;

            this.hoveredInterpolation =
                    null;

            return true;
        }

        if (!this.dropdownOpen)
        {
            return false;
        }

        final int headerHeight = 25;
        final int itemHeight = 18;

        int dropdownY =
                this.y +
                        headerHeight +
                        25;

        int visibleItems =
                Math.max(
                        1,
                        this.height / itemHeight
                );

        int maxScroll =
                Math.max(
                        0,
                        this.interpolations.size()
                                - visibleItems
                );

        this.dropdownScroll =
                clamp(
                        this.dropdownScroll,
                        0,
                        maxScroll
                );

        /*
         * =====================================================
         * SELECT INTERPOLATION
         * =====================================================
         */

        if (mouseButton == 0)
        {
            for (
                    int i = 0;
                    i <
                            this.interpolations.size();
                    i++
            )
            {
                int visibleIndex =
                        i -
                                this.dropdownScroll;

                if (
                        visibleIndex < 0 ||
                                visibleIndex >=
                                        visibleItems
                )
                {
                    continue;
                }

                int itemY =
                        dropdownY +
                                visibleIndex *
                                        itemHeight;

                if (
                        isInside(
                                mouseX,
                                mouseY,
                                this.x,
                                itemY,
                                this.width,
                                itemHeight
                        )
                )
                {
                    this.selectedInterpolation =
                            this.interpolations.get(
                                    i
                            );

                    if (
                            this.interpolationChanged
                                    != null
                    )
                    {
                        this.interpolationChanged.run();
                    }

                    this.dropdownOpen =
                            false;

                    this.hoveredInterpolation =
                            null;

                    this.animationStartTime =
                            System.currentTimeMillis();

                    return true;
                }
            }
        }

        /*
         * =====================================================
         * CLICK OUTSIDE
         * =====================================================
         */

        int dropdownHeight =
                visibleItems *
                        itemHeight;

        boolean insideDropdown =
                isInside(
                        mouseX,
                        mouseY,
                        this.x,
                        dropdownY,
                        this.width,
                        dropdownHeight
                );

        boolean insideSelector =
                isInside(
                        mouseX,
                        mouseY,
                        selectorX,
                        selectorY,
                        selectorWidth,
                        selectorHeight
                );

        if (
                !insideDropdown &&
                        !insideSelector
        )
        {
            this.dropdownOpen =
                    false;

            this.hoveredInterpolation =
                    null;

            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * RELEASE
     * =========================================================
     */

    public boolean mouseReleased(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        return false;
    }

    /*
     * =========================================================
     * MOUSE OVER
     * =========================================================
     */

    public boolean isMouseOver(
            int mouseX,
            int mouseY)
    {
        return isInside(
                mouseX,
                mouseY,
                this.x,
                this.y,
                this.width,
                this.height
        );
    }

    /*
     * =========================================================
     * SCROLL
     * =========================================================
     */

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int amount)
    {
        if (!this.dropdownOpen)
        {
            return false;
        }

        final int itemHeight = 18;

        int visibleItems =
                Math.max(
                        1,
                        this.height / itemHeight
                );

        int maxScroll =
                Math.max(
                        0,
                        this.interpolations.size()
                                - visibleItems
                );

        if (maxScroll <= 0)
        {
            return false;
        }

        if (amount > 0)
        {
            this.dropdownScroll =
                    Math.max(
                            0,
                            this.dropdownScroll - 1
                    );
        }
        else if (amount < 0)
        {
            this.dropdownScroll =
                    Math.min(
                            maxScroll,
                            this.dropdownScroll + 1
                    );
        }

        return true;
    }

    /*
     * =========================================================
     * HIT TEST
     * =========================================================
     */

    private boolean isInside(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height)
    {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }

    /*
     * =========================================================
     * LINE
     * =========================================================
     */

    private void drawLine(
            int x1,
            int y1,
            int x2,
            int y2,
            int color)
    {
        int distance =
                Math.max(
                        Math.abs(
                                x2 - x1
                        ),
                        Math.abs(
                                y2 - y1
                        )
                );

        if (distance <= 0)
        {
            Gui.drawRect(
                    x1,
                    y1,
                    x1 + 1,
                    y1 + 1,
                    color
            );

            return;
        }

        for (
                int i = 0;
                i <= distance;
                i++
        )
        {
            float t =
                    i /
                            (float) distance;

            int drawX =
                    Math.round(
                            x1 +
                                    (x2 - x1) *
                                            t
                    );

            int drawY =
                    Math.round(
                            y1 +
                                    (y2 - y1) *
                                            t
                    );

            Gui.drawRect(
                    drawX,
                    drawY,
                    drawX + 1,
                    drawY + 1,
                    color
            );
        }
    }

    /*
     * =========================================================
     * CLAMP
     * =========================================================
     */

    private double clamp(
            double value,
            double min,
            double max)
    {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }

    private int clamp(
            int value,
            int min,
            int max)
    {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
}