package com.example.examplemod.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;

/**
 * Standalone Java 8 prototype of a Jolt-based hanging bone chain.
 * Source API is intentionally independent of Minecraft/BBS/Chameleon.
 *
 * Jolt must be loaded and initialized by the caller before construction.
 * The root follows a kinematic body; each link is a real dynamic rigid body
 * with orientation and a SwingTwist cone joint. No guessed Euler rotations.
 *
 * Independently implements published rigid-chain concepts analogous to
 * Wemppy BBS Physics Engine's BoneChainRig/PhysicsJoints.
 * Upstream bbs-physics-engine does not declare a repository license; no
 * upstream implementation was copied into this Java 8 prototype.
 */
public final class JoltHairChain implements AutoCloseable {
    public static final float TICK = 1F / 20F;
    public static final int SUBSTEPS = 3;
    /**
     * Opt-in test parameter only. Wemppy exposes bend as a chain setting;
     * ours remains 80 degrees unless explicitly configured at launch.
     */
    public static final String BEND_DEGREES_PROPERTY =
            "bbs.editor.physics.jolt.bendDegrees";

    private final ObjectLayerPairFilterTable pairFilter;
    /**
     * Wemppy's bone-chain principle: the pin and root segment are attached
     * to their own animated collider and must not be expelled by it.
     * Keep that exclusion local to this rig; downstream hair still touches
     * the torso. Stored for the lifetime of all native bodies.
     */
    private final GroupFilterTable contactGroups;
    private final BroadPhaseLayerInterfaceTable broadLayers;
    private final ObjectVsBroadPhaseLayerFilterTable broadFilter;
    private final PhysicsSystem world;
    private final BodyInterface bodies;
    private final TempAllocatorImpl allocator;
    private final JobSystemSingleThreaded jobs;
    private final Body pin;
    private final Body[] links;
    private final float linkLength;
    private final float startX;
    private final float startY;
    private final float startZ;
    private final float[] initialDirection;
    private final float[] initialRotation;
    private final float[][] initialRotations;
    private final SwingTwistConstraint[] joints;
    /** Optional Emoticons torso proxy (layer 1); no hair self collision. */
    private Body torsoCollider;
    private float colliderRadius, colliderSpan;
    /** Three independently fitted native bodies; mutually noncolliding. */
    private Body[] torsoSegments;
    private float[] torsoSegmentRadius, torsoSegmentSpan;
    /** Optional cube colliders from arbitrary additional Chameleon BodyParts. */
    public static final int MAX_ADDITIONAL_BOXES=32;
    private Body[] additionalBoxes;
    private float[][] additionalBoxHalf;
    private int[] additionalShapeKinds;
    private final float[] physicalLengths;
    private final float[] physicalRadii;
    private boolean closed;

    /** Backward-compatible straight vertical chain. */
    public JoltHairChain(int count, float linkLength, float startY,
            float stiffness, float damping, float falloff) {
        this(count,linkLength,0F,startY,0F,0F,-1F,0F,
                stiffness,damping,falloff);
    }

    /** Straight chain from arbitrary world-space attachment and tangent. */
    public JoltHairChain(int count,float linkLength,
            float rootX,float rootY,float rootZ,
            float directionX,float directionY,float directionZ,
            float stiffness,float damping,float falloff) {
        this(straightPivots(count,linkLength,rootX,rootY,rootZ,
                directionX,directionY,directionZ),stiffness,damping,falloff);
    }

