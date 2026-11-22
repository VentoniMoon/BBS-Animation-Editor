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
    // Preserve the 60 original Chameleon anchors/UV nodes (three source
    // latitudes x 20 sectors) and experiment with one simulated latitude
    // per user-referenced pixel row: 7 rings, 6 fabric height intervals.
    // Exact A/B: -Dbbs.editor.physics.skirt.xpbdRowCount=5; old 3-ring
    // fallback still works via xpbdFineGrid=false or xpbdRowCount=3.
    // Only ODD values are supported so the native central seam remains
    // mapped exactly onto a physics row (3 source -> 3,5,7 rings).
    public static final int N=60;
    public static final int SEGMENTS=20;
    /** Minimal stable cloth profile for walking and a conservative
     * 3-ring seated collision experiment. All seat-specific cosmetic
     * shape modifications remain available for an explicit A/B, but are
     * inactive here. Restart required to change any system properties.
     * -Dbbs.editor.physics.skirt.xpbdCleanThreeRow=false restores
     * the experimental customizations when their flags are enabled.
     */
    public static final boolean CLEAN_THREE_ROW=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdCleanThreeRow","true"));
    public static final int ROWS=resolveRows();

    private static int resolveRows()
    {
        String raw=System.getProperty(
                "bbs.editor.physics.skirt.xpbdRowCount");
        if(raw==null||raw.trim().isEmpty())
            return Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdFineGrid","true"))?
                    (CLEAN_THREE_ROW?3:7):3;
        int n;
        try{n=Integer.parseInt(raw.trim());}
        catch(NumberFormatException invalid)
        {throw new IllegalArgumentException("Invalid XPBD row count: "+raw);}
        if(n!=3&&n!=5&&n!=7)
            throw new IllegalArgumentException(
                    "xpbdRowCount supports exactly 3, 5, or 7: "+n);
        return n;
    }
    public static final int SIM_NODES=SEGMENTS*ROWS;
    /** Minimal skirt: no extra pelvis capsules, no invented seat guide,
     * no extra ring stiffness. Keep ONLY cloth links + fitted leg boxes
     * and conservative per-vertex collision resolution. */
    public static final boolean ANTI_PINCH_CONTACTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdAntiPinchContacts","true"));
    private static final int ITERATIONS=8;
    private static final int SUBSTEPS=2;
    private static final float GRAVITY=-9.81F;
    private static final float DRAG=.993F;
    /**
     * Sitting should not pull the dangling REAR hem straight down while
     * the front is supported by raised thighs. Instead of removing cloth
     * springs or adding new colliders, gradually reduce only the rear
     * gravity load and the old standing-silhouette bias.
     *
     * Independent A/B (require restart):
     * -Dbbs.editor.physics.skirt.xpbdAdaptiveSeatedGravity=false
     * -Dbbs.editor.physics.skirt.xpbdAdaptiveSeatedShape=false
     */
    static final boolean ADAPTIVE_SEATED_GRAVITY=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdAdaptiveSeatedGravity",
                    "false"))&&!CLEAN_THREE_ROW;
    static final boolean ADAPTIVE_SEATED_SHAPE=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdAdaptiveSeatedShape",
                    "false"))&&!CLEAN_THREE_ROW;
    /**
     * New isolated experiment: when BOTH upper legs raise for sitting,
     * guide the free skirt latitudes around the lap, instead of holding
     * a vertical standing hem or weakening its actual cloth seams.
     * The guide rotates only the standing HANGING portion of the
     * per-column reference; its original lateral flare is preserved.
     *
     * Reproduce the previous solver with:
     * -Dbbs.editor.physics.skirt.xpbdPoseAwareLapGuide=false
     */
    public static final boolean POSE_AWARE_LAP_GUIDE=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdPoseAwareLapGuide","false"))&&
            !CLEAN_THREE_ROW;
    /**
     * PR #248 used the CURRENT animated hem to measure the amount of cloth
     * available to rotate around the lap. That measurement collapses near
     * zero on the exact seated frames where the native bones have already
     * hoisted the hem toward the belt. Use the original garment latitudes
     * and anchored waist instead. Structural XPBD rest LENGTHS are not
     * retuned or replaced.
     *
     * A/B with previous PR #248:
     * -Dbbs.editor.physics.skirt.xpbdRestLengthLapGuide=false
     */
    public static final boolean REST_LENGTH_LAP_GUIDE=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdRestLengthLapGuide","false"))&&
            !CLEAN_THREE_ROW;
    private static final boolean SEATED_GEOMETRY_AUDIT=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedGeometryAudit","false"));

    /** Gradual rotation of the hanging cloth latitude, up to ~60 degrees
     * on the rear hem. Ordinary walking or unilateral leg lift returns 0.
     * No changes to structural XPBD rest lengths. */
    static float lapGuideAngle(float seated,float rear,int row)
    {
        if(!POSE_AWARE_LAP_GUIDE||row<=0)return 0F;
        float t=seatedRelief(seated);
        float sector=.35F+.65F*Math.max(0F,Math.min(1F,rear));
        float rowRatio=row/(float)(ROWS-1);
        return 1.05F*t*sector*(.40F+.60F*rowRatio);
    }


    /** Smooth relief only when BOTH actual thighs bend. The upstream
     * seatedBlend is already zero for ordinary gait / one raised knee. */
    static float seatedRelief(float seated)
    {
        float t=Math.max(0F,Math.min(1F,(seated-.06F)/.86F));
        return t*t*(3F-2F*t);
    }

    static float rearSector(float radialX,float radialZ,
            float forwardX,float forwardZ)
    {
        float r=(float)Math.sqrt(radialX*radialX+radialZ*radialZ);
        float f=(float)Math.sqrt(forwardX*forwardX+forwardZ*forwardZ);
        if(r<1E-6F||f<1E-6F)return 0F;
        float alignment=(radialX*forwardX+radialZ*forwardZ)/(r*f);
        // Back of skirt=1, front over the thigh=0; sides only 1/4.
        return Math.max(0F,Math.min(1F,(.30F-alignment)/1.30F));
    }

    static float clothRowWeight(int row)
    {
        if(row<=0)return 0F;
        float t=row/(float)(ROWS-1);
        // Previously tuned 5-ring control points (.25/.5/.75/1).
        // Interpolate them for 7 rings; old 5-ring values stay exact.
        if(t<=.25F)return .20F*t/.25F;
        if(t<=.50F)return .20F+(.50F-.20F)*(t-.25F)/.25F;
        if(t<=.75F)return .50F+(.78F-.50F)*(t-.50F)/.25F;
        return .78F+.22F*(t-.75F)/.25F;
    }

    /** The seated mode releases only ADDITIONAL artificial stiffness
     * imposed on the first free hoop. Native distance constraints and
     * circumference rest lengths are never dropped. */
    static float upperHoopComplianceScale(float seated)
    {
        // A rotating guide cannot overcome an extra artificial rigid
        // horizontal hoop. Its original circumferential material edges
        // remain intact; release ONLY the seated stiffness multiplier.
        if(POSE_AWARE_LAP_GUIDE&&seated>.06F)
        {
            float t=seatedRelief(seated);
            float old=1F-.85F*seated;
            return old+(1F-old)*t;
        }
        float shape=ADAPTIVE_SEATED_SHAPE?seatedRelief(seated):0F;
        return 1F-.85F*seated*(1F-.80F*shape);
    }

    /** At maximal sitting the back hem retains at least 20% of gravity.
     * Front fabric retains its full weight to settle onto the thigh. */
    static float seatedGravityScale(float seated,int row,float rear)
    {
        if(!ADAPTIVE_SEATED_GRAVITY)return 1F;
        return 1F-.80F*seatedRelief(seated)*
                clothRowWeight(row)*Math.max(0F,Math.min(1F,rear));
    }

    /** Only a large posterior DOWNSWING is damped; true moving-bone
     * velocity and walking inertia are untouched. */
    static float seatedDownwardDamping(float seated,int row,float rear)
    {
        if(!ADAPTIVE_SEATED_GRAVITY)return 1F;
        return 1F-.45F*seatedRelief(seated)*
                clothRowWeight(row)*Math.max(0F,Math.min(1F,rear));
    }

    /**
     * A positional collision projection must NOT create additional Verlet
     * velocity. Without shifting previous[] by the same correction, a leg
     * can eject a cloth node at 60fps and the next step replays that eject
     * as a real impulse. Repeated projections around a bent thigh can
     * catapult the hem into the belt and trap it there.
     *
     * A/B to reproduce original behavior:
     * -Dbbs.editor.physics.skirt.xpbdPreserveContactVelocity=false
     */
    static final boolean PRESERVE_CONTACT_VELOCITY=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdPreserveContactVelocity",
                    "true"));

    /** Transfer an explicitly positional CONTACT correction to Verlet
     * history, preserving physical velocity across the projection.
     * NOTE: XPBD link constraints remain untouched; only collision
     * projections use this method. */
    static void contactMove(float[] current,float[] previous,
            float dx,float dy,float dz)
    {
        current[0]+=dx;current[1]+=dy;current[2]+=dz;
        if(PRESERVE_CONTACT_VELOCITY)
        {
            previous[0]+=dx;previous[1]+=dy;previous[2]+=dz;
        }
    }
    /** A fitted thigh may overlap a fast-moving 3-ring cloth panel. An
     * OBB nearest-face projection can choose the UP face and teleport
     * cloth toward the pinned belt. Reject inward and strongly upward
     * exits; allow bounded lateral/OUTWARD movement. When the fitted
     * collision has no safe exit, some penetration is preferable to
     * crushing the skirt into the waistband.
     *
     * Pure, repeatable and A/B testable. Works identically in preview
     * (dt=0) and 60 Hz integration (dt>0). */
    static float[] boundedThighExit(float[] before,float[] proposed,
            float[] waist,float seated)
    {
        if(!CLEAN_THREE_ROW||!ANTI_PINCH_CONTACTS||seated<=.20F)
            return proposed.clone();
        float t=Math.max(0F,Math.min(1F,(seated-.20F)/.45F));
        t=t*t*(3F-2F*t);
        float dx=proposed[0]-before[0];
        float dy=proposed[1]-before[1];
        float dz=proposed[2]-before[2];
        float x=before[0]-waist[0],z=before[2]-waist[2];
        float length=(float)Math.sqrt(x*x+z*z);
        if(length>1E-5F)
        {
            float nx=x/length,nz=z/length;
            float inward=Math.min(0F,dx*nx+dz*nz);
            dx-=inward*nx;
            dz-=inward*nz;
        }
        // At full bilateral bend do not select the top face of the thigh
        // as an ejector into the belt. Nearby real contact can still move
        // upward a little in transition / gait.
        dy=Math.min(dy,.012F*(1F-t));
        float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(d>.018F)
        {
            float scale=.018F/d;
            dx*=scale;dy*=scale;dz*=scale;
        }
        return new float[]{
                before[0]+(proposed[0]-before[0])*(1F-t)+dx*t,
                before[1]+(proposed[1]-before[1])*(1F-t)+dy*t,
                before[2]+(proposed[2]-before[2])*(1F-t)+dz*t};
    }

    /** Filter a proposed collision exit from an interior triangle or
     * edge sample. Outward displacement is always permitted; a SHORT
     * upward exit onto the upper thigh is permitted only when the sample
     * is on the physically upper side. Never force the sample backward
     * through the leg or up into the pinned waistband.
     *
     * This does not teleport tissue across the thigh. Deep penetration
     * remains gradual because one material node moves <=.012 per frame.
     */
    static float[] safePanelExit(float[] original,float[] proposed,
            float[] waist,boolean supportedAboveThigh,float seated)
    {
        if(original==null||proposed==null||waist==null)
            throw new IllegalArgumentException("Missing panel contact");
        float dx=proposed[0]-original[0];
        float dy=proposed[1]-original[1];
        float dz=proposed[2]-original[2];
        float rx=original[0]-waist[0];
        float rz=original[2]-waist[2];
        float radial=(float)Math.sqrt(rx*rx+rz*rz);
        if(radial>1E-5F)
        {
            float nx=rx/radial,nz=rz/radial;
            float inward=dx*nx+dz*nz;
            if(inward<0F)
            {
                dx-=inward*nx;
                dz-=inward*nz;
            }
        }
        // Do not revive the old blanket no-upward rule: a vertex that
        // is physically above a raised thigh may need a modest UPWARD
        // correction to land on its top instead of escaping behind it.
        // The final solved cloth is still guarded by waist separation.
        float blend=Math.max(0F,Math.min(1F,
                (seated-.55F)/.30F));
        if(!supportedAboveThigh||blend<=0F)
            dy=Math.min(0F,dy);
        else
            // A sample on the upper/lap side must NEVER be ejected
            // downward through the thigh's back face.
            dy=Math.max(0F,Math.min(dy,.014F*blend));
        if(!Float.isFinite(dx)||!Float.isFinite(dy)||
                !Float.isFinite(dz))
            return new float[]{0F,0F,0F};
        float magnitude=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(magnitude>SAFE_PANEL_MAX_NODE_STEP)
        {
            float gain=SAFE_PANEL_MAX_NODE_STEP/magnitude;
            dx*=gain;dy*=gain;dz*=gain;
        }
        return new float[]{dx,dy,dz};
    }

    private static final float CONTACT_RADIUS=.018F;
    private static final float SELF_RADIUS=.030F;
    private static final float MAX_OFFSET=.37F;
    private static final float POSE_JUMP_RESET=.40F;
    /**
     * Controlled A/B: free skirt latitudes should not inherit the full
     * vertical rotation/translation of their authored Chameleon bones
     * when BOTH real thighs are deeply seated. The belt stays pinned to
     * the native pose; the simulated cloth follows the belt displacement
     * and its own constraints/collisions.
     *
     * Original solver: -Dbbs.editor.physics.skirt.xpbdSeatedPoseIsolation=false
     */
    public static final boolean SEATED_POSE_ISOLATION=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedPoseIsolation",
                    "true"));
    private static final float MIN_RADIAL_FRACTION=.85F;
    // The normal 0.37-block leash and 85% radial silhouette work for
    // walking, but forbid folds over both horizontal thighs. Relax only
    // when the actual two fitted upper-leg bones agree on a seated pose.
    private static final float SEATED_EXTRA_OFFSET=.28F;
    private static final float SEATED_RADIAL_RELIEF=.38F;
    // When both thighs are horizontal, the generic .47x radial minimum
    // allows the upper rows to fold INSIDE the lap. Preserve the waist-to-
    // cloth direction without freezing the hem or widening the standing
    // pose. A/B: -Dbbs.editor.physics.skirt.xpbdSeatedCollapseGuard=false
    private static final boolean SEATED_COLLAPSE_GUARD=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedCollapseGuard","true"))&&
            !CLEAN_THREE_ROW;
    private static final boolean SEATED_FOLD=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdSeatedFold","true"));
    // Keep the first free ring below the belt from expanding when the
    // thighs raise; a cap is applied ONLY if the proposed position is
    // outside the real leg hull. A/B: -D...xpbdSeatedUpperControl=false
    private static final boolean SEATED_UPPER_CONTROL=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedUpperControl","true"))&&
            !CLEAN_THREE_ROW;
    private static final boolean SEATED_BEND_RELIEF=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedBendRelief","true"))&&
            !CLEAN_THREE_ROW;
    // Contact must be evaluated on the triangles BETWEEN cloth nodes:
    // a thigh can intersect a polygon without containing any vertex.
    // Keep this additional work restricted to a deep bilateral seat.
    private static final boolean SEATED_SURFACE_CONTACTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSeatedSurfaceContacts","true"))&&
            !CLEAN_THREE_ROW;
    private static final float MAX_SURFACE_CORRECTION=.045F;
    /**
     * Replacement for the old repeated Gauss-Seidel triangle pushes on
     * the minimal three-row skirt. The old contact ONLY checked vertices;
     * a whole large polygon can tunnel through a raised thigh without a
     * single vertex intersecting it.
     *
     * Apply once per distinct timeline sample, with Jacobi-averaged,
     * outward/top-only displacements limited to 0.012 model units per
     * free node. This is deliberately NOT the old repeated per-iteration
     * seated triangle pressure that caused bell shapes and jitter.
     *
     * Switch off for exact PR #253 behavior:
     * -Dbbs.editor.physics.skirt.xpbdSafePanelContacts=false
     */
    public static final boolean SAFE_PANEL_CONTACTS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSafePanelContacts",
                    "true"))&&CLEAN_THREE_ROW;
    private static final float SAFE_PANEL_MAX_NODE_STEP=.012F;
    private static final boolean SAFE_PANEL_AUDIT=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.xpbdSafePanelAudit",
                    "false"));
    private static final boolean RADIAL_GUARD=Boolean.parseBoolean(
            System.getProperty("bbs.editor.physics.skirt.xpbdRadialGuard","true"))&&
            !CLEAN_THREE_ROW;
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
        /** Size of the NEW rest-length lap shape requested this sample.
         * This is measured before XPBD constraints or leg collisions. */
        public final float maxGuideTargetMotion;
        /** Mean signed fraction of requested lap move still present in the
         * solved nodes after ALL links, collisions and silhouette guards.
         * Values near zero/negative indicate an active opposition. */
        public final float meanGuideFollow;
        /** Round pelvis-to-thigh capsules actually active in this frame. */
        public final int hipBridgeCount;
        private Result(float[][] nativePoints,float[][] finePoints,
                float[][] fineTargets,int[] map,int legs,int self,int steps,
                float stretch,float guideMax,float guideFollow,int hips)
        {
            particles=copy(nativePoints);
            refinedParticles=copy(finePoints);
            refinedTarget=copy(fineTargets);
            sourceToRefined=map.clone();
            legContacts=legs;selfContacts=self;substeps=steps;
            maxDistanceError=stretch;
            maxGuideTargetMotion=guideMax;
            meanGuideFollow=guideFollow;
            hipBridgeCount=hips;
        }
    }
    public static final class State
    {
        private final float[][] position=new float[SIM_NODES][3];
        private final float[][] previous=new float[SIM_NODES][3];
        private final float[][] previousPoseTarget=new float[SIM_NODES][3];
        /** Free drape reference: held near the last unbent garment shape.
         * Advected by the belt, NOT by fully folded native lower bones. */
        private final float[][] seatedReference=new float[SIM_NODES][3];
        /** Last pose-aware guide offsets are stored separately from the
         * native pose advection so neither movement is applied twice. */
        private final float[][] lapGuideOffset=new float[SIM_NODES][3];
        private float lastGuideMaximum,lastGuideFollow;
        private int lastHipBridgeCount;
        private double lastAuditSample=Double.NaN;
        /** Do not re-apply a panel projection on extra shadow, mirror,
         * paused frame or FBO passes with an unchanged pose. */
        private double lastSafePanelSample=Double.NaN;
        private int lastSafePanelProbes;
        private final float[][] restFine=new float[SIM_NODES][3];
        private final boolean[] pinned=new boolean[SIM_NODES];
        private final int[] column=new int[SIM_NODES];
        private final int[] sourceToFine=new int[N];
        private final int[][] originalRing=new int[3][SEGMENTS];
        private final int[][] ring=new int[ROWS][SEGMENTS];
        private final List<Constraint> links=new ArrayList<Constraint>();
        private final List<int[]> triangles=new ArrayList<int[]>();
        private final Set<Long> adjacent=new HashSet<Long>();
        /** Raised thighs establish the forward/lap direction in WORLD
         * space. No guessed model-forward sign, camera angle or fake box.
         * When the fitted bodies are unavailable the old gravity is kept. */
        private float[] seatedForward(SkirtXPBDContacts.Frame bodies,
                float[] waist)
        {
            if(bodies==null||bodies.seatedBlend<=.06F||
                    !SkirtXPBDContacts.matrixValid(bodies.modelToWorld))
                return null;
            float[] m=bodies.modelToWorld;
            float wx=m[0]*waist[0]+m[4]*waist[1]+m[8]*waist[2]+m[12];
            float wz=m[2]*waist[0]+m[6]*waist[1]+m[10]*waist[2]+m[14];
            float dx=0F,dz=0F;
            int count=0;
            for(int i=0;i<bodies.legBoxes.size();i++)
            {
                String n=bodies.legOwners.get(i);
                if(!n.startsWith("emoticons.")||
                        !(n.contains("left_leg")||n.contains("right_leg")||
                        n.contains("leg_left")||n.contains("leg_right")||
                        n.contains("thigh_left")||n.contains("thigh_right")||
                        n.contains("left_thigh")||n.contains("right_thigh"))||
                        n.contains("low_leg")||n.contains("lower")||
                        n.contains("knee")||n.contains("shin"))
                    continue;
                float[] b=bodies.legBoxes.get(i);
                dx+=b[0]-wx;dz+=b[2]-wz;
                count++;
            }
            if(count==0)return null;
            float length=(float)Math.sqrt(dx*dx+dz*dz);
            if(length<.05F*count)return null;
            return new float[]{dx/length,dz/length};
        }

        /** Stable sector mask from the native/rest drape before the
         * collision pass. Never classify the CURRENT crumpled particles:
         * that would feed a collapse back into the gravity force. */
        private float[] rearMask(float[][] nativeReference,float[] waist,
                SkirtXPBDContacts.Frame bodies)
        {
            float[] values=new float[SIM_NODES];
            float[] fwd=seatedForward(bodies,waist);
            if(fwd==null)return values;
            float[] m=bodies.modelToWorld;
            float wx=m[0]*waist[0]+m[4]*waist[1]+m[8]*waist[2]+m[12];
            float wz=m[2]*waist[0]+m[6]*waist[1]+m[10]*waist[2]+m[14];
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n])continue;
                float[] p=nativeReference[n];
                float x=m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12]-wx;
                float z=m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]-wz;
                values[n]=rearSector(x,z,fwd[0],fwd[1]);
            }
            return values;
        }

        /** Invert the full actor 3x3, not just yaw. The world-space
         * upper-thigh direction must remain correct for rotated actors. */
        private float[] localLapDirection(float[] world,
                SkirtXPBDContacts.Frame frame)
        {
            if(world==null||frame==null)return null;
            float[] m=frame.modelToWorld;
            float a=m[0],b=m[4],c=m[8],
                    d=m[1],e=m[5],f=m[9],
                    g=m[2],h=m[6],i=m[10];
            float A=e*i-f*h,D=f*g-d*i,G=d*h-e*g;
            float determinant=a*A+b*D+c*G;
            if(!Float.isFinite(determinant)||
                    Math.abs(determinant)<1E-8F)return null;
            float x=(A*world[0]+(b*f-c*e)*world[1])/determinant;
            float z=(G*world[0]+(a*e-b*d)*world[1])/determinant;
            float size=(float)Math.sqrt(x*x+z*z);
            if(!Float.isFinite(size)||size<1E-5F)return null;
            return new float[]{x/size,0F,z/size};
        }

        /**
         * Pose-aware lap reference. Unlike a new collider or force, this
         * supplies a DIFFERENT target shape to the SAME free cloth nodes.
         * Each longitudinal column tilts its dangling vertical distance
         * toward the measured thigh direction. Original lateral flaring,
         * belt pins and every structural/shear/bending rest length stay as
         * authored. The solved mesh remains free to settle with contact.
         *
         * The returned offset is separate from native pose movement; it
         * can be incrementally advected once per scene sample, including
         * a paused/scrubbed pose. No repeated same-frame motion.
         */
        private void lapReference(float[][] physicalTarget,float[] waist,
                SkirtXPBDContacts.Frame bodies,float[] rearWeight,
                float[][] desiredOffsets)
        {
            if(!POSE_AWARE_LAP_GUIDE||
                    bodies==null||bodies.seatedBlend<=.06F)return;
            float[] forward=localLapDirection(
                    seatedForward(bodies,waist),bodies);
            if(forward==null)return;
            if(!REST_LENGTH_LAP_GUIDE)
            {
                // Exact old PR #248 behavior for A/B comparison.
                for(int row=1;row<ROWS;row++)
                    for(int c=0;c<SEGMENTS;c++)
                    {
                        int n=ring[row][c];
                        float[] belt=physicalTarget[ring[0][c]];
                        float hang=belt[1]-physicalTarget[n][1];
                        if(hang<=.01F)continue;
                        float angle=lapGuideAngle(
                                bodies.seatedBlend,rearWeight[n],row);
                        if(angle<=1E-5F)continue;
                        float run=hang*(float)Math.sin(angle);
                        float rise=hang*(1F-(float)Math.cos(angle));
                        desiredOffsets[n][0]=forward[0]*run;
                        desiredOffsets[n][1]=rise;
                        desiredOffsets[n][2]=forward[2]*run;
                    }
                return;
            }
            // Unlike the animated/current pose, restFine cannot collapse
            // when the actor suddenly bends at hip angle ~75 degrees.
            // Build each longitudinal row by accumulating its ORIGINAL
            // physical segments (two original vertical panels, now split
            // into four). Turning each descending segment toward the
            // actual lap makes a curved reference without shortening any
            // standing vertical section or moving the pinned top ring.
            float seatedGain=seatedRelief(bodies.seatedBlend);
            for(int c=0;c<SEGMENTS;c++)
            {
                int waistNode=ring[0][c];
                float[] accumulated=physicalTarget[waistNode].clone();
                for(int row=1;row<ROWS;row++)
                {
                    int n=ring[row][c],above=ring[row-1][c];
                    float[] rest=restFine[n],prior=restFine[above];
                    float radialX=rest[0]-restFine[waistNode][0];
                    float radialZ=rest[2]-restFine[waistNode][2];
                    float rear=rearSector(radialX,radialZ,
                            forward[0],forward[2]);
                    float angle=lapGuideAngle(
                            bodies.seatedBlend,rear,row);
                    float hanging=Math.max(0F,prior[1]-rest[1]);
                    for(int k=0;k<3;k++)
                        accumulated[k]+=rest[k]-prior[k];
                    accumulated[0]+=forward[0]*hanging*
                            (float)Math.sin(angle);
                    accumulated[1]+=hanging*(1F-(float)Math.cos(angle));
                    accumulated[2]+=forward[2]*hanging*
                            (float)Math.sin(angle);
                    // No abrupt teleport when going from walking to
                    // seating: native pose remains primary while the real
                    // two-thigh bend grows. The offset is toward this
                    // independent supported drape reference.
                    for(int k=0;k<3;k++)
                        desiredOffsets[n][k]=seatedGain*
                                (accumulated[k]-physicalTarget[n][k]);
                }
            }
        }

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
                System.arraycopy(fineTarget[i],0,seatedReference[i],0,3);
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
            // Blend is driven only by both genuine upper-thigh orientations;
            // unilateral high steps and upright walking get EXACTLY the old
            // native per-node advection and silhouette reference.
            float drape=0F;
            if(SEATED_POSE_ISOLATION&&seated>.10F)
            {
                drape=Math.min(1F,Math.max(0F,(seated-.10F)/.70F));
                drape=drape*drape*(3F-2F*drape);
            }
            float maxWaist=0F,maxPoseShift=0F;
            float[] waistDelta=new float[3];
            int waistCount=0;
            for(int n=0;n<SIM_NODES;n++)
            {
                valid(fineTarget[n]);
                maxPoseShift=Math.max(maxPoseShift,
                        distance(previousPoseTarget[n],fineTarget[n]));
                if(pinned[n])
                {
                    maxWaist=Math.max(maxWaist,
                            distance(position[n],fineTarget[n]));
                    for(int k=0;k<3;k++)
                        waistDelta[k]+=fineTarget[n][k]-
                                previousPoseTarget[n][k];
                    waistCount++;
                }
            }
            if(waistCount>0)
                for(int k=0;k<3;k++)waistDelta[k]/=waistCount;
            // A seated garment can have large *authored lower-bone* jumps
            // while its fixed belt hardly moves. Do not reset its physics
            // onto a folded native mesh for those jumps. Still reset for
            // actual waist teleports and very large seek discontinuities.
            boolean reset=maxWaist>.60F||
                    maxPoseShift>(drape>.50F?1.00F:POSE_JUMP_RESET);
            float[][] physicalTarget=new float[SIM_NODES][3];
            for(int n=0;n<SIM_NODES;n++)
            {
                if(reset)
                {
                    System.arraycopy(fineTarget[n],0,position[n],0,3);
                    System.arraycopy(fineTarget[n],0,previous[n],0,3);
                    System.arraycopy(fineTarget[n],0,seatedReference[n],0,3);
                    for(int k=0;k<3;k++)lapGuideOffset[n][k]=0F;
                }
                else if(!pinned[n])
                {
                    for(int k=0;k<3;k++)
                    {
                        float nativeDelta=fineTarget[n][k]-
                                previousPoseTarget[n][k];
                        // At full sitting this is BELT movement only;
                        // before sitting it is ordinary native deformation.
                        float step=nativeDelta*(1F-drape)+
                                waistDelta[k]*drape;
                        position[n][k]+=step;
                        previous[n][k]+=step;
                        seatedReference[n][k]+=step;
                    }
                }
                if(pinned[n])
                {
                    System.arraycopy(fineTarget[n],0,position[n],0,3);
                    System.arraycopy(fineTarget[n],0,seatedReference[n],0,3);
                }
                if(drape<=1E-5F)
                    System.arraycopy(fineTarget[n],0,seatedReference[n],0,3);
                else if(!pinned[n])
                    for(int k=0;k<3;k++)
                    {
                        // Converge gradually back to the native garment as
                        // the actor STANDS again, without snapping the hem.
                        float recovery=.30F*(1F-drape);
                        seatedReference[n][k]+=
                                (fineTarget[n][k]-seatedReference[n][k])*
                                recovery;
                    }
                for(int k=0;k<3;k++)
                    physicalTarget[n][k]=fineTarget[n][k]*(1F-drape)+
                            seatedReference[n][k]*drape;
                System.arraycopy(fineTarget[n],0,previousPoseTarget[n],0,3);
            }
            float[] waistCenter=waistCenter(fineTarget);
            float[] rearWeight=rearMask(
                    SEATED_POSE_ISOLATION?seatedReference:fineTarget,
                    waistCenter,bodies);
            float shapeRelief=ADAPTIVE_SEATED_SHAPE?
                    seatedRelief(seated):0F;
            float[][] guide=new float[SIM_NODES][3];
            lapReference(physicalTarget,waistCenter,bodies,
                    rearWeight,guide);
            lastGuideMaximum=0F;
            lastHipBridgeCount=bodies==null?0:bodies.hipBridges.size();
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n])continue;
                lastGuideMaximum=Math.max(lastGuideMaximum,
                        length(guide[n]));
                for(int k=0;k<3;k++)
                {
                    // Follow the CHANGE in the curved lap reference;
                    // do not reapply its whole translation every frame.
                    float delta=guide[n][k]-lapGuideOffset[n][k];
                    if(POSE_AWARE_LAP_GUIDE)
                    {
                        float move=delta*.72F;
                        position[n][k]+=move;
                        previous[n][k]+=move;
                    }
                    lapGuideOffset[n][k]=guide[n][k];
                    physicalTarget[n][k]+=guide[n][k];
                }
            }
            // Pausing must freeze integration, not freeze outdated pose
            // coordinates or leave an already collapsed silhouette behind.
            if(dt<1E-6F)
            {
                // Playback can render two fractional poses between fixed
                // 60Hz steps. The latest FITTED leg positions must still
                // push any cloth node outside them on that display frame.
                // This pass does not integrate gravity, change constraints,
                // or inject Verlet velocity on a repeated FBO render.
                int contacts=resolveDisplayContacts(physicalTarget,bodies,waistCenter);
                contacts+=resolveSurfaceContacts(physicalTarget,bodies,
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
                        for(Constraint c:links)
                            project(c,relaxDt,seated,shapeRelief);
                        for(int n=0;n<SIM_NODES;n++)
                            if(pinned[n])
                                System.arraycopy(fineTarget[n],0,
                                        position[n],0,3);
                        // Finish every pass with body contact projection,
                        // not a link correction that pushes cloth into legs.
                        contacts+=resolveDisplayContacts(physicalTarget,bodies,waistCenter);
                        if(settle<2)
                            contacts+=resolveSurfaceContacts(physicalTarget,bodies,
                                    waistCenter,seated);
                    }
                    // Paused FBO, mirror and shadow passes must not turn
                    // these collision corrections into physical velocity.
                    for(int n=0;n<SIM_NODES;n++)
                        if(!pinned[n])
                            System.arraycopy(position[n],0,previous[n],0,3);
                }
                protectRadialSilhouette(physicalTarget,waistCenter,
                        seated,maxOffset);
                stabilizeUpperRings(physicalTarget,waistCenter,bodies,seated);
                keepFabricOutOfWaist(fineTarget,seated);
                // Do this last: previous vertex/edge relaxation must not
                // pull a just-lifted interior panel back through the leg.
                contacts+=resolveSafePanelContacts(
                        physicalTarget,bodies,waistCenter,seated);
                measureGuide(physicalTarget,guide);
                auditSeat(bodies,0,contacts);
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
                    int row=n/SEGMENTS;
                    // Downward motion is the source of the pulling rear
                    // panels. Preserve upward/sideways animation movement.
                    if(vy<0F)
                        vy*=seatedDownwardDamping(
                                seated,row,rearWeight[n]);
                    for(int k=0;k<3;k++)prev[k]=p[k];
                    p[0]+=vx;
                    p[1]+=vy+GRAVITY*
                            seatedGravityScale(seated,row,rearWeight[n])*
                            h*h;
                    p[2]+=vz;
                    // The standing silhouette is only a weak guidance
                    // target when the cloth is draping over a raised lap.
                    // Keep actual distance/shear links intact.
                    float pull=.012F*(1F-.75F*seated);
                    pull*=1F-shapeRelief*(.45F+.40F*rearWeight[n]);
                    if(POSE_AWARE_LAP_GUIDE&&REST_LENGTH_LAP_GUIDE)
                        // The standing silhouette had almost no influence
                        // when first seated. The new rest-based lap guide
                        // needs modest, soft attraction through successive
                        // 60 Hz frames, not just one initial position jump.
                        pull=Math.max(pull,.025F*seatedRelief(seated));
                    for(int k=0;k<3;k++)
                        p[k]+=(physicalTarget[n][k]-p[k])*pull;
                }

                for(Constraint c:links)c.lambda=0F;
                for(int iteration=0;iteration<ITERATIONS;iteration++)
                {
                    for(Constraint c:links)
                        project(c,h,seated,shapeRelief);
                    for(int n=0;n<SIM_NODES;n++)
                    {
                        if(pinned[n])continue;
                        if(bodies!=null)
                        {
                            float[] outward={
                                    physicalTarget[n][0]-waistCenter[0],
                                    0F,physicalTarget[n][2]-waistCenter[2]};
                            float[] corrected=SkirtXPBDContacts.corrected(
                                    position[n],bodies,
                                    clothContactRadius(n,bodies),outward,
                                    kneeContactWeight(n));
                            corrected=boundedThighExit(position[n],corrected,
                                    waistCenter,seated);
                            if(distance(corrected,position[n])>1E-7F)
                            {
                                // Both fitted OBB and optional skinned
                                // contacts are positional projections, not
                                // accelerations. The old OBB-only branch
                                // omitted previous[] compensation.
                                contactMove(position[n],previous[n],
                                        corrected[0]-position[n][0],
                                        corrected[1]-position[n][1],
                                        corrected[2]-position[n][2]);
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
                            legs+=resolveSurfaceContacts(physicalTarget,bodies,
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
                    // CRITICAL: a seated free drape cannot be transported
                    // relative to the waist while its hard pose leash still
                    // uses the folded native Chameleon hem. The mismatch
                    // pulls every node back up toward the original bones
                    // on EACH substep, recreating the waistband bunching.
                    // physicalTarget is identical to fineTarget when
                    // walking or the A/B flag is disabled.
                    float[] p=position[n],ref=physicalTarget[n];
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
                legs+=resolveDisplayContacts(physicalTarget,bodies,waistCenter);
                legs+=resolveSurfaceContacts(physicalTarget,bodies,
                        waistCenter,seated);
            }
            protectRadialSilhouette(physicalTarget,waistCenter,seated,maxOffset);
            stabilizeUpperRings(physicalTarget,waistCenter,bodies,seated);
            keepFabricOutOfWaist(fineTarget,seated);
            legs+=resolveSafePanelContacts(
                    physicalTarget,bodies,waistCenter,seated);
            measureGuide(physicalTarget,guide);
            auditSeat(bodies,SUBSTEPS,legs);
            stepCount++;
            return result(legs,self,SUBSTEPS);
        }

        /**
         * Lightweight 3-row area contact: sample the barycenter AND three
         * edge midpoints of the actual visible cloth triangles. Existing
         * node contacts cannot detect a small OBB piercing this interior.
         *
         * Corrections are evaluated against an immutable per-frame node
         * snapshot and accumulated Jacobi-style. Each free vertex receives
         * one averaged contactMove (which also shifts Verlet previous[]),
         * with a strict .012 cap. This runs ONCE per distinct scene tick,
         * not eight times per XPBD constraint iteration or six times in
         * paused FBO display. Walking and unilateral leg lifts are exact
         * legacy behavior.
         */
        private int resolveSafePanelContacts(float[][] fineTarget,
                SkirtXPBDContacts.Frame bodies,float[] waist,
                float seated)
        {
            if(!SAFE_PANEL_CONTACTS||bodies==null||seated<=.55F)
                return 0;
            if(Double.isFinite(lastSafePanelSample)&&
                    Math.abs(lastSafePanelSample-bodies.sampleTick)<1E-5D)
                return 0;
            lastSafePanelSample=bodies.sampleTick;
            lastSafePanelProbes=0;
            float[][] snapshot=copy(position);
            float[][] accumulated=new float[SIM_NODES][3];
            float[] weights=new float[SIM_NODES];
            final float[][] probes={
                    {1F/3F,1F/3F,1F/3F},
                    {.5F,.5F,0F},
                    {0F,.5F,.5F},
                    {.5F,0F,.5F}};
            float[] m=bodies.modelToWorld;
            float wx=m[0]*waist[0]+m[4]*waist[1]+m[8]*waist[2]+m[12];
            float wy=m[1]*waist[0]+m[5]*waist[1]+m[9]*waist[2]+m[13];
            float wz=m[2]*waist[0]+m[6]*waist[1]+m[10]*waist[2]+m[14];
            for(int[] tri:triangles)
                for(float[] weights3:probes)
                {
                    float[] sample=new float[3];
                    float[] outward=new float[3];
                    float denom=0F;
                    for(int j=0;j<3;j++)
                    {
                        int n=tri[j];
                        float w=weights3[j];
                        if(!pinned[n])denom+=w*w;
                        for(int k=0;k<3;k++)
                        {
                            sample[k]+=snapshot[n][k]*w;
                            outward[k]+=fineTarget[n][k]*w;
                        }
                    }
                    if(denom<1E-8F)continue;
                    outward[0]-=waist[0];outward[1]=0F;
                    outward[2]-=waist[2];
                    // Resolve the same real Jolt-fitted leg OBBs that
                    // the visible three-ring skirt already uses.
                    float[] corrected=SkirtXPBDContacts.corrected(
                            sample,bodies,CONTACT_RADIUS,outward,0F);
                    float depth=distance(sample,corrected);
                    if(!Float.isFinite(depth)||depth<.0002F)continue;
                    float sx=m[0]*sample[0]+m[4]*sample[1]+
                            m[8]*sample[2]+m[12];
                    float sy=m[1]*sample[0]+m[5]*sample[1]+
                            m[9]*sample[2]+m[13];
                    float sz=m[2]*sample[0]+m[6]*sample[1]+
                            m[10]*sample[2]+m[14];
                    boolean overThigh=false;
                    // Above a raised upper-thigh OBB, not above the knee
                    // or beneath the leg. Use the real world-space top,
                    // independent of camera orientation.
                    if(sy<wy-.025F)
                        for(int box=0;box<bodies.legBoxes.size();box++)
                        {
                            String owner=bodies.legOwners.get(box);
                            int suffix=owner.indexOf('@');
                            String base=suffix<0?owner:
                                    owner.substring(0,suffix);
                            if(!BodyPartJoltContactBudget.isUpperThigh(base))
                                continue;
                            float[] b=bodies.legBoxes.get(box);
                            float ax=sx-b[0],az=sz-b[2];
                            float near=b[7]+b[9]+.10F;
                            if(ax*ax+az*az<=near*near &&
                                    sy>=b[1]-.045F)
                            {overThigh=true;break;}
                        }
                    float[] delta=safePanelExit(sample,corrected,
                            waist,overThigh,seated);
                    float magnitude=length(delta);
                    if(magnitude<.00001F)continue;
                    for(int j=0;j<3;j++)
                    {
                        int n=tri[j];
                        if(pinned[n]||weights3[j]<=0F)continue;
                        float gain=weights3[j]/denom;
                        for(int k=0;k<3;k++)
                            accumulated[n][k]+=delta[k]*gain;
                        weights[n]+=1F;
                    }
                    lastSafePanelProbes++;
                }
            int moved=0;
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n]||weights[n]<.5F)continue;
                float[] shift=accumulated[n];
                float dx=shift[0]/weights[n],dy=shift[1]/weights[n],
                        dz=shift[2]/weights[n];
                float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(length<1E-6F)continue;
                if(length>SAFE_PANEL_MAX_NODE_STEP)
                {
                    float scale=SAFE_PANEL_MAX_NODE_STEP/length;
                    dx*=scale;dy*=scale;dz*=scale;
                }
                contactMove(position[n],previous[n],dx,dy,dz);
                moved++;
            }
            if(SAFE_PANEL_AUDIT)
                System.out.println("[BBS Editor][SkirtSafePanel] tick="+
                        bodies.sampleTick+" seated="+seated+
                        " panelProbes="+lastSafePanelProbes+
                        " movedVertices="+moved+" maxStep="+
                        SAFE_PANEL_MAX_NODE_STEP);
            return moved;
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
                float freeWeight=0F;
                float[] sample=new float[3];
                float[] outward=new float[3];
                for(int index:tri)
                {
                    if(!pinned[index])
                        freeWeight+=seatedContactWeight(index);
                    for(int k=0;k<3;k++)
                        sample[k]+=position[index][k]/3F;
                    outward[0]+=fineTarget[index][0]/3F;
                    outward[2]+=fineTarget[index][2]/3F;
                }
                if(freeWeight<=0F)continue;
                outward[0]-=center[0];
                outward[2]-=center[2];
                float surfaceRadius=0F,kneeWeight=0F;
                for(int index:tri)
                {
                    surfaceRadius+=clothContactRadius(index,bodies)/3F;
                    kneeWeight+=kneeContactWeight(index)/3F;
                }
                float[] clearance=SkirtXPBDContacts.corrected(
                        sample,bodies,surfaceRadius,outward,kneeWeight);
                float depth=distance(sample,clearance);
                if(!Float.isFinite(depth)||depth<=1E-5F)continue;

                // Three barycentric weights; fixed contact cap, and an
                // averaged single update per cloth node, not per triangle.
                float ratio=Math.min(depth,MAX_SURFACE_CORRECTION)*
                        strength/depth*(3F/freeWeight);
                for(int index:tri)
                {
                    if(pinned[index])continue;
                    for(int k=0;k<3;k++)
                        accumulated[index][k]+=
                                (clearance[k]-sample[k])*ratio*
                                seatedContactWeight(index);
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

        /**
         * First cloth latitude is close to the pinned belt: let the
         * lower latitudes absorb more of a thigh surface displacement.
         * Weight is used only for the seated triangle contact correction;
         * direct vertex-OBB clearance remains authoritative.
         */
        private float seatedContactWeight(int index)
        {
            if(!SEATED_UPPER_CONTROL)return 1F;
            int row=index/SEGMENTS;
            float t=row/(float)(ROWS-1);
            if(t<=.25F)return .30F*t/.25F;
            if(t<=.50F)return .30F+.35F*(t-.25F)/.25F;
            return Math.min(1F,.65F+.70F*(t-.50F));
        }

        /**
         * The first free latitude is just under the pinned waist. A full
         * .018m skin clearance on that ring creates a visible ledge on a
         * cube-like Emoticons thigh. Taper only the mesh-based collision
         * shell, never the legacy OBB path used for walking fallback.
         * This does NOT weaken garment constraints or move the waist pins.
         */
        private float clothContactRadius(int index,
                SkirtXPBDContacts.Frame bodies)
        {
            if(bodies==null||bodies.meshContactBlend<=0F)
                return CONTACT_RADIUS;
            int row=index/SEGMENTS;
            float t=row/(float)(ROWS-1);
            float skinRadius=t<=.25F?.008F:
                    t<=.5F?.008F+(.013F-.008F)*(t-.25F)/.25F:
                    t<=.75F?.013F+(CONTACT_RADIUS-.013F)*
                            (t-.50F)/.25F:CONTACT_RADIUS;
            return CONTACT_RADIUS+
                    bodies.meshContactBlend*(skinRadius-CONTACT_RADIUS);
        }

        /**
         * A knee gap should affect the lower drape, not the pinned belt
         * or upper skirt. In the 5-row refinement, the last two rows get
         * most of the contact; the original 3-row mode is also supported.
         */
        private float kneeContactWeight(int index)
        {
            int row=index/SEGMENTS;
            float t=row/(float)(ROWS-1);
            if(t<=.25F)return 0F;
            if(t<=.50F)return (t-.25F);
            if(t<=.75F)return .25F+1.6F*(t-.50F);
            return Math.min(1F,.65F+1.4F*(t-.75F));
        }

        /** A very small one-sided separation guard; it only fires when a
         * free cloth latitude is crushed close to its own fixed belt point.
         * This guards against the exact waistband bunching reported at
         * strong thigh bend WITHOUT holding the native standing silhouette
         * or inventing a rigid platform. Vertical motion away from the
         * belt is allowed; belt points remain physically pinned. */
        private void keepFabricOutOfWaist(float[][] reference,float seated)
        {
            if(!CLEAN_THREE_ROW||!ANTI_PINCH_CONTACTS||
                    seated<=.35F)return;
            float t=Math.min(1F,(seated-.35F)/.40F);
            t=t*t*(3F-2F*t);
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n])continue;
                int belt=ring[0][n%SEGMENTS];
                float source=distance(restFine[n],restFine[belt]);
                float minimum=source*.49F;
                float[] p=position[n],waist=reference[belt];
                float dx=p[0]-waist[0],dy=p[1]-waist[1],
                        dz=p[2]-waist[2];
                float distance=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(distance>=minimum||minimum<1E-5F)continue;
                float ux,uy,uz;
                if(distance>1E-5F)
                {
                    ux=dx/distance;uy=dy/distance;uz=dz/distance;
                    if(uy>0F)uy=0F;
                }
                else
                {
                    ux=restFine[n][0]-restFine[belt][0];
                    uy=Math.min(0F,
                            restFine[n][1]-restFine[belt][1]);
                    uz=restFine[n][2]-restFine[belt][2];
                }
                float norm=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);
                if(norm<1E-5F){ux=0F;uy=-1F;uz=0F;norm=1F;}
                float correction=Math.min(.025F,minimum-distance)*t/norm;
                contactMove(p,previous[n],ux*correction,
                        uy*correction,uz*correction);
            }
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
                            position[n],bodies,
                            clothContactRadius(n,bodies),outward,
                            kneeContactWeight(n));
                    corrected=boundedThighExit(position[n],corrected,
                            center,bodies.seatedBlend);
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
                if(SEATED_COLLAPSE_GUARD&&seated>.60F)
                {
                    // Keep the first two free latitudes outside the lap,
                    // while the lower fabric still has room to gather and
                    // drape over the knees. This is a MINIMUM, never an
                    // expansion beyond the garment's own posed outline.
                    int row=n/SEGMENTS;
                    float depth=row/(float)(ROWS-1);
                    float floor=depth<=.25F?.79F:
                            depth<=.50F?.79F-.09F*(depth-.25F)/.25F:
                            depth<=.75F?.70F-.09F*(depth-.50F)/.25F:
                            .61F;
                    float blend=Math.min(1F,(seated-.60F)/.35F);
                    fraction=Math.max(fraction,
                            .47F+(floor-.47F)*blend);
                }
                // The minimum standing radius is aesthetic, not a
                // fabric-length constraint. Allow curved posterior panels
                // to take up a little more slack at deep bilateral bend.
                if(ADAPTIVE_SEATED_SHAPE)
                    fraction=Math.max(.45F,
                            fraction-.10F*seatedRelief(seated));
                if(POSE_AWARE_LAP_GUIDE&&REST_LENGTH_LAP_GUIDE)
                    // An aesthetic standing-radius floor must not re-pin
                    // the seated hem to its previous vertical silhouette.
                    // Real leg contact and structural fabric links remain.
                    fraction=Math.max(.45F,
                            fraction-.14F*seatedRelief(seated));
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
            int upper=(ROWS-1)/2;
            for(int row=1;row<=upper;row++)
            {
                float depth=row/(float)(ROWS-1);
                float blend=Math.max(0F,Math.min(1F,
                        (depth-.25F)/.25F));
                float extra=.08F+.09F*blend;
                float margin=.018F+.007F*blend;
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
                    // Keep the proven upper hoop perimeter cap. Unlike
                    // the old vertical-target guide, this cap prevents a
                    // seated bell without restricting actual Y/folds.
                    float desired=actual-(actual-limit)*seated;
                    float scale=desired/actual;
                    float nx=center[0]+px*scale,
                            nz=center[2]+pz*scale;
                    float[] proposed={nx,position[n][1],nz};
                    if(bodies!=null)
                    {
                        float[] pushed=SkirtXPBDContacts.corrected(
                                proposed,bodies,
                                clothContactRadius(n,bodies),
                                new float[]{tx,0F,tz},
                                kneeContactWeight(n));
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

        /**
         * Compare the signed motion along each guide arrow with its
         * desired length, AFTER all XPBD/collision/cosmetic corrections.
         * Never use this measurement to force the physics into a pose.
         */
        private void measureGuide(float[][] guidedTarget,float[][] guide)
        {
            float sum=0F;
            int count=0;
            for(int n=0;n<SIM_NODES;n++)
            {
                if(pinned[n])continue;
                float[] delta=guide[n];
                float mag2=delta[0]*delta[0]+delta[1]*delta[1]+
                        delta[2]*delta[2];
                if(mag2<.0001F)continue;
                float dot=0F;
                for(int k=0;k<3;k++)
                    dot+=(position[n][k]-
                            (guidedTarget[n][k]-delta[k]))*delta[k];
                float fraction=dot/mag2;
                if(!Float.isFinite(fraction))continue;
                sum+=fraction;
                count++;
            }
            lastGuideFollow=count==0?0F:sum/count;
        }

        private void auditSeat(SkirtXPBDContacts.Frame bodies,
                int substeps,int contacts)
        {
            if(!SEATED_GEOMETRY_AUDIT||bodies==null||
                    bodies.seatedBlend<.10F||
                    (Double.isFinite(lastAuditSample)&&
                    Math.abs(lastAuditSample-bodies.sampleTick)<.01D))
                return;
            lastAuditSample=bodies.sampleTick;
            System.out.println("[BBS Editor][SkirtSeatAudit] "+
                    "sampleTick="+bodies.sampleTick+
                    " seated="+bodies.seatedBlend+
                    " realLegOBBs="+bodies.legBoxes.size()+
                    " hipBridges="+lastHipBridgeCount+
                    " lapGuideFromRest="+REST_LENGTH_LAP_GUIDE+
                    " maxLapTargetMotion="+lastGuideMaximum+
                    " meanGuideFollow="+lastGuideFollow+
                    " contactProjections="+contacts+
                    " substeps="+substeps);
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
                    sourceToFine,contacts,self,steps,max,
                    lastGuideMaximum,lastGuideFollow,lastHipBridgeCount);
        }

        private void project(Constraint c,float dt,float seated,
                float shapeRelief)
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
                    // Remove the EXTRA artificial seated hoop stiffening
                    // while keeping original circumference edge lengths.
                    compliance*=upperHoopComplianceScale(seated);
                // Make the lower TWO-row bending constraints softer.
                // Stretch/shear links stay intact; no torn seams.
                if(SEATED_BEND_RELIEF&&Math.abs(rowA-rowB)==2&&
                        Math.min(rowA,rowB)>=1)
                    compliance*=1F+(7F+4F*shapeRelief)*seated;
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
                    float[] delta={
                            (position[j][0]-position[i][0])*push,
                            (position[j][1]-position[i][1])*push,
                            (position[j][2]-position[i][2])*push
                    };
                    // Fabric-to-fabric contact is a positional projection
                    // too; do not replay its separation as a frame impulse.
                    contactMove(position[i],previous[i],
                            -wi*delta[0],-wi*delta[1],-wi*delta[2]);
                    contactMove(position[j],previous[j],
                            wj*delta[0],wj*delta[1],wj*delta[2]);
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
                    float px=sign*amount*n[0]/total;
                    float py=sign*amount*n[1]/total;
                    float pz=sign*amount*n[2]/total;
                    contactMove(point,previous[p],
                            wa*px,wa*py,wa*pz);
                    contactMove(a,previous[tri[0]],
                            -w0*px/3F,-w0*py/3F,-w0*pz/3F);
                    contactMove(b,previous[tri[1]],
                            -w1*px/3F,-w1*py/3F,-w1*pz/3F);
                    contactMove(c,previous[tri[2]],
                            -w2*px/3F,-w2*py/3F,-w2*pz/3F);
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
