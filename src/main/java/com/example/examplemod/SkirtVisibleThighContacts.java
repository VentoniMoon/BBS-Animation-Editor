package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview-only clearance for actual skirt panel SURFACES against actual
 * Emoticons leg geometry. Jolt solves the central 2-bone chains; broad flat
 * panels reach farther than those tiny central native capsules. In particular,
 * a 3D leg collider can be installed yet pass through the visible hem.
 *
 * Uses the same captured world-space fitted OBBs passed into native Jolt.
 * Rotates ONLY skirt panel bones around their animated waist anchors.
 * Does not move the actor, change animation keys, touch the native Jolt world,
 * invent missing colliders, or alter hair/ragdoll physics.
 */
public final class SkirtVisibleThighContacts
{
    private static final int PANELS=20;
    private static final float WIDTH=.0625F; // actual 2px wide panels / 16
    private static final float SURFACE_MARGIN=.023F;
    /*
     * Previous correction repeatedly rotated ROOTS by up to 0.95 rad
     * and propagated to neighbors. Both legs near the standing waist
     * could open all 20 panels into an inverted umbrella.
     *
     * Keep the waist nearly fixed; allow the hem to give locally.
     */
    private static final float MAX_EXTRA_ROOT=.095F;
    private static final float MAX_EXTRA_HEM=.28F;
    private static final float MAX_FINAL_ROOT=.52F;
    private static final float MAX_FINAL_HEM=.60F;
    private static final float NEIGHBOR_RESPONSE=.16F;
    /* Skip neutral hips only if the entire oriented leg volume sits
     * well inside the waist, never based on the bone CENTER alone. */
    private static final float LEG_OUTWARD_THRESHOLD=.82F;