    /**
     * General Chameleon model: one world-space pivot for each bone, plus
     * the terminal tip. Every segment has its OWN real length and direction.
     * Supports curved and unequal-length strands without flattening them.
     */
    public JoltHairChain(float[][] originalPivots,
            float stiffness,float damping,float falloff) {
        if (originalPivots==null || originalPivots.length<2 ||
                originalPivots.length>65)
            throw new IllegalArgumentException("Need 1-64 native bone segments");
        if (!Float.isFinite(stiffness) || !Float.isFinite(damping) ||
                !Float.isFinite(falloff))
            throw new IllegalArgumentException("Invalid strand settings");
        // Validate BEFORE native world allocation. Bad JVM properties must
        // never leak a half-initialized physics scene.
        float allowedSwingRadians=allowedSwingRadians();
        int count=originalPivots.length-1;
        float[][] points=new float[count+1][3];
        for(int i=0;i<points.length;i++) {
            if(originalPivots[i]==null || originalPivots[i].length!=3)
                throw new IllegalArgumentException("Invalid world pivot");
            for(int j=0;j<3;j++) {
                if(!Float.isFinite(originalPivots[i][j]))
                    throw new IllegalArgumentException("Non-finite world pivot");
                points[i][j]=originalPivots[i][j];
            }
        }
        float[] lengths=new float[count];
        float[][] directions=new float[count][3];
        float[][] rotations=new float[count][4];
        float total=0F;
        for(int i=0;i<count;i++) {
            float dx=points[i+1][0]-points[i][0];
            float dy=points[i+1][1]-points[i][1];
            float dz=points[i+1][2]-points[i][2];
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(!(length>=0.05F) || !Float.isFinite(length))
                throw new IllegalArgumentException("Short/invalid Jolt bone "+i);
            lengths[i]=length;
            total+=length;
            directions[i]=new float[] {dx/length,dy/length,dz/length};
            rotations[i]=rotationFromNegativeY(directions[i]);
        }
        this.physicalLengths=lengths.clone();
        this.physicalRadii=new float[count];
        this.linkLength=total/count;
        this.startX=points[0][0];
        this.startY=points[0][1];
        this.startZ=points[0][2];
        this.initialDirection=directions[0].clone();
        this.initialRotation=rotations[0].clone();
        this.initialRotations=rotations;
        // Jolt's table defaults to NO collisions. Separate torso/body
        // contacts from hair/hair: adjacent hair bones should not collide
        // against each other or the pinned kinematic root.
        // Subgroup 0=pin, 1..N=dynamic hair, N+1..N+3=torso shapes.
        // There is one physical world per chain, so group id 0 is local.
        this.contactGroups=new GroupFilterTable(count+4+MAX_ADDITIONAL_BOXES);
        this.pairFilter=new ObjectLayerPairFilterTable(2);
        pairFilter.enableCollision(0,1);
        this.broadLayers=new BroadPhaseLayerInterfaceTable(2,2);
        broadLayers.mapObjectToBroadPhaseLayer(0,0);
        broadLayers.mapObjectToBroadPhaseLayer(1,1);
        this.broadFilter=new ObjectVsBroadPhaseLayerFilterTable(
                broadLayers,2,pairFilter,2);
        this.world=new PhysicsSystem();
        world.init(256,0,512,512,broadLayers,broadFilter,pairFilter);
        world.setGravity(0F,-9.81F,0F);
        this.bodies=world.getBodyInterface();
        this.allocator=new TempAllocatorImpl(2*1024*1024);
        this.jobs=new JobSystemSingleThreaded(1024);
        this.links=new Body[count];
        this.joints=new SwingTwistConstraint[count];

        // Inertia-only stand-ins: no invisible contact capsules.
        SphereShape pinShape=new SphereShape(0.025F);
        BodyCreationSettings pinSpec=new BodyCreationSettings(
                pinShape,new RVec3(startX,startY,startZ),
                new Quat(rotations[0][0],rotations[0][1],
                        rotations[0][2],rotations[0][3]),
                EMotionType.Kinematic,0);
        pinSpec.setCollisionGroup(new CollisionGroup(contactGroups,0,0));
        this.pin=bodies.createBody(pinSpec);
        bodies.addBody(pin.getId(),EActivation.Activate);
        pinSpec.close();
        pinShape.close();
        Body previous=pin;
        float dampingRate=dampingPerSecond(damping);
        for(int i=0;i<count;i++) {
            float length=lengths[i];
            float radius=Math.min(0.045F,length*0.30F);
            physicalRadii[i]=radius;
            CapsuleShape capsule=new CapsuleShape(
                    Math.max(0.005F,length*0.5F-radius),radius);
            float[] q=rotations[i];
            BodyCreationSettings spec=new BodyCreationSettings(
                    capsule,new RVec3(
                            (points[i][0]+points[i+1][0])*0.5D,
                            (points[i][1]+points[i+1][1])*0.5D,
                            (points[i][2]+points[i+1][2])*0.5D),
                    new Quat(q[0],q[1],q[2],q[3]),
                    EMotionType.Dynamic,0);
            spec.setLinearDamping(dampingRate);
            spec.setAngularDamping(dampingRate);
            spec.setGravityFactor(1F);
            spec.setCollisionGroup(new CollisionGroup(contactGroups,0,i+1));
            Body body=bodies.createBody(spec);
            bodies.addBody(body.getId(),EActivation.Activate);
            spec.close();
            capsule.close();
            links[i]=body;

            SwingTwistConstraintSettings jointSpec=new SwingTwistConstraintSettings();
            RVec3 pivot=new RVec3(points[i][0],points[i][1],points[i][2]);
            jointSpec.setPosition1(pivot);
            jointSpec.setPosition2(pivot);
            float[] direction=directions[i],plane=perpendicular(direction);
            jointSpec.setTwistAxis1(new Vec3(
                    direction[0],direction[1],direction[2]));
            jointSpec.setTwistAxis2(new Vec3(
                    direction[0],direction[1],direction[2]));
            jointSpec.setPlaneAxis1(new Vec3(plane[0],plane[1],plane[2]));
            jointSpec.setPlaneAxis2(new Vec3(plane[0],plane[1],plane[2]));
            float swingAngle=allowedSwingRadians;
            jointSpec.setNormalHalfConeAngle(swingAngle);
            jointSpec.setPlaneHalfConeAngle(swingAngle);
            float twistAngle=(float)Math.toRadians(45D);
            jointSpec.setTwistMinAngle(-twistAngle);
            jointSpec.setTwistMaxAngle(twistAngle);
            SwingTwistConstraint constraint=(SwingTwistConstraint)
                    jointSpec.create(previous,body);
            world.addConstraint(constraint);
            joints[i]=constraint;
            jointSpec.close();
            tuneSpring(constraint,stiffness,damping,falloff,i,count);
            previous=body;
        }
    }

