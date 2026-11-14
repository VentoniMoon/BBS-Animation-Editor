package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Pure Java 8 test of native-quad -> textured skirt surface extraction. */
public final class SkirtClothSurfaceMeshSmokeTest
{
    private SkirtClothSurfaceMeshSmokeTest(){}

    public static void main(String[] args)
    {
        decodedUvAndRestGeometryAreExact();
        sharedStaticSurfaceKeepsOriginalUvsAndClosesAllSeams();
        sharedSurfaceTopologyIsDeterministic();
        bothSeamEndpointOrdersAreCompared();
        mirroredBackAndRotatedSectionsHaveStableTopology();
        missingCubeAndSingularMatrixFailClosed();
        System.out.println("SkirtClothSurfaceMeshSmokeTest OK");
    }

    private static void decodedUvAndRestGeometryAreExact()
    {
        List<SkirtClothSurfaceMesh.Strip> strips=fixture();
        SkirtClothSurfaceMesh.Snapshot mesh=
                SkirtClothSurfaceMesh.capture(strips);
        require(mesh.patches.size()==40,"20x2 decoded outward quads");
        require(mesh.triangleCount==80,
                "two original triangles per outward quad");
        require(mesh.seams.size()==60,
                "20 mid joins + 20 upper + 20 lower side joins");
        require(mesh.maxRoundTripError<.00001F,
                "model->world->model roundtrip changed a corner");
        require(mesh.maxRestGap<.10F,
                "fixture should be continuous without runtime welding");
        require(mesh.max[1]>mesh.min[1],
                "world height must not collapse");
        for(SkirtClothSurfaceMesh.Patch p:mesh.patches)
        {
            require(p.world.length==4&&p.uv.length==4,
                    "decoded quad layout must be preserved");
            require(p.triangles.length==6,
                    "original quad has exactly two faces");
            SkirtClothSurfaceMesh.Cube cube=find(strips,p.bone);
            // External +Z face is the very first real decoded quad
            // in our fixture. This comparison proves the builder did
            // not invent averaged UVs while finding the outward skin.
            for(int v=0;v<4;v++)
                for(int k=0;k<2;k++)
                    close(p.uv[v][k],cube.quads.get(0).vertices[v].uv[k],
                            0F,"original ModelVertex.uv changed");
        }
    }

    private static void sharedStaticSurfaceKeepsOriginalUvsAndClosesAllSeams()
    {
        SkirtClothSurfaceMesh.Snapshot original=
                SkirtClothSurfaceMesh.capture(fixture());
        SkirtClothSharedSurface.Surface united=
                SkirtClothSharedSurface.connect(original);
        require(united.nodes.size()==60,"20 x 3 physical rest lattice");
        require(united.patches.size()==40&&united.triangleCount==80,
                "surface still uses exactly the 40 original textured quads");
        require(united.rowCounts[0]==20&&united.rowCounts[1]==20&&
                united.rowCounts[2]==20,
                "20 waist, 20 hinge and 20 hem rest particles");
        require(united.residualSeamGap<1E-7F,
                "common node must remove candidate edge discontinuity");
        require(united.maxRestCorrection<=2F*original.maxRestGap+.001F,
                "cannot silently drag a fabric vertex across the entire skirt");
        require(united.flippedTriangles==0&&united.degenerateTriangles==0,
                "rest stitch cannot reverse or collapse a textured patch");
        java.util.Map<String,SkirtClothSharedSurface.SkinPatch> byName=
                new java.util.HashMap<String,SkirtClothSharedSurface.SkinPatch>();
        for(int p=0;p<original.patches.size();p++)
        {
            SkirtClothSurfaceMesh.Patch old=original.patches.get(p);
            SkirtClothSharedSurface.SkinPatch mapped=united.patches.get(p);
            require(byName.put(mapped.bone,mapped)==null,
                    "each authored skin quad should remain separate");
            require(mapped.bone.equals(old.bone),
                    "original quad order must survive the seam stitching");
            for(int v=0;v<4;v++)
            {
                require(mapped.nodes[v]>=0&&mapped.nodes[v]<60,
                        "valid shared rest particle index");
                for(int u=0;u<2;u++)
                    close(mapped.uv[v][u],old.uv[v][u],0F,
                            "UV seam must not be averaged");
            }
        }
        for(SkirtClothSurfaceMesh.Seam seam:original.seams)
        {
            SkirtClothSharedSurface.SkinPatch a=byName.get(seam.a);
            SkirtClothSharedSurface.SkinPatch b=byName.get(seam.b);
            for(int end=0;end<2;end++)
            {
                int ia=seam.aEndpoints[end],ib=seam.bEndpoints[end];
                require(a.nodes[ia]==b.nodes[ib],
                        "claimed seam still has two physical particles");
                for(int k=0;k<3;k++)
                    close(a.rest[ia][k],b.rest[ib][k],0F,
                            "shared seam still separates in world space");
            }
        }
    }

