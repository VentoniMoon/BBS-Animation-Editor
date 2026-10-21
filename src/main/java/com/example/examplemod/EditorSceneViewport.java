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
    private final PreviewRenderBackend renderBackend;
    private final PreviewWeatherRenderer weatherRenderer;

    private boolean rotatingCamera;

    private int lastMouseX;
    private int lastMouseY;

    private double sceneX;
    private double sceneY;
    private double sceneZ;

    private boolean worldAfterActorRendered;
    private boolean weatherRendered;
    /** True when native Minecraft rendered this preview frame. */
    private boolean nativeWorldRendered;
    /** Frame sampled by the actor before the final preview FBO overlay. */
    private int lastJoltOverlayFrame = -1;
    private double joltActorX, joltActorY, joltActorZ;
    private boolean hasJoltActorOrigin;


    public EditorSceneViewport()
    {
        this.x = 0;
        this.y = 0;
        this.width = 0;
        this.height = 0;

        this.sceneX = 0.0D;
        this.sceneY = 0.0D;
        this.sceneZ = 0.0D;

        this.worldAfterActorRendered = false;
        this.weatherRendered = false;
        this.nativeWorldRendered = false;

        this.renderBackend =
                new PreviewRenderBackend(
                        Minecraft.getMinecraft()
                );

        this.animationPreview =
                new AnimationPreview();

        this.minecraftWorldPreview =
                new MinecraftWorldPreview();

        this.minecraftWorldPreview.clearScene();

        this.minecraftWorldPreview.setWorldPosition(
                this.sceneX,
                this.sceneY,
                this.sceneZ
        );

        this.actorPreviewRenderer =
                new BlockbusterActorPreviewRenderer();

        this.camera =
                new EditorCamera();

        this.weatherRenderer =
                new PreviewWeatherRenderer(
                        Minecraft.getMinecraft()
                );
    }


    public void setBodyPartsController(
            BodyPartsEditorController controller)
    {
        this.actorPreviewRenderer.setBodyPartsController(
                controller
        );
    }

    public mchorse.metamorph.api.morphs.AbstractMorph getPreparedBodyPartMorph(
            BodyPartModelData data)
    {
        return this.actorPreviewRenderer == null
                ? null
                : this.actorPreviewRenderer.getPreparedBodyPartMorph(data);
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
        this.lastJoltOverlayFrame = currentFrame;
        this.hasJoltActorOrigin = false;
        if (mc == null)
        {
            return;
        }

        if (this.width <= 0 ||
                this.height <= 0)
        {
            return;
        }


        /*
         * =========================================================
         * PREVIEW BOUNDS
         * =========================================================
         */

        this.renderBackend.setPreviewBounds(
                this.x,
                this.y,
                this.width,
                this.height
        );


        /*
         * =========================================================
         * BEGIN PREVIEW FBO
         * =========================================================
         *
         * ВАЖНО:
         *
         * Здесь больше НЕТ никаких вызовов OptiFine.
         *
         * Shader pipeline временно полностью исключён из
         * EditorSceneViewport. Мы вернём его после завершения
         * основной архитектуры Preview.
         */

        this.renderBackend.beginPreviewRender();


        this.worldAfterActorRendered = false;
        this.weatherRendered = false;


        /*
         * =========================================================
         * WORLD FIRST PASS
         * =========================================================
         *
         * MinecraftWorldPreview сам подготавливает:
         *
         * - EditorCamera
         * - projection
         * - modelview
         * - sky
         * - fog
         * - terrain
         * - lighting
         *
         * Никаких дополнительных camera/shader операций здесь
         * больше не выполняем.
         */

        /*
         * BBS FS architecture: camera already drove Minecraft's real world
         * pass at RenderTick START. Copy the finished frame into our preview
         * texture; never render another sky/chunk/shader pipeline here.
         *
         * Legacy world stages are deliberately disabled, even on failure:
         * an empty preview is preferable to returning to the old broken
         * world renderer without noticing.
         */
        BbsFsWorldCameraDriver.attach(this);
        this.nativeWorldRendered = true;
        BbsFsWorldFrameCapture.copyWorld(
                mc, this.renderBackend, this.x, this.y,
                this.width, this.height);


        /* Clouds render only after actors, translucent world and weather. */
    }


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

    /** Camera-track playback must not move the actor or change Jolt time. */
    public void setCameraMode(EditorCamera.Mode mode)
    {
        this.camera.setMode(mode);
        if(mode==EditorCamera.Mode.TRACK)this.rotatingCamera=false;
    }

    public void applyCameraTrack(PreviewCameraPose pose)
    {
        if(pose==null)return;
        this.camera.setMode(EditorCamera.Mode.TRACK);
        this.rotatingCamera=false;
        this.camera.apply(pose);
    }

    public void releaseCameraTrack()
    {
        this.camera.setMode(EditorCamera.Mode.ORBIT);
    }


    public PreviewRenderBackend getRenderBackend()
    {
        return this.renderBackend;
    }


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
        if (mc == null)
        {
            return;
        }

        if (this.actorPreviewRenderer == null)
        {
            return;
        }

        if (actorData == null)
        {
            return;
        }

        if (frame == null)
        {
            return;
        }


        this.joltActorX = frame.getX();
        this.joltActorY = frame.getY();
        this.joltActorZ = frame.getZ();
        this.hasJoltActorOrigin = true;

        /*
         * =========================================================
         * ACTOR
         * =========================================================
         *
         * OptiFine entity/shader pass больше здесь НЕ запускается.
         *
         * Актёр просто рисуется поверх первого прохода мира.
         */

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


        /*
         * После актёра дорисовываем прозрачные/поздние
         * слои мира.
         */

        this.renderWorldAfterActor();
    }


    /**
     * Render an unselected actor in the same world/camera pass.
     * The selected actor's established renderer is intentionally untouched.
     */
    public void drawAdditionalActor(
            Minecraft mc,
            BlockbusterSceneActorData data,
            BlockbusterRecordFrame frame,
            BlockbusterActorPreviewRenderer renderer)
    {
        if (mc == null || renderer == null || frame == null) return;
        renderer.drawActor(
                mc, data, frame, this.camera,
                this.x, this.y, this.width, this.height);
    }

    private void renderWorldAfterActor()
    {
        if (this.worldAfterActorRendered)
        {
            return;
        }

        if(!this.nativeWorldRendered)
            this.minecraftWorldPreview.drawAfterActor();

        this.worldAfterActorRendered = true;
    }


    private void renderWeather(
            Minecraft mc)
    {
        if (this.weatherRendered)
        {
            return;
        }

        if (mc == null)
        {
            return;
        }

        if(!this.nativeWorldRendered)
            this.weatherRenderer.render(
                    this.camera,mc.getRenderPartialTicks()
            );

        this.weatherRendered = true;
    }


    private void endWorldRender()
    {
        if(!this.nativeWorldRendered)
            this.minecraftWorldPreview.endRender();
    }


    public void finishPreviewRender()
    {
        Minecraft mc =
                Minecraft.getMinecraft();


        /*
         * =========================================================
         * WORLD AFTER ACTOR
         * =========================================================
         */

        this.renderWorldAfterActor();


        /*
         * =========================================================
         * WEATHER
         * =========================================================
         */

        this.renderWeather(
                mc
        );


        /*
         * Clouds are the final transparent world pass. Drawing them
         * before actors left the cloud texture/state active at the start
         * of the multi-actor render sequence.
         */
        if(!this.nativeWorldRendered)
            this.minecraftWorldPreview.renderClouds();

        /*
         * =========================================================
         * END WORLD
         * =========================================================
         */

        this.endWorldRender();

        /*
         * =========================================================
         * END PREVIEW FBO
         * ========================================================= */

        this.renderBackend.endPreviewRender();
    }


    public void renderPreviewToScreen()
    {
        this.renderBackend.renderPreviewToScreen(
                this.x,
                this.y,
                this.width,
                this.height
        );

        /*
         * The working gizmo already draws successfully in regular GUI
         * space. The old Jolt glBegin wireframes were added to the Preview
         * FBO yet never became visible despite native bodies existing.
         * Draw *after* the FBO-to-GUI blit, using gizmo-matched CPU camera
         * projection and Minecraft's GUI tessellation. This does not tick
         * physics, change animation, or modify the Preview framebuffer.
         */
        if(BodyPartJoltPreviewAdapter.drawContactsRequested()
                && this.lastJoltOverlayFrame>=0 && this.hasJoltActorOrigin)
        {
            JoltContactOverlaySnapshot shapes =
                    this.actorPreviewRenderer.snapshotJoltContactShapes(
                            this.lastJoltOverlayFrame);
            JoltContactGuiOverlay.draw(Minecraft.getMinecraft(),this.camera,
                    this.x,this.y,this.width,this.height,shapes,
                    shapes!=null && shapes.worldSpace?0D:this.joltActorX,
                    shapes!=null && shapes.worldSpace?0D:this.joltActorY,
                    shapes!=null && shapes.worldSpace?0D:this.joltActorZ);
        }
    }


    public boolean isInside(
            int mouseX,
            int mouseY)
    {
        return mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height;
    }


    public boolean mousePressed(
            int mouseX,
            int mouseY,
            int button)
    {
        if(this.camera.getMode()==EditorCamera.Mode.TRACK)return false;
        if (!isInside(
                mouseX,
                mouseY))
        {
            return false;
        }

        /*
         * Правая кнопка мыши — вращение камеры.
         */

        if (button == 1)
        {
            this.rotatingCamera = true;

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            return true;
        }

        return false;
    }


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

        float sensitivity =
                0.5F;

        this.camera.rotate(
                deltaX * sensitivity,
                -deltaY * sensitivity
        );

        return true;
    }


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


    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int amount)
    {
        if(this.camera.getMode()==EditorCamera.Mode.TRACK)return false;
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

            return true;
        }
        else if (amount < 0)
        {
            this.camera.zoom(
                    0.75D
            );

            return true;
        }

        return false;
    }


    public void updateCameraMovement(
            Minecraft mc)
    {
        if(this.camera.getMode()==EditorCamera.Mode.TRACK)return;
        if (mc == null)
        {
            return;
        }

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

        if (!this.isInside(
                mouseX,
                mouseY))
        {
            return;
        }

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
                Keyboard.isKeyDown(
                        Keyboard.KEY_SPACE
                );

        boolean down =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LSHIFT
                );

        boolean fast =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                );


        if (!forward &&
                !backward &&
                !left &&
                !right &&
                !up &&
                !down)
        {
            return;
        }


        double forwardAmount =
                0.0D;

        double strafeAmount =
                0.0D;

        double verticalAmount =
                0.0D;


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
         * Нормализация диагонального движения.
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


        this.camera.move(
                forwardAmount,
                strafeAmount,
                verticalAmount,
                fast
        );
    }


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


        this.minecraftWorldPreview.setSceneAvailable(
                true
        );


        /*
         * При смене сцены камера смотрит на её начало.
         */

        this.camera.setTarget(
                x,
                y + 1.0D,
                z
        );
    }


    public void clearScene()
    {
        this.sceneX = 0.0D;
        this.sceneY = 0.0D;
        this.sceneZ = 0.0D;


        this.animationPreview.setWorldPosition(
                0.0D,
                0.0D,
                0.0D
        );


        this.minecraftWorldPreview.clearScene();


        this.camera.reset();


        this.worldAfterActorRendered = false;
        this.weatherRendered = false;
    }


    public boolean hasScene()
    {
        return this.minecraftWorldPreview.isSceneAvailable();
    }
}