    /**
     * Optional kinematic torso collider, fitted by the editor from the
     * ACTUAL CPU-skinned Emoticons torso mesh and animated bone positions.
     *
     * Endpoints are in the same world frame as the hair chain. The capsule
     * follows the actor every simulated frame and naturally reacts to all
     * directions of gravity. No guessed giant capsule or flat-plane force.
     * Caller must omit this method entirely unless mesh-space validation
     * succeeded. Only the hair-vs-torso collision pair is enabled.
     */
    public void setTorsoCollider(float[] a,float[] b,float radius) {
        requireOpen();
        if(!finite3(a)||!finite3(b)||!Float.isFinite(radius)||
                radius<0.04F||radius>0.24F)
            throw new IllegalArgumentException("Invalid fitted torso collider");
        float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
        float span=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(span<0.10F||span>1F)
            throw new IllegalArgumentException("Unexpected torso joint span");
        float[] direction=new float[]{dx/span,dy/span,dz/span};
        float[] quaternion=rotationFromNegativeY(direction);
        RVec3 center=new RVec3((a[0]+b[0])*0.5D,
                (a[1]+b[1])*0.5D,(a[2]+b[2])*0.5D);
        Quat orientation=new Quat(quaternion[0],quaternion[1],
                quaternion[2],quaternion[3]);
        if(torsoCollider==null) {
            // The first link shares its root with the animated body.
            // Other dynamic links remain collidable with the torso.
            excuseRootContact(0);
            // Caps at both ends round the torso instead of terminating in
            // artificial sharp contact planes. This is a fitted proxy, NOT
            // a replacement for the skin mesh.
            float halfCylinder=Math.max(0.005F,0.5F*span-radius);
            CapsuleShape shape=new CapsuleShape(halfCylinder,radius);
            BodyCreationSettings settings=new BodyCreationSettings(
                    shape,center,orientation,EMotionType.Kinematic,1);
            settings.setCollisionGroup(new CollisionGroup(
                    contactGroups,0,links.length+1));
            Body created=null;
            try {
                created=bodies.createBody(settings);
                if(created==null)
                    throw new IllegalStateException("Jolt failed to create torso collider");
                bodies.addBody(created.getId(),EActivation.Activate);
                torsoCollider=created;
                colliderRadius=radius;
                colliderSpan=span;
            } finally {
                settings.close();
                shape.close();
            }
        } else {
            // A dynamically changed torso thickness requires rebuilding
            // the proxy in a fresh rig; NEVER silently reinterpret its shape.
            if(Math.abs(colliderRadius-radius)>0.03F||
                    Math.abs(colliderSpan-span)>0.08F)
                throw new IllegalArgumentException(
                        "Torso topology changed: restart preview scene");
            bodies.moveKinematic(torsoCollider.getId(),center,orientation,TICK);
        }
    }

