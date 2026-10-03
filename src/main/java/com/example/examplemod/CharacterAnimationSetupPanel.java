package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.animation.model.ActionConfig;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

public class CharacterAnimationSetupPanel
{
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_HEIGHT =
            ROW_HEIGHT * VISIBLE_ROWS;

    public static final int HEIGHT = 178;

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

    private GuiTextField speedField;
    private GuiTextField fadeField;
    private GuiTextField tickField;

    private String fieldSignature = "";
    private boolean suppressFieldSync;

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

        positionTextFields();
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
            this.fieldSignature = "";
        }

        syncFields();
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

        ensureTextFields(Minecraft.getMinecraft());

        if (this.speedField.mouseClicked(mouseX, mouseY, mouseButton) ||
            this.fadeField.mouseClicked(mouseX, mouseY, mouseButton) ||
            this.tickField.mouseClicked(mouseX, mouseY, mouseButton))
        {
            return true;
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
                syncFields();

                positionPopup();

                this.pickerPopup.open(
                        this.runtimeActor,
                        this.selectedAction
                );

                return true;
            }
        }

        int parametersY = this.y + 99;

        if (isInside(
                this.x + 88,
                parametersY + 18,
                this.width - 100,
                17,
                mouseX,
                mouseY
        ))
        {
            ActionConfig config = getSelectedConfig();
            boolean next = !config.clamp;

            if (CharacterAnimationSetupController.setParameter(
                    this.selectedKey,
                    this.selectedAction,
                    CharacterAnimationSetupController.PARAM_CLAMP,
                    Boolean.valueOf(next)
            ))
            {
                this.fieldSignature = "";
                return true;
            }
        }

        if (isInside(
                this.x + 88,
                parametersY + 36,
                this.width - 100,
                17,
                mouseX,
                mouseY
        ))
        {
            ActionConfig config = getSelectedConfig();
            boolean next = !config.reset;

            if (CharacterAnimationSetupController.setParameter(
                    this.selectedKey,
                    this.selectedAction,
                    CharacterAnimationSetupController.PARAM_RESET,
                    Boolean.valueOf(next)
            ))
            {
                this.fieldSignature = "";
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
        if (this.pickerPopup.isOpen() &&
                this.pickerPopup.keyTyped(
                        typedChar,
                        keyCode
                ))
        {
            return true;
        }

        if (this.selectedKey == null)
        {
            return false;
        }

        if (this.speedField != null && this.speedField.isFocused())
        {
            if (this.speedField.textboxKeyTyped(typedChar, keyCode))
            {
                applyNumericField(
                        CharacterAnimationSetupController.PARAM_SPEED,
                        this.speedField,
                        false
                );
                return true;
            }
        }

        if (this.fadeField != null && this.fadeField.isFocused())
        {
            if (this.fadeField.textboxKeyTyped(typedChar, keyCode))
            {
                applyNumericField(
                        CharacterAnimationSetupController.PARAM_FADE,
                        this.fadeField,
                        true
                );
                return true;
            }
        }

        if (this.tickField != null && this.tickField.isFocused())
        {
            if (this.tickField.textboxKeyTyped(typedChar, keyCode))
            {
                applyNumericField(
                        CharacterAnimationSetupController.PARAM_TICK,
                        this.tickField,
                        true
                );
                return true;
            }
        }

        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            this.speedField.setFocused(false);
            this.fadeField.setFocused(false);
            this.tickField.setFocused(false);
            return true;
        }

        return false;
    }

    public void updateCursorCounter()
    {
        if (this.pickerPopup.isOpen())
        {
            this.pickerPopup.updateCursorCounter();
        }

        if (this.speedField != null) this.speedField.updateCursorCounter();
        if (this.fadeField != null) this.fadeField.updateCursorCounter();
        if (this.tickField != null) this.tickField.updateCursorCounter();
    }

    private void ensureTextFields(Minecraft mc)
    {
        if (this.speedField != null)
        {
            positionTextFields();
            return;
        }

        this.speedField = createField(mc, 7311);
        this.fadeField = createField(mc, 7312);
        this.tickField = createField(mc, 7313);

        positionTextFields();
    }

    private GuiTextField createField(Minecraft mc, int id)
    {
        GuiTextField field =
                new GuiTextField(
                        id,
                        mc.fontRenderer,
                        0,
                        0,
                        40,
                        16
                );

        field.setMaxStringLength(16);
        field.setCanLoseFocus(true);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(COLOR_TEXT);
        field.setVisible(true);

        return field;
    }

    private void positionTextFields()
    {
        if (this.speedField == null) return;

        int parametersY = this.y + 99;

        this.speedField.x = this.x + 48;
        this.speedField.y = parametersY + 1;
        this.speedField.width = 38;
        this.speedField.height = 16;

        this.fadeField.x = this.x + 123;
        this.fadeField.y = parametersY + 1;
        this.fadeField.width = 34;
        this.fadeField.height = 16;

        this.tickField.x = this.x + 48;
        this.tickField.y = parametersY + 19;
        this.tickField.width = 38;
        this.tickField.height = 16;
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

    private ActionConfig getSelectedConfig()
    {
        return CharacterAnimationSetupController.getEffectiveConfig(
                this.selectedActor,
                this.currentFrame,
                this.selectedAction
        );
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

    private void drawParameters(
            Minecraft mc,
            int y,
            int mouseX,
            int mouseY)
    {
        ActionConfig config = getSelectedConfig();

        drawHint(
                mc,
                "Parameters: " + this.selectedAction,
                this.x + 12,
                y
        );

        drawLabel(mc, "Speed", this.x + 12, y + 23);
        drawLabel(mc, "Fade", this.x + 88, y + 23);
        drawLabel(mc, "Tick", this.x + 12, y + 41);

        drawTextFieldFrame(this.speedField);
        drawTextFieldFrame(this.fadeField);
        drawTextFieldFrame(this.tickField);

        boolean clampHovered = isInside(
                this.x + 88,
                y + 18,
                this.width - 100,
                17,
                mouseX,
                mouseY
        );

        boolean resetHovered = isInside(
                this.x + 88,
                y + 36,
                this.width - 100,
                17,
                mouseX,
                mouseY
        );

        drawToggle(
                mc,
                "Clamp",
                config.clamp,
                this.x + 88,
                y + 18,
                this.width - 100,
                clampHovered
        );

        drawToggle(
                mc,
                "Reset",
                config.reset,
                this.x + 88,
                y + 36,
                this.width - 100,
                resetHovered
        );
    }

    private void drawTextFieldFrame(GuiTextField field)
    {
        if (field == null) return;

        int color =
                field.isFocused()
                        ? EditorThemeManager.get().getAccent()
                        : COLOR_BORDER;

        GuiScreen.drawRect(
                field.x - 1,
                field.y - 1,
                field.x + field.width + 1,
                field.y + field.height + 1,
                color
        );

        field.drawTextBox();
    }

    private void drawToggle(
            Minecraft mc,
            String label,
            boolean value,
            int x,
            int y,
            int width,
            boolean hovered)
    {
        GuiScreen.drawRect(
                x,
                y,
                x + width,
                y + 17,
                hovered
                        ? COLOR_PANEL_HOVER
                        : COLOR_PANEL_DARK
        );

        mc.fontRenderer.drawString(
                value ? "✓ " + label : "□ " + label,
                x + 5,
                y + 5,
                value
                        ? EditorThemeManager.get().getAccentBright()
                        : COLOR_TEXT_SECONDARY
        );
    }

    private String formatFloat(float value)
    {
        if (Math.abs(value - Math.round(value)) < 0.0001F)
        {
            return String.valueOf((int) value);
        }

        return String.format(
                java.util.Locale.US,
                "%.3f",
                value
        );
    }

    private String trim(
            Minecraft mc,
            String text,
            int width)
    {
        if (text == null) return "";

        if (mc.fontRenderer.getStringWidth(text) <= width)
        {
            return text;
        }

        return mc.fontRenderer.trimStringToWidth(
                text,
                Math.max(1, width - 10)
        ) + "...";
    }

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
