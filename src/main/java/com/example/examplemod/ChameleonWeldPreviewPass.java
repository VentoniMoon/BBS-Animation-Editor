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
    /** Stage 1: read-only exact decoded Chameleon skin/UV extraction.
     * OFF by default; does NOT replace native cube rendering or physics. */
    private static final boolean SKIRT_MESH_CAPTURE=
            Boolean.getBoolean("bbs.editor.physics.skirt.meshCapture");
    private static int skirtMeshCapturePasses;
    /** Experimental detached textured source-skin A/B, OFF by default. */
    private static final boolean SKIRT_STATIC_AB=
            Boolean.getBoolean("bbs.editor.physics.skirt.staticSkinAB");
    private static boolean reportedStaticPreviewFailure;
    private SkirtClothSurfaceMesh.Snapshot staticPreviewSource;
    private SkirtClothRestBinding.Binding staticPreviewBinding;
    private float[][] staticPreviewProperties;
    /** Exact 43-cube/6-face reference: unlike the deliberately incomplete
     * 40-quad cloth skin, this preserves the real visible source volume. */
    private ChameleonSkirtVolumeSnapshot.Snapshot staticPreviewVolume;
    /** Independent XPBD fabric, gated by a separate system property.
     * All original native cubes are restored in close(), even on failure. */
    private SkirtXPBDRuntime.RenderFrame xpbdFrame;
    /** Optional fully textured native volume under the same 60 XPBD nodes.
     * Used ONLY in the new visual parity A/B mode. */
    private SkirtXPBDVolumeSkin.Mesh xpbdVolume;
    private static boolean reportedXPBDFailure;
    private final IdentityHashMap<Object,Object> hiddenFabricCubes=
            new IdentityHashMap<Object,Object>();
    /** Print full ranked seam diagnostics only once, not every render. */
    private static boolean skirtMeshSeamDetailsLogged;
    /**
     * Read-only snapshot of the ACTUAL Chameleon quad position changes
     * after per-morph Weld, immediately before the library draws them.
     * This reports model-space vertex deltas, NOT world-space leg hits.
     */
    private static final boolean SKIRT_RENDER_VERTEX_AUDIT=
            Boolean.getBoolean("bbs.editor.physics.skirt.renderVertexAudit");
    private static int skirtVertexAuditPasses;

    private ChameleonWeldPreviewPass() {}

    /**
     * Entry for the real ChameleonRenderer hook (Blockbuster playback,
     * world rendering, and Preview). Unlike begin(morphs), the library has
     * already applied the exact frame's pose to ModelBone.current.
     */
    public static ChameleonWeldPreviewPass beginPreparedModel(Object model)
    {
        return beginPreparedModel(model,true);
    }

    public static boolean isSkirtMeshCaptureRequested()
    {
        return SKIRT_MESH_CAPTURE;
    }
    public static boolean isSkirtStaticABRequested()
    {
        return SKIRT_STATIC_AB;
    }
    public static boolean isSkirtXPBDRequested()
    {
        return SkirtXPBDRuntime.ENABLED;
    }
    public SkirtXPBDRuntime.RenderFrame getXPBDFrame()
    {
        return xpbdFrame;
    }
    public SkirtXPBDVolumeSkin.Mesh getXPBDVolume()
    {
        return xpbdVolume;
    }
    public SkirtClothSurfaceMesh.Snapshot getStaticPreviewSource()
    {
        return staticPreviewSource;
    }
    public SkirtClothRestBinding.Binding getStaticPreviewBinding()
    {
        return staticPreviewBinding;
    }
    public float[][] getStaticPreviewProperties()
    {
        return staticPreviewProperties;
    }
    public ChameleonSkirtVolumeSnapshot.Snapshot getStaticPreviewVolume()
    {
        return staticPreviewVolume;
    }

    /** When weld=false, capture decoded UV/positions but NEVER modify any
     * model vertices. This must work even with a disabled Weld checkbox. */
    public static ChameleonWeldPreviewPass beginPreparedModel(
            Object model,boolean weld)
    {
        ChameleonWeldPreviewPass pass=new ChameleonWeldPreviewPass();
        if(model==null)return pass;
        try
        {
            pass.prepareModel(model,weld);
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
        prepareModel(model,true);
    }

    @SuppressWarnings("unchecked")
    private void prepareModel(Object model,boolean weld)throws Exception
    {
        List<Object> roots=(List<Object>)field(model,"bones");
        List<Bone> bones=new ArrayList<Bone>();
        for(Object root:roots)
            collectBone(root,null,identity(),identity(),bones);
        // Snapshot the untouched ORIGINAL decoded geometry and UVs before
        // hair/parent weld or circumferential weld modifies any positions.
        if(bones.size()==44)
        {
            if(SKIRT_MESH_CAPTURE && ++skirtMeshCapturePasses%120==1)
                inspectSkirtMesh(bones);
            if(SKIRT_STATIC_AB)
                prepareStaticPreview(bones);
            if(SkirtXPBDRuntime.ENABLED)
            {
                prepareXPBD(bones);
                if(xpbdFrame!=null&&SkirtXPBDRuntime.REPLACE)
                {
                    // Everything must be built/validated BEFORE the
                    // original fabric is hidden. The waist's 3 authored
                    // cubes stay visible in the normal native renderer.
                    if(hideNativeFabric(bones))
                        return; // No old per-bone Weld over new cloth.
                    xpbdFrame=null;
                }
            }
        }
        if(!weld)return;
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
        if(SKIRT_RENDER_VERTEX_AUDIT && ringSeams>0 &&
                ++skirtVertexAuditPasses%20==0)
            reportRenderedVertexDisplacements();
    }

    private void reportRenderedVertexDisplacements()
    {
        int moved=0;
        float max=0F,total=0F;
        for(Map.Entry<Vector3f,float[]> entry:originals.entrySet())
        {
            Vector3f vertex=entry.getKey();
            float[] previous=entry.getValue();
            float x=vertex.x-previous[0],y=vertex.y-previous[1],
                    z=vertex.z-previous[2];
            float distance=(float)Math.sqrt(x*x+y*y+z*z);
            if(distance<=.000001F)continue;
            moved++;
            max=Math.max(max,distance);
            total+=distance;
        }
        System.out.println(
                "[BBS Animation Editor][Jolt][SkirtRenderVertexAudit] "+
                "weldRingSeams="+ringSeams+
                " changedQuadVertices="+moved+
                " maxModelDelta="+max+
                " totalModelDelta="+total+
                " (measured in native posed Chameleon quads AFTER Weld; "+
                "before original vertex restoration; no leg-space audit)");
    }

    /**
     * Select the exact 40 outward faces from the 44-bone Chameleon
     * model. All ModelVertex.uv values are copied without scaling or
     * regenerating a box UV layout. No renderer vertex is ever written.
     */
    private void inspectSkirtMesh(List<Bone> all)
    {
        try
        {
            SkirtClothSurfaceMesh.Snapshot mesh=collectSkirtSource(all);
            if(mesh==null)return;
            // Stage 2 constructs an immutable 20x3 canonical rest lattice.
            // This does not render the proposed surface or move a cube:
            // report the precise distortion required BEFORE any visual test.
            try
            {
                SkirtClothSharedSurface.Surface shared=
                        SkirtClothSharedSurface.connect(mesh);
                SkirtClothRestBinding.Binding binding=
                        SkirtClothRestBinding.bind(mesh,shared);
                System.out.println(
                        "[BBS Animation Editor][SkirtRestBinding] "+
                        "physicalNodes="+binding.nodeRest.length+
                        " sourcePatches="+binding.patches.size()+
                        " maxMaterialOffset="+binding.maxMaterialOffset+
                        " maxRestReconstructionError="+
                            binding.maxRestReconstructionError+
                        " directProjectionUnsafe="+
                            binding.directProjectionUnsafe+
                        " projectedRelativeEdgeChange="+
                            binding.projectedRelativeEdgeChange+
                        " mode=READ_ONLY_REST_PARITY_NO_RENDER");
                System.out.println(
                        "[BBS Animation Editor][SkirtStaticSkin] "+
                        "particles="+shared.nodes.size()+
                        " rows="+java.util.Arrays.toString(shared.rowCounts)+
                        " texturedQuads="+shared.patches.size()+
                        " triangles="+shared.triangleCount+
                        " originalMaxGap="+shared.sourceMaxSeamGap+
                        " residualGap="+shared.residualSeamGap+
                        " maxRestCorrection="+shared.maxRestCorrection+
                        " meanRestCorrection="+shared.meanRestCorrection+
                        " maxRelativeEdgeChange="+shared.maxRelativeEdgeChange+
                        " flippedTriangles="+shared.flippedTriangles+
                        " degenerateTriangles="+shared.degenerateTriangles+
                        " mode=READ_ONLY_CANDIDATE_NO_RENDER");
            }
            catch(IllegalArgumentException rejected)
            {
                System.err.println(
                        "[BBS Animation Editor][SkirtStaticSkin] "+
                        "Candidate rejected: "+rejected.getMessage()+
                        " (original Chameleon mesh unchanged)");
            }
            // Candidate seam gaps are not proven holes. Separate
            // horizontal/vertical connections and expose the actual bone
            // pairs before drawing or welding a replacement surface.
            float[] maxByType=new float[3],sumByType=new float[3];
            int[] counts=new int[3],flippedByType=new int[3];
            for(SkirtClothSurfaceMesh.Seam seam:mesh.seams)
            {
                int idx="upper".equals(seam.category)?0:
                        "lower".equals(seam.category)?1:2;
                counts[idx]++;
                sumByType[idx]+=seam.restGap;
                maxByType[idx]=Math.max(maxByType[idx],seam.restGap);
                if(seam.reversed)flippedByType[idx]++;
            }
            if(!skirtMeshSeamDetailsLogged)
            {
                skirtMeshSeamDetailsLogged=true;
                List<SkirtClothSurfaceMesh.Seam> ranked=
                        new ArrayList<SkirtClothSurfaceMesh.Seam>(mesh.seams);
                Collections.sort(ranked,
                        new Comparator<SkirtClothSurfaceMesh.Seam>()
                        {
                            @Override
                            public int compare(SkirtClothSurfaceMesh.Seam a,
                                    SkirtClothSurfaceMesh.Seam b)
                            {
                                int c=Float.compare(b.restGap,a.restGap);
                                if(c!=0)return c;
                                c=a.category.compareTo(b.category);
                                return c!=0?c:a.a.compareTo(b.a);
                            }
                        });
                for(int i=0;i<Math.min(12,ranked.size());i++)
                {
                    SkirtClothSurfaceMesh.Seam seam=ranked.get(i);
                    System.out.println(
                            "[BBS Animation Editor][SkirtMeshSeam] "+
                            "rank="+(i+1)+
                            " type="+seam.category+
                            " from="+seam.a+" to="+seam.b+
                            " gapBlocks="+seam.restGap+
                            " gapPixels="+(seam.restGap*16F)+
                            " originalMatch="+seam.forwardGap+
                            " oppositeMatch="+seam.reverseGap+
                            " flipped="+seam.reversed+
                            " classification=CANDIDATE_ONLY");
                }
            }
            System.out.println(
                    "[BBS Animation Editor][SkirtMeshCapture] "+
                    "bones="+all.size()+
                    " strips="+SkirtClothSurfaceMesh.STRIP_COUNT+
                    " outwardQuads="+mesh.patches.size()+
                    " triangles="+mesh.triangleCount+
                    " uvCorners="+(mesh.patches.size()*4)+
                    " seamCandidates="+mesh.seams.size()+
                    " maxRestGap="+mesh.maxRestGap+
                    " meanRestGap="+mesh.meanRestGap+
                    " maxAssumedGap="+mesh.maxAssumedGap+
                    " reversedSeams="+mesh.reversedSeams+
                    " upperMaxGap="+maxByType[0]+
                    " lowerMaxGap="+maxByType[1]+
                    " verticalMaxGap="+maxByType[2]+
                    " upperMeanGap="+sumByType[0]/Math.max(1,counts[0])+
                    " lowerMeanGap="+sumByType[1]/Math.max(1,counts[1])+
                    " verticalMeanGap="+sumByType[2]/Math.max(1,counts[2])+
                    " reversedByType="+java.util.Arrays.toString(flippedByType)+
                    " maxRoundTripError="+mesh.maxRoundTripError+
                    " boundsMin="+java.util.Arrays.toString(mesh.min)+
                    " boundsMax="+java.util.Arrays.toString(mesh.max)+
                    " mode=READ_ONLY_SOURCE_UV");
        }
        catch(Exception error)
        {
            System.err.println(
                    "[BBS Animation Editor][SkirtMeshCapture] "+
                    "Native quad capture unavailable: "+
                    error.getClass().getSimpleName()+": "+
                    error.getMessage()+
                    " (source cubes/render/physics unchanged)");
        }
    }

    /**
     * Complete opt-in 3-stage fabric pipeline:
     *  1. capture CURRENT Chameleon bone and cube transforms,
     *  2. build a shared 60-particle + original UV + gap-bridge shell,
     *  3. step independent XPBD with real Emoticons leg OBBs when present.
     * No model resources or cached morph animation data are mutated here.
     */
    @SuppressWarnings("unchecked")
    private void prepareXPBD(List<Bone> bones)
    {
        try
        {
            Object morph=ChameleonWeldRuntime.activeMorph();
            if(morph==null)return; // Outside exact actor scope: fail closed.
            SkirtClothSurfaceMesh.Snapshot raw=collectSkirtSource(bones);
            if(raw==null)return;
            SkirtClothSharedSurface.Surface shared=
                    SkirtClothSharedSurface.connect(raw);
            if(shared.flippedTriangles>0||
                    shared.degenerateTriangles>0)return;
            SkirtClothRestBinding.Binding binding=
                    SkirtClothRestBinding.bind(raw,shared);
            if(binding.maxRestReconstructionError>1E-5F)return;
            SkirtXPBDSurfaceTopology.Topology topology=
                    SkirtXPBDSurfaceTopology.connect(raw,binding);

            Map<String,Bone> byName=new HashMap<String,Bone>();
            for(Bone bone:bones)
                byName.put(idOf(bone.value),bone);
            List<SkirtXPBDPoseSkin.Transform> transforms=
                    new ArrayList<SkirtXPBDPoseSkin.Transform>(40);
            float[][] colors=new float[40][];
            for(int i=0;i<raw.patches.size();i++)
            {
                String boneName=raw.patches.get(i).bone;
                Bone bone=byName.get(boneName);
                if(bone==null||!visibleNativeChain(bone))return;
                List<Object> cubes=(List<Object>)field(bone.value,"cubes");
                if(cubes==null||cubes.size()!=1)return;
                Object cube=cubes.get(0);
                float[] rest=meshDecodedCube(
                        bone,cube,false).restMatrix;
                float[] posed=meshDecodedCube(
                        bone,cube,true).restMatrix;
                transforms.add(new SkirtXPBDPoseSkin.Transform(
                        boneName,rest,posed));
                colors[i]=skinMaterialProperties(bone);
            }
            SkirtXPBDPoseSkin.Frame pose=
                    SkirtXPBDPoseSkin.follow(binding,transforms);
            if(pose.maxPoseReconstructionError>1E-5F)return;
            SkirtXPBDContacts.Frame bodies=
                    SkirtXPBDContacts.forMorph(morph);
            xpbdFrame=SkirtXPBDRuntime.sample(
                    morph,raw,shared,binding,topology,pose,colors,bodies);

            if(xpbdFrame!=null&&SkirtXPBDVolumeSkin.ENABLED)
            {
                // Preserve ALL 43 authored Chameleon cubes, including
                // the three ALL waist pieces and every source UV face.
                // The same XPBD particles deform the source cuboid volume
                // via each panel's original 4 UV-owned skin corners.
                // Build BEFORE hideNativeFabric, so unsupported/missing
                // geometry always falls back to original Chameleon.
                List<ChameleonSkirtVolumeSnapshot.Part> restParts=
                        new ArrayList<ChameleonSkirtVolumeSnapshot.Part>();
                List<ChameleonSkirtVolumeSnapshot.Part> posedParts=
                        new ArrayList<ChameleonSkirtVolumeSnapshot.Part>();
                for(Bone bone:bones)
                {
                    if(!visibleNativeChain(bone))
                        throw new IllegalArgumentException(
                                "XPBD native volume contains hidden bone");
                    List<Object> cubes=(List<Object>)field(
                            bone.value,"cubes");
                    float[] tint=skinMaterialProperties(bone);
                    for(Object cube:cubes)
                    {
                        restParts.add(
                            new ChameleonSkirtVolumeSnapshot.Part(
                                meshDecodedCube(bone,cube,false),tint));
                        posedParts.add(
                            new ChameleonSkirtVolumeSnapshot.Part(
                                meshDecodedCube(bone,cube,true),tint));
                    }
                }
                xpbdVolume=SkirtXPBDVolumeSkin.build(
                        ChameleonSkirtVolumeSnapshot.capture(restParts),
                        ChameleonSkirtVolumeSnapshot.capture(posedParts),
                        raw,binding,pose,xpbdFrame.solved);
            }
        }
        catch(Exception error)
        {
            xpbdFrame=null;
            xpbdVolume=null;
            if(!reportedXPBDFailure)
            {
                reportedXPBDFailure=true;
                System.err.println("[BBS Editor][SkirtXPBD] "+
                        "Opt-in cloth preparation failed: "+
                        error.getClass().getSimpleName()+": "+
                        error.getMessage()+
                        " (normal native cloth remains visible)");
            }
        }
    }

    /**
     * Suppress only the 40 fabric cubes while the custom cloth draws.
     * The native waist, character and all other morphs remain unchanged.
     * NEVER clear the shared Chameleon list itself; temporarily replace
     * the reference and restore it in close() even if native draw throws.
     */
    private boolean hideNativeFabric(List<Bone> bones)
    {
        Map<String,Bone> names=new HashMap<String,Bone>();
        for(Bone bone:bones)names.put(idOfSafe(bone.value),bone);
        if(xpbdFrame==null||xpbdFrame.source.patches.size()!=40)
            return false;
        for(SkirtClothSurfaceMesh.Patch panel:xpbdFrame.source.patches)
        {
            Bone bone=names.get(panel.bone);
            if(bone==null)return false;
            try
            {
                Object existing=field(bone.value,"cubes");
                if(!(existing instanceof List)||
                        ((List<?>)existing).size()!=1)
                    return false;
            }
            catch(Exception invalid){return false;}
        }
        try
        {
            for(SkirtClothSurfaceMesh.Patch panel:xpbdFrame.source.patches)
            {
                Object bone=names.get(panel.bone).value;
                Object existing=field(bone,"cubes");
                hiddenFabricCubes.put(bone,existing);
                bone.getClass().getField("cubes").set(bone,
                        Collections.emptyList());
            }
            return hiddenFabricCubes.size()==40;
        }
        catch(Exception error)
        {
            restoreNativeFabric();
            return false;
        }
    }

    private static String idOfSafe(Object bone)
    {
        try{return (String)field(bone,"id");}
        catch(Exception missing){return null;}
    }

    /** Restore before another model/actor can see shared native cubes. */
    private void restoreNativeFabric()
    {
        for(Map.Entry<Object,Object> hidden:hiddenFabricCubes.entrySet())
        {
            try
            {
                hidden.getKey().getClass().getField("cubes").set(
                        hidden.getKey(),hidden.getValue());
            }
            catch(Exception failure)
            {
                System.err.println("[BBS Editor][SkirtXPBD] "+
                        "CRITICAL: original native cubes restore failed: "+
                        failure.getClass().getSimpleName());
            }
        }
        hiddenFabricCubes.clear();
    }

    /** Exact authored 44-bone profile only. Source quad vertices and UV
     * are copied BEFORE any Weld pass can change their positions. */
    private static SkirtClothSurfaceMesh.Snapshot collectSkirtSource(
            List<Bone> all)throws Exception
    {
        if(all==null||all.size()!=44)return null;
        Map<String,Bone> byName=new HashMap<String,Bone>();
        Bone waist=null;
        for(Bone b:all)
        {
            String id=(String)field(b.value,"id");
            if(id==null||byName.put(id,b)!=null)return null;
            if("ALL".equals(id))waist=b;
        }
        if(waist==null)return null;
        SkirtGeoProfile.Plan<Object> profile=
                SkirtGeoProfile.inspect(waist.value,
                        value->childrenOf(value),value->idOf(value));
        List<SkirtClothSurfaceMesh.Strip> strips=
                new ArrayList<SkirtClothSurfaceMesh.Strip>();
        for(List<Object> chain:profile.strips)
        {
            Bone upper=byName.get(idOf(chain.get(0)));
            Bone lower=byName.get(idOf(chain.get(1)));
            if(upper==null||lower==null)return null;
            strips.add(new SkirtClothSurfaceMesh.Strip(
                    meshCube(upper),meshCube(lower)));
        }
        return SkirtClothSurfaceMesh.capture(strips);
    }

    /** Prepare a completely separate source-UV material mesh in rest pose.
     * Native cubes still render unchanged. On any unsupported model, the
     * optional renderer receives null and draws nothing. */
    private void prepareStaticPreview(List<Bone> bones)
    {
        try
        {
            SkirtClothSurfaceMesh.Snapshot raw=collectSkirtSource(bones);
            if(raw==null)return;
            SkirtClothSharedSurface.Surface shared=
                    SkirtClothSharedSurface.connect(raw);
            SkirtClothRestBinding.Binding binding=
                    SkirtClothRestBinding.bind(raw,shared);
            if(binding.maxRestReconstructionError>1E-5F)return;

            Map<String,Bone> names=new HashMap<String,Bone>();
            for(Bone bone:bones)
                names.put(idOf(bone.value),bone);
            float[][] props=new float[raw.patches.size()][];
            for(int i=0;i<raw.patches.size();i++)
                props[i]=skinMaterialProperties(
                        names.get(raw.patches.get(i).bone));
            // The former A/B drew only one face of each of 40 cubes and
            // naturally looked like an exploded skirt of disconnected
            // cards. Native parity must include all 43 ORIGINAL cubes:
            // 40 moving fabric segments AND the 3 waist cubes, complete
            // with box ends, inner sides, caps and independent face UVs.
            List<ChameleonSkirtVolumeSnapshot.Part> authored=
                    new ArrayList<ChameleonSkirtVolumeSnapshot.Part>();
            for(Bone bone:bones)
            {
                // ChameleonRenderer skips entire hidden bone subtrees.
                // A "native parity" duplicate must not resurrect an
                // editor-disabled strip that the original renderer hides.
                if(!visibleNativeChain(bone))
                    throw new IllegalArgumentException(
                            "Hidden native bone; cannot show full-volume A/B");
                @SuppressWarnings("unchecked")
                List<Object> cubes=(List<Object>)field(bone.value,"cubes");
                if(cubes==null)continue;
                float[] material=skinMaterialProperties(bone);
                for(Object cube:cubes)
                    authored.add(new ChameleonSkirtVolumeSnapshot.Part(
                            meshDecodedCube(bone,cube),material));
            }
            ChameleonSkirtVolumeSnapshot.Snapshot volume=
                    ChameleonSkirtVolumeSnapshot.capture(authored);
            staticPreviewSource=raw;
            staticPreviewBinding=binding;
            staticPreviewProperties=props;
            staticPreviewVolume=volume;
        }
        catch(Exception error)
        {
            staticPreviewSource=null;
            staticPreviewBinding=null;
            staticPreviewProperties=null;
            staticPreviewVolume=null;
            if(!reportedStaticPreviewFailure)
            {
                reportedStaticPreviewFailure=true;
                System.err.println("[BBS Editor][SkirtStaticVisual] "+
                        "Failed to build a static source mesh: "+
                        error.getClass().getSimpleName()+": "+
                        error.getMessage()+
                        " (original Chameleon render unchanged)");
            }
        }
    }

    /** Native Chameleon recursive render omits a hidden parent and all
     * children. Fail closed instead of drawing hidden model geometry. */
    private static boolean visibleNativeChain(Bone bone)throws Exception
    {
        for(Bone b=bone;b!=null;b=b.parent)
            if(!Boolean.TRUE.equals(field(b.value,"visible")))return false;
        return true;
    }

    /**
     * ChameleonCubeRenderer uses per-bone color and glow. Reflection keeps
     * Chameleon optional, including in the standalone Weld Java 8 tests.
     * RGBA + absoluteBrightness + glow; default white without glow.
     */
    private static float[] skinMaterialProperties(Bone bone)
    {
        float[] props={1F,1F,1F,1F,0F,0F};
        if(bone==null)return props;
        try
        {
            Object tint=field(bone.value,"color");
            String[] keys={"r","g","b","a"};
            for(int i=0;i<4;i++)
                props[i]=((Number)field(tint,keys[i])).floatValue();
            props[4]=Boolean.TRUE.equals(
                    field(bone.value,"absoluteBrightness"))?1F:0F;
            props[5]=((Number)field(bone.value,"glow")).floatValue();
        }
        catch(Exception optional){ /* original white material fallback */ }
        return props;
    }

    @SuppressWarnings("unchecked")
    private static SkirtClothSurfaceMesh.Cube meshCube(Bone bone)
            throws Exception
    {
        List<Object> cubes=(List<Object>)field(bone.value,"cubes");
        if(cubes==null||cubes.size()!=1)
            throw new IllegalArgumentException(
                    "Need one decoded cube on "+idOf(bone.value));
        return meshDecodedCube(bone,cubes.get(0));
    }

    /** Capture ANY authored native cube, including the three belt cubes
     * on ALL. The original meshCube() is intentionally limited to one
     * cuboid per moving cloth bone for Stage 1/2 cloth skin extraction. */
    @SuppressWarnings("unchecked")
    private static SkirtClothSurfaceMesh.Cube meshDecodedCube(
            Bone bone,Object cube)throws Exception
    {
        return meshDecodedCube(bone,cube,false);
    }

    @SuppressWarnings("unchecked")
    private static SkirtClothSurfaceMesh.Cube meshDecodedCube(
            Bone bone,Object cube,boolean posed)throws Exception
    {
        Vector3f pivot=vec(cube,"pivot");
        Vector3f r=vec(cube,"rotation");
        float[] local=translate(pivot.x/16F,pivot.y/16F,
                pivot.z/16F);
        local=mul(local,rotationZ(r.z));
        local=mul(local,rotationY(r.y));
        local=mul(local,rotationX(r.x));
        local=mul(local,translate(-pivot.x/16F,-pivot.y/16F,
                -pivot.z/16F));
        float[] cubeRest=mul(posed?bone.posed:bone.rest,local);

        List<SkirtClothSurfaceMesh.Quad> quads=
                new ArrayList<SkirtClothSurfaceMesh.Quad>();
        for(Object face:(List<Object>)field(cube,"quads"))
        {
            List<Object> vertices=(List<Object>)field(face,"vertices");
            if(vertices==null||vertices.size()!=4)continue;
            SkirtClothSurfaceMesh.Vertex[] corners=
                    new SkirtClothSurfaceMesh.Vertex[4];
            for(int i=0;i<4;i++)
            {
                Object vertex=vertices.get(i);
                Vector3f xyz=vec(vertex,"position");
                Object uv=field(vertex,"uv");
                float u=((Number)field(uv,"x")).floatValue();
                float v=((Number)field(uv,"y")).floatValue();
                corners[i]=new SkirtClothSurfaceMesh.Vertex(
                        new float[]{xyz.x,xyz.y,xyz.z},
                        new float[]{u,v});
            }
            Vector3f normal=vec(face,"normal");
            quads.add(new SkirtClothSurfaceMesh.Quad(corners,
                    new float[]{normal.x,normal.y,normal.z}));
        }
        return new SkirtClothSurfaceMesh.Cube(
                idOf(bone.value),cubeRest,quads);
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
        restoreNativeFabric();
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