    public boolean hasTorsoCollider() {
        requireOpen();
        return torsoCollider!=null || torsoSegments!=null;
    }
    /**
     * Compound torso made of actual native Jolt kinematic capsules.
     * The actor's CPU-skinned mesh (not a hardcoded model) supplies
     * each radius. This is intentionally a separate, opt-in path.
     *
     * Repeated calls MOVE the same native collider bodies. A changing
     * shape within the same scene is rejected rather than silently
     * desynchronizing visible debug geometry from physical contacts.
     */
    public void setTorsoSegments(float[][] a,float[][] b,float[] radii)
    {
        requireOpen();
        if(a==null||b==null||radii==null || radii.length!=3 ||
                a.length!=3 || b.length!=3)
            throw new IllegalArgumentException("Need three fitted torso contacts");
        if(torsoCollider!=null)
            throw new IllegalStateException("Cannot mix single and compound torso");
        float[] spans=new float[3];
        RVec3[] centers=new RVec3[3];
        Quat[] orientations=new Quat[3];
        for(int i=0;i<3;i++)
        {
            if(!finite3(a[i])||!finite3(b[i])||
                    !Float.isFinite(radii[i])||radii[i]<0.05F||
                    radii[i]>0.23F)
                throw new IllegalArgumentException("Invalid fitted torso segment "+i);
            float x=b[i][0]-a[i][0],y=b[i][1]-a[i][1],z=b[i][2]-a[i][2];
            float length=(float)Math.sqrt(x*x+y*y+z*z);
            if(!Float.isFinite(length) || length<0.075F || length>0.70F)
                throw new IllegalArgumentException("Torso segment length invalid "+i);
            spans[i]=length;
            float[] rotation=rotationFromNegativeY(
                    new float[]{x/length,y/length,z/length});
            centers[i]=new RVec3((a[i][0]+b[i][0])*0.5D,
                    (a[i][1]+b[i][1])*0.5D,
                    (a[i][2]+b[i][2])*0.5D);
            orientations[i]=new Quat(rotation[0],rotation[1],
                    rotation[2],rotation[3]);
        }
        if(torsoSegments==null)
        {
            // No contact against the attached pin/root; downstream links
            // still contact every fitted torso zone.
            for(int zone=0;zone<3;zone++)excuseRootContact(zone);
            // Create all 3 only AFTER validating every input. Bodies share
            // layer 1 and do not collide against other layer-1 bodies.
            Body[] created=new Body[3];
            try
            {
                for(int i=0;i<3;i++)
                {
                    float halfCylinder=Math.max(0.005F,
                            0.5F*spans[i]-radii[i]);
                    CapsuleShape shape=new CapsuleShape(halfCylinder,radii[i]);
                    BodyCreationSettings settings=new BodyCreationSettings(
                            shape,centers[i],orientations[i],
                            EMotionType.Kinematic,1);
                    settings.setCollisionGroup(new CollisionGroup(
                            contactGroups,0,links.length+1+i));
                    try
                    {
                        created[i]=bodies.createBody(settings);
                        if(created[i]==null)
                            throw new IllegalStateException("Failed torso contact "+i);
                        bodies.addBody(created[i].getId(),EActivation.Activate);
                    }
                    finally
                    {
                        settings.close();
                        shape.close();
                    }
                }
                torsoSegments=created;
                torsoSegmentRadius=radii.clone();
                torsoSegmentSpan=spans.clone();
            }
            catch(Throwable error)
            {
                for(Body body:created)
                    if(body!=null)
                    {
                        bodies.removeBody(body.getId());
                        bodies.destroyBody(body.getId());
                    }
                throw error;
            }
        }
        else
        {
            for(int i=0;i<3;i++)
                if(Math.abs(torsoSegmentRadius[i]-radii[i])>0.025F ||
                        Math.abs(torsoSegmentSpan[i]-spans[i])>0.055F)
                    throw new IllegalArgumentException(
                            "Torso segment size changed: recreate native scene "+i);
            for(int i=0;i<3;i++)
                bodies.moveKinematic(torsoSegments[i].getId(),
                        centers[i],orientations[i],TICK);
        }
    }

