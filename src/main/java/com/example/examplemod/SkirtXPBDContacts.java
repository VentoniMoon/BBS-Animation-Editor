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
    /** Per-editor attachment cloth activation. Unregistered standalone
     * morphs keep the preexisting XPBD preview behavior. */
    private static final WeakHashMap<Object,Boolean> CLOTH_ENABLED=
            new WeakHashMap<Object,Boolean>();
    private static final long MAX_AGE_NANOS=1500000000L;
    private static final boolean SEATED_CONTACTS=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdSeatedContacts","false"));
    // The old heuristic fired during a single high walking step. The new
    // seated treatment requires BOTH real upper-thigh rigs to bend.
    private static final boolean BILATERAL_DRAPE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdBilateralDrape","true"));
    /** Rollback: ordinary tapered OBBs, optional bridge, no SKIN by default. */
    private static final boolean HIP_BRIDGE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdHipBridge","false"));
    /** Per-knee, cloth-only rounded bridge. Independent of the hip switch.
     * A/B: -Dbbs.editor.physics.skirt.xpbdKneeBridge=false. */
    private static final boolean KNEE_BRIDGE=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdKneeBridge","false"));

    public static final class Frame
    {
        public final int timelineFrame;
        /** Fractional editor tick captured with the fitted Emoticons hull. */
        public final double sampleTick;
        public final float[] modelToWorld;
        public final List<float[]> legBoxes;
        /** Exact owners aligned with legBoxes, including @skirt zones. */
        public final List<String> legOwners;
        /** Small rounded hip-gap contacts, stored in world coordinates. */
        public final List<HipBridge> hipBridges;
        /** Rounded gaps between actual distal thigh and proximal shin OBBs. */
        public final List<KneeBridge> kneeBridges;
        /** Actual animated thigh/pelvis triangles, or null for legacy OBBs. */
        public final SkinSurface skinSurface;
        /** 0 = proven walking OBB, 1 = real skinned thigh contact. */
        public final float meshContactBlend;
        public final String surfaceStatus;
        public final boolean leftThigh,rightThigh;
        /** 0 = walking, 1 = BOTH real thigh axes close to horizontal. */
        public final float seatedBlend;
        private final long createdNanos;

        private Frame(int frame,double tick,float[] world,List<float[]> boxes,
                List<String> owners,List<float[]> pelvis,
                boolean left,boolean right,
                SkinSurface surface,String surfaceStatus,long time)
        {
            timelineFrame=frame;
            sampleTick=tick;
            modelToWorld=world.clone();
            List<float[]> data=new ArrayList<float[]>();
            for(float[] b:boxes)data.add(b.clone());
            legBoxes=Collections.unmodifiableList(data);
            legOwners=Collections.unmodifiableList(
                    new ArrayList<String>(owners));
            hipBridges=Collections.unmodifiableList(
                    hipBridges(boxes,owners,pelvis));
            kneeBridges=Collections.unmodifiableList(
                    kneeBridges(boxes,owners));
            skinSurface=surface;
            this.surfaceStatus=surfaceStatus;
            leftThigh=left;rightThigh=right;
            seatedBlend=bothThighsBent(boxes,owners);
            if(surface==null)meshContactBlend=0F;
            else if(SKINNED_WALKING_CONTACTS)meshContactBlend=1F;
            else
            {
                float t=Math.max(0F,Math.min(1F,
                        (seatedBlend-.12F)/.62F));
                meshContactBlend=t*t*(3F-2F*t);
            }
            createdNanos=time;
        }
    }

    /**
     * Compute a stable, symmetric fold factor from actual Emoticons thigh
     * orientations. Each tapered proxy zone repeats its parent's local-Y
     * bone axis, so duplicates cannot artificially increase confidence.
     * No single raised leg may activate seated cloth corrections.
     */
    /** Pure-Java subset also compiles without the native Jolt bridge. */
    private static boolean isUpperThigh(String name)
    {
        if(name==null||!name.startsWith("emoticons."))return false;
        if(name.contains("lower")||name.contains("low_leg")||
                name.contains("knee")||name.contains("foot")||
                name.contains("ankle"))return false;
        return name.contains("left_leg")||name.contains("right_leg")||
                name.contains("leg_left")||name.contains("leg_right")||
                name.contains("left_thigh")||name.contains("right_thigh")||
                name.contains("thigh_left")||name.contains("thigh_right");
    }

    private static float bothThighsBent(List<float[]> boxes,
            List<String> owners)
    {
        float left=0F,right=0F;
        for(int i=0;i<boxes.size();i++)
        {
            String name=owners.get(i);
            if(!isUpperThigh(name))continue;
            float[] box=boxes.get(i);
            float[] q={box[3],box[4],box[5],box[6]};
            normalizeQuat(q);
            float vertical=Math.abs(rotate(q,new float[]{0F,1F,0F})[1]);
            // A 45-degree knee raise is normal walking. Reserve full
            // draping for both thighs bent near 75 degrees or more.
            float bend=Math.max(0F,Math.min(1F,(.70F-vertical)/.40F));
            bend=bend*bend*(3F-2F*bend);
            if(name.contains("left"))left=Math.max(left,bend);
            if(name.contains("right"))right=Math.max(right,bend);
        }
        return Math.min(left,right);
    }


    public static final class HipBridge
    {
        public final float[] a,b;
        public final float radius,weight;
        public final String side;
        private HipBridge(float[] a,float[] b,float r,float weight,String side)
        {
            this.a=a.clone();this.b=b.clone();radius=r;
            this.weight=weight;this.side=side;
        }
    }

    public static final class KneeBridge
    {
        public final float[] a,b;
        public final float radius,weight;
        public final String side;
        private KneeBridge(float[] a,float[] b,float r,float weight,String side)
        {
            this.a=a.clone();this.b=b.clone();
            radius=r;this.weight=weight;this.side=side;
        }
    }

    private static float sat(float x)
    {return Math.max(0F,Math.min(1F,x));}

    private static boolean hipOwner(String s)
    {
        return "emoticons.low_body".equals(s)||
                "emoticons.pelvis".equals(s)||
                "emoticons.hip".equals(s)||"emoticons.hips".equals(s);
    }

    /** Fitted pelvis and PROXIMAL tapered upper zone only. Never use the
     * knee or invented camera/waist positions. A separate bridge is
     * activated independently for the left and right upper thighs. */
    private static List<HipBridge> hipBridges(List<float[]> boxes,
            List<String> owners,List<float[]> pelvis)
    {
        List<HipBridge> out=new ArrayList<HipBridge>();
        if(!HIP_BRIDGE||pelvis.isEmpty())return out;
        for(String side:new String[]{"left","right"})
        {
            float[] upper=null;
            for(int i=0;i<boxes.size();i++)
            {
                String n=owners.get(i);
                if(!isUpperThigh(n)||!n.contains(side))continue;
                if(n.endsWith("@skirt_upper"))
                {upper=boxes.get(i);break;}
                if(!n.contains("@skirt_")&&upper==null)
                    upper=boxes.get(i);
            }
            if(upper==null)continue;
            float[] hip=null;
            float nearest=Float.POSITIVE_INFINITY;
            for(float[] p:pelvis)
            {
                float d=0F;
                for(int k=0;k<3;k++)
                {float e=p[k]-upper[k];d+=e*e;}
                if(d<nearest){nearest=d;hip=p;}
            }
            if(hip==null||nearest>.49F)continue;
            float[] q={upper[3],upper[4],upper[5],upper[6]};
            normalizeQuat(q);
            float[] axis=rotate(q,new float[]{0F,1F,0F});
            float dot=0F;
            for(int k=0;k<3;k++)
                dot+=axis[k]*(hip[k]-upper[k]);
            float sign=dot>=0F?1F:-1F;
            // 45deg stride = off; starts at ~56deg, full at ~77deg.
            float t=sat((.56F-Math.abs(axis[1]))/.34F);
            float w=t*t*(3F-2F*t);
            if(w<.01F)continue;
            float[] proximal=new float[3];
            for(int k=0;k<3;k++)
                proximal[k]=upper[k]+axis[k]*sign*upper[8];
            float[] hipQ={hip[3],hip[4],hip[5],hip[6]};
            normalizeQuat(hipQ);
            float[] relative={proximal[0]-hip[0],
                    proximal[1]-hip[1],proximal[2]-hip[2]};
            float[] local=rotateInverse(hipQ,relative);
            for(int k=0;k<3;k++)
                local[k]=Math.max(-hip[7+k],Math.min(hip[7+k],local[k]));
            float[] surface=rotate(hipQ,local);
            float[] a={hip[0]+surface[0],hip[1]+surface[1],
                    hip[2]+surface[2]};
            // Previous endpoint crossed past the proximal end and deep
            // INSIDE the thigh OBB. It created a second contact right
            // where the original OBB already supported the skirt.
            // Connect only the genuine free gap up to the proximal cap.
            float[] b=proximal.clone();
            float dist2=0F;
            for(int k=0;k<3;k++){float v=b[k]-a[k];dist2+=v*v;}
            if(dist2<.000625F||dist2>.0324F)continue;
            // Never grow wider than the narrowed existing upper thigh.
            float radius=Math.min(.085F,
                    Math.min(upper[7],upper[9])*.62F)*w;
            if(radius>=.012F)
                out.add(new HipBridge(a,b,radius,w,side));
        }
        return out;
    }

    /**
     * Build ONE knee-gap capsule per side, and only from physically fitted
     * Emoticons OBBs. Distal thigh = the real @skirt-lower box, proximal
     * shin = nearest cap of the actual lower-leg box. The capsule does not
     * stand in for an absent shin, nor does it run at straight-leg gait.
     *
     * Knee activation uses the RELATIVE thigh/shin axial angle. Unlike
     * checking the shin against world vertical, this does not trigger
     * merely because the entire actor leans or the hip rotates.
     */
    private static boolean lowerLeg(String name)
    {
        if(name==null||!name.startsWith("emoticons.")||
                name.contains("foot")||name.contains("ankle"))return false;
        return (name.contains("low_leg")||name.contains("lower_leg")||
                name.contains("low_left_leg")||
                name.contains("low_right_leg")||
                name.contains("shin")) &&
                (name.contains("leg")||name.contains("shin"));
    }

    private static float[] boneAxis(float[] box)
    {
        float[] q={box[3],box[4],box[5],box[6]};
        normalizeQuat(q);
        return rotate(q,new float[]{0F,1F,0F});
    }

    private static float sqDistance(float[] a,float[] b)
    {
        float d=0F;
        for(int k=0;k<3;k++)
        {float t=a[k]-b[k];d+=t*t;}
        return d;
    }

    private static float[] axialEnd(float[] box,float[] axis,
            float sign)
    {
        return new float[]{
            box[0]+sign*box[8]*axis[0],
            box[1]+sign*box[8]*axis[1],
            box[2]+sign*box[8]*axis[2]};
    }

    private static List<KneeBridge> kneeBridges(List<float[]> boxes,
            List<String> owners)
    {
        List<KneeBridge> result=new ArrayList<KneeBridge>();
        if(!KNEE_BRIDGE)return result;
        for(String side:new String[]{"left","right"})
        {
            float[] thigh=null;
            // Prefer the actual DISTAL of three tapered zones. A single
            // unsplit upper leg is supported when no taper fit is present.
            for(int i=0;i<boxes.size();i++)
            {
                String n=owners.get(i);
                // The suffix @skirt-lower names the DISTAL zone of an
                // UPPER thigh. Strip only the proxy-zone suffix before
                // checking bone identity: the generic isUpperThigh()
                // rightly rejects lower-leg BONES containing "lower".
                String bone=n;
                int zone=bone.indexOf("@skirt_");
                if(zone>=0)bone=bone.substring(0,zone);
                if(!isUpperThigh(bone)||!n.contains(side))continue;
                if(n.endsWith("@skirt_lower"))
                {thigh=boxes.get(i);break;}
                if(!n.contains("@skirt_")&&thigh==null)
                    thigh=boxes.get(i);
            }
            if(thigh==null)continue;
            float[] axis=boneAxis(thigh);
            float[] shin=null,nearEnd=null;
            float best=Float.POSITIVE_INFINITY;
            // Select a leg on the SAME side, then pair the physically
            // nearest ends rather than guessing signs from world Y.
            for(int i=0;i<boxes.size();i++)
            {
                String name=owners.get(i);
                if(!name.contains(side)||!lowerLeg(name))continue;
                float[] candidate=boxes.get(i);
                float[] candidateAxis=boneAxis(candidate);
                float[] tePlus=axialEnd(thigh,axis,1F);
                float[] teMinus=axialEnd(thigh,axis,-1F);
                for(float[] thighEnd:new float[][]{tePlus,teMinus})
                    for(float shinSign:new float[]{-1F,1F})
                    {
                        float[] shinEnd=axialEnd(candidate,candidateAxis,shinSign);
                        float d=sqDistance(thighEnd,shinEnd);
                        if(d<best)
                        {
                            best=d;shin=candidate;nearEnd=thighEnd;
                        }
                    }
            }
            // A broken/missing shin fit must never synthesize a contact
            // between far-away limbs or bridge left and right knees.
            if(shin==null||best>.25F*.25F)continue;
            float[] shinAxis=boneAxis(shin);
            float alignment=Math.abs(axis[0]*shinAxis[0]+
                    axis[1]*shinAxis[1]+axis[2]*shinAxis[2]);
            // ~40 deg = 0 weight, ~75 deg = full. Separate per knee.
            float blend=sat((.76604444F-alignment)/
                    (.76604444F-.25881904F));
            float weight=blend*blend*(3F-2F*blend);
            if(weight<.01F)continue;
            float[] endShinPlus=axialEnd(shin,shinAxis,1F);
            float[] endShinMinus=axialEnd(shin,shinAxis,-1F);
            float[] endShin=sqDistance(nearEnd,endShinPlus)<
                    sqDistance(nearEnd,endShinMinus)?
                    endShinPlus:endShinMinus;
            float gap2=sqDistance(nearEnd,endShin);
            // With coincident ends the source boxes already overlap.
            // Keep debug and physics from receiving degenerate capsules.
            if(gap2<.025F*.025F||gap2>.18F*.18F)continue;
            float width=Math.min(Math.min(thigh[7],thigh[9]),
                    Math.min(shin[7],shin[9]));
            float radius=Math.min(.075F,width*.55F)*weight;
            if(radius<.012F)continue;
            result.add(new KneeBridge(nearEnd,endShin,radius,weight,side));
        }
        return result;
    }

    /**
     * Physically overlapping constraints fight each other: the box pass
     * may push a cloth node OUTSIDE a thigh and the later rounded bridge
     * would then push it BACK INSIDE that same thigh, creating the observed
     * repeated pinching near the leg crease.
     *
     * Bridge contact is a filler only, never an extra collision layer on
     * top of an existing OBB. With the same world frame used for the
     * actual point contact, test both the input and projected result.
     */
    private static boolean insideAnyLegBox(float[] point,
            List<float[]> boxes,float margin)
    {
        for(float[] b:boxes)
        {
            float[] q={b[3],b[4],b[5],b[6]};
            normalizeQuat(q);
            float[] rel={point[0]-b[0],point[1]-b[1],
                    point[2]-b[2]};
            float[] local=rotateInverse(q,rel);
            if(Math.abs(local[0])<b[7]+margin&&
                    Math.abs(local[1])<b[8]+margin&&
                    Math.abs(local[2])<b[9]+margin)
                return true;
        }
        return false;
    }

    /** Finite rounded capsule contact. Prefer the authored outward
     * direction if a cloth node fell inward through the medial seam. */
    private static float[] bridgeExit(float[] p,HipBridge bridge,
            float thickness,float[] outward)
    {
        return roundedExit(p,bridge.a,bridge.b,bridge.radius,
                thickness,outward);
    }

    private static float[] roundedExit(float[] p,float[] a,float[] b,
            float radius,float thickness,float[] outward)
    {
        float dx=b[0]-a[0],
                dy=b[1]-a[1],
                dz=b[2]-a[2];
        float length2=dx*dx+dy*dy+dz*dz;
        if(length2<1E-7F)return p;
        float t=sat(((p[0]-a[0])*dx+
                (p[1]-a[1])*dy+(p[2]-a[2])*dz)/length2);
        float x=p[0]-a[0]-t*dx,
                y=p[1]-a[1]-t*dy,
                z=p[2]-a[2]-t*dz;
        float dist=(float)Math.sqrt(x*x+y*y+z*z);
        float limit=radius+thickness;
        if(dist>=limit)return p;
        float[] normal=dist>1E-6F?
                new float[]{x/dist,y/dist,z/dist}:
                outward!=null?outward.clone():new float[]{0F,1F,0F};
        if(outward!=null&&
                normal[0]*outward[0]+normal[2]*outward[2]<-.35F)
            normal=outward.clone();
        float depth=Math.min(.10F,limit-dist);
        return new float[]{p[0]+depth*normal[0],
                p[1]+depth*normal[1],p[2]+depth*normal[2]};
    }

    /**
     * Read-only, CPU-skinned Emoticons thigh/pelvis collision surface.
     * This is NOT a second Jolt rigid-body world. The surface is captured
     * only after Emoticons finishes its normal skinning pass, and built
     * once for a published editor sample. No new box joints or synthetic
     * bridge volumes can introduce the artificial three-zone cracks.
     *
     * The 3-D spatial hash contains ONLY triangles whose AABBs overlap
     * its cell; expanded AABBs cover a conservative contact search band.
     * Queries are thus bounded by the nearby surface, not all body mesh
     * triangles x 100 cloth particles x 8 XPBD iterations.
     */
    public static final boolean SKINNED_MESH_CONTACTS=Boolean.parseBoolean(
            System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSkinnedMeshContacts","false"));
    // Pelvis is located directly under the PINNED waist. Including all
    // pelvis triangles as active cloth contacts prematurely lifts and
    // crushes the first unpinned ring even in the standing pose.
    // Keep this opt-in until a separate waist/thigh stitching test passes.
    public static final boolean SKINNED_PELVIS_CONTACTS=Boolean.parseBoolean(
            System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSkinnedPelvisContacts","false"));
    /**
     * New Emoticons surface contacts are useful for deeply raised thighs,
     * but existing tapered OBBs already give stable walking. Do NOT
     * substitute a dense skinned surface at 0 degrees: it turns the
     * standing skirt into a rigid bell near the upper-thigh edges.
     * Explicit A/B override:
     * -Dbbs.editor.physics.skirt.xpbdSkinnedWalkingContacts=true
     */
    private static final boolean SKINNED_WALKING_CONTACTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSkinnedWalkingContacts",
                    "false"));

    /** One CPU-skinned Emoticons submesh, selected by dominant bone weights. */
    public static final class SkinRegion
    {
        public final String owner;
        public final float[] vertices;
        public final int[] triangles;
        public SkinRegion(String owner,float[] vertices,int[] triangles)
        {
            if(owner==null||vertices==null||triangles==null||
                    vertices.length%3!=0||triangles.length%3!=0)
                throw new IllegalArgumentException("Invalid skinned region");
            this.owner=owner;
            this.vertices=vertices.clone();
            this.triangles=triangles.clone();
        }
    }

    public static final class SkinSurface
    {
        private static final float CELL=.22F;
        private static final float BAND=.16F;
        // Previous contact could advance .065 per XPBD iteration. Near a
        // thigh edge, that jumped across a second face and raised the
        // pinned waist's neighboring rows like a rigid shelf.
        private static final float MAX_CONTACT_STEP=.032F;
        // The 20x5 cloth queries a broadphase hash, not every face.
        // Detailed Emoticons thigh skins can exceed 1700 faces EACH,
        // before pelvic geometry is included. The former 4500-face cap
        // silently disabled the entire skin contact path.
        private static final int MAX_TRIANGLES=12000;
        private static final int MAX_CELL_REFERENCES=650000;
        public final int triangleCount,leftCount,rightCount,pelvisCount;
        /** Stable hash of the EXACT current skinned surface and actor yaw. */
        public final long fingerprint;
        public final float[] bounds;
        private final java.util.Map<Cell,java.util.List<Face>> grid;
        private final java.util.List<Face> faces;

        private SkinSurface(java.util.Map<Cell,java.util.List<Face>> cells,
                java.util.List<Face> selected,
                int count,int left,int right,int pelvis,long stamp,
                float[] range)
        {
            grid=java.util.Collections.unmodifiableMap(cells);
            faces=java.util.Collections.unmodifiableList(selected);
            triangleCount=count;leftCount=left;rightCount=right;
            pelvisCount=pelvis;fingerprint=stamp;bounds=range;
        }

        private static final class Cell
        {
            final int x,y,z;
            Cell(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
            @Override public int hashCode()
            {return (x*73856093)^(y*19349663)^(z*83492791);}
            @Override public boolean equals(Object other)
            {
                if(!(other instanceof Cell))return false;
                Cell c=(Cell)other;
                return x==c.x&&y==c.y&&z==c.z;
            }
        }
        private static final class Face
        {
            final float[] a,b,c,normal,na,nb,nc;
            Face(float[] a,float[] b,float[] c,float[] n,
                    float[] na,float[] nb,float[] nc)
            {
                this.a=a;this.b=b;this.c=c;normal=n;
                this.na=na;this.nb=nb;this.nc=nc;
            }
        }
        /** Weld identical skinned positions ONLY within the same limb,
         * so UV seams do not reintroduce sharp artificial normals. */
        private static final class VertexKey
        {
            final int x,y,z;
            VertexKey(float[] p)
            {
                x=Math.round(p[0]*10000F);
                y=Math.round(p[1]*10000F);
                z=Math.round(p[2]*10000F);
            }
            @Override public int hashCode()
            {return (x*73856093)^(y*19349663)^(z*83492791);}
            @Override public boolean equals(Object other)
            {
                if(!(other instanceof VertexKey))return false;
                VertexKey k=(VertexKey)other;
                return x==k.x&&y==k.y&&z==k.z;
            }
        }
        private static int cell(float value)
        {return (int)Math.floor(value/CELL);}
        private static float[] minus(float[] a,float[] b)
        {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
        private static float dot(float[] a,float[] b)
        {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
        private static float[] cross(float[] a,float[] b)
        {
            return new float[]{a[1]*b[2]-a[2]*b[1],
                    a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
        }
        private static float[] mix(float[] a,float[] b,float t)
        {
            return new float[]{a[0]+(b[0]-a[0])*t,
                    a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t};
        }
        private static float clamp(float v)
        {return Math.max(0F,Math.min(1F,v));}

        /**
         * Returns null if a real skinned surface is incomplete. Old OBBs
         * remain the fallback; no guessed actor geometry is ever created.
         * Vertex coordinates are Emoticons' CPU-skinned render-local data.
         * The actor-world transform must be applied ONCE (not the child
         * Chameleon attachment transform, which belongs only to cloth).
         */
        public static SkinSurface build(float[] actorWorld,
                java.util.List<SkinRegion> regions)
        {
            return build(actorWorld,regions,null);
        }

        /**
         * Detailed rejection diagnostics remain separate from the caller's
         * triangle selection counts. If a single skinned triangle is an
         * extreme outlier, skip that face rather than discarding ALL legs.
         */
        public static SkinSurface build(float[] actorWorld,
                java.util.List<SkinRegion> regions,String[] rejection)
        {
            if(!SKINNED_MESH_CONTACTS||!matrixValid(actorWorld)||
                    regions==null||regions.isEmpty())
            {
                reason(rejection,"disabled, invalid actor frame or no regions");
                return null;
            }
            java.util.Map<Cell,java.util.List<Face>> cells=
                    new java.util.HashMap<Cell,java.util.List<Face>>();
            java.util.List<Face> allFaces=
                    new java.util.ArrayList<Face>();
            float[] bounds={Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY};
            int accepted=0,left=0,right=0,pelvis=0;
            int skippedOversized=0,skippedDegenerate=0;
            int cellReferences=0;
            long hash=1469598103934665603L;
            for(SkinRegion region:regions)
            {
                if(region==null||region.triangles.length==0)continue;
                float[] source=region.vertices;
                float[][] world=new float[source.length/3][];
                for(int i=0;i<world.length;i++)
                {
                    float[] p={source[3*i],source[3*i+1],source[3*i+2]};
                    if(!finite(p))
                    {
                        reason(rejection,"nonfinite Emoticons vertex");
                        return null;
                    }
                    world[i]=point(actorWorld,p);
                }
                // Compute the owning bone surface centroid from referenced
                // vertices, not from the whole (multi-bone) Emoticons mesh.
                boolean[] used=new boolean[world.length];
                float[] center=new float[3];
                int count=0;
                for(int index:region.triangles)
                {
                    if(index<0||index>=world.length)
                    {
                        reason(rejection,"bone triangle index out of range");
                        return null;
                    }
                    if(used[index])continue;
                    used[index]=true;count++;
                    for(int k=0;k<3;k++)center[k]+=world[index][k];
                }
                if(count<3)continue;
                for(int k=0;k<3;k++)center[k]/=count;
                float[][] smooth=vertexNormals(world,region.triangles,
                        center);
                int regionAccepted=0;
                for(int j=0;j<region.triangles.length;j+=3)
                {
                    float[] a=world[region.triangles[j]];
                    float[] b=world[region.triangles[j+1]];
                    float[] c=world[region.triangles[j+2]];
                    float[] ab=minus(b,a),ac=minus(c,a);
                    float[] normal=cross(ab,ac);
                    float length=(float)Math.sqrt(dot(normal,normal));
                    if(length<1E-7F||!Float.isFinite(length))
                    {
                        skippedDegenerate++;
                        continue;
                    }
                    for(int k=0;k<3;k++)normal[k]/=length;
                    float[] middle={(a[0]+b[0]+c[0])/3F,
                            (a[1]+b[1]+c[1])/3F,
                            (a[2]+b[2]+c[2])/3F};
                    // A dominant-bone triangle set can be open at joints,
                    // but most surface faces still point away from the
                    // owning bone's centroid. Repair inverted winding.
                    float outward=dot(normal,minus(middle,center));
                    if(outward < -1E-5F)
                        for(int k=0;k<3;k++)normal[k]=-normal[k];
                    float[] min=new float[3],max=new float[3];
                    boolean oversized=false;
                    for(int k=0;k<3;k++)
                    {
                        min[k]=Math.min(a[k],Math.min(b[k],c[k]));
                        max[k]=Math.max(a[k],Math.max(b[k],c[k]));
                        // Some imported Emoticons skins contain a few
                        // malformed far-reaching faces. Exclude those
                        // faces, not the entire otherwise valid thigh hull.
                        if(!Float.isFinite(min[k])||
                                !Float.isFinite(max[k])||
                                max[k]-min[k]>1.5F)
                            oversized=true;
                    }
                    if(oversized)
                    {
                        skippedOversized++;
                        continue;
                    }
                    int[] lo=new int[3],hi=new int[3];
                    for(int k=0;k<3;k++)
                    {
                        bounds[k]=Math.min(bounds[k],min[k]);
                        bounds[k+3]=Math.max(bounds[k+3],max[k]);
                        lo[k]=cell(min[k]-BAND);
                        hi[k]=cell(max[k]+BAND);
                    }
                    long newRefs=(long)(hi[0]-lo[0]+1)*
                            (hi[1]-lo[1]+1)*(hi[2]-lo[2]+1);
                    if(newRefs>MAX_CELL_REFERENCES-cellReferences)
                    {
                        reason(rejection,"spatial hash limit reached after "+
                                accepted+" triangles");
                        return null;
                    }
                    cellReferences+=(int)newRefs;
                    Face face=new Face(a,b,c,normal,
                            smooth[region.triangles[j]],
                            smooth[region.triangles[j+1]],
                            smooth[region.triangles[j+2]]);
                    allFaces.add(face);
                    for(int ix=lo[0];ix<=hi[0];ix++)
                        for(int iy=lo[1];iy<=hi[1];iy++)
                            for(int iz=lo[2];iz<=hi[2];iz++)
                            {
                                Cell key=new Cell(ix,iy,iz);
                                java.util.List<Face> entry=cells.get(key);
                                if(entry==null)
                                {
                                    entry=new java.util.ArrayList<Face>();
                                    cells.put(key,entry);
                                }
                                entry.add(face);
                            }
                    for(float value:middle)
                        hash=(hash^Float.floatToIntBits(value))*
                                1099511628211L;
                    regionAccepted++;
                    if(++accepted>MAX_TRIANGLES)
                    {
                        reason(rejection,"triangle budget reached: "+
                                accepted+" > "+MAX_TRIANGLES);
                        return null;
                    }
                }
                String owner=region.owner.toLowerCase(java.util.Locale.ROOT);
                if(owner.contains("left"))left+=regionAccepted;
                if(owner.contains("right"))right+=regionAccepted;
                if(owner.contains("pelvis")||owner.contains("hip")||
                        owner.contains("low_body"))pelvis+=regionAccepted;
            }
            // Partial hulls are unsafe: falling back to OBBs is preferable
            // to leaving one leg without collisions.
            if(left<8||right<8)
            {
                reason(rejection,"insufficient valid thighs L="+left+
                        " R="+right+" oversized="+skippedOversized+
                        " degenerate="+skippedDegenerate);
                return null;
            }
            reason(rejection,"accepted="+accepted+" skippedOversized="+
                    skippedOversized+" skippedDegenerate="+skippedDegenerate);
            return new SkinSurface(cells,allFaces,accepted,left,right,pelvis,
                    hash,bounds);
        }

        /**
         * Area-weighted normals, oriented toward the owning limb's
         * exterior and welded across duplicated UV seam vertices.
         * Compute from all valid incident triangles BEFORE contact,
         * so a particle crossing a 90-degree thigh corner receives
         * a smoothly interpolated surface normal instead of an abrupt
         * top-face versus side-face jump.
         */
        private static float[][] vertexNormals(float[][] vertices,
                int[] indices,float[] centroid)
        {
            java.util.Map<VertexKey,float[]> sums=
                    new java.util.HashMap<VertexKey,float[]>();
            VertexKey[] keys=new VertexKey[vertices.length];
            for(int i=0;i<vertices.length;i++)
                keys[i]=new VertexKey(vertices[i]);
            for(int j=0;j+2<indices.length;j+=3)
            {
                int ia=indices[j],ib=indices[j+1],ic=indices[j+2];
                float[] a=vertices[ia],b=vertices[ib],c=vertices[ic];
                float[] n=cross(minus(b,a),minus(c,a));
                float length=(float)Math.sqrt(dot(n,n));
                if(!(length>1E-7F)||!Float.isFinite(length))continue;
                boolean oversized=false;
                for(int k=0;k<3;k++)
                    if(Math.max(a[k],Math.max(b[k],c[k]))-
                            Math.min(a[k],Math.min(b[k],c[k]))>1.5F)
                        oversized=true;
                if(oversized)continue;
                float[] middle={(a[0]+b[0]+c[0])/3F,
                        (a[1]+b[1]+c[1])/3F,
                        (a[2]+b[2]+c[2])/3F};
                if(dot(n,minus(middle,centroid)) < -1E-5F)
                    for(int k=0;k<3;k++)n[k]=-n[k];
                // Cap face-area dominance to avoid imported long faces
                // forcing an incorrect normal across a small smooth seam.
                float factor=Math.min(length,.075F)/length;
                for(int index:new int[]{ia,ib,ic})
                {
                    VertexKey key=keys[index];
                    float[] sum=sums.get(key);
                    if(sum==null)
                    {
                        sum=new float[3];
                        sums.put(key,sum);
                    }
                    for(int k=0;k<3;k++)sum[k]+=n[k]*factor;
                }
            }
            float[][] out=new float[vertices.length][3];
            for(int i=0;i<vertices.length;i++)
            {
                float[] sum=sums.get(keys[i]);
                if(sum==null)continue;
                float length=(float)Math.sqrt(dot(sum,sum));
                if(length>1E-7F)
                    for(int k=0;k<3;k++)out[i][k]=sum[k]/length;
            }
            return out;
        }

        private static float[] smoothNormal(Face face,float[] closest)
        {
            float[] ab=minus(face.b,face.a),
                    ac=minus(face.c,face.a),
                    ap=minus(closest,face.a);
            float d00=dot(ab,ab),d01=dot(ab,ac),
                    d11=dot(ac,ac),d20=dot(ap,ab),d21=dot(ap,ac);
            float denominator=d00*d11-d01*d01;
            if(Math.abs(denominator)<1E-9F)return face.normal;
            float v=(d11*d20-d01*d21)/denominator;
            float w=(d00*d21-d01*d20)/denominator;
            float u=1F-v-w;
            float[] n=new float[3];
            for(int k=0;k<3;k++)
                n[k]=u*face.na[k]+v*face.nb[k]+w*face.nc[k];
            float magnitude=(float)Math.sqrt(dot(n,n));
            if(!(magnitude>1E-5F))return face.normal;
            for(int k=0;k<3;k++)n[k]/=magnitude;
            // Do not allow an ill-formed skinned mesh to turn its surface
            // contact more than ~70 degrees away from the actual face.
            if(dot(n,face.normal)<.35F)return face.normal;
            return n;
        }

        private static void reason(String[] output,String value)
        {
            if(output!=null&&output.length>0)output[0]=value;
        }

        private static boolean finite(float[] point)
        {
            for(float value:point)if(!Float.isFinite(value))return false;
            return true;
        }
        /**
         * Nearest point on triangle (Ericson / real-time collision).
         * Handles all edges/corners, unlike plane-only projection that
         * pushes fabric through gaps along a triangle's infinite plane.
         */
        private static float[] closest(float[] p,Face t)
        {
            float[] ab=minus(t.b,t.a),ac=minus(t.c,t.a),
                    ap=minus(p,t.a);
            float d1=dot(ab,ap),d2=dot(ac,ap);
            if(d1<=0F&&d2<=0F)return t.a;
            float[] bp=minus(p,t.b);
            float d3=dot(ab,bp),d4=dot(ac,bp);
            if(d3>=0F&&d4<=d3)return t.b;
            float vc=d1*d4-d3*d2;
            if(vc<=0F&&d1>=0F&&d3<=0F)
                return mix(t.a,t.b,d1/(d1-d3));
            float[] cp=minus(p,t.c);
            float d5=dot(ab,cp),d6=dot(ac,cp);
            if(d6>=0F&&d5<=d6)return t.c;
            float vb=d5*d2-d1*d6;
            if(vb<=0F&&d2>=0F&&d6<=0F)
                return mix(t.a,t.c,d2/(d2-d6));
            float va=d3*d6-d5*d4;
            if(va<=0F&&(d4-d3)>=0F&&(d5-d6)>=0F)
                return mix(t.b,t.c,(d4-d3)/
                        ((d4-d3)+(d5-d6)));
            float total=va+vb+vc;
            if(Math.abs(total)<1E-9F)return t.a;
            float v=vb/total,w=vc/total;
            return new float[]{t.a[0]+ab[0]*v+ac[0]*w,
                    t.a[1]+ab[1]*v+ac[1]*w,
                    t.a[2]+ab[2]*v+ac[2]*w};
        }

        /** Subsample read-only REAL skinned triangle edges for GUI.
         * The preview never feeds them back into collision or Jolt. */
        /**
         * Fails closed if a contact hull is far from the garment's
         * animated attachment. Coordinates are compared in world space
         * before any contact projection. Do not silently apply a guessed
         * offset/rotation to make a detached hull appear to overlap.
         */
        public boolean closeToClothAnchor(float[] clothWorld)
        {
            if(!matrixValid(clothWorld))return false;
            // The skirt starts at the hip: a real thigh must have an
            // AABB horizontally near the waist attachment. Vertical leg
            // travel can be longer, so give Y a more generous margin.
            for(int k=0;k<3;k++)
            {
                float v=clothWorld[12+k];
                float tolerance=k==1?1.15F:.65F;
                if(!Float.isFinite(bounds[k])||
                        !Float.isFinite(bounds[k+3])||
                        v<bounds[k]-tolerance||
                        v>bounds[k+3]+tolerance)
                    return false;
            }
            return true;
        }

        public java.util.List<float[][]> debugEdges(int maxFaces)
        {
            int limit=Math.max(1,maxFaces);
            int stride=Math.max(1,(faces.size()+limit-1)/limit);
            java.util.List<float[][]> output=
                    new java.util.ArrayList<float[][]>();
            for(int i=0;i<faces.size();i+=stride)
            {
                Face f=faces.get(i);
                output.add(new float[][]{f.a.clone(),f.b.clone()});
                output.add(new float[][]{f.b.clone(),f.c.clone()});
                output.add(new float[][]{f.c.clone(),f.a.clone()});
            }
            return output;
        }

        /**
         * Single smooth contact against the nearest ACTUAL skinned face.
         * Never project sequentially against overlapping boxes. The
         * outward normal comes from the CPU-skinned surface, not the
         * artificial hip-up / thigh-radial switches.
         *
         * A conservative one-step cap avoids teleporting skirt vertices
         * to the waist when a rendered pose changes suddenly.
         */
        public float[] correctedWorld(float[] world,float thickness)
        {
            return correctedWorld(world,thickness,0F);
        }

        /**
         * A seated thigh is nearly horizontal. The cloth may straddle
         * the REAL top-face edge while no particle sits directly above
         * a triangle interior. Expand ONLY the top-face edge capture band;
         * keep vertical/medial side edges narrow so separate left/right
         * thighs never acquire a fictitious collision bridge.
         *
         * This affects only candidate selection against finite triangles;
         * neither normals nor collision hull dimensions are enlarged.
         */
        public float[] correctedWorld(float[] world,float thickness,
                float seatedTopEdgeAllowance)
        {
            if(world==null||world.length!=3||!finite(world))
                return world==null?null:world.clone();
            java.util.List<Face> nearby=grid.get(new Cell(
                    cell(world[0]),cell(world[1]),cell(world[2])));
            if(nearby==null||nearby.isEmpty())return world.clone();
            Face best=null;
            float[] bestPoint=null;
            float best2=BAND*BAND;
            for(Face f:nearby)
            {
                float[] closest=closest(world,f);
                float[] delta=minus(world,closest);
                float d2=dot(delta,delta);
                // Do not collide with the infinite extrusion of the
                // nearest triangle. A point in the actual gap between
                // two thighs can be close to BOTH triangle edges without
                // lying above either leg (the previous code built an
                // artificial collision bridge through that gap).
                float signedDistance=dot(delta,f.normal);
                float lateral2=Math.max(0F,
                        d2-signedDistance*signedDistance);
                float topAllowance=f.normal[1]>.50F?
                        Math.max(0F,Math.min(.045F,seatedTopEdgeAllowance)):0F;
                float lateralBand=thickness+.008F+topAllowance;
                if(lateral2>lateralBand*lateralBand)continue;
                if(d2<best2)
                {
                    best2=d2;best=f;bestPoint=closest;
                }
            }
            if(best==null)return world.clone();
            float[] normal=smoothNormal(best,bestPoint);
            float signed=dot(minus(world,bestPoint),normal);
            float need=thickness+0.004F-signed;
            if(need<=0F)return world.clone();
            float step=Math.min(MAX_CONTACT_STEP,need);
            return new float[]{world[0]+normal[0]*step,
                    world[1]+normal[1]*step,
                    world[2]+normal[2]*step};
        }
    }

    private SkirtXPBDContacts(){}

    public static synchronized void registerOwner(Object morph,Object track)
    {
        if(morph!=null&&track!=null)
            MORPH_OWNERS.put(morph,track);
    }

    /** Unlike legacy Jolt hair chains, cloth activation is attached
     * to the complete skirt model, never to an internal bone name. */
    public static synchronized void setClothEnabled(Object track,
            boolean enabled)
    {
        if(track!=null)
        {
            CLOTH_ENABLED.put(track,Boolean.valueOf(enabled));
            if(!enabled)FRAMES.remove(track);
        }
    }

    public static synchronized boolean isClothEnabled(Object morph)
    {
        if(morph==null)return false;
        Boolean enabled=CLOTH_ENABLED.get(stableOwner(morph));
        return enabled==null||enabled.booleanValue();
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
        publish(morph,timelineFrame,(double)timelineFrame,
                modelToWorld,boxes,owners);
    }

    /**
     * Fractional clock must be taken from the SAME editor render sample
     * used to fit the legs, not from wall time (shadow/reflection passes
     * must not progress physical time independently).
     */
    public static synchronized void publish(Object morph,int timelineFrame,
            double sampleTick,float[] modelToWorld,float[][] boxes,
            List<String> owners)
    {
        publish(morph,timelineFrame,sampleTick,modelToWorld,boxes,
                owners,null);
    }

    /** Publish exactly one read-only surface with its matching collider tick. */
    public static synchronized void publish(Object morph,int timelineFrame,
            double sampleTick,float[] modelToWorld,float[][] boxes,
            List<String> owners,SkinSurface surface)
    {
        if(!Double.isFinite(sampleTick)||
                sampleTick<timelineFrame-1E-4D||
                sampleTick>timelineFrame+1.0001D)return;
        if(morph==null||!matrixValid(modelToWorld)||boxes==null||
                owners==null||boxes.length!=owners.size())
            return;
        List<float[]> selected=new ArrayList<float[]>();
        List<String> selectedOwners=new ArrayList<String>();
        List<float[]> pelvis=new ArrayList<float[]>();
        boolean left=false,right=false;
        for(int i=0;i<boxes.length;i++)
        {
            String id=owners.get(i);
            if(id==null)continue;
            String name=id.toLowerCase(java.util.Locale.ROOT)
                    .replace('-','_').replace(' ','_');
            if(!hipOwner(name)&&(!name.startsWith("emoticons.")||
                    (!name.contains("leg")&&!name.contains("thigh")&&
                            !name.contains("knee"))))
                continue;
            float[] b=boxes[i];
            if(b==null||b.length!=10)continue;
            boolean finite=true;
            for(float x:b)if(!Float.isFinite(x))finite=false;
            if(!finite||b[7]<.015F||b[8]<.015F||b[9]<.015F||
                    b[7]>1F||b[8]>1F||b[9]>1F)continue;
            if(hipOwner(name))
            {
                pelvis.add(b.clone());
                continue;
            }
            if(name.contains("left"))left=true;
            if(name.contains("right"))right=true;
            selected.add(b.clone());
            selectedOwners.add(name);
        }
        // Native Emoticons thigh fit must provide BOTH legs, otherwise
        // resolving just one would push cloth into the missing leg.
        Object owner=stableOwner(morph);
        if(!left||!right||selected.isEmpty())
        {
            FRAMES.remove(owner);
            return;
        }
        SkinSurface active=SKINNED_MESH_CONTACTS?surface:null;
        String status=active!=null?"skinned triangles":
                "real skin unavailable";
        if(active!=null&&!active.closeToClothAnchor(modelToWorld))
        {
            // The same discrepancy can make cyan debug triangles appear
            // beside the actor rather than over its thighs. This prevents
            // invalid world-space contacts from distorting the skirt.
            active=null;
            status="skin/cloth world-space mismatch";
        }
        FRAMES.put(owner,new Frame(timelineFrame,sampleTick,modelToWorld,
                selected,selectedOwners,pelvis,left,right,active,status,
                System.nanoTime()));
    }

    public static synchronized Frame forMorph(Object morph)
    {
        if(morph==null||!isClothEnabled(morph))return null;
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
        return corrected(clothLocal,frame,thickness,posedOutwardLocal,1F);
    }

    /** Knee clearance acts only on the intended cloth latitudes.
     * Pass 0 for upper/belt rows; direct callers retain 1 for parity. */
    public static float[] corrected(float[] clothLocal,Frame frame,
            float thickness,float[] posedOutwardLocal,float kneeWeight)
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
        final float mix=frame.meshContactBlend;
        final float[] skinExit=frame.skinSurface!=null&&mix>0F?
                frame.skinSurface.correctedWorld(point,thickness,
                        .045F*frame.seatedBlend):null;
        if(mix>=.99999F&&skinExit!=null)
        {
            // Deep sitting: one true continuous surface, no OBB seams.
            point=skinExit;
        }
        else for(int boxIndex=0;boxIndex<frame.legBoxes.size();boxIndex++)
        {
            float[] b=frame.legBoxes.get(boxIndex);
            String owner=frame.legOwners.get(boxIndex);
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
            float[] direction=outwardWorld==null?null:
                    rotateInverse(q,outwardWorld);
            // A raised (nearly horizontal) thigh should support the skirt
            // from BELOW. The old unconditional radial exit pushed fabric
            // forward like a rigid shelf whenever the actor sat down.
            //
            // Keep the established radial behavior on vertical walking
            // thighs. This opt-out also permits exact A/B comparisons.
            if(SEATED_CONTACTS ||
                    (BILATERAL_DRAPE&&frame.seatedBlend>.60F))
            {
                // A tapered thigh zone is intentionally SHORT in its
                // bone-local Y direction. Using the LONGEST OBB dimension
                // misidentifies X/Z as the thigh axis after splitting,
                // especially at 70-90 degree seated bends.
                //
                // The fitted upper-leg quaternion is bone-aligned, so
                // local +Y is its longitudinal axis regardless of shape
                // width. A knee/shin OBB is not a thigh support plane.
                boolean raised=isUpperThigh(owner)&&
                        Math.abs(rotate(q,new float[]{0F,1F,0F})[1])<.65F;
                if(raised&&point[1]>=b[1]-.06F)
                {
                    float[] up=rotateInverse(q,new float[]{0F,1F,0F});
                    float topExit=rayExit(axes,limits,up);
                    float outerExit=direction==null?
                            Float.POSITIVE_INFINITY:
                            rayExit(axes,limits,direction);
                    // Only take a nearby top exit: never send fabric
                    // through the thigh from its underside or jump over
                    // the whole leg when a short side exit is available.
                    if(Float.isFinite(topExit)&&topExit<=.28F&&
                            (!Float.isFinite(outerExit)||
                            topExit<=outerExit*1.35F+.025F))
                        direction=up;
                }
            }
            if(direction!=null)
            {
                float ray=rayExit(axes,limits,direction);
                if(Float.isFinite(ray))
                {
                    // Bound a single correction; XPBD iterations may
                    // continue settling overlaps against neighboring OBBs.
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
        if(mix<=0F)
        {
            // Never let the bridge counteract the OBB pass. Only points
            // in the genuine outside-the-box joint gap can receive
            // rounded contact, and their projection must stay outside
            // every fitted leg box as well.
            for(HipBridge bridge:frame.hipBridges)
            {
                if(insideAnyLegBox(point,frame.legBoxes,.002F))break;
                float[] candidate=bridgeExit(point,bridge,thickness,
                        outwardWorld);
                if(!insideAnyLegBox(candidate,frame.legBoxes,.002F))
                    point=candidate;
            }
            if(kneeWeight>0F)
                for(KneeBridge bridge:frame.kneeBridges)
                {
                    if(insideAnyLegBox(point,frame.legBoxes,.002F))break;
                    float[] candidate=roundedExit(point,bridge.a,bridge.b,
                            bridge.radius*sat(kneeWeight),
                            thickness,outwardWorld);
                    if(!insideAnyLegBox(candidate,frame.legBoxes,.002F))
                        point=candidate;
                }
        }
        if(skinExit!=null&&mix>0F&&mix<.99999F)
            for(int k=0;k<3;k++)
                // Between moderate and deep sitting, interpolate the
                // TWO proposed exits from the same original cloth point.
                // Never apply OBB then mesh consecutively, which compounds
                // penetration corrections and bunches the waistband.
                point[k]+=mix*(skinExit[k]-point[k]);
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

    /** Distance from a point inside a local OBB to its first positive
     * exit along a unit ray. Reused for radial and seated/top contacts. */
    private static float rayExit(float[] point,float[] limits,float[] direction)
    {
        float ray=Float.POSITIVE_INFINITY;
        for(int j=0;j<3;j++)
        {
            float d=direction[j];
            if(Math.abs(d)<1E-6F)continue;
            float boundary=d>0F?limits[j]:-limits[j];
            float exit=(boundary-point[j])/d;
            if(exit>=0F&&exit<ray)ray=exit;
        }
        return ray;
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
