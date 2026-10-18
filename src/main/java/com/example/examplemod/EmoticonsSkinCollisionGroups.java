package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Read-only skin-weight grouping of animated Emoticons triangles.
 * Each triangle may belong to several collision groups (blended skin).
 * No assumptions about mesh-local/world-space transformations are made.
 */
public final class EmoticonsSkinCollisionGroups
{
    public enum Group { HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, OTHER }

    public static final class Result
    {
        public final Map<Group,Integer> triangleCounts;
        public final int totalTriangles;
        public final int unweightedTriangles;
        Result(Map<Group,Integer> counts,int total,int unweighted)
        {
            this.triangleCounts=Collections.unmodifiableMap(counts);
            this.totalTriangles=total;
            this.unweightedTriangles=unweighted;
        }
        @Override public String toString()
        {
            return "total="+totalTriangles+" unweighted="+unweightedTriangles
                    +" groups="+triangleCounts;
        }
    }

    private EmoticonsSkinCollisionGroups() {}

    public static Result analyze(Object morph, String meshName)
    {
        Map<Group,Integer> counts=new LinkedHashMap<Group,Integer>();
        for(Group g:Group.values())counts.put(g,Integer.valueOf(0));
        int total=0,unweighted=0;
        if(!(morph instanceof AnimatedMorph))
            return new Result(counts,0,0);

        for(AnimationMesh mesh:EmoticonsModelAccess.getMeshes((AnimatedMorph)morph))
        {
            if(mesh==null||mesh.data==null||meshName==null
                    ||!meshName.equals(mesh.name))continue;
            int[] indices=mesh.data.indexData;
            int[] boneIndices=mesh.data.boneIndexData;
            float[] weights=mesh.data.weightData;
            BOBJArmature skeleton=mesh.getArmature();
            if(indices==null||boneIndices==null||weights==null||skeleton==null
                    ||skeleton.orderedBones==null)continue;

            Map<Integer,Group> boneGroups=new LinkedHashMap<Integer,Group>();
            for(BOBJBone bone:skeleton.orderedBones)
                if(bone!=null)boneGroups.put(Integer.valueOf(bone.index),classify(bone.name));

            for(int t=0;t+2<indices.length;t+=3)
            {
                java.util.EnumMap<Group,Float> mass=
                        new java.util.EnumMap<Group,Float>(Group.class);
                for(int vertex=0;vertex<3;vertex++)
                {
                    int vi=indices[t+vertex];
                    if(vi<0 || vi>Integer.MAX_VALUE/4-4)continue;
                    int offset=vi*4;
                    if(offset+3>=weights.length||offset+3>=boneIndices.length)continue;
                    for(int slot=0;slot<4;slot++)
                    {
                        float w=weights[offset+slot];
                        if(!(w>0F)||!Float.isFinite(w))continue;
                        Group group=boneGroups.get(Integer.valueOf(boneIndices[offset+slot]));
                        if(group==null)group=Group.OTHER;
                        Float old=mass.get(group);
                        mass.put(group,(old==null?0F:old.floatValue())+w);
                    }
                }
                total++;
                if(mass.isEmpty())
                {
                    unweighted++;
                    increment(counts,Group.OTHER);
                    continue;
                }
                // Include every group with at least 15% of the summed
                // triangle vertex weights. This deliberately allows overlaps.
                float sum=0F;
                for(Float amount:mass.values())sum+=amount.floatValue();
                for(Map.Entry<Group,Float> entry:mass.entrySet())
                    if(entry.getValue().floatValue()>=sum*0.15F)
                        increment(counts,entry.getKey());
            }
        }
        return new Result(counts,total,unweighted);
    }

    /**
     * Produces a filtered subset of an already-skinned geometry snapshot.
     * Only indices are filtered; vertex positions stay unchanged, so the
     * physics solver can read the real deformed triangles. This method does
     * NOT convert them to world coordinates.
     *
     * Fails closed on unmatched source/snapshot topology.
     */
    public static EmoticonsCollisionGeometryProvider.MeshSnapshot select(
            Object morph,
            EmoticonsCollisionGeometryProvider.MeshSnapshot snapshot,
            java.util.Set<Group> allowed)
    {
        if (!(morph instanceof AnimatedMorph) || snapshot == null ||
                allowed == null || allowed.isEmpty()) return null;
        for (AnimationMesh mesh :
                EmoticonsModelAccess.getMeshes((AnimatedMorph)morph))
        {
            if (mesh == null || mesh.data == null ||
                    !snapshot.name.equals(mesh.name)) continue;
            int[] indices = mesh.data.indexData;
            int[] bones = mesh.data.boneIndexData;
            float[] weights = mesh.data.weightData;
            BOBJArmature armature = mesh.getArmature();
            if (indices == null || bones == null || weights == null ||
                    armature == null || armature.orderedBones == null ||
                    snapshot.triangles.length != indices.length ||
                    snapshot.vertices.length % 3 != 0 ||
                    weights.length != bones.length ||
                    weights.length != (long)(snapshot.vertices.length / 3) * 4)
                return null;
            for (int i = 0; i < indices.length; i++)
                if (indices[i] != snapshot.triangles[i]) return null;
            Map<Integer,Group> byBone = new LinkedHashMap<Integer,Group>();
            for (BOBJBone bone : armature.orderedBones)
                if (bone != null)
                    byBone.put(Integer.valueOf(bone.index), classify(bone.name));

            List<Integer> result = new ArrayList<Integer>();
            for (int t = 0; t + 2 < indices.length; t += 3)
            {
                java.util.EnumMap<Group,Float> contributions =
                        new java.util.EnumMap<Group,Float>(Group.class);
                float totalWeight = 0F;
                for (int j = 0; j < 3; j++)
                {
                    int vertex = indices[t+j];
                    if (vertex < 0 || vertex >= snapshot.vertices.length/3)
                        return null;
                    int offset = vertex * 4;
                    for (int slot = 0; slot < 4; slot++)
                    {
                        float w = weights[offset+slot];
                        if (!Float.isFinite(w) || w <= 0F) continue;
                        Group g = byBone.get(Integer.valueOf(bones[offset+slot]));
                        if (g == null) g = Group.OTHER;
                        Float old = contributions.get(g);
                        contributions.put(g, Float.valueOf(
                                (old == null ? 0F : old.floatValue()) + w));
                        totalWeight += w;
                    }
                }
                boolean include = false;
                if (totalWeight <= 0F) include = allowed.contains(Group.OTHER);
                else for (Map.Entry<Group,Float> c : contributions.entrySet())
                    if (allowed.contains(c.getKey()) &&
                            c.getValue().floatValue() >= totalWeight * 0.15F)
                    {
                        include = true;
                        break;
                    }
                if (include)
                {
                    result.add(Integer.valueOf(indices[t]));
                    result.add(Integer.valueOf(indices[t+1]));
                    result.add(Integer.valueOf(indices[t+2]));
                }
            }
            int[] triangleIndices = new int[result.size()];
            for (int i = 0; i < triangleIndices.length; i++)
                triangleIndices[i] = result.get(i).intValue();
            return new EmoticonsCollisionGeometryProvider.MeshSnapshot(
                    snapshot.name + "_collision_groups",
                    snapshot.vertices, triangleIndices);
        }
        return null;
    }

