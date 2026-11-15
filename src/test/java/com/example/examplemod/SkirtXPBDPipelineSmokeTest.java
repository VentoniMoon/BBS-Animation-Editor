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
        pausedPoseChangePreservesClothRelativeMotion();
        actualBodyObbsProjectWithoutInventedContacts();
        outwardThighContactsPreserveHemSide();
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
