package com.example.examplemod;

/** Java 8 geometric regression test for Jolt/Emoticons hinge handedness. */
public final class RagdollAnatomySmokeTest
{
    private static void check(boolean ok,String reason)
    {
        if(!ok)throw new AssertionError(reason);
    }

    private static float[] yaw(float x,float y,float z,double a)
    {
        return new float[]{
            (float)(Math.cos(a)*x+Math.sin(a)*z),y,
            (float)(-Math.sin(a)*x+Math.cos(a)*z)
        };
    }

    private static void verify(String role,float[] candidate,
            float[] child,float[] forward,boolean knee)
    {
        float sign=RagdollAnatomy.hingeAxisMultiplier(
                role,candidate,child,forward);
        // Empirically verified with Jolt JNI 6.0.0: a positive hinge
        // rotates the child segment in the -cross(axis,child) direction.
        float ax=sign*candidate[0],ay=sign*candidate[1];
        float az=sign*candidate[2];
        float flexX=-(ay*child[2]-az*child[1]);
        float flexY=-(az*child[0]-ax*child[2]);
        float flexZ=-(ax*child[1]-ay*child[0]);
        float wanted=knee?-1F:1F;
        float dot=wanted*(flexX*forward[0]+flexY*forward[1]+
                flexZ*forward[2]);
        check(dot>.7F,"Hyperextending "+role+" / dot="+dot);
    }

    public static void main(String[] args)
    {
        check(RagdollAnatomy.forwardLocalZ()==-1F,
                "Emoticons front is not model-local -Z");

        String[] knees={"left_shin","right_shin"};
        String[] elbows={"left_forearm","right_forearm"};
        for(int degrees=0;degrees<360;degrees+=15)
        {
            double a=Math.toRadians(degrees);
            float[] forward=yaw(0F,0F,-1F,a);
            float[] child={0F,-1F,0F};
            float[] original=yaw(1F,0F,0F,a);
            float[] mirrored=yaw(-1F,0F,0F,a);
            for(String role:knees)
            {
                verify(role,original,child,forward,true);
                verify(role,mirrored,child,forward,true);
            }
            for(String role:elbows)
            {
                verify(role,original,child,forward,false);
                verify(role,mirrored,child,forward,false);
            }
        }
        boolean rejected=false;
        try{RagdollAnatomy.hingeAxisMultiplier(
                "head",new float[]{1,0,0},new float[]{0,-1,0},
                new float[]{0,0,-1});}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Non-hinge role was accepted");
        System.out.println("Emoticons forward and Jolt anatomical hinges OK");
    }
}
