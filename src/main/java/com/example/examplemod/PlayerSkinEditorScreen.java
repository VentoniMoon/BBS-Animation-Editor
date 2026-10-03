package com.example.examplemod;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import net.minecraft.client.Minecraft;

/**
 * Container for the original Metamorph Morph editor.
 *
 * Metamorph does not provide a separate Done/Cancel workflow
 * here. Escape closes the editor and commits the current changes.
 */
public class PlayerSkinEditorScreen extends GuiBase
{
    private final Minecraft minecraft;
    private final AbstractMorph sourceMorph;
    private final BlockbusterCharacterGuiBridge bridge;

    private GuiAbstractMorph editor;
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

    @Override
    public void initGui()
    {
        super.initGui();

        if (this.sourceMorph == null)
        {
            return;
        }

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

        startEditor();

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

        this.editorContainer.resize();

        selectInitialPanel();
    }

    private GuiAbstractMorph createEditor(
            AbstractMorph morph)
    {
        if (morph == null)
        {
            return null;
        }

        if (morph instanceof
                mchorse.blockbuster_pack.morphs.CustomMorph)
        {
            return new
                    mchorse.blockbuster_pack.client.gui.GuiCustomMorph(
                    this.minecraft
            );
        }

        GuiAbstractMorph animated =
                createAnimatedEditor(
                        morph
                );

        if (animated != null)
        {
            return animated;
        }

        return new GuiAbstractMorph(
                this.minecraft
        );
    }

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
                            (GuiAbstractMorph) instance;

                    if (result.canEdit(morph))
                    {
                        return result;
                    }
                }
            }
            catch(Throwable error)
            {
                /*
                 * Optional editor is not available.
                 */
            }
        }

        return null;
    }

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
            this.editor.startEdit(
                    this.sourceMorph
            );
        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }
    }

    private void selectInitialPanel()
    {
        if (this.editor == null)
        {
            return;
        }

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

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks)
    {
        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );
    }

    @Override
    public void updateScreen()
    {
        super.updateScreen();
    }

    /**
     * Commits the current Morph editor state.
     *
     * The original Metamorph editor is finished first, then
     * the resulting Morph is written back through the bridge.
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

    /**
     * Escape is the Metamorph confirmation/close action.
     */
    @Override
    protected void closeScreen()
    {
        saveAndReturn();
    }

    @Override
    public boolean doesGuiPauseGame()
    {
        return false;
    }

    public boolean wasConfirmed()
    {
        return this.confirmed;
    }
}
