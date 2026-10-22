package com.example.examplemod;

/** Standalone Java 8 deterministic procedural camera tests. */
public final class CameraMathExpressionSmokeTest
{
    private static void near(double value,double expected,double epsilon,String why)
    {
        if(Math.abs(value-expected)>epsilon)
            throw new AssertionError(why+": "+value+" expected "+expected);
    }
    private static void same(PreviewCameraPose a,PreviewCameraPose b)
    {
        near(a.x,b.x,1E-8,"x");
        near(a.y,b.y,1E-8,"y");
        near(a.z,b.z,1E-8,"z");
        near(a.yaw,b.yaw,1E-6,"yaw");
        near(a.pitch,b.pitch,1E-6,"pitch");
        near(a.roll,b.roll,1E-6,"roll");
        near(a.fov,b.fov,1E-6,"fov");
    }
    public static void main(String[] args)
    {
        PreviewCameraPose base=new PreviewCameraPose(
                4,5,6,10,20,0,70,0.05F,512F);
        String formula="x=2*t+1; yaw=sin(t*pi/10)*30; "+
                "fov=clamp(35+ticks,45,100); z=max(3,12-seconds*2)";
        PreviewCameraPose frame=CameraMathExpression.apply(formula,base,25,5,50);
        near(frame.x,41,1E-8,"relative time");
        near(frame.y,5,1E-8,"unmodified channel");
        near(frame.yaw,0,1E-4,"sine");
        near(frame.fov,60,1E-6,"fov");
        near(frame.z,9.5,1E-8,"seconds");
        same(CameraMathExpression.apply(formula,base,25,5,50),frame);
        same(CameraMathExpression.apply("foo=100",base,25,5,50),base);
        same(CameraMathExpression.apply("x=1/0",base,25,5,50),base);
        same(CameraMathExpression.apply("x=sin(",base,25,5,50),base);
        near(CameraMathExpression.apply("fov=10000",base,25,5,50).fov,
                170,1E-6,"fov clamping");
        near(CameraMathExpression.apply("x=2^3^2",base,25,5,50).x,
                512,1E-8,"right associative exponent");
        System.out.println("CAMERA deterministic procedural expression PASS");
    }
}
