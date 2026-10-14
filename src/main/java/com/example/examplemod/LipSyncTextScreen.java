package com.example.examplemod;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

/** Small self-contained Text Lip Sync dialog. Audio is a separate future stage. */
public class LipSyncTextScreen extends GuiScreen
{
    private final AnimationEditorScreen parent;
    private final BodyPartModelData model;
    private final int initialFrame;
    private GuiTextField speech;
    private GuiTextField frame;
    private GuiTextField speed;
    private GuiTextField idle;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;

    public LipSyncTextScreen(AnimationEditorScreen parent,
                             BodyPartModelData model, int frame)
    {
        this.parent = parent;
        this.model = model;
        this.initialFrame = frame;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        int left = width / 2 - 155;
        int top = height / 2 - 105;
        speech = new GuiTextField(101, fontRenderer, left, top + 38, 310, 20);
        speech.setMaxStringLength(1024);
        frame = new GuiTextField(102, fontRenderer, left, top + 82, 95, 20);
        frame.setText(String.valueOf(initialFrame));
        speed = new GuiTextField(103, fontRenderer, left + 110, top + 82, 95, 20);
        speed.setText("140");
        idle = new GuiTextField(104, fontRenderer, left, top + 126, 310, 20);
        idle.setMaxStringLength(512);
        if (model != null)
        {
            NBTTagCompound nbt = null;
            CharacterKey state = model.getCharacterStateKeyAt(initialFrame);
            if (state != null && state.hasSkin()) nbt = state.getSkin();
            if (nbt == null && model.getMorphAt(initialFrame) != null)
                nbt = model.getMorphAt(initialFrame).toNBT();
            String path = LipSyncTextureBaker.findTexture(nbt);
            if (path != null) idle.setText(path);
        }
        buttonList.clear();
        buttonList.add(new GuiButton(1, left, top + 155, 150, 20, "Generate"));
        buttonList.add(new GuiButton(2, left + 160, top + 155, 150, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        // Keep the editor scene, timeline and panels visible behind the dialog.
        if (parent != null)
            parent.drawScreen(mouseX, mouseY, partialTicks);
        else
            drawDefaultBackground();
        drawRect(0, 0, width, height, 0x99000000);
        int left = width / 2 - 155;
        int top = height / 2 - 105;
        drawCenteredString(fontRenderer, "TEXT LIP SYNC - BODY PARTS", width / 2,
                top + 5, 0xFFE2E5E7);
        fontRenderer.drawString("Speech (RU / EN)", left, top + 26, 0xFFBFC6CC);
        fontRenderer.drawString("Start frame", left, top + 70, 0xFFBFC6CC);
        fontRenderer.drawString("Words per minute (20-600)", left + 110,
                top + 70, 0xFFBFC6CC);
        fontRenderer.drawString("Idle texture (full resource path)", left,
                top + 114, 0xFFBFC6CC);
        speech.drawTextBox();
        frame.drawTextBox();
        speed.drawTextBox();
        idle.drawTextBox();
        drawCenteredString(fontRenderer, status, width / 2,
                top + 182, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException
    {
        if (button.id == 2)
        {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id != 1) return;
        try
        {
            int start = Integer.parseInt(frame.getText().trim());
            float wpm = Float.parseFloat(speed.getText().trim());
            if (start < 0) throw new IllegalArgumentException("Frame cannot be negative");
            int count = LipSyncTextureBaker.generate(model, speech.getText(),
                    start, wpm, idle.getText());
            if (parent != null) parent.refreshCharacterState();
            status = "Generated " + count + " mouth texture keys";
            statusColor = 0xFF55CC88;
        }
        catch (Exception error)
        {
            status = error.getMessage() == null ? "Generation failed" : error.getMessage();
            statusColor = 0xFFFF7777;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException
    {
        super.mouseClicked(mouseX, mouseY, button);
        speech.mouseClicked(mouseX, mouseY, button);
        frame.mouseClicked(mouseX, mouseY, button);
        speed.mouseClicked(mouseX, mouseY, button);
        idle.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException
    {
        if (speech.textboxKeyTyped(character, key) ||
            frame.textboxKeyTyped(character, key) ||
            speed.textboxKeyTyped(character, key) ||
            idle.textboxKeyTyped(character, key)) return;
        if (key == Keyboard.KEY_ESCAPE)
        {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    public void updateScreen()
    {
        speech.updateCursorCounter();
        frame.updateCursorCounter();
        speed.updateCursorCounter();
        idle.updateCursorCounter();
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }
}
