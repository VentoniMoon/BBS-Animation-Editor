package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Отвечает только за работу с AnimationKeyframe внутри редактора.
 *
 * EditorKeyframeController НЕ знает ничего о:
 * - Preview
 * - Blockbuster
 * - Record
 * - Actor
 * - InterpolationPanel
 * - UI отрисовке
 *
 * Он управляет:
 * - выбранной костью;
 * - выбранным keyframe;
 * - созданием keyframe;
 * - удалением keyframe;
 * - поиском keyframe;
 * - перемещением keyframe;
 * - состоянием drag;
 * - pending delete.
 */
public class EditorKeyframeController
{
    private final EditorTimeline timeline;

    private final int timelineStartX;

    /*
     * =========================================================
     * SELECTION
     * =========================================================
     */

    private int selectedBone = 0;

    private AnimationKeyframe selectedKeyframe = null;

    private AnimationBone selectedKeyframeBone = null;

    /*
     * =========================================================
     * DRAGGING
     * =========================================================
     */

    private boolean keyframeDragging = false;

    private AnimationBone draggingKeyframeBone = null;

    private AnimationKeyframe draggingKeyframe = null;

    /*
     * =========================================================
     * PENDING DELETE
     * =========================================================
     */

    private AnimationBone pendingDeleteBone = null;

    private AnimationKeyframe pendingDeleteKeyframe = null;

    private int pendingDeleteFrame = -1;


    public EditorKeyframeController(
            EditorTimeline timeline,
            int timelineStartX)
    {
        this.timeline = timeline;
        this.timelineStartX = timelineStartX;
    }


    /*
     * =========================================================
     * SELECTION
     * =========================================================
     */

    public int getSelectedBoneIndex()
    {
        return this.selectedBone;
    }


    public void setSelectedBoneIndex(
            int index,
            List<AnimationBone> bones)
    {
        if (bones == null || bones.isEmpty())
        {
            this.selectedBone = 0;
            this.selectedKeyframe = null;
            this.selectedKeyframeBone = null;

            return;
        }

        if (index < 0)
        {
            index = 0;
        }

        if (index >= bones.size())
        {
            index = bones.size() - 1;
        }

        this.selectedBone = index;
        this.selectedKeyframe = null;
        this.selectedKeyframeBone = null;
    }


    public AnimationBone getSelectedBone(
            List<AnimationBone> bones)
    {
        if (bones == null)
        {
            return null;
        }

        if (
                this.selectedBone < 0
                        ||
                        this.selectedBone >= bones.size()
        )
        {
            return null;
        }

        return bones.get(
                this.selectedBone
        );
    }


    public AnimationKeyframe getSelectedKeyframe()
    {
        return this.selectedKeyframe;
    }


    public void setSelectedKeyframe(
            AnimationKeyframe keyframe)
    {
        this.selectedKeyframe = keyframe;
    }


    public void clearSelection()
    {
        this.selectedKeyframe = null;
        this.selectedKeyframeBone = null;

        stopKeyframeDragging();
    }


    /*
     * =========================================================
     * KEYFRAME SEARCH
     * =========================================================
     */

    public AnimationKeyframe findKeyframe(
            AnimationBone bone,
            int frame)
    {
        if (bone == null)
        {
            return null;
        }

        List<AnimationKeyframe> keyframes =
                bone.getKeyframes();

        if (keyframes == null)
        {
            return null;
        }

        for (
                AnimationKeyframe keyframe :
                keyframes
        )
        {
            if (keyframe == null)
            {
                continue;
            }

            if (
                    keyframe.getFrame() ==
                            frame
            )
            {
                return keyframe;
            }
        }

        return null;
    }


    public AnimationKeyframe findKeyframeAt(
            AnimationBone bone,
            int mouseX,
            int radius)
    {
        if (bone == null)
        {
            return null;
        }

        List<AnimationKeyframe> keyframes =
                bone.getKeyframes();

        if (keyframes == null)
        {
            return null;
        }

        for (
                AnimationKeyframe keyframe :
                keyframes
        )
        {
            if (keyframe == null)
            {
                continue;
            }

            int keyframeX =
                    this.timeline.getFrameX(
                            keyframe.getFrame(),
                            this.timelineStartX
                    );

            if (
                    Math.abs(
                            keyframeX -
                                    mouseX
                    ) <= radius
            )
            {
                return keyframe;
            }
        }

        return null;
    }


    /*
     * =========================================================
     * KEYFRAME CREATION
     * =========================================================
     */

    public AnimationKeyframe createKeyframe(
            AnimationBone bone,
            int frame)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationKeyframe existing =
                findKeyframe(
                        bone,
                        frame
                );

        if (existing != null)
        {
            this.selectedKeyframeBone = bone;
            this.selectedKeyframe = existing;

            return existing;
        }

        bone.addKeyframe(
                frame
        );

        AnimationKeyframe created =
                findKeyframe(
                        bone,
                        frame
                );

        this.selectedKeyframe =
                created;

        sortKeyframes(
                bone
        );

