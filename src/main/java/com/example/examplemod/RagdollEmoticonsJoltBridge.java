package com.example.examplemod;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import net.minecraft.client.Minecraft;

/**
 * Optional Emoticons CHARACTER ragdoll renderer bridge.
 *
 * The original Animator has ALREADY calculated every native BOBJ bone.mat
 * and the skinned matrices before this hook. For weight=0, absent JNI, or an
 * invalid rig, NOTHING is mutated (no behavioral or export regression).
 *
 * Opt-in in the development client:
 *   -Dbbs.editor.physics.jolt.ragdoll=true
 *
 * Model-space physical scenes are owned per actor and tied to the actual
 * model bone signature. No hard JNI dependency is linked into Forge code;
 * the Java 8 Jolt prototype is loaded reflectively. This first-pass solver
 * only tests a ground plane at local Y0, not real Minecraft collision blocks.
 */
public final class RagdollEmoticonsJoltBridge
{
    private static final boolean ENABLED=
            Boolean.getBoolean("bbs.editor.physics.jolt.ragdoll");
    private static final String NATIVE=
            "com.example.examplemod.physics.JoltHumanoidRagdoll";
    private static boolean attempted,available,logged,reportedHierarchy;
    private static Method create,step,poses,startFall;
    private static Method nativeImpulse,nativeEnvironment;
    private static boolean reportedOldPhysicsJar;
    private static boolean fitted;
    private static final Map<String,State> scenes=
            new LinkedHashMap<String,State>();

    private static final class State
    {
        final AutoCloseable nativeRig;
        final String signature;
        final RagdollSkeletonDefinition definition;
        final Quat4f[] boneOffsets;
        final float[] lengths;
        int frame;
        int lastImpulseFrame;

        State(AutoCloseable nativeRig,String signature,
                RagdollSkeletonDefinition definition,
                Quat4f[] boneOffsets,float[] lengths,int frame)
        {
            this.nativeRig=nativeRig;this.signature=signature;
            this.definition=definition;
            this.boneOffsets=boneOffsets;this.lengths=lengths;
            this.frame=frame;
            this.lastImpulseFrame=frame-1;
        }
        void close()
        {
            try{nativeRig.close();}
            catch(Exception error)
            {
                System.err.println("[BBS Editor][Ragdoll] Jolt close: "+error);
            }
        }
    }

    private RagdollEmoticonsJoltBridge(){}

    public static void resetAll()
    {
        for(State state:scenes.values())state.close();
        scenes.clear();
    }

    /**
     * Read-only model-space rigid-body center for the PHYSICS preview
     * impulse handle. Unlike model bind positions, this follows the
     * actually fallen body. Never step the solver from a GUI paint pass.
     */
    public static float[] previewBodyCenter(BlockbusterSceneActorData actor,
            String role)
    {
        if(actor==null||role==null||poses==null)return null;
        State state=scenes.get(actor.getId());
        if(state==null)return null;
        try
        {
            List<RagdollSkeletonDefinition.Segment> segments=
                    state.definition.getSegments();
            int index=-1;
            for(int i=0;i<segments.size();i++)
                if(role.equals(segments.get(i).role)){index=i;break;}
            if(index<0)return null;
            float[][] samples=(float[][])poses.invoke(state.nativeRig);
            if(samples==null||index>=samples.length||
                    samples[index]==null||samples[index].length<3)return null;
            float[] p=samples[index];
            if(!Float.isFinite(p[0])||!Float.isFinite(p[1])||
                    !Float.isFinite(p[2]))return null;
            return new float[]{p[0],p[1],p[2]};
        }
        catch(Throwable unavailable){return null;}
    }

    private static void dispose(String actorId)
    {
        State state=scenes.remove(actorId);
        if(state!=null)state.close();
    }

