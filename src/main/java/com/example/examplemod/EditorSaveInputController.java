package com.example.examplemod;

import org.lwjgl.input.Keyboard;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

/**
 * Обработка клавиатурного ввода, связанного с сохранением редактора.
 *
 * Здесь намеренно нет GUI и отрисовки.
 *
 * Горячие клавиши:
 * - Ctrl + S — сохранить весь редактор.
 *
 * Состояние dirty хранится в EditorSaveController.
 */
public class EditorSaveInputController
{
    private final EditorSaveController saveController;

    public EditorSaveInputController(EditorSceneState sceneState)
    {
        if (sceneState == null)
        {
            this.saveController = null;
        }
        else
        {
            this.saveController = sceneState.getSaveController();
        }
    }

    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    public boolean isDirty()
    {
        return this.saveController != null
                && this.saveController.isDirty();
    }

    public void markDirty()
    {
        if (this.saveController != null)
        {
            this.saveController.markDirty();
        }
    }

    public void markClean()
    {
        if (this.saveController != null)
        {
            this.saveController.markClean();
        }
    }

    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    public boolean save()
    {
        if (this.saveController == null)
        {
            return false;
        }

        try
        {
            this.saveController.save();
            notifyPlayer("[BBS Editor] Scene, records and animation keys saved.");
            return true;
        }
        catch (Exception e)
        {
            /*
             * Пока у редактора нет отдельного status/error overlay.
             *
             * Поэтому ошибку не скрываем:
             * она попадёт в консоль Minecraft/Forge.
             */
            e.printStackTrace();
            notifyPlayer("[BBS Editor] Save ERROR: " + e.getMessage()
                    + " (check latest.log; editor keys may already be saved)");
            return false;
        }
    }

    private static void notifyPlayer(String message)
    {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.player != null)
        {
            minecraft.player.sendMessage(new TextComponentString(message));
        }
        System.out.println(message);
    }

    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

    public boolean handleKeyTyped(
            char typedChar,
            int keyCode)
    {
        if (keyCode != Keyboard.KEY_S)
        {
            return false;
        }

        boolean controlDown =
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                        || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (!controlDown)
        {
            return false;
        }

        /*
         * Ctrl + S.
         *
         * Возвращаем true даже если сохранение завершилось
         * ошибкой, чтобы AnimationEditorInput не передал
         * Ctrl + S дальше другим обработчикам.
         */
        this.save();

        return true;
    }
}