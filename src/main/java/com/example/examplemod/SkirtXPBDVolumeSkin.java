package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental cloth visual fidelity A/B:
 * Re-use ALL 43 authored Chameleon cubes (258 native face quads), with the
 * EXACT source UV of every cap/side/front/back face. Deform each FABRIC cube
 * using interpolated XPBD 60-node deltas of its own 4 source-skin corners.
 *
 * This keeps a shared physics rig, but is deliberately a VOLUMETRIC visual
 * baseline, NOT a truly watertight single-surface cloth replacement.
 * The previous 40-front-quad + 120-bridge renderer stays available for
 * future topology work. Everything here is pure Java 8 and immutable.
 */
public final class SkirtXPBDVolumeSkin
{
    // Preserve the full 43-cuboid material/UV skin verified in the
    // earlier walking tests. Thin-shell seam bridges remain opt-in only.
    public static final boolean ENABLED=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdVolumeSkin","true"));
    private static final int PATCHES=40;
    private static final int CUBES=43;
    private static final float EPS=1E-8F;

    public static final class Face
    {
        public final String bone;
        public final boolean waist;
        public final float[][] world,uv;
        public final float[] normal,material;
        private Face(String bone,boolean waist,float[][] p,float[][] uv,
                float[] normal,float[] material)
        {
            this.bone=bone;
            this.waist=waist;
            this.world=copy(p);
            this.uv=copy(uv);
            this.normal=normal.clone();
            this.material=material.clone();
        }
    }

    public static final class Mesh
    {
        public final List<Face> faces;
        public final int cubes,quads,waistQuads,fabricQuads;
        public final float maxNodeDisplacement,maxVisualDisplacement;
        private Mesh(List<Face> faces,int waist,int fabric,
                float nodeShift,float visualShift)
        {
            this.faces=Collections.unmodifiableList(faces);
            cubes=CUBES;
            quads=faces.size();
            waistQuads=waist;
            fabricQuads=fabric;
            maxNodeDisplacement=nodeShift;
            maxVisualDisplacement=visualShift;
        }
    }
    private SkirtXPBDVolumeSkin(){}

    public static Mesh build(ChameleonSkirtVolumeSnapshot.Snapshot rest,
            ChameleonSkirtVolumeSnapshot.Snapshot posed,
            SkirtClothSurfaceMesh.Snapshot source,
            SkirtClothRestBinding.Binding binding,
            SkirtXPBDPoseSkin.Frame pose,float[][] particles)
    {
        if(rest==null||posed==null||source==null||binding==null||
                pose==null||particles==null||
                rest.cuboidCount!=CUBES||posed.cuboidCount!=CUBES||
                rest.faces.size()!=posed.faces.size()||
                source.patches.size()!=PATCHES||
                binding.patches.size()!=PATCHES||
                particles.length!=60||pose.target.length!=60)
            throw new IllegalArgumentException("Incomplete XPBD native volume");

        Map<String,Integer> byBone=new HashMap<String,Integer>();
        float[][][] nodeDelta=new float[PATCHES][4][3];
        int[] materialFaceCount=new int[PATCHES];
        float maxNodeDelta=0F;
        for(int p=0;p<PATCHES;p++)
        {
            String name=source.patches.get(p).bone;
            if(byBone.put(name,p)!=null||
                    !name.equals(binding.patches.get(p).bone))
                throw new IllegalArgumentException("Source patch mismatch");
            for(int v=0;v<4;v++)
            {
                int node=binding.patches.get(p).nodes[v];
                if(node<0||node>=60)
                    throw new IllegalArgumentException("Invalid cloth particle");
                requireXYZ(particles[node]);
                requireXYZ(pose.target[node]);
                for(int k=0;k<3;k++)
                    nodeDelta[p][v][k]=particles[node][k]-
                            pose.target[node][k];
                maxNodeDelta=Math.max(maxNodeDelta,length(nodeDelta[p][v]));
            }
        }
        List<Face> out=new ArrayList<Face>(posed.faces.size());
        int waistCount=0,fabricCount=0;
        float maxVisual=0F;
        for(int i=0;i<rest.faces.size();i++)
        {
            ChameleonSkirtVolumeSnapshot.Face r=rest.faces.get(i);
            ChameleonSkirtVolumeSnapshot.Face current=posed.faces.get(i);
            if(!r.bone.equals(current.bone))
                throw new IllegalArgumentException(
                        "Native rest/posed cuboid face order mismatch");
            boolean waist="ALL".equals(r.bone);
            Integer patch=byBone.get(r.bone);
            if(!waist&&patch==null)
                throw new IllegalArgumentException(
                        "Unmapped native skirt fabric bone: "+r.bone);
            if(waist)waistCount++;
            else{fabricCount++;materialFaceCount[patch]++;}
            float[][] positions=new float[4][3];
            for(int v=0;v<4;v++)
            {
                requireXYZ(r.world[v]);
                requireXYZ(current.world[v]);
                if(r.uv[v].length!=2||current.uv[v].length!=2)
                    throw new IllegalArgumentException("Missing native UV");
                for(int j=0;j<2;j++)
                    if(Float.floatToIntBits(r.uv[v][j])!=
                            Float.floatToIntBits(current.uv[v][j]))
                        throw new IllegalArgumentException(
                                "Native rest/posed UV mismatch");
                float[] shift=waist?new float[3]:
                        blendedDelta(r.world[v],source.patches.get(patch),
                                nodeDelta[patch]);
                maxVisual=Math.max(maxVisual,length(shift));
                for(int k=0;k<3;k++)
                    positions[v][k]=current.world[v][k]+shift[k];
            }
            out.add(new Face(r.bone,waist,positions,
                    current.uv,current.normal,current.material));
        }
        if(waistCount<3||fabricCount<40)
            throw new IllegalArgumentException("Native waist/fabric missing");
        for(int count:materialFaceCount)
            if(count<1)
                throw new IllegalArgumentException("Fabric quad missing");
        return new Mesh(out,waistCount,fabricCount,
                maxNodeDelta,maxVisual);
    }


