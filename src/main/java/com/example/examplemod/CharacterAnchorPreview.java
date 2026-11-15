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
    private static int sceneIndex=Integer.MIN_VALUE;
    private static final Map<String,BodyPartModelData> CACHE=
            new HashMap<String,BodyPartModelData>();
    private CharacterAnchorPreview(){}
    public static void bind(EditorSceneState value)
    {
        int index=value==null?Integer.MIN_VALUE:value.getSelectedScene();
        if(scene!=value||sceneIndex!=index)
        {
            scene=value;
            sceneIndex=index;
            CACHE.clear();
        }
    }
    public static void clear()
    {
        scene=null;sceneIndex=Integer.MIN_VALUE;CACHE.clear();
    }
    public static boolean hidesActor(String actorId,double sceneTick)
    {
        return scene!=null&&CharacterAnchorTransfers.hidesActor(
                scene.getActors(),actorId,sceneTick);
    }

    /**
     * Migrate the older renderer-only keys to actual editable BODY PARTS.
     * Run once from the editor GUI per observed state, never from actor,
     * mirror or 60fps export render callbacks. Registered models survive
     * saving editor.dat and subsequent reloads; stable transfer identities
     * guarantee that revisiting a frame cannot duplicate them.
     */
    public static void synchronizeEditableParts(EditorSceneState current)
    {
        if(current==null)return;
        bind(current);
        BodyPartsEditorController parts=current.getBodyPartsController();
        if(parts==null)return;
        java.util.List<CharacterAnchorTransfers.Transfer> events=
                CharacterAnchorTransfers.all(current.getActors());
        java.util.Map<String,BodyPartModelData> previous=
                new java.util.HashMap<String,BodyPartModelData>();
        boolean modified=false;
        int sceneLength=Math.max(2,current.getSceneLength());
        for(CharacterAnchorTransfers.Transfer event:events)
        {
            if(!event.attached())
            {
                previous.remove(event.itemId);
                continue;
            }
            BodyPartModelData prior=previous.get(event.itemId);
            if(prior==null&&!event.sourcePartId.isEmpty())
                for(BodyPartModelData original:
                        parts.getModelsForActor(event.sourceActorId))
                    if(original!=null&&
                            event.sourcePartId.equals(original.getAnchorId()))
                    {
                        prior=original;
                        break;
                    }
            BodyPartModelData model=parts.findAnchorAttachment(
                    event.ownerActorId,event.itemId,event.frame);
            if(model==null)
            {
                model=parts.ensureAnchorAttachment(event,prior,sceneLength);
                if(model!=null)modified=true;
            }
            if(model!=null)previous.put(event.itemId,model);
        }

        // A deleted anchor key must never leave a permanent ghost BODY
        // PART in the saved actor editor. Do not alter normal attachments.
        for(java.util.Map.Entry<String,
                java.util.List<BodyPartModelData>> entry:
                parts.snapshotActorModels().entrySet())
            for(BodyPartModelData model:entry.getValue())
            {
                if(model==null||!model.isAnchorAttachment())continue;
                boolean exists=false;
                for(CharacterAnchorTransfers.Transfer event:events)
                    if(event.attached()&&
                            entry.getKey().equals(event.ownerActorId)&&
                            model.getAnchorItemId().equals(event.itemId)&&
                            model.getAnchorEventFrame()==event.frame)
                    {
                        exists=true;
                        break;
                    }
                if(!exists&&parts.removeAnchorAttachment(
                        entry.getKey(),model.getAnchorItemId(),
                        model.getAnchorEventFrame()))
                    modified=true;
            }
        if(modified&&current.getSaveController()!=null)
            current.getSaveController().markDirty();
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
            {
                if(model==null)continue;
                if(model.isAnchorAttachment())
                {
                    // Keep every authored recipient in BODY PARTS, but
                    // render only the current owner and the exact event
                    // instance. A hand-off at the same frame switches
                    // render representation atomically.
                    if(CharacterAnchorTransfers.isActiveEditableAttachment(
                            scene.getActors(),model.getAnchorItemId(),
                            actorId,model.getAnchorEventFrame(),sceneTick))
                        result.add(model);
                }
                else if(!CharacterAnchorTransfers.hidesPart(
                        scene.getActors(),actorId,model.getAnchorId(),
                        sceneTick))
                    result.add(model);
            }
        for(CharacterAnchorTransfers.Transfer transfer:
                CharacterAnchorTransfers.forOwner(
                        scene.getActors(),actorId,sceneTick))
        {
            if(transfer.boneFromItem==null||
                    transfer.ownerBone.isEmpty())continue;
            // The registered, editable BODY PART is authoritative. The
            // old virtual renderer is only a temporary compatibility
            // fallback if registration failed while loading a scene.
            boolean registered=false;
            for(BodyPartModelData model:result)
                if(model.isAnchorAttachment()&&
                        transfer.itemId.equals(model.getAnchorItemId())&&
                        transfer.frame==model.getAnchorEventFrame())
                {
                    registered=true;
                    break;
                }
            if(registered)continue;
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
                    // On actor-to-actor hand-off preserve the source BODY
                    // PART's internal keyframes, physics, morph changes and
                    // skin settings as well as its visible Morph. Only its
                    // parent attachment and global offset are rebased.
                    if(!transfer.sourcePartId.isEmpty()&&
                            scene.getBodyPartsController()!=null)
                        for(BodyPartModelData original:
                                scene.getBodyPartsController()
                                    .getModelsForActor(
                                            transfer.sourceActorId))
                        {
                            if(original==null||
                                    !transfer.sourcePartId.equals(
                                        original.getAnchorId()))continue;
                            net.minecraft.nbt.NBTTagCompound skeleton=
                                    new net.minecraft.nbt.NBTTagCompound();
                            skeleton.setTag("Bones",
                                    EditorSceneSerializer.writeBones(
                                            original.getBones()));
                            EditorSceneSerializer.readBones(
                                    virtual.getBones(),skeleton);
                            virtual.restoreMorphKeys(
                                    original.getMorphKeysSnapshot());
                            virtual.restoreModelKeys(
                                    original.getModelKeysSnapshot());
                            virtual.readPhysicsSettings(
                                    original.writePhysicsSettings());
                            virtual.readColliderSettings(
                                    original.writeColliderSettings());
                            virtual.setWeldEnabled(
                                    original.isWeldEnabled());
                            if(original.hasExplicitClothPhysicsSetting())
                                virtual.setClothPhysicsEnabled(
                                        original.isClothPhysicsEnabled());
                            CharacterTimelineController tracks=
                                    CharacterTimelineController.fromNBT(
                                        original.getCharacterTimeline().toNBT());
                            if(tracks!=null)
                            {
                                virtual.getCharacterTimeline().clear();
                                virtual.getCharacterTimeline().getTracks()
                                        .addAll(tracks.getTracks());
                            }
                            break;
                        }
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
