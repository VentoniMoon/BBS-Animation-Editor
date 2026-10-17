package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/** Inspector for physics properties of a selected Body Part internal bone. */
public final class BodyPartPhysicsPanel
{
    private int x;
    private int y;
    private String boneName = "";
    private BodyPartPhysicsSettings settings;
    private int chainBones;

    public void setTarget(int x, int y, String boneName, BodyPartPhysicsSettings settings, int chainBones)
    {
        this.x = x;
        this.y = y;
        this.boneName = boneName == null ? "" : boneName;
        this.settings = settings;
        this.chainBones = chainBones;
    }

    public void draw(Minecraft mc, int mouseX, int mouseY)
    {
        if (this.settings == null) return;
        int accent = EditorThemeManager.get().getAccent();
        Gui.drawRect(x, y, x + 175, y + 235, 0xFF252629);
        Gui.drawRect(x, y, x + 175, y + 22, 0xFF303134);
        Gui.drawRect(x, y, x + 3, y + 22, accent);
        mc.fontRenderer.drawString("Physics - Chain", x + 9, y + 7, 0xFFE0E0E0);
        mc.fontRenderer.drawString(trim(mc, this.boneName, 155), x + 9, y + 31, 0xFFAAAAAA);
        drawToggle(mc, "Enable chain root", settings.isEnabled(), 50);
        drawValue(mc, "Gravity", settings.getGravity(), 77);
        drawValue(mc, "Stiffness", settings.getStiffness(), 108);
        drawValue(mc, "Damping", settings.getDamping(), 139);
        drawToggle(mc, "Body collisions", settings.hasBodyCollisions(), 170);
        mc.fontRenderer.drawString("Chain bones: " + chainBones, x + 9, y + 196, 0xFFAAAAAA);
        if (BodyPartJoltPreviewAdapter.isRequested())
            drawToggle(mc, "Show Jolt colliders",
                    BodyPartJoltPreviewAdapter.drawContactsRequested(), 211);
        else
            mc.fontRenderer.drawString("Solver: Verlet preview", x + 9, y + 209, 0xFF999999);
    }

    private void drawValue(Minecraft mc, String title, float value, int lineY)
    {
        mc.fontRenderer.drawString(title, x + 9, y + lineY, 0xFFE0E0E0);
        String number = String.format(java.util.Locale.ROOT, "%.2f", value);
        mc.fontRenderer.drawString(number, x + 101, y + lineY, 0xFFCCCCCC);
        drawButton(mc, "-", x + 136, y + lineY - 4);
        drawButton(mc, "+", x + 155, y + lineY - 4);
    }

    private void drawToggle(Minecraft mc, String label, boolean selected, int lineY)
    {
        mc.fontRenderer.drawString(label, x + 9, y + lineY, 0xFFE0E0E0);
        Gui.drawRect(x + 145, y + lineY - 3, x + 166, y + lineY + 10,
                selected ? 0xFF408060 : 0xFF484848);
        mc.fontRenderer.drawString(selected ? "ON" : "OFF",
                x + (selected ? 148 : 146), y + lineY, 0xFFFFFFFF);
    }

    private void drawButton(Minecraft mc, String text, int left, int top)
    {
        Gui.drawRect(left, top, left + 17, top + 17, 0xFF44474B);
        mc.fontRenderer.drawString(text, left + 6, top + 4, 0xFFFFFFFF);
    }

    private static String trim(Minecraft mc, String value, int maxWidth)
    {
        return mc.fontRenderer.trimStringToWidth(value, maxWidth);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton != 0 || settings == null ||
                mouseX < x || mouseX >= x + 175 ||
                mouseY < y || mouseY >= y + 235) return false;
        int row = mouseY - y;
        if (row >= 47 && row <= 64 && mouseX >= x + 140)
            settings.setEnabled(!settings.isEnabled());
        else if (row >= 73 && row <= 93) change(mouseX, 0);
        else if (row >= 104 && row <= 124) change(mouseX, 1);
        else if (row >= 135 && row <= 155) change(mouseX, 2);
        else if (row >= 167 && row <= 185 && mouseX >= x + 140)
            settings.setBodyCollisions(!settings.hasBodyCollisions());
        else if (row >= 208 && row <= 226 && mouseX >= x + 140 &&
                BodyPartJoltPreviewAdapter.isRequested())
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
        return true;
    }

    private void change(int mouseX, int property)
    {
        float delta;
        if (mouseX >= x + 136 && mouseX < x + 153) delta = -0.05F;
        else if (mouseX >= x + 155 && mouseX < x + 172) delta = 0.05F;
        else return;
        if (property == 0) settings.setGravity(settings.getGravity() + delta);
        else if (property == 1) settings.setStiffness(settings.getStiffness() + delta);
        else settings.setDamping(settings.getDamping() + delta);
    }
}
