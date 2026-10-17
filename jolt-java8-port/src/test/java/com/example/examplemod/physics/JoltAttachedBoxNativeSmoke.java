package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Live Java8 JNI regression: added rotated body part pushes dynamic hair. */
public final class JoltAttachedBoxNativeSmoke
{
    private JoltAttachedBoxNativeSmoke() {}

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt factory");
        Jolt.registerTypes();
        float[][] hairPivots={
                {0.22F,1.85F,0F},{0.22F,1.65F,0F},
                {0.22F,1.45F,0F},{0.22F,1.25F,0F}
        };
        JoltHairChain free=new JoltHairChain(hairPivots,0F,.3F,.5F);
        JoltHairChain impacted=new JoltHairChain(hairPivots,0F,.3F,.5F);
        try
        {
            // Chest modeled by two separate rotated cubes.
            float s=(float)Math.sin(Math.PI/8D);
            float c=(float)Math.cos(Math.PI/8D);
            float[][] boxes={
                {0F,1.48F,0F,s,0F,0F,c,.20F,.22F,.18F},
                {0F,1.18F,0F,s,0F,0F,c,.19F,.16F,.16F}
            };
            impacted.setAdditionalBoxes(boxes);
            for(int frame=0;frame<140;frame++)
            {
                free.step();
                impacted.step();
            }
            double originalX=free.position(1).xx();
            double collidedX=impacted.position(1).xx();
            if(!Double.isFinite(originalX) || !Double.isFinite(collidedX))
                throw new AssertionError("Non-finite simulated hair contact");
            if(!(collidedX>originalX+0.001D))
                throw new AssertionError("Attached boxes produced no hair contact: "
                        +originalX+" -> "+collidedX);
            for(float[] box:boxes)box[0]+=0.025F;
            impacted.setAdditionalBoxes(boxes);
            impacted.step();
            try
            {
                float[][] changed={
                    boxes[0].clone()
                };
                impacted.setAdditionalBoxes(changed);
                throw new AssertionError("Native rig accepted changed collision topology");
            }
            catch(IllegalArgumentException expected) { }
            System.out.println("Java8 native attached chest boxes contact: PASS; "
                    +originalX+" -> "+collidedX);
        }
        finally
        {
            free.close();
            impacted.close();
        }
    }
}
