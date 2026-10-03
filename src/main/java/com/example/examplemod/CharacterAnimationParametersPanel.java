package com.example.examplemod;

import java.io.IOException;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.animation.model.ActionConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

public class CharacterAnimationParametersPanel
{
    public static final int HEIGHT = 78;

    private static final int COLOR_PANEL_DARK = 0xFF111111;
    private static final int COLOR_BORDER = 0xFF303030;
    private static final int COLOR_TEXT = 0xFFE2E5E7;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9AA1A6;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;

    private int x;
    private int y;
    private int width;

    private BlockbusterSceneActorData selectedActor;
    private CharacterKey selectedKey;
    private int currentFrame;
    private String selectedAction = "Idle";

    private GuiTextField speedField;
    private GuiTextField fadeField;
    private GuiTextField tickField;

    private String fieldSignature = "";
    private boolean suppressFieldSync;

    public void setBounds(int x, int y, int width)
    {
        this.x = x;
        this.y = y;
        this.width = width;

        positionTextFields();
    }

    public void setState(
            BlockbusterSceneActorData actor,
            CharacterKey key,
            int frame,
            String action)
    {
        boolean changed =
                this.selectedActor != actor ||
                this.selectedKey != key ||
                this.currentFrame != frame ||
                !equals(this.selectedAction, action);

        this.selectedActor = actor;
        this.selectedKey = key;
        this.currentFrame = Math.max(0, frame);

        if (action != null && !action.isEmpty())
        {
            this.selectedAction = action;
        }
        else
        {
            this.selectedAction = "Idle";
        }

        if (changed)
        {
            this.fieldSignature = "";
        }

        syncFields();
    }

    public void draw(Minecraft mc, int mouseX, int mouseY)
    {
        ensureTextFields(mc);

        if (this.selectedKey == null)
        {
            drawHint(
                    mc,
                    "Select Character key",
                    this.x + 12,
                    this.y + 4
            );

            return;
        }

        ActionConfig config = getSelectedConfig();

        drawHint(
                mc,
                "Animation: " + this.selectedAction,
                this.x + 12,
                this.y + 4
        );

        drawLabel(mc, "Speed", this.x + 12, this.y + 23);
        drawLabel(mc, "Fade", this.x + 88, this.y + 23);
        drawLabel(mc, "Tick", this.x + 12, this.y + 41);

        drawTextFieldFrame(this.speedField);
        drawTextFieldFrame(this.fadeField);
        drawTextFieldFrame(this.tickField);

        drawLabel(mc, "Clamp", this.x + 88, this.y + 41);
        drawToggle(
                mc,
                this.x + 123,
                this.y + 36,
                config.clamp
        );

        drawLabel(mc, "Reset", this.x + 12, this.y + 59);
        drawToggle(
                mc,
                this.x + 48,
                this.y + 54,
                config.reset
        );
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (mouseButton != 0 || this.selectedKey == null)
        {
            return false;
        }

        ensureTextFields(Minecraft.getMinecraft());

        if (this.speedField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            return true;
        }

        if (this.fadeField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            return true;
        }

        if (this.tickField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            return true;
        }

        ActionConfig config = getSelectedConfig();

        if (isInside(
                this.x + 123,
                this.y + 36,
                17,
                17,
                mouseX,
                mouseY))
        {
            return CharacterAnimationSetupController.setParameter(
                    this.selectedKey,
                    this.selectedAction,
                    CharacterAnimationSetupController.PARAM_CLAMP,
                    Boolean.valueOf(!config.clamp)
            );
        }

        if (isInside(
                this.x + 48,
                this.y + 54,
                17,
                17,
                mouseX,
                mouseY))
        {
            return CharacterAnimationSetupController.setParameter(
                    this.selectedKey,
                    this.selectedAction,
                    CharacterAnimationSetupController.PARAM_RESET,
                    Boolean.valueOf(!config.reset)
            );
        }

        return false;
    }

