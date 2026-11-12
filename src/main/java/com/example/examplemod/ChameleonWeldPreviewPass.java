package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Vector3f;

import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * EXPERIMENTAL, opt-in Chameleon hair seam welding in the editor Preview.
 *
 * The ModelCube quad positions are shared model resources. We snapshot every
 * modified ModelVertex and restore them as soon as this actor's render ends.
 * No source geometry, Jolt state, keys or exported Record data are changed.
 *
 * Enabled with -Dbbs.editor.weld.preview=true, default OFF.
 *
 * Initial scope: static Chameleon models with exactly one unrotated cube
 * per parent/child strand bone and coincident DOWN/UP rest faces.
 * More complex bindings and curved falloff need a separate renderer stage.
 */
public final class ChameleonWeldPreviewPass implements AutoCloseable
{
    private static final float FACE_EPS=1E-5F;
    private static final float MAX_REST_GAP=0.003F;
    private static final boolean SKIRT_RING=Boolean.parseBoolean(
            System.getProperty("bbs.editor.weld.skirtRing","true"));
    /** The verified 20x2 source skirt.geo.json, in any runtime bone order. */
    private static final String[][] SKIRT_BONES={
            {"group2","group22"},{"group6","group24"},
            {"group3","group25"},{"group4","group23"},
            {"group5","group26"},{"group18","group29"},
            {"group17","group27"},{"group7","group38"},
            {"group16","group39"},{"group8","group40"},
            {"group9","group37"},{"group10","group41"},
            {"group20","group35"},{"group19","group36"},
            {"group14","group31"},{"group15","group32"},
            {"group21","group30"},{"group13","group34"},
            {"group12","group28"},{"group11","group33"}};
    private static boolean reportedFailure;
    private static boolean reportedSuccess;

    private final IdentityHashMap<Vector3f,float[]> originals=
            new IdentityHashMap<Vector3f,float[]>();
    private int seams;

    private ChameleonWeldPreviewPass() {}

    /**
     * Entry for the real ChameleonRenderer hook (Blockbuster playback,
     * world rendering, and Preview). Unlike begin(morphs), the library has
     * already applied the exact frame's pose to ModelBone.current.
     */
    public static ChameleonWeldPreviewPass beginPreparedModel(Object model)
    {
        ChameleonWeldPreviewPass pass=new ChameleonWeldPreviewPass();
        if(model==null)return pass;
        try
        {
            pass.prepareModel(model);
        }
        catch(Exception e)
        {
            pass.close();
            throw new IllegalStateException("Chameleon Weld geometry failed",e);
        }
        return pass;
    }

    public int getSeamCount()
    {
        return seams;
    }