    private static boolean initialize()
    {
        if(!ENABLED)return false;
        if(attempted)return available;
        attempted=true;
        try
        {
            Minecraft mc=Minecraft.getMinecraft();
            File game=mc==null?null:mc.mcDataDir;
            Class<?> bootstrap=Class.forName(
                    "com.example.examplemod.physics.JoltJava8Bootstrap");
            Method start=bootstrap.getMethod("available",File.class);
            if(!Boolean.TRUE.equals(start.invoke(null,game)))return false;
            Class<?> clazz=Class.forName(NATIVE);
            try
            {
                create=clazz.getMethod("createFitted",float[][].class,
                        float[][].class,float[].class,int[].class,
                        int[].class,float[][].class,float[][].class,float.class);
                startFall=clazz.getMethod("startFall",float.class,float.class);
                fitted=true;
            }
            catch(NoSuchMethodException previousVersion)
            {
                create=clazz.getMethod("create",float[][].class,
                        float[][].class,float[].class,int[].class,
                        int[].class,float[][].class,float.class);
                fitted=false;
                startFall=null;
            }
            step=clazz.getMethod("step");
            poses=clazz.getMethod("poses");
            nativeImpulse=clazz.getMethod("impulse",int.class,float.class,
                    float.class,float.class,float.class,float.class,float.class);
            try
            {
                nativeEnvironment=clazz.getMethod("setEnvironment",
                        float.class,float.class,float.class,float.class,float.class);
            }
            catch(NoSuchMethodException oldJar)
            {
                nativeEnvironment=null;
                if(!reportedOldPhysicsJar)
                {
                    reportedOldPhysicsJar=true;
                    System.err.println("[BBS Editor][PHYSICS] New gravity/water "+
                            "requires updated jolt-hair-prototype-java8.jar");
                }
            }
            available=true;
            System.out.println("[BBS Editor][Ragdoll] Native Jolt "+
                    "multibody solver connected (experimental)");
        }
        catch(Throwable error)
        {
            System.err.println("[BBS Editor][Ragdoll] Optional Jolt "+
                    "rig unavailable: "+error);
        }
        return available;
    }

    public static boolean apply(BOBJArmature armature,
            BlockbusterSceneActorData actor,int frame)
    {
        // Export must remain bit-for-bit compatible with the authored
        // animation until fixed-step Jolt replay/baking is implemented.
        if(!ENABLED||CameraExportRenderClock.current()!=null||
                armature==null||actor==null||
                frame<0||actor.getCharacterTimeline()==null)return false;
        String id=actor.getId();
        if(id==null||id.isEmpty())return false;
        float weight=CharacterRagdollController.sampleWeight(
                actor.getCharacterTimeline(),frame);
        if(weight<=0F)
        {
            dispose(id);
            return false;
        }
        if(!initialize())return false;

        // Do not confuse model/rig swaps between multiple scene actors.
        RagdollSkeletonDefinition definition=
                RagdollSkeletonDefinition.bind(
                        RagdollSkeletonDefinition.Preset.EMOTICONS,
                        armature.bones.keySet());
        if(!definition.hasPhysicalSkeleton())return false;
        List<RagdollSkeletonDefinition.Segment> bones=
                definition.getSegments();
        // The Emoticons special ragdoll.bobj has many detached model
        // bones. Never pretend they are parented like default.bobj and
        // inject constrained physics into a different BOBJ hierarchy.
        // Only the actual armature can establish joint ancestry.
        if(!matchesActualHierarchy(armature,bones))
        {
            if(!reportedHierarchy)
            {
                reportedHierarchy=true;
                System.err.println("[BBS Editor][Ragdoll] "+
                        "This BOBJ hierarchy differs from stock Emoticons; "+
                        "native physics skipped until a matching rig is fitted");
            }
            dispose(id);
            return false;
        }
        StringBuilder signature=new StringBuilder();
        for(RagdollSkeletonDefinition.Segment segment:bones)
        {
            BOBJBone nativeBone=armature.bones.get(segment.bone);
            if(nativeBone==null)return false;
            signature.append(segment.bone).append(':')
                    .append(nativeBone.parent).append(':')
                    .append(nativeBone.length).append(';');
        }

        try
        {
            State state=scenes.get(id);
            if(state==null||!state.signature.equals(signature.toString())||
                    frame<state.frame||frame-state.frame>10)
            {
                dispose(id);
                state=createState(armature,definition,
                        signature.toString(),frame,
                        actor.getCharacterTimeline());
                if(state==null)return false;
                scenes.put(id,state);
                // The editor has many actors; never leak an unlimited
                // number of native PhysicsSystems when scenes are switched.
                while(scenes.size()>16)
                {
                    String eldest=scenes.keySet().iterator().next();
                    dispose(eldest);
                }
            }
            // Apply exactly once at activation, not on repeated preview,
            // mirror or screenshot passes for this same frame.
            applyEnvironment(state,actor.getCharacterTimeline(),state.frame);
            applyImpulse(state,actor.getCharacterTimeline(),state.frame);
            // Fixed-step replay: impulses fire once on the frame crossing.
            while(state.frame<frame)
            {
                int next=state.frame+1;
                applyEnvironment(state,actor.getCharacterTimeline(),next);
                applyImpulse(state,actor.getCharacterTimeline(),next);
                step.invoke(state.nativeRig);
                state.frame=next;
            }
            float[][] physical=(float[][])poses.invoke(state.nativeRig);
            if(physical.length!=bones.size())return false;
            return blend(armature,state,physical,weight);
        }
        catch(Throwable error)
        {
            dispose(id);
            if(!logged)
            {
                logged=true;
                System.err.println("[BBS Editor][Ragdoll] "+
                        "Experimental body solver disabled for actor: "+error);
                error.printStackTrace();
            }
        }
        return false;
    }

