package dz.betterlights;

import mchorse.blockbuster.common.entity.EntityActor;

/** Test-only stand-in for the optional BetterLights manager. */
public final class BetterLightsMod
{
    public static final Manager INSTANCE=new Manager();
    public static Manager getLightManager(){return INSTANCE;}

    public static final class Manager
    {
        public int removals;
        public Object last;
        public void removeLightCaster(EntityActor.World world,
                Object caster,boolean flag)
        {
            if(world==null||caster==null||flag)
                throw new AssertionError("invalid removal");
            removals++;
            last=caster;
        }
    }
}
