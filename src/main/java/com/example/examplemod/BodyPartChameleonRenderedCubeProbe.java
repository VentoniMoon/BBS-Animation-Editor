package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Read-only access to the ACTUAL decoded Chameleon ModelCube.quads used by
 * ChameleonCubeRenderer (no hard dependency on the optional mod).
 *
 * ModelParser mirrors JSON X when importing, ModelCube.generateQuads() emits
 * final vertices, and ChameleonCubeRenderer applies its own cube pivot and
 * rotation. Measuring a Blockbench origin as if it were a Chameleon vertex
 * would therefore produce misleading world-space diagnostics.
 *
 * This code reads fields after the real render; it never calls the renderer,
 * changes the GL state or writes a bone pose.
 */
public final class BodyPartChameleonRenderedCubeProbe
{
    private BodyPartChameleonRenderedCubeProbe() {}

    public static final class Result
    {
        public final BodyPartJoltCubeGeometry.Measurement measurement;
        /** All actual cube objects on this bone, not just the selected cube. */
        public final int cubeCount;
        /** Number of decoded quad vertices used on the selected cube. */
        public final int vertexCount;

        private Result(BodyPartJoltCubeGeometry.Measurement measurement,
                int cubeCount,int vertexCount)
        {
            this.measurement=measurement;
            this.cubeCount=cubeCount;
            this.vertexCount=vertexCount;
        }
    }

    /** Return null when a model or suitable geometric cube is unavailable. */
    public static Result inspect(Object morph,String boneName,float[] worldBone)
    {
        if(morph==null || boneName==null || worldBone==null)
            return null;
        try
        {
            Object chameleon=morph.getClass().getMethod("getModel").invoke(morph);
            if(chameleon==null)return null;
            Object model=publicField(chameleon,"model");
            if(model==null)return null;
            Object rootBones=publicField(model,"bones");
            if(!(rootBones instanceof List))return null;
            Object bone=findBone((List<?>)rootBones,boneName,
                    new IdentityHashMap<Object,Boolean>());
            if(bone==null)return null;

            Object bonesCubes=publicField(bone,"cubes");
            if(!(bonesCubes instanceof List))return null;
            List<?> cubes=(List<?>)bonesCubes;
            if(cubes.isEmpty())return null;

            Object initial=publicField(bone,"initial");
            float[] bonePivot=readXYZ(publicField(initial,"translate"));
            BodyPartJoltCubeGeometry.Measurement best=null;
            int bestVertices=0;
            float bestExtent=0F;
            for(Object cube:cubes)
            {
                float[] pivot=readXYZ(publicField(cube,"pivot"));
                float[] rotation=readXYZ(publicField(cube,"rotation"));
                Object faceList=publicField(cube,"quads");
                if(!(faceList instanceof List))continue;
                List<float[]> vertexData=new ArrayList<float[]>();
                float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY;
                for(Object face:(List<?>)faceList)
                {
                    Object vertices=publicField(face,"vertices");
                    if(!(vertices instanceof List))continue;
                    for(Object vertex:(List<?>)vertices)
                    {
                        float[] p=readXYZ(publicField(vertex,"position"));
                        vertexData.add(p);
                        lo=Math.min(lo,p[1]);
                        hi=Math.max(hi,p[1]);
                    }
                }
                if(vertexData.isEmpty() || hi-lo<=1E-6F)continue;
                BodyPartJoltCubeGeometry.Measurement candidate =
                        BodyPartJoltCubeGeometry.measureRenderedVertices(
                                worldBone,bonePivot,pivot,rotation,
                                vertexData.toArray(new float[vertexData.size()][]));
                // If there are multiple cubes, report the longest Y extent
                // and its count. Do not claim this single result covers
                // every independently rotated cube of a composite hair bone.
                if(best==null || hi-lo>bestExtent)
                {
                    best=candidate;
                    bestExtent=hi-lo;
                    bestVertices=vertexData.size();
                }
            }
            return best==null?null:new Result(best,cubes.size(),bestVertices);
        }
        catch(ReflectiveOperationException error)
        {
            return null;
        }
        catch(RuntimeException error)
        {
            return null;
        }
    }

    private static Object findBone(List<?> bones,String name,
            IdentityHashMap<Object,Boolean> visited)
            throws ReflectiveOperationException
    {
        for(Object bone:bones)
        {
            if(bone==null || visited.put(bone,Boolean.TRUE)!=null)
                continue;
            if(name.equals(publicField(bone,"id")))
                return bone;
            Object children=publicField(bone,"children");
            if(children instanceof List)
            {
                Object result=findBone((List<?>)children,name,visited);
                if(result!=null)return result;
            }
        }
        return null;
    }

    private static Object publicField(Object target,String name)
            throws ReflectiveOperationException
    {
        if(target==null)throw new NoSuchFieldException(name+" on null");
        Field field=target.getClass().getField(name);
        return field.get(target);
    }

    private static float[] readXYZ(Object value)
            throws ReflectiveOperationException
    {
        return new float[] {
                ((Number)publicField(value,"x")).floatValue(),
                ((Number)publicField(value,"y")).floatValue(),
                ((Number)publicField(value,"z")).floatValue()
        };
    }
}
