package com.example.examplemod;

import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class EditorSceneViewport
{
    private int x;
    private int y;
    private int width;
    private int height;

    private final AnimationPreview animationPreview;
    private final MinecraftWorldPreview minecraftWorldPreview;
    private final EditorCamera camera;
    private final BlockbusterActorPreviewRenderer actorPreviewRenderer;

    /*
     * ПКМ используется для вращения камеры.
     *
     * ЛКМ специально НЕ используется здесь.
     * Позже он понадобится для выбора актёров
     * и костей мышью.
     */
    private boolean rotatingCamera;

    private int lastMouseX;
    private int lastMouseY;

    private double sceneX;
    private double sceneY;
    private double sceneZ;

    public EditorSceneViewport()
    {
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;

        this.sceneX = 0.0D;
        this.sceneY = 0.0D;
        this.sceneZ = 0.0D;

        this.animationPreview =
                new AnimationPreview();

        this.minecraftWorldPreview =
                new MinecraftWorldPreview();

        this.minecraftWorldPreview.setWorldPosition(
                this.sceneX,
                this.sceneY,
                this.sceneZ
        );

        this.actorPreviewRenderer =
                new BlockbusterActorPreviewRenderer();

        this.camera =
                new EditorCamera();
    }

    public void setBounds(
            int x,
            int y,
            int width,
            int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.animationPreview.setBounds(
                x,
                y,
                width,
                height
        );
    }

    public void draw(
            Minecraft mc,
            ActorPose actorPose,
            List<AnimationBone> bones,
            int currentFrame)
    {
        if (mc == null)
        {
            return;
        }

        /*
         * ==================================================
         * 1. СТАРЫЙ EDITOR PREVIEW
         * ==================================================
         */

        this.animationPreview.draw(
                mc,
                actorPose,
                bones,
                currentFrame,
                this.camera
        );

        /*
         * ==================================================
         * 2. REAL MINECRAFT WORLD
         * ==================================================
         */

        this.minecraftWorldPreview.draw(
                mc,
                this.camera,
                this.x,
                this.y,
                this.width,
                this.height
        );
    }

    /*
     * =========================================================
     * CAMERA API
     * =========================================================
     */

    public void rotateCamera(
            float deltaYaw,
            float deltaPitch)
    {
        this.camera.rotate(
                deltaYaw,
                deltaPitch
        );
    }

    public void zoomCamera(
            double amount)
    {
        this.camera.zoom(
                amount
        );
    }

    public void resetCamera()
    {
        this.camera.reset();
    }

    public void focusCamera(
            double x,
            double y,
            double z)
    {
        this.camera.setTarget(
                x,
                y,
                z
        );
    }

    public EditorCamera getCamera()
    {
        return this.camera;
    }

    /*
     * =========================================================
     * BOUNDS
     * =========================================================
     */

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }

    /*
     * =========================================================
     * CAMERA POSITION
     * =========================================================
     */

    public double getCameraX()
    {
        return this.camera.getCameraX();
    }

    public double getCameraY()
    {
        return this.camera.getCameraY();
    }

    public double getCameraZ()
    {
        return this.camera.getCameraZ();
    }

    /*
     * =========================================================
     * PREVIEWS
     * =========================================================
     */

    public AnimationPreview getAnimationPreview()
    {
        return this.animationPreview;
    }

    public MinecraftWorldPreview getMinecraftWorldPreview()
    {
        return this.minecraftWorldPreview;
    }

    public void drawActor(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            BlockbusterRecordFrame frame)
    {
        if (
                this.actorPreviewRenderer == null ||
                        actorData == null ||
                        frame == null
        )
        {
            return;
        }

        this.actorPreviewRenderer.drawActor(
                mc,
                actorData,
                frame,
                this.camera,
                this.x,
                this.y,
                this.width,
                this.height
        );
    }

    /*
     * =========================================================
     * MOUSE / VIEWPORT
     * =========================================================
     */

    public boolean isInside(
            int mouseX,
            int mouseY)
    {
        return mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height;
    }

    /*
     * =========================================================
     * MOUSE PRESS
     * =========================================================
     *
     * ПКМ = вращение камеры.
     *
     * ЛКМ здесь специально не обрабатывается.
     */

    public boolean mousePressed(
            int mouseX,
            int mouseY,
            int button)
    {
        if (!isInside(
                mouseX,
                mouseY))
        {
            return false;
        }

        /*
         * ПКМ.
         */
        if (button == 1)
        {
            this.rotatingCamera = true;

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            return true;
        }

        /*
         * ЛКМ оставляем свободным
         * для будущего выбора объектов.
         */
        return false;
    }

    /*
     * =========================================================
     * MOUSE DRAG
     * =========================================================
     */

    public boolean mouseDragged(
            int mouseX,
            int mouseY)
    {
        if (!this.rotatingCamera)
        {
            return false;
        }

        int deltaX =
                mouseX -
                        this.lastMouseX;

        int deltaY =
                mouseY -
                        this.lastMouseY;

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        /*
         * Чувствительность вращения.
         */
        float sensitivity = 0.5F;

        this.camera.rotate(
                deltaX * sensitivity,
                -deltaY * sensitivity
        );

        return true;
    }

    /*
     * =========================================================
     * MOUSE RELEASE
     * =========================================================
     */

    public boolean mouseReleased(
            int mouseX,
            int mouseY,
            int button)
    {
        if (button == 1 &&
                this.rotatingCamera)
        {
            this.rotatingCamera = false;

            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * SCROLL
     * =========================================================
     */

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int amount)
    {
        if (!isInside(
                mouseX,
                mouseY))
        {
            return false;
        }

        if (amount > 0)
        {
            this.camera.zoom(
                    -0.75D
            );
        }
        else if (amount < 0)
        {
            this.camera.zoom(
                    0.75D
            );
        }

        return true;
    }

    /*
     * =========================================================
     * WASD CAMERA MOVEMENT
     * =========================================================
     *
     * Метод вызывается из AnimationEditorScreen.updateScreen().
     *
     * Важно:
     * Mouse.getX()/getY() здесь переводятся из
     * физических координат окна в координаты GUI Minecraft.
     */

    public void updateCameraMovement(
            Minecraft mc)
    {
        if (mc == null)
        {
            return;
        }

        /*
         * Если окно Preview не имеет размеров,
         * ничего делать не нужно.
         */
        if (this.width <= 0 ||
                this.height <= 0)
        {
            return;
        }

        ScaledResolution resolution =
                new ScaledResolution(mc);

        int scaledWidth =
                resolution.getScaledWidth();

        int scaledHeight =
                resolution.getScaledHeight();

        /*
         * Mouse.getX() имеет начало координат
         * снизу слева.
         *
         * GUI Minecraft:
         * начало координат сверху слева.
         */
        int mouseX =
                Mouse.getX()
                        * scaledWidth
                        / mc.displayWidth;

        int mouseY =
                scaledHeight -
                        Mouse.getY()
                                * scaledHeight
                                / mc.displayHeight
                        - 1;

        /*
         * WASD работает только внутри Preview.
         */
        if (!this.isInside(
                mouseX,
                mouseY))
        {
            return;
        }

        /*
         * Направление движения.
         */
        boolean forward =
                Keyboard.isKeyDown(
                        Keyboard.KEY_W
                );

        boolean backward =
                Keyboard.isKeyDown(
                        Keyboard.KEY_S
                );

        boolean left =
                Keyboard.isKeyDown(
                        Keyboard.KEY_A
                );

        boolean right =
                Keyboard.isKeyDown(
                        Keyboard.KEY_D
                );

        boolean up =
                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        boolean down =
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        /*
         * Ctrl = ускоренное перемещение.
         */
        boolean fast =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                );

        /*
         * Ничего не нажато.
         */
        if (!forward &&
                !backward &&
                !left &&
                !right &&
                !up &&
                !down)
        {
            return;
        }

        /*
         * Собираем направление.
         */
        double forwardAmount = 0.0D;
        double strafeAmount = 0.0D;
        double verticalAmount = 0.0D;

        if (forward)
        {
            forwardAmount += 1.0D;
        }

        if (backward)
        {
            forwardAmount -= 1.0D;
        }

        if (right)
        {
            strafeAmount += 1.0D;
        }

        if (left)
        {
            strafeAmount -= 1.0D;
        }

        if (up)
        {
            verticalAmount += 1.0D;
        }

        if (down)
        {
            verticalAmount -= 1.0D;
        }

        /*
         * Нормализуем диагональное движение.
         *
         * Например:
         *
         * W
         *
         * и
         *
         * W + D
         *
         * должны иметь одинаковую скорость.
         */
        if (forwardAmount != 0.0D &&
                strafeAmount != 0.0D)
        {
            double length =
                    Math.sqrt(
                            forwardAmount *
                                    forwardAmount
                                    +
                                    strafeAmount *
                                            strafeAmount
                    );

            forwardAmount /=
                    length;

            strafeAmount /=
                    length;
        }

        /*
         * Передаём движение настоящей камере.
         */
        this.camera.move(
                forwardAmount,
                strafeAmount,
                verticalAmount,
                fast
        );
    }

    /*
     * =========================================================
     * SCENE POSITION
     * =========================================================
     */

    public void setScenePosition(
            double x,
            double y,
            double z)
    {
        this.sceneX = x;
        this.sceneY = y;
        this.sceneZ = z;

        this.animationPreview.setWorldPosition(
                x,
                y,
                z
        );

        this.minecraftWorldPreview.setWorldPosition(
                x,
                y,
                z
        );

        /*
         * При загрузке новой сцены
         * камера автоматически смотрит
         * на её начало.
         */
        this.camera.setTarget(
                x,
                y + 1.0D,
                z
        );
    }

    public double getSceneX()
    {
        return this.sceneX;
    }

    public double getSceneY()
    {
        return this.sceneY;
    }

    public double getSceneZ()
    {
        return this.sceneZ;
    }
}