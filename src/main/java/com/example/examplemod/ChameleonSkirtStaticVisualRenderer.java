package com.example.examplemod;

import mchorse.mclib.client.render.VertexBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Optional static A/B volume PARITY preview (OFF by default).
 *
 * The first experimental preview exposed an important topology issue: it
 * selected only 40 exterior quads from the 40 fabric cubes, and therefore
 * looked like separate loose cardboard strips. The source Chameleon
 * appearance is actually 43 cuboids / up to 258 textured quads, including
 * all sidewalls and 3 waist pieces. We must verify an exact source-volume
 * replica before attempting any further cloth surface elimination.
 *
 * Draws a detached +1.2-block static reference. Does NOT replace native
 * rendering, edit native vertices/UV, change animation, or affect Jolt.
 */
public final class ChameleonSkirtStaticVisualRenderer
{
    private static final float OFFSET_X=1.20F;
    private static boolean reportedFirstDraw;
    private static boolean reportedFailure;

    private ChameleonSkirtStaticVisualRenderer(){}

    public static void draw(ChameleonSkirtVolumeSnapshot.Snapshot snapshot)
    {
        if(snapshot==null ||
                snapshot.cuboidCount!=ChameleonSkirtVolumeSnapshot.EXPECTED_CUBES||
                snapshot.quadCount<43)return;
        try
        {
            // Same native texture already bound by ChameleonMorph.
            GlStateManager.disableCull();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            try
            {
                BufferBuilder builder=Tessellator.getInstance().getBuffer();
                builder.begin(GL11.GL_QUADS,
                        VertexBuilder.getFormat(true,true,true,true));
                int lightX=(int)OpenGlHelper.lastBrightnessX;
                int lightY=(int)OpenGlHelper.lastBrightnessY;
                for(ChameleonSkirtVolumeSnapshot.Face quad:snapshot.faces)
                {
                    float[] tint=quad.material;
                    int light=tint[4]>.5F?0:lightX;
                    light=(int)(light+(240-light)*tint[5]);
                    for(int i=0;i<4;i++)
                    {
                        float[] p=quad.world[i],uv=quad.uv[i];
                        builder.pos(p[0]+OFFSET_X,p[1],p[2])
                                .color(tint[0],tint[1],tint[2],tint[3])
                                .tex(uv[0],uv[1])
                                .lightmap(lightY,light)
                                .normal(quad.normal[0],quad.normal[1],
                                        quad.normal[2])
                                .endVertex();
                    }
                }
                Tessellator.getInstance().draw();
                if(!reportedFirstDraw)
                {
                    reportedFirstDraw=true;
                    System.out.println(
                            "[BBS Editor][SkirtStaticVisual] "+
                            "drawn=true mode=DETACHED_FULL_VOLUME_PARITY"+
                            " cubes="+snapshot.cuboidCount+
                            " quads="+snapshot.quadCount+
                            " vertices="+snapshot.vertexCount+
                            " xOffsetBlocks="+OFFSET_X+
                            " includes=WAIST_AND_ALL_NATIVE_CUBE_FACES"+
                            " sourceTexture=BOUND_CHAMELEON"+
                            " sourceModel=UNCHANGED"+
                            " (rest volume A/B; not connected cloth or XPBD)");
                }
            }
            finally
            {
                // Mirrors the native ChameleonRenderer.render state restore.
                GlStateManager.disableBlend();
                GlStateManager.disableRescaleNormal();
                GlStateManager.enableCull();
            }
        }
        catch(Throwable failure)
        {
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println("[BBS Editor][SkirtStaticVisual] "+
                        "Detached source-volume parity draw failed: "+
                        failure.getClass().getSimpleName()+": "+
                        failure.getMessage()+
                        " (normal actor/source model unchanged)");
            }
        }
    }
}
