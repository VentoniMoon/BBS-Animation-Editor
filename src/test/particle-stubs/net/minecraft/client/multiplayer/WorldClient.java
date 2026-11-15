package net.minecraft.client.multiplayer;
import net.minecraft.util.EnumParticleTypes;
public class WorldClient
{
    public int count;
    public double x,y,z;
    public EnumParticleTypes last;
    public int[] args;
    public void spawnParticle(EnumParticleTypes type,boolean distance,
            double x,double y,double z,double vx,double vy,double vz,
            int... args)
    {
        last=type;this.x=x;this.y=y;this.z=z;
        this.args=args;count++;
    }
}
