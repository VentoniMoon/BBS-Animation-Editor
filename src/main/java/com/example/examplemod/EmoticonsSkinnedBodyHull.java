package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A conservative, independent skin-mesh based BODY collision approximation.
 * Input geometry is the actual animated Emoticons CPU-skinned mesh; no hair
 * attachment, body-part model name, or arbitrary actor height is consulted.
 *
 * Split elongated regions into small axis-aligned rectangular volumes,
 * avoiding the large empty gaps of a single round torso capsule. The shapes
 * remain local to the Emoticons render frame; caller applies actor yaw and
 * world translation before passing them to native Jolt.
 *
 * This is an outer surface proxy, not triangle/cloth collision.
 */
public final class EmoticonsSkinnedBodyHull {
    private EmoticonsSkinnedBodyHull() {}

    /** Return 10-component OBBs in the SAME local frame as vertices. */
    public static List<float[]> boxes(float[] vertices,int[] triangles,
            int slices,float padding)
    {
        List<float[]> result=new ArrayList<float[]>();
        if(vertices==null||triangles==null||vertices.length%3!=0||
                slices<1||slices>3||!Float.isFinite(padding)||
                padding<0F||padding>0.08F)return result;
        int n=vertices.length/3;
        if(n<8||triangles.length<24||triangles.length%3!=0)return result;
        boolean[] selected=new boolean[n];
        int count=0;
        for(int index:triangles){
            if(index<0||index>=n)return result;
            if(!selected[index]){selected[index]=true;count++;}
        }
        if(count<8)return result;
        float[][] positions=new float[count][3];
        int at=0;
        for(int i=0;i<n;i++)if(selected[i]){
            int k=i*3;
            for(int axis=0;axis<3;axis++){
                float v=vertices[k+axis];
                if(!Float.isFinite(v))return new ArrayList<float[]>();
                positions[at][axis]=v;
            }
            at++;
        }
        float[][] sorted=new float[3][count];
        for(int axis=0;axis<3;axis++){
            for(int i=0;i<count;i++)sorted[axis][i]=positions[i][axis];
            Arrays.sort(sorted[axis]);
        }
        // Drop a small number of anomalous weighted vertices at the edge.
        int low=Math.max(0,(count-1)/100),high=Math.min(count-1,(count-1)*99/100);
        float[] min={sorted[0][low],sorted[1][low],sorted[2][low]};
        float[] max={sorted[0][high],sorted[1][high],sorted[2][high]};
        int main=0;
        if(max[1]-min[1]>max[main]-min[main])main=1;
        if(max[2]-min[2]>max[main]-min[main])main=2;
        float length=max[main]-min[main];
        if(!(length>0.025F)||length>3.5F)return result;
        int nSlices=Math.min(slices,Math.max(1,(int)Math.floor(length/.14F)));
        for(int slice=0;slice<nSlices;slice++){
            float lowSlice=min[main]+length*slice/nSlices;
            float highSlice=min[main]+length*(slice+1F)/nSlices;
            float[] lo={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
            float[] hi={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
            int included=0;
            for(float[] p:positions){
                if(p[main]<lowSlice-0.0001F || p[main]>highSlice+0.0001F)
                    continue;
                // Reject outlier geometry beyond measured robust bounds.
                boolean valid=true;
                for(int axis=0;axis<3;axis++)
                    if(p[axis]<min[axis]-.04F||p[axis]>max[axis]+.04F)
                        valid=false;
                if(!valid)continue;
                included++;
                for(int axis=0;axis<3;axis++){
                    lo[axis]=Math.min(lo[axis],p[axis]);
                    hi[axis]=Math.max(hi[axis],p[axis]);
                }
            }
            if(included<4)continue;
            // Exact slice boundaries make adjacent sections touch.
            lo[main]=Math.min(lo[main],lowSlice);
            hi[main]=Math.max(hi[main],highSlice);
            float[] box=new float[10];
            for(int axis=0;axis<3;axis++){
                box[axis]=(lo[axis]+hi[axis])*.5F;
                box[axis+7]=Math.max(0.015F,
                        (hi[axis]-lo[axis])*.5F+padding);
                if(!(box[axis+7]<2F))return new ArrayList<float[]>();
            }
            box[6]=1F; // Quaternion identity in local actor space.
            result.add(box);
        }
        return result;
    }
}