    /**
     * Select skinned triangles by their dominant actual armature bone.
     * Each triangle has exactly one owner to avoid overlapping colliders.
     */
    public static EmoticonsCollisionGeometryProvider.MeshSnapshot selectBone(
            Object morph, EmoticonsCollisionGeometryProvider.MeshSnapshot snapshot,
            String boneName)
    {
        if (!(morph instanceof AnimatedMorph) || snapshot == null ||
                boneName == null) return null;
        for (AnimationMesh mesh : EmoticonsModelAccess.getMeshes((AnimatedMorph)morph))
        {
            if (mesh == null || mesh.data == null || !snapshot.name.equals(mesh.name))
                continue;
            int[] indices = mesh.data.indexData;
            int[] boneIds = mesh.data.boneIndexData;
            float[] weights = mesh.data.weightData;
            BOBJArmature armature = mesh.getArmature();
            if (indices == null || boneIds == null || weights == null ||
                    armature == null || armature.orderedBones == null ||
                    indices.length != snapshot.triangles.length ||
                    weights.length != boneIds.length ||
                    weights.length != (long)(snapshot.vertices.length / 3) * 4)
                return null;
            int target = -1;
            for (BOBJBone bone : armature.orderedBones)
                if (bone != null && boneName.equals(bone.name))
                    target = bone.index;
            if (target < 0) return null;
            List<Integer> selected = new ArrayList<Integer>();
            for (int t = 0; t + 2 < indices.length; t += 3)
            {
                Map<Integer,Float> mass = new LinkedHashMap<Integer,Float>();
                for (int j = 0; j < 3; j++)
                {
                    int vi = indices[t+j];
                    if (vi < 0 || vi >= snapshot.vertices.length/3) return null;
                    int off = vi*4;
                    for (int slot = 0; slot < 4; slot++)
                    {
                        float w = weights[off+slot];
                        if (w <= 0F || !Float.isFinite(w)) continue;
                        Integer id = Integer.valueOf(boneIds[off+slot]);
                        Float old = mass.get(id);
                        mass.put(id, Float.valueOf(w + (old==null?0F:old.floatValue())));
                    }
                }
                int dominant = -1;
                float maximum = 0F;
                for (Map.Entry<Integer,Float> entry : mass.entrySet())
                    if (entry.getValue().floatValue() > maximum)
                    {
                        dominant = entry.getKey().intValue();
                        maximum = entry.getValue().floatValue();
                    }
                if (dominant == target)
                    for (int j=0;j<3;j++) selected.add(Integer.valueOf(indices[t+j]));
            }
            int[] tris = new int[selected.size()];
            for (int i=0;i<tris.length;i++) tris[i]=selected.get(i).intValue();
            return new EmoticonsCollisionGeometryProvider.MeshSnapshot(
                    snapshot.name+"_bone_"+boneName,snapshot.vertices,tris);
        }
        return null;
    }

    private static void increment(Map<Group,Integer> counts,Group group)
    {counts.put(group,Integer.valueOf(counts.get(group).intValue()+1));}

    public static Group classify(String boneName)
    {
        if(boneName==null)return Group.OTHER;
        String n=boneName.toLowerCase(Locale.ROOT).replace("-","_").replace(" ","_");
        if(n.contains("head")||n.contains("neck")||n.contains("jaw"))return Group.HEAD;
        if(n.contains("left")&&(n.contains("arm")||n.contains("hand")||
                n.contains("shoulder")||n.contains("elbow")))return Group.LEFT_ARM;
        if(n.contains("right")&&(n.contains("arm")||n.contains("hand")||
                n.contains("shoulder")||n.contains("elbow")))return Group.RIGHT_ARM;
        if(n.contains("left")&&(n.contains("leg")||n.contains("foot")||
                n.contains("knee")))return Group.LEFT_LEG;
        if(n.contains("right")&&(n.contains("leg")||n.contains("foot")||
                n.contains("knee")))return Group.RIGHT_LEG;
        if(n.contains("body")||n.contains("chest")||n.contains("torso")||
                n.contains("spine")||n.contains("pelvis")||n.contains("hip"))
            return Group.TORSO;
        return Group.OTHER;
    }
}