    /**
     * Draw a visibly finer cloth without replacing the authored Chameleon
     * volume or changing the old 60-point source UV binding.
     *
     * The production 20x5 solver has two extra horizontal physical rings.
     * Divide every moving original cube face into four native-UV quads and
     * add ONLY the non-linear intermediate-row displacement. The original
     * four corners stay bit-compatible with the verified volume renderer,
     * as do all 18 undisturbed waistband faces.
     *
     * The ordinary build(...,float[][]) remains an exact 258-quad reference
     * for every old model/test. This opt-out is also useful on slow maps.
     */
    public static Mesh build(ChameleonSkirtVolumeSnapshot.Snapshot rest,
            ChameleonSkirtVolumeSnapshot.Snapshot posed,
            SkirtClothSurfaceMesh.Snapshot source,
            SkirtClothRestBinding.Binding binding,
            SkirtXPBDPoseSkin.Frame pose,SkirtXPBDSolver.Result solved)
    {
        if(solved==null)throw new IllegalArgumentException("Missing XPBD result");
        Mesh coarse=build(rest,posed,source,binding,pose,solved.particles);
        boolean refine=SkirtXPBDSolver.ROWS==5 &&
                Boolean.parseBoolean(System.getProperty(
                        "bbs.editor.physics.skirt.xpbdVolumeSubdivision","true"));
        if(!refine)return coarse;
        if(solved.refinedParticles==null||solved.refinedTarget==null||
                solved.refinedParticles.length!=100||
                solved.refinedTarget.length!=100||
                solved.sourceToRefined==null||
                solved.sourceToRefined.length!=60)
            throw new IllegalArgumentException("Incomplete fine cloth skin");
        Map<String,Integer> byBone=new HashMap<String,Integer>();
        float[][][] coarseDelta=new float[PATCHES][4][3];
        float[][][] fineDelta=new float[PATCHES][6][3];
        for(int p=0;p<PATCHES;p++)
        {
            SkirtClothSurfaceMesh.Patch patch=source.patches.get(p);
            if(byBone.put(patch.bone,p)!=null)
                throw new IllegalArgumentException("Duplicate skirt panel");
            int[] node=binding.patches.get(p).nodes;
            for(int v=0;v<4;v++)
            {
                int nativeId=node[v];
                for(int k=0;k<3;k++)
                    coarseDelta[p][v][k]=solved.particles[nativeId][k]-
                            pose.target[nativeId][k];
            }
            int lt=solved.sourceToRefined[node[patch.topLeft]];
            int rt=solved.sourceToRefined[node[patch.topRight]];
            int lb=solved.sourceToRefined[node[patch.bottomLeft]];
            int rb=solved.sourceToRefined[node[patch.bottomRight]];
            if(lb-lt!=40||rb-rt!=40)
                throw new IllegalArgumentException("Invalid 20x5 panel stride");
            int[] indices={lt,rt,lt+20,rt+20,lb,rb};
            for(int v=0;v<indices.length;v++)
                for(int k=0;k<3;k++)
                    fineDelta[p][v][k]=
                            solved.refinedParticles[indices[v]][k]-
                            solved.refinedTarget[indices[v]][k];
        }
        List<Face> out=new ArrayList<Face>(coarse.faces.size()*4);
        float maxVisual=coarse.maxVisualDisplacement;
        for(int i=0;i<coarse.faces.size();i++)
        {
            Face quad=coarse.faces.get(i);
            if(quad.waist)
            {
                out.add(quad);
                continue;
            }
            Integer index=byBone.get(quad.bone);
            if(index==null)
                throw new IllegalArgumentException("Unmapped skirt panel");
            SkirtClothSurfaceMesh.Patch panel=source.patches.get(index);
            float[][] sourceFace=rest.faces.get(i).world;
            // These four subdivided faces share their old exact outside
            // edges; interior points are displaced by the fine ring only.
            for(int y=0;y<2;y++)
                for(int x=0;x<2;x++)
                {
                    float left=x*.5F,right=(x+1)*.5F;
                    float top=y*.5F,bottom=(y+1)*.5F;
                    float[][] uvPairs={{left,top},{right,top},
                            {right,bottom},{left,bottom}};
                    float[][] world=new float[4][3],uv=new float[4][2];
                    for(int v=0;v<4;v++)
                    {
                        float u=uvPairs[v][0],t=uvPairs[v][1];
                        float[] restPoint=bilinear(sourceFace,u,t);
                        float[] basePoint=bilinear(quad.world,u,t);
                        float[] original=blendedDelta(restPoint,panel,
                                coarseDelta[index]);
                        float[] refined=blendedFineDelta(restPoint,panel,
                                fineDelta[index]);
                        for(int k=0;k<3;k++)
                            world[v][k]=basePoint[k]+refined[k]-original[k];
                        uv[v]=bilinear(quad.uv,u,t);
                        maxVisual=Math.max(maxVisual,
                                length(sub(world[v],bilinear(
                                        posed.faces.get(i).world,u,t))));
                    }
                    out.add(new Face(quad.bone,false,world,uv,
                            quad.normal,quad.material));
                }
        }
        return new Mesh(out,coarse.waistQuads,
                coarse.fabricQuads*4,
                coarse.maxNodeDisplacement,maxVisual);
    }

