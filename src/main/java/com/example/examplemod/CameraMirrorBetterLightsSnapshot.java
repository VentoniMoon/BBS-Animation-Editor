package com.example.examplemod;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * Capture the actual BetterLights GLSL struct AFTER the native renderer
 * has populated it, then initialize mirror-ONLY compiled shader clones.
 *
 * BetterLights updates the native OptiFine programs asynchronously from
 * our auxiliary stage calls. At the first mirror draw lightCount may be 0
 * while the native terrain program receives a verified lightCount>0 later
 * in that same frame. This snapshot avoids inventing color, intensity,
 * attenuation, shadows or source properties from the editor model.
 *
 * Only native GPU values are captured, and only when an active caster's
 * world-space position matches the editor's registered LightCaster.
 * The snapshot is rejected if the caster subsequently moves.
 */
public final class CameraMirrorBetterLightsSnapshot
{
    private static final class Entry
    {
        final String name;
        final int type;
        final float[] floats;
        final int[] integers;
        Entry(String name,int type,float[] f,int[] ints)
        {
            this.name=name;this.type=type;
            floats=f;integers=ints;
        }
    }

    private static final List<Entry> snapshot=new ArrayList<Entry>();
    private static final List<double[]> origin=new ArrayList<double[]>();
    private static int sourceCount;
    private static double[] snapshotCamera;
    private static boolean captureLogged,restoreLogged,staleScreenLogged;

    private CameraMirrorBetterLightsSnapshot(){}

    public static void reset()
    {
        snapshot.clear();origin.clear();sourceCount=0;
        snapshotCamera=null;
        captureLogged=false;restoreLogged=false;staleScreenLogged=false;
    }