    /**
     * Animated kinematic collision boxes from OTHER attached BodyParts.
     * Every row contains world center xyz, quaternion xyzw and positive half
     * dimensions xyz (ten floats), all in the SAME frame as hair and torso.
     *
     * The geometry is created once per chain; later frames move these native
     * bodies with velocity so a turning chest can actually nudge front hair.
     * If topology or half extents change, the caller must recreate this rig.
     * Existing inertia-only hair / torso setups remain untouched when no
     * additional boxes were supplied.
     */
    /** Legacy API accepted by previously downloaded editor builds. */
    public void setAdditionalBoxes(float[][] boxes)
    {
        if(boxes==null)
        {
            setAdditionalShapes(null);
            return;
        }
        float[][] primitives=new float[boxes.length][];
        for(int i=0;i<boxes.length;i++)
        {
            if(boxes[i]==null || boxes[i].length!=10)
                throw new IllegalArgumentException("Expected 10-float Box");
            primitives[i]=new float[11];
            System.arraycopy(boxes[i],0,primitives[i],0,10);
            primitives[i][10]=0F;
        }
        setAdditionalShapes(primitives);
    }

    /**
     * Typed passive collision bodies from any authored Chameleon bone.
     * Each 11-float descriptor holds center, quaternion, three half-sizes
     * and type 0=box, 1=sphere, 2=capsule. Native capsule long axis is Y;
     * the caller has already rotated its quaternion onto its longest
     * decoded cube axis. Only genuinely changed topologies require a reset.
     */
    public void setAdditionalShapes(float[][] shapes)
    {
        requireOpen();
        if(shapes==null || shapes.length==0)
        {
            if(additionalBoxes!=null && additionalBoxes.length>0)
                throw new IllegalArgumentException(
                        "Collision topology changed: recreate Jolt rig");
            return;
        }
        if(shapes.length>MAX_ADDITIONAL_BOXES)
            throw new IllegalArgumentException("Too many BodyPart collision shapes");
        RVec3[] centers=new RVec3[shapes.length];
        Quat[] rotations=new Quat[shapes.length];
        float[][] extents=new float[shapes.length][3];
        int[] kinds=new int[shapes.length];

        for(int i=0;i<shapes.length;i++)
        {
            float[] row=shapes[i];
            if(row==null || row.length!=11)
                throw new IllegalArgumentException("Expected 11-float collider");
            for(float value:row)
                if(!Float.isFinite(value))
                    throw new IllegalArgumentException("Nonfinite collider");
            int kind=(int)row[10];
            if(row[10]!=(float)kind || kind<0 || kind>2)
                throw new IllegalArgumentException("Unknown Jolt collider type");
            kinds[i]=kind;
            float qLength=row[3]*row[3]+row[4]*row[4]+
                    row[5]*row[5]+row[6]*row[6];
            if(Math.abs(qLength-1F)>0.02F)
                throw new IllegalArgumentException("Non-unit collision rotation");
            for(int axis=0;axis<3;axis++)
            {
                extents[i][axis]=row[7+axis];
                if(extents[i][axis]<0.003F || extents[i][axis]>2F)
                    throw new IllegalArgumentException("Invalid collider half-size");
            }
            centers[i]=new RVec3(row[0],row[1],row[2]);
            rotations[i]=new Quat(row[3],row[4],row[5],row[6]);
        }
        if(additionalBoxes==null)
        {
            Body[] created=new Body[shapes.length];
            try
            {
                for(int i=0;i<shapes.length;i++)
                {
                    int boxGroup=links.length+4+i;
                    contactGroups.disableCollision(0,boxGroup);
                    // The first DYNAMIC hair link must collide with other
                    // BodyParts (in particular a chest). Exclude it only
                    // from a passive shape enclosing its own root anchor,
                    // such as the attached hair model's decorative scalp.
                    if(rootInsidePrimitive(shapes[i],0.035F))
                        contactGroups.disableCollision(1,boxGroup);
                    Shape shape;
                    if(kinds[i]==0)
                        shape=new BoxShape(new Vec3(
                                extents[i][0],extents[i][1],extents[i][2]),
                                0.002F);
                    else if(kinds[i]==1)
                        shape=new SphereShape(extents[i][0]);
                    else
                        shape=new CapsuleShape(
                                Math.max(0.005F,
                                        extents[i][1]-extents[i][0]),
                                extents[i][0]);
                    BodyCreationSettings settings=new BodyCreationSettings(
                            shape,centers[i],rotations[i],EMotionType.Kinematic,1);
                    settings.setCollisionGroup(new CollisionGroup(
                            contactGroups,0,boxGroup));
                    try
                    {
                        created[i]=bodies.createBody(settings);
                        if(created[i]==null)
                            throw new IllegalStateException("Cannot create BodyPart collider");
                        bodies.addBody(created[i].getId(),EActivation.Activate);
                    }
                    finally
                    {
                        settings.close();
                        shape.close();
                    }
                }
                additionalBoxes=created;
                additionalBoxHalf=extents;
                additionalShapeKinds=kinds;
            }
            catch(Throwable error)
            {
                for(Body body:created)
                    if(body!=null)
                    {
                        bodies.removeBody(body.getId());
                        bodies.destroyBody(body.getId());
                    }
                throw error;
            }
        }
        else
        {
            if(additionalBoxes.length!=shapes.length)
                throw new IllegalArgumentException(
                        "Collider count changed: recreate native rig");
            for(int i=0;i<shapes.length;i++)
            {
                if(additionalShapeKinds[i]!=kinds[i])
                    throw new IllegalArgumentException(
                            "Collider primitive changed: recreate native rig");
                for(int k=0;k<3;k++)
                    if(Math.abs(additionalBoxHalf[i][k]-extents[i][k])>0.025F)
                        throw new IllegalArgumentException(
                                "Collider size changed: recreate native rig");
            }
            for(int i=0;i<shapes.length;i++)
                bodies.moveKinematic(additionalBoxes[i].getId(),
                        centers[i],rotations[i],TICK);
        }
    }

