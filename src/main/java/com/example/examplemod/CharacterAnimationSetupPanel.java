package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;


public class CharacterAnimationSetupPanel
{
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_HEIGHT =
            ROW_HEIGHT * VISIBLE_ROWS;

    public static final int HEIGHT = 94;

    private static final int COLOR_PANEL_DARK = 0xFF111315;
    private static final int COLOR_PANEL_HOVER = 0xFF25292D;
    private static final int COLOR_BORDER = 0xFF303438;
    private static final int COLOR_TEXT = 0xFFE2E5E7;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9AA1A6;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;

    private int x;
    private int y;
    private int width;

    private BlockbusterSceneActorData selectedActor;
    private CharacterKey selectedKey;
    private EntityActor runtimeActor;
    private int currentFrame;

    private int actionOffset;
    private String selectedAction = "Idle";

    private final CharacterAnimationPickerPopup pickerPopup;

    public CharacterAnimationSetupPanel()
    {
        this.pickerPopup =
                new CharacterAnimationPickerPopup(
                        Minecraft.getMinecraft(),
                        new CharacterAnimationPickerPopup.SelectionListener()
                        {
                            @Override
                            public void onAnimationSelected(
                                    String animation)
                            {
                                assignAnimation(animation);
                            }
                        }
                );
    }

    public void setBounds(
            int x,
            int y,
            int width)
    {
        this.x = x;
        this.y = y;
        this.width = width;

        positionPopup();
    }

    public void setState(
            BlockbusterSceneActorData actor,
            CharacterKey key,
            EntityActor runtimeActor,
            int frame)
    {
        boolean changed =
                this.selectedActor != actor ||
                this.selectedKey != key ||
                this.currentFrame != frame;

        this.selectedActor = actor;
        this.selectedKey = key;
        this.runtimeActor = runtimeActor;
        this.currentFrame = Math.max(0, frame);

        if (changed)
        {
            this.actionOffset = 0;
        }

        positionPopup();
    }

    public void draw(
            Minecraft mc,
            int mouseX,
            int mouseY)
    {
        ensureTextFields(mc);

        if (this.selectedActor == null ||
                this.selectedKey == null)
        {
            drawHint(
                    mc,
                    "Select Character key",
                    this.x + 12,
                    this.y + 4
            );

            return;
        }

        if (getCurrentMorph() == null)
        {
            drawHint(
                    mc,
                    "Animation Setup requires",
                    this.x + 12,
                    this.y + 4
            );

            drawHint(
                    mc,
                    "an Emoticons character",
                    this.x + 12,
                    this.y + 20
            );

            return;
        }

        List<String> actions = getActions();

        this.actionOffset =
                Math.max(
                        0,
                        Math.min(
                                getMaxActionOffset(actions),
                                this.actionOffset
                        )
                );

        drawHint(
                mc,
                "Actions",
                this.x + 12,
                this.y
        );

        int listY = this.y + 14;
        int listWidth = this.width - 28;

        GuiScreen.drawRect(
                this.x + 8,
                listY,
                this.x + 8 + listWidth,
                listY + LIST_HEIGHT,
                COLOR_PANEL_DARK
        );

        for (int i = 0; i < VISIBLE_ROWS; i++)
        {
            int index = this.actionOffset + i;

            if (index >= actions.size())
            {
                break;
            }

            drawActionRow(
                    mc,
                    actions.get(index),
                    listY + i * ROW_HEIGHT,
                    mouseX,
                    mouseY
            );
        }

        drawActionScrollbar(
                actions,
                listY,
                mouseX,
                mouseY
        );

        drawParameters(
                mc,
                this.y + 99,
                mouseX,
                mouseY
        );

        if (this.pickerPopup.isOpen())
        {
            this.pickerPopup.draw(
                    mc,
                    mouseX,
                    mouseY
            );
        }
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (this.pickerPopup.isOpen())
        {
            return this.pickerPopup.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );
        }

        if (mouseButton != 0 || this.selectedKey == null)
        {
            return false;
        }