    /** Refined interpolation matches the native source corners exactly. */
    private static float[] blendedFineDelta(float[] xyz,
            SkirtClothSurfaceMesh.Patch patch,float[][] delta)
    {
        float[] tl=patch.world[patch.topLeft],
                tr=patch.world[patch.topRight],
                bl=patch.world[patch.bottomLeft],
                br=patch.world[patch.bottomRight];
        float[] tc=average(tl,tr),bc=average(bl,br);
        float[] down=sub(bc,tc);
        float dd=dot(down,down);
        if(dd<EPS)throw new IllegalArgumentException("Collapsed fabric height");
        float t=clamp(dot(sub(xyz,tc),down)/dd);
        float[] left=lerp(tl,bl,t),right=lerp(tr,br,t);
        float[] across=sub(right,left);
        float ww=dot(across,across);
        if(ww<EPS)throw new IllegalArgumentException("Collapsed fabric width");
        float u=clamp(dot(sub(xyz,left),across)/ww);

        int row=t<.5F?0:1;
        float inner=t*2F-row;
        float[] upper=lerp(delta[row*2],delta[row*2+1],u);
        float[] lower=lerp(delta[(row+1)*2],delta[(row+1)*2+1],u);
        return lerp(upper,lower,inner);
    }

    private static float[] bilinear(float[][] quad,float u,float v)
    {
        if(quad==null||quad.length!=4)
            throw new IllegalArgumentException("Incomplete volume quad");
        float[] out=new float[quad[0].length];
        for(int i=0;i<out.length;i++)
            out[i]=quad[0][i]*(1F-u)*(1F-v)+
                    quad[1][i]*u*(1F-v)+
                    quad[2][i]*u*v+quad[3][i]*(1F-u)*v;
        return out;
    }

    /** Bilinear approximation inside a cuboid using its real chosen
     * outer face as the parameter plane. Works for caps, back, sides and
     * rotated/mirrored panels, without touching their source UVs. */
    private static float[] blendedDelta(float[] xyz,
            SkirtClothSurfaceMesh.Patch patch,float[][] delta)
    {
        float[] tl=patch.world[patch.topLeft],
                tr=patch.world[patch.topRight],
                bl=patch.world[patch.bottomLeft],
                br=patch.world[patch.bottomRight];
        float[] tc=average(tl,tr),bc=average(bl,br);
        float[] down=sub(bc,tc);
        float dd=dot(down,down);
        if(dd<EPS)throw new IllegalArgumentException("Collapsed fabric height");
        float t=clamp(dot(sub(xyz,tc),down)/dd);
        float[] left=lerp(tl,bl,t),right=lerp(tr,br,t);
        float[] across=sub(right,left);
        float ww=dot(across,across);
        if(ww<EPS)throw new IllegalArgumentException("Collapsed fabric width");
        float u=clamp(dot(sub(xyz,left),across)/ww);

        float[] top=lerp(delta[patch.topLeft],delta[patch.topRight],u);
        float[] bottom=lerp(delta[patch.bottomLeft],
                delta[patch.bottomRight],u);
        return lerp(top,bottom,t);
    }

    private static float[] average(float[] a,float[] b)
    {return new float[]{(a[0]+b[0])*.5F,(a[1]+b[1])*.5F,
            (a[2]+b[2])*.5F};}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return new float[]{a[0]+(b[0]-a[0])*t,
            a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float length(float[] a)
    {return (float)Math.sqrt(dot(a,a));}
    private static float clamp(float n)
    {return Math.max(0F,Math.min(1F,n));}
    private static void requireXYZ(float[] xyz)
    {
        if(xyz==null||xyz.length!=3)
            throw new IllegalArgumentException("Invalid native XYZ");
        for(float f:xyz)
            if(!Float.isFinite(f))
                throw new IllegalArgumentException("Nonfinite native XYZ");
    }
    private static float[][] copy(float[][] a)
    {
        float[][] b=new float[a.length][];
        for(int i=0;i<a.length;i++)b[i]=a[i].clone();
        return b;
    }
}
