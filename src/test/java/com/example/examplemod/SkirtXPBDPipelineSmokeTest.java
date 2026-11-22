package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Pure Java 8 tests: three-stage posed/continuous/XPBD skirt pipeline. */
public final class SkirtXPBDPipelineSmokeTest
{
    private SkirtXPBDPipelineSmokeTest(){}

    private static final class Fixture
    {
        final SkirtClothSurfaceMesh.Snapshot raw;
        final SkirtClothSharedSurface.Surface shared;
        final SkirtClothRestBinding.Binding binding;
        final SkirtXPBDSurfaceTopology.Topology topology;
        final SkirtXPBDPoseSkin.Frame rest;
        Fixture()
        {
            raw=SkirtClothSurfaceMesh.capture(
                    SkirtClothSurfaceMeshSmokeTest.fixture());
            shared=SkirtClothSharedSurface.connect(raw);
            binding=SkirtClothRestBinding.bind(raw,shared);
            topology=SkirtXPBDSurfaceTopology.connect(raw,binding);
            rest=pose(raw,binding,identity());
        }
    }
    public static void main(String[] args)
    {
        exactlyFollowAnimatedNativeBonePoses();
        nativeVolumeFollowsXPBDWithoutDestroyingTextureFaces();
        originalUvsAndSeamBridgesAreConnected();
        xpbdHasPinnedWaistGravityAndBounds();
        refinedGridRetainsNativeSkinCorrespondence();
        pausedPoseChangePreservesClothRelativeMotion();
        actualBodyObbsProjectWithoutInventedContacts();
        outwardThighContactsPreserveHemSide();
        raisedThighContactsSupportClothFromAbove();
        taperedThighUsesBoneAxisEvenWhenZoneIsWiderThanLong();
        skinnedThighMeshProvidesContinuousContact();
        denseEmoticonsSkinDoesNotFallBackFor4500PlusTriangles();
        bilateralSittingFoldsWithoutBreakingWalking();
        seatedUpperHoopGuardDoesNotFreezeTheHem();
        seatedTriangleInteriorContactDoesNotRequireVertexPenetration();
        pausedRepeatedDrawIsBitStableUntilLegPoseChanges();
        fractionalClockAdvancesOncePerSixtiethSecond();
        renderOnlyFramesStillCorrectFastThighContact();
        perAttachmentClothActivationDoesNotAffectAnotherRig();
        clonedMorphFramesShareOneLiveXPBDState();
        System.out.println("SkirtXPBDPipelineSmokeTest OK");
    }

    private static void exactlyFollowAnimatedNativeBonePoses()
    {
        Fixture f=new Fixture();
        need(f.rest.maxPoseReconstructionError<1E-6F,
                "original rest geometry must reconstruct exactly");
        for(int p=0;p<40;p++)
            for(int v=0;v<4;v++)
            {
                float[] actual=f.rest.corner(f.binding,f.rest.target,p,v);
                float[] nativeRest=f.raw.patches.get(p).world[v];
                equal(actual,nativeRest,1E-6F,
                        "identity source cube pose must be unchanged");
            }
        float[] translation=identity();
        translation[12]=.21F;translation[13]=.13F;
        translation[14]=-.07F;
        SkirtXPBDPoseSkin.Frame posed=pose(f.raw,f.binding,translation);
        for(int p=0;p<40;p++)
            for(int v=0;v<4;v++)
            {
                float[] expected=f.raw.patches.get(p).world[v].clone();
                expected[0]+=.21F;expected[1]+=.13F;expected[2]-=.07F;
                equal(posed.corner(f.binding,posed.target,p,v),
                        expected,1E-6F,
                        "animated bone translation must follow source skin");
            }
        // Yaw rotation (actor model-space pose), unlike simply moving
        // the skirt to a fixed X offset beside the actor.
        float yaw=.57F;
        float[] rotated=identity();
        rotated[0]=(float)Math.cos(yaw);
        rotated[2]=-(float)Math.sin(yaw);
        rotated[8]=(float)Math.sin(yaw);
        rotated[10]=(float)Math.cos(yaw);
        SkirtXPBDPoseSkin.Frame turned=pose(f.raw,f.binding,rotated);
        for(int p=0;p<40;p++)
            for(int v=0;v<4;v++)
            {
                float[] x=f.raw.patches.get(p).world[v];
                float[] goal={rotated[0]*x[0]+rotated[8]*x[2],
                        x[1],rotated[2]*x[0]+rotated[10]*x[2]};
                equal(turned.corner(f.binding,turned.target,p,v),
                        goal,1E-6F,"rotated bone changed native UV corner XYZ");
            }
    }

