package com.example.examplemod;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Java 8 compile/runtime fixture for newly added frame/presenter/projection.
 * Runs without Minecraft/OptiFine binaries; catches Java API typos, FBO
 * lifecycle mistakes, stale-world capture and GL matrix leaks.
 */
public final class Preview3FsFramebufferSmokeTest
{
    private static void require(boolean ok,String message)
    {
        if (!ok)throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        Minecraft mc=new Minecraft();
        PreviewRenderBackend backend=new PreviewRenderBackend(mc);
        BbsFsPreviewTarget target=backend.getNativeTarget();
        target.setBounds(100,50,800,600);
        int beforeAttrib=GL11.attrDepth;

        BbsFsWorldFrameCapture.beginNativeFrame();
        BbsFsWorldFrameCapture.beginFrame(mc);
        require(target.begin(),"target not created");
        require(!BbsFsWorldFrameCapture.copyWorld(
                 mc,backend,100,50,800,600),
                 "captured stale world before native world event");
        target.end();

        /* Use non-default GL state to test preservation. */
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,17);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,19);
        GL20.glUseProgram(23);

        BbsFsWorldFrameCapture.markNativeWorldRendered();
        BbsFsWorldFrameCapture.beginFrame(mc);
        int nativeBlits = GL30.blits;
        BbsFsWorldFrameCapture.beginFrame(mc);
        require(GL30.blits == nativeBlits,
                "repeated drawScreen captured editor GUI into world texture");
        require(GL30.read==17 && GL30.draw==19,
                "world snapshot changed native FBO target");
        require(target.begin(),"target begin failed");
        require(target.isActive(),"active flag missing");
        require(BbsFsWorldFrameCapture.copyWorld(
                mc,backend,100,50,800,600),"color world blit failed");
        require(GL30.blits>=3 && GL30.depthBlits>=1,
                "color/depth native world was not blitted");
        target.end();
        require(GL30.read==17 && GL30.draw==19,
                "target end leaked original FBO binding");
        require(GL20.program==23,"target end leaked GL shader program");
        require(GL11.attrDepth==beforeAttrib,"GL attribute stack leaked");

        target.present();
        require(GL11.quads==1,"preview target texture was not presented");
        require(GL20.program==23,"presentation changed caller shader program");

        BbsFsWorldProjection.beginFrame();
        require(!BbsFsWorldProjection.loadForPreview(800,600),
                "stale native camera projection accepted");
        BbsFsWorldProjection.capture(1280,720);
        require(BbsFsWorldProjection.isCaptured(),
                "native GL perspective capture failed");
        require(BbsFsWorldProjection.loadForPreview(800,600),
                "native projection crop failed");
        require(BbsFsWorldProjection.loadForNativeWorld(),
                "native world projection restore failed");

        mc.getRenderManager().viewerPosX=91;
        GL20.glUseProgram(11);
        try(BbsFsWorldGlGuard guard=new BbsFsWorldGlGuard(mc))
        {
            mc.getRenderManager().viewerPosX=999;
            GL20.glUseProgram(0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
        }
        require(mc.getRenderManager().viewerPosX==91,
                "actor guard leaked RenderManager position");
        require(GL20.program==11,"actor guard leaked shader program");
        require(GL11.modelDepth==1 && GL11.projDepth==1,
                "actor guard leaked world matrix stack");
        require(GL11.attrDepth==beforeAttrib,
                "actor guard leaked GL attribute scope");

        BbsFsWorldFrameCapture.release();
        target.dispose();
        require(GL30.read==17 && GL30.draw==19,
                "FBO deletion corrupted native world target");

        System.out.println("Preview FS framebuffer, native depth, " +
                "presentation, projection and GL safety PASS");
    }
}
