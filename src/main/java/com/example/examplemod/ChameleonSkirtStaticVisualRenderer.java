package com.example.examplemod;

import java.util.List;

import mchorse.mclib.client.render.VertexBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Physics 3.0 static A/B preview, EXPERIMENTAL and OFF by default.
 *
 * Draws the detached source-UV outward fabric skin as a second textured skirt
 * 1.2 blocks to model +X, after the original Chameleon actor has been drawn.
 * It does NOT replace the original Chameleon renderer, body parts, physics,
 * pose, or texture, and does not mutate shared ModelVertex resources.
 *
 * This is a REST/source-skin parity test: the duplicate stays in its authored
 * shape while the source character can animate. It is not yet XPBD, fabric
 * gap bridging, a live skinned mesh, or a collision/lighting parity guarantee.
 */
public final class ChameleonSkirtStaticVisualRenderer
{
    private static final float OFFSET_X=1.20F;
    private static boolean reportedFirstDraw;
    private static boolean reportedFailure;

    private ChameleonSkirtStaticVisualRenderer(){}

    public static void draw(SkirtClothRestBinding.Binding skin,
            SkirtClothSurfaceMesh.Snapshot source,float[][] properties)
    {
        if(skin==null||source==null||properties==null)return;
        if(skin.patches.size()!=40||source.patches.size()!=40||
                properties.length!=40||
                skin.maxRestReconstructionError>1E-5F)
            return;

        try
        {
            // The enclosing native ChameleonMorph has already bound the
            // actor's real texture and established its modelview matrix.
            GlStateManager.disableCull();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            try
            {
                List<SkirtClothRestBinding.FramePatch> rest=skin.atRest();
                BufferBuilder builder=Tessellator.getInstance().getBuffer();
                builder.begin(GL11.GL_QUADS,
                        VertexBuilder.getFormat(true,true,true,true));
                int lx=(int)OpenGlHelper.lastBrightnessX;
                int ly=(int)OpenGlHelper.lastBrightnessY;
                for(int i=0;i<rest.size();i++)
                {
                    SkirtClothRestBinding.FramePatch patch=rest.get(i);
                    SkirtClothSurfaceMesh.Patch authored=source.patches.get(i);
                    float[] tint=properties[i];
                    if(tint==null||tint.length!=6)
                        throw new IllegalArgumentException("Bad Chameleon tint");
                    float r=tint[0],g=tint[1],b=tint[2],a=tint[3];
                    int light=(tint[4]>.5F)?0:lx;
                    light=(int)(light+(240-light)*tint[5]);
                    float nx=authored.normal[0],
                            ny=authored.normal[1],
                            nz=authored.normal[2];
                    for(int j=0;j<4;j++)
                    {
                        float[] pos=patch.world[j];
                        float[] uv=patch.uv[j];
                        builder.pos(pos[0]+OFFSET_X,pos[1],pos[2])
                                .color(r,g,b,a)
                                .tex(uv[0],uv[1])
                                .lightmap(ly,light)
                                .normal(nx,ny,nz)
                                .endVertex();
                    }
                }
                Tessellator.getInstance().draw();
                if(!reportedFirstDraw)
                {
                    reportedFirstDraw=true;
                    System.out.println(
                            "[BBS Editor][SkirtStaticVisual] "+
                            "drawn=true mode=DETACHED_REST_UV_AB"+
                            " outwardQuads=40 triangles=80"+
                            " xOffsetBlocks="+OFFSET_X+
                            " restError="+skin.maxRestReconstructionError+
                            " sourceTexture=BOUND_CHAMELEON"+
                            " sourceModel=UNCHANGED"+
                            " (compare geometry only; lighting may differ)");
                }
            }
            finally
            {
                // ChameleonRenderer.render restores precisely these
                // defaults after its own BufferBuilder draw.
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
                        "Render failed without changing source model: "+
                        failure.getClass().getSimpleName()+": "+
                        failure.getMessage());
            }
        }
    }
}
