package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Physics 3.0 / Stage 3: feed REAL Emoticons leg OBBs into the independent
 * cloth solver. We deliberately do not invent leg cylinders from skirt
 * geometry: if editor contact fitting is unavailable, body collisions are
 * disabled with a visible diagnostic while gravity/self-contacts still work.
 *
 * The editor's ephemeral ChameleonMorph instances are associated with
 * their stable BodyPartModelData track; other render paths fall back to the
 * actual morph identity. Never key contacts by model name (two actors may
 * share one model with different leg poses).
 * The publisher supplies the same actor-world attachment matrix used by
 * the existing Jolt collider capture. The consumer resolves the cloth
 * local points to world, performs an OBB contact there, then transforms
 * the correction vector back into the cloth's model space.
 */
public final class SkirtXPBDContacts
{
    private static final WeakHashMap<Object,Frame> FRAMES=
            new WeakHashMap<Object,Frame>();
    /** The editor rebuilds ChameleonMorph objects on every timeline draw.
     * Resolve them to the STABLE BodyPartModelData owner instead of
     * resetting XPBD for each temporary morph clone. */
    private static final WeakHashMap<Object,Object> MORPH_OWNERS=
            new WeakHashMap<Object,Object>();
    private static final long MAX_AGE_NANOS=1500000000L;

    public static final class Frame
    {
        public final int timelineFrame;
        public final float[] modelToWorld;
        public final List<float[]> legBoxes;
        public final boolean leftThigh,rightThigh;
        private final long createdNanos;

        private Frame(int frame,float[] world,List<float[]> boxes,
                boolean left,boolean right,long time)
        {
            timelineFrame=frame;
            modelToWorld=world.clone();
            List<float[]> data=new ArrayList<float[]>();
            for(float[] b:boxes)data.add(b.clone());
            legBoxes=Collections.unmodifiableList(data);
            leftThigh=left;rightThigh=right;
            createdNanos=time;
        }
    }

    private SkirtXPBDContacts(){}

    public static synchronized void registerOwner(Object morph,Object track)
    {
        if(morph!=null&&track!=null)
            MORPH_OWNERS.put(morph,track);
    }

    public static synchronized Object stableOwner(Object morph)
    {
        if(morph==null)return null;
        Object owner=MORPH_OWNERS.get(morph);
        return owner==null?morph:owner;
    }

    public static synchronized void publish(Object morph,int timelineFrame,
            float[] modelToWorld,float[][] boxes,List<String> owners)
    {
        if(morph==null||!matrixValid(modelToWorld)||boxes==null||
                owners==null||boxes.length!=owners.size())
            return;
        List<float[]> selected=new ArrayList<float[]>();
        boolean left=false,right=false;
        for(int i=0;i<boxes.length;i++)
        {
            String id=owners.get(i);
            if(id==null)continue;
            String name=id.toLowerCase(java.util.Locale.ROOT)
                    .replace('-','_').replace(' ','_');
            if(!name.startsWith("emoticons.")||
                    (!name.contains("leg")&&!name.contains("thigh")&&
                            !name.contains("knee")))
                continue;
            float[] b=boxes[i];
            if(b==null||b.length!=10)continue;
            boolean finite=true;
            for(float x:b)if(!Float.isFinite(x))finite=false;
            if(!finite||b[7]<.015F||b[8]<.015F||b[9]<.015F||
                    b[7]>1F||b[8]>1F||b[9]>1F)continue;
            if(name.contains("left"))left=true;
            if(name.contains("right"))right=true;
            selected.add(b.clone());
        }
        // Native Emoticons thigh fit must provide BOTH legs, otherwise
        // resolving just one would push cloth into the missing leg.
        Object owner=stableOwner(morph);
        if(!left||!right||selected.isEmpty())
        {
            FRAMES.remove(owner);
            return;
        }
        FRAMES.put(owner,new Frame(timelineFrame,modelToWorld,
                selected,left,right,System.nanoTime()));
    }

    public static synchronized Frame forMorph(Object morph)
    {
        if(morph==null)return null;
        Object owner=stableOwner(morph);
        Frame frame=FRAMES.get(owner);
        if(frame!=null&&System.nanoTime()-frame.createdNanos>
                MAX_AGE_NANOS)
        {
            FRAMES.remove(owner);
            return null;
        }
        return frame;
    }

    /** Sphere-padded point vs REAL actor-world oriented box.
     * The response is in the LOCAL fabric frame, not world axes. */
    public static float[] corrected(float[] clothLocal,Frame frame,
            float thickness)
    {
        return corrected(clothLocal,frame,thickness,null);
    }

