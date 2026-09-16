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
        this.mc = Minecraft.getMinecraft();

        this.selectedInterpolation =
                KeyframeInterpolation.LINEAR;

        this.selectedEasing =
                KeyframeEasing.IN;

        this.hoveredInterpolation = null;

        this.dropdownOpen = false;

        this.dropdownScroll = 0;

        this.animationStartTime =
                System.currentTimeMillis();

        this.interpolations =
                new ArrayList<KeyframeInterpolation>();

        for (KeyframeInterpolation interpolation :
                KeyframeInterpolation.values())
        {
            this.interpolations.add(interpolation);
        }
    }

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

    public void setSelectedEasing(
            KeyframeEasing easing)
    {
        if (easing == null)
        {
            easing = KeyframeEasing.IN;
        }

        this.selectedEasing = easing;
    }

    public KeyframeEasing getSelectedEasing()
    {
        return this.selectedEasing;
    }

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

    public void draw(
            int mouseX,
            int mouseY)
    {
        if (this.width <= 0 || this.height <= 0)
        {
            return;
        }

        /*
         * Main panel.
         */
        Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                0xE0181818
        );

        /*
         * Header.
         */
        int headerHeight = 20;

        Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + headerHeight,
                0xFF242424
        );

        String selectedName =
                getInterpolationName(
                        this.selectedInterpolation
                );

        this.mc.fontRenderer.drawString(
                selectedName,
                this.x + 6,
                this.y + 6,
                0xFFFFFFFF
        );

        /*
         * Arrow.
         */
        String arrow =
                this.dropdownOpen
                        ? "▲"
                        : "▼";

        this.mc.fontRenderer.drawString(
                arrow,
                this.x + this.width - 12,
                this.y + 6,
                0xFFFFFFFF
        );

        /*
         * Closed state:
         * show the selected interpolation graph.
         */
        if (!this.dropdownOpen)
        {
            return;
        }

        /*
         * Open state:
         * draw the dropdown and determine
         * which interpolation is hovered.
         */
        drawDropdown(
                mouseX,
                mouseY
        );
    }

    private void drawDropdown(
            int mouseX,
            int mouseY)
    {
        int headerHeight = 20;
        int itemHeight = 18;

        int dropdownX = this.x;
        int dropdownY =
                this.y + headerHeight;

        int dropdownWidth =
                this.width;

        /*
         * Показываем максимум столько элементов,
         * сколько реально помещается в высоту панели.
         */
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
                visibleItems * itemHeight;

        /*
         * Background.
         */
        Gui.drawRect(
                dropdownX,
                dropdownY,
                dropdownX + dropdownWidth,
                dropdownY + dropdownHeight,
                0xFF181818
        );

        this.hoveredInterpolation = null;

        /*
         * Draw visible interpolation items.
         */
        for (int visibleIndex = 0;
             visibleIndex < visibleItems;
             visibleIndex++)
        {
            int index =
                    this.dropdownScroll
                            + visibleIndex;

            if (index >= this.interpolations.size())
            {
                break;
            }

            KeyframeInterpolation interpolation =
                    this.interpolations.get(index);

            int itemY =
                    dropdownY
                            + visibleIndex * itemHeight;

            boolean hovered =
                    mouseX >= dropdownX
                            && mouseX < dropdownX + dropdownWidth
                            && mouseY >= itemY
                            && mouseY < itemY + itemHeight;

            if (hovered)
            {
                this.hoveredInterpolation =
                        interpolation;

                Gui.drawRect(
                        dropdownX,
                        itemY,
                        dropdownX + dropdownWidth,
                        itemY + itemHeight,
                        0xFF3A3A3A
                );
            }

            /*
             * Selected interpolation marker.
             */
            if (interpolation ==
                    this.selectedInterpolation)
            {
                Gui.drawRect(
                        dropdownX,
                        itemY,
                        dropdownX + 3,
                        itemY + itemHeight,
                        0xFFFFFFFF
                );
            }

            String name =
                    getInterpolationName(
                            interpolation
                    );

            this.mc.fontRenderer.drawString(
                    name,
                    dropdownX + 7,
                    itemY + 5,
                    0xFFFFFFFF
            );
        }

        /*
         * Scroll indicators.
         */
        if (this.dropdownScroll > 0)
        {
            this.mc.fontRenderer.drawString(
                    "▲",
                    dropdownX + dropdownWidth - 12,
                    dropdownY + 2,
                    0xFFFFFFFF
            );
        }

        if (this.dropdownScroll < maxScroll)
        {
            this.mc.fontRenderer.drawString(
                    "▼",
                    dropdownX + dropdownWidth - 12,
                    dropdownY
                            + dropdownHeight
                            - 10,
                    0xFFFFFFFF
            );
        }

        /*
         * Graph preview of the interpolation
         * currently under the mouse.
         */
        if (this.hoveredInterpolation != null)
        {
            int graphWidth = 110;
            int graphHeight = 75;

            int graphX =
                    dropdownX
                            + dropdownWidth
                            + 6;

            int graphY =
                    dropdownY;

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

    private void drawFloatingGraph(
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
                graphX - 2,
                graphY - 2,
                graphX + graphWidth + 2,
                graphY + graphHeight + 2,
                0xF0202020
        );

        /*
         * Border.
         */
        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + 1,
                0xFF606060
        );

        Gui.drawRect(
                graphX,
                graphY + graphHeight - 1,
                graphX + graphWidth,
                graphY + graphHeight,
                0xFF606060
        );

        Gui.drawRect(
                graphX,
                graphY,
                graphX + 1,
                graphY + graphHeight,
                0xFF606060
        );

        Gui.drawRect(
                graphX + graphWidth - 1,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                0xFF606060
        );

        /*
         * Animated mathematical curve.
         */
        int previousX = graphX;
        int previousY =
                graphY + graphHeight;

        int samples = 60;

        for (int i = 0; i <= samples; i++)
        {
            float factor =
                    i / (float) samples;

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
                    graphX
                            + Math.round(
                            factor
                                    * graphWidth
                    );

            int pointY =
                    graphY
                            + graphHeight
                            - Math.round(
                            (float)
                                    ((value + 0.25D)
                                            / 1.5D
                                            * graphHeight)
                    );

            pointY =
                    clamp(
                            pointY,
                            graphY,
                            graphY + graphHeight
                    );

            if (i > 0)
            {
                drawLine(
                        previousX,
                        previousY,
                        pointX,
                        pointY,
                        0xFFFFFFFF
                );
            }

            previousX = pointX;
            previousY = pointY;
        }

        /*
         * Animated point.
         */
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
                graphX
                        + Math.round(
                        animationFactor
                                * graphWidth
                );

        int pointY =
                graphY
                        + graphHeight
                        - Math.round(
                        (float)
                                ((animatedValue + 0.25D)
                                        / 1.5D
                                        * graphHeight)
                );

        pointY =
                clamp(
                        pointY,
                        graphY,
                        graphY + graphHeight
                );

        /*
         * Draw a small mathematical
         * animation marker.
         */
        Gui.drawRect(
                pointX - 2,
                pointY - 2,
                pointX + 3,
                pointY + 3,
                0xFFFFFFFF
        );
    }

    private void drawGraph(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            int graphX,
            int graphY,
            int graphWidth,
            int graphHeight)
    {
        Gui.drawRect(
                graphX,
                graphY,
                graphX + graphWidth,
                graphY + graphHeight,
                0xFF101010
        );

        int previousX = graphX;
        int previousY =
                graphY + graphHeight;

        int samples = 60;

        for (int i = 0; i <= samples; i++)
        {
            float factor =
                    i / (float) samples;

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
                    graphX
                            + Math.round(
                            factor * graphWidth
                    );

            int pointY =
                    graphY
                            + graphHeight
                            - Math.round(
                            (float)
                                    ((value + 0.25D)
                                            / 1.5D
                                            * graphHeight)
                    );

            pointY =
                    clamp(
                            pointY,
                            graphY,
                            graphY + graphHeight
                    );

            if (i > 0)
            {
                drawLine(
                        previousX,
                        previousY,
                        pointX,
                        pointY,
                        0xFFCCCCCC
                );
            }

            previousX = pointX;
            previousY = pointY;
        }

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
                graphX
                        + Math.round(
                        animationFactor * graphWidth
                );

        int pointY =
                graphY
                        + graphHeight
                        - Math.round(
                        (float)
                                ((animatedValue + 0.25D)
                                        / 1.5D
                                        * graphHeight)
                );

        pointY =
                clamp(
                        pointY,
                        graphY,
                        graphY + graphHeight
                );

        Gui.drawRect(
                pointX - 2,
                pointY - 2,
                pointX + 3,
                pointY + 3,
                0xFFFFFFFF
        );
    }

    private double calculateInterpolation(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing,
            float factor)
    {
        /*
         * These are real McLib keyframes.
         *
         * We deliberately use the same
         * KeyframeInterpolation.interpolate()
         * path used by AnimationInterpolator.
         */
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

    private float getAnimationFactor()
    {
        long elapsed =
                System.currentTimeMillis()
                        - this.animationStartTime;

        /*
         * 1200 ms for one complete
         * mathematical movement.
         */
        float factor =
                (elapsed % 1200L)
                        / 1200.0F;

        return factor;
    }

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
                I18n.format(key);

        if (localized == null
                || localized.equals(key))
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

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        /*
         * Left click on header opens/closes
         * the dropdown.
         */
        if (mouseButton == 0
                && isInside(
                mouseX,
                mouseY,
                this.x,
                this.y,
                this.width,
                20))
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

        int itemHeight = 18;

        int dropdownY =
                this.y + 20;

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
         * Select an interpolation.
         */
        if (mouseButton == 0)
        {
            for (int i = 0;
                 i < this.interpolations.size();
                 i++)
            {
                int visibleIndex =
                        i - this.dropdownScroll;

                if (visibleIndex < 0
                        || visibleIndex >= visibleItems)
                {
                    continue;
                }

                int itemY =
                        dropdownY
                                + visibleIndex * itemHeight;

                if (isInside(
                        mouseX,
                        mouseY,
                        this.x,
                        itemY,
                        this.width,
                        itemHeight))
                {
                    this.selectedInterpolation =
                            this.interpolations.get(i);

                    if (this.interpolationChanged != null)
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
         * Clicking outside closes
         * the dropdown.
         */
        if (!isInside(
                mouseX,
                mouseY,
                this.x,
                this.y,
                this.width,
                this.height))
        {
            this.dropdownOpen =
                    false;

            this.hoveredInterpolation =
                    null;

            return true;
        }

        return false;
    }

    public boolean mouseReleased(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        return false;
    }

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

    private void drawLine(
            int x1,
            int y1,
            int x2,
            int y2,
            int color)
    {
        int distance =
                Math.max(
                        Math.abs(x2 - x1),
                        Math.abs(y2 - y1)
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

        for (int i = 0;
             i <= distance;
             i++)
        {
            float t =
                    i / (float) distance;

            int x =
                    Math.round(
                            x1
                                    + (x2 - x1)
                                    * t
                    );

            int y =
                    Math.round(
                            y1
                                    + (y2 - y1)
                                    * t
                    );

            Gui.drawRect(
                    x,
                    y,
                    x + 1,
                    y + 1,
                    color
            );
        }
    }

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
    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int amount)
    {
        if (!this.dropdownOpen)
        {
            return false;
        }

        int itemHeight = 18;

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

}