    private static void sharedSurfaceTopologyIsDeterministic()
    {
        SkirtClothSurfaceMesh.Snapshot captured=
                SkirtClothSurfaceMesh.capture(fixture());
        SkirtClothSharedSurface.Surface first=
                SkirtClothSharedSurface.connect(captured);
        SkirtClothSharedSurface.Surface second=
                SkirtClothSharedSurface.connect(captured);
        require(first.nodes.size()==second.nodes.size(),
                "repeated source must preserve node count");
        for(int n=0;n<first.nodes.size();n++)
        {
            SkirtClothSharedSurface.Node x=first.nodes.get(n);
            SkirtClothSharedSurface.Node y=second.nodes.get(n);
            require(x.row==y.row&&x.contributors==y.contributors,
                    "node identity or contributor count changed");
            for(int k=0;k<3;k++)
                close(x.rest[k],y.rest[k],0F,
                        "rest nodes must remain frame deterministic");
        }
        for(int p=0;p<first.patches.size();p++)
            for(int v=0;v<4;v++)
                require(first.patches.get(p).nodes[v]==
                        second.patches.get(p).nodes[v],
                        "UV-owned quad references a different particle");
    }

    private static void bothSeamEndpointOrdersAreCompared()
    {
        float[] leftTop={0,1,0},leftBottom={0,0,0};
        float[] rightTop={.01F,1,0},rightBottom={.01F,0,0};
        require(!SkirtClothSurfaceMesh.reversedIsCloser(
                leftTop,leftBottom,rightTop,rightBottom),
                "already matched endpoints must not flip");
        require(SkirtClothSurfaceMesh.reversedIsCloser(
                leftTop,leftBottom,rightBottom,rightTop),
                "mirrored or reversed face must reverse its seam endpoints");
        SkirtClothSurfaceMesh.Snapshot mesh=
                SkirtClothSurfaceMesh.capture(fixture());
        int[] groups=new int[3];
        int reversed=0;
        float assumedMax=0F;
        for(SkirtClothSurfaceMesh.Seam seam:mesh.seams)
        {
            int category="upper".equals(seam.category)?0:
                    "lower".equals(seam.category)?1:
                    "vertical".equals(seam.category)?2:-1;
            require(category>=0,"unknown seam category");
            groups[category]++;
            float selected=seam.reversed?seam.reverseGap:seam.forwardGap;
            close(seam.restGap,selected,0F,
                    "stored seam score must use selected pairing");
            require(seam.restGap<=seam.forwardGap+.000001F,
                    "orientation-aware match cannot widen an existing gap");
            assumedMax=Math.max(assumedMax,seam.forwardGap);
            if(seam.reversed)reversed++;
        }
        require(groups[0]==20&&groups[1]==20&&groups[2]==20,
                "need exact 20 upper/lower/vertical candidates");
        require(mesh.reversedSeams==reversed,
                "orientation repair count must match raw seam entries");
        close(mesh.maxAssumedGap,assumedMax,0F,
                "legacy one-order gap metric must remain auditable");
    }

    private static void mirroredBackAndRotatedSectionsHaveStableTopology()
    {
        List<SkirtClothSurfaceMesh.Strip> strips=fixture();
        SkirtClothSurfaceMesh.Snapshot a=
                SkirtClothSurfaceMesh.capture(strips);
        SkirtClothSurfaceMesh.Snapshot b=
                SkirtClothSurfaceMesh.capture(strips);
        for(int i=0;i<a.patches.size();i++)
        {
            SkirtClothSurfaceMesh.Patch p=a.patches.get(i);
            SkirtClothSurfaceMesh.Patch q=b.patches.get(i);
            require(p.bone.equals(q.bone),"strip order changed on replay");
            require(p.lower==((i&1)==1),
                    "upper/lower pairing was lost during polar sort");
            for(int j=0;j<4;j++)
                for(int k=0;k<3;k++)
                    close(p.world[j][k],q.world[j][k],0F,
                            "source mesh changed between frames");
        }
        for(int i=0;i<a.seams.size();i++)
        {
            SkirtClothSurfaceMesh.Seam x=a.seams.get(i);
            SkirtClothSurfaceMesh.Seam y=b.seams.get(i);
            close(x.restGap,y.restGap,0F,"seam order unstable");
            require(x.restGap>=0F&&Float.isFinite(x.restGap),
                    "invalid static rest seam gap");
            require(x.aEndpoints.length==2&&x.bEndpoints.length==2,
                    "seam endpoints must be proper pairs");
        }
    }

