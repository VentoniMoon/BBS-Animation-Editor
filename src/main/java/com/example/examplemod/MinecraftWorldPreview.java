package com.example.examplemod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.util.ArrayList;
import java.util.List;

public class MinecraftWorldPreview
{
    private static final int WORLD_RENDER_RADIUS = 32;

    private static final int WORLD_RENDER_MIN_Y = -16;
    private static final int WORLD_RENDER_MAX_Y = 32;

    private static final float WORLD_RENDER_FAR_CLIP = 256.0F;

    private static final int CHUNK_SIZE = 16;

    /*
     * =========================================================
     * CACHED CHUNK
     * =========================================================
     */

    private static class CachedChunk
    {
        private int chunkX;
        private int chunkZ;

        /*
         * Обычные непрозрачные блоки.
         */
        private int solidDisplayList;

        /*
         * Жидкости.
         *
         * В первую очередь вода и лава.
         */
        private int liquidDisplayList;

        /*
         * Остальные TRANSLUCENT-блоки:
         * стекло, цветное стекло и т.д.
         */
        private int translucentDisplayList;

        private boolean built;

        public CachedChunk(
                int chunkX,
                int chunkZ)
        {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;

            this.solidDisplayList = -1;
            this.liquidDisplayList = -1;
            this.translucentDisplayList = -1;

            this.built = false;
        }
    }

    private double worldX;
    private double worldY;
    private double worldZ;

    private final List<CachedChunk> cachedChunks =
            new ArrayList<CachedChunk>();

    private int cachedCenterX;
    private int cachedCenterY;
    private int cachedCenterZ;

    private boolean cacheBuilt = false;

    private boolean printedDiagnostics = false;

    public MinecraftWorldPreview()
    {
        this.worldX = 0.0D;
        this.worldY = 0.0D;
        this.worldZ = 0.0D;

        this.cachedCenterX = 0;
        this.cachedCenterY = 0;
        this.cachedCenterZ = 0;
    }

    public void setWorldPosition(
            double x,
            double y,
            double z)
    {
        this.worldX = x;
        this.worldY = y;
        this.worldZ = z;

        this.printedDiagnostics = false;

        this.invalidateCache();
    }

    public double getWorldX()
    {
        return this.worldX;
    }

    public double getWorldY()
    {
        return this.worldY;
    }

    public double getWorldZ()
    {
        return this.worldZ;
    }

    /*
     * =========================================================
     * CACHE MANAGEMENT
     * =========================================================
     */

    public void invalidateCache()
    {
        for (CachedChunk chunk : this.cachedChunks)
        {
            if (chunk.solidDisplayList != -1)
            {
                GL11.glDeleteLists(
                        chunk.solidDisplayList,
                        1
                );
            }

            if (chunk.liquidDisplayList != -1)
            {
                GL11.glDeleteLists(
                        chunk.liquidDisplayList,
                        1
                );
            }

            if (chunk.translucentDisplayList != -1)
            {
                GL11.glDeleteLists(
                        chunk.translucentDisplayList,
                        1
                );
            }
        }

        this.cachedChunks.clear();

        this.cacheBuilt = false;
    }

    /*
     * =========================================================
     * MAIN RENDER
     * =========================================================
     */

    public void draw(
            Minecraft mc,
            EditorCamera camera,
            int x,
            int y,
            int width,
            int height)
    {
        if (mc == null ||
                mc.world == null ||
                camera == null)
        {
            return;
        }

        if (width <= 0 ||
                height <= 0)
        {
            return;
        }

        net.minecraft.client.gui.ScaledResolution resolution =
                new net.minecraft.client.gui.ScaledResolution(mc);

        int scaledWidth =
                resolution.getScaledWidth();

        int scaledHeight =
                resolution.getScaledHeight();

        float scaleX =
                (float) mc.displayWidth /
                        (float) scaledWidth;

        float scaleY =
                (float) mc.displayHeight /
                        (float) scaledHeight;

        int viewportX =
                Math.round(
                        x * scaleX
                );

        int viewportWidth =
                Math.round(
                        width * scaleX
                );

        int viewportHeight =
                Math.round(
                        height * scaleY
                );

        int viewportY =
                mc.displayHeight -
                        Math.round(
                                (y + height) * scaleY
                        );

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

        /*
         * =====================================================
         * VIEWPORT
         * =====================================================
         */

        GL11.glViewport(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );

        GL11.glEnable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glScissor(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );

        GL11.glClearColor(
                0.08F,
                0.09F,
                0.11F,
                1.0F
        );

        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT
        );

