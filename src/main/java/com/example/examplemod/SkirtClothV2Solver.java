package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Physics 2.0 / cloth v2: a single constrained twenty-strip ring.
 *
 * The 20 independent Jolt chains supply real inertia and their exact pose
 * for this animation frame. This solver never advances another clock.
 * It solves fixed waist anchors, two articulated cloth rows, neighbor
 * seams and leg tolerance in ONE projected position iteration, then maps
 * the result back to Chameleon bones. Legacy radial/silhouette/post-width
 * visual solvers MUST NOT run after this solver.
 *
 * This is a connected TWO-LINK rig; Chameleon Weld is still responsible
 * for the last per-vertex seam adjustment on the actual GPU quads.
 */
public final class SkirtClothV2Solver
{
    private static final int N=20;
    private static final int ITERATIONS=8;
    private static final float BODY_MARGIN=.008F;
    private static final float ENTRY_EPS=.003F;
    private static final float MAX_UPPER_CORRECTION=.28F;
    private static final float MAX_LOWER_CORRECTION=.43F;
    private static final float MAX_MID_OFFSET=.105F;
    private static final float MAX_HEM_OFFSET=.16F;
    private static final float SOFT_FLARE_DISTANCE=.048F;
    private static final float MAX_BOX_HALF=.40F;
    private static final float MAX_BOX_VERTICAL=.60F;
    private static final float WIDTH_HALF_FALLBACK=.039F;

    public static final class Parameters
    {
        /** Allowed penetration as a fraction of the full thigh width. */
        public final float allowedLegPenetration;
        /** 0: close-to-rigid ring; 1: free flowing, responsive skirt. */
        public final float airiness;
        /** Soft preference to follow Jolt's freely simulated shape. */
        public final float restShapePull;

        public Parameters(float allowedLegPenetration,float airiness,
                float restShapePull)
        {
            this.allowedLegPenetration=clamp(allowedLegPenetration,0F,.35F);
            this.airiness=clamp(airiness,0F,1F);
            this.restShapePull=clamp(restShapePull,0F,1F);
        }

        public static Parameters defaults()
        {
            return new Parameters(read("penetrationRatio",.14F),
                    read("airiness",.82F),read("shapeElasticity",.24F));
        }
        private static float read(String name,float fallback)
        {
            try
            {
                float value=Float.parseFloat(System.getProperty(
                        "bbs.editor.physics.skirt.v2."+name,
                        Float.toString(fallback)));
                return Float.isFinite(value)?value:fallback;
            }
            catch(NumberFormatException invalid){return fallback;}
        }
    }

    public static final class Result
    {
        public final Map<String,float[]> swings;
        public final Map<String,SkirtAdaptiveWidth.Width> widths;
        public final int activePanels,movedPanels,validLegBoxes,ignoredLegBoxes;
        public final float maxPenetrationBefore,maxPenetrationAfter;
        public final float seamErrorBefore,seamErrorAfter;
        public final float peakExtraOutward,allowedPenetration;
        public final boolean accepted;

        Result(Map<String,float[]> swings,
                Map<String,SkirtAdaptiveWidth.Width> widths,
                int active,int moved,int boxes,int ignored,
                float before,float after,float seamBefore,float seamAfter,
                float outward,float penetration,boolean accepted)
        {
            this.swings=Collections.unmodifiableMap(swings);
            this.widths=Collections.unmodifiableMap(widths);
            this.activePanels=active;
            this.movedPanels=moved;
            this.validLegBoxes=boxes;
            this.ignoredLegBoxes=ignored;
            this.maxPenetrationBefore=before;
            this.maxPenetrationAfter=after;
            this.seamErrorBefore=seamBefore;
            this.seamErrorAfter=seamAfter;
            this.peakExtraOutward=outward;
            this.allowedPenetration=penetration;
            this.accepted=accepted;
        }
    }