    /** Regression for the first real XPBD screenshot: the 40 selected
     * front quads + bridges had cyan/green/red cardboard edges and no
     * detached belt. This alternate material draw must preserve ALL
     * original cube sides/caps, original native UVs and a moving waist. */
    private static void nativeVolumeFollowsXPBDWithoutDestroyingTextureFaces()
    {
        Fixture f=new Fixture();
        List<SkirtClothSurfaceMesh.Strip> strips=
                SkirtClothSurfaceMeshSmokeTest.fixture();
        float[] translation=identity();
        translation[12]=.13F;translation[13]=.04F;
        SkirtXPBDPoseSkin.Frame posed=pose(f.raw,f.binding,translation);
        List<ChameleonSkirtVolumeSnapshot.Part> original=
                new ArrayList<ChameleonSkirtVolumeSnapshot.Part>();
        List<ChameleonSkirtVolumeSnapshot.Part> animated=
                new ArrayList<ChameleonSkirtVolumeSnapshot.Part>();
        float[] white={1F,1F,1F,1F,0F,0F};
        // The real 43-cube skirt includes 3 ALL-bone waist cubes;
        // model them with independent full six-quad cube lists.
        for(int i=0;i<3;i++)
            volumePart(original,animated,"ALL",
                    strips.get(i).upper,translation,white);
        for(SkirtClothSurfaceMesh.Strip strip:strips)
        {
            volumePart(original,animated,strip.upper.bone,
                    strip.upper,translation,white);
            volumePart(original,animated,strip.lower.bone,
                    strip.lower,translation,white);
        }
        ChameleonSkirtVolumeSnapshot.Snapshot rest=
                ChameleonSkirtVolumeSnapshot.capture(original);
        ChameleonSkirtVolumeSnapshot.Snapshot current=
                ChameleonSkirtVolumeSnapshot.capture(animated);
        SkirtXPBDVolumeSkin.Mesh neutral=SkirtXPBDVolumeSkin.build(
                rest,current,f.raw,f.binding,posed,posed.target);
        need(neutral.cubes==43&&neutral.quads==258 &&
                neutral.waistQuads==18&&neutral.fabricQuads==240,
                "XPBD original material volume omitted native cube faces");
        need(neutral.maxNodeDisplacement==0F &&
                neutral.maxVisualDisplacement==0F,
                "source pose must not be moved when particle targets match");
        for(int face=0;face<current.faces.size();face++)
        {
            ChameleonSkirtVolumeSnapshot.Face source=current.faces.get(face);
            SkirtXPBDVolumeSkin.Face result=neutral.faces.get(face);
            need(source.bone.equals(result.bone),
                    "native volume face order changed");
            for(int v=0;v<4;v++)
            {
                equal(result.world[v],source.world[v],1E-6F,
                        "XPBD zero-displacement altered current Chameleon pose");
                equal2(result.uv[v],source.uv[v],0F,
                        "source cube UV must survive exact native skin copy");
            }
        }

        float[][] dynamic=new float[60][3];
        for(int n=0;n<60;n++)
        {
            dynamic[n]=posed.target[n].clone();
            if(f.shared.nodes.get(n).row>0)
            {
                dynamic[n][0]+=.025F;
                dynamic[n][1]-=.013F;
            }
        }
        SkirtXPBDVolumeSkin.Mesh moving=SkirtXPBDVolumeSkin.build(
                rest,current,f.raw,f.binding,posed,dynamic);
        need(moving.maxNodeDisplacement>.02F &&
                moving.maxVisualDisplacement>.01F &&
                moving.maxVisualDisplacement<.045F,
                "actual XPBD particles did not move the source-volume skin");
        for(int i=0;i<moving.faces.size();i++)
        {
            SkirtXPBDVolumeSkin.Face live=moving.faces.get(i);
            ChameleonSkirtVolumeSnapshot.Face source=current.faces.get(i);
            for(int v=0;v<4;v++)
            {
                equal2(live.uv[v],source.uv[v],0F,
                        "dynamic cloth corrupted a native source-face UV");
                if(live.waist)
                    equal(live.world[v],source.world[v],1E-6F,
                            "XPBD may not deform source waist cuboids");
            }
        }
        // Native volume render now samples TWO intermediate physical rings
        // without replacing original UV corners or waistband cubes.
        SkirtXPBDSolver.Result neutralFine=
                SkirtXPBDSolver.create(f.raw,f.shared,posed).current();
        SkirtXPBDVolumeSkin.Mesh refined=SkirtXPBDVolumeSkin.build(
                rest,current,f.raw,f.binding,posed,neutralFine);
        if(SkirtXPBDSolver.ROWS==5)
        {
            need(refined.quads==978&&refined.waistQuads==18&&
                    refined.fabricQuads==960,
                    "20x5 skirt must subdivide 240 fabric faces into 960");
            for(SkirtXPBDVolumeSkin.Face face:refined.faces)
            {
                for(float[] uv:face.uv)
                    for(float coord:uv)
                        need(Float.isFinite(coord),
                                "UV subdivision created non-finite coordinates");
                for(float[] xyz:face.world)
                    for(float coord:xyz)
                        need(Float.isFinite(coord),
                                "refined volumetric fabric is non-finite");
            }
        }
        boolean rejected=false;
        try{SkirtXPBDVolumeSkin.build(
                rest,null,f.raw,f.binding,posed,dynamic);}
        catch(IllegalArgumentException expected){rejected=true;}
        need(rejected,"incomplete native volume must fail before hiding cubes");
    }

    private static void volumePart(
            List<ChameleonSkirtVolumeSnapshot.Part> rest,
            List<ChameleonSkirtVolumeSnapshot.Part> posed,
            String bone,SkirtClothSurfaceMesh.Cube cube,
            float[] shift,float[] material)
    {
        rest.add(new ChameleonSkirtVolumeSnapshot.Part(
                new SkirtClothSurfaceMesh.Cube(bone,
                        cube.restMatrix,cube.quads),material));
        float[] m=cube.restMatrix.clone();
        m[12]+=shift[12];m[13]+=shift[13];m[14]+=shift[14];
        posed.add(new ChameleonSkirtVolumeSnapshot.Part(
                new SkirtClothSurfaceMesh.Cube(bone,m,cube.quads),
                material));
    }

    private static void originalUvsAndSeamBridgesAreConnected()
    {
        Fixture f=new Fixture();
        need(f.topology.quads.size()==160,
                "40 original faces + 120 half-seam bridges");
        for(int i=0;i<40;i++)
        {
            SkirtXPBDSurfaceTopology.Quad q=f.topology.quads.get(i);
            need(!q.bridge,"original material quad before bridges");
            for(int v=0;v<4;v++)
            {
                equal2(q.vertices[v].uv,f.raw.patches.get(i).uv[v],
                        0F,"source UV changed");
                float[] xyz=f.topology.position(q,q.vertices[v],
                        f.rest,f.binding,f.rest.target);
                equal(xyz,f.raw.patches.get(i).world[v],
                        1E-6F,"source panel stretched at rest");
            }
        }
        for(int i=0;i<60;i++)
        {
            SkirtXPBDSurfaceTopology.Quad a=
                    f.topology.quads.get(40+i*2);
            SkirtXPBDSurfaceTopology.Quad b=
                    f.topology.quads.get(41+i*2);
            need(a.bridge&&b.bridge,"both half-seams must exist");
            float[] a2=f.topology.position(a,a.vertices[2],
                    f.rest,f.binding,f.rest.target);
            float[] b1=f.topology.position(b,b.vertices[1],
                    f.rest,f.binding,f.rest.target);
            equal(a2,b1,1E-6F,"bridge halves must share exact midpoint");
            float[] a3=f.topology.position(a,a.vertices[3],
                    f.rest,f.binding,f.rest.target);
            float[] b0=f.topology.position(b,b.vertices[0],
                    f.rest,f.binding,f.rest.target);
            equal(a3,b0,1E-6F,"other bridge edge must share midpoint");
        }
    }

    private static void xpbdHasPinnedWaistGravityAndBounds()
    {
        Fixture f=new Fixture();
        SkirtXPBDSolver.State simulation=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        float[][] first=simulation.current().particles;
        SkirtXPBDSolver.Result result=null;
        for(int i=0;i<50;i++)
            result=simulation.advance(1F/60F,f.rest,null);
        need(result!=null&&result.substeps==2,"two solver substeps");
        need(result.particles.length==60,"60 real cloth nodes");
        float changed=0F;
        for(int n=0;n<60;n++)
        {
            float[] point=result.particles[n];
            for(float v:point)
                need(Float.isFinite(v),"XPBD cloth must stay finite");
            if(f.shared.nodes.get(n).row==0)
                equal(point,f.rest.target[n],1E-7F,
                        "waist particle must follow actor exactly");
            else
            {
                changed=Math.max(changed,
                        distance(point,first[n]));
                need(distance(point,f.rest.target[n])<=.371F,
                        "no falling-through or exploding cloth node");
            }
        }
        need(changed>.0001F,
                "gravity must actually displace free cloth under its own weight");
        need(result.maxDistanceError<.20F,
                "XPBD constraint errors should remain bounded");
        simulation.rootMotion(new float[]{.06F,0,0},identity());
        result=simulation.advance(1F/60F,f.rest,null);
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row==0)
                equal(result.particles[n],f.rest.target[n],1E-7F,
                        "root motion must not displace pinned waist");

