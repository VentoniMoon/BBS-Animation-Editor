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


    /**
     * Fit the animated skinned mesh in the COORDINATE FRAME of its driven
     * parent bone. Its half-extents are authored once, then reused while
     * the rigid bone frame follows Emoticons animation. This prevents a
     * rotated arm's axis-aligned bounding box from changing dimensions on
     * every tick and triggering perpetual native Jolt rig restarts.
     */
    public static List<float[]> boneLocalBoxes(float[] vertices,int[] triangles,
            float[] boneMatrix,int slices,float padding)
    {
        if(vertices==null||vertices.length%3!=0 ||
                !rigidBasis(boneMatrix))return new ArrayList<float[]>();
        float[] converted=new float[vertices.length];
        for(int i=0;i<vertices.length;i+=3){
            float dx=vertices[i]-boneMatrix[12];
            float dy=vertices[i+1]-boneMatrix[13];
            float dz=vertices[i+2]-boneMatrix[14];
            for(int axis=0;axis<3;axis++){
                float bx=boneMatrix[axis*4],by=boneMatrix[axis*4+1],
                        bz=boneMatrix[axis*4+2];
                float length2=bx*bx+by*by+bz*bz;
                converted[i+axis]=(dx*bx+dy*by+dz*bz)/length2;
            }
        }
        return boxes(converted,triangles,slices,padding);
    }

    /** Transform an immutable bone-local Box into model/world coordinates.
     * Handles proper 3D bone rotations as well as translation.
     */
    public static float[] transformRigid(float[] matrix,float[] box)
    {
        if(!rigidBasis(matrix)||box==null||box.length!=10)
            throw new IllegalArgumentException("Invalid rigid Emoticons collider");
        float[] out=box.clone();
        for(int axis=0;axis<3;axis++)
            out[axis]=matrix[12+axis]+matrix[axis]*box[0]+
                    matrix[4+axis]*box[1]+matrix[8+axis]*box[2];
        for(int axis=0;axis<3;axis++){
            float x=matrix[axis*4],y=matrix[axis*4+1],
                    z=matrix[axis*4+2];
            float length=(float)Math.sqrt(x*x+y*y+z*z);
            out[axis+7]=box[axis+7]*length;
        }
        float[] q=quaternion(matrix);
        System.arraycopy(q,0,out,3,4);
        return out;
    }

    private static boolean rigidBasis(float[] m)
    {
        if(m==null||m.length!=16)return false;
        for(float v:m)if(!Float.isFinite(v))return false;
        float[] lengths=new float[3];
        for(int axis=0;axis<3;axis++){
            float x=m[axis*4],y=m[axis*4+1],z=m[axis*4+2];
            lengths[axis]=(float)Math.sqrt(x*x+y*y+z*z);
            if(lengths[axis]<.01F||lengths[axis]>4F)return false;
        }
        for(int i=0;i<3;i++)
            for(int j=i+1;j<3;j++){
                float dot=0F;
                for(int k=0;k<3;k++)
                    dot+=m[i*4+k]*m[j*4+k];
                if(Math.abs(dot)/(lengths[i]*lengths[j])>.025F)
                    return false;
            }
        float det=(m[0]*(m[5]*m[10]-m[9]*m[6])-
                m[4]*(m[1]*m[10]-m[9]*m[2])+
                m[8]*(m[1]*m[6]-m[5]*m[2]))/
                (lengths[0]*lengths[1]*lengths[2]);
        return det>.95F;
    }

    private static float[] quaternion(float[] m)
    {
        float[] n=new float[9];
        for(int col=0;col<3;col++){
            float a=m[col*4],b=m[col*4+1],c=m[col*4+2];
            float length=(float)Math.sqrt(a*a+b*b+c*c);
            n[col*3]=a/length;
            n[col*3+1]=b/length;
            n[col*3+2]=c/length;
        }
        float trace=n[0]+n[4]+n[8];
        float x,y,z,w;
        if(trace>0F){
            float t=(float)Math.sqrt(trace+1F)*2F;
            w=.25F*t;
            x=(n[5]-n[7])/t;
            y=(n[6]-n[2])/t;
            z=(n[1]-n[3])/t;
        }else if(n[0]>n[4]&&n[0]>n[8]){
            float t=(float)Math.sqrt(1F+n[0]-n[4]-n[8])*2F;
            w=(n[5]-n[7])/t;x=.25F*t;
            y=(n[3]+n[1])/t;z=(n[6]+n[2])/t;
        }else if(n[4]>n[8]){
            float t=(float)Math.sqrt(1F+n[4]-n[0]-n[8])*2F;
            w=(n[6]-n[2])/t;x=(n[3]+n[1])/t;
            y=.25F*t;z=(n[7]+n[5])/t;
        }else{
            float t=(float)Math.sqrt(1F+n[8]-n[0]-n[4])*2F;
            w=(n[1]-n[3])/t;x=(n[6]+n[2])/t;
            y=(n[7]+n[5])/t;z=.25F*t;
        }
        float length=(float)Math.sqrt(x*x+y*y+z*z+w*w);
        if(!(length>.01F))
            throw new IllegalArgumentException("Invalid Emoticons bone rotation");
        return new float[]{x/length,y/length,z/length,w/length};
    }

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
        // A cube with only eight vertices has no samples at the middle
        // of its long axis. Splitting it would leave a literal hole in the
        // parent body hull, so segment only sufficiently dense skin meshes.
        int nSlices=count<32?1:
                Math.min(slices,Math.max(1,(int)Math.floor(length/.14F)));
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
