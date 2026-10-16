package com.example.examplemod;

/**
 * Mesh-backed one-sided collision constraint. The caller must supply a
 * verified outward direction and positions in the SAME coordinate space.
 * No guess about Emoticons winding, Chameleon Y=180, or actor transforms.
 */
public final class BodyPartMeshSurfaceConstraint
        implements BodyPartChainSolver.SurfaceConstraint
{
    private final EmoticonsMeshCollisionSolver mesh;
    private final BodyPartChainSolver.Vec3 outward;
    private final float clearance;
    private final float searchRadius;

    public BodyPartMeshSurfaceConstraint(EmoticonsMeshCollisionSolver mesh,
            BodyPartChainSolver.Vec3 verifiedOutward, float clearance)
    {
        if(mesh==null || verifiedOutward==null)
            throw new IllegalArgumentException("Missing mesh or outward direction");
        float length=verifiedOutward.length();
        if(!(length>1E-6F) || !Float.isFinite(length) ||
                !Float.isFinite(clearance) || clearance<0F || clearance>0.1F)
            throw new IllegalArgumentException("Invalid mesh collision parameters");
        this.mesh=mesh;
        this.outward=verifiedOutward.scale(1F/length);
        this.clearance=clearance;
        this.searchRadius=Math.max(0.075F,clearance+0.05F);
    }

    @Override
    public BodyPartChainSolver.Vec3 project(BodyPartChainSolver.Vec3 point,
            int particleIndex)
    {
        if(point==null || particleIndex<=0)return point;
        EmoticonsMeshCollisionSolver.Contact contact=
                mesh.nearest(point,searchRadius);
        if(contact==null)return point;
        BodyPartChainSolver.Vec3 n=mesh.windingNormal(contact);
        if(n==null)return point;
        // Treat winding as ambiguous. Align with explicitly verified
        // outward direction and reject grazing/sidewall triangles.
        float alignment=n.x*outward.x+n.y*outward.y+n.z*outward.z;
        if(Math.abs(alignment)<0.5F)return point;
        if(alignment<0F)n=n.scale(-1F);
        BodyPartChainSolver.Vec3 offset=point.sub(contact.nearest);
        float signed=offset.x*n.x+offset.y*n.y+offset.z*n.z;
        if(signed>=clearance)return point;
        float move=Math.min(0.02F,clearance-signed);
        return point.add(n.scale(move));
    }
}
