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
 * The Metamorph browser owns the actual selection state.
 * Escape closes this wrapper and commits the currently selected
 * CustomMorph back to the Body Parts controller.
 */
public class BodyPartModelPickerScreen extends GuiBase
{
    private final AnimationEditorScreen returnScreen;
    private final Consumer<String> callback;

    private final GuiCreativeMorphsList picker;

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
                this::onMorphSelected
        );

        this.picker.flex()
                .relative(this.viewport)
                .wh(1F, 1F);

        this.root.add(this.picker);
    }

    /**
     * The original picker already keeps the selected morph.
     * Do not close the screen here.
     */
    private void onMorphSelected(AbstractMorph morph)
    {
        /*
         * Selection is intentionally owned by GuiCreativeMorphsList.
         * We read picker.getSelected() when Escape closes the screen.
         */
    }

    /**
     * Commit the actual current selection from Metamorph.
     */
    private void commitSelection()
    {
        if (this.committed)
        {
            return;
        }

        this.committed = true;

        AbstractMorph morph =
                this.picker.getSelected();

        if (morph instanceof CustomMorph)
        {
            String key =
                    ((CustomMorph) morph).getKey();

            if (key != null &&
                    !key.isEmpty() &&
                    this.callback != null)
            {
                System.out.println(
                        "[BBS Animation Editor] " +
                                "Body Parts selected model: " +
                                key
                );

                this.callback.accept(key);
            }
            else
            {
                System.out.println(
                        "[BBS Animation Editor] " +
                                "Body Parts picker closed without a valid CustomMorph key"
                );
            }
        }
        else
        {
            System.out.println(
                    "[BBS Animation Editor] " +
                            "Body Parts picker closed without a CustomMorph selection"
            );
        }
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws java.io.IOException
    {
        /*
         * Handle Escape before GuiBase dispatches the key to the
         * embedded Metamorph element. This guarantees that the
         * Body Parts wrapper gets a chance to commit the current
         * Metamorph selection.
         */
        if (keyCode == 1)
        {
            commitSelection();

            this.mc.displayGuiScreen(
                    this.returnScreen
            );

            return;
        }

        super.keyTyped(
                typedChar,
                keyCode
        );
    }

    @Override
    protected void closeScreen()
    {
        /*
         * Metamorph's creative browser has no Done button.
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
