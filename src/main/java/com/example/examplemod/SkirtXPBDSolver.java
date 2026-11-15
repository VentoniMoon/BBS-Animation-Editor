package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Physics 3.0 / Stage 3: connected 60-node cloth with XPBD distance,
 * diagonal shear, bending and moving waist anchors. Runs independently
 * from the former per-bone Jolt skirt. All constraints share a topology;
 * no rotation of authored Chameleon cube bones is produced.
 *
 * Contacts: fitted Emoticons leg OBBs from SkirtXPBDContacts (only when
 * really present), particle-particle and non-neighbor vertex-triangle
 * cloth self-collision. Triangle continuous collision detection, wind
 * and render-independent offline frame baking are future refinements;
 * this is a guarded, opt-in realtime preview, not production final physics.
 */
public final class SkirtXPBDSolver
{
    public static final int N=60;
    public static final int SEGMENTS=20;
    private static final int ITERATIONS=8;
    private static final int SUBSTEPS=2;
    private static final float GRAVITY=-9.81F;
    private static final float DRAG=.993F;
    private static final float CONTACT_RADIUS=.018F;
    private static final float SELF_RADIUS=.030F;
    private static final float MAX_OFFSET=.37F;
    private static final float PIN_EPS=1E-8F;

    /** Rest-length constraint; rest authored geometry is never modified. */
    public static final class Constraint
    {
        public final int a,b;
        public final float rest,compliance;
        float lambda;
        private Constraint(int a,int b,float rest,float compliance)
        {
            this.a=a;this.b=b;this.rest=rest;this.compliance=compliance;
        }
    }
    public static final class Result
    {
        public final float[][] particles;
        public final int legContacts,selfContacts,substeps;
        public final float maxDistanceError;
        private Result(float[][] points,int legs,int self,int steps,
                float stretch)
        {
            particles=copy(points);
            legContacts=legs;selfContacts=self;substeps=steps;
            maxDistanceError=stretch;
        }
    }
    public static final class State
    {
        private final float[][] position=new float[N][3];
        private final float[][] previous=new float[N][3];
        private final boolean[] pinned=new boolean[N];
        private final int[] column=new int[N];
        private final List<Constraint> links=new ArrayList<Constraint>();
        private final List<int[]> triangles=new ArrayList<int[]>();
        private final Set<Long> adjacent=new HashSet<Long>();
        private int stepCount;

        private State(SkirtClothSurfaceMesh.Snapshot source,
                SkirtClothSharedSurface.Surface shared,float[][] target)
        {
            if(source==null||shared==null||shared.nodes.size()!=N||
                    target==null||target.length!=N)
                throw new IllegalArgumentException("Invalid XPBD source");
            int[][] rings=new int[3][SEGMENTS];
            boolean[] used=new boolean[N];
            for(int c=0;c<SEGMENTS;c++)
            {
                SkirtClothSurfaceMesh.Patch upper=
                        source.patches.get(c*2);
                SkirtClothSurfaceMesh.Patch lower=
                        source.patches.get(c*2+1);
                SkirtClothSharedSurface.SkinPatch up=
                        shared.patches.get(c*2);
                SkirtClothSharedSurface.SkinPatch lo=
                        shared.patches.get(c*2+1);
                if(upper.lower||!lower.lower||
                        !upper.bone.equals(up.bone)||
                        !lower.bone.equals(lo.bone))
                    throw new IllegalArgumentException("Wrong cloth patch order");
                rings[0][c]=up.nodes[upper.topLeft];
                rings[1][c]=up.nodes[upper.bottomLeft];
                rings[2][c]=lo.nodes[lower.bottomLeft];
                for(int row=0;row<3;row++)
                {
                    int n=rings[row][c];
                    if(n<0||n>=N||used[n]||
                            shared.nodes.get(n).row!=row)
                        throw new IllegalArgumentException("Invalid 20x3 cloth ring");
                    used[n]=true;
                    column[n]=c;
                    pinned[n]=(row==0);
                }
            }
            for(boolean b:used)
                if(!b)throw new IllegalArgumentException("Unbound particle");
            for(int i=0;i<N;i++)
            {
                valid(target[i]);
                System.arraycopy(target[i],0,position[i],0,3);
                System.arraycopy(target[i],0,previous[i],0,3);
            }

            // Shared structural mesh: no independent local cube chains.
            for(int row=0;row<3;row++)
                for(int c=0;c<SEGMENTS;c++)
                {
                    int next=(c+1)%SEGMENTS,second=(c+2)%SEGMENTS;
                    link(rings[row][c],rings[row][next],
                            shared,.0000005F); // circumferential stretch
                    if(row>0)
                        link(rings[row][c],rings[row][second],
                                shared,.00008F); // in-plane bending
                }
            for(int c=0;c<SEGMENTS;c++)
            {
                int n=(c+1)%SEGMENTS;
                for(int row=0;row<2;row++)
                {
                    link(rings[row][c],rings[row+1][c],
                            shared,.0000004F); // vertical stretch
                    link(rings[row][c],rings[row+1][n],
                            shared,.000004F); // shear
                    link(rings[row][n],rings[row+1][c],
                            shared,.000004F);
                    // Build a fixed 20x2 two-triangle cloth manifold.
                    triangles.add(new int[]{rings[row][c],
                            rings[row+1][c],rings[row+1][n]});
                    triangles.add(new int[]{rings[row][c],
                            rings[row+1][n],rings[row][n]});
                }
                link(rings[0][c],rings[2][c],
                        shared,.00010F); // vertical bend
            }
        }

