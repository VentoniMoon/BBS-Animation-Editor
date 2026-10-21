package mchorse.chameleon.lib.data.model;
import java.util.ArrayList;
import java.util.List;
public class ModelBone
{
    public final List<ModelBone> children=new ArrayList<ModelBone>();
    public final List<ModelCube> cubes=new ArrayList<ModelCube>();
    public final ModelTransform initial=new ModelTransform();
    public final ModelTransform current=new ModelTransform();
}
