package com.example.examplemod;

import org.lwjgl.input.Keyboard;

import java.io.IOException;

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
            return true;
        }
        catch (IOException e)
        {
            /*
             * Пока у редактора нет отдельного status/error overlay.
             *
             * Поэтому ошибку не скрываем:
             * она попадёт в консоль Minecraft/Forge.
             */
            e.printStackTrace();
            return false;
        }
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