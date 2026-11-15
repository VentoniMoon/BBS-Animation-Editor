package com.example.examplemod;

import mchorse.mclib.client.render.VertexBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Physics 3.0 / Stage 2+3 native Chameleon cloth surface draw.
 *
 * Draws a SINGLE connected fabric shell: original native face UVs and
 * source-matched material corners plus two edge-color-preserving bridge
 * quads per candidate seam. A 60-node XPBD solver displaces physical
 * positions while the material skin follows actual posed Chameleon bones.
 *
 * It is called by the existing native renderer's exception-safe scope
 * at the exact actor modelview/texture binding, AFTER native waist cubes.
 * The 40 source fabric cubes are temporarily hidden and always restored
 * in the scope finally; other models are untouched.
 *
 * This preview has zero-thickness two-sided cloth, not the original 43
 * blocky cubes and no guaranteed exact full-volume texture parity.
 */
public final class ChameleonSkirtXPBDRenderer
{
    private static boolean reportedFirstDraw;
    private static boolean reportedFirstNoColliders;
    private static boolean reportedFailure;
    private static int drawCount;
    private ChameleonSkirtXPBDRenderer(){}

    /** Returns whether the native draw submission completed. */
    public static boolean draw(SkirtXPBDRuntime.RenderFrame frame)
    {
        if(frame==null||frame.topology==null||
                frame.topology.quads.size()!=
                        SkirtXPBDSurfaceTopology.CONNECTED_QUADS||
                frame.binding==null||frame.solved==null)return false;

        boolean started=false;
        try
        {
            GlStateManager.disableCull();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            BufferBuilder builder=Tessellator.getInstance().getBuffer();
            builder.begin(GL11.GL_QUADS,
                    VertexBuilder.getFormat(true,true,true,true));
            started=true;
            int lx=(int)OpenGlHelper.lastBrightnessX;
            int ly=(int)OpenGlHelper.lastBrightnessY;
            int drawn=0,skipped=0,bridges=0;
            for(SkirtXPBDSurfaceTopology.Quad quad:frame.topology.quads)
            {
                float[] material=frame.materials[quad.materialPatch];
                if(material==null||material.length!=6)
                    throw new IllegalArgumentException("Missing native material");
                float[][] xyz=new float[4][];
                for(int i=0;i<4;i++)
                    xyz[i]=frame.topology.position(quad,quad.vertices[i],
                            frame.pose,frame.binding,
                            frame.solved.particles);
                float[] normal=normal(xyz);
                if(normal==null)
                {
                    // Some native upper/lower cube edges are coincident
                    // already. Their zero-area join should not be drawn.
                    skipped++;
                    continue;
                }
                int light=material[4]>.5F?0:lx;
                light=(int)(light+(240-light)*material[5]);
                for(int i=0;i<4;i++)
                {
                    float[] uv=quad.vertices[i].uv;
                    float[] p=xyz[i];
                    builder.pos(p[0]+(SkirtXPBDRuntime.REPLACE?0F:1.20F),
                            p[1],p[2])
                            .color(material[0],material[1],
                                    material[2],material[3])
                            .tex(uv[0],uv[1])
                            .lightmap(ly,light)
                            .normal(normal[0],normal[1],normal[2])
                            .endVertex();
                }
                drawn++;
                if(quad.bridge)bridges++;
            }
            Tessellator.getInstance().draw();
            started=false;
            if(!reportedFirstDraw)
            {
                reportedFirstDraw=true;
                System.out.println(
                        "[BBS Editor][SkirtXPBD] "+
                        "drawn=true mode=POSED_CONNECTED_CLOTH"+
                        " particles="+frame.solved.particles.length+
                        " renderedQuads="+drawn+
                        " bridgeQuads="+bridges+
                        " skippedDegenerate="+skipped+
                        " offsetX="+(SkirtXPBDRuntime.REPLACE?0F:1.20F)+
                        " nativeUV=UNCHANGED"+
                        " shape=40_SOURCE_PANELS_PLUS_SEAM_BRIDGES"+
                        " legBoxes="+frame.legBoxCount+
                        " maxConstraintError="+
                            frame.solved.maxDistanceError+
                        " sourceModel=RESTORED_AFTER_RENDER");
            }
            if(frame.legBoxCount==0&&!reportedFirstNoColliders)
            {
                reportedFirstNoColliders=true;
                System.err.println(
                        "[BBS Editor][SkirtXPBD] NO_REAL_LEG_BOXES "+
                        "Leg collisions inactive until editor publishes "+
                        "valid Emoticons thigh OBBs for this actor. "+
                        "Self-collision and cloth gravity remain active.");
            }
            if(++drawCount%120==0)
                System.out.println("[BBS Editor][SkirtXPBDFrame] "+
                        "stepSubpasses="+frame.solved.substeps+
                        " legs="+frame.solved.legContacts+
                        " self="+frame.solved.selfContacts+
                        " constraintError="+frame.solved.maxDistanceError+
                        " boxes="+frame.legBoxCount+
                        " nativeFabric=TEMPORARILY_HIDDEN");
            return true;
        }
        catch(Throwable error)
        {
            if(started)
            {
                // End a BufferBuilder safely if possible; next actor
                // render must never inherit an unfinished tessellator.
                try{Tessellator.getInstance().draw();}
                catch(Throwable ignored){}
            }
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println(
                        "[BBS Editor][SkirtXPBD] Native fabric draw failed: "+
                        error.getClass().getSimpleName()+": "+
                        error.getMessage());
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

    private static float[] normal(float[][] p)
    {
        float[] a={p[1][0]-p[0][0],p[1][1]-p[0][1],
                p[1][2]-p[0][2]};
        float[] b={p[3][0]-p[0][0],p[3][1]-p[0][1],
                p[3][2]-p[0][2]};
        float[] n={a[1]*b[2]-a[2]*b[1],
                a[2]*b[0]-a[0]*b[2],
                a[0]*b[1]-a[1]*b[0]};
        float length=(float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
        if(!Float.isFinite(length)||length<1E-7F)return null;
        for(int k=0;k<3;k++)n[k]/=length;
        return n;
    }
}
