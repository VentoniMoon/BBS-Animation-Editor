package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * One logical item, one active owner per scene time. Transitions are
 * immutable events stored on CHARACTER's ordinary main track keys,
 * never destructive replacements of Morph or Body Parts NBT.
 *
 * The public preview resolver is pure with respect to scene data:
 * playing, seeking, reversing and exporting cannot add/delete models.
 */
public final class CharacterAnchorTransfers
{
    public static final String KEY="AnchorTransfers";
    private CharacterAnchorTransfers(){}

    public static final class Transfer
    {
        public final String itemId;
        public final String sourceActorId;
        public final String sourcePartId;
        public final String ownerActorId;
        public final String ownerBone;
        public final int frame;
        public final NBTTagCompound morph;
        public final float[] worldAtKey;
        public final float[] boneFromItem;

        public Transfer(String itemId,String sourceActorId,
                String sourcePartId,String ownerActorId,String ownerBone,
                int frame,NBTTagCompound morph,float[] worldAtKey,
                float[] boneFromItem)
        {
            this.itemId=nonNull(itemId);
            this.sourceActorId=nonNull(sourceActorId);
            this.sourcePartId=nonNull(sourcePartId);
            this.ownerActorId=nonNull(ownerActorId);
            this.ownerBone=nonNull(ownerBone);
            this.frame=Math.max(0,frame);
            this.morph=morph==null?new NBTTagCompound():morph.copy();
            this.worldAtKey=worldAtKey==null?null:worldAtKey.clone();
            this.boneFromItem=boneFromItem==null?null:boneFromItem.clone();
        }
        public boolean attached()
        {
            return !ownerActorId.isEmpty()&&!ownerBone.isEmpty();
        }
        /** An explicit release has no owner but keeps a fixed world pose.
         * It is NOT a return to the original scene actor's Record.
         */
        public boolean released()
        {
            return ownerActorId.isEmpty()&&ownerBone.isEmpty()&&
                    worldAtKey!=null;
        }
        private static String nonNull(String v){return v==null?"":v;}
        public NBTTagCompound save()
        {
            NBTTagCompound tag=new NBTTagCompound();
            tag.setString("ItemId",itemId);
            tag.setString("SourceActor",sourceActorId);
            tag.setString("SourcePart",sourcePartId);
            tag.setString("OwnerActor",ownerActorId);
            tag.setString("OwnerBone",ownerBone);
            tag.setTag("Morph",morph.copy());
            if(worldAtKey!=null)
                tag.setTag("World",AnchorTransferMath.matrixToNBT(worldAtKey));
            if(boneFromItem!=null)
                tag.setTag("Local",AnchorTransferMath.matrixToNBT(boneFromItem));
            return tag;
        }
        public static Transfer load(NBTTagCompound nbt,int frame)
        {
            if(nbt==null||!nbt.hasKey("ItemId",8)||
                    !nbt.hasKey("Morph",10))return null;
            String id=nbt.getString("ItemId");
            String source=nbt.getString("SourceActor");
            if(id.isEmpty()||source.isEmpty())return null;
            NBTTagCompound morph=nbt.getCompoundTag("Morph");
            if(morph.hasNoTags())return null;
            String part=nbt.getString("SourcePart");
            String owner=nbt.getString("OwnerActor");
            String bone=nbt.getString("OwnerBone");
            float[] world=nbt.hasKey("World",10)?
                    AnchorTransferMath.matrixFromNBT(
                            nbt.getCompoundTag("World")):null;
            float[] local=nbt.hasKey("Local",10)?
                    AnchorTransferMath.matrixFromNBT(
                            nbt.getCompoundTag("Local")):null;
            if(!owner.isEmpty()&&(bone.isEmpty()||local==null))
                return null; // Never display a wrong-space attachment.
            if(owner.isEmpty()&&(!bone.isEmpty()||world==null))
                return null; // A released object needs an explicit world pose.
            return new Transfer(id,source,part,owner,bone,frame,morph,
                    world,local);
        }
    }

    /** A key may already contain MORPH, SKIN and other Character state. */
    public static CharacterKey upsert(CharacterTimelineController timeline,
            Transfer transfer)
    {
        if(timeline==null||transfer==null||transfer.itemId.isEmpty()||
                transfer.sourceActorId.isEmpty()||
                (transfer.attached()&&transfer.boneFromItem==null)||
                (!transfer.attached()&&!transfer.released()))
            throw new IllegalArgumentException("Incomplete anchor transfer");
        CharacterTrack main=timeline.ensureMainTrack();
        CharacterKey key=main.getKeyAtFrame(transfer.frame);
        if(key==null)
        {
            key=new CharacterKey(transfer.frame,CharacterKey.Type.CUSTOM);
            main.addKey(key);
        }
        NBTTagCompound data=key.getData();
        NBTTagList original=data.getTagList(KEY,10);
        NBTTagList result=new NBTTagList();
        for(int i=0;i<original.tagCount();i++)
        {
            NBTTagCompound entry=original.getCompoundTagAt(i);
            if(!transfer.itemId.equals(entry.getString("ItemId")))
                result.appendTag(entry.copy());
        }
        result.appendTag(transfer.save());
        data.setTag(KEY,result);
        key.setData(data);
        return key;
    }

