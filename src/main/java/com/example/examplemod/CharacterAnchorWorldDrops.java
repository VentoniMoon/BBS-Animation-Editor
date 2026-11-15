package com.example.examplemod;

/**
 * A released item is no longer parented to the previous character, but it
 * is not sent back to its source actor's Record position either.
 *
 * World actors are render-only; they are never inserted into scene actors,
 * never get saved as Record files, and never mutate the source actor Morph.
 * The complete 4x4 world pose and Morph live on a CHARACTER release key.
 *
 * The same BlockbusterActorPreviewRenderer pipeline handles normal,
 * native/shader, mirror and shadow rendering for these synthetic actors.
 */
public final class CharacterAnchorWorldDrops
{
    private static final String PREFIX="anchor-world:";
    private CharacterAnchorWorldDrops(){}

    public static String runtimeId(String itemId)
    {
        return PREFIX+itemId;
    }

    public static BlockbusterSceneActorData create(
            CharacterAnchorTransfers.Transfer release)
    {
        if(release==null||!release.released()||
                release.itemId.isEmpty()||
                release.worldAtKey==null||release.worldAtKey.length!=16)
            throw new IllegalArgumentException("Invalid anchor release");

        BlockbusterSceneActor actor=new BlockbusterSceneActor();
        actor.setId(runtimeId(release.itemId));
        actor.setName("Placed: "+release.itemId);
        actor.setMorph(release.morph.copy());
        BlockbusterSceneActorData data=new BlockbusterSceneActorData(actor);

        // A real frame is required by the existing renderer, but it is
        // never allowed to animate the world-placed object. Its pose is
        // overridden by the full affine matrix at render time.
        BlockbusterRecord record=new BlockbusterRecord("anchor-world");
        BlockbusterRecordFrame pose=new BlockbusterRecordFrame();
        pose.setX(release.worldAtKey[12]);
        pose.setY(release.worldAtKey[13]);
        pose.setZ(release.worldAtKey[14]);
        pose.setYaw(0F);
        pose.setPitch(0F);
        pose.setYawHead(0F);
        pose.setBodyYaw(0F);
        record.setFrame(0,pose);
        data.setRecord(record);
        return data;
    }
}