    /** Root-ownership test for the FIRST dynamic link; do not blanket-ignore
     * arbitrary chest/shoulder colliders. Local-space distance works for
     * rotated boxes, spheres and vertical capsule primitives alike.
     */
    private boolean rootInsidePrimitive(float[] row,float margin)
    {
        float dx=startX-row[0],dy=startY-row[1],dz=startZ-row[2];
        float x=row[3],y=row[4],z=row[5],w=row[6];
        // Project root in the collider's local axes (inverse unit quaternion).
        float a=(1F-2F*(y*y+z*z))*dx+
                2F*(x*y+w*z)*dy+2F*(x*z-w*y)*dz;
        float b=2F*(x*y-w*z)*dx+
                (1F-2F*(x*x+z*z))*dy+2F*(y*z+w*x)*dz;
        float c=2F*(x*z+w*y)*dx+
                2F*(y*z-w*x)*dy+(1F-2F*(x*x+y*y))*dz;
        int kind=(int)row[10];
        if(kind==0)
            return Math.abs(a)<=row[7]+margin &&
                    Math.abs(b)<=row[8]+margin &&
                    Math.abs(c)<=row[9]+margin;
        if(kind==1)
        {
            float radius=row[7]+margin;
            return a*a+b*b+c*c<=radius*radius;
        }
        float cylinder=Math.max(0F,row[8]-row[7]);
        float nearestY=Math.max(-cylinder,Math.min(cylinder,b));
        float h=b-nearestY,r=row[7]+margin;
        return a*a+h*h+c*c<=r*r;
    }