    /**
     * For the 60-node skirt, choose the OUTER exit of a fitted thigh OBB
     * using this particle's native posed direction from the waist. A generic
     * nearest-face projection can send a deeply penetrating left-thigh node
     * toward the actor center and collapse the skirt during playback.
     * This direction is from REAL posed skirt geometry, not a fake collider.
     * Null retains the original nearest-face behavior for legacy callers.
     */
    public static float[] corrected(float[] clothLocal,Frame frame,
            float thickness,float[] posedOutwardLocal)
    {
        if(clothLocal==null||clothLocal.length!=3||
                frame==null||!matrixValid(frame.modelToWorld))
            return clothLocal==null?null:clothLocal.clone();
        float[] point=point(frame.modelToWorld,clothLocal);
        float[] outwardWorld=null;
        if(posedOutwardLocal!=null&&posedOutwardLocal.length==3)
        {
            float[] m=frame.modelToWorld;
            float ox=m[0]*posedOutwardLocal[0]+
                    m[4]*posedOutwardLocal[1]+m[8]*posedOutwardLocal[2];
            float oz=m[2]*posedOutwardLocal[0]+
                    m[6]*posedOutwardLocal[1]+m[10]*posedOutwardLocal[2];
            float radial=(float)Math.sqrt(ox*ox+oz*oz);
            if(Float.isFinite(radial)&&radial>1E-5F)
                outwardWorld=new float[]{ox/radial,0F,oz/radial};
        }
        for(float[] b:frame.legBoxes)
        {
            float[] q={b[3],b[4],b[5],b[6]};
            normalizeQuat(q);
            float[] relative={point[0]-b[0],point[1]-b[1],
                    point[2]-b[2]};
            float[] axes=rotateInverse(q,relative);
            float[] limits={b[7]+thickness,b[8]+thickness,
                    b[9]+thickness};
            int closest=-1;
            float minimum=Float.POSITIVE_INFINITY;
            for(int j=0;j<3;j++)
            {
                float depth=limits[j]-Math.abs(axes[j]);
                if(depth<=0F){closest=-1;break;}
                if(depth<minimum){minimum=depth;closest=j;}
            }
            if(closest<0)continue;
            boolean outwardExit=false;
            if(outwardWorld!=null)
            {
                float[] direction=rotateInverse(q,outwardWorld);
                float ray=Float.POSITIVE_INFINITY;
                for(int j=0;j<3;j++)
                {
                    float d=direction[j];
                    if(Math.abs(d)<1E-6F)continue;
                    float boundary=d>0F?limits[j]:-limits[j];
                    float exit=(boundary-axes[j])/d;
                    if(exit>=0F&&exit<ray)ray=exit;
                }
                if(Float.isFinite(ray))
                {
                    // Bound a single contact correction; subsequent XPBD
                    // iterations may continue projecting deep penetrations.
                    float travel=Math.min(.37F,ray+1E-4F);
                    for(int j=0;j<3;j++)
                        axes[j]+=direction[j]*travel;
                    outwardExit=true;
                }
            }
            if(!outwardExit)
            {
                float sign=axes[closest]>=0F?1F:-1F;
                axes[closest]+=minimum*sign;
            }
            float[] moved=rotate(q,axes);
            point[0]=b[0]+moved[0];
            point[1]=b[1]+moved[1];
            point[2]=b[2]+moved[2];
        }
        float[] matrix=frame.modelToWorld;
        float dx=point[0]-matrix[12],dy=point[1]-matrix[13],
                dz=point[2]-matrix[14];
        float a=matrix[0],b=matrix[4],c=matrix[8];
        float d=matrix[1],e=matrix[5],f=matrix[9];
        float g=matrix[2],h=matrix[6],i=matrix[10];
        float A=e*i-f*h,D=f*g-d*i,G=d*h-e*g;
        float det=a*A+b*D+c*G;
        if(Math.abs(det)<1E-9F||!Float.isFinite(det))
            return clothLocal.clone();
        float z=1F/det;
        float[] local={
            (A*dx+(c*h-b*i)*dy+(b*f-c*e)*dz)*z,
            (D*dx+(a*i-c*g)*dy+(c*d-a*f)*dz)*z,
            (G*dx+(b*g-a*h)*dy+(a*e-b*d)*dz)*z
        };
        for(float x:local)if(!Float.isFinite(x))
            return clothLocal.clone();
        return local;
    }

    public static boolean matrixValid(float[] m)
    {
        if(m==null||m.length!=16)return false;
        for(float v:m)if(!Float.isFinite(v))return false;
        return Math.abs(m[15]-1F)<.01F;
    }

    private static float[] point(float[] m,float[] v)
    {
        return new float[]{
                m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12],
                m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13],
                m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]};
    }
    private static void normalizeQuat(float[] q)
    {
        float n=0F;
        for(float x:q)n+=x*x;
        if(n<1E-8F){q[0]=q[1]=q[2]=0F;q[3]=1F;return;}
        float inv=1F/(float)Math.sqrt(n);
        for(int j=0;j<4;j++)q[j]*=inv;
    }
    private static float[] rotateInverse(float[] q,float[] v)
    {return rotate(new float[]{-q[0],-q[1],-q[2],q[3]},v);}
    private static float[] rotate(float[] q,float[] v)
    {
        float x=q[0],y=q[1],z=q[2],w=q[3];
        float tx=2F*(y*v[2]-z*v[1]);
        float ty=2F*(z*v[0]-x*v[2]);
        float tz=2F*(x*v[1]-y*v[0]);
        return new float[]{
                v[0]+w*tx+y*tz-z*ty,
                v[1]+w*ty+z*tx-x*tz,
                v[2]+w*tz+x*ty-y*tx};
    }
}
