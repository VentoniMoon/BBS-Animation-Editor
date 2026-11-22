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