    /** A strand root is jointed into this animated body, not a free impact. */
    private void excuseRootContact(int zone)
    {
        int body=links.length+1+zone;
        contactGroups.disableCollision(0,body);
        contactGroups.disableCollision(1,body);
    }

    public boolean hasTorsoSegments()
    {
        requireOpen();
        return torsoSegments!=null && torsoSegments.length==3;
    }

    /**
     * Diagnostic WORLD endpoints of the actual Jolt rigid-body capsules;
     * columns: x0,y0,z0,x1,y1,z1,radius. Never infers body positions from
     * a Chameleon pivot or changes simulation state.
     */
    public float[][] nativeHairSegments()
    {
        requireOpen();
        float[][] values=new float[links.length][7];
        for(int i=0;i<links.length;i++)
        {
            RVec3 center=position(i);
            Quat rotation=rotation(i);
            float x=rotation.getX(),y=rotation.getY();
            float z=rotation.getZ(),w=rotation.getW();
            float dx=-2F*(x*y-z*w);
            float dy=-1F+2F*(x*x+z*z);
            float dz=-2F*(y*z+x*w);
            float half=physicalLengths[i]*0.5F;
            values[i]=new float[] {
                    (float)center.xx()-half*dx,
                    (float)center.yy()-half*dy,
                    (float)center.zz()-half*dz,
                    (float)center.xx()+half*dx,
                    (float)center.yy()+half*dy,
                    (float)center.zz()+half*dz,
                    physicalRadii[i]
            };
        }
        return values;
    }


    private static boolean finite3(float[] xyz) {
        if(xyz==null||xyz.length!=3)return false;
        for(float value:xyz)
            if(!Float.isFinite(value))return false;
        return true;
    }

    public void setGravity(float multiplier) {
        requireOpen();
        if (!Float.isFinite(multiplier) || multiplier < 0F || multiplier > 2F)
            throw new IllegalArgumentException("Invalid gravity multiplier");
        world.setGravity(0F, -9.81F * multiplier, 0F);
    }

    /** Advance exactly one Minecraft animation tick at a fixed 60Hz substep. */
    public void step() {
        requireOpen();
        int status = world.update(TICK, SUBSTEPS, allocator, jobs);
        if (status != 0)
            throw new IllegalStateException("Jolt update flags=" + status);
    }

    /** Move the root continuously; caller must invoke once before next step. */
    public void moveAnchor(double x, double y, double z,
            float qx, float qy, float qz, float qw) {
        requireOpen();
        bodies.moveKinematic(pin.getId(), new RVec3(x, y, z),
                new Quat(qx, qy, qz, qw), TICK);
    }

    public RVec3 position(int index) {
        requireOpen();
        return bodies.getPosition(links[index].getId());
    }

    public Quat rotation(int index) {
        requireOpen();
        return bodies.getRotation(links[index].getId());
    }

    public int size() { return links.length; }

    public float[] initialWorldRotation() { return initialRotation.clone(); }

    public float[] initialWorldRotation(int boneIndex) {
        return initialRotations[boneIndex].clone();
    }

    /**
     * Physical end points, rather than body centres, for visual diagnostics.
     * Bone pose reconstruction should use the actual body quaternion.
     */
    public RVec3 attachmentPosition() {
        return new RVec3(startX, startY, startZ);
    }