        /*
         * =====================================================
         * DEPTH
         * =====================================================
         */

        GL11.glEnable(
                GL11.GL_DEPTH_TEST
        );

        GL11.glDepthFunc(
                GL11.GL_LEQUAL
        );

        GL11.glDepthMask(true);

        /*
         * =====================================================
         * PROJECTION
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        float aspect =
                (float) viewportWidth /
                        (float) viewportHeight;

        GLU.gluPerspective(
                60.0F,
                aspect,
                0.05F,
                WORLD_RENDER_FAR_CLIP
        );

        /*
         * =====================================================
         * CAMERA
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        GL11.glRotatef(
                -camera.getPitch(),
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glRotatef(
                -camera.getYaw(),
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glTranslated(
                -camera.getTargetX(),
                -camera.getTargetY(),
                -camera.getTargetZ()
        );

        GL11.glTranslated(
                0.0D,
                0.0D,
                -camera.getDistance()
        );

        /*
         * =====================================================
         * BASE RENDER STATE
         * =====================================================
         */

        GlStateManager.disableLighting();

        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        mc.entityRenderer.enableLightmap();

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        mc.getTextureManager().bindTexture(
                TextureMap.LOCATION_BLOCKS_TEXTURE
        );

        /*
         * =====================================================
         * SCENE POSITION
         * =====================================================
         */

        int centerX =
                (int) Math.floor(
                        this.worldX
                );

        int centerY =
                (int) Math.floor(
                        this.worldY
                );

        int centerZ =
                (int) Math.floor(
                        this.worldZ
                );

        /*
         * =====================================================
         * DIAGNOSTICS
         * =====================================================
         */

        if (!this.printedDiagnostics)
        {
            this.printWorldDiagnostics(
                    mc,
                    centerX,
                    centerY,
                    centerZ
            );

            this.printedDiagnostics = true;
        }

        /*
         * =====================================================
         * SKY
         * =====================================================
         */

        this.renderMinecraftSky(
                mc,
                camera
        );

        /*
         * =====================================================
         * RESTORE BLOCK RENDER STATE
         * =====================================================
         */

        GlStateManager.disableLighting();

        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        mc.entityRenderer.enableLightmap();

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        mc.getTextureManager().bindTexture(
                TextureMap.LOCATION_BLOCKS_TEXTURE
        );

        /*
         * =====================================================
         * BUILD CACHE
         * =====================================================
         */

        if (!this.cacheBuilt ||
                this.cachedCenterX != centerX ||
                this.cachedCenterY != centerY ||
                this.cachedCenterZ != centerZ)
        {
            this.buildWorldCache(
                    mc,
                    centerX,
                    centerY,
                    centerZ
            );
        }

        /*
         * =====================================================
         * RENDER CACHE
         * =====================================================
         */

        this.renderWorldCache();

        mc.entityRenderer.disableLightmap();

        /*
         * =====================================================
         * RESTORE MATRICES
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                oldMatrixMode
        );

        /*
         * =====================================================
         * RESTORE OPENGL STATE
         * =====================================================
         */

        GL11.glPopAttrib();

