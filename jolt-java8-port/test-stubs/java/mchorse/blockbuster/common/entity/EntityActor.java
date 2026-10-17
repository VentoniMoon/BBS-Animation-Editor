package mchorse.blockbuster.common.entity;

/** Compile-only entity stand-in for Emoticons Record preview clock tests. */
public class EntityActor
{
    public int ticksExisted;
    public double posX,posY,posZ,prevPosX,prevPosY,prevPosZ;
    public double motionX,motionY,motionZ;
    public float rotationYaw,prevRotationYaw,rotationPitch,prevRotationPitch;
    public float fallDistance,moveForward;
    public boolean onGround;
    public boolean sneaking,sprinting;
    public void setSneaking(boolean flag) { sneaking=flag; }
    public void setSprinting(boolean flag) { sprinting=flag; }
}
