package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyHandler
{
    public static final KeyBinding OPEN_EDITOR = new KeyBinding(
            "key.bbsanimationeditor.open",
            Keyboard.KEY_O,
            "key.categories.bbsanimationeditor"
    );

    public KeyHandler()
    {
        ClientRegistry.registerKeyBinding(OPEN_EDITOR);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event)
    {
        if (OPEN_EDITOR.isPressed())
        {
            Minecraft.getMinecraft().displayGuiScreen(
                    new AnimationEditorScreen()
            );
        }
    }
}