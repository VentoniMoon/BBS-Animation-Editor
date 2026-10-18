package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/** Geometry + settings persistence; no live Minecraft dependencies. */
public final class JoltColliderMarkupSmoke
{
    private JoltColliderMarkupSmoke() {}
    static void near(float v,float desired)
    {
        if(Math.abs(v-desired)>0.0003F)
            throw new AssertionError(v+" != "+desired);
    }
    static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        BodyPartCollisionSettings legacy=BodyPartCollisionSettings.fromNBT(
                new NBTTagCompound());
        check(legacy.isEnabled() &&
                legacy.getShape()==BodyPartCollisionSettings.Shape.BOX &&
                legacy.getScale()==1F,"Old scene should retain original Box physics");
        BodyPartCollisionSettings authored=new BodyPartCollisionSettings();
        authored.setEnabled(false);
        authored.setShape(BodyPartCollisionSettings.Shape.CAPSULE);
        authored.setScale(1.25F);
        BodyPartCollisionSettings restored=BodyPartCollisionSettings.fromNBT(
                authored.toNBT());
        check(!restored.isEnabled() &&
                restored.getShape()==BodyPartCollisionSettings.Shape.CAPSULE &&
                restored.getScale()==1.25F,"NBT collider markup roundtrip");

        float[] cube={3F,2F,1F,0F,0F,0F,1F,.1F,.45F,.18F};
        float[] box=JoltColliderPrimitives.encode(cube,JoltColliderPrimitives.BOX,1F);
        near(box[7],.1F);
        near(box[8],.45F);
        near(box[10],0F);
        float[] sphere=JoltColliderPrimitives.encode(cube,
                JoltColliderPrimitives.SPHERE,1F);
        near(sphere[7],.1F);
        near(sphere[8],.1F);
        near(sphere[9],.1F);
        near(sphere[10],1F);
        float[] capsule=JoltColliderPrimitives.encode(cube,
                JoltColliderPrimitives.CAPSULE,1F);
        near(capsule[7],.1F);
        near(capsule[8],.45F);
        near(capsule[9],.1F);
        near(capsule[10],2F);

        float[] alongX={0,0,0,0,0,0,1,.3F,.04F,.06F};
        float[] capsuleX=JoltColliderPrimitives.encode(
                alongX,JoltColliderPrimitives.CAPSULE,1F);
        near(capsuleX[8],.3F);
        near(capsuleX[7],.04F);
        float[][] endsX=JoltColliderPrimitives.capsulePoles(capsuleX);
        near(endsX[0][0],-.3F);
        near(endsX[1][0],.3F);
        near(endsX[0][1],0F);
        near(endsX[1][2],0F);
        // Y-axis of q(Z,-90°) is the original model's X axis.
        float qz=capsuleX[5],qw=capsuleX[6];
        near(-2F*qz*qw,1F);
        float[] alongZ={0,0,0,0,0,0,1,.07F,.04F,.27F};
        float[] capsuleZ=JoltColliderPrimitives.encode(
                alongZ,JoltColliderPrimitives.CAPSULE,1F);
        near(capsuleZ[8],.27F);
        near(capsuleZ[7],.04F);
        float[][] endsZ=JoltColliderPrimitives.capsulePoles(capsuleZ);
        near(endsZ[0][2],-.27F);
        near(endsZ[1][2],.27F);
        near(2F*capsuleZ[3]*capsuleZ[6],1F);

        check(!JoltColliderPrimitives.sameTopology(
                new float[][]{box},new float[][]{capsule}),
                "Changing primitive must invalidate Jolt topology");
        check(JoltColliderPrimitives.sameTopology(
                new float[][]{box.clone()},new float[][]{box.clone()}),
                "Unchanged boxes should remain kinematic and stable");
        float[] newPosition=box.clone();
        newPosition[0]+=100F;
        check(JoltColliderPrimitives.sameTopology(
                new float[][]{box},new float[][]{newPosition}),
                "Moving collider must not recreate native scene");
        System.out.println("Authored collider NBT Box/Sphere/Capsule pivots and topology: PASS");
    }
}
