package com.example.examplemod;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;


/**
 * Контейнер для оригинального редактора Morph.
 *
 * Здесь НЕ создается собственный редактор.
 *
 * Мы берем настоящий GuiAbstractMorph, созданный
 * Blockbuster / Metamorph / Emoticons, и встраиваем
 * его непосредственно в дерево GuiBase.
 *
 *
 * Character Editor
 *       |
 *       v
 * PlayerSkinEditorScreen
 *       |
 *       +-- GuiAbstractMorph
 *               |
 *               +-- GuiCustomMorph
 *               |
 *               +-- GuiAnimatedMorph
 *               |
 *               +-- GuiEmoticonsMorph
 *
 *
 * Благодаря этому оригинальный MCLib GUI получает
 * нормальные resize / draw / mouse / keyboard события.
 */
public class PlayerSkinEditorScreen extends GuiBase
{
    private static final int BUTTON_DONE = 4101;
    private static final int BUTTON_CANCEL = 4102;


    private final Minecraft minecraft;
    private final AbstractMorph sourceMorph;
    private final BlockbusterCharacterGuiBridge bridge;


    /**
     * Реальный оригинальный редактор Morph.
     */
    private GuiAbstractMorph editor;


    /**
     * Delegate, через который оригинальный редактор
     * подключается к дереву GuiBase.
     */
    private GuiDelegateElement<GuiAbstractMorph> editorContainer;


    private boolean closing;
    private boolean confirmed;


    public PlayerSkinEditorScreen(
            Minecraft mc,
            AbstractMorph morph,
            BlockbusterCharacterGuiBridge bridge)
    {
        super();

        this.minecraft = mc;
        this.sourceMorph = morph;
        this.bridge = bridge;

        this.editor = null;
        this.editorContainer = null;

        this.closing = false;
        this.confirmed = false;
    }


    /*
     * =========================================================
     * INIT
     * =========================================================
     */

    @Override
    public void initGui()
    {
        super.initGui();


        if (this.sourceMorph == null)
        {
            return;
        }


        /*
         * =====================================================
         * Создаем ОРИГИНАЛЬНЫЙ редактор для данного Morph.
         *
         * Важно:
         *
         * GuiCustomMorph / GuiAnimatedMorph являются
         * наследниками GuiAbstractMorph.
         *
         * Здесь используется конкретный редактор,
         * который уже был выбран Bridge.
         * =====================================================
         */

        this.editor =
                createEditor(
                        this.sourceMorph
                );


        if (this.editor == null)
        {
            System.err.println(
                    "[BBS Animation Editor] " +
                            "Cannot create Morph editor for: " +
                            this.sourceMorph.getClass().getName()
            );

            return;
        }


        /*
         * =====================================================
         * Передаем Morph оригинальному редактору.
         * =====================================================
         */

        startEditor();


        /*
         * =====================================================
         * Подключаем GuiAbstractMorph к дереву GuiBase.
         *
         * Это КЛЮЧЕВОЙ момент.
         *
         * GuiDelegateElement сам передает:
         *
         * - resize
         * - draw
         * - mouse
         * - keyboard
         * - текущую панель
         * =====================================================
         */

        this.editorContainer =
                new GuiDelegateElement<GuiAbstractMorph>(
                        this.minecraft,
                        this.editor
                );


        this.editorContainer
                .flex()
                .relative(this.viewport)
                .wh(1F, 1F);


        this.root.add(
                this.editorContainer
        );


        /*
         * =====================================================
         * Оригинальный редактор должен занимать весь экран.
         * =====================================================
         */

        this.editorContainer.resize();


        /*
         * =====================================================
         * Добавляем наши кнопки Done / Cancel.
         *
         * Они являются обычными Minecraft GuiButton,
         * поэтому не вмешиваются в оригинальный MCLib GUI.
         * =====================================================
         */

        this.buttonList.add(
                new GuiButton(
                        BUTTON_DONE,
                        this.width - 150,
                        this.height - 28,
                        70,
                        20,
                        I18n.format("gui.done")
                )
        );


        this.buttonList.add(
                new GuiButton(
                        BUTTON_CANCEL,
                        this.width - 75,
                        this.height - 28,
                        70,
                        20,
                        I18n.format("gui.cancel")
                )
        );


        /*
         * =====================================================
         * Если это GuiCustomMorph — открываем Materials.
         *
         * Для AnimatedMorph здесь оставляем панель,
         * которую выбрал сам оригинальный редактор.
         *
         * Это важно: AnimatedMorph НЕ должен превращаться
         * в CustomMorph.
         * =====================================================
         */

        selectInitialPanel();
    }


