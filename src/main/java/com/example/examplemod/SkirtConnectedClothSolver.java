package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connected render-pose cloth projection for the exact Chameleon skirt
 * with 20 vertical strips x 2 dynamic links. This replaces the idea that
 * 20 INDEPENDENT Jolt hair rigs plus visual Weld are a unified garment.
 *
 * Native Jolt remains responsible for temporal inertia and the initial
 * unconstrained target. A shared position-based grid then solves the
 * 40 moving link endpoints with 20 pinned belt anchors, lateral/diagonal
 * constraints and contact of broad hem/hip proxies against the REAL
 * animated Emoticons leg OBBs. Finally it maps the corrected links to
 * two Chameleon local axis-angle swings per strip.
 *
 * All corrections are bounded and deterministic for a given source frame.
 * This is a prototype for a connected *rig*, not fully meshed cloth; the
 * real visual cube corners may still clip at extreme poses.
 */
public final class SkirtConnectedClothSolver
{
    private static final int COUNT=20;
    // The source Jolt pose is the shape reference. Connected constraints
    // must ONLY correct a local confirmed thigh contact, never overwrite
    // ordinary gravity and walking motion around the whole ring.
    private static final int STEPS=5;
    private static final float SEAM_STIFFNESS=.28F;
    private static final float LENGTH_STIFFNESS=.50F;
    private static final float MAX_MID_SHIFT=.075F;
    private static final float MAX_HEM_SHIFT=.13F;
    private static final float MAX_UPPER_ROT=.105F;
    private static final float MAX_LOWER_ROT=.24F;
    private static final float MIN_CONTACT=.008F;
    private static final float NEIGHBOR_ACTIVITY=.22F;
    private static final float CLOTH_MARGIN=.042F;
    private static final float MIN_LEG_HALF=.018F;
    private static final float MAX_LEG_HALF=.48F;

    public static final class Result
    {
        public final Map<String,float[]> swings;
        public final int adjustedPanels,colliderCount,ignoredColliders;
        public final float maxContactBefore,maxContactAfter,largestSeamStretch;

        Result(Map<String,float[]> swings,int adjusted,int contacts,
                int ignored,float before,float after,float seam)
        {
            this.swings=swings;
            adjustedPanels=adjusted;
            colliderCount=contacts;
            ignoredColliders=ignored;
            maxContactBefore=before;
            maxContactAfter=after;
            largestSeamStretch=seam;
        }
    }

    private static final class Panel
    {
        SkirtClothPoseCoupler.Strip strip;
        float[] root,restMid,restHem;
        float[] mid,hem,baseMid,baseHem;
        float[] upperQ,lowerQ;
        double angle;
    }

    private static final class BodyBox
    {
        final float[] p;
        final float[][] axes=new float[3][];
        BodyBox(float[] source)
        {
            p=source.clone();
            float[] q={p[3],p[4],p[5],p[6]};
            float length=norm4(q);
            if(length<1E-6F)
                throw new IllegalArgumentException("Missing body box quaternion");
            for(int i=0;i<4;i++)q[i]/=length;
            axes[0]=rotate(q,new float[]{1,0,0});
            axes[1]=rotate(q,new float[]{0,1,0});
            axes[2]=rotate(q,new float[]{0,0,1});
        }
    }

    private SkirtConnectedClothSolver(){}

