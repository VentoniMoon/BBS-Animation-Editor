package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Non-destructive preview projection of CHARACTER anchor ownership.
 *
 * Original source actors and Body Part tracks stay in editor.dat. A
 * resolved transfer only changes visibility of the original and creates
 * a transient BodyPart on the currently owning actor. Seeking backwards
 * regenerates the original representation with no cleanup key required.
 *
 * All local matrices are authored from exact native world bone matrices,
 * never recomputed from X/Y/Z subtraction during render.
 */
public final class CharacterAnchorPreview
{
    private static EditorSceneState scene;
    private static final Map<String,BodyPartModelData> CACHE=
            new HashMap<String,BodyPartModelData>();
    private CharacterAnchorPreview(){}
    public static void bind(EditorSceneState value)
    {
        if(scene!=value)
        {
            scene=value;
            CACHE.clear();
        }
    }
    public static void clear(){scene=null;CACHE.clear();}
    public static boolean hidesActor(String actorId,double sceneTick)
    {
        return scene!=null&&CharacterAnchorTransfers.hidesActor(
                scene.getActors(),actorId,sceneTick);
    }
    public static List<BodyPartModelData> modelsFor(String actorId,
            double sceneTick,List<BodyPartModelData> originals)
    {
        List<BodyPartModelData> result=new ArrayList<BodyPartModelData>();
        if(scene==null)
        {
            if(originals!=null)result.addAll(originals);
            return result;
        }
        int frame=Math.max(0,(int)Math.floor(sceneTick));
        if(originals!=null)
            for(BodyPartModelData model:originals)
                if(model!=null&&!CharacterAnchorTransfers.hidesPart(
                        scene.getActors(),actorId,model.getAnchorId(),sceneTick))
                    result.add(model);
        for(CharacterAnchorTransfers.Transfer transfer:
                CharacterAnchorTransfers.forOwner(
                        scene.getActors(),actorId,sceneTick))
        {
            if(transfer.boneFromItem==null||
                    transfer.ownerBone.isEmpty())continue;
            String signature=transfer.itemId+"|"+transfer.frame+"|"+
                    actorId+"|"+transfer.ownerBone+"|"+
                    transfer.morph.toString()+"|"+
                    java.util.Arrays.toString(transfer.boneFromItem);
            BodyPartModelData virtual=CACHE.get(signature);
            if(virtual==null)
            {
                try
                {
                    AbstractMorph morph=MorphManager.INSTANCE.morphFromNBT(
                            transfer.morph.copy());
                    if(morph==null)continue;
                    virtual=new BodyPartModelData(
                            morph.name==null?"anchored item":morph.name,
                            transfer.ownerBone,null,
                            new ArrayList<AnimationBone>(),0,
                            Integer.MAX_VALUE-2);
                    virtual.setMorph(morph);
                    AnimationTransform local=AnchorTransferMath
                            .toBodyPartTransform(transfer.boneFromItem);
                    AnimationTransform target=virtual.getGlobalTransform();
                    target.setPosition(local.getPositionX(),
                            local.getPositionY(),local.getPositionZ());
                    target.setRotation(local.getRotationX(),
                            local.getRotationY(),local.getRotationZ());
                    target.setScale(local.getScaleX(),local.getScaleY(),
                            local.getScaleZ());
                    CACHE.put(signature,virtual);
                }
                catch(RuntimeException invalid)
                {
                    System.err.println("[BBS Editor][Anchors] Cannot render "+
                            transfer.itemId+" on "+transfer.ownerBone+
                            ": "+invalid.getMessage());
                    continue;
                }
            }
            result.add(virtual);
        }
        return result;
    }
}
