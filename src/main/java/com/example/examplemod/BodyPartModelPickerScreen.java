package com.example.examplemod;

import java.util.function.Consumer;

import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiCreativeMorphsList;
import mchorse.mclib.client.gui.framework.GuiBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Uses the original Metamorph creative morph browser as the
 * model picker for Body Parts.
 *
 * Metamorph does not use a Done button here. The selection is
 * committed when the GUI is closed with Escape.
 */
public class BodyPartModelPickerScreen extends GuiBase
{
    private final AnimationEditorScreen returnScreen;
    private final Consumer<String> callback;

    private GuiCreativeMorphsList picker;

    /**
     * Last Blockbuster CustomMorph selected in the Metamorph browser.
     *
     * It is intentionally NOT committed immediately. The original
     * Metamorph workflow confirms the current state when the GUI
     * is closed with Escape.
     */
    private CustomMorph selectedMorph;

    private boolean committed;

    public BodyPartModelPickerScreen(
            Minecraft mc,
            AnimationEditorScreen returnScreen,
            Consumer<String> callback)
    {
        super();

        this.returnScreen = returnScreen;
        this.callback = callback;

        this.picker = new GuiCreativeMorphsList(
                mc,
                this::selectMorph
        );

        this.picker.flex()
                .relative(this.viewport)
                .wh(1F, 1F);

        this.root.add(this.picker);
    }

    /**
     * Selecting a morph only changes the pending selection.
     * The editor is not closed here.
     */
    private void selectMorph(AbstractMorph morph)
    {
        if (morph instanceof CustomMorph)
        {
            this.selectedMorph =
                    (CustomMorph) morph;
        }
    }

    /**
     * Commit the currently selected model when the user closes
     * the Metamorph GUI with Escape.
     */
    private void commitSelection()
    {
        if (this.committed)
        {
            return;
        }

        this.committed = true;

        if (this.selectedMorph != null &&
                this.callback != null)
        {
            String key =
                    this.selectedMorph.getKey();

            if (key != null && !key.isEmpty())
            {
                this.callback.accept(key);
            }
        }
    }

    @Override
    protected void closeScreen()
    {
        /*
         * Metamorph's creative GUI has no Done button.
         * Escape is the confirmation/close action.
         */
        commitSelection();

        this.mc.displayGuiScreen(
                this.returnScreen
        );
    }

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks)
    {
        Gui.drawRect(
                0,
                0,
                this.width,
                this.height,
                0xFF101010
        );

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );
    }
}
