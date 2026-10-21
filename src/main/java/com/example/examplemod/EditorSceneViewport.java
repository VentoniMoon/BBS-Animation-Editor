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
    /** Created only if external legacy code explicitly requests it. */
    private MinecraftWorldPreview minecraftWorldPreview;
    private boolean sceneAvailable;
    private final EditorCamera camera;
    private final BlockbusterActorPreviewRenderer actorPreviewRenderer;
    private final PreviewRenderBackend renderBackend;

    private boolean rotatingCamera;

    private int lastMouseX;
    private int lastMouseY;

    private double sceneX;
    private double sceneY;
    private double sceneZ;

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

        this.sceneAvailable = false;

        this.renderBackend =
                new PreviewRenderBackend(
                        Minecraft.getMinecraft()
                );

        this.animationPreview =
                new AnimationPreview();

        this.actorPreviewRenderer =
                new BlockbusterActorPreviewRenderer();

        this.camera =
                new EditorCamera();

        /*
         * Until a scene/actor is selected, begin near the actual player
         * instead of the old hard-coded world origin (whose camera eye was
         * below Y=0). Actor autofocus can override this target normally.
         */
        this.focusOnCurrentPlayer();
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
         * BBS FS camera: Forge's RenderTick START already selected our
         * detached view. Minecraft rendered its normal world (and OptiFine)
         * before GuiScreen, and BbsFsWorldFrameCapture captured that frame
         * before any editor GUI pixels. Nothing renders chunks here.
         */
        this.renderBackend.beginPreviewRender();
        if (!this.renderBackend.isPreviewRenderActive()) return;

        BbsFsWorldCameraDriver.attach(this);
        BbsFsWorldFrameCapture.copyWorld(
                mc, this.renderBackend, this.x, this.y,
                this.width, this.height);
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


    /** Compatibility only. Preview FS never constructs or renders this. */
    @Deprecated
    public MinecraftWorldPreview getMinecraftWorldPreview()
    {
        if (this.minecraftWorldPreview == null)
            this.minecraftWorldPreview = new MinecraftWorldPreview();
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

        int viewportX = this.x, viewportY = this.y;
        int viewportWidth = this.width, viewportHeight = this.height;
        if (BbsFsNativeActorPass.isRendering())
        {
            /* Actors now inhabit the actual Minecraft world framebuffer.
             * Their full-screen coordinates will be cropped only when
             * BBS FS copies the finished native frame to the film. */
            net.minecraft.client.shader.Framebuffer world = mc.getFramebuffer();
            if (world != null)
            {
                viewportX = 0;
                viewportY = 0;
                viewportWidth = world.framebufferWidth;
                viewportHeight = world.framebufferHeight;
            }
        }

        this.actorPreviewRenderer.drawActor(
                mc,
                actorData,
                frame,
                this.camera,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );


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
        int vx=this.x, vy=this.y, vw=this.width, vh=this.height;
        if (BbsFsNativeActorPass.isRendering())
        {
            net.minecraft.client.shader.Framebuffer world = mc.getFramebuffer();
            if (world != null)
            {
                vx=0; vy=0;
                vw=world.framebufferWidth; vh=world.framebufferHeight;
            }
        }
        renderer.drawActor(
                mc, data, frame, this.camera, vx, vy, vw, vh);
    }

    /**
     * The entire world including translucent layers, weather and clouds
     * already lives in BBS FS's native captured frame.
     */
    public void finishPreviewRender()
    {
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


        this.sceneAvailable = true;
        if (this.minecraftWorldPreview != null)
            this.minecraftWorldPreview.setSceneAvailable(true);


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


        this.sceneAvailable = false;
        if (this.minecraftWorldPreview != null)
            this.minecraftWorldPreview.clearScene();

        this.camera.reset();
        this.focusOnCurrentPlayer();
    }


    public boolean hasScene()
    {
        return this.sceneAvailable;
    }

    private void focusOnCurrentPlayer()
    {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.entity.Entity player = mc.player != null
                ? mc.player : mc.getRenderViewEntity();
        if (player != null)
            this.camera.setTarget(player.posX,
                    player.posY + 3D, player.posZ);
    }

    /** Recreated lazily on returning from a child GUI. */
    public void disposePreviewResources()
    {
        this.renderBackend.delete();
    }
}