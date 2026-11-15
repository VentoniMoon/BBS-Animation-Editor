package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stage 2.1: bind 40 independent Chameleon material quads to the 60 shared
 * physical skirt nodes WITHOUT projecting the rendered source positions onto
 * the node averages. UV seams and original cube silhouette are preserved.
 *
 * The old SkirtClothSharedSurface.connect() is a useful topology PROPOSAL,
 * not a safe replacement rest mesh: actual Minecraft diagnostics measured
 * up to 45% relative source-edge deformation from averaging corner XYZ.
 * This class explicitly separates physics nodes from render vertices.
 *
 * Pure Java 8, read-only and no renderer/Jolt coupling.
 */
public final class SkirtClothRestBinding
{
    public static final int NODE_COUNT=60;
    public static final int PATCH_COUNT=40;
    private static final float EPS=2E-5F;

    public static final class Patch
    {
        public final String bone;
        public final boolean lower;
        /** Four distinct render-vertex bindings into shared physical nodes. */
        public final int[] nodes;
        /** Original texture atlas coordinates. Never averaged at seams. */
        public final float[][] uv;
        public final int[] triangles;
        /** Authored positions are recovered by nodeRest + materialOffset. */
        public final float[][] materialOffset;
        public final float[][] sourceRest;

        private Patch(SkirtClothSurfaceMesh.Patch source,
                SkirtClothSharedSurface.SkinPatch shared,
                float[][] offsets)
        {
            bone=source.bone;
            lower=source.lower;
            nodes=shared.nodes.clone();
            uv=clone2(source.uv);
            triangles=source.triangles.clone();
            materialOffset=clone2(offsets);
            sourceRest=clone2(source.world);
        }
    }

    public static final class FramePatch
    {
        public final String bone;
        public final float[][] world,uv;
        public final int[] triangles;

        private FramePatch(Patch patch,float[][] positions)
        {
            bone=patch.bone;
            world=clone2(positions);
            uv=clone2(patch.uv);
            triangles=patch.triangles.clone();
        }
    }

    public static final class Binding
    {
        public final List<Patch> patches;
        /** The physical node REST state, not rendered surface XYZ. */
        public final float[][] nodeRest;
        public final float maxMaterialOffset;
        public final float maxRestReconstructionError;
        /** Whether replacing rendered cube corners directly by shared
         * averaged nodes exceeds a conservative static similarity gate. */
        public final boolean directProjectionUnsafe;
        public final float projectedRelativeEdgeChange;

        private Binding(List<Patch> patches,float[][] rest,
                float maxOffset,float maxError,
                boolean unsafe,float edgeChange)
        {
            this.patches=Collections.unmodifiableList(patches);
            nodeRest=clone2(rest);
            maxMaterialOffset=maxOffset;
            maxRestReconstructionError=maxError;
            directProjectionUnsafe=unsafe;
            projectedRelativeEdgeChange=edgeChange;
        }

        /**
         * A future XPBD solver can supply 60 physical node positions.
         * The original material corners remain distinct, with their
         * original rest offsets relative to shared nodes; therefore
         * evaluating the REST frame is visually identical to the source.
         *
         * This does NOT stitch visible gaps, calculate skin normals,
         * remove overlapping cube faces or claim self-collision.
         */
        public List<FramePatch> evaluate(float[][] nodePositions)
        {
            if(nodePositions==null||nodePositions.length!=NODE_COUNT)
                throw new IllegalArgumentException("Need 60 physical node poses");
            for(float[] p:nodePositions)valid(p,3);
            List<FramePatch> out=new ArrayList<FramePatch>(PATCH_COUNT);
            for(Patch patch:patches)
            {
                float[][] corners=new float[4][3];
                for(int v=0;v<4;v++)
                {
                    int n=patch.nodes[v];
                    float[] node=nodePositions[n],offset=patch.materialOffset[v];
                    for(int j=0;j<3;j++)
                        corners[v][j]=node[j]+offset[j];
                }
                out.add(new FramePatch(patch,corners));
            }
            return Collections.unmodifiableList(out);
        }

        public List<FramePatch> atRest()
        {return evaluate(nodeRest);}
    }

    private SkirtClothRestBinding(){}

    public static Binding bind(SkirtClothSurfaceMesh.Snapshot raw,
            SkirtClothSharedSurface.Surface physical)
    {
        if(raw==null||physical==null||
                raw.patches.size()!=PATCH_COUNT||
                physical.patches.size()!=PATCH_COUNT||
                physical.nodes.size()!=NODE_COUNT)
            throw new IllegalArgumentException("Incomplete captured skirt");

        float[][] nodeRest=new float[NODE_COUNT][3];
        for(int n=0;n<NODE_COUNT;n++)
        {
            float[] rest=physical.nodes.get(n).rest;
            valid(rest,3);
            nodeRest[n]=rest.clone();
        }
        List<Patch> patches=new ArrayList<Patch>(PATCH_COUNT);
        float maxOffset=0F,maxError=0F;
        for(int p=0;p<PATCH_COUNT;p++)
        {
            SkirtClothSurfaceMesh.Patch original=raw.patches.get(p);
            SkirtClothSharedSurface.SkinPatch shared=physical.patches.get(p);
            if(original==null||shared==null||
                    !original.bone.equals(shared.bone)||
                    original.lower!=shared.lower)
                throw new IllegalArgumentException("Source patch identity mismatch");
            float[][] offsets=new float[4][3];
            for(int v=0;v<4;v++)
            {
                valid(original.world[v],3);
                valid(original.uv[v],2);
                valid(shared.uv[v],2);
                int node=shared.nodes[v];
                if(node<0||node>=NODE_COUNT)
                    throw new IllegalArgumentException("Invalid physical binding");
                for(int k=0;k<2;k++)
                    if(Float.floatToIntBits(original.uv[v][k])!=
                            Float.floatToIntBits(shared.uv[v][k]))
                        throw new IllegalArgumentException("Native UV altered");
                float[] root=nodeRest[node];
                for(int j=0;j<3;j++)
                {
                    if(Math.abs(shared.rest[v][j]-root[j])>EPS)
                        throw new IllegalArgumentException(
                                "Physical surface node is inconsistent");
                    offsets[v][j]=original.world[v][j]-root[j];
                }
                maxOffset=Math.max(maxOffset,length(offsets[v]));
                // Quantify FP reconstruction error instead of promising
                // mathematically exact float equality.
                maxError=Math.max(maxError,
                        distance(add(root,offsets[v]),original.world[v]));
            }
            patches.add(new Patch(original,shared,offsets));
        }
        boolean unsafe=physical.flippedTriangles>0||
                physical.degenerateTriangles>0||
                physical.maxRelativeEdgeChange>.10F||
                physical.maxRestCorrection>.025F;
        return new Binding(patches,nodeRest,maxOffset,maxError,
                unsafe,physical.maxRelativeEdgeChange);
    }

    private static float[] valid(float[] data,int size)
    {
        if(data==null||data.length!=size)
            throw new IllegalArgumentException("Malformed cloth XYZ/UV");
        for(float x:data)if(!Float.isFinite(x))
            throw new IllegalArgumentException("Non-finite cloth XYZ/UV");
        return data;
    }
    private static float[][] clone2(float[][] src)
    {
        float[][] out=new float[src.length][];
        for(int i=0;i<src.length;i++)out[i]=src[i].clone();
        return out;
    }
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static float distance(float[] a,float[] b)
    {return length(new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]});}
    private static float length(float[] p)
    {return (float)Math.sqrt(p[0]*p[0]+p[1]*p[1]+p[2]*p[2]);}
}
