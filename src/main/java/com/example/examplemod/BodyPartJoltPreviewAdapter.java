package com.example.examplemod;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OPTIONAL Jolt preview bridge. Java 8 / Forge classes have NO hard JNI
 * linkage; an absent physics prototype leaves the old renderer available.
 *
 * Enable ONLY for development:
 *   -Dbbs.editor.physics.jolt.preview=true
 *
 * Jolt JNI and the jolt-hair-prototype Java8 jar must both be on the launch
 * classpath. The experimental module is intentionally not a Forge mod.
 *
 * This bridge owns its native preview scenes and never exports keyframes.
 * Collisions are not supported by the first Jolt Chameleon preview.
 */
public final class BodyPartJoltPreviewAdapter {
    private static final boolean REQUESTED =
            Boolean.getBoolean("bbs.editor.physics.jolt.preview");
    private static final boolean RAW_DIAGNOSTIC =
            Boolean.getBoolean("bbs.editor.physics.jolt.diagnostic");
    private static final String RIG =
            "com.example.examplemod.physics.JoltChameleonPreviewRig";
    private boolean attempted;
    private boolean available;
    private boolean diagnosticLogged;
    private Method create;
    private Method setGravity;
    private Method advance;
    private Method settle;
    private Method localSwings;
    /** Optional prototype API: old downloaded JARs must still work. */
    private Method nativeDirections;
    private boolean nativeDirectionWarning;
    private boolean recreatedLastSample;

    private static final class State {
        final AutoCloseable rig;
        final String[] bones;
        final float stiffness, damping, gravity;
        int frame;
        float[] lastRoot;
        float[] lastParent;
        float[][] lastBones;
        float[] lastTip;
        Map<String, float[]> sample=Collections.emptyMap();
        Map<String, float[]> raw=Collections.emptyMap();

        State(AutoCloseable rig, String[] bones, int frame,
                float stiffness, float damping, float gravity,
                float[] root, float[] parent, float[][] boneWorld,
                float[] terminalTip) {
            this.rig=rig;
            this.bones=bones.clone();
            this.frame=frame;
            this.stiffness=stiffness;
            this.damping=damping;
            this.gravity=gravity;
            this.lastRoot=root.clone();
            this.lastParent=parent.clone();
            this.lastBones=copyMatrices(boneWorld);
            this.lastTip=terminalTip.clone();
        }
    }

    private final Map<String, State> scenes = new HashMap<String, State>();

    public static boolean isRequested() { return REQUESTED; }

    /** A scene was newly initialized and can publish a settled paused pose. */
    public boolean wasRecreatedThisSample() { return recreatedLastSample; }

    private boolean initialize(File gameDir) {
        if(!REQUESTED)return false;
        if(attempted)return available;
        attempted=true;
        try {
            Class<?> bootstrap=Class.forName(
                    "com.example.examplemod.physics.JoltJava8Bootstrap");
            Method start=bootstrap.getMethod("available",File.class);
            available=Boolean.TRUE.equals(start.invoke(null,gameDir));
            if(available) {
                Class<?> klass=Class.forName(RIG);
                create=klass.getMethod("create",int.class,float[][].class,
                        float[].class,float[].class,
                        float.class,float.class,float.class);
                setGravity=klass.getMethod("setGravity",float.class);
                advance=klass.getMethod("advance",int.class,
                        float[].class,float[].class);
                settle=klass.getMethod("settle",int.class);
                localSwings=klass.getMethod("localBoneSwings",
                        float[][].class,float[].class);
                if(RAW_DIAGNOSTIC) {
                    try {
                        nativeDirections=klass.getMethod("rawPhysicalWorldDirections");
                    } catch(NoSuchMethodException oldPrototype) {
                        // Do not disable working physics because a diagnostic
                        // was added after the user's local JAR was downloaded.
                        if(!nativeDirectionWarning) {
                            nativeDirectionWarning=true;
                            System.err.println("[BBS Editor][Jolt][Raw] "
                                    + "Installed prototype JAR lacks raw direction API; "
                                    + "download a newer CI artifact for native-vs-Euler comparison");
                        }
                    }
                }
            }
        } catch(Throwable error) {
            available=false;
            System.err.println("[BBS Editor][Jolt] Optional Java8 prototype unavailable: "+error);
        }
        if(!available && !diagnosticLogged) {
            diagnosticLogged=true;
            System.err.println("[BBS Editor][Jolt] Falling back to legacy physics");
        }
        return available;
    }

