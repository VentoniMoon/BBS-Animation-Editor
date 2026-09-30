package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import mchorse.blockbuster_pack.client.gui.GuiCustomMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.mclib.client.gui.framework.GuiBase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;


/**
 * Wrapper над оригинальным Blockbuster GuiCustomMorph.
 *
 * AnimationEditor
 *      |
 *      v
 * PlayerSkinEditorScreen
 *      |
 *      v
 * GuiCustomMorph
 *      |
 *      v
 * CustomMorph
 */
public class PlayerSkinEditorScreen
        extends GuiBase
{
    private static final int BUTTON_DONE = 4101;

    private static final int BUTTON_CANCEL = 4102;


    private final Minecraft minecraft;

    private final CustomMorph customMorph;

    private final BlockbusterCharacterGuiBridge bridge;


    private GuiCustomMorph editor;

    private boolean closing;

    private boolean confirmed;


    /*
     * =========================================================
     * REFLECTION
     * =========================================================
     */

    private Method editorInitGui;

    private Method editorDrawScreen;

    private Method editorUpdateScreen;

    private Method editorMouseClicked;

    private Method editorMouseReleased;

    private Method editorMouseScrolled;

    private Method editorKeyTyped;

    private Method editorActionPerformed;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public PlayerSkinEditorScreen(
            Minecraft mc,
            CustomMorph morph,
            BlockbusterCharacterGuiBridge bridge)
    {
        super();

        this.minecraft = mc;

        this.customMorph = morph;

        this.bridge = bridge;

        this.editor = null;

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

        if (this.customMorph == null)
        {
            return;
        }

        /*
         * Защита от старого бага:
         *
         * GuiCustomMorph.startEdit()
         * требует model != null.
         */
        try
        {
            this.customMorph.updateModel(true);
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }

        if (this.customMorph.model == null)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "PlayerSkinEditorScreen: "
                            + "CustomMorph model is null."
            );

            return;
        }


        /*
         * Создаём настоящий Blockbuster editor.
         */
        this.editor =
                new GuiCustomMorph(
                        this.minecraft
                );


        prepareReflection();


        /*
         * GuiCustomMorph является GuiScreen,
         * поэтому ему нужно передать наше состояние.
         */
        syncEditorScreenState();


        /*
         * Инициализация оригинального GUI.
         */
        invoke(
                this.editorInitGui
        );


        /*
         * Передаём CustomMorph в Blockbuster.
         *
         * В этот момент model уже гарантированно существует.
         */
        this.editor.startEdit(
                this.customMorph
        );


        /*
         * startEdit() мог создать/перестроить панели.
         */
        syncEditorScreenState();


        /*
         * Открываем Materials panel.
         *
         * Это то место, где пользователь фактически
         * редактирует Skin / Materials.
         */
        if (this.editor.materials != null)
        {
            this.editor.setPanel(
                    this.editor.materials
            );
        }


        /*
         * Скрываем оригинальную кнопку Finish,
         * потому что управление закрытием должно
         * оставаться у нашего wrapper.
         */
        hideOriginalFinishButton();


        /*
         * Done.
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


        /*
         * Cancel.
         */
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
    }


    /*
     * =========================================================
     * SCREEN STATE
     * =========================================================
     */

    private void syncEditorScreenState()
    {
        if (this.editor == null)
        {
            return;
        }

        try
        {
            Field width =
                    findField(
                            this.editor.getClass(),
                            "width"
                    );

            if (width != null)
            {
                width.setInt(
                        this.editor,
                        this.width
                );
            }


            Field height =
                    findField(
                            this.editor.getClass(),
                            "height"
                    );

            if (height != null)
            {
                height.setInt(
                        this.editor,
                        this.height
                );
            }


            Field mc =
                    findField(
                            this.editor.getClass(),
                            "mc"
                    );

            if (mc != null)
            {
                mc.set(
                        this.editor,
                        this.minecraft
                );
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * PREPARE REFLECTION
     * =========================================================
     */

    private void prepareReflection()
    {
        if (this.editor == null)
        {
            return;
        }

        Class<?> clazz =
                this.editor.getClass();


        this.editorInitGui =
                findMethod(
                        clazz,
                        "initGui"
                );


        this.editorDrawScreen =
                findMethod(
                        clazz,
                        "drawScreen",
                        int.class,
                        int.class,
                        float.class
                );


        this.editorUpdateScreen =
                findMethod(
                        clazz,
                        "updateScreen"
                );


        this.editorMouseClicked =
                findMethod(
                        clazz,
                        "mouseClicked",
                        int.class,
                        int.class,
                        int.class
                );


        this.editorMouseReleased =
                findMethod(
                        clazz,
                        "mouseReleased",
                        int.class,
                        int.class,
                        int.class
                );


        this.editorMouseScrolled =
                findMethod(
                        clazz,
                        "mouseScrolled",
                        int.class,
                        int.class,
                        int.class
                );


        this.editorKeyTyped =
                findMethod(
                        clazz,
                        "keyTyped",
                        char.class,
                        int.class
                );


        this.editorActionPerformed =
                findMethod(
                        clazz,
                        "actionPerformed",
                        GuiButton.class
                );
    }


    /*
     * =========================================================
     * FIND METHOD
     * =========================================================
     */

    private Method findMethod(
            Class<?> clazz,
            String name,
            Class<?>... parameterTypes)
    {
        Class<?> current =
                clazz;

        while (current != null)
        {
            try
            {
                Method method =
                        current.getDeclaredMethod(
                                name,
                                parameterTypes
                        );

                method.setAccessible(true);

                return method;
            }
            catch (Throwable ignored)
            {
            }

            current =
                    current.getSuperclass();
        }


        /*
         * SRG fallback для Minecraft 1.12.2.
         */
        String srg =
                getSrgName(name);

        if (!name.equals(srg))
        {
            current =
                    clazz;

            while (current != null)
            {
                try
                {
                    Method method =
                            current.getDeclaredMethod(
                                    srg,
                                    parameterTypes
                            );

                    method.setAccessible(true);

                    return method;
                }
                catch (Throwable ignored)
                {
                }

                current =
                        current.getSuperclass();
            }
        }

        return null;
    }


    private String getSrgName(
            String name)
    {
        if ("initGui".equals(name))
        {
            return "func_73866_w_";
        }

        if ("drawScreen".equals(name))
        {
            return "func_73863_a";
        }

        if ("updateScreen".equals(name))
        {
            return "func_73876_c";
        }

        if ("mouseClicked".equals(name))
        {
            return "func_73864_a";
        }

        if ("mouseReleased".equals(name))
        {
            return "func_146286_b";
        }

        if ("mouseScrolled".equals(name))
        {
            return "func_73868_f";
        }

        if ("keyTyped".equals(name))
        {
            return "func_73869_a";
        }

        if ("actionPerformed".equals(name))
        {
            return "func_146284_a";
        }

        return name;
    }


    /*
     * =========================================================
     * FIND FIELD
     * =========================================================
     */

    private Field findField(
            Class<?> clazz,
            String name)
    {
        Class<?> current =
                clazz;

        while (current != null)
        {
            try
            {
                Field field =
                        current.getDeclaredField(
                                name
                        );

                field.setAccessible(true);

                return field;
            }
            catch (Throwable ignored)
            {
            }

            current =
                    current.getSuperclass();
        }

        return null;
    }


    /*
     * =========================================================
     * INVOKE
     * =========================================================
     */

    private Object invoke(
            Method method,
            Object... arguments)
    {
        if (method == null ||
                this.editor == null)
        {
            return null;
        }

        try
        {
            return method.invoke(
                    this.editor,
                    arguments
            );
        }
        catch (Throwable error)
        {
            Throwable cause =
                    error.getCause();

            if (cause != null)
            {
                cause.printStackTrace();
            }
            else
            {
                error.printStackTrace();
            }

            return null;
        }
    }


    /*
     * =========================================================
     * HIDE FINISH
     * =========================================================
     */

    private void hideOriginalFinishButton()
    {
        if (this.editor == null)
        {
            return;
        }

        try
        {
            Field finish =
                    findField(
                            this.editor.getClass(),
                            "finish"
                    );

            if (finish == null)
            {
                return;
            }

            Object value =
                    finish.get(
                            this.editor
                    );

            if (value instanceof GuiButton)
            {
                ((GuiButton) value).visible =
                        false;
            }
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
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
        syncEditorScreenState();

        invoke(
                this.editorDrawScreen,
                mouseX,
                mouseY,
                partialTicks
        );

        /*
         * Наши кнопки поверх Blockbuster GUI.
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
        invoke(
                this.editorUpdateScreen
        );

        super.updateScreen();
    }


    /*
     * =========================================================
     * MOUSE CLICK
     * =========================================================
     */

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws java.io.IOException
    {
        /*
         * Сначала Blockbuster.
         */
        invoke(
                this.editorMouseClicked,
                mouseX,
                mouseY,
                mouseButton
        );

        /*
         * Затем наши кнопки.
         */
        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }


    /*
     * =========================================================
     * MOUSE RELEASE
     * =========================================================
     */

    @Override
    protected void mouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        invoke(
                this.editorMouseReleased,
                mouseX,
                mouseY,
                state
        );

        super.mouseReleased(
                mouseX,
                mouseY,
                state
        );
    }


    /*
     * =========================================================
     * SCROLL
     * =========================================================
     */

    @Override
    protected void mouseScrolled(
            int mouseX,
            int mouseY,
            int amount)
    {
        invoke(
                this.editorMouseScrolled,
                mouseX,
                mouseY,
                amount
        );

        super.mouseScrolled(
                mouseX,
                mouseY,
                amount
        );
    }


    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws java.io.IOException
    {
        /*
         * ESC = Cancel.
         */
        if (keyCode == 1)
        {
            cancelAndReturn();
            return;
        }

        invoke(
                this.editorKeyTyped,
                typedChar,
                keyCode
        );
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


        /*
         * Остальные кнопки принадлежат Blockbuster.
         */
        invoke(
                this.editorActionPerformed,
                button
        );
    }


    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    private void saveAndReturn()
    {
        if (this.closing)
        {
            return;
        }

        this.closing = true;

        this.confirmed = true;


        /*
         * Даём оригинальному GuiCustomMorph завершить
         * собственное редактирование.
         */
        if (this.editor != null)
        {
            Method finishEdit =
                    findMethod(
                            this.editor.getClass(),
                            "finishEdit"
                    );

            invoke(
                    finishEdit
            );
        }


        /*
         * Теперь customMorph содержит изменения.
         */
        if (this.bridge != null)
        {
            this.bridge.syncCustomMorphToSkin(
                    this.customMorph
            );

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
         * finishEdit() НЕ вызываем.
         *
         * customMorph является отдельной копией,
         * поэтому изменения просто выбрасываются.
         */
        if (this.bridge != null)
        {
            this.bridge.returnToEditor();
        }
    }


    /*
     * =========================================================
     * CLOSE
     * =========================================================
     */

    @Override
    protected void closeScreen()
    {
        cancelAndReturn();
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