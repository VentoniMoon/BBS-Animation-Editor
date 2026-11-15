package com.example.examplemod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Java 8 guard for the most dangerous XPBD integration failure:
 * accidentally leaking temporary empty native fabric-cube lists into
 * another ChameleonMorph or the next preview frame.
 */
public final class ChameleonSkirtXPBDNativeScopeSmoke
{
    public static final class ModelBoneStub
    {
        public List<Object> cubes=new ArrayList<Object>();
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception
    {
        ChameleonWeldPreviewPass scope=
                ChameleonWeldPreviewPass.beginPreparedModel(null);
        Field hidden=ChameleonWeldPreviewPass.class.getDeclaredField(
                "hiddenFabricCubes");
        hidden.setAccessible(true);
        IdentityHashMap<Object,Object> snapshots=
                (IdentityHashMap<Object,Object>)hidden.get(scope);
        ModelBoneStub[] bones=new ModelBoneStub[40];
        List<Object>[] originals=(List<Object>[])new List[40];
        for(int i=0;i<bones.length;i++)
        {
            bones[i]=new ModelBoneStub();
            bones[i].cubes.add("nativeCube"+i);
            originals[i]=bones[i].cubes;
            snapshots.put(bones[i],originals[i]);
            bones[i].cubes=Collections.emptyList();
        }
        scope.close();
        for(int i=0;i<bones.length;i++)
        {
            if(bones[i].cubes!=originals[i] ||
                    bones[i].cubes.size()!=1)
                throw new AssertionError(
                        "Native cube list not restored for strip "+i);
        }
        if(!snapshots.isEmpty())
            throw new AssertionError("Restoration scope leaked");
        // Also ensure a second close is safe in the event of a
        // nested finally/exception handler.
        scope.close();
        System.out.println("ChameleonSkirtXPBDNativeScopeSmoke OK");
    }
}
