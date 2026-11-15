package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physics 3.0 / Stage 2: one connected TEXTURED fabric surface built from
 * Chameleon's 40 exact outer source quads and the 60 verified candidate
 * associations (20 upper sides, 20 lower sides, 20 mid joins).
 *
 * The 45%-edge-stretch averaged-node surface is NEVER rendered. Original
 * material positions and UV are preserved at each of 160 source corners;
 * missing side/vertical space is covered by two thin, UV-edge-constant
 * bridge quads per seam. This uses a single shared 60-node PHYSICS graph,
 * but separate per-face UV corners, so a seam may change colors sharply
 * without creating a hole or dragging the authored silhouette.
 *
 * The historical 43-cuboid volume remains separate reference geometry.
 * This skin is a two-sided zero-thickness SHELL; collision against legs
 * is a physical solver concern and source skirt inner/cap textures are
 * not implied to be a seamless cloth atlas.
 */
public final class SkirtXPBDSurfaceTopology
{
    public static final int SOURCE_QUADS=40;
    public static final int SEAM_PAIRS=60;
    public static final int CONNECTED_QUADS=SOURCE_QUADS+2*SEAM_PAIRS;

    /** Source anchor on one of the 40 native material quads. */
    public static final class Corner
    {
        public final int patchA,cornerA,patchB,cornerB;
        /** 0=source A, 1=source B, 0.5=middle of the two. */
        public final float mix;
        /** The original Chameleon UV, deliberately unchanged/duplicated. */
        public final float[] uv;

        private Corner(int pa,int ca,int pb,int cb,float mix,float[] uv)
        {
            if(pa<0||pa>=SOURCE_QUADS||ca<0||ca>3||
                    pb<0||pb>=SOURCE_QUADS||cb<0||cb>3||
                    mix<0F||mix>1F||uv==null||uv.length!=2)
                throw new IllegalArgumentException("Invalid material corner");
            this.patchA=pa;this.cornerA=ca;
            this.patchB=pb;this.cornerB=cb;
            this.mix=mix;this.uv=uv.clone();
        }
    }

    /** One material quad; 'source' means original skin, otherwise bridge. */
    public static final class Quad
    {
        public final Corner[] vertices;
        public final int materialPatch;
        public final boolean bridge;
        public final String seamType;
        private Quad(Corner[] c,int materialPatch,boolean bridge,
                String seamType)
        {
            if(c==null||c.length!=4)
                throw new IllegalArgumentException("Need 4 material corners");
            this.vertices=c.clone();
            this.materialPatch=materialPatch;
            this.bridge=bridge;
            this.seamType=seamType;
        }
    }
    public static final class Topology
    {
        public final List<Quad> quads;
        public final int nativeCorners,sharedNodes,bridgeQuads;
        private Topology(List<Quad> quads)
        {
            this.quads=Collections.unmodifiableList(quads);
            nativeCorners=40*4;
            sharedNodes=60;
            bridgeQuads=2*SEAM_PAIRS;
        }
        /** Recover an exact source UV/XYZ at rest when material stays still,
         * or an interpolated bridge at the current actor/XPBD pose. */
        public float[] position(Quad q,Corner c,
                SkirtXPBDPoseSkin.Frame pose,
                SkirtClothRestBinding.Binding binding,float[][] particles)
        {
            float[] a=pose.corner(binding,particles,c.patchA,c.cornerA);
            float[] b=pose.corner(binding,particles,c.patchB,c.cornerB);
            return new float[]{a[0]+(b[0]-a[0])*c.mix,
                    a[1]+(b[1]-a[1])*c.mix,
                    a[2]+(b[2]-a[2])*c.mix};
        }
    }

    private SkirtXPBDSurfaceTopology(){}

    public static Topology connect(SkirtClothSurfaceMesh.Snapshot source,
            SkirtClothRestBinding.Binding binding)
    {
        if(source==null||binding==null||
                source.patches.size()!=SOURCE_QUADS||
                binding.patches.size()!=SOURCE_QUADS||
                source.seams.size()!=SEAM_PAIRS)
            throw new IllegalArgumentException("Incomplete source skin");
        Map<String,Integer> boneIndex=new HashMap<String,Integer>();
        List<Quad> out=new ArrayList<Quad>(CONNECTED_QUADS);
        for(int i=0;i<SOURCE_QUADS;i++)
        {
            SkirtClothSurfaceMesh.Patch original=source.patches.get(i);
            SkirtClothRestBinding.Patch bound=binding.patches.get(i);
            if(!original.bone.equals(bound.bone)||boneIndex.put(
                    original.bone,i)!=null)
                throw new IllegalArgumentException("Broken patch identity");
            Corner[] vertices=new Corner[4];
            for(int j=0;j<4;j++)
                vertices[j]=new Corner(i,j,i,j,0F,original.uv[j]);
            out.add(new Quad(vertices,i,false,"source"));
        }

        int horizontal=0,vertical=0;
        for(SkirtClothSurfaceMesh.Seam seam:source.seams)
        {
            Integer ia=boneIndex.get(seam.a);
            Integer ib=boneIndex.get(seam.b);
            if(ia==null||ib==null||ia.equals(ib)||
                    seam.aEndpoints.length!=2||seam.bEndpoints.length!=2)
                throw new IllegalArgumentException("Unmapped surface seam");
            if("vertical".equals(seam.category))vertical++;
            else if("upper".equals(seam.category)||
                    "lower".equals(seam.category))horizontal++;
            else throw new IllegalArgumentException("Unknown seam category");
            SkirtClothSurfaceMesh.Patch a=source.patches.get(ia);
            SkirtClothSurfaceMesh.Patch b=source.patches.get(ib);
            int a0=seam.aEndpoints[0],a1=seam.aEndpoints[1],
                    b0=seam.bEndpoints[0],b1=seam.bEndpoints[1];
            if(binding.patches.get(ia).nodes[a0]!=
                    binding.patches.get(ib).nodes[b0] ||
                    binding.patches.get(ia).nodes[a1]!=
                    binding.patches.get(ib).nodes[b1])
                throw new IllegalArgumentException("Seam not physically joined");
            // Fill half of the original source gap by extending color at
            // the source edge (never interpolate across different UV islands).
            Corner[] sideA={
                    new Corner(ia,a0,ia,a0,0F,a.uv[a0]),
                    new Corner(ia,a1,ia,a1,0F,a.uv[a1]),
                    new Corner(ia,a1,ib,b1,.5F,a.uv[a1]),
                    new Corner(ia,a0,ib,b0,.5F,a.uv[a0])
            };
            // The second half uses the neighbor's own UV edge; its
            // color may differ, but the geometry cannot have a gap.
            Corner[] sideB={
                    new Corner(ia,a0,ib,b0,.5F,b.uv[b0]),
                    new Corner(ia,a1,ib,b1,.5F,b.uv[b1]),
                    new Corner(ib,b1,ib,b1,0F,b.uv[b1]),
                    new Corner(ib,b0,ib,b0,0F,b.uv[b0])
            };
            out.add(new Quad(sideA,ia,true,seam.category));
            out.add(new Quad(sideB,ib,true,seam.category));
        }
        if(horizontal!=40||vertical!=20||
                out.size()!=CONNECTED_QUADS)
            throw new IllegalArgumentException("Incomplete fabric ring");
        return new Topology(out);
    }
}
