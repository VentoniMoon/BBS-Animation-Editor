package com.example.examplemod;

/** Isolated stand-ins for Record actor interpolation tests (no Forge). */
final class BlockbusterRecordFrame
{
    double x,y,z;
    float yaw,pitch,yawHead,bodyYaw,mountYaw,mountPitch,motionX,motionY,motionZ;
    boolean hasBodyYaw;
    int discreteAction;
    Object toNBT()
    {
        BlockbusterRecordFrame copy=new BlockbusterRecordFrame();
        copy.x=x;copy.y=y;copy.z=z;
        copy.yaw=yaw;copy.pitch=pitch;copy.yawHead=yawHead;
        copy.bodyYaw=bodyYaw;copy.mountYaw=mountYaw;copy.mountPitch=mountPitch;
        copy.motionX=motionX;copy.motionY=motionY;copy.motionZ=motionZ;
        copy.hasBodyYaw=hasBodyYaw;copy.discreteAction=discreteAction;
        return copy;
    }
    static BlockbusterRecordFrame fromNBT(Object tag)
    {return (BlockbusterRecordFrame)tag;}
    double getX(){return x;} double getY(){return y;} double getZ(){return z;}
    void setX(double x){this.x=x;} void setY(double y){this.y=y;}
    void setZ(double z){this.z=z;}
    float getYaw(){return yaw;} void setYaw(float v){yaw=v;}
    float getPitch(){return pitch;} void setPitch(float v){pitch=v;}
    float getYawHead(){return yawHead;} void setYawHead(float v){yawHead=v;}
    boolean hasBodyYaw(){return hasBodyYaw;}
    float getBodyYaw(){return bodyYaw;}
    void setBodyYaw(float v){bodyYaw=v;hasBodyYaw=true;}
    float getMountYaw(){return mountYaw;} void setMountYaw(float v){mountYaw=v;}
    float getMountPitch(){return mountPitch;}
    void setMountPitch(float v){mountPitch=v;}
    float getMotionX(){return motionX;} void setMotionX(float v){motionX=v;}
    float getMotionY(){return motionY;} void setMotionY(float v){motionY=v;}
    float getMotionZ(){return motionZ;} void setMotionZ(float v){motionZ=v;}
}
final class BlockbusterRecord
{
    BlockbusterRecordFrame[] frames=new BlockbusterRecordFrame[4];
    BlockbusterRecordFrame getFrame(int n)
    {return n>=0&&n<frames.length?frames[n]:null;}
}