        boolean rejected=false;
        try{simulation.advance(.5F,f.rest,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        need(rejected,"unbounded time step must fail closed");
    }

    /**
     * A finer internal fabric lattice must NOT reorder the original 60
     * Chameleon skin/UV anchors. Original pose reproduction stays exact.
     */
    private static void refinedGridRetainsNativeSkinCorrespondence()
    {
        Fixture f=new Fixture();
        SkirtXPBDSolver.State state=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        SkirtXPBDSolver.Result initial=state.current();
        need(initial.particles.length==60&&
                initial.refinedParticles.length==
                        SkirtXPBDSolver.SIM_NODES,
                "refined solver changed native 60-node binding");
        for(int n=0;n<60;n++)
        {
            int fine=initial.sourceToRefined[n];
            need(fine>=0&&fine<SkirtXPBDSolver.SIM_NODES,
                    "source node is not mapped to refined grid");
            equal(initial.particles[n],f.rest.target[n],1E-6F,
                    "fine grid moved authored source anchor");
            equal(initial.refinedParticles[fine],
                    initial.particles[n],1E-6F,
                    "fine grid remapping differs from native skin");
        }
        if(SkirtXPBDSolver.ROWS==5)
        {
            need(initial.refinedParticles.length==100,
                    "20x5 skirt lattice must contain 100 nodes");
            for(int c=0;c<20;c++)
                for(int r=1;r<5;r+=2)
                {
                    float[] a=initial.refinedParticles[(r-1)*20+c];
                    float[] b=initial.refinedParticles[(r+1)*20+c];
                    float[] mid=initial.refinedParticles[r*20+c];
                    for(int k=0;k<3;k++)
                        need(Math.abs(mid[k]-(a[k]+b[k])*.5F)<1E-5F,
                                "extra cloth latitude has incorrect rest pose");
                }
        }
        for(int i=0;i<3;i++)
            state.advance(1F/60F,f.rest,null);
        SkirtXPBDSolver.Result moved=state.current();
        for(int n=0;n<60;n++)
            for(float v:moved.particles[n])
                need(Float.isFinite(v),
                        "new fine solver produced nonfinite native geometry");
    }

    /** A newly posed Chameleon frame must carry existing XPBD dynamics
     * into the CURRENT pose even if that render has dt=0 (pause/seek).
     * The same paused frame twice must not move the nodes a second time. */
    private static void pausedPoseChangePreservesClothRelativeMotion()
    {
        Fixture f=new Fixture();
        SkirtXPBDSolver.State solver=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        for(int i=0;i<8;i++)
            solver.advance(1F/60F,f.rest,null);
        float[][] before=solver.current().particles;
        float[] move=identity();
        move[12]=.10F;
        SkirtXPBDPoseSkin.Frame next=pose(f.raw,f.binding,move);
        SkirtXPBDSolver.Result paused=solver.advance(0F,next,null);
        need(paused.substeps==0,"pause must not advance cloth integration");
        for(int n=0;n<60;n++)
        {
            float[] expected=before[n].clone();
            expected[0]+=.10F;
            equal(paused.particles[n],expected,1E-5F,
                    "paused native pose delta must carry cloth position");
        }
        SkirtXPBDSolver.Result repeat=solver.advance(0F,next,null);
        for(int n=0;n<60;n++)
            equal(repeat.particles[n],paused.particles[n],1E-7F,
                    "repeated pause render must be idempotent");
        SkirtXPBDSolver.Result running=solver.advance(1F/60F,next,null);
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row==0)
                equal(running.particles[n],next.target[n],1E-6F,
                        "playback must not detach the authored waistband");
    }

    /** Regression: nearest-face exit at a thigh's center pushed LEFT
     * cloth to the right (into the actor), particularly on a wide walk. */
    private static void outwardThighContactsPreserveHemSide()
    {
        Object morph=new Object();
        float[] left={-.16F,.46F,0F,0F,0F,0F,1F,
                .13F,.18F,.14F};
        float[] right={.16F,.46F,0F,0F,0F,0F,1F,
                .13F,.18F,.14F};
        SkirtXPBDContacts.publish(morph,40,identity(),
                new float[][]{left,right},
                java.util.Arrays.asList("emoticons.left_leg",
                        "emoticons.right_leg"));
        SkirtXPBDContacts.Frame frame=SkirtXPBDContacts.forMorph(morph);
        need(frame!=null&&frame.legBoxes.size()==2,
                "real bilateral thigh fitting must be available");
        float[] hitLeft={-.16F,.46F,0F};
        float[] hitRight={.16F,.46F,0F};
        float[] pushedLeft=SkirtXPBDContacts.corrected(
                hitLeft,frame,.018F,new float[]{-1F,0F,0F});
        float[] pushedRight=SkirtXPBDContacts.corrected(
                hitRight,frame,.018F,new float[]{1F,0F,0F});
        need(pushedLeft[0]<hitLeft[0]-.01F,
                "left-thigh interior must exit OUTSIDE, not toward center");
        need(pushedRight[0]>hitRight[0]+.01F,
                "right-thigh interior must exit OUTSIDE, not toward center");
        float[] noHit={.60F,.46F,.60F};
        equal(SkirtXPBDContacts.corrected(
                noHit,frame,.018F,new float[]{1F,0F,1F}),
                noHit,1E-6F,"outside cloth must remain unchanged");
    }

    /**
     * Tapered 3-zone thigh OBBs become wider than their AXIAL half-length.
     * Choosing the longest box dimension mistakes X/Z for the leg axis
     * and pushes a sitting skirt sideways. A bone rotated ~80 degrees
     * about X must still provide an upward thigh support.
     */
    private static void taperedThighUsesBoneAxisEvenWhenZoneIsWiderThanLong()
    {
        float qx=(float)Math.sin(Math.toRadians(80D)/2D),
                qw=(float)Math.cos(Math.toRadians(80D)/2D);
        float[] left={-.16F,.46F,0F,qx,0F,0F,qw,
                .11F,.099F,.14F};
        float[] right={.16F,.46F,0F,qx,0F,0F,qw,
                .11F,.099F,.14F};
        Object seated=new Object();
        SkirtXPBDContacts.publish(seated,51,identity(),
                new float[][]{left,right},java.util.Arrays.asList(
                        "emoticons.left_leg@skirt-middle",
                        "emoticons.right_leg@skirt-middle"));
        SkirtXPBDContacts.Frame fit=SkirtXPBDContacts.forMorph(seated);
        need(fit!=null&&fit.seatedBlend>.90F,
                "test requires both genuine horizontal upper thigh axes");
        need(fit.legOwners.size()==fit.legBoxes.size()&&
                fit.legOwners.get(0).contains("left_leg"),
                "XPBD collider labels must align with fitted OBBs");
        float[] point={-.16F,.49F,0F};
        float[] top=SkirtXPBDContacts.corrected(
                point,fit,.018F,new float[]{-1F,0F,0F});
        need(top[1]>point[1]+.08F&&
                Math.abs(top[0]-point[0])<.02F,
                "80-degree taper zone must support cloth UP, not outward");

        // Same flattened zone has a vertical BONE axis when upright.
        Object upright=new Object();
        float[] l={-.16F,.46F,0F,0F,0F,0F,1F,.11F,.099F,.14F};
        float[] r={.16F,.46F,0F,0F,0F,0F,1F,.11F,.099F,.14F};
        SkirtXPBDContacts.publish(upright,51,identity(),
                new float[][]{l,r},java.util.Arrays.asList(
                        "emoticons.left_leg@skirt-middle",
                        "emoticons.right_leg@skirt-middle"));
        SkirtXPBDContacts.Frame walk=SkirtXPBDContacts.forMorph(upright);
        need(walk!=null&&walk.seatedBlend==0F,
                "vertical thigh wrongly activated seated contacts");
        float[] side=SkirtXPBDContacts.corrected(
                point,walk,.018F,new float[]{-1F,0F,0F});
        need(side[0]<point[0]-.08F,
                "standing taper zone lost normal outward contact");
    }