    private static void missingCubeAndSingularMatrixFailClosed()
    {
        List<SkirtClothSurfaceMesh.Strip> strips=fixture();
        boolean rejected=false;
        try{SkirtClothSurfaceMesh.capture(strips.subList(0,19));}
        catch(IllegalArgumentException e){rejected=true;}
        require(rejected,"incomplete 19-strip ring must fail closed");

        SkirtClothSurfaceMesh.Cube old=strips.get(0).upper;
        float[] singular=old.restMatrix.clone();
        singular[0]=0F;singular[1]=0F;singular[2]=0F;
        List<SkirtClothSurfaceMesh.Strip> broken=fixture();
        broken.set(0,new SkirtClothSurfaceMesh.Strip(
                new SkirtClothSurfaceMesh.Cube(old.bone,singular,old.quads),
                broken.get(0).lower));
        rejected=false;
        try{SkirtClothSurfaceMesh.capture(broken);}
        catch(IllegalArgumentException e){rejected=true;}
        require(rejected,"singular cube must not enter texture binding");
    }

    private static SkirtClothSurfaceMesh.Cube find(
            List<SkirtClothSurfaceMesh.Strip> strips,String name)
    {
        for(SkirtClothSurfaceMesh.Strip p:strips)
        {
            if(name.equals(p.upper.bone))return p.upper;
            if(name.equals(p.lower.bone))return p.lower;
        }
        throw new AssertionError("Missing source "+name);
    }
    private static List<SkirtClothSurfaceMesh.Strip> fixture()
    {
        List<SkirtClothSurfaceMesh.Strip> list=
                new ArrayList<SkirtClothSurfaceMesh.Strip>();
        for(int i=0;i<20;i++)
        {
            double a=2D*Math.PI*i/20D;
            list.add(new SkirtClothSurfaceMesh.Strip(
                    cube("upper"+i,a,.45F,0F,-.23F,i,
                            i==12),
                    cube("lower"+i,a,.47F,-.23F,-.41F,i+20,
                            i==12)));
        }
        return list;
    }

    private static SkirtClothSurfaceMesh.Cube cube(String name,
            double angle,float radius,float top,float bottom,
            int texIndex,boolean mirror)
    {
        // Local +Z is the outward face, with a 180-degree-ish
        // orientation around the ring and a mirrored authored side.
        float a=(float)(Math.PI*.5D-angle);
        float c=(float)Math.cos(a),s=(float)Math.sin(a);
        float sign=mirror?-1F:1F;
        float[] matrix={c*sign,0,-s*sign,0,
                        0,1,0,0,
                        s,0,c,0,
                        radius*(float)Math.cos(angle),0,
                        radius*(float)Math.sin(angle),1};
        float l=-.068F,r=.068F,z=.030F;
        List<SkirtClothSurfaceMesh.Quad> quads=
                new ArrayList<SkirtClothSurfaceMesh.Quad>();
        quads.add(face(texIndex,new float[]{0,0,1},
                xyz(l,bottom,z),xyz(r,bottom,z),
                xyz(r,top,z),xyz(l,top,z)));
        quads.add(face(texIndex+1,new float[]{0,0,-1},
                xyz(r,bottom,-z),xyz(l,bottom,-z),
                xyz(l,top,-z),xyz(r,top,-z)));
        quads.add(face(texIndex+2,new float[]{1,0,0},
                xyz(r,bottom,z),xyz(r,bottom,-z),
                xyz(r,top,-z),xyz(r,top,z)));
        quads.add(face(texIndex+3,new float[]{-1,0,0},
                xyz(l,bottom,-z),xyz(l,bottom,z),
                xyz(l,top,z),xyz(l,top,-z)));
        quads.add(face(texIndex+4,new float[]{0,1,0},
                xyz(l,top,z),xyz(r,top,z),
                xyz(r,top,-z),xyz(l,top,-z)));
        quads.add(face(texIndex+5,new float[]{0,-1,0},
                xyz(l,bottom,-z),xyz(r,bottom,-z),
                xyz(r,bottom,z),xyz(l,bottom,z)));
        return new SkirtClothSurfaceMesh.Cube(name,matrix,quads);
    }
    private static SkirtClothSurfaceMesh.Quad face(int tex,
            float[] normal,float[] a,float[] b,float[] c,float[] d)
    {
        float u=(tex%48)/64F,v=((tex+7)%48)/64F;
        SkirtClothSurfaceMesh.Vertex[] verts={
            new SkirtClothSurfaceMesh.Vertex(a,new float[]{u,v}),
            new SkirtClothSurfaceMesh.Vertex(b,new float[]{u+.025F,v}),
            new SkirtClothSurfaceMesh.Vertex(c,new float[]{u+.025F,v+.035F}),
            new SkirtClothSurfaceMesh.Vertex(d,new float[]{u,v+.035F})};
        return new SkirtClothSurfaceMesh.Quad(verts,normal);
    }
    private static float[] xyz(float x,float y,float z)
    {return new float[]{x,y,z};}
    private static void require(boolean condition,String label)
    {if(!condition)throw new AssertionError(label);}
    private static void close(float x,float y,float eps,String label)
    {require(Math.abs(x-y)<=eps,label+" "+x+" vs "+y);}
}