    public static final class Result
    {
        public final Map<String,float[]> swings;
        public final int touchedPanels;
        public final float peakOverlap;
        /** Actual decoded Chameleon cube surface samples vs contact hits. */
        public final int exactSurfaceSamples,exactSurfaceHits;
        /** Unlike exactSurfaceHits, each source vertex contributes once. */
        public final int uniqueSurfaceHits;
        public final int availableLegBoxes,activeLegBoxes;
        /** Real matched collider identities and actor-relative geometry. */
        public final String colliderSummary;
        public final String hitPanelNames;

        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap)
        {
            this(swings,touchedPanels,peakOverlap,0,0,0,0,0,"","");
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    0,0,0,"","");
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,int uniqueHits,
                int availableLegs,int activeLegs,String summary,String hitPanels)
        {
            this.swings=swings;
            this.touchedPanels=touchedPanels;
            this.peakOverlap=peakOverlap;
            exactSurfaceSamples=exactSamples;
            exactSurfaceHits=exactHits;
            uniqueSurfaceHits=uniqueHits;
            availableLegBoxes=availableLegs;
            activeLegBoxes=activeLegs;
            colliderSummary=summary;
            hitPanelNames=hitPanels;
        }
    }

    private static final class Leg
    {
        final float[] p;
        final String owner;
        final float[][] basis=new float[3][];
        Leg(float[] p,String owner)
        {
            this.p=p;
            this.owner=owner;
            float[] q={p[3],p[4],p[5],p[6]};
            basis[0]=rotateQuaternion(new float[]{1,0,0},q);
            basis[1]=rotateQuaternion(new float[]{0,1,0},q);
            basis[2]=rotateQuaternion(new float[]{0,0,1},q);
        }
    }

    private static final class Point
    {
        final float[] p,axis;
        Point(float[] p,float[] axis){this.p=p;this.axis=axis;}
    }

    private SkirtVisibleThighContacts(){}

    public static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected)
    {
        Map<String,float[]> copy=cloneSwings(starting);
        if(strips==null||strips.size()!=PANELS||selected==null||
                selected.boxes==null||selected.owners==null)
            return new Result(copy,0,0F);
        List<Leg> legs=new ArrayList<Leg>();
        for(int i=0;i<Math.min(selected.boxes.length,selected.owners.size());i++)
        {
            if(!BodyPartJoltContactBudget.isLeg(selected.owners.get(i)))continue;
            float[] box=selected.boxes[i];
            if(valid(box,10) && box[7]>.01F && box[8]>.01F && box[9]>.01F)
                legs.add(new Leg(box,selected.owners.get(i)));
        }
        if(legs.isEmpty())return new Result(copy,0,0F);
        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null||s.tipWorld==null||!valid(s.tipWorld,3)||
                    !valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(copy.get(s.upper),4)||!valid(copy.get(s.lower),4)||
                    SkirtAdaptiveWidth.widthAxis(s.lower)<0)
                return new Result(copy,0,0F);
            cx+=s.upperWorld[12];
            cz+=s.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;

        // A stationary thigh lives UNDER the waist. Its approximate hull
        // can intersect the inner cloth surface even in a neutral stance.
        // A lifted thigh can extend THROUGH the skirt while its BONE CENTER
        // remains near the pelvis: use the entire fitted collider envelope
        // rather than the center-only test. Only then run actual OBB overlap
        // against the lower hem sample points.
        float waistRadius=0F;
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            float[] root=origin(strip.upperWorld);
            float x=root[0]-cx,z=root[2]-cz;
            waistRadius+=(float)Math.sqrt(x*x+z*z);
        }
        waistRadius/=PANELS;
        List<Leg> active=new ArrayList<Leg>();
        for(Leg leg:legs)
        {
            float x=leg.p[0]-cx,z=leg.p[2]-cz;
            float distance=(float)Math.sqrt(x*x+z*z);
            // Project all three oriented half-extents onto the outward
            // horizontal direction, not only the collider center. This
            // handles rotated thighs whose broad corner reaches the skirt.
            float[] radial=distance>.0001F?
                    new float[]{x/distance,0F,z/distance}:
                    new float[]{1F,0F,0F};
            float support=0F;
            for(int axis=0;axis<3;axis++)
                support+=Math.abs(dot(radial,leg.basis[axis]))*
                        leg.p[7+axis];
            // A broad but VERTICAL neutral thigh can graze the short
            // skirt edge. Do not apply a resting outward force all around
            // the waist; require either outward-moving center or a thigh
            // whose longest fitted axis visibly tilts off vertical.
            int longest=0;
            if(leg.p[8]>leg.p[7])longest=1;
            if(leg.p[9]>leg.p[7+longest])longest=2;
            float tilted=(float)Math.sqrt(
                    leg.basis[longest][0]*leg.basis[longest][0]+
                    leg.basis[longest][2]*leg.basis[longest][2]);
            boolean raised=tilted>=.30F;
            boolean movedOut=distance>=waistRadius*.68F;
            if(distance+support>=
                    Math.max(.09F,waistRadius*LEG_OUTWARD_THRESHOLD) &&
                    (raised||movedOut))
                active.add(leg);
        }
        if(active.isEmpty())return new Result(copy,0,0F);

        boolean[] touched=new boolean[PANELS];
        float peak=0F;
        int exactSampleCount=0,exactHitCount=0,uniqueExactHitCount=0;
        float[] need=new float[PANELS];
        java.util.Map<String,Integer> legHits=
                new LinkedHashMap<String,Integer>();
        StringBuilder panelHits=new StringBuilder();
        // Evaluate ONLY lower-fabric contact. Upper samples near the waist
        // used to treat the resting hips as a penetration and push outward
        // the entire skirt. A single pass prevents repeated amplifying
        // corrections from the same stationary overlapping collider.
        for(int i=0;i<PANELS;i++)
        {
            SkirtClothPoseCoupler.Strip strip=strips.get(i);
            float[] root=origin(strip.upperWorld);
            float rx=root[0]-cx,rz=root[2]-cz;
            float rr=(float)Math.sqrt(rx*rx+rz*rz);
            if(rr<.02F)continue;
            float[] outward={rx/rr,0F,rz/rr};
            float[] hinge=origin(strip.lowerWorld);
            float[] upperSwing=worldSwing(strip.upperWorld,
                    copy.get(strip.upper));
            float[] lowerSwing=worldSwing(strip.lowerWorld,
                    copy.get(strip.lower));
            float[] upper=rotate(sub(hinge,root),upperSwing);
            float[] lower=rotate(rotate(sub(strip.tipWorld,hinge),
                    lowerSwing),upperSwing);
            float[] pivot=add(root,upper);
            float[] hemAxis=rotate(rotate(
                    widthAxisWorld(strip.lowerWorld,strip.lower),
                    lowerSwing),upperSwing);
            // Contact the actual rotated front/side/diagonal cube surfaces.
            // A hardcoded 2px bone-centered strip misses 45-degree corner
            // cuboids and their face centers (including under Weld). Ignore
            // the top 28% around the fitted waist, which must stay pinned.
            List<Point> samples=new ArrayList<Point>();
            exactSampleCount+=addExactSurface(samples,
                    strip.upperSurfaceWorld,root,hinge,
                    strip.tipWorld,upperSwing,lowerSwing,false);
            exactSampleCount+=addExactSurface(samples,
                    strip.lowerSurfaceWorld,root,hinge,
                    strip.tipWorld,upperSwing,lowerSwing,true);
            // Keep the original thin centerline fallback for cubes without
            // a readable Chameleon surface or missing/hidden face quads.
            samples.add(new Point(add(pivot,mul(lower,.55F)),hemAxis));
            samples.add(new Point(add(pivot,mul(lower,.80F)),hemAxis));
            samples.add(new Point(add(pivot,lower),hemAxis));
            float desired=0F;
            boolean exactHitPanel=false;
            for(Point sample:samples)
            {
                boolean exact=sample.axis==null;
                boolean uniqueHit=false;
                float[] side=exact?new float[]{0F,0F,0F}:
                        mul(normalize(sample.axis),WIDTH);
                int edgeLimit=exact?0:1;
                for(int edge=-edgeLimit;edge<=edgeLimit;edge++)
                {
                    float[] point=add(sample.p,mul(side,edge));
                    for(Leg leg:active)
                    {
                        float overlap=outwardPenetration(point,leg,outward);
                        if(overlap>0F)
                        {
                            if(exact)
                            {
                                exactHitCount++;
                                uniqueHit=true;
                                exactHitPanel=true;
                                Integer prev=legHits.get(leg.owner);
                                legHits.put(leg.owner,prev==null?1:prev+1);
                            }
                            peak=Math.max(peak,overlap);
                            float distance=Math.max(.14F,
                                    length(sub(sample.p,pivot)));
                            desired=Math.max(desired,
                                    (float)Math.atan(overlap/distance));
                        }
                    }
                }
                if(exact && uniqueHit)uniqueExactHitCount++;
            }
            if(exactHitPanel)
            {
                if(panelHits.length()>0)panelHits.append(',');
                panelHits.append(strip.upper);
            }
            need[i]=Math.min(.34F,desired);
        }

        for(int i=0;i<PANELS;i++)
        {
            // A subtle neighboring response avoids disconnected cardboard
            // panels but does not transmit thigh impact around the waist.
            float contact=Math.max(need[i],Math.max(
                    need[(i+1)%PANELS]*NEIGHBOR_RESPONSE,
                    need[(i+PANELS-1)%PANELS]*NEIGHBOR_RESPONSE));
            if(contact<.012F)continue;
            SkirtClothPoseCoupler.Strip strip=strips.get(i);
            float[] root=origin(strip.upperWorld);
            float rx=root[0]-cx,rz=root[2]-cz;
            float rr=(float)Math.sqrt(rx*rx+rz*rz);
            if(rr<.02F)continue;
            float[] tangent={-rz/rr,0F,rx/rr};

            // Preserve the natural waist silhouette: <6 degrees extra at
            // the root; the lower link bends by up to 16 degrees instead.
            float upperStep=Math.min(MAX_EXTRA_ROOT,contact*.28F);
            float lowerStep=Math.min(MAX_EXTRA_HEM,contact*.85F);
            boolean changed=false;
            changed|=bend(copy,strip.upper,strip.upperWorld,tangent,
                    upperStep,MAX_FINAL_ROOT);
            changed|=bend(copy,strip.lower,strip.lowerWorld,tangent,
                    lowerStep,MAX_FINAL_HEM);
            touched[i]=changed;
        }
        int total=0;
        for(boolean value:touched)if(value)total++;
        return new Result(copy,total,peak,exactSampleCount,exactHitCount,
                uniqueExactHitCount,legs.size(),active.size(),
                contactSummary(active,cx,cz,legHits),panelHits.toString());
    }

    /**
     * Transform decoded CLEAN quad surface vertices by the same two
     * world-space native bone swings as the Chameleon panel. Upper bone
     * surfaces follow its root swing; lower bone surfaces additionally
     * follow the lower swing around the animated hinge. This avoids
     * treating cube geometry as if it were aligned to the bone axis.
     */
    private static int addExactSurface(List<Point> out,float[][] source,
            float[] root,float[] hinge,float[] tip,
            float[] upperSwing,float[] lowerSwing,boolean lower)
    {
        if(source==null||source.length==0||source.length>128)return 0;
        float[] down=sub(tip,root);
        float length=length(down);
        if(length<.05F)return 0;
        float[] direction=mul(down,1F/length);
        int n=0;
        for(float[] p:source)
        {
            if(!valid(p,3))continue;
            float fraction=dot(sub(p,root),direction)/length;
            if(fraction<.28F || fraction>1.45F)continue;
            float[] world;
            if(lower)
            {
                float[] inner=rotate(sub(p,hinge),lowerSwing);
                world=add(root,rotate(add(sub(hinge,root),inner),
                        upperSwing));
            }
            else world=add(root,rotate(sub(p,root),upperSwing));
            if(!valid(world,3))continue;
            out.add(new Point(world,null));
            n++;
        }
        return n;
    }

    /** Compact, actor-relative diagnostic for deciding whether the fitted
     * leg contact is implausibly large or intersects most skirt panels.
     * Only up to 8 actual active hulls are printed; no new colliders.
     */
    private static String contactSummary(List<Leg> legs,float cx,float cz,
            java.util.Map<String,Integer> hits)
    {
        StringBuilder builder=new StringBuilder();
        int count=Math.min(8,legs.size());
        for(int i=0;i<count;i++)
        {
            Leg leg=legs.get(i);
            if(i>0)builder.append(';');
            builder.append(leg.owner)
                    .append("(dx=").append(round(leg.p[0]-cx))
                    .append(",dz=").append(round(leg.p[2]-cz))
                    .append(",half=").append(round(leg.p[7]))
                    .append('/').append(round(leg.p[8]))
                    .append('/').append(round(leg.p[9]))
                    .append(",hits=").append(
                            hits.containsKey(leg.owner)?hits.get(leg.owner):0)
                    .append(')');
        }
        if(legs.size()>count)
            builder.append(";plus=").append(legs.size()-count);
        return builder.toString();
    }

    private static float round(float x)
    {
        return Math.round(x*1000F)/1000F;
    }

    private static boolean bend(Map<String,float[]> swings,String id,
            float[] matrix,float[] tangent,float amount,float maximum)
    {
        if(amount<.003F)return false;
        float[] current=worldSwing(matrix,swings.get(id));
        float[] after=compose(new float[]{
                tangent[0],tangent[1],tangent[2],amount},current);
        if(after[3]>maximum)
        {
            // Use the remaining safe outward bend, not a full skip that
            // would leave an apparently random isolated unresponsive panel.
            float available=Math.max(0F,maximum-current[3]);
            if(available<.003F)return false;
            after=compose(new float[]{
                    tangent[0],tangent[1],tangent[2],
                    Math.min(available,amount)},current);
            if(after[3]>maximum+.0001F)return false;
        }
        float[] local=localSwing(matrix,after);
        if(!valid(local,4))return false;
        swings.put(id,local);
        return true;
    }

    /** A point must actually overlap the leg's FULL box. If it does,
     * request just enough *outward* motion to clear the far radial face. */
    private static float outwardPenetration(float[] point,Leg leg,float[] radial)
    {
        float[] d=sub(point,new float[]{leg.p[0],leg.p[1],leg.p[2]});
        for(int k=0;k<3;k++)
            if(Math.abs(dot(d,leg.basis[k]))>leg.p[7+k]+SURFACE_MARGIN)
                return 0F;
        float support=SURFACE_MARGIN;
        for(int k=0;k<3;k++)
            support+=Math.abs(dot(radial,leg.basis[k]))*leg.p[7+k];
        return Math.max(0F,Math.min(.25F,support-dot(d,radial)));
    }

    private static float[] worldSwing(float[] m,float[] aa)
    {
        float x=m[0]*aa[0]+m[4]*aa[1]+m[8]*aa[2];
        float y=m[1]*aa[0]+m[5]*aa[1]+m[9]*aa[2];
        float z=m[2]*aa[0]+m[6]*aa[1]+m[10]*aa[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,aa[3]};
    }

    private static float[] localSwing(float[] m,float[] world)
    {
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Float.isFinite(det)||Math.abs(det)<1E-7F)return null;
        float inv=1F/det;
        float x=((e*i-f*h)*world[0]+(c*h-b*i)*world[1]+(b*f-c*e)*world[2])*inv;
        float y=((f*g-d*i)*world[0]+(a*i-c*g)*world[1]+(c*d-a*f)*world[2])*inv;
        float z=((d*h-e*g)*world[0]+(b*g-a*h)*world[1]+(a*e-b*d)*world[2])*inv;
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return null;
        return new float[]{x/n,y/n,z/n,world[3]};
    }

    private static float[] widthAxisWorld(float[] m,String bone)
    {
        int axis=SkirtAdaptiveWidth.widthAxis(bone);
        return axis==0?new float[]{m[0],m[1],m[2]}:
                new float[]{m[8],m[9],m[10]};
    }

    private static float[] compose(float[] extra,float[] original)
    {
        float[] a=quaternion(extra),b=quaternion(original);
        float x=a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1];
        float y=a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0];
        float z=a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3];
        float w=a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,2F*(float)Math.atan2(n,Math.abs(w))};
    }

    private static float[] quaternion(float[] aa)
    {
        float h=aa[3]*.5F;
        float sin=(float)Math.sin(h),cos=(float)Math.cos(h);
        return new float[]{aa[0]*sin,aa[1]*sin,aa[2]*sin,cos};
    }

    private static float[] rotate(float[] vec,float[] aa)
    {
        float[] q=quaternion(aa);
        return rotateQuaternion(vec,q);
    }

    private static float[] rotateQuaternion(float[] v,float[] q)
    {
        float x=q[0],y=q[1],z=q[2],w=q[3];
        float tx=2F*(y*v[2]-z*v[1]);
        float ty=2F*(z*v[0]-x*v[2]);
        float tz=2F*(x*v[1]-y*v[0]);
        return new float[]{v[0]+w*tx+y*tz-z*ty,
                v[1]+w*ty+z*tx-x*tz,
                v[2]+w*tz+x*ty-y*tx};
    }

    private static float[] origin(float[] m)
    {return new float[]{m[12],m[13],m[14]};}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static float[] mul(float[] a,float s)
    {return new float[]{a[0]*s,a[1]*s,a[2]*s};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return add(a,mul(sub(b,a),t));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float length(float[] a)
    {return (float)Math.sqrt(dot(a,a));}
    private static float[] normalize(float[] a)
    {float n=length(a);return n<1E-7F?new float[]{0,0,0}:mul(a,1F/n);}
    private static boolean valid(float[] a,int size)
    {
        if(a==null||a.length!=size)return false;
        for(float x:a)if(!Float.isFinite(x))return false;
        return true;
    }
    private static Map<String,float[]> cloneSwings(Map<String,float[]> source)
    {
        Map<String,float[]> output=new LinkedHashMap<String,float[]>();
        if(source!=null)
            for(Map.Entry<String,float[]> e:source.entrySet())
                if(e.getKey()!=null&&e.getValue()!=null)
                    output.put(e.getKey(),e.getValue().clone());
        return output;
    }
}