    public static List<Transfer> keys(CharacterTimelineController timeline)
    {
        List<Transfer> all=new ArrayList<Transfer>();
        if(timeline==null)return all;
        for(CharacterTrack track:timeline.getTracks())
        {
            if(track==null)continue;
            for(CharacterKey key:track.getKeys())
            {
                if(key==null||!key.hasData(KEY))continue;
                NBTTagList list=key.getData().getTagList(KEY,10);
                for(int i=0;i<list.tagCount();i++)
                {
                    Transfer event=Transfer.load(
                            list.getCompoundTagAt(i),key.getFrame());
                    if(event!=null)all.add(event);
                }
            }
        }
        return all;
    }

    /** Same frame tie-breaks are stable across save/load and actor order. */
    public static List<Transfer> all(List<BlockbusterSceneActorData> actors)
    {
        List<Transfer> result=new ArrayList<Transfer>();
        if(actors==null)return result;
        for(BlockbusterSceneActorData actor:actors)
            if(actor!=null)
                result.addAll(keys(actor.getCharacterTimeline()));
        Collections.sort(result,new Comparator<Transfer>()
        {
            @Override public int compare(Transfer a,Transfer b)
            {
                int f=Integer.compare(a.frame,b.frame);
                if(f!=0)return f;
                f=a.ownerActorId.compareTo(b.ownerActorId);
                if(f!=0)return f;
                return a.itemId.compareTo(b.itemId);
            }
        });
        return result;
    }

    /**
     * Effective state at a sampled frame. Only the LAST transition for
     * an item survives; replaying historical frames does not mutate
     * source actors or stored BodyPartModelData.
     */
    public static Map<String,Transfer> resolve(
            List<BlockbusterSceneActorData> actors,double frame)
    {
        Map<String,Transfer> current=new LinkedHashMap<String,Transfer>();
        if(!Double.isFinite(frame)||frame<0D)return current;
        for(Transfer t:all(actors))
        {
            if(t.frame>frame)break;
            current.put(t.itemId,t);
        }
        return current;
    }

    public static boolean hidesActor(
            List<BlockbusterSceneActorData> actors,String actorId,double frame)
    {
        if(actorId==null||actorId.isEmpty())return false;
        for(Transfer t:resolve(actors,frame).values())
            if((t.attached()||t.released())&&t.sourcePartId.isEmpty()&&
                    actorId.equals(t.sourceActorId))
                return true;
        return false;
    }

    public static boolean hidesPart(
            List<BlockbusterSceneActorData> actors,String ownerActor,
            String partId,double frame)
    {
        if(ownerActor==null||partId==null||partId.isEmpty())return false;
        for(Transfer t:resolve(actors,frame).values())
            if((t.attached()||t.released())&&
                    ownerActor.equals(t.sourceActorId)&&
                    partId.equals(t.sourcePartId))
                return true;
        return false;
    }

    /**
     * A saved editable BODY PART has one transfer-key identity. Keep
     * that part in the owner's editor list at every frame, but render
     * it only while the matching key is the effective logical owner.
     * A later hand-off (even back to the same actor) must not resurrect
     * the earlier editable instance.
     */
    public static boolean isActiveEditableAttachment(
            List<BlockbusterSceneActorData> actors,
            String itemId,String actorId,int eventFrame,double frame)
    {
        if(itemId==null||itemId.isEmpty()||actorId==null)return false;
        Transfer current=resolve(actors,frame).get(itemId);
        return current!=null&&current.attached()&&
                actorId.equals(current.ownerActorId)&&
                current.frame==eventFrame;
    }

    /** Explicit world-released items: one current state per logical item.
     * Scrubbing before the release removes this world instance.
     */
    public static List<Transfer> releasedItems(
            List<BlockbusterSceneActorData> actors,double frame)
    {
        List<Transfer> result=new ArrayList<Transfer>();
        for(Transfer t:resolve(actors,frame).values())
            if(t.released())result.add(t);
        return result;
    }

    public static List<Transfer> forOwner(
            List<BlockbusterSceneActorData> actors,String owner,double frame)
    {
        List<Transfer> result=new ArrayList<Transfer>();
        if(owner==null)return result;
        for(Transfer t:resolve(actors,frame).values())
            if(t.attached()&&owner.equals(t.ownerActorId))
                result.add(t);
        return result;
    }
}
