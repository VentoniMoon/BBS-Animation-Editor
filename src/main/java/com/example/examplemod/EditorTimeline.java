package com.example.examplemod;

public class EditorTimeline
{
    public static final int TICKS_PER_SECOND = 20;

    private static final float BASE_FRAME_WIDTH = 6.0F;

    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 4.0F;
    private static final float ZOOM_STEP = 0.25F;

    private int tick = 0;
    private int length = 1;

    private boolean playing = false;

    private float currentFrameFloat = 0.0F;

    /*
     * =========================================================
     * VIEW
     * =========================================================
     *
     * Эти значения относятся именно к отображению Timeline,
     * а не к времени воспроизведения.
     */

    private float zoom = 1.0F;
    private int offset = 0;

    /*
     * =========================================================
     * LENGTH
     * =========================================================
     */

    public void setLength(int length)
    {
        this.length = Math.max(1, length);

        if (this.tick >= this.length)
        {
            this.tick = this.length - 1;
        }

        if (this.tick < 0)
        {
            this.tick = 0;
        }

        this.currentFrameFloat = this.tick;
    }

    public int getLength()
    {
        return this.length;
    }

    /*
     * =========================================================
     * TICK
     * =========================================================
     */

    public void setTick(int tick)
    {
        this.tick = clampTick(tick);
        this.currentFrameFloat = this.tick;
    }

    public int getTick()
    {
        return this.tick;
    }

    public int getLastTick()
    {
        return Math.max(0, this.length - 1);
    }

    private int clampTick(int value)
    {
        return Math.max(
                0,
                Math.min(
                        value,
                        getLastTick()
                )
        );
    }

    /*
     * =========================================================
     * FRAME
     * =========================================================
     */

    public float getCurrentFrameFloat()
    {
        return this.currentFrameFloat;
    }

    /*
     * =========================================================
     * PLAYBACK
     * =========================================================
     */

    public void togglePlaying()
    {
        if (this.playing)
        {
            pause();
        }
        else
        {
            play();
        }
    }

    public void play()
    {
        if (getLastTick() <= 0)
        {
            this.playing = false;
            return;
        }

        this.playing = true;
    }

    public void pause()
    {
        this.playing = false;
        this.currentFrameFloat = this.tick;
    }

    public boolean isPlaying()
    {
        return this.playing;
    }

    /*
     * =========================================================
     * REWIND
     * =========================================================
     */

    public void rewind()
    {
        this.tick = 0;
        this.currentFrameFloat = 0.0F;
    }

    /*
     * =========================================================
     * STEP
     * =========================================================
     */

    public void previousTick()
    {
        this.tick =
                Math.max(
                        0,
                        this.tick - 1
                );

        this.currentFrameFloat = this.tick;
    }

    public void nextTick()
    {
        this.tick =
                Math.min(
                        getLastTick(),
                        this.tick + 1
                );

        this.currentFrameFloat = this.tick;
    }

    /*
     * =========================================================
     * UPDATE
     * =========================================================
     *
     * 20 ticks = 1 second.
     *
     * currentFrameFloat нужен для плавного движения между
     * целыми кадрами во время воспроизведения.
     */

    public void update()
    {
        if (!this.playing)
        {
            this.currentFrameFloat = this.tick;
            return;
        }

        this.currentFrameFloat += 1.0F;

        if (this.currentFrameFloat >= this.length)
        {
            this.currentFrameFloat = 0.0F;
            this.tick = 0;
        }
        else
        {
            this.tick =
                    (int) Math.floor(
                            this.currentFrameFloat
                    );
        }
    }

    /*
     * =========================================================
     * ZOOM
     * =========================================================
     */

    public float getZoom()
    {
        return this.zoom;
    }

    public void setZoom(float zoom)
    {
        this.zoom =
                Math.max(
                        MIN_ZOOM,
                        Math.min(
                                MAX_ZOOM,
                                zoom
                        )
                );
    }

    public void changeZoom(float delta)
    {
        setZoom(
                this.zoom + delta
        );
    }

    public float getPixelsPerFrame()
    {
        return BASE_FRAME_WIDTH *
                this.zoom;
    }

    /*
     * =========================================================
     * OFFSET
     * =========================================================
     */

    public int getOffset()
    {
        return this.offset;
    }

    public void setOffset(int offset)
    {
        this.offset = Math.max(0, offset);
    }

    public void addOffset(int amount)
    {
        this.offset += amount;

        if (this.offset < 0)
        {
            this.offset = 0;
        }
    }

    public void resetOffset()
    {
        this.offset = 0;
    }

    /*
     * =========================================================
     * FRAME <-> SCREEN
     * =========================================================
     */

    public int getFrameX(
            int frame,
            int timelineStartX)
    {
        return timelineStartX
                + Math.round(
                frame *
                        getPixelsPerFrame()
        )
                - this.offset;
    }

    public int getFrameFromMouseX(
            int mouseX,
            int timelineStartX)
    {
        int relativeX =
                mouseX -
                        timelineStartX +
                        this.offset;

        float pixelsPerFrame =
                getPixelsPerFrame();

        if (pixelsPerFrame <= 0.0F)
        {
            return 0;
        }

        return Math.round(
                (float) relativeX /
                        pixelsPerFrame
        );
    }

    /*
     * =========================================================
     * OFFSET LIMIT
     * =========================================================
     */

    public int getMaximumOffset(
            int maximumFrame,
            int timelineWidth)
    {
        if (timelineWidth <= 0)
        {
            return 0;
        }

        int contentWidth =
                Math.round(
                        (maximumFrame + 1) *
                                getPixelsPerFrame()
                );

        return Math.max(
                0,
                contentWidth -
                        timelineWidth
        );
    }

    public void clampOffset(
            int maximumFrame,
            int timelineWidth)
    {
        int maximumOffset =
                getMaximumOffset(
                        maximumFrame,
                        timelineWidth
                );

        this.offset =
                Math.max(
                        0,
                        Math.min(
                                this.offset,
                                maximumOffset
                        )
                );
    }
}