    /**
     * Genuine CPU-skinned thigh triangles form ONE collision surface even
     * when coarse OBB zones are discontinuous. Verify both world/cloth
     * transforms and the contact normal at a shared triangle edge. No
     * Minecraft runtime, native Jolt, or synthesized boxes are needed.
     */
    private static void skinnedThighMeshProvidesContinuousContact()
    {
        float[] world=identity();
        world[12]=3F;
        world[14]=-2F;
        java.util.List<SkirtXPBDContacts.SkinRegion> regions=
                new java.util.ArrayList<SkirtXPBDContacts.SkinRegion>();
        for(int side=0;side<2;side++)
        {
            float middle=side==0?-.25F:.25F;
            float[] vertices=new float[27];
            int p=0;
            for(int row=0;row<3;row++)
                for(int col=0;col<3;col++)
                {
                    vertices[p++]=middle+(col-1)*.12F;
                    vertices[p++]=.50F;
                    vertices[p++]=(row-1)*.12F;
                }
            int[] indices=new int[24];
            int o=0;
            for(int row=0;row<2;row++)
                for(int col=0;col<2;col++)
                {
                    int a=row*3+col,b=a+1,c=a+3,d=c+1;
                    // +Y outward normal. Shared seams must be seamless.
                    indices[o++]=a;indices[o++]=c;indices[o++]=b;
                    indices[o++]=b;indices[o++]=c;indices[o++]=d;
                }
            regions.add(new SkirtXPBDContacts.SkinRegion(
                    side==0?"emoticons.left_leg":
                            "emoticons.right_leg",vertices,indices));
        }
        SkirtXPBDContacts.SkinSurface skin=
                SkirtXPBDContacts.SkinSurface.build(world,regions);
        if(!SkirtXPBDContacts.SKINNED_MESH_CONTACTS)return;
        need(skin!=null&&skin.triangleCount==16&&
                skin.leftCount==8&&skin.rightCount==8,
                "real triangle thigh hull did not publish both legs");
        need(skin.bounds[0]>2F&&skin.bounds[5]<-1F,
                "skinned Emoticons vertices were not transformed to actor world");
        float[] left={2.75F,.50F,-2F,0F,0F,0F,1F,
                .11F,.15F,.11F};
        float[] right={3.25F,.50F,-2F,0F,0F,0F,1F,
                .11F,.15F,.11F};
        Object morph=new Object();
        SkirtXPBDContacts.publish(morph,77,77D,world,
                new float[][]{left,right},
                java.util.Arrays.asList("emoticons.left_leg",
                        "emoticons.right_leg"),skin);
        SkirtXPBDContacts.Frame scene=SkirtXPBDContacts.forMorph(morph);
        need(scene!=null&&scene.skinSurface==skin,
                "published skirt sample ignored animated triangle mesh");

        float[] cloth={-.25F,.49F,0F};
        float[] corrected=SkirtXPBDContacts.corrected(cloth,scene,
                .018F,new float[]{-1F,0F,0F});
        need(corrected[1]>.51F&&
                Math.abs(corrected[0]-cloth[0])<.002F,
                "mesh did not project fabric along REAL thigh top normal");
        float[] onEdge={-.25F,.485F,0F};
        float[] seam=SkirtXPBDContacts.corrected(onEdge,scene,.018F);
        need(seam[1]>.505F,
                "shared skinned triangle edge introduced collider gap");
        float[] free={0F,.49F,0F};
        equal(SkirtXPBDContacts.corrected(free,scene,.018F),
                free,1E-6F,
                "mesh collision invented a bridge between the two legs");
        float[] above={-.25F,.63F,0F};
        equal(SkirtXPBDContacts.corrected(above,scene,.018F),
                above,1E-6F,
                "cloth above the actual Emoticons skin got pushed again");
    }

    /**
     * The real user's Emoticons character has 1704 triangles per thigh
     * BEFORE the pelvis mesh. 4500 total was an unjustified early fallback.
     * Also exercise one malformed long triangle among valid skinned faces:
     * skipping it must not discard both complete thighs.
     */
    private static void denseEmoticonsSkinDoesNotFallBackFor4500PlusTriangles()
    {
        if(!SkirtXPBDContacts.SKINNED_MESH_CONTACTS)return;
        java.util.List<SkirtXPBDContacts.SkinRegion> meshes=
                new java.util.ArrayList<SkirtXPBDContacts.SkinRegion>();
        String[] owners={"emoticons.left_leg",
                "emoticons.right_leg","emoticons.low_body"};
        int[] counts={1704,1704,1100};
        for(int side=0;side<3;side++)
        {
            float middle=side==0?-.28F:side==1?.28F:0F;
            float[] vertices={
                    middle-.08F,.50F,-.08F,
                    middle+.08F,.50F,-.08F,
                    middle-.08F,.50F,.08F,
                    middle+.08F,.50F,.08F,
                    middle-1.15F,.5F,0F,
                    middle+1.15F,.5F,0F,
                    middle,.5F,.05F};
            int count=counts[side];
            int[] indices=new int[count*3+(side==0?3:0)];
            for(int j=0;j<count;j++)
            {
                int[] tri=j%2==0?
                        new int[]{0,2,1}:new int[]{1,2,3};
                System.arraycopy(tri,0,indices,j*3,3);
            }
            if(side==0)
            {
                indices[count*3]=4;
                indices[count*3+1]=5;
                indices[count*3+2]=6;
            }
            meshes.add(new SkirtXPBDContacts.SkinRegion(
                    owners[side],vertices,indices));
        }
        String[] diagnostic={"unset"};
        SkirtXPBDContacts.SkinSurface hull=
                SkirtXPBDContacts.SkinSurface.build(
                        identity(),meshes,diagnostic);
        need(hull!=null,
                "dense Emoticons skin unexpectedly fell back: "+
                        diagnostic[0]);
        need(hull.triangleCount==4508&&
                hull.leftCount==1704&&hull.rightCount==1704&&
                hull.pelvisCount==1100,
                "wrong retained triangle regions after outlier cleanup");
        need(diagnostic[0].contains("skippedOversized=1"),
                "malformed long triangle was not rejected individually");
        float[] probe={-.28F,.49F,0F};
        float[] projected=hull.correctedWorld(probe,.018F);
        need(projected[1]>probe[1]+.005F,
                "dense thigh contact surface no longer supports cloth");
    }