        int listY = this.y + 14;
        int listWidth = this.width - 28;

        if (isInside(
                this.x + 8,
                listY,
                listWidth,
                LIST_HEIGHT,
                mouseX,
                mouseY
        ))
        {
            List<String> actions = getActions();

            int index =
                    this.actionOffset +
                    (mouseY - listY) / ROW_HEIGHT;

            if (index >= 0 && index < actions.size())
            {
                this.selectedAction = actions.get(index);

                positionPopup();

                this.pickerPopup.open(
                        this.runtimeActor,
                        this.selectedAction
                );

                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int direction)
    {
        if (this.pickerPopup.isOpen())
        {
            return this.pickerPopup.mouseScrolled(
                    mouseX,
                    mouseY,
                    direction
            );
        }

        int listY = this.y + 14;
        int listWidth = this.width - 28;

        if (isInside(
                this.x + 8,
                listY,
                listWidth,
                LIST_HEIGHT,
                mouseX,
                mouseY
        ))
        {
            List<String> actions = getActions();

            if (direction > 0)
            {
                this.actionOffset =
                        Math.max(
                                0,
                                this.actionOffset - 1
                        );
            }
            else if (direction < 0)
            {
                this.actionOffset =
                        Math.min(
                                getMaxActionOffset(actions),
                                this.actionOffset + 1
                        );
            }

            return true;
        }

        return false;
    }

    public boolean keyTyped(
            char typedChar,
            int keyCode)
            throws IOException
    {
        if (this.pickerPopup.isOpen())
        {
            return this.pickerPopup.keyTyped(
                    typedChar,
                    keyCode
            );
        }

        return false;
    }

    public void updateCursorCounter()
    {
        if (this.pickerPopup.isOpen())
        {
            this.pickerPopup.updateCursorCounter();
        }

    }

    private void positionPopup()
    {
        this.pickerPopup.setBounds(
                this.x + 8,
                this.y + 14,
                this.width - 16
        );
    }

    private void syncFields()
    {
        if (this.speedField == null || this.selectedKey == null)
        {
            return;
        }

        ActionConfig config = getSelectedConfig();

        String signature =
                config.name + "|" +
                config.speed + "|" +
                config.fade + "|" +
                config.tick + "|" +
                config.clamp + "|" +
                config.reset + "|" +
                this.selectedAction;

        if (signature.equals(this.fieldSignature))
        {
            return;
        }

        this.fieldSignature = signature;
        this.suppressFieldSync = true;

        this.speedField.setText(formatFloat(config.speed));
        this.fadeField.setText(String.valueOf((int) config.fade));
        this.tickField.setText(String.valueOf(config.tick));

        this.suppressFieldSync = false;
    }

    private void applyNumericField(
            String parameter,
            GuiTextField field,
            boolean integer)
    {
        if (this.suppressFieldSync || this.selectedKey == null)
        {
            return;
        }

        String text = field.getText();

        try
        {
            if (integer)
            {
                int value = Integer.parseInt(text);

                if (value < 0)
                {
                    value = 0;
                    field.setText("0");
                }

                if (CharacterAnimationSetupController.setParameter(
                        this.selectedKey,
                        this.selectedAction,
                        parameter,
                        Integer.valueOf(value)
                ))
                {
                    this.fieldSignature = "";
                }
            }
            else
            {
                float value = Float.parseFloat(text);

                if (value < -100F) value = -100F;
                if (value > 100F) value = 100F;

                if (CharacterAnimationSetupController.setParameter(
                        this.selectedKey,
                        this.selectedAction,
                        parameter,
                        Float.valueOf(value)
                ))
                {
                    this.fieldSignature = "";
                }
            }
        }
        catch (NumberFormatException ignored)
        {
        }
    }

    private void assignAnimation(String animation)
    {
        if (this.selectedKey == null) return;

        if (CharacterAnimationSetupController.assignAnimation(
                this.selectedKey,
                this.selectedAction,
                animation
        ))
        {
            this.fieldSignature = "";
            syncFields();
        }
    }

    public String getSelectedAction()
    {
        return this.selectedAction;
    }

    private List<String> getActions()
    {
        List<String> actions =
                new ArrayList<String>();

        for (String action :
                CharacterAnimationSetupController.ACTIONS)
        {
            actions.add(action);
        }

        return actions;
    }

    private void validateSelectedAction()
    {
        for (String action :
                CharacterAnimationSetupController.ACTIONS)
        {
            if (action.equals(this.selectedAction))
            {
                return;
            }
        }

        this.selectedAction = "Idle";
    }

    private int getMaxActionOffset(List<String> actions)
    {
        return Math.max(
                0,
                actions.size() - VISIBLE_ROWS
        );
    }

    private AnimatedMorph getCurrentMorph()
    {
        if (this.runtimeActor == null ||
                this.runtimeActor.morph == null)
        {
            return null;
        }

        try
        {
            AbstractMorphHolder:
            {
                if (this.runtimeActor.morph.get() instanceof AnimatedMorph)
                {
                    return (AnimatedMorph) this.runtimeActor.morph.get();
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return null;
    }

    private void drawActionRow(
            Minecraft mc,
            String action,
            int rowY,
            int mouseX,
            int mouseY)
    {
        boolean selected = action.equals(this.selectedAction);

        boolean hovered = isInside(
                this.x + 8,
                rowY,
                this.width - 28,
                ROW_HEIGHT,
                mouseX,
                mouseY
        );

        if (selected || hovered)
        {
            GuiScreen.drawRect(
                    this.x + 8,
                    rowY,
                    this.x + 8 + this.width - 28,
                    rowY + ROW_HEIGHT,
                    COLOR_PANEL_HOVER
            );
        }

        mc.fontRenderer.drawString(
                action,
                this.x + 12,
                rowY + 4,
                selected
                        ? EditorThemeManager.get().getAccentBright()
                        : COLOR_TEXT_SECONDARY
        );

        String assigned =
                CharacterAnimationSetupController.getAssignedAnimation(
                        this.selectedActor,
                        this.currentFrame,
                        action
                );

        boolean explicit =
                CharacterAnimationSetupController.hasEffectiveActionData(
                        this.selectedActor,
                        this.currentFrame,
                        action
                );

        String value =
                explicit
                        ? assigned
                        : "default: " + assigned;

        int valueWidth = Math.max(40, this.width - 95);
        int valueX = this.x + this.width - valueWidth - 13;

        mc.fontRenderer.drawString(
                trim(mc, value, valueWidth - 6),
                valueX,
                rowY + 4,
                explicit
                        ? EditorThemeManager.get().getAccent()
                        : COLOR_TEXT_MUTED
        );
    }

    private void drawActionScrollbar(
            List<String> actions,
            int listY,
            int mouseX,
            int mouseY)
    {
        if (actions.size() <= VISIBLE_ROWS) return;

        int scrollbarX = this.x + this.width - 16;
        int height = LIST_HEIGHT;

        float ratio =
                (float) VISIBLE_ROWS /
                (float) actions.size();

        int thumbHeight =
                Math.max(12, (int) (height * ratio));

        int travel = height - thumbHeight;
        int thumbY = listY;

        if (getMaxActionOffset(actions) > 0)
        {
            thumbY +=
                    (int) (
                            travel *
                            ((float) this.actionOffset /
                                    (float) getMaxActionOffset(actions))
                    );
        }

        boolean hovered =
                mouseX >= scrollbarX &&
                mouseX < scrollbarX + 7 &&
                mouseY >= thumbY &&
                mouseY < thumbY + thumbHeight;

        GuiScreen.drawRect(
                scrollbarX,
                thumbY,
                scrollbarX + 7,
                thumbY + thumbHeight,
                hovered
                        ? EditorThemeManager.get().getAccent()
                        : COLOR_BORDER
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

    private boolean isInside(
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
}
