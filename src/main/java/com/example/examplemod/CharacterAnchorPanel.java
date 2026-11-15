package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;

/**
 * CHARACTER > ANCHORS. Both pickup and hand-off are the same keyed
 * change-of-owner operation. The item remains a single stable identity:
 * source morph/record and source Body Part are never overwritten by EMPTY.
 */
public final class CharacterAnchorPanel
{
    public static final int HEIGHT=191;

    private static final class Candidate
    {
        final String itemId,sourceActor,sourcePart,label;
        final NBTTagCompound morph;
        Candidate(String id,String actor,String part,String text,
                NBTTagCompound data)
        {
            itemId=id;sourceActor=actor;sourcePart=part;
            label=text;morph=data==null?null:data.copy();
        }
    }
    private int selectedSource,selectedBone;
    private String preferredSourceId="";
    private String preferredBoneName="";
    private CharacterKey selectedKey;
    private Object inspectedMorph;
    private final List<String> cachedBoneNames=new ArrayList<String>();
    private String status="SELECT ITEM + RECEIVER BONE";
    private String actorId="";
    private List<Candidate> choices=new ArrayList<Candidate>();
    private List<String> bones=new ArrayList<String>();
    private int x,y,width;
    private mchorse.blockbuster.common.entity.EntityActor previewActor;
    public void setPreviewActor(
            mchorse.blockbuster.common.entity.EntityActor actor)
    {
        this.previewActor=actor;
    }

    /** A click on an existing anchor key opens its item + target bone,
     * rather than silently editing the first source in the scene list.
     */
    public void setSelectedKey(CharacterKey key)
    {
        if(selectedKey==key)return;
        selectedKey=key;
        if(key==null||!key.hasData(CharacterAnchorTransfers.KEY))return;
        net.minecraft.nbt.NBTTagList transitions=key.getData()
                .getTagList(CharacterAnchorTransfers.KEY,10);
        if(transitions.tagCount()==0)return;
        net.minecraft.nbt.NBTTagCompound transition=
                transitions.getCompoundTagAt(0);
        preferredSourceId=transition.getString("ItemId");
        preferredBoneName=transition.getString("OwnerBone");
    }

    private void update(AnimationEditorScreen editor,
            BlockbusterSceneActorData target,int frame)
    {
        String id=target==null?"":target.getId();
        if(!id.equals(actorId))
        {
            actorId=id;selectedSource=0;selectedBone=0;
            if(selectedKey==null||
                    !selectedKey.hasData(CharacterAnchorTransfers.KEY))
            {
                preferredSourceId="";
                preferredBoneName="";
            }
            inspectedMorph=null;cachedBoneNames.clear();
            status="SELECT ITEM + RECEIVER BONE";
        }
        choices.clear();bones.clear();
        if(editor==null||target==null)return;
        EditorSceneState scene=editor.getSceneState();
        if(scene==null)return;
        Map<String,Candidate> all=new LinkedHashMap<String,Candidate>();
        for(BlockbusterSceneActorData actor:scene.getActors())
        {
            if(actor==null||actor.getActor()==null||
                    actor.getId().equals(target.getId()))continue;
            NBTTagCompound morph=CharacterMorphResolver.getCurrentMorph(
                    actor,frame);
            if(morph!=null&&!morph.hasNoTags())
            {
                String idItem="actor:"+actor.getId();
                all.put(idItem,new Candidate(idItem,actor.getId(),"",
                        actor.getName()+" [ACTOR]",morph));
            }
            BodyPartsEditorController parts=scene.getBodyPartsController();
            if(parts==null)continue;
            for(BodyPartModelData model:
                    parts.getModelsForActor(actor.getId()))
            {
                // This is a receiver view of an EXISTING logical item.
                // Do not offer it as a new independent source: doing so
                // creates a second item ID and duplicates the object.
                if(model==null||model.isAnchorAttachment()||
                        model.getBaseMorph()==null)continue;
                AbstractMorph selected=model.getMorphAt(frame);
                if(selected==null)continue;
                String idItem="part:"+actor.getId()+":"+
                        model.getAnchorId();
                all.put(idItem,new Candidate(idItem,actor.getId(),
                        model.getAnchorId(),model.getModelName()+
                                " ["+actor.getName()+"]",selected.toNBT()));
            }
        }
        // Current logical items appear under their ORIGINAL identity:
        // [HELD] for placing, [WORLD] for picking up a released item,
        // or [ACTOR] for handing off to another actor. Never duplicate
        // an anchored BODY PART as a newly capturable model.
        for(CharacterAnchorTransfers.Transfer current:
                CharacterAnchorTransfers.resolve(
                        scene.getActors(),frame).values())
        {
            String state=current.released()?"WORLD":
                    current.ownerActorId.equals(target.getId())
                            ?"HELD":"ACTOR";
            if(current.attached()||current.released())
                all.put(current.itemId,new Candidate(current.itemId,
                        current.sourceActorId,current.sourcePartId,
                        current.itemId+" ["+state+"]",current.morph));
        }
        choices.addAll(all.values());
        if(!preferredSourceId.isEmpty())
            for(int i=0;i<choices.size();i++)
                if(preferredSourceId.equals(choices.get(i).itemId))
                {
                    selectedSource=i;
                    break;
                }
        if(selectedSource>=choices.size())selectedSource=0;
        if(!choices.isEmpty())
            preferredSourceId=choices.get(selectedSource).itemId;

        // BOBJ skeleton extraction is expensive and logged. Rebuild the
        // list only when the runtime morph reference changes, not once
        // per rendered GUI frame or shadow pass.
        Object morph=previewActor==null?null:previewActor.getMorph();
        if(morph!=inspectedMorph)
        {
            inspectedMorph=morph;
            cachedBoneNames.clear();
            if(morph instanceof mchorse.metamorph.api.morphs.AbstractMorph)
                cachedBoneNames.addAll(
                        CharacterBodyPartOverrideController.getBones(
                            (mchorse.metamorph.api.morphs.AbstractMorph)morph));
        }
        bones.addAll(cachedBoneNames);
        if(!preferredBoneName.isEmpty())
            for(int i=0;i<bones.size();i++)
                if(preferredBoneName.equals(bones.get(i)))
                {
                    selectedBone=i;
                    break;
                }
        if(selectedBone>=bones.size())selectedBone=0;
    }

