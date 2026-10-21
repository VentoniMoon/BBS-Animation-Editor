package mchorse.chameleon.lib.data.model;
import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Vector3f;
public class ModelCube
{
    public final Vector3f origin=new Vector3f();
    public final Vector3f size=new Vector3f();
    public final Vector3f rotation=new Vector3f();
    public float inflate;
    public final List<ModelQuad> quads=new ArrayList<ModelQuad>();
}