    /*
     * =========================================================
     * CREATE EDITOR
     * =========================================================
     */

    private GuiAbstractMorph createEditor(
            AbstractMorph morph)
    {
        if (morph == null)
        {
            return null;
        }


        /*
         * -----------------------------------------------------
         * CustomMorph
         * -----------------------------------------------------
         *
         * Для Blockbuster-модели нужен настоящий
         * GuiCustomMorph.
         */

        if (morph instanceof
                mchorse.blockbuster_pack.morphs.CustomMorph)
        {
            return new
                    mchorse.blockbuster_pack.client.gui.GuiCustomMorph(
                    this.minecraft
            );
        }


        /*
         * -----------------------------------------------------
         * AnimatedMorph
         * -----------------------------------------------------
         *
         * Не создаем CustomMorph.
         *
         * Здесь нам нужен зарегистрированный
         * Emoticons editor.
         *
         * В нормальной установке Emoticons этот класс
         * доступен как наследник GuiAbstractMorph.
         *
         * Создание через reflection позволяет не привязывать
         * Character Editor к конкретному имени реализации.
         */

        GuiAbstractMorph animated =
                createAnimatedEditor(
                        morph
                );


        if (animated != null)
        {
            return animated;
        }


        /*
         * -----------------------------------------------------
         * Fallback
         * -----------------------------------------------------
         *
         * Если конкретный Animated editor недоступен,
         * создаем стандартный GuiAbstractMorph.
         *
         * Это не преобразует Morph.
         */

        return new GuiAbstractMorph(
                this.minecraft
        );
    }


    /*
     * =========================================================
     * ANIMATED EDITOR
     * =========================================================
     */

    private GuiAbstractMorph createAnimatedEditor(
            AbstractMorph morph)
    {
        if (morph == null)
        {
            return null;
        }


        String[] classNames =
                {
                        "mchorse.emoticons.skin_n_bones.api.metamorph.editor.GuiEmoticonsMorph",
                        "mchorse.emoticons.skin_n_bones.api.metamorph.editor.GuiAnimatedMorph"
                };


        for (String className : classNames)
        {
            try
            {
                Class<?> clazz =
                        Class.forName(
                                className
                        );


                Object instance =
                        clazz
                                .getConstructor(
                                        Minecraft.class
                                )
                                .newInstance(
                                        this.minecraft
                                );


                if (instance instanceof GuiAbstractMorph)
                {
                    GuiAbstractMorph result =
                            (GuiAbstractMorph)instance;


                    if (result.canEdit(morph))
                    {
                        return result;
                    }
                }
            }
            catch(Throwable error)
            {
                /*
                 * Этот editor может отсутствовать
                 * в конкретной сборке.
                 *
                 * Переходим к следующему.
                 */
            }
        }


        return null;
    }


    /*
     * =========================================================
     * START EDIT
     * =========================================================
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void startEditor()
    {
        if (this.editor == null ||
                this.sourceMorph == null)
        {
            return;
        }


        try
        {
            /*
             * Передаем ТОТ ЖЕ Morph в оригинальный editor.
             *
             * Никакого копирования здесь нет.
             */

            this.editor.startEdit(
                    this.sourceMorph
            );
        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * INITIAL PANEL
     * =========================================================
     */

