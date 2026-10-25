package mchorse.blockbuster.common.entity;
import mchorse.metamorph.api.morphs.AbstractMorph;
public class EntityActor{
    public static final class World{public boolean isRemote=true;}
    public World world=new World();
    public AbstractMorph morph;
    public AbstractMorph getMorph(){return morph;}
}