    /** A horizontal thigh should carry the front hem from beneath instead
     * of throwing the cloth forward, while ordinary upright thighs continue
     * using the familiar outward exit. */
    private static void raisedThighContactsSupportClothFromAbove()
    {
        Object morph=new Object();
        float[] left={-.16F,.46F,0F,0F,0F,0F,1F,
                .34F,.12F,.13F}; // long axis along X: raised thigh
        float[] right={.45F,.46F,0F,0F,0F,0F,1F,
                .34F,.12F,.13F};
        SkirtXPBDContacts.publish(morph,45,identity(),
                new float[][]{left,right},
                java.util.Arrays.asList("emoticons.left_thigh",
                        "emoticons.right_thigh"));
        SkirtXPBDContacts.Frame frame=SkirtXPBDContacts.forMorph(morph);
        need(frame!=null,"raised real thigh collider missing");
        float[] point={-.16F,.51F,0F};
        float[] supported=SkirtXPBDContacts.corrected(
                point,frame,.018F,new float[]{-1F,0F,0F});
        if(Boolean.parseBoolean(System.getProperty(
                "bbs.editor.physics.skirt.xpbdSeatedContacts","false")))
            need(supported[1]>.59F&&
                    Math.abs(supported[0]-point[0])<.01F,
                    "opt-in raised-thigh projection should exit UP");
        else
            need(supported[0]<point[0]-.01F,
                    "disabled seated experiment must retain radial exit");
        // A vertical leg must still push toward the skirt's OUTER side.
        Object standing=new Object();
        float[] upright={-.16F,.46F,0F,0F,0F,0F,1F,
                .12F,.34F,.13F};
        float[] other={.45F,.46F,0F,0F,0F,0F,1F,
                .12F,.34F,.13F};
        SkirtXPBDContacts.publish(standing,45,identity(),
                new float[][]{upright,other},
                java.util.Arrays.asList("emoticons.left_thigh",
                        "emoticons.right_thigh"));
        SkirtXPBDContacts.Frame stood=SkirtXPBDContacts.forMorph(standing);
        float[] walking=SkirtXPBDContacts.corrected(
                point,stood,.018F,new float[]{-1F,0F,0F});
        need(walking[0]<point[0]-.08F,
                "vertical thigh should preserve outward walking contact");
    }


    /**
     * Real seated draping is not synonymous with one raised leg. Compare
     * two rotated upper thighs against walking, test finite 60Hz stepping,
     * then return to upright with the normal .37-block cloth limit.
     */

