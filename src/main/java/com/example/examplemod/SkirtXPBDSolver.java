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
    // Keep the original 60 source/UV nodes stable for existing Chameleon
    // bindings, but physically solve five rows (100 nodes) by default.
    // The 3-row baseline remains available for a controlled A/B comparison.
    public static final int N=60;
    public static final int SEGMENTS=20;
    public static final int ROWS=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdFineGrid","true"))?5:3;
    public static final int SIM_NODES=SEGMENTS*ROWS;
    private static final int ITERATIONS=8;
    private static final int SUBSTEPS=2;
    private static final float GRAVITY=-9.81F;
    private static final float DRAG=.993F;
    private static final float CONTACT_RADIUS=.018F;
    private static final float SELF_RADIUS=.030F;
    private static final float MAX_OFFSET=.37F;
    private static final float POSE_JUMP_RESET=.40F;
    private static final float MIN_RADIAL_FRACTION=.85F;
    // The normal 0.37-block leash and 85% radial silhouette work for
    // walking, but forbid folds over both horizontal thighs. Relax only
    // when the actual two fitted upper-leg bones agree on a seated pose.
    private static final float SEATED_EXTRA_OFFSET=.28F;
    private static final float SEATED_RADIAL_RELIEF=.38F;
    private static final boolean SEATED_FOLD=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdSeatedFold","true"));
    // Keep the first free ring below the belt from expanding when the
    // thighs raise; a cap is applied ONLY if the proposed position is
    // outside the real leg hull. A/B: -D...xpbdSeatedUpperControl=false
    private static final boolean SEATED_UPPER_CONTROL=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedUpperControl","true"));
    private static final boolean SEATED_BEND_RELIEF=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedBendRelief","true"));
    // Contact must be evaluated on the triangles BETWEEN cloth nodes:
    // a thigh can intersect a polygon without containing any vertex.
    // Keep this additional work restricted to a deep bilateral seat.
    private static final boolean SEATED_SURFACE_CONTACTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedSurfaceContacts","true"));
    private static final float MAX_SURFACE_CORRECTION=.045F;
    private static final boolean RADIAL_GUARD=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdRadialGuard","true"));
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
        /** Sixty UV-owned control points, backward compatible with the
         * original Chameleon material binding and volumetric skin. */
        public final float[][] particles;
        /** All solver particles, including interpolated height rows. */
        public final float[][] refinedParticles,refinedTarget;
        /** Native source node index -> refined solver particle index. */
        public final int[] sourceToRefined;
        public final int legContacts,selfContacts,substeps;
        public final float maxDistanceError;
        private Result(float[][] nativePoints,float[][] finePoints,
                float[][] fineTargets,int[] map,int legs,int self,int steps,
                float stretch)
        {
            particles=copy(nativePoints);
            refinedParticles=copy(finePoints);
            refinedTarget=copy(fineTargets);
            sourceToRefined=map.clone();
            legContacts=legs;selfContacts=self;substeps=steps;
            maxDistanceError=stretch;
        }
    }
    public static final class State
    {
        private final float[][] position=new float[SIM_NODES][3];
        private final float[][] previous=new float[SIM_NODES][3];
        private final float[][] previousPoseTarget=new float[SIM_NODES][3];
        private final float[][] restFine=new float[SIM_NODES][3];
        private final boolean[] pinned=new boolean[SIM_NODES];
        private final int[] column=new int[SIM_NODES];
        private final int[] sourceToFine=new int[N];
        private final int[][] originalRing=new int[3][SEGMENTS];
        private final int[][] ring=new int[ROWS][SEGMENTS];
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
                originalRing[0][c]=up.nodes[upper.topLeft];
                originalRing[1][c]=up.nodes[upper.bottomLeft];
                originalRing[2][c]=lo.nodes[lower.bottomLeft];
                for(int row=0;row<3;row++)
                {
                    int n=originalRing[row][c];
                    if(n<0||n>=N||used[n]||
                            shared.nodes.get(n).row!=row)
                        throw new IllegalArgumentException("Invalid source ring");
                    used[n]=true;
                }
            }
            for(boolean b:used)
                if(!b)throw new IllegalArgumentException("Unbound source node");
            int stride=(ROWS-1)/2;
            for(int row=0;row<ROWS;row++)
                for(int c=0;c<SEGMENTS;c++)
                {
                    int id=row*SEGMENTS+c;
                    ring[row][c]=id;
                    pinned[id]=(row==0);
                    column[id]=c;
                    int lower=Math.min(1,row/stride);
                    float t=(row-lower*stride)/(float)stride;
                    int a=originalRing[lower][c];
                    int b=originalRing[lower+1][c];
                    for(int k=0;k<3;k++)
                        restFine[id][k]=
                                shared.nodes.get(a).rest[k]*(1F-t)+
                                shared.nodes.get(b).rest[k]*t;
                }
            for(int sourceRow=0;sourceRow<3;sourceRow++)
                for(int c=0;c<SEGMENTS;c++)
                    sourceToFine[originalRing[sourceRow][c]]=
                            ring[sourceRow*stride][c];
            float[][] fineTarget=refine(target);
            for(int i=0;i<SIM_NODES;i++)
            {
                valid(fineTarget[i]);
                System.arraycopy(fineTarget[i],0,position[i],0,3);
                System.arraycopy(fineTarget[i],0,previous[i],0,3);
                System.arraycopy(fineTarget[i],0,previousPoseTarget[i],0,3);
            }

            // Each original source strip receives an intermediate physical
            // latitude; the rendered UV seam stays in its original place.
            for(int row=0;row<ROWS;row++)
                for(int c=0;c<SEGMENTS;c++)
                {
                    int next=(c+1)%SEGMENTS,second=(c+2)%SEGMENTS;
                    link(ring[row][c],ring[row][next],
                            restFine,.0000005F);
                    if(row>0)
                        link(ring[row][c],ring[row][second],
                                restFine,.00008F);
                }
            for(int c=0;c<SEGMENTS;c++)
            {
                int next=(c+1)%SEGMENTS;
                for(int row=0;row<ROWS-1;row++)
                {
                    link(ring[row][c],ring[row+1][c],
                            restFine,.0000004F);
                    link(ring[row][c],ring[row+1][next],
                            restFine,.000004F);
                    link(ring[row][next],ring[row+1][c],
                            restFine,.000004F);
                    triangles.add(new int[]{ring[row][c],
                            ring[row+1][c],ring[row+1][next]});
                    triangles.add(new int[]{ring[row][c],
                            ring[row+1][next],ring[row][next]});
                }
                // Bending over two adjacent latitudes allows the fabric to
                // curve across a bent thigh instead of one rigid plate.
                for(int row=0;row<ROWS-2;row++)
                    link(ring[row][c],ring[row+2][c],
                            restFine,.00010F);
            }
        }

        /** Map 60 native control nodes to the chosen 60/100 solver grid. */
        private float[][] refine(float[][] source)
        {
            if(source==null||source.length!=N)
                throw new IllegalArgumentException("Incomplete source pose");
            float[][] out=new float[SIM_NODES][3];
            int stride=(ROWS-1)/2;
            for(int row=0;row<ROWS;row++)
                for(int c=0;c<SEGMENTS;c++)
                {
                    int lower=Math.min(1,row/stride);
                    float t=(row-lower*stride)/(float)stride;
                    float[] a=source[originalRing[lower][c]];
                    float[] b=source[originalRing[lower+1][c]];
                    int id=ring[row][c];
                    for(int k=0;k<3;k++)
                        out[id][k]=a[k]*(1F-t)+b[k]*t;
                }
            return out;
        }

        private void link(int a,int b,float[][] rest,float compliance)
        {
            if(a==b)return;
            long key=key(a,b);
            if(adjacent.add(key))
            {
                float length=distance(rest[a],rest[b]);
                if(length<.0001F||!Float.isFinite(length))
                    throw new IllegalArgumentException("Zero XPBD cloth edge");
                links.add(new Constraint(a,b,length,compliance));
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
            for(int n=0;n<SIM_NODES;n++)
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
            float[][] fineTarget=refine(pose.target);
            float seated=SEATED_FOLD&&bodies!=null?bodies.seatedBlend:0F;
            float maxOffset=MAX_OFFSET+SEATED_EXTRA_OFFSET*seated;
            float maxWaist=0F,maxPoseShift=0F;
            for(int n=0;n<SIM_NODES;n++)
            {
                valid(fineTarget[n]);
                maxPoseShift=Math.max(maxPoseShift,
                        distance(previousPoseTarget[n],fineTarget[n]));
                if(pinned[n])
                    maxWaist=Math.max(maxWaist,
                            distance(position[n],fineTarget[n]));
            }
            boolean reset=maxWaist>.60F||maxPoseShift>POSE_JUMP_RESET;
            for(int n=0;n<SIM_NODES;n++)
            {
                if(reset)
                {
                    // A seek/teleport must not inherit stale cloth velocity.
                    System.arraycopy(fineTarget[n],0,position[n],0,3);
                    System.arraycopy(fineTarget[n],0,previous[n],0,3);
                }
                else if(!pinned[n])
                {
                    // Advect the simulated state by exactly the CURRENT
                    // native pose delta, including paused/same-tick redraws.
                    // Translate Verlet current AND previous together so a
                    // new animation pose does not become spurious velocity.
                    for(int k=0;k<3;k++)
                    {
                        float delta=fineTarget[n][k]-previousPoseTarget[n][k];
                        position[n][k]+=delta;
                        previous[n][k]+=delta;
                    }
                }
                if(pinned[n])
                    System.arraycopy(fineTarget[n],0,position[n],0,3);
                System.arraycopy(fineTarget[n],0,previousPoseTarget[n],0,3);
            }
            float[] waistCenter=waistCenter(fineTarget);
            // Pausing must freeze integration, not freeze outdated pose
            // coordinates or leave an already collapsed silhouette behind.
            if(dt<1E-6F)
            {
                // Playback can render two fractional poses between fixed
                // 60Hz steps. The latest FITTED leg positions must still
                // push any cloth node outside them on that display frame.
                // This pass does not integrate gravity, change constraints,
                // or inject Verlet velocity on a repeated FBO render.
                int contacts=resolveDisplayContacts(fineTarget,bodies,waistCenter);
                contacts+=resolveSurfaceContacts(fineTarget,bodies,
                        waistCenter,seated);
                if(contacts>0)
                {
                    // The earlier display-only path displaced individual
                    // vertices but never projected the shared fabric links:
                    // a bent leg could leave spikes and split-looking panels
                    // while the timeline was paused. Relax the full graph
                    // WITHOUT gravity/Verlet integration.
                    final float relaxDt=1F/120F;
                    for(int settle=0;settle<5;settle++)
                    {
                        for(Constraint c:links)c.lambda=0F;
                        for(Constraint c:links)project(c,relaxDt,seated);
                        for(int n=0;n<SIM_NODES;n++)
                            if(pinned[n])
                                System.arraycopy(fineTarget[n],0,
                                        position[n],0,3);
                        // Finish every pass with body contact projection,
                        // not a link correction that pushes cloth into legs.
                        contacts+=resolveDisplayContacts(fineTarget,bodies,waistCenter);
                        if(settle<2)
                            contacts+=resolveSurfaceContacts(fineTarget,bodies,
                                    waistCenter,seated);
                    }
                    // Paused FBO, mirror and shadow passes must not turn
                    // these collision corrections into physical velocity.
                    for(int n=0;n<SIM_NODES;n++)
                        if(!pinned[n])
                            System.arraycopy(position[n],0,previous[n],0,3);
                }
                protectRadialSilhouette(fineTarget,waistCenter,
                        seated,maxOffset);
                stabilizeUpperRings(fineTarget,waistCenter,bodies,seated);
                return result(contacts,0,0);
            }
            int legs=0,self=0;
            float h=Math.min(dt,.045F)/SUBSTEPS;
            for(int sub=0;sub<SUBSTEPS;sub++)
            {
                // Inverse-mass zero anchors at the moving waistband.
                for(int n=0;n<SIM_NODES;n++)
                {
                    if(pinned[n])
                    {
                        System.arraycopy(fineTarget[n],0,position[n],0,3);
                        System.arraycopy(fineTarget[n],0,previous[n],0,3);
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
                        p[k]+=(fineTarget[n][k]-p[k])*
                                (.012F*(1F-.75F*seated));
                }

                for(Constraint c:links)c.lambda=0F;
                for(int iteration=0;iteration<ITERATIONS;iteration++)
                {
                    for(Constraint c:links)project(c,h,seated);
                    for(int n=0;n<SIM_NODES;n++)
                    {
                        if(pinned[n])continue;
                        if(bodies!=null)
                        {
                            float[] outward={
                                    fineTarget[n][0]-waistCenter[0],
                                    0F,fineTarget[n][2]-waistCenter[2]};
                            float[] corrected=SkirtXPBDContacts.corrected(
                                    position[n],bodies,CONTACT_RADIUS,outward);
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
                        if(seated>.60F)
                            legs+=resolveSurfaceContacts(fineTarget,bodies,
                                    waistCenter,seated);
                    }
                    // Collision resolution MUST NOT move the pinned waist.
                    for(int n=0;n<SIM_NODES;n++)
                        if(pinned[n])
                            System.arraycopy(fineTarget[n],0,position[n],0,3);
                }

                for(int n=0;n<SIM_NODES;n++)
                {
                    if(pinned[n])continue;
                    float[] p=position[n],ref=fineTarget[n];
                    float gap=distance(p,ref);
                    if(!Float.isFinite(gap))
                        throw new IllegalStateException("XPBD divergence");
                    if(gap>maxOffset)
                    {
                        float gain=maxOffset/gap;
                        for(int k=0;k<3;k++)
                            p[k]=ref[k]+(p[k]-ref[k])*gain;
                        for(int k=0;k<3;k++)previous[n][k]=p[k];
                    }
                }
            }
            // The bounded pose leash must not undo contacts on the final
            // substep of a seated pose. Unlike walking, deeply folded
            // cloth often needs to travel beyond its original silhouette.
            if(seated>.50F&&bodies!=null)
            {
                legs+=resolveDisplayContacts(fineTarget,bodies,waistCenter);
                legs+=resolveSurfaceContacts(fineTarget,bodies,
                        waistCenter,seated);
            }
            protectRadialSilhouette(fineTarget,waistCenter,seated,maxOffset);
            stabilizeUpperRings(fineTarget,waistCenter,bodies,seated);
            stepCount++;
            return result(legs,self,SUBSTEPS);
        }

        /**
         * Seated surface / thigh support (triangle-area contacts).
         * The 100-node lattice can have three vertices clear of a thigh
         * while the large triangle BETWEEN them still cuts through it.
         * Sample its barycenter; if truly inside a fitted leg OBB, apply
         * a small, mass-distributed correction to its free vertices.
         *
         * There is deliberately no artificial attraction to the leg:
         * gravity remains responsible for bringing fabric DOWN onto
         * its support. This is not run during ordinary walking.
         */
        private int resolveSurfaceContacts(float[][] fineTarget,
                SkirtXPBDContacts.Frame bodies,float[] center,float seated)
        {
            if(!SEATED_SURFACE_CONTACTS||bodies==null||seated<=.60F)
                return 0;
            float strength=Math.min(1F,Math.max(0F,
                    (seated-.60F)/.30F));
            if(strength<=1E-5F)return 0;

            // Jacobi accumulation: repeated adjacent triangle hits must NOT
            // move a shared node once per face. Gauss-Seidel-style in-place
            // correction made the 20x5 skirt alternate between two poses,
            // even with an unchanged leg collider.
            float[][] accumulated=new float[SIM_NODES][3];
            int[] samples=new int[SIM_NODES];
            int contacts=0;
            for(int[] tri:triangles)
            {
                int free=0;
                float[] sample=new float[3];
                float[] outward=new float[3];
                for(int index:tri)
                {
                    if(!pinned[index])free++;
                    for(int k=0;k<3;k++)
                        sample[k]+=position[index][k]/3F;
                    outward[0]+=fineTarget[index][0]/3F;
                    outward[2]+=fineTarget[index][2]/3F;
                }
                if(free==0)continue;
                outward[0]-=center[0];
                outward[2]-=center[2];
                float[] clearance=SkirtXPBDContacts.corrected(
                        sample,bodies,CONTACT_RADIUS,outward);
                float depth=distance(sample,clearance);
                if(!Float.isFinite(depth)||depth<=1E-5F)continue;

                // Three barycentric weights; fixed contact cap, and an
                // averaged single update per cloth node, not per triangle.
                float ratio=Math.min(depth,MAX_SURFACE_CORRECTION)*
                        strength/depth*(3F/free);
                for(int index:tri)
                {
                    if(pinned[index])continue;
                    for(int k=0;k<3;k++)
                        accumulated[index][k]+=
                                (clearance[k]-sample[k])*ratio;
                    samples[index]++;
                }
                contacts++;
            }
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n]||samples[n]==0)continue;
                float[] delta=accumulated[n];
                float dx=delta[0]/samples[n],dy=delta[1]/samples[n],
                        dz=delta[2]/samples[n];
                float norm=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                float max=MAX_SURFACE_CORRECTION*strength;
                float scale=norm>max?max/norm:1F;
                for(int k=0;k<3;k++)
                {
                    float amount=(k==0?dx:(k==1?dy:dz))*scale;
                    position[n][k]+=amount;
                    // A correction is a positional contact constraint,
                    // NOT a bounce impulse. Translate Verlet history with
                    // it to prevent alternating-frame velocity.
                    previous[n][k]+=amount;
                }
            }
            return contacts;
        }

        private int resolveDisplayContacts(float[][] fineTarget,
                SkirtXPBDContacts.Frame bodies,float[] center)
        {
            if(bodies==null)return 0;
            int contacts=0;
            // Multiple overlapping fitted OBBs (three skirt zones per leg)
            // need a second pass to settle adjacent contacts. Keep this
            // cheaper than a complete XPBD integration step.
            for(int pass=0;pass<2;pass++)
                for(int n=0;n<SIM_NODES;n++)
                {
                    if(pinned[n])continue;
                    float[] outward={
                            fineTarget[n][0]-center[0],0F,
                            fineTarget[n][2]-center[2]};
                    float[] corrected=SkirtXPBDContacts.corrected(
                            position[n],bodies,CONTACT_RADIUS,outward);
                    if(distance(corrected,position[n])<=1E-7F)
                        continue;
                    for(int k=0;k<3;k++)
                    {
                        float delta=corrected[k]-position[n][k];
                        position[n][k]=corrected[k];
                        previous[n][k]+=delta;
                    }
                    contacts++;
                }
            return contacts;
        }

        private float[] waistCenter(float[][] fineTarget)
        {
            float[] center=new float[3];
            int count=0;
            for(int n=0;n<SIM_NODES;n++)
                if(pinned[n])
                {
                    for(int k=0;k<3;k++)center[k]+=fineTarget[n][k];
                    count++;
                }
            if(count>0)
                for(int k=0;k<3;k++)center[k]/=count;
            return center;
        }

        /** Keep the hem and middle ring from inverting into the legs.
         * A conservative 85% of EACH node's current native pose radius
         * is a lower bound only, never a force that expands beyond the
         * authored silhouette. No waist anchor or model bone is changed.
         * Disabled with -Dbbs.editor.physics.skirt.xpbdRadialGuard=false. */
        private void protectRadialSilhouette(float[][] fineTarget,
                float[] center,float seated,float maxOffset)
        {
            if(!RADIAL_GUARD)return;
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n])continue;
                float tx=fineTarget[n][0]-center[0];
                float tz=fineTarget[n][2]-center[2];
                float sourceRadius=(float)Math.sqrt(tx*tx+tz*tz);
                if(sourceRadius<1E-5F)continue;
                float dx=position[n][0]-center[0];
                float dz=position[n][2]-center[2];
                float radius=(float)Math.sqrt(dx*dx+dz*dz);
                float fraction=Math.max(.45F,
                        MIN_RADIAL_FRACTION-SEATED_RADIAL_RELIEF*seated);
                float deficit=sourceRadius*fraction-radius;
                if(deficit<=0F)continue;
                // Limit per-frame correction and preserve the existing
                // maximum pose offset even with deep thigh contact.
                float allowance=Math.max(0F,
                        maxOffset-distance(position[n],fineTarget[n]));
                float shift=Math.min(.08F,Math.min(deficit,allowance));
                if(shift<=0F)continue;
                position[n][0]+=tx/sourceRadius*shift;
                position[n][2]+=tz/sourceRadius*shift;
                // Do not reintroduce an inward horizontal impulse on
                // the next Verlet step; vertical movement remains free.
                previous[n][0]=position[n][0];
                previous[n][2]=position[n][2];
            }
        }

        /**
         * Seat-specific upper silhouette guard. Rows 1 and 2 may bend and
         * move vertically, but cannot balloon horizontally far beyond the
         * animated native skirt. It NEVER forces a vertex into an actual
         * thigh: skip that vertex instead of fighting the contact solver.
         * Hem rows 3/4 are deliberately not constrained by this guard.
         */
        private void stabilizeUpperRings(float[][] target,float[] center,
                SkirtXPBDContacts.Frame bodies,float seated)
        {
            if(!SEATED_UPPER_CONTROL||seated<=1E-5F)return;
            int upper=ROWS==5?2:1;
            for(int row=1;row<=upper;row++)
            {
                float extra=row==1?.08F:.17F;
                float margin=row==1?.018F:.025F;
                for(int c=0;c<SEGMENTS;c++)
                {
                    int n=ring[row][c];
                    float tx=target[n][0]-center[0],
                            tz=target[n][2]-center[2];
                    float original=(float)Math.sqrt(tx*tx+tz*tz);
                    if(original<1E-5F)continue;
                    float px=position[n][0]-center[0],
                            pz=position[n][2]-center[2];
                    float actual=(float)Math.sqrt(px*px+pz*pz);
                    if(actual<1E-5F)continue;
                    // Gradual threshold after ~45 degrees on BOTH hips.
                    // A partial seated pose is allowed a wider upper ring.
                    float limit=original*(1F+extra+
                            .05F*(1F-seated))+margin;
                    if(actual<=limit)continue;
                    float desired=actual-(actual-limit)*seated;
                    float scale=desired/actual;
                    float nx=center[0]+px*scale,
                            nz=center[2]+pz*scale;
                    float[] proposed={nx,position[n][1],nz};
                    if(bodies!=null)
                    {
                        float[] pushed=SkirtXPBDContacts.corrected(
                                proposed,bodies,CONTACT_RADIUS,
                                new float[]{tx,0F,tz});
                        // If restricting the ring would enter a thigh,
                        // physical contact wins over the aesthetic cap.
                        if(distance(pushed,proposed)>.001F)continue;
                    }
                    position[n][0]=nx;
                    position[n][2]=nz;
                    // The horizontal silhouette constraint must not
                    // become outward Verlet velocity next frame.
                    previous[n][0]=nx;
                    previous[n][2]=nz;
                }
            }
        }

        public Result current()
        {return result(0,0,0);}

        private Result result(int contacts,int self,int steps)
        {
            float max=0F;
            for(Constraint c:links)
                max=Math.max(max,Math.abs(
                        distance(position[c.a],position[c.b])-c.rest));
            float[][] nativeNodes=new float[N][3];
            for(int n=0;n<N;n++)
                System.arraycopy(position[sourceToFine[n]],0,nativeNodes[n],0,3);
            return new Result(nativeNodes,position,previousPoseTarget,
                    sourceToFine,contacts,self,steps,max);
        }

        private void project(Constraint c,float dt,float seated)
        {
            float[] a=position[c.a],b=position[c.b];
            float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
            float dist=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(dist<1E-7F)return;
            float wa=pinned[c.a]?0F:1F,wb=pinned[c.b]?0F:1F;
            float compliance=c.compliance;
            if(seated>0F)
            {
                int rowA=c.a/SEGMENTS,rowB=c.b/SEGMENTS;
                // Circumferential links on the first free ring resist
                // widening, but the panel can still fold in 3D.
                if(SEATED_UPPER_CONTROL&&rowA==1&&rowB==1)
                    compliance*=1F-.85F*seated;
                // Make the lower TWO-row bending constraints softer.
                // Stretch/shear links stay intact; no torn seams.
                if(SEATED_BEND_RELIEF&&Math.abs(rowA-rowB)==2&&
                        Math.min(rowA,rowB)>=1)
                    compliance*=1F+7F*seated;
            }
            float alpha=compliance/(dt*dt);
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
            for(int i=0;i<SIM_NODES;i++)
                for(int j=i+1;j<SIM_NODES;j++)
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
            for(int p=0;p<SIM_NODES;p++)
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
