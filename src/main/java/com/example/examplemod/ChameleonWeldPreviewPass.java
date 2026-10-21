package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
    private static final boolean REQUESTED=
            Boolean.getBoolean("bbs.editor.weld.preview");
    private static final float FACE_EPS=1E-5F;
    private static final float MAX_REST_GAP=0.003F;
    private static boolean reportedFailure;
    private static boolean reportedSuccess;

    private final IdentityHashMap<Vector3f,float[]> originals=
            new IdentityHashMap<Vector3f,float[]>();
    private int seams;

    private ChameleonWeldPreviewPass() {}

    public static ChameleonWeldPreviewPass begin(
            Iterable<? extends AbstractMorph> childMorphs,float partialTicks)
    {
        ChameleonWeldPreviewPass pass=new ChameleonWeldPreviewPass();
        if(!REQUESTED || childMorphs==null)return pass;
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
                .equals(morph.getClass().getName()))
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

        List<Object> roots=(List<Object>)field(model,"bones");
        List<Bone> bones=new ArrayList<Bone>();
        for(Object root:roots)
            collectBone(root,null,identity(),identity(),bones);
        for(Bone bone:bones)
            if(bone.parent!=null)
                weldJoint(bone.parent,bone);
    }

    @SuppressWarnings("unchecked")
    private void collectBone(Object nativeBone,Bone parent,
            float[] restParent,float[] poseParent,List<Bone> output)
            throws Exception
    {
        Bone b=new Bone();
        b.value=nativeBone;
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

    private static final class Bone
    {
        Object value;
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

    @SuppressWarnings("unchecked")
    private float[][] face(Object cube,boolean top)throws Exception
    {
        List<Object> quads=(List<Object>)field(cube,"quads");
        if(quads.isEmpty())return null;
        float y=top?Float.NEGATIVE_INFINITY:Float.POSITIVE_INFINITY;
        for(Object quad:quads)
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f v=vec(vertex,"position");
                float[] base=originals.get(v);
                float yy=base==null?v.y:base[1];
                y=top?Math.max(y,yy):Math.min(y,yy);
            }

        List<float[]> unique=new ArrayList<float[]>();
        for(Object quad:quads)
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f v=vec(vertex,"position");
                float[] original=originals.get(v);
                float vx=original==null?v.x:original[0];
                float vy=original==null?v.y:original[1];
                float vz=original==null?v.z:original[2];
                if(Math.abs(vy-y)>FACE_EPS)continue;
                boolean exists=false;
                for(float[] old:unique)
                    if(Math.abs(old[0]-vx)<=FACE_EPS &&
                            Math.abs(old[1]-vy)<=FACE_EPS &&
                            Math.abs(old[2]-vz)<=FACE_EPS)
                    {
                        exists=true;
                        break;
                    }
                if(!exists)unique.add(new float[] {vx,vy,vz});
            }
        if(unique.size()!=4)return null;
        return unique.toArray(new float[4][3]);
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
