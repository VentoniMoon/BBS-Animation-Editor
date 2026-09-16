package com.example.examplemod;

import org.lwjgl.opengl.GL30;

public class GL30Helper
{
    public static final int GL_FRAMEBUFFER_BINDING =
            GL30.GL_FRAMEBUFFER_BINDING;

    public static void bindFramebuffer(
            int framebuffer)
    {
        GL30.glBindFramebuffer(
                GL30.GL_FRAMEBUFFER,
                framebuffer
        );
    }
}