        private void link(int a,int b,SkirtClothSharedSurface.Surface rest,
                float compliance)
        {
            if(a==b)return;
            long key=key(a,b);
            if(adjacent.add(key))
            {
                float r=distance(rest.nodes.get(a).rest,
                        rest.nodes.get(b).rest);
                if(r<.0001F||!Float.isFinite(r))
                    throw new IllegalArgumentException("Zero XPBD cloth edge");
                links.add(new Constraint(a,b,r,compliance));
            }
        }

        /** Actor root has moved even when its local animated bones did
         * not. Move only the simulated rows slightly behind the new actor
         * center, using the true model-to-world affine 3x3. */
        public void rootMotion(float[] worldDelta,float[] modelToWorld)
        {
            if(worldDelta==null||worldDelta.length!=3||
                    !SkirtXPBDContacts.matrixValid(modelToWorld))return;
            float[] m=modelToWorld;
            float a=m[0],b=m[4],c=m[8],
                    d=m[1],e=m[5],f=m[9],
                    g=m[2],h=m[6],i=m[10];
            float A=e*i-f*h,D=f*g-d*i,G=d*h-e*g;
            float det=a*A+b*D+c*G;
            if(!Float.isFinite(det)||Math.abs(det)<1E-8F)return;
            float dx=worldDelta[0],dy=worldDelta[1],dz=worldDelta[2];
            float inv=1F/det;
            float[] local={
                    (A*dx+(c*h-b*i)*dy+(b*f-c*e)*dz)*inv,
                    (D*dx+(a*i-c*g)*dy+(c*d-a*f)*dz)*inv,
                    (G*dx+(b*g-a*h)*dy+(a*e-b*d)*dz)*inv
            };
            float magnitude=length(local);
            if(!Float.isFinite(magnitude)||magnitude>.4F)return;
            for(int n=0;n<N;n++)
            {
                if(pinned[n])continue;
                float response=(magnitude<.001F?0F:.25F);
                for(int k=0;k<3;k++)
                {
                    float delta=local[k]*response;
                    position[n][k]-=delta;
                    previous[n][k]-=delta;
                }
            }
        }

