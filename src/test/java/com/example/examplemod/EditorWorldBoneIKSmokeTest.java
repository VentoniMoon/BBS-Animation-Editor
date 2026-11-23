package com.example.examplemod;

public final class EditorWorldBoneIKSmokeTest
{
    private EditorWorldBoneIKSmokeTest(){}

    public static void main(String[] args)
    {
        reachableFootStaysInWorldWithoutScaling();
        unreachableActorMovePreservesAllLimbLengths();
        registryCapturesWorldPositionAndIsolation();
        rejectBadGeometry();
        System.out.println("EditorWorldBoneIKSmokeTest OK");
    }

    private static void reachableFootStaysInWorldWithoutScaling()
    {
        float[][] original={{0F,0F,0F},{1F,0F,0F},
                {2F,0F,0F}};
        float[] pinned={1F,1F,0F};
        EditorWorldBoneIK.Result first=EditorWorldBoneIK.solve(
                original,pinned,64,.0001F);
        need(first.reachable&&first.residual<.00011F,
                "reachable world foot did not converge");
        near(first.joints[0],original[0],1E-5F,
                "root pivot moved");
        near(first.joints[2],pinned,.00011F,
                "world anchor drifted");
        rigid(original,first.joints);

        // Moving an actor ROOT doesn't move its fixed foot provided
        // the pin stays within the original chain's reach.
        float[][] moved={{.3F,0F,0F},{1.3F,0F,0F},
                {2.3F,0F,0F}};
        EditorWorldBoneIK.Result second=EditorWorldBoneIK.solve(
                moved,pinned,64,.0001F);
        near(second.joints[0],moved[0],1E-5F,
                "new actor root moved");
        near(second.joints[2],pinned,.00011F,
                "fixed foot followed actor translation");
        rigid(moved,second.joints);
    }

    private static void unreachableActorMovePreservesAllLimbLengths()
    {
        float[][] chain={{0F,0F,0F},{0F,-1F,0F},
                {0F,-2F,0F}};
        EditorWorldBoneIK.Result r=EditorWorldBoneIK.solve(
                chain,new float[]{5F,0F,0F},32,.0001F);
        need(!r.reachable,"unreachable target claimed reached");
        rigid(chain,r.joints);
        need(r.residual>=2.999F,
                "unreachable foot crossed original full chain length");
        near(r.joints[2],new float[]{2F,0F,0F},1E-5F,
                "unreachable chain did not align at max reach");
    }

    private static void registryCapturesWorldPositionAndIsolation()
    {
        EditorWorldBonePinRegistry pins=new EditorWorldBonePinRegistry();
        float[] world={4F,5F,6F};
        need(pins.pin("actor-one","right_foot",world),
                "world pin rejected");
        world[0]=99F;
        EditorWorldBonePinRegistry.Pin p=pins.find(
                "actor-one","right_foot");
        near(p.world,new float[]{4F,5F,6F},0F,
                "pin stored the caller's mutable vector");
        p.world[1]=77F;
        near(pins.find("actor-one","right_foot").world,
                new float[]{4F,5F,6F},0F,
                "external pin snapshot mutated registry");
        need(pins.find("actor-two","right_foot")==null,
                "world pin leaked across actor identities");
        need(pins.unpin("actor-one","right_foot"),
                "unpin did not clear target");
        need(pins.find("actor-one","right_foot")==null,
                "world pin survived explicit removal");
    }

    private static void rejectBadGeometry()
    {
        boolean failed=false;
        try{EditorWorldBoneIK.solve(new float[][]{
                    {0F,0F,0F},{0F,0F,0F}},
                    new float[]{0F,1F,0F},12,.001F);}
        catch(IllegalArgumentException expected){failed=true;}
        need(failed,"zero-length bone must be rejected");
    }

    private static void rigid(float[][] original,float[][] result)
    {
        for(int i=1;i<original.length;i++)
        {
            float a=length(original[i],original[i-1]);
            float b=length(result[i],result[i-1]);
            need(Math.abs(a-b)<.0002F,
                    "bone "+i+" length changed by "+(b-a));
        }
    }

    private static float length(float[] a,float[] b)
    {
        float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }

    private static void near(float[] a,float[] b,float eps,String msg)
    {
        for(int i=0;i<3;i++)
            need(Math.abs(a[i]-b[i])<=eps,msg+" axis="+i);
    }

    private static void need(boolean ok,String reason)
    {
        if(!ok)throw new AssertionError(reason);
    }
}
