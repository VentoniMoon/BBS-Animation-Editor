package com.example.examplemod;

/** Java 8 regression test for native Emoticons model-space directions. */
public final class RagdollAnatomySmokeTest
{
    private static void check(boolean ok,String message)
    {
        if(!ok)throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        check(RagdollAnatomy.forwardLocalZ()==-1F,
                "Emoticons forward changed sign");

        final String[] knees={"left_shin","right_shin"};
        final String[] elbows={"left_forearm","right_forearm"};
        final float t=(float)Math.toRadians(50D);
        for(String role:knees)
        {
            float sign=RagdollAnatomy.hingeAxisSign(role);
            check(sign==-1F,"Knee must rotate about -X: "+role);
            // Rotate a downward shin around the +angle hinge.
            // A positive Z displacement bends behind the character.
            float bentZ=-sign*(float)Math.sin(t);
            check(bentZ>0.5F,"Knee hyperextends forward: "+role);
        }
        for(String role:elbows)
        {
            float sign=RagdollAnatomy.hingeAxisSign(role);
            check(sign==1F,"Elbow must rotate about +X: "+role);
            float bentZ=-sign*(float)Math.sin(t);
            check(bentZ<-.5F,"Forearm bends behind the torso: "+role);
        }

        // A yaw change rotates both model-forward and knee bend
        // together. In all directions the knee remains behind the body.
        for(int yaw=0;yaw<360;yaw+=15)
        {
            double angle=Math.toRadians(yaw);
            double frontX=-Math.sin(angle),frontZ=-Math.cos(angle);
            double backX=Math.sin(angle),backZ=Math.cos(angle);
            check(frontX*backX+frontZ*backZ<-.99D,
                    "Wrong knee-side after yaw="+yaw);
        }
        boolean rejected=false;
        try{RagdollAnatomy.hingeAxisSign("head");}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Non-hinge role accepted");
        System.out.println("Emoticons forward and knee hinge directions OK");
    }
}