        /**
         * Feed a deterministic fixed length step (1/60 typical).
         * For multi-pass renderer use the same RESULT per video/timeline
         * frame; do not advance once per eye, shadow or FBO redraw.
         */
        public Result advance(float dt,SkirtXPBDPoseSkin.Frame pose,
                SkirtXPBDContacts.Frame bodies)
        {
            if(pose==null||pose.target.length!=N||
                    !Float.isFinite(dt)||dt<0F||dt>.1F)
                throw new IllegalArgumentException("Invalid cloth time/pose");
            float maxWaist=0F;
            for(int n=0;n<N;n++)
            {
                valid(pose.target[n]);
                if(pinned[n])
                    maxWaist=Math.max(maxWaist,
                            distance(position[n],pose.target[n]));
            }
            if(maxWaist>.60F)
            {
                // Seek, new pose, teleport or invalid actor identity:
                // carry zero prior inertia into the new timeline position.
                for(int n=0;n<N;n++)
                {
                    System.arraycopy(pose.target[n],0,position[n],0,3);
                    System.arraycopy(pose.target[n],0,previous[n],0,3);
                }
            }
            for(int n=0;n<N;n++)
                if(pinned[n])
                    System.arraycopy(pose.target[n],0,position[n],0,3);

            if(dt<1E-6F)return result(0,0,0);
            int legs=0,self=0;
            float h=Math.min(dt,.045F)/SUBSTEPS;
            for(int sub=0;sub<SUBSTEPS;sub++)
            {
                // Inverse-mass zero anchors at the moving waistband.
                for(int n=0;n<N;n++)
                {
                    if(pinned[n])
                    {
                        System.arraycopy(pose.target[n],0,position[n],0,3);
                        System.arraycopy(pose.target[n],0,previous[n],0,3);
                        continue;
                    }
                    float[] p=position[n],prev=previous[n];
                    float vx=(p[0]-prev[0])*DRAG;
                    float vy=(p[1]-prev[1])*DRAG;
                    float vz=(p[2]-prev[2])*DRAG;
                    for(int k=0;k<3;k++)prev[k]=p[k];
                    p[0]+=vx;
                    p[1]+=vy+GRAVITY*h*h;
                    p[2]+=vz;
                    // Weak pose following, not an independent second bone
                    // animation. Waist follows the actor exactly.
                    for(int k=0;k<3;k++)
                        p[k]+=(pose.target[n][k]-p[k])*.012F;
                }

                for(Constraint c:links)c.lambda=0F;
                for(int iteration=0;iteration<ITERATIONS;iteration++)
                {
                    for(Constraint c:links)project(c,h);
                    for(int n=0;n<N;n++)
                    {
                        if(pinned[n])continue;
                        if(bodies!=null)
                        {
                            float[] corrected=SkirtXPBDContacts.corrected(
                                    position[n],bodies,CONTACT_RADIUS);
                            if(distance(corrected,position[n])>1E-7F)
                            {
                                System.arraycopy(corrected,0,position[n],0,3);
                                legs++;
                            }
                        }
                    }
                    // Full non-neighbor self checks are comparatively
                    // expensive in Forge 1.12 and don't need to run on
                    // every stretch/shear iteration. Two trailing passes
                    // settle interpenetration after most edge corrections.
                    if(iteration>=ITERATIONS-2)
                    {
                        self+=selfParticles();
                        self+=selfTriangles();
                    }
                    // Collision resolution MUST NOT move the pinned waist.
                    for(int n=0;n<N;n++)
                        if(pinned[n])
                            System.arraycopy(pose.target[n],0,position[n],0,3);
                }

                for(int n=0;n<N;n++)
                {
                    if(pinned[n])continue;
                    float[] p=position[n],ref=pose.target[n];
                    float gap=distance(p,ref);
                    if(!Float.isFinite(gap))
                        throw new IllegalStateException("XPBD divergence");
                    if(gap>MAX_OFFSET)
                    {
                        float gain=MAX_OFFSET/gap;
                        for(int k=0;k<3;k++)
                            p[k]=ref[k]+(p[k]-ref[k])*gain;
                        for(int k=0;k<3;k++)previous[n][k]=p[k];
                    }
                }
            }
            stepCount++;
            return result(legs,self,SUBSTEPS);
        }

        public Result current()
        {return result(0,0,0);}

        private Result result(int contacts,int self,int steps)
        {
            float max=0F;
            for(Constraint c:links)
                max=Math.max(max,Math.abs(
                        distance(position[c.a],position[c.b])-c.rest));
            return new Result(position,contacts,self,steps,max);
        }

        private void project(Constraint c,float dt)
        {
            float[] a=position[c.a],b=position[c.b];
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            float dist=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(dist<1E-7F)return;
            float wa=pinned[c.a]?0F:1F,wb=pinned[c.b]?0F:1F;
            float alpha=c.compliance/(dt*dt);
            float delta=-(dist-c.rest+alpha*c.lambda)/(wa+wb+alpha);
            c.lambda+=delta;
            float adjust=delta/dist;
            for(int k=0;k<3;k++)
            {
                float component=k==0?dx:k==1?dy:dz;
                a[k]-=wa*adjust*component;
                b[k]+=wb*adjust*component;
            }
        }

        private int selfParticles()
        {
            int contacts=0;
            for(int i=0;i<N;i++)
                for(int j=i+1;j<N;j++)
                {
                    if(neighbor(i,j)||Math.abs(column[i]-column[j])<=1||
                            Math.abs(column[i]-column[j])>=SEGMENTS-1)
                        continue;
                    float d=distance(position[i],position[j]);
                    if(d>=SELF_RADIUS||d<1E-7F)continue;
                    float wi=pinned[i]?0F:1F,wj=pinned[j]?0F:1F;
                    if(wi+wj<.5F)continue;
                    float push=(SELF_RADIUS-d)/(d*(wi+wj));
                    for(int k=0;k<3;k++)
                    {
                        float delta=(position[j][k]-position[i][k])*push;
                        position[i][k]-=wi*delta;
                        position[j][k]+=wj*delta;
                    }
                    contacts++;
                }
            return contacts;
        }

