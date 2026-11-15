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
        originalUvsAndSeamBridgesAreConnected();
        xpbdHasPinnedWaistGravityAndBounds();
        actualBodyObbsProjectWithoutInventedContacts();
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