    public boolean keyTyped(char typedChar, int keyCode)
            throws IOException
    {
        if (this.selectedKey == null)
        {
            return false;
        }

        if (this.speedField != null &&
                this.speedField.isFocused() &&
                this.speedField.textboxKeyTyped(
                        typedChar,
                        keyCode))
        {
            applyNumericField(
                    CharacterAnimationSetupController.PARAM_SPEED,
                    this.speedField,
                    false
            );

            return true;
        }

        if (this.fadeField != null &&
                this.fadeField.isFocused() &&
                this.fadeField.textboxKeyTyped(
                        typedChar,
                        keyCode))
        {
            applyNumericField(
                    CharacterAnimationSetupController.PARAM_FADE,
                    this.fadeField,
                    true
            );

            return true;
        }

        if (this.tickField != null &&
                this.tickField.isFocused() &&
                this.tickField.textboxKeyTyped(
                        typedChar,
                        keyCode))
        {
            applyNumericField(
                    CharacterAnimationSetupController.PARAM_TICK,
                    this.tickField,
                    true
            );

            return true;
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
        if (this.speedField != null)
        {
            this.speedField.updateCursorCounter();
        }

        if (this.fadeField != null)
        {
            this.fadeField.updateCursorCounter();
        }

        if (this.tickField != null)
        {
            this.tickField.updateCursorCounter();
        }
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
        if (this.speedField == null)
        {
            return;
        }

        this.speedField.x = this.x + 48;
        this.speedField.y = this.y + 19;
        this.speedField.width = 38;
        this.speedField.height = 16;

        this.fadeField.x = this.x + 123;
        this.fadeField.y = this.y + 19;
        this.fadeField.width = 34;
        this.fadeField.height = 16;

        this.tickField.x = this.x + 48;
        this.tickField.y = this.y + 37;
        this.tickField.width = 38;
        this.tickField.height = 16;
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

        this.speedField.setText(
                formatFloat(config.speed)
        );

        this.fadeField.setText(
                String.valueOf((int) config.fade)
        );

        this.tickField.setText(
                String.valueOf(config.tick)
        );

        this.suppressFieldSync = false;
    }

    private void applyNumericField(
            String parameter,
            GuiTextField field,
            boolean integer)
    {
        if (this.suppressFieldSync ||
                this.selectedKey == null)
        {
            return;
        }

        try
        {
            if (integer)
            {
                int value =
                        Integer.parseInt(
                                field.getText()
                        );

                if (value < 0)
                {
                    value = 0;
                    field.setText("0");
                }

                if (CharacterAnimationSetupController.setParameter(
                        this.selectedKey,
                        this.selectedAction,
                        parameter,
                        Integer.valueOf(value)))
                {
                    this.fieldSignature = "";
                }
            }
            else
            {
                float value =
                        Float.parseFloat(
                                field.getText()
                        );

                if (value < -100F)
                {
                    value = -100F;
                }

                if (value > 100F)
                {
                    value = 100F;
                }

                if (CharacterAnimationSetupController.setParameter(
                        this.selectedKey,
                        this.selectedAction,
                        parameter,
                        Float.valueOf(value)))
                {
                    this.fieldSignature = "";
                }
            }
        }
        catch (NumberFormatException ignored)
        {
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

    private void drawTextFieldFrame(GuiTextField field)
    {
        if (field == null)
        {
            return;
        }

        drawRect(
                field.x - 1,
                field.y - 1,
                field.x + field.width + 1,
                field.y + field.height + 1,
                COLOR_BORDER
        );

        int originalY = field.y;
        field.y = originalY + 2;
        field.drawTextBox();
        field.y = originalY;
    }

    private void drawToggle(
            Minecraft mc,
            int x,
            int y,
            boolean enabled)
    {
        drawRect(
                x,
                y,
                x + 17,
                y + 17,
                enabled
                        ? EditorThemeManager.get().getAccent()
                        : COLOR_PANEL_DARK
        );

        drawRect(
                x,
                y,
                x + 17,
                y + 1,
                COLOR_BORDER
        );

        mc.fontRenderer.drawString(
                enabled ? "✓" : "×",
                x + 4,
                y + 4,
                enabled
                        ? COLOR_TEXT
                        : COLOR_TEXT_MUTED
        );
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

    private String formatFloat(float value)
    {
        if (value == (int) value)
        {
            return String.valueOf((int) value);
        }

        return String.valueOf(value);
    }

    private boolean equals(String a, String b)
    {
        if (a == null)
        {
            return b == null;
        }

        return a.equals(b);
    }
}
