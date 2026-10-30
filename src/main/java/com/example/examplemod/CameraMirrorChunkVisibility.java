package com.example.examplemod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * A SECOND VIEW of the SAME compiled chunks, without a second WorldClient,
 * RenderGlobal, ViewFrustum, VBO or rebuild queue.
 *
 * Minecraft 1.12.2 RenderGlobal.renderBlockLayer() does not enumerate chunks;
 * it consumes the private renderInfos list prepared by setupTerrain() for
 * the original camera. Rendering from a reflected view with that same list
 * shows only sky/void in directions the original camera culled.
 *
 * Temporarily replace ONLY renderInfos with independently frustum-tested
 * references to existing ViewFrustum.renderChunks. Never call setupTerrain,
 * updateChunkPositions, loadRenderers or updateChunks. Restore the exact
 * original list and renderContainer origin before returning.
 */
public final class CameraMirrorChunkVisibility
{
    private static Field infosField, frustumField, containerField,
            dispatcherField;
    private static final Map<RenderChunk,Long> requestedCompilation=
            new WeakHashMap<RenderChunk,Long>();
    private static Constructor<?> infoConstructor;
    private static boolean initialized;
    private static boolean logged;
    private static boolean reportedFailure;

    private CameraMirrorChunkVisibility(){}

    public static final class Scope implements AutoCloseable
    {
        private final RenderGlobal global;
        private final List<?> original;
        private final ChunkRenderContainer container;
        private final double restoreX,restoreY,restoreZ;
        private boolean closed;

        private Scope(RenderGlobal global,List<?> original,
                ChunkRenderContainer container,
                double restoreX,double restoreY,double restoreZ)
        {
            this.global=global;
            this.original=original;
            this.container=container;
            this.restoreX=restoreX;this.restoreY=restoreY;
            this.restoreZ=restoreZ;
        }

        @Override public void close()
        {
            if(closed)return;
            closed=true;
            try
            {
                infosField.set(global,original);
            }
            catch(IllegalAccessException e)
            {
                throw new IllegalStateException(
                        "Failed to restore main visible chunks",e);
            }
            finally
            {
                // VboRenderList and legacy RenderList both retain the
                // camera origin from initialize(). Do not leave the
                // reflected camera's origin on the main renderer.
                container.initialize(restoreX,restoreY,restoreZ);
            }
        }
    }

    public static Scope begin(Minecraft mc,PreviewCameraPose reflected,
            PreviewCameraPose normal)
    {
        if(mc==null||mc.renderGlobal==null||reflected==null||
                normal==null)return null;
        try
        {
            RenderGlobal global=mc.renderGlobal;
            if(!initialize(global))return null;
            ViewFrustum source=(ViewFrustum)frustumField.get(global);
            ChunkRenderContainer container=
                    (ChunkRenderContainer)containerField.get(global);
            if(source==null||source.renderChunks==null||container==null)
                return null;
            ChunkRenderDispatcher dispatcher=dispatcherField==null?null:
                    (ChunkRenderDispatcher)dispatcherField.get(global);

            List<?> old=(List<?>)infosField.get(global);
            if(old==null)return null;

            // Frustum reads the CURRENT OpenGL projection/modelview.
            // The terrain pass has just installed the reflected camera
            // rotation and projection. Frustum.setPosition supplies the
            // world-space translation, as in vanilla setupTerrain().
            Frustum frustum=new Frustum();
            frustum.setPosition(reflected.x,reflected.y,reflected.z);
            final double limit=mc.gameSettings.renderDistanceChunks*16D+32D;
            final double limitSq=limit*limit;
            List<RenderChunk> visible=new ArrayList<RenderChunk>();
            int scheduled=0;
            long now=System.nanoTime();
            for(RenderChunk chunk:source.renderChunks)
            {
                if(chunk==null)continue;
                BlockPos pos=chunk.getPosition();
                double dx=pos.getX()+8D-reflected.x;
                double dz=pos.getZ()+8D-reflected.z;
                if(dx*dx+dz*dz>limitSq)continue;
                AxisAlignedBB bounds=new AxisAlignedBB(pos,
                        pos.add(16,16,16));
                if(!frustum.isBoundingBoxInFrustum(bounds))continue;
                CompiledChunk compiled=chunk.getCompiledChunk();

                // The rear-side chunks may never have been drawn or
                // compiled by the normal camera. Schedule their EXISTING
                // RenderChunk buffers, at a small budget per frame,
                // without allocating a second world or chunk renderer.
                if(dispatcher!=null && scheduled<4 &&
                        (compiled==null||compiled==CompiledChunk.DUMMY||
                         chunk.needsUpdate()))
                {
                    Long last=requestedCompilation.get(chunk);
                    if(last==null||now-last.longValue()>1500000000L)
                    {
                        requestedCompilation.put(chunk,now);
                        if(dispatcher.updateChunkLater(chunk))
                        {
                            chunk.clearNeedsUpdate();
                            scheduled++;
                        }
                    }
                }
                if(compiled==null||compiled==CompiledChunk.DUMMY||
                        compiled.isEmpty())continue;
                visible.add(chunk);
            }
            // Solid chunks front-to-back. Translucent chunks are rendered
            // by RenderGlobal in REVERSE order using the same list.
            visible.sort(new Comparator<RenderChunk>()
            {
                @Override public int compare(RenderChunk a,RenderChunk b)
                {
                    return Double.compare(distanceSq(a,reflected),
                            distanceSq(b,reflected));
                }
            });
            List<Object> mirrorInfos=new ArrayList<Object>(visible.size());
            for(RenderChunk chunk:visible)
            {
                Object info;
                if(infoConstructor.getParameterTypes().length==4)
                    info=infoConstructor.newInstance(global,chunk,null,0);
                else
                    info=infoConstructor.newInstance(chunk,null,0);
                mirrorInfos.add(info);
            }

            Scope scope=new Scope(global,old,container,
                    normal.x,normal.y,normal.z);
            try
            {
                infosField.set(global,mirrorInfos);
                container.initialize(reflected.x,reflected.y,reflected.z);
            }
            catch(Throwable t)
            {
                scope.close();
                throw t;
            }
            if(!logged)
            {
                logged=true;
                System.out.println("[BBS Editor][Mirror] Shared chunks: "+
                        "mirror-visible="+visible.size()+
                        " original-visible="+old.size()+
                        "; no secondary world or RenderGlobal.");
            }
            return scope;
        }
        catch(Throwable t)
        {
            if(!reportedFailure)
            {
                reportedFailure=true;
                System.err.println("[BBS Editor][Mirror] Reflective frustum "+
                        "selection failed; capture will use fallback: "+t);
                t.printStackTrace();
            }
            return null;
        }
    }

