package mchorse.blockbuster_pack.morphs;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.blockbuster.common.entity.EntityActor;
public class BetterLightsMorph extends AbstractMorph {
    public static class Dummy{
        private int age=2;
        public int lifetime;
        public int getAge(){return age;}
        public void setLifetime(int limit){lifetime=limit;}
    }
    private Dummy dummy=new Dummy();
    private Object lightCaster=new Object();
    public Object caster(){return lightCaster;}
    public int getKeepAliveUntil(){return dummy.lifetime;}
    @Override public void update(EntityActor actor){super.update(actor);}
}
