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
 * Only Blockbuster CustomMorph entries are accepted. The selected
 * CustomMorph's Blockbuster model key is returned to the editor.
 */
public class BodyPartModelPickerScreen extends GuiBase
{
    private final AnimationEditorScreen returnScreen;
    private final Consumer<String> callback;
    private GuiCreativeMorphsList picker;

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

    private void selectMorph(AbstractMorph morph)
    {
        if (morph instanceof CustomMorph)
        {
            CustomMorph custom = (CustomMorph) morph;
            String key = custom.getKey();

            if (key != null && !key.isEmpty())
            {
                if (this.callback != null)
                {
                    this.callback.accept(key);
                }

                this.mc.displayGuiScreen(this.returnScreen);
            }
        }
    }

    @Override
    protected void closeScreen()
    {
        this.mc.displayGuiScreen(this.returnScreen);
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
