package com.example.examplemod;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * In-game WAV browser. Copy WAV files into <minecraft>/config/bbs-editor/audio.
 */
public class EditorWavLoadScreen extends GuiScreen
{
    private static final int VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT = 21;

    private final AnimationEditorScreen parent;
    private final EditorWavAudioController audio;
    private final File audioFolder;
    private File[] files = new File[0];
    private int selectedIndex = -1;
    private int scrollOffset;
    private String message = "";
    private int messageColor = 0xFFCCCCCC;

    public EditorWavLoadScreen(AnimationEditorScreen parent, EditorWavAudioController audio)
    {
        this.parent = parent;
        this.audio = audio;
        this.audioFolder = new File(
                net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                "config/bbs-editor/audio");
    }

    @Override
    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        int left = width / 2 - 165;
        int top = height / 2 - 125;
        buttonList.clear();
        buttonList.add(new GuiButton(1, left, top + 224, 105, 20, "Refresh"));
        buttonList.add(new GuiButton(2, left + 110, top + 224, 105, 20, "Load selected"));
        buttonList.add(new GuiButton(3, left + 220, top + 224, 105, 20, "Back"));
        refreshFiles();
    }

    private void refreshFiles()
    {
        if (!audioFolder.isDirectory() && !audioFolder.mkdirs())
        {
            message = "Could not create audio folder";
            messageColor = 0xFFFF7777;
            files = new File[0];
            selectedIndex = -1;
            return;
        }
        // Copy legacy WAVs once without overwriting existing files.
        File legacy = new File(
                net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                "bbs-animation-editor/audio");
        if (legacy.isDirectory())
        {
            File[] oldFiles = legacy.listFiles();
            if (oldFiles != null)
            {
                for (File oldFile : oldFiles)
                {
                    if (!oldFile.isFile() ||
                            !oldFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".wav"))
                        continue;
                    File destination = new File(audioFolder, oldFile.getName());
                    if (!destination.exists())
                    {
                        try
                        {
                            java.nio.file.Files.copy(oldFile.toPath(), destination.toPath());
                        }
                        catch (IOException ignored) {}
                    }
                }
            }
        }
        File[] listed = audioFolder.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.toLowerCase(java.util.Locale.ROOT).endsWith(".wav")
                        && new File(dir, name).isFile();
            }
        });
        files = listed == null ? new File[0] : listed;
        Arrays.sort(files, new Comparator<File>()
        {
            @Override
            public int compare(File a, File b)
            {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        selectedIndex = files.length > 0 ? 0 : -1;
        scrollOffset = 0;
        message = files.length == 0 ? "Put WAV files in the audio folder, then Refresh"
                : files.length + " WAV file(s) found";
        messageColor = 0xFFCCCCCC;
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
        int left = width / 2 - 165;
        int top = height / 2 - 125;
        drawCenteredString(fontRenderer, "TIMELINE AUDIO - WAV LIBRARY",
                width / 2, top, 0xFFE2E5E7);
        String folder = audioFolder.getAbsolutePath();
        fontRenderer.drawString(fontRenderer.trimStringToWidth(folder, 326),
                left, top + 18, 0xFFAAAAAA);
        drawRect(left, top + 34, left + 330,
                top + 34 + VISIBLE_ROWS * ROW_HEIGHT, 0xCC15191E);
        for (int i = 0; i < VISIBLE_ROWS; i++)
        {
            int index = scrollOffset + i;
            if (index >= files.length) break;
            int y = top + 34 + i * ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= left && mouseX < left + 330 &&
                    mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (selected || hovered)
                drawRect(left + 2, y + 1, left + 328, y + ROW_HEIGHT - 1,
                        selected ? 0xFF24526B : 0xFF30343A);
            fontRenderer.drawString(
                    fontRenderer.trimStringToWidth(files[index].getName(), 310),
                    left + 9, y + 7, selected ? 0xFF8DDCFF : 0xFFE0E4E7);
        }
        drawCenteredString(fontRenderer,
                fontRenderer.trimStringToWidth(message, 328),
                width / 2, top + 207, messageColor);
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
            refreshFiles();
        }
        else if (button.id == 2)
        {
            if (selectedIndex < 0 || selectedIndex >= files.length)
            {
                message = "Select a WAV file";
                messageColor = 0xFFFF7777;
                return;
            }
            try
            {
                audio.load(files[selectedIndex]);
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
        if (button != 0) return;
        int left = width / 2 - 165;
        int y = mouseY - (height / 2 - 125 + 34);
        if (mouseX >= left && mouseX < left + 330 &&
                y >= 0 && y < VISIBLE_ROWS * ROW_HEIGHT)
        {
            int index = scrollOffset + y / ROW_HEIGHT;
            if (index < files.length) selectedIndex = index;
        }
    }

    @Override
    public void handleMouseInput() throws IOException
    {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
        {
            scrollOffset = Math.max(0, Math.min(
                    Math.max(0, files.length - VISIBLE_ROWS),
                    scrollOffset + (wheel < 0 ? 1 : -1)));
        }
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException
    {
        if (key == Keyboard.KEY_ESCAPE)
        {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(character, key);
    }

    @Override
    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
    }
}
