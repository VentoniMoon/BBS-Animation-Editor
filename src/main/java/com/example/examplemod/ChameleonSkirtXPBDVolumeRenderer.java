package com.example.examplemod;

import mchorse.mclib.client.render.VertexBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Optional second visual output for the SAME 60-particle XPBD simulation.
 * Renders the complete native Chameleon cube volume, posed and moved by
 * XPBD interpolated source-skin node deltas. This is a source-volume fidelity
 * baseline, not a physically watertight cloth shell: panel sides and caps
 * intentionally remain so the original texture/silhouette can be compared.
 *
 * In A/B overlay mode the duplicated waist is drawn too. When replacing
 * native fabric at actor position, the original waist remains rendered by
 * Chameleon and is NOT duplicated (avoids z-fighting).
 */
public final class ChameleonSkirtXPBDVolumeRenderer
{
    private static boolean printed,reportedFailure,reportedNoLeg;
    private static int draws;
    private ChameleonSkirtXPBDVolumeRenderer(){}

    public static boolean draw(SkirtXPBDVolumeSkin.Mesh mesh,
            SkirtXPBDRuntime.RenderFrame frame)
    {
        if(mesh==null||frame==null||
                mesh.cubes!=43||mesh.quads<43)return false;
        boolean started=false;
        try
        {
            GlStateManager.disableCull();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            BufferBuilder b=Tessellator.getInstance().getBuffer();
            b.begin(GL11.GL_QUADS,VertexBuilder.getFormat(true,true,true,true));
            started=true;
            int lx=(int)OpenGlHelper.lastBrightnessX;
            int ly=(int)OpenGlHelper.lastBrightnessY;
            int faces=0,skippedWaist=0;
            boolean replace=SkirtXPBDRuntime.REPLACE;
            for(SkirtXPBDVolumeSkin.Face f:mesh.faces)
            {
                if(replace&&f.waist){skippedWaist++;continue;}
                float[] color=f.material;
                int light=color[4]>.5F?0:lx;
                light=(int)(light+(240-light)*color[5]);
                for(int i=0;i<4;i++)
                {
                    float[] p=f.world[i],uv=f.uv[i];
                    b.pos(p[0]+(replace?0F:1.20F),p[1],p[2])
                            .color(color[0],color[1],color[2],color[3])
                            .tex(uv[0],uv[1])
                            .lightmap(ly,light)
                            .normal(f.normal[0],f.normal[1],f.normal[2])
                            .endVertex();
                }
                faces++;
            }
            Tessellator.getInstance().draw();
            started=false;
            if(!printed)
            {
                printed=true;
                System.out.println("[BBS Editor][SkirtXPBDVolume] "+
                        "drawn=true mode=POSED_NATIVE_VOLUME_XPBD"+
                        " physicalNodes=60"+
                        " legBoxes="+frame.legBoxCount+
                        " sourceCuboids="+mesh.cubes+
                        " totalSourceQuads="+mesh.quads+
                        " drawnQuads="+faces+
                        " skippedNativeWaistQuads="+skippedWaist+
                        " maxNodeMotion="+mesh.maxNodeDisplacement+
                        " maxVisualMotion="+mesh.maxVisualDisplacement+
                        " offsetX="+(replace?0F:1.20F)+
                        " allNativeUV=UNCHANGED"+
                        " volumeSidesAndCaps=PRESERVED"+
                        " warning=NOT_CONTINUOUS_RENDER_MESH");
            }
            if(frame.legBoxCount==0&&!reportedNoLeg)
            {
                reportedNoLeg=true;
                System.err.println("[BBS Editor][SkirtXPBDVolume] "+
                        "NO_REAL_LEG_BOXES: gravity/self contacts only; "+
                        "enable existing fitted Emoticons leg contact source");
            }
            if(++draws%120==0)
                System.out.println("[BBS Editor][SkirtXPBDVolumeFrame] "+
                        "maxNodeMotion="+mesh.maxNodeDisplacement+
                        " maxVisualMotion="+mesh.maxVisualDisplacement+
                        " renderedQuads="+faces);
            return true;
        }
        catch(Throwable e)
        {
            if(started)
                try{Tessellator.getInstance().draw();}
                catch(Throwable ignored){}
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println("[BBS Editor][SkirtXPBDVolume] "+
                        "Native volume draw failed: "+
                        e.getClass().getSimpleName()+": "+e.getMessage());
            }
            return false;
        }
        finally
        {
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.enableCull();
        }
    }
}