    /**
     * Null means Jolt unavailable (caller should use legacy solver).
     * An initialized or reset scene returns its bounded gravity-settled pose.
     * A nonempty map is one preview-only swing per native model bone.
     */
    public Map<String, float[]> sample(File gameDir, String modelId,
            String[] boneNames, int frame, float[][] cleanBoneWorld,
            float[] terminalTipWorld, float[] cleanParentWorld,
            float stiffness, float damping, float gravity) {
        recreatedLastSample=false;
        if(!initialize(gameDir))return null;
        if(modelId==null || boneNames==null || cleanBoneWorld==null ||
                boneNames.length!=cleanBoneWorld.length ||
                boneNames.length<2 || cleanParentWorld==null ||
                cleanBoneWorld[0]==null || cleanBoneWorld[0].length!=16 ||
                terminalTipWorld==null || terminalTipWorld.length!=3)return null;
        String id=modelId;
        float[] root=new float[] {cleanBoneWorld[0][12],
                cleanBoneWorld[0][13],cleanBoneWorld[0][14]};
        State state=scenes.get(id);
        boolean recreate=state==null || frame<state.frame ||
                frame>state.frame+1 ||
                !Arrays.equals(state.bones,boneNames) ||
                state.stiffness!=stiffness || state.damping!=damping ||
                state.gravity!=gravity;
        // Editor can re-evaluate a paused frame after numerical bone edits.
        if(!recreate && frame==state.frame &&
                (!closeEnough(root,state.lastRoot) ||
                 !closeEnough(cleanParentWorld,state.lastParent) ||
                 !sameMatrices(cleanBoneWorld,state.lastBones) ||
                 !closeEnough(terminalTipWorld,state.lastTip)))recreate=true;
        if(recreate) {
            dispose(id);
            try {
                Object instance=create.invoke(null,frame,cleanBoneWorld,
                        terminalTipWorld,cleanParentWorld,stiffness,damping,0.6F);
                if(!(instance instanceof AutoCloseable))
                    throw new IllegalStateException("Invalid Jolt preview scene");
                AutoCloseable scene=(AutoCloseable)instance;
                try {
                    setGravity.invoke(instance,gravity);
                    // The source frame may be a static pose or direct seek.
                    // Relax only once on reset; never simulate every render.
                    settle.invoke(instance,24);
                    state=new State(scene,boneNames,frame,stiffness,damping,
                            gravity,root,cleanParentWorld,cleanBoneWorld,
                            terminalTipWorld);
                    state.sample=collect(scene,boneNames,cleanBoneWorld,cleanParentWorld);
                    state.raw=collectNativeDirections(scene,boneNames);
                    scenes.put(id,state);
                    recreatedLastSample=true;
                    return copy(state.sample);
                } catch(Throwable failure) {
                    scene.close();
                    throw failure;
                }
            } catch(Throwable error) {
                report(id,error);
                return null;
            }
        }
        if(frame==state.frame)
            return copy(state.sample);
        try {
            advance.invoke(state.rig,frame,root,cleanParentWorld);
            Map<String,float[]> result=collect(state.rig,boneNames,
                    cleanBoneWorld,cleanParentWorld);
            state.sample=copy(result);
            state.raw=collectNativeDirections(state.rig,boneNames);
            state.frame=frame;
            state.lastRoot=root;
            state.lastParent=cleanParentWorld.clone();
            state.lastBones=copyMatrices(cleanBoneWorld);
            state.lastTip=terminalTipWorld.clone();
            return result;
        } catch(Throwable error) {
            dispose(id);
            report(id,error);
            return null;
        }
    }