    private static int readInt(int pid,String name)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return 0;
        IntBuffer b=BufferUtils.createIntBuffer(16);
        GL20.glGetUniform(pid,loc,b);
        return b.get(0);
    }

    private static float[] readPosition(int pid,String name)
    {
        int loc=GL20.glGetUniformLocation(pid,name);
        if(loc<0)return null;
        FloatBuffer b=BufferUtils.createFloatBuffer(16);
        GL20.glGetUniform(pid,loc,b);
        return new float[]{b.get(0),b.get(1),b.get(2)};
    }

    private static boolean matches(float[] xyz,List<double[]> expected)
    {
        if(xyz==null)return false;
        for(double[] p:expected)
            if(p!=null&&p.length>=3&&
                    Math.abs(xyz[0]-p[0])<0.2D&&
                    Math.abs(xyz[1]-p[1])<0.2D&&
                    Math.abs(xyz[2]-p[2])<0.2D)
                return true;
        return false;
    }

    /**
     * BetterLights may populate enabled GPU light slots while the native
     * OptiFine program still reports lightCount=0. Only trust a contiguous
     * prefix of slots whose WORLD coordinates match editor-owned casters.
     * No light is invented from a nonzero GPU color or an arbitrary slot.
     */
    public static int validatedPrefix(int pid,List<double[]> sources)
    {
        if(pid<=0||sources==null||sources.isEmpty())return 0;
        int count=0;
        for(int i=0;i<Math.min(16,sources.size());i++)
        {
            String base="lights["+i+"].";
            int enabledLoc=GL20.glGetUniformLocation(pid,
                    base+"isEnabled");
            if(enabledLoc<0)break;
            IntBuffer enabled=BufferUtils.createIntBuffer(16);
            GL20.glGetUniform(pid,enabledLoc,enabled);
            if(enabled.get(0)!=1)break;
            if(!matches(readPosition(pid,base+"position"),sources))
                break;
            count++;
        }
        return count;
    }

    private static boolean sourceStillValid(List<double[]> positions)
    {
        if(sourceCount<1||positions==null||positions.isEmpty())
            return false;
        for(double[] expected:origin)
        {
            float[] p={(float)expected[0],(float)expected[1],
                    (float)expected[2]};
            if(!matches(p,positions))return false;
        }
        return true;
    }

    /**
     * Reads original OptiFine program values, never a mirrored clone.
     * A lightCount=1 with an unregistered/unrelated source is rejected.
     */
    public static void capture(int pid,List<double[]> sources,
            double[] mainCamera)
    {
        if(pid<=0||sources==null||sources.isEmpty())return;
        int nativeCount=Math.min(16,readInt(pid,"lightCount"));
        int provenCount=validatedPrefix(pid,sources);
        // Native count can be 0 or stale despite populated registered
        // light uniforms in the private mirror's linked program.
        int count=Math.max(nativeCount,provenCount);
        if(count<=0)return;
        List<double[]> verified=new ArrayList<double[]>();
        for(int i=0;i<count;i++)
        {
            float[] position=readPosition(pid,
                    "lights["+i+"].position");
            if(matches(position,sources))
                verified.add(new double[]{position[0],position[1],
                        position[2]});
        }
        if(verified.isEmpty())return;

        List<Entry> next=new ArrayList<Entry>();
        int uniforms=Math.min(2048,GL20.glGetProgrami(pid,
                GL20.GL_ACTIVE_UNIFORMS));
        for(int i=0;i<uniforms;i++)
        {
            IntBuffer meta=BufferUtils.createIntBuffer(2);
            String base=GL20.glGetActiveUniform(pid,i,256,meta);
            if(base==null||!base.startsWith("lights["))continue;
            int countInArray=Math.max(1,Math.min(16,meta.get(0)));
            int type=meta.get(1);
            if(!supported(type))continue;
            for(int j=0;j<countInArray;j++)
            {
                String name=base;
                if(countInArray>1)
                {
                    int k=base.indexOf("[0]");
                    if(k<0)continue;
                    name=base.substring(0,k)+"["+j+"]"+
                            base.substring(k+3);
                }
                int loc=GL20.glGetUniformLocation(pid,name);
                if(loc<0)continue;
                if(integer(type))
                {
                    IntBuffer value=BufferUtils.createIntBuffer(16);
                    GL20.glGetUniform(pid,loc,value);
                    int[] arr=new int[4];
                    for(int z=0;z<4;z++)arr[z]=value.get(z);
                    next.add(new Entry(name,type,null,arr));
                }
                else
                {
                    FloatBuffer value=BufferUtils.createFloatBuffer(16);
                    GL20.glGetUniform(pid,loc,value);
                    float[] arr=new float[16];
                    for(int z=0;z<16;z++)arr[z]=value.get(z);
                    next.add(new Entry(name,type,arr,null));
                }
            }
        }
        if(next.isEmpty())return;
        snapshot.clear();snapshot.addAll(next);
        origin.clear();origin.addAll(verified);
        sourceCount=count;
        snapshotCamera=mainCamera==null?null:mainCamera.clone();
        if(!captureLogged)
        {
            captureLogged=true;
            System.out.println("[BBS Editor][Mirror][BetterLights] "+
                    "Latched native populated light uniforms: "+
                    "count="+count+" fields="+next.size()+
                    " sourceProgram="+pid+
                    " nativeCount="+nativeCount+
                    " validatedPrefix="+provenCount+
                    " (only verified casters)");
        }
    }

    /** Never apply to original/main OptiFine shaders. */
    public static boolean applyToPrivateClone(int clone,
            List<double[]> currentSources,double[] currentMainCamera)
    {
        if(clone<=0||!sourceStillValid(currentSources)||
                snapshot.isEmpty())return false;
        if("false".equalsIgnoreCase(System.getProperty(
                "bbs.editor.camera.mirrorBSLLightSnapshot","true")))
            return false;
        if(GL20.glGetUniformLocation(clone,"lightCount")<0)
            return false;
        // World-space light properties remain valid when the camera
        // moves, but positionScreenSpace was projected for the camera at
        // snapshot capture time. Never reintroduce a stale projection to
        // a private mirror clone. The following mirror-camera correction
        // will reproject it when the coordinate encoding is verified.
        boolean cameraMoved=snapshotCamera==null||
                currentMainCamera==null||
                CameraMirrorLightSpace.cameraMoved(
                        snapshotCamera,currentMainCamera,0.02D);
        int applied=0;
        for(Entry entry:snapshot)
        {
            if(cameraMoved&&entry.name.endsWith(".positionScreenSpace"))
            {
                if(!staleScreenLogged)
                {
                    staleScreenLogged=true;
                    System.out.println(
                            "[BBS Editor][Mirror][BetterLights] "+
                            "Skipped screen-space uniforms from a "+
                            "different main-camera position; "+
                            "mirror reprojection must use current view");
                }
                continue;
            }
            int loc=GL20.glGetUniformLocation(clone,entry.name);
            if(loc<0)continue;
            if(write(loc,entry))applied++;
        }
        if(applied==0)return false;
        GL20.glUniform1i(GL20.glGetUniformLocation(clone,
                "lightCount"),sourceCount);
        if(!restoreLogged)
        {
            restoreLogged=true;
            System.out.println("[BBS Editor][Mirror][BetterLights] "+
                    "Populated mirror-only GLSL clone="+clone+
                    " from native light data: count="+sourceCount+
                    " fields="+applied+" (main programs unchanged)");
        }
        return true;
    }

    private static boolean integer(int type)
    {
        return type==GL11.GL_INT||type==GL20.GL_BOOL||
                type==GL20.GL_INT_VEC2||type==GL20.GL_INT_VEC3||
                type==GL20.GL_INT_VEC4||type==GL20.GL_BOOL_VEC2||
                type==GL20.GL_BOOL_VEC3||type==GL20.GL_BOOL_VEC4;
    }

    private static boolean supported(int type)
    {
        return integer(type)||type==GL11.GL_FLOAT||
                type==GL20.GL_FLOAT_VEC2||type==GL20.GL_FLOAT_VEC3||
                type==GL20.GL_FLOAT_VEC4||
                type==GL20.GL_FLOAT_MAT4||type==GL20.GL_FLOAT_MAT3;
    }

    private static boolean write(int loc,Entry item)
    {
        int t=item.type;
        if(integer(t))
        {
            int[] v=item.integers;
            if(t==GL20.GL_INT_VEC2||t==GL20.GL_BOOL_VEC2)
                GL20.glUniform2i(loc,v[0],v[1]);
            else if(t==GL20.GL_INT_VEC3||t==GL20.GL_BOOL_VEC3)
                GL20.glUniform3i(loc,v[0],v[1],v[2]);
            else if(t==GL20.GL_INT_VEC4||t==GL20.GL_BOOL_VEC4)
                GL20.glUniform4i(loc,v[0],v[1],v[2],v[3]);
            else GL20.glUniform1i(loc,v[0]);
            return true;
        }
        float[] v=item.floats;
        if(t==GL11.GL_FLOAT)GL20.glUniform1f(loc,v[0]);
        else if(t==GL20.GL_FLOAT_VEC2)
            GL20.glUniform2f(loc,v[0],v[1]);
        else if(t==GL20.GL_FLOAT_VEC3)
            GL20.glUniform3f(loc,v[0],v[1],v[2]);
        else if(t==GL20.GL_FLOAT_VEC4)
            GL20.glUniform4f(loc,v[0],v[1],v[2],v[3]);
        else if(t==GL20.GL_FLOAT_MAT4||t==GL20.GL_FLOAT_MAT3)
        {
            FloatBuffer data=BufferUtils.createFloatBuffer(16);
            data.put(v).flip();
            if(t==GL20.GL_FLOAT_MAT4)
                GL20.glUniformMatrix4(loc,false,data);
            else GL20.glUniformMatrix3(loc,false,data);
        }
        else return false;
        return true;
    }
}