    public static Result solve(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> physical,
            BodyPartJoltContactBudget.Selection contacts)
    {
        Map<String,float[]> fallback=copy(physical);
        Result unmodified=new Result(fallback,0,0,0,0F,0F,0F);
        if(strips==null || strips.size()!=COUNT || physical==null ||
                physical.size()!=COUNT*2)return unmodified;
        List<Panel> ring=new ArrayList<Panel>();
        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null||!valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(s.tipWorld,3)||!valid(physical.get(s.upper),4)||
                    !valid(physical.get(s.lower),4))return unmodified;
            Panel p=new Panel();
            p.strip=s;
            p.root=origin(s.upperWorld);
            p.restMid=origin(s.lowerWorld);
            p.restHem=s.tipWorld.clone();
            if(length(sub(p.restMid,p.root))<.045F ||
                    length(sub(p.restHem,p.restMid))<.04F)
                return unmodified;
            p.upperQ=worldQ(s.upperWorld,physical.get(s.upper));
            p.lowerQ=worldQ(s.lowerWorld,physical.get(s.lower));
            p.mid=add(p.root,rotate(p.upperQ,sub(p.restMid,p.root)));
            p.hem=add(p.mid,rotate(p.upperQ,
                    rotate(p.lowerQ,sub(p.restHem,p.restMid))));
            p.baseMid=p.mid.clone();
            p.baseHem=p.hem.clone();
            cx+=p.root[0];cz+=p.root[2];
            ring.add(p);
        }
        cx/=COUNT;cz/=COUNT;
        final float centerX=cx,centerZ=cz;
        for(Panel p:ring)
            p.angle=Math.atan2(p.root[2]-cz,p.root[0]-cx);
        Collections.sort(ring,new Comparator<Panel>()
        {
            @Override public int compare(Panel a,Panel b)
            {
                int result=Double.compare(a.angle,b.angle);
                return result!=0?result:a.strip.upper.compareTo(b.strip.upper);
            }
        });

        List<BodyBox> legs=new ArrayList<BodyBox>();
        int ignored=0;
        if(contacts!=null && contacts.boxes!=null && contacts.owners!=null)
            for(int i=0;i<Math.min(contacts.boxes.length,
                    contacts.owners.size());i++)
            {
                if(!BodyPartJoltContactBudget.isLeg(contacts.owners.get(i)))
                    continue;
                float[] box=contacts.boxes[i];
                if(!valid(box,10) || box[7]<MIN_LEG_HALF ||
                        box[8]<MIN_LEG_HALF||box[9]<MIN_LEG_HALF||
                        box[7]>MAX_LEG_HALF||box[8]>MAX_LEG_HALF||
                        box[9]>MAX_LEG_HALF)
                {
                    ignored++;
                    continue;
                }
                try
                {
                    BodyBox leg=new BodyBox(box);
                    // A contact proxy covering almost the ENTIRE skirt
                    // cannot be a useful local thigh contact. In previous
                    // logs all 20 panels were hit and the skirt exploded.
                    int hits=0;
                    for(Panel p:ring)
                        if(contactShift(p.mid,leg,radial(p.root,cx,cz))>.001F ||
                                contactShift(p.hem,leg,radial(p.root,cx,cz))>.001F)
                            hits++;
                    if(hits>=COUNT*3/4)ignored++;
                    else legs.add(leg);
                }
                catch(IllegalArgumentException invalid){ignored++;}
            }

        float before=maxOverlap(ring,legs,cx,cz);
        // The prior implementation processed nine full-ring constraint
        // iterations even when NOTHING intersected a thigh. That forced
        // the reference skirt into a nearly rigid tube in ordinary poses.
        // In the absence of a real overlap, return EXACT native pose.
        float[] activity=new float[COUNT];
        boolean anyContact=false;
        for(int i=0;i<COUNT;i++)
        {
            Panel p=ring.get(i);
            float[] outward=radial(p.root,cx,cz);
            float overlap=0F;
            for(BodyBox leg:legs)
            {
                overlap=Math.max(overlap,contactShift(p.mid,leg,outward));
                overlap=Math.max(overlap,contactShift(p.hem,leg,outward));
                overlap=Math.max(overlap,contactShift(
                        lerp(p.mid,p.hem,.5F),leg,outward));
            }
            if(overlap>MIN_CONTACT)
            {
                activity[i]=1F;
                anyContact=true;
            }
        }
        if(!anyContact)
            return new Result(fallback,0,legs.size(),ignored,
                    before,before,0F);

        // Spread a small part of the response to immediate neighbours,
        // but keep every other panel pinned to its existing Jolt pose.
        float[] localActivity=activity.clone();
        for(int i=0;i<COUNT;i++)
            if(activity[i]>=1F)
            {
                localActivity[(i+1)%COUNT]=Math.max(
                        localActivity[(i+1)%COUNT],NEIGHBOR_ACTIVITY);
                localActivity[(i+COUNT-1)%COUNT]=Math.max(
                        localActivity[(i+COUNT-1)%COUNT],NEIGHBOR_ACTIVITY);
            }

        // Local connected cloth projection ONLY near actual contacts.
        // Global ring constraints remain represented by the separate
        // visual Weld and native skirt coupler on the original Jolt pose.
        for(int step=0;step<STEPS;step++)
        {
            for(int i=0;i<COUNT;i++)
            {
                Panel p=ring.get(i),n=ring.get((i+1)%COUNT);
                float w=localActivity[i],wn=localActivity[(i+1)%COUNT];
                if(w>0F)
                {
                    project(p.root,p.mid,
                            distance(p.root,p.restMid),0F,w,
                            LENGTH_STIFFNESS*w);
                    project(p.mid,p.hem,
                            distance(p.restMid,p.restHem),w,w,
                            LENGTH_STIFFNESS*w);
                }
                if(w>0F||wn>0F)
                {
                    project(p.mid,n.mid,
                            distance(p.restMid,n.restMid),w,wn,
                            SEAM_STIFFNESS*Math.max(w,wn));
                    project(p.hem,n.hem,
                            distance(p.restHem,n.restHem),w,wn,
                            SEAM_STIFFNESS*Math.max(w,wn));
                    project(p.mid,n.hem,
                            distance(p.restMid,n.restHem),w,wn,
                            SEAM_STIFFNESS*.25F*Math.max(w,wn));
                    project(n.mid,p.hem,
                            distance(n.restMid,p.restHem),wn,w,
                            SEAM_STIFFNESS*.25F*Math.max(w,wn));
                }
            }
            for(int i=0;i<COUNT;i++)
            {
                Panel p=ring.get(i);
                float weight=localActivity[i];
                if(weight<=0F)continue;
                if(activity[i]>=1F)
                {
                    float[] direction=radial(p.root,cx,cz);
                    for(BodyBox leg:legs)
                    {
                        pushNode(p.mid,leg,direction,.55F);
                        pushNode(p.hem,leg,direction,.85F);
                        pushLink(p.mid,p.hem,leg,direction);
                        pushLink(p.root,p.mid,leg,direction);
                    }
                }
                limit(p.mid,p.baseMid,MAX_MID_SHIFT*weight);
                limit(p.hem,p.baseHem,MAX_HEM_SHIFT*weight);
            }
        }

        int adjusted=0;
        Map<String,float[]> out=copy(physical);
        for(int panelIndex=0;panelIndex<COUNT;panelIndex++)
        {
            Panel p=ring.get(panelIndex);
            float weight=localActivity[panelIndex];
            if(weight<=0F)continue;
            if(distance(p.mid,p.baseMid)<.003F &&
                    distance(p.hem,p.baseHem)<.003F)continue;
            float[] desiredUpper=sub(p.mid,p.root);
            float[] currentUpper=rotate(p.upperQ,sub(p.restMid,p.root));
            float[] correction=limitAngle(arc(currentUpper,desiredUpper),
                    MAX_UPPER_ROT*weight);
            float[] upperNew=unitQ(multiply(correction,p.upperQ));
            float[] desiredLower=sub(p.hem,p.mid);
            float[] currentLower=rotate(upperNew,
                    rotate(p.lowerQ,sub(p.restHem,p.restMid)));
            float[] lowerCorrection=limitAngle(arc(currentLower,desiredLower),
                    MAX_LOWER_ROT*weight);
            float[] lowerNew=unitQ(multiply(multiply(
                    multiply(conjugate(upperNew),lowerCorrection),upperNew),
                    p.lowerQ));

            float[] upperSwing=toLocalSwing(p.strip.upperWorld,upperNew);
            float[] lowerSwing=toLocalSwing(p.strip.lowerWorld,lowerNew);
            if(upperSwing==null||lowerSwing==null)
                return unmodified;
            out.put(p.strip.upper,upperSwing);
            out.put(p.strip.lower,lowerSwing);
            if(distance(p.mid,p.baseMid)>.008F||
                    distance(p.hem,p.baseHem)>.008F)adjusted++;
        }
        float largest=0F;
        for(int i=0;i<COUNT;i++)
        {
            Panel p=ring.get(i),n=ring.get((i+1)%COUNT);
            float rest=distance(p.restHem,n.restHem);
            largest=Math.max(largest,Math.abs(distance(p.hem,n.hem)-rest));
        }
        // Measure the ACTUAL returned Chameleon rotations, not the
        // temporary particle positions before angular correction limits.
        // A correct constraint pose may still leave visual penetration
        // when the 2-bone rig cannot reach the projected cloth nodes.
        for(Panel p:ring)
        {
            float[] upper=worldQ(p.strip.upperWorld,
                    out.get(p.strip.upper));
            float[] lower=worldQ(p.strip.lowerWorld,
                    out.get(p.strip.lower));
            p.mid=add(p.root,rotate(upper,sub(p.restMid,p.root)));
            p.hem=add(p.mid,rotate(upper,rotate(lower,
                    sub(p.restHem,p.restMid))));
        }
        return new Result(out,adjusted,legs.size(),ignored,
                before,maxOverlap(ring,legs,cx,cz),largest);
    }

    private static void project(float[] a,float[] b,float rest,
            float weightA,float weightB,float strength)
    {
        float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        float actual=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        float sum=weightA+weightB;
        if(actual<1E-6F||sum<1E-6F)return;
        float delta=(actual-rest)*strength/actual;
        float fa=delta*weightA/sum,fb=delta*weightB/sum;
        for(int k=0;k<3;k++)
        {
            float d=k==0?dx:k==1?dy:dz;
            a[k]+=d*fa;
            b[k]-=d*fb;
        }
    }

    private static void pushNode(float[] node,BodyBox box,float[] radial,
            float weight)
    {
        float shift=contactShift(node,box,radial);
        if(shift<=0F)return;
        float amount=Math.min(.055F,shift)*weight;
        node[0]+=radial[0]*amount;
        node[2]+=radial[2]*amount;
    }

    private static void pushLink(float[] start,float[] end,
            BodyBox box,float[] radial)
    {
        float[] middle=lerp(start,end,.50F);
        float shift=contactShift(middle,box,radial);
        if(shift<=0F)return;
        float amount=Math.min(.045F,shift);
        // Both nodes share a link contact, unless one is the fixed root.
        // The caller must restore any pinned root after this step.
        end[0]+=radial[0]*amount;
        end[2]+=radial[2]*amount;
    }

    private static float maxOverlap(List<Panel> ring,List<BodyBox> legs,
            float cx,float cz)
    {
        float maximum=0F;
        for(Panel p:ring)
        {
            float[] radial=radial(p.root,cx,cz);
            for(BodyBox box:legs)
                maximum=Math.max(maximum,Math.max(
                        contactShift(p.mid,box,radial),
                        contactShift(p.hem,box,radial)));
        }
        return maximum;
    }

    /**
     * The minimum positive step along the outward radial direction until
     * a sample leaves a fitted OBB, inflated by a tiny cloth thickness.
     * A bad world-space collider cannot push through the full ring.
     */
    private static float contactShift(float[] point,BodyBox box,float[] radial)
    {
        float[] d=sub(point,new float[]{box.p[0],box.p[1],box.p[2]});
        float needed=Float.POSITIVE_INFINITY;
        for(int axis=0;axis<3;axis++)
        {
            float projected=dot(d,box.axes[axis]);
            float limit=box.p[7+axis]+CLOTH_MARGIN;
            if(Math.abs(projected)>=limit)return 0F;
            float speed=dot(radial,box.axes[axis]);
            if(Math.abs(speed)<.00001F)continue;
            float distance=speed>0F?
                    (limit-projected)/speed:
                    (-limit-projected)/speed;
            if(distance>=0F)needed=Math.min(needed,distance);
        }
        return Float.isFinite(needed)?Math.min(.7F,needed+.006F):0F;
    }

    private static void limit(float[] v,float[] initial,float maximum)
    {
        float[] d=sub(v,initial);
        float l=length(d);
        if(l<=maximum || l<1E-7F)return;
        float factor=maximum/l;
        for(int k=0;k<3;k++)v[k]=initial[k]+d[k]*factor;
    }

    private static float[] radial(float[] root,float cx,float cz)
    {
        float x=root[0]-cx,z=root[2]-cz;
        float size=(float)Math.sqrt(x*x+z*z);
        return size<.01F?new float[]{1,0,0}:
                new float[]{x/size,0,z/size};
    }

    private static float[] worldQ(float[] m,float[] swing)
    {
        float x=m[0]*swing[0]+m[4]*swing[1]+m[8]*swing[2];
        float y=m[1]*swing[0]+m[5]*swing[1]+m[9]*swing[2];
        float z=m[2]*swing[0]+m[6]*swing[1]+m[10]*swing[2];
        float len=(float)Math.sqrt(x*x+y*y+z*z);
        if(len<1E-8F||Math.abs(swing[3])<1E-8F)
            return new float[]{0,0,0,1};
        float h=swing[3]*.5F,si=(float)Math.sin(h)/len;
        return unitQ(new float[]{x*si,y*si,z*si,(float)Math.cos(h)});
    }

    private static float[] toLocalSwing(float[] m,float[] quaternion)
    {
        float[] q=unitQ(quaternion);
        if(q[3]<0F)for(int k=0;k<4;k++)q[k]=-q[k];
        float v=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]);
        if(v<1E-7F)return new float[]{1,0,0,0};
        float angle=2F*(float)Math.atan2(v,q[3]);
        float x=q[0]/v,y=q[1]/v,z=q[2]/v;

        float a=m[0],b=m[4],c=m[8];
        float d=m[1],e=m[5],f=m[9];
        float g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=c*h-b*i,C=b*f-c*e;
        float D=f*g-d*i,E=a*i-c*g,F=c*d-a*f;
        float G=d*h-e*g,H=b*g-a*h,I=a*e-b*d;
        float determinant=a*A+b*D+c*G;
        if(Math.abs(determinant)<1E-8F)return null;
        float lx=(A*x+B*y+C*z)/determinant;
        float ly=(D*x+E*y+F*z)/determinant;
        float lz=(G*x+H*y+I*z)/determinant;
        float mag=(float)Math.sqrt(lx*lx+ly*ly+lz*lz);
        if(mag<1E-8F || !Float.isFinite(mag))return null;
        return new float[]{lx/mag,ly/mag,lz/mag,angle};
    }

    private static float[] arc(float[] from,float[] to)
    {
        float a=length(from),b=length(to);
        if(a<1E-7F||b<1E-7F)return new float[]{0,0,0,1};
        float[] x=mul(from,1F/a),y=mul(to,1F/b);
        float d=Math.max(-1F,Math.min(1F,dot(x,y)));
        if(d>.99999F)return new float[]{0,0,0,1};
        float[] axis=cross(x,y);
        if(d<-.99999F)
        {
            axis=cross(x,new float[]{1,0,0});
            if(length(axis)<.01F)axis=cross(x,new float[]{0,1,0});
            return new float[]{normalize(axis)[0],
                    normalize(axis)[1],normalize(axis)[2],0};
        }
        return unitQ(new float[]{axis[0],axis[1],axis[2],1F+d});
    }

    private static float[] limitAngle(float[] q,float limit)
    {
        float[] v=unitQ(q);
        if(v[3]<0F)for(int i=0;i<4;i++)v[i]=-v[i];
        float len=(float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        float angle=2F*(float)Math.atan2(len,v[3]);
        if(angle<=limit||len<1E-7F)return v;
        float sine=(float)Math.sin(limit*.5F)/len;
        return new float[]{v[0]*sine,v[1]*sine,v[2]*sine,
                (float)Math.cos(limit*.5F)};
    }

    private static float[] conjugate(float[] q)
    {return new float[]{-q[0],-q[1],-q[2],q[3]};}
    private static float[] multiply(float[] a,float[] b)
    {
        return new float[]{
                a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
                a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
                a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
                a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]};
    }
    private static float[] unitQ(float[] q)
    {
        float n=norm4(q);
        if(n<1E-7F)throw new IllegalArgumentException("Zero quaternion");
        return mul(q,1F/n);
    }
    private static float norm4(float[] q)
    {
        return (float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
    }
    private static float[] rotate(float[] q,float[] v)
    {
        float[] axis={q[0],q[1],q[2]};
        float[] t=mul(cross(axis,v),2F);
        return add(v,add(mul(t,q[3]),cross(axis,t)));
    }
    private static float[] origin(float[] m)
    {return new float[]{m[12],m[13],m[14]};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return add(a,mul(sub(b,a),t));}
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] mul(float[] a,float factor)
    {
        float[] out=new float[a.length];
        for(int i=0;i<a.length;i++)out[i]=a[i]*factor;
        return out;
    }
    private static float[] cross(float[] a,float[] b)
    {return new float[]{a[1]*b[2]-a[2]*b[1],
            a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static float[] normalize(float[] v)
    {
        float n=length(v);return n<1E-7F?new float[]{1,0,0}:mul(v,1F/n);
    }
    private static float length(float[] a)
    {return (float)Math.sqrt(dot(a,a));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float distance(float[] a,float[] b)
    {return length(sub(a,b));}
    private static boolean valid(float[] a,int n)
    {
        if(a==null||a.length!=n)return false;
        for(float x:a)if(!Float.isFinite(x))return false;
        return true;
    }
    private static Map<String,float[]> copy(Map<String,float[]> values)
    {
        Map<String,float[]> out=new LinkedHashMap<String,float[]>();
        if(values!=null)for(Map.Entry<String,float[]> e:values.entrySet())
            if(e.getKey()!=null&&e.getValue()!=null)
                out.put(e.getKey(),e.getValue().clone());
        return out;
    }
}
