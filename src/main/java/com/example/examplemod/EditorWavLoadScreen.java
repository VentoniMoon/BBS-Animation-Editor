package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** WAV import dialog. Supports a native file picker and manual file paths. */
public class EditorWavLoadScreen extends GuiScreen
{
    private final AnimationEditorScreen parent;
    private final EditorWavAudioController audio;
    private GuiTextField path;
    private String message = "";
    private int messageColor = 0xFFCCCCCC;

    public EditorWavLoadScreen(AnimationEditorScreen parent, EditorWavAudioController audio)
    {
        this.parent = parent;
        this.audio = audio;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        int x = width / 2 - 155;
        int y = height / 2 - 65;
        path = new GuiTextField(201, fontRenderer, x, y + 29, 310, 20);
        path.setMaxStringLength(1024);
        buttonList.clear();
        buttonList.add(new GuiButton(1, x, y + 58, 100, 20, "Browse WAV"));
        buttonList.add(new GuiButton(2, x + 105, y + 58, 100, 20, "Load"));
        buttonList.add(new GuiButton(3, x + 210, y + 58, 100, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks)
    {
        drawDefaultBackground();
        int x = width / 2 - 155;
        int y = height / 2 - 65;
        drawCenteredString(fontRenderer, "LOAD WAV - TIMELINE AUDIO",
                width / 2, y, 0xFFE2E5E7);
        fontRenderer.drawString("WAV file path", x, y + 17, 0xFFCCCCCC);
        path.drawTextBox();
        drawCenteredString(fontRenderer, message, width / 2, y + 90, messageColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException
    {
        if (button.id == 3)
        {
            mc.displayGuiScreen(parent);
        }
        else if (button.id == 1)
        {
            try
            {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
                chooser.setAcceptAllFileFilterUsed(false);
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    path.setText(chooser.getSelectedFile().getAbsolutePath());
            }
            catch (Exception e)
            {
                message = "File picker unavailable; paste the path instead";
                messageColor = 0xFFFF7777;
            }
        }
        else if (button.id == 2)
        {
            try
            {
                audio.load(new File(path.getText().trim()));
                message = "Loaded: " + audio.getFileName();
                messageColor = 0xFF66DD99;
                mc.displayGuiScreen(parent);
            }
            catch (Exception e)
            {
                message = e.getMessage() == null ? "Could not load WAV" : e.getMessage();
                messageColor = 0xFFFF7777;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException
    {
        super.mouseClicked(mouseX, mouseY, button);
        path.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException
    {
        if (path.textboxKeyTyped(character, key)) return;
        if (key == Keyboard.KEY_ESCAPE)
        {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    public void updateScreen() { path.updateCursorCounter(); }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
