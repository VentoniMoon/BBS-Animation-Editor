package com.example.examplemod.physics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.example.examplemod.BodyPartJoltPreviewAdapter;
import com.example.examplemod.BodyPartJoltMultiTorsoFit;
import com.example.examplemod.BodyPartJoltTorsoFit;

/** The real reflection bridge and live wireframe toggle, not just JNI direct. */
public final class JoltMultiTorsoBridgeSmoke
{
    private static float[] matrix(float x,float y,float z) {
        return new float[] {-1F,0F,0F,0F,
                0F,1F,0F,0F,0F,0F,-1F,0F,x,y,z,1F};
    }
    public static void main(String[] args)
    {
        if(!BodyPartJoltPreviewAdapter.compoundTorsoRequested())
            throw new AssertionError("Missing compound preview JVM property");

        String[] names={"back_strand_1","back_strand_2","back_strand_3"};
        float[][] bone={matrix(3.21F,5.0F,-2F),
                matrix(3.21F,4.80F,-2F),matrix(3.21F,4.60F,-2F)};
        float[] tip={3.21F,4.40F,-2F};
        float[] a={3F,4.4F,-2F},b={3F,4.8F,-2F};

        List<Float> vertices=new ArrayList<Float>();
        List<Integer> indices=new ArrayList<Integer>();
        int n=24,rings=12;
        for(int j=0;j<rings;j++)
            for(int i=0;i<n;i++)
            {
                float t=.04F+j*.93F/(rings-1);
                float angle=(float)(2*Math.PI*i/n);
                float radius=t<.34F?.13F:t<.68F?.19F:.12F;
                vertices.add(a[0]+radius*(float)Math.cos(angle));
                vertices.add(a[1]+t*(b[1]-a[1]));
                vertices.add(a[2]+radius*(float)Math.sin(angle));
            }
        for(int j=0;j<rings-1;j++)
            for(int i=0;i<n;i++)
            {
                int v=j*n+i,next=j*n+(i+1)%n,w=(j+1)*n+i;
                indices.add(v);indices.add(w);indices.add(next);
            }
        float[] points=new float[vertices.size()];
        int[] triangles=new int[indices.size()];
        for(int i=0;i<points.length;i++)points[i]=vertices.get(i);
        for(int i=0;i<triangles.length;i++)triangles[i]=indices.get(i);
        BodyPartJoltTorsoFit.Fit base=
                BodyPartJoltTorsoFit.fit(a,b,points,triangles);
        if(base==null)throw new AssertionError("Missing base fit");
        BodyPartJoltMultiTorsoFit.Segments multi=
                BodyPartJoltMultiTorsoFit.fit(base,points,triangles);
        if(multi==null)throw new AssertionError("Missing 3-zone fit");

        BodyPartJoltPreviewAdapter bridge=new BodyPartJoltPreviewAdapter();
        try
        {
            Map<String,float[]> first=bridge.sample(new File("."),
                    "compound/back",names,0,bone,tip,matrix(3F,5F,-2F),
                    0F,.30F,1F,a,b,base.radius,multi);
            if(first==null||first.size()!=3||!bridge.wasRecreatedThisSample())
                throw new AssertionError("Compound reflection create unavailable");
            if(bridge.contactModeFor("compound/back",0)!=3)
                throw new AssertionError("Overlay must identify THREE installed Jolt colliders");
            if(bridge.contactModeFor("compound/back",1)!=0)
                throw new AssertionError("Overlay must reject stale frame contact proxies");
            if(bridge.contactModeFor("unknown-scene",0)!=0)
                throw new AssertionError("Overlay must not invent contact geometry");
            if(!BodyPartJoltPreviewAdapter.drawContactsRequested())
                throw new AssertionError("Missing live debug drawing opt-in");
            float[][] real=bridge.hairSegmentsFor("compound/back",0);
            if(real==null||real.length!=3||real[0].length!=7)
                throw new AssertionError("No actual native hair body endpoints");
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
            if(BodyPartJoltPreviewAdapter.drawContactsRequested())
                throw new AssertionError("UI toggle failed to hide colliders");
            BodyPartJoltPreviewAdapter.toggleDrawContacts();
            if(!BodyPartJoltPreviewAdapter.drawContactsRequested())
                throw new AssertionError("UI toggle failed to restore colliders");

            // Shift torso with animation without mutating radii or shape.
            BodyPartJoltMultiTorsoFit.Segments next=multi;
            Map<String,float[]> moving=bridge.sample(new File("."),
                    "compound/back",names,1,bone,tip,matrix(3F,5F,-2F),
                    0F,.30F,1F,a,b,base.radius,next);
            if(moving==null||moving.size()!=3||bridge.wasRecreatedThisSample())
                throw new AssertionError("Compound torso recreated unnecessarily");
            if(bridge.contactModeFor("compound/back",1)!=3 ||
                    bridge.contactModeFor("compound/back",0)!=0)
                throw new AssertionError("Contact frame/version mismatch");
            System.out.println("Native Java8 compound reflection & UI toggle: PASS");
        }
        finally
        {
            bridge.clear();
        }
    }
}