    private static double distanceSq(RenderChunk chunk,
            PreviewCameraPose pose)
    {
        BlockPos p=chunk.getPosition();
        double x=p.getX()+8D-pose.x;
        double y=p.getY()+8D-pose.y;
        double z=p.getZ()+8D-pose.z;
        return x*x+y*y+z*z;
    }

    private static boolean initialize(RenderGlobal global)
            throws ReflectiveOperationException
    {
        if(initialized)return infosField!=null&&
                frustumField!=null&&containerField!=null&&
                infoConstructor!=null;
        initialized=true;
        Class<?> cls=global.getClass();
        infosField=findField(cls,"renderInfos",List.class);
        frustumField=findField(cls,"viewFrustum",ViewFrustum.class);
        containerField=findField(cls,"renderContainer",
                ChunkRenderContainer.class);
        dispatcherField=findField(cls,"renderDispatcher",
                ChunkRenderDispatcher.class);
        for(Class<?> nested:RenderGlobal.class.getDeclaredClasses())
        {
            if(!nested.getSimpleName().equals(
                    "ContainerLocalRenderInformation"))continue;
            for(Constructor<?> ctor:nested.getDeclaredConstructors())
            {
                Class<?>[] args=ctor.getParameterTypes();
                if((args.length==4 &&
                        RenderGlobal.class.isAssignableFrom(args[0]) &&
                        RenderChunk.class.isAssignableFrom(args[1]))||
                    (args.length==3 &&
                        RenderChunk.class.isAssignableFrom(args[0])))
                {
                    ctor.setAccessible(true);
                    infoConstructor=ctor;
                    break;
                }
            }
        }
        return infosField!=null&&frustumField!=null&&
                containerField!=null&&infoConstructor!=null;
    }

    private static Field findField(Class<?> cls,String name,Class<?> type)
    {
        try
        {
            Field named=cls.getDeclaredField(name);
            if(type.isAssignableFrom(named.getType()))
            {
                named.setAccessible(true);
                return named;
            }
        }
        catch(ReflectiveOperationException ignored){}
        // Runtime mappings may rename the MCP field. In these cases
        // non-List fields have unique types in RenderGlobal.
        if(type!=List.class)
        {
            for(Field f:cls.getDeclaredFields())
            {
                if(type.isAssignableFrom(f.getType()))
                {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null; // fail closed instead of changing a different list
    }
}
