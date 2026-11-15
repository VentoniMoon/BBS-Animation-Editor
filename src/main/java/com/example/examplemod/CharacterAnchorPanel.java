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
    public static final int HEIGHT=161;

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

    private void update(AnimationEditorScreen editor,
            BlockbusterSceneActorData target,int frame)
    {
        String id=target==null?"":target.getId();
        if(!id.equals(actorId))
        {
            actorId=id;selectedSource=0;selectedBone=0;
            preferredSourceId="";
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
                if(model==null||model.getBaseMorph()==null)continue;
                AbstractMorph selected=model.getMorphAt(frame);
                if(selected==null)continue;
                String idItem="part:"+actor.getId()+":"+
                        model.getAnchorId();
                all.put(idItem,new Candidate(idItem,actor.getId(),
                        model.getAnchorId(),model.getModelName()+
                                " ["+actor.getName()+"]",selected.toNBT()));
            }
        }
        // Items already held by another actor remain selectable without
        // creating another source or a second identifier on hand-off.
        for(CharacterAnchorTransfers.Transfer current:
                CharacterAnchorTransfers.resolve(
                        scene.getActors(),frame).values())
            if(!current.ownerActorId.equals(target.getId())&&
                    current.attached())
                all.put(current.itemId,new Candidate(current.itemId,
                        current.sourceActorId,current.sourcePartId,
                        current.itemId+" ["+current.ownerActorId+"]",
                        current.morph));
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
        Gui.drawRect(x+4,y+118,right-4,y+138,
                EditorVisualStyle.themedSurface(.10F));
        center(mc,"REMOVE KEY TRANSFER",y+124,text);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                status,width-11),x+5,y+148,
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
                selectedBone=(selectedBone+(mx<x+width/2?-1:1)+
                        bones.size())%bones.size();
            return true;
        }
        if(my>=y+89&&my<y+113)
        {
            author(editor,actor,frame);
            return true;
        }
        if(my>=y+118&&my<y+138)
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
        if(previous!=null&&previous.attached())
        {
            float[] holder=CharacterAnchorPoseCache.bone(
                    previous.ownerActorId,previous.ownerBone,frame);
            if(holder!=null&&previous.boneFromItem!=null)
                world=BodyPartPhysicsMatrixSpace.multiply(
                        holder,previous.boneFromItem);
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
                    break;
                }
        }
        if(world==null)
        {status="RENDER ITEM POSE FIRST";return;}
        try
        {
            float[] local=AnchorTransferMath.rebase(world,receiving);
            // All rotations/scale must survive native BodyPart TRS.
            AnchorTransferMath.toBodyPartTransform(local);
            CharacterAnchorTransfers.Transfer transfer=
                    new CharacterAnchorTransfers.Transfer(
                            item.itemId,item.sourceActor,item.sourcePart,
                            target.getId(),bone,frame,item.morph,world,local);
            CharacterKey key=CharacterAnchorTransfers.upsert(
                    target.getCharacterTimeline(),transfer);
            editor.getSceneState().getSaveController().markDirty();
            CharacterTimelineEditorController
                    .requestInspectorKeySelection(target,key);
            status="KEY @ "+frame+" / WORLD PRESERVED";
        }
        catch(IllegalArgumentException invalid)
        {
            status="INVALID POSE / SCALE";
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
        editor.getSceneState().getSaveController().markDirty();
        status="KEY REMOVED";
    }
}
