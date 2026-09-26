package com.example.examplemod;

public class EditorTimeline
{
    public static final int TICKS_PER_SECOND = 20;

    private int currentTick = 0;
    private int lastTick = 0;

    private boolean playing = false;

    /*
     * Fractional playback position.
     *
     * Например:
     *
     * currentTick = 10
     * playbackAccumulator = 0.5
     *
     * => реальная позиция = 10.5
     */
    private double playbackAccumulator = 0.0D;

    private long lastUpdateTime = 0L;

    /*
     * Скорость воспроизведения.
     *
     * 1.0 = один Minecraft tick/frame за один tick времени.
     */
    private double playbackSpeed = 1.0D;


    public EditorTimeline()
    {
        this.lastUpdateTime = System.nanoTime();
    }


    public void setLength(int length)
    {
        this.lastTick = Math.max(0, length - 1);

        if (this.currentTick > this.lastTick)
        {
            this.currentTick = this.lastTick;
        }

        this.playbackAccumulator = 0.0D;
    }


    public int getTick()
    {
        return this.currentTick;
    }


    public int getLastTick()
    {
        return this.lastTick;
    }


    /**
     * Возвращает текущую дробную позицию Timeline.
     *
     * Например:
     *
     * 10.0
     * 10.25
     * 10.5
     * 10.75
     */
    public float getCurrentFrameFloat()
    {
        if (!this.playing)
        {
            return (float) this.currentTick;
        }

        return (float)
                (
                        (double) this.currentTick
                                + this.playbackAccumulator
                );
    }


    public double getPlaybackSpeed()
    {
        return this.playbackSpeed;
    }


    public void setPlaybackSpeed(double speed)
    {
        this.playbackSpeed =
                Math.max(
                        0.0D,
                        speed
                );
    }


    public boolean isPlaying()
    {
        return this.playing;
    }


    public void play()
    {
        if (this.lastTick <= 0)
        {
            this.playing = false;
            return;
        }

        this.playing = true;
        this.lastUpdateTime = System.nanoTime();
    }


    public void pause()
    {
        this.playing = false;
        this.playbackAccumulator = 0.0D;
        this.lastUpdateTime = System.nanoTime();
    }


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


    public void rewind()
    {
        this.currentTick = 0;
        this.playbackAccumulator = 0.0D;
        this.lastUpdateTime = System.nanoTime();
    }


    public void setTick(int tick)
    {
        this.currentTick =
                Math.max(
                        0,
                        Math.min(
                                tick,
                                this.lastTick
                        )
                );

        this.playbackAccumulator = 0.0D;
        this.lastUpdateTime = System.nanoTime();
    }


    public void previousTick()
    {
        this.currentTick =
                Math.max(
                        0,
                        this.currentTick - 1
                );

        this.playbackAccumulator = 0.0D;
        this.lastUpdateTime = System.nanoTime();
    }


    public void nextTick()
    {
        this.currentTick =
                Math.min(
                        this.lastTick,
                        this.currentTick + 1
                );

        this.playbackAccumulator = 0.0D;
        this.lastUpdateTime = System.nanoTime();
    }


    public void update()
    {
        long now = System.nanoTime();

        /*
         * Первый update после создания / паузы.
         */
        if (this.lastUpdateTime == 0L)
        {
            this.lastUpdateTime = now;
            return;
        }

        long elapsedNanos =
                now - this.lastUpdateTime;

        this.lastUpdateTime = now;

        if (!this.playing)
        {
            return;
        }

        if (elapsedNanos <= 0L)
        {
            return;
        }

        /*
         * Переводим реальное прошедшее время
         * в Minecraft ticks.
         *
         * 20 ticks = 1 секунда.
         */
        double elapsedTicks =
                (
                        (double) elapsedNanos
                                /
                                1000000000.0D
                )
                        *
                        (double) TICKS_PER_SECOND;

        elapsedTicks *= this.playbackSpeed;

        this.playbackAccumulator += elapsedTicks;

        /*
         * Переходим через целые кадры,
         * сохраняя дробную часть.
         */
        while (
                this.playbackAccumulator >= 1.0D
        )
        {
            this.playbackAccumulator -= 1.0D;

            if (this.currentTick < this.lastTick)
            {
                this.currentTick++;
            }
            else
            {
                /*
                 * Дошли до конца.
                 *
                 * Зацикливаем воспроизведение
                 * с начала Timeline.
                 */
                this.currentTick = 0;
                this.playbackAccumulator = 0.0D;
            }
        }

        /*
         * Защита от возможного накопления
         * слишком большого значения.
         */
        if (this.playbackAccumulator < 0.0D)
        {
            this.playbackAccumulator = 0.0D;
        }

        if (this.playbackAccumulator >= 1.0D)
        {
            this.playbackAccumulator =
                    this.playbackAccumulator % 1.0D;
        }
    }
}