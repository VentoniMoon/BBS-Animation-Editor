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
    public static final int HEIGHT = 140;

    private static int COLOR_PANEL_DARK()
    {
        return EditorVisualStyle.themedSurface(0.100F);
    }
    private static int COLOR_BORDER()
    {
        return EditorVisualStyle.themedSurface(0.180F);
    }
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
    private CharacterTimelineController selectedTimeline;

    public void setBodyPartTimeline(CharacterTimelineController timeline)
    {
        this.selectedTimeline = timeline;
        this.fieldSignature = "";
    }

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
            /*
             * A different key/action is a different edit target.
             * Drop the old focus so the new key's saved values load.
             */
            if (this.speedField != null)
            {
                this.speedField.setFocused(false);
                this.fadeField.setFocused(false);
                this.tickField.setFocused(false);
            }
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

        drawLabel(mc, "Speed", this.x + 12, this.y + 25);
        drawLabel(mc, "Fade", this.x + 12, this.y + 47);
        drawLabel(mc, "Tick", this.x + 12, this.y + 69);

        drawTextFieldFrame(this.speedField);
        drawTextFieldFrame(this.fadeField);
        drawTextFieldFrame(this.tickField);

        drawLabel(mc, "Clamp", this.x + 12, this.y + 97);
        drawToggle(mc, this.x + 145, this.y + 91, config.clamp);
        drawLabel(mc, "Reset", this.x + 12, this.y + 119);
        drawToggle(mc, this.x + 145, this.y + 113, config.reset);
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

        if (clickStepper(this.speedField,
                CharacterAnimationSetupController.PARAM_SPEED, false, mouseX, mouseY)) return true;
        if (clickStepper(this.fadeField,
                CharacterAnimationSetupController.PARAM_FADE, true, mouseX, mouseY)) return true;
        if (clickStepper(this.tickField,
                CharacterAnimationSetupController.PARAM_TICK, true, mouseX, mouseY)) return true;

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
                this.x + 145,
                this.y + 91,
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
                this.x + 145,
                this.y + 113,
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

        if ((keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) &&
                this.speedField != null)
        {
            int dir = keyCode == Keyboard.KEY_UP ? 1 : -1;
            if (this.speedField.isFocused())
            {
                nudgeField(this.speedField, CharacterAnimationSetupController.PARAM_SPEED,
                        false, dir, 0.03125F);
                return true;
            }
            if (this.fadeField.isFocused())
            {
                nudgeField(this.fadeField, CharacterAnimationSetupController.PARAM_FADE,
                        true, dir, 1F);
                return true;
            }
            if (this.tickField.isFocused())
            {
                nudgeField(this.tickField, CharacterAnimationSetupController.PARAM_TICK,
                        true, dir, 1F);
                return true;
            }
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
        if (this.speedField == null) return;
        GuiTextField[] fields = {speedField, fadeField, tickField};
        for (int i = 0; i < fields.length; i++)
        {
            GuiTextField field = fields[i];
            field.x = x + 92;
            field.y = y + 20 + i * 22;
            field.width = 62;
            field.height = 17;
        }
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

        /*
         * Do not overwrite the field being edited. Partial input such
         * as an empty string, "-" or "0." is normal while typing.
         * Otherwise each successful keystroke resets the signature,
         * and the next draw replaces the user's text immediately.
         */
        if (!this.speedField.isFocused())
        {
            this.speedField.setText(formatFloat(config.speed));
        }

        if (!this.fadeField.isFocused())
        {
            this.fadeField.setText(String.valueOf((int) config.fade));
        }

        if (!this.tickField.isFocused())
        {
            this.tickField.setText(String.valueOf(config.tick));
        }

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
        return this.selectedTimeline != null
                ? CharacterAnimationSetupController.getEffectiveConfig(
                        this.selectedTimeline,
                        this.currentFrame,
                        this.selectedAction
                )
                : CharacterAnimationSetupController.getEffectiveConfig(
                        this.selectedActor,
                        this.currentFrame,
                        this.selectedAction
                );
    }

    private boolean clickStepper(GuiTextField field, String parameter,
            boolean integer, int mouseX, int mouseY)
    {
        if (field == null) return false;
        if (isInside(field.x - 17, field.y, 15, 17, mouseX, mouseY))
        {
            nudgeField(field, parameter, integer, -1, integer ? 1F : 0.0625F);
            return true;
        }
        if (isInside(field.x + field.width + 1, field.y, 15, 17, mouseX, mouseY))
        {
            nudgeField(field, parameter, integer, 1, integer ? 1F : 0.0625F);
            return true;
        }
        return false;
    }

    private void nudgeField(GuiTextField field, String parameter,
            boolean integer, int direction, float step)
    {
        float previous;
        try { previous = Float.parseFloat(field.getText()); }
        catch (NumberFormatException ignored) { previous = 0F; }
        float next = previous + direction * step;
        if (integer) field.setText(Integer.toString(Math.max(0, Math.round(next))));
        else field.setText(String.format(java.util.Locale.US, "%.5f", next));
        applyNumericField(parameter, field, integer);
    }

    private void drawTextFieldFrame(GuiTextField field)
    {
        if (field == null) return;
        int accent = EditorThemeManager.get().getAccent();
        int arrowBg = EditorVisualStyle.themedSurface(0.28F);
        drawRect(field.x - 17, field.y, field.x - 2, field.y + 17, arrowBg);
        drawRect(field.x + field.width + 1, field.y,
                field.x + field.width + 16, field.y + 17, arrowBg);
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRenderer.drawString("<", field.x - 12, field.y + 4, 0xFFE6F0F3);
        mc.fontRenderer.drawString(">", field.x + field.width + 6,
                field.y + 4, 0xFFE6F0F3);
        drawRect(field.x - 1, field.y - 1, field.x + field.width + 1,
                field.y + field.height + 1, field.isFocused() ? accent : COLOR_BORDER());
        drawRect(field.x, field.y, field.x + field.width, field.y + field.height,
                EditorVisualStyle.themedSurface(0.15F));
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
                        : COLOR_PANEL_DARK()
        );

        drawRect(
                x,
                y,
                x + 17,
                y + 1,
                COLOR_BORDER()
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
