package com.example.examplemod;

/**
 * State-aware diagnostic mesh contact candidate for an animated hair chain.
 * All inputs MUST already share the same coordinate space. Do not apply the
 * candidate to a pose until this correspondence and triangle winding have
 * been validated against the actual preview.
 */
public final class BodyPartMeshContactTracker
{
    public static final class Result
    {
        public final BodyPartChainSolver.Vec3[] candidate;
        public final int sweptCrossings;
        public final int staticCrossings;
        public final int recoveryCandidates;
        public final float largestCorrection;
        Result(BodyPartChainSolver.Vec3[] pose, int swept, int stat,
                int recovered, float largest)
        {
            candidate=pose; sweptCrossings=swept; staticCrossings=stat;
            recoveryCandidates=recovered; largestCorrection=largest;
        }
    }

    private BodyPartChainSolver.Vec3[] previous;
    private int lastFrame=Integer.MIN_VALUE;

    public void clear()
    {
        previous=null;
        lastFrame=Integer.MIN_VALUE;
    }

    /**
     * One-sided separation candidate. The accepted side is the triangle's
     * winding-positive halfspace, NOT a proven outward body surface.
     * The pinned root (index zero) is never moved.
     *
     * Candidate motion is limited to 0.02 blocks per frame. Existing deep
     * overlaps cannot be fixed instantly, avoiding explosive corrections.
     */
    public Result inspect(int frame, BodyPartChainSolver.Vec3[] points,
            EmoticonsMeshCollisionSolver surface)
    {
        if (points==null || points.length<2 || surface==null)
            return new Result(points,0,0,0,0F);
        boolean consecutive=previous!=null && frame==lastFrame+1 &&
                previous.length==points.length;
        BodyPartChainSolver.Vec3[] proposed=points.clone();
        int swept=0,staticHits=0,recovered=0;
        float largest=0F;
        for(int i=1;i<points.length;i++)
        {
            if(points[i]==null||points[i-1]==null)continue;
            EmoticonsMeshCollisionSolver.SegmentHit staticHit=
                    surface.intersectSegment(points[i-1],points[i]);
            if(staticHit!=null)staticHits++;
            EmoticonsMeshCollisionSolver.SegmentHit sweptHit=null;
            if(consecutive && previous[i]!=null)
            {
                sweptHit=surface.intersectSegment(previous[i],points[i]);
                if(sweptHit!=null)swept++;
            }
            EmoticonsMeshCollisionSolver.Contact contact=
                    surface.nearest(points[i],0.15F);
            if(contact==null)continue;
            float signed=surface.windingSignedDistance(points[i],contact);
            if(!Float.isFinite(signed))continue;

            // Crossing the current frame's surface (or beginning across a
            // segment which intersects it) flags a potential recovery.
            // This may still represent an internal/back-facing triangle.
            boolean potential=(sweptHit!=null || staticHit!=null ||
                    signed < -0.002F);
            if(!potential)continue;
            // Only calculate a modest outward candidate when winding says
            // the point is on the negative side. No automatic pose mutation.
            if(signed>=0.015F)continue;
            float displacement=Math.min(0.02F,0.015F-signed);
            if(displacement<=0F)continue;
            BodyPartChainSolver.Vec3 oriented=surface.windingNormal(contact);
            if(oriented==null)continue;
            proposed[i]=points[i].add(oriented.scale(displacement));
            largest=Math.max(largest,displacement);
            recovered++;
        }
        previous=points.clone();
        lastFrame=frame;
        return new Result(proposed,swept,staticHits,recovered,largest);
    }
}