    private static float[][] straightPivots(int count,float linkLength,
            float rootX,float rootY,float rootZ,
            float dx,float dy,float dz) {
        if(count<1 || count>64 || !(linkLength>=0.05F) ||
                !Float.isFinite(linkLength) || !Float.isFinite(rootX) ||
                !Float.isFinite(rootY) || !Float.isFinite(rootZ) ||
                !Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz))
            throw new IllegalArgumentException("Invalid straight strand");
        float m=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!(m>1E-6F) || !Float.isFinite(m))
            throw new IllegalArgumentException("Invalid strand direction");
        float[][] points=new float[count+1][3];
        for(int i=0;i<=count;i++) {
            points[i][0]=rootX+i*linkLength*dx/m;
            points[i][1]=rootY+i*linkLength*dy/m;
            points[i][2]=rootZ+i*linkLength*dz/m;
        }
        return points;
    }

    private static float[] perpendicular(float[] direction) {
        float x, y, z;
        if (Math.abs(direction[1]) < 0.9F) {
            x = -direction[2];
            y = 0F;
            z = direction[0];
        } else {
            x = 0F;
            y = direction[2];
            z = -direction[1];
        }
        float len = (float)Math.sqrt(x*x+y*y+z*z);
        return new float[] {x/len,y/len,z/len};
    }

    /** Quaternion mapping local -Y (the authored strand axis) to world tangent. */
    private static float[] rotationFromNegativeY(float[] d) {
        float w = 1F - d[1];
        float x = -d[2], z = d[0];
        if (w < 1E-5F) return new float[] {1F,0F,0F,0F};
        float length = (float)Math.sqrt(x*x+z*z+w*w);
        return new float[] {x/length,0F,z/length,w/length};
    }

    private static void tuneSpring(SwingTwistConstraint constraint,
            float stiffness, float damping, float falloff, int index, int total) {
        stiffness = clamp(stiffness);
        damping = clamp(damping);
        falloff = clamp(falloff);
        EMotorState state = stiffness == 0F ? EMotorState.Off : EMotorState.Position;
        constraint.setSwingMotorState(state);
        constraint.setTwistMotorState(state);
        if (state == EMotorState.Off) return;
        float along = total <= 1 ? 0F : index / (float)(total - 1);
        float share = 1F - falloff * along;
        float hz = (0.5F + 11.5F * stiffness) * (float)Math.sqrt(share);
        float ratio = 0.25F + 0.75F * damping;
        MotorSettings swing = constraint.getSwingMotorSettings();
        MotorSettings twist = constraint.getTwistMotorSettings();
        swing.getSpringSettings().setFrequency(hz);
        swing.getSpringSettings().setDamping(ratio);
        twist.getSpringSettings().setFrequency(hz);
        twist.getSpringSettings().setDamping(ratio);
    }

    private static float allowedSwingRadians() {
        String text=System.getProperty(BEND_DEGREES_PROPERTY);
        if(text==null || text.trim().isEmpty())
            return (float)Math.toRadians(80D);
        final float degrees;
        try {
            degrees=Float.parseFloat(text.trim());
        } catch(NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Invalid Jolt hair bend degrees: "+text,error);
        }
        if(!Float.isFinite(degrees) || degrees<10F || degrees>175F)
            throw new IllegalArgumentException(
                    "Jolt hair bend degrees must be between 10 and 175: "+text);
        return (float)Math.toRadians(degrees);
    }

    private static float dampingPerSecond(float knob) {
        float loss = Math.min(0.99F, Math.max(0F, knob));
        float perStep = (float)Math.pow(1F - loss, 1D / SUBSTEPS);
        return (1F - perStep) * SUBSTEPS / TICK;
    }

    private static float clamp(float n) {
        return Math.min(1F, Math.max(0F, n));
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Chain already closed");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        // Explicitly release native scene-owned objects before world teardown.
        // A Body returned by BodyInterface is borrowed from the PhysicsSystem.
        for (SwingTwistConstraint joint : joints) {
            if (joint != null) {
                world.removeConstraint(joint);
                joint.close();
            }
        }
        for (Body link : links) {
            if (link != null) {
                bodies.removeBody(link.getId());
                bodies.destroyBody(link.getId());
            }
        }
        if(torsoCollider!=null) {
            bodies.removeBody(torsoCollider.getId());
            bodies.destroyBody(torsoCollider.getId());
            torsoCollider=null;
        }
        if(torsoSegments!=null) {
            for(Body body:torsoSegments) {
                if(body!=null) {
                    bodies.removeBody(body.getId());
                    bodies.destroyBody(body.getId());
                }
            }
            torsoSegments=null;
        }
        if(additionalBoxes!=null)
        {
            for(Body body:additionalBoxes)
                if(body!=null)
                {
                    bodies.removeBody(body.getId());
                    bodies.destroyBody(body.getId());
                }
            additionalBoxes=null;
        }
        bodies.removeBody(pin.getId());
        bodies.destroyBody(pin.getId());
        world.close();
        jobs.close();
        allocator.close();
        broadFilter.close();
        broadLayers.close();
        pairFilter.close();
        contactGroups.close();
    }
}