    /**
     * Raw rigid-body directions for the EXACT scene sample returned by
     * sample(). If the installed prototype predates this optional API,
     * returns an empty map and keeps the main preview working.
     */
    public Map<String,float[]> rawDirectionsFor(String modelId, int frame) {
        if(!RAW_DIAGNOSTIC || modelId==null)return Collections.emptyMap();
        State state=scenes.get(modelId);
        if(state==null || state.frame!=frame || state.raw==null)
            return Collections.emptyMap();
        return copy(state.raw);
    }

    private Map<String,float[]> collectNativeDirections(
            AutoCloseable rig, String[] names) {
        if(!RAW_DIAGNOSTIC || nativeDirections==null)
            return Collections.emptyMap();
        try {
            float[][] raw=(float[][])nativeDirections.invoke(rig);
            if(raw==null || raw.length!=names.length)
                throw new IllegalArgumentException("Wrong native Jolt segment count");
            Map<String,float[]> result=new LinkedHashMap<String,float[]>();
            for(int i=0;i<raw.length;i++) {
                if(raw[i]==null || raw[i].length!=3)
                    throw new IllegalArgumentException("Wrong native direction size");
                float norm=0F;
                for(float component:raw[i]) {
                    if(!Float.isFinite(component))
                        throw new IllegalArgumentException("Non-finite native direction");
                    norm+=component*component;
                }
                if(norm<0.98F || norm>1.02F)
                    throw new IllegalArgumentException("Unnormalized native direction");
                result.put(names[i],raw[i].clone());
            }
            return result;
        } catch(Throwable error) {
            // Diagnostics can fail independently of a healthy physics scene.
            if(!nativeDirectionWarning) {
                nativeDirectionWarning=true;
                System.err.println("[BBS Editor][Jolt][Raw] "
                        + "Native direction probe unavailable, physics unchanged: "+error);
            }
            return Collections.emptyMap();
        }
    }

    private Map<String,float[]> collect(AutoCloseable rig, String[] names,
            float[][] cleanBones, float[] parentWorld) throws Exception {
        float[][] swings=(float[][])localSwings.invoke(rig,cleanBones,parentWorld);
        if(swings==null || swings.length!=names.length)
            throw new IllegalStateException("Wrong Jolt pose bone count");
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(int i=0;i<swings.length;i++) {
            if(swings[i]==null || swings[i].length!=4)
                throw new IllegalStateException("Invalid Jolt swing");
            for(float value:swings[i])
                if(!Float.isFinite(value))
                    throw new IllegalStateException("Non-finite Jolt swing");
            result.put(names[i],swings[i].clone());
        }
        return result;
    }

    private static float[][] copyMatrices(float[][] source) {
        float[][] out=new float[source.length][];
        for(int i=0;i<source.length;i++)
            out[i]=source[i]==null?null:source[i].clone();
        return out;
    }

    private static boolean sameMatrices(float[][] x, float[][] y) {
        if(x==null || y==null || x.length!=y.length)return false;
        for(int i=0;i<x.length;i++)
            if(!closeEnough(x[i],y[i]))return false;
        return true;
    }

    private static boolean closeEnough(float[] x, float[] y) {
        if(x==null || y==null || x.length!=y.length)return false;
        for(int i=0;i<x.length;i++)
            if(Math.abs(x[i]-y[i])>1E-5F)return false;
        return true;
    }

    private static Map<String,float[]> copy(Map<String,float[]> source) {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,float[]> e:source.entrySet())
            result.put(e.getKey(),e.getValue().clone());
        return result;
    }

    private void report(String id, Throwable failure) {
        Throwable cause=failure.getCause()==null?failure:failure.getCause();
        System.err.println("[BBS Editor][Jolt] Scene '"+id+
                "' unsupported/failed; using legacy solver: "+cause);
    }

    private void dispose(String id) {
        State removed=scenes.remove(id);
        if(removed!=null)try {removed.rig.close();}
        catch(Exception error) {
            System.err.println("[BBS Editor][Jolt] Scene cleanup failed: "+error);
        }
    }

    /** Call when leaving the editor or replacing the scene. */
    public void clear() {
        for(String id:new java.util.ArrayList<String>(scenes.keySet()))
            dispose(id);
    }
}
