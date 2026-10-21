package mchorse.chameleon.lib.data.model;
import javax.vecmath.Vector3f;
public class ModelVertex
{
    public final Vector3f position=new Vector3f();
    public ModelVertex(float x,float y,float z) {position.set(x,y,z);}
}
