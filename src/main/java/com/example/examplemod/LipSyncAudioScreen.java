package com.example.examplemod;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Keyboard;

/** Audio-to-Keys runs recognition off-thread; mutations stay on Minecraft GUI thread. */
public class LipSyncAudioScreen extends GuiScreen
{
    private final AnimationEditorScreen parent;
    private final BodyPartModelData model;
    private final EditorWavAudioController audio;
    private final int initialFrame;
    private GuiTextField frame, idle, python;
    private boolean boneMode;
    private volatile boolean running;
    private volatile List<EditorVoskRecognizer.Word> recognized;
    private volatile String failure;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;

    public LipSyncAudioScreen(AnimationEditorScreen parent, BodyPartModelData model,
                              EditorWavAudioController audio, int initialFrame)
    {
        this.parent = parent; this.model = model;
        this.audio = audio; this.initialFrame = initialFrame;
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        int x = width / 2 - 155, y = height / 2 - 100;
        frame = new GuiTextField(301, fontRenderer, x, y + 51, 90, 20);
        frame.setText(String.valueOf(initialFrame));
        python = new GuiTextField(302, fontRenderer, x + 110, y + 51, 200, 20);
        python.setText("python");
        idle = new GuiTextField(303, fontRenderer, x, y + 104, 310, 20);
        idle.setMaxStringLength(512);
        if (model != null)
        {
            NBTTagCompound nbt = null;
            CharacterKey key = model.getCharacterStateKeyAt(initialFrame);
            if (key != null && key.hasSkin()) nbt = key.getSkin();
            if (nbt == null && model.getMorphAt(initialFrame) != null)
                nbt = model.getMorphAt(initialFrame).toNBT();
            String detected = LipSyncTextureBaker.findTexture(nbt);
            if (detected != null) idle.setText(detected);
        }
        buttonList.clear();
        buttonList.add(new EditorLipSyncDialogStyle.Button(1, x, y + 139, 150, 20, "Analyze + Generate"));
        buttonList.add(new EditorLipSyncDialogStyle.Button(2, x + 160, y + 139, 150, 20, "Back"));
        buttonList.add(new EditorLipSyncDialogStyle.Button(3, x, y + 201, 310, 20, "Output: TEXTURE (Skin keys)"));
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
        int x = width / 2 - 155, y = height / 2 - 100;
        EditorLipSyncDialogStyle.panel(mc, x - 14, y - 29, 338, 281,
                "AUDIO TO KEYS", "VOSK / speech recognition");
        fontRenderer.drawString(audio == null ? "No audio" :
                fontRenderer.trimStringToWidth(audio.getFileName(), 300),
                x, y + 26, 0xFF9AB6C5);
        fontRenderer.drawString("Start frame", x, y + 40, 0xFFCCCCCC);
        fontRenderer.drawString("Python executable", x + 110, y + 40, 0xFFCCCCCC);
        fontRenderer.drawString("Idle texture (full resource path)", x,
                y + 92, 0xFFCCCCCC);
        frame.drawTextBox(); python.drawTextBox(); idle.drawTextBox();
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(status, 300),
                width / 2, y + 174, statusColor);
        for (GuiButton button : buttonList)
            button.enabled = button.id == 2 || !running;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException
    {
        if (button.id == 2) { mc.displayGuiScreen(parent); return; }
        if (button.id == 3)
        {
            boneMode = !boneMode;
            button.displayString = boneMode
                    ? "Output: CHAMELEON (Character keys)"
                    : "Output: TEXTURE (Skin keys)";
            return;
        }
        if (button.id != 1 || running) return;
        if (audio == null || !audio.isLoaded())
        {
            status = "Load WAV in LIP SYNC first"; statusColor = 0xFFFF7777; return;
        }
        final int start;
        try
        {
            start = Integer.parseInt(frame.getText().trim());
            if (start < 0) throw new NumberFormatException();
        }
        catch (NumberFormatException ex)
        {
            status = "Invalid start frame"; statusColor = 0xFFFF7777; return;
        }
        final String idlePath = idle.getText();
        final String interpreter = python.getText();
        final java.io.File wav = audio.getLoadedFile();
        running = true;
        recognized = null; failure = null;
        status = "Analyzing WAV with offline Vosk...";
        statusColor = 0xFFCCCCCC;
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    recognized = EditorVoskRecognizer.recognize(wav, interpreter);
                }
                catch (Exception e)
                {
                    failure = e.getMessage() == null ? "Recognition failed" : e.getMessage();
                }
            }
        }, "BBS-Vosk-Recognition").start();
    }

    @Override
    public void updateScreen()
    {
        frame.updateCursorCounter(); python.updateCursorCounter(); idle.updateCursorCounter();
        if (!running) return;
        if (failure != null)
        {
            status = failure;
            statusColor = 0xFFFF7777;
            failure = null; running = false;
        }
        else if (recognized != null)
        {
            List<EditorVoskRecognizer.Word> words = recognized;
            recognized = null; running = false;
            if (words.isEmpty())
            {
                status = "Vosk did not recognize any words"; statusColor = 0xFFFF7777;
                return;
            }
            try
            {
                int start = Integer.parseInt(frame.getText().trim());
                List<LipSyncTimeline.Key> visemes = boneMode
                        ? EditorVoskRecognizer.toCharacterVisemes(words, start)
                        : EditorVoskRecognizer.toVisemes(words, start);
                int count = boneMode ? LipSyncCharacterBaker.generate(model, visemes)
                        : LipSyncTextureBaker.generateTimed(model, visemes,
                                start, idle.getText());
                parent.refreshCharacterState();
                status = "Recognized " + words.size() + " words, created " + count + " keys";
                statusColor = 0xFF66DD99;
            }
            catch (Exception e)
            {
                status = e.getMessage() == null ? "Key generation failed" : e.getMessage();
                statusColor = 0xFFFF7777;
            }
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException
    {
        super.mouseClicked(x, y, button);
        frame.mouseClicked(x, y, button);
        python.mouseClicked(x, y, button);
        idle.mouseClicked(x, y, button);
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException
    {
        if (frame.textboxKeyTyped(character, key) ||
                python.textboxKeyTyped(character, key) ||
                idle.textboxKeyTyped(character, key)) return;
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        super.keyTyped(character, key);
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
