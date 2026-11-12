package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

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
    private static boolean reportedFailure;
    private static boolean reportedSuccess;

    private final IdentityHashMap<Vector3f,float[]> originals=
            new IdentityHashMap<Vector3f,float[]>();
    private int seams;
    private int ringSeams;

    private static final boolean SKIRT_RING_WELD=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.ringWeld","true"));

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
    public int getSkirtRingSeamCount(){return ringSeams;}

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
        for(Bone bone:bones)
            if(bone.parent!=null)
                weldJoint(bone.parent,bone);
        if(SKIRT_RING_WELD)
        {
            try{weldSkirtRing(bones);}
            catch(Exception ignored)
            {
                // A missing optional cube/pivot must never disable ordinary
                // parent-child Weld or the actor's native Chameleon render.
                if(!reportedFailure)
                {
                    reportedFailure=true;
                    System.err.println("[BBS Editor][Weld][SkirtRing] "
                            +"Skipped unsupported ring geometry: "+
                            ignored.getClass().getSimpleName());
                }
            }
        }
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

    /**
     * Stitch the 20 neighboring skirt strips around the entire waist, at
     * BOTH the upper and lower cube level. The hair Weld pass above only
     * joins parent/child bones and cannot repair circumferential gaps.
     *
     * This strictly matches the exact 44-bone user skirt topology; other
     * Chameleon attachments, hair, and arbitrary cube names are excluded.
     * No physics keys or model vertex resources survive close().
     */
    private void weldSkirtRing(List<Bone> all)throws Exception
    {
        if(all.size()!=44)return;
        final Map<String,Bone> names=new HashMap<String,Bone>();
        Object waist=null;
        for(Bone b:all)
        {
            String id=(String)field(b.value,"id");
            if(id==null||names.put(id,b)!=null)return;
            if("ALL".equals(id))waist=b.value;
        }
        if(waist==null)return;
        SkirtGeoProfile.Plan<Object> profile;
        try
        {
            profile=SkirtGeoProfile.inspect(waist,
                    value->childrenOf(value),value->idOf(value));
        }
        catch(IllegalArgumentException invalid){return;}
        for(int layer=0;layer<2;layer++)
        {
            List<RingCube> ring=new ArrayList<RingCube>();
            float cx=0F,cz=0F;
            for(List<Object> strip:profile.strips)
            {
                Bone b=names.get(idOf(strip.get(layer)));
                if(b==null)return;
                RingCube cube=ringCube(b);
                if(cube==null)return;
                ring.add(cube);
                cx+=cube.restCenter[0];
                cz+=cube.restCenter[2];
            }
            cx/=ring.size();cz/=ring.size();
            final float x=cx,z=cz;
            Collections.sort(ring,new Comparator<RingCube>()
            {
                @Override public int compare(RingCube a,RingCube b)
                {
                    double aa=Math.atan2(a.restCenter[2]-z,
                            a.restCenter[0]-x);
                    double bb=Math.atan2(b.restCenter[2]-z,
                            b.restCenter[0]-x);
                    return Double.compare(aa,bb);
                }
            });
            for(int i=0;i<ring.size();i++)
                weldRingNeighbors(ring.get(i),
                        ring.get((i+1)%ring.size()));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> childrenOf(Object bone)
    {
        try{return (List<Object>)field(bone,"children");}
        catch(Exception e){throw new IllegalArgumentException(e);}
    }
    private static String idOf(Object bone)
    {
        try{return (String)field(bone,"id");}
        catch(Exception e){throw new IllegalArgumentException(e);}
    }

    private static final class RingEdge
    {
        float[] top,bottom;
        RingEdge(float[] top,float[] bottom)
        {this.top=top;this.bottom=bottom;}
        SkirtRingSeamGeometry.Edge asGeometry()
        {return new SkirtRingSeamGeometry.Edge(top,bottom);}
    }
    private static final class RingCube
    {
        Object cube;
        float[] rest,posed,restCenter;
        List<RingEdge> edges;
    }

    @SuppressWarnings("unchecked")
    private RingCube ringCube(Bone bone)throws Exception
    {
        List<Object> cubes=(List<Object>)field(bone.value,"cubes");
        if(cubes.size()!=1)return null;
        Object cube=cubes.get(0);
        List<Object> quads=(List<Object>)field(cube,"quads");
        if(quads.size()<2)return null;
        Vector3f pivot=vec(cube,"pivot");
        Vector3f rot=vec(cube,"rotation");
        float[] move=translate(pivot.x/16F,pivot.y/16F,pivot.z/16F);
        move=mul(move,rotationZ(rot.z));
        move=mul(move,rotationY(rot.y));
        move=mul(move,rotationX(rot.x));
        move=mul(move,translate(-pivot.x/16F,-pivot.y/16F,
                -pivot.z/16F));
        RingCube rc=new RingCube();
        rc.cube=cube;
        rc.rest=mul(bone.rest,move);
        rc.posed=mul(bone.posed,move);

        List<float[]> points=new ArrayList<float[]>();
        for(Object quad:quads)
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f v=vec(vertex,"position");
                float[] original=originals.get(v);
                float[] raw=original!=null?original:
                        new float[]{v.x,v.y,v.z};
                boolean seen=false;
                for(float[] known:points)
                    if(close(known,raw)){seen=true;break;}
                if(!seen)points.add(raw.clone());
            }
        if(points.size()<6||points.size()>24)return null;
        float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        float[] sum={0F,0F,0F};
        for(float[] p:points)
        {
            minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);
            float[] w=ChameleonWeldGeometry.transform(rc.rest,p);
            sum[0]+=w[0];sum[1]+=w[1];sum[2]+=w[2];
        }
        if(maxY-minY<.03F)return null;
        for(int i=0;i<3;i++)sum[i]/=points.size();
        rc.restCenter=sum;
        rc.edges=new ArrayList<RingEdge>();
        for(float[] low:points)
        {
            if(Math.abs(low[1]-minY)>.0001F)continue;
            for(float[] high:points)
                if(Math.abs(high[1]-maxY)<.0001F &&
                        Math.abs(low[0]-high[0])<.0001F &&
                        Math.abs(low[2]-high[2])<.0001F)
                    rc.edges.add(new RingEdge(high,low));
        }
        return rc.edges.size()>=2&&rc.edges.size()<=8?rc:null;
    }

    private void weldRingNeighbors(RingCube a,RingCube b)throws Exception
    {
        SkirtRingSeamGeometry.Plan best=null;
        RingEdge bestA=null,bestB=null;
        for(RingEdge ea:a.edges)
            for(RingEdge eb:b.edges)
            {
                SkirtRingSeamGeometry.Plan p=SkirtRingSeamGeometry.plan(
                        a.rest,b.rest,a.posed,b.posed,
                        ea.asGeometry(),eb.asGeometry());
                if(p!=null&&(best==null||p.restGap<best.restGap))
                {
                    best=p;bestA=ea;bestB=eb;
                }
            }
        if(best==null)return;
        patchRingEdge(a.cube,bestA,best.sourceLocal);
        // targetLocal is already indexed in the TARGET's own edge order.
        patchRingEdge(b.cube,bestB,best.targetLocal);
        ringSeams++;seams++;
    }

    @SuppressWarnings("unchecked")
    private void patchRingEdge(Object cube,RingEdge edge,
            float[][] newPoints)throws Exception
    {
        for(Object quad:(List<Object>)field(cube,"quads"))
            for(Object vertex:(List<Object>)field(quad,"vertices"))
            {
                Vector3f v=vec(vertex,"position");
                float[] src=originals.get(v);
                if(src==null)src=new float[]{v.x,v.y,v.z};
                int which=close(src,edge.top)?0:
                        close(src,edge.bottom)?1:-1;
                if(which<0)continue;
                // Mesh may already have parent/child Weld displacement.
                // Add a bounded side-seam delta rather than overwriting it.
                if(!originals.containsKey(v))
                    originals.put(v,src.clone());
                float[] target=newPoints[which];
                float[] origin=which==0?edge.top:edge.bottom;
                v.set(v.x+(target[0]-origin[0]),
                        v.y+(target[1]-origin[1]),
                        v.z+(target[2]-origin[2]));
            }
    }

    private static boolean close(float[] a,float[] b)
    {
        return Math.abs(a[0]-b[0])<1E-5F &&
                Math.abs(a[1]-b[1])<1E-5F &&
                Math.abs(a[2]-b[2])<1E-5F;
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