    /**
     * Regression: a thin thigh can intersect the INSIDE of a 20x5 panel
     * even with all 100 physics vertices outside the OBB. This case was
     * previously invisible to the point-only contact solver.
     */
    private static void seatedTriangleInteriorContactDoesNotRequireVertexPenetration()
    {
        if(SkirtXPBDSolver.ROWS!=5)return;
        Fixture f=new Fixture();
        SkirtXPBDSolver.State state=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        float[][] mesh=state.current().refinedParticles;
        float best=-1F;
        float[] middle=null;
        for(int row=1;row<4;row++)
            for(int c=0;c<20;c++)
            {
                int a=row*20+c,b=(row+1)*20+c,
                        d=(row+1)*20+(c+1)%20;
                float[] centroid=new float[3];
                for(int k=0;k<3;k++)
                    centroid[k]=(mesh[a][k]+mesh[b][k]+mesh[d][k])/3F;
                float nearest=Float.POSITIVE_INFINITY;
                for(float[] vertex:mesh)
                    nearest=Math.min(nearest,distance(centroid,vertex));
                if(nearest>best)
                {
                    best=nearest;
                    middle=centroid;
                }
            }
        need(middle!=null&&best>.038F,
                "fixture needs a triangle interior clear of all vertices");
        float s=(float)Math.sin(Math.toRadians(80D)/2D);
        float c=(float)Math.cos(Math.toRadians(80D)/2D);
        float[] left={middle[0],middle[1],middle[2],
                s,0F,0F,c,.016F,.016F,.016F};
        float[] right={middle[0]+5F,middle[1],middle[2],
                s,0F,0F,c,.016F,.016F,.016F};
        Object morph=new Object();
        SkirtXPBDContacts.publish(morph,27,identity(),
                new float[][]{left,right},java.util.Arrays.asList(
                        "emoticons.left_leg","emoticons.right_leg"));
        SkirtXPBDContacts.Frame bodies=
                SkirtXPBDContacts.forMorph(morph);
        need(bodies!=null&&bodies.seatedBlend>.80F,
                "surface regression requires a deeply seated bilateral pose");
        float[] midpointAfter=SkirtXPBDContacts.corrected(
                middle,bodies,.018F);
        need(distance(middle,midpointAfter)>.01F,
                "triangle center must actually overlap a fitted OBB");
        for(float[] v:mesh)
            need(distance(v,SkirtXPBDContacts.corrected(
                    v,bodies,.018F))<.00001F,
                    "test fixture accidentally penetrates at a vertex");
        SkirtXPBDSolver.Result after=state.advance(0F,f.rest,bodies);
        need(after.substeps==0&&after.legContacts>0,
                "seated polygon interior contact was not detected");
        float moved=0F;
        for(int n=0;n<mesh.length;n++)
            moved=Math.max(moved,
                    distance(after.refinedParticles[n],mesh[n]));
        need(moved>.0001F,
                "polygon collision failed to reposition any cloth vertices");
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row==0)
                equal(after.particles[n],f.rest.target[n],1E-6F,
                        "seated panel correction displaced a waist pin");
    }

    /**
     * Deep bend: the first free horizontal latitudes must not balloon
     * beyond their native circumference. A fake root inertial offset
     * creates an over-expanded upper hoop without moving any actual
     * Emoticons thighs. Contrast seated and walking; the unprotected hem
     * remains free to drape. No authoring bone/UV is modified.
     */
    private static void seatedUpperHoopGuardDoesNotFreezeTheHem()
    {
        if(!Boolean.parseBoolean(System.getProperty(
                "bbs.editor.physics.skirt.xpbdSeatedUpperControl","true")))
            return;
        Fixture f=new Fixture();
        float t=(float)Math.sin(Math.toRadians(80D)/2D),
                w=(float)Math.cos(Math.toRadians(80D)/2D);
        float[] seatedLeft={-5F,-5F,0F,t,0F,0F,w,
                .12F,.20F,.12F};
        float[] seatedRight={5F,-5F,0F,t,0F,0F,w,
                .12F,.20F,.12F};
        float[] walkingLeft={-5F,-5F,0F,0F,0F,0F,1F,
                .12F,.20F,.12F};
        float[] walkingRight={5F,-5F,0F,0F,0F,0F,1F,
                .12F,.20F,.12F};
        java.util.List<String> owners=java.util.Arrays.asList(
                "emoticons.left_leg","emoticons.right_leg");
        Object bent=new Object(),straight=new Object();
        SkirtXPBDContacts.publish(bent,120,identity(),
                new float[][]{seatedLeft,seatedRight},owners);
        SkirtXPBDContacts.publish(straight,120,identity(),
                new float[][]{walkingLeft,walkingRight},owners);
        SkirtXPBDContacts.Frame seated=SkirtXPBDContacts.forMorph(bent);
        SkirtXPBDContacts.Frame walking=SkirtXPBDContacts.forMorph(straight);
        need(seated!=null&&seated.seatedBlend>.99F&&
                walking!=null&&walking.seatedBlend==0F,
                "upper-hoop regression requires isolated sitting and walking");
        SkirtXPBDSolver.State sat=SkirtXPBDSolver.create(
                f.raw,f.shared,f.rest);
        SkirtXPBDSolver.State walk=SkirtXPBDSolver.create(
                f.raw,f.shared,f.rest);
        for(int i=0;i<4;i++)
        {
            sat.rootMotion(new float[]{.32F,0F,0F},identity());
            walk.rootMotion(new float[]{.32F,0F,0F},identity());
        }
        SkirtXPBDSolver.Result s0=sat.current();
        SkirtXPBDSolver.Result s1=sat.advance(0F,f.rest,seated);
        SkirtXPBDSolver.Result w1=walk.advance(0F,f.rest,walking);
        float[] center=new float[3];
        // The waist center is defined by the solver's zero-th ring.
        for(int c=0;c<20;c++)
            for(int k=0;k<3;k++)
                center[k]+=s1.refinedTarget[c][k]/20F;
        float maxWalking=0F, maxSeated=0F;
        for(int c=0;c<20;c++)
        {
            int row=1,n=row*20+c;
            float original=horizontalDistance(
                    s1.refinedTarget[n],center);
            float before=horizontalDistance(
                    s0.refinedParticles[n],center);
            float after=horizontalDistance(
                    s1.refinedParticles[n],center);
            float walkAfter=horizontalDistance(
                    w1.refinedParticles[n],center);
            maxWalking=Math.max(maxWalking,walkAfter-original);
            maxSeated=Math.max(maxSeated,after-original);
            need(after<=original*1.08F+.019F,
                    "sitting must contain upper hoop expansion");
            if(before>original*1.08F+.02F)
                need(after<before-.005F,
                        "sitting failed to retract the inflated upper ring");
            equal(s1.refinedParticles[c],s1.refinedTarget[c],
                    1E-6F,"upper hoop stabilizer moved waist");
        }
        need(maxWalking>maxSeated+.015F,
                "new upper control altered ordinary walking contacts");
        if(SkirtXPBDSolver.ROWS==5)
        {
            float bottomDrift=0F;
            for(int c=0;c<20;c++)
                bottomDrift=Math.max(bottomDrift,distance(
                        s1.refinedParticles[4*20+c],
                        s1.refinedTarget[4*20+c]));
            need(bottomDrift>.10F,
                    "lower hem should remain free to fold after a seated cap");
        }
    }
    private static float horizontalDistance(float[] a,float[] b)
    {
        float dx=a[0]-b[0],dz=a[2]-b[2];
        return (float)Math.sqrt(dx*dx+dz*dz);
    }

    private static void bilateralSittingFoldsWithoutBreakingWalking()
    {
        float[] id=identity();
        float sine=(float)Math.sin(Math.toRadians(80D)/2D);
        float cosine=(float)Math.cos(Math.toRadians(80D)/2D);
        float[] leftBent={-.16F,.46F,0F,sine,0F,0F,cosine,
                .13F,.28F,.14F};
        float[] rightBent={.16F,.46F,0F,sine,0F,0F,cosine,
                .13F,.28F,.14F};
        float[] upright={-.16F,.46F,0F,0F,0F,0F,1F,
                .13F,.28F,.14F};
        java.util.List<String> labels=java.util.Arrays.asList(
                "emoticons.left_leg","emoticons.right_leg");
        Object seated=new Object(),stepping=new Object(),
                walking=new Object();
        SkirtXPBDContacts.publish(seated,20,id,
                new float[][]{leftBent,rightBent},labels);
        SkirtXPBDContacts.publish(stepping,20,id,
                new float[][]{leftBent,
                        new float[]{.16F,.46F,0F,0F,0F,0F,1F,
                                .13F,.28F,.14F}},labels);
        SkirtXPBDContacts.publish(walking,20,id,
                new float[][]{upright,
                        new float[]{.16F,.46F,0F,0F,0F,0F,1F,
                                .13F,.28F,.14F}},labels);
        SkirtXPBDContacts.Frame bent=SkirtXPBDContacts.forMorph(seated);
        SkirtXPBDContacts.Frame one=SkirtXPBDContacts.forMorph(stepping);
        SkirtXPBDContacts.Frame straight=SkirtXPBDContacts.forMorph(walking);
        need(bent!=null&&bent.seatedBlend>.85F,
                "both horizontal thighs must activate deep seated folds");
        need(one!=null&&one.seatedBlend==0F,
                "one high walking step must retain standing cloth limits");
        need(straight!=null&&straight.seatedBlend==0F,
                "upright gait must not activate seated cloth limits");

        Fixture f=new Fixture();
        SkirtXPBDSolver.State cloth=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        SkirtXPBDSolver.Result result=null;
        for(int t=0;t<24;t++)
            result=cloth.advance(1F/60F,f.rest,bent);
        need(result!=null&&result.refinedParticles.length==
                SkirtXPBDSolver.SIM_NODES,
                "seated simulation did not preserve refined cloth");
        for(int n=0;n<60;n++)
        {
            float[] point=result.particles[n];
            for(float value:point)
                need(Float.isFinite(value),
                        "sitting generated a non-finite skirt vertex");
            if(f.shared.nodes.get(n).row==0)
                equal(point,f.rest.target[n],1E-6F,
                        "seated simulation moved the locked waistband");
        }
        // Recover without residual seated radial expansion.
        Object recovered=new Object();
        float[] l={-.16F,-2F,0F,0F,0F,0F,1F,
                .13F,.28F,.14F};
        float[] r={.16F,-2F,0F,0F,0F,0F,1F,
                .13F,.28F,.14F};
        SkirtXPBDContacts.publish(recovered,21,id,
                new float[][]{l,r},labels);
        SkirtXPBDContacts.Frame after=
                SkirtXPBDContacts.forMorph(recovered);
        need(after!=null&&after.seatedBlend==0F,
                "standing frame should restore original cloth limits");
        for(int t=0;t<24;t++)
            result=cloth.advance(1F/60F,f.rest,after);
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row>0)
                need(distance(result.particles[n],f.rest.target[n])<=.371F,
                        "standing cloth did not recover bounded gait");
    }

    private static void actualBodyObbsProjectWithoutInventedContacts()
    {
        Object morph=new Object();
        need(SkirtXPBDContacts.forMorph(morph)==null,
                "missing Emoticons body fit should not invent leg boxes");
        float[] world=identity();
        float[] left={-.16F,.46F,0F,0F,0F,0F,1F,
                .13F,.18F,.14F};
        float[] right={.16F,.46F,0F,0F,0F,0F,1F,
                .13F,.18F,.14F};
        SkirtXPBDContacts.publish(morph,25,world,
                new float[][]{left,right},
                java.util.Arrays.asList("emoticons.left_leg",
                        "emoticons.right_leg"));
        SkirtXPBDContacts.Frame captured=
                SkirtXPBDContacts.forMorph(morph);
        need(captured!=null&&captured.legBoxes.size()==2 &&
                captured.leftThigh&&captured.rightThigh,
                "must capture both real Emoticons leg OBBs");
        // The editor creates a fresh ChameleonMorph on a new timeline
        // sample. A replacement morph must find the previous track's
        // real collider frame rather than resetting the cloth clock.
        Object track=new Object();
        Object firstMorph=new Object();
        Object nextMorph=new Object();
        SkirtXPBDContacts.registerOwner(firstMorph,track);
        SkirtXPBDContacts.registerOwner(nextMorph,track);
        SkirtXPBDContacts.publish(firstMorph,26,world,
                new float[][]{left,right},
                java.util.Arrays.asList("emoticons.left_leg",
                        "emoticons.right_leg"));
        need(SkirtXPBDContacts.stableOwner(firstMorph)==track &&
                SkirtXPBDContacts.stableOwner(nextMorph)==track,
                "cloned morphs must resolve to one physical actor track");
        SkirtXPBDContacts.Frame next=
                SkirtXPBDContacts.forMorph(nextMorph);
        need(next!=null&&next.timelineFrame==26&&
                next.legBoxes.size()==2,
                "next frame's cloned morph must inherit fitted leg contacts");
        float[] corrected=SkirtXPBDContacts.corrected(
                new float[]{.16F,.46F,0F},captured,.018F);
        need(distance(corrected,new float[]{.16F,.46F,0F})>.01F,
                "point inside fitted thigh must be pushed outside");
        float[] outside=SkirtXPBDContacts.corrected(
                new float[]{.9F,.46F,0F},captured,.018F);
        equal(outside,new float[]{.9F,.46F,0F},1E-6F,
                "point outside leg must not be moved");

        Object bad=new Object();
        SkirtXPBDContacts.publish(bad,26,world,
                new float[][]{left},
                java.util.Arrays.asList("emoticons.left_leg"));
        need(SkirtXPBDContacts.forMorph(bad)==null,
                "single thigh shape must not masquerade as full collision set");
    }

    /**
     * A paused 60Hz scene can be drawn many times for main viewport,
     * shadows, mirrors and video. Every identical call must be bit-stable:
     * no contact ping-pong, no spontaneous recalculation and no
     * phantom Verlet velocity. A newly moved collider at the SAME tick
     * must still get a legitimate one-time collision correction.
     */
    private static void pausedRepeatedDrawIsBitStableUntilLegPoseChanges()
    {
        Fixture f=new Fixture();
        float[][] tint=new float[40][];
        for(int i=0;i<40;i++)
            tint[i]=new float[]{1F,1F,1F,1F,0F,0F};
        Object morph=new Object();
        float[] distantLeft={-5F,-5F,0F,0F,0F,0F,1F,
                .10F,.20F,.10F};
        float[] distantRight={5F,-5F,0F,0F,0F,0F,1F,
                .10F,.20F,.10F};
        float[][] far={distantLeft,distantRight};
        java.util.List<String> owners=java.util.Arrays.asList(
                "emoticons.left_leg","emoticons.right_leg");
        SkirtXPBDContacts.publish(morph,31,31D,identity(),far,owners);
        SkirtXPBDRuntime.RenderFrame first=SkirtXPBDRuntime.sample(
                morph,f.raw,f.shared,f.binding,f.topology,f.rest,tint,
                SkirtXPBDContacts.forMorph(morph));
        need(first!=null&&first.physicsSteps==1,
                "first frame must initialize physics exactly once");
        for(int repeat=0;repeat<12;repeat++)
        {
            SkirtXPBDContacts.publish(morph,31,31D,identity(),far,owners);
            SkirtXPBDRuntime.RenderFrame again=SkirtXPBDRuntime.sample(
                    morph,f.raw,f.shared,f.binding,f.topology,f.rest,tint,
                    SkirtXPBDContacts.forMorph(morph));
            need(again.physicsSteps==0&&again.solved.substeps==0,
                    "same scene time must never integrate again");
            for(int n=0;n<SkirtXPBDSolver.SIM_NODES;n++)
                equal(again.solved.refinedParticles[n],
                        first.solved.refinedParticles[n],0F,
                        "unchanged paused frame drifted in repeated draw");
        }

        // New thigh location on the same paused timeline frame: still
        // detect penetration rather than blindly caching all dt=0 calls.
        int free=-1;
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row>0){free=n;break;}
        need(free>=0,"missing free cloth control node");
        float[] p=first.solved.particles[free];
        float[] nearLeft={p[0],p[1],p[2],
                0F,0F,0F,1F,.09F,.12F,.09F};
        float[] nearRight={p[0]+.28F,p[1],p[2],
                0F,0F,0F,1F,.09F,.12F,.09F};
        float[][] near={nearLeft,nearRight};
        SkirtXPBDContacts.publish(morph,31,31D,identity(),near,owners);
        SkirtXPBDRuntime.RenderFrame changed=SkirtXPBDRuntime.sample(
                morph,f.raw,f.shared,f.binding,f.topology,f.rest,tint,
                SkirtXPBDContacts.forMorph(morph));
        need(changed.physicsSteps==0&&changed.solved.legContacts>0,
                "new fitted thigh at paused tick was silently ignored");
        for(int repeat=0;repeat<12;repeat++)
        {
            SkirtXPBDContacts.publish(morph,31,31D,identity(),near,owners);
            SkirtXPBDRuntime.RenderFrame again=SkirtXPBDRuntime.sample(
                    morph,f.raw,f.shared,f.binding,f.topology,f.rest,tint,
                    SkirtXPBDContacts.forMorph(morph));
            for(int n=0;n<SkirtXPBDSolver.SIM_NODES;n++)
                equal(again.solved.refinedParticles[n],
                        changed.solved.refinedParticles[n],0F,
                        "contacted skirt oscillated on identical paused draws");
        }
    }

    /**
     * On a 60fps preview the scene clock contains fractions of a 20-TPS
     * tick. Identical shadow/mirror passes at each time must not advance
     * the solver again. Three 1/60 steps occur over one 20-TPS interval.
     */
    private static void fractionalClockAdvancesOncePerSixtiethSecond()
    {
        Fixture f=new Fixture();
        float[][] tint=new float[40][];
        for(int p=0;p<40;p++)
            tint[p]=new float[]{1F,1F,1F,1F,0F,0F};
        Object morph=new Object();
        float[] left={-0.16F,.46F,0F,0F,0F,0F,1F,
                .10F,.18F,.10F};
        float[] right={.16F,.46F,0F,0F,0F,0F,1F,
                .10F,.18F,.10F};
        float[][] boxes={left,right};
        java.util.List<String> labels=java.util.Arrays.asList(
                "emoticons.left_leg","emoticons.right_leg");
        int[] expected={1,1,1,1};
        for(int i=0;i<4;i++)
        {
            double tick=60D+i/3D;
            SkirtXPBDContacts.publish(morph,60,tick,
                    identity(),boxes,labels);
            SkirtXPBDContacts.Frame contact=
                    SkirtXPBDContacts.forMorph(morph);
            need(contact!=null&&Math.abs(contact.sampleTick-tick)<1E-9,
                    "fractional fitted contact clock must be preserved");
            SkirtXPBDRuntime.RenderFrame frame=SkirtXPBDRuntime.sample(
                    morph,f.raw,f.shared,f.binding,f.topology,
                    f.rest,tint,contact);
            need(frame!=null&&frame.physicsSteps==expected[i],
                    "60Hz fixed step missing at sub-tick "+i);
            SkirtXPBDRuntime.RenderFrame mirror=SkirtXPBDRuntime.sample(
                    morph,f.raw,f.shared,f.binding,f.topology,
                    f.rest,tint,contact);
            need(mirror!=null&&mirror.physicsSteps==0 &&
                    mirror.solved.substeps==0,
                    "same-time mirror/overlay must not double-step XPBD");
        }
        SkirtXPBDContacts.publish(morph,61,61D,
                identity(),boxes,labels);
        SkirtXPBDRuntime.RenderFrame next=SkirtXPBDRuntime.sample(
                morph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,SkirtXPBDContacts.forMorph(morph));
        need(next.physicsSteps==0,
                "1/20 tick must contain three, not four, 1/60 advances");
    }

    /** A fractional intermediate render with zero integration dt still
     * has to correct against the most recently published genuine thigh
     * OBB: otherwise a fast advancing leg pierces the garment visibly. */
    private static void renderOnlyFramesStillCorrectFastThighContact()
    {
        Fixture f=new Fixture();
        SkirtXPBDSolver.State cloth=
                SkirtXPBDSolver.create(f.raw,f.shared,f.rest);
        int n=-1;
        for(int i=0;i<60;i++)
            if(f.shared.nodes.get(i).row>0)
            {n=i;break;}
        need(n>=0,"missing free skirt node for contact regression");
        float[] pos=f.rest.target[n];
        // Cover the target node with both actually fitted leg boxes.
        // Do not invent collision boxes inside the production pipeline.
        float[] l={pos[0],pos[1],pos[2],
                0F,0F,0F,1F,.09F,.12F,.09F};
        float[] r={pos[0]+.28F,pos[1],pos[2],
                0F,0F,0F,1F,.09F,.12F,.09F};
        Object morph=new Object();
        SkirtXPBDContacts.publish(morph,10,10.2D,identity(),
                new float[][]{l,r},java.util.Arrays.asList(
                        "emoticons.left_leg","emoticons.right_leg"));
        SkirtXPBDContacts.Frame bodies=SkirtXPBDContacts.forMorph(morph);
        need(bodies!=null,"test must publish both real-style thigh OBBs");
        SkirtXPBDSolver.Result corrected=cloth.advance(0F,f.rest,bodies);
        need(corrected.substeps==0&&corrected.legContacts>0,
                "render-only intermediate frame must resolve leg contact");
        need(distance(corrected.particles[n],pos)>.01F,
                "leg collision must move penetrating fabric on dt=0");
        SkirtXPBDSolver.Result repeated=cloth.advance(0F,f.rest,bodies);
        need(repeated.substeps==0,
                "second render should remain render-only");
    }

    /** CLOTH is a model-level XPBD toggle. Disabling one garment
     * must not disable another model (including independent hair owners)
     * or leave a stale cloth simulation active in its former owner. */
    private static void perAttachmentClothActivationDoesNotAffectAnotherRig()
    {
        Fixture f=new Fixture();
        float[][] tint=new float[40][];
        for(int i=0;i<tint.length;i++)
            tint[i]=new float[]{1F,1F,1F,1F,0F,0F};
        Object clothTrack=new Object();
        Object hairTrack=new Object();
        Object clothMorph=new Object();
        Object otherMorph=new Object();
        SkirtXPBDContacts.registerOwner(clothMorph,clothTrack);
        SkirtXPBDContacts.registerOwner(otherMorph,hairTrack);
        SkirtXPBDContacts.setClothEnabled(clothTrack,false);
        need(!SkirtXPBDContacts.isClothEnabled(clothMorph),
                "cloth toggle must apply to the stable cloth owner");
        need(SkirtXPBDContacts.isClothEnabled(otherMorph),
                "other model must keep its original independent setting");
        SkirtXPBDRuntime.RenderFrame disabled=SkirtXPBDRuntime.sample(
                clothMorph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,null);
        need(disabled==null,"disabled cloth must not run XPBD");
        SkirtXPBDContacts.setClothEnabled(clothTrack,true);
        need(SkirtXPBDContacts.isClothEnabled(clothMorph),
                "re-enabled cloth model must be independently active");
        SkirtXPBDRuntime.RenderFrame active=SkirtXPBDRuntime.sample(
                clothMorph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,null);
        need(active!=null,"re-enabled cloth must create a new solver");
        SkirtXPBDContacts.setClothEnabled(clothTrack,false);
        SkirtXPBDRuntime.RenderFrame again=SkirtXPBDRuntime.sample(
                clothMorph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,null);
        need(again==null,"disable after running must stop XPBD");
    }

    private static void clonedMorphFramesShareOneLiveXPBDState()
    {
        need(SkirtXPBDRuntime.ENABLED,
                "CI must opt into XPBD for runtime owner continuity test");
        Fixture f=new Fixture();
        float[][] tint=new float[40][];
        for(int p=0;p<40;p++)
            tint[p]=new float[]{1F,1F,1F,1F,0F,0F};
        float[] matrix=identity();
        float[] left={-.16F,.46F,0F,0F,0F,0F,1F,.12F,.17F,.12F};
        float[] right={.16F,.46F,0F,0F,0F,0F,1F,.12F,.17F,.12F};
        float[][] box={left,right};
        java.util.List<String> labels=java.util.Arrays.asList(
                "emoticons.left_leg","emoticons.right_leg");
        Object stableTrack=new Object();
        Object firstMorph=new Object();
        Object nextMorph=new Object();
        SkirtXPBDContacts.registerOwner(firstMorph,stableTrack);
        SkirtXPBDContacts.registerOwner(nextMorph,stableTrack);
        SkirtXPBDContacts.publish(firstMorph,50,matrix,box,labels);
        SkirtXPBDRuntime.RenderFrame first=SkirtXPBDRuntime.sample(
                firstMorph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,SkirtXPBDContacts.forMorph(firstMorph));
        need(first!=null,"XPBD runtime should activate under CI flag");
        SkirtXPBDContacts.publish(nextMorph,51,matrix,box,labels);
        SkirtXPBDRuntime.RenderFrame continued=SkirtXPBDRuntime.sample(
                nextMorph,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,SkirtXPBDContacts.forMorph(nextMorph));
        need(continued!=null,"clone continuation should succeed");
        Object separate=new Object();
        SkirtXPBDContacts.publish(separate,51,matrix,box,labels);
        SkirtXPBDRuntime.RenderFrame fresh=SkirtXPBDRuntime.sample(
                separate,f.raw,f.shared,f.binding,f.topology,
                f.rest,tint,SkirtXPBDContacts.forMorph(separate));
        need(fresh!=null,"independent morph must still simulate");
        float difference=0F;
        for(int n=0;n<60;n++)
            if(f.shared.nodes.get(n).row>0)
                difference=Math.max(difference,distance(
                        continued.solved.particles[n],
                        fresh.solved.particles[n]));
        need(difference>1E-7F,
                "cloning morph every frame reset XPBD rather than continuing");
    }

    private static SkirtXPBDPoseSkin.Frame pose(
            SkirtClothSurfaceMesh.Snapshot raw,
            SkirtClothRestBinding.Binding binding,float[] current)
    {
        List<SkirtXPBDPoseSkin.Transform> poses=
                new ArrayList<SkirtXPBDPoseSkin.Transform>();
        for(SkirtClothSurfaceMesh.Patch p:raw.patches)
            poses.add(new SkirtXPBDPoseSkin.Transform(
                    p.bone,identity(),current));
        return SkirtXPBDPoseSkin.follow(binding,poses);
    }
    private static float[] identity()
    {return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
    private static float distance(float[] a,float[] b)
    {
        float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    private static void equal(float[] a,float[] b,float eps,String label)
    {
        need(a.length==3&&b.length==3,"bad XYZ "+label);
        for(int k=0;k<3;k++)
            need(Math.abs(a[k]-b[k])<=eps,label+" "+k+": "+a[k]+"/"+b[k]);
    }
    private static void equal2(float[] a,float[] b,float eps,String label)
    {
        need(a.length==2&&b.length==2,"bad UV "+label);
        for(int k=0;k<2;k++)
            need(Math.abs(a[k]-b[k])<=eps,label+" "+k);
    }
    private static void need(boolean yes,String label)
    {if(!yes)throw new AssertionError(label);}
}