        GL11.glViewport(
                0,
                0,
                mc.displayWidth,
                mc.displayHeight
        );

        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GL11.glMatrixMode(
                oldMatrixMode
        );
    }

    /*
     * =========================================================
     * WORLD CACHE
     * =========================================================
     */

    private void buildWorldCache(
            Minecraft mc,
            int centerX,
            int centerY,
            int centerZ)
    {
        this.invalidateCache();

        this.cachedCenterX = centerX;
        this.cachedCenterY = centerY;
        this.cachedCenterZ = centerZ;

        int minX =
                centerX -
                        WORLD_RENDER_RADIUS;

        int maxX =
                centerX +
                        WORLD_RENDER_RADIUS;

        int minZ =
                centerZ -
                        WORLD_RENDER_RADIUS;

        int maxZ =
                centerZ +
                        WORLD_RENDER_RADIUS;

        int minChunkX =
                floorDiv(
                        minX,
                        CHUNK_SIZE
                );

        int maxChunkX =
                floorDiv(
                        maxX,
                        CHUNK_SIZE
                );

        int minChunkZ =
                floorDiv(
                        minZ,
                        CHUNK_SIZE
                );

        int maxChunkZ =
                floorDiv(
                        maxZ,
                        CHUNK_SIZE
                );

        System.out.println(
                "[BBS Animation Editor] Building world cache..."
        );

        System.out.println(
                "[BBS Animation Editor] Chunk range: "
                        + minChunkX
                        + " .. "
                        + maxChunkX
                        + " / "
                        + minChunkZ
                        + " .. "
                        + maxChunkZ
        );

        for (int chunkX = minChunkX;
             chunkX <= maxChunkX;
             chunkX++)
        {
            for (int chunkZ = minChunkZ;
                 chunkZ <= maxChunkZ;
                 chunkZ++)
            {
                CachedChunk chunk =
                        new CachedChunk(
                                chunkX,
                                chunkZ
                        );

                this.buildChunkCache(
                        mc,
                        chunk,
                        minX,
                        maxX,
                        minZ,
                        maxZ
                );

                this.cachedChunks.add(
                        chunk
                );
            }
        }

        this.cacheBuilt = true;

        System.out.println(
                "[BBS Animation Editor] World cache built. "
                        + "Chunks: "
                        + this.cachedChunks.size()
        );
    }

    private void buildChunkCache(
            Minecraft mc,
            CachedChunk chunk,
            int minX,
            int maxX,
            int minZ,
            int maxZ)
    {
        /*
         * =====================================================
         * CHUNK BOUNDS
         * =====================================================
         */

        int chunkMinX =
                chunk.chunkX *
                        CHUNK_SIZE;

        int chunkMaxX =
                chunkMinX +
                        CHUNK_SIZE -
                        1;

        int chunkMinZ =
                chunk.chunkZ *
                        CHUNK_SIZE;

        int chunkMaxZ =
                chunkMinZ +
                        CHUNK_SIZE -
                        1;

        int renderMinX =
                Math.max(
                        chunkMinX,
                        minX
                );

        int renderMaxX =
                Math.min(
                        chunkMaxX,
                        maxX
                );

        int renderMinZ =
                Math.max(
                        chunkMinZ,
                        minZ
                );

        int renderMaxZ =
                Math.min(
                        chunkMaxZ,
                        maxZ
                );

        if (renderMinX > renderMaxX ||
                renderMinZ > renderMaxZ)
        {
            return;
        }

        BlockRendererDispatcher dispatcher =
                mc.getBlockRendererDispatcher();

        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();

        BlockPos.MutableBlockPos blockPos =
                new BlockPos.MutableBlockPos();

        /*
         * =====================================================
         * SOLID DISPLAY LIST
         * =====================================================
         */

        chunk.solidDisplayList =
                GL11.glGenLists(1);

        GL11.glNewList(
                chunk.solidDisplayList,
                GL11.GL_COMPILE
        );

        GL11.glPushMatrix();

        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.BLOCK
        );

        for (int bx = renderMinX;
             bx <= renderMaxX;
             bx++)
        {
            for (int by = WORLD_RENDER_MIN_Y;
                 by <= WORLD_RENDER_MAX_Y;
                 by++)
            {
                if (by < 0 ||
                        by >= 256)
                {
                    continue;
                }

                for (int bz = renderMinZ;
                     bz <= renderMaxZ;
                     bz++)
                {
                    blockPos.setPos(
                            bx,
                            by,
                            bz
                    );

                    IBlockState state =
                            mc.world.getBlockState(
                                    blockPos
                            );

                    if (state == null)
                    {
                        continue;
                    }

                    if (state.getBlock().isAir(
                            state,
                            mc.world,
                            blockPos
                    ))
                    {
                        continue;
                    }

                    BlockRenderLayer layer =
                            state.getBlock().getBlockLayer();

                    if (layer ==
                            BlockRenderLayer.TRANSLUCENT)
                    {
                        continue;
                    }

                    dispatcher.renderBlock(
                            state,
                            blockPos,
                            mc.world,
                            buffer
                    );
                }
            }
        }

        tessellator.draw();

        GL11.glPopMatrix();

        GL11.glEndList();

        /*
         * =====================================================
         * LIQUID DISPLAY LIST
         * =====================================================
         *
         * ВАЖНО:
         * вода остаётся в отдельном проходе.
         */

        chunk.liquidDisplayList =
                GL11.glGenLists(1);

        GL11.glNewList(
                chunk.liquidDisplayList,
                GL11.GL_COMPILE
        );

        GL11.glPushMatrix();

        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.BLOCK
        );

        for (int bx = renderMinX;
             bx <= renderMaxX;
             bx++)
        {
            for (int by = WORLD_RENDER_MIN_Y;
                 by <= WORLD_RENDER_MAX_Y;
                 by++)
            {
                if (by < 0 ||
                        by >= 256)
                {
                    continue;
                }

                for (int bz = renderMinZ;
                     bz <= renderMaxZ;
                     bz++)
                {
                    blockPos.setPos(
                            bx,
                            by,
                            bz
                    );

                    IBlockState state =
                            mc.world.getBlockState(
                                    blockPos
                            );

                    if (state == null)
                    {
                        continue;
                    }

                    /*
                     * Только настоящие жидкости.
                     */
                    if (!state.getMaterial().isLiquid())
                    {
                        continue;
                    }

                    dispatcher.renderBlock(
                            state,
                            blockPos,
                            mc.world,
                            buffer
                    );
                }
            }
        }

        tessellator.draw();

        GL11.glPopMatrix();

        GL11.glEndList();

        /*
         * =====================================================
         * OTHER TRANSLUCENT DISPLAY LIST
         * =====================================================
         *
         * Здесь находятся:
         * - обычное стекло;
         * - цветное стекло;
         * - другие блоки TRANSLUCENT.
         *
         * Жидкости специально исключены,
         * потому что они уже находятся выше.
         */

        chunk.translucentDisplayList =
                GL11.glGenLists(1);

        GL11.glNewList(
                chunk.translucentDisplayList,
                GL11.GL_COMPILE
        );

        GL11.glPushMatrix();

        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.BLOCK
        );

        for (int bx = renderMinX;
             bx <= renderMaxX;
             bx++)
        {
            for (int by = WORLD_RENDER_MIN_Y;
                 by <= WORLD_RENDER_MAX_Y;
                 by++)
            {
                if (by < 0 ||
                        by >= 256)
                {
                    continue;
                }

                for (int bz = renderMinZ;
                     bz <= renderMaxZ;
                     bz++)
                {
                    blockPos.setPos(
                            bx,
                            by,
                            bz
                    );

                    IBlockState state =
                            mc.world.getBlockState(
                                    blockPos
                            );

                    if (state == null)
                    {
                        continue;
                    }

                    if (state.getBlock().isAir(
                            state,
                            mc.world,
                            blockPos
                    ))
                    {
                        continue;
                    }

                    /*
                     * Жидкости уже отрисованы
                     * в отдельном Liquid Pass.
                     */
                    if (state.getMaterial().isLiquid())
                    {
                        continue;
                    }

                    BlockRenderLayer layer =
                            state.getBlock().getBlockLayer();

                    if (layer !=
                            BlockRenderLayer.TRANSLUCENT)
                    {
                        continue;
                    }

                    /*
                     * Здесь теперь будут нормально
                     * попадать стекло и цветное стекло.
                     */
                    dispatcher.renderBlock(
                            state,
                            blockPos,
                            mc.world,
                            buffer
                    );
                }
            }
        }

        tessellator.draw();

        GL11.glPopMatrix();

        GL11.glEndList();

        chunk.built = true;
    }

    /*
     * =========================================================
     * RENDER CACHE
     * =========================================================
     */

    private void renderWorldCache()
    {
        /*
         * =====================================================
         * PASS 1 — SOLID
         * =====================================================
         */

        GlStateManager.enableDepth();

        GlStateManager.depthMask(true);

        for (CachedChunk chunk :
                this.cachedChunks)
        {
            if (!chunk.built ||
                    chunk.solidDisplayList == -1)
            {
                continue;
            }

            GL11.glCallList(
                    chunk.solidDisplayList
            );
        }

        /*
         * =====================================================
         * PASS 2 — LIQUID
         * =====================================================
         */

        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        GlStateManager.depthMask(false);

        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );

        for (CachedChunk chunk :
                this.cachedChunks)
        {
            if (!chunk.built ||
                    chunk.liquidDisplayList == -1)
            {
                continue;
            }

            GL11.glCallList(
                    chunk.liquidDisplayList
            );
        }

        /*
         * =====================================================
         * PASS 3 — OTHER TRANSLUCENT
         * =====================================================
         */

        for (CachedChunk chunk :
                this.cachedChunks)
        {
            if (!chunk.built ||
                    chunk.translucentDisplayList == -1)
            {
                continue;
            }

            GL11.glCallList(
                    chunk.translucentDisplayList
            );
        }

        /*
         * Возвращаем обычную запись глубины.
         */
        GlStateManager.depthMask(true);
    }

    /*
     * =========================================================
     * FLOOR DIV
     * =========================================================
     */

    private int floorDiv(
            int value,
            int divisor)
    {
        int result =
                value / divisor;

        int remainder =
                value % divisor;

        if (remainder != 0 &&
                ((value < 0) != (divisor < 0)))
        {
            result--;
        }

        return result;
    }

    /*
     * =========================================================
     * DIAGNOSTICS
     * =========================================================
     */

    private void printWorldDiagnostics(
            Minecraft mc,
            int centerX,
            int centerY,
            int centerZ)
    {
        System.out.println(
                "[BBS Animation Editor] ================================"
        );

        System.out.println(
                "[BBS Animation Editor] WORLD DIAGNOSTICS"
        );

        System.out.println(
                "[BBS Animation Editor] Scene position: "
                        + centerX
                        + ", "
                        + centerY
                        + ", "
                        + centerZ
        );

        System.out.println(
                "[BBS Animation Editor] Vertical column:"
        );

        BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos();

        for (int y = 0;
             y <= 20;
             y++)
        {
            pos.setPos(
                    centerX,
                    y,
                    centerZ
            );

            IBlockState state =
                    mc.world.getBlockState(
                            pos
                    );

            if (state == null)
            {
                continue;
            }

            String name =
                    state.getBlock()
                            .getRegistryName() != null
                            ?
                            state.getBlock()
                                    .getRegistryName()
                                    .toString()
                            :
                            "unknown";

            System.out.println(
                    "[BBS Animation Editor] Y="
                            + y
                            + " -> "
                            + name
                            + " / "
                            + state
            );
        }

        System.out.println(
                "[BBS Animation Editor] Surface scan:"
        );

        for (int dx = -2;
             dx <= 2;
             dx++)
        {
            for (int dz = -2;
                 dz <= 2;
                 dz++)
            {
                int highestY = -1;

                IBlockState highestState = null;

                for (int y = 0;
                     y < 40;
                     y++)
                {
                    pos.setPos(
                            centerX + dx,
                            y,
                            centerZ + dz
                    );

                    IBlockState state =
                            mc.world.getBlockState(
                                    pos
                            );

                    if (state == null)
                    {
                        continue;
                    }

                    if (state.getBlock().isAir(
                            state,
                            mc.world,
                            pos
                    ))
                    {
                        continue;
                    }

                    highestY = y;
                    highestState = state;
                }

                if (highestY >= 0 &&
                        highestState != null)
                {
                    String name =
                            highestState.getBlock()
                                    .getRegistryName() != null
                                    ?
                                    highestState.getBlock()
                                            .getRegistryName()
                                            .toString()
                                    :
                                    "unknown";

                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "dx="
                                    + dx
                                    + " dz="
                                    + dz
                                    + " -> Y="
                                    + highestY
                                    + " : "
                                    + name
                    );
                }
            }
        }

        System.out.println(
                "[BBS Animation Editor] ================================"
        );
    }

    /*
     * =========================================================
     * SKY
     * =========================================================
     */

    private void renderMinecraftSky(
            Minecraft mc,
            EditorCamera camera)
    {
        if (mc == null ||
                mc.world == null ||
                mc.renderGlobal == null ||
                camera == null)
        {
            return;
        }

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        GL11.glRotatef(
                -camera.getPitch(),
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glRotatef(
                -camera.getYaw(),
                0.0F,
                1.0F,
                0.0F
        );

        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        mc.renderGlobal.renderSky(
                0.0F,
                2
        );

        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableCull();

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );
    }
}