    public static ChameleonWeldPreviewPass begin(
            Iterable<? extends AbstractMorph> childMorphs,float partialTicks)
    {
        ChameleonWeldPreviewPass pass=new ChameleonWeldPreviewPass();
        if(!ChameleonWeldRuntime.isEnabled() || childMorphs==null)return pass;
        try
        {
            for(AbstractMorph morph:childMorphs)
                pass.prepareMorph(morph,partialTicks);
            if(pass.seams>0 && !reportedSuccess)
            {
                reportedSuccess=true;
                System.out.println("[BBS Editor][Weld] Preview seam welding active, "+
                        pass.seams+" matching joints (render-only; Jolt untouched)");
            }
        }
        catch(Exception failure)
        {
            pass.close();
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println("[BBS Editor][Weld] Disabled this preview pass: "+
                        failure.getClass().getSimpleName()+": "+failure.getMessage());
            }
        }
        return pass;
    }

    @SuppressWarnings("unchecked")
    private void prepareMorph(AbstractMorph morph,float partialTicks)
            throws Exception
    {
        if(morph==null || !"mchorse.chameleon.metamorph.ChameleonMorph"
                .equals(morph.getClass().getName()) ||
                !ChameleonWeldRuntime.isMorphWeldEnabled(morph))
            return;
        Object wrapper=morph.getClass().getMethod("getModel").invoke(morph);
        if(wrapper==null ||
                !Boolean.TRUE.equals(wrapper.getClass().getMethod("isStatic")
                        .invoke(wrapper)))
            return; // Avoid overriding built-in action animations.
        Object model=field(wrapper,"model");
        if(model==null)return;

        Class<?> modelClass=model.getClass();
        // The original renderer runs these same operations immediately before
        // ChameleonRenderer.render(). Pre-evaluate only STATIC model poses so
        // that the seam solver sees the current exported/editor keyframe.
        Class<?> animator=Class.forName("mchorse.chameleon.lib.ChameleonAnimator");
        animator.getMethod("resetPose",modelClass).invoke(null,model);
        Method apply=morph.getClass().getDeclaredMethod(
                "applyPose",modelClass,float.class);
        apply.setAccessible(true);
        apply.invoke(morph,model,partialTicks);
        prepareModel(model);
    }

    @SuppressWarnings("unchecked")
    private void prepareModel(Object model)throws Exception
    {
        List<Object> roots=(List<Object>)field(model,"bones");
        List<Bone> bones=new ArrayList<Bone>();
        for(Object root:roots)
            collectBone(root,null,identity(),identity(),bones);
        Set<String> ringMembers=SKIRT_RING ?
                weldSkirtRing(bones):java.util.Collections.<String>emptySet();
        for(Bone bone:bones)
            if(bone.parent!=null && !ringMembers.contains(bone.name))
                weldJoint(bone.parent,bone);
    }

    @SuppressWarnings("unchecked")
    private void collectBone(Object nativeBone,Bone parent,
            float[] restParent,float[] poseParent,List<Bone> output)
            throws Exception
    {
        Bone b=new Bone();
        b.value=nativeBone;
        b.name=(String)field(nativeBone,"id");
        b.parent=parent;
        b.rest=mul(restParent,boneMatrix(nativeBone,true));
        b.posed=mul(poseParent,boneMatrix(nativeBone,false));
        output.add(b);
        for(Object child:(List<Object>)field(nativeBone,"children"))
            collectBone(child,b,b.rest,b.posed,output);
    }

    @SuppressWarnings("unchecked")
    private void weldJoint(Bone parent,Bone child)throws Exception
    {
        List<Object> above=(List<Object>)field(parent.value,"cubes");
        List<Object> below=(List<Object>)field(child.value,"cubes");
        // No automatic one-to-many welding until a user-selectable binding
        // exists; prevents applying hair-only heuristics to unrelated models.
        if(above.size()!=1||below.size()!=1)return;
        Object topCube=above.get(0),bottomCube=below.get(0);
        if(hasRotation(topCube)||hasRotation(bottomCube))return;

        float[][] upper=face(topCube,false);
        float[][] lower=face(bottomCube,true);
        if(upper==null||lower==null)return;
        ChameleonWeldGeometry.Seam seam=ChameleonWeldGeometry.weld(
                parent.rest,child.rest,parent.posed,child.posed,
                upper,lower,0.5F,MAX_REST_GAP);
        if(seam==null)return;
        patchFace(topCube,upper,seam.sourceLocal);
        patchFace(bottomCube,lower,seam.targetLocal);
        seams++;
    }

    private static final class SkirtCubeBinding
    {
        final Object cube;
        final float[][] local;
        final float[] posed;
        final SkirtContinuityRing.Cube sample;
        SkirtCubeBinding(Object cube,float[][] local,float[] posed,
                SkirtContinuityRing.Cube sample)
        {
            this.cube=cube;this.local=local;
            this.posed=posed;this.sample=sample;
        }
    }

    /**
     * The original Weld only joined parent/child DOWN/UP faces. For the
     * 20x2 skirt we also link the two sideways edges of every neighboring
     * strip, closing the ring *before* Chameleon submits any triangles.
     * Topology is inferred from rest-world positions, not a hardcoded
     * front/back cube rotation or the model's authoring order.
     *
     * All edits are temporary ModelVertex position patches, restored on
     * close(). No Jolt/animation/bone/NBT source data is changed.
     */
    @SuppressWarnings("unchecked")
    private Set<String> weldSkirtRing(List<Bone> bones)throws Exception
    {
        if(!SKIRT_RING)return java.util.Collections.emptySet();
        Map<String,Bone> byName=new HashMap<String,Bone>();
        for(Bone bone:bones)
            if(bone.name!=null)byName.put(bone.name,bone);
        if(!byName.containsKey("ALL")||!byName.containsKey("group42")||
                !byName.containsKey("group43"))
            return java.util.Collections.emptySet();

        Map<String,SkirtCubeBinding> bindings=
                new HashMap<String,SkirtCubeBinding>();
        List<SkirtContinuityRing.Strip> strips=
                new ArrayList<SkirtContinuityRing.Strip>();
        for(String[] names:SKIRT_BONES)
        {
            Bone upper=byName.get(names[0]),lower=byName.get(names[1]);
            if(upper==null||lower==null||lower.parent!=upper)
                return java.util.Collections.emptySet();
            if(!Boolean.TRUE.equals(field(upper.value,"visible"))||
                    !Boolean.TRUE.equals(field(lower.value,"visible")))
                return java.util.Collections.emptySet();
            SkirtCubeBinding top=binding(upper),bottom=binding(lower);
            if(top==null||bottom==null)
                return java.util.Collections.emptySet();
            bindings.put(names[0],top);
            bindings.put(names[1],bottom);
            strips.add(new SkirtContinuityRing.Strip(
                    top.sample,bottom.sample));
        }

        SkirtContinuityRing.Result ring=SkirtContinuityRing.stitch(strips);
        if(ring.joinedVertices==0)return java.util.Collections.emptySet();

        for(Map.Entry<String,SkirtCubeBinding> entry:bindings.entrySet())
        {
            float[][] world=ring.targets.get(entry.getKey());
            if(world==null)continue;
            SkirtCubeBinding binding=entry.getValue();
            float[] inverse=ChameleonWeldGeometry.inverseAffine(
                    binding.posed);
            float[][] localResult=new float[8][3];
            for(int corner=0;corner<8;corner++)
                localResult[corner]=ChameleonWeldGeometry.transform(
                        inverse,world[corner]);
            patchSkirtCube(binding.cube,binding.local,localResult);
        }
        seams+=ring.lateralSeams+ring.verticalSeams;
        return new HashSet<String>(bindings.keySet());
    }

    @SuppressWarnings("unchecked")
    private static SkirtCubeBinding binding(Bone bone)throws Exception
    {
        List<Object> cubes=(List<Object>)field(bone.value,"cubes");
        if(cubes==null||cubes.size()!=1)return null;
        Object cube=cubes.get(0);
        List<Object> quads=(List<Object>)field(cube,"quads");
        if(quads==null||quads.isEmpty())return null;
        Vector3f o=vec(cube,"origin"),size=vec(cube,"size"),
                pivot=vec(cube,"pivot"),rot=vec(cube,"rotation");
        float inflate=((Number)field(cube,"inflate")).floatValue();
        float[] min={(o.x-size.x-inflate)/16F,
                (o.y-inflate)/16F,(o.z-inflate)/16F};
        float[] max={(o.x+inflate)/16F,
                (o.y+size.y+inflate)/16F,(o.z+size.z+inflate)/16F};
        float[][] original=new float[8][3];
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++)
            original[x*4+y*2+z]=new float[]{
                    x==0?min[0]:max[0],
                    y==0?min[1]:max[1],
                    z==0?min[2]:max[2]};

        float[] cubeRotation=mul(
                translate(pivot.x/16F,pivot.y/16F,pivot.z/16F),
                mul(rotationZ(rot.z),
                mul(rotationY(rot.y),mul(rotationX(rot.x),
                translate(-pivot.x/16F,-pivot.y/16F,-pivot.z/16F)))));
        float[] rest=mul(bone.rest,cubeRotation);
        float[] posed=mul(bone.posed,cubeRotation);
        float[][] restCorners=new float[8][3],posedCorners=new float[8][3];
        for(int i=0;i<8;i++)
        {
            restCorners[i]=ChameleonWeldGeometry.transform(rest,original[i]);
            posedCorners[i]=ChameleonWeldGeometry.transform(posed,original[i]);
        }
        return new SkirtCubeBinding(cube,original,posed,
                new SkirtContinuityRing.Cube(bone.name,restCorners,
                        posedCorners));
    }

    @SuppressWarnings("unchecked")
    private void patchSkirtCube(Object cube,float[][] source,
            float[][] adjusted)throws Exception
    {
        for(Object quad:(List<Object>)field(cube,"quads"))
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f p=vec(vertex,"position");
                float[] original=originals.get(p);
                if(original==null)
                {
                    original=new float[]{p.x,p.y,p.z};
                    originals.put(p,original);
                }
                for(int i=0;i<8;i++)
                    if(Math.abs(original[0]-source[i][0])<FACE_EPS &&
                            Math.abs(original[1]-source[i][1])<FACE_EPS &&
                            Math.abs(original[2]-source[i][2])<FACE_EPS)
                    {
                        float[] next=adjusted[i];
                        p.set(next[0],next[1],next[2]);
                        break;
                    }
            }
    }

    private static final class Bone
    {
        Object value;
        String name;
        Bone parent;
        float[] rest,posed;
    }

    private static Object field(Object instance,String key)throws Exception
    {
        return instance.getClass().getField(key).get(instance);
    }

    private static Vector3f vec(Object instance,String key)throws Exception
    {
        return (Vector3f)field(instance,key);
    }

    private static boolean hasRotation(Object cube)throws Exception
    {
        Vector3f r=vec(cube,"rotation");
        return Math.abs(r.x)>1E-5F || Math.abs(r.y)>1E-5F ||
                Math.abs(r.z)>1E-5F;
    }

    /**
     * Derive the physical face from the cube geometry, not its visible quads.
     * Chameleon supports UV-omitted faces: side strands have only side
     * textures, hence scanning rendered vertices detects 3/7 hair joints.
     * The bounds below reproduce ModelCube.generateQuads exactly.
     */
    @SuppressWarnings("unchecked")
    private float[][] face(Object cube,boolean top)throws Exception
    {
        List<Object> quads=(List<Object>)field(cube,"quads");
        if(quads.isEmpty())return null;
        Vector3f origin=vec(cube,"origin");
        Vector3f size=vec(cube,"size");
        float inflate=((Number)field(cube,"inflate")).floatValue();

        float minX=(origin.x-size.x-inflate)/16F;
        float maxX=(origin.x+inflate)/16F;
        float minY=(origin.y-inflate)/16F;
        float maxY=(origin.y+size.y+inflate)/16F;
        float minZ=(origin.z-inflate)/16F;
        float maxZ=(origin.z+size.z+inflate)/16F;
        float y=top?maxY:minY;

        return new float[][] {
            {minX,y,minZ},
            {maxX,y,minZ},
            {maxX,y,maxZ},
            {minX,y,maxZ}
        };
    }

    @SuppressWarnings("unchecked")
    private void patchFace(Object cube,float[][] before,float[][] after)
            throws Exception
    {
        for(Object quad:(List<Object>)field(cube,"quads"))
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f p=vec(vertex,"position");
                float[] original=originals.get(p);
                if(original==null)
                {
                    original=new float[] {p.x,p.y,p.z};
                    originals.put(p,original);
                }
                for(int i=0;i<4;i++)
                    if(Math.abs(original[0]-before[i][0])<FACE_EPS &&
                            Math.abs(original[1]-before[i][1])<FACE_EPS &&
                            Math.abs(original[2]-before[i][2])<FACE_EPS)
                    {
                        p.set(after[i][0],after[i][1],after[i][2]);
                        break;
                    }
            }
    }

    /**
     * Restore ALL model vertex positions, even if the actor renderer threw.
     * Original morph models and future frames cannot inherit welded geometry.
     */
    @Override
    public void close()
    {
        for(Map.Entry<Vector3f,float[]> pair:originals.entrySet())
        {
            float[] p=pair.getValue();
            pair.getKey().set(p[0],p[1],p[2]);
        }
        originals.clear();
    }

    private static float[] boneMatrix(Object bone,boolean rest)
            throws Exception
    {
        Object initial=field(bone,"initial");
        Object transform=rest?initial:field(bone,"current");
        Vector3f pivot=vec(initial,"translate");
        Vector3f offset=vec(transform,"translate");
        Vector3f rotation=vec(transform,"rotation");
        Vector3f scale=vec(transform,"scale");
        float[] result=identity();

        // Exactly Chameleon's MatrixStack.translateBone() sign convention.
        result=mul(result,translate(
                -(offset.x-pivot.x)/16F,(offset.y-pivot.y)/16F,
                (offset.z-pivot.z)/16F));
        result=mul(result,translate(pivot.x/16F,pivot.y/16F,pivot.z/16F));
        result=mul(result,rotationZ(rotation.z));
        result=mul(result,rotationY(rotation.y));
        result=mul(result,rotationX(rotation.x));
        result=mul(result,scale(scale.x,scale.y,scale.z));
        return mul(result,translate(-pivot.x/16F,-pivot.y/16F,-pivot.z/16F));
    }

    private static float[] identity()
    {
        return new float[] {
                1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1
        };
    }

    private static float[] translate(float x,float y,float z)
    {
        float[] m=identity();
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }

    private static float[] scale(float x,float y,float z)
    {
        float[] m=identity();
        m[0]=x;m[5]=y;m[10]=z;
        return m;
    }

    private static float[] rotationX(float deg)
    {
        float c=(float)Math.cos(Math.toRadians(deg));
        float s=(float)Math.sin(Math.toRadians(deg));
        float[] m=identity();
        m[5]=c;m[6]=s;m[9]=-s;m[10]=c;
        return m;
    }

    private static float[] rotationY(float deg)
    {
        float c=(float)Math.cos(Math.toRadians(deg));
        float s=(float)Math.sin(Math.toRadians(deg));
        float[] m=identity();
        m[0]=c;m[2]=-s;m[8]=s;m[10]=c;
        return m;
    }

    private static float[] rotationZ(float deg)
    {
        float c=(float)Math.cos(Math.toRadians(deg));
        float s=(float)Math.sin(Math.toRadians(deg));
        float[] m=identity();
        m[0]=c;m[1]=s;m[4]=-s;m[5]=c;
        return m;
    }

    private static float[] mul(float[] a,float[] b)
    {
        float[] out=new float[16];
        for(int c=0;c<4;c++)
            for(int r=0;r<4;r++)
                for(int k=0;k<4;k++)
                    out[c*4+r]+=a[k*4+r]*b[c*4+k];
        return out;
    }
}