    /** Independent gravity + water channels for an actor's Jolt world. */
    private static void applyEnvironment(State state,
            CharacterTimelineController timeline,int frame) throws Exception
    {
        if(nativeEnvironment==null)return;
        PhysicsChannels.Fluid f=PhysicsChannels.waterAt(timeline,frame);
        nativeEnvironment.invoke(state.nativeRig,
                PhysicsChannels.gravityAt(timeline,frame),
                f.amount,f.buoyancy,f.drag,f.angularDrag);
    }

    /** Apply a recorded impulse once, to a native rigid body and hit point. */
    private static void applyImpulse(State state,
            CharacterTimelineController timeline,int frame) throws Exception
    {
        if(frame<=state.lastImpulseFrame)return;
        state.lastImpulseFrame=frame;
        PhysicsChannels.Impact impact=PhysicsChannels.impulseAt(timeline,frame);
        if(impact==null||impact.strength<=0F||nativeImpulse==null)return;
        List<RagdollSkeletonDefinition.Segment> physical=
                state.definition.getSegments();
        int index=-1;
        for(int i=0;i<physical.size();i++)
            if(physical.get(i).role.equals(impact.target)){index=i;break;}
        if(index<0)return;
        float norm=(float)Math.sqrt(impact.x*impact.x+
                impact.y*impact.y+impact.z*impact.z);
        if(norm<.001F)return;
        float[][] pose=(float[][])poses.invoke(state.nativeRig);
        if(index>=pose.length)return;
        float[] p=pose[index];
        nativeImpulse.invoke(state.nativeRig,index,
                impact.x/norm*impact.strength,
                impact.y/norm*impact.strength,
                impact.z/norm*impact.strength,
                p[0]+impact.offsetX,p[1]+impact.offsetY,
                p[2]+impact.offsetZ);
    }

    private static boolean matchesActualHierarchy(BOBJArmature armature,
            List<RagdollSkeletonDefinition.Segment> segments)
    {
        Map<String,String> nativeByRole=new HashMap<String,String>();
        for(RagdollSkeletonDefinition.Segment seg:segments)
            nativeByRole.put(seg.role,seg.bone);
        for(RagdollSkeletonDefinition.Segment seg:segments)
        {
            if(seg.parentRole==null)continue;
            String expected=nativeByRole.get(seg.parentRole);
            BOBJBone child=armature.bones.get(seg.bone);
            if(expected==null||child==null)return false;
            // Optional intervening native control bones are harmless,
            // but the selected rigid parent MUST be an actual ancestor.
            String parent=child.parent;
            boolean connected=false;
            for(int depth=0;depth<32;depth++)
            {
                if(expected.equals(parent)){connected=true;break;}
                if(parent==null||parent.isEmpty())break;
                BOBJBone next=armature.bones.get(parent);
                if(next==null)break;
                parent=next.parent;
            }
            if(!connected)return false;
        }
        return true;
    }

