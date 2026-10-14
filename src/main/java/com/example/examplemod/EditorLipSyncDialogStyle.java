package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/** Shared modal styling for the three Lip Sync dialogs. */
public final class EditorLipSyncDialogStyle
{
    private EditorLipSyncDialogStyle() {}

    public static int accent()
    {
        EditorTheme theme = EditorThemeManager.get();
        return theme == null ? 0xFF66CCFF : theme.getAccent();
    }

    public static void panel(Minecraft mc, int x, int y, int w, int h,
                             String title, String subtitle)
    {
        Gui.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF080B0E);
        Gui.drawRect(x, y, x + w, y + h, 0xFF191D22);
        Gui.drawRect(x, y, x + w, y + 30, 0xFF252A30);
        Gui.drawRect(x, y, x + 3, y + 30, accent());
        Gui.drawRect(x, y + 30, x + w, y + 31, 0xFF394047);
        mc.fontRenderer.drawString(title, x + 13, y + 10, 0xFFE6EBF0);
        if (subtitle != null && !subtitle.isEmpty())
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(subtitle, w - 22),
                    x + 11, y + 39, 0xFF92A3B0);
    }

    public static final class Button extends GuiButton
    {
        public Button(int id, int x, int y, int w, int h, String label)
        {
            super(id, x, y, w, h, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks)
        {
            if (!this.visible) return;
            boolean over = this.enabled && mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int fill = !this.enabled ? 0xFF22272B : over ? 0xFF303D46 : 0xFF262C33;
            Gui.drawRect(this.x, this.y, this.x + this.width,
                    this.y + this.height, 0xFF0B0E12);
            Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1,
                    this.y + this.height - 1, fill);
            Gui.drawRect(this.x + 1, this.y + 1, this.x + this.width - 1,
                    this.y + 2, over ? accent() : 0xFF47535D);
            int color = !this.enabled ? 0xFF77818B : over ? accent() : 0xFFE1E6EB;
            this.drawCenteredString(mc.fontRenderer, this.displayString,
                    this.x + this.width / 2,
                    this.y + (this.height - 8) / 2, color);
        }
    }
}
