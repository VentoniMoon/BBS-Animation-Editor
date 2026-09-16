package com.example.examplemod;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import org.lwjgl.opengl.GL11;

public class TessellatorHelper
{
    public static void drawTexturedQuad(
            int x,
            int y,
            int width,
            int height)
    {
        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();

        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION_TEX
        );

        buffer.pos(
                        x,
                        y + height,
                        0.0D
                )
                .tex(
                        0.0D,
                        1.0D
                )
                .endVertex();

        buffer.pos(
                        x + width,
                        y + height,
                        0.0D
                )
                .tex(
                        1.0D,
                        1.0D
                )
                .endVertex();

        buffer.pos(
                        x + width,
                        y,
                        0.0D
                )
                .tex(
                        1.0D,
                        0.0D
                )
                .endVertex();

        buffer.pos(
                        x,
                        y,
                        0.0D
                )
                .tex(
                        0.0D,
                        0.0D
                )
                .endVertex();

        tessellator.draw();
    }
}