    private static State createState(BOBJArmature armature,
            RagdollSkeletonDefinition definition,String signature,int frame,
            CharacterTimelineController timeline) throws Exception
    {
        List<RagdollSkeletonDefinition.Segment> segments=
                definition.getSegments();
        int count=segments.size();
        float[][] head=new float[count][3],tail=new float[count][3];
        float[] radii=new float[count],lengths=new float[count];
        int[] parents=new int[count],types=new int[count];
        float[][] limit=new float[count][5];
        float[][] hingeFrames=new float[count][6];
        Quat4f[] offsets=new Quat4f[count];
        Map<String,Integer> byRole=new HashMap<String,Integer>();
        float lowest=Float.POSITIVE_INFINITY;
        for(int i=0;i<count;i++)
        {
            RagdollSkeletonDefinition.Segment info=segments.get(i);
            BOBJBone bone=armature.bones.get(info.bone);
            if(bone==null)return null;
            // bone.mat is the real model-space skinned world matrix,
            // already computed by the native Emoticons animator.
            Matrix4f model=new Matrix4f(bone.mat);
            head[i]=new float[]{model.m03,model.m13,model.m23};
            Vector4f tip=new Vector4f(
                    bone.tail.x,bone.tail.y,bone.tail.z,1F);
            bone.invBoneMat.transform(tip);
            model.transform(tip);
            tail[i]=new float[]{tip.x,tip.y,tip.z};
            float x=tip.x-model.m03,y=tip.y-model.m13,z=tip.z-model.m23;
            float length=(float)Math.sqrt(x*x+y*y+z*z);
            if(!Float.isFinite(length)||length<0.06F||length>2F)
                return null;
            lengths[i]=length;
            radii[i]=Math.min(info.radiusBlocks,length*0.4F);
            float[] physicalInit=axisRotation(x/length,y/length,z/length);
            Quat4f alignment=new Quat4f(physicalInit);
            alignment.inverse();
            Quat4f qWorld=rotation(model);
            alignment.mul(qWorld);
            alignment.normalize();
            offsets[i]=alignment;
            Integer parent=byRole.get(info.parentRole);
            if(i>0&&parent==null)return null;
            parents[i]=parent==null?-1:parent.intValue();
            types[i]=info.joint==RagdollSkeletonDefinition.Joint.HINGE?
                    2:1;
            if(types[i]==2 && parent!=null)
            {
                // Use the animated PARENT's real BOBJ joint basis.
                // A fixed world X hinge becomes wrong as soon as the
                // actor or limb is rotated by an authored animation.
                BOBJBone source=armature.bones.get(
                        segments.get(parent.intValue()).bone);
                if(source==null)return null;
                Quat4f basis=rotation(new Matrix4f(source.mat));
                // Keep the REAL BOBJ parent hinge plane but choose
                // its sign from the child segment and the current
                // actor-facing direction. A fixed +X/-X convention
                // is wrong for mirrored or rotated skeletons.
                Vector3f hingeAxis=new Vector3f(1F,0F,0F);
                Vector3f hingeNormal=new Vector3f(0F,1F,0F);
                rotate(basis,hingeAxis);
                rotate(basis,hingeNormal);
                BOBJBone root=armature.bones.get(segments.get(0).bone);
                if(root==null)return null;
                Vector3f forward=new Vector3f(0F,0F,
                        RagdollAnatomy.forwardLocalZ());
                rotate(rotation(new Matrix4f(root.mat)),forward);
                float multiplier=RagdollAnatomy.hingeAxisMultiplier(
                        info.role,
                        new float[]{hingeAxis.x,hingeAxis.y,hingeAxis.z},
                        new float[]{x,y,z},
                        new float[]{forward.x,forward.y,forward.z});
                hingeAxis.scale(multiplier);
                hingeFrames[i]=new float[]{
                        hingeAxis.x,hingeAxis.y,hingeAxis.z,
                        hingeNormal.x,hingeNormal.y,hingeNormal.z};
            }
            limit[i]=new float[]{info.swingDegrees,
                    info.twistMinDegrees,info.twistMaxDegrees,
                    info.hingeMinDegrees,info.hingeMaxDegrees};
            byRole.put(info.role,Integer.valueOf(i));
            lowest=Math.min(lowest,Math.min(tip.y,model.m13));
        }
        Object nativeObject=fitted?
                create.invoke(null,head,tail,radii,parents,
                        types,limit,hingeFrames,lowest-0.03F):
                create.invoke(null,head,tail,radii,parents,
                        types,limit,lowest-0.03F);
        if(!(nativeObject instanceof AutoCloseable))
            throw new IllegalStateException("Jolt ragdoll not closable");
        // The fitted solver starts from the exact animated pose. A
        // stationary vertical biped will otherwise compress under
        // gravity instead of losing balance. Give it one bounded,
        // model-facing impulse at transition; never on render frames.
        if(fitted&&startFall!=null &&
                PhysicsChannels.impulseAt(timeline,frame)==null &&
                !"false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.physics.jolt.ragdoll.autoNudge","true")))
        {
            BOBJBone root=armature.bones.get(segments.get(0).bone);
            // Emoticons' model-local FRONT is -Z; the previous +Z
            // vector forced every standing actor to fall backward.
            // Derive the direction from the current root pose so it
            // stays correct after an authored yaw rotation.
            Vector3f forward=new Vector3f(0F,0F,
                    RagdollAnatomy.forwardLocalZ());
            rotate(rotation(new Matrix4f(root.mat)),forward);
            float horizontal=(float)Math.sqrt(
                    forward.x*forward.x+forward.z*forward.z);
            if(horizontal>0.001F)
                startFall.invoke(nativeObject,
                        forward.x/horizontal,forward.z/horizontal);
        }
        return new State((AutoCloseable)nativeObject,signature,
                definition,offsets,lengths,frame);
    }

    /**
     * Update only actual BOBJ deform bones. Every helper (.end/.item/misc)
     * continues to inherit its native parent's animation automatically.
     * Animated local positions/angles are preserved at 0% influence.
     */
    private static boolean blend(BOBJArmature armature,State state,
            float[][] pose,float weight)
    {
        List<RagdollSkeletonDefinition.Segment> segments=
                state.definition.getSegments();
        // First save a clean animated copy for all parents, including
        // non-physical anchor bones.
        Map<String,Matrix4f> clean=new HashMap<String,Matrix4f>();
        for(BOBJBone bone:armature.orderedBones)
            clean.put(bone.name,new Matrix4f(bone.mat));
        Map<String,Matrix4f> output=new HashMap<String,Matrix4f>();

        for(int i=0;i<segments.size();i++)
        {
            RagdollSkeletonDefinition.Segment info=segments.get(i);
            BOBJBone bone=armature.bones.get(info.bone);
            Matrix4f original=clean.get(info.bone);
            if(bone==null||original==null)return false;
            float[] sample=pose[i];
            if(sample==null||sample.length!=7)return false;
            for(float value:sample)
                if(!Float.isFinite(value))return false;

            Quat4f capsule=new Quat4f(sample[3],sample[4],
                    sample[5],sample[6]);
            capsule.normalize();
            Quat4f physWorld=new Quat4f(capsule);
            physWorld.mul(state.boneOffsets[i]);
            physWorld.normalize();
            // Capsule's long axis is local -Y; +Y endpoint is bone head.
            Vector3f axis=new Vector3f(0F,state.lengths[i]*0.5F,0F);
            rotate(capsule,axis);
            Vector3f physicalHead=new Vector3f(
                    sample[0]+axis.x,sample[1]+axis.y,
                    sample[2]+axis.z);
            Quat4f animatedWorld=rotation(original);
            Quat4f blended=new Quat4f();
            blended.interpolate(animatedWorld,physWorld,weight);
            blended.normalize();
            Vector3f pivot=new Vector3f(
                    original.m03+(physicalHead.x-original.m03)*weight,
                    original.m13+(physicalHead.y-original.m13)*weight,
                    original.m23+(physicalHead.z-original.m23)*weight);
            Matrix4f authoredWorld=new Matrix4f(blended,pivot,1F);
            // BOBJ's model-space bind basis includes a reflected X axis
            // (determinant -1 in stock default.bobj). Preserving that
            // handedness is mandatory: quaternion rotation cannot by
            // itself represent a reflection and would invert/skew skin.
            if(handedness(original)<0F)
            {
                authoredWorld.m00=-authoredWorld.m00;
                authoredWorld.m10=-authoredWorld.m10;
                authoredWorld.m20=-authoredWorld.m20;
            }
            output.put(info.bone,authoredWorld);
        }

        // Compute local Rz*Ry*Rx relative to BOBJ's real parent and
        // bind pose. Rollbacks are automatic: this function checks all
        // transforms before mutating even one bone.
        List<float[]> edits=new ArrayList<float[]>();
        for(int i=0;i<segments.size();i++)
        {
            BOBJBone bone=armature.bones.get(segments.get(i).bone);
            Matrix4f target=output.get(bone.name);
            Matrix4f parent=bone.parentBone==null?null:
                    output.get(bone.parentBone.name);
            if(parent==null&&bone.parentBone!=null)
                parent=clean.get(bone.parentBone.name);
            Matrix4f local=new Matrix4f(target);
            if(parent!=null)
            {
                Matrix4f parentInverse=new Matrix4f(parent);
                parentInverse.invert();
                local.mul(parentInverse,local);
            }
            Matrix4f restInverse=new Matrix4f(bone.relBoneMat);
            restInverse.invert();
            local.mul(restInverse,local);
            float[] euler=extractEulerZYX(local);
            float[] row=new float[]{local.m03,local.m13,local.m23,
                    euler[0],euler[1],euler[2]};
            for(float value:row)
                if(!Float.isFinite(value)||Math.abs(value)>20F)
                    return false;
            edits.add(row);
        }
        for(int i=0;i<segments.size();i++)
        {
            BOBJBone bone=armature.bones.get(segments.get(i).bone);
            float[] edit=edits.get(i);
            bone.x=edit[0];bone.y=edit[1];bone.z=edit[2];
            bone.rotateX=edit[3];
            bone.rotateY=edit[4];
            bone.rotateZ=edit[5];
        }
        return true;
    }

    private static float[] extractEulerZYX(Matrix4f matrix)
    {
        Matrix3f r=new Matrix3f();
        matrix.getRotationScale(r);
        // Ignore authored scale when extracting angle; the original
        // scaleX/Y/Z are left intact on the Emoticons bone.
        Quat4f q=new Quat4f();q.set(r);q.normalize();
        r.set(q);
        float ry=(float)Math.asin(Math.max(-1D,
                Math.min(1D,-r.m20)));
        float rx,rz;
        if(Math.abs(Math.cos(ry))>1E-5F)
        {
            rx=(float)Math.atan2(r.m21,r.m22);
            rz=(float)Math.atan2(r.m10,r.m00);
        }
        else
        {
            rx=0F;
            rz=(float)Math.atan2(-r.m01,r.m11);
        }
        return new float[]{rx,ry,rz};
    }

    private static Quat4f rotation(Matrix4f model)
    {
        Matrix3f r=new Matrix3f();
        model.getRotationScale(r);
        // Emoticons skin scale can be non-unit; normalize each column
        // before reading a rotation rather than treating scale as shear.
        for(int i=0;i<3;i++)
        {
            float x=r.getElement(0,i),y=r.getElement(1,i),
                    z=r.getElement(2,i);
            float n=(float)Math.sqrt(x*x+y*y+z*z);
            if(n>1E-6F)
            {
                r.setElement(0,i,x/n);
                r.setElement(1,i,y/n);
                r.setElement(2,i,z/n);
            }
        }
        if(handedness(model)<0F)
        {
            r.m00=-r.m00;
            r.m10=-r.m10;
            r.m20=-r.m20;
        }
        Quat4f q=new Quat4f();q.set(r);q.normalize();return q;
    }

    private static float handedness(Matrix4f matrix)
    {
        return matrix.m00*(matrix.m11*matrix.m22-
                        matrix.m12*matrix.m21)-
                matrix.m01*(matrix.m10*matrix.m22-
                        matrix.m12*matrix.m20)+
                matrix.m02*(matrix.m10*matrix.m21-
                        matrix.m11*matrix.m20);
    }

    private static void rotate(Quat4f q,Vector3f v)
    {
        // v' = v + 2w(q.xyz x v) + 2(q.xyz x (q.xyz x v))
        float tx=2F*(q.y*v.z-q.z*v.y);
        float ty=2F*(q.z*v.x-q.x*v.z);
        float tz=2F*(q.x*v.y-q.y*v.x);
        v.set(v.x+q.w*tx+q.y*tz-q.z*ty,
                v.y+q.w*ty+q.z*tx-q.x*tz,
                v.z+q.w*tz+q.x*ty-q.y*tx);
    }

    private static float[] axisRotation(float x,float y,float z)
    {
        float w=1F-y,qx=-z,qz=x;
        if(w<1E-5F)return new float[]{1F,0F,0F,0F};
        float n=(float)Math.sqrt(qx*qx+qz*qz+w*w);
        return new float[]{qx/n,0F,qz/n,w/n};
    }
}