    private void selectInitialPanel()
    {
        if (this.editor == null)
        {
            return;
        }


        /*
         * CustomMorph:
         *
         * Edit -> Materials
         */

        if (this.editor instanceof
                mchorse.blockbuster_pack.client.gui.GuiCustomMorph)
        {
            mchorse.blockbuster_pack.client.gui.GuiCustomMorph custom =
                    (mchorse.blockbuster_pack.client.gui.GuiCustomMorph)
                            this.editor;


            if (custom.materials != null)
            {
                custom.setPanel(
                        custom.materials
                );
            }


            return;
        }


        /*
         * AnimatedMorph:
         *
         * Edit -> Meshes
         */

        try
        {
            java.lang.reflect.Field meshes =
                    findField(
                            this.editor.getClass(),
                            "meshes"
                    );


            if (meshes != null)
            {
                Object value =
                        meshes.get(
                                this.editor
                        );


                if (value instanceof
                        mchorse.metamorph.client.gui.editor.GuiMorphPanel)
                {
                    this.editor.setPanel(
                            (mchorse.metamorph.client.gui.editor.GuiMorphPanel)
                                    value
                    );
                }
            }
        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * FIELD SEARCH
     * =========================================================
     */

    private java.lang.reflect.Field findField(
            Class<?> clazz,
            String name)
    {
        Class<?> current = clazz;


        while(current != null)
        {
            try
            {
                java.lang.reflect.Field field =
                        current.getDeclaredField(
                                name
                        );


                field.setAccessible(
                        true
                );


                return field;
            }
            catch(Throwable ignored)
            {
            }


            current =
                    current.getSuperclass();
        }


        return null;
    }


    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks)
    {
        /*
         * GuiBase сам рисует root.
         *
         * Нам НЕ нужно вручную вызывать
         * editor.drawScreen().
         */

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );
    }


    /*
     * =========================================================
     * UPDATE
     * =========================================================
     */

    @Override
    public void updateScreen()
    {
        super.updateScreen();
    }


    /*
     * =========================================================
     * BUTTONS
     * =========================================================
     */

    @Override
    protected void actionPerformed(
            GuiButton button)
            throws java.io.IOException
    {
        if (button == null)
        {
            return;
        }


        if (button.id == BUTTON_DONE)
        {
            saveAndReturn();

            return;
        }


        if (button.id == BUTTON_CANCEL)
        {
            cancelAndReturn();

            return;
        }


        super.actionPerformed(
                button
        );
    }


    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void saveAndReturn()
    {
        if (this.closing)
        {
            return;
        }


        this.closing = true;
        this.confirmed = true;


        try
        {
            if (this.editor != null)
            {
                /*
                 * Даем оригинальному editor завершить
                 * редактирование текущей панели.
                 *
                 * Для AnimatedMorph это особенно важно:
                 * GuiAnimatedMorph.finishEdit() обновляет
                 * userConfigData.
                 */

                this.editor.finishEdit();
            }


            AbstractMorph result =
                    this.editor != null &&
                            this.editor.morph != null
                            ?
                            this.editor.morph
                            :
                            this.sourceMorph;


            if (result != null &&
                    this.bridge != null)
            {
                this.bridge.syncMorphEditorResult(
                        result
                );
            }
        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }


        if (this.bridge != null)
        {
            this.bridge.returnToEditor();
        }
    }


    /*
     * =========================================================
     * CANCEL
     * =========================================================
     */

    private void cancelAndReturn()
    {
        if (this.closing)
        {
            return;
        }


        this.closing = true;
        this.confirmed = false;


        /*
         * Ничего не записываем в CharacterKey.
         *
         * В дальнейшем при необходимости можно добавить
         * полноценный copy/rollback, но сейчас Bridge
         * передает отдельный Morph из NBT CharacterKey,
         * поэтому исходный CharacterKey не изменяется
         * напрямую.
         */

        if (this.bridge != null)
        {
            this.bridge.returnToEditor();
        }
    }


    /*
     * =========================================================
     * ESC / CLOSE
     * =========================================================
     */

    @Override
    protected void closeScreen()
    {
        /*
         * В оригинальном Morph Editor изменения должны
         * сохраняться при выходе из редактора через ESC.
         *
         * GuiAbstractMorph / конкретный Emoticons editor
         * уже содержит изменяемый Morph.
         *
         * Поэтому ESC рассматриваем как подтверждение
         * редактирования.
         */
        saveAndReturn();
    }


    /*
     * =========================================================
     * PAUSE
     * =========================================================
     */

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }


    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    public boolean wasConfirmed()
    {
        return this.confirmed;
    }
}