        return created;
    }


    /*
     * =========================================================
     * KEYFRAME SORTING
     * =========================================================
     */

    public void sortKeyframes(
            AnimationBone bone)
    {
        if (bone == null)
        {
            return;
        }

        List<AnimationKeyframe> keyframes =
                bone.getKeyframes();

        if (
                keyframes == null
                        ||
                        keyframes.size() < 2
        )
        {
            return;
        }

        Collections.sort(
                keyframes,
                new Comparator<AnimationKeyframe>()
                {
                    @Override
                    public int compare(
                            AnimationKeyframe first,
                            AnimationKeyframe second)
                    {
                        if (first == null && second == null)
                        {
                            return 0;
                        }

                        if (first == null)
                        {
                            return 1;
                        }

                        if (second == null)
                        {
                            return -1;
                        }

                        return Integer.compare(
                                first.getFrame(),
                                second.getFrame()
                        );
                    }
                }
        );
    }


    /*
     * =========================================================
     * KEYFRAME DRAGGING
     * =========================================================
     */

    public void startKeyframeDragging(
            AnimationBone bone,
            AnimationKeyframe keyframe)
    {
        if (
                bone == null
                        ||
                        keyframe == null
        )
        {
            stopKeyframeDragging();

            return;
        }

        this.keyframeDragging = true;

        this.draggingKeyframeBone =
                bone;

        this.draggingKeyframe =
                keyframe;

        this.selectedKeyframe =
                keyframe;
    }


    public boolean isKeyframeDragging()
    {
        return this.keyframeDragging;
    }


    public AnimationBone getDraggingKeyframeBone()
    {
        return this.draggingKeyframeBone;
    }


    public AnimationKeyframe getDraggingKeyframe()
    {
        return this.draggingKeyframe;
    }


    public void stopKeyframeDragging()
    {
        this.keyframeDragging = false;

        this.draggingKeyframeBone = null;

        this.draggingKeyframe = null;
    }


    /**
     * Перемещает keyframe на новый кадр.
     *
     * Возвращает true, если перемещение действительно произошло.
     */
    public boolean moveDraggingKeyframe(
            int mouseX,
            int minimumFrame)
    {
        if (
                !this.keyframeDragging
                        ||
                        this.draggingKeyframe == null
                        ||
                        this.draggingKeyframeBone == null
        )
        {
            return false;
        }

        AnimationBone bone =
                this.draggingKeyframeBone;

        AnimationKeyframe keyframe =
                this.draggingKeyframe;

        int newFrame =
                this.timeline.getFrameFromMouseX(
                        mouseX,
                        this.timelineStartX
                );

        newFrame =
                Math.max(
                        minimumFrame,
                        newFrame
                );

        AnimationKeyframe occupied =
                findKeyframe(
                        bone,
                        newFrame
                );

        if (
                occupied != null
                        &&
                        occupied != keyframe
        )
        {
            return false;
        }

        if (
                keyframe.getFrame() ==
                        newFrame
        )
        {
            return false;
        }

        keyframe.setFrame(
                newFrame
        );

        sortKeyframes(
                bone
        );

        this.selectedKeyframe =
                keyframe;

        return true;
    }


    /*
     * =========================================================
     * DELETE
     * =========================================================
     */

    public void requestDelete(
            AnimationBone bone,
            AnimationKeyframe keyframe)
    {
        if (
                bone == null
                        ||
                        keyframe == null
        )
        {
            clearPendingDelete();

            return;
        }

        this.pendingDeleteBone =
                bone;

        this.pendingDeleteKeyframe =
                keyframe;

        this.pendingDeleteFrame =
                keyframe.getFrame();
    }


    public boolean isDeletePending()
    {
        return this.pendingDeleteBone != null
                &&
                this.pendingDeleteKeyframe != null;
    }


    public AnimationBone getPendingDeleteBone()
    {
        return this.pendingDeleteBone;
    }


    public AnimationKeyframe getPendingDeleteKeyframe()
    {
        return this.pendingDeleteKeyframe;
    }


    public int getPendingDeleteFrame()
    {
        return this.pendingDeleteFrame;
    }


    public boolean confirmDelete()
    {
        if (
                this.pendingDeleteBone == null
                        ||
                        this.pendingDeleteKeyframe == null
        )
        {
            clearPendingDelete();

            return false;
        }

        AnimationBone bone =
                this.pendingDeleteBone;

        AnimationKeyframe keyframe =
                this.pendingDeleteKeyframe;

        boolean belongsToBone = false;

        List<AnimationKeyframe> keyframes =
                bone.getKeyframes();

        if (keyframes != null)
        {
            for (
                    AnimationKeyframe existing :
                    keyframes
            )
            {
                if (existing == keyframe)
                {
                    belongsToBone = true;
                    break;
                }
            }
        }

        if (!belongsToBone)
        {
            clearPendingDelete();

            return false;
        }

        int frame =
                keyframe.getFrame();

        bone.removeKeyframe(
                frame
        );

        if (
                this.selectedKeyframe ==
                        keyframe
        )
        {
            this.selectedKeyframe =
                    null;
        }

        clearPendingDelete();

        return true;
    }


    public void clearPendingDelete()
    {
        this.pendingDeleteBone = null;

        this.pendingDeleteKeyframe = null;

        this.pendingDeleteFrame = -1;
    }


    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        this.selectedBone = 0;

        this.selectedKeyframe = null;
        this.selectedKeyframeBone = null;

        stopKeyframeDragging();

        clearPendingDelete();
    }


    public void resetSelectionOnly()
    {
        this.selectedKeyframe = null;
        this.selectedKeyframeBone = null;

        stopKeyframeDragging();

        clearPendingDelete();
    }
}