    public void draw(Minecraft mc,AnimationEditorScreen editor,
            BlockbusterSceneActorData actor,int frame,
            int left,int top,int panelWidth)
    {
        update(editor,actor,frame);
        x=left;y=top;width=Math.max(90,panelWidth);
        int right=x+width,accent=EditorThemeManager.get().getAccent();
        int text=0xFFE1E0E8,muted=0xFFAFAAB9;
        mc.fontRenderer.drawString("SOURCE ITEM",x+5,y+2,muted);
        row(mc,y+18,choices.isEmpty()?"NO ITEM":
                choices.get(selectedSource).label);
        mc.fontRenderer.drawString("ATTACH TO BONE",x+5,y+45,muted);
        row(mc,y+61,bones.isEmpty()?"NO RENDERED BONES":
                bones.get(selectedBone));
        Gui.drawRect(x+4,y+89,right-4,y+113,
                EditorVisualStyle.themedSurface(.25F));
        Gui.drawRect(x+4,y+111,right-4,y+113,accent);
        center(mc,"ADD / UPDATE KEY",y+96,text);
        Gui.drawRect(x+4,y+118,right-4,y+140,
                EditorVisualStyle.themedSurface(.22F));
        Gui.drawRect(x+4,y+138,right-4,y+140,accent);
        center(mc,"PLACE / RELEASE IN WORLD",y+125,text);
        Gui.drawRect(x+4,y+145,right-4,y+164,
                EditorVisualStyle.themedSurface(.10F));
        center(mc,"REMOVE KEY TRANSFER",y+150,text);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                status,width-11),x+5,y+175,
                status.startsWith("KEY")?0xFF9FE6BE:muted);
    }

    private void center(Minecraft mc,String label,int yy,int color)
    {
        String shown=mc.fontRenderer.trimStringToWidth(
                label,Math.max(1,width-18));
        mc.fontRenderer.drawString(shown,x+(width-
                mc.fontRenderer.getStringWidth(shown))/2,yy,color);
    }
    private void row(Minecraft mc,int yy,String title)
    {
        int right=x+width;
        Gui.drawRect(x+4,yy,right-4,yy+23,
                EditorVisualStyle.themedSurface(.12F));
        Gui.drawRect(x+4,yy,right-4,yy+1,
                EditorVisualStyle.themedSurface(.28F));
        mc.fontRenderer.drawString("<",x+10,yy+8,0xFFEAE3F4);
        mc.fontRenderer.drawString(">",right-17,yy+8,0xFFEAE3F4);
        String text=mc.fontRenderer.trimStringToWidth(title,
                Math.max(1,width-48));
        mc.fontRenderer.drawString(text,x+23,yy+8,0xFFE2E4EB);
    }

    public boolean click(AnimationEditorScreen editor,
            BlockbusterSceneActorData actor,int frame,int mx,int my)
    {
        if(mx<x||mx>=x+width||my<y||my>=y+HEIGHT)return false;
        update(editor,actor,frame);
        if(my>=y+18&&my<y+41)
        {
            if(!choices.isEmpty())
            {
                selectedSource=(selectedSource+(mx<x+width/2?-1:1)+
                        choices.size())%choices.size();
                preferredSourceId=choices.get(selectedSource).itemId;
            }
            return true;
        }
        if(my>=y+61&&my<y+84)
        {
            if(!bones.isEmpty())
            {
                selectedBone=(selectedBone+(mx<x+width/2?-1:1)+
                        bones.size())%bones.size();
                preferredBoneName=bones.get(selectedBone);
            }
            return true;
        }
        if(my>=y+89&&my<y+113)
        {
            author(editor,actor,frame);
            return true;
        }
        if(my>=y+118&&my<y+140)
        {
            release(editor,actor,frame);
            return true;
        }
        if(my>=y+145&&my<y+164)
        {
            remove(editor,actor,frame);
            return true;
        }
        return true;
    }

    private void author(AnimationEditorScreen editor,
            BlockbusterSceneActorData target,int frame)
    {
        if(editor==null||target==null||choices.isEmpty()||bones.isEmpty())
        {status="SELECT ITEM AND BONE";return;}
        Candidate item=choices.get(selectedSource);
        String bone=bones.get(selectedBone);
        float[] receiving=CharacterAnchorPoseCache.bone(
                target.getId(),bone,frame);
        if(receiving==null)
        {status="RENDER TARGET BONE FIRST";return;}

        EditorSceneState scene=editor.getSceneState();
        CharacterAnchorTransfers.Transfer previous=
                CharacterAnchorTransfers.resolve(scene.getActors(),frame)
                        .get(item.itemId);
        float[] world=null;
        NBTTagCompound movedMorph=item.morph;
        boolean bakedPose=false;
        if(previous!=null&&previous.released())
        {
            // A released item remains at its saved world pose while
            // every original source actor Record continues separately.
            world=previous.worldAtKey.clone();
            movedMorph=previous.morph.copy();
            bakedPose=previous.bakedPose;
        }
        else if(previous!=null&&previous.attached())
        {
            BodyPartsEditorController parts=scene.getBodyPartsController();
            BodyPartModelData edited=parts==null?null:
                    parts.findAnchorAttachment(previous.ownerActorId,
                            previous.itemId,previous.frame);
            // The BODY PARTS author may have changed the held item's
            // position, rotation, scale or Morph AFTER the first pickup.
            // Inherit the actual editable model rather than the obsolete
            // base matrix stored on its CHARACTER key.
            String heldBone=edited==null?previous.ownerBone:
                    edited.getAttachmentBoneName();
            float[] localHeld=edited==null?previous.boneFromItem:
                    AnchorTransferMath.composeBodyPart(
                            edited.getGlobalTransform());
            float[] holder=CharacterAnchorPoseCache.bone(
                    previous.ownerActorId,heldBone,frame);
            if(holder!=null&&localHeld!=null)
                world=BodyPartPhysicsMatrixSpace.multiply(
                        holder,localHeld);
            if(edited!=null)
            {
                // The rendered BODY PART already has its *internal*
                // bone keys applied by BodyPartsPreviewRenderer.
                // getMorphAt(frame) is only the unposed source Morph
                // and used to lose rotations at handoff.
                NBTTagCompound posed=CharacterAnchorPoseCache
                        .posedPartMorph(previous.ownerActorId,edited,frame);
                if(posed==null)
                {
                    status="RENDER ITEM POSE FIRST";
                    System.err.println("[BBS Editor][Anchors] No posed "+
                            "BODY PART snapshot at frame "+frame+
                            " for handoff "+previous.itemId);
                    return;
                }
                movedMorph=posed;
                bakedPose=true;
            }
            else
            {
                movedMorph=previous.morph.copy();
                bakedPose=previous.bakedPose;
            }
        }
        else if(item.sourcePart.isEmpty())
            world=CharacterAnchorPoseCache.actor(item.sourceActor,frame);
        else
        {
            BodyPartsEditorController parts=scene.getBodyPartsController();
            if(parts!=null)
                for(BodyPartModelData original:
                        parts.getModelsForActor(item.sourceActor))
                {
                    if(!item.sourcePart.equals(original.getAnchorId()))
                        continue;
                    float[] sourceBone=CharacterAnchorPoseCache.bone(
                            item.sourceActor,
                            original.getAttachmentBoneName(),frame);
                    if(sourceBone!=null)
                        world=BodyPartPhysicsMatrixSpace.multiply(sourceBone,
                                AnchorTransferMath.composeBodyPart(
                                        original.getGlobalTransform()));
                    NBTTagCompound posed=CharacterAnchorPoseCache
                            .posedPartMorph(item.sourceActor,original,frame);
                    if(posed==null)
                    {
                        status="RENDER ITEM POSE FIRST";
                        return;
                    }
                    movedMorph=posed;
                    bakedPose=true;
                    break;
                }
        }
        if(world==null)
        {status="RENDER ITEM POSE FIRST";return;}
        try
        {
            float[] local=AnchorTransferMath.rebase(world,receiving);
            // All rotations/scale must survive native BodyPart TRS.
            AnimationTransform attachment=AnchorTransferMath
                    .toBodyPartTransform(local);
            float[] reconstructed=BodyPartPhysicsMatrixSpace.multiply(
                    receiving,AnchorTransferMath.composeBodyPart(attachment));
            float matrixError=0F;
            for(int i=0;i<16;i++)
                matrixError=Math.max(matrixError,
                        Math.abs(world[i]-reconstructed[i]));
            if(matrixError>1E-3F)
                throw new IllegalArgumentException(
                        "Anchor BodyPart decomposition error "+matrixError);
            CharacterAnchorTransfers.Transfer transfer=
                    new CharacterAnchorTransfers.Transfer(
                            item.itemId,item.sourceActor,item.sourcePart,
                            target.getId(),bone,frame,movedMorph,world,local,
                            bakedPose);
            CharacterKey key=CharacterAnchorTransfers.upsert(
                    target.getCharacterTimeline(),transfer);
            // The key is authoritative; the receiving model must now be
            // a regular persistent/visible BODY PARTS editor entry, not
            // just a render-time anonymous proxy.
            CharacterAnchorPreview.synchronizeEditableParts(scene);
            BodyPartsEditorController parts=scene.getBodyPartsController();
            if(parts!=null)
            {
                BodyPartModelData editable=parts.findAnchorAttachment(
                        target.getId(),transfer.itemId,transfer.frame);
                if(editable!=null)
                {
                    parts.rebaseAnchorAttachment(editable,transfer);
                    // The active actor's BODY PARTS collection is
                    // authoritative for selection; don't reach into
                    // AnimationEditorScreen's private actor getter.
                    if(parts.getModels().contains(editable))
                        parts.selectAttachment(editable);
                }
            }
            editor.getSceneState().getSaveController().markDirty();
            editor.syncCharacterTimelineActor();
            CharacterTimelineEditorController
                    .requestInspectorKeySelection(target,key);
            setSelectedKey(key);
            status="KEY @ "+frame+" / WORLD PRESERVED";
            System.out.println("[BBS Editor][Anchors] KEY frame="+frame+
                    " item="+item.itemId+" owner="+target.getId()+
                    " bone="+bone+" worldRebased=true"+
                    " bakedPose="+bakedPose+
                    " targetMorphScale="+
                    CharacterAnchorPoseCache.nativeAnimatedScale(
                            target.getId(),frame)+
                    " localPx=("+
                    attachment.getPositionX()+","+
                    attachment.getPositionY()+","+
                    attachment.getPositionZ()+")"+
                    " maxMatrixError="+matrixError);
        }
        catch(IllegalArgumentException invalid)
        {
            status="INVALID POSE / SCALE";
        }
    }

    /**
     * Opposite of pickup: freeze the CURRENT, user-edited item's full
     * world transform and Morph on this CHARACTER frame. From this
     * tick it is no longer a BodyPart of the holder; the detached
     * world proxy renders the exact saved pose until the next key.
     *
     * A key cannot overwrite the original pickup at the same frame:
     * that would erase the frame interval in which it was held.
     */
    private void release(AnimationEditorScreen editor,
            BlockbusterSceneActorData actor,int frame)
    {
        if(editor==null||actor==null||choices.isEmpty())
        {status="SELECT HELD ITEM";return;}
        EditorSceneState scene=editor.getSceneState();
        if(scene==null)return;
        Candidate selected=choices.get(selectedSource);
        CharacterAnchorTransfers.Transfer current=
                CharacterAnchorTransfers.resolve(scene.getActors(),frame)
                        .get(selected.itemId);
        if(current==null||!current.attached()||
                !actor.getId().equals(current.ownerActorId))
        {
            status="ITEM IS NOT HELD BY THIS ACTOR";
            return;
        }
        if(current.frame>=frame)
        {
            status="RELEASE AFTER PICKUP KEY FRAME";
            return;
        }

        BodyPartsEditorController parts=scene.getBodyPartsController();
        BodyPartModelData edited=parts==null?null:
                parts.findAnchorAttachment(current.ownerActorId,
                        current.itemId,current.frame);
        String bone=edited==null?current.ownerBone:
                edited.getAttachmentBoneName();
        float[] holder=CharacterAnchorPoseCache.bone(
                actor.getId(),bone,frame);
        if(holder==null)
        {status="RENDER HOLDER POSE FIRST";return;}
        try
        {
            float[] local=edited==null?current.boneFromItem:
                    AnchorTransferMath.composeBodyPart(
                            edited.getGlobalTransform());
            if(local==null)
            {status="ITEM HAS NO LOCAL TRANSFORM";return;}
            float[] world=BodyPartPhysicsMatrixSpace.multiply(
                    holder,local);
            // Refuse malformed/singular transforms, not phantom world
            // objects that teleport to the original source on playback.
            AnchorTransferMath.inverseAffine(world);

            NBTTagCompound morph=current.morph.copy();
            boolean bakedPose=current.bakedPose;
            if(edited!=null)
            {
                // Freeze the exact final pose produced by BODY PARTS:
                // customPose / Emoticons.pose / Chameleon.pose already
                // includes all authored internal-bone keyframes.
                // Never silently fall back to getMorphAt(frame), which
                // is unposed and caused the reported rotation reset.
                NBTTagCompound posed=CharacterAnchorPoseCache
                        .posedPartMorph(actor.getId(),edited,frame);
                if(posed==null)
                {
                    status="RENDER ITEM POSE FIRST";
                    System.err.println("[BBS Editor][Anchors] No posed "+
                            "BODY PART snapshot at frame "+frame+
                            " for world release "+current.itemId);
                    return;
                }
                morph=posed;
                bakedPose=true;
            }

            CharacterAnchorTransfers.Transfer drop=
                    new CharacterAnchorTransfers.Transfer(
                            current.itemId,current.sourceActorId,
                            current.sourcePartId,"","",frame,morph,
                            world,null,bakedPose);
            CharacterKey key=CharacterAnchorTransfers.upsert(
                    actor.getCharacterTimeline(),drop);
            CharacterAnchorPreview.synchronizeEditableParts(scene);
            scene.getSaveController().markDirty();
            editor.syncCharacterTimelineActor();
            CharacterTimelineEditorController
                    .requestInspectorKeySelection(actor,key);
            setSelectedKey(key);
            status="WORLD PLACED @ "+frame;
            System.out.println("[BBS Editor][Anchors] RELEASE "+
                    "frame="+frame+" item="+current.itemId+
                    " from="+actor.getId()+
                    " bakedPose="+bakedPose+" world=("+
                    world[12]+","+world[13]+","+world[14]+")");
        }
        catch(IllegalArgumentException invalid)
        {
            status="INVALID WORLD TRANSFORM";
        }
    }

    private void remove(AnimationEditorScreen editor,
            BlockbusterSceneActorData target,int frame)
    {
        if(editor==null||target==null||choices.isEmpty())return;
        CharacterTrack main=target.getCharacterTimeline().ensureMainTrack();
        CharacterKey key=main.getKeyAtFrame(frame);
        if(key==null||!key.hasData(CharacterAnchorTransfers.KEY))
        {status="NO TRANSFER AT FRAME";return;}
        NBTTagCompound data=key.getData();
        net.minecraft.nbt.NBTTagList list=data.getTagList(
                CharacterAnchorTransfers.KEY,10);
        net.minecraft.nbt.NBTTagList retained=new net.minecraft.nbt.NBTTagList();
        String selected=choices.get(selectedSource).itemId;
        for(int i=0;i<list.tagCount();i++)
        {
            NBTTagCompound entry=list.getCompoundTagAt(i);
            if(!selected.equals(entry.getString("ItemId")))
                retained.appendTag(entry.copy());
        }
        if(retained.tagCount()==list.tagCount())
        {status="NO KEY FOR SELECTED ITEM";return;}
        if(retained.tagCount()>0)data.setTag(
                CharacterAnchorTransfers.KEY,retained);
        else data.removeTag(CharacterAnchorTransfers.KEY);
        key.setData(data);
        // Prune the keyed model as well, rather than leaving a ghost
        // that reappears or blocks a second transfer on scene reload.
        CharacterAnchorPreview.synchronizeEditableParts(
                editor.getSceneState());
        editor.getSceneState().getSaveController().markDirty();
        editor.syncCharacterTimelineActor();
        status="KEY REMOVED";
    }
}