        /** Non-adjacent vertex/triangle self-contact. This prevents
         * a distant panel vertex from passing through another panel.
         * Swept/CCD edge-edge collision is not claimed here. */
        private int selfTriangles()
        {
            int contacts=0;
            for(int p=0;p<N;p++)
            {
                for(int[] tri:triangles)
                {
                    if(p==tri[0]||p==tri[1]||p==tri[2])continue;
                    boolean sameSector=false;
                    for(int v:tri)
                    {
                        int circular=Math.abs(column[p]-column[v]);
                        circular=Math.min(circular,SEGMENTS-circular);
                        if(circular<=2){sameSector=true;break;}
                    }
                    if(sameSector)continue;
                    float[] a=position[tri[0]],b=position[tri[1]],
                            c=position[tri[2]],point=position[p];
                    float[] edge1=minus(b,a),edge2=minus(c,a);
                    float[] n=cross(edge1,edge2);
                    float norm=length(n);
                    if(norm<1E-7F)continue;
                    for(int k=0;k<3;k++)n[k]/=norm;
                    float signed=dot(minus(point,a),n);
                    if(Math.abs(signed)>=SELF_RADIUS*.8F)continue;
                    float[] surface=new float[3];
                    for(int k=0;k<3;k++)
                        surface[k]=point[k]-signed*n[k];
                    if(!withinTriangle(surface,a,b,c))continue;
                    float amount=SELF_RADIUS*.8F-Math.abs(signed);
                    float sign=signed>=0F?1F:-1F;
                    float wa=pinned[p]?0F:1F;
                    float w0=pinned[tri[0]]?0F:1F;
                    float w1=pinned[tri[1]]?0F:1F;
                    float w2=pinned[tri[2]]?0F:1F;
                    float total=wa+(w0+w1+w2)/3F;
                    if(total<.5F)continue;
                    for(int k=0;k<3;k++)
                    {
                        float push=sign*amount*n[k]/total;
                        point[k]+=wa*push;
                        a[k]-=w0*push/3F;
                        b[k]-=w1*push/3F;
                        c[k]-=w2*push/3F;
                    }
                    contacts++;
                }
            }
            return contacts;
        }

        private static boolean withinTriangle(float[] p,float[] a,
                float[] b,float[] c)
        {
            float[] v0=minus(c,a),v1=minus(b,a),v2=minus(p,a);
            float d00=dot(v0,v0),d01=dot(v0,v1),d02=dot(v0,v2);
            float d11=dot(v1,v1),d12=dot(v1,v2);
            float den=d00*d11-d01*d01;
            if(Math.abs(den)<1E-9F)return false;
            float v=(d11*d02-d01*d12)/den;
            float w=(d00*d12-d01*d02)/den;
            return v>=.01F&&w>=.01F&&v+w<=.99F;
        }

        private boolean neighbor(int a,int b)
        {return adjacent.contains(key(a,b));}
    }
    private SkirtXPBDSolver(){}

    public static State create(SkirtClothSurfaceMesh.Snapshot source,
            SkirtClothSharedSurface.Surface shared,
            SkirtXPBDPoseSkin.Frame pose)
    {
        if(pose==null)return null;
        return new State(source,shared,pose.target);
    }

    private static long key(int a,int b)
    {
        int lo=Math.min(a,b),hi=Math.max(a,b);
        return ((long)lo<<32)|hi;
    }
    private static float distance(float[] a,float[] b)
    {return length(minus(a,b));}
    private static float length(float[] v)
    {return (float)Math.sqrt(dot(v,v));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float[] minus(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] cross(float[] a,float[] b)
    {return new float[]{a[1]*b[2]-a[2]*b[1],
            a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float[][] copy(float[][] a)
    {
        float[][] c=new float[a.length][];
        for(int i=0;i<a.length;i++)c[i]=a[i].clone();
        return c;
    }
    private static void valid(float[] point)
    {
        if(point==null||point.length!=3)
            throw new IllegalArgumentException("Bad XPBD target");
        for(float x:point)
            if(!Float.isFinite(x))
                throw new IllegalArgumentException("Non-finite XPBD target");
    }
}
