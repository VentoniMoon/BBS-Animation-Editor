package com.example.examplemod;

/**
 * World-space bone pin position solver (FABRIK, Java 8).
 *
 * This is geometry-only: no renderer, keyframe, scale or actor entity is
 * modified. A rig adapter supplies live joint world positions root-to-tip,
 * and receives the solved TARGET pivots for its own rotation-only retarget.
 *
 * Distances are captured from the supplied pose and preserved through
 * all iterations. When the target is unreachable the chain aims toward it
 * at full length WITHOUT scaling any bone, morph or mesh.
 */
public final class EditorWorldBoneIK
{
    private EditorWorldBoneIK(){}

    public static final class Result
    {
        public final float[][] joints;
        public final boolean reachable;
        public final float residual;
        public final int iterations;

        private Result(float[][] points,boolean reachable,
                float residual,int iterations)
        {
            this.joints=copy(points);
            this.reachable=reachable;
            this.residual=residual;
            this.iterations=iterations;
        }
    }

    public static Result solve(float[][] input,float[] target,
            int maxIterations,float tolerance)
    {
        if(input==null||input.length<2||input.length>64||
                target==null||target.length!=3||
                maxIterations<1||maxIterations>128||
                !Float.isFinite(tolerance)||tolerance<=0F)
            throw new IllegalArgumentException("Invalid world pin chain");
        for(float v:target)if(!Float.isFinite(v))
            throw new IllegalArgumentException("Non-finite world pin target");
        float[][] joints=copy(input);
        float[] root=joints[0].clone();
        float[] length=new float[joints.length-1];
        float total=0F;
        for(int i=0;i<length.length;i++)
        {
            float distance=distance(joints[i],joints[i+1]);
            if(distance<1E-5F||!Float.isFinite(distance))
                throw new IllegalArgumentException("Zero-length world pin segment");
            length[i]=distance;total+=distance;
        }
        float rootDistance=distance(root,target);
        if(rootDistance>=total)
        {
            // Unreachable target: aim at it, but NEVER stretch the rig.
            float[] direction=unit(root,target);
            for(int i=0;i<length.length;i++)
                for(int k=0;k<3;k++)
                    joints[i+1][k]=joints[i][k]+direction[k]*length[i];
            return new Result(joints,false,
                    distance(joints[joints.length-1],target),0);
        }

        int iterations=0;
        while(iterations<maxIterations)
        {
            iterations++;
            joints[joints.length-1]=target.clone();

            // Tip->root: each segment must retain its native rest length.
            for(int i=joints.length-2;i>=0;i--)
            {
                float[] dir=unit(joints[i+1],joints[i]);
                for(int k=0;k<3;k++)
                    joints[i][k]=joints[i+1][k]+dir[k]*length[i];
            }

            joints[0]=root.clone();
            // Root->tip: keep a rigid kinematic chain, no scale factors.
            for(int i=0;i<length.length;i++)
            {
                float[] dir=unit(joints[i],joints[i+1]);
                for(int k=0;k<3;k++)
                    joints[i+1][k]=joints[i][k]+dir[k]*length[i];
            }
            if(distance(joints[joints.length-1],target)<=tolerance)break;
        }
        return new Result(joints,true,
                distance(joints[joints.length-1],target),iterations);
    }

    private static float[][] copy(float[][] a)
    {
        float[][] b=new float[a.length][3];
        for(int i=0;i<a.length;i++)
        {
            if(a[i]==null||a[i].length!=3)
                throw new IllegalArgumentException("Invalid world pin joint");
            for(int j=0;j<3;j++)
            {
                if(!Float.isFinite(a[i][j]))
                    throw new IllegalArgumentException("Non-finite world pin joint");
                b[i][j]=a[i][j];
            }
        }
        return b;
    }

    private static float distance(float[] a,float[] b)
    {
        double sum=0D;
        for(int k=0;k<3;k++)
        {
            double d=(double)a[k]-b[k];sum+=d*d;
        }
        return (float)Math.sqrt(sum);
    }

    private static float[] unit(float[] a,float[] b)
    {
        float d=distance(a,b);
        // Degenerate FABRIK pose: a deterministic direction keeps the
        // solver finite; a later pass with a non-degenerate link settles.
        if(d<1E-7F)return new float[]{0F,1F,0F};
        return new float[]{(b[0]-a[0])/d,
                (b[1]-a[1])/d,(b[2]-a[2])/d};
    }
}
