package com.example.examplemod;

/**
 * Stage 8: a one-shot camera focus when a key is created or selected.
 * Key identity is tracked so editing a selected key or playing the scene
 * cannot repeatedly reset the camera.
 */
public final class EditorPreviewAutoFocusController
{
    private boolean enabled = true;
    private Object previousKey;
    private String previousActor = "";

    public boolean isEnabled()
    {
        return enabled;
    }

    public void toggle()
    {
        enabled = !enabled;
        previousKey = null;
    }

    public void reset()
    {
        previousKey = null;
        previousActor = "";
    }

    public void update(
            EditorSceneViewport viewport,
            BlockbusterSceneActorData actor,
            Object selectedKey,
            int keyFrame,
            boolean playing)
    {
        String actorId = actor == null ? "" : actor.getId();

        if (selectedKey == null || actor == null)
        {
            previousKey = null;
            previousActor = actorId;
            return;
        }

        if (previousKey == selectedKey && actorId.equals(previousActor))
        {
            return;
        }

        previousKey = selectedKey;
        previousActor = actorId;

        if (!enabled || playing || viewport == null || !actor.hasRecord() ||
                viewport.getCamera().getMode() != EditorCamera.Mode.ORBIT)
        {
            return;
        }

        BlockbusterRecord record = actor.getRecord();
        if (record == null || record.getFrames() == null || record.getFrames().isEmpty())
        {
            return;
        }

        int frame = Math.max(0, Math.min(
                keyFrame, record.getFrames().size() - 1));
        BlockbusterRecordFrame value = record.getFrame(frame);
        if (value == null) return;

        EditorCamera camera = viewport.getCamera();
        if (camera == null) return;

        camera.setTarget(value.getX(), value.getY() + 1.4D, value.getZ());
        float facing = value.hasBodyYaw() ? value.getBodyYaw() : value.getYaw();
        camera.rotate(facing - camera.getYaw(), 12.0F - camera.getPitch());
        camera.setDistance(2.75D);
    }
}