    private static final class Panel
    {
        SkirtClothPoseCoupler.Strip s;
        float[] root,restMid,restHem;
        float[] mid,hem,baseMid,baseHem;
        float[] initialUpper,initialLower;
        float halfWidth;
        float[] outward,tangent;
        double angle;
        float activity;
    }

    private static final class Box
    {
        float[] p;
        float[][] axis=new float[3][];
        Box(float[] p)
        {
            this.p=p;
            float[] q={p[3],p[4],p[5],p[6]};
            float l=norm(q);
            for(int k=0;k<4;k++)q[k]/=l;
            axis[0]=rotate(q,new float[]{1,0,0});
            axis[1]=rotate(q,new float[]{0,1,0});
            axis[2]=rotate(q,new float[]{0,0,1});
        }
    }

    private static final class Contact
    {
        final float[] normal;
        final float depth;
        Contact(float[] normal,float depth)
        {
            this.normal=normal;
            this.depth=depth;
        }
    }

    private SkirtClothV2Solver(){}

    public static Result solve(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> jolt,BodyPartJoltContactBudget.Selection bodies,
            Parameters params)
    {
        Map<String,float[]> source=copy(jolt);
        Map<String,SkirtAdaptiveWidth.Width> baselineWidths=
                safeWidths(strips,source);
        Result untouched=new Result(source,baselineWidths,
                0,0,0,0,0F,0F,0F,0F,0F,
                params==null?.14F:params.allowedLegPenetration,false);
        if(params==null||strips==null||strips.size()!=N||jolt==null||
                jolt.size()!=2*N)return untouched;

        List<Panel> ring=new ArrayList<Panel>();
        float cx=0F,cz=0F;
        java.util.HashSet<String> used=new java.util.HashSet<String>();
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null||s.upper==null||s.lower==null||
                    !used.add(s.upper)||!used.add(s.lower)||
                    !valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(s.tipWorld,3)||!valid(jolt.get(s.upper),4)||
                    !valid(jolt.get(s.lower),4))return untouched;
            Panel p=new Panel();
            p.s=s;p.root=origin(s.upperWorld);
            p.restMid=origin(s.lowerWorld);
            p.restHem=s.tipWorld.clone();
            if(dist(p.root,p.restMid)<.04F||
                    dist(p.restMid,p.restHem)<.04F)return untouched;
            p.initialUpper=worldQ(s.upperWorld,jolt.get(s.upper));
            p.initialLower=worldQ(s.lowerWorld,jolt.get(s.lower));
            p.baseMid=add(p.root,rotate(p.initialUpper,
                    sub(p.restMid,p.root)));
            p.baseHem=add(p.baseMid,rotate(p.initialUpper,
                    rotate(p.initialLower,sub(p.restHem,p.restMid))));
            p.mid=p.baseMid.clone();
            p.hem=p.baseHem.clone();
            p.halfWidth=measureHalfWidth(s,p);
            cx+=p.root[0];cz+=p.root[2];
            ring.add(p);
        }
        cx/=N;cz/=N;
        final float x=cx,z=cz;
        for(Panel p:ring)
        {
            p.angle=Math.atan2(p.root[2]-z,p.root[0]-x);
            p.outward=radial(p.root,x,z);
            p.tangent=new float[]{-p.outward[2],0F,p.outward[0]};
        }
        Collections.sort(ring,new Comparator<Panel>()
        {
            @Override public int compare(Panel a,Panel b)
            {
                int cmp=Double.compare(a.angle,b.angle);
                return cmp!=0?cmp:a.s.upper.compareTo(b.s.upper);
            }
        });

        int ignored=0;
        List<Box> legs=new ArrayList<Box>();
        if(bodies!=null&&bodies.boxes!=null&&bodies.owners!=null)
            for(int i=0;i<Math.min(bodies.boxes.length,
                    bodies.owners.size());i++)
            {
                if(!BodyPartJoltContactBudget.isLeg(
                        bodies.owners.get(i)))continue;
                float[] box=bodies.boxes[i];
                if(!valid(box,10)||box[7]<.018F||box[8]<.018F||
                        box[9]<.018F||box[7]>MAX_BOX_HALF||
                        box[8]>MAX_BOX_VERTICAL||box[9]>MAX_BOX_HALF||
                        norm(new float[]{box[3],box[4],box[5],box[6]})<.5F)
                {
                    ignored++;
                    continue;
                }
                Box candidate=new Box(box);
                int touching=0;
                for(Panel p:ring)
                    if(depth(p,candidate,params)>ENTRY_EPS)
                        touching++;
                // A collider intersecting nearly ALL strips is not a
                // local thigh. Reject the shape, not every other valid leg.
                if(touching>=16){ignored++;continue;}
                legs.add(candidate);
            }

        float before=worst(ring,legs,params);
        float seamBefore=seamError(ring);
        if(legs.isEmpty()||before<=ENTRY_EPS)
            return new Result(source,baselineWidths,0,0,legs.size(),
                    ignored,before,before,seamBefore,seamBefore,0F,
                    params.allowedLegPenetration,false);

        int active=0;
        for(Panel p:ring)
        {
            float pressure=0F;
            for(Box box:legs)
                pressure=Math.max(pressure,depth(p,box,params));
            if(pressure>ENTRY_EPS)
            {
                // Smooth activation as the leg approaches the permitted
                // ingress depth. A binary 0->1 contact on one gait frame
                // was one source of 1-2-frame section popping.
                float t=clamp((pressure-ENTRY_EPS)/.038F,0F,1F);
                p.activity=t*t*(3F-2F*t);
                active++;
            }
        }
        if(active==0)
            return new Result(source,baselineWidths,0,0,legs.size(),
                    ignored,before,before,seamBefore,seamBefore,0F,
                    params.allowedLegPenetration,false);

        // Neighbors participate in seams but are NOT given fictitious
        // leg impulses. Activation is a smooth, single-ring quantity.
        float[] influence=new float[N];
        for(int i=0;i<N;i++)influence[i]=ring.get(i).activity;
        for(int i=0;i<N;i++)
            if(ring.get(i).activity>0F)
            {
                influence[(i+1)%N]=Math.max(influence[(i+1)%N],.31F);
                influence[(i+N-1)%N]=Math.max(influence[(i+N-1)%N],.31F);
            }

        float pull=(.025F+.14F*params.restShapePull)*
                (1F+.25F*params.airiness);
        float seamStiffness=.60F-.28F*params.airiness;
        for(int pass=0;pass<ITERATIONS;pass++)
        {
            // Jolt provides the freely swinging target, so the cloth
            // stays airy instead of becoming an independent rigid tube.
            for(int i=0;i<N;i++)
            {
                Panel p=ring.get(i);
                float w=influence[i];
                if(w<=0F)continue;
                toward(p.mid,p.baseMid,pull*w);
                toward(p.hem,p.baseHem,pull*w);
                // Replace hard anti-flare silhouette truncation with a
                // gradually increasing elastic REST SHAPE energy.
                softenOutward(p.mid,p.baseMid,p.outward,
                        SOFT_FLARE_DISTANCE,pull*.65F*w);
                softenOutward(p.hem,p.baseHem,p.outward,
                        SOFT_FLARE_DISTANCE,pull*.45F*w);
            }
            // Contact and adjacent seam/length constraints operate in
            // the SAME projection loop; no independent bone rotation
            // afterward is allowed to pull plates apart.
            for(int i=0;i<N;i++)
            {
                Panel p=ring.get(i);
                if(p.activity<=0F)continue;
                float[] tangent=p.tangent;
                for(Box leg:legs)
                {
                    pushSurface(p.mid,p.halfWidth,tangent,leg,params,
                            .52F*p.activity);
                    pushSurface(p.hem,p.halfWidth,tangent,leg,params,
                            .78F*p.activity);
                    float[] halfway=lerp(p.mid,p.hem,.5F);
                    Contact midway=contact(halfway,leg,params);
                    if(midway!=null)
                    {
                        float dx=Math.min(.026F,midway.depth)*
                                .22F*p.activity;
                        addInPlace(p.mid,mul(midway.normal,dx));
                        addInPlace(p.hem,mul(midway.normal,dx));
                    }
                }
            }
            // Hard length anchors, weak cloth surface neighbors.
            for(int i=0;i<N;i++)
            {
                Panel p=ring.get(i),n=ring.get((i+1)%N);
                float w=influence[i],wn=influence[(i+1)%N];
                if(w>0F)
                {
                    link(p.root,p.mid,dist(p.root,p.restMid),0F,w,.60F*w);
                    link(p.mid,p.hem,dist(p.restMid,p.restHem),
                            w,w,.51F*w);
                }
                if(w<=0F&&wn<=0F)continue;
                // Stretch-only seams: preserve airy ruffles when panels
                // approach, but refuse widening past natural panel gaps.
                float strength=seamStiffness*Math.max(w,wn);
                seam(p.mid,n.mid,dist(p.restMid,n.restMid)+.012F,
                        w,wn,strength);
                seam(p.hem,n.hem,dist(p.restHem,n.restHem)+.017F,
                        w,wn,strength);
                seam(p.mid,n.hem,dist(p.restMid,n.restHem)+.027F,
                        w,wn,strength*.18F);
                seam(n.mid,p.hem,dist(n.restMid,p.restHem)+.027F,
                        wn,w,strength*.18F);
            }
            for(int i=0;i<N;i++)
            {
                Panel p=ring.get(i);
                if(influence[i]<=0F)continue;
                limit(p.mid,p.baseMid,MAX_MID_OFFSET*influence[i]);
                limit(p.hem,p.baseHem,MAX_HEM_OFFSET*influence[i]);
            }
        }

        // Project the connected node graph back into two actual Chameleon
        // local swings. Validate THIS mapped output, not just particles.
        Map<String,float[]> mapped=copy(source);
        int moved=0;
        for(int i=0;i<N;i++)
        {
            Panel p=ring.get(i);
            if(influence[i]<=0F||
                    (dist(p.mid,p.baseMid)<.0025F&&
                     dist(p.hem,p.baseHem)<.0025F))continue;
            float[] upperStart=rotate(p.initialUpper,sub(p.restMid,p.root));
            float[] upperGoal=sub(p.mid,p.root);
            float[] extraUpper=limitedArc(upperStart,upperGoal,
                    MAX_UPPER_CORRECTION*influence[i]);
            float[] upper=multiply(extraUpper,p.initialUpper);
            float[] lowerStart=rotate(upper,rotate(p.initialLower,
                    sub(p.restHem,p.restMid)));
            float[] lowerGoal=sub(p.hem,p.mid);
            float[] extraLower=limitedArc(lowerStart,lowerGoal,
                    MAX_LOWER_CORRECTION*influence[i]);
            float[] lower=multiply(multiply(multiply(
                    conjugate(upper),extraLower),upper),p.initialLower);
            float[] localUpper=toLocal(p.s.upperWorld,upper);
            float[] localLower=toLocal(p.s.lowerWorld,lower);
            if(localUpper==null||localLower==null)return untouched;
            mapped.put(p.s.upper,localUpper);
            mapped.put(p.s.lower,localLower);
            moved++;
        }
        if(moved==0)return untouched;
        float after=assess(ring,mapped,legs,params);
        float seamAfter=assessSeams(ring,mapped);
        // Reject any solution that produces more collision OR obviously
        // opens the seams. No beauty-over-collision fallback stage.
        if(after>before+.002F ||
                seamAfter>Math.max(seamBefore+.075F,.12F))
        {
            mapped=copy(source);
            moved=0;
            after=before;
            seamAfter=seamBefore;
        }
        boolean accepted=moved>0;
        Map<String,SkirtAdaptiveWidth.Width> widths=
                accepted?smoothWidths(ring,safeWidths(strips,mapped)):
                baselineWidths;
        float extraOut=0F;
        if(accepted)
            for(Panel p:ring)
            {
                float[] mid=poseMid(p,mapped);
                float[] hem=poseHem(p,mapped);
                extraOut=Math.max(extraOut,Math.max(
                        dot(sub(mid,p.baseMid),p.outward),
                        dot(sub(hem,p.baseHem),p.outward)));
            }
        return new Result(mapped,widths,active,moved,legs.size(),ignored,
                before,after,seamBefore,seamAfter,extraOut,
                params.allowedLegPenetration,accepted);
    }

    private static Map<String,SkirtAdaptiveWidth.Width> safeWidths(
            List<SkirtClothPoseCoupler.Strip> strips,Map<String,float[]> pose)
    {
        Map<String,SkirtAdaptiveWidth.Width> w=
                SkirtAdaptiveWidth.measure(strips,pose);
        return new LinkedHashMap<String,SkirtAdaptiveWidth.Width>(w);
    }

    /** Smooth width in physical ring order; never shrink a needed panel. */
    private static Map<String,SkirtAdaptiveWidth.Width> smoothWidths(
            List<Panel> ring,Map<String,SkirtAdaptiveWidth.Width> measured)
    {
        Map<String,SkirtAdaptiveWidth.Width> out=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>(measured);
        for(int i=0;i<N;i++)
        {
            Panel p=ring.get(i),left=ring.get((i+N-1)%N),
                    right=ring.get((i+1)%N);
            for(boolean upper:new boolean[]{true,false})
            {
                String key=upper?p.s.upper:p.s.lower;
                SkirtAdaptiveWidth.Width current=measured.get(key);
                if(current==null)continue;
                SkirtAdaptiveWidth.Width a=measured.get(
                        upper?left.s.upper:left.s.lower);
                SkirtAdaptiveWidth.Width b=measured.get(
                        upper?right.s.upper:right.s.lower);
                if(a==null||b==null)continue;
                float boost=Math.max(current.multiplier,
                        .76F*current.multiplier+
                        .12F*(a.multiplier+b.multiplier));
                float cap=upper?SkirtAdaptiveWidth.MAX_UPPER:
                        SkirtAdaptiveWidth.MAX_LOWER;
                out.put(key,new SkirtAdaptiveWidth.Width(current.axis,
                        clamp(boost,1F,cap)));
            }
        }
        return out;
    }

    private static float measureHalfWidth(SkirtClothPoseCoupler.Strip s,
            Panel p)
    {
        // The decoded vertices can be rotated or mirrored in the
        // authored model; an isotropic estimate is safer than a wrong
        // hardcoded global X/Z width axis.
        float max=0F;
        if(s.lowerSurfaceWorld!=null)
            for(float[] v:s.lowerSurfaceWorld)
                if(valid(v,3))
                    max=Math.max(max,dist(v,p.restHem));
        // Only a fraction contributes to the lateral surface footprint:
        // cube surfaces include vertical extents as well.
        return clamp(max>0F?max*.38F:WIDTH_HALF_FALLBACK,.025F,.085F);
    }

    private static void pushSurface(float[] node,float halfWidth,
            float[] tangent,Box leg,Parameters params,float strength)
    {
        Contact best=null;
        for(int k=-1;k<=1;k++)
        {
            Contact c=contact(add(node,mul(tangent,k*halfWidth)),
                    leg,params);
            if(c!=null&&(best==null||c.depth>best.depth))best=c;
        }
        if(best==null)return;
        addInPlace(node,mul(best.normal,
                Math.min(.033F,best.depth)*strength));
    }

    private static float depth(Panel p,Box leg,Parameters params)
    {
        float[] t=p.tangent;
        float maximum=0F;
        for(float[] node:new float[][]{p.baseMid,p.baseHem,
                lerp(p.baseMid,p.baseHem,.5F)})
            for(int side=-1;side<=1;side++)
            {
                Contact c=contact(add(node,mul(t,side*p.halfWidth)),
                        leg,params);
                if(c!=null)maximum=Math.max(maximum,c.depth);
            }
        return maximum;
    }

    /**
     * Allowed ingress is relative to the REAL thigh's thickness, not a
     * percentage of world space. Project horizontally to a nearest
     * rotated OBB side: no radial-only umbrella push and no Y-only exit.
     */
    private static Contact contact(float[] node,Box box,Parameters params)
    {
        float[] d=sub(node,new float[]{box.p[0],box.p[1],box.p[2]});
        float[] local=new float[3];
        for(int i=0;i<3;i++)
        {
            local[i]=dot(d,box.axis[i]);
            if(Math.abs(local[i])>box.p[7+i]+BODY_MARGIN)return null;
        }
        float tolerance=params.allowedLegPenetration*
                2F*Math.min(box.p[7],box.p[9]);
        float best=Float.POSITIVE_INFINITY;
        float[] bestNormal=null;
        for(int k:new int[]{0,2})
        {
            float[] axis=box.axis[k];
            float horizontal=(float)Math.sqrt(
                    axis[0]*axis[0]+axis[2]*axis[2]);
            if(horizontal<.17F)continue;
            float face=(box.p[7+k]+BODY_MARGIN-Math.abs(local[k]))/
                    horizontal;
            if(face<best-.0001F)
            {
                best=face;
                float sign=local[k]>=0F?1F:-1F;
                bestNormal=new float[]{axis[0]*sign/horizontal,0F,
                        axis[2]*sign/horizontal};
            }
        }
        // The closest side is already within the permitted ingress.
        // Never project away from a more distant side instead.
        float excess=best-tolerance;
        return bestNormal==null||excess<=ENTRY_EPS?
                null:new Contact(bestNormal,excess);
    }

    private static float worst(List<Panel> ring,List<Box> legs,Parameters c)
    {
        float best=0F;
        for(Panel p:ring)
            for(Box box:legs)
                best=Math.max(best,depth(p,box,c));
        return best;
    }
    private static float assess(List<Panel> ring,Map<String,float[]> pose,
            List<Box> legs,Parameters c)
    {
        float best=0F;
        for(Panel p:ring)
        {
            float[] mid=poseMid(p,pose),hem=poseHem(p,pose);
            float[] tangent=p.tangent;
            for(Box box:legs)
                for(float[] node:new float[][]{mid,hem,
                        lerp(mid,hem,.5F)})
                    for(int side=-1;side<=1;side++)
                    {
                        Contact found=contact(add(node,mul(tangent,
                                side*p.halfWidth)),box,c);
                        if(found!=null)best=Math.max(best,found.depth);
                    }
        }
        return best;
    }
    private static float[] poseMid(Panel p,Map<String,float[]> pose)
    {
        float[] upper=worldQ(p.s.upperWorld,pose.get(p.s.upper));
        return add(p.root,rotate(upper,sub(p.restMid,p.root)));
    }
    private static float[] poseHem(Panel p,Map<String,float[]> pose)
    {
        float[] upper=worldQ(p.s.upperWorld,pose.get(p.s.upper));
        float[] lower=worldQ(p.s.lowerWorld,pose.get(p.s.lower));
        return add(poseMid(p,pose),rotate(upper,
                rotate(lower,sub(p.restHem,p.restMid))));
    }
    private static float seamError(List<Panel> ring)
    {
        float max=0F;
        for(int i=0;i<N;i++)
        {
            Panel p=ring.get(i),q=ring.get((i+1)%N);
            max=Math.max(max,Math.max(
                    Math.max(0F,dist(p.mid,q.mid)-
                            dist(p.restMid,q.restMid)),
                    Math.max(0F,dist(p.hem,q.hem)-
                            dist(p.restHem,q.restHem))));
        }
        return max;
    }
    private static float assessSeams(List<Panel> ring,
            Map<String,float[]> pose)
    {
        float max=0F;
        for(int i=0;i<N;i++)
        {
            Panel p=ring.get(i),q=ring.get((i+1)%N);
            max=Math.max(max,Math.max(
                    Math.max(0F,dist(poseMid(p,pose),poseMid(q,pose))-
                            dist(p.restMid,q.restMid)),
                    Math.max(0F,dist(poseHem(p,pose),poseHem(q,pose))-
                            dist(p.restHem,q.restHem))));
        }
        return max;
    }

    private static void softenOutward(float[] node,float[] base,
            float[] outward,float slack,float stiffness)
    {
        float radial=dot(sub(node,base),outward);
        if(radial>slack)
            addInPlace(node,mul(outward,-(radial-slack)*stiffness));
    }
    private static void toward(float[] p,float[] base,float k)
    {
        for(int j=0;j<3;j++)p[j]+=(base[j]-p[j])*k;
    }
    private static void link(float[] a,float[] b,float rest,
            float wa,float wb,float strength)
    {
        float current=dist(a,b),sum=wa+wb;
        if(current<1E-7F||sum<1E-7F)return;
        float delta=(current-rest)*strength/current;
        for(int i=0;i<3;i++)
        {
            float v=(b[i]-a[i])*delta;
            a[i]+=v*wa/sum;b[i]-=v*wb/sum;
        }
    }
    private static void seam(float[] a,float[] b,float maximum,
            float wa,float wb,float strength)
    {
        float current=dist(a,b);
        if(current<=maximum)return;
        link(a,b,maximum,wa,wb,strength);
    }
    private static void limit(float[] at,float[] base,float maximum)
    {
        float[] diff=sub(at,base);float d=norm(diff);
        if(d<=maximum||d<1E-7F)return;
        for(int i=0;i<3;i++)at[i]=base[i]+diff[i]*maximum/d;
    }

    private static float[] limitedArc(float[] from,float[] to,float maximum)
    {
        float lenA=norm(from),lenB=norm(to);
        if(lenA<1E-7F||lenB<1E-7F)
            return new float[]{0,0,0,1};
        float[] a=mul(from,1F/lenA),b=mul(to,1F/lenB);
        float dot=clamp(dot(a,b),-1F,1F);
        if(dot>.99999F)return new float[]{0,0,0,1};
        float[] axis=cross(a,b);
        if(norm(axis)<1E-6F)
        {
            axis=cross(a,new float[]{0F,1F,0F});
            if(norm(axis)<1E-6F)axis=cross(a,new float[]{1F,0F,0F});
        }
        axis=mul(axis,1F/norm(axis));
        float angle=Math.min(maximum,(float)Math.acos(dot));
        float h=angle*.5F;
        return new float[]{axis[0]*(float)Math.sin(h),
                axis[1]*(float)Math.sin(h),
                axis[2]*(float)Math.sin(h),(float)Math.cos(h)};
    }
    private static float[] worldQ(float[] m,float[] swing)
    {
        float x=m[0]*swing[0]+m[4]*swing[1]+m[8]*swing[2];
        float y=m[1]*swing[0]+m[5]*swing[1]+m[9]*swing[2];
        float z=m[2]*swing[0]+m[6]*swing[1]+m[10]*swing[2];
        float l=(float)Math.sqrt(x*x+y*y+z*z);
        if(l<1E-8F||Math.abs(swing[3])<1E-8F)
            return new float[]{0,0,0,1};
        float h=swing[3]*.5F,scale=(float)Math.sin(h)/l;
        return normalizedQ(new float[]{x*scale,y*scale,z*scale,
                (float)Math.cos(h)});
    }
    private static float[] toLocal(float[] m,float[] qInput)
    {
        float[] q=normalizedQ(qInput);
        if(q[3]<0F)for(int k=0;k<4;k++)q[k]=-q[k];
        float v=norm(new float[]{q[0],q[1],q[2]});
        if(v<1E-7F)return new float[]{1,0,0,0};
        float theta=2F*(float)Math.atan2(v,q[3]);
        float wx=q[0]/v,wy=q[1]/v,wz=q[2]/v;
        float a=m[0],b=m[4],c=m[8],
                d=m[1],e=m[5],f=m[9],
                g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=c*h-b*i,C=b*f-c*e;
        float D=f*g-d*i,E=a*i-c*g,F=c*d-a*f;
        float G=d*h-e*g,H=b*g-a*h,I=a*e-b*d;
        float det=a*A+b*D+c*G;
        if(!Float.isFinite(det)||Math.abs(det)<1E-8F)return null;
        float x=(A*wx+B*wy+C*wz)/det;
        float y=(D*wx+E*wy+F*wz)/det;
        float z=(G*wx+H*wy+I*wz)/det;
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        return n<1E-8F||!Float.isFinite(n)?null:
                new float[]{x/n,y/n,z/n,theta};
    }
    private static float[] normalizedQ(float[] q)
    {
        float l=norm(q);return l<1E-8F?
                new float[]{0F,0F,0F,1F}:mul(q,1F/l);
    }
    private static float[] multiply(float[] a,float[] b)
    {
        return normalizedQ(new float[]{
                a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
                a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
                a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
                a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]});
    }
    private static float[] conjugate(float[] q)
    {return new float[]{-q[0],-q[1],-q[2],q[3]};}
    private static float[] rotate(float[] q,float[] v)
    {
        float[] r={q[0],q[1],q[2]};
        float[] cross=cross(r,v),cross2=cross(r,cross);
        return add(v,add(mul(cross,2F*q[3]),mul(cross2,2F)));
    }
    private static float[] radial(float[] r,float cx,float cz)
    {
        float x=r[0]-cx,z=r[2]-cz;
        float l=(float)Math.sqrt(x*x+z*z);
        return l<.01F?new float[]{1,0,0}:
                new float[]{x/l,0,z/l};
    }
    private static float[] origin(float[] m)
    {return new float[]{m[12],m[13],m[14]};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return add(a,mul(sub(b,a),t));}
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static void addInPlace(float[] a,float[] b)
    {for(int i=0;i<3;i++)a[i]+=b[i];}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] mul(float[] a,float scale)
    {
        float[] out=new float[a.length];
        for(int k=0;k<a.length;k++)out[k]=a[k]*scale;
        return out;
    }
    private static float[] cross(float[] a,float[] b)
    {return new float[]{a[1]*b[2]-a[2]*b[1],
            a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float norm(float[] a)
    {
        float value=0F;
        for(float component:a)value+=component*component;
        return (float)Math.sqrt(value);
    }
    private static float dist(float[] a,float[] b)
    {return norm(sub(a,b));}
    private static float clamp(float v,float a,float b)
    {return Math.max(a,Math.min(b,v));}
    private static boolean valid(float[] a,int count)
    {
        if(a==null||a.length!=count)return false;
        for(float f:a)if(!Float.isFinite(f))return false;
        return true;
    }
    private static Map<String,float[]> copy(Map<String,float[]> raw)
    {
        Map<String,float[]> out=new LinkedHashMap<String,float[]>();
        if(raw!=null)
            for(Map.Entry<String,float[]> e:raw.entrySet())
                if(e.getKey()!=null&&e.getValue()!=null)
                    out.put(e.getKey(),e.getValue().clone());
        return